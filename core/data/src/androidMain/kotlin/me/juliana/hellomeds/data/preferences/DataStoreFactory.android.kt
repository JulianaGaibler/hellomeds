// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.notificationDataStore by preferencesDataStore("notification_preferences")
private val Context.appearanceDataStore by preferencesDataStore("appearance_preferences")
private val Context.onboardingDataStore by preferencesDataStore("onboarding_preferences")
private val Context.cameraDataStore by preferencesDataStore("camera_preferences")
private val Context.autoBackupDataStore by preferencesDataStore("autobackup_preferences")
private val Context.reliabilityDataStore by preferencesDataStore("reliability_preferences")

fun createNotificationDataStore(context: Context): DataStore<Preferences> = context.notificationDataStore

fun createAppearanceDataStore(context: Context): DataStore<Preferences> = context.appearanceDataStore

fun createOnboardingDataStore(context: Context): DataStore<Preferences> = context.onboardingDataStore

fun createCameraDataStore(context: Context): DataStore<Preferences> = context.cameraDataStore
fun createAutoBackupDataStore(context: Context): DataStore<Preferences> = context.autoBackupDataStore
fun createReliabilityDataStore(context: Context): DataStore<Preferences> = context.reliabilityDataStore
