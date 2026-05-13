// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import me.juliana.hellomeds.createMedication
import me.juliana.hellomeds.createProjectedEvent
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.util.AppLogger
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Verifies the notification-posting fallback path that lives on [MissedDoseProcessor].
 * Originally tested via GlobalAlarmReceiver, but the showNotification helper moved
 * to MissedDoseProcessor when the catch-up pipeline was extracted so it could be
 * shared with BootReceiver. The fallback contract (post a generic "tap to open"
 * notification when buildNotification throws) is unchanged.
 */
class GlobalAlarmReceiverTest {

    @RelaxedMockK
    private lateinit var context: Context

    @MockK
    private lateinit var notifBuilder: NotificationBuilder

    @RelaxedMockK
    private lateinit var notifManager: NotificationManager

    @RelaxedMockK
    private lateinit var projector: ScheduleProjector

    @RelaxedMockK
    private lateinit var sessionManager: NotificationSessionManager

    @RelaxedMockK
    private lateinit var medicationDao: MedicationDao

    @RelaxedMockK
    private lateinit var importanceLabelDao: ImportanceLabelDao

    @RelaxedMockK
    private lateinit var notifPrefs: NotificationPreferences

    @RelaxedMockK
    private lateinit var historyDao: MedicationHistoryDao

    private lateinit var processor: MissedDoseProcessor

    private val fallbackNotification = mockk<Notification>()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        mockkStatic(PendingIntent::class)
        mockkObject(AppLogger)
        mockkConstructor(NotificationCompat.Builder::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { AppLogger.e(any(), any(), any()) } returns Unit
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notifManager

        // Mock NotificationCompat.Builder fluent API so the fallback path works in unit tests
        every {
            anyConstructed<NotificationCompat.Builder>().setContentTitle(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setContentText(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setSmallIcon(any<Int>())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setPriority(
                any(),
            )
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setAutoCancel(any())
        } answers { self as NotificationCompat.Builder }
        every {
            anyConstructed<NotificationCompat.Builder>().setContentIntent(any())
        } answers { self as NotificationCompat.Builder }
        every { anyConstructed<NotificationCompat.Builder>().build() } returns fallbackNotification

        processor = MissedDoseProcessor(
            context,
            projector,
            sessionManager,
            medicationDao,
            importanceLabelDao,
            notifBuilder,
            notifPrefs,
            historyDao,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(PendingIntent::class)
        unmockkObject(AppLogger)
        unmockkConstructor(NotificationCompat.Builder::class)
    }

    @Test
    fun `showNotification posts fallback when buildNotification throws`() {
        val events = listOf(createProjectedEvent())
        val medications = listOf(createMedication())

        every {
            notifBuilder.buildNotification(
                events = any(),
                medications = any(),
                channelId = any(),
                notificationId = any(),
                scheduledTime = any(),
                lockScreenVisibility = any(),
                isFollowUp = any(),
                isSnoozed = any(),
                groupKey = any(),
                followUpNumber = any(),
            )
        } throws IndexOutOfBoundsException("Index 2 out of bounds for length 2")

        processor.showNotification(
            events = events,
            medications = medications,
            channelId = "test_channel",
            notificationId = 42,
            scheduledTime = 1_000_000_000_000L,
            lockScreenVisibility = LockScreenVisibility.SHOW_WITH_NAMES,
            isFollowUp = false,
        )

        // Fallback notification should still be posted with correct ID
        verify { notifManager.notify(42, fallbackNotification) }

        // Error should be logged via AppLogger (reaches DiagnosticLog)
        verify { AppLogger.e("MissedDoseProcessor", "Failed to build notification", any()) }
    }

    @Test
    fun `showNotification posts normal notification on success`() {
        val events = listOf(createProjectedEvent())
        val medications = listOf(createMedication())
        val fakeNotification = mockk<Notification>()

        every {
            notifBuilder.buildNotification(
                events = any(),
                medications = any(),
                channelId = any(),
                notificationId = any(),
                scheduledTime = any(),
                lockScreenVisibility = any(),
                isFollowUp = any(),
                isSnoozed = any(),
                groupKey = any(),
                followUpNumber = any(),
            )
        } returns fakeNotification

        processor.showNotification(
            events = events,
            medications = medications,
            channelId = "test_channel",
            notificationId = 42,
            scheduledTime = 1_000_000_000_000L,
            lockScreenVisibility = LockScreenVisibility.SHOW_WITH_NAMES,
            isFollowUp = false,
        )

        verify { notifManager.notify(42, fakeNotification) }

        // No error logged
        verify(exactly = 0) { AppLogger.e(any(), any(), any()) }
    }
}
