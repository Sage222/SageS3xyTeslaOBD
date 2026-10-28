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
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * Real S3XY_OBD Wi-Fi Socket Client (192.168.4.1 UDP:1338 Panda/CANserver & TCP:35000 ELM327)
 * with Android ConnectivityManager WifiNetworkSpecifier binding.
 * Contains zero demo or simulated data.
 */
class S3xyTransportEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onBinaryDatagramReceived: (ByteArray, Int, Int) -> Unit,
    private val onAsciiLineReceived: (String, Int) -> Unit,
    private val onSocketStateChanged: (Boolean, String) -> Unit
) {
    private var liveSocketJob: Job? = null
    private var boundNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    private var activeUdpSocket: DatagramSocket? = null

    @Volatile
    private var activeTargetAddress: InetAddress? = null

    @Volatile
    private var activeTargetPort: Int = 1338

    @Volatile
    private var activeTcpOutput: OutputStream? = null

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
                            "SSID '$ssid' not found in range. Ensure S3XY Commander OBD Wi-Fi is enabled."
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
                    "S3XY_OBD ($hostIp:$port) unreachable: ${e.message ?: "Timeout"}. Connect your device to the S3XY_OBD Wi-Fi network."
                )
            } finally {
                activeUdpSocket = null
                activeTcpOutput = null
            }
        }
    }

    /**
     * Transmits an active UDS / OBD-II DTC interrogation request over the connected S3XY_OBD socket.
     */
    fun sendCanInterrogationRequest(onResult: (Boolean, String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val udp = activeUdpSocket
                val addr = activeTargetAddress
                val tcpOut = activeTcpOutput
                if (udp != null && !udp.isClosed && addr != null) {
                    // Build 16-byte Panda CAN frame for UDS Broadcast 0x7DF: 02 03 00 00 00 00 00 00 (Read Active DTCs)
                    val frame = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putInt((0x7DF and 0x7FF) shl 21)
                        putInt(8)
                        put(byteArrayOf(0x02, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    }.array()
                    udp.send(DatagramPacket(frame, frame.size, addr, activeTargetPort))
                    onResult(true, "Transmitted UDS/CAN fault interrogation (0x7DF Mode 03) over S3XY_OBD UDP.")
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
                        "Not connected to S3XY_OBD Wi-Fi (192.168.4.1). Connect to your S3XY Commander first."
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
                    // Build 16-byte Panda CAN frame for UDS 0x7DF: 04 14 FF FF FF 00 00 00 (Clear Diagnostic Info)
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
                    onResult(true, "Transmitted UDS 0x14 Clear DTC request to S3XY_OBD.")
                } else if (tcpOut != null) {
                    tcpOut.write("04\r".toByteArray())
                    tcpOut.flush()
                    delay(120)
                    tcpOut.write("ATMA\r".toByteArray())
                    tcpOut.flush()
                    onResult(true, "Transmitted OBD-II Mode 04 Clear DTC request to S3XY_OBD.")
                } else {
                    onResult(
                        false,
                        "Cleared local alert list (S3XY_OBD socket currently offline)."
                    )
                }
            } catch (e: Exception) {
                onResult(false, "Clear DTC transmit error: ${e.localizedMessage ?: "Socket error"}")
            }
        }
    }

    private suspend fun runPandaUdpLoop(hostIp: String, port: Int) {
        DatagramSocket().use { socket ->
            socket.soTimeout = 3000
            boundNetwork?.bindSocket(socket)
            val targetAddress = InetAddress.getByName(hostIp)
            activeUdpSocket = socket
            activeTargetAddress = targetAddress
            activeTargetPort = port

            // Send Panda / CANserver wake & stream request byte (0x0F)
            val helloBytes = byteArrayOf(0x0F)
            val helloPacket = DatagramPacket(helloBytes, helloBytes.size, targetAddress, port)
            socket.send(helloPacket)

            val rxBuffer = ByteArray(1024)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            var lastHeartbeatMs = System.currentTimeMillis()
            var framesThisSec = 0
            var fpsWindowStart = System.currentTimeMillis()
            var currentFps = 0

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
                    onBinaryDatagramReceived(rxBuffer.copyOf(bytesRead), bytesRead, max(1, currentFps))
                }
            }
        }
    }

    private suspend fun runElm327TcpLoop(hostIp: String, port: Int) {
        val socket = boundNetwork?.socketFactory?.createSocket() ?: Socket()
        socket.use { tcp ->
            tcp.connect(InetSocketAddress(hostIp, port), 3000)
            tcp.soTimeout = 3000
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

            onSocketStateChanged(true, "Connected S3XY_OBD TCP $hostIp:$port")
            var linesThisSec = 0
            var windowStart = System.currentTimeMillis()
            var currentFps = 0

            while (scope.isActive) {
                val line = reader.readLine() ?: break
                val now = System.currentTimeMillis()
                linesThisSec++
                if (now - windowStart >= 1000L) {
                    currentFps = linesThisSec
                    linesThisSec = 0
                    windowStart = now
                }
                onAsciiLineReceived(line, max(1, currentFps))
            }
        }
    }

    fun stopAll() {
        liveSocketJob?.cancel()
        liveSocketJob = null
        activeUdpSocket?.close()
        activeUdpSocket = null
        activeTcpOutput = null
    }

    private fun unregisterNetworkCallback(cm: ConnectivityManager) {
        networkCallback?.let {
            runCatching { cm.unregisterNetworkCallback(it) }
        }
        networkCallback = null
    }
}
