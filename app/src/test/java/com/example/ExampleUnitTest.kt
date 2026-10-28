package com.example

import com.example.data.can.Model3CanFrameDecoder
import com.example.model.VehicleTelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun pandaUdpCanFrameRoundTrip_decodes2023Model3SignalsAccurately() {
        val original = VehicleTelemetrySnapshot(
            speedKph = 145.0f,
            currentGear = "D",
            throttlePct = 82.0f,
            brakePct = 0.0f,
            packVoltageV = 376.5f,
            packCurrentA = 750.0f,
            rearMotorKw = 185.0f,
            frontMotorKw = 97.4f,
            rearTorqueNm = 320.0f,
            frontTorqueNm = 175.0f,
            usableSocPct = 71.5f,
            bmsSocPct = 73.2f,
            nominalFullPackKwh = 76.8f,
            initialPackKwh = 79.5f,
            cellMinV = 3.890f,
            cellMaxV = 3.912f,
            coolantBatteryInletC = 36.0f,
            coolantPowertrainInletC = 48.5f,
            octovalvePosition = 4,
            rearInverterTempC = 78.4f,
            statorTempC = 112.6f,
            lateralG = 0.65f,
            longitudinalG = 0.72f,
            brakePressureBar = 0.0f
        )

        val packet = Model3CanFrameDecoder.encodeSnapshotToPandaUdpPacket(original)
        assertEquals(9 * 16, packet.size)

        val (decoded, frames) = Model3CanFrameDecoder.parsePandaUdpDatagram(
            data = packet,
            length = packet.size,
            current = VehicleTelemetrySnapshot()
        )

        assertEquals(9, frames.size)
        assertEquals(145.0f, decoded.speedKph, 0.2f)
        assertEquals(376.5f, decoded.packVoltageV, 0.2f)
        assertEquals(750.0f, decoded.packCurrentA, 0.5f)
        assertEquals(71.5f, decoded.usableSocPct, 0.2f)
        assertEquals(22.0f, decoded.cellDeltaMv, 1.0f)
        assertEquals(4, decoded.octovalvePosition)
        assertEquals(112.6f, decoded.statorTempC, 0.2f)
    }
}
