// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import me.juliana.hellomeds.data.util.AppLogger
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import me.juliana.hellomeds.ml.detector.MedicationDetector
import java.util.concurrent.ExecutorService

private const val TAG = "CameraPreview"

/**
 * Camera preview composable that handles CameraX setup and image analysis.
 *
 * @param isCameraLive Whether the camera analyzer should process frames
 * @param isFrozen Whether to freeze the preview (stop showing live feed)
 * @param torchEnabled Whether the camera flash/torch should be enabled
 * @param onObjectDetected Callback when object detection finds something
 * @param onNoObject Callback when object detection finds nothing
 * @param onTextRecognized Callback when OCR completes on detected object
 * @param onFrameCaptured Callback for every analyzed frame (for freezing)
 */
@Composable
fun CameraPreview(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    isCameraLive: Boolean,
    isFrozen: Boolean,
    torchEnabled: Boolean = false,
    onObjectDetected: (MedicationDetector.ObjectDetectionResult, Int, Int, Int) -> Unit,
    onNoObject: () -> Unit,
    onTextRecognized: (String, Int) -> Unit,
    onFrameCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewView = remember { PreviewView(context) }
    val medicationDetector = remember { MedicationDetector(context) }

    // Keep reference to preview for freezing/unfreezing
    var preview by remember { mutableStateOf<Preview?>(null) }

    // Keep reference to camera for torch control
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    // Make isCameraLive reactive so the analyzer can check it dynamically
    var isActiveState by remember { mutableStateOf(isCameraLive) }

    // Update isActiveState when isCameraLive parameter changes
    LaunchedEffect(isCameraLive) {
        Log.d(TAG, "Camera active state changed: isCameraLive=$isCameraLive")
        isActiveState = isCameraLive
    }

    // Handle freeze/unfreeze
    LaunchedEffect(isFrozen) {
        Log.d(TAG, "Camera freeze state changed: isFrozen=$isFrozen")
        if (isFrozen) {
            preview?.surfaceProvider = null
        } else {
            preview?.surfaceProvider = previewView.surfaceProvider
        }
    }

    // Handle torch/flash control
    LaunchedEffect(torchEnabled, camera) {
        camera?.let {
            try {
                Log.d(TAG, "Setting torch: torchEnabled=$torchEnabled")
                it.cameraControl.enableTorch(torchEnabled)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to set torch state", e)
            }
        }
    }

    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Preview use case
                preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                // Image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                if (isActiveState) {
                                    val imageWidth = imageProxy.width
                                    val imageHeight = imageProxy.height
                                    val rotation = imageProxy.imageInfo.rotationDegrees

                                    val bitmap = imageProxy.toBitmap()

                                    // Captured regardless of detection result so frozenFullBitmap is
                                    // available even when no object is detected.
                                    onFrameCaptured(bitmap)

                                    medicationDetector.detectObject(
                                        bitmap = bitmap,
                                        rotation = rotation,
                                        onObjectDetected = { objectResult ->
                                            onObjectDetected(objectResult, imageWidth, imageHeight, rotation)

                                            medicationDetector.recognizeText(
                                                bitmap = objectResult.croppedBitmap,
                                                onTextRecognized = onTextRecognized,
                                            )
                                        },
                                        onNoObject = onNoObject,
                                    )
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                // Camera selector (back camera)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera and store camera reference for torch control
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )
}
