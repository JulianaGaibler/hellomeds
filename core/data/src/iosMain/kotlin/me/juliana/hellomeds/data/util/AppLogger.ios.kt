// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual object AppLogger {
    private val isDebug = Platform.isDebugBinary

    actual fun d(tag: String, message: String) {
        if (isDebug) println("D/$tag: $message")
        DiagnosticLog.d(tag, message)
    }

    actual fun i(tag: String, message: String) {
        if (isDebug) println("I/$tag: $message")
        DiagnosticLog.i(tag, message)
    }

    actual fun w(tag: String, message: String) {
        if (isDebug) println("W/$tag: $message")
        DiagnosticLog.w(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (isDebug) {
            println("E/$tag: $message")
            throwable?.printStackTrace()
        }
        DiagnosticLog.e(tag, if (throwable != null) "$message: ${throwable.message}" else message)
    }
}
