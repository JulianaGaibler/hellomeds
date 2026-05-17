// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3.entries

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.features.tracking.TrackingScreen
import me.juliana.hellomeds.ui.features.tracking.TrackingScreenActions
import me.juliana.hellomeds.ui.features.tracking.TrackingScreenState
import me.juliana.hellomeds.ui.viewmodel.TrackingViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Entry point for the Tracking screen.
 * Shows scheduled medication events for the selected date.
 */
@Composable
fun TrackingScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    notificationEventIds: IntArray? = null,
    notificationGroupingMode: String? = null,
    onNotificationHandled: () -> Unit = {},
) {
    val viewModel: TrackingViewModel = koinViewModel()
    val scheduledEvents by viewModel.scheduledEvents.collectAsStateWithLifecycle()
    val takenEvents by viewModel.takenEvents.collectAsStateWithLifecycle()
    val skippedEvents by viewModel.skippedEvents.collectAsStateWithLifecycle()
    val overdueEvents by viewModel.overdueEvents.collectAsStateWithLifecycle()
    val allMedications by viewModel.allMedications.collectAsStateWithLifecycle()
    val allSchedules by viewModel.allSchedules.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()
    val showMissedDoseBanner by viewModel.showMissedDoseBanner.collectAsStateWithLifecycle()

    TrackingScreen(
        state = TrackingScreenState(
            scheduledEvents = scheduledEvents,
            takenEvents = takenEvents,
            skippedEvents = skippedEvents,
            overdueEvents = overdueEvents,
            allMedications = allMedications,
            allSchedules = allSchedules,
            selectedDate = selectedDate,
            hasLoaded = hasLoaded,
            notificationEventIds = notificationEventIds,
            notificationGroupingMode = notificationGroupingMode,
            showMissedDoseBanner = showMissedDoseBanner,
        ),
        actions = TrackingScreenActions(
            onDateSelected = { date -> viewModel.setSelectedDate(date) },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToSupport = onNavigateToSupport,
            onSaveScheduledLogs = { logs -> viewModel.saveScheduledLogs(logs) },
            onSaveAsNeededLogs = { logs -> viewModel.saveAsNeededLogs(logs) },
            onMarkAsTaken = { event -> viewModel.markAsTakenViaSwipe(event) },
            onMarkAsSkipped = { event -> viewModel.markAsSkippedViaSwipe(event) },
            onLogScheduledItem = { logEvent, time, dose, isSkipped ->
                viewModel.logScheduledItem(logEvent, time, dose, isSkipped)
            },
            onUpdateLoggedItem = { logEvent, time, dose, status ->
                viewModel.updateLoggedItem(logEvent, time, dose, status)
            },
            onDeleteLoggedItem = { logEvent ->
                viewModel.deleteLoggedItem(logEvent)
            },
            onNotificationHandled = onNotificationHandled,
        ),
        trackingViewModel = viewModel,
    )
}
