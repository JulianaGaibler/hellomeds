// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera

import android.Manifest
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ml.detector.MedicationDetector
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_permission_required
import me.juliana.hellomeds.ui.features.camera.components.AnalysisPhase
import me.juliana.hellomeds.ui.features.camera.components.CameraDetectionState
import me.juliana.hellomeds.ui.features.camera.components.CameraPreview
import me.juliana.hellomeds.ui.features.camera.components.ObjectState
import me.juliana.hellomeds.ui.features.camera.components.freezeCameraAndInitializeReticle
import me.juliana.hellomeds.ui.features.camera.components.resumeLiveCamera
import me.juliana.hellomeds.ui.features.camera.components.runOCROnReticle
import me.juliana.hellomeds.util.camera.CoordinateTransformer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.util.concurrent.Executors

private const val TAG = "CameraDetectionScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetectionScreen(
    onNavigateBack: () -> Unit,
    onDetectionComplete: (MedicationDetectionResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current

    val medicationDetector = remember { MedicationDetector(context) }

    // Camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PermissionChecker.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted -> hasCameraPermission = isGranted }

    val topInset = with(density) {
        WindowInsets.systemBars.getTop(density).toDp().value.dp.toPx()
    }

    val state = remember { CameraDetectionState() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraPreferences = koinInject<CameraPreferences>()
    val detectionMethod by cameraPreferences.detectionMethod.collectAsState(initial = DetectionMethod.GEMINI)

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    // Back gesture
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            if (!state.isCameraLive) state.resumeLiveCamera() else onNavigateBack()
        } catch (_: CancellationException) {
        }
    }

    // Request camera permission
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Remap reticle on screen size change while frozen
    LaunchedEffect(state.currentScreenWidth, state.currentScreenHeight) {
        if (!state.isCameraLive && state.coordinateTransformer != null &&
            state.currentScreenWidth > 0f && state.currentScreenHeight > 0f
        ) {
            val oldTransformer = state.coordinateTransformer!!
            if (oldTransformer.screenWidth != state.currentScreenWidth ||
                oldTransformer.screenHeight != state.currentScreenHeight
            ) {
                val bitmapRect = oldTransformer.screenRectToBitmap(
                    state.reticleLeft,
                    state.reticleTop,
                    state.reticleRight,
                    state.reticleBottom,
                )
                val newTransformer = CoordinateTransformer(
                    bitmapWidth = state.frozenFullBitmap!!.width,
                    bitmapHeight = state.frozenFullBitmap!!.height,
                    screenWidth = state.currentScreenWidth,
                    screenHeight = state.currentScreenHeight,
                    rotation = state.frozenImageRotation,
                )
                state.coordinateTransformer = newTransformer
                val newScreenRect = newTransformer.bitmapRectToScreen(bitmapRect)
                state.reticleLeft = newScreenRect.left.coerceIn(0f, state.currentScreenWidth)
                state.reticleTop =
                    newScreenRect.top.coerceAtLeast(topInset).coerceAtMost(state.currentScreenHeight)
                state.reticleRight = newScreenRect.right.coerceIn(0f, state.currentScreenWidth)
                state.reticleBottom = newScreenRect.bottom.coerceIn(topInset, state.currentScreenHeight)
            }
        }
    }

    // Haptic feedback on object detection
    var previousObjectState by remember { mutableStateOf<ObjectState?>(null) }
    LaunchedEffect(state.objectState) {
        if (state.objectState == ObjectState.OBJECT_WITH_TEXT && previousObjectState != ObjectState.OBJECT_WITH_TEXT) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        previousObjectState = state.objectState
    }

    // Gemini warmup
    LaunchedEffect(state.isCameraLive) {
        if (!state.isCameraLive) {
            try {
                medicationDetector.warmupGemini()
            } catch (_: Exception) {
            }
        }
    }

    // Countdown + Gemini analysis
    var countdownSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.analysisPhase, state.gracePeriodVersion, state.isUserDragging) {
        if (state.analysisPhase == AnalysisPhase.GRACE_PERIOD && !state.isUserDragging) {
            countdownSeconds = 0
            delay(500)
            countdownSeconds = 3
            delay(500)
            countdownSeconds = 2
            delay(500)
            countdownSeconds = 1
            delay(500)
            countdownSeconds = 0
            if (state.analysisPhase == AnalysisPhase.GRACE_PERIOD) {
                state.analysisPhase = AnalysisPhase.PROCESSING
                scope.launch {
                    try {
                        val transformer = state.coordinateTransformer
                        val bitmap = state.frozenFullBitmap
                        if (transformer != null && bitmap != null) {
                            val boundingBox = transformer.screenRectToSensorBitmap(
                                state.reticleLeft,
                                state.reticleTop,
                                state.reticleRight,
                                state.reticleBottom,
                            )
                            val result = medicationDetector.analyzeFullMedication(
                                bitmap = bitmap,
                                boundingBox = boundingBox,
                                extractedText = state.extractedText,
                                useGemini = detectionMethod == DetectionMethod.GEMINI,
                            )
                            val hasValidData = result.nameSuggestions.isNotEmpty() ||
                                result.typeSuggestions.isNotEmpty() ||
                                result.strengthSuggestion != null
                            if (hasValidData) {
                                state.detectionResult = result
                                state.analysisPhase = AnalysisPhase.COMPLETE
                            } else {
                                state.analysisPhase = AnalysisPhase.NO_DATA
                            }
                        } else {
                            state.analysisPhase = AnalysisPhase.NO_DATA
                        }
                    } catch (_: Exception) {
                        state.analysisPhase = AnalysisPhase.NO_DATA
                    }
                }
            }
        }
    }

    // Map Android analysis phase to shared CameraPhase
    val phase = when {
        state.isCameraLive -> CameraPhase.LIVE
        state.analysisPhase == AnalysisPhase.GRACE_PERIOD -> CameraPhase.FROZEN_COUNTDOWN
        state.analysisPhase == AnalysisPhase.PROCESSING -> CameraPhase.PROCESSING
        state.analysisPhase == AnalysisPhase.COMPLETE -> CameraPhase.RESULTS
        state.analysisPhase == AnalysisPhase.NO_DATA -> CameraPhase.NO_DATA
        else -> CameraPhase.FROZEN_COUNTDOWN // INITIALIZING, READY, INSUFFICIENT_TEXT
    }

    if (hasCameraPermission) {
        CameraDetectionLayout(
            cameraPreview = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            val size = coordinates.size
                            if (state.currentScreenWidth != size.width.toFloat() ||
                                state.currentScreenHeight != size.height.toFloat()
                            ) {
                                state.currentScreenWidth = size.width.toFloat()
                                state.currentScreenHeight = size.height.toFloat()
                            }
                        },
                ) {
                    CameraPreview(
                        context = context,
                        lifecycleOwner = lifecycleOwner,
                        cameraExecutor = cameraExecutor,
                        isCameraLive = state.isCameraLive,
                        isFrozen = !state.isCameraLive,
                        torchEnabled = state.isTorchOn && state.isCameraLive,
                        onObjectDetected = { result, _, _, rotation ->
                            if (state.isCameraLive) {
                                state.detectedObjectBox = result.boundingBox
                                state.frozenImageRotation = rotation
                                state.hasDetectedObjectOnce = true
                                state.framesWithoutObject = 0
                            }
                        },
                        onNoObject = {
                            if (state.isCameraLive) {
                                state.framesWithoutObject++
                                if (state.framesWithoutObject > state.maxFramesWithoutObject && state.hasDetectedObjectOnce) {
                                    state.objectState = ObjectState.NO_OBJECT
                                    state.detectedObjectBox = null
                                    state.hasDetectedObjectOnce = false
                                    state.framesWithoutObject = 0
                                }
                            }
                        },
                        onTextRecognized = { text, wordCount ->
                            if (state.isCameraLive) {
                                state.extractedText = text
                                state.wordCount = wordCount
                                if (state.hasDetectedObjectOnce) {
                                    state.objectState = when {
                                        wordCount >= 4 -> ObjectState.OBJECT_WITH_TEXT
                                        wordCount > 0 -> ObjectState.OBJECT_NO_TEXT
                                        else -> ObjectState.NO_OBJECT
                                    }
                                }
                            }
                        },
                        onFrameCaptured = { bitmap -> state.frozenFullBitmap = bitmap },
                    )
                }
            },
            phase = phase,
            wordCount = state.wordCount,
            countdownSeconds = countdownSeconds,
            isDragging = state.isUserDragging,
            cropLeft = state.reticleLeft,
            cropTop = state.reticleTop,
            cropRight = state.reticleRight,
            cropBottom = state.reticleBottom,
            cropInitialized = state.analysisPhase != null,
            cropTopInset = topInset,
            onCropBoundsChanged = { l, t, r, b ->
                state.reticleLeft = l
                state.reticleTop = t
                state.reticleRight = r
                state.reticleBottom = b
            },
            onCropDragStart = {
                state.isUserDragging = true
                // If results are showing, clear them to restart the scan cycle
                if (state.analysisPhase == AnalysisPhase.COMPLETE) {
                    state.detectionResult = null
                    state.analysisPhase = AnalysisPhase.GRACE_PERIOD
                }
            },
            onCropDragEnd = {
                state.isUserDragging = false
                state.gracePeriodVersion++
                // Re-run OCR on the new crop area
                state.runOCROnReticle(medicationDetector)
            },
            onNavigateBack = {
                if (!state.isCameraLive) state.resumeLiveCamera() else onNavigateBack()
            },
            onShutterPress = {
                state.wasTorchOnBeforeFreeze = state.isTorchOn
                state.isTorchOn = false
                state.freezeCameraAndInitializeReticle(topInset, density, medicationDetector)
            },
            onTryAgain = { state.resumeLiveCamera() },
            onUseResult = onDetectionComplete,
            hasTorch = true,
            isTorchOn = state.isTorchOn,
            onTorchToggle = { state.isTorchOn = !state.isTorchOn },
            detectionResult = state.detectionResult,
            modifier = modifier,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.camera_detection_permission_required),
                color = Color.White,
            )
        }
    }
}
