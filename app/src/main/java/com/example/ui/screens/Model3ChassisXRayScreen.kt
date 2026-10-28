package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VehicleTelemetrySnapshot
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.InverterAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.OctovalveViolet
import com.example.ui.theme.OrbitronDisplayFamily
import com.example.ui.theme.RegenEmerald
import com.example.ui.theme.TrackCrimson

@Composable
fun Model3ChassisXRayScreen(
    snapshot: VehicleTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "energy_flow")
    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_phase"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChassisBlueprintCard(
                    snapshot = snapshot,
                    flowPhase = flowPhase,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    XRaySubsystemCards(snapshot = snapshot)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChassisBlueprintCard(
                    snapshot = snapshot,
                    flowPhase = flowPhase,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                )
                XRaySubsystemCards(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun ChassisBlueprintCard(
    snapshot: VehicleTelemetrySnapshot,
    flowPhase: Float,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val isRegenOrCharging = snapshot.netPowerKw < -0.5f || snapshot.chargePowerKw > 0.5f
    val hvFlowColor = if (isRegenOrCharging) RegenEmerald else ElectricCyan

    Card(
        modifier = modifier.testTag("chassis_xray_canvas_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, outlineColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "2023 MODEL 3 POWERTRAIN & OCTOVALVE X-RAY",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = snapshot.octovalveModeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = OctovalveViolet
                    )
                }
                Surface(
                    color = hvFlowColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (snapshot.chargePowerKw > 0.5f) "SUPERCHARGING ${"%.0f".format(snapshot.chargePowerKw)} kW"
                        else if (snapshot.netPowerKw < -1f) "REGEN ${"%.1f".format(snapshot.netPowerKw)} kW"
                        else "DISCHARGE ${"%+.1f".format(snapshot.netPowerKw)} kW",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = hvFlowColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val carW = (size.width * 0.56f).coerceAtMost(290.dp.toPx())
                    val carH = (size.height * 0.90f).coerceAtMost(420.dp.toPx())
                    val carLeft = cx - carW / 2f
                    val carTop = (size.height - carH) / 2f

                    // Outer Chassis Silhouette
                    drawRoundRect(
                        color = outlineColor.copy(alpha = 0.5f),
                        topLeft = Offset(carLeft, carTop),
                        size = Size(carW, carH),
                        cornerRadius = CornerRadius(44.dp.toPx(), 44.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 4 Wheels (FL, FR, RL, RR)
                    val wheelW = carW * 0.13f
                    val wheelH = carH * 0.16f
                    val frontAxleY = carTop + carH * 0.20f
                    val rearAxleY = carTop + carH * 0.78f
                    val wheelOffsets = listOf(
                        Offset(carLeft - wheelW * 0.65f, frontAxleY - wheelH / 2f),
                        Offset(carLeft + carW - wheelW * 0.35f, frontAxleY - wheelH / 2f),
                        Offset(carLeft - wheelW * 0.65f, rearAxleY - wheelH / 2f),
                        Offset(carLeft + carW - wheelW * 0.35f, rearAxleY - wheelH / 2f)
                    )
                    wheelOffsets.forEach { topLeft ->
                        drawRoundRect(
                            color = ElectricCyan.copy(alpha = 0.75f),
                            topLeft = topLeft,
                            size = Size(wheelW, wheelH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // HV 96-Group Battery Pack Floor
                    val packLeft = carLeft + carW * 0.16f
                    val packTop = carTop + carH * 0.31f
                    val packW = carW * 0.68f
                    val packH = carH * 0.38f
                    drawRoundRect(
                        color = RegenEmerald.copy(alpha = 0.14f),
                        topLeft = Offset(packLeft, packTop),
                        size = Size(packW, packH),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                    drawRoundRect(
                        color = RegenEmerald,
                        topLeft = Offset(packLeft, packTop),
                        size = Size(packW, packH),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                        style = Stroke(width = 1.8.dp.toPx())
                    )

                    // 4 Internal Module Divisions inside Battery Pack
                    for (m in 1..3) {
                        val modY = packTop + (packH / 4f) * m
                        drawLine(
                            color = RegenEmerald.copy(alpha = 0.45f),
                            start = Offset(packLeft + 6.dp.toPx(), modY),
                            end = Offset(packLeft + packW - 6.dp.toPx(), modY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Octovalve & Supermanifold Node (Front-Left of Pack)
                    val octoCenter = Offset(cx, carTop + carH * 0.11f)
                    drawCircle(
                        color = OctovalveViolet.copy(alpha = 0.25f),
                        radius = 16.dp.toPx(),
                        center = octoCenter
                    )
                    drawCircle(
                        color = OctovalveViolet,
                        radius = 10.dp.toPx(),
                        center = octoCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Front Induction Drive Unit
                    val frontDuSize = Size(carW * 0.38f, carH * 0.08f)
                    drawRoundRect(
                        color = ElectricCyan.copy(alpha = 0.25f),
                        topLeft = Offset(cx - frontDuSize.width / 2f, frontAxleY - frontDuSize.height / 2f),
                        size = frontDuSize,
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Rear SiC Permanent Magnet Drive Unit
                    val rearDuColor = if (snapshot.statorTempC >= 120f) TrackCrimson else InverterAmber
                    val rearDuSize = Size(carW * 0.44f, carH * 0.09f)
                    drawRoundRect(
                        color = rearDuColor.copy(alpha = 0.3f),
                        topLeft = Offset(cx - rearDuSize.width / 2f, rearAxleY - rearDuSize.height / 2f),
                        size = rearDuSize,
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )

                    // Animated Coolant Loop Line (Octovalve -> Pack -> Rear DU)
                    val dashEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(12f, 10f),
                        phase = -flowPhase * 44f
                    )
                    drawLine(
                        color = OctovalveViolet,
                        start = octoCenter,
                        end = Offset(cx - carW * 0.14f, rearAxleY),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = dashEffect
                    )

                    // Animated High-Voltage Bus Line (Pack -> Front & Rear Inverters)
                    val hvDashEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(14f, 10f),
                        phase = if (isRegenOrCharging) flowPhase * 48f else -flowPhase * 48f
                    )
                    drawLine(
                        color = hvFlowColor,
                        start = Offset(cx + carW * 0.12f, frontAxleY),
                        end = Offset(cx + carW * 0.12f, rearAxleY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = hvDashEffect
                    )
                }

                // Corner Wheel Speed & Subsystem Overlay Labels
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OverlayTelemetryPill(
                            label = "FL WHEEL",
                            value = "${"%.0f".format(snapshot.flWheelKph)} km/h",
                            accent = ElectricCyan
                        )
                        OverlayTelemetryPill(
                            label = "FRONT DU",
                            value = "${"%.1f".format(snapshot.frontMotorKw)} kW",
                            accent = ElectricCyan
                        )
                        OverlayTelemetryPill(
                            label = "FR WHEEL",
                            value = "${"%.0f".format(snapshot.frWheelKph)} km/h",
                            accent = ElectricCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OverlayTelemetryPill(
                            label = "HV PACK",
                            value = "${"%.1f".format(snapshot.usableSocPct)}% (${"%.0f".format(snapshot.packVoltageV)}V)",
                            accent = RegenEmerald
                        )
                        OverlayTelemetryPill(
                            label = "OCTOVALVE",
                            value = "STEP ${snapshot.octovalvePosition} (${"%.1f".format(snapshot.batteryCoolantLpm)} L/m)",
                            accent = OctovalveViolet
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OverlayTelemetryPill(
                            label = "RL WHEEL",
                            value = "${"%.0f".format(snapshot.rlWheelKph)} km/h",
                            accent = ElectricCyan
                        )
                        OverlayTelemetryPill(
                            label = "REAR SiC DU",
                            value = "${"%.1f".format(snapshot.rearMotorKw)} kW • ${"%.0f".format(snapshot.statorTempC)}°C",
                            accent = InverterAmber
                        )
                        OverlayTelemetryPill(
                            label = "RR WHEEL",
                            value = "${"%.0f".format(snapshot.rrWheelKph)} km/h",
                            accent = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTelemetryPill(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = accent
            )
        }
    }
}

@Composable
private fun XRaySubsystemCards(snapshot: VehicleTelemetrySnapshot) {
    // 1. Octovalve & Thermal Loop Breakdown
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "OCTOVALVE & SUPERMANIFOLD THERMAL LOOP",
                style = MaterialTheme.typography.titleSmall,
                color = OctovalveViolet
            )
            Text(
                text = snapshot.octovalveModeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniColumn("Batt Coolant", "${"%.1f".format(snapshot.coolantBatteryInletC)}°C", OctovalveViolet)
                MetricMiniColumn("PT Coolant", "${"%.1f".format(snapshot.coolantPowertrainInletC)}°C", InverterAmber)
                MetricMiniColumn("Batt Flow", "${"%.1f".format(snapshot.batteryCoolantLpm)} L/m", ElectricCyan)
                MetricMiniColumn("Heat Pump", "${"%.2f".format(snapshot.hvacPowerKw)} kW", RegenEmerald)
            }
        }
    }

    // 2. Dual Motor Inverter & Stator Telemetry
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "FRONT INDUCTION & REAR SiC DRIVE UNITS",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricCyan
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniColumn("Rear Torque", "${"%.0f".format(snapshot.rearTorqueNm)} Nm", ElectricCyan)
                MetricMiniColumn("Front Torque", "${"%.0f".format(snapshot.frontTorqueNm)} Nm", ElectricCyan)
                MetricMiniColumn("R-Inverter", "${"%.1f".format(snapshot.rearInverterTempC)}°C", InverterAmber)
                MetricMiniColumn("Rear Stator", "${"%.1f".format(snapshot.statorTempC)}°C", TrackCrimson)
            }
        }
    }

    // 3. High Voltage Battery Pack & Degradation Health
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "96-GROUP HV BATTERY PACK & CELL BALANCE",
                style = MaterialTheme.typography.titleSmall,
                color = RegenEmerald
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniColumn("Full Cap", "${"%.1f".format(snapshot.nominalFullPackKwh)} kWh", RegenEmerald)
                MetricMiniColumn("New Cap", "${"%.1f".format(snapshot.initialPackKwh)} kWh", MaterialTheme.colorScheme.onSurface)
                MetricMiniColumn("Degradation", "${"%.2f".format(snapshot.degradationPct)}%", InverterAmber)
                MetricMiniColumn("Cell Delta", "${"%.1f".format(snapshot.cellDeltaMv)} mV", ElectricCyan)
            }
        }
    }
}

@Composable
private fun MetricMiniColumn(
    label: String,
    value: String,
    valueColor: Color
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
            fontSize = 14.sp,
            color = valueColor
        )
    }
}
