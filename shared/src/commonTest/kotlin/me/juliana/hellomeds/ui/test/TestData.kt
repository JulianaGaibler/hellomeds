// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.test

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.ui.features.tracking.TrackingScreenActions
import me.juliana.hellomeds.ui.features.tracking.TrackingScreenState

/** Creates a test medication with sensible defaults. */
fun testMedication(
    id: Int = 1,
    name: String = "Test Med",
    type: MedicationType = MedicationType.TABLET,
    timeZoneMode: TimeZoneMode = TimeZoneMode.LOCAL,
    cycleType: CycleType = CycleType.NONE,
    importanceLabelId: Int = 1,
): Medication = Medication(
    id = id,
    name = name,
    type = type,
    shape = "",
    importanceLabelId = importanceLabelId,
    foregroundShape = "CIRCLE",
    backgroundShape = "CIRCLE",
    timeZoneMode = timeZoneMode,
    cycleType = cycleType,
)

/** Creates a test ProjectedEventWithMedication for UI display. */
fun testEventWithMedication(
    medicationName: String = "Test Med",
    medicationId: Int = 1,
    scheduleId: Int = 1,
    scheduledTime: Long = kotlinx.datetime.LocalDateTime(
        LocalDate(2025, 6, 15),
        LocalTime(9, 0),
    ).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
    dose: Double = 1.0,
    status: String? = null, // null = pending, "TAKEN", "SKIPPED"
    isPlacebo: Boolean = false,
): ProjectedEventWithMedication {
    val historyRecord = when (status) {
        "TAKEN" -> MedicationHistory(
            medicationId = medicationId,
            scheduleId = scheduleId,
            scheduledTime = scheduledTime,
            takenTime = scheduledTime + 60000,
            scheduledDose = dose,
            actualDose = dose,
            status = MedicationHistory.STATUS_TAKEN,
        )
        "SKIPPED" -> MedicationHistory(
            medicationId = medicationId,
            scheduleId = scheduleId,
            scheduledTime = scheduledTime,
            scheduledDose = dose,
            status = MedicationHistory.STATUS_SKIPPED,
        )
        else -> null
    }

    return ProjectedEventWithMedication(
        event = ProjectedEvent(
            scheduleId = scheduleId,
            medicationId = medicationId,
            scheduledTime = scheduledTime,
            dose = dose,
            historyRecord = historyRecord,
            isPlacebo = isPlacebo,
        ),
        medication = testMedication(id = medicationId, name = medicationName),
    )
}

/** Creates a TrackingScreenState with sensible defaults. */
fun testTrackingScreenState(
    scheduledEvents: List<ProjectedEventWithMedication> = emptyList(),
    takenEvents: List<ProjectedEventWithMedication> = emptyList(),
    skippedEvents: List<ProjectedEventWithMedication> = emptyList(),
    overdueEvents: List<ProjectedEventWithMedication> = emptyList(),
    selectedDate: LocalDate = LocalDate(2025, 6, 15),
    hasLoaded: Boolean = true,
    showMissedDoseBanner: Boolean = false,
): TrackingScreenState = TrackingScreenState(
    scheduledEvents = scheduledEvents,
    takenEvents = takenEvents,
    skippedEvents = skippedEvents,
    overdueEvents = overdueEvents,
    allMedications = emptyList(),
    allSchedules = emptyList(),
    selectedDate = selectedDate,
    hasLoaded = hasLoaded,
    showMissedDoseBanner = showMissedDoseBanner,
)

/** Creates TrackingScreenActions with no-op lambdas. Override specific ones for assertions. */
fun testTrackingScreenActions(
    onDateSelected: (LocalDate) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onMarkAsTaken: (ProjectedEventWithMedication) -> Unit = {},
    onMarkAsSkipped: (ProjectedEventWithMedication) -> Unit = {},
): TrackingScreenActions = TrackingScreenActions(
    onDateSelected = onDateSelected,
    onNavigateToSettings = onNavigateToSettings,
    onNavigateToSupport = {},
    onSaveScheduledLogs = {},
    onSaveAsNeededLogs = {},
    onMarkAsTaken = onMarkAsTaken,
    onMarkAsSkipped = onMarkAsSkipped,
    onLogScheduledItem = { _, _, _, _ -> },
    onUpdateLoggedItem = { _, _, _, _ -> },
    onDeleteLoggedItem = {},
)
