// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.settings_battery_action_label
import me.juliana.hellomeds.shared.settings_battery_last_resort_info
import me.juliana.hellomeds.shared.settings_battery_status_label
import me.juliana.hellomeds.shared.settings_battery_status_optimized
import me.juliana.hellomeds.shared.settings_battery_status_unrestricted
import me.juliana.hellomeds.shared.settings_battery_title
import me.juliana.hellomeds.shared.settings_notification_advanced
import me.juliana.hellomeds.shared.settings_notification_behavior
import me.juliana.hellomeds.shared.settings_notification_channel_critical
import me.juliana.hellomeds.shared.settings_notification_channel_critical_description
import me.juliana.hellomeds.shared.settings_notification_channel_normal
import me.juliana.hellomeds.shared.settings_notification_channel_normal_description
import me.juliana.hellomeds.shared.settings_notification_channels
import me.juliana.hellomeds.shared.settings_notification_channels_description
import me.juliana.hellomeds.shared.settings_notification_discreet_toggle
import me.juliana.hellomeds.shared.settings_notification_discreet_toggle_description
import me.juliana.hellomeds.shared.settings_notification_grouping
import me.juliana.hellomeds.shared.settings_notification_grouping_combined
import me.juliana.hellomeds.shared.settings_notification_grouping_grouped
import me.juliana.hellomeds.shared.settings_notification_open_settings
import me.juliana.hellomeds.shared.settings_notification_privacy
import me.juliana.hellomeds.shared.settings_notification_privacy_discreet
import me.juliana.hellomeds.shared.settings_notification_privacy_discreet_description
import me.juliana.hellomeds.shared.settings_notification_privacy_hide
import me.juliana.hellomeds.shared.settings_notification_privacy_hide_description
import me.juliana.hellomeds.shared.settings_notification_privacy_show_names
import me.juliana.hellomeds.shared.settings_notification_privacy_show_names_description
import me.juliana.hellomeds.shared.settings_notification_snooze_interval
import me.juliana.hellomeds.shared.settings_notification_snooze_minutes_suffix
import me.juliana.hellomeds.shared.settings_notification_snooze_title
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.PermissionWarningBanners
import me.juliana.hellomeds.ui.components.list.IntegerInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListNavigationItem
import me.juliana.hellomeds.ui.components.list.SmartListRadioItem
import me.juliana.hellomeds.ui.components.list.SmartListSwitchItem
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.util.LocalPermissionWarnings
import me.juliana.hellomeds.ui.util.NotificationChannels
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PermissionWarning
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onNavigateBack: () -> Unit) {
    val context = platformContext()
    val scope = rememberCoroutineScope()
    val prefs = koinInject<NotificationPreferences>()

    val groupingMode by prefs.groupingMode.collectAsStateWithLifecycle(NotificationGroupingMode.COMBINED)
    val lockScreenVisibility by prefs.lockScreenVisibility.collectAsStateWithLifecycle(
        LockScreenVisibility.SHOW_WITH_NAMES,
    )
    val snoozeInterval by prefs.snoozeIntervalMinutes.collectAsStateWithLifecycle(10)

    val permissionState = LocalPermissionWarnings.current
    var dismissedWarnings by remember { mutableStateOf(emptySet<PermissionWarning>()) }

    // Battery optimization (Android only)
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(PermissionUtils.isBatteryOptimizationIgnored(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_notification_advanced)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            // Centralized permission warnings (permissionState read outside LazyColumn scope)
            if (permissionState.hasWarnings) {
                item {
                    PermissionWarningBanners(
                        state = permissionState,
                        dismissedWarnings = dismissedWarnings,
                        onDismiss = { dismissedWarnings = dismissedWarnings + it },
                    )
                }
            }

            // Section 1: Behavior (shared)
            item {
                SettingsHeader(stringResource(Res.string.settings_notification_behavior), isFirst = true)
            }
            item {
                SmartList {
                    SmartListSwitchItem(
                        label = stringResource(Res.string.settings_notification_grouping),
                        checked = groupingMode == NotificationGroupingMode.GROUPED,
                        onCheckedChange = { grouped ->
                            val newMode =
                                if (grouped) NotificationGroupingMode.GROUPED else NotificationGroupingMode.COMBINED
                            scope.launch {
                                prefs.setGroupingMode(newMode)
                                PlatformCapabilities.triggerNotificationReconciliation(context)
                            }
                        },
                        shapes = smartListSegmentedShapes(index = 0, count = 1),
                        supportingText = if (groupingMode == NotificationGroupingMode.COMBINED) {
                            stringResource(Res.string.settings_notification_grouping_combined)
                        } else {
                            stringResource(Res.string.settings_notification_grouping_grouped)
                        },
                    )
                }
            }

            // Section 2: Snooze (shared)
            item {
                SettingsHeader(stringResource(Res.string.settings_notification_snooze_title))
            }
            item {
                SmartList {
                    SmartListTextItem(
                        label = stringResource(Res.string.settings_notification_snooze_interval),
                        value = snoozeInterval.toString(),
                        onValueChange = { newValue ->
                            val minutes = newValue.toIntOrNull()
                            if (minutes != null && minutes in 1..60) {
                                scope.launch { prefs.setSnoozeInterval(minutes) }
                            }
                        },
                        shapes = smartListSegmentedShapes(index = 0, count = 1),
                        suffix = stringResource(Res.string.settings_notification_snooze_minutes_suffix),
                        validator = { value ->
                            val num = value.toIntOrNull()
                            num != null && num in 1..60
                        },
                        inputTransformation = IntegerInputTransformation(),
                    )
                }
            }

            // Section 3: Notification Privacy
            item {
                SettingsHeader(stringResource(Res.string.settings_notification_privacy))
            }
            item {
                SmartList {
                    if (PlatformCapabilities.supportsLockScreenVisibilityControl()) {
                        // Android: 3 radio options controlling both content and lock screen behavior
                        SmartListRadioItem(
                            label = stringResource(Res.string.settings_notification_privacy_show_names),
                            selected = lockScreenVisibility == LockScreenVisibility.SHOW_WITH_NAMES,
                            onClick = {
                                scope.launch { prefs.setLockScreenVisibility(LockScreenVisibility.SHOW_WITH_NAMES) }
                            },
                            shapes = smartListSegmentedShapes(index = 0, count = 3),
                            supportingText = stringResource(
                                Res.string.settings_notification_privacy_show_names_description,
                            ),
                        )
                        SmartListRadioItem(
                            label = stringResource(Res.string.settings_notification_privacy_discreet),
                            selected = lockScreenVisibility == LockScreenVisibility.SHOW_WITHOUT_NAMES,
                            onClick = {
                                scope.launch { prefs.setLockScreenVisibility(LockScreenVisibility.SHOW_WITHOUT_NAMES) }
                            },
                            shapes = smartListSegmentedShapes(index = 1, count = 3),
                            supportingText = stringResource(
                                Res.string.settings_notification_privacy_discreet_description,
                            ),
                        )
                        SmartListRadioItem(
                            label = stringResource(Res.string.settings_notification_privacy_hide),
                            selected = lockScreenVisibility == LockScreenVisibility.HIDE,
                            onClick = {
                                scope.launch { prefs.setLockScreenVisibility(LockScreenVisibility.HIDE) }
                            },
                            shapes = smartListSegmentedShapes(index = 2, count = 3),
                            supportingText = stringResource(Res.string.settings_notification_privacy_hide_description),
                        )
                    } else {
                        // iOS: simple toggle (maps SHOW_WITH_NAMES ↔ SHOW_WITHOUT_NAMES)
                        SmartListSwitchItem(
                            label = stringResource(Res.string.settings_notification_discreet_toggle),
                            checked = lockScreenVisibility != LockScreenVisibility.SHOW_WITH_NAMES,
                            onCheckedChange = { discreet ->
                                val newVisibility = if (discreet) {
                                    LockScreenVisibility.SHOW_WITHOUT_NAMES
                                } else {
                                    LockScreenVisibility.SHOW_WITH_NAMES
                                }
                                scope.launch {
                                    prefs.setLockScreenVisibility(newVisibility)
                                    PlatformCapabilities.triggerNotificationReconciliation(context)
                                }
                            },
                            shapes = smartListSegmentedShapes(index = 0, count = 1),
                            supportingText = stringResource(
                                Res.string.settings_notification_discreet_toggle_description,
                            ),
                        )
                    }
                }
            }

            // Section 4: Notification Channels
            if (PlatformCapabilities.supportsNotificationChannels()) {
                // Android: per-channel links
                item {
                    SettingsHeader(stringResource(Res.string.settings_notification_channels))
                }
                item {
                    SmartList {
                        SmartListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_notification_channel_normal))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_notification_channel_normal_description))
                            },
                            onClick = {
                                PermissionUtils.openChannelSettings(context, NotificationChannels.NORMAL_CHANNEL_ID)
                            },
                            shapes = smartListSegmentedShapes(index = 0, count = 2),
                        )
                        SmartListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_notification_channel_critical))
                            },
                            supportingContent = {
                                Text(stringResource(Res.string.settings_notification_channel_critical_description))
                            },
                            onClick = {
                                PermissionUtils.openChannelSettings(
                                    context,
                                    NotificationChannels.CRITICAL_CHANNEL_ID,
                                )
                            },
                            shapes = smartListSegmentedShapes(index = 1, count = 2),
                        )
                    }
                }
            } else {
                // iOS: single link to system notification settings
                item {
                    SettingsHeader(stringResource(Res.string.settings_notification_channels))
                }
                item {
                    SmartList {
                        SmartListNavigationItem(
                            label = stringResource(Res.string.settings_notification_open_settings),
                            onClick = { PermissionUtils.openNotificationSettings(context) },
                            shapes = smartListSegmentedShapes(index = 0, count = 1),
                            supportingText = stringResource(Res.string.settings_notification_channels_description),
                        )
                    }
                }
            }

            // Section 5: Battery Optimization (Android only)
            if (PlatformCapabilities.supportsBatteryOptimization()) {
                item {
                    SettingsHeader(stringResource(Res.string.settings_battery_title))
                }
                item {
                    SmartList {
                        SmartListInfoCard(
                            headlineContent = {},
                            supportingContent = {
                                Text(
                                    stringResource(Res.string.settings_battery_last_resort_info),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            shapes = smartListSegmentedShapes(index = 0, count = 3),
                        )
                        SmartListItem(
                            headlineContent = {
                                Text(stringResource(Res.string.settings_battery_status_label))
                            },
                            supportingContent = {
                                Text(
                                    if (isBatteryOptimizationIgnored) {
                                        stringResource(Res.string.settings_battery_status_unrestricted)
                                    } else {
                                        stringResource(Res.string.settings_battery_status_optimized)
                                    },
                                )
                            },
                            shapes = smartListSegmentedShapes(index = 1, count = 3),
                        )
                        SmartListNavigationItem(
                            label = stringResource(Res.string.settings_battery_action_label),
                            onClick = { PermissionUtils.openBatteryOptimizationSettings(context) },
                            shapes = smartListSegmentedShapes(index = 2, count = 3),
                        )
                    }
                }
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
