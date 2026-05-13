// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.util.camera.CoordinateTransformer

/**
 * Analysis phase - only used when camera is frozen
 */
enum class AnalysisPhase {
    INITIALIZING, // Positioning reticle after freeze
    READY, // Reticle positioned, awaiting/showing OCR results
    INSUFFICIENT_TEXT, // OCR found < 4 words
    GRACE_PERIOD, // Sufficient text, 3s countdown before Gemini
    PROCESSING, // Calling Gemini
    NO_DATA, // Gemini returned no valid data
    COMPLETE, // Analysis complete, results ready
}

/**
 * Object detection state (while live camera is scanning)
 */
enum class ObjectState {
    NO_OBJECT, // No object detected
    OBJECT_NO_TEXT, // Object detected but no/insufficient text
    OBJECT_WITH_TEXT, // Object detected with sufficient text
}

/**
 * State holder for camera detection screen.
 * Manages all mutable state for both live scanning and frozen analysis modes.
 */
@Stable
class CameraDetectionState {
    // Camera mode
    var isCameraLive: Boolean by mutableStateOf(true)
    var isTorchOn: Boolean by mutableStateOf(false)
    var wasTorchOnBeforeFreeze: Boolean = false

    // Analysis state (only used when camera is frozen)
    var analysisPhase: AnalysisPhase? by mutableStateOf(null)

    // Live scanning state
    var objectState: ObjectState by mutableStateOf(ObjectState.NO_OBJECT)
    var detectedObjectBox: Rect? by mutableStateOf(null)

    // Frozen analysis state
    var frozenFullBitmap: Bitmap? = null
    var frozenImageRotation: Int = 0
    var extractedText: String = ""
    var detectionResult: MedicationDetectionResult? by mutableStateOf(null)
    var wordCount: Int by mutableIntStateOf(0)

    // Reticle position (screen coordinates in pixels)
    var reticleLeft: Float by mutableFloatStateOf(0f)
    var reticleTop: Float by mutableFloatStateOf(0f)
    var reticleRight: Float by mutableFloatStateOf(0f)
    var reticleBottom: Float by mutableFloatStateOf(0f)
    var currentDragAction: String? = null

    // Coordinate transformer
    var coordinateTransformer: CoordinateTransformer? = null

    // Screen dimensions
    var currentScreenWidth: Float = 0f
    var currentScreenHeight: Float = 0f

    // Grace period management
    var isUserDragging: Boolean = false
    var gracePeriodVersion: Int = 0

    // Stabilization for live scanning
    var hasDetectedObjectOnce: Boolean = false
    var framesWithoutObject: Int = 0
    val maxFramesWithoutObject: Int = 15
}
