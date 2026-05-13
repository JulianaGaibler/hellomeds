// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.content.Context
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import org.junit.Test

/**
 * Regression test for the [MissedDoseProcessor] race against in-app
 * `markAsTaken`. Pre-fix, `getPendingEventsSince` returned an unlocked
 * snapshot and the per-slot session-existence check was the only guard;
 * a concurrent in-app TAKEN write that committed between snapshot and
 * notification post produced a phantom "take medication" notification
 * for a med the user had just logged.
 *
 * The fix re-queries `historyDao.findByCompositeKey` immediately before
 * `handleNewEvents` so any committed history row (TAKEN/SKIPPED/AUTO_SKIPPED)
 * collapses the slot's `stillPending` list to empty.
 */
class MissedDoseProcessorTest {

    @Test
    fun `processMissedDoses suppresses phantom when concurrent markAsTaken has committed`() = runTest {
        mockkObject(AppLogger)
        try {
            every { AppLogger.d(any(), any()) } returns Unit
            every { AppLogger.i(any(), any()) } returns Unit
            every { AppLogger.w(any(), any()) } returns Unit

            val context = mockk<Context>(relaxed = true)
            val projector = mockk<ScheduleProjector>()
            val sessionManager = mockk<NotificationSessionManager>()
            val medicationDao = mockk<MedicationDao>(relaxed = true)
            val importanceLabelDao = mockk<ImportanceLabelDao>(relaxed = true)
            val notifBuilder = mockk<NotificationBuilder>(relaxed = true)
            val notifPrefs = mockk<NotificationPreferences> {
                every { lockScreenVisibility } returns flowOf(LockScreenVisibility.SHOW_WITH_NAMES)
                every { groupingMode } returns flowOf(NotificationGroupingMode.COMBINED)
            }
            val historyDao = mockk<MedicationHistoryDao>()

            val pendingEvent = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = 5_000L,
                dose = 1.0,
                historyRecord = null,
            )

            // Stale "pending" snapshot — projector.getPendingEventsSince() runs
            // outside a transaction and reflects the state before the in-app
            // TAKEN write committed.
            coEvery { projector.getPendingEventsSince(any(), any()) } returns listOf(pendingEvent)
            coEvery { sessionManager.getSessionForSchedule(5_000L, 1) } returns null

            // The race: concurrent in-app markAsTaken just committed a TAKEN
            // row keyed by (medId, scheduleId, scheduledTime). The pre-flight
            // composite-key lookup hits it.
            coEvery { historyDao.findByCompositeKey(1, 1, 5_000L) } returns 42

            val processor = MissedDoseProcessor(
                context,
                projector,
                sessionManager,
                medicationDao,
                importanceLabelDao,
                notifBuilder,
                notifPrefs,
                historyDao,
            )

            processor.processMissedDoses(now = 10_000L)

            // The pre-flight must collapse stillPending to empty so
            // handleNewEvents is never entered and notifBuilder is never
            // touched. A regression here means the phantom is back.
            verify { notifBuilder wasNot Called }
            // Reinforce: the composite-key lookup MUST have happened —
            // otherwise the test would also pass under the pre-fix code
            // path (which never queried historyDao at all).
            coVerify(exactly = 1) { historyDao.findByCompositeKey(1, 1, 5_000L) }
        } finally {
            unmockkObject(AppLogger)
        }
    }
}
