package com.example.data.can

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import com.example.model.S3xyProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

data class SocketTransportDiagnostics(
    val isWifiInterfaceBound: Boolean = false,
    val localWifiIp: String = "--",
    val detectedGatewayIp: String = "192.168.4.1",
    val handshakesSent: Int = 0,
    val datagramsReceived: Long = 0L,
    val bytesReceived: Long = 0L,
    val lastRawPacketHex: String = "None"
)

/**
 * Real Enhance S3XY Commander Wi-Fi Socket Client (192.168.4.1 UDP:1338 Panda/CANserver & TCP:35000 ELM327).
 *
 * Implements:
 * 1. Automatic active Wi-Fi interface discovery & socket binding (`Network.bindSocket`) so Android does not
 *    route 192.168.4.1 traffic over mobile cellular data when connected to a custom non-internet OBD SSID.
 * 2. Authentic S3XY Commander / CANserver Panda UDP protocol on port 1338:
 *    - 5-byte ASCII `"ehllo"` (`0x65 0x68 0x6C 0x6C 0x6F`) handshake and 1 Hz keep-alive heartbeat.
 *    - `0x0F` CAN ID filter subscription packet with 3-byte big-endian triplets `[0xFF, id_hi, id_lo]`.
 *    - Resilient receive loop that catches `SocketTimeoutException` without tearing down the UDP socket,
 *      continuously re-sending `"ehllo"` heartbeats and filter subscriptions until the vehicle streams data.
 *    - 3-byte ASCII `"bye"` (`0x62 0x79 0x65`) clean session teardown on disconnect.
 */
class S3xyTransportEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onBinaryDatagramReceived: (ByteArray, Int, Int) -> Unit,
    private val onAsciiLineReceived: (String, Int) -> Unit,
    private val onSocketStateChanged: (Boolean, String, SocketTransportDiagnostics) -> Unit
) {
    companion object {
        // S3XY Commander / Panda UDP handshake & heartbeat ("ehllo" - intentionally spelled ehllo in Panda firmware)
        private val PANDA_EHLLO_BYTES = byteArrayOf(0x65, 0x68, 0x6C, 0x6C, 0x6F)
        // S3XY Commander / Panda UDP disconnect command ("bye")
        private val PANDA_BYE_BYTES = byteArrayOf(0x62, 0x79, 0x65)
    }

    private var liveSocketJob: Job? = null
    @Volatile
    private var boundNetwork: Network? = null
    private var specifierCallback: ConnectivityManager.NetworkCallback? = null
    private var passiveWifiTrackerCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var activeUdpSocket: DatagramSocket? = null

    @Volatile
    private var activeTargetAddress: InetAddress? = null

    @Volatile
    private var activeTargetPort: Int = 1338

    @Volatile
    private var activeTcpOutput: OutputStream? = null

    @Volatile
    private var sendFilterWhitelist: Boolean = true

    @Volatile
    private var diagnostics = SocketTransportDiagnostics()

    init {
        registerPassiveWifiTracker()
    }

    /**
     * Passively tracks any active Wi-Fi network (even without internet capability, such as custom
     * S3XY Commander SSIDs connected via Android's system Wi-Fi settings) so sockets always bind to Wi-Fi.
     */
    private fun registerPassiveWifiTracker() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (boundNetwork == null) {
                        boundNetwork = network
                    }
                    runCatching { cm.bindProcessToNetwork(network) }
                }

                override fun onLost(network: Network) {
                    if (boundNetwork == network) {
                        boundNetwork = null
                    }
                }
            }
            passiveWifiTrackerCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            // Fallback to resolveActiveWifiNetwork() scan
        }
    }

    /**
     * Finds the currently connected Wi-Fi [Network] on the device (even if it has no internet access)
     * and binds the app process to it so 192.168.4.1 packets never route over 4G/5G cellular data.
     */
    fun resolveActiveWifiNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null

        // 1. Check explicitly bound network first
        boundNetwork?.let { existing ->
            val caps = runCatching { cm.getNetworkCapabilities(existing) }.getOrNull()
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                runCatching { cm.bindProcessToNetwork(existing) }
                return existing
            }
        }

        // 2. Scan all system networks for an active Wi-Fi transport
        val allNets = runCatching { cm.allNetworks }.getOrDefault(emptyArray())
        for (net in allNets) {
            val caps = runCatching { cm.getNetworkCapabilities(net) }.getOrNull()
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                boundNetwork = net
                runCatching { cm.bindProcessToNetwork(net) }
                return net
            }
        }
        return null
    }

    /**
     * Inspects LinkProperties of the active Wi-Fi network to determine the phone's local IPv4 address
     * and the S3XY Commander's gateway IP (typically 192.168.4.1).
     */
    fun inspectWifiLinkAddresses(wifiNetwork: Network?): Pair<String, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ("--" to "192.168.4.1")
        val net = wifiNetwork ?: resolveActiveWifiNetwork() ?: return ("--" to "192.168.4.1")
        val props = runCatching { cm.getLinkProperties(net) }.getOrNull() ?: return ("--" to "192.168.4.1")

        var localIp = "--"
        var gatewayIp = "192.168.4.1"

        for (linkAddr in props.linkAddresses) {
            val addr = linkAddr.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                localIp = addr.hostAddress ?: "--"
                val parts = localIp.split(".")
                if (parts.size == 4) {
                    gatewayIp = "${parts[0]}.${parts[1]}.${parts[2]}.1"
                }
                break
            }
        }

        for (route in props.routes) {
            val gw = route.gateway
            if (gw is Inet4Address && !gw.isLoopbackAddress && gw.hostAddress != "0.0.0.0") {
                gatewayIp = gw.hostAddress ?: gatewayIp
                break
            }
        }

        return localIp to gatewayIp
    }

    /**
     * Requests Android OS Wi-Fi connection & binding to the user's custom or default S3XY Commander SSID.
     */
    fun requestS3xyWifiBinding(
        ssid: String,
        passphrase: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            onResult(false, "ConnectivityManager unavailable on this device.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                unregisterSpecifierCallback(cm)
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                if (passphrase.isNotEmpty()) {
                    specifierBuilder.setWpa2Passphrase(passphrase)
                }

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifierBuilder.build())
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        boundNetwork = network
                        runCatching { cm.bindProcessToNetwork(network) }
                        onResult(true, "Bound to '$ssid' Wi-Fi network. Starting Panda/CAN stream...")
                    }

                    override fun onUnavailable() {
                        // Even if the specifier dialog was dismissed or already connected via system settings,
                        // check if the phone is already on Wi-Fi!
                        val activeWifi = resolveActiveWifiNetwork()
                        if (activeWifi != null) {
                            onResult(true, "Using active system Wi-Fi connection for '$ssid'.")
                        } else {
                            onResult(
                                false,
                                "Could not bind to '$ssid'. Connect to '$ssid' in Android Wi-Fi Settings first."
                            )
                        }
                    }
                }
                specifierCallback = callback
                cm.requestNetwork(request, callback, 8000)
            } catch (e: Exception) {
                val activeWifi = resolveActiveWifiNetwork()
                if (activeWifi != null) {
                    onResult(true, "Using active system Wi-Fi interface.")
                } else {
                    onResult(false, "Wi-Fi binding error: ${e.localizedMessage ?: "Unable to bind"}")
                }
            }
        } else {
            onResult(true, "Using system active Wi-Fi interface for '$ssid'.")
        }
    }

    fun startLiveSocket(
        hostIp: String,
        port: Int,
        protocol: S3xyProtocol,
        useFilterSubscription: Boolean = true
    ) {
        stopAll(sendBye = false)
        sendFilterWhitelist = useFilterSubscription
        liveSocketJob = scope.launch(Dispatchers.IO) {
            val wifiNet = resolveActiveWifiNetwork()
            val (localIp, detectedGw) = inspectWifiLinkAddresses(wifiNet)
            diagnostics = SocketTransportDiagnostics(
                isWifiInterfaceBound = wifiNet != null,
                localWifiIp = localIp,
                detectedGatewayIp = detectedGw
            )
            onSocketStateChanged(
                false,
                "Opening ${protocol.label} socket to $hostIp:$port (Wi-Fi IP: $localIp)...",
                diagnostics
            )
            try {
                when (protocol) {
                    S3xyProtocol.PANDA_UDP_1338 -> runPandaUdpLoop(hostIp, port)
                    S3xyProtocol.ELM327_TCP_35000 -> runElm327TcpLoop(hostIp, port)
                }
            } catch (e: Exception) {
                onSocketStateChanged(
                    false,
                    "Socket error on $hostIp:$port: ${e.localizedMessage ?: "Unreachable"}. Verify S3XY app -> Commander -> OBD Wi-Fi is ON.",
                    diagnostics
                )
            } finally {
                activeUdpSocket = null
                activeTcpOutput = null
            }
        }
    }

    /**
     * Manually re-sends the Panda `"ehllo"` handshake and `0x0F` CAN ID filter subscription over the active UDP socket.
     */
    fun sendPandaHandshakeAndFiltersNow(useFilterWhitelist: Boolean = sendFilterWhitelist) {
        sendFilterWhitelist = useFilterWhitelist
        scope.launch(Dispatchers.IO) {
            val udp = activeUdpSocket
            val addr = activeTargetAddress
            if (udp != null && !udp.isClosed && addr != null) {
                runCatching {
                    transmitPandaHandshakeAndFilters(udp, addr, activeTargetPort, useFilterWhitelist)
                }
            }
        }
    }

    private suspend fun transmitPandaHandshakeAndFilters(
        socket: DatagramSocket,
        targetAddress: InetAddress,
        port: Int,
        includeFilterPacket: Boolean
    ) {
        // 1. Send 5-byte ASCII "ehllo" handshake / keep-alive required by S3XY Commander & CANserver Panda UDP
        val ehlloPacket = DatagramPacket(PANDA_EHLLO_BYTES, PANDA_EHLLO_BYTES.size, targetAddress, port)
        socket.send(ehlloPacket)

        // 2. Send 0x0F + [0xFF, id_hi, id_lo]... filter subscription list so S3XY Commander forwards our CAN IDs
        if (includeFilterPacket) {
            delay(25)
            val filterBytes = Model3CanFrameDecoder.buildPandaFilterSubscriptionPacket()
            val filterPacket = DatagramPacket(filterBytes, filterBytes.size, targetAddress, port)
            socket.send(filterPacket)
        }

        diagnostics = diagnostics.copy(handshakesSent = diagnostics.handshakesSent + 1)
    }

    private suspend fun runPandaUdpLoop(hostIp: String, port: Int) {
        val socket = DatagramSocket(null)
        socket.reuseAddress = true
        socket.broadcast = true
        socket.soTimeout = 1000
        // Bind to ephemeral local port on all interfaces, then bind socket to the Wi-Fi network
        socket.bind(InetSocketAddress(0))

        socket.use { udp ->
            var wifiNet = resolveActiveWifiNetwork()
            if (wifiNet != null) {
                runCatching { wifiNet.bindSocket(udp) }
            }
            val (localIp, detectedGw) = inspectWifiLinkAddresses(wifiNet)
            val targetAddress = InetAddress.getByName(hostIp)
            activeUdpSocket = udp
            activeTargetAddress = targetAddress
            activeTargetPort = port

            diagnostics = diagnostics.copy(
                isWifiInterfaceBound = wifiNet != null,
                localWifiIp = localIp,
                detectedGatewayIp = detectedGw
            )

            // Initial S3XY Commander / Panda UDP handshake ("ehllo") + 0x0F filter subscription
            runCatching {
                transmitPandaHandshakeAndFilters(udp, targetAddress, port, sendFilterWhitelist)
            }

            onSocketStateChanged(
                false,
                "Sent 'ehllo' handshake to $hostIp:$port (Local IP $localIp) — awaiting CAN stream...",
                diagnostics
            )

            val rxBuffer = ByteArray(2048)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            var lastHeartbeatMs = System.currentTimeMillis()
            var lastFilterResendMs = System.currentTimeMillis()
            var framesThisSec = 0
            var fpsWindowStart = System.currentTimeMillis()
            var currentFps = 0
            var consecutiveTimeouts = 0

            while (scope.isActive) {
                val now = System.currentTimeMillis()

                // Send "ehllo" heartbeat every 900ms as required by Panda UDP 1338 protocol
                if (now - lastHeartbeatMs >= 900L) {
                    // Re-verify Wi-Fi binding if user switched networks while app was open
                    if (diagnostics.localWifiIp == "--" || !diagnostics.isWifiInterfaceBound) {
                        wifiNet = resolveActiveWifiNetwork()
                        if (wifiNet != null) {
                            runCatching { wifiNet.bindSocket(udp) }
                            val (newLocalIp, newGw) = inspectWifiLinkAddresses(wifiNet)
                            diagnostics = diagnostics.copy(
                                isWifiInterfaceBound = true,
                                localWifiIp = newLocalIp,
                                detectedGatewayIp = newGw
                            )
                        }
                    }

                    runCatching {
                        val ehlloPacket = DatagramPacket(
                            PANDA_EHLLO_BYTES,
                            PANDA_EHLLO_BYTES.size,
                            targetAddress,
                            port
                        )
                        udp.send(ehlloPacket)
                        diagnostics = diagnostics.copy(handshakesSent = diagnostics.handshakesSent + 1)
                    }
                    lastHeartbeatMs = now
                }

                // If no CAN packets have arrived yet, periodically re-send the 0x0F filter subscription packet
                if (diagnostics.datagramsReceived == 0L && now - lastFilterResendMs >= 2500L) {
                    runCatching {
                        if (sendFilterWhitelist) {
                            val filterBytes = Model3CanFrameDecoder.buildPandaFilterSubscriptionPacket()
                            udp.send(DatagramPacket(filterBytes, filterBytes.size, targetAddress, port))
                        }
                    }
                    lastFilterResendMs = now
                }

                try {
                    udp.receive(rxPacket)
                    val bytesRead = rxPacket.length
                    if (bytesRead > 0) {
                        consecutiveTimeouts = 0
                        val packetCopy = rxBuffer.copyOf(bytesRead)
                        val previewHex = packetCopy.take(16).joinToString(" ") { "%02X".format(it) }
                        diagnostics = diagnostics.copy(
                            datagramsReceived = diagnostics.datagramsReceived + 1L,
                            bytesReceived = diagnostics.bytesReceived + bytesRead.toLong(),
                            lastRawPacketHex = previewHex
                        )

                        framesThisSec += max(1, bytesRead / 16)
                        val afterRx = System.currentTimeMillis()
                        if (afterRx - fpsWindowStart >= 1000L) {
                            currentFps = framesThisSec
                            framesThisSec = 0
                            fpsWindowStart = afterRx
                        }

                        onSocketStateChanged(
                            true,
                            "Streaming S3XY_OBD UDP $hostIp:$port (${diagnostics.datagramsReceived} pkts)",
                            diagnostics
                        )
                        onBinaryDatagramReceived(packetCopy, bytesRead, max(1, currentFps))
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal 1-second receive timeout: do NOT close the socket! Keep heartbeating & listening.
                    consecutiveTimeouts++
                    if (consecutiveTimeouts >= 3) {
                        currentFps = 0
                        val bindStatus = if (diagnostics.isWifiInterfaceBound) {
                            "Wi-Fi Bound (${diagnostics.localWifiIp})"
                        } else {
                            "No Wi-Fi detected"
                        }
                        val waitingMsg = if (diagnostics.datagramsReceived == 0L) {
                            "Listening on $hostIp:$port [$bindStatus] — 0 pkts received (Verify Car Awake & S3XY OBD Wi-Fi ON)"
                        } else {
                            "Paused on $hostIp:$port [$bindStatus] — awaiting next CAN burst..."
                        }
                        onSocketStateChanged(
                            diagnostics.datagramsReceived > 0L && consecutiveTimeouts < 6,
                            waitingMsg,
                            diagnostics
                        )
                    }
                }
            }
        }
    }

    private suspend fun runElm327TcpLoop(hostIp: String, port: Int) {
        val wifiNet = resolveActiveWifiNetwork()
        val socket = wifiNet?.socketFactory?.createSocket() ?: Socket()
        socket.use { tcp ->
            tcp.connect(InetSocketAddress(hostIp, port), 4000)
            tcp.soTimeout = 4000
            val writer = tcp.getOutputStream()
            activeTcpOutput = writer
            val reader = BufferedReader(InputStreamReader(tcp.getInputStream()))

            // Initialize ELM327 / STN1170 for Tesla 500kbps 11-bit CAN monitoring
            val initCommands = listOf("ATZ\r", "ATE0\r", "ATH1\r", "ATS0\r", "ATSP6\r", "ATMA\r")
            for (cmd in initCommands) {
                writer.write(cmd.toByteArray())
                writer.flush()
                delay(80)
            }

            onSocketStateChanged(true, "Connected S3XY_OBD TCP $hostIp:$port", diagnostics)
            var linesThisSec = 0
            var windowStart = System.currentTimeMillis()
            var currentFps = 0

            while (scope.isActive) {
                try {
                    val line = reader.readLine() ?: break
                    val now = System.currentTimeMillis()
                    linesThisSec++
                    diagnostics = diagnostics.copy(
                        datagramsReceived = diagnostics.datagramsReceived + 1L,
                        bytesReceived = diagnostics.bytesReceived + line.length.toLong(),
                        lastRawPacketHex = line.take(24)
                    )
                    if (now - windowStart >= 1000L) {
                        currentFps = linesThisSec
                        linesThisSec = 0
                        windowStart = now
                    }
                    onSocketStateChanged(true, "Streaming S3XY_OBD TCP $hostIp:$port", diagnostics)
                    onAsciiLineReceived(line, max(1, currentFps))
                } catch (_: SocketTimeoutException) {
                    // Re-send ATMA monitor command if stream paused
                    runCatching {
                        writer.write("ATMA\r".toByteArray())
                        writer.flush()
                    }
                }
            }
        }
    }

    /**
     * Transmits an active UDS / OBD-II DTC interrogation request over the connected S3XY_OBD socket
     * and refreshes the CAN alert multiplexer subscriptions (0x3E2 BMS, 0x3E3 HVP, 0x7E8..0x7EF).
     */
    fun sendCanInterrogationRequest(onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val udp = activeUdpSocket
                val addr = activeTargetAddress
                val tcpOut = activeTcpOutput
                if (udp != null && !udp.isClosed && addr != null) {
                    // 1. Re-send "ehllo" and the 0x0F filter subscription including 0x3E2, 0x3E3, 0x7E8..0x7EB
                    transmitPandaHandshakeAndFilters(udp, addr, activeTargetPort, sendFilterWhitelist)
                    // 2. Also send 16-byte Panda CAN frame for UDS Broadcast 0x7DF: 02 03 00 00 00 00 00 00 (Read Active DTCs)
                    val frame = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt((0x7DF and 0x7FF) shl 21)
                        putInt(8)
                        put(byteArrayOf(0x02, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    }.array()
                    udp.send(DatagramPacket(frame, frame.size, addr, activeTargetPort))
                    onResult(
                        true,
                        "Sent Panda 'ehllo' + 0x0F alert filter subscription & UDS 0x7DF interrogation to $activeTargetPort."
                    )
                } else if (tcpOut != null) {
                    tcpOut.write("03\r".toByteArray())
                    tcpOut.flush()
                    delay(120)
                    tcpOut.write("ATMA\r".toByteArray())
                    tcpOut.flush()
                    onResult(true, "Transmitted OBD-II Mode 03 interrogation over S3XY_OBD TCP.")
                } else {
                    onResult(
                        false,
                        "UDP/TCP socket is not open. Tap the top status pill to open the S3XY Commander socket."
                    )
                }
            } catch (e: Exception) {
                onResult(false, "CAN interrogation transmit failed: ${e.localizedMessage ?: "Socket error"}")
            }
        }
    }

    /**
     * Transmits an OBD-II Mode 04 / UDS 0x14 Clear Diagnostic Information frame over the live S3XY_OBD socket.
     */
    fun sendClearFaultsRequest(onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val udp = activeUdpSocket
                val addr = activeTargetAddress
                val tcpOut = activeTcpOutput
                if (udp != null && !udp.isClosed && addr != null) {
                    val frame = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt((0x7DF and 0x7FF) shl 21)
                        putInt(8)
                        put(
                            byteArrayOf(
                                0x04,
                                0x14,
                                0xFF.toByte(),
                                0xFF.toByte(),
                                0xFF.toByte(),
                                0x00,
                                0x00,
                                0x00
                            )
                        )
                    }.array()
                    udp.send(DatagramPacket(frame, frame.size, addr, activeTargetPort))
                    onResult(true, "Transmitted UDS 0x14 Clear DTC request to S3XY Commander.")
                } else if (tcpOut != null) {
                    tcpOut.write("04\r".toByteArray())
                    tcpOut.flush()
                    delay(120)
                    tcpOut.write("ATMA\r".toByteArray())
                    tcpOut.flush()
                    onResult(true, "Transmitted OBD-II Mode 04 Clear DTC request to S3XY Commander.")
                } else {
                    onResult(
                        false,
                        "Cleared local alert list (S3XY Commander socket currently offline)."
                    )
                }
            } catch (e: Exception) {
                onResult(false, "Clear DTC transmit error: ${e.localizedMessage ?: "Socket error"}")
            }
        }
    }

    fun stopAll(sendBye: Boolean = true) {
        val udp = activeUdpSocket
        val addr = activeTargetAddress
        val port = activeTargetPort
        if (sendBye && udp != null && !udp.isClosed && addr != null) {
            runCatching {
                udp.send(DatagramPacket(PANDA_BYE_BYTES, PANDA_BYE_BYTES.size, addr, port))
            }
        }
        liveSocketJob?.cancel()
        liveSocketJob = null
        runCatching { activeUdpSocket?.close() }
        activeUdpSocket = null
        activeTcpOutput = null
    }

    private fun unregisterSpecifierCallback(cm: ConnectivityManager) {
        specifierCallback?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        specifierCallback = null
    }
}
