// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import kotlinx.serialization.json.Json

const val HMBACKUP_MIME_TYPE = "application/vnd.hellomeds.backup"
const val HMBACKUP_EXTENSION = "hmbackup"

val backupJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
}
