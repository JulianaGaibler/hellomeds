// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.juliana.hellomeds.createImportanceLabel
import me.juliana.hellomeds.createMedication
import me.juliana.hellomeds.createNotificationSession
import me.juliana.hellomeds.createProjectedEvent
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.TimeProvider
import kotlin.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlarmReconcilerTest {

    @MockK
    private lateinit var projector: ScheduleProjector

    @MockK
    private lateinit var medicationDao: MedicationDao

    @MockK
    private lateinit var importanceLabelDao: ImportanceLabelDao

    @MockK
    private lateinit var sessionManager: NotificationSessionManager

    @MockK
    private lateinit var notificationPrefs: NotificationPreferences

    @RelaxedMockK
    private lateinit var alarmManager: AlarmManager

    @RelaxedMockK
    private lateinit var context: Context

    @RelaxedMockK
    private lateinit var notifBuilder: NotificationBuilder

    @RelaxedMockK
    private lateinit var reliabilityPrefs: ReliabilityPreferences

    @RelaxedMockK
    private lateinit var missedDoseProcessor: MissedDoseProcessor

    private lateinit var reconciler: AlarmReconciler

    private val fixedNow = 1_000_000_000_000L

    private val timeProvider: TimeProvider = object : TimeProvider {
        override fun now(): Instant = Instant.fromEpochMilliseconds(fixedNow)
    }

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        mockkStatic(PendingIntent::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk()
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        every { context.getSystemService(android.app.NotificationManager::class.java) } returns mockk(
            relaxed = true,
        )
        every {
            notificationPrefs.lockScreenVisibility
        } returns flowOf(me.juliana.hellomeds.data.model.enums.LockScreenVisibility.SHOW_WITH_NAMES)

        reconciler = AlarmReconciler(
            context,
            projector,
            medicationDao,
            importanceLabelDao,
            sessionManager,
            notificationPrefs,
            alarmManager,
            notifBuilder,
            timeProvider,
            reliabilityPrefs,
            missedDoseProcessor,
        )
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
        unmockkStatic(PendingIntent::class)
    }

    // --- computeNextWakeupTime ---

    @Test
    fun `returns event time when no sessions`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime)
        coEvery { sessionManager.getAllSessions() } returns emptyList()

        val result = reconciler.computeNextWakeupTime(fixedNow)

        assertEquals(eventTime, result)
    }

    @Test
    fun `returns follow-up time when sooner than event`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        val followUpTime = fixedNow + 300_000L
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime)
        coEvery { sessionManager.getAllSessions() } returns listOf(
            createNotificationSession(nextFollowUpTime = followUpTime),
        )

        val result = reconciler.computeNextWakeupTime(fixedNow)

        assertEquals(followUpTime, result)
    }

    @Test
    fun `returns event time when sooner than follow-up`() = runTest {
        val eventTime = fixedNow + 300_000L
        val followUpTime = fixedNow + 1_800_000L
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime)
        coEvery { sessionManager.getAllSessions() } returns listOf(
            createNotificationSession(nextFollowUpTime = followUpTime),
        )

        val result = reconciler.computeNextWakeupTime(fixedNow)

        assertEquals(eventTime, result)
    }

    @Test
    fun `returns null when nothing pending`() = runTest {
        coEvery { projector.findNextPendingEvent(fixedNow) } returns null
        coEvery { sessionManager.getAllSessions() } returns emptyList()

        val result = reconciler.computeNextWakeupTime(fixedNow)

        assertNull(result)
    }

    @Test
    fun `past-due follow-up times are included for self-healing catch-up`() = runTest {
        val pastFollowUp = fixedNow - 60_000L
        coEvery { projector.findNextPendingEvent(fixedNow) } returns null
        coEvery { sessionManager.getAllSessions() } returns listOf(
            createNotificationSession(nextFollowUpTime = pastFollowUp),
        )

        val result = reconciler.computeNextWakeupTime(fixedNow)

        // Past-due session times must be visible so reconcile() triggers immediate catch-up
        assertEquals(pastFollowUp, result)
    }

    // --- reconcile ---

    @Test
    fun `cancels alarm when notifications disabled`() = runTest {
        every { notificationPrefs.notificationsEnabled } returns flowOf(false)

        reconciler.reconcile(fixedNow)

        verify { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun `cancels alarm when no wakeup needed`() = runTest {
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns null
        coEvery { sessionManager.getAllSessions() } returns emptyList()

        reconciler.reconcile(fixedNow)

        verify { alarmManager.cancel(any<PendingIntent>()) }
    }

    @Test
    fun `sets alarm clock even for non-critical wakeup`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1)
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        coEvery { projector.getPendingEventsAtTime(eventTime) } returns listOf(
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1),
        )
        coEvery { medicationDao.getByIdSync(1) } returns createMedication(importanceLabelId = 1)
        coEvery { importanceLabelDao.getByIdSync(1) } returns createImportanceLabel(isCritical = false)

        reconciler.reconcile(fixedNow)

        // All alarms now use setAlarmClock to pierce Doze mode
        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun `sets alarm clock when wakeup is critical`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1)
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        coEvery { projector.getPendingEventsAtTime(eventTime) } returns listOf(
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1),
        )
        coEvery { medicationDao.getByIdSync(1) } returns createMedication(importanceLabelId = 1)
        coEvery { importanceLabelDao.getByIdSync(1) } returns createImportanceLabel(isCritical = true)

        reconciler.reconcile(fixedNow)

        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    // --- isNextWakeupCritical (tested through reconcile) ---

    @Test
    fun `critical session follow-up triggers alarm clock`() = runTest {
        val followUpTime = fixedNow + 300_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns null
        coEvery { sessionManager.getAllSessions() } returns listOf(
            createNotificationSession(
                nextFollowUpTime = followUpTime,
                hasCriticalMed = true,
            ),
        )

        reconciler.reconcile(fixedNow)

        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun `event with critical importance label triggers alarm clock`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1)
        // No critical sessions
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        coEvery { projector.getPendingEventsAtTime(eventTime) } returns listOf(
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1),
        )
        coEvery { medicationDao.getByIdSync(1) } returns createMedication(importanceLabelId = 1)
        coEvery { importanceLabelDao.getByIdSync(1) } returns createImportanceLabel(isCritical = true)

        reconciler.reconcile(fixedNow)

        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    @Test
    fun `all wakeups use alarm clock regardless of criticality`() = runTest {
        val eventTime = fixedNow + 3_600_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1)
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        coEvery { projector.getPendingEventsAtTime(eventTime) } returns listOf(
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1),
        )
        coEvery { medicationDao.getByIdSync(1) } returns createMedication(importanceLabelId = 1)
        coEvery { importanceLabelDao.getByIdSync(1) } returns createImportanceLabel(isCritical = false)

        reconciler.reconcile(fixedNow)

        // Scheduling priority is decoupled from presentation priority —
        // all alarms use setAlarmClock to guarantee Doze bypass
        verify { alarmManager.setAlarmClock(any(), any()) }
        verify(exactly = 0) { alarmManager.setAndAllowWhileIdle(any(), any(), any()) }
    }

    // --- Snooze timing ---

    @Test
    fun `snooze time sooner than event is chosen as next wakeup`() = runTest {
        // Consequence of failure: Snooze timer ignored, user sleeps through snoozed medication.
        val eventTime = fixedNow + 3_600_000L // 1 hour
        val snoozeTime = fixedNow + 300_000L // 5 minutes
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime)
        coEvery { sessionManager.getAllSessions() } returns listOf(
            createNotificationSession(snoozeUntilTime = snoozeTime, isSnoozed = true),
        )

        val result = reconciler.computeNextWakeupTime(fixedNow)

        assertEquals("Snooze time (5min) should win over event time (1h)", snoozeTime, result)
    }

    // --- Multi-medication criticality ---

    @Test
    fun `multiple medications at same time triggers criticality check for all`() = runTest {
        // Consequence of failure: Only one medication's criticality checked; critical med
        // at same slot uses non-critical alarm, potentially missing Doze bypass.
        val eventTime = fixedNow + 3_600_000L
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1)
        coEvery { sessionManager.getAllSessions() } returns emptyList()
        // 3 meds at same time — only med 3 is critical
        coEvery { projector.getPendingEventsAtTime(eventTime) } returns listOf(
            createProjectedEvent(scheduledTime = eventTime, medicationId = 1),
            createProjectedEvent(scheduledTime = eventTime, medicationId = 2),
            createProjectedEvent(scheduledTime = eventTime, medicationId = 3),
        )
        coEvery { medicationDao.getByIdSync(1) } returns createMedication(id = 1, importanceLabelId = 1)
        coEvery { medicationDao.getByIdSync(2) } returns createMedication(id = 2, importanceLabelId = 2)
        coEvery { medicationDao.getByIdSync(3) } returns createMedication(id = 3, importanceLabelId = 3)
        coEvery { importanceLabelDao.getByIdSync(1) } returns createImportanceLabel(id = 1, isCritical = false)
        coEvery { importanceLabelDao.getByIdSync(2) } returns createImportanceLabel(id = 2, isCritical = false)
        coEvery { importanceLabelDao.getByIdSync(3) } returns createImportanceLabel(id = 3, isCritical = true)

        reconciler.reconcile(fixedNow)

        // Critical med 3 should trigger setAlarmClock
        verify { alarmManager.setAlarmClock(any(), any()) }
    }

    // --- Reboot catch-up ---

    @Test
    fun `past event sets immediate alarm for catch-up`() = runTest {
        // Consequence of failure: After device reboot (phone off 07:55-08:15), the missed
        // 08:00 medication gets no notification. User MISSES a critical dose.
        val pastEventTime = fixedNow - 300_000L // 5 minutes ago
        every { notificationPrefs.notificationsEnabled } returns flowOf(true)
        coEvery { projector.findNextPendingEvent(fixedNow) } returns
            createProjectedEvent(scheduledTime = pastEventTime, medicationId = 1)
        coEvery { sessionManager.getAllSessions() } returns emptyList()

        reconciler.reconcile(fixedNow)

        // Past event arm: should set immediate alarm at now+100ms with setAlarmClock (critical=true)
        verify { alarmManager.setAlarmClock(any(), any()) }
    }
}
