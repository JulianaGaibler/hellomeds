// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

actual class PlatformNavigator(private val context: Context) {

    /**
     * Routes to `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` on Android 12+
     * (the page where users grant SCHEDULE_EXACT_ALARM). On older versions
     * the permission is granted by manifest declaration, so this falls back
     * to the app-info page in case anything else needs adjusting.
     *
     * Uses `FLAG_ACTIVITY_NEW_TASK` because this is invoked with the application
     * `Context`, which has no task affinity of its own.
     */
    actual fun openExactAlarmSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
