package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.can.Model3CanFrameDecoder
import com.example.data.can.S3xyTransportEngine
import com.example.data.export.TelemetryExportManager
import com.example.data.local.AppDatabase
import com.example.data.local.CockpitJsonSerializer
import com.example.data.local.DashboardPresetEntity
import com.example.data.local.TelemetrySampleEntity
import com.example.data.local.TelemetrySessionEntity
import com.example.data.local.VehicleAlertEntity
import com.example.data.repository.TelemetryRepository
import com.example.model.ConnectionStatus
import com.example.model.DashboardWidgetConfig
import com.example.model.MetricPid
import com.example.model.S3xyProtocol
import com.example.model.TeslaSubsystem
import com.example.model.VehicleTelemetrySnapshot
import com.example.model.WidgetSpan
import com.example.model.WidgetStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class CockpitTab(val title: String) {
    COCKPIT("Cockpit"),
    CHASSIS_XRAY("X-Ray Flow"),
    ALERTS_DTC("Alerts / DTC"),
    LOGS_REPLAY("Logs")
}

data class CockpitUiState(
    val currentTab: CockpitTab = CockpitTab.COCKPIT,
    val isDarkTheme: Boolean = true,
    // S3XY_OBD Live Wi-Fi Connection State
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusMessage: String = "Offline — Tap to connect to S3XY_OBD Wi-Fi (192.168.4.1)",
    val s3xySsid: String = "S3XY_OBD",
    val s3xyPassphrase: String = "12345678",
    val s3xyHostIp: String = "192.168.4.1",
    val s3xyPort: Int = 1338,
    val s3xyProtocol: S3xyProtocol = S3xyProtocol.PANDA_UDP_1338,
    val busFrameRateFps: Int = 0,
    val showConnectionSheet: Boolean = false,
    // Live Telemetry & History Sparklines
    val snapshot: VehicleTelemetrySnapshot = VehicleTelemetrySnapshot(),
    val pidSparklineHistory: Map<MetricPid, List<Float>> = emptyMap(),
    val peakHoldValues: Map<MetricPid, Float> = emptyMap(),
    // Dashboard & Layout Customization
    val selectedDashboardId: Long = 1L,
    val isEditLayoutMode: Boolean = false,
    val showAddWidgetDialog: Boolean = false,
    val showNewDashboardDialog: Boolean = false,
    // Diagnostics & DTC Interrogation
    val selectedSubsystemFilter: TeslaSubsystem = TeslaSubsystem.ALL,
    val showActiveAlertsOnly: Boolean = false,
    val selectedAlertId: Long? = null,
    val isInterrogatingBus: Boolean = false,
    // Session Recording & Logs
    val isRecordingSession: Boolean = false,
    val activeRecordingSessionId: Long? = null,
    val recordingElapsedSec: Int = 0,
    val recordingSampleCount: Int = 0,
    val selectedLogSessionId: Long? = null,
    val selectedLogSamples: List<TelemetrySampleEntity> = emptyList(),
    // User feedback banner
    val snackbarMessage: String? = null
)

class TelemetryCockpitViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TelemetryRepository(
        dashboardDao = database.dashboardDao(),
        alertDao = database.alertDao(),
        sessionDao = database.telemetrySessionDao()
    )

    private val _uiState = MutableStateFlow(CockpitUiState())
    val uiState: StateFlow<CockpitUiState> = _uiState.asStateFlow()

    val dashboards: StateFlow<List<DashboardPresetEntity>> = repository.dashboardsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alerts: StateFlow<List<VehicleAlertEntity>> = repository.alertsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<TelemetrySessionEntity>> = repository.sessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var recordingJob: Job? = null
    private var recordingStartMs: Long = 0L
    private var recMaxSpeed: Float = 0f
    private var recPeakKw: Float = 0f
    private var recMaxRegenKw: Float = 0f
    private var recMinSoc: Float = 100f
    private var recMaxSoc: Float = 0f
    private var recMaxDeltaMv: Float = 0f
    private var recMaxStatorC: Float = 0f

    private val transportEngine = S3xyTransportEngine(
        context = application.applicationContext,
        scope = viewModelScope,
        onBinaryDatagramReceived = { bytes, len, fps ->
            handleIncomingBinaryPacket(bytes, len, fps)
        },
        onAsciiLineReceived = { line, fps ->
            handleIncomingAsciiLine(line, fps)
        },
        onSocketStateChanged = { connected, message ->
            _uiState.update { state ->
                val status = when {
                    connected -> ConnectionStatus.CONNECTED_LIVE
                    message.contains("Connecting") -> ConnectionStatus.CONNECTING
                    else -> ConnectionStatus.SOCKET_ERROR
                }
                state.copy(
                    connectionStatus = status,
                    busFrameRateFps = if (connected) state.busFrameRateFps else 0,
                    statusMessage = message
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            // Attempt automatic live connection to 192.168.4.1:1338 if user is already on S3XY_OBD Wi-Fi
            val st = _uiState.value
            transportEngine.startLiveSocket(st.s3xyHostIp, st.s3xyPort, st.s3xyProtocol)
        }
    }

    private fun handleIncomingBinaryPacket(bytes: ByteArray, length: Int, fps: Int) {
        val batch = Model3CanFrameDecoder.parsePandaUdpDatagramWithAlerts(
            data = bytes,
            length = length,
            current = _uiState.value.snapshot
        )
        _uiState.update { state ->
            updateTelemetryMaps(state, batch.snapshot, fps)
        }
        if (batch.alerts.isNotEmpty()) {
            viewModelScope.launch {
                batch.alerts.forEach { repository.insertOrUpdateLiveAlert(it) }
            }
        }
    }

    private fun handleIncomingAsciiLine(line: String, fps: Int) {
        val batch = Model3CanFrameDecoder.parseElm327AsciiLineWithAlerts(
            line = line,
            current = _uiState.value.snapshot
        )
        _uiState.update { state ->
            updateTelemetryMaps(state, batch.snapshot, fps)
        }
        if (batch.alerts.isNotEmpty()) {
            viewModelScope.launch {
                batch.alerts.forEach { repository.insertOrUpdateLiveAlert(it) }
            }
        }
    }

    private fun updateTelemetryMaps(
        state: CockpitUiState,
        nextSnap: VehicleTelemetrySnapshot,
        fps: Int
    ): CockpitUiState {
        val updatedSparklines = state.pidSparklineHistory.toMutableMap()
        val updatedPeaks = state.peakHoldValues.toMutableMap()

        for (pid in MetricPid.entries) {
            val value = nextSnap.getValue(pid)
            val prevList = updatedSparklines[pid] ?: emptyList()
            updatedSparklines[pid] = (prevList + value).takeLast(36)

            val prevPeak = updatedPeaks[pid]
            if (prevPeak == null || kotlin.math.abs(value) > kotlin.math.abs(prevPeak)) {
                updatedPeaks[pid] = value
            }
        }

        return state.copy(
            snapshot = nextSnap,
            busFrameRateFps = fps,
            pidSparklineHistory = updatedSparklines,
            peakHoldValues = updatedPeaks
        )
    }

    // Navigation & Theme
    fun selectTab(tab: CockpitTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun setShowConnectionSheet(show: Boolean) {
        _uiState.update { it.copy(showConnectionSheet = show) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // S3XY_OBD Live Wi-Fi Controls
    fun updateS3xyNetworkConfig(
        ssid: String,
        passphrase: String,
        hostIp: String,
        port: Int,
        protocol: S3xyProtocol
    ) {
        _uiState.update {
            it.copy(
                s3xySsid = ssid.trim().ifEmpty { "S3XY_OBD" },
                s3xyPassphrase = passphrase,
                s3xyHostIp = hostIp.trim().ifEmpty { "192.168.4.1" },
                s3xyPort = port,
                s3xyProtocol = protocol
            )
        }
    }

    fun connectToLiveS3xyWifi(bindOsWifiFirst: Boolean = false) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                statusMessage = "Binding to ${state.s3xySsid} (${state.s3xyHostIp}:${state.s3xyPort})..."
            )
        }

        if (bindOsWifiFirst) {
            transportEngine.requestS3xyWifiBinding(state.s3xySsid, state.s3xyPassphrase) { ok, msg ->
                _uiState.update { it.copy(statusMessage = msg, snackbarMessage = msg) }
                if (ok) {
                    transportEngine.startLiveSocket(state.s3xyHostIp, state.s3xyPort, state.s3xyProtocol)
                } else {
                    _uiState.update { it.copy(connectionStatus = ConnectionStatus.SOCKET_ERROR) }
                }
            }
        } else {
            transportEngine.startLiveSocket(state.s3xyHostIp, state.s3xyPort, state.s3xyProtocol)
        }
    }

    fun disconnectS3xySocket() {
        transportEngine.stopAll()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                busFrameRateFps = 0,
                statusMessage = "Disconnected from S3XY_OBD socket",
                snackbarMessage = "Disconnected from S3XY_OBD socket"
            )
        }
    }

    fun resetPeakHoldValues() {
        _uiState.update { it.copy(peakHoldValues = emptyMap(), snackbarMessage = "Peak-hold gauge markers reset") }
    }

    // Custom Dashboard Configuration
    fun selectDashboard(dashboardId: Long) {
        _uiState.update { it.copy(selectedDashboardId = dashboardId) }
    }

    fun toggleEditLayoutMode() {
        _uiState.update { it.copy(isEditLayoutMode = !it.isEditLayoutMode) }
    }

    fun setShowAddWidgetDialog(show: Boolean) {
        _uiState.update { it.copy(showAddWidgetDialog = show) }
    }

    fun setShowNewDashboardDialog(show: Boolean) {
        _uiState.update { it.copy(showNewDashboardDialog = show) }
    }

    fun addWidgetToActiveDashboard(pid: MetricPid, style: WidgetStyle, span: WidgetSpan) {
        val activeDash = dashboards.value.find { it.id == _uiState.value.selectedDashboardId }
            ?: dashboards.value.firstOrNull() ?: return
        val currentWidgets = CockpitJsonSerializer.decodeWidgets(activeDash.widgetsJson).toMutableList()
        val newWidget = DashboardWidgetConfig(
            id = "${pid.name}_${System.currentTimeMillis()}",
            pid = pid,
            style = style,
            span = span,
            warningThreshold = pid.defaultWarningThreshold
        )
        currentWidgets.add(newWidget)
        viewModelScope.launch {
            repository.updateDashboardPreset(
                activeDash.copy(widgetsJson = CockpitJsonSerializer.encodeWidgets(currentWidgets))
            )
            _uiState.update {
                it.copy(
                    showAddWidgetDialog = false,
                    snackbarMessage = "Added ${pid.shortLabel} (${style.label}) to ${activeDash.name}"
                )
            }
        }
    }

    fun removeWidgetFromActiveDashboard(widgetId: String) {
        val activeDash = dashboards.value.find { it.id == _uiState.value.selectedDashboardId }
            ?: dashboards.value.firstOrNull() ?: return
        val currentWidgets = CockpitJsonSerializer.decodeWidgets(activeDash.widgetsJson)
            .filterNot { it.id == widgetId }
        viewModelScope.launch {
            repository.updateDashboardPreset(
                activeDash.copy(widgetsJson = CockpitJsonSerializer.encodeWidgets(currentWidgets))
            )
        }
    }

    fun cycleWidgetSpan(widgetId: String) {
        val activeDash = dashboards.value.find { it.id == _uiState.value.selectedDashboardId }
            ?: dashboards.value.firstOrNull() ?: return
        val updated = CockpitJsonSerializer.decodeWidgets(activeDash.widgetsJson).map { w ->
            if (w.id == widgetId) {
                val nextSpan = when (w.span) {
                    WidgetSpan.COMPACT_1X1 -> WidgetSpan.WIDE_2X1
                    WidgetSpan.WIDE_2X1 -> WidgetSpan.LARGE_2X2
                    WidgetSpan.LARGE_2X2 -> WidgetSpan.COMPACT_1X1
                }
                w.copy(span = nextSpan)
            } else w
        }
        viewModelScope.launch {
            repository.updateDashboardPreset(
                activeDash.copy(widgetsJson = CockpitJsonSerializer.encodeWidgets(updated))
            )
        }
    }

    fun cycleWidgetStyle(widgetId: String) {
        val activeDash = dashboards.value.find { it.id == _uiState.value.selectedDashboardId }
            ?: dashboards.value.firstOrNull() ?: return
        val styles = listOf(
            WidgetStyle.RADIAL_GAUGE,
            WidgetStyle.SPARKLINE_CARD,
            WidgetStyle.HORIZONTAL_BAR,
            WidgetStyle.DIGITAL_READOUT,
            WidgetStyle.BI_DIRECTIONAL_ARC
        )
        val updated = CockpitJsonSerializer.decodeWidgets(activeDash.widgetsJson).map { w ->
            if (w.id == widgetId) {
                val idx = styles.indexOf(w.style)
                val nextStyle = styles[(idx + 1) % styles.size]
                w.copy(style = nextStyle)
            } else w
        }
        viewModelScope.launch {
            repository.updateDashboardPreset(
                activeDash.copy(widgetsJson = CockpitJsonSerializer.encodeWidgets(updated))
            )
        }
    }

    fun moveWidgetOrder(widgetId: String, moveUp: Boolean) {
        val activeDash = dashboards.value.find { it.id == _uiState.value.selectedDashboardId }
            ?: dashboards.value.firstOrNull() ?: return
        val list = CockpitJsonSerializer.decodeWidgets(activeDash.widgetsJson).toMutableList()
        val idx = list.indexOfFirst { it.id == widgetId }
        if (idx == -1) return
        val targetIdx = if (moveUp) max(0, idx - 1) else min(list.lastIndex, idx + 1)
        if (idx == targetIdx) return
        val item = list.removeAt(idx)
        list.add(targetIdx, item)
        viewModelScope.launch {
            repository.updateDashboardPreset(
                activeDash.copy(widgetsJson = CockpitJsonSerializer.encodeWidgets(list))
            )
        }
    }

    fun createCustomDashboard(name: String, subtitle: String) {
        val cleanName = name.trim().ifEmpty { "Custom Track Layout" }
        val cleanSub = subtitle.trim().ifEmpty { "User-configured 2023 Model 3 telemetry cockpit" }
        val starterWidgets = listOf(
            DashboardWidgetConfig("cust_1", MetricPid.SPEED_KPH, WidgetStyle.RADIAL_GAUGE, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("cust_2", MetricPid.NET_POWER_KW, WidgetStyle.BI_DIRECTIONAL_ARC, WidgetSpan.LARGE_2X2),
            DashboardWidgetConfig("cust_3", MetricPid.USABLE_SOC_PCT, WidgetStyle.HORIZONTAL_BAR, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("cust_4", MetricPid.CELL_DELTA_MV, WidgetStyle.DIGITAL_READOUT, WidgetSpan.COMPACT_1X1),
            DashboardWidgetConfig("cust_5", MetricPid.STATOR_TEMP_C, WidgetStyle.SPARKLINE_CARD, WidgetSpan.WIDE_2X1)
        )
        viewModelScope.launch {
            val newId = repository.saveDashboardPreset(
                DashboardPresetEntity(
                    name = cleanName,
                    subtitle = cleanSub,
                    categoryTag = "CUSTOM",
                    isBuiltIn = false,
                    sortOrder = dashboards.value.size + 1,
                    widgetsJson = CockpitJsonSerializer.encodeWidgets(starterWidgets)
                )
            )
            _uiState.update {
                it.copy(
                    selectedDashboardId = newId,
                    showNewDashboardDialog = false,
                    isEditLayoutMode = true,
                    snackbarMessage = "Created custom dashboard '$cleanName'"
                )
            }
        }
    }

    fun deleteCustomDashboard(dashboard: DashboardPresetEntity) {
        if (dashboard.isBuiltIn) return
        viewModelScope.launch {
            repository.deleteDashboardPreset(dashboard.id)
            val fallbackId = dashboards.value.firstOrNull { it.id != dashboard.id }?.id ?: 1L
            _uiState.update {
                it.copy(
                    selectedDashboardId = fallbackId,
                    snackbarMessage = "Deleted dashboard '${dashboard.name}'"
                )
            }
        }
    }

    fun resetDashboardsToFactory() {
        viewModelScope.launch {
            repository.resetFactoryDashboards()
            _uiState.update {
                it.copy(
                    selectedDashboardId = 1L,
                    isEditLayoutMode = false,
                    snackbarMessage = "Restored all 4 factory 2023 Model 3 dashboards"
                )
            }
        }
    }

    // Diagnostic Alert Interrogation & Clear Commands (Strictly Real S3XY_OBD Socket)
    fun selectSubsystemFilter(subsystem: TeslaSubsystem) {
        _uiState.update { it.copy(selectedSubsystemFilter = subsystem) }
    }

    fun toggleActiveAlertsOnly() {
        _uiState.update { it.copy(showActiveAlertsOnly = !it.showActiveAlertsOnly) }
    }

    fun selectAlertForInspection(alertId: Long?) {
        _uiState.update { it.copy(selectedAlertId = alertId) }
    }

    fun interrogateCanBusSubsystems() {
        if (_uiState.value.isInterrogatingBus) return
        _uiState.update { it.copy(isInterrogatingBus = true) }
        transportEngine.sendCanInterrogationRequest { _, msg ->
            _uiState.update {
                it.copy(
                    isInterrogatingBus = false,
                    snackbarMessage = msg
                )
            }
        }
    }

    fun clearAndAcknowledgeFaults() {
        viewModelScope.launch {
            repository.clearAndAcknowledgeActiveAlerts()
            transportEngine.sendClearFaultsRequest { _, msg ->
                _uiState.update { it.copy(snackbarMessage = msg) }
            }
        }
    }

    fun acknowledgeSingleAlert(alertId: Long) {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId)
            _uiState.update { it.copy(snackbarMessage = "Alert acknowledged") }
        }
    }

    fun purgeHistoricalAlerts() {
        viewModelScope.launch {
            repository.purgeHistoricalAlerts()
            _uiState.update {
                it.copy(
                    selectedAlertId = null,
                    snackbarMessage = "Cleared historical diagnostic alerts"
                )
            }
        }
    }

    // Telemetry Session Recording & Clear Logging
    fun toggleSessionRecording() {
        if (_uiState.value.isRecordingSession) {
            stopSessionRecording()
        } else {
            if (_uiState.value.connectionStatus != ConnectionStatus.CONNECTED_LIVE) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Connect to S3XY_OBD Wi-Fi (192.168.4.1) first before recording live vehicle telemetry."
                    )
                }
                return
            }
            startSessionRecording()
        }
    }

    private fun startSessionRecording() {
        recordingJob?.cancel()
        val now = System.currentTimeMillis()
        recordingStartMs = now
        val snap = _uiState.value.snapshot
        recMaxSpeed = snap.speedKph
        recPeakKw = snap.netPowerKw
        recMaxRegenKw = min(0f, snap.netPowerKw)
        recMinSoc = snap.usableSocPct
        recMaxSoc = snap.usableSocPct
        recMaxDeltaMv = snap.cellDeltaMv
        recMaxStatorC = snap.statorTempC

        val stamp = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(now))
        val modeLabel = "S3XY_OBD (${_uiState.value.s3xyHostIp}:${_uiState.value.s3xyPort})"

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val sessionId = repository.createSession(
                TelemetrySessionEntity(
                    title = "Model 3 Live Log ($stamp)",
                    sourceMode = modeLabel,
                    startTimeMs = now,
                    endTimeMs = now,
                    sampleCount = 0,
                    maxSpeedKph = recMaxSpeed,
                    peakPowerKw = recPeakKw,
                    maxRegenKw = recMaxRegenKw,
                    minSocPct = recMinSoc,
                    maxSocPct = recMaxSoc,
                    maxCellDeltaMv = recMaxDeltaMv,
                    maxStatorTempC = recMaxStatorC
                )
            )

            _uiState.update {
                it.copy(
                    isRecordingSession = true,
                    activeRecordingSessionId = sessionId,
                    recordingElapsedSec = 0,
                    recordingSampleCount = 0,
                    snackbarMessage = "Recording live S3XY_OBD telemetry to device storage..."
                )
            }

            var count = 0
            while (isActive) {
                val cur = _uiState.value.snapshot
                val elapsed = System.currentTimeMillis() - recordingStartMs
                count++
                recMaxSpeed = max(recMaxSpeed, cur.speedKph)
                recPeakKw = max(recPeakKw, cur.netPowerKw)
                recMaxRegenKw = min(recMaxRegenKw, cur.netPowerKw)
                recMinSoc = min(recMinSoc, cur.usableSocPct)
                recMaxSoc = max(recMaxSoc, cur.usableSocPct)
                recMaxDeltaMv = max(recMaxDeltaMv, cur.cellDeltaMv)
                recMaxStatorC = max(recMaxStatorC, cur.statorTempC)

                repository.insertSample(
                    TelemetrySampleEntity(
                        sessionId = sessionId,
                        elapsedMs = elapsed,
                        speedKph = cur.speedKph,
                        netPowerKw = cur.netPowerKw,
                        rearMotorKw = cur.rearMotorKw,
                        frontMotorKw = cur.frontMotorKw,
                        usableSocPct = cur.usableSocPct,
                        packVoltageV = cur.packVoltageV,
                        packCurrentA = cur.packCurrentA,
                        cellDeltaMv = cur.cellDeltaMv,
                        statorTempC = cur.statorTempC,
                        coolantBatteryInletC = cur.coolantBatteryInletC,
                        lateralG = cur.lateralG,
                        longitudinalG = cur.longitudinalG,
                        brakePressureBar = cur.brakePressureBar
                    )
                )

                repository.updateSession(
                    TelemetrySessionEntity(
                        id = sessionId,
                        title = "Model 3 Live Log ($stamp)",
                        sourceMode = modeLabel,
                        startTimeMs = recordingStartMs,
                        endTimeMs = System.currentTimeMillis(),
                        sampleCount = count,
                        maxSpeedKph = recMaxSpeed,
                        peakPowerKw = recPeakKw,
                        maxRegenKw = recMaxRegenKw,
                        minSocPct = recMinSoc,
                        maxSocPct = recMaxSoc,
                        maxCellDeltaMv = recMaxDeltaMv,
                        maxStatorTempC = recMaxStatorC
                    )
                )

                _uiState.update {
                    it.copy(
                        recordingElapsedSec = (elapsed / 1000L).toInt(),
                        recordingSampleCount = count
                    )
                }

                delay(500L)
            }
        }
    }

    private fun stopSessionRecording() {
        recordingJob?.cancel()
        recordingJob = null
        val totalSamples = _uiState.value.recordingSampleCount
        val finishedId = _uiState.value.activeRecordingSessionId
        _uiState.update {
            it.copy(
                isRecordingSession = false,
                activeRecordingSessionId = null,
                selectedLogSessionId = finishedId,
                snackbarMessage = "Saved telemetry log ($totalSamples samples)"
            )
        }
        if (finishedId != null) {
            selectLogSessionForInspection(finishedId)
        }
    }

    fun selectLogSessionForInspection(sessionId: Long) {
        viewModelScope.launch {
            val samples = repository.getSamplesForSession(sessionId)
            _uiState.update {
                it.copy(
                    selectedLogSessionId = sessionId,
                    selectedLogSamples = samples
                )
            }
        }
    }

    fun deleteSingleLogSession(session: TelemetrySessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session.id)
            _uiState.update {
                val clearedSelection = if (it.selectedLogSessionId == session.id) null else it.selectedLogSessionId
                it.copy(
                    selectedLogSessionId = clearedSelection,
                    selectedLogSamples = if (clearedSelection == null) emptyList() else it.selectedLogSamples,
                    snackbarMessage = "Cleared telemetry log '${session.title}'"
                )
            }
        }
    }

    fun purgeAllTelemetryLogs() {
        viewModelScope.launch {
            repository.clearAllSessions()
            _uiState.update {
                it.copy(
                    selectedLogSessionId = null,
                    selectedLogSamples = emptyList(),
                    snackbarMessage = "Purged all recorded telemetry logs from device storage"
                )
            }
        }
    }

    fun exportSessionCsv(session: TelemetrySessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val samples = repository.getSamplesForSession(session.id)
            val csvFile = TelemetryExportManager.exportSessionToCsv(context, session, samples)
            _uiState.update {
                it.copy(snackbarMessage = "Exported ${csvFile.name} (${samples.size} rows)")
            }
            TelemetryExportManager.shareExportedFile(context, csvFile, "text/csv")
        }
    }

    fun exportSessionOrFullBundleJson(session: TelemetrySessionEntity?) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val samples = if (session != null) repository.getSamplesForSession(session.id) else emptyList()
            val jsonFile = TelemetryExportManager.exportFullBundleToJson(
                context = context,
                session = session,
                samples = samples,
                dashboards = dashboards.value,
                alerts = alerts.value
            )
            _uiState.update {
                it.copy(snackbarMessage = "Exported JSON bundle ${jsonFile.name}")
            }
            TelemetryExportManager.shareExportedFile(context, jsonFile, "application/json")
        }
    }

    override fun onCleared() {
        super.onCleared()
        transportEngine.stopAll()
        recordingJob?.cancel()
    }
}
