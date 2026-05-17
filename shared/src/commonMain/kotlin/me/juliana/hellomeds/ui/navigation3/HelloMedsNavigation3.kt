// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
// BETA: Closed-beta survey nudge. Remove per BETA_ROLLBACK.md before public release.
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.backup_nudge_body
import me.juliana.hellomeds.shared.backup_nudge_dismiss
import me.juliana.hellomeds.shared.backup_nudge_setup
import me.juliana.hellomeds.shared.backup_nudge_title
// BETA: Closed-beta survey nudge. Remove per BETA_ROLLBACK.md before public release.
import me.juliana.hellomeds.shared.closed_beta_survey_nudge_body
import me.juliana.hellomeds.shared.closed_beta_survey_nudge_cta
import me.juliana.hellomeds.shared.closed_beta_survey_nudge_dismiss
import me.juliana.hellomeds.shared.closed_beta_survey_nudge_title
import me.juliana.hellomeds.shared.closed_beta_survey_url
import me.juliana.hellomeds.ui.compat.ConfigureStatusBar
import me.juliana.hellomeds.ui.compat.DynamicDarkOnboardingTheme
import me.juliana.hellomeds.ui.components.medication.MedicationAddedDialog
import me.juliana.hellomeds.ui.features.backup.AutoBackupSettingsScreen
import me.juliana.hellomeds.ui.features.backup.ExportDataScreen
import me.juliana.hellomeds.ui.features.backup.ImportDataScreen
import me.juliana.hellomeds.ui.features.onboarding.OnboardingScreen
import me.juliana.hellomeds.ui.features.settings.CameraSettingsScreen
import me.juliana.hellomeds.ui.features.settings.NotificationSettingsScreen
import me.juliana.hellomeds.ui.features.settings.SettingsScreen
import me.juliana.hellomeds.ui.features.settings.SupportScreen
import me.juliana.hellomeds.ui.navigation3.entries.AddMedicationScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.AddStockTrackingFlowScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.EditLabelScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.EditMedicationScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.EditScheduleScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.ImportanceLabelsScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.MedicationDetailScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.StockTrackingDetailScreenEntry
import me.juliana.hellomeds.ui.navigation3.entries.StockTrackingSettingsScreenEntry
import me.juliana.hellomeds.ui.util.IncomingBackupHandler
import me.juliana.hellomeds.ui.util.PendingImport
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.rememberFolderPicker
import me.juliana.hellomeds.ui.util.suggestedAutoBackupInitialUri
import me.juliana.hellomeds.ui.viewmodel.AutoBackupViewModel
import me.juliana.hellomeds.ui.viewmodel.BackupViewModel
import me.juliana.hellomeds.ui.viewmodel.SupportViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main navigation component using Single Root Stack Architecture with Official Navigation 3.
 *
 * Architecture:
 * - Single root navigation stack starting with [MainAppRoute]
 * - Overlay routes (Settings, Edit, Camera) pushed on top completely replace MainAppRoute
 * - Navigation bars/rails defined INSIDE MainAppRoute are automatically hidden when overlays are on top
 * - Tabs within MainAppRoute managed by local state (NOT navigation stack)
 *
 * Navigation Stack Examples:
 * - [MainAppRoute] -> Shows main screen with nav bar
 * - [MainAppRoute, SettingsRoute] -> Settings full-screen, nav bar hidden
 * - [MainAppRoute, SettingsRoute, ImportanceLabelsRoute] -> ImportanceLabels full-screen
 *
 * This pattern ensures:
 * - Native predictive back gestures (handled by NavDisplay automatically)
 * - Natural full-screen overlay presentation
 * - Automatic state preservation when MainAppRoute is stopped
 * - Standard Navigation 3 architecture (like Activity navigation)
 *
 * Adapts to window size:
 * - Compact (< 600dp): Bottom navigation bar (inside MainAppRoute)
 * - Medium (600-840dp): Navigation rail (inside MainAppRoute)
 * - Expanded (>= 840dp): 1:1:1 three-pane layout (inside MainAppRoute)
 *
 * @param screenProviders Provides platform-specific screen implementations (Settings, Debug, etc.)
 */
@Composable
fun HelloMedsNavigation3(
    modifier: Modifier = Modifier,
    initialRoute: NavKey = MainAppRoute,
    notificationEventIds: IntArray? = null,
    notificationGroupingMode: String? = null,
    onNotificationHandled: () -> Unit = {},
    stockDetailMedicationId: Int? = null,
    onStockDetailHandled: () -> Unit = {},
    screenProviders: NavigationScreenProviders = NavigationScreenProviders(),
) {
    // Create navigation state with single root stack
    val appNavState = rememberAppNavigationState(initialRoute = initialRoute)

    // Create navigator to handle navigation events
    val navigator = remember { Navigator(appNavState) }

    // Check onboarding status and navigate if needed (non-blocking)
    val onboardingPrefs = koinInject<OnboardingPreferences>()
    var hasCheckedOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasCheckedOnboarding) {
            val isCompleted = onboardingPrefs.onboardingCompleted.first()

            if (!isCompleted && appNavState.rootBackStack.lastOrNull() !is OnboardingRoute) {
                // User hasn't completed onboarding, navigate there
                appNavState.rootBackStack.clear()
                appNavState.rootBackStack.add(OnboardingRoute())
            }

            hasCheckedOnboarding = true
        }
    }

    // Define all app destinations (main route + overlays)
    val entryProvider = entryProvider {
        // ====================================================================
        // MAIN APP ROUTE - Contains Nav Bar/Rail and 3 Tabs
        // ====================================================================

        entry<MainAppRoute> {
            // This entry contains ALL the adaptive layout logic and navigation UI
            AdaptiveMainScreen(
                selectedTabIndex = appNavState.selectedTabIndex,
                onTabSelected = { navigator.selectTab(it) },
                onNavigateToSettings = {
                    navigator.openOverlay(SettingsRoute)
                },
                onNavigateToSupport = {
                    navigator.openOverlay(SupportRoute)
                },
                onNavigateToAddMedication = {
                    navigator.openOverlay(AddMedicationRoute)
                },
                onNavigateToCamera = {
                    navigator.openOverlay(CameraDetectionRoute)
                },
                onNavigateToMedicationDetail = { medicationId ->
                    navigator.openOverlay(MedicationDetailRoute(medicationId))
                },
                onNavigateToEditSchedule = { medicationId ->
                    navigator.openOverlay(EditScheduleRoute(medicationId))
                },
                onNavigateToEditLabel = { medicationId ->
                    navigator.openOverlay(EditLabelRoute(medicationId))
                },
                onNavigateToStockDetail = { medicationId ->
                    navigator.openOverlay(StockTrackingDetailRoute(medicationId))
                },
                notificationEventIds = notificationEventIds,
                notificationGroupingMode = notificationGroupingMode,
                onNotificationHandled = onNotificationHandled,
            )
        }

        // ====================================================================
        // ONBOARDING ROUTE - First-Launch Permission Setup
        // ====================================================================

        entry<OnboardingRoute> { key ->
            OnboardingScreen(
                showAllSteps = key.showAllSteps,
                onComplete = {
                    // If this is the initial onboarding (backstack only has OnboardingRoute),
                    // clear backstack and navigate to main app
                    // If this is a debug/review launch (backstack has more entries),
                    // just close the overlay
                    if (appNavState.rootBackStack.size == 1) {
                        // Initial onboarding - replace with main app
                        appNavState.rootBackStack.clear()
                        appNavState.rootBackStack.add(MainAppRoute)
                    } else {
                        // Debug/review launch - just close the overlay
                        navigator.closeOverlay()
                    }
                },
                onNavigateToCamera = {
                    // Complete onboarding, navigate to main app (Medications tab), and open camera
                    if (appNavState.rootBackStack.size == 1) {
                        appNavState.rootBackStack.clear()
                        appNavState.rootBackStack.add(MainAppRoute)
                        appNavState.selectedTabIndex = 1 // Select Medications tab
                    }
                    navigator.openOverlay(CameraDetectionRoute)
                },
                onNavigateToManualAdd = {
                    // Complete onboarding, navigate to main app (Medications tab), and open manual add
                    if (appNavState.rootBackStack.size == 1) {
                        appNavState.rootBackStack.clear()
                        appNavState.rootBackStack.add(MainAppRoute)
                        appNavState.selectedTabIndex = 1 // Select Medications tab
                    }
                    navigator.openOverlay(AddMedicationRoute)
                },
                onNavigateToImport = {
                    // Complete onboarding, navigate to main app, and open import
                    if (appNavState.rootBackStack.size == 1) {
                        appNavState.rootBackStack.clear()
                        appNavState.rootBackStack.add(MainAppRoute)
                    }
                    navigator.openOverlay(ImportDataRoute)
                },
                notificationPermissionScreen = screenProviders.notificationPermissionScreen,
                alarmKitPermissionScreen = screenProviders.alarmKitPermissionScreen,
                criticalAlertsPermissionScreen = screenProviders.criticalAlertsPermissionScreen,
            )
        }

        // ====================================================================
        // OVERLAY ROUTES - Simple Full-Screen Composables (NO Scaffold)
        // ====================================================================
        // Settings Hierarchy

        entry<SettingsRoute> {
            OverlayScreenWrapper {
                SettingsScreen(
                    onNavigateBack = { navigator.closeOverlay() },
                    onNavigateToImportanceLabels = { navigator.openOverlay(ImportanceLabelsRoute) },
                    onNavigateToNotificationSettings = { navigator.openOverlay(NotificationSettingsRoute) },
                    onNavigateToDebug = { navigator.openOverlay(DebugRoute) },
                    onNavigateToAutoBackup = { navigator.openOverlay(AutoBackupSettingsRoute) },
                    onNavigateToExportData = { navigator.openOverlay(ExportDataRoute) },
                    onNavigateToImportData = { navigator.openOverlay(ImportDataRoute) },
                    onNavigateToSupport = { navigator.openOverlay(SupportRoute) },
                    onNavigateToCameraSettings = { navigator.openOverlay(CameraSettingsRoute) },
                )
            }
        }

        entry<ImportanceLabelsRoute> {
            OverlayScreenWrapper {
                ImportanceLabelsScreenEntry(
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<NotificationSettingsRoute> {
            OverlayScreenWrapper {
                NotificationSettingsScreen(
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<CameraSettingsRoute> {
            OverlayScreenWrapper {
                CameraSettingsScreen(
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        if (PlatformCapabilities.isDebugBuild()) {
            entry<DebugRoute> {
                OverlayScreenWrapper {
                    screenProviders.debugScreen(
                        { navigator.closeOverlay() },
                        { showAllSteps ->
                            navigator.openOverlay(OnboardingRoute(showAllSteps = showAllSteps))
                        },
                    )
                }
            }
        }

        entry<AutoBackupSettingsRoute> {
            OverlayScreenWrapper {
                val viewModel: AutoBackupViewModel = koinViewModel()
                val pickFolder = rememberFolderPicker { uri ->
                    if (uri != null) {
                        viewModel.setDestinationUri(uri)
                    } else {
                        viewModel.onFolderPickerCancelled()
                    }
                }
                AutoBackupSettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigator.closeOverlay() },
                    onPickFolder = pickFolder,
                    suggestedInitialUri = suggestedAutoBackupInitialUri(),
                )
            }
        }

        entry<SupportRoute> {
            OverlayScreenWrapper {
                val viewModel: SupportViewModel = koinViewModel()
                SupportScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<ExportDataRoute> {
            OverlayScreenWrapper {
                val backupViewModel: BackupViewModel = koinViewModel()
                ExportDataScreen(
                    viewModel = backupViewModel,
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<ImportDataRoute> {
            OverlayScreenWrapper {
                val backupViewModel: BackupViewModel = koinViewModel()

                // Auto-parse if opened from an external backup file
                val pendingImport = remember { IncomingBackupHandler.pendingImport.value }
                LaunchedEffect(pendingImport) {
                    val bytes = when (pendingImport) {
                        is PendingImport.Bytes -> pendingImport.data.takeIf { it.isNotEmpty() }
                        is PendingImport.Reader -> pendingImport.read()?.takeIf { it.isNotEmpty() }
                        null -> null
                    }
                    IncomingBackupHandler.clearPendingImport()
                    if (bytes != null) {
                        backupViewModel.parseImportFile(bytes)
                    }
                }

                ImportDataScreen(
                    viewModel = backupViewModel,
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        // Medication Management

        entry<MedicationDetailRoute> { key ->
            OverlayScreenWrapper {
                MedicationDetailScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                    onEditSchedule = { medicationId ->
                        navigator.openOverlay(EditScheduleRoute(medicationId))
                    },
                    onEditMedication = { medicationId ->
                        navigator.openOverlay(EditMedicationRoute(medicationId))
                    },
                    onEditLabel = { medicationId ->
                        navigator.openOverlay(EditLabelRoute(medicationId))
                    },
                    onManageStock = { medicationId ->
                        navigator.openOverlay(StockTrackingDetailRoute(medicationId))
                    },
                )
            }
        }

        entry<AddMedicationRoute> {
            OverlayScreenWrapper {
                AddMedicationScreenEntry(
                    onClose = {
                        appNavState.detectedMedicationData = null // Clear detection data
                        navigator.closeOverlay()
                    },
                    onMedicationAdded = { medicationId, medicationName, state ->
                        // Store completion data to show dialog after screen closes
                        appNavState.completionDialogData = MedicationCompletionData(
                            medicationId = medicationId,
                            medicationName = medicationName,
                            state = state,
                        )
                    },
                    detectionData = appNavState.detectedMedicationData,
                )
            }
        }

        entry<EditMedicationRoute> { key ->
            OverlayScreenWrapper {
                EditMedicationScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<EditScheduleRoute> { key ->
            OverlayScreenWrapper {
                EditScheduleScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                    onNavigateToEditLabel = { medicationId ->
                        navigator.openOverlay(EditLabelRoute(medicationId))
                    },
                )
            }
        }

        entry<EditLabelRoute> { key ->
            OverlayScreenWrapper {
                EditLabelScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                    onNavigateToSettings = { navigator.openOverlay(ImportanceLabelsRoute) },
                )
            }
        }

        entry<StockTrackingDetailRoute> { key ->
            OverlayScreenWrapper {
                StockTrackingDetailScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                    onAddTracking = { medicationId ->
                        navigator.openOverlay(AddStockTrackingFlowRoute(medicationId))
                    },
                    onSettings = { medicationId ->
                        navigator.openOverlay(StockTrackingSettingsRoute(medicationId))
                    },
                )
            }
        }

        entry<StockTrackingSettingsRoute> { key ->
            OverlayScreenWrapper {
                StockTrackingSettingsScreenEntry(
                    medicationId = key.medicationId,
                    onNavigateBack = { navigator.closeOverlay() },
                )
            }
        }

        entry<AddStockTrackingFlowRoute> { key ->
            OverlayScreenWrapper {
                AddStockTrackingFlowScreenEntry(
                    medicationId = key.medicationId,
                    onClose = { navigator.closeOverlay() },
                )
            }
        }

        // Camera Detection

        entry<CameraDetectionRoute> {
            OverlayScreenWrapper {
                screenProviders.cameraDetectionScreen?.invoke(
                    { navigator.closeOverlay() },
                    { detectionResult ->
                        // Use special handler to store detection data, close camera, then open add medication
                        navigator.onCameraDetectionComplete(detectionResult)
                    },
                ) ?: run {
                    // Safety fallback: flavor doesn't support camera — close immediately
                    LaunchedEffect(Unit) { navigator.closeOverlay() }
                }
            }
        }
    }

    // Render the entire app with a single NavDisplay
    // Apply dark theme when on onboarding route
    Box(modifier = modifier.fillMaxSize()) {
        val currentRoute = appNavState.rootBackStack.lastOrNull()
        val isDarkThemeRoute = currentRoute is OnboardingRoute

        // Reset status bar icons when leaving onboarding (which forces dark/light icons)
        val darkTheme = isSystemInDarkTheme()
        LaunchedEffect(isDarkThemeRoute) {
            // ConfigureStatusBar is a composable, but we need the reset to happen
            // when the route changes. The actual composable call below handles the
            // status bar configuration.
        }

        // Configure status bar when NOT on dark theme route
        if (!isDarkThemeRoute) {
            ConfigureStatusBar(isDarkTheme = darkTheme)
        }

        if (isDarkThemeRoute) {
            DynamicDarkOnboardingTheme {
                NavDisplay(
                    entries = appNavState.toRootEntries(entryProvider),
                    // Guard: CMP's UIKitBackGestureRecognizer crashes with NSRangeException
                    // ("index (0) beyond bounds (0)") when it fires on the root entry with
                    // nothing to pop. Only process back when there's an overlay to close.
                    onBack = { if (appNavState.rootBackStack.size > 1) navigator.closeOverlay() },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = pushTransitionSpec,
                    popTransitionSpec = popTransitionSpec,
                    predictivePopTransitionSpec = predictivePopTransitionSpec,
                )
            }
        } else {
            MaterialTheme {
                NavDisplay(
                    entries = appNavState.toRootEntries(entryProvider),
                    // Guard: CMP's UIKitBackGestureRecognizer crashes with NSRangeException
                    // ("index (0) beyond bounds (0)") when it fires on the root entry with
                    // nothing to pop. Only process back when there's an overlay to close.
                    onBack = { if (appNavState.rootBackStack.size > 1) navigator.closeOverlay() },
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = pushTransitionSpec,
                    popTransitionSpec = popTransitionSpec,
                    predictivePopTransitionSpec = predictivePopTransitionSpec,
                )
            }
        }

        // Show medication added dialog on top when completion data is available
        appNavState.completionDialogData?.let { completionData ->
            MedicationAddedDialog(
                medicationName = completionData.medicationName,
                foregroundShape = completionData.state.foregroundShape,
                backgroundShape = completionData.state.backgroundShape,
                color1 = completionData.state.color1,
                onAddSchedule = {
                    appNavState.completionDialogData = null
                    navigator.openOverlay(EditScheduleRoute(completionData.medicationId.toInt()))
                },
                onClose = {
                    appNavState.completionDialogData = null
                },
            )
        }

        // Auto-backup nudge dialog — shown 2 days after onboarding if backups not enabled
        val nudgeScope = rememberCoroutineScope()
        val autoBackupPrefs = koinInject<AutoBackupPreferences>()
        val autoBackupEnabled by autoBackupPrefs.autoBackupEnabled.collectAsStateWithLifecycle(false)
        val nudgeDismissed by autoBackupPrefs.backupNudgeDismissed.collectAsStateWithLifecycle(false)
        val onboardingTimestamp by autoBackupPrefs.onboardingCompletedTimestamp.collectAsStateWithLifecycle(0L)

        val twoDaysMs = 2 * 24 * 60 * 60 * 1000L
        val showNudge = onboardingTimestamp > 0L &&
            (kotlin.time.Clock.System.now().toEpochMilliseconds() - onboardingTimestamp) >= twoDaysMs &&
            !autoBackupEnabled &&
            !nudgeDismissed

        if (showNudge) {
            AlertDialog(
                onDismissRequest = {
                    nudgeScope.launch {
                        autoBackupPrefs.setBackupNudgeDismissed(true)
                    }
                },
                title = { Text(stringResource(Res.string.backup_nudge_title)) },
                text = { Text(stringResource(Res.string.backup_nudge_body)) },
                confirmButton = {
                    Button(onClick = {
                        nudgeScope.launch {
                            autoBackupPrefs.setBackupNudgeDismissed(true)
                        }
                        navigator.openOverlay(AutoBackupSettingsRoute)
                    }) {
                        Text(stringResource(Res.string.backup_nudge_setup))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        nudgeScope.launch {
                            autoBackupPrefs.setBackupNudgeDismissed(true)
                        }
                    }) {
                        Text(stringResource(Res.string.backup_nudge_dismiss))
                    }
                },
            )
        }

        // === BETA: Closed-beta survey nudge — shown 10 days after onboarding.
        // Remove this entire block per BETA_ROLLBACK.md before public release. ===
        val surveyNudgeDismissed by autoBackupPrefs.closedBetaSurveyNudgeDismissed
            .collectAsStateWithLifecycle(false)
        val tenDaysMs = 10 * 24 * 60 * 60 * 1000L
        val showSurveyNudge = onboardingTimestamp > 0L &&
            (kotlin.time.Clock.System.now().toEpochMilliseconds() - onboardingTimestamp) >= tenDaysMs &&
            !surveyNudgeDismissed

        if (showSurveyNudge) {
            val surveyUrl = stringResource(Res.string.closed_beta_survey_url)
            val surveyUriHandler = LocalUriHandler.current
            AlertDialog(
                onDismissRequest = {
                    nudgeScope.launch {
                        autoBackupPrefs.setClosedBetaSurveyNudgeDismissed(true)
                    }
                },
                title = { Text(stringResource(Res.string.closed_beta_survey_nudge_title)) },
                text = { Text(stringResource(Res.string.closed_beta_survey_nudge_body)) },
                confirmButton = {
                    Button(onClick = {
                        nudgeScope.launch {
                            autoBackupPrefs.setClosedBetaSurveyNudgeDismissed(true)
                        }
                        surveyUriHandler.openUri(surveyUrl)
                    }) {
                        Text(stringResource(Res.string.closed_beta_survey_nudge_cta))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        nudgeScope.launch {
                            autoBackupPrefs.setClosedBetaSurveyNudgeDismissed(true)
                        }
                    }) {
                        Text(stringResource(Res.string.closed_beta_survey_nudge_dismiss))
                    }
                },
            )
        }
        // === END BETA: Closed-beta survey nudge ===

        // Navigate to stock detail screen from low stock notification deep-link
        LaunchedEffect(stockDetailMedicationId) {
            stockDetailMedicationId?.let { medId ->
                navigator.openOverlay(StockTrackingDetailRoute(medId))
                onStockDetailHandled()
            }
        }

        // Navigate to import screen when a backup file is opened from another app
        LaunchedEffect(Unit) {
            IncomingBackupHandler.pendingImport.collect { pending ->
                if (pending != null) {
                    navigator.openOverlay(ImportDataRoute)
                }
            }
        }
    }
}

/**
 * Legacy alias for backward compatibility.
 */
@Composable
fun HelloMedsNavigation(
    modifier: Modifier = Modifier,
    initialRoute: NavKey = MainAppRoute,
    notificationEventIds: IntArray? = null,
    notificationGroupingMode: String? = null,
    onNotificationHandled: () -> Unit = {},
    stockDetailMedicationId: Int? = null,
    onStockDetailHandled: () -> Unit = {},
    screenProviders: NavigationScreenProviders = NavigationScreenProviders(),
) {
    HelloMedsNavigation3(
        modifier = modifier,
        initialRoute = initialRoute,
        notificationEventIds = notificationEventIds,
        notificationGroupingMode = notificationGroupingMode,
        onNotificationHandled = onNotificationHandled,
        stockDetailMedicationId = stockDetailMedicationId,
        onStockDetailHandled = onStockDetailHandled,
        screenProviders = screenProviders,
    )
}
