// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.di

import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.crypto.DatabaseKeyManager
import me.juliana.hellomeds.data.crypto.configureEncryptedDriver
import me.juliana.hellomeds.data.database.AppDatabase
import me.juliana.hellomeds.data.database.migrations.MIGRATION_3_4
import me.juliana.hellomeds.data.database.seedDefaultImportanceLabels
import me.juliana.hellomeds.data.interfaces.StockContainerAnchor
import me.juliana.hellomeds.data.repository.ImportanceLabelRepository
import me.juliana.hellomeds.data.repository.MedicationHistoryRepository
import me.juliana.hellomeds.data.repository.MedicationRepository
import me.juliana.hellomeds.data.repository.ScheduleRepository
import me.juliana.hellomeds.data.repository.StockTrackingRepository
import me.juliana.hellomeds.data.service.ScheduleProjector
import me.juliana.hellomeds.data.service.StockPredictionEngine
import me.juliana.hellomeds.data.util.MissedDoseDetector
import me.juliana.hellomeds.data.util.ReliabilityStateProvider
import me.juliana.hellomeds.data.util.RoomTransactionRunner
import me.juliana.hellomeds.data.util.SystemTimeProvider
import me.juliana.hellomeds.data.util.TimeProvider
import me.juliana.hellomeds.data.util.TransactionRunner
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformDataModule: Module

val dataModule = module {
    includes(platformDataModule)

    // Single source of truth for "now" — see TimeProvider.kt for rationale.
    single<TimeProvider> { SystemTimeProvider() }

    // Database (encrypted via SQLCipher).
    // Migrations are registered via .addMigrations(...). There is intentionally no
    // fallbackToDestructiveMigration() — a missing migration must throw at startup
    // rather than silently wiping user health data. Downgrades still wipe to recover
    // from a downgrade install (rare; would otherwise crash on schema mismatch).
    single<AppDatabase> {
        val builder = get<RoomDatabase.Builder<AppDatabase>>()
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .addMigrations(MIGRATION_3_4)
        configureEncryptedDriver(builder, get<DatabaseKeyManager>())
            .build()
            .also { db ->
                CoroutineScope(Dispatchers.Default).launch {
                    seedDefaultImportanceLabels(db.importanceLabelDao())
                }
            }
    } bind RoomDatabase::class

    // Transaction abstraction — production wraps Room's writer connection; tests
    // can substitute a no-op runner without needing the Room builder to run.
    single<TransactionRunner> { RoomTransactionRunner(get<AppDatabase>()) }

    // DAOs
    single { get<AppDatabase>().importanceLabelDao() }
    single { get<AppDatabase>().medicationDao() }
    single { get<AppDatabase>().scheduleDao() }
    single { get<AppDatabase>().medicationHistoryDao() }
    single { get<AppDatabase>().stockAdjustmentDao() }
    single { get<AppDatabase>().notificationSessionDao() }

    // Services
    single { ScheduleProjector(get(), get(), get()) }
    single { StockPredictionEngine() }
    // Reliability surfacing — combines missed doses + platform-specific flags
    single { MissedDoseDetector(get(), get(), get(), get(), get(), get()) }
    single { ReliabilityStateProvider(get(), get()) }
    single { me.juliana.hellomeds.data.backup.BackupExportService(get(), get(), get(), get(), get()) }
    single {
        me.juliana.hellomeds.data.backup.BackupImportService(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    single { me.juliana.hellomeds.data.backup.AutoBackupService(get(), get(), get(), get()) }
    single {
        me.juliana.hellomeds.data.support.BugReportService(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }

    // Repositories (param counts match constructors)
    single { MedicationRepository(get(), get(), get(), get()) } // 4 params
    single { ScheduleRepository(get(), get(), get()) } // 3 params
    single {
        MedicationHistoryRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    } // 9 params
    single {
        StockTrackingRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    } bind StockContainerAnchor::class // 8 params
    single { ImportanceLabelRepository(get(), get(), get()) } // 3 params
}
