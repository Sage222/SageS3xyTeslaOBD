package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DashboardWidgetConfig
import com.example.model.MetricPid
import com.example.model.TeslaSubsystem
import com.example.model.VehicleTelemetrySnapshot
import com.example.model.WidgetSpan
import com.example.model.WidgetStyle
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.InverterAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctovalveViolet
import com.example.ui.theme.OrbitronDisplayFamily
import com.example.ui.theme.RegenEmerald
import com.example.ui.theme.TrackCrimson
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun subsystemAccentColor(subsystem: TeslaSubsystem): Color = when (subsystem) {
    TeslaSubsystem.DI_INVERTER -> ElectricCyan
    TeslaSubsystem.BMS -> RegenEmerald
    TeslaSubsystem.VCFRONT_THERMAL -> OctovalveViolet
    TeslaSubsystem.HVP_CHARGING -> InverterAmber
    TeslaSubsystem.ESP_DYNAMICS -> ElectricCyan
    TeslaSubsystem.ALL -> ElectricCyan
}

@Composable
fun TelemetryWidgetCard(
    widget: DashboardWidgetConfig,
    snapshot: VehicleTelemetrySnapshot,
    sparklineHistory: List<Float>,
    peakValue: Float?,
    isEditMode: Boolean,
    onMoveOrder: (Boolean) -> Unit,
    onCycleSpan: () -> Unit,
    onCycleStyle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pid = widget.pid
    val rawValue = snapshot.getValue(pid)
    val animatedValue by animateFloatAsState(
        targetValue = rawValue,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "gauge_${widget.id}"
    )
    val isWarning = snapshot.isWarningActive(pid, widget.warningThreshold)
    val baseAccent = if (isWarning) TrackCrimson else subsystemAccentColor(pid.subsystem)
    val borderColor = if (isWarning) {
        TrackCrimson.copy(alpha = 0.85f)
    } else if (isEditMode) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }

    Card(
        modifier = modifier.testTag("widget_card_${pid.name.lowercase()}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (isWarning || isEditMode) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(baseAccent)
                    )
                    Text(
                        text = pid.shortLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isWarning) {
                    Surface(
                        color = TrackCrimson.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Threshold warning",
                                tint = TrackCrimson,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "LIMIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = TrackCrimson
                            )
                        }
                    }
                } else {
                    Text(
                        text = pid.subsystem.shortCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = baseAccent.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body Content based on WidgetStyle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (widget.style) {
                    WidgetStyle.RADIAL_GAUGE -> RadialSweepGaugeBody(
                        pid = pid,
                        animatedValue = animatedValue,
                        formattedText = snapshot.formatValue(pid),
                        peakValue = peakValue,
                        warningThreshold = widget.warningThreshold,
                        accentColor = baseAccent
                    )

                    WidgetStyle.BI_DIRECTIONAL_ARC -> BiDirectionalArcBody(
                        pid = pid,
                        animatedValue = animatedValue,
                        formattedText = snapshot.formatValue(pid),
                        peakValue = peakValue
                    )

                    WidgetStyle.SPARKLINE_CARD -> SparklineGaugeBody(
                        pid = pid,
                        formattedText = snapshot.formatValue(pid),
                        history = sparklineHistory,
                        peakValue = peakValue,
                        accentColor = baseAccent
                    )

                    WidgetStyle.HORIZONTAL_BAR -> HorizontalBarGaugeBody(
                        pid = pid,
                        animatedValue = animatedValue,
                        formattedText = snapshot.formatValue(pid),
                        warningThreshold = widget.warningThreshold,
                        accentColor = baseAccent
                    )

                    WidgetStyle.DIGITAL_READOUT -> DigitalReadoutBody(
                        pid = pid,
                        formattedText = snapshot.formatValue(pid),
                        peakValue = peakValue,
                        accentColor = baseAccent
                    )

                    WidgetStyle.G_FORCE_CIRCLE -> GForceFrictionCircleBody(
                        lateralG = snapshot.lateralG,
                        longitudinalG = snapshot.longitudinalG,
                        trail = snapshot.gForceTrail
                    )

                    WidgetStyle.CELL_BRICK_HISTOGRAM -> CellBrickHistogramBody(
                        cellMinV = snapshot.cellMinV,
                        cellMaxV = snapshot.cellMaxV,
                        cellDeltaMv = snapshot.cellDeltaMv,
                        brickVoltages = snapshot.cellBrickVoltages
                    )
                }
            }

            // Edit Layout Controls Strip
            if (isEditMode) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onMoveOrder(true) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Move widget earlier",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveOrder(false) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Move widget later",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onCycleSpan,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Resize widget span (${widget.span.label})",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onCycleStyle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Cycle gauge style (${widget.style.label})",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove widget",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadialSweepGaugeBody(
    pid: MetricPid,
    animatedValue: Float,
    formattedText: String,
    peakValue: Float?,
    warningThreshold: Float,
    accentColor: Color
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height) * 0.88f
            val strokeWidth = (diameter * 0.095f).coerceIn(8f, 22f)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f + diameter * 0.04f
            )
            val arcSize = Size(diameter, diameter)
            val startAngle = 135f
            val totalSweep = 270f

            // Background Track Arc
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Redline Zone Arc
            val range = max(1f, pid.maxVal - pid.minVal)
            val warnFraction = ((warningThreshold - pid.minVal) / range).coerceIn(0f, 1f)
            if (warnFraction in 0.5f..0.98f) {
                val warnStart = startAngle + totalSweep * warnFraction
                val warnSweep = totalSweep * (1f - warnFraction)
                drawArc(
                    color = TrackCrimson.copy(alpha = 0.35f),
                    startAngle = warnStart,
                    sweepAngle = warnSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
            }

            // Active Value Sweep Arc
            val fraction = ((animatedValue - pid.minVal) / range).coerceIn(0f, 1f)
            val activeSweep = totalSweep * fraction
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(accentColor.copy(alpha = 0.7f), accentColor)
                ),
                startAngle = startAngle,
                sweepAngle = activeSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Peak-Hold Tick Marker
            if (peakValue != null) {
                val peakFrac = ((peakValue - pid.minVal) / range).coerceIn(0f, 1f)
                val angleRad = (startAngle + totalSweep * peakFrac) * (PI / 180.0)
                val center = Offset(topLeft.x + diameter / 2f, topLeft.y + diameter / 2f)
                val rInner = diameter / 2f - strokeWidth * 0.75f
                val rOuter = diameter / 2f + strokeWidth * 0.75f
                drawLine(
                    color = InverterAmber,
                    start = Offset(
                        center.x + (rInner * cos(angleRad)).toFloat(),
                        center.y + (rInner * sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        center.x + (rOuter * cos(angleRad)).toFloat(),
                        center.y + (rOuter * sin(angleRad)).toFloat()
                    ),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = formattedText,
                fontFamily = OrbitronDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = onSurface
            )
            Text(
                text = pid.unit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = mutedColor
            )
            if (peakValue != null) {
                Text(
                    text = "PK ${"%.${pid.decimals}f".format(peakValue)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = InverterAmber
                )
            }
        }
    }
}

@Composable
private fun BiDirectionalArcBody(
    pid: MetricPid,
    animatedValue: Float,
    formattedText: String,
    peakValue: Float?
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val isNegative = animatedValue < 0f
    val activeColor = if (isNegative) RegenEmerald else ElectricCyan

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height) * 0.88f
            val strokeWidth = (diameter * 0.095f).coerceIn(8f, 22f)
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f + diameter * 0.04f
            )
            val arcSize = Size(diameter, diameter)
            val startAngle = 135f
            val totalSweep = 270f

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Zero center position along [minVal..maxVal]
            val range = max(1f, pid.maxVal - pid.minVal)
            val zeroFrac = ((0f - pid.minVal) / range).coerceIn(0.1f, 0.9f)
            val valFrac = ((animatedValue - pid.minVal) / range).coerceIn(0f, 1f)
            val zeroAngle = startAngle + totalSweep * zeroFrac
            val sweepAngle = (valFrac - zeroFrac) * totalSweep

            drawArc(
                color = activeColor,
                startAngle = zeroAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedText,
                fontFamily = OrbitronDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isNegative && pid == MetricPid.NET_POWER_KW) "REGEN ${pid.unit}" else pid.unit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = activeColor
            )
            if (peakValue != null) {
                Text(
                    text = "PK ${"%.${pid.decimals}f".format(peakValue)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SparklineGaugeBody(
    pid: MetricPid,
    formattedText: String,
    history: List<Float>,
    peakValue: Float?,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formattedText,
                    fontFamily = OrbitronDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pid.unit,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            if (peakValue != null) {
                Text(
                    text = "PEAK ${"%.${pid.decimals}f".format(peakValue)} ${pid.unit}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            val pts = if (history.size >= 2) history else listOf(pid.minVal, pid.minVal)
            val minV = min(pid.minVal, pts.minOrNull() ?: 0f)
            val maxV = max(pid.maxVal, pts.maxOrNull() ?: 1f)
            val span = max(0.01f, maxV - minV)

            val strokePath = Path()
            val fillPath = Path()

            pts.forEachIndexed { idx, v ->
                val x = (idx.toFloat() / max(1, pts.lastIndex)) * size.width
                val normY = ((v - minV) / span).coerceIn(0f, 1f)
                val y = size.height - (normY * (size.height * 0.85f) + size.height * 0.08f)
                if (idx == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (idx == pts.lastIndex) {
                    fillPath.lineTo(x, size.height)
                    fillPath.close()
                }
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.32f), Color.Transparent)
                )
            )
            drawPath(
                path = strokePath,
                color = accentColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun HorizontalBarGaugeBody(
    pid: MetricPid,
    animatedValue: Float,
    formattedText: String,
    warningThreshold: Float,
    accentColor: Color
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val range = max(0.01f, pid.maxVal - pid.minVal)
    val fraction = ((animatedValue - pid.minVal) / range).coerceIn(0f, 1f)
    val warnFrac = ((warningThreshold - pid.minVal) / range).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = formattedText,
                fontFamily = OrbitronDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pid.unit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
        ) {
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            drawRoundRect(
                color = accentColor,
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            val warnX = size.width * warnFrac
            drawLine(
                color = TrackCrimson,
                start = Offset(warnX, 0f),
                end = Offset(warnX, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${"%.0f".format(pid.minVal)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${"%.0f".format(pid.maxVal)} ${pid.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DigitalReadoutBody(
    pid: MetricPid,
    formattedText: String,
    peakValue: Float?,
    accentColor: Color
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = formattedText,
                fontFamily = OrbitronDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pid.unit,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = accentColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (peakValue != null) "PEAK: ${"%.${pid.decimals}f".format(peakValue)} ${pid.unit}"
            else pid.title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GForceFrictionCircleBody(
    lateralG: Float,
    longitudinalG: Float,
    trail: List<Pair<Float, Float>>
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val totalG = kotlin.math.sqrt(lateralG * lateralG + longitudinalG * longitudinalG)

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = min(size.width, size.height) * 0.44f
                val center = Offset(size.width / 2f, size.height / 2f)

                // Outer 1.0G and Inner 0.5G rings
                drawCircle(
                    color = gridColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = gridColor.copy(alpha = 0.35f),
                    radius = radius * 0.5f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
                // Crosshairs
                drawLine(
                    color = gridColor.copy(alpha = 0.4f),
                    start = Offset(center.x - radius, center.y),
                    end = Offset(center.x + radius, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.4f),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y + radius),
                    strokeWidth = 1.dp.toPx()
                )

                // Historical G-Trail
                trail.forEachIndexed { idx, (lat, lon) ->
                    val alpha = ((idx + 1).toFloat() / max(1, trail.size)) * 0.45f
                    val px = center.x + (lat / 1.25f).coerceIn(-1f, 1f) * radius
                    val py = center.y - (lon / 1.25f).coerceIn(-1f, 1f) * radius
                    drawCircle(
                        color = ElectricCyan.copy(alpha = alpha),
                        radius = 3.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Live G-Vector Puck
                val liveX = center.x + (lateralG / 1.25f).coerceIn(-1f, 1f) * radius
                val liveY = center.y - (longitudinalG / 1.25f).coerceIn(-1f, 1f) * radius
                drawLine(
                    color = ElectricCyan,
                    start = center,
                    end = Offset(liveX, liveY),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = if (totalG >= 0.95f) TrackCrimson else ElectricCyan,
                    radius = 6.dp.toPx(),
                    center = Offset(liveX, liveY)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                text = "${"%.2f".format(totalG)}G",
                fontFamily = OrbitronDisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "LAT ${"%+.2f".format(lateralG)}G",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = ElectricCyan
            )
            Text(
                text = "LON ${"%+.2f".format(longitudinalG)}G",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = RegenEmerald
            )
        }
    }
}

@Composable
private fun CellBrickHistogramBody(
    cellMinV: Float,
    cellMaxV: Float,
    cellDeltaMv: Float,
    brickVoltages: List<Float>
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "${"%.1f".format(cellDeltaMv)} mV",
                    fontFamily = OrbitronDisplayFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = if (cellDeltaMv >= 25f) TrackCrimson else RegenEmerald
                )
                Text(
                    text = "MIN ${"%.3f".format(cellMinV)}V • MAX ${"%.3f".format(cellMaxV)}V",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "12 BRICKS",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = RegenEmerald
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val count = brickVoltages.size.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            val minRef = cellMinV - 0.004f
            val maxRef = max(cellMaxV + 0.004f, minRef + 0.015f)

            brickVoltages.forEachIndexed { index, volts ->
                val norm = ((volts - minRef) / (maxRef - minRef)).coerceIn(0.18f, 1.0f)
                val barHeight = size.height * norm
                val x = index * (barWidth + gap)
                val y = size.height - barHeight
                val barColor = when {
                    abs(volts - cellMaxV) < 0.0008f -> InverterAmber
                    abs(volts - cellMinV) < 0.0008f -> ElectricCyan
                    else -> RegenEmerald.copy(alpha = 0.8f)
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
    }
}
