package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import com.example.model.BundledCanTrace
import com.example.model.ConnectionMode
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
            ConnectionStatus.REPLAYING_LOG -> ElectricCyan
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
                // Left: Brand + Connection Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Gear PRND Pill
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

                    // Connection Status Chip (Opens S3XY_OBD Wi-Fi & Replay Drawer)
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
                                    text = uiState.connectionStatus.badgeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                                Text(
                                    text = "${uiState.busFrameRateFps} msg/s • ${
                                        if (uiState.connectionMode == ConnectionMode.LIVE_S3XY_WIFI) uiState.s3xyHostIp
                                        else uiState.activeReplayTrace.tag
                                    }",
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

            // If in Recorded CAN Replay Mode, show compact scrub & speed control strip
            if (uiState.connectionMode == ConnectionMode.CAN_LOG_REPLAY) {
                ReplayControlStrip(
                    uiState = uiState,
                    onOpenConnectionSheet = onOpenConnectionSheet
                )
            }
        }
    }
}

@Composable
fun ReplayControlStrip(
    uiState: CockpitUiState,
    onTogglePause: (() -> Unit)? = null,
    onSpeedSelected: ((Float) -> Unit)? = null,
    onSeek: ((Float) -> Unit)? = null,
    onOpenConnectionSheet: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenConnectionSheet() }
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = "CAN Replay Source",
                    tint = ElectricCyan,
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
                text = "${uiState.replayElapsedSec}s / ${uiState.replayTotalSec}s • ${uiState.replaySpeedMultiplier}x",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = ElectricCyan
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun S3xyConnectionModalSheet(
    uiState: CockpitUiState,
    onDismiss: () -> Unit,
    onUpdateNetworkConfig: (String, String, String, Int, S3xyProtocol) -> Unit,
    onConnectLiveWifi: (Boolean) -> Unit,
    onSelectBundledTrace: (BundledCanTrace) -> Unit,
    onToggleReplayPause: () -> Unit,
    onSelectReplaySpeed: (Float) -> Unit,
    onSeekReplay: (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var ssid by remember(uiState.s3xySsid) { mutableStateOf(uiState.s3xySsid) }
    var passphrase by remember(uiState.s3xyPassphrase) { mutableStateOf(uiState.s3xyPassphrase) }
    var hostIp by remember(uiState.s3xyHostIp) { mutableStateOf(uiState.s3xyHostIp) }
    var portText by remember(uiState.s3xyPort) { mutableStateOf(uiState.s3xyPort.toString()) }
    var protocol by remember(uiState.s3xyProtocol) { mutableStateOf(uiState.s3xyProtocol) }

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
                text = "S3XY_OBD Wi-Fi & CAN Replay Controller",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Connect directly to your 2023 Model 3 Enhance S3XY Commander over Wi-Fi (192.168.4.1) or replay recorded multi-bus CAN captures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Section 1: Live S3XY_OBD Wi-Fi Configuration
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                            text = "1. Live S3XY_OBD Wi-Fi Socket",
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
                            label = { Text("Wi-Fi SSID") },
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
                            label = { Text("Socket Port") },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val parsedPort = portText.toIntOrNull() ?: protocol.defaultPort
                                onUpdateNetworkConfig(ssid, passphrase, hostIp, parsedPort, protocol)
                                onConnectLiveWifi(false)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_socket_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                        ) {
                            Text("Open CAN Socket", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val parsedPort = portText.toIntOrNull() ?: protocol.defaultPort
                                onUpdateNetworkConfig(ssid, passphrase, hostIp, parsedPort, protocol)
                                onConnectLiveWifi(true)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bind S3XY_OBD Wi-Fi")
                        }
                    }
                }
            }

            // Section 2: Recorded CAN Log Replay Engine
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. Recorded 2023 Model 3 CAN Log Replay",
                        style = MaterialTheme.typography.titleMedium,
                        color = RegenEmerald
                    )

                    // Playback Transport Controls (Play/Pause, Timeline Scrub, Speed Multipliers)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = onToggleReplayPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isReplayPaused) RegenEmerald else ElectricCyan
                            )
                        ) {
                            Icon(
                                imageVector = if (uiState.isReplayPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (uiState.isReplayPaused) "Resume Replay" else "Pause Replay")
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(0.5f, 1.0f, 2.0f, 4.0f).forEach { spd ->
                                FilterChip(
                                    selected = uiState.replaySpeedMultiplier == spd,
                                    onClick = { onSelectReplaySpeed(spd) },
                                    label = { Text("${spd}x") }
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Scrub CAN Timeline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.replayElapsedSec}s / ${uiState.replayTotalSec}s",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = ElectricCyan
                            )
                        }
                        Slider(
                            value = uiState.replayProgress,
                            onValueChange = onSeekReplay,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan
                            )
                        )
                    }

                    // 4 Bundled 2023 Model 3 CAN Traces
                    BundledCanTrace.entries.forEach { trace ->
                        val isSelected = uiState.connectionMode == ConnectionMode.CAN_LOG_REPLAY &&
                            uiState.activeReplayTrace == trace
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectBundledTrace(trace) }
                                .testTag("replay_trace_${trace.id}"),
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trace.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = trace.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = trace.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
