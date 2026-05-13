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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListExpandableSection
import me.juliana.hellomeds.ui.components.list.SmartListInfoCard
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListLabel
import me.juliana.hellomeds.ui.viewmodel.AlarmType
import me.juliana.hellomeds.ui.viewmodel.DatabaseHealth
import me.juliana.hellomeds.ui.viewmodel.DebugViewModel
import me.juliana.hellomeds.ui.viewmodel.DetailedAlarmInfo
import me.juliana.hellomeds.ui.viewmodel.TodayDoseInfo
import me.juliana.hellomeds.ui.viewmodel.TodayOverview
import me.juliana.hellomeds.ui.viewmodel.WakeupReason
import me.juliana.hellomeds.ui.viewmodel.toHumanDescription
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DebugScreen(
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

    val viewModel: DebugViewModel = koinViewModel()
    val todayOverview by viewModel.todayOverview.collectAsState()
    val scheduledAlarms by viewModel.scheduledAlarms.collectAsState()
    val reconcilerStatus by viewModel.reconcilerStatus.collectAsState()
    val systemStatus by viewModel.systemStatus.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val databaseHealth by viewModel.databaseHealth.collectAsState()

    AppScaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Debug Information",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            item {
                SmartListLabel(text = "Today's Doses")
            }

            item {
                TodayDosesSection(todayOverview = todayOverview)
            }

            // ── Section 2: Alarm & Notifications ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Alarm & Notifications")
            }

            item {
                AlarmStatusSection(
                    alarmHealthy = reconcilerStatus.alarmHealthy,
                    alarmHealthMessage = reconcilerStatus.alarmHealthMessage,
                    alarmExists = reconcilerStatus.alarmExists,
                    nextWakeupTime = reconcilerStatus.nextWakeupTime,
                    alarmType = reconcilerStatus.alarmType,
                    wakeupReason = reconcilerStatus.wakeupReason,
                    catchUpEventCount = reconcilerStatus.catchUpEventCount,
                )
            }

            if (reconcilerStatus.sessionDetails.isNotEmpty()) {
                item {
                    SmartListLabel(text = "Active Sessions")
                }

                item {
                    SessionSummaryCard(
                        totalCount = reconcilerStatus.totalSessionCount,
                        activeFollowUpCount = reconcilerStatus.activeFollowUpCount,
                        snoozedCount = reconcilerStatus.snoozedCount,
                    )
                }

                reconcilerStatus.sessionDetails.forEach { detail ->
                    item(key = "session_${detail.session.timeSlotKey}") {
                        SessionDetailSection(detail = detail)
                    }
                }
            }

            // ── Section 3: Upcoming Schedules ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Upcoming Schedules")
            }

            item {
                UpcomingSchedulesSection(scheduledAlarms = scheduledAlarms)
            }

            // ── Section 4: System & Permissions ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "System & Permissions")
            }

            item {
                NotificationsGroup(
                    notificationsEnabled = systemStatus.notificationsEnabled,
                    normalChannelEnabled = systemStatus.normalChannelEnabled,
                    criticalChannelEnabled = systemStatus.criticalChannelEnabled,
                    groupingMode = preferences.groupingMode,
                    lockScreenVisibility = preferences.lockScreenVisibility,
                    snoozeIntervalMinutes = preferences.snoozeIntervalMinutes,
                )
            }

            item {
                AlarmsGroup(
                    exactAlarmsGranted = systemStatus.exactAlarmsGranted,
                    batteryOptimizationIgnored = systemStatus.batteryOptimizationIgnored,
                    useExactAlarms = preferences.useExactAlarms,
                    schedulingWindowHours = preferences.schedulingWindowHours,
                    lastSchedulingTimestamp = preferences.lastSchedulingTimestamp,
                )
            }

            item {
                AppInfoGroup(
                    appVersion = systemStatus.appVersion,
                    buildType = systemStatus.buildType,
                    onboardingCompleted = preferences.onboardingCompleted,
                )
            }

            // ── Section 5: Database ──
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SmartListLabel(text = "Database")
            }

            item {
                DatabaseHealthSection(databaseHealth = databaseHealth)
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
                val reconcileSuccessMsg = "Reconciliation complete"

                val alarmPreviewContext = androidx.compose.ui.platform.LocalContext.current
                ActionsSection(
                    onForceReconcile = {
                        viewModel.forceReconcile()
                        scope.launch { snackbarHostState.showSnackbar(reconcileSuccessMsg) }
                    },
                    onNavigateToOnboarding = onNavigateToOnboarding,
                    onPreviewAlarmScreen = {
                        val intent = android.content.Intent(
                            alarmPreviewContext,
                            me.juliana.hellomeds.notifications.AlarmActivity::class.java,
                        ).apply {
                            putExtra("scheduleIds", intArrayOf(0))
                            putExtra("scheduledTime", System.currentTimeMillis())
                            putExtra("notificationId", -1)
                            putExtra("medicationNames", arrayOf("Ibuprofen", "Vitamin D"))
                        }
                        alarmPreviewContext.startActivity(intent)
                    },
                )
            }

            item {
                val autoBackupPrefs: AutoBackupPreferences = koinInject()
                AutoSmartList(
                    items = listOf(
                        SmartListItemConfig(visible = true) { shapes, visible ->
                            SmartListItem(
                                headlineContent = { Text("Trigger backup nudge dialog") },
                                supportingContent = {
                                    Text(
                                        "Resets nudge dismissed flag and sets onboarding timestamp to 3 days ago",
                                    )
                                },
                                shapes = shapes,
                                visible = visible,
                                onClick = {
                                    scope.launch {
                                        val threeDaysAgo = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
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
        }
    }
}

// ── Section Composables ──

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayDosesSection(todayOverview: TodayOverview) {
    if (todayOverview.totalCount == 0) {
        SmartListInfoCard(
            headlineContent = { Text("No doses scheduled for today") },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        return
    }

    val completedCount = todayOverview.takenCount + todayOverview.skippedCount
    val summaryColor = when {
        todayOverview.overdueCount > 0 -> MaterialTheme.colorScheme.errorContainer
        completedCount == todayOverview.totalCount -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val summaryContentColor = when {
        todayOverview.overdueCount > 0 -> MaterialTheme.colorScheme.onErrorContainer
        completedCount == todayOverview.totalCount -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    val detailParts = mutableListOf<String>()
    if (todayOverview.pendingCount > 0) detailParts.add("${todayOverview.pendingCount} pending")
    if (todayOverview.takenCount > 0) detailParts.add("${todayOverview.takenCount} taken")
    if (todayOverview.skippedCount > 0) detailParts.add("${todayOverview.skippedCount} skipped")

    SmartListInfoCard(
        headlineContent = {
            Text("$completedCount of ${todayOverview.totalCount} completed")
        },
        supportingContent = {
            Text(detailParts.joinToString(" · "))
        },
        containerColor = summaryColor,
        contentColor = summaryContentColor,
    )

    Spacer(modifier = Modifier.height(8.dp))

    AutoSmartList(
        items = todayOverview.doses.map { dose ->
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = {
                        Text("${formatTime(dose.event.scheduledTime)} — ${dose.medicationName}")
                    },
                    supportingContent = {
                        val doseText = if (dose.strengthUnit != null) {
                            "${dose.dose} ${dose.strengthUnit}"
                        } else {
                            "${dose.dose}"
                        }
                        Text("$doseText · ${dose.scheduleDescription}")
                    },
                    trailingContent = {
                        Text(
                            text = doseStatusText(dose),
                            color = doseStatusColor(dose),
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmStatusSection(
    alarmHealthy: Boolean,
    alarmHealthMessage: String,
    alarmExists: Boolean,
    nextWakeupTime: Long?,
    alarmType: AlarmType,
    wakeupReason: WakeupReason,
    catchUpEventCount: Int,
) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListInfoCard(
                    headlineContent = { Text(if (alarmHealthy) "Alarm system healthy" else "Alarm system issue") },
                    supportingContent = { if (!alarmHealthy) Text(alarmHealthMessage) },
                    containerColor = if (alarmHealthy) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (alarmHealthy) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Alarm Registered") },
                    trailingContent = { Text(if (alarmExists) "Yes" else "No") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = nextWakeupTime != null) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Next Wakeup") },
                    trailingContent = {
                        Text(
                            text = nextWakeupTime?.let { formatTimestamp(it) } ?: "None",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = nextWakeupTime != null) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Time Until Wakeup") },
                    trailingContent = {
                        Text(
                            text = nextWakeupTime?.let { calculateTimeUntil(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = nextWakeupTime != null) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Alarm Type") },
                    trailingContent = {
                        Text(
                            text = alarmTypeText(alarmType),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = nextWakeupTime != null) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Wakeup Reason") },
                    trailingContent = {
                        Text(
                            text = wakeupReasonText(wakeupReason),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Catch-up window") },
                    supportingContent = { Text("Pending events in [now − 4h, now]") },
                    trailingContent = {
                        Text(
                            text = "$catchUpEventCount",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionDetailSection(detail: me.juliana.hellomeds.ui.viewmodel.SessionDetail) {
    val session = detail.session
    val timeFormatted = formatTime(session.timeSlotKey.toLongOrNull() ?: 0L)

    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Session: $timeFormatted") },
                    supportingContent = { Text("Notification ID: ${session.notificationId}") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Follow-ups") },
                    trailingContent = {
                        val text = if (session.nextFollowUpTime != null) {
                            "${session.followUpsFired}/${session.maxFollowUps} fired"
                        } else if (session.followUpsFired >= session.maxFollowUps) {
                            "Exhausted (${session.followUpsFired}/${session.maxFollowUps})"
                        } else {
                            "None"
                        }
                        Text(text = text, style = MaterialTheme.typography.bodySmall)
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = session.nextFollowUpTime != null) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Next Follow-up") },
                    trailingContent = {
                        Text(
                            text = session.nextFollowUpTime?.let {
                                "${formatTimestamp(it)} (${calculateTimeUntil(it)})"
                            } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = session.isSnoozed) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Snoozed Until") },
                    trailingContent = {
                        Text(
                            text = session.snoozeUntilTime?.let {
                                "${formatTimestamp(it)} (${calculateTimeUntil(it)})"
                            } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Channel") },
                    trailingContent = {
                        Text(
                            text = if (session.channelId.contains(
                                    "critical",
                                    ignoreCase = true,
                                )
                            ) {
                                "CRITICAL"
                            } else {
                                "NORMAL"
                            },
                            color = if (session.channelId.contains("critical", ignoreCase = true)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Escalation") },
                    trailingContent = {
                        Text(
                            text = session.criticalAfterFollowUp?.let {
                                "Critical after #$it"
                            } ?: "No escalation",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = detail.medicationNames.isNotEmpty()) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Medications") },
                    trailingContent = {
                        Text(
                            text = detail.medicationNames.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpcomingSchedulesSection(scheduledAlarms: List<DetailedAlarmInfo>) {
    if (scheduledAlarms.isEmpty()) {
        SmartListInfoCard(
            headlineContent = { Text("No upcoming schedules") },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
        return
    }

    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)

    val todayAlarms = scheduledAlarms.filter { alarm ->
        Instant.fromEpochMilliseconds(alarm.event.scheduledTime).toLocalDateTime(tz).date == today
    }
    val tomorrowAlarms = scheduledAlarms.filter { alarm ->
        Instant.fromEpochMilliseconds(alarm.event.scheduledTime).toLocalDateTime(tz).date == tomorrow
    }
    val thisWeekAlarms = scheduledAlarms.filter { alarm ->
        val date = Instant.fromEpochMilliseconds(alarm.event.scheduledTime).toLocalDateTime(tz).date
        date > tomorrow
    }

    var todayExpanded by remember { mutableStateOf(true) }
    var tomorrowExpanded by remember { mutableStateOf(false) }
    var thisWeekExpanded by remember { mutableStateOf(false) }

    if (todayAlarms.isNotEmpty()) {
        SmartListExpandableSection(
            title = "Today",
            expanded = todayExpanded,
            onExpandToggle = { todayExpanded = !todayExpanded },
            badge = "${todayAlarms.size}",
        ) {
            AlarmListItems(alarms = todayAlarms)
        }
    }

    if (tomorrowAlarms.isNotEmpty()) {
        SmartListExpandableSection(
            title = "Tomorrow",
            expanded = tomorrowExpanded,
            onExpandToggle = { tomorrowExpanded = !tomorrowExpanded },
            badge = "${tomorrowAlarms.size}",
        ) {
            AlarmListItems(alarms = tomorrowAlarms)
        }
    }

    if (thisWeekAlarms.isNotEmpty()) {
        SmartListExpandableSection(
            title = "This Week",
            expanded = thisWeekExpanded,
            onExpandToggle = { thisWeekExpanded = !thisWeekExpanded },
            badge = "${thisWeekAlarms.size}",
        ) {
            AlarmListItems(alarms = thisWeekAlarms)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmListItems(alarms: List<DetailedAlarmInfo>) {
    AutoSmartList(
        items = alarms.map { alarm ->
            SmartListItemConfig(visible = true) { shapes, visible ->
                val medName = alarm.medication?.displayName ?: alarm.medication?.name ?: "Unknown"
                val doseText = alarm.medication?.strengthUnit?.let { unit ->
                    "${alarm.event.dose} ${unit.value}"
                } ?: "${alarm.event.dose}"
                val scheduleDesc = alarm.schedule?.toHumanDescription() ?: "Unknown"
                val labelName = alarm.importanceLabel?.name

                val supportingParts = mutableListOf(scheduleDesc, "Schedule #${alarm.event.scheduleId}")
                if (labelName != null) supportingParts.add(labelName)

                SmartListItem(
                    headlineContent = {
                        Text("${formatTime(alarm.event.scheduledTime)} — $medName $doseText")
                    },
                    supportingContent = {
                        Text(supportingParts.joinToString(" · "))
                    },
                    shapes = shapes,
                    visible = visible,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationsGroup(
    notificationsEnabled: Boolean,
    normalChannelEnabled: Boolean,
    criticalChannelEnabled: Boolean,
    groupingMode: String,
    lockScreenVisibility: String,
    snoozeIntervalMinutes: Int,
) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Notifications") },
                    trailingContent = { Text(if (notificationsEnabled) "Enabled" else "Disabled") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Normal Channel") },
                    trailingContent = { Text(if (normalChannelEnabled) "Enabled" else "Disabled") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Critical Channel") },
                    trailingContent = { Text(if (criticalChannelEnabled) "Enabled" else "Disabled") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Grouping Mode") },
                    trailingContent = { Text(groupingMode) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Lock Screen") },
                    trailingContent = { Text(lockScreenVisibility) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Snooze Interval") },
                    trailingContent = { Text("$snoozeIntervalMinutes min") },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlarmsGroup(
    exactAlarmsGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    useExactAlarms: Boolean,
    schedulingWindowHours: Int,
    lastSchedulingTimestamp: Long,
) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Exact Alarms Permission") },
                    trailingContent = { Text(if (exactAlarmsGranted) "Granted" else "Not Granted") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Battery Optimization") },
                    trailingContent = { Text(if (batteryOptimizationIgnored) "Ignored" else "Not Ignored") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Use Exact Alarms (pref)") },
                    trailingContent = { Text(useExactAlarms.toString()) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Scheduling Window") },
                    trailingContent = { Text("$schedulingWindowHours hours") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Last Scheduling") },
                    trailingContent = {
                        Text(
                            text = if (lastSchedulingTimestamp > 0L) {
                                formatTimestamp(lastSchedulingTimestamp)
                            } else {
                                "Never"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppInfoGroup(appVersion: String, buildType: String, onboardingCompleted: Boolean) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("App Version") },
                    trailingContent = { Text(appVersion) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Build Type") },
                    trailingContent = { Text(buildType) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Onboarding Completed") },
                    trailingContent = { Text(onboardingCompleted.toString()) },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionSummaryCard(totalCount: Int, activeFollowUpCount: Int, snoozedCount: Int) {
    val parts = mutableListOf("$totalCount total")
    if (activeFollowUpCount > 0) parts.add("$activeFollowUpCount with follow-ups")
    if (snoozedCount > 0) parts.add("$snoozedCount snoozed")

    SmartListInfoCard(
        headlineContent = { Text("Sessions") },
        supportingContent = { Text(parts.joinToString(" · ")) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DatabaseHealthSection(databaseHealth: DatabaseHealth) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Active Medications") },
                    trailingContent = { Text("${databaseHealth.activeMedicationCount}") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Active Schedules") },
                    trailingContent = { Text("${databaseHealth.activeScheduleCount}") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Active Sessions") },
                    trailingContent = { Text("${databaseHealth.activeSessionCount}") },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EncryptionSection(keyManager: DatabaseKeyManager) {
    val hasKey = remember { keyManager.hasKey() }
    val keyStatus = if (hasKey) "Present" else "Missing"

    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
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
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Key Status") },
                    trailingContent = { Text(keyStatus) },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Key Storage") },
                    trailingContent = { Text("EncryptedSharedPreferences") },
                    shapes = shapes,
                    visible = visible,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Cipher") },
                    trailingContent = { Text("SQLCipher AES-256") },
                    shapes = shapes,
                    visible = visible,
                )
            },
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionsSection(
    onForceReconcile: () -> Unit,
    onNavigateToOnboarding: (showAllSteps: Boolean) -> Unit,
    onPreviewAlarmScreen: () -> Unit = {},
) {
    AutoSmartList(
        items = listOf(
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Force Reconcile") },
                    supportingContent = { Text("Trigger alarm reconciler immediately") },
                    shapes = shapes,
                    visible = visible,
                    onClick = onForceReconcile,
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Normal Onboarding Flow") },
                    supportingContent = {
                        Text(
                            "Launch onboarding with smart permission skipping (granted permissions are hidden)",
                        )
                    },
                    shapes = shapes,
                    visible = visible,
                    onClick = { onNavigateToOnboarding(false) },
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Force All Steps") },
                    supportingContent = { Text("Show all onboarding screens regardless of permission status") },
                    shapes = shapes,
                    visible = visible,
                    onClick = { onNavigateToOnboarding(true) },
                )
            },
            SmartListItemConfig(visible = true) { shapes, visible ->
                SmartListItem(
                    headlineContent = { Text("Preview alarm screen") },
                    supportingContent = { Text("Launch the fullscreen alarm UI with test data") },
                    shapes = shapes,
                    visible = visible,
                    onClick = onPreviewAlarmScreen,
                )
            },
        ),
    )
}

// ── Helper Functions ──

@Composable
private fun doseStatusText(dose: TodayDoseInfo): String {
    return when {
        dose.event.isTaken -> {
            val takenTime = dose.event.historyRecord?.takenTime
            if (takenTime != null) "TAKEN ${formatTime(takenTime)}" else "TAKEN"
        }

        dose.event.historyRecord?.status == MedicationHistory.STATUS_AUTO_SKIPPED -> "AUTO_SKIPPED"
        dose.event.isSkipped -> "SKIPPED"
        dose.isOverdue -> "OVERDUE"
        else -> "PENDING"
    }
}

@Composable
private fun doseStatusColor(dose: TodayDoseInfo): androidx.compose.ui.graphics.Color {
    return when {
        dose.event.isTaken -> MaterialTheme.colorScheme.primary
        dose.event.isSkipped -> MaterialTheme.colorScheme.tertiary
        dose.isOverdue -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun alarmTypeText(type: AlarmType): String = when (type) {
    AlarmType.NONE -> "None"
    AlarmType.SET_ALARM_CLOCK -> "setAlarmClock (critical)"
    AlarmType.SET_EXACT -> "setExactAndAllowWhileIdle"
    AlarmType.SET_INEXACT -> "setAndAllowWhileIdle (fallback)"
}

private fun wakeupReasonText(reason: WakeupReason): String = when (reason) {
    WakeupReason.NONE -> "None"
    WakeupReason.SCHEDULED_EVENT -> "Scheduled dose"
    WakeupReason.FOLLOW_UP -> "Follow-up reminder"
    WakeupReason.SNOOZE -> "Snoozed reminder"
}

private fun formatTimestamp(timeMillis: Long): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}

private fun formatTime(timeMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timeMillis))
}

private fun calculateTimeUntil(scheduledTime: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = scheduledTime - now

    if (diffMs <= 0) return "now"

    val minutes = (diffMs / (60 * 1000)) % 60
    val hours = (diffMs / (60 * 60 * 1000)) % 24
    val days = diffMs / (24 * 60 * 60 * 1000)

    return buildString {
        if (days > 0) append("${days}d ")
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || (days == 0L && hours == 0L)) append("${minutes}m")
    }.trim()
}
