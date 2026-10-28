package com.example.data.repository

import com.example.data.local.AlertDao
import com.example.data.local.CockpitJsonSerializer
import com.example.data.local.DashboardDao
import com.example.data.local.DashboardPresetEntity
import com.example.data.local.TelemetrySampleEntity
import com.example.data.local.TelemetrySessionDao
import com.example.data.local.TelemetrySessionEntity
import com.example.data.local.VehicleAlertEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin

class TelemetryRepository(
    private val dashboardDao: DashboardDao,
    private val alertDao: AlertDao,
    private val sessionDao: TelemetrySessionDao
) {
    val dashboardsFlow: Flow<List<DashboardPresetEntity>> = dashboardDao.observeAllDashboards()
    val alertsFlow: Flow<List<VehicleAlertEntity>> = alertDao.observeAllAlerts()
    val sessionsFlow: Flow<List<TelemetrySessionEntity>> = sessionDao.observeAllSessions()

    suspend fun ensureSeeded() {
        if (dashboardDao.countDashboards() == 0) {
            dashboardDao.insertAllDashboards(CockpitJsonSerializer.createFactoryDashboards())
        }
        if (alertDao.countAlerts() == 0) {
            alertDao.insertAllAlerts(CockpitJsonSerializer.createInitialTeslaAlerts())
        }
        if (sessionDao.countSessions() == 0) {
            seedSampleDriveSessions()
        }
    }

    suspend fun resetFactoryDashboards() {
        dashboardDao.deleteAllDashboards()
        dashboardDao.insertAllDashboards(CockpitJsonSerializer.createFactoryDashboards())
    }

    suspend fun saveDashboardPreset(preset: DashboardPresetEntity): Long {
        return dashboardDao.insertDashboard(preset)
    }

    suspend fun updateDashboardPreset(preset: DashboardPresetEntity) {
        dashboardDao.updateDashboard(preset)
    }

    suspend fun deleteDashboardPreset(id: Long) {
        dashboardDao.deleteDashboardById(id)
    }

    suspend fun insertAlert(alert: VehicleAlertEntity): Long {
        return alertDao.insertAlert(alert)
    }

    suspend fun clearAndAcknowledgeActiveAlerts() {
        alertDao.clearAndAcknowledgeActiveAlerts()
    }

    suspend fun acknowledgeAlert(id: Long) {
        alertDao.acknowledgeAlertById(id)
    }

    suspend fun purgeHistoricalAlerts() {
        alertDao.purgeHistoricalAlerts()
    }

    suspend fun createSession(session: TelemetrySessionEntity): Long {
        return sessionDao.insertSession(session)
    }

    suspend fun updateSession(session: TelemetrySessionEntity) {
        sessionDao.updateSession(session)
    }

    suspend fun insertSample(sample: TelemetrySampleEntity) {
        sessionDao.insertSample(sample)
    }

    suspend fun getSamplesForSession(sessionId: Long): List<TelemetrySampleEntity> {
        return sessionDao.getSamplesForSession(sessionId)
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteSamplesForSession(sessionId)
        sessionDao.deleteSessionById(sessionId)
    }

    suspend fun clearAllSessions() {
        sessionDao.deleteAllSamples()
        sessionDao.deleteAllSessions()
    }

    private suspend fun seedSampleDriveSessions() {
        val now = System.currentTimeMillis()
        val canyonStart = now - 1000L * 60 * 95
        val canyonSessionId = sessionDao.insertSession(
            TelemetrySessionEntity(
                title = "Angles Crest Canyon Run (2023 M3 AWD)",
                sourceMode = "S3XY_OBD UDP:1338",
                startTimeMs = canyonStart,
                endTimeMs = canyonStart + 180_000L,
                sampleCount = 60,
                maxSpeedKph = 184.5f,
                peakPowerKw = 342.8f,
                maxRegenKw = -78.4f,
                minSocPct = 68.2f,
                maxSocPct = 76.5f,
                maxCellDeltaMv = 24.0f,
                maxStatorTempC = 122.4f,
                notes = "Dual motor launch + high regen hairpin braking test"
            )
        )

        val canyonSamples = List(60) { i ->
            val t = i * 0.25f
            val speed = (95f + 78f * sin(t * 0.4f)).coerceIn(30f, 185f)
            val netKw = (295f * cos(t * 0.45f)).coerceIn(-78f, 342.8f)
            val soc = 76.5f - (i / 60f) * 8.3f
            val packV = 382f - netKw * 0.08f
            val packA = (netKw * 1000f) / packV
            TelemetrySampleEntity(
                sessionId = canyonSessionId,
                elapsedMs = i * 3000L,
                speedKph = speed,
                netPowerKw = netKw,
                rearMotorKw = netKw * 0.64f,
                frontMotorKw = netKw * 0.36f,
                usableSocPct = soc,
                packVoltageV = packV,
                packCurrentA = packA,
                cellDeltaMv = 10f + kotlin.math.abs(netKw) * 0.04f,
                statorTempC = 64f + (i / 60f) * 58f,
                coolantBatteryInletC = 32f + (i / 60f) * 11f,
                lateralG = 0.82f * sin(t * 0.6f),
                longitudinalG = (netKw / 340f) * 0.85f,
                brakePressureBar = if (netKw < -30f) 48f else 0f
            )
        }
        sessionDao.insertSamples(canyonSamples)

        val scStart = now - 1000L * 60 * 320
        val scSessionId = sessionDao.insertSession(
            TelemetrySessionEntity(
                title = "V3 Supercharger 250kW Taper Curve",
                sourceMode = "S3XY_OBD UDP:1338",
                startTimeMs = scStart,
                endTimeMs = scStart + 900_000L,
                sampleCount = 45,
                maxSpeedKph = 0.0f,
                peakPowerKw = -249.6f,
                maxRegenKw = -249.6f,
                minSocPct = 14.0f,
                maxSocPct = 80.0f,
                maxCellDeltaMv = 19.0f,
                maxStatorTempC = 54.0f,
                notes = "Preconditioned pack to 47°C via Octovalve Step 7"
            )
        )

        val scSamples = List(45) { i ->
            val frac = i / 44f
            val soc = 14f + frac * 66f
            val taper = if (soc < 36f) 1f else (1f - (soc - 36f) / 60f).coerceIn(0.25f, 1f)
            val chargeKw = 249.6f * taper
            val packV = 350f + frac * 48f
            TelemetrySampleEntity(
                sessionId = scSessionId,
                elapsedMs = i * 20_000L,
                speedKph = 0f,
                netPowerKw = -chargeKw,
                rearMotorKw = 0f,
                frontMotorKw = 0f,
                usableSocPct = soc,
                packVoltageV = packV,
                packCurrentA = -(chargeKw * 1000f / packV),
                cellDeltaMv = 12f + 7f * taper,
                statorTempC = 48f + 6f * taper,
                coolantBatteryInletC = 46.5f,
                lateralG = 0f,
                longitudinalG = 0f,
                brakePressureBar = 0f
            )
        }
        sessionDao.insertSamples(scSamples)
    }
}
