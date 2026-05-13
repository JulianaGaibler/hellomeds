// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.work.WorkManager
import org.koin.dsl.module

val appModule = module {
    // System services (Android-only)
    single { get<Context>().getSystemService(AlarmManager::class.java) }
    single { get<Context>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    single { WorkManager.getInstance(get()) }
}
