// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.crypto.DatabaseKeyManager
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.debug_action_force_reconcile
import me.juliana.hellomeds.shared.debug_action_force_reconcile_desc
import me.juliana.hellomeds.shared.debug_action_success_reconcile
import me.juliana.hellomeds.shared.debug_onboarding_force_all
import me.juliana.hellomeds.shared.debug_onboarding_force_all_desc
import me.juliana.hellomeds.shared.debug_onboarding_normal
import me.juliana.hellomeds.shared.debug_onboarding_normal_desc
import me.juliana.hellomeds.shared.debug_screen_title
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListExpandableSection
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListLabel
import me.juliana.hellomeds.ui.viewmodel.DetailedAlarmInfo
import me.juliana.hellomeds.ui.viewmodel.TodayOverview
import me.juliana.hellomeds.ui.viewmodel.toHumanDescription
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSDebugScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: (showAllSteps: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: IOSDebugViewModel = koinViewModel()
    val todayOverview by viewModel.todayOverview.collectAsState()
    val scheduledAlarms by viewModel.scheduledAlarms.collectAsState()
    val notificationStatus by viewModel.notificationStatus.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val databaseHealth by viewModel.databaseHealth.collectAsState()

    AppScaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.debug_screen_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = scrollState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Section 1: Today's Doses ──
            item { SmartListLabel(text = "Today's Doses") }
            item { TodayDosesSection(todayOverview) }

            // ── Section 2: Notifications (iOS) ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Notifications")
            }
            item { NotificationStatusSection(notificationStatus) }

            // ── Section 3: Upcoming Schedules ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Upcoming Schedules")
            }
            item { UpcomingSchedulesSection(scheduledAlarms) }

            // ── Section 4: System & Preferences ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "System & Preferences")
            }
            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Notification authorization") },
                                trailingContent = { Text(notificationStatus.authorizationStatus) },
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Grouping mode") },
                                trailingContent = { Text(preferences.groupingMode) },
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Snooze interval (minutes)") },
                                trailingContent = { Text("${preferences.snoozeIntervalMinutes}") },
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Lock screen visibility") },
                                trailingContent = { Text(preferences.lockScreenVisibility) },
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("App version") },
                                trailingContent = { Text(preferences.detectionMethod) }, // Repurposed for iOS version
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Onboarding completed") },
                                trailingContent = {
                                    Text(if (preferences.onboardingCompleted) "Yes" else "No")
                                },
                                shapes = shapes,
                            )
                        },
                    ),
                )
            }

            // ── Section 5: Database Health ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Database Health")
            }
            item {
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Active medications") },
                                trailingContent = { Text("${databaseHealth.activeMedicationCount}") },
                                shapes = shapes,
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Active schedules") },
                                trailingContent = { Text("${databaseHealth.activeScheduleCount}") },
                                shapes = shapes,
                            )
                        },
                    ),
                )
            }

            // ── Section 5b: Encryption ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Encryption")
            }
            item {
                val keyManager: DatabaseKeyManager = koinInject()
                EncryptionSection(keyManager = keyManager)
            }

            // ── Section 6: Actions ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Actions")
            }
            item {
                val reconcileMessage = stringResource(Res.string.debug_action_success_reconcile)
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.debug_action_force_reconcile)) },
                                supportingContent = {
                                    Text(
                                        stringResource(Res.string.debug_action_force_reconcile_desc),
                                    )
                                },
                                shapes = shapes,
                                onClick = {
                                    viewModel.forceReconcile()
                                    scope.launch { snackbarHostState.showSnackbar(reconcileMessage) }
                                },
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.debug_onboarding_normal)) },
                                supportingContent = { Text(stringResource(Res.string.debug_onboarding_normal_desc)) },
                                shapes = shapes,
                                onClick = { onNavigateToOnboarding(false) },
                            )
                        },
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text(stringResource(Res.string.debug_onboarding_force_all)) },
                                supportingContent = {
                                    Text(
                                        stringResource(Res.string.debug_onboarding_force_all_desc),
                                    )
                                },
                                shapes = shapes,
                                onClick = { onNavigateToOnboarding(true) },
                            )
                        },
                    ),
                )
            }

            item {
                val autoBackupPrefs: AutoBackupPreferences = koinInject()
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, _ ->
                            SmartListItem(
                                headlineContent = { Text("Trigger backup nudge dialog") },
                                supportingContent = {
                                    Text(
                                        "Resets nudge dismissed flag and sets onboarding timestamp to 3 days ago",
                                    )
                                },
                                shapes = shapes,
                                onClick = {
                                    scope.launch {
                                        val threeDaysAgo = kotlin.time.Clock.System.now()
                                            .toEpochMilliseconds() - (3 * 24 * 60 * 60 * 1000L)
                                        autoBackupPrefs.setOnboardingCompletedTimestamp(threeDaysAgo)
                                        autoBackupPrefs.setBackupNudgeDismissed(false)
                                        snackbarHostState.showSnackbar("Nudge will appear on next app open")
                                    }
                                },
                            )
                        },
                    ),
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Section Composables ──

@Composable
private fun EncryptionSection(keyManager: DatabaseKeyManager) {
    val hasKey = remember { keyManager.hasKey() }
    val keyStatus = if (hasKey) "Present" else "Missing"

    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListInfoCard(
                    headlineContent = {
                        Text(if (hasKey) "Database encrypted (SQLCipher)" else "Encryption key missing")
                    },
                    containerColor = if (hasKey) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (hasKey) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    shapes = shapes,
                )
            },
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Key Status") },
                    trailingContent = { Text(keyStatus) },
                    shapes = shapes,
                )
            },
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Key Storage") },
                    trailingContent = { Text("iOS Keychain") },
                    shapes = shapes,
                )
            },
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Cipher") },
                    trailingContent = { Text("SQLCipher AES-256") },
                    shapes = shapes,
                )
            },
        ),
    )
}

@Composable
private fun TodayDosesSection(todayOverview: TodayOverview) {
    val summary = buildString {
        append("${todayOverview.totalCount} total")
        if (todayOverview.takenCount > 0) append(" · ${todayOverview.takenCount} taken")
        if (todayOverview.skippedCount > 0) append(" · ${todayOverview.skippedCount} skipped")
        if (todayOverview.pendingCount > 0) append(" · ${todayOverview.pendingCount} pending")
        if (todayOverview.overdueCount > 0) append(" · ${todayOverview.overdueCount} overdue")
    }

    val hasOverdue = todayOverview.overdueCount > 0

    SmartListInfoCard(
        headlineContent = { Text(if (hasOverdue) "Overdue doses" else "Today's schedule") },
        supportingContent = { Text(summary) },
        containerColor = if (hasOverdue) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (hasOverdue) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    )

    if (todayOverview.doses.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        var dosesExpanded by remember { mutableStateOf(false) }
        SmartListExpandableSection(
            title = "Doses (${todayOverview.doses.size})",
            badge = "${todayOverview.doses.size}",
            expanded = dosesExpanded,
            onExpandToggle = { dosesExpanded = !dosesExpanded },
        ) {
            AutoSmartList(
                items = todayOverview.doses.map { dose ->
                    SmartListItemConfig(visible = true) { shapes, _ ->
                        val tz = TimeZone.currentSystemDefault()
                        val time = Instant.fromEpochMilliseconds(dose.event.scheduledTime)
                            .toLocalDateTime(tz)
                        val timeStr =
                            "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

                        val status = when {
                            dose.event.isTaken -> "TAKEN"
                            dose.event.isSkipped -> "SKIPPED"
                            dose.isOverdue -> "OVERDUE"
                            else -> "PENDING"
                        }

                        SmartListItem(
                            headlineContent = { Text("$timeStr  ${dose.medicationName}") },
                            supportingContent = {
                                Text("${dose.dose} ${dose.strengthUnit ?: ""} · ${dose.scheduleDescription}")
                            },
                            trailingContent = {
                                Text(
                                    text = status,
                                    color = when (status) {
                                        "TAKEN" -> MaterialTheme.colorScheme.primary
                                        "OVERDUE" -> MaterialTheme.colorScheme.error
                                        "SKIPPED" -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.tertiary
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            shapes = shapes,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun NotificationStatusSection(status: IOSNotificationStatus) {
    SmartListInfoCard(
        headlineContent = {
            Text(if (status.healthy) "Notifications healthy" else "Notification issue")
        },
        supportingContent = { Text(status.healthMessage) },
        containerColor = if (status.healthy) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (status.healthy) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    )

    Spacer(modifier = Modifier.height(8.dp))

    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Authorization") },
                    trailingContent = { Text(status.authorizationStatus) },
                    shapes = shapes,
                )
            },
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Pending notifications") },
                    trailingContent = { Text("${status.pendingCount}") },
                    shapes = shapes,
                )
            },
            SmartListItemConfig(visible = true) { shapes, _ ->
                SmartListItem(
                    headlineContent = { Text("Delivered notifications") },
                    trailingContent = { Text("${status.deliveredCount}") },
                    shapes = shapes,
                )
            },
        ),
    )

    if (status.pendingNotifications.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        var pendingExpanded by remember { mutableStateOf(false) }
        SmartListExpandableSection(
            title = "Pending (${status.pendingNotifications.size})",
            badge = "${status.pendingNotifications.size}",
            expanded = pendingExpanded,
            onExpandToggle = { pendingExpanded = !pendingExpanded },
        ) {
            AutoSmartList(
                items = status.pendingNotifications.map { notif ->
                    SmartListItemConfig(visible = true) { shapes, _ ->
                        val tz = TimeZone.currentSystemDefault()
                        val time = Instant.fromEpochMilliseconds(notif.scheduledTime)
                            .toLocalDateTime(tz)
                        val timeStr =
                            "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
                        val dateStr = "${time.month.ordinal + 1}/${time.day}"

                        SmartListItem(
                            headlineContent = { Text("$dateStr $timeStr") },
                            supportingContent = { Text(notif.medicationNames) },
                            trailingContent = {
                                if (notif.isCritical) {
                                    Text(
                                        "CRITICAL",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            shapes = shapes,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun UpcomingSchedulesSection(scheduledAlarms: List<DetailedAlarmInfo>) {
    if (scheduledAlarms.isEmpty()) {
        SmartListInfoCard(
            headlineContent = { Text("No upcoming doses") },
            supportingContent = { Text("No pending medication events in the next 7 days") },
        )
        return
    }

    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val today = now.toLocalDateTime(tz).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    val todayAlarms = scheduledAlarms.filter {
        Instant.fromEpochMilliseconds(it.event.scheduledTime).toLocalDateTime(tz).date == today
    }
    val tomorrowAlarms = scheduledAlarms.filter {
        Instant.fromEpochMilliseconds(it.event.scheduledTime).toLocalDateTime(tz).date == tomorrow
    }
    val laterAlarms = scheduledAlarms.filter {
        val date = Instant.fromEpochMilliseconds(it.event.scheduledTime).toLocalDateTime(tz).date
        date > tomorrow
    }

    if (todayAlarms.isNotEmpty()) {
        var todayExpanded by remember { mutableStateOf(true) }
        SmartListExpandableSection(
            title = "Today",
            badge = "${todayAlarms.size}",
            expanded = todayExpanded,
            onExpandToggle = { todayExpanded = !todayExpanded },
        ) {
            AlarmList(todayAlarms)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (tomorrowAlarms.isNotEmpty()) {
        var tomorrowExpanded by remember { mutableStateOf(false) }
        SmartListExpandableSection(
            title = "Tomorrow",
            badge = "${tomorrowAlarms.size}",
            expanded = tomorrowExpanded,
            onExpandToggle = { tomorrowExpanded = !tomorrowExpanded },
        ) {
            AlarmList(tomorrowAlarms)
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (laterAlarms.isNotEmpty()) {
        var laterExpanded by remember { mutableStateOf(false) }
        SmartListExpandableSection(
            title = "This Week",
            badge = "${laterAlarms.size}",
            expanded = laterExpanded,
            onExpandToggle = { laterExpanded = !laterExpanded },
        ) {
            AlarmList(laterAlarms)
        }
    }
}

@Composable
private fun AlarmList(alarms: List<DetailedAlarmInfo>) {
    AutoSmartList(
        items = alarms.map { alarm ->
            SmartListItemConfig(visible = true) { shapes, _ ->
                val tz = TimeZone.currentSystemDefault()
                val time = Instant.fromEpochMilliseconds(alarm.event.scheduledTime)
                    .toLocalDateTime(tz)
                val timeStr =
                    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
                val medName = alarm.medication?.displayName ?: alarm.medication?.name ?: "Unknown"
                val schedDesc = alarm.schedule?.toHumanDescription() ?: ""
                val labelName = alarm.importanceLabel?.name

                SmartListItem(
                    headlineContent = { Text("$timeStr  $medName") },
                    supportingContent = {
                        Text(
                            buildString {
                                append("${alarm.event.dose} ${alarm.medication?.strengthUnit?.value ?: ""}")
                                if (schedDesc.isNotBlank()) append(" · $schedDesc")
                                if (labelName != null) append(" · $labelName")
                            },
                        )
                    },
                    shapes = shapes,
                )
            }
        },
    )
}
