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

class TelemetryRepository(
    private val dashboardDao: DashboardDao,
    private val alertDao: AlertDao,
    private val sessionDao: TelemetrySessionDao
) {
    val dashboardsFlow: Flow<List<DashboardPresetEntity>> = dashboardDao.observeAllDashboards()
    val alertsFlow: Flow<List<VehicleAlertEntity>> = alertDao.observeAllAlerts()
    val sessionsFlow: Flow<List<TelemetrySessionEntity>> = sessionDao.observeAllSessions()

    suspend fun ensureSeeded() {
        // Only seed the 4 empty gauge layout presets; never seed fake alerts or demo sessions.
        if (dashboardDao.countDashboards() == 0) {
            dashboardDao.insertAllDashboards(CockpitJsonSerializer.createFactoryDashboards())
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

    suspend fun insertOrUpdateLiveAlert(alert: VehicleAlertEntity): Long {
        val existing = alertDao.findActiveAlertByCode(alert.alertCode)
        return if (existing != null) {
            existing.id
        } else {
            alertDao.insertAlert(alert)
        }
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
}
