// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.ml

import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType

/**
 * Platform-agnostic interface for medication detection from OCR text.
 *
 * Android: GeminiNanoMedicationEngine (Gemini Nano on-device AI)
 * iOS: AppleIntelligenceEngine (NaturalLanguage + CoreML)
 * Fallback: HeuristicMedicationEngine (pure Kotlin regex/dictionary)
 */
interface MedicationIntelligenceEngine {
    suspend fun guessMedicationDetails(ocrText: String): MedicationDetectionResult?
}

data class MedicationDetectionResult(
    val nameSuggestions: List<String> = emptyList(),
    val typeSuggestions: List<MedicationType> = emptyList(),
    val strengthSuggestion: StrengthSuggestion? = null,
    val usedAI: Boolean = false,
)

data class StrengthSuggestion(
    val value: Double,
    val unit: MedicationStrengthUnit,
)
