// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_title
import me.juliana.hellomeds.shared.camera_flash_off
import me.juliana.hellomeds.shared.camera_flash_on
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.flashlight_off_24px
import me.juliana.hellomeds.shared.flashlight_on_24px
import me.juliana.hellomeds.ui.features.camera.components.CountdownOverlay
import me.juliana.hellomeds.ui.features.camera.components.CropReticleOverlay
import me.juliana.hellomeds.ui.features.camera.components.DetectionResultsSheet
import me.juliana.hellomeds.ui.features.camera.components.InstructionPill
import me.juliana.hellomeds.ui.features.camera.components.NoDataOverlay
import me.juliana.hellomeds.ui.features.camera.components.ProcessingIndicator
import me.juliana.hellomeds.ui.features.camera.components.ShutterButton
import me.juliana.hellomeds.ui.features.camera.components.StaticReticleOverlay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Phase of the camera detection flow (after consent/permission resolved).
 */
enum class CameraPhase {
    LIVE,
    FROZEN_COUNTDOWN,
    PROCESSING,
    RESULTS,
    NO_DATA,
}

/**
 * Shared camera detection layout used by both Android and iOS.
 * Handles all visual UI: top bar, shutter button, reticle overlays, countdown,
 * processing indicator, results bottom sheet, and no-data overlay.
 *
 * Platform-specific pieces (camera preview, OCR, permissions) are injected via parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraDetectionLayout(
    cameraPreview: @Composable () -> Unit,
    phase: CameraPhase,
    wordCount: Int,
    countdownSeconds: Int,
    isDragging: Boolean,
    // Crop reticle
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    cropInitialized: Boolean,
    cropTopInset: Float = 0f,
    onCropBoundsChanged: (Float, Float, Float, Float) -> Unit,
    onCropDragStart: () -> Unit,
    onCropDragEnd: () -> Unit,
    // Actions
    onNavigateBack: () -> Unit,
    onShutterPress: () -> Unit,
    onTryAgain: () -> Unit,
    onUseResult: (MedicationDetectionResult) -> Unit,
    // Flash
    hasTorch: Boolean,
    isTorchOn: Boolean,
    onTorchToggle: () -> Unit,
    // Results
    detectionResult: MedicationDetectionResult?,
    modifier: Modifier = Modifier,
) {
    // Animated UI values — driven by wordCount
    val reticleStrokeWidth = remember { Animatable(2f) }
    val reticleAlpha = remember { Animatable(0.5f) }
    val shutterBorderWidth = remember { Animatable(4f) }
    val shutterBorderColor = remember { Animatable(0f) }

    LaunchedEffect(wordCount) {
        if (wordCount >= 4) {
            launch { reticleStrokeWidth.animateTo(6f, animationSpec = tween(200)) }
            launch { reticleAlpha.animateTo(1f, animationSpec = tween(200)) }
            launch { shutterBorderColor.animateTo(1f, animationSpec = tween(200)) }
            launch {
                shutterBorderWidth.animateTo(
                    targetValue = 12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800),
                        repeatMode = RepeatMode.Reverse,
                    ),
                )
            }
        } else {
            launch { reticleStrokeWidth.animateTo(2f, animationSpec = tween(200)) }
            launch { reticleAlpha.animateTo(0.5f, animationSpec = tween(200)) }
            launch { shutterBorderWidth.animateTo(4f, animationSpec = tween(200)) }
            launch { shutterBorderColor.animateTo(0f, animationSpec = tween(200)) }
        }
    }

    // Bottom sheet state
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
        ),
    )

    // Show/hide bottom sheet based on results
    LaunchedEffect(detectionResult) {
        if (detectionResult != null) {
            bottomSheetScaffoldState.bottomSheetState.partialExpand()
        } else {
            bottomSheetScaffoldState.bottomSheetState.hide()
        }
    }

    // Prevent dismissal when results are showing
    LaunchedEffect(bottomSheetScaffoldState.bottomSheetState.currentValue, detectionResult) {
        if (detectionResult != null &&
            bottomSheetScaffoldState.bottomSheetState.currentValue == SheetValue.Hidden
        ) {
            bottomSheetScaffoldState.bottomSheetState.partialExpand()
        }
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        sheetContent = {
            DetectionResultsSheet(
                detectionResult = detectionResult,
                onTryAgain = onTryAgain,
                onUseThis = onUseResult,
            )
        },
        sheetPeekHeight = 192.dp,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 1: Camera preview (platform-specific)
            cameraPreview()

            // Layer 2: Static reticle (live scanning only)
            if (phase == CameraPhase.LIVE) {
                StaticReticleOverlay(
                    strokeWidth = reticleStrokeWidth.value,
                    alpha = reticleAlpha.value,
                )
            }

            // Layer 3: Crop reticle (all frozen states)
            val showCrop = phase != CameraPhase.LIVE && cropInitialized
            if (showCrop) {
                CropReticleOverlay(
                    initialLeft = cropLeft,
                    initialTop = cropTop,
                    initialRight = cropRight,
                    initialBottom = cropBottom,
                    isEditable = phase == CameraPhase.FROZEN_COUNTDOWN || phase == CameraPhase.RESULTS,
                    topInset = cropTopInset,
                    onBoundsChanged = onCropBoundsChanged,
                    onDragStart = onCropDragStart,
                    onDragEnd = onCropDragEnd,
                )
            }

            // Layer 4: Countdown (bottom-center, hidden while dragging)
            if (phase == CameraPhase.FROZEN_COUNTDOWN && !isDragging) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    CountdownOverlay(secondsRemaining = countdownSeconds)
                }
            }

            // Layer 5: Top bar
            TopAppBar(
                title = { Text(stringResource(Res.string.camera_detection_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    if (hasTorch) {
                        IconButton(
                            onClick = onTorchToggle,
                            enabled = phase == CameraPhase.LIVE,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isTorchOn) {
                                        Res.drawable.flashlight_on_24px
                                    } else {
                                        Res.drawable.flashlight_off_24px
                                    },
                                ),
                                contentDescription = stringResource(
                                    if (isTorchOn) {
                                        Res.string.camera_flash_on
                                    } else {
                                        Res.string.camera_flash_off
                                    },
                                ),
                                tint = if (phase == CameraPhase.LIVE) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )

            // Layer 6: Bottom UI (shutter + instruction when live, spinner when processing)
            when (phase) {
                CameraPhase.LIVE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .systemBarsPadding()
                            .padding(bottom = 48.dp),
                    ) {
                        ShutterButton(
                            shutterBorderWidth = shutterBorderWidth.value,
                            shutterBorderColor = shutterBorderColor.value,
                            onClick = onShutterPress,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        InstructionPill(wordCount = wordCount)
                    }
                }

                CameraPhase.PROCESSING -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(bottom = 48.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        ProcessingIndicator()
                    }
                }

                else -> {
                    /* No bottom UI for other phases */
                }
            }
        }
    }

    // No-data overlay (on top of scaffold)
    if (phase == CameraPhase.NO_DATA) {
        NoDataOverlay(onTryAgain = onTryAgain)
    }
}
