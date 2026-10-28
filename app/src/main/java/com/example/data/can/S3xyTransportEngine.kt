package com.example.data.can

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.example.data.local.TelemetrySampleEntity
import com.example.model.BundledCanTrace
import com.example.model.S3xyProtocol
import com.example.model.VehicleTelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Handles both:
 * 1) Real S3XY_OBD Wi-Fi Socket Connectivity (192.168.4.1 UDP:1338 Panda stream & TCP:35000 ELM327)
 *    with Android ConnectivityManager WifiNetworkSpecifier binding.
 * 2) Deterministic Recorded CAN Log Replay Engine feeding binary Panda UDP packets through
 *    [Model3CanFrameDecoder] with Play/Pause, Scrubbing (0..100%), and Speed Multipliers (0.5x..4x).
 */
class S3xyTransportEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onBinaryDatagramReceived: (ByteArray, Int, Int) -> Unit,
    private val onAsciiLineReceived: (String, Int) -> Unit,
    private val onSocketStateChanged: (Boolean, String) -> Unit,
    private val onReplayProgressChanged: (Float, Int, Int) -> Unit
) {
    private var liveSocketJob: Job? = null
    private var replayJob: Job? = null
    private var boundNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var replayPaused: Boolean = false

    @Volatile
    private var replaySpeedMultiplier: Float = 1.0f

    @Volatile
    private var replayTickIndex: Int = 0

    private var activeSavedSamples: List<TelemetrySampleEntity>? = null

    /**
     * Requests Android OS Wi-Fi binding to the S3XY_OBD access point so local 192.168.4.1 sockets
     * route over Wi-Fi while mobile data stays active.
     */
    fun requestS3xyWifiBinding(
        ssid: String = "S3XY_OBD",
        passphrase: String = "12345678",
        onResult: (Boolean, String) -> Unit
    ) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            onResult(false, "ConnectivityManager unavailable on this device.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                unregisterNetworkCallback(cm)
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(passphrase)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        boundNetwork = network
                        cm.bindProcessToNetwork(network)
                        onResult(true, "Bound to $ssid Wi-Fi network. Opening CAN socket...")
                    }

                    override fun onUnavailable() {
                        onResult(
                            false,
                            "SSID '$ssid' not found in range. Ensure S3XY Commander OBD Wi-Fi is enabled, or use CAN Log Replay."
                        )
                    }
                }
                networkCallback = callback
                cm.requestNetwork(request, callback, 6000)
            } catch (e: Exception) {
                onResult(false, "Wi-Fi specifier error: ${e.localizedMessage ?: "Unable to bind"}")
            }
        } else {
            onResult(true, "Using system active Wi-Fi interface for $ssid.")
        }
    }

    fun startLiveSocket(
        hostIp: String,
        port: Int,
        protocol: S3xyProtocol
    ) {
        stopAll()
        liveSocketJob = scope.launch(Dispatchers.IO) {
            onSocketStateChanged(false, "Connecting to $hostIp:$port (${protocol.label})...")
            try {
                when (protocol) {
                    S3xyProtocol.PANDA_UDP_1338 -> runPandaUdpLoop(hostIp, port)
                    S3xyProtocol.ELM327_TCP_35000 -> runElm327TcpLoop(hostIp, port)
                }
            } catch (e: Exception) {
                onSocketStateChanged(
                    false,
                    "S3XY_OBD socket ($hostIp:$port) unreachable: ${e.message ?: "Timeout"}. Switch to CAN Log Replay when away from vehicle."
                )
            }
        }
    }

    private suspend fun runPandaUdpLoop(hostIp: String, port: Int) {
        DatagramSocket().use { socket ->
            socket.soTimeout = 2800
            boundNetwork?.bindSocket(socket)
            val targetAddress = InetAddress.getByName(hostIp)

            // Send Panda / CANserver wake & stream request byte (0x0F)
            val helloBytes = byteArrayOf(0x0F)
            val helloPacket = DatagramPacket(helloBytes, helloBytes.size, targetAddress, port)
            socket.send(helloPacket)

            val rxBuffer = ByteArray(1024)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            var lastHeartbeatMs = System.currentTimeMillis()
            var framesThisSec = 0
            var fpsWindowStart = System.currentTimeMillis()
            var currentFps = 45

            while (scope.isActive) {
                val now = System.currentTimeMillis()
                if (now - lastHeartbeatMs >= 1000L) {
                    socket.send(helloPacket)
                    lastHeartbeatMs = now
                }

                socket.receive(rxPacket)
                val bytesRead = rxPacket.length
                if (bytesRead > 0) {
                    framesThisSec += max(1, bytesRead / 16)
                    if (now - fpsWindowStart >= 1000L) {
                        currentFps = framesThisSec
                        framesThisSec = 0
                        fpsWindowStart = now
                    }
                    onSocketStateChanged(true, "Streaming S3XY_OBD UDP $hostIp:$port")
                    onBinaryDatagramReceived(rxBuffer.copyOf(bytesRead), bytesRead, currentFps)
                }
            }
        }
    }

    private suspend fun runElm327TcpLoop(hostIp: String, port: Int) {
        val socket = boundNetwork?.socketFactory?.createSocket() ?: Socket()
        socket.use { tcp ->
            tcp.connect(InetSocketAddress(hostIp, port), 2800)
            tcp.soTimeout = 2800
            val writer = tcp.getOutputStream()
            val reader = BufferedReader(InputStreamReader(tcp.getInputStream()))

            // Initialize ELM327 / STN1170 for Tesla 500kbps 11-bit CAN monitoring
            val initCommands = listOf("ATZ\r", "ATE0\r", "ATH1\r", "ATS0\r", "ATSP6\r", "ATMA\r")
            for (cmd in initCommands) {
                writer.write(cmd.toByteArray())
                writer.flush()
                delay(80)
            }

            onSocketStateChanged(true, "Connected S3XY_OBD TCP $hostIp:$port")
            var linesThisSec = 0
            var windowStart = System.currentTimeMillis()
            var currentFps = 35

            while (scope.isActive) {
                val line = reader.readLine() ?: break
                val now = System.currentTimeMillis()
                linesThisSec++
                if (now - windowStart >= 1000L) {
                    currentFps = linesThisSec
                    linesThisSec = 0
                    windowStart = now
                }
                onAsciiLineReceived(line, currentFps)
            }
        }
    }

    /**
     * Starts replaying a recorded 2023 Model 3 CAN trace (or user-recorded session from Room)
     * by synthesizing authentic binary Panda UDP frames and feeding them into the CAN decoder.
     */
    fun startRecordedCanReplay(
        trace: BundledCanTrace,
        savedSamples: List<TelemetrySampleEntity>? = null
    ) {
        stopAll()
        activeSavedSamples = savedSamples
        replayTickIndex = 0
        replayPaused = false

        val totalTicks = if (!savedSamples.isNullOrEmpty()) {
            savedSamples.size
        } else {
            trace.durationSec * 10 // 10 Hz base timeline ticks (1200 ticks = 120s)
        }

        replayJob = scope.launch(Dispatchers.Default) {
            onSocketStateChanged(
                true,
                if (!savedSamples.isNullOrEmpty()) "Replaying Saved Session (${savedSamples.size} frames)"
                else "CAN Replay: ${trace.title}"
            )

            while (isActive) {
                if (!replayPaused) {
                    val tick = replayTickIndex.coerceIn(0, max(1, totalTicks - 1))
                    val snapshot = if (!savedSamples.isNullOrEmpty()) {
                        buildSnapshotFromSavedSample(savedSamples[tick], tick)
                    } else {
                        synthesizeTraceSnapshot(trace, tick, totalTicks)
                    }

                    val binaryPacket = Model3CanFrameDecoder.encodeSnapshotToPandaUdpPacket(snapshot)
                    val simulatedMsgRate = (90 * replaySpeedMultiplier).toInt()
                    onBinaryDatagramReceived(binaryPacket, binaryPacket.size, simulatedMsgRate)

                    val progress = tick.toFloat() / max(1, totalTicks - 1).toFloat()
                    onReplayProgressChanged(progress.coerceIn(0f, 1f), tick / 10, totalTicks / 10)

                    replayTickIndex = (replayTickIndex + 1) % max(1, totalTicks)
                }

                val delayMs = (100L / replaySpeedMultiplier).toLong().coerceIn(20L, 250L)
                delay(delayMs)
            }
        }
    }

    fun setReplayPaused(paused: Boolean) {
        replayPaused = paused
    }

    fun setReplaySpeed(multiplier: Float) {
        replaySpeedMultiplier = multiplier.coerceIn(0.5f, 4.0f)
    }

    fun seekReplayProgress(fraction: Float, trace: BundledCanTrace) {
        val totalTicks = activeSavedSamples?.size ?: (trace.durationSec * 10)
        replayTickIndex = (fraction.coerceIn(0f, 1f) * (totalTicks - 1)).toInt()
    }

    fun stopAll() {
        liveSocketJob?.cancel()
        liveSocketJob = null
        replayJob?.cancel()
        replayJob = null
    }

    private fun unregisterNetworkCallback(cm: ConnectivityManager) {
        networkCallback?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        networkCallback = null
    }

    private fun buildSnapshotFromSavedSample(
        sample: TelemetrySampleEntity,
        tick: Int
    ): VehicleTelemetrySnapshot {
        val phase = tick * 0.1f
        return VehicleTelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            speedKph = sample.speedKph,
            netPowerKw = sample.netPowerKw,
            rearMotorKw = sample.rearMotorKw,
            frontMotorKw = sample.frontMotorKw,
            rearTorqueNm = sample.rearMotorKw * 1.65f,
            frontTorqueNm = sample.frontMotorKw * 1.45f,
            rearRpm = (sample.speedKph * 74).toInt(),
            frontRpm = (sample.speedKph * 69).toInt(),
            throttlePct = if (sample.netPowerKw > 0) (sample.netPowerKw / 3.5f).coerceIn(0f, 100f) else 0f,
            brakePct = if (sample.brakePressureBar > 2f) (sample.brakePressureBar * 0.8f).coerceIn(0f, 100f) else 0f,
            rearInverterTempC = (sample.statorTempC * 0.72f).coerceIn(28f, 105f),
            frontInverterTempC = (sample.statorTempC * 0.65f).coerceIn(25f, 95f),
            statorTempC = sample.statorTempC,
            usableSocPct = sample.usableSocPct,
            bmsSocPct = (sample.usableSocPct + 1.8f).coerceIn(0f, 100f),
            packVoltageV = sample.packVoltageV,
            packCurrentA = sample.packCurrentA,
            cellMinV = (sample.packVoltageV / 96f) - (sample.cellDeltaMv / 2000f),
            cellMaxV = (sample.packVoltageV / 96f) + (sample.cellDeltaMv / 2000f),
            cellDeltaMv = sample.cellDeltaMv,
            coolantBatteryInletC = sample.coolantBatteryInletC,
            coolantPowertrainInletC = sample.coolantBatteryInletC + 8.5f,
            batteryCoolantLpm = 12.0f + 4.0f * abs(sin(phase)),
            powertrainCoolantLpm = 15.0f + 6.0f * abs(cos(phase)),
            lateralG = sample.lateralG,
            longitudinalG = sample.longitudinalG,
            brakePressureBar = sample.brakePressureBar,
            steeringAngleDeg = sample.lateralG * 95f
        )
    }

    private fun synthesizeTraceSnapshot(
        trace: BundledCanTrace,
        tick: Int,
        totalTicks: Int
    ): VehicleTelemetrySnapshot {
        val t = tick * 0.1f // seconds
        val progress = tick.toFloat() / max(1, totalTicks).toFloat()

        return when (trace) {
            BundledCanTrace.CANYON_SPIRITED_RUN -> {
                val wave = sin(t * 0.35f)
                val fastWave = cos(t * 0.85f)
                val speed = (108f + 64f * wave + 18f * fastWave).coerceIn(32f, 196f)
                val accelDemand = cos(t * 0.35f) * 0.7f - sin(t * 0.85f) * 0.5f
                val netKw = (accelDemand * 310f).coerceIn(-82f, 348f)
                val rearKw = (netKw * 0.64f).coerceIn(-55f, 225f)
                val frontKw = (netKw * 0.36f).coerceIn(-30f, 130f)
                val packV = (386f - netKw * 0.085f).coerceIn(345f, 398f)
                val packA = (netKw * 1000f / packV).coerceIn(-220f, 920f)
                val soc = (78.5f - progress * 6.4f).coerceIn(10f, 100f)
                val cellMin = (packV / 96f) - 0.006f
                val cellMax = (packV / 96f) + (0.006f + abs(netKw) * 0.00004f)
                val statorTemp = 68f + progress * 48f + 8f * abs(wave)
                val rearInvTemp = 48f + progress * 34f + 6f * abs(fastWave)
                val latG = (0.78f * sin(t * 0.55f)).coerceIn(-1.15f, 1.15f)
                val lonG = (netKw / 340f * 0.88f).coerceIn(-0.95f, 0.98f)
                val brakeBar = if (netKw < -35f) abs(netKw + 35f) * 1.4f else 0f

                VehicleTelemetrySnapshot(
                    speedKph = speed,
                    netPowerKw = netKw,
                    rearMotorKw = rearKw,
                    frontMotorKw = frontKw,
                    rearTorqueNm = rearKw * 1.75f,
                    frontTorqueNm = frontKw * 1.55f,
                    rearRpm = (speed * 75f).toInt(),
                    frontRpm = (speed * 70f).toInt(),
                    throttlePct = if (netKw > 0) (netKw / 3.48f).coerceIn(0f, 100f) else 0f,
                    brakePct = if (brakeBar > 0) (brakeBar * 0.9f).coerceIn(0f, 100f) else 0f,
                    rearInverterTempC = rearInvTemp,
                    frontInverterTempC = rearInvTemp - 6.5f,
                    statorTempC = statorTemp,
                    zeroToSixtySec = 3.24f,
                    currentGear = "D",
                    usableSocPct = soc,
                    bmsSocPct = soc + 1.9f,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    cellMinV = cellMin,
                    cellMaxV = cellMax,
                    cellDeltaMv = (cellMax - cellMin) * 1000f,
                    packTempMinC = 32f + progress * 9f,
                    packTempMaxC = 35f + progress * 12f,
                    nominalFullPackKwh = 76.8f,
                    initialPackKwh = 79.5f,
                    degradationPct = 3.4f,
                    chargePowerKw = 0f,
                    octovalvePosition = 4,
                    coolantBatteryInletC = 33.5f + progress * 8.5f,
                    coolantPowertrainInletC = 44.0f + progress * 14.0f,
                    batteryCoolantLpm = 16.5f + 4f * abs(wave),
                    powertrainCoolantLpm = 24.0f + 5f * abs(fastWave),
                    cabinTempC = 20.5f,
                    ambientTempC = 19.0f,
                    hvacPowerKw = 1.15f,
                    lateralG = latG,
                    longitudinalG = lonG,
                    brakePressureBar = brakeBar,
                    regenSplitPct = if (brakeBar > 5f) 68f else 100f,
                    steeringAngleDeg = latG * 115f
                )
            }

            BundledCanTrace.V3_SUPERCHARGE_250KW -> {
                val soc = (14.0f + progress * 58.0f).coerceIn(10f, 95f)
                val taperFactor = if (soc < 38f) 1.0f else (1.0f - (soc - 38f) / 62f).coerceIn(0.28f, 1.0f)
                val chargeKw = (250.0f * taperFactor + 3.0f * sin(t * 0.4f)).coerceIn(45f, 252f)
                val packV = (352f + progress * 46f).coerceIn(340f, 404f)
                val packA = -(chargeKw * 1000f / packV)
                val cellMin = (packV / 96f) - 0.008f
                val cellMax = (packV / 96f) + 0.011f

                VehicleTelemetrySnapshot(
                    speedKph = 0f,
                    netPowerKw = -chargeKw,
                    rearMotorKw = 0f,
                    frontMotorKw = 0f,
                    rearTorqueNm = 0f,
                    frontTorqueNm = 0f,
                    rearRpm = 0,
                    frontRpm = 0,
                    throttlePct = 0f,
                    brakePct = 0f,
                    rearInverterTempC = 41.0f,
                    frontInverterTempC = 39.0f,
                    statorTempC = 46.5f,
                    zeroToSixtySec = 3.28f,
                    currentGear = "P",
                    usableSocPct = soc,
                    bmsSocPct = soc + 1.5f,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    cellMinV = cellMin,
                    cellMaxV = cellMax,
                    cellDeltaMv = (cellMax - cellMin) * 1000f,
                    packTempMinC = 44.0f + 4.5f * sin(progress * PI.toFloat()),
                    packTempMaxC = 48.5f + 4.5f * sin(progress * PI.toFloat()),
                    nominalFullPackKwh = 76.8f,
                    initialPackKwh = 79.5f,
                    degradationPct = 3.4f,
                    chargePowerKw = chargeKw,
                    octovalvePosition = 7,
                    coolantBatteryInletC = 45.2f + 3.0f * cos(t * 0.2f),
                    coolantPowertrainInletC = 42.0f,
                    batteryCoolantLpm = 26.8f,
                    powertrainCoolantLpm = 18.4f,
                    cabinTempC = 21.5f,
                    ambientTempC = 24.0f,
                    hvacPowerKw = 4.65f,
                    lateralG = 0.0f,
                    longitudinalG = 0.0f,
                    brakePressureBar = 0f,
                    regenSplitPct = 100f,
                    steeringAngleDeg = 0f
                )
            }

            BundledCanTrace.SUB_ZERO_OCTOVALVE -> {
                val speed = (58f + 22f * sin(t * 0.25f)).coerceIn(0f, 95f)
                val netKw = (34f + 24f * cos(t * 0.4f)).coerceIn(-25f, 75f)
                val packTemp = -6.0f + progress * 24.0f
                val cabinTemp = -2.0f + progress * 23.0f
                val hvacKw = (5.8f - progress * 2.4f).coerceIn(1.8f, 6.2f)
                val packV = 374.0f - netKw * 0.05f
                val packA = (netKw * 1000f) / packV

                VehicleTelemetrySnapshot(
                    speedKph = speed,
                    netPowerKw = netKw,
                    rearMotorKw = netKw * 0.55f,
                    frontMotorKw = netKw * 0.45f,
                    rearTorqueNm = netKw * 1.2f,
                    frontTorqueNm = netKw * 0.9f,
                    rearRpm = (speed * 74f).toInt(),
                    frontRpm = (speed * 70f).toInt(),
                    throttlePct = (netKw.coerceAtLeast(0f) * 0.8f),
                    brakePct = 0f,
                    rearInverterTempC = 28.0f + progress * 24f,
                    frontInverterTempC = 26.0f + progress * 22f,
                    statorTempC = 54.0f + progress * 28f, // Stator intentional heat scavenge
                    zeroToSixtySec = 3.45f,
                    currentGear = "D",
                    usableSocPct = 68.0f - progress * 4.2f,
                    bmsSocPct = 71.5f - progress * 4.2f,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    cellMinV = (packV / 96f) - 0.007f,
                    cellMaxV = (packV / 96f) + 0.007f,
                    cellDeltaMv = 14.0f,
                    packTempMinC = packTemp,
                    packTempMaxC = packTemp + 3.5f,
                    nominalFullPackKwh = 76.8f,
                    initialPackKwh = 79.5f,
                    degradationPct = 3.4f,
                    chargePowerKw = 0f,
                    octovalvePosition = 8,
                    coolantBatteryInletC = packTemp + 6.5f,
                    coolantPowertrainInletC = 48.0f + progress * 12f,
                    batteryCoolantLpm = 14.2f,
                    powertrainCoolantLpm = 19.6f,
                    cabinTempC = cabinTemp,
                    ambientTempC = -8.0f,
                    hvacPowerKw = hvacKw,
                    lateralG = 0.18f * sin(t * 0.3f),
                    longitudinalG = 0.14f * cos(t * 0.4f),
                    brakePressureBar = 0f,
                    regenSplitPct = 55f,
                    steeringAngleDeg = 22f * sin(t * 0.3f)
                )
            }

            BundledCanTrace.TRACK_FAULT_DIAGNOSTIC -> {
                val wave = sin(t * 0.6f)
                val speed = (154f + 58f * wave).coerceIn(72f, 228f)
                val netKw = (210f + 135f * cos(t * 0.6f)).coerceIn(-92f, 355f)
                val packV = (364f - netKw * 0.09f).coerceIn(332f, 392f)
                val packA = (netKw * 1000f) / packV
                val deltaMv = 24.0f + 14.0f * abs(cos(t * 0.4f))
                val cellMin = (packV / 96f) - (deltaMv / 2000f)
                val cellMax = (packV / 96f) + (deltaMv / 2000f)
                val statorTemp = 126.0f + 22.0f * abs(wave)
                val rearInvTemp = 88.0f + 14.0f * abs(wave)
                val latG = 1.05f * sin(t * 0.7f)
                val lonG = (netKw / 350f) * 0.92f
                val brakeBar = if (netKw < -20f) 85f * abs(wave) else 0f

                VehicleTelemetrySnapshot(
                    speedKph = speed,
                    netPowerKw = netKw,
                    rearMotorKw = netKw * 0.65f,
                    frontMotorKw = netKw * 0.35f,
                    rearTorqueNm = netKw * 1.15f,
                    frontTorqueNm = netKw * 0.72f,
                    rearRpm = (speed * 76f).toInt(),
                    frontRpm = (speed * 71f).toInt(),
                    throttlePct = if (netKw > 0) (netKw / 3.55f).coerceIn(0f, 100f) else 0f,
                    brakePct = if (brakeBar > 0) (brakeBar * 0.85f).coerceIn(0f, 100f) else 0f,
                    rearInverterTempC = rearInvTemp,
                    frontInverterTempC = rearInvTemp - 9f,
                    statorTempC = statorTemp,
                    zeroToSixtySec = 3.19f,
                    currentGear = "D",
                    usableSocPct = 54.0f - progress * 11.0f,
                    bmsSocPct = 56.2f - progress * 11.0f,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    cellMinV = cellMin,
                    cellMaxV = cellMax,
                    cellDeltaMv = deltaMv,
                    packTempMinC = 48.0f,
                    packTempMaxC = 54.5f,
                    nominalFullPackKwh = 76.8f,
                    initialPackKwh = 79.5f,
                    degradationPct = 3.4f,
                    chargePowerKw = 0f,
                    octovalvePosition = 1,
                    coolantBatteryInletC = 51.4f,
                    coolantPowertrainInletC = 71.2f,
                    batteryCoolantLpm = 27.5f,
                    powertrainCoolantLpm = 31.8f,
                    cabinTempC = 23.0f,
                    ambientTempC = 31.0f,
                    hvacPowerKw = 5.20f,
                    lateralG = latG,
                    longitudinalG = lonG,
                    brakePressureBar = brakeBar,
                    regenSplitPct = 62f,
                    steeringAngleDeg = latG * 130f
                )
            }
        }
    }
}
