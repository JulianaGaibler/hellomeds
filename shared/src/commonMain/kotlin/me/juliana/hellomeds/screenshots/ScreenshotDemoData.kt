// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import me.juliana.hellomeds.shared.Res

/** Single source of truth for the demo backup JSON used by both Android and iOS screenshot runs. */
object ScreenshotDemoData {
    suspend fun loadJson(languageTag: String): String {
        val lang = languageTag.substringBefore('-').lowercase()
        val candidates = listOf("files/screenshots/example.$lang.json", "files/screenshots/example.en.json")
        for (path in candidates) {
            try {
                return Res.readBytes(path).decodeToString()
            } catch (_: Throwable) {
                // try next candidate
            }
        }
        error("No screenshot demo JSON found for language=$languageTag")
    }
}
