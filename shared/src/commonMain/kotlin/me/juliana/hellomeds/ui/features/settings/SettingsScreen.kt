// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.debug_mode_enabled
import me.juliana.hellomeds.shared.debug_screen_title
import me.juliana.hellomeds.shared.importance_labels_section_description
import me.juliana.hellomeds.shared.importance_labels_section_title
import me.juliana.hellomeds.shared.screen_settings
import me.juliana.hellomeds.shared.settings_about_source_code
import me.juliana.hellomeds.shared.settings_about_source_code_url
import me.juliana.hellomeds.shared.settings_about_title
import me.juliana.hellomeds.shared.settings_about_version
import me.juliana.hellomeds.shared.settings_about_website
import me.juliana.hellomeds.shared.settings_about_website_url
import me.juliana.hellomeds.shared.settings_closed_beta_body
import me.juliana.hellomeds.shared.settings_closed_beta_cta
import me.juliana.hellomeds.shared.settings_closed_beta_support_cta
import me.juliana.hellomeds.shared.settings_closed_beta_title
import me.juliana.hellomeds.shared.settings_closed_beta_url
// BETA: Closed-beta survey nudge. Remove per BETA_ROLLBACK.md before public release.
import me.juliana.hellomeds.shared.closed_beta_survey_settings_label
import me.juliana.hellomeds.shared.closed_beta_survey_settings_description
import me.juliana.hellomeds.shared.closed_beta_survey_url
import me.juliana.hellomeds.shared.settings_appearance_dynamic_color
import me.juliana.hellomeds.shared.settings_appearance_dynamic_color_description
import me.juliana.hellomeds.shared.auto_backup_disabled_banner
import me.juliana.hellomeds.shared.settings_auto_backup
import me.juliana.hellomeds.shared.settings_auto_backup_disabled
import me.juliana.hellomeds.shared.settings_auto_backup_enabled
import me.juliana.hellomeds.shared.settings_camera_configure
import me.juliana.hellomeds.shared.settings_camera_configure_description
import me.juliana.hellomeds.shared.settings_camera_description
import me.juliana.hellomeds.shared.settings_camera_enable
import me.juliana.hellomeds.shared.settings_camera_title
import me.juliana.hellomeds.shared.settings_data_export
import me.juliana.hellomeds.shared.settings_data_export_description
import me.juliana.hellomeds.shared.settings_data_import
import me.juliana.hellomeds.shared.settings_data_import_description
import me.juliana.hellomeds.shared.settings_data_title
import me.juliana.hellomeds.shared.settings_general_title
import me.juliana.hellomeds.shared.settings_notification_advanced
import me.juliana.hellomeds.shared.settings_notification_advanced_description
import me.juliana.hellomeds.shared.settings_notification_disabled_message
import me.juliana.hellomeds.shared.settings_notification_disabled_warning
import me.juliana.hellomeds.shared.settings_notification_enable
import me.juliana.hellomeds.shared.settings_notification_enable_description
import me.juliana.hellomeds.shared.settings_notification_exact_alarms
import me.juliana.hellomeds.shared.settings_notification_exact_description
import me.juliana.hellomeds.shared.settings_notification_exact_disabled
import me.juliana.hellomeds.shared.settings_notification_exact_not_granted
import me.juliana.hellomeds.shared.settings_notification_grant_permission
import me.juliana.hellomeds.shared.settings_notification_inexact_warning
import me.juliana.hellomeds.shared.settings_notification_open_settings
import me.juliana.hellomeds.shared.settings_notification_title
import me.juliana.hellomeds.shared.settings_privacy_policy
import me.juliana.hellomeds.shared.settings_privacy_policy_url
import me.juliana.hellomeds.shared.settings_screen_privacy
import me.juliana.hellomeds.shared.settings_screen_privacy_description
import me.juliana.hellomeds.shared.support_settings_description
import me.juliana.hellomeds.shared.support_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListNavigationItem
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.isNotificationPermissionGranted
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToImportanceLabels: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
    onNavigateToAutoBackup: () -> Unit = {},
    onNavigateToExportData: () -> Unit = {},
    onNavigateToImportData: () -> Unit = {},
    onNavigateToSupport: () -> Unit = {},
    onNavigateToCameraSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )
    val snackbarHostState = remember { SnackbarHostState() }

    // Preferences (all shared via DataStore)
    val cameraPreferences = koinInject<CameraPreferences>()
    val hasConsented by cameraPreferences.hasConsented.collectAsStateWithLifecycle(initial = false)

    val notifPrefs = koinInject<NotificationPreferences>()
    val notificationsEnabled by notifPrefs.notificationsEnabled.collectAsStateWithLifecycle(true)
    val useExactAlarms by notifPrefs.useExactAlarms.collectAsStateWithLifecycle(true)

    val appearancePrefs = koinInject<AppearancePreferences>()
    val useDynamicColor by appearancePrefs.useDynamicColor.collectAsStateWithLifecycle(true)
    val screenPrivacy by appearancePrefs.screenPrivacy.collectAsStateWithLifecycle(false)

    val autoBackupPrefs = koinInject<AutoBackupPreferences>()
    val autoBackupEnabled by autoBackupPrefs.autoBackupEnabled.collectAsStateWithLifecycle(false)

    // System permission states (reactive — updates when user returns from Settings)
    val systemNotificationsEnabled = isNotificationPermissionGranted()
    var canScheduleExact by remember {
        mutableStateOf(PermissionUtils.canScheduleExactAlarms(context))
    }

    // Computed visibility
    val showNotificationDisabledWarning = !systemNotificationsEnabled && notificationsEnabled
    val showExactAlarmWarning = notificationsEnabled && !canScheduleExact
    val showInexactWarning =
        notificationsEnabled && PlatformCapabilities.supportsExactAlarmToggle() && (!useExactAlarms || !canScheduleExact)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(Res.string.screen_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            state = scrollState,
        ) {
            // === BETA: Closed-beta announcement card. Remove per BETA_ROLLBACK.md before release. ===
            item {
                val betaUriHandler = LocalUriHandler.current
                val closedBetaUrl = stringResource(Res.string.settings_closed_beta_url)
                // BETA: Closed-beta survey nudge — gate the survey link on 10-day window.
                val onboardingTimestamp by autoBackupPrefs.onboardingCompletedTimestamp
                    .collectAsStateWithLifecycle(0L)
                val tenDaysMs = 10 * 24 * 60 * 60 * 1000L
                val showSurveyLink = onboardingTimestamp > 0L &&
                    (
                        kotlin.time.Clock.System.now().toEpochMilliseconds() -
                            onboardingTimestamp
                        ) >= tenDaysMs
                val surveyUrl = stringResource(Res.string.closed_beta_survey_url)
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.settings_closed_beta_title),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(Res.string.settings_closed_beta_body),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { betaUriHandler.openUri(closedBetaUrl) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                                ),
                                            ) {
                                                Text(stringResource(Res.string.settings_closed_beta_cta))
                                            }
                                            OutlinedButton(
                                                onClick = onNavigateToSupport,
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                ),
                                            ) {
                                                Text(stringResource(Res.string.settings_closed_beta_support_cta))
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        // BETA: Closed-beta survey link — appears 10 days after onboarding so testers
                        // always have a way to find the survey even if they dismissed the dialog.
                        // Remove per BETA_ROLLBACK.md before public release.
                        SmartListItemConfig(visible = showSurveyLink) { shapes, visible ->
                            SmartListItem(
                                headlineContent = {
                                    Text(stringResource(Res.string.closed_beta_survey_settings_label))
                                },
                                supportingContent = {
                                    Text(stringResource(Res.string.closed_beta_survey_settings_description))
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = { betaUriHandler.openUri(surveyUrl) },
                            )
                        },
                    ),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // === NOTIFICATION SETTINGS ===
            item {
                SettingsHeader(text = stringResource(Res.string.settings_notification_title))
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.importance_labels_section_title),
                                onClick = onNavigateToImportanceLabels,
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.importance_labels_section_description),
                                modifier = Modifier.testTag(ScreenshotTestTags.SETTINGS_IMPORTANCE_LABELS),
                            )
                        },
                    ),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                AutoSmartList(
                    items = listOf(
                        // Notification disabled warning
                        SmartListItemConfig(visible = showNotificationDisabledWarning) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        stringResource(Res.string.settings_notification_disabled_warning),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(Res.string.settings_notification_disabled_message),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Button(
                                            onClick = { PermissionUtils.openNotificationSettings(context) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError,
                                            ),
                                        ) {
                                            Text(stringResource(Res.string.settings_notification_open_settings))
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },

                        // Exact alarm warning
                        SmartListItemConfig(
                            visible = notificationsEnabled && !showNotificationDisabledWarning && (showExactAlarmWarning || showInexactWarning),
                        ) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Text(
                                        if (showExactAlarmWarning) {
                                            stringResource(Res.string.settings_notification_exact_not_granted)
                                        } else {
                                            stringResource(Res.string.settings_notification_exact_disabled)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(Res.string.settings_notification_inexact_warning),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (showExactAlarmWarning) {
                                            Button(
                                                onClick = { PermissionUtils.openExactAlarmSettings(context) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error,
                                                    contentColor = MaterialTheme.colorScheme.onError,
                                                ),
                                            ) {
                                                Text(stringResource(Res.string.settings_notification_grant_permission))
                                            }
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },

                        // Enable notifications switch
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.settings_notification_enable),
                                checked = notificationsEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        notifPrefs.setNotificationsEnabled(enabled)
                                        PlatformCapabilities.triggerNotificationReconciliation(context)
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.settings_notification_enable_description),
                            )
                        },

                        // Exact alarms toggle (Android < TIRAMISU only)
                        SmartListItemConfig(
                            visible = notificationsEnabled && PlatformCapabilities.supportsExactAlarmToggle(),
                        ) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.settings_notification_exact_alarms),
                                checked = useExactAlarms,
                                onCheckedChange = {
                                    scope.launch {
                                        notifPrefs.setUseExactAlarms(it)
                                        PlatformCapabilities.triggerNotificationReconciliation(context)
                                    }
                                },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.settings_notification_exact_description),
                            )
                        },

                        // Advanced notification settings
                        SmartListItemConfig(visible = notificationsEnabled) { shapes, visible ->
                            SmartListItem(
                                headlineContent = {
                                    Text(stringResource(Res.string.settings_notification_advanced))
                                },
                                supportingContent = {
                                    Text(stringResource(Res.string.settings_notification_advanced_description))
                                },
                                onClick = { onNavigateToNotificationSettings() },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            // === GENERAL SETTINGS ===
            item {
                SettingsHeader(text = stringResource(Res.string.settings_general_title))
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = PlatformCapabilities.supportsDynamicColor()) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.settings_appearance_dynamic_color),
                                checked = useDynamicColor,
                                onCheckedChange = { enabled ->
                                    scope.launch { appearancePrefs.setUseDynamicColor(enabled) }
                                },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(
                                    Res.string.settings_appearance_dynamic_color_description,
                                ),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListSwitchItem(
                                label = stringResource(Res.string.settings_screen_privacy),
                                checked = screenPrivacy,
                                onCheckedChange = { enabled ->
                                    scope.launch { appearancePrefs.setScreenPrivacy(enabled) }
                                },
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(
                                    Res.string.settings_screen_privacy_description,
                                ),
                            )
                        },
                    ),
                )
            }

            // === DATA SECTION ===
            item {
                SettingsHeader(text = stringResource(Res.string.settings_data_title))
            }

            // Disabled banner shown above the auto-backup entry when backups are off.
            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = !autoBackupEnabled) { shapes, visible ->
                            SmartListInfoCard(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                        )
                                        Text(stringResource(Res.string.auto_backup_disabled_banner))
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                    ),
                )
            }

            // Conditional gap between the banner and the auto-backup entry — only when the
            // banner is shown, so the entry sits flush against the data group otherwise.
            if (!autoBackupEnabled) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_auto_backup),
                                onClick = onNavigateToAutoBackup,
                                shapes = shapes,
                                visible = visible,
                                supportingText = if (autoBackupEnabled) {
                                    stringResource(Res.string.settings_auto_backup_enabled)
                                } else {
                                    stringResource(Res.string.settings_auto_backup_disabled)
                                },
                                trailingIcon = if (autoBackupEnabled) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_data_export),
                                onClick = onNavigateToExportData,
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.settings_data_export_description),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_data_import),
                                onClick = onNavigateToImportData,
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.settings_data_import_description),
                            )
                        },
                    ),
                )
            }

            // === CAMERA SETTINGS === (hidden for F-Droid builds)
            if (PlatformCapabilities.supportsCameraDetection()) {
                item {
                    SettingsHeader(text = stringResource(Res.string.settings_camera_title))
                }

                item {
                    AutoSmartList(
                        items = listOf(
                            // Camera consent switch
                            SmartListItemConfig(visible = true) { shapes, visible ->
                                SmartListSwitchItem(
                                    label = stringResource(Res.string.settings_camera_enable),
                                    checked = hasConsented,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            cameraPreferences.setConsent(enabled)
                                            if (!enabled) cameraPreferences.markDialogShown(false)
                                        }
                                    },
                                    shapes = shapes,
                                    visible = visible,
                                    supportingText = stringResource(Res.string.settings_camera_description),
                                )
                            },

                            // Configure subpage (privacy + detection method)
                            SmartListItemConfig(visible = hasConsented) { shapes, visible ->
                                SmartListNavigationItem(
                                    label = stringResource(Res.string.settings_camera_configure),
                                    onClick = onNavigateToCameraSettings,
                                    shapes = shapes,
                                    visible = visible,
                                    supportingText = stringResource(Res.string.settings_camera_configure_description),
                                )
                            },
                        ),
                    )
                }
            } // end camera settings guard

            // === ABOUT ===
            item {
                SettingsHeader(text = stringResource(Res.string.settings_about_title))
            }

            item {
                val versionText = PlatformCapabilities.appVersionString()
                val uriHandler = LocalUriHandler.current
                val websiteUrl = stringResource(Res.string.settings_about_website_url)
                val sourceCodeUrl = stringResource(Res.string.settings_about_source_code_url)
                val privacyPolicyUrl = stringResource(Res.string.settings_privacy_policy_url)

                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.support_title),
                                onClick = onNavigateToSupport,
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.support_settings_description),
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_about_website),
                                onClick = { uriHandler.openUri(websiteUrl) },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_about_source_code),
                                onClick = { uriHandler.openUri(sourceCodeUrl) },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.settings_privacy_policy),
                                onClick = { uriHandler.openUri(privacyPolicyUrl) },
                                shapes = shapes,
                                visible = visible,
                            )
                        },
                        SmartListItemConfig(visible = PlatformCapabilities.isDebugBuild()) { shapes, visible ->
                            SmartListNavigationItem(
                                label = stringResource(Res.string.debug_screen_title),
                                onClick = onNavigateToDebug,
                                shapes = shapes,
                                visible = visible,
                                supportingText = stringResource(Res.string.debug_mode_enabled),
                            )
                        },
                    ),
                )
            }

            item {
                val versionLabel = stringResource(Res.string.settings_about_version)
                val versionNumber = PlatformCapabilities.appVersionString()
                Text(
                    text = versionNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .semantics { contentDescription = "$versionLabel $versionNumber" },
                )
            }
        }
    }
}
