// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import android.util.Log

actual object AppLogger {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
        DiagnosticLog.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
        DiagnosticLog.i(tag, message)
    }
    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
        DiagnosticLog.w(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        DiagnosticLog.e(tag, if (throwable != null) "$message: ${throwable.message}" else message)
    }
}
