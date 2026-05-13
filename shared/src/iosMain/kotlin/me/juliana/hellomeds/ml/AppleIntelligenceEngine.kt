// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ml

import kotlinx.coroutines.flow.first
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.domain.ml.HeuristicMedicationEngine
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.domain.ml.MedicationIntelligenceEngine
import me.juliana.hellomeds.domain.ml.StrengthSuggestion

/**
 * iOS implementation of [MedicationIntelligenceEngine].
 *
 * Strategy:
 * 1. Try Apple Foundation Models (~3B on-device LLM, iOS 26+, M-series/A17+)
 *    via registered Swift callback bridge
 * 2. Fall back to shared pure-Kotlin [HeuristicMedicationEngine]
 *
 * Foundation Models uses guided generation with @Generable Swift structs
 * (equivalent to Gemini Nano on Android). The Swift bridge is registered
 * during app init via [registerFoundationModelCallback].
 */
class AppleIntelligenceEngine(
    private val heuristicFallback: HeuristicMedicationEngine,
    private val cameraPreferences: CameraPreferences,
) : MedicationIntelligenceEngine {

    private val TAG = "AppleIntelligenceEngine"

    override suspend fun guessMedicationDetails(ocrText: String): MedicationDetectionResult? {
        if (ocrText.length < 3) return null

        val userMethod = cameraPreferences.detectionMethod.first()

        // Try Apple Foundation Models only if user opted in (GEMINI on iOS == Apple Intelligence)
        if (userMethod == DetectionMethod.GEMINI && isFoundationModelAvailable()) {
            AppLogger.d(TAG, "Trying Apple Foundation Models...")
            val fmResult = analyzeWithFoundationModel(ocrText)
            if (fmResult != null && fmResult.success) {
                AppLogger.d(
                    TAG,
                    "Foundation Models returned: names=${fmResult.names}, type=${fmResult.type}, strength=${fmResult.strengthValue}${fmResult.strengthUnit}",
                )
                return convertFoundationModelResult(fmResult)
            }
            AppLogger.d(TAG, "Foundation Models returned no results, falling back")
        } else {
            AppLogger.d(TAG, "Foundation Models not available on this device")
        }

        // Fall back to shared heuristic engine
        AppLogger.d(TAG, "Using heuristic engine (smart pattern matching)")
        return heuristicFallback.guessMedicationDetails(ocrText)
    }

    private fun convertFoundationModelResult(result: FoundationModelResult): MedicationDetectionResult {
        val typeSuggestions = result.type?.let { typeStr ->
            MedicationType.fromValue(typeStr)?.let { listOf(it) }
        } ?: emptyList()

        val strengthSuggestion = if (result.strengthValue != null && result.strengthUnit != null) {
            val unit = MedicationStrengthUnit.fromValue(result.strengthUnit)
            if (unit != null) StrengthSuggestion(result.strengthValue, unit) else null
        } else {
            null
        }

        return MedicationDetectionResult(
            nameSuggestions = result.names.distinctBy { it.lowercase() }.take(4),
            typeSuggestions = typeSuggestions,
            strengthSuggestion = strengthSuggestion,
            usedAI = true,
        )
    }
}
