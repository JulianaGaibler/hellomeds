// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector

/**
 * Detects medication doses that should have fired but never did.
 *
 * **Why this exists:** the OS will silently drop alarms — Doze mode, force-stop,
 * permission revocation, low battery, OEM background restrictions, app standby
 * buckets, full-screen-intent denial, channel disabling. Every layer below this
 * detector can fail silently. This detector is the "last line of defense" — it
 * compares the projection ("what should have fired") against the history record
 * ("what did the user act on") and surfaces the gap.
 *
 * **Lookback semantics:** `[max(lastAcknowledgedTimestamp, now - 24h), now]`.
 * - The 24h cap prevents overwhelming users who haven't opened the app in weeks.
 * - The acknowledgment timestamp prevents re-showing the same set on every open.
 * - First run: `lastAcknowledgedTimestamp` is null → full 24h lookback.
 *
 * **Severity classification:**
 * - [DoseSeverity.OVERDUE]: `scheduledTime` is in the past but within
 *   [GRACE_PERIOD_MS] of `now`. Still reasonable to take retroactively.
 * - [DoseSeverity.MISSED]: `scheduledTime` is older than the grace period.
 *   Surface for awareness — but the dialog should discourage retroactive logging.
 *
 * **Acknowledgment:** the UI calls [acknowledge] after presenting the dialog.
 * That advances [ReliabilityPreferences.lastReconciliationTimestamp], so future
 * emissions exclude these doses. Until acknowledged the same set keeps emitting —
 * if the user backgrounds the app while the dialog is showing, they see it again
 * on the next foreground.
 *
 * **Reactivity:** the flow recomputes when ANY of these change:
 * - History rows (the user took/skipped a dose anywhere)
 * - Active schedules (medication added or edited)
 * - Active medications (a medication is archived/restored)
 * - The acknowledgment timestamp
 */
class MissedDoseDetector(
    private val projector: ScheduleProjector,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val historyDao: MedicationHistoryDao,
    private val reliabilityPrefs: ReliabilityPreferences,
    private val timeProvider: TimeProvider,
) {
    companion object {
        const val GRACE_PERIOD_MS: Long = 60 * 60 * 1000L // 1 hour
        const val MAX_LOOKBACK_MS: Long = 24 * 60 * 60 * 1000L // 24 hours
    }

    /**
     * Reactive list of past-due doses with no history record. Sorted ascending
     * by scheduledTime. Severity is computed against the clock at compute time.
     */
    fun missedDoses(): Flow<List<MissedDose>> = combine(
        historyDao.getAll(),
        scheduleDao.getActive(),
        medicationDao.getActive(),
        reliabilityPrefs.lastReconciliationTimestamp,
    ) { history, schedules, medications, lastAcked ->
        val now = timeProvider.nowMillis()
        val maxLookback = now - MAX_LOOKBACK_MS
        val windowStart = if (lastAcked == null) maxLookback else maxOf(lastAcked, maxLookback)
        val windowEnd = now
        if (windowStart >= windowEnd) return@combine emptyList()

        val medMap = medications.associateBy { it.id }
        val events = projector.projectEventsWithHistory(
            schedules,
            windowStart,
            windowEnd,
            history,
            medMap,
        )

        events
            .asSequence()
            .filter { it.isPending && it.scheduledTime <= now }
            .mapNotNull { event ->
                val med = medMap[event.medicationId] ?: return@mapNotNull null
                val severity = if (event.scheduledTime + GRACE_PERIOD_MS > now) {
                    DoseSeverity.OVERDUE
                } else {
                    DoseSeverity.MISSED
                }
                MissedDose(
                    medicationId = event.medicationId,
                    medicationName = med.name,
                    scheduleId = event.scheduleId,
                    scheduledTime = event.scheduledTime,
                    dose = event.dose,
                    severity = severity,
                )
            }
            .sortedBy { it.scheduledTime }
            .toList()
    }

    /**
     * Mark the current detection set as "shown to the user". Future emissions
     * exclude doses whose scheduledTime is older than the moment of this call.
     *
     * Call this AFTER the dialog is presented. Calling it before display means
     * if the user backgrounds the app mid-dialog they will not see the missed
     * doses again — defeating the safety net.
     */
    suspend fun acknowledge() {
        reliabilityPrefs.setLastReconciliationTimestamp(timeProvider.nowMillis())
    }
}
