// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun dataStorePath(name: String): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path) + "/$name.preferences_pb"
}

fun createNotificationDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("notification_preferences").toPath() }

fun createAppearanceDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("appearance_preferences").toPath() }

fun createOnboardingDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("onboarding_preferences").toPath() }

fun createCameraDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("camera_preferences").toPath() }

fun createAutoBackupDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("autobackup_preferences").toPath() }

fun createReliabilityDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { dataStorePath("reliability_preferences").toPath() }
