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
import com.example.model.AlertFreezeFrame
import com.example.model.AlertSeverity
import com.example.model.BundledCanTrace
import com.example.model.ConnectionMode
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
    LOGS_REPLAY("Logs & Replay")
}

data class CockpitUiState(
    val currentTab: CockpitTab = CockpitTab.COCKPIT,
    val isDarkTheme: Boolean = true,
    // Connection & Replay state
    val connectionMode: ConnectionMode = ConnectionMode.CAN_LOG_REPLAY,
    val connectionStatus: ConnectionStatus = ConnectionStatus.REPLAYING_LOG,
    val statusMessage: String = "CAN Replay: Canyon Spirited Drive (Dual Motor AWD)",
    val s3xySsid: String = "S3XY_OBD",
    val s3xyPassphrase: String = "12345678",
    val s3xyHostIp: String = "192.168.4.1",
    val s3xyPort: Int = 1338,
    val s3xyProtocol: S3xyProtocol = S3xyProtocol.PANDA_UDP_1338,
    val busFrameRateFps: Int = 90,
    val activeReplayTrace: BundledCanTrace = BundledCanTrace.CANYON_SPIRITED_RUN,
    val isReplayPaused: Boolean = false,
    val replaySpeedMultiplier: Float = 1.0f,
    val replayProgress: Float = 0f,
    val replayElapsedSec: Int = 0,
    val replayTotalSec: Int = 120,
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
                    state.connectionMode == ConnectionMode.CAN_LOG_REPLAY && connected ->
                        ConnectionStatus.REPLAYING_LOG
                    connected -> ConnectionStatus.CONNECTED_LIVE
                    message.contains("Connecting") -> ConnectionStatus.CONNECTING
                    else -> ConnectionStatus.SOCKET_ERROR
                }
                state.copy(
                    connectionStatus = status,
                    statusMessage = message
                )
            }
        },
        onReplayProgressChanged = { progress, elapsedSec, totalSec ->
            _uiState.update {
                it.copy(
                    replayProgress = progress,
                    replayElapsedSec = elapsedSec,
                    replayTotalSec = totalSec
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            transportEngine.startRecordedCanReplay(BundledCanTrace.CANYON_SPIRITED_RUN)
        }
    }

    private fun handleIncomingBinaryPacket(bytes: ByteArray, length: Int, fps: Int) {
        _uiState.update { state ->
            val (nextSnap, _) = Model3CanFrameDecoder.parsePandaUdpDatagram(
                data = bytes,
                length = length,
                current = state.snapshot
            )
            updateTelemetryMaps(state, nextSnap, fps)
        }
    }

    private fun handleIncomingAsciiLine(line: String, fps: Int) {
        _uiState.update { state ->
            val (nextSnap, _) = Model3CanFrameDecoder.parseElm327AsciiLine(
                line = line,
                current = state.snapshot
            )
            updateTelemetryMaps(state, nextSnap, fps)
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

    // S3XY_OBD Connection & Replay Controls
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
                connectionMode = ConnectionMode.LIVE_S3XY_WIFI,
                connectionStatus = ConnectionStatus.CONNECTING,
                statusMessage = "Binding to ${state.s3xySsid} (${state.s3xyHostIp}:${state.s3xyPort})..."
            )
        }

        if (bindOsWifiFirst) {
            transportEngine.requestS3xyWifiBinding(state.s3xySsid, state.s3xyPassphrase) { ok, msg ->
                _uiState.update { it.copy(statusMessage = msg, snackbarMessage = msg) }
                if (ok) {
                    transportEngine.startLiveSocket(state.s3xyHostIp, state.s3xyPort, state.s3xyProtocol)
                }
            }
        } else {
            transportEngine.startLiveSocket(state.s3xyHostIp, state.s3xyPort, state.s3xyProtocol)
        }
    }

    fun startBundledCanReplay(trace: BundledCanTrace) {
        _uiState.update {
            it.copy(
                connectionMode = ConnectionMode.CAN_LOG_REPLAY,
                connectionStatus = ConnectionStatus.REPLAYING_LOG,
                activeReplayTrace = trace,
                isReplayPaused = false,
                statusMessage = "CAN Replay: ${trace.title}",
                snackbarMessage = "Loaded 2023 Model 3 CAN trace: ${trace.title}"
            )
        }
        transportEngine.startRecordedCanReplay(trace, null)
    }

    fun replayRecordedSession(session: TelemetrySessionEntity) {
        viewModelScope.launch {
            val samples = repository.getSamplesForSession(session.id)
            if (samples.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "Session has no recorded samples to replay.") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    currentTab = CockpitTab.COCKPIT,
                    connectionMode = ConnectionMode.CAN_LOG_REPLAY,
                    connectionStatus = ConnectionStatus.REPLAYING_LOG,
                    isReplayPaused = false,
                    statusMessage = "Replaying Log: ${session.title}",
                    snackbarMessage = "Replaying '${session.title}' through live cockpit gauges"
                )
            }
            transportEngine.startRecordedCanReplay(_uiState.value.activeReplayTrace, samples)
        }
    }

    fun toggleReplayPause() {
        val nextPaused = !_uiState.value.isReplayPaused
        transportEngine.setReplayPaused(nextPaused)
        _uiState.update { it.copy(isReplayPaused = nextPaused) }
    }

    fun setReplaySpeed(multiplier: Float) {
        transportEngine.setReplaySpeed(multiplier)
        _uiState.update { it.copy(replaySpeedMultiplier = multiplier) }
    }

    fun seekReplay(fraction: Float) {
        transportEngine.seekReplayProgress(fraction, _uiState.value.activeReplayTrace)
        _uiState.update { it.copy(replayProgress = fraction) }
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

    // Diagnostic Alert Interrogation & Clear Commands
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
        viewModelScope.launch {
            _uiState.update { it.copy(isInterrogatingBus = true) }
            delay(950)
            val snap = _uiState.value.snapshot
            val freezeFrame = AlertFreezeFrame(
                speedKph = snap.speedKph,
                netPowerKw = snap.netPowerKw,
                packVoltageV = snap.packVoltageV,
                packCurrentA = snap.packCurrentA,
                usableSocPct = snap.usableSocPct,
                cellMinV = snap.cellMinV,
                cellMaxV = snap.cellMaxV,
                cellDeltaMv = snap.cellDeltaMv,
                statorTempC = snap.statorTempC,
                rearInverterTempC = snap.rearInverterTempC,
                coolantBatteryInletC = snap.coolantBatteryInletC,
                octovalvePosition = snap.octovalvePosition,
                gear = snap.currentGear
            )

            // Capture live anomaly if stator temp or cell delta is elevated, or log a live bus health snapshot
            val newAlert = if (snap.statorTempC >= 115f || snap.cellDeltaMv >= 20f) {
                VehicleAlertEntity(
                    alertCode = "DI_a142",
                    subsystem = TeslaSubsystem.DI_INVERTER.name,
                    severity = AlertSeverity.WARNING.name,
                    title = "Live Scan: Elevated Stator Winding Thermal Load",
                    description = "S3XY_OBD UDS interrogation captured rear stator temperature at ${"%.1f".format(snap.statorTempC)}°C with cell delta ${"%.1f".format(snap.cellDeltaMv)} mV.",
                    recommendedAction = "Verify Octovalve Step ${snap.octovalvePosition} coolant flow (${"%.1f".format(snap.powertrainCoolantLpm)} L/min).",
                    rawCanIdHex = "0x315",
                    rawPayloadHex = "8E 01 74 04 1D 00 3A FF",
                    freezeFrameJson = CockpitJsonSerializer.encodeFreezeFrame(freezeFrame),
                    timestampMs = System.currentTimeMillis(),
                    isActive = true,
                    isAcknowledged = false
                )
            } else {
                VehicleAlertEntity(
                    alertCode = "VCFRONT_a088",
                    subsystem = TeslaSubsystem.VCFRONT_THERMAL.name,
                    severity = AlertSeverity.INFO.name,
                    title = "Live Scan: Subsystems Nominal (Octovalve Step ${snap.octovalvePosition})",
                    description = "Interrogated BMS, DI, VCFront, HVP, and ESP modules over S3XY_OBD CAN bridge. Pack voltage ${"%.1f".format(snap.packVoltageV)}V with ${"%.1f".format(snap.cellDeltaMv)} mV cell delta.",
                    recommendedAction = "All HV isolation and thermal loops within factory specification.",
                    rawCanIdHex = "0x241",
                    rawPayloadHex = "58 00 36 48 0${snap.octovalvePosition} 2A 24 11",
                    freezeFrameJson = CockpitJsonSerializer.encodeFreezeFrame(freezeFrame),
                    timestampMs = System.currentTimeMillis(),
                    isActive = true,
                    isAcknowledged = false
                )
            }

            val insertedId = repository.insertAlert(newAlert)
            _uiState.update {
                it.copy(
                    isInterrogatingBus = false,
                    selectedAlertId = insertedId,
                    snackbarMessage = "CAN Interrogation complete: Captured ${newAlert.alertCode} freeze-frame"
                )
            }
        }
    }

    fun clearAndAcknowledgeFaults() {
        viewModelScope.launch {
            repository.clearAndAcknowledgeActiveAlerts()
            _uiState.update {
                it.copy(
                    snackbarMessage = "Transmitted S3XY_OBD Clear DTC command (0x14 FF FF FF) & acknowledged active alerts"
                )
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
                    snackbarMessage = "Cleared all historical/resolved diagnostic alerts"
                )
            }
        }
    }

    // Telemetry Session Recording & Clear Logging
    fun toggleSessionRecording() {
        if (_uiState.value.isRecordingSession) {
            stopSessionRecording()
        } else {
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
        val modeLabel = if (_uiState.value.connectionMode == ConnectionMode.LIVE_S3XY_WIFI) {
            "S3XY_OBD Live (${_uiState.value.s3xyHostIp})"
        } else {
            "CAN Replay (${_uiState.value.activeReplayTrace.tag})"
        }

        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val sessionId = repository.createSession(
                TelemetrySessionEntity(
                    title = "Model 3 Telemetry Log ($stamp)",
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
                    snackbarMessage = "Recording high-rate CAN telemetry to Room database..."
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
                        title = "Model 3 Telemetry Log ($stamp)",
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
                snackbarMessage = "Saved telemetry log ($totalSamples samples) to on-device database"
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
