// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIDevice

@Composable
actual fun rememberMlDetectionStatus(): MlDetectionStatusValue {
    return remember {
        // Apple Intelligence requires iOS 18.1+
        val version = UIDevice.currentDevice.systemVersion
        val major = version.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val minor = version.split(".").getOrNull(1)?.toIntOrNull() ?: 0
        if (major > 18 || (major == 18 && minor >= 1)) {
            MlDetectionStatusValue.AVAILABLE
        } else {
            MlDetectionStatusValue.UNAVAILABLE
        }
    }
}

actual fun mlDetectionMethodLabel(): String = "Apple Intelligence"
