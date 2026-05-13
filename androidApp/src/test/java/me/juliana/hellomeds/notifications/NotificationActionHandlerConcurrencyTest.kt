// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression test for the torn-mutex window in [NotificationActionHandler].
 *
 * Pre-fix, two flaws compounded: (a) `ConcurrentHashMap.getOrPut` is not atomic
 * (default get → put) so concurrent arrivals could create distinct Mutex
 * instances both stored under the same workKey, and (b) the finally block ran
 * `unlock()` before `remove(workKey)` so a fresh arrival in that window could
 * tryLock the just-unlocked mutex while the cleanup was still pending.
 *
 * The fix uses `computeIfAbsent` for atomic acquisition and `remove(key, mutex)`
 * before `unlock` for safe release. This test parks the lock holder inside
 * `projector.projectEvents`, races three more coroutines through the gate, and
 * asserts the inner workload runs exactly once.
 */
class NotificationActionHandlerConcurrencyTest {

    @Test
    fun `concurrent processAction for same key runs the inner workload exactly once`() = runBlocking {
        mockkObject(AppLogger)
        mockkStatic(Log::class)
        try {
            every { AppLogger.d(any(), any()) } returns Unit
            every { AppLogger.i(any(), any()) } returns Unit
            every { AppLogger.e(any(), any(), any()) } returns Unit
            every { Log.d(any(), any()) } returns 0
            every { Log.i(any(), any()) } returns 0

            val projector = mockk<ScheduleProjector>()
            val historyRepo = mockk<MedicationHistoryRepository>()
            val reconciler = mockk<AlarmReconciler>(relaxed = true)
            val sessionManager = mockk<NotificationSessionManager>()
            val notifPrefs = mockk<NotificationPreferences> {
                every { lockScreenVisibility } returns flowOf(LockScreenVisibility.SHOW_WITH_NAMES)
            }
            val medicationDao = mockk<MedicationDao>(relaxed = true)
            val notifBuilder = mockk<NotificationBuilder>(relaxed = true)
            val stockRepo = mockk<StockTrackingRepository>(relaxed = true)
            val timeProvider = mockk<TimeProvider> { every { nowMillis() } returns 1_000L }
            val notifManager = mockk<NotificationManager>(relaxed = true)
            val context = mockk<Context>(relaxed = true) {
                every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notifManager
            }

            val event = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = 5_000L,
                dose = 1.0,
                historyRecord = null,
            )

            // Park the FIRST projectEvents() call so the lock holder is still
            // inside the try-block when the other arrivals race through
            // computeIfAbsent + tryLock(). A torn-mutex regression would let
            // one of them ALSO acquire a (different) mutex and run the workload.
            val lockHeld = CompletableDeferred<Unit>()
            val releaseLock = CompletableDeferred<Unit>()
            val markAsTakenCount = AtomicInteger(0)
            val projectEventsCount = AtomicInteger(0)

            coEvery { projector.projectEvents(any(), any()) } coAnswers {
                if (projectEventsCount.incrementAndGet() == 1) {
                    lockHeld.complete(Unit)
                    releaseLock.await()
                }
                listOf(event)
            }
            coEvery { projector.getPendingEventsAtTime(any()) } returns emptyList()
            coEvery { sessionManager.getSessionForSchedule(any(), any()) } returns null
            coEvery { historyRepo.markAsTaken(any(), any(), any()) } coAnswers {
                markAsTakenCount.incrementAndGet()
                1
            }

            val handler = NotificationActionHandler(
                projector,
                historyRepo,
                reconciler,
                sessionManager,
                notifPrefs,
                medicationDao,
                notifBuilder,
                stockRepo,
                timeProvider,
            )

            val pool = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
            try {
                val jobs = (1..4).map {
                    CoroutineScope(pool).launch {
                        handler.processAction(context, "TAKEN", listOf(1), 5_000L, 100)
                    }
                }
                lockHeld.await()
                delay(100)
                releaseLock.complete(Unit)
                jobs.joinAll()
            } finally {
                pool.close()
            }

            assertEquals(
                "Only the lock holder may run handleTaken; the 3 dropped requests must not " +
                    "double-execute the workload (torn-mutex regression).",
                1,
                markAsTakenCount.get(),
            )
        } finally {
            unmockkObject(AppLogger)
            unmockkStatic(Log::class)
        }
    }
}
