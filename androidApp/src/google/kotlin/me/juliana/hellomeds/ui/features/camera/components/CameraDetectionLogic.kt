// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.unit.Density
import me.juliana.hellomeds.data.util.AppLogger
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.ml.detector.MedicationDetector
import me.juliana.hellomeds.util.camera.CoordinateTransformer

private const val TAG = "CameraDetectionLogic"

/**
 * Resume live camera mode - clears all frozen state and restarts scanning
 */
fun CameraDetectionState.resumeLiveCamera() {
    Log.d(TAG, "=== RESUMING LIVE CAMERA ===")
    isCameraLive = true
    analysisPhase = null

    // Clear frozen state
    frozenFullBitmap = null
    frozenImageRotation = 0
    extractedText = ""
    detectionResult = null
    wordCount = 0

    // Clear reticle
    reticleLeft = 0f
    reticleTop = 0f
    reticleRight = 0f
    reticleBottom = 0f
    coordinateTransformer = null

    // Reset live scanning state
    objectState = ObjectState.NO_OBJECT
    detectedObjectBox = null
    hasDetectedObjectOnce = false
    framesWithoutObject = 0

    // Reset grace period
    isUserDragging = false
    gracePeriodVersion = 0

    // Restore flash state from before freeze
    isTorchOn = wasTorchOnBeforeFreeze
    Log.d(TAG, "Flash state restored: isTorchOn=$isTorchOn")

    Log.d(TAG, "Live camera resumed")
}

/**
 * Freeze camera and initialize reticle - called when shutter pressed.
 * This function encapsulates all state transitions from live to frozen mode.
 */
fun CameraDetectionState.freezeCameraAndInitializeReticle(
    topInset: Float,
    density: Density,
    medicationDetector: MedicationDetector,
) {
    Log.d(TAG, "=== FREEZING CAMERA AND INITIALIZING RETICLE ===")

    // Validate we have necessary data
    if (frozenFullBitmap == null) {
        AppLogger.e(TAG, "Cannot freeze: no frozen bitmap available")
        return
    }
    if (currentScreenWidth == 0f || currentScreenHeight == 0f) {
        AppLogger.e(TAG, "Cannot freeze: screen dimensions not available")
        return
    }

    // Stop camera analyzer
    isCameraLive = false
    analysisPhase = AnalysisPhase.INITIALIZING

    val bitmap = frozenFullBitmap!!

    Log.d(TAG, "Screen: $currentScreenWidth×$currentScreenHeight")
    Log.d(TAG, "Bitmap: ${bitmap.width}×${bitmap.height}")
    Log.d(TAG, "Rotation: $frozenImageRotation°")

    // Create coordinate transformer
    val transformer = CoordinateTransformer(
        bitmapWidth = bitmap.width,
        bitmapHeight = bitmap.height,
        screenWidth = currentScreenWidth,
        screenHeight = currentScreenHeight,
        rotation = frozenImageRotation,
    )
    coordinateTransformer = transformer

    // Position reticle on detected object (if available), otherwise use centered fallback
    if (detectedObjectBox != null) {
        val box = detectedObjectBox!!
        Log.d(TAG, "Positioning reticle on detected object: $box")

        // Check if detected object is mostly visible
        val isVisible = transformer.isInVisibleArea(box, threshold = 0.3f)
        Log.d(TAG, "Object is visible: $isVisible")

        if (isVisible) {
            // Transform detected box from ML Kit space to screen coordinates
            val screenRect = transformer.bitmapRectToScreen(box)

            // Calculate safe bottom boundary (leave 100dp space at bottom)
            val bottomSpacePx = with(density) { 100.dp.toPx() }
            val maxBottom = currentScreenHeight - bottomSpacePx

            // Clamp reticle to safe area (below app bar, above bottom space, within screen bounds)
            reticleLeft = screenRect.left.coerceIn(0f, currentScreenWidth)
            reticleTop = screenRect.top.coerceAtLeast(topInset).coerceAtMost(maxBottom)
            reticleRight = screenRect.right.coerceIn(0f, currentScreenWidth)
            reticleBottom = screenRect.bottom.coerceIn(topInset, maxBottom)

            Log.d(
                TAG,
                "Reticle positioned on object (clamped): [$reticleLeft,$reticleTop-$reticleRight,$reticleBottom]",
            )
        } else {
            // Object is mostly off-screen, use centered fallback
            positionReticleCentered(topInset, density)
        }
    } else {
        // No object detected during scanning, use centered fallback
        Log.d(TAG, "No detected object, using centered fallback")
        positionReticleCentered(topInset, density)
    }

    // Transition to READY and trigger OCR
    analysisPhase = AnalysisPhase.READY
    runOCROnReticle(medicationDetector)
}

/**
 * Position reticle as centered 300dp×300dp rectangle with safe area constraints
 */
fun CameraDetectionState.positionReticleCentered(topInset: Float, density: Density) {
    val reticleWidthPx = with(density) { 300.dp.toPx() }
    val reticleHeightPx = with(density) { 300.dp.toPx() }

    // Calculate safe bottom boundary (leave 100dp space at bottom)
    val bottomSpacePx = with(density) { 100.dp.toPx() }
    val maxBottom = currentScreenHeight - bottomSpacePx

    // Calculate available height for reticle
    val availableHeight = maxBottom - topInset
    val centeredTop = topInset + (availableHeight - reticleHeightPx) / 2f

    reticleLeft = ((currentScreenWidth - reticleWidthPx) / 2f).coerceAtLeast(0f)
    reticleTop = centeredTop.coerceAtLeast(topInset)
    reticleRight = (reticleLeft + reticleWidthPx).coerceAtMost(currentScreenWidth)
    reticleBottom = (reticleTop + reticleHeightPx).coerceAtMost(maxBottom)

    Log.d(
        TAG,
        "Reticle positioned centered (safe area): [$reticleLeft,$reticleTop-$reticleRight,$reticleBottom]",
    )
}

/**
 * Run OCR on current reticle area - updates analysisPhase based on results
 */
fun CameraDetectionState.runOCROnReticle(medicationDetector: MedicationDetector) {
    Log.d(TAG, "=== RUNNING OCR ON RETICLE ===")

    // Validate reticle dimensions
    val reticleWidth = reticleRight - reticleLeft
    val reticleHeight = reticleBottom - reticleTop

    if (reticleWidth <= 0f || reticleHeight <= 0f) {
        AppLogger.e(TAG, "Invalid reticle dimensions: $reticleWidth×$reticleHeight")
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
        return
    }

    val bitmap = frozenFullBitmap
    val transformer = coordinateTransformer

    if (bitmap == null || transformer == null) {
        AppLogger.e(TAG, "Cannot run OCR: missing bitmap or transformer")
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
        return
    }

    // Convert screen coordinates to sensor bitmap coordinates for cropping
    val cropRect = transformer.screenRectToSensorBitmap(
        reticleLeft,
        reticleTop,
        reticleRight,
        reticleBottom,
    )

    Log.d(
        TAG,
        "Screen rect [$reticleLeft,$reticleTop-$reticleRight,$reticleBottom] → Sensor bitmap rect $cropRect",
    )

    val cropWidth = cropRect.width()
    val cropHeight = cropRect.height()

    if (cropWidth <= 0 || cropHeight <= 0) {
        AppLogger.e(TAG, "Invalid crop dimensions: $cropWidth×$cropHeight")
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
        return
    }

    try {
        // Crop the bitmap
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropWidth,
            cropHeight,
        )

        Log.d(TAG, "Running OCR on cropped region: $cropWidth×$cropHeight")

        // Run OCR on cropped bitmap
        medicationDetector.recognizeText(croppedBitmap) { text, newWordCount ->
            extractedText = text
            wordCount = newWordCount

            Log.d(TAG, "OCR result: $newWordCount words")

            if (newWordCount < 4) {
                // Insufficient text detected
                analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
            } else {
                // Sufficient text, start grace period
                analysisPhase = AnalysisPhase.GRACE_PERIOD
                gracePeriodVersion++ // Restart the timer
            }
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error cropping bitmap: ${e.message}", e)
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
    }
}
