// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.di

import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.notifications.AlarmReconciler
import me.juliana.hellomeds.notifications.DepletionReminderNotifier
import me.juliana.hellomeds.notifications.LowStockNotifier
import me.juliana.hellomeds.notifications.MissedDoseProcessor
import me.juliana.hellomeds.notifications.NotificationActionHandler
import me.juliana.hellomeds.notifications.NotificationBuilder
import me.juliana.hellomeds.notifications.NotificationSessionManager
import me.juliana.hellomeds.ui.util.PlatformNavigator
import org.koin.dsl.module

val notificationModule = module {
    // NotificationSessionManager: dao + timeProvider
    single { NotificationSessionManager(get(), get()) }
    single { NotificationBuilder(get()) }
    // MissedDoseProcessor: catch-up + new-event notification posting (used by both
    // AlarmReconciler.processMissedDoses and GlobalAlarmReceiver.handleAlarm).
    // 8 collaborators: context, projector, sessionManager, medicationDao,
    // importanceLabelDao, notifBuilder, notifPrefs, historyDao.
    single {
        MissedDoseProcessor(get(), get(), get(), get(), get(), get(), get(), get())
    }
    // AlarmReconciler: 8 collaborators + timeProvider + reliabilityPrefs + missedDoseProcessor
    single {
        AlarmReconciler(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    single<ScheduleReconciler> { get<AlarmReconciler>() }
    // NotificationActionHandler: 8 collaborators + timeProvider
    single { NotificationActionHandler(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single<LowStockChecker> { LowStockNotifier(get(), get(), get(), get(), get()) }
    single<DepletionChecker> { DepletionReminderNotifier(get(), get(), get(), get(), get(), get()) }
    // Platform navigator — opens system settings panels (e.g. SCHEDULE_EXACT_ALARM)
    single { PlatformNavigator(get<android.content.Context>()) }
}
