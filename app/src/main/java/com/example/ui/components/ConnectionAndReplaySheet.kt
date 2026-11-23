package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.S3xyProtocol
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.InverterAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OrbitronDisplayFamily
import com.example.ui.theme.RegenEmerald
import com.example.ui.theme.TrackCrimson
import com.example.ui.viewmodel.CockpitUiState

@Composable
fun CockpitTopStatusBar(
    uiState: CockpitUiState,
    onOpenConnectionSheet: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor by animateColorAsState(
        targetValue = when (uiState.connectionStatus) {
            ConnectionStatus.CONNECTED_LIVE -> RegenEmerald
            ConnectionStatus.CONNECTING -> InverterAmber
            ConnectionStatus.SOCKET_ERROR, ConnectionStatus.DISCONNECTED -> TrackCrimson
        },
        label = "status_badge_color"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Gear + S3XY_OBD Connection Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = uiState.snapshot.currentGear,
                            fontFamily = OrbitronDisplayFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElectricCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenConnectionSheet)
                            .testTag("connection_status_pill"),
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Column {
                                Text(
                                    text = "${uiState.s3xySsid} • ${uiState.connectionStatus.badgeText}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${uiState.busFrameRateFps} msg/s • ${uiState.s3xyHostIp}:${uiState.s3xyPort} (${uiState.socketDiagnostics.datagramsReceived} pkts)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Right: Session REC Button & Dark/Light Mode Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isRec = uiState.isRecordingSession
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleRecording)
                            .testTag("record_session_button"),
                        color = if (isRec) TrackCrimson else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isRec) TrackCrimson else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isRec) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = if (isRec) "Stop Recording" else "Start Recording",
                                tint = if (isRec) MaterialTheme.colorScheme.onError else TrackCrimson,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isRec) "REC ${uiState.recordingElapsedSec}s" else "REC",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isRec) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark or Daylight Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.connectionStatus != ConnectionStatus.CONNECTED_LIVE) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenConnectionSheet)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = "S3XY_OBD Status",
                                tint = InverterAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = uiState.statusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "DIAGNOSE / BIND",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3xyConnectionModalSheet(
    uiState: CockpitUiState,
    onDismiss: () -> Unit,
    onUpdateNetworkConfig: (String, String, String, Int, S3xyProtocol, Boolean) -> Unit,
    onTogglePandaFilterWhitelist: (Boolean) -> Unit,
    onResendPandaHandshake: () -> Unit,
    onConnectLiveWifi: (Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ssid by remember(uiState.s3xySsid) { mutableStateOf(uiState.s3xySsid) }
    var passphrase by remember(uiState.s3xyPassphrase) { mutableStateOf(uiState.s3xyPassphrase) }
    var hostIp by remember(uiState.s3xyHostIp) { mutableStateOf(uiState.s3xyHostIp) }
    var portText by remember(uiState.s3xyPort) { mutableStateOf(uiState.s3xyPort.toString()) }
    var protocol by remember(uiState.s3xyProtocol) { mutableStateOf(uiState.s3xyProtocol) }
    var useFilterList by remember(uiState.usePandaFilterWhitelist) { mutableStateOf(uiState.usePandaFilterWhitelist) }
    val diag = uiState.socketDiagnostics

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "S3XY Commander Wi-Fi & Panda UDP Configuration",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Supports custom SSIDs and automatically binds UDP/TCP sockets to your active Wi-Fi interface so Android does not route 192.168.4.1 traffic over mobile data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Live Wi-Fi & UDP Transport Diagnostics Panel
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                border = BorderStroke(
                    1.dp,
                    if (diag.datagramsReceived > 0L) RegenEmerald else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE SOCKET & WI-FI INTERFACE TELEMETRY",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ElectricCyan
                        )
                        Text(
                            text = if (diag.isWifiInterfaceBound) "WI-FI BOUND" else "NO WI-FI INTERFACE",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (diag.isWifiInterfaceBound) RegenEmerald else TrackCrimson
                        )
                    }

                    Text(
                        text = "Phone Wi-Fi IP: ${diag.localWifiIp}  •  Detected Gateway: ${diag.detectedGatewayIp}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Panda 'ehllo' Sent: ${diag.handshakesSent}  •  RX Packets: ${diag.datagramsReceived} (${diag.bytesReceived} bytes)",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Last RX Hex: ${diag.lastRawPacketHex}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (diag.detectedGatewayIp != hostIp && diag.detectedGatewayIp != "--") {
                        OutlinedButton(
                            onClick = {
                                hostIp = diag.detectedGatewayIp
                                val parsedPort = portText.toIntOrNull() ?: protocol.defaultPort
                                onUpdateNetworkConfig(ssid, passphrase, hostIp, parsedPort, protocol, useFilterList)
                                onConnectLiveWifi(false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Detected Wi-Fi Gateway IP (${diag.detectedGatewayIp})")
                        }
                    }

                    OutlinedButton(
                        onClick = onResendPandaHandshake,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resend_handshake_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Re-Send Panda 'ehllo' + 0x0F CAN Filter Subscription Now")
                    }
                }
            }

            // 2. Custom SSID, IP, Port & Protocol Settings
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Text(
                            text = "Enhance S3XY Commander Network Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("Custom Wi-Fi SSID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("WPA2 Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hostIp,
                            onValueChange = { hostIp = it },
                            label = { Text("Commander IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it },
                            label = { Text("UDP/TCP Port") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        S3xyProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = protocol == proto,
                                onClick = {
                                    protocol = proto
                                    portText = proto.defaultPort.toString()
                                },
                                label = { Text(proto.label) }
                            )
                        }
                    }

                    if (protocol == S3xyProtocol.PANDA_UDP_1338) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Send 0x0F CAN ID Filter Subscription",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Subscribes to 26 Model 3 CAN IDs ([0x0F, 0xFF, id_hi, id_lo]) after 'ehllo'. Toggle off if your firmware streams all frames unfiltered.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useFilterList,
                                onCheckedChange = { checked ->
                                    useFilterList = checked
                                    onTogglePandaFilterWhitelist(checked)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val parsedPort = portText.toIntOrNull() ?: protocol.defaultPort
                                onUpdateNetworkConfig(ssid, passphrase, hostIp, parsedPort, protocol, useFilterList)
                                onConnectLiveWifi(false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_socket_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                        ) {
                            Text("Save & Open Socket", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val parsedPort = portText.toIntOrNull() ?: protocol.defaultPort
                                onUpdateNetworkConfig(ssid, passphrase, hostIp, parsedPort, protocol, useFilterList)
                                onConnectLiveWifi(true)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bind Custom SSID")
                        }
                    }

                    if (uiState.connectionStatus == ConnectionStatus.CONNECTED_LIVE ||
                        uiState.connectionStatus == ConnectionStatus.CONNECTING
                    ) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send 'bye' & Disconnect Socket", color = TrackCrimson)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
