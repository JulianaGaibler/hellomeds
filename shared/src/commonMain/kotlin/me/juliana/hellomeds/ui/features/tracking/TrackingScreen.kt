// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.tracking

// colorResource removed - using hardcoded Color values for CMP compatibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.Schedule
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.util.MissedDoseDetector
import me.juliana.hellomeds.data.util.TimeProvider
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_close_menu
import me.juliana.hellomeds.shared.accessibility_collapsed
import me.juliana.hellomeds.shared.accessibility_expanded
import me.juliana.hellomeds.shared.accessibility_loading
import me.juliana.hellomeds.shared.accessibility_toggle_menu
import me.juliana.hellomeds.shared.date_relative_day_time_format
import me.juliana.hellomeds.shared.date_today
import me.juliana.hellomeds.shared.date_yesterday
import me.juliana.hellomeds.shared.illustration_empty_no_schedule
import me.juliana.hellomeds.shared.outline_step_over_24px
import me.juliana.hellomeds.shared.tracking_active_badge
import me.juliana.hellomeds.shared.tracking_empty_state
import me.juliana.hellomeds.shared.tracking_fab_tooltip
import me.juliana.hellomeds.shared.tracking_log_as_needed
import me.juliana.hellomeds.shared.tracking_log_scheduled
import me.juliana.hellomeds.shared.tracking_missed_dose_banner
import me.juliana.hellomeds.shared.tracking_overdue_section_title
import me.juliana.hellomeds.shared.tracking_placebo_badge
import me.juliana.hellomeds.shared.tracking_section_missed
import me.juliana.hellomeds.shared.tracking_section_scheduled
import me.juliana.hellomeds.shared.tracking_section_skipped
import me.juliana.hellomeds.shared.tracking_section_taken
import me.juliana.hellomeds.ui.compat.ExpandableFabMenu
import me.juliana.hellomeds.ui.compat.FabMenuItem
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.LoadingIndicator
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.AutoBackupWarningBanner
import me.juliana.hellomeds.ui.components.PermissionWarningBanners
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.common.OverflowMenu
import me.juliana.hellomeds.ui.components.common.OverflowMenuPrimaryAction
import me.juliana.hellomeds.ui.components.common.SwipeableListItem
import me.juliana.hellomeds.ui.components.list.LazySmartListItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.components.medication.LogStatus
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.components.pickers.DatePickerHeader
import me.juliana.hellomeds.ui.components.pickers.HorizontalDatePicker
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.LocalPermissionWarnings
import me.juliana.hellomeds.ui.util.PermissionWarning
import me.juliana.hellomeds.ui.util.formatLogEventDose
import me.juliana.hellomeds.ui.util.formatLogEventTime
import me.juliana.hellomeds.ui.util.formatMedicationTypeAndStrength
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * State holder for TrackingScreen display data.
 */
data class TrackingScreenState(
    val scheduledEvents: List<ProjectedEventWithMedication>,
    val takenEvents: List<ProjectedEventWithMedication>,
    val skippedEvents: List<ProjectedEventWithMedication>,
    val overdueEvents: List<ProjectedEventWithMedication> = emptyList(),
    val allMedications: List<Medication>,
    val allSchedules: List<Schedule>,
    val selectedDate: LocalDate,
    val hasLoaded: Boolean = true,
    val notificationEventIds: IntArray? = null,
    val notificationGroupingMode: String? = null,
    val showMissedDoseBanner: Boolean = false,
)

/**
 * Action callbacks for TrackingScreen interactions.
 */
data class TrackingScreenActions(
    val onDateSelected: (LocalDate) -> Unit,
    val onNavigateToSettings: () -> Unit,
    val onNavigateToSupport: () -> Unit,
    val onSaveScheduledLogs: (List<ScheduledMedicationLog>) -> Unit,
    val onSaveAsNeededLogs: (List<AsNeededMedicationLog>) -> Unit,
    val onMarkAsTaken: (ProjectedEventWithMedication) -> Unit,
    val onMarkAsSkipped: (ProjectedEventWithMedication) -> Unit,
    val onLogScheduledItem: (ProjectedEvent, LocalTime, Double, Boolean) -> Unit,
    val onUpdateLoggedItem: (ProjectedEvent, LocalTime, Double, LogStatus?) -> Unit,
    val onDeleteLoggedItem: (ProjectedEvent) -> Unit,
    val onNotificationHandled: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    state: TrackingScreenState,
    actions: TrackingScreenActions,
    modifier: Modifier = Modifier,
    trackingViewModel: me.juliana.hellomeds.ui.viewmodel.TrackingViewModel = org.koin.compose.viewmodel.koinViewModel(),
) {
    // Destructure state and actions for backwards-compatible access
    val scheduledEvents = state.scheduledEvents
    val takenEvents = state.takenEvents
    val skippedEvents = state.skippedEvents
    val allMedications = state.allMedications
    val allSchedules = state.allSchedules
    val selectedDate = state.selectedDate
    val hasLoaded = state.hasLoaded
    val notificationEventIds = state.notificationEventIds
    val notificationGroupingMode = state.notificationGroupingMode
    val onDateSelected = actions.onDateSelected
    val onNavigateToSettings = actions.onNavigateToSettings
    val onNavigateToSupport = actions.onNavigateToSupport
    val onSaveScheduledLogs = actions.onSaveScheduledLogs
    val onSaveAsNeededLogs = actions.onSaveAsNeededLogs
    val onMarkAsTaken = actions.onMarkAsTaken
    val onMarkAsSkipped = actions.onMarkAsSkipped
    val onLogScheduledItem = actions.onLogScheduledItem
    val onUpdateLoggedItem = actions.onUpdateLoggedItem
    val onDeleteLoggedItem = actions.onDeleteLoggedItem
    val onNotificationHandled = actions.onNotificationHandled

    // Split today's pending events into past (Missed/Overdue) and future (Scheduled).
    // Finding #24 fix: a 9 AM dose with no log at 4 PM is no longer indistinguishable
    // from a 5 PM future dose. Severity within the past bucket uses the same
    // grace period as MissedDoseDetector — within 1h is OVERDUE, beyond is MISSED.
    // Non-today views keep the unified "Scheduled" rendering since the distinction
    // is only meaningful relative to the current clock.
    val timeProvider: TimeProvider = koinInject()
    val nowMs = timeProvider.nowMillis()
    val today = Instant.fromEpochMilliseconds(nowMs)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    val isTodaySelected = state.selectedDate == today

    val (pastTodayPending, futureTodayPending) = if (isTodaySelected) {
        scheduledEvents.partition { it.event.scheduledTime <= nowMs }
    } else {
        Pair(emptyList(), scheduledEvents)
    }
    val allPastPending = state.overdueEvents + pastTodayPending
    val (overdueWithinGrace, missedBeyondGrace) = allPastPending.partition {
        it.event.scheduledTime + MissedDoseDetector.GRACE_PERIOD_MS > nowMs
    }

    val permissionState = LocalPermissionWarnings.current
    var dismissedWarnings by remember { mutableStateOf(emptySet<PermissionWarning>()) }
    var backupWarningDismissed by remember { mutableStateOf(false) }
    val autoBackupPrefs = org.koin.compose.koinInject<me.juliana.hellomeds.data.preferences.AutoBackupPreferences>()
    val backupEnabled by autoBackupPrefs.autoBackupEnabled.collectAsStateWithLifecycle(initial = false)
    val backupFailures by autoBackupPrefs.consecutiveFailures.collectAsStateWithLifecycle(initial = 0)
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showLogMedicationSheet by remember { mutableStateOf(false) }
    var logMedicationMode by remember { mutableStateOf(LogMedicationMode.SCHEDULED) }

    // Dialog state
    var showLogMedicationDialog by remember { mutableStateOf(false) }
    var showEditLoggedDialog by remember { mutableStateOf(false) }
    var selectedEventForDialog by remember { mutableStateOf<ProjectedEventWithMedication?>(null) }

    // State for notification-triggered filtering (Step 3)
    var filteredNotificationEvents by remember {
        mutableStateOf<List<ProjectedEventWithMedication>?>(
            null,
        )
    }

    // Preview date for real-time header updates as user scrolls
    var previewDate by remember(selectedDate) { mutableStateOf(selectedDate) }

    // Hoisted clock — avoids calling Clock.System.now() per LazyColumn item
    val twoDaysFromNowMs = remember { Clock.System.now().toEpochMilliseconds() + (2 * 24 * 60 * 60 * 1000L) }

    // Snackbar for error events
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        trackingViewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Collect completion status map from ViewModel (reactive to all log event changes)
    val completionStatusMap by trackingViewModel.completionStatusMap.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollForward
        }
    }
    val focusRequester = FocusRequester()

    // Handle notification intent to auto-open log sheets
    LaunchedEffect(notificationEventIds, scheduledEvents) {
        if (notificationEventIds == null || notificationEventIds.isEmpty()) {
            filteredNotificationEvents = null
            return@LaunchedEffect
        }

        if (scheduledEvents.isEmpty()) return@LaunchedEffect

        // Filter scheduled events by notification schedule IDs
        val notificationEvents = scheduledEvents.filter { eventWithMed ->
            eventWithMed.event.scheduleId in notificationEventIds
        }

        if (notificationEvents.isEmpty()) {
            onNotificationHandled()
            return@LaunchedEffect
        }

        if (notificationGroupingMode == "GROUPED") {
            selectedEventForDialog = notificationEvents.first()
            showLogMedicationDialog = true
        } else {
            filteredNotificationEvents = notificationEvents
            logMedicationMode = LogMedicationMode.SCHEDULED
            showLogMedicationSheet = true
        }

        onNotificationHandled()
    }

    PlatformBackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    Box(modifier = modifier.fillMaxSize()) {
        AppScaffold(
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
            topBar = {
                DatePickerHeader(
                    selectedDate = previewDate,
                    actions = {
                        OverflowMenu(
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToSupport = onNavigateToSupport,
                            primaryAction = OverflowMenuPrimaryAction(
                                label = stringResource(Res.string.date_today),
                                onClick = {
                                    onDateSelected(
                                        Clock.System.now()
                                            .toLocalDateTime(TimeZone.currentSystemDefault()).date,
                                    )
                                },
                            ),
                        )
                    },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 88.dp), // Space for FAB
            ) {
                // Horizontal Date Picker
                item {
                    HorizontalDatePicker(
                        selectedDate = selectedDate,
                        onDateSelected = onDateSelected,
                        modifier = Modifier.fillMaxWidth(),
                        completionStatusMap = completionStatusMap,
                        onCenteredDateChanged = { date -> previewDate = date },
                    )
                }

                // Divider
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                // Permission warnings (permissionState read outside LazyColumn scope)
                if (permissionState.hasWarnings) {
                    item {
                        PermissionWarningBanners(
                            state = permissionState,
                            dismissedWarnings = dismissedWarnings,
                            onDismiss = { dismissedWarnings = dismissedWarnings + it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Auto-backup failure warning
                if (backupEnabled && backupFailures > 0 && !backupWarningDismissed) {
                    item {
                        AutoBackupWarningBanner(
                            consecutiveFailures = backupFailures,
                            onNavigateToSettings = onNavigateToSettings,
                            onDismiss = { backupWarningDismissed = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Missed medications section — past doses beyond the 1h grace period.
                // Sorted ascending in [allPastPending], so the oldest miss surfaces first.
                if (missedBeyondGrace.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.tracking_section_missed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                        )
                    }

                    itemsIndexed(
                        items = missedBeyondGrace,
                        key = { _, it -> "missed_${it.event.compositeKey}" },
                    ) { index, eventWithMed ->
                        // Disambiguate doses that crossed midnight from earlier
                        // today's doses by prefixing the time with a relative-day
                        // label. The format string is translator-controlled so
                        // languages can adjust separator and word order.
                        val tz = TimeZone.currentSystemDefault()
                        val eventDate = Instant.fromEpochMilliseconds(eventWithMed.event.scheduledTime)
                            .toLocalDateTime(tz).date
                        val yesterday = today.minus(1, DateTimeUnit.DAY)
                        val dayLabel = when (eventDate) {
                            today -> stringResource(Res.string.date_today)
                            yesterday -> stringResource(Res.string.date_yesterday)
                            else -> null
                        }
                        val baseTime = formatLogEventTime(eventWithMed.event)
                        val prefixedTime = if (dayLabel != null) {
                            stringResource(Res.string.date_relative_day_time_format, dayLabel, baseTime)
                        } else {
                            baseTime
                        }
                        SwipeableMedicationLogListItem(
                            eventWithMedication = eventWithMed,
                            onClick = {
                                selectedEventForDialog = eventWithMed
                                showLogMedicationDialog = true
                            },
                            onMarkAsTaken = { onMarkAsTaken(eventWithMed) },
                            onMarkAsSkipped = { onMarkAsSkipped(eventWithMed) },
                            shapes = smartListSegmentedShapes(
                                index = index,
                                count = missedBeyondGrace.size,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                            timeOverride = prefixedTime,
                        )

                        if (index < missedBeyondGrace.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Overdue medications section — past doses still within the 1h grace.
                if (overdueWithinGrace.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.tracking_overdue_section_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                        )
                    }

                    itemsIndexed(
                        items = overdueWithinGrace,
                        key = { _, it -> "overdue_${it.event.compositeKey}" },
                    ) { index, eventWithMed ->
                        SwipeableMedicationLogListItem(
                            eventWithMedication = eventWithMed,
                            onClick = {
                                selectedEventForDialog = eventWithMed
                                showLogMedicationDialog = true
                            },
                            onMarkAsTaken = { onMarkAsTaken(eventWithMed) },
                            onMarkAsSkipped = { onMarkAsSkipped(eventWithMed) },
                            shapes = smartListSegmentedShapes(
                                index = index,
                                count = overdueWithinGrace.size,
                            ),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )

                        if (index < overdueWithinGrace.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Taken medications section
                if (takenEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.tracking_section_taken),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                        )
                    }

                    itemsIndexed(
                        items = takenEvents,
                        key = { _, it -> "taken_${it.event.historyRecord?.id ?: it.event.compositeKey}" },
                    ) { index, eventWithMed ->
                        SwipeableMedicationLogListItem(
                            eventWithMedication = eventWithMed,
                            onClick = {
                                selectedEventForDialog = eventWithMed
                                showEditLoggedDialog = true
                            },
                            onMarkAsTaken = null,
                            onMarkAsSkipped = null,
                            shapes = smartListSegmentedShapes(index = index, count = takenEvents.size),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )

                        if (index < takenEvents.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Skipped medications section
                if (skippedEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.tracking_section_skipped),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                        )
                    }

                    itemsIndexed(
                        items = skippedEvents,
                        key = { _, it -> "skipped_${it.event.historyRecord?.id ?: it.event.compositeKey}" },
                    ) { index, eventWithMed ->
                        SwipeableMedicationLogListItem(
                            eventWithMedication = eventWithMed,
                            onClick = {
                                selectedEventForDialog = eventWithMed
                                showEditLoggedDialog = true
                            },
                            onMarkAsTaken = null,
                            onMarkAsSkipped = null,
                            shapes = smartListSegmentedShapes(index = index, count = skippedEvents.size),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )

                        if (index < skippedEvents.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Missed dose banner for cyclic medications
                if (state.showMissedDoseBanner) {
                    item {
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Text(
                                text = stringResource(Res.string.tracking_missed_dose_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                // Scheduled medications section — future-today only when viewing today,
                // otherwise the full day's pending list. Past-today missed/overdue items
                // have moved to the dedicated Missed/Overdue sections above.
                if (futureTodayPending.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.tracking_section_scheduled),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .semantics { heading() },
                        )
                    }

                    itemsIndexed(
                        items = futureTodayPending,
                        key = { _, it -> "scheduled_${it.event.historyRecord?.id ?: it.event.compositeKey}" },
                    ) { index, eventWithMed ->
                        val isInFuture = eventWithMed.event.scheduledTime > twoDaysFromNowMs

                        SwipeableMedicationLogListItem(
                            eventWithMedication = eventWithMed,
                            onClick = {
                                selectedEventForDialog = eventWithMed
                                showLogMedicationDialog = true
                            },
                            onMarkAsTaken = if (!isInFuture) {
                                { onMarkAsTaken(eventWithMed) }
                            } else {
                                null
                            },
                            onMarkAsSkipped = if (!isInFuture) {
                                { onMarkAsSkipped(eventWithMed) }
                            } else {
                                null
                            },
                            shapes = smartListSegmentedShapes(index = index, count = futureTodayPending.size),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )

                        if (index < futureTodayPending.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Loading or empty state — every renderable section must be empty,
                // including the new Missed/Overdue sections.
                if (futureTodayPending.isEmpty() &&
                    takenEvents.isEmpty() &&
                    skippedEvents.isEmpty() &&
                    missedBeyondGrace.isEmpty() &&
                    overdueWithinGrace.isEmpty()
                ) {
                    item {
                        if (!hasLoaded) {
                            val loadingDescription = stringResource(Res.string.accessibility_loading)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp)
                                    .semantics {
                                        contentDescription = loadingDescription
                                        liveRegion = LiveRegionMode.Polite
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator()
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    painter = painterResource(Res.drawable.illustration_empty_no_schedule),
                                    contentDescription = null,
                                    modifier = Modifier.size(200.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outlineVariant),
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(Res.string.tracking_empty_state),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrim to capture outside clicks when FAB menu is expanded
        if (fabMenuExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) {
                        fabMenuExpanded = false
                    },
                color = Color.Transparent,
            ) {}
        }

        // FAB Menu
        val fabMenuItems = listOf(
            FabMenuItem(Icons.Default.Add, stringResource(Res.string.tracking_log_scheduled)),
            FabMenuItem(Icons.Default.Add, stringResource(Res.string.tracking_log_as_needed)),
        )

        ExpandableFabMenu(
            modifier = Modifier.align(Alignment.BottomEnd),
            expanded = fabMenuExpanded,
            onExpandedChange = { fabMenuExpanded = it },
            items = fabMenuItems,
            onItemClick = { i ->
                fabMenuExpanded = false
                when (i) {
                    0 -> {
                        logMedicationMode = LogMedicationMode.SCHEDULED
                        showLogMedicationSheet = true
                    }

                    1 -> {
                        logMedicationMode = LogMedicationMode.AS_NEEDED
                        showLogMedicationSheet = true
                    }
                }
            },
            visible = fabVisible || fabMenuExpanded,
            tooltipText = stringResource(Res.string.tracking_fab_tooltip),
            expandedLabel = stringResource(Res.string.accessibility_expanded),
            collapsedLabel = stringResource(Res.string.accessibility_collapsed),
            toggleMenuLabel = stringResource(Res.string.accessibility_toggle_menu),
            closeMenuLabel = stringResource(Res.string.accessibility_close_menu),
        )
    }

    // Log Medication Bottom Sheet (FAB multi-add or notification-triggered)
    if (showLogMedicationSheet) {
        LogMedicationBottomSheet(
            mode = logMedicationMode,
            date = selectedDate,
            scheduledEvents = if (logMedicationMode == LogMedicationMode.SCHEDULED) {
                // Use filtered events from notification if available, otherwise show all
                filteredNotificationEvents ?: scheduledEvents
            } else {
                emptyList()
            },
            allMedications = if (logMedicationMode == LogMedicationMode.AS_NEEDED) {
                allMedications
            } else {
                emptyList()
            },
            onDismiss = {
                showLogMedicationSheet = false
                filteredNotificationEvents = null // Clear filter on dismiss
            },
            onSave = { mode, scheduledLogs, asNeededLogs ->
                when (mode) {
                    LogMedicationMode.SCHEDULED -> onSaveScheduledLogs(scheduledLogs)
                    LogMedicationMode.AS_NEEDED -> onSaveAsNeededLogs(asNeededLogs)
                }
                filteredNotificationEvents = null // Clear filter after save
            },
        )
    }

    // Log Medication Dialog (single scheduled item)
    if (showLogMedicationDialog && selectedEventForDialog != null) {
        LogMedicationDialog(
            eventWithMedication = selectedEventForDialog!!,
            date = selectedDate,
            onDismiss = {
                showLogMedicationDialog = false
                selectedEventForDialog = null
            },
            onSkip = { event, time, dose ->
                onLogScheduledItem(event, time, dose, true)
                showLogMedicationDialog = false
                selectedEventForDialog = null
            },
            onSave = { event, time, dose ->
                onLogScheduledItem(event, time, dose, false)
                showLogMedicationDialog = false
                selectedEventForDialog = null
            },
        )
    }

    // Edit Logged Medication Dialog
    if (showEditLoggedDialog && selectedEventForDialog != null) {
        val isScheduled = selectedEventForDialog!!.event.scheduleId != 0
        val schedule = selectedEventForDialog!!.event.scheduleId.let { scheduleId ->
            allSchedules.firstOrNull { it.id == scheduleId }
        }
        EditLoggedMedicationBottomSheet(
            eventWithMedication = selectedEventForDialog!!,
            date = selectedDate,
            isScheduled = isScheduled,
            schedule = schedule,
            onDismiss = {
                showEditLoggedDialog = false
                selectedEventForDialog = null
            },
            onDelete = { event ->
                onDeleteLoggedItem(event)
                showEditLoggedDialog = false
                selectedEventForDialog = null
            },
            onSave = { event, time, dose, status ->
                onUpdateLoggedItem(event, time, dose, status)
                showEditLoggedDialog = false
                selectedEventForDialog = null
            },
        )
    }
}

@Composable
private fun SwipeableMedicationLogListItem(
    eventWithMedication: ProjectedEventWithMedication,
    onClick: () -> Unit,
    onMarkAsTaken: (() -> Unit)? = null,
    onMarkAsSkipped: (() -> Unit)? = null,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    timeOverride: String? = null,
) {
    SwipeableListItem(
        key = "${eventWithMedication.event.historyRecord?.id ?: eventWithMedication.event.compositeKey}",
        onSwipeLeft = onMarkAsTaken,
        onSwipeRight = onMarkAsSkipped,
        leftSwipeIcon = if (onMarkAsTaken != null) rememberVectorPainter(Icons.Default.Done) else null,
        rightSwipeIcon = if (onMarkAsSkipped != null) painterResource(Res.drawable.outline_step_over_24px) else null,
        leftSwipeBackgroundColor = Color(0xFF326A35),
        rightSwipeBackgroundColor = Color(0xFF32456A),
        leftSwipeIconTint = Color.White,
        rightSwipeIconTint = Color.White,
        modifier = modifier,
    ) {
        MedicationLogListItem(
            eventWithMedication = eventWithMedication,
            onClick = onClick,
            shapes = shapes,
            timeOverride = timeOverride,
        )
    }
}

@Composable
private fun MedicationLogListItem(
    eventWithMedication: ProjectedEventWithMedication,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    isLogged: Boolean = false,
    timeOverride: String? = null,
) {
    val context = platformContext()
    val medication = eventWithMedication.medication
    val event = eventWithMedication.event

    // Get display name (use displayName if set, otherwise name)
    val displayName = medication.displayName?.takeIf { it.isNotBlank() } ?: medication.name

    // Get type and strength
    val typeAndStrength = formatMedicationTypeAndStrength(medication)

    // Get time and dose
    val time = timeOverride ?: formatLogEventTime(event)
    val dose = formatLogEventDose(event, medication.type)

    // Parse shape properties
    val foregroundShape = MedicationForegroundShape.fromNameOrDefault(medication.foregroundShape)
    val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)

    val color1 = medication.shapeColor?.let {
        MedicationColor.fromName(it)
    }

    val isPlacebo = event.isPlacebo
    val isCyclicWithPlacebos = medication.cycleType == me.juliana.hellomeds.data.model.enums.CycleType.CYCLIC && medication.cycleHasPlacebos

    // Badge text: "Placebo" for placebo events, "Active" for active events on cyclic meds with placebos
    val badgeText = when {
        isPlacebo -> stringResource(Res.string.tracking_placebo_badge)
        isCyclicWithPlacebos -> stringResource(Res.string.tracking_active_badge)
        else -> null
    }

    LazySmartListItem(
        headlineContent = {
            if (badgeText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(displayName, modifier = Modifier.weight(1f, fill = false))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isPlacebo) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.semantics {
                            contentDescription = if (isPlacebo) "$badgeText pill" else "$badgeText dose"
                        },
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPlacebo) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(displayName)
            }
        },
        supportingContent = { Text(typeAndStrength) },
        leadingContent = {
            MedicationShapeIcon(
                foregroundShape = foregroundShape,
                backgroundShape = backgroundShape,
                color1 = color1,
                size = 48.dp,
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = Color.Transparent,
                ) {
                    Text(
                        text = dose,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
        shapes = shapes,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun TrackingScreenPreview() {
    HelloMedsTheme {
        val sampleMedication = Medication(
            id = 1,
            name = "Sample Med",
            type = MedicationType.CAPSULE,
            shape = "Round",
            importanceLabelId = 1,
            strengthValue = 100.0,
            strengthUnit = MedicationStrengthUnit.MG,
            foregroundShape = MedicationForegroundShape.CAPSULE_PILL.name,
            backgroundShape = MedicationBackgroundShape.CIRCLE.name,
            shapeColor = MedicationColor.Blue::class.simpleName,
        )

        val scheduledEvent = ProjectedEventWithMedication(
            event = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = Clock.System.now().toEpochMilliseconds(),
                dose = 1.0,
            ),
            medication = sampleMedication,
        )

        val takenEvent = ProjectedEventWithMedication(
            event = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = Clock.System.now().toEpochMilliseconds() - 3600000,
                dose = 1.0,
            ),
            medication = sampleMedication,
        )

        val skippedEvent = ProjectedEventWithMedication(
            event = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = Clock.System.now().toEpochMilliseconds() - 7200000,
                dose = 1.0,
            ),
            medication = sampleMedication,
        )

        TrackingScreen(
            state = TrackingScreenState(
                scheduledEvents = listOf(scheduledEvent),
                takenEvents = listOf(takenEvent),
                skippedEvents = listOf(skippedEvent),
                allMedications = listOf(sampleMedication),
                allSchedules = emptyList(),
                selectedDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            ),
            actions = TrackingScreenActions(
                onDateSelected = {},
                onNavigateToSettings = {},
                onNavigateToSupport = {},
                onSaveScheduledLogs = {},
                onSaveAsNeededLogs = {},
                onMarkAsTaken = {},
                onMarkAsSkipped = {},
                onLogScheduledItem = { _, _, _, _ -> },
                onUpdateLoggedItem = { _, _, _, _ -> },
                onDeleteLoggedItem = {},
            ),
        )
    }
}
