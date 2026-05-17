// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.ProjectedEventWithMedication
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.domain.schedule.CompletionStatusCalculator
import me.juliana.hellomeds.domain.validation.TwoDayWindowValidator
import me.juliana.hellomeds.ui.components.medication.LogStatus
import me.juliana.hellomeds.ui.features.tracking.AsNeededMedicationLog
import me.juliana.hellomeds.ui.features.tracking.ScheduledMedicationLog
import me.juliana.hellomeds.ui.util.OperationGuard
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModel(
    private val historyRepository: MedicationHistoryRepository,
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
) : ViewModel() {

    // Loading state - true once the first DB emission arrives
    private val _hasLoaded = MutableStateFlow(false)
    val hasLoaded: StateFlow<Boolean> = _hasLoaded.asStateFlow()

    // One-shot error events for UI (Snackbar)
    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    // Guards against double-tap: tracks in-flight dose logging operations by composite key
    internal val doseOperationGuard = OperationGuard()

    // Current selected date (shifted by 3 hours - day doesn't change until 3:00 AM)
    private val _selectedDate = MutableStateFlow(getAdjustedCurrentDate())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private fun getAdjustedCurrentDate(): LocalDate {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        // Subtract 3 hours: day doesn't change until 3:00 AM
        val adjustedMillis = now.toEpochMilliseconds() - 3 * 60 * 60 * 1000L
        val adjustedInstant = kotlin.time.Instant.fromEpochMilliseconds(adjustedMillis)
        return adjustedInstant.toLocalDateTime(tz).date
    }

    // Events for the selected date split into pending/taken/skipped/overdue
    val scheduledEvents: StateFlow<List<ProjectedEventWithMedication>>

    val takenEvents: StateFlow<List<ProjectedEventWithMedication>>

    val skippedEvents: StateFlow<List<ProjectedEventWithMedication>>

    /** Overdue events from the previous 24h (carry-forward, only when viewing today). */
    val overdueEvents: StateFlow<List<ProjectedEventWithMedication>>

    // All medications for as-needed logging
    val allMedications: StateFlow<List<Medication>>

    // All schedules for grace period calculation
    val allSchedules: StateFlow<List<me.juliana.hellomeds.data.database.entities.Schedule>>

    /** True when a cyclic medication has a recent missed active dose. */
    val showMissedDoseBanner: StateFlow<Boolean>

    // Completion status map for date picker (reactive to all changes)
    val completionStatusMap: StateFlow<Map<LocalDate, me.juliana.hellomeds.data.model.DayCompletionStatus>>

    init {
        // Combine selected date with events to get reactive updates
        val eventsFlow = _selectedDate.flatMapLatest { date ->
            val isToday = date == getAdjustedCurrentDate()
            historyRepository.getEventsForDateWithMedication(date, isToday)
        }

        scheduledEvents = eventsFlow.map { it.pending }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        takenEvents = eventsFlow.map { it.taken }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        skippedEvents = eventsFlow.map { it.skipped }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        overdueEvents = eventsFlow.map { it.overdue }.distinctUntilChanged().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        allMedications = medicationDao.getActive().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        allSchedules = scheduleDao.getActive().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

        // Missed dose banner for cyclic medications: only show for today/yesterday
        showMissedDoseBanner = combine(
            skippedEvents,
            medicationDao.getActive(),
            _selectedDate,
        ) { skipped, medications, selectedDate ->
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val yesterday = today.plus(-1, DateTimeUnit.DAY)
            if (selectedDate != today && selectedDate != yesterday) return@combine false

            val cyclicMedIds = medications
                .filter { it.cycleType == CycleType.CYCLIC }
                .map { it.id }
                .toSet()
            if (cyclicMedIds.isEmpty()) return@combine false

            skipped.any { ewm ->
                ewm.event.medicationId in cyclicMedIds && !ewm.event.isPlacebo
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

        // Create reactive completion status map for all dates in picker range
        completionStatusMap = combine(
            historyRepository.getAll(),
            scheduleDao.getActive(),
        ) { _, _ ->
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val startDate = today.plus(-30, DateTimeUnit.DAY)
            val endDate = today.plus(30, DateTimeUnit.DAY)
            val startMillis =
                startDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val endMillis =
                endDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()

            val projected = projector.projectEvents(startMillis, endMillis)

            CompletionStatusCalculator.calculate(projected, startDate, endDate)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap(),
        )

        // Mark as loaded once the first DB emission arrives
        viewModelScope.launch {
            eventsFlow.first()
            _hasLoaded.value = true
        }
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    private fun validateTwoDayWindow(event: ProjectedEvent, operation: String): Boolean {
        return TwoDayWindowValidator.validate(event, operation)
    }

    fun saveScheduledLogs(logs: List<ScheduledMedicationLog>) {
        viewModelScope.launch {
            val logsToProcess = logs.filter { it.included }

            AppLogger.d(
                "TrackingViewModel",
                "Saving ${logsToProcess.size} logs out of ${logs.size} total",
            )

            logsToProcess.forEach { log ->
                val event = log.eventWithMedication.event

                if (!validateTwoDayWindow(event, "saveScheduledLogs")) {
                    return@forEach
                }

                AppLogger.d(
                    "TrackingViewModel",
                    "Processing log: scheduleId=${event.scheduleId}, status=${log.status}, scheduledTime=${event.scheduledTime}",
                )

                val takenTime = LocalDateTime(_selectedDate.value, log.time)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()

                try {
                    when (log.status) {
                        LogStatus.TAKEN -> {
                            historyRepository.markAsTaken(
                                event = event,
                                actualDose = log.dose,
                                takenTime = takenTime,
                            )
                            AppLogger.d("TrackingViewModel", "Marked as TAKEN: schedule=${event.scheduleId}")
                        }

                        LogStatus.SKIPPED -> {
                            historyRepository.markAsSkipped(event)
                            AppLogger.d("TrackingViewModel", "Marked as SKIPPED: schedule=${event.scheduleId}")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("TrackingViewModel", "Error saving log: ${e.message}", e)
                    _errorEvents.tryEmit("Failed to save medication log")
                }
            }

            AppLogger.d("TrackingViewModel", "Finished saving logs")
        }
    }

    fun saveAsNeededLogs(logs: List<AsNeededMedicationLog>) {
        viewModelScope.launch {
            AppLogger.d("TrackingViewModel", "Saving ${logs.size} as-needed logs")

            logs.forEach { log ->
                val timeMillis = LocalDateTime(_selectedDate.value, log.time)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()

                AppLogger.d(
                    "TrackingViewModel",
                    "Creating manual dose for medication ${log.medication.id}, dose=${log.dose}, time=${log.time}",
                )

                try {
                    val id = historyRepository.createManualDose(
                        medicationId = log.medication.id,
                        dose = log.dose,
                        takenTime = timeMillis,
                        notes = null,
                    )
                    AppLogger.d("TrackingViewModel", "Created manual dose with ID: $id")
                } catch (e: Exception) {
                    AppLogger.e("TrackingViewModel", "Error creating manual dose: ${e.message}", e)
                }
            }

            AppLogger.d("TrackingViewModel", "Finished saving as-needed logs")
        }
    }

    fun markAsTakenViaSwipe(eventWithMedication: ProjectedEventWithMedication) {
        val event = eventWithMedication.event
        val operationKey = "${event.medicationId}_${event.scheduleId}_${event.scheduledTime}"
        if (!doseOperationGuard.tryAcquire(operationKey)) return // Already in progress — prevent double-tap
        viewModelScope.launch {
            val timestamp = Clock.System.now().toEpochMilliseconds()

            AppLogger.d(
                "TrackingViewModel",
                "markAsTakenViaSwipe: START - scheduleId=${event.scheduleId}, " +
                    "medicationId=${event.medicationId}, " +
                    "scheduledTime=${event.scheduledTime}, timestamp=$timestamp",
            )

            if (!validateTwoDayWindow(event, "markAsTakenViaSwipe")) {
                AppLogger.w("TrackingViewModel", "markAsTakenViaSwipe: ABORTED - outside 2-day window")
                doseOperationGuard.release(operationKey)
                return@launch
            }

            try {
                historyRepository.markAsTaken(
                    event = event,
                    actualDose = event.dose,
                    takenTime = timestamp,
                )
                AppLogger.i(
                    "TrackingViewModel",
                    "markAsTakenViaSwipe: SUCCESS - schedule=${event.scheduleId}, timestamp=$timestamp",
                )
            } catch (e: Exception) {
                AppLogger.e(
                    "TrackingViewModel",
                    "markAsTakenViaSwipe: ERROR - schedule=${event.scheduleId}, error=${e.message}",
                    e,
                )
                _errorEvents.tryEmit("Failed to mark medication as taken")
            } finally {
                doseOperationGuard.release(operationKey)
            }
        }
    }

    fun markAsSkippedViaSwipe(eventWithMedication: ProjectedEventWithMedication) {
        val event = eventWithMedication.event
        val operationKey = "${event.medicationId}_${event.scheduleId}_${event.scheduledTime}"
        if (!doseOperationGuard.tryAcquire(operationKey)) return // Already in progress — prevent double-tap
        viewModelScope.launch {
            val timestamp = Clock.System.now().toEpochMilliseconds()

            AppLogger.d(
                "TrackingViewModel",
                "markAsSkippedViaSwipe: START - scheduleId=${event.scheduleId}, " +
                    "medicationId=${event.medicationId}, " +
                    "scheduledTime=${event.scheduledTime}, timestamp=$timestamp",
            )

            if (!validateTwoDayWindow(event, "markAsSkippedViaSwipe")) {
                AppLogger.w("TrackingViewModel", "markAsSkippedViaSwipe: ABORTED - outside 2-day window")
                doseOperationGuard.release(operationKey)
                return@launch
            }

            try {
                historyRepository.markAsSkipped(event)
                AppLogger.i(
                    "TrackingViewModel",
                    "markAsSkippedViaSwipe: SUCCESS - schedule=${event.scheduleId}, timestamp=$timestamp",
                )
            } catch (e: Exception) {
                AppLogger.e(
                    "TrackingViewModel",
                    "markAsSkippedViaSwipe: ERROR - schedule=${event.scheduleId}, error=${e.message}",
                    e,
                )
                _errorEvents.tryEmit("Failed to mark medication as skipped")
            } finally {
                doseOperationGuard.release(operationKey)
            }
        }
    }

    fun logScheduledItem(event: ProjectedEvent, time: LocalTime, dose: Double, isSkipped: Boolean) {
        viewModelScope.launch {
            if (!validateTwoDayWindow(event, "logScheduledItem")) {
                return@launch
            }

            AppLogger.d(
                "TrackingViewModel",
                "Logging scheduled item: schedule=${event.scheduleId}, isSkipped=$isSkipped, dose=$dose, time=$time",
            )
            try {
                val timeMillis = LocalDateTime(_selectedDate.value, time)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()

                if (isSkipped) {
                    historyRepository.markAsSkipped(event)
                } else {
                    historyRepository.markAsTaken(
                        event = event,
                        actualDose = dose,
                        takenTime = timeMillis,
                    )
                }
                AppLogger.d("TrackingViewModel", "Logged scheduled item successfully")
            } catch (e: Exception) {
                AppLogger.e("TrackingViewModel", "Error logging scheduled item: ${e.message}", e)
            }
        }
    }

    fun updateLoggedItem(event: ProjectedEvent, time: LocalTime, dose: Double, status: LogStatus?) {
        viewModelScope.launch {
            if (!validateTwoDayWindow(event, "updateLoggedItem")) {
                return@launch
            }

            AppLogger.d(
                "TrackingViewModel",
                "Updating logged item: historyId=${event.historyRecord?.id}, status=$status, dose=$dose, time=$time",
            )
            try {
                val timeMillis = LocalDateTime(_selectedDate.value, time)
                    .toInstant(TimeZone.currentSystemDefault())
                    .toEpochMilliseconds()

                when (status) {
                    LogStatus.SKIPPED -> {
                        val historyId = event.historyRecord?.id ?: return@launch
                        historyRepository.updateSkipped(historyId)
                    }

                    // TAKEN, or null for as-needed entries
                    else -> {
                        val historyId = event.historyRecord?.id ?: return@launch
                        historyRepository.updateTaken(
                            historyId = historyId,
                            actualDose = dose,
                            takenTime = timeMillis,
                        )
                    }
                }
                AppLogger.d("TrackingViewModel", "Updated logged item successfully")
            } catch (e: Exception) {
                AppLogger.e("TrackingViewModel", "Error updating logged item: ${e.message}", e)
                _errorEvents.tryEmit("Failed to update medication log")
            }
        }
    }

    fun deleteLoggedItem(event: ProjectedEvent) {
        viewModelScope.launch {
            AppLogger.d(
                "TrackingViewModel",
                "deleteLoggedItem: START - historyId=${event.historyRecord?.id}, " +
                    "scheduleId=${event.scheduleId}, scheduledTime=${event.scheduledTime}",
            )

            try {
                historyRepository.deleteHistoryRecord(event)
                AppLogger.i(
                    "TrackingViewModel",
                    "deleteLoggedItem: SUCCESS - scheduleId=${event.scheduleId} scheduledTime=${event.scheduledTime}",
                )
            } catch (e: Exception) {
                AppLogger.e(
                    "TrackingViewModel",
                    "deleteLoggedItem: ERROR - scheduleId=${event.scheduleId}, error=${e.message}",
                    e,
                )
                _errorEvents.tryEmit("Failed to delete medication log")
            }
        }
    }

    suspend fun getCompletionStatusForDate(date: LocalDate): me.juliana.hellomeds.data.model.DayCompletionStatus {
        return historyRepository.getEventsForDateWithMedication(date)
            .map { (pending, taken, skipped) ->
                me.juliana.hellomeds.data.model.DayCompletionStatus(
                    date = date,
                    totalScheduled = pending.size + taken.size + skipped.size,
                    completed = taken.size,
                )
            }.stateIn(viewModelScope).value
    }
}
