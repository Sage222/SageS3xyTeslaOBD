package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TelemetrySampleEntity
import com.example.data.local.TelemetrySessionEntity
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.InverterAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OrbitronDisplayFamily
import com.example.ui.theme.RegenEmerald
import com.example.ui.theme.TrackCrimson
import com.example.ui.viewmodel.CockpitUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun TelemetryLogsScreen(
    uiState: CockpitUiState,
    sessions: List<TelemetrySessionEntity>,
    onToggleRecording: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onExportCsv: (TelemetrySessionEntity) -> Unit,
    onExportJson: (TelemetrySessionEntity?) -> Unit,
    onDeleteSession: (TelemetrySessionEntity) -> Unit,
    onPurgeAllLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }
    val activeSession = sessions.find { it.id == uiState.selectedLogSessionId } ?: sessions.firstOrNull()

    LaunchedEffect(activeSession?.id) {
        if (activeSession != null && uiState.selectedLogSessionId != activeSession.id) {
            onSelectSession(activeSession.id)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        Column(modifier = Modifier.fillMaxSize()) {
            LogsTopActionStrip(
                uiState = uiState,
                sessionCount = sessions.size,
                onToggleRecording = onToggleRecording,
                onExportFullJson = { onExportJson(activeSession) },
                onRequestPurgeAll = { showPurgeConfirmDialog = true }
            )

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No Recorded S3XY_OBD Telemetry Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect to your S3XY_OBD Wi-Fi (192.168.4.1) and tap 'Start Session Log' to record live 2023 Model 3 CAN metrics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .testTag("sessions_list_column"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionSummaryCard(
                                session = session,
                                isSelected = session.id == activeSession?.id,
                                onSelect = { onSelectSession(session.id) },
                                onExportCsv = { onExportCsv(session) },
                                onDelete = { onDeleteSession(session) }
                            )
                        }
                    }

                    if (activeSession != null) {
                        SessionDetailChartCard(
                            session = activeSession,
                            samples = uiState.selectedLogSamples,
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("sessions_list_compact"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeSession != null) {
                        item(key = "session_chart_${activeSession.id}") {
                            SessionDetailChartCard(
                                session = activeSession,
                                samples = uiState.selectedLogSamples,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    items(sessions, key = { it.id }) { session ->
                        SessionSummaryCard(
                            session = session,
                            isSelected = session.id == activeSession?.id,
                            onSelect = { onSelectSession(session.id) },
                            onExportCsv = { onExportCsv(session) },
                            onDelete = { onDeleteSession(session) }
                        )
                    }
                }
            }
        }

        if (showPurgeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirmDialog = false },
                title = { Text("Purge All Recorded Telemetry Logs?") },
                text = {
                    Text("This will permanently clear all recorded drive and charging sessions from the on-device database.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPurgeConfirmDialog = false
                            onPurgeAllLogs()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrackCrimson)
                    ) {
                        Text("Purge All Logs")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPurgeConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun LogsTopActionStrip(
    uiState: CockpitUiState,
    sessionCount: Int,
    onToggleRecording: () -> Unit,
    onExportFullJson: () -> Unit,
    onRequestPurgeAll: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleRecording,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecordingSession) TrackCrimson else RegenEmerald
                ),
                modifier = Modifier.testTag("logs_toggle_record_button")
            ) {
                Icon(
                    imageVector = if (uiState.isRecordingSession) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isRecordingSession) {
                        "Stop Log (${uiState.recordingSampleCount} samples)"
                    } else {
                        "Start Session Log"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExportFullJson,
                    modifier = Modifier.testTag("export_json_bundle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export JSON")
                }

                if (sessionCount > 0) {
                    OutlinedButton(
                        onClick = onRequestPurgeAll,
                        modifier = Modifier.testTag("purge_all_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear All Logs",
                            tint = TrackCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs")
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    session: TelemetrySessionEntity,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(session.startTimeMs) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(session.startTimeMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .testTag("session_card_${session.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$dateStr • ${session.sourceMode} • ${session.sampleCount} pts",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Session Log",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MAX ${"%.0f".format(session.maxSpeedKph)} km/h",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = ElectricCyan
                )
                Text(
                    text = "PEAK ${"%.1f".format(session.peakPowerKw)} kW",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = InverterAmber
                )
                Text(
                    text = "SOC ${"%.0f".format(session.minSocPct)}..${"%.0f".format(session.maxSocPct)}%",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = RegenEmerald
                )
            }

            OutlinedButton(
                onClick = onExportCsv,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export Session CSV")
            }
        }
    }
}

@Composable
private fun SessionDetailChartCard(
    session: TelemetrySessionEntity,
    samples: List<TelemetrySampleEntity>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.testTag("session_detail_chart_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "TELEMETRY SESSION TRACE ANALYSIS",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    if (samples.size >= 2) {
                        val speedPath = Path()
                        val powerPath = Path()
                        val tempPath = Path()

                        val minKw = min(-80f, samples.minOf { it.netPowerKw })
                        val maxKw = max(260f, samples.maxOf { it.netPowerKw })

                        samples.forEachIndexed { idx, s ->
                            val x = (idx.toFloat() / max(1, samples.lastIndex)) * size.width
                            val ySpeed = size.height - ((s.speedKph / 220f).coerceIn(0f, 1f) * size.height)
                            val yPower = size.height - (((s.netPowerKw - minKw) / (maxKw - minKw)).coerceIn(0f, 1f) * size.height)
                            val yTemp = size.height - (((s.statorTempC - 20f) / 140f).coerceIn(0f, 1f) * size.height)

                            if (idx == 0) {
                                speedPath.moveTo(x, ySpeed)
                                powerPath.moveTo(x, yPower)
                                tempPath.moveTo(x, yTemp)
                            } else {
                                speedPath.lineTo(x, ySpeed)
                                powerPath.lineTo(x, yPower)
                                tempPath.lineTo(x, yTemp)
                            }
                        }

                        drawPath(speedPath, color = ElectricCyan, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(powerPath, color = InverterAmber, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(tempPath, color = TrackCrimson, style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LegendStat("SPEED (CYAN)", "${"%.1f".format(session.maxSpeedKph)} km/h", ElectricCyan)
                LegendStat("POWER (AMBER)", "${"%.1f".format(session.peakPowerKw)} kW", InverterAmber)
                LegendStat("STATOR (RED)", "${"%.1f".format(session.maxStatorTempC)}°C", TrackCrimson)
                LegendStat("CELL DELTA", "${"%.1f".format(session.maxCellDeltaMv)} mV", RegenEmerald)
            }
        }
    }
}

@Composable
private fun LegendStat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Text(
            text = value,
            fontFamily = OrbitronDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
