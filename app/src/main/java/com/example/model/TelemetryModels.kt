package com.example.model

import kotlin.math.abs

enum class TeslaSubsystem(val label: String, val shortCode: String) {
    ALL("All Subsystems", "ALL"),
    BMS("Battery Mgmt (BMS)", "BMS"),
    DI_INVERTER("Drive Inverters (DI)", "DI"),
    VCFRONT_THERMAL("Thermal & Octovalve", "VCFRONT"),
    HVP_CHARGING("High Voltage & Charge", "HVP"),
    ESP_DYNAMICS("Braking & Dynamics", "ESP")
}

enum class AlertSeverity(val label: String) {
    CRITICAL("Critical Fault"),
    WARNING("Performance Advisory"),
    INFO("Service / Info")
}

enum class WidgetStyle(val label: String) {
    RADIAL_GAUGE("Radial Sweep Dial"),
    BI_DIRECTIONAL_ARC("Zero-Center Power/Regen Arc"),
    SPARKLINE_CARD("Live Trend Sparkline"),
    HORIZONTAL_BAR("Threshold Bar Meter"),
    DIGITAL_READOUT("Precision Digital Tile"),
    G_FORCE_CIRCLE("2D G-Force Friction Plot"),
    CELL_BRICK_HISTOGRAM("Cell Brick Voltage Map")
}

enum class WidgetSpan(val label: String, val columns: Int) {
    COMPACT_1X1("Compact (1 Col)", 1),
    WIDE_2X1("Wide (2 Col)", 2),
    LARGE_2X2("Hero Dial (2 Col Tall)", 2)
}

enum class MetricPid(
    val title: String,
    val shortLabel: String,
    val unit: String,
    val minVal: Float,
    val maxVal: Float,
    val defaultWarningThreshold: Float,
    val defaultStyle: WidgetStyle,
    val defaultSpan: WidgetSpan,
    val subsystem: TeslaSubsystem,
    val decimals: Int = 0
) {
    // 1. Performance & Inverter Power
    SPEED_KPH(
        title = "Vehicle Speed",
        shortLabel = "SPEED",
        unit = "km/h",
        minVal = 0f,
        maxVal = 260f,
        defaultWarningThreshold = 180f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 0
    ),
    NET_POWER_KW(
        title = "Net Pack Power",
        shortLabel = "NET POWER",
        unit = "kW",
        minVal = -100f,
        maxVal = 380f,
        defaultWarningThreshold = 320f,
        defaultStyle = WidgetStyle.BI_DIRECTIONAL_ARC,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 1
    ),
    REAR_MOTOR_KW(
        title = "Rear Drive Unit Power",
        shortLabel = "REAR DU",
        unit = "kW",
        minVal = -70f,
        maxVal = 240f,
        defaultWarningThreshold = 210f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 1
    ),
    FRONT_MOTOR_KW(
        title = "Front Drive Unit Power",
        shortLabel = "FRONT DU",
        unit = "kW",
        minVal = -40f,
        maxVal = 150f,
        defaultWarningThreshold = 135f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 1
    ),
    REAR_TORQUE_NM(
        title = "Rear Motor Torque",
        shortLabel = "REAR TRQ",
        unit = "Nm",
        minVal = -250f,
        maxVal = 450f,
        defaultWarningThreshold = 400f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 0
    ),
    FRONT_TORQUE_NM(
        title = "Front Motor Torque",
        shortLabel = "FRONT TRQ",
        unit = "Nm",
        minVal = -150f,
        maxVal = 280f,
        defaultWarningThreshold = 240f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 0
    ),
    REAR_INVERTER_TEMP_C(
        title = "Rear Inverter MOSFET Temp",
        shortLabel = "R-INV TEMP",
        unit = "°C",
        minVal = -10f,
        maxVal = 115f,
        defaultWarningThreshold = 90f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 1
    ),
    STATOR_TEMP_C(
        title = "Rear Stator Winding Temp",
        shortLabel = "STATOR TEMP",
        unit = "°C",
        minVal = -10f,
        maxVal = 160f,
        defaultWarningThreshold = 130f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 1
    ),
    ZERO_TO_SIXTY_SEC(
        title = "0-100 km/h Sprint Timer",
        shortLabel = "0-100 KM/H",
        unit = "sec",
        minVal = 2.5f,
        maxVal = 10.0f,
        defaultWarningThreshold = 4.5f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 2
    ),
    THROTTLE_PCT(
        title = "Accelerator Pedal Map",
        shortLabel = "THROTTLE",
        unit = "%",
        minVal = 0f,
        maxVal = 100f,
        defaultWarningThreshold = 95f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.DI_INVERTER,
        decimals = 0
    ),

    // 2. Battery Pack Health, Cell Balance & Charging
    USABLE_SOC_PCT(
        title = "Usable State of Charge",
        shortLabel = "USABLE SOC",
        unit = "%",
        minVal = 0f,
        maxVal = 100f,
        defaultWarningThreshold = 15f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    BMS_SOC_PCT(
        title = "Raw BMS State of Charge",
        shortLabel = "BMS SOC",
        unit = "%",
        minVal = 0f,
        maxVal = 100f,
        defaultWarningThreshold = 12f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    CELL_DELTA_MV(
        title = "Cell Voltage Imbalance Delta",
        shortLabel = "CELL DELTA",
        unit = "mV",
        minVal = 0f,
        maxVal = 80f,
        defaultWarningThreshold = 25f,
        defaultStyle = WidgetStyle.CELL_BRICK_HISTOGRAM,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    PACK_VOLTAGE_V(
        title = "HV Pack Bus Voltage",
        shortLabel = "HV VOLTS",
        unit = "V",
        minVal = 280f,
        maxVal = 410f,
        defaultWarningThreshold = 398f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    PACK_CURRENT_A(
        title = "HV Pack Current",
        shortLabel = "HV AMPS",
        unit = "A",
        minVal = -650f,
        maxVal = 950f,
        defaultWarningThreshold = 750f,
        defaultStyle = WidgetStyle.BI_DIRECTIONAL_ARC,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    CELL_MIN_V(
        title = "Lowest Brick Cell Voltage",
        shortLabel = "CELL MIN",
        unit = "V",
        minVal = 3.00f,
        maxVal = 4.25f,
        defaultWarningThreshold = 3.30f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 3
    ),
    CELL_MAX_V(
        title = "Highest Brick Cell Voltage",
        shortLabel = "CELL MAX",
        unit = "V",
        minVal = 3.00f,
        maxVal = 4.25f,
        defaultWarningThreshold = 4.18f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 3
    ),
    NOMINAL_FULL_PACK_KWH(
        title = "Nominal Full Pack Capacity",
        shortLabel = "FULL CAP",
        unit = "kWh",
        minVal = 50f,
        maxVal = 82f,
        defaultWarningThreshold = 68f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 1
    ),
    DEGRADATION_PCT(
        title = "Estimated Pack Degradation",
        shortLabel = "DEGRADATION",
        unit = "%",
        minVal = 0f,
        maxVal = 20f,
        defaultWarningThreshold = 10f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.BMS,
        decimals = 2
    ),
    CHARGE_POWER_KW(
        title = "DC / AC Fast Charge Power",
        shortLabel = "CHARGE PWR",
        unit = "kW",
        minVal = 0f,
        maxVal = 260f,
        defaultWarningThreshold = 240f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.HVP_CHARGING,
        decimals = 1
    ),

    // 3. Thermal Loop & HVAC Status
    COOLANT_BATTERY_INLET_C(
        title = "Battery Coolant Inlet Temp",
        shortLabel = "BATT INLET",
        unit = "°C",
        minVal = -15f,
        maxVal = 65f,
        defaultWarningThreshold = 50f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    COOLANT_POWERTRAIN_INLET_C(
        title = "Powertrain Coolant Temp",
        shortLabel = "PT COOLANT",
        unit = "°C",
        minVal = -15f,
        maxVal = 85f,
        defaultWarningThreshold = 68f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    BATTERY_COOLANT_LPM(
        title = "Battery Loop Flow Rate",
        shortLabel = "BATT FLOW",
        unit = "L/min",
        minVal = 0f,
        maxVal = 30f,
        defaultWarningThreshold = 24f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    POWERTRAIN_COOLANT_LPM(
        title = "Powertrain Loop Flow Rate",
        shortLabel = "PT FLOW",
        unit = "L/min",
        minVal = 0f,
        maxVal = 35f,
        defaultWarningThreshold = 28f,
        defaultStyle = WidgetStyle.SPARKLINE_CARD,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    OCTOVALVE_POS(
        title = "Octovalve Thermal Mode Step",
        shortLabel = "OCTOVALVE",
        unit = "pos",
        minVal = 1f,
        maxVal = 8f,
        defaultWarningThreshold = 7f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 0
    ),
    PACK_TEMP_MAX_C(
        title = "Max Battery Module Temp",
        shortLabel = "PACK MAX T",
        unit = "°C",
        minVal = -15f,
        maxVal = 65f,
        defaultWarningThreshold = 52f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    CABIN_TEMP_C(
        title = "Cabin Interior Temperature",
        shortLabel = "CABIN TEMP",
        unit = "°C",
        minVal = -10f,
        maxVal = 50f,
        defaultWarningThreshold = 35f,
        defaultStyle = WidgetStyle.DIGITAL_READOUT,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 1
    ),
    HVAC_POWER_KW(
        title = "Heat Pump Compressor Power",
        shortLabel = "HEAT PUMP",
        unit = "kW",
        minVal = 0f,
        maxVal = 7.0f,
        defaultWarningThreshold = 5.5f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.COMPACT_1X1,
        subsystem = TeslaSubsystem.VCFRONT_THERMAL,
        decimals = 2
    ),

    // 4. Braking, Regen & G-Force Dynamics
    COMBINED_G_FORCE(
        title = "2D Lateral & Longitudinal G-Force",
        shortLabel = "G-VECTOR",
        unit = "G",
        minVal = 0f,
        maxVal = 1.5f,
        defaultWarningThreshold = 0.95f,
        defaultStyle = WidgetStyle.G_FORCE_CIRCLE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.ESP_DYNAMICS,
        decimals = 2
    ),
    BRAKE_PRESSURE_BAR(
        title = "Master Cylinder Brake Pressure",
        shortLabel = "BRAKE BAR",
        unit = "bar",
        minVal = 0f,
        maxVal = 140f,
        defaultWarningThreshold = 90f,
        defaultStyle = WidgetStyle.RADIAL_GAUGE,
        defaultSpan = WidgetSpan.LARGE_2X2,
        subsystem = TeslaSubsystem.ESP_DYNAMICS,
        decimals = 1
    ),
    REGEN_SPLIT_PCT(
        title = "Regen Energy Recovery Ratio",
        shortLabel = "REGEN SPLIT",
        unit = "%",
        minVal = 0f,
        maxVal = 100f,
        defaultWarningThreshold = 85f,
        defaultStyle = WidgetStyle.HORIZONTAL_BAR,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.ESP_DYNAMICS,
        decimals = 0
    ),
    STEERING_ANGLE_DEG(
        title = "Steering Rack Angle",
        shortLabel = "STEER ANGLE",
        unit = "°",
        minVal = -450f,
        maxVal = 450f,
        defaultWarningThreshold = 280f,
        defaultStyle = WidgetStyle.BI_DIRECTIONAL_ARC,
        defaultSpan = WidgetSpan.WIDE_2X1,
        subsystem = TeslaSubsystem.ESP_DYNAMICS,
        decimals = 1
    )
}

data class DashboardWidgetConfig(
    val id: String,
    val pid: MetricPid,
    val style: WidgetStyle = pid.defaultStyle,
    val span: WidgetSpan = pid.defaultSpan,
    val warningThreshold: Float = pid.defaultWarningThreshold
)

data class CanFramePacket(
    val timestampMs: Long,
    val busId: Int,
    val canId: Int,
    val payload: ByteArray,
    val decodedSummary: String
) {
    val canIdHex: String
        get() = "0x%03X".format(canId)

    val payloadHex: String
        get() = payload.joinToString(" ") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFramePacket) return false
        return timestampMs == other.timestampMs &&
            busId == other.busId &&
            canId == other.canId &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + busId
        result = 31 * result + canId
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

data class VehicleTelemetrySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    // Powertrain & Performance
    val speedKph: Float = 0f,
    val netPowerKw: Float = 0f,
    val rearMotorKw: Float = 0f,
    val frontMotorKw: Float = 0f,
    val rearTorqueNm: Float = 0f,
    val frontTorqueNm: Float = 0f,
    val rearRpm: Int = 0,
    val frontRpm: Int = 0,
    val throttlePct: Float = 0f,
    val brakePct: Float = 0f,
    val rearInverterTempC: Float = 34.0f,
    val frontInverterTempC: Float = 31.5f,
    val statorTempC: Float = 42.0f,
    val zeroToSixtySec: Float = 3.28f,
    val currentGear: String = "D",
    // Battery & Charging
    val usableSocPct: Float = 78.4f,
    val bmsSocPct: Float = 80.1f,
    val packVoltageV: Float = 384.2f,
    val packCurrentA: Float = 12.4f,
    val cellMinV: Float = 3.988f,
    val cellMaxV: Float = 3.996f,
    val cellDeltaMv: Float = 8.0f,
    val cellBrickVoltages: List<Float> = listOf(
        3.991f, 3.994f, 3.989f, 3.996f, 3.992f, 3.988f,
        3.993f, 3.995f, 3.990f, 3.992f, 3.994f, 3.991f
    ),
    val packTempMinC: Float = 28.5f,
    val packTempMaxC: Float = 31.2f,
    val nominalFullPackKwh: Float = 76.8f,
    val initialPackKwh: Float = 79.5f,
    val degradationPct: Float = 3.40f,
    val chargePowerKw: Float = 0.0f,
    val targetChargeLimitPct: Int = 80,
    // Thermal & Octovalve
    val octovalvePosition: Int = 2,
    val octovalveModeLabel: String = "Parallel Cabin + Pack Loop",
    val coolantBatteryInletC: Float = 27.4f,
    val coolantPowertrainInletC: Float = 36.8f,
    val batteryCoolantLpm: Float = 8.5f,
    val powertrainCoolantLpm: Float = 11.2f,
    val cabinTempC: Float = 21.0f,
    val ambientTempC: Float = 18.5f,
    val hvacPowerKw: Float = 0.85f,
    val chillerActive: Boolean = false,
    // Braking, Regen & G-Force Dynamics
    val lateralG: Float = 0.02f,
    val longitudinalG: Float = 0.05f,
    val gForceTrail: List<Pair<Float, Float>> = emptyList(),
    val brakePressureBar: Float = 0.0f,
    val regenPowerKw: Float = 0.0f,
    val regenSplitPct: Float = 100f,
    val steeringAngleDeg: Float = 0.0f,
    val flWheelKph: Float = 0f,
    val frWheelKph: Float = 0f,
    val rlWheelKph: Float = 0f,
    val rrWheelKph: Float = 0f,
    // Recent CAN Frames
    val recentCanFrames: List<CanFramePacket> = emptyList()
) {
    fun getValue(pid: MetricPid): Float = when (pid) {
        MetricPid.SPEED_KPH -> speedKph
        MetricPid.NET_POWER_KW -> netPowerKw
        MetricPid.REAR_MOTOR_KW -> rearMotorKw
        MetricPid.FRONT_MOTOR_KW -> frontMotorKw
        MetricPid.REAR_TORQUE_NM -> rearTorqueNm
        MetricPid.FRONT_TORQUE_NM -> frontTorqueNm
        MetricPid.REAR_INVERTER_TEMP_C -> rearInverterTempC
        MetricPid.STATOR_TEMP_C -> statorTempC
        MetricPid.ZERO_TO_SIXTY_SEC -> zeroToSixtySec
        MetricPid.THROTTLE_PCT -> throttlePct
        MetricPid.USABLE_SOC_PCT -> usableSocPct
        MetricPid.BMS_SOC_PCT -> bmsSocPct
        MetricPid.CELL_DELTA_MV -> cellDeltaMv
        MetricPid.PACK_VOLTAGE_V -> packVoltageV
        MetricPid.PACK_CURRENT_A -> packCurrentA
        MetricPid.CELL_MIN_V -> cellMinV
        MetricPid.CELL_MAX_V -> cellMaxV
        MetricPid.NOMINAL_FULL_PACK_KWH -> nominalFullPackKwh
        MetricPid.DEGRADATION_PCT -> degradationPct
        MetricPid.CHARGE_POWER_KW -> chargePowerKw
        MetricPid.COOLANT_BATTERY_INLET_C -> coolantBatteryInletC
        MetricPid.COOLANT_POWERTRAIN_INLET_C -> coolantPowertrainInletC
        MetricPid.BATTERY_COOLANT_LPM -> batteryCoolantLpm
        MetricPid.POWERTRAIN_COOLANT_LPM -> powertrainCoolantLpm
        MetricPid.OCTOVALVE_POS -> octovalvePosition.toFloat()
        MetricPid.PACK_TEMP_MAX_C -> packTempMaxC
        MetricPid.CABIN_TEMP_C -> cabinTempC
        MetricPid.HVAC_POWER_KW -> hvacPowerKw
        MetricPid.COMBINED_G_FORCE -> kotlin.math.sqrt(lateralG * lateralG + longitudinalG * longitudinalG)
        MetricPid.BRAKE_PRESSURE_BAR -> brakePressureBar
        MetricPid.REGEN_SPLIT_PCT -> regenSplitPct
        MetricPid.STEERING_ANGLE_DEG -> steeringAngleDeg
    }

    fun formatValue(pid: MetricPid): String {
        val raw = getValue(pid)
        return "%.${pid.decimals}f".format(raw)
    }

    fun isWarningActive(pid: MetricPid, threshold: Float): Boolean {
        val value = getValue(pid)
        return when (pid) {
            MetricPid.USABLE_SOC_PCT, MetricPid.BMS_SOC_PCT, MetricPid.CELL_MIN_V, MetricPid.NOMINAL_FULL_PACK_KWH ->
                value <= threshold
            MetricPid.STEERING_ANGLE_DEG ->
                abs(value) >= threshold
            else ->
                value >= threshold
        }
    }
}

data class AlertFreezeFrame(
    val speedKph: Float,
    val netPowerKw: Float,
    val packVoltageV: Float,
    val packCurrentA: Float,
    val usableSocPct: Float,
    val cellMinV: Float,
    val cellMaxV: Float,
    val cellDeltaMv: Float,
    val statorTempC: Float,
    val rearInverterTempC: Float,
    val coolantBatteryInletC: Float,
    val octovalvePosition: Int,
    val gear: String
)

enum class ConnectionMode(val label: String) {
    LIVE_S3XY_WIFI("Live S3XY_OBD Wi-Fi"),
    CAN_LOG_REPLAY("Recorded CAN Replay")
}

enum class S3xyProtocol(val label: String, val defaultPort: Int) {
    PANDA_UDP_1338("Panda / CANserver UDP", 1338),
    ELM327_TCP_35000("ELM327 / STN TCP Bridge", 35000)
}

enum class ConnectionStatus(val badgeText: String) {
    DISCONNECTED("OFFLINE"),
    CONNECTING("LINKING S3XY_OBD..."),
    CONNECTED_LIVE("S3XY_OBD LIVE"),
    REPLAYING_LOG("CAN REPLAY ACTIVE"),
    SOCKET_ERROR("S3XY_OBD UNREACHABLE")
}

enum class BundledCanTrace(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationSec: Int,
    val tag: String
) {
    CANYON_SPIRITED_RUN(
        id = "canyon_run",
        title = "Canyon Spirited Drive (Dual Motor AWD)",
        subtitle = "345 kW launches, -78 kW trail regen, 0.98G cornering & stator heat sweep",
        durationSec = 120,
        tag = "PERFORMANCE"
    ),
    V3_SUPERCHARGE_250KW(
        id = "v3_supercharge",
        title = "250 kW V3 Supercharger Session",
        subtitle = "Octovalve 48°C pack preconditioning, 615A peak DC taper & cell balancing",
        durationSec = 120,
        tag = "CHARGING"
    ),
    SUB_ZERO_OCTOVALVE(
        id = "cold_soak",
        title = "-8°C Cold Soak & Octovalve Heat Pump",
        subtitle = "Series heat-pump compressor loop, stator heat scavenging & cabin warmup",
        durationSec = 120,
        tag = "THERMAL"
    ),
    TRACK_FAULT_DIAGNOSTIC(
        id = "track_faults",
        title = "Track Redline & Fault Interrogation Trace",
        subtitle = "High thermal load triggering DI_a138, BMS_a063 & VCFRONT_a192 alerts",
        durationSec = 120,
        tag = "DIAGNOSTICS"
    )
}
