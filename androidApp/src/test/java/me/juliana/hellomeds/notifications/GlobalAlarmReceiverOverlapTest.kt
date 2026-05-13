// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.model.NotificationSession
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.util.TimeProvider
import org.junit.Test
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Regression test for the snooze/follow-up sibling overlap in
 * [GlobalAlarmReceiver.handleAlarm]. Pre-fix, when child A had a due follow-up
 * and child B had a due snooze at the same parent slot, Step 2's
 * `handleSnoozes` ran for B and rebuilt the entire sibling group — re-posting
 * the group summary that Step 1's `handleIndividualFollowUp` had already
 * posted with `isFollowUpAlert=true`. Result: double audible alert and a race
 * that could overwrite the freshly-escalated child notification.
 *
 * The fix expands `followUpKeys` to include `parentTimeSlotKey` of any
 * INDIVIDUAL follow-up, then checks both the snooze's own key and its parent
 * key against that set. This test asserts `showGroupSummary` is invoked
 * exactly once across the whole alarm-fire and that childB's snooze state is
 * still cleared (overlap detected, not silently leaked).
 *
 * handleAlarm resolves dependencies through KoinComponent, so this test
 * scopes a tiny Koin module to its body.
 */
class GlobalAlarmReceiverOverlapTest {

    @Test
    fun handleAlarm_followUpAndSnoozeAtSameParentSlot_postsSummaryExactlyOnce() = runBlocking {
        mockkObject(AppLogger)
        mockkStatic(Log::class)
        try {
            every { AppLogger.d(any(), any()) } returns Unit
            every { AppLogger.i(any(), any()) } returns Unit
            every { AppLogger.w(any(), any()) } returns Unit
            every { AppLogger.e(any(), any(), any()) } returns Unit
            every { Log.d(any(), any()) } returns 0
            every { Log.i(any(), any()) } returns 0

            val now = 5_000L
            val parentSlot = 4_000L

            val timeProvider = mockk<TimeProvider> { every { nowMillis() } returns now }
            val reconciler = mockk<AlarmReconciler>(relaxed = true)
            val projector = mockk<ScheduleProjector>()
            val sessionManager = mockk<NotificationSessionManager>(relaxed = true)
            val notifPrefs = mockk<NotificationPreferences> {
                every { lockScreenVisibility } returns flowOf(LockScreenVisibility.SHOW_WITH_NAMES)
            }
            val medicationDao = mockk<MedicationDao>(relaxed = true)
            val importanceLabelDao = mockk<ImportanceLabelDao>(relaxed = true)
            val missedDoseProcessor = mockk<MissedDoseProcessor>(relaxed = true) {
                every { groupKeyForTimeSlot(any()) } returns "hellomeds_$parentSlot"
            }
            coEvery {
                missedDoseProcessor.determineSummaryChannelFromDao(any(), any(), any())
            } returns NotificationChannels.CRITICAL_CHANNEL_ID
            coEvery { missedDoseProcessor.processMissedDoses(any()) } returns Unit

            val childA = NotificationSession(
                timeSlotKey = "${parentSlot}_1",
                scheduleIds = listOf(1),
                notificationId = 100,
                maxFollowUps = 4,
                followUpIntervalMs = 1000L,
                nextFollowUpTime = 4_500L,
                channelId = NotificationChannels.CRITICAL_CHANNEL_ID,
                hasCriticalMed = true,
                criticalAfterFollowUp = null,
                sessionType = SessionType.INDIVIDUAL,
                parentTimeSlotKey = parentSlot.toString(),
                scheduleId = 1,
            )
            val childB = NotificationSession(
                timeSlotKey = "${parentSlot}_2",
                scheduleIds = listOf(2),
                notificationId = 101,
                maxFollowUps = 4,
                followUpIntervalMs = 1000L,
                nextFollowUpTime = null,
                channelId = NotificationChannels.CRITICAL_CHANNEL_ID,
                hasCriticalMed = true,
                criticalAfterFollowUp = null,
                snoozeUntilTime = 4_500L,
                isSnoozed = true,
                sessionType = SessionType.INDIVIDUAL,
                parentTimeSlotKey = parentSlot.toString(),
                scheduleId = 2,
            )

            coEvery { sessionManager.getDueFollowUps(now) } returns listOf(childA)
            coEvery { sessionManager.getDueSnoozes(now) } returns listOf(childB)
            coEvery {
                sessionManager.getSessionsByParent(parentSlot.toString())
            } returns listOf(childA, childB)
            coEvery {
                sessionManager.updateFollowUpFired(childA.timeSlotKey)
            } returns childA.copy(followUpsFired = 1)
            coEvery { sessionManager.handleSnoozeFired(any()) } returns null

            val eventA = ProjectedEvent(
                scheduleId = 1,
                medicationId = 1,
                scheduledTime = parentSlot,
                dose = 1.0,
            )
            val eventB = ProjectedEvent(
                scheduleId = 2,
                medicationId = 2,
                scheduledTime = parentSlot,
                dose = 1.0,
            )
            coEvery { projector.getPendingEventsAtTime(parentSlot) } returns listOf(eventA, eventB)
            coEvery { medicationDao.getByIdSync(1) } returns mockk(relaxed = true)
            coEvery { medicationDao.getByIdSync(2) } returns mockk(relaxed = true)
            coEvery { importanceLabelDao.getByIdSync(any()) } returns null

            val testModule = org.koin.dsl.module {
                single<TimeProvider> { timeProvider }
                single<AlarmReconciler> { reconciler }
                single<ScheduleProjector> { projector }
                single<NotificationSessionManager> { sessionManager }
                single<NotificationPreferences> { notifPrefs }
                single<MedicationDao> { medicationDao }
                single<ImportanceLabelDao> { importanceLabelDao }
                single<MissedDoseProcessor> { missedDoseProcessor }
            }
            org.koin.core.context.startKoin { modules(testModule) }
            try {
                val context = mockk<Context>(relaxed = true) {
                    every {
                        getSystemService(Context.NOTIFICATION_SERVICE)
                    } returns mockk<NotificationManager>(relaxed = true)
                    every { getSystemService(PowerManager::class.java) } returns null
                }

                val handleAlarmFn = GlobalAlarmReceiver::class.declaredMemberFunctions
                    .first { it.name == "handleAlarm" }
                    .also { it.isAccessible = true }
                handleAlarmFn.callSuspend(GlobalAlarmReceiver(), context)

                // Step 1's handleIndividualFollowUp posts the summary once
                // (allPendingEvents.size = 2). Step 2's snooze for childB has
                // parentKey="4000" which is in the expanded followUpKeys, so
                // its handleSnoozes branch is skipped. Pre-fix this verify
                // would observe 2 calls (Step 1 + Step 2 sibling rebuild).
                verify(exactly = 1) {
                    missedDoseProcessor.showGroupSummary(
                        any(),
                        any(),
                        any(),
                        parentSlot,
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                }
                // Overlap-clearing branch must still run so the snooze state
                // is reset (parent-aware check identified the overlap).
                coVerify { sessionManager.handleSnoozeFired("${parentSlot}_2") }
            } finally {
                org.koin.core.context.stopKoin()
            }
        } finally {
            unmockkObject(AppLogger)
            unmockkStatic(Log::class)
        }
    }
}
