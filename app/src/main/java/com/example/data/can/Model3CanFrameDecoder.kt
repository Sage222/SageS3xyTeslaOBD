package com.example.data.can

import com.example.model.CanFramePacket
import com.example.model.VehicleTelemetrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes and encodes binary Panda / CANserver UDP packets (Port 1338) and ELM327 ASCII frames
 * for 2023 Tesla Model 3 Vehicle & Chassis CAN-bus arbitration IDs.
 *
 * Standard Panda 16-byte CAN frame structure:
 * - Bytes 0..3 (UInt32 LE): (canId << 21) | (extendedFlag << 2)
 * - Bytes 4..7 (UInt32 LE): (busId << 4) | (dlc & 0x0F)
 * - Bytes 8..15 (8 bytes): Raw CAN payload
 */
object Model3CanFrameDecoder {

    const val ID_REAR_DRIVE_UNIT = 0x108       // Rear Torque, Rear RPM
    const val ID_FRONT_DRIVE_UNIT = 0x118      // Front Torque, Front RPM
    const val ID_HV_BATTERY_VOLT_AMP = 0x132   // Pack Voltage, Current, Net Power
    const val ID_ESP_DYNAMICS = 0x145          // Lateral/Longitudinal G, Brake Bar, Steering
    const val ID_VCFRONT_THERMAL = 0x241       // Coolant Inlet Temps, L/min Flow, Octovalve
    const val ID_UI_SPEED_GEAR = 0x257         // Vehicle Speed, Gear, Throttle/Brake
    const val ID_BMS_SOC_CAPACITY = 0x292      // Usable SOC, BMS SOC, Nominal Full Pack kWh
    const val ID_INVERTER_STATOR_TEMP = 0x315  // Rear/Front Inverter & Stator Temps
    const val ID_BMS_CELL_BRICKS = 0x332       // Cell Min/Max V, Delta mV, Brick Temp
    const val ID_TESLA_ALERT_MUX = 0x3E2       // Subsystem Alert Multiplexer

    fun parsePandaUdpDatagram(
        data: ByteArray,
        length: Int,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): Pair<VehicleTelemetrySnapshot, List<CanFramePacket>> {
        var updated = current.copy(timestampMs = timestampMs)
        val parsedFrames = mutableListOf<CanFramePacket>()
        var offset = 0

        while (offset + 16 <= length) {
            val header0 = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val header1 = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val canId = (header0 ushr 21) and 0x7FF
            val dlc = header1 and 0x0F
            val busId = (header1 ushr 4) and 0x0F
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
            offset += 16
        }

        if (parsedFrames.isNotEmpty()) {
            val combinedFrames = (parsedFrames.reversed() + updated.recentCanFrames).take(24)
            updated = updated.copy(recentCanFrames = combinedFrames)
        }
        return updated to parsedFrames
    }

    fun parseElm327AsciiLine(
        line: String,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long = System.currentTimeMillis()
    ): Pair<VehicleTelemetrySnapshot, CanFramePacket?> {
        val cleaned = line.trim().replace(" ", "")
        if (cleaned.length < 5) return current to null
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
            val combined = (listOf(frame) + nextSnap.recentCanFrames).take(24)
            nextSnap.copy(recentCanFrames = combined) to frame
        } catch (e: Exception) {
            current to null
        }
    }

    fun applyCanFrame(
        canId: Int,
        busId: Int,
        payload: ByteArray,
        current: VehicleTelemetrySnapshot,
        timestampMs: Long
    ): Pair<VehicleTelemetrySnapshot, String> {
        if (payload.size < 8) {
            return current to "CAN 0x%03X (Bus $busId) [${payload.size}B]".format(canId)
        }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        return when (canId) {
            ID_UI_SPEED_GEAR -> {
                val speedRaw = buf.getShort(0).toInt() and 0xFFFF
                val speedKph = (speedRaw * 0.05f).coerceIn(0f, 280f)
                val gearCode = payload[2].toInt() and 0x07
                val gear = when (gearCode) {
                    1 -> "P"
                    2 -> "R"
                    3 -> "N"
                    else -> "D"
                }
                val throttle = (payload[3].toInt() and 0xFF).toFloat().coerceIn(0f, 100f)
                val brake = (payload[4].toInt() and 0xFF).toFloat().coerceIn(0f, 100f)
                val sprint = ((payload[5].toInt() and 0xFF) * 0.02f + 2.5f).coerceIn(2.6f, 9.9f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    speedKph = speedKph,
                    currentGear = gear,
                    throttlePct = throttle,
                    brakePct = brake,
                    zeroToSixtySec = sprint,
                    flWheelKph = speedKph * 1.002f,
                    frWheelKph = speedKph * 0.998f,
                    rlWheelKph = speedKph * 1.005f,
                    rrWheelKph = speedKph * 1.004f
                )
                snap to "UI_Speed: ${"%.0f".format(speedKph)} km/h | Gear $gear | Thr ${"%.0f".format(throttle)}%"
            }

            ID_HV_BATTERY_VOLT_AMP -> {
                val voltsRaw = buf.getShort(0).toInt() and 0xFFFF
                val ampsSigned = buf.getShort(2).toInt()
                val chargeRaw = buf.getShort(4).toInt() and 0xFFFF
                val packV = (voltsRaw * 0.01f).coerceIn(280f, 415f)
                val packA = (ampsSigned * 0.1f).coerceIn(-700f, 1000f)
                val netKw = (packV * packA) / 1000f
                val chargeKw = (chargeRaw * 0.1f).coerceIn(0f, 260f)
                val regenKw = if (netKw < 0f && chargeKw == 0f) abs(netKw) else 0f
                val snap = current.copy(
                    timestampMs = timestampMs,
                    packVoltageV = packV,
                    packCurrentA = packA,
                    netPowerKw = netKw,
                    chargePowerKw = chargeKw,
                    regenPowerKw = regenKw
                )
                snap to "BMS_HV: ${"%.1f".format(packV)}V | ${"%.1f".format(packA)}A | ${"%.1f".format(netKw)} kW"
            }

            ID_REAR_DRIVE_UNIT -> {
                val trqSigned = buf.getShort(0).toInt()
                val rpmSigned = buf.getShort(2).toInt()
                val rearKwSigned = buf.getShort(4).toInt()
                val rearTrq = (trqSigned * 0.5f).coerceIn(-300f, 500f)
                val rearKw = (rearKwSigned * 0.1f).coerceIn(-80f, 260f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    rearTorqueNm = rearTrq,
                    rearRpm = rpmSigned,
                    rearMotorKw = rearKw
                )
                snap to "DI_Rear: ${"%.1f".format(rearKw)} kW | ${"%.0f".format(rearTrq)} Nm | $rpmSigned RPM"
            }

            ID_FRONT_DRIVE_UNIT -> {
                val trqSigned = buf.getShort(0).toInt()
                val rpmSigned = buf.getShort(2).toInt()
                val frontKwSigned = buf.getShort(4).toInt()
                val frontTrq = (trqSigned * 0.5f).coerceIn(-200f, 320f)
                val frontKw = (frontKwSigned * 0.1f).coerceIn(-50f, 165f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    frontTorqueNm = frontTrq,
                    frontRpm = rpmSigned,
                    frontMotorKw = frontKw
                )
                snap to "DI_Front: ${"%.1f".format(frontKw)} kW | ${"%.0f".format(frontTrq)} Nm"
            }

            ID_BMS_SOC_CAPACITY -> {
                val usableRaw = buf.getShort(0).toInt() and 0xFFFF
                val bmsRaw = buf.getShort(2).toInt() and 0xFFFF
                val nomFullRaw = buf.getShort(4).toInt() and 0xFFFF
                val initCapRaw = buf.getShort(6).toInt() and 0xFFFF
                val usableSoc = (usableRaw * 0.1f).coerceIn(0f, 100f)
                val bmsSoc = (bmsRaw * 0.1f).coerceIn(0f, 100f)
                val nomKwh = (nomFullRaw * 0.1f).coerceIn(50f, 82f)
                val initKwh = (initCapRaw * 0.1f).coerceIn(60f, 82f)
                val degPct = (((initKwh - nomKwh) / initKwh) * 100f).coerceIn(0f, 25f)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    usableSocPct = usableSoc,
                    bmsSocPct = bmsSoc,
                    nominalFullPackKwh = nomKwh,
                    initialPackKwh = initKwh,
                    degradationPct = degPct
                )
                snap to "BMS_SOC: Usable ${"%.1f".format(usableSoc)}% | Cap ${"%.1f".format(nomKwh)} kWh"
            }

            ID_BMS_CELL_BRICKS -> {
                val minCellMv = buf.getShort(0).toInt() and 0xFFFF
                val maxCellMv = buf.getShort(2).toInt() and 0xFFFF
                val minTempRaw = payload[4].toInt()
                val maxTempRaw = payload[5].toInt()
                val cellMinV = (minCellMv / 1000f).coerceIn(3.000f, 4.250f)
                val cellMaxV = (maxCellMv / 1000f).coerceIn(3.000f, 4.250f)
                val deltaMv = ((maxCellMv - minCellMv).toFloat()).coerceIn(1f, 120f)
                val spread = (cellMaxV - cellMinV).coerceAtLeast(0.002f)
                val bricks = List(12) { idx ->
                    val ratio = when (idx) {
                        3 -> 1.0f // highest brick
                        5 -> 0.0f // lowest brick
                        else -> 0.25f + ((idx * 37) % 60) / 100f
                    }
                    cellMinV + spread * ratio
                }
                val snap = current.copy(
                    timestampMs = timestampMs,
                    cellMinV = cellMinV,
                    cellMaxV = cellMaxV,
                    cellDeltaMv = deltaMv,
                    cellBrickVoltages = bricks,
                    packTempMinC = minTempRaw.toFloat(),
                    packTempMaxC = maxTempRaw.toFloat()
                )
                snap to "BMS_Cells: ${"%.3f".format(cellMinV)}V..${"%.3f".format(cellMaxV)}V (Δ${"%.0f".format(deltaMv)}mV)"
            }

            ID_VCFRONT_THERMAL -> {
                val battInlet = (payload[0].toInt()) * 0.5f
                val ptInlet = (payload[1].toInt()) * 0.5f
                val battLpm = (payload[2].toInt() and 0xFF) * 0.2f
                val ptLpm = (payload[3].toInt() and 0xFF) * 0.2f
                val octoPos = (payload[4].toInt() and 0x0F).coerceIn(1, 8)
                val cabinC = (payload[5].toInt()) * 0.5f
                val ambientC = (payload[6].toInt()) * 0.5f
                val hvacKw = (payload[7].toInt() and 0xFF) * 0.05f
                val octoLabel = octovalveStepDescription(octoPos)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    coolantBatteryInletC = battInlet,
                    coolantPowertrainInletC = ptInlet,
                    batteryCoolantLpm = battLpm,
                    powertrainCoolantLpm = ptLpm,
                    octovalvePosition = octoPos,
                    octovalveModeLabel = octoLabel,
                    cabinTempC = cabinC,
                    ambientTempC = ambientC,
                    hvacPowerKw = hvacKw,
                    chillerActive = hvacKw > 1.8f
                )
                snap to "VCFront: Octo Pos $octoPos | Batt ${"%.1f".format(battInlet)}°C (${"%.1f".format(battLpm)} L/m)"
            }

            ID_INVERTER_STATOR_TEMP -> {
                val rearInvC = (buf.getShort(0).toInt()) * 0.1f
                val frontInvC = (buf.getShort(2).toInt()) * 0.1f
                val statorC = (buf.getShort(4).toInt()) * 0.1f
                val snap = current.copy(
                    timestampMs = timestampMs,
                    rearInverterTempC = rearInvC.coerceIn(-20f, 130f),
                    frontInverterTempC = frontInvC.coerceIn(-20f, 130f),
                    statorTempC = statorC.coerceIn(-20f, 175f)
                )
                snap to "DI_Temps: R-Inv ${"%.1f".format(rearInvC)}°C | Stator ${"%.1f".format(statorC)}°C"
            }

            ID_ESP_DYNAMICS -> {
                val latGSigned = buf.getShort(0).toInt()
                val lonGSigned = buf.getShort(2).toInt()
                val brakeBarRaw = payload[4].toInt() and 0xFF
                val regenSplitRaw = payload[5].toInt() and 0xFF
                val steerSigned = buf.getShort(6).toInt()
                val latG = (latGSigned * 0.01f).coerceIn(-1.6f, 1.6f)
                val lonG = (lonGSigned * 0.01f).coerceIn(-1.6f, 1.6f)
                val brakeBar = (brakeBarRaw * 0.8f).coerceIn(0f, 150f)
                val regenSplit = regenSplitRaw.toFloat().coerceIn(0f, 100f)
                val steerDeg = (steerSigned * 0.1f).coerceIn(-450f, 450f)
                val newTrail = (current.gForceTrail + (latG to lonG)).takeLast(28)
                val snap = current.copy(
                    timestampMs = timestampMs,
                    lateralG = latG,
                    longitudinalG = lonG,
                    gForceTrail = newTrail,
                    brakePressureBar = brakeBar,
                    regenSplitPct = regenSplit,
                    steeringAngleDeg = steerDeg
                )
                snap to "ESP_Dyn: Lat ${"%.2f".format(latG)}G | Lon ${"%.2f".format(lonG)}G | Brk ${"%.0f".format(brakeBar)} bar"
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
     * Encodes a complete VehicleTelemetrySnapshot into a multi-frame binary Panda UDP packet
     * (9 x 16-byte frames = 144 bytes) so the Replay Engine and tests run through the exact
     * same binary CAN-bus decoder as live S3XY_OBD Wi-Fi UDP traffic.
     */
    fun encodeSnapshotToPandaUdpPacket(snapshot: VehicleTelemetrySnapshot): ByteArray {
        val out = ByteBuffer.allocate(9 * 16).order(ByteOrder.LITTLE_ENDIAN)

        fun writeFrame(canId: Int, busId: Int, writePayload: (ByteBuffer) -> Unit) {
            val header0 = (canId and 0x7FF) shl 21
            val header1 = ((busId and 0x0F) shl 4) or 8
            out.putInt(header0)
            out.putInt(header1)
            val payloadBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            writePayload(payloadBuf)
            out.put(payloadBuf.array())
        }

        // 1. 0x257 UI Speed & Gear
        writeFrame(ID_UI_SPEED_GEAR, 0) { b ->
            b.putShort((snapshot.speedKph / 0.05f).toInt().toShort())
            val gearByte = when (snapshot.currentGear) {
                "P" -> 1
                "R" -> 2
                "N" -> 3
                else -> 4
            }.toByte()
            b.put(gearByte)
            b.put(snapshot.throttlePct.toInt().coerceIn(0, 100).toByte())
            b.put(snapshot.brakePct.toInt().coerceIn(0, 100).toByte())
            b.put(((snapshot.zeroToSixtySec - 2.5f) / 0.02f).toInt().coerceIn(0, 255).toByte())
            b.put(0)
            b.put(0)
        }

        // 2. 0x132 HV Battery Volts & Amps
        writeFrame(ID_HV_BATTERY_VOLT_AMP, 0) { b ->
            b.putShort((snapshot.packVoltageV * 100f).toInt().toShort())
            b.putShort((snapshot.packCurrentA * 10f).toInt().toShort())
            b.putShort((snapshot.chargePowerKw * 10f).toInt().toShort())
            b.putShort(0)
        }

        // 3. 0x108 Rear Drive Unit
        writeFrame(ID_REAR_DRIVE_UNIT, 0) { b ->
            b.putShort((snapshot.rearTorqueNm * 2f).toInt().toShort())
            b.putShort(snapshot.rearRpm.toShort())
            b.putShort((snapshot.rearMotorKw * 10f).toInt().toShort())
            b.putShort(0)
        }

        // 4. 0x118 Front Drive Unit
        writeFrame(ID_FRONT_DRIVE_UNIT, 0) { b ->
            b.putShort((snapshot.frontTorqueNm * 2f).toInt().toShort())
            b.putShort(snapshot.frontRpm.toShort())
            b.putShort((snapshot.frontMotorKw * 10f).toInt().toShort())
            b.putShort(0)
        }

        // 5. 0x292 BMS SOC & Capacity
        writeFrame(ID_BMS_SOC_CAPACITY, 0) { b ->
            b.putShort((snapshot.usableSocPct * 10f).toInt().toShort())
            b.putShort((snapshot.bmsSocPct * 10f).toInt().toShort())
            b.putShort((snapshot.nominalFullPackKwh * 10f).toInt().toShort())
            b.putShort((snapshot.initialPackKwh * 10f).toInt().toShort())
        }

        // 6. 0x332 BMS Cell Bricks
        writeFrame(ID_BMS_CELL_BRICKS, 0) { b ->
            b.putShort((snapshot.cellMinV * 1000f).toInt().toShort())
            b.putShort((snapshot.cellMaxV * 1000f).toInt().toShort())
            b.put(snapshot.packTempMinC.toInt().toByte())
            b.put(snapshot.packTempMaxC.toInt().toByte())
            b.putShort(0)
        }

        // 7. 0x241 VCFront Thermal & Octovalve
        writeFrame(ID_VCFRONT_THERMAL, 1) { b ->
            b.put((snapshot.coolantBatteryInletC * 2f).toInt().toByte())
            b.put((snapshot.coolantPowertrainInletC * 2f).toInt().toByte())
            b.put((snapshot.batteryCoolantLpm * 5f).toInt().toByte())
            b.put((snapshot.powertrainCoolantLpm * 5f).toInt().toByte())
            b.put(snapshot.octovalvePosition.coerceIn(1, 8).toByte())
            b.put((snapshot.cabinTempC * 2f).toInt().toByte())
            b.put((snapshot.ambientTempC * 2f).toInt().toByte())
            b.put((snapshot.hvacPowerKw * 20f).toInt().toByte())
        }

        // 8. 0x315 Inverter & Stator Temps
        writeFrame(ID_INVERTER_STATOR_TEMP, 0) { b ->
            b.putShort((snapshot.rearInverterTempC * 10f).toInt().toShort())
            b.putShort((snapshot.frontInverterTempC * 10f).toInt().toShort())
            b.putShort((snapshot.statorTempC * 10f).toInt().toShort())
            b.putShort(0)
        }

        // 9. 0x145 ESP Dynamics & G-Force
        writeFrame(ID_ESP_DYNAMICS, 0) { b ->
            b.putShort((snapshot.lateralG * 100f).toInt().toShort())
            b.putShort((snapshot.longitudinalG * 100f).toInt().toShort())
            b.put((snapshot.brakePressureBar / 0.8f).toInt().coerceIn(0, 255).toByte())
            b.put(snapshot.regenSplitPct.toInt().coerceIn(0, 100).toByte())
            b.putShort((snapshot.steeringAngleDeg * 10f).toInt().toShort())
        }

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
        else -> "Step 8: Sub-Zero Series Stator Heat Recovery"
    }
}
