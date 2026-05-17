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

/** Android: Gemini Nano via ML Kit. iOS: Apple Intelligence availability. */
@Composable
expect fun rememberMlDetectionStatus(): MlDetectionStatusValue

/** Android: "Gemini". iOS: "Apple Intelligence". */
expect fun mlDetectionMethodLabel(): String
