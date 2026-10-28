package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.local.CockpitJsonSerializer
import com.example.data.local.DashboardPresetEntity
import com.example.model.CanFramePacket
import com.example.model.MetricPid
import com.example.model.WidgetSpan
import com.example.model.WidgetStyle
import com.example.ui.components.TelemetryWidgetCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RegenEmerald
import com.example.ui.viewmodel.CockpitUiState
import kotlin.math.min

@Composable
fun CockpitDashboardScreen(
    uiState: CockpitUiState,
    dashboards: List<DashboardPresetEntity>,
    onSelectDashboard: (Long) -> Unit,
    onToggleEditLayout: () -> Unit,
    onOpenAddWidgetDialog: () -> Unit,
    onCloseAddWidgetDialog: () -> Unit,
    onAddWidget: (MetricPid, WidgetStyle, WidgetSpan) -> Unit,
    onOpenNewDashboardDialog: () -> Unit,
    onCloseNewDashboardDialog: () -> Unit,
    onCreateCustomDashboard: (String, String) -> Unit,
    onDeleteCustomDashboard: (DashboardPresetEntity) -> Unit,
    onMoveWidgetOrder: (String, Boolean) -> Unit,
    onCycleWidgetSpan: (String) -> Unit,
    onCycleWidgetStyle: (String) -> Unit,
    onRemoveWidget: (String) -> Unit,
    onResetPeakHold: () -> Unit,
    onResetFactoryDashboards: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDashboard = dashboards.find { it.id == uiState.selectedDashboardId }
        ?: dashboards.firstOrNull()
    val activeWidgets = remember(activeDashboard?.widgetsJson) {
        activeDashboard?.let { CockpitJsonSerializer.decodeWidgets(it.widgetsJson) } ?: emptyList()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val isTabletWide = availableWidth >= 840.dp
        val gridColumns = when {
            availableWidth >= 900.dp -> 4
            availableWidth >= 600.dp -> 4
            else -> 2
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Dashboard Selector & Customization Toolbar
            DashboardPresetSelectorBar(
                dashboards = dashboards,
                selectedId = activeDashboard?.id ?: 1L,
                isEditMode = uiState.isEditLayoutMode,
                onSelectDashboard = onSelectDashboard,
                onToggleEditLayout = onToggleEditLayout,
                onOpenNewDashboard = onOpenNewDashboardDialog,
                onOpenAddWidget = onOpenAddWidgetDialog,
                onDeleteCustomDashboard = {
                    if (activeDashboard != null && !activeDashboard.isBuiltIn) {
                        onDeleteCustomDashboard(activeDashboard)
                    }
                },
                onResetPeakHold = onResetPeakHold,
                onResetFactory = onResetFactoryDashboards
            )

            // Main Adaptive Content Area (On wide tablets, show side-by-side Live CAN Frame Stream)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier
                        .weight(if (isTabletWide) 0.72f else 1f)
                        .fillMaxHeight()
                        .testTag("cockpit_gauge_grid"),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = activeWidgets,
                        key = { it.id },
                        span = { widget ->
                            val spanCols = min(gridColumns, widget.span.columns)
                            GridItemSpan(spanCols)
                        }
                    ) { widget ->
                        val cardHeight = when (widget.span) {
                            WidgetSpan.LARGE_2X2 -> if (uiState.isEditLayoutMode) 225.dp else 188.dp
                            WidgetSpan.WIDE_2X1 -> if (uiState.isEditLayoutMode) 175.dp else 138.dp
                            WidgetSpan.COMPACT_1X1 -> if (uiState.isEditLayoutMode) 168.dp else 132.dp
                        }
                        TelemetryWidgetCard(
                            widget = widget,
                            snapshot = uiState.snapshot,
                            sparklineHistory = uiState.pidSparklineHistory[widget.pid] ?: emptyList(),
                            peakValue = uiState.peakHoldValues[widget.pid],
                            isEditMode = uiState.isEditLayoutMode,
                            onMoveOrder = { moveUp -> onMoveWidgetOrder(widget.id, moveUp) },
                            onCycleSpan = { onCycleWidgetSpan(widget.id) },
                            onCycleStyle = { onCycleWidgetStyle(widget.id) },
                            onRemove = { onRemoveWidget(widget.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
                        )
                    }

                    // Compact CAN Frame Ticker at bottom of grid when not on wide tablet
                    if (!isTabletWide) {
                        item(span = { GridItemSpan(gridColumns) }) {
                            CompactCanTickerCard(
                                frames = uiState.snapshot.recentCanFrames.take(4)
                            )
                        }
                    }
                }

                // Tablet / Wide Landscape Supporting Pane: Live CAN-Bus Frame Inspector
                if (isTabletWide) {
                    LiveCanBusSidePane(
                        frames = uiState.snapshot.recentCanFrames,
                        fps = uiState.busFrameRateFps,
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight()
                            .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                    )
                }
            }
        }

        if (uiState.showAddWidgetDialog) {
            AddTelemetryWidgetDialog(
                onDismiss = onCloseAddWidgetDialog,
                onConfirm = onAddWidget
            )
        }

        if (uiState.showNewDashboardDialog) {
            CreateCustomDashboardDialog(
                onDismiss = onCloseNewDashboardDialog,
                onConfirm = onCreateCustomDashboard
            )
        }
    }
}

@Composable
private fun DashboardPresetSelectorBar(
    dashboards: List<DashboardPresetEntity>,
    selectedId: Long,
    isEditMode: Boolean,
    onSelectDashboard: (Long) -> Unit,
    onToggleEditLayout: () -> Unit,
    onOpenNewDashboard: () -> Unit,
    onOpenAddWidget: () -> Unit,
    onDeleteCustomDashboard: () -> Unit,
    onResetPeakHold: () -> Unit,
    onResetFactory: () -> Unit
) {
    val activeDash = dashboards.find { it.id == selectedId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Scrollable Preset Pills
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dashboards.forEach { dash ->
                    val selected = dash.id == selectedId
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectDashboard(dash.id) }
                            .testTag("dashboard_pill_${dash.id}"),
                        color = if (selected) ElectricCyan.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) ElectricCyan else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = dash.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }

                // + New Preset Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenNewDashboard)
                        .testTag("new_dashboard_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Custom Dashboard",
                            tint = RegenEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "New Preset",
                            style = MaterialTheme.typography.labelLarge,
                            color = RegenEmerald
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Actions: Reset Peaks & Customize Layout Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onResetPeakHold,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("reset_peaks_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Peak-Hold Markers",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onToggleEditLayout)
                        .testTag("customize_layout_button"),
                    color = if (isEditMode) RegenEmerald else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Customize Dashboard Layout",
                            tint = if (isEditMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isEditMode) "Done" else "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isEditMode) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Edit Mode Action Bar
        if (isEditMode) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onOpenAddWidget,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.testTag("add_widget_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add PID Gauge", fontWeight = FontWeight.Bold)
                        }

                        if (activeDash != null && !activeDash.isBuiltIn) {
                            OutlinedButton(
                                onClick = onDeleteCustomDashboard,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Custom Dashboard",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete Preset")
                            }
                        }
                    }

                    TextButton(onClick = onResetFactory) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Factory Defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCanTickerCard(frames: List<CanFramePacket>) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE S3XY_OBD CAN-BUS STREAM (500 KBPS)",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Text(
                    text = "11-BIT STD",
                    style = MaterialTheme.typography.labelSmall,
                    color = RegenEmerald
                )
            }
            frames.forEach { frame ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${frame.canIdHex} [${frame.payloadHex}]",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = frame.decodedSummary,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveCanBusSidePane(
    frames: List<CanFramePacket>,
    fps: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CAN FRAME MONITOR",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "$fps fps",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = RegenEmerald
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(frames) { frame ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${frame.canIdHex} (Bus ${frame.busId})",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = frame.payloadHex,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = frame.decodedSummary,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTelemetryWidgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (MetricPid, WidgetStyle, WidgetSpan) -> Unit
) {
    var selectedPid by remember { mutableStateOf(MetricPid.SPEED_KPH) }
    var selectedStyle by remember { mutableStateOf(MetricPid.SPEED_KPH.defaultStyle) }
    var selectedSpan by remember { mutableStateOf(MetricPid.SPEED_KPH.defaultSpan) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add 2023 Model 3 Telemetry Gauge") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select CAN Signal (PID):",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(MetricPid.entries) { pid ->
                        val isSelected = pid == selectedPid
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPid = pid
                                    selectedStyle = pid.defaultStyle
                                    selectedSpan = pid.defaultSpan
                                },
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pid.title,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${pid.subsystem.label} • ${pid.unit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = pid.shortLabel,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Visualization Style:",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WidgetStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style.label) }
                        )
                    }
                }

                Text(
                    text = "Tile Size / Span:",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WidgetSpan.entries.forEach { span ->
                        FilterChip(
                            selected = selectedSpan == span,
                            onClick = { selectedSpan = span },
                            label = { Text(span.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPid, selectedStyle, selectedSpan) }) {
                Text("Add to Cockpit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateCustomDashboardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Custom Dashboard Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Dashboard Title (e.g., Track Day Attack)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Notes / Focus Subsystems") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, subtitle) }) {
                Text("Create Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
