// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.di

import android.content.Context
import me.juliana.hellomeds.data.backup.AutoBackupStorageProvider
import me.juliana.hellomeds.data.crypto.DatabaseKeyManager
import me.juliana.hellomeds.data.crypto.PassphraseManager
import me.juliana.hellomeds.data.database.getDatabaseBuilder
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.preferences.createAppearanceDataStore
import me.juliana.hellomeds.data.preferences.createAutoBackupDataStore
import me.juliana.hellomeds.data.preferences.createCameraDataStore
import me.juliana.hellomeds.data.preferences.createNotificationDataStore
import me.juliana.hellomeds.data.preferences.createOnboardingDataStore
import me.juliana.hellomeds.data.preferences.createReliabilityDataStore
import me.juliana.hellomeds.data.support.AndroidDiagnosticsProvider
import me.juliana.hellomeds.data.support.PlatformDiagnosticsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module = module {
    single { getDatabaseBuilder(get<Context>()) }
    single { DatabaseKeyManager(get<Context>()) }

    // Preferences — each wraps a platform-specific DataStore instance
    single { NotificationPreferences(createNotificationDataStore(get<Context>())) }
    single { AppearancePreferences(createAppearanceDataStore(get<Context>())) }
    single { OnboardingPreferences(createOnboardingDataStore(get<Context>())) }
    single { CameraPreferences(createCameraDataStore(get<Context>())) }
    single { PassphraseManager(get<Context>()) }
    single { me.juliana.hellomeds.data.preferences.AutoBackupPreferences(createAutoBackupDataStore(get<Context>())) }
    single { ReliabilityPreferences(createReliabilityDataStore(get<Context>())) }
    single { AutoBackupStorageProvider(get<Context>(), get()) }
    single<PlatformDiagnosticsProvider> { AndroidDiagnosticsProvider(get<Context>()) }
}
