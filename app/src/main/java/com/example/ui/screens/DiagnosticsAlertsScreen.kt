package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CockpitJsonSerializer
import com.example.data.local.VehicleAlertEntity
import com.example.model.AlertSeverity
import com.example.model.TeslaSubsystem
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

@Composable
fun DiagnosticsAlertsScreen(
    uiState: CockpitUiState,
    alerts: List<VehicleAlertEntity>,
    onSelectSubsystem: (TeslaSubsystem) -> Unit,
    onToggleActiveOnly: () -> Unit,
    onSelectAlert: (Long?) -> Unit,
    onInterrogateBus: () -> Unit,
    onClearAndAcknowledge: () -> Unit,
    onAcknowledgeSingle: (Long) -> Unit,
    onPurgeHistorical: () -> Unit,
    onExportDiagnosticJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredAlerts = remember(alerts, uiState.selectedSubsystemFilter, uiState.showActiveAlertsOnly) {
        alerts.filter { alert ->
            val matchesSubsystem = uiState.selectedSubsystemFilter == TeslaSubsystem.ALL ||
                alert.subsystem == uiState.selectedSubsystemFilter.name
            val matchesActive = !uiState.showActiveAlertsOnly || alert.isActive
            matchesSubsystem && matchesActive
        }
    }

    val selectedAlert = remember(filteredAlerts, uiState.selectedAlertId) {
        filteredAlerts.find { it.id == uiState.selectedAlertId } ?: filteredAlerts.firstOrNull()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCanonicalListDetail = maxWidth >= 680.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Interrogation & Subsystem Filter Bar
            DiagnosticsToolbar(
                uiState = uiState,
                activeCount = alerts.count { it.isActive },
                totalCount = alerts.size,
                onSelectSubsystem = onSelectSubsystem,
                onToggleActiveOnly = onToggleActiveOnly,
                onInterrogateBus = onInterrogateBus,
                onClearAndAcknowledge = onClearAndAcknowledge,
                onPurgeHistorical = onPurgeHistorical,
                onExportDiagnosticJson = onExportDiagnosticJson
            )

            if (isCanonicalListDetail) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AlertsListColumn(
                        alerts = filteredAlerts,
                        selectedAlertId = selectedAlert?.id,
                        onSelectAlert = onSelectAlert,
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight()
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight()
                    ) {
                        if (selectedAlert != null) {
                            AlertFreezeFrameDetailCard(
                                alert = selectedAlert,
                                onAcknowledge = { onAcknowledgeSingle(selectedAlert.id) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            EmptyDiagnosticsState()
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("alerts_list_compact"),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedAlert != null) {
                        item {
                            AlertFreezeFrameDetailCard(
                                alert = selectedAlert,
                                onAcknowledge = { onAcknowledgeSingle(selectedAlert.id) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    items(filteredAlerts, key = { it.id }) { alert ->
                        AlertSummaryItemCard(
                            alert = alert,
                            isSelected = alert.id == selectedAlert?.id,
                            onClick = { onSelectAlert(alert.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsToolbar(
    uiState: CockpitUiState,
    activeCount: Int,
    totalCount: Int,
    onSelectSubsystem: (TeslaSubsystem) -> Unit,
    onToggleActiveOnly: () -> Unit,
    onInterrogateBus: () -> Unit,
    onClearAndAcknowledge: () -> Unit,
    onPurgeHistorical: () -> Unit,
    onExportDiagnosticJson: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Action Row: Interrogate Bus, Clear Faults, Export JSON
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onInterrogateBus,
                    enabled = !uiState.isInterrogatingBus,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.testTag("interrogate_bus_button")
                ) {
                    if (uiState.isInterrogatingBus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Interrogate CAN Bus", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onClearAndAcknowledge,
                    colors = ButtonDefaults.buttonColors(containerColor = RegenEmerald),
                    modifier = Modifier.testTag("clear_faults_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear & Acknowledge ($activeCount)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onPurgeHistorical,
                    modifier = Modifier.testTag("purge_historical_alerts_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Purge History")
                }

                OutlinedButton(onClick = onExportDiagnosticJson) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export Report")
                }
            }

            // Subsystem Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.showActiveAlertsOnly,
                    onClick = onToggleActiveOnly,
                    label = { Text("Active Only ($activeCount/$totalCount)") }
                )

                TeslaSubsystem.entries.forEach { sub ->
                    FilterChip(
                        selected = uiState.selectedSubsystemFilter == sub,
                        onClick = { onSelectSubsystem(sub) },
                        label = { Text(sub.shortCode) },
                        modifier = Modifier.testTag("subsystem_filter_${sub.shortCode.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertsListColumn(
    alerts: List<VehicleAlertEntity>,
    selectedAlertId: Long?,
    onSelectAlert: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("alerts_list_column"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(alerts, key = { it.id }) { alert ->
            AlertSummaryItemCard(
                alert = alert,
                isSelected = alert.id == selectedAlertId,
                onClick = { onSelectAlert(alert.id) }
            )
        }
    }
}

@Composable
private fun AlertSummaryItemCard(
    alert: VehicleAlertEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val sevColor = when (alert.severity) {
        AlertSeverity.CRITICAL.name -> TrackCrimson
        AlertSeverity.WARNING.name -> InverterAmber
        else -> ElectricCyan
    }
    val timeStr = remember(alert.timestampMs) {
        SimpleDateFormat("MMM dd HH:mm:ss", Locale.US).format(Date(alert.timestampMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("alert_card_${alert.alertCode}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) sevColor else MaterialTheme.colorScheme.outline
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (alert.severity) {
                            AlertSeverity.CRITICAL.name -> Icons.Default.Error
                            AlertSeverity.WARNING.name -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                        contentDescription = alert.severity,
                        tint = sevColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = alert.alertCode,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = sevColor
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = alert.subsystem,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = if (alert.isActive) TrackCrimson.copy(alpha = 0.2f)
                    else RegenEmerald.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (alert.isActive) "ACTIVE FAULT" else "HISTORICAL / CLEARED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alert.isActive) TrackCrimson else RegenEmerald,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = alert.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CAN ${alert.rawCanIdHex} [${alert.rawPayloadHex}]",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = ElectricCyan
                )
                Text(
                    text = timeStr,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AlertFreezeFrameDetailCard(
    alert: VehicleAlertEntity,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val freeze = remember(alert.freezeFrameJson) {
        CockpitJsonSerializer.decodeFreezeFrame(alert.freezeFrameJson)
    }
    val sevColor = when (alert.severity) {
        AlertSeverity.CRITICAL.name -> TrackCrimson
        AlertSeverity.WARNING.name -> InverterAmber
        else -> ElectricCyan
    }

    Card(
        modifier = modifier.testTag("freeze_frame_inspector_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, sevColor.copy(alpha = 0.75f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FREEZE-FRAME TELEMETRY SNAPSHOT",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = "${alert.alertCode} — ${alert.title}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!alert.isAcknowledged) {
                    OutlinedButton(onClick = onAcknowledge) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Acknowledge")
                    }
                }
            }

            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "RECOMMENDED SERVICE / ENGINEERING ACTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = RegenEmerald
                    )
                    Text(
                        text = alert.recommendedAction,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Raw CAN Frame Hex Inspector
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RAW CAN ID: ${alert.rawCanIdHex}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElectricCyan
                    )
                    Text(
                        text = "DATA: ${alert.rawPayloadHex}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Captured Freeze-Frame Metrics Grid
            Text(
                text = "VEHICLE TELEMETRY AT FAULT TRIGGER",
                style = MaterialTheme.typography.labelMedium,
                color = InverterAmber
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FreezeMetricBox("Speed / Gear", "${"%.0f".format(freeze.speedKph)} km/h (${freeze.gear})", ElectricCyan)
                FreezeMetricBox("Net Power", "${"%.1f".format(freeze.netPowerKw)} kW", InverterAmber)
                FreezeMetricBox("HV Bus V / A", "${"%.1f".format(freeze.packVoltageV)}V / ${"%.0f".format(freeze.packCurrentA)}A", RegenEmerald)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FreezeMetricBox("Cell Min / Max", "${"%.3f".format(freeze.cellMinV)}V / ${"%.3f".format(freeze.cellMaxV)}V", RegenEmerald)
                FreezeMetricBox("Cell Delta", "${"%.1f".format(freeze.cellDeltaMv)} mV", if (freeze.cellDeltaMv >= 25f) TrackCrimson else ElectricCyan)
                FreezeMetricBox("Usable SOC", "${"%.1f".format(freeze.usableSocPct)}%", ElectricCyan)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FreezeMetricBox("Stator Temp", "${"%.1f".format(freeze.statorTempC)}°C", TrackCrimson)
                FreezeMetricBox("R-Inverter", "${"%.1f".format(freeze.rearInverterTempC)}°C", InverterAmber)
                FreezeMetricBox("Octovalve", "Step ${freeze.octovalvePosition} (${"%.1f".format(freeze.coolantBatteryInletC)}°C)", ElectricCyan)
            }
        }
    }
}

@Composable
private fun FreezeMetricBox(
    label: String,
    value: String,
    accent: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontFamily = OrbitronDisplayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = accent
        )
    }
}

@Composable
private fun EmptyDiagnosticsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No matching alerts in this subsystem filter. Tap 'Interrogate CAN Bus' to scan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
