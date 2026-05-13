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
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import org.junit.Test
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Regression test for the INDIVIDUAL-branch summary-channel downgrade in
 * [GlobalAlarmReceiver.handleSnooze]. Pre-fix, the snooze rebuild's
 * summary-channel computation only inspected CRITICAL_CHANNEL_ID and silently
 * downgraded any alarm-importance group to NORMAL — losing the DnD/Focus
 * bypass on the summary that owns the alert (GROUP_ALERT_SUMMARY).
 *
 * The fix uses the canonical hierarchy `ALARM > CRITICAL > NORMAL`. This test
 * drives handleSnooze with an alarm-channel sibling and a normal-channel
 * sibling and asserts the summary is rebuilt on ALARM_CHANNEL_ID.
 *
 * handleSnooze takes all collaborators as parameters (no Koin), so we drive it
 * via Kotlin reflection without standing up the full DI graph.
 */
class GlobalAlarmReceiverSnoozeChannelTest {

    @Test
    fun handleSnooze_individualWithAlarmAndNormalChildren_postsSummaryOnAlarmChannel() = runBlocking {
        mockkObject(AppLogger)
        mockkStatic(Log::class)
        try {
            every { AppLogger.d(any(), any()) } returns Unit
            every { AppLogger.i(any(), any()) } returns Unit
            every { AppLogger.e(any(), any(), any()) } returns Unit
            every { Log.d(any(), any()) } returns 0
            every { Log.i(any(), any()) } returns 0

            val notifManager = mockk<NotificationManager>(relaxed = true)
            val context = mockk<Context>(relaxed = true) {
                every { getSystemService(Context.NOTIFICATION_SERVICE) } returns notifManager
            }
            val projector = mockk<ScheduleProjector>()
            val sessionManager = mockk<NotificationSessionManager>(relaxed = true)
            val medicationDao = mockk<MedicationDao>(relaxed = true)
            val missedDoseProcessor = mockk<MissedDoseProcessor>(relaxed = true) {
                every { groupKeyForTimeSlot(any()) } returns "hellomeds_5000"
            }

            val parentKey = "5000"
            val childA = NotificationSession(
                timeSlotKey = "5000_1",
                scheduleIds = listOf(1),
                notificationId = 100,
                maxFollowUps = 0,
                followUpIntervalMs = 0L,
                nextFollowUpTime = null,
                channelId = NotificationChannels.NORMAL_CHANNEL_ID,
                hasCriticalMed = false,
                criticalAfterFollowUp = null,
                sessionType = SessionType.INDIVIDUAL,
                parentTimeSlotKey = parentKey,
                scheduleId = 1,
            )
            val childB = NotificationSession(
                timeSlotKey = "5000_2",
                scheduleIds = listOf(2),
                notificationId = 101,
                maxFollowUps = 0,
                followUpIntervalMs = 0L,
                nextFollowUpTime = null,
                channelId = NotificationChannels.ALARM_CHANNEL_ID, // the alarm-importance sibling
                hasCriticalMed = true,
                criticalAfterFollowUp = null,
                sessionType = SessionType.INDIVIDUAL,
                parentTimeSlotKey = parentKey,
                scheduleId = 2,
            )

            coEvery { sessionManager.getSessionsByParent(parentKey) } returns listOf(childA, childB)
            val eventA = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = 5_000L,
                dose = 1.0,
            )
            val eventB = ProjectedEvent(
                scheduleId = 2,
                medicationId = 2,
                scheduledTime = 5_000L,
                dose = 1.0,
            )
            coEvery { projector.getPendingEventsAtTime(5_000L) } returns listOf(eventA, eventB)
            coEvery { medicationDao.getByIdSync(1) } returns mockk(relaxed = true)
            coEvery { medicationDao.getByIdSync(2) } returns mockk(relaxed = true)

            val handleSnoozeFn = GlobalAlarmReceiver::class.declaredMemberFunctions
                .first { it.name == "handleSnooze" }
                .also { it.isAccessible = true }
            handleSnoozeFn.callSuspend(
                GlobalAlarmReceiver(),
                context,
                childB, // entry session — INDIVIDUAL branch iterates both siblings
                projector,
                sessionManager,
                medicationDao,
                missedDoseProcessor,
                LockScreenVisibility.SHOW_WITH_NAMES,
            )

            // Pre-fix posted NORMAL because the when only checked CRITICAL.
            // Canonical ordering (ALARM > CRITICAL > NORMAL) must promote to ALARM.
            verify {
                missedDoseProcessor.showGroupSummary(
                    allEvents = any(),
                    allMedications = any(),
                    summaryChannelId = NotificationChannels.ALARM_CHANNEL_ID,
                    scheduledTime = 5_000L,
                    lockScreenVisibility = any(),
                    isFollowUp = any(),
                    isSnoozed = any(),
                    isFollowUpAlert = any(),
                )
            }
        } finally {
            unmockkObject(AppLogger)
            unmockkStatic(Log::class)
        }
    }
}
