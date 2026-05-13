// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.domain.ml.HeuristicMedicationEngine
import me.juliana.hellomeds.domain.ml.MedicationIntelligenceEngine
import me.juliana.hellomeds.ml.AppleIntelligenceEngine
import me.juliana.hellomeds.ui.features.settings.IOSDebugViewModel
import me.juliana.hellomeds.workers.IOSBackgroundTaskManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for iOS notification classes.
 *
 * IOSNotificationDelegate extends NSObject — Koin cannot reflect on it.
 * We wrap it in a NotificationHandlerHolder (pure Kotlin class) so Koin
 * only indexes the pure Kotlin type.
 */

/**
 * Pure Kotlin wrapper that holds the IOSNotificationDelegate.
 * Koin indexes this class (safe), not the NSObject subclass.
 */
class NotificationHandlerHolder(val delegate: IOSNotificationDelegate)

fun createIosNotificationModule() = module {
    single { IOSNotificationSessionManager(get()) }

    single<ScheduleReconciler> {
        IOSScheduleReconciler(get(), get(), get(), get(), get(), get())
    }

    // IOSNotificationDelegate now resolves its DB-touching collaborators lazily
    // (see class doc) — Koin construction is dep-free.
    single {
        NotificationHandlerHolder(IOSNotificationDelegate())
    }

    // IOSBackgroundTaskManager also resolves its collaborators lazily so that
    // registerTasks() at cold launch does not trigger AppDatabase construction.
    single { IOSBackgroundTaskManager() }

    // ML / medication detection — both are pure Kotlin classes, safe for Koin.
    // AppleIntelligenceEngine imports platform.NaturalLanguage.* and platform.Foundation.*
    // but only uses them inside method bodies (not at file scope), so it's safe here.
    // Registered in the notification module (loaded after startKoin) to avoid any
    // risk of iOS framework class initialization during module scanning.
    single { HeuristicMedicationEngine() }
    single<MedicationIntelligenceEngine> { AppleIntelligenceEngine(get(), get()) }

    viewModel { IOSDebugViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
