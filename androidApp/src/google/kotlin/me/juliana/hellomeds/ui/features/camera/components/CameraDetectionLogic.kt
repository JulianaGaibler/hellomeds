// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.graphics.Bitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.ml.detector.MedicationDetector
import me.juliana.hellomeds.util.camera.CoordinateTransformer

private const val TAG = "CameraDetectionLogic"

fun CameraDetectionState.resumeLiveCamera() {
    isCameraLive = true
    analysisPhase = null

    frozenFullBitmap = null
    frozenImageRotation = 0
    extractedText = ""
    detectionResult = null
    wordCount = 0

    reticleLeft = 0f
    reticleTop = 0f
    reticleRight = 0f
    reticleBottom = 0f
    coordinateTransformer = null

    objectState = ObjectState.NO_OBJECT
    detectedObjectBox = null
    hasDetectedObjectOnce = false
    framesWithoutObject = 0

    isUserDragging = false
    gracePeriodVersion = 0

    isTorchOn = wasTorchOnBeforeFreeze
}

fun CameraDetectionState.freezeCameraAndInitializeReticle(
    topInset: Float,
    density: Density,
    medicationDetector: MedicationDetector,
) {
    if (frozenFullBitmap == null) {
        AppLogger.e(TAG, "Cannot freeze: no frozen bitmap available")
        return
    }
    if (currentScreenWidth == 0f || currentScreenHeight == 0f) {
        AppLogger.e(TAG, "Cannot freeze: screen dimensions not available")
        return
    }

    isCameraLive = false
    analysisPhase = AnalysisPhase.INITIALIZING

    val bitmap = frozenFullBitmap!!

    val transformer = CoordinateTransformer(
        bitmapWidth = bitmap.width,
        bitmapHeight = bitmap.height,
        screenWidth = currentScreenWidth,
        screenHeight = currentScreenHeight,
        rotation = frozenImageRotation,
    )
    coordinateTransformer = transformer

    if (detectedObjectBox != null) {
        val box = detectedObjectBox!!
        val isVisible = transformer.isInVisibleArea(box, threshold = 0.3f)

        if (isVisible) {
            val screenRect = transformer.bitmapRectToScreen(box)

            val bottomSpacePx = with(density) { 100.dp.toPx() }
            val maxBottom = currentScreenHeight - bottomSpacePx

            reticleLeft = screenRect.left.coerceIn(0f, currentScreenWidth)
            reticleTop = screenRect.top.coerceAtLeast(topInset).coerceAtMost(maxBottom)
            reticleRight = screenRect.right.coerceIn(0f, currentScreenWidth)
            reticleBottom = screenRect.bottom.coerceIn(topInset, maxBottom)
        } else {
            positionReticleCentered(topInset, density)
        }
    } else {
        positionReticleCentered(topInset, density)
    }

    analysisPhase = AnalysisPhase.READY
    runOCROnReticle(medicationDetector)
}

fun CameraDetectionState.positionReticleCentered(topInset: Float, density: Density) {
    val reticleWidthPx = with(density) { 300.dp.toPx() }
    val reticleHeightPx = with(density) { 300.dp.toPx() }

    val bottomSpacePx = with(density) { 100.dp.toPx() }
    val maxBottom = currentScreenHeight - bottomSpacePx

    val availableHeight = maxBottom - topInset
    val centeredTop = topInset + (availableHeight - reticleHeightPx) / 2f

    reticleLeft = ((currentScreenWidth - reticleWidthPx) / 2f).coerceAtLeast(0f)
    reticleTop = centeredTop.coerceAtLeast(topInset)
    reticleRight = (reticleLeft + reticleWidthPx).coerceAtMost(currentScreenWidth)
    reticleBottom = (reticleTop + reticleHeightPx).coerceAtMost(maxBottom)
}

fun CameraDetectionState.runOCROnReticle(medicationDetector: MedicationDetector) {
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

    val cropRect = transformer.screenRectToSensorBitmap(
        reticleLeft,
        reticleTop,
        reticleRight,
        reticleBottom,
    )

    val cropWidth = cropRect.width()
    val cropHeight = cropRect.height()

    if (cropWidth <= 0 || cropHeight <= 0) {
        AppLogger.e(TAG, "Invalid crop dimensions: $cropWidth×$cropHeight")
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
        return
    }

    try {
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropWidth,
            cropHeight,
        )

        medicationDetector.recognizeText(croppedBitmap) { text, newWordCount ->
            extractedText = text
            wordCount = newWordCount

            if (newWordCount < 4) {
                analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
            } else {
                analysisPhase = AnalysisPhase.GRACE_PERIOD
                gracePeriodVersion++
            }
        }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Error cropping bitmap: ${e.message}", e)
        analysisPhase = AnalysisPhase.INSUFFICIENT_TEXT
    }
}
