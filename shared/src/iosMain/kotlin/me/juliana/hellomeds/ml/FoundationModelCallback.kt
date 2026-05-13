// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ml

/**
 * Callback bridge for Apple Foundation Models.
 *
 * Since Foundation Models is a Swift-only API (@Generable macro, LanguageModelSession),
 * and the shared KMP framework can't call Swift directly, we use a callback pattern:
 *
 * 1. Swift registers a callback during app init via [registerFoundationModelCallback]
 * 2. Kotlin calls [analyzeWithFoundationModel] which invokes the registered callback
 * 3. Swift runs the Foundation Model and returns result via the completion handler
 *
 * This avoids needing the FoundationModels framework as a direct KMP dependency.
 */

/**
 * Result from the Foundation Model analysis.
 */
data class FoundationModelResult(
    val names: List<String>,
    val type: String?,
    val strengthValue: Double?,
    val strengthUnit: String?,
    val success: Boolean,
)

/**
 * Type alias for the callback that Swift registers.
 * Parameters: (ocrText: String, completion: (FoundationModelResult) -> Unit)
 */
private var foundationModelCallback: ((String, (FoundationModelResult) -> Unit) -> Unit)? = null
private var foundationModelAvailable: Boolean = false

/**
 * Called from Swift during app initialization to register the Foundation Model callback.
 */
fun registerFoundationModelCallback(
    isAvailable: Boolean,
    callback: ((String, (FoundationModelResult) -> Unit) -> Unit)?,
) {
    foundationModelAvailable = isAvailable
    foundationModelCallback = callback
}

/**
 * Returns true if Apple Foundation Models is available on this device.
 */
fun isFoundationModelAvailable(): Boolean = foundationModelAvailable

/**
 * Calls the registered Foundation Model callback to analyze OCR text.
 * Returns null if no callback registered or Foundation Models unavailable.
 */
suspend fun analyzeWithFoundationModel(ocrText: String): FoundationModelResult? {
    val callback = foundationModelCallback ?: return null
    if (!foundationModelAvailable) return null

    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        callback(ocrText) { result ->
            cont.resumeWith(Result.success(result))
        }
    }
}
