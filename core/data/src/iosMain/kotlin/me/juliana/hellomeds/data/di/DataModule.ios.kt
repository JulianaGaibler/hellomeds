// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.di

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
import me.juliana.hellomeds.data.support.IOSDiagnosticsProvider
import me.juliana.hellomeds.data.support.PlatformDiagnosticsProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module = module {
    single { getDatabaseBuilder() }
    single { DatabaseKeyManager() }

    // Preferences — each wraps a platform-specific DataStore instance
    single { NotificationPreferences(createNotificationDataStore()) }
    single { AppearancePreferences(createAppearanceDataStore()) }
    single { OnboardingPreferences(createOnboardingDataStore()) }
    single { CameraPreferences(createCameraDataStore()) }
    single { PassphraseManager() }
    single { me.juliana.hellomeds.data.preferences.AutoBackupPreferences(createAutoBackupDataStore()) }
    single { ReliabilityPreferences(createReliabilityDataStore()) }
    single { AutoBackupStorageProvider(get()) }
    single<PlatformDiagnosticsProvider> { IOSDiagnosticsProvider() }
}
