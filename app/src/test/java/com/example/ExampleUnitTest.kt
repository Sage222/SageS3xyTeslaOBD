package com.example

import com.example.data.can.Model3CanFrameDecoder
import com.example.model.VehicleTelemetrySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            rearInverterTempC = 78.0f,
            statorTempC = 112.0f
        )

        val packet = Model3CanFrameDecoder.encodeSnapshotToPandaUdpPacket(original)
        assertTrue(packet.size >= 8 * 16)

        val (decoded, frames) = Model3CanFrameDecoder.parsePandaUdpDatagram(
            data = packet,
            length = packet.size,
            current = VehicleTelemetrySnapshot()
        )

        assertTrue(frames.isNotEmpty())
        assertEquals(145.0f, decoded.speedKph, 0.5f)
        assertEquals("D", decoded.currentGear)
        assertEquals(376.5f, decoded.packVoltageV, 0.2f)
        assertEquals(750.0f, decoded.packCurrentA, 0.5f)
        assertEquals(185.0f, decoded.rearMotorKw, 0.5f)
        assertEquals(320.0f, decoded.rearTorqueNm, 2.0f)
        assertEquals(71.5f, decoded.usableSocPct, 0.2f)
        assertEquals(22.0f, decoded.cellDeltaMv, 2.0f)
        assertEquals(4, decoded.octovalvePosition)
        assertEquals(112.0f, decoded.statorTempC, 1.0f)
    }

    @Test
    fun pandaFilterSubscriptionPacket_buildsBigEndian0x0FTriplets() {
        val filterPacket = Model3CanFrameDecoder.buildPandaFilterSubscriptionPacket(
            intArrayOf(0x257, 0x132, 0x332)
        )
        assertEquals(1 + 3 * 3, filterPacket.size)
        assertEquals(0x0F.toByte(), filterPacket[0])
        // First ID: 0x0257 -> [0xFF, 0x02, 0x57]
        assertEquals(0xFF.toByte(), filterPacket[1])
        assertEquals(0x02.toByte(), filterPacket[2])
        assertEquals(0x57.toByte(), filterPacket[3])
    }
}
