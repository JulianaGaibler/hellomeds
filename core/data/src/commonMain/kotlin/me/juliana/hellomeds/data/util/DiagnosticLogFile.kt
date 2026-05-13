// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

/**
 * Platform-specific file I/O for [DiagnosticLog] persistence.
 * Implementations use java.io.File on Android and NSFileManager on iOS.
 */
expect fun readDiagnosticLogFile(path: String): String?

expect fun writeDiagnosticLogFile(path: String, content: String)

expect fun appendDiagnosticLogFile(path: String, line: String)
