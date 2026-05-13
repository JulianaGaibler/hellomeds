// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.di

import me.juliana.hellomeds.data.interfaces.DepletionChecker
import me.juliana.hellomeds.data.interfaces.LowStockChecker
import me.juliana.hellomeds.data.interfaces.ScheduleReconciler
import me.juliana.hellomeds.ui.util.PlatformNavigator
import me.juliana.hellomeds.ui.viewmodel.CmpMedicationDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.CmpStockDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.MedicationDisplayFormatter
import me.juliana.hellomeds.ui.viewmodel.StockDisplayFormatter
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific DI bindings for interfaces that Android provides via its own modules.
 *
 * Note: IOSScheduleReconciler and IOSNotificationDelegate are NOT registered here.
 * Their class references trigger iOS framework API class initialization at file load time,
 * causing FileFailedToInitializeException. They are loaded separately via
 * createIosNotificationModule() after startKoin completes.
 */
val iosPlatformModule: Module = module {
    // Stub reconciler — overridden by createIosNotificationModule() after startKoin
    single<ScheduleReconciler> {
        object : ScheduleReconciler {
            override suspend fun reconcile() {
                /* stub — replaced at runtime */
            }
        }
    }

    single<LowStockChecker> {
        object : LowStockChecker {
            override suspend fun checkAndNotify(medicationId: Int) {}
        }
    }

    single<DepletionChecker> {
        object : DepletionChecker {
            override suspend fun checkAndNotify(medicationId: Int) {}
        }
    }

    // Shared formatters using CMP string resources
    single<MedicationDisplayFormatter> { CmpMedicationDisplayFormatter() }
    single<StockDisplayFormatter> { CmpStockDisplayFormatter() }

    // Platform navigator — iOS routes to app settings as a fallback (the
    // exact-alarm banner never fires on iOS, so this is symmetry only).
    single { PlatformNavigator() }
}
