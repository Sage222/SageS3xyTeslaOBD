package com.example.data.can

import com.example.data.local.CockpitJsonSerializer
import com.example.data.local.VehicleAlertEntity
import com.example.model.AlertFreezeFrame
import com.example.model.AlertSeverity
import com.example.model.CanFramePacket
import com.example.model.TeslaSubsystem
import com.example.model.VehicleTelemetrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes binary Panda / CANserver UDP packets (Port 1338) and ELM327 ASCII frames
 * for 2023 Tesla Model 3 Vehicle & Chassis CAN-bus arbitration IDs using authentic
 * Model3CAN.dbc bit-packed Intel little-endian definitions.
 */
object Model3CanFrameDecoder {

    // Core 2023 Tesla Model 3 CAN Arbitration IDs (Model3CAN.dbc)
    const val ID_DIR_TORQUE = 0x108            // BO_ 264: DIR_torqueActual (27|13@1-), DIR_axleSpeed (40|16@1-)
    const val ID_RCM_INERTIAL2 = 0x111         // BO_ 273: RCM_longitudinalAccel (0|16@1-), RCM_lateralAccel (16|16@1-)
    const val ID_DRIVE_SYSTEM_STATUS = 0x118   // BO_ 280: DI_brakePedalState (19|2@1+), DI_gear (21|3@1+), DI_accelPedalPos (32|8@1+)
    const val ID_STEERING_ANGLE = 0x129        // BO_ 297: SteeringAngle129 (16|14@1+ scale 0.1 offset -819.2)
    const val ID_HV_BATTERY_VOLT_AMP = 0x132   // BO_ 306: BattVoltage132 (0|16@1+ 0.01), SmoothBattCurrent132 (16|16@1- -0.1)
    const val ID_ESP_STATUS = 0x145            // BO_ 325: ESP brake & dynamics status
    const val ID_ESP_WHEEL_SPEEDS = 0x175      // BO_ 373: Wheel speeds FL/FR/RL/RR
    const val ID_DIF_TORQUE = 0x186            // BO_ 390: DIF_torqueActual (27|13@1-), DIF_axleSpeed (40|16@1-)
    const val ID_FRONT_TORQUE_1D5 = 0x1D5      // BO_ 469: FrontTorque1D5
    const val ID_REAR_TORQUE_1D8 = 0x1D8       // BO_ 472: RearTorque1D8
    const val ID_VCFRONT_COOLANT = 0x241       // BO_ 577: VCFRONT_coolantFlowBatActual (0|9@1+ 0.1), PTActual (22|9@1+ 0.1)
    const val ID_DI_SPEED = 0x257              // BO_ 599: DI_uiSpeed_signed (12|12@1+ 0.05,-25), DI_uiSpeed (24|9@1+), Units (33|1@1+)
    const val ID_REAR_INVERTER_POWER = 0x266   // BO_ 614: RearPower266 (0|11@1- 0.5 kW)
    const val ID_BMS_SOC = 0x292               // BO_ 658: SOCUI292 (0|10@1+ 0.1), SOCmin (10|10), SOCmax (20|10), SOCave (30|10)
    const val ID_FRONT_INVERTER_POWER = 0x2E5  // BO_ 741: FrontPower2E5 (0|11@1- 0.5 kW)
    const val ID_REAR_INVERTER_TEMPS = 0x315   // BO_ 789: RearTempInvPCB (0|8), RearTempInverter (8|8), RearTempStator (16|8) (+1,-40)
    const val ID_VCFRONT_SENSORS = 0x321       // BO_ 801: tempCoolantBatInlet (0|10 0.125,-40), PTInlet (10|11 0.125,-40), Ambient (24|8 0.5,-40)
    const val ID_BMS_CELL_BRICKS = 0x332       // BO_ 818: Mux (0|2): m0 Temps (16|8, 24|8 0.5,-40), m1 Voltages (Max 2|12, Min 16|12 0.002)
    const val ID_BMS_ENERGY_STATUS = 0x352     // BO_ 850: NominalFullPackEnergy (0|11 0.1 kWh), EnergyRemaining (11|11 0.1 kWh)
    const val ID_FRONT_INVERTER_TEMPS = 0x376  // BO_ 886: Front inverter temperatures
    const val ID_TESLA_BMS_ALERT_MUX = 0x3E2   // BMS Alert Multiplexer
    const val ID_TESLA_HVP_ALERT_MUX = 0x3E3   // HVP / Thermal / DI Alert Multiplexer

    /**
     * All 11-bit CAN arbitration IDs to subscribe to via the S3XY Commander / Panda 0x0F filter command.
     * Maximum 42 IDs per 0x0F packet (1 + 42 * 3 = 127 bytes).
     */
    val SUBSCRIBED_MODEL3_CAN_IDS: IntArray = intArrayOf(
        ID_DIR_TORQUE,
        ID_RCM_INERTIAL2,
        ID_DRIVE_SYSTEM_STATUS,
        ID_STEERING_ANGLE,
        ID_HV_BATTERY_VOLT_AMP,
        ID_ESP_STATUS,
        ID_ESP_WHEEL_SPEEDS,
        ID_DIF_TORQUE,
        ID_FRONT_TORQUE_1D5,
        ID_REAR_TORQUE_1D8,
        ID_VCFRONT_COOLANT,
        ID_DI_SPEED,
        ID_REAR_INVERTER_POWER,
        ID_BMS_SOC,
        ID_FRONT_INVERTER_POWER,
        ID_REAR_INVERTER_TEMPS,
        ID_VCFRONT_SENSORS,
        ID_BMS_CELL_BRICKS,
        ID_BMS_ENERGY_STATUS,
        ID_FRONT_INVERTER_TEMPS,
        ID_TESLA_BMS_ALERT_MUX,
        ID_TESLA_HVP_ALERT_MUX,
        0x7E8,
        0x7E9,
        0x7EA,
        0x7EB
    )

    /**
     * Builds the S3XY Commander / CANserver Panda UDP 0x0F filter subscription packet:
     * Prefix byte 0x0F followed by 3-byte big-endian entries: [0xFF, frame_id_hi, frame_id_lo].
     */
    fun buildPandaFilterSubscriptionPacket(canIds: IntArray = SUBSCRIBED_MODEL3_CAN_IDS): ByteArray {
        val count = min(42, canIds.size)
        val out = ByteArray(1 + count * 3)
        out[0] = 0x0F.toByte()
        for (i in 0 until count) {
            val id = canIds[i] and 0x7FF
            val base = 1 + i * 3
            out[base] = 0xFF.toByte()
            out[base + 1] = ((id ushr 8) and 0xFF).toByte()
            out[base + 2] = (id and 0xFF).toByte()
        }
        return out
    }

    data class DecodedCanBatch(
        val snapshot: VehicleTelemetrySnapshot,
        val frames: List<CanFramePacket>,
        val alerts: List<VehicleAlertEntity>
    )

    /**
     * Extracts an unsigned Intel (little-endian) bitfield from an 8-byte CAN payload.
     */
    fun extractUnsignedBits(payload: ByteArray, startBit: Int, bitLength: Int): Long {
        if (bitLength <= 0 || bitLength > 64 || payload.isEmpty()) return 0L
        var raw64 = 0L
        val len = min(8, payload.size)
        for (i in 0 until len) {
            raw64 = raw64 or ((payload[i].toLong() and 0xFFL) shl (i * 8))
        }
        val mask = if (bitLength == 64) -1L else (1L shl bitLength) - 1L
        return (raw64 ushr startBit) and mask
    }

    /**
     * Extracts a two's-complement signed Intel (little-endian) bitfield from an 8-byte CAN payload.
     */
    fun extractSignedBits(payload: ByteArray, startBit: Int, bitLength: Int): Long {
        val unsigned = extractUnsignedBits(payload, startBit, bitLength)
        if (bitLength <= 0 || bitLength >= 64) return unsigned
        val signBit = 1L shl (bitLength - 1)
        return if ((unsigned and signBit) != 0L) {
            unsigned - (1L shl bitLength)
        } else {
            unsigned
        }
    }

    /**
     * Inserts a bitfield into a 64-bit Intel (little-endian) word for unit-test packet construction.
     */
    private fun insertBits(word: Long, value: Long, startBit: Int, bitLength: Int): Long {
        val mask = if (bitLength == 64) -1L else (1L shl bitLength) - 1L
        val cleaned = value and mask
        return (word and (mask shl startBit).inv()) or (cleaned shl startBit)
    }

    fun parsePandaUdpDatagram(
        data: ByteArray,
        length: Int,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): Pair<VehicleTelemetrySnapshot, List<CanFramePacket>> {
        val batch = parsePandaUdpDatagramWithAlerts(data, length, current, timestampMs)
        return batch.snapshot to batch.frames
    }

    fun parsePandaUdpDatagramWithAlerts(
        data: ByteArray,
        length: Int,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedCanBatch {
        var updated = current.copy(timestampMs = timestampMs)
        val parsedFrames = mutableListOf<CanFramePacket>()
        val decodedAlerts = mutableListOf<VehicleAlertEntity>()
        var offset = 0

        // Standard Panda UDP datagrams pack N * 16-byte CAN frames per UDP packet.
        while (offset + 16 <= length) {
            val header0 = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val header1 = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int

            // Panda format: extended ID if (header0 & 0x04) != 0, otherwise 11-bit ID in header0 >>> 21
            val isExtended = (header0 and 0x04) != 0
            val canId = if (isExtended) {
                (header0 ushr 3) and 0x1FFFFFFF
            } else {
                (header0 ushr 21) and 0x7FF
            }
            val dlc = header1 and 0x0F
            val busId = (header1 ushr 4) and 0x0F

            if (canId in 1..0x1FFFFFFF && dlc in 1..8) {
                val payloadLen = min(8, max(0, dlc))
                val payload = data.copyOfRange(offset + 8, offset + 8 + payloadLen)

                val (nextSnap, summary) = applyCanFrame(canId, busId, payload, updated, timestampMs)
                updated = nextSnap
                parsedFrames.add(
                    CanFramePacket(
                        timestampMs = timestampMs,
                        busId = busId,
                        canId = canId,
                        payload = payload,
                        decodedSummary = summary
                    )
                )
                decodeAlertFromCanFrame(canId, payload, updated, timestampMs)?.let {
                    decodedAlerts.add(it)
                }
            }
            offset += 16
        }

        if (parsedFrames.isEmpty() && length > 0) {
            val preview = data.copyOf(min(8, length))
            val hexDump = data.take(min(16, length)).joinToString(" ") { "%02X".format(it) }
            parsedFrames.add(
                CanFramePacket(
                    timestampMs = timestampMs,
                    busId = 0,
                    canId = 0,
                    payload = preview,
                    decodedSummary = "UDP Datagram (${length}B): $hexDump"
                )
            )
        }

        if (parsedFrames.isNotEmpty()) {
            val combinedFrames = (parsedFrames.reversed() + updated.recentCanFrames).take(32)
            updated = updated.copy(recentCanFrames = combinedFrames)
        }
        return DecodedCanBatch(updated, parsedFrames, decodedAlerts)
    }

    fun parseElm327AsciiLineWithAlerts(
        line: String,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedCanBatch {
        val cleaned = line.trim().replace(" ", "")
        if (cleaned.length < 5) return DecodedCanBatch(current, emptyList(), emptyList())
        return try {
            val canId = cleaned.substring(0, 3).toInt(16)
            val hexPayload = cleaned.substring(3)
            val byteCount = min(8, hexPayload.length / 2)
            val payload = ByteArray(byteCount) { idx ->
                hexPayload.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
            }
            val (nextSnap, summary) = applyCanFrame(canId, 0, payload, current, timestampMs)
            val frame = CanFramePacket(
                timestampMs = timestampMs,
                busId = 0,
                canId = canId,
                payload = payload,
                decodedSummary = summary
            )
            val alert = decodeAlertFromCanFrame(canId, payload, nextSnap, timestampMs)
            val combined = (listOf(frame) + nextSnap.recentCanFrames).take(32)
            DecodedCanBatch(
                snapshot = nextSnap.copy(recentCanFrames = combined),
                frames = listOf(frame),
                alerts = listOfNotNull(alert)
            )
        } catch (e: Exception) {
            DecodedCanBatch(current, emptyList(), emptyList())
        }
    }

    private fun decodeAlertFromCanFrame(
        canId: Int,
        payload: ByteArray,
        snapshot: VehicleTelemetrySnapshot,
        timestampMs: Long
    ): VehicleAlertEntity? {
        if (payload.size < 4) return null
        val canIdHex = "0x%03X".format(canId)
        val payloadHex = payload.joinToString(" ") { "%02X".format(it) }
        fun buildFreezeJson(): String = CockpitJsonSerializer.encodeFreezeFrame(
            AlertFreezeFrame(
                speedKph = snapshot.speedKph,
                netPowerKw = snapshot.netPowerKw,
                packVoltageV = snapshot.packVoltageV,
                packCurrentA = snapshot.packCurrentA,
                usableSocPct = snapshot.usableSocPct,
                cellMinV = snapshot.cellMinV,
                cellMaxV = snapshot.cellMaxV,
                cellDeltaMv = snapshot.cellDeltaMv,
                statorTempC = snapshot.statorTempC,
                rearInverterTempC = snapshot.rearInverterTempC,
                coolantBatteryInletC = snapshot.coolantBatteryInletC,
                octovalvePosition = snapshot.octovalvePosition,
                gear = snapshot.currentGear
            )
        )

        return when (canId) {
            ID_TESLA_BMS_ALERT_MUX -> {
                val muxIndex = payload[0].toInt() and 0xFF
                val activeBits = payload[1].toInt() and 0xFF
                if (muxIndex == 0 || activeBits == 0) return null
                val freezeJson = buildFreezeJson()
                val code = "BMS_a%03d".format(muxIndex)
                VehicleAlertEntity(
                    alertCode = code,
                    subsystem = TeslaSubsystem.BMS.name,
                    severity = if (activeBits >= 2) AlertSeverity.CRITICAL.name else AlertSeverity.WARNING.name,
                    title = "BMS CAN Alert $code (Mux 0x%02X)".format(muxIndex),
                    description = "Decoded live BMS alert frame from S3XY_OBD on CAN ID $canIdHex [$payloadHex].",
                    recommendedAction = "Inspect HV pack voltage (${snapshot.packVoltageV}V) and cell balance delta (${snapshot.cellDeltaMv} mV).",
                    rawCanIdHex = canIdHex,
                    rawPayloadHex = payloadHex,
                    freezeFrameJson = freezeJson,
                    timestampMs = timestampMs,
                    isActive = true,
                    isAcknowledged = false
                )
            }

            ID_TESLA_HVP_ALERT_MUX -> {
                val muxIndex = payload[0].toInt() and 0xFF
                val activeBits = payload[1].toInt() and 0xFF
                if (muxIndex == 0 || activeBits == 0) return null
                val freezeJson = buildFreezeJson()
                val code = "HVP_a%03d".format(muxIndex)
                VehicleAlertEntity(
                    alertCode = code,
                    subsystem = TeslaSubsystem.HVP_CHARGING.name,
                    severity = AlertSeverity.WARNING.name,
                    title = "High Voltage Processor Alert $code",
                    description = "Decoded live HVP alert frame from S3XY_OBD on CAN ID $canIdHex [$payloadHex].",
                    recommendedAction = "Verify HV contactor state and charge port pilot signal.",
                    rawCanIdHex = canIdHex,
                    rawPayloadHex = payloadHex,
                    freezeFrameJson = freezeJson,
                    timestampMs = timestampMs,
                    isActive = true,
                    isAcknowledged = false
                )
            }

            in 0x7E8..0x7EF -> {
                val serviceId = payload[1].toInt() and 0xFF
                if (serviceId != 0x43 && serviceId != 0x59) return null
                val dtcHi = payload[2].toInt() and 0xFF
                val dtcLo = payload[3].toInt() and 0xFF
                if (dtcHi == 0 && dtcLo == 0) return null
                val freezeJson = buildFreezeJson()
                val dtcCode = "P%02X%02X".format(dtcHi, dtcLo)
                VehicleAlertEntity(
                    alertCode = dtcCode,
                    subsystem = TeslaSubsystem.DI_INVERTER.name,
                    severity = AlertSeverity.WARNING.name,
                    title = "OBD-II / UDS Diagnostic Fault $dtcCode",
                    description = "ECU ${"0x%03X".format(canId)} reported active diagnostic trouble code $dtcCode over S3XY_OBD.",
                    recommendedAction = "Inspect freeze-frame telemetry and clear fault after verifying subsystem health.",
                    rawCanIdHex = canIdHex,
                    rawPayloadHex = payloadHex,
                    freezeFrameJson = freezeJson,
                    timestampMs = timestampMs,
                    isActive = true,
                    isAcknowledged = false
                )
            }

            else -> null
        }
    }

    fun applyCanFrame(
        canId: Int,
        busId: Int,
        payload: ByteArray,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long
    ): Pair<VehicleTelemetrySnapshot, String> {
        if (payload.isEmpty()) {
            return current to "CAN 0x%03X (Bus $busId) [0B]".format(canId)
        }

        return when (canId) {
            // BO_ 599 (0x257) ID257DIspeed:
            // SG_ DI_uiSpeed_signed : 12|12@1+ (0.05,-25)
            // SG_ DI_uiSpeed : 24|9@1+ (1,0)
            // SG_ DI_uiSpeedUnits : 33|1@1+ (0=MPH, 1=KPH)
            ID_DI_SPEED -> {
                val speedSignedRaw = extractUnsignedBits(payload, 12, 12).toInt()
                val uiSpeedRaw = extractUnsignedBits(payload, 24, 9).toInt()
                val unitsIsKph = extractUnsignedBits(payload, 33, 1) == 1L

                val rawSpeedValue = if (speedSignedRaw in 1..4094) {
                    (speedSignedRaw * 0.05f - 25f).coerceAtLeast(0f)
                } else if (uiSpeedRaw != 0x1FF) {
                    uiSpeedRaw.toFloat()
                } else {
                    0f
                }

                val speedKph = (if (unitsIsKph || speedSignedRaw in 1..4094) {
                    rawSpeedValue
                } else {
                    rawSpeedValue * 1.60934f
                }).coerceIn(0f, 280f)

                val snap = current.copy(
                    timestampMs = timestampMs,
                    speedKph = speedKph,
                    flWheelKph = speedKph,
                    frWheelKph = speedKph,
                    rlWheelKph = speedKph,
                    rrWheelKph = speedKph
                )
                snap to "0x257 DI_Speed: ${"%.1f".format(speedKph)} km/h"
            }

            // BO_ 280 (0x118) ID118DriveSystemStatus:
            // SG_ DI_brakePedalState : 19|2@1+
            // SG_ DI_gear : 21|3@1+ (1=P, 2=R, 3=N, 4=D)
            // SG_ DI_accelPedalPos : 32|8@1+ (0.4,0)
            ID_DRIVE_SYSTEM_STATUS -> {
                val brakeState = extractUnsignedBits(payload, 19, 2).toInt()
                val gearCode = extractUnsignedBits(payload, 21, 3).toInt()
                val accelRaw = extractUnsignedBits(payload, 32, 8).toInt()
                val gear = when (gearCode) {
                    1 -> "P"
                    2 -> "R"
                    3 -> "N"
                    4 -> "D"
                    else -> if (current.currentGear == "--") "P" else current.currentGear
                }
                val throttlePct = (accelRaw * 0.4f).coerceIn(0f, 100f)
                val brakePct = if (brakeState == 1) max(current.brakePct, 25f) else 0f
                val snap = current.copy(
                    timestampMs = timestampMs,
                    currentGear = gear,
                    throttlePct = throttlePct,
                    brakePct = brakePct
                )
                snap to "0x118 DI_Status: Gear $gear | Accel ${"%.0f".format(throttlePct)}%"
            }

            // BO_ 306 (0x132) ID132HVBattAmpVolt:
            // SG_ BattVoltage132 : 0|16@1+ (0.01,0) V
            // SG_ SmoothBattCurrent132 : 16|16@1- (-0.1,0) A
            ID_HV_BATTERY_VOLT_AMP -> {
                val voltsRaw = extractUnsignedBits(payload, 0, 16).toInt()
                val currentSigned = extractSignedBits(payload, 16, 16).toInt()
                val packV = (voltsRaw * 0.01f).coerceIn(0f, 500f)
                val packA = (currentSigned * -0.1f).coerceIn(-1000f, 1500f)
                val netKw = (packV * packA) / 1000f
                val isCharging = current.currentGear == "P" && netKw < -0.5f
                val chargeKw = if (isCharging) abs(netKw) else 0f
                val regenKw = if (!isCharging && netKw < 0f) abs(netKw) else 0f
                val regenSplit = if (regenKw > 0.5f) {
                    ((regenKw / (regenKw + current.brakePressureBar * 0.8f + 0.1f)) * 100f).coerceIn(0f, 100f)
                } else {
                    current.regenSplitPct
                }
                val snap = current.copy(
                    timestampMs = timestampMs,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    netPowerKw = netKw,
                    chargePowerKw = chargeKw,
                    regenPowerKw = regenKw,
                    regenSplitPct = regenSplit
                )
                snap to "0x132 HV_Bus: ${"%.1f".format(packV)}V | ${"%.1f".format(packA)}A (${"%.1f".format(netKw)} kW)"
            }

            // BO_ 264 (0x108) ID108DIR_torque:
            // SG_ DIR_torqueActual : 27|13@1- (2,0) Nm
            // SG_ DIR_axleSpeed : 40|16@1- (0.1,0) RPM
            // SG_ DIR_slavePedalPos : 56|8@1+ (0.4,0) %
            ID_DIR_TORQUE -> {
                val torqueSigned = extractSignedBits(payload, 27, 13).toInt()
                val axleSpeedSigned = extractSignedBits(payload, 40, 16).toInt()
                val pedalRaw = extractUnsignedBits(payload, 56, 8).toInt()
                val rearTrq = (torqueSigned * 2.0f).coerceIn(-600f, 900f)
                val rearRpm = (axleSpeedSigned * 0.9f).roundToInt() // Motor RPM (~9:1 gear reduction * 0.1 scale)
                val pedalPct = (pedalRaw * 0.4f).coerceIn(0f, 100f)
                // Estimate rear kW from torque & RPM if 0x266 hasn't arrived yet
                val estimatedKw = if (current.rearMotorKw == 0f && rearRpm != 0) {
                    ((rearTrq * axleSpeedSigned * 0.1f * 9.0f) / 9549f).coerceIn(-120f, 320f)
                } else {
                    current.rearMotorKw
                }
                val snap = current.copy(
                    timestampMs = timestampMs,
                    rearTorqueNm = rearTrq,
                    rearRpm = rearRpm,
                    rearMotorKw = estimatedKw,
                    throttlePct = if (pedalPct > 0f) pedalPct else current.throttlePct
                )
                snap to "0x108 DIR_Trq: ${"%.0f".format(rearTrq)} Nm | $rearRpm RPM"
            }

            // BO_ 390 (0x186) ID186DIF_torque:
            // SG_ DIF_torqueActual : 27|13@1- (2,0) Nm
            // SG_ DIF_axleSpeed : 40|16@1- (0.1,0) RPM
            ID_DIF_TORQUE -> {
                val torqueSigned = extractSignedBits(payload, 27, 13).toInt()
                val axleSpeedSigned = extractSignedBits(payload, 40, 16).toInt()
                val frontTrq = (torqueSigned * 2.0f).coerceIn(-500f, 700f)
                val frontRpm = (axleSpeedSigned * 0.9f).roundToInt()
                val estimatedKw = if (current.frontMotorKw == 0f && frontRpm != 0) {
                    ((frontTrq * axleSpeedSigned * 0.1f * 9.0f) / 9549f).coerceIn(-80f, 220f)
                } else {
                    current.frontMotorKw
                }
                val snap = current.copy(
                    timestampMs = timestampMs,
                    frontTorqueNm = frontTrq,
                    frontRpm = frontRpm,
                    frontMotorKw = estimatedKw
                )
                snap to "0x186 DIF_Trq: ${"%.0f".format(frontTrq)} Nm | $frontRpm RPM"
            }

            // BO_ 472 (0x1D8) ID1D8RearTorque & BO_ 469 (0x1D5) ID1D5FrontTorque
            ID_REAR_TORQUE_1D8 -> {
                val trqRaw = extractSignedBits(payload, 21, 13).toInt()
                val rearTrq = (trqRaw * 0.25f).coerceIn(-600f, 900f)
                val snap = current.copy(timestampMs = timestampMs, rearTorqueNm = rearTrq)
                snap to "0x1D8 RearTrq: ${"%.0f".format(rearTrq)} Nm"
            }

            ID_FRONT_TORQUE_1D5 -> {
                val trqRaw = extractSignedBits(payload, 21, 13).toInt()
                val frontTrq = (trqRaw * 0.25f).coerceIn(-500f, 700f)
                val snap = current.copy(timestampMs = timestampMs, frontTorqueNm = frontTrq)
                snap to "0x1D5 FrontTrq: ${"%.0f".format(frontTrq)} Nm"
            }

            // BO_ 614 (0x266) ID266RearInverterPower:
            // SG_ RearPower266 : 0|11@1- (0.5,0) kW
            ID_REAR_INVERTER_POWER -> {
                val powerRaw = extractSignedBits(payload, 0, 11).toInt()
                val rearKw = (powerRaw * 0.5f).coerceIn(-150f, 350f)
                val snap = current.copy(timestampMs = timestampMs, rearMotorKw = rearKw)
                snap to "0x266 RearPwr: ${"%.1f".format(rearKw)} kW"
            }

            // BO_ 741 (0x2E5) ID2E5FrontInverterPower:
            // SG_ FrontPower2E5 : 0|11@1- (0.5,0) kW
            ID_FRONT_INVERTER_POWER -> {
                val powerRaw = extractSignedBits(payload, 0, 11).toInt()
                val frontKw = (powerRaw * 0.5f).coerceIn(-120f, 250f)
                val snap = current.copy(timestampMs = timestampMs, frontMotorKw = frontKw)
                snap to "0x2E5 FrontPwr: ${"%.1f".format(frontKw)} kW"
            }

            // BO_ 658 (0x292) ID292BMS_SOC:
            // SG_ SOCUI292 : 0|10@1+ (0.1,0)
            // SG_ SOCmin292 : 10|10@1+ (0.1,0)
            // SG_ SOCmax292 : 20|10@1+ (0.1,0)
            // SG_ SOCave292 : 30|10@1+ (0.1,0)
            ID_BMS_SOC -> {
                val socUiRaw = extractUnsignedBits(payload, 0, 10).toInt()
                val socMinRaw = extractUnsignedBits(payload, 10, 10).toInt()
                val socAveRaw = extractUnsignedBits(payload, 30, 10).toInt()
                val usableSoc = (socUiRaw * 0.1f).coerceIn(0f, 100f)
                val bmsSoc = (if (socAveRaw in 1..1000) socAveRaw * 0.1f else socMinRaw * 0.1f).coerceIn(0f, 100f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    usableSocPct = usableSoc,
                    bmsSocPct = bmsSoc
                )
                snap to "0x292 BMS_SOC: UI ${"%.1f".format(usableSoc)}% | BMS ${"%.1f".format(bmsSoc)}%"
            }

            // BO_ 850 (0x352) ID352BMS_EnergyStatus:
            // SG_ BMS_nominalFullPackEnergy : 0|11@1+ (0.1,0) kWh
            // SG_ BMS_nominalEnergyRemaining : 11|11@1+ (0.1,0) kWh
            ID_BMS_ENERGY_STATUS -> {
                val nomFullRaw = extractUnsignedBits(payload, 0, 11).toInt()
                val nomKwh = (nomFullRaw * 0.1f).coerceIn(0f, 100f)
                if (nomKwh in 25f..95f) {
                    // Infer initial pack capacity tier (e.g., ~60 kWh LFP RWD vs ~79.5 kWh LR/Performance)
                    val baselineKwh = if (nomKwh <= 62.5f) 60.5f else 79.5f
                    val degPct = (((baselineKwh - nomKwh) / baselineKwh) * 100f).coerceIn(0f, 30f)
                    val snap = current.copy(
                        timestampMs = timestampMs,
                        nominalFullPackKwh = nomKwh,
                        initialPackKwh = baselineKwh,
                        degradationPct = degPct
                    )
                    snap to "0x352 BMS_Energy: Full Cap ${"%.1f".format(nomKwh)} kWh (Deg ${"%.1f".format(degPct)}%)"
                } else {
                    current to "0x352 BMS_Energy frame"
                }
            }

            // BO_ 818 (0x332) ID332BattBrickMinMax:
            // SG_ BattBrickMultiplexer332 M : 0|2@1+
            // m0 (Temps): Max 16|8@1+ (0.5,-40), Min 24|8@1+ (0.5,-40)
            // m1 (Voltages): Max 2|12@1+ (0.002,0), Min 16|12@1+ (0.002,0)
            ID_BMS_CELL_BRICKS -> {
                val mux = extractUnsignedBits(payload, 0, 2).toInt()
                if (mux == 0) {
                    val tempMaxRaw = extractUnsignedBits(payload, 16, 8).toInt()
                    val tempMinRaw = extractUnsignedBits(payload, 24, 8).toInt()
                    val maxC = (tempMaxRaw * 0.5f - 40f).coerceIn(-40f, 85f)
                    val minC = (tempMinRaw * 0.5f - 40f).coerceIn(-40f, 85f)
                    val snap = current.copy(
                        timestampMs = timestampMs,
                        packTempMaxC = maxC,
                        packTempMinC = minC
                    )
                    snap to "0x332 BMS_Temp(m0): ${"%.1f".format(minC)}°C..${"%.1f".format(maxC)}°C"
                } else if (mux == 1) {
                    val maxVoltRaw = extractUnsignedBits(payload, 2, 12).toInt()
                    val minVoltRaw = extractUnsignedBits(payload, 16, 12).toInt()
                    val cellMaxV = (maxVoltRaw * 0.002f).coerceIn(0f, 4.5f)
                    val cellMinV = (minVoltRaw * 0.002f).coerceIn(0f, 4.5f)
                    val deltaMv = ((cellMaxV - cellMinV) * 1000f).coerceIn(0f, 500f)
                    val spread = (cellMaxV - cellMinV).coerceAtLeast(0.001f)
                    val bricks = List(12) { idx ->
                        val ratio = when (idx) {
                            3 -> 1.0f
                            5 -> 0.0f
                            else -> 0.25f + ((idx * 37) % 60) / 100f
                        }
                        cellMinV + spread * ratio
                    }
                    val snap = current.copy(
                        timestampMs = timestampMs,
                        cellMinV = cellMinV,
                        cellMaxV = cellMaxV,
                        cellDeltaMv = deltaMv,
                        cellBrickVoltages = bricks
                    )
                    snap to "0x332 BMS_Cells(m1): ${"%.3f".format(cellMinV)}V..${"%.3f".format(cellMaxV)}V (Δ${"%.0f".format(deltaMv)}mV)"
                } else {
                    current to "0x332 BMS_Brick(m$mux)"
                }
            }

            // BO_ 577 (0x241) ID241VCFRONT_coolant:
            // SG_ VCFRONT_coolantFlowBatActual : 0|9@1+ (0.1,0) L/min
            // SG_ VCFRONT_coolantFlowBatReason : 18|4@1+ (1,0)
            // SG_ VCFRONT_coolantFlowPTActual : 22|9@1+ (0.1,0) L/min
            ID_VCFRONT_COOLANT -> {
                val batFlowRaw = extractUnsignedBits(payload, 0, 9).toInt()
                val batReason = extractUnsignedBits(payload, 18, 4).toInt()
                val ptFlowRaw = extractUnsignedBits(payload, 22, 9).toInt()
                val battLpm = (batFlowRaw * 0.1f).coerceIn(0f, 40f)
                val ptLpm = (ptFlowRaw * 0.1f).coerceIn(0f, 45f)
                val octoStep = (batReason.coerceIn(1, 8))
                val snap = current.copy(
                    timestampMs = timestampMs,
                    batteryCoolantLpm = battLpm,
                    powertrainCoolantLpm = ptLpm,
                    octovalvePosition = octoStep,
                    octovalveModeLabel = octovalveStepDescription(octoStep)
                )
                snap to "0x241 Coolant: Batt ${"%.1f".format(battLpm)} L/m | PT ${"%.1f".format(ptLpm)} L/m"
            }

            // BO_ 801 (0x321) ID321VCFRONT_sensors:
            // SG_ VCFRONT_tempCoolantBatInlet : 0|10@1+ (0.125,-40) °C
            // SG_ VCFRONT_tempCoolantPTInlet : 10|11@1+ (0.125,-40) °C
            // SG_ VCFRONT_tempAmbient : 24|8@1+ (0.5,-40) °C
            ID_VCFRONT_SENSORS -> {
                val batInletRaw = extractUnsignedBits(payload, 0, 10).toInt()
                val ptInletRaw = extractUnsignedBits(payload, 10, 11).toInt()
                val ambientRaw = extractUnsignedBits(payload, 24, 8).toInt()
                val batInletC = (batInletRaw * 0.125f - 40f).coerceIn(-40f, 90f)
                val ptInletC = (ptInletRaw * 0.125f - 40f).coerceIn(-40f, 110f)
                val ambientC = (ambientRaw * 0.5f - 40f).coerceIn(-40f, 70f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    coolantBatteryInletC = batInletC,
                    coolantPowertrainInletC = ptInletC,
                    ambientTempC = ambientC
                )
                snap to "0x321 Thermal: BattIn ${"%.1f".format(batInletC)}°C | PTIn ${"%.1f".format(ptInletC)}°C | Amb ${"%.1f".format(ambientC)}°C"
            }

            // BO_ 789 (0x315) ID315RearInverterTemps:
            // SG_ RearTempInvPCB315 : 0|8@1+ (1,-40)
            // SG_ RearTempInverter315 : 8|8@1+ (1,-40)
            // SG_ RearTempStator315 : 16|8@1+ (1,-40)
            ID_REAR_INVERTER_TEMPS -> {
                val invTempRaw = extractUnsignedBits(payload, 8, 8).toInt()
                val statorTempRaw = extractUnsignedBits(payload, 16, 8).toInt()
                val rearInvC = (invTempRaw - 40f).coerceIn(-40f, 150f)
                val statorC = (statorTempRaw - 40f).coerceIn(-40f, 190f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    rearInverterTempC = rearInvC,
                    statorTempC = statorC
                )
                snap to "0x315 DI_Temps: R-Inv ${"%.0f".format(rearInvC)}°C | Stator ${"%.0f".format(statorC)}°C"
            }

            // BO_ 886 (0x376) ID376FrontInverterTemps:
            ID_FRONT_INVERTER_TEMPS -> {
                val invTempRaw = extractUnsignedBits(payload, 8, 8).toInt()
                val frontInvC = (invTempRaw - 40f).coerceIn(-40f, 150f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    frontInverterTempC = frontInvC
                )
                snap to "0x376 FrontInv: ${"%.0f".format(frontInvC)}°C"
            }

            // BO_ 273 (0x111) ID111RCM_inertial2:
            // SG_ RCM_longitudinalAccel : 0|16@1- (0.00125,0) m/s^2 (or G)
            // SG_ RCM_lateralAccel : 16|16@1- (0.00125,0)
            ID_RCM_INERTIAL2 -> {
                val lonRaw = extractSignedBits(payload, 0, 16).toInt()
                val latRaw = extractSignedBits(payload, 16, 16).toInt()
                val lonG = ((lonRaw * 0.00125f) / 9.80665f).coerceIn(-2.0f, 2.0f)
                val latG = ((latRaw * 0.00125f) / 9.80665f).coerceIn(-2.0f, 2.0f)
                val newTrail = (current.gForceTrail + (latG to lonG)).takeLast(28)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    lateralG = latG,
                    longitudinalG = lonG,
                    gForceTrail = newTrail
                )
                snap to "0x111 Inertial: Lat ${"%.2f".format(latG)}G | Lon ${"%.2f".format(lonG)}G"
            }

            // BO_ 297 (0x129) ID129SteeringAngle:
            // SG_ SteeringAngle129 : 16|14@1+ (0.1,-819.2) Deg
            ID_STEERING_ANGLE -> {
                val angleRaw = extractUnsignedBits(payload, 16, 14).toInt()
                val steerDeg = (angleRaw * 0.1f - 819.2f).coerceIn(-540f, 540f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    steeringAngleDeg = steerDeg
                )
                snap to "0x129 Steer: ${"%.1f".format(steerDeg)}°"
            }

            // BO_ 325 (0x145) ID145ESP_status:
            ID_ESP_STATUS -> {
                val brakeApply = extractUnsignedBits(payload, 31, 1) == 1L
                val brakePressureRaw = extractUnsignedBits(payload, 32, 8).toInt()
                val brakeBar = if (brakePressureRaw > 0) {
                    (brakePressureRaw * 0.5f).coerceIn(0f, 160f)
                } else if (brakeApply) {
                    18f
                } else {
                    0f
                }
                val snap = current.copy(
                    timestampMs = timestampMs,
                    brakePressureBar = brakeBar,
                    brakePct = if (brakeApply) max(current.brakePct, 20f) else current.brakePct
                )
                snap to "0x145 ESP_Status: Brake ${"%.1f".format(brakeBar)} bar"
            }

            else -> {
                current to "CAN 0x%03X (Bus $busId) %s".format(
                    canId,
                    payload.joinToString(" ") { "%02X".format(it) }
                )
            }
        }
    }

    /**
     * Encodes a [VehicleTelemetrySnapshot] into authentic Model3CAN.dbc bit-packed 16-byte Panda UDP frames.
     */
    fun encodeSnapshotToPandaUdpPacket(snapshot: VehicleTelemetrySnapshot): ByteArray {
        val frames = mutableListOf<ByteArray>()

        fun addFrame(canId: Int, busId: Int, word64: Long) {
            val buf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            val header0 = (canId and 0x7FF) shl 21
            val header1 = ((busId and 0x0F) shl 4) or 8
            buf.putInt(header0)
            buf.putInt(header1)
            buf.putLong(word64)
            frames.add(buf.array())
        }

        // 1. 0x257 ID_DI_SPEED
        var w257 = 0L
        val speedSignedRaw = ((snapshot.speedKph + 25f) / 0.05f).roundToInt().coerceIn(0, 4095).toLong()
        w257 = insertBits(w257, speedSignedRaw, 12, 12)
        w257 = insertBits(w257, snapshot.speedKph.roundToInt().toLong(), 24, 9)
        w257 = insertBits(w257, 1L, 33, 1) // KPH
        addFrame(ID_DI_SPEED, 0, w257)

        // 2. 0x118 ID_DRIVE_SYSTEM_STATUS
        var w118 = 0L
        val gearCode = when (snapshot.currentGear) {
            "P" -> 1L
            "R" -> 2L
            "N" -> 3L
            else -> 4L
        }
        w118 = insertBits(w118, gearCode, 21, 3)
        w118 = insertBits(w118, (snapshot.throttlePct / 0.4f).roundToInt().coerceIn(0, 255).toLong(), 32, 8)
        addFrame(ID_DRIVE_SYSTEM_STATUS, 0, w118)

        // 3. 0x132 ID_HV_BATTERY_VOLT_AMP
        var w132 = 0L
        val voltsRaw = (snapshot.packVoltageV / 0.01f).roundToInt().coerceIn(0, 65535).toLong()
        val currentRaw = (snapshot.packCurrentA / -0.1f).roundToInt().toLong()
        w132 = insertBits(w132, voltsRaw, 0, 16)
        w132 = insertBits(w132, currentRaw, 16, 16)
        addFrame(ID_HV_BATTERY_VOLT_AMP, 0, w132)

        // 4. 0x108 ID_DIR_TORQUE & 0x266 ID_REAR_INVERTER_POWER
        var w108 = 0L
        w108 = insertBits(w108, (snapshot.rearTorqueNm / 2.0f).roundToInt().toLong(), 27, 13)
        w108 = insertBits(w108, (snapshot.rearRpm / 0.9f).roundToInt().toLong(), 40, 16)
        addFrame(ID_DIR_TORQUE, 0, w108)

        var w266 = 0L
        w266 = insertBits(w266, (snapshot.rearMotorKw / 0.5f).roundToInt().toLong(), 0, 11)
        addFrame(ID_REAR_INVERTER_POWER, 0, w266)

        // 5. 0x292 ID_BMS_SOC
        var w292 = 0L
        w292 = insertBits(w292, (snapshot.usableSocPct * 10f).roundToInt().coerceIn(0, 1000).toLong(), 0, 10)
        w292 = insertBits(w292, (snapshot.bmsSocPct * 10f).roundToInt().coerceIn(0, 1000).toLong(), 30, 10)
        addFrame(ID_BMS_SOC, 0, w292)

        // 6. 0x332 ID_BMS_CELL_BRICKS (m1 voltages)
        var w332m1 = 0L
        w332m1 = insertBits(w332m1, 1L, 0, 2) // mux = 1
        w332m1 = insertBits(w332m1, (snapshot.cellMaxV / 0.002f).roundToInt().coerceIn(0, 4095).toLong(), 2, 12)
        w332m1 = insertBits(w332m1, (snapshot.cellMinV / 0.002f).roundToInt().coerceIn(0, 4095).toLong(), 16, 12)
        addFrame(ID_BMS_CELL_BRICKS, 0, w332m1)

        // 7. 0x241 ID_VCFRONT_COOLANT & 0x321 ID_VCFRONT_SENSORS
        var w241 = 0L
        w241 = insertBits(w241, (snapshot.batteryCoolantLpm * 10f).roundToInt().toLong(), 0, 9)
        w241 = insertBits(w241, snapshot.octovalvePosition.coerceIn(1, 8).toLong(), 18, 4)
        w241 = insertBits(w241, (snapshot.powertrainCoolantLpm * 10f).roundToInt().toLong(), 22, 9)
        addFrame(ID_VCFRONT_COOLANT, 1, w241)

        var w321 = 0L
        w321 = insertBits(w321, ((snapshot.coolantBatteryInletC + 40f) / 0.125f).roundToInt().toLong(), 0, 10)
        w321 = insertBits(w321, ((snapshot.coolantPowertrainInletC + 40f) / 0.125f).roundToInt().toLong(), 10, 11)
        addFrame(ID_VCFRONT_SENSORS, 1, w321)

        // 8. 0x315 ID_REAR_INVERTER_TEMPS
        var w315 = 0L
        w315 = insertBits(w315, (snapshot.rearInverterTempC + 40f).roundToInt().coerceIn(0, 255).toLong(), 8, 8)
        w315 = insertBits(w315, (snapshot.statorTempC + 40f).roundToInt().coerceIn(0, 255).toLong(), 16, 8)
        addFrame(ID_REAR_INVERTER_TEMPS, 0, w315)

        val out = ByteBuffer.allocate(frames.size * 16)
        frames.forEach { out.put(it) }
        return out.array()
    }

    fun octovalveStepDescription(step: Int): String = when (step) {
        1 -> "Step 1: Series Radiator Cooling (Max Reject)"
        2 -> "Step 2: Parallel Cabin + Pack Thermal Loop"
        3 -> "Step 3: Radiator Bypass & Chiller Balance"
        4 -> "Step 4: Powertrain Scavenge -> Pack Warmup"
        5 -> "Step 5: Heat Pump Cabin Heating + Chiller"
        6 -> "Step 6: Supercharger Pack Preconditioning"
        7 -> "Step 7: Max Chiller Active Battery Cooling"
        8 -> "Step 8: Sub-Zero Series Stator Heat Recovery"
        else -> "Awaiting S3XY_OBD CAN stream..."
    }
}
