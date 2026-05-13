// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable

enum class MlDetectionStatusValue {
    AVAILABLE,
    DOWNLOADABLE,
    DOWNLOADING,
    UNAVAILABLE,
}

/**
 * Returns the current ML detection model status.
 * Android: checks Gemini Nano via ML Kit.
 * iOS: checks Apple Intelligence availability.
 */
@Composable
expect fun rememberMlDetectionStatus(): MlDetectionStatusValue

/**
 * Platform-specific label for the advanced ML detection method.
 * Android: "Gemini", iOS: "Apple Intelligence"
 */
expect fun mlDetectionMethodLabel(): String
