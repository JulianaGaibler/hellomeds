// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.util

import java.io.File

actual fun readDiagnosticLogFile(path: String): String? {
    val file = File(path)
    return if (file.exists()) file.readText() else null
}

actual fun writeDiagnosticLogFile(path: String, content: String) {
    File(path).writeText(content)
}

actual fun appendDiagnosticLogFile(path: String, line: String) {
    File(path).appendText(line + "\n")
}
