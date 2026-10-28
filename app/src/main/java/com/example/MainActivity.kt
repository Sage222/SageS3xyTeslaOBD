package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.CockpitTopStatusBar
import com.example.ui.components.S3xyConnectionModalSheet
import com.example.ui.screens.CockpitDashboardScreen
import com.example.ui.screens.DiagnosticsAlertsScreen
import com.example.ui.screens.Model3ChassisXRayScreen
import com.example.ui.screens.TelemetryLogsScreen
import com.example.ui.theme.S3xyCockpitTheme
import com.example.ui.viewmodel.CockpitTab
import com.example.ui.viewmodel.TelemetryCockpitViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TelemetryCockpitViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            S3xyCockpitTheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    S3xyCockpitAppShell(viewModel = viewModel)
                }
            }
        }
    }
}

private data class CockpitNavItem(
    val tab: CockpitTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

private val NAV_ITEMS = listOf(
    CockpitNavItem(
        tab = CockpitTab.COCKPIT,
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        tag = "nav_tab_cockpit"
    ),
    CockpitNavItem(
        tab = CockpitTab.CHASSIS_XRAY,
        selectedIcon = Icons.Filled.DirectionsCar,
        unselectedIcon = Icons.Outlined.DirectionsCar,
        tag = "nav_tab_chassis"
    ),
    CockpitNavItem(
        tab = CockpitTab.ALERTS_DTC,
        selectedIcon = Icons.Filled.Build,
        unselectedIcon = Icons.Outlined.Build,
        tag = "nav_tab_alerts"
    ),
    CockpitNavItem(
        tab = CockpitTab.LOGS_REPLAY,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        tag = "nav_tab_logs"
    )
)

@Composable
fun S3xyCockpitAppShell(
    viewModel: TelemetryCockpitViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboards by viewModel.dashboards.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeFaultCount = remember(alerts) { alerts.count { it.isActive } }

    BackHandler(enabled = uiState.currentTab != CockpitTab.COCKPIT || uiState.isEditLayoutMode) {
        if (uiState.isEditLayoutMode) {
            viewModel.toggleEditLayoutMode()
        } else {
            viewModel.selectTab(CockpitTab.COCKPIT)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val useNavigationRail = maxWidth >= 600.dp || maxWidth > maxHeight

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                modifier = Modifier.testTag(item.tag),
                                icon = {
                                    if (item.tab == CockpitTab.ALERTS_DTC && activeFaultCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(activeFaultCount.toString()) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.tab.title
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.tab.title
                                        )
                                    }
                                },
                                label = { Text(item.tab.title) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NAV_ITEMS.forEach { item ->
                            val selected = uiState.currentTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(item.tab) },
                                modifier = Modifier.testTag(item.tag),
                                icon = {
                                    if (item.tab == CockpitTab.ALERTS_DTC && activeFaultCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge { Text(activeFaultCount.toString()) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.tab.title
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.tab.title
                                        )
                                    }
                                },
                                label = { Text(item.tab.title) }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CockpitTopStatusBar(
                        uiState = uiState,
                        onOpenConnectionSheet = { viewModel.setShowConnectionSheet(true) },
                        onToggleRecording = { viewModel.toggleSessionRecording() },
                        onToggleTheme = { viewModel.toggleDarkTheme() }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (uiState.currentTab) {
                            CockpitTab.COCKPIT -> CockpitDashboardScreen(
                                uiState = uiState,
                                dashboards = dashboards,
                                onSelectDashboard = viewModel::selectDashboard,
                                onToggleEditLayout = viewModel::toggleEditLayoutMode,
                                onOpenAddWidgetDialog = { viewModel.setShowAddWidgetDialog(true) },
                                onCloseAddWidgetDialog = { viewModel.setShowAddWidgetDialog(false) },
                                onAddWidget = viewModel::addWidgetToActiveDashboard,
                                onOpenNewDashboardDialog = { viewModel.setShowNewDashboardDialog(true) },
                                onCloseNewDashboardDialog = { viewModel.setShowNewDashboardDialog(false) },
                                onCreateCustomDashboard = viewModel::createCustomDashboard,
                                onDeleteCustomDashboard = viewModel::deleteCustomDashboard,
                                onMoveWidgetOrder = viewModel::moveWidgetOrder,
                                onCycleWidgetSpan = viewModel::cycleWidgetSpan,
                                onCycleWidgetStyle = viewModel::cycleWidgetStyle,
                                onRemoveWidget = viewModel::removeWidgetFromActiveDashboard,
                                onResetPeakHold = viewModel::resetPeakHoldValues,
                                onResetFactoryDashboards = viewModel::resetDashboardsToFactory
                            )

                            CockpitTab.CHASSIS_XRAY -> Model3ChassisXRayScreen(
                                snapshot = uiState.snapshot
                            )

                            CockpitTab.ALERTS_DTC -> DiagnosticsAlertsScreen(
                                uiState = uiState,
                                alerts = alerts,
                                onSelectSubsystem = viewModel::selectSubsystemFilter,
                                onToggleActiveOnly = viewModel::toggleActiveAlertsOnly,
                                onSelectAlert = viewModel::selectAlertForInspection,
                                onInterrogateBus = viewModel::interrogateCanBusSubsystems,
                                onClearAndAcknowledge = viewModel::clearAndAcknowledgeFaults,
                                onAcknowledgeSingle = viewModel::acknowledgeSingleAlert,
                                onPurgeHistorical = viewModel::purgeHistoricalAlerts,
                                onExportDiagnosticJson = { viewModel.exportSessionOrFullBundleJson(null) }
                            )

                            CockpitTab.LOGS_REPLAY -> TelemetryLogsScreen(
                                uiState = uiState,
                                sessions = sessions,
                                onToggleRecording = viewModel::toggleSessionRecording,
                                onSelectSession = viewModel::selectLogSessionForInspection,
                                onExportCsv = viewModel::exportSessionCsv,
                                onExportJson = viewModel::exportSessionOrFullBundleJson,
                                onDeleteSession = viewModel::deleteSingleLogSession,
                                onPurgeAllLogs = viewModel::purgeAllTelemetryLogs
                            )
                        }
                    }
                }
            }

            if (uiState.showConnectionSheet) {
                S3xyConnectionModalSheet(
                    uiState = uiState,
                    onDismiss = { viewModel.setShowConnectionSheet(false) },
                    onUpdateNetworkConfig = viewModel::updateS3xyNetworkConfig,
                    onConnectLiveWifi = { bindFirst ->
                        viewModel.setShowConnectionSheet(false)
                        viewModel.connectToLiveS3xyWifi(bindFirst)
                    },
                    onDisconnect = {
                        viewModel.setShowConnectionSheet(false)
                        viewModel.disconnectS3xySocket()
                    }
                )
            }
        }
    }
}
