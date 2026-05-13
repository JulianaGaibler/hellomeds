// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.model.enums

/**
 * User's choice for medication detection method
 */
enum class DetectionMethod {
    /**
     * Basic pattern matching using heuristics
     */
    HEURISTIC,

    /**
     * Gemini Nano AI for more accurate detection
     */
    GEMINI,
}
