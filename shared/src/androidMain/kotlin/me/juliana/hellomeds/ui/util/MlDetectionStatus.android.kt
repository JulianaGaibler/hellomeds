// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Set by the app module at startup — returns MlDetectionStatusValue
var mlDetectionStatusChecker: (suspend () -> MlDetectionStatusValue)? = null

@Composable
actual fun rememberMlDetectionStatus(): MlDetectionStatusValue {
    var status by remember { mutableStateOf(MlDetectionStatusValue.UNAVAILABLE) }
    val checker = mlDetectionStatusChecker

    LaunchedEffect(Unit) {
        if (checker != null) {
            status = checker()
        }
    }

    return status
}

actual fun mlDetectionMethodLabel(): String = "Gemini"
