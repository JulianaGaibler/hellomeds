// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package me.juliana.hellomeds.ui.features.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.domain.ml.MedicationIntelligenceEngine
import me.juliana.hellomeds.ml.isFoundationModelAvailable
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.camera_detection_permission_required
import me.juliana.hellomeds.ui.components.CameraConsentDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionDidStartRunningNotification
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSinceDate
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIWindowScene
import platform.Vision.VNDetectRectanglesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRectangleObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

private const val TAG = "IOSCameraDetectionScreen"

/** Normalized rectangle (0-1 range, Vision coordinate system: origin at bottom-left) */
data class NormalizedRect(val x: Float, val y: Float, val width: Float, val height: Float)

/** Screen pixel rectangle (top-left origin) */
private data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Convert Vision normalized rect (0-1, bottom-left origin) to screen pixels (top-left origin).
 * Accounts for AVLayerVideoGravityResizeAspectFill crop/scale, matching Android's CoordinateTransformer.
 */
private fun visionToScreen(
    rect: NormalizedRect,
    sensorW: Float,
    sensorH: Float,
    screenW: Float,
    screenH: Float,
    isPortrait: Boolean,
): ScreenRect {
    val orientedW = if (isPortrait) sensorH else sensorW
    val orientedH = if (isPortrait) sensorW else sensorH
    val scale = maxOf(screenW / orientedW, screenH / orientedH)
    val offsetX = (screenW - orientedW * scale) / 2f
    val offsetY = (screenH - orientedH * scale) / 2f

    val left = rect.x * orientedW * scale + offsetX
    val top = (1f - rect.y - rect.height) * orientedH * scale + offsetY
    val width = rect.width * orientedW * scale
    val height = rect.height * orientedH * scale
    return ScreenRect(left, top, left + width, top + height)
}

/**
 * Reverse: screen pixel rect -> Vision normalized rect (for regionOfInterest).
 */
private fun screenToVision(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    sensorW: Float,
    sensorH: Float,
    screenW: Float,
    screenH: Float,
    isPortrait: Boolean,
): NormalizedRect {
    val orientedW = if (isPortrait) sensorH else sensorW
    val orientedH = if (isPortrait) sensorW else sensorH
    val scale = maxOf(screenW / orientedW, screenH / orientedH)
    val offsetX = (screenW - orientedW * scale) / 2f
    val offsetY = (screenH - orientedH * scale) / 2f

    val vX = (left - offsetX) / (orientedW * scale)
    val vW = (right - left) / (orientedW * scale)
    val vH = (bottom - top) / (orientedH * scale)
    val vY = 1f - (top - offsetY) / (orientedH * scale) - vH
    return NormalizedRect(
        x = vX.coerceIn(0f, 1f),
        y = vY.coerceIn(0f, 1f),
        width = vW.coerceIn(0f, 1f - vX.coerceIn(0f, 1f)),
        height = vH.coerceIn(0f, 1f - vY.coerceIn(0f, 1f)),
    )
}

/** Whether the current interface orientation is portrait (swaps sensor W/H). */
private fun isCurrentOrientationPortrait(): Boolean {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull()
    val windowScene = scene as? UIWindowScene
    val orientation = windowScene?.interfaceOrientation ?: 1L
    return orientation == 1L || orientation == 2L // Portrait or PortraitUpsideDown
}

/**
 * Flow states for the iOS camera detection entry screen.
 */
private enum class IOSCameraEntryState {
    LOADING,
    SHOW_CONSENT_DIALOG,
    REQUESTING_PERMISSION,
    PERMISSION_DENIED,
    CAMERA_ACTIVE,
    FROZEN_GRACE_PERIOD,
    PROCESSING,
    SHOW_RESULTS,
    NO_DATA,
}

/**
 * iOS camera detection screen that provides:
 * 1. Consent dialog (shared BasicCameraConsentDialog)
 * 2. iOS camera permission request via AVCaptureDevice
 * 3. Live camera preview with AVCaptureSession
 * 4. Vision framework OCR on video frames
 * 5. MedicationIntelligenceEngine processing
 * 6. Result display with Use/Retry actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSCameraDetectionScreen(
    onNavigateBack: () -> Unit,
    onDetectionComplete: (MedicationDetectionResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val cameraPreferences = koinInject<CameraPreferences>()
    val intelligenceEngine = koinInject<MedicationIntelligenceEngine>()

    // Consent state
    val hasConsented by cameraPreferences.hasConsented.collectAsState(initial = null)
    val hasShownDialog by cameraPreferences.hasShownDialog.collectAsState(initial = null)

    // Flow state
    var flowState by remember { mutableStateOf(IOSCameraEntryState.LOADING) }

    // Camera/OCR state
    var extractedText by remember { mutableStateOf("") }
    var wordCount by remember { mutableStateOf(0) }
    var detectionResult by remember { mutableStateOf<MedicationDetectionResult?>(null) }
    var countdownSeconds by remember { mutableStateOf(0) }
    var gracePeriodVersion by remember { mutableStateOf(0) }

    // Screen dimensions (updated by onSizeChanged)
    var screenWidth by remember { mutableStateOf(800f) }
    var screenHeight by remember { mutableStateOf(1200f) }

    // Camera sensor frame dimensions (from CVPixelBuffer — raw sensor orientation)
    var sensorWidth by remember { mutableStateOf(0f) }
    var sensorHeight by remember { mutableStateOf(0f) }

    // Detected rectangles from Vision (normalized 0-1, bottom-left origin)
    var detectedRectsFromCamera by remember { mutableStateOf<List<NormalizedRect>>(emptyList()) }

    // OCR delegate — hoisted here so we can access getLastPixelBuffer() for region OCR
    val ocrDelegate = remember {
        IOSOCROutputDelegate(
            onTextResult = { text, words ->
                extractedText = text
                wordCount = words
            },
            onRectsDetected = { rects -> detectedRectsFromCamera = rects },
            onFrameDimensions = { w, h ->
                sensorWidth = w
                sensorHeight = h
            },
        )
    }

    // Crop reticle bounds (screen pixels)
    var cropLeft by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }
    var cropInitialized by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Countdown timer — pauses while user is dragging, resets on drag end
    if (flowState == IOSCameraEntryState.FROZEN_GRACE_PERIOD && !isDragging) {
        LaunchedEffect(flowState, gracePeriodVersion) {
            countdownSeconds = 0 // hidden — brief pause before countdown
            kotlinx.coroutines.delay(500)
            countdownSeconds = 3
            kotlinx.coroutines.delay(500)
            countdownSeconds = 2
            kotlinx.coroutines.delay(500)
            countdownSeconds = 1
            kotlinx.coroutines.delay(500)
            countdownSeconds = 0
            // Countdown finished — re-run OCR on just the crop region, then process
            flowState = IOSCameraEntryState.PROCESSING

            // Convert screen crop bounds to Vision normalized coords
            val regionRect = if (sensorWidth > 0f) {
                screenToVision(
                    cropLeft, cropTop, cropRight, cropBottom,
                    sensorWidth, sensorHeight, screenWidth, screenHeight,
                    isCurrentOrientationPortrait(),
                )
            } else {
                null
            }

            val lastBuffer = ocrDelegate.getLastPixelBuffer()
            if (regionRect != null && lastBuffer != null) {
                // Run OCR on just the cropped region
                val ocrQueue = dispatch_queue_create("me.juliana.hellomeds.regionocr", null)
                dispatch_async(ocrQueue) {
                    runRegionOCR(lastBuffer, regionRect) { regionText, regionWords ->
                        val textForAnalysis = if (regionWords >= 2) regionText else extractedText
                        AppLogger.d(
                            TAG,
                            "Region OCR: $regionWords words, using ${if (regionWords >= 2) "region" else "full"} text",
                        )
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                intelligenceEngine.guessMedicationDetails(textForAnalysis)
                            }
                            detectionResult = result
                            flowState = if (result != null && result.nameSuggestions.isNotEmpty()) {
                                IOSCameraEntryState.SHOW_RESULTS
                            } else {
                                IOSCameraEntryState.NO_DATA
                            }
                        }
                    }
                }
            } else {
                // Fallback: use full-frame live OCR text
                AppLogger.d(TAG, "No pixel buffer for region OCR, using live text")
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        intelligenceEngine.guessMedicationDetails(extractedText)
                    }
                    detectionResult = result
                    flowState = if (result != null && result.nameSuggestions.isNotEmpty()) {
                        IOSCameraEntryState.SHOW_RESULTS
                    } else {
                        IOSCameraEntryState.NO_DATA
                    }
                }
            }
        }
    }

    // Determine initial flow state
    LaunchedEffect(hasConsented, hasShownDialog) {
        if (hasConsented == null || hasShownDialog == null) {
            flowState = IOSCameraEntryState.LOADING
            return@LaunchedEffect
        }

        when {
            hasConsented == false || hasShownDialog == false -> {
                flowState = IOSCameraEntryState.SHOW_CONSENT_DIALOG
            }

            else -> {
                // Already consented, check camera permission
                flowState = checkCameraPermissionState()
            }
        }
    }

    when (flowState) {
        IOSCameraEntryState.LOADING -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        IOSCameraEntryState.SHOW_CONSENT_DIALOG -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            )

            CameraConsentDialog(
                isApplePlatform = true,
                isAiAvailable = isFoundationModelAvailable(),
                onContinue = { method ->
                    scope.launch {
                        cameraPreferences.setConsent(true)
                        cameraPreferences.setDetectionMethod(method)
                        cameraPreferences.markDialogShown()
                        flowState = checkCameraPermissionState()
                    }
                },
                onCancel = {
                    onNavigateBack()
                },
            )
        }

        IOSCameraEntryState.REQUESTING_PERMISSION -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = stringResource(Res.string.camera_detection_permission_required),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // Request permission
            LaunchedEffect(Unit) {
                requestCameraPermission { granted ->
                    flowState = if (granted) {
                        IOSCameraEntryState.CAMERA_ACTIVE
                    } else {
                        IOSCameraEntryState.PERMISSION_DENIED
                    }
                }
            }
        }

        IOSCameraEntryState.PERMISSION_DENIED -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.camera_detection_permission_required),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onNavigateBack) {
                        Text(stringResource(Res.string.action_back))
                    }
                }
            }
        }

        IOSCameraEntryState.CAMERA_ACTIVE,
        IOSCameraEntryState.FROZEN_GRACE_PERIOD,
        IOSCameraEntryState.PROCESSING,
        IOSCameraEntryState.SHOW_RESULTS,
        IOSCameraEntryState.NO_DATA,
        -> {
            // Map iOS flow state to shared CameraPhase
            val phase = when (flowState) {
                IOSCameraEntryState.CAMERA_ACTIVE -> CameraPhase.LIVE
                IOSCameraEntryState.FROZEN_GRACE_PERIOD -> CameraPhase.FROZEN_COUNTDOWN
                IOSCameraEntryState.PROCESSING -> CameraPhase.PROCESSING
                IOSCameraEntryState.SHOW_RESULTS -> CameraPhase.RESULTS
                IOSCameraEntryState.NO_DATA -> CameraPhase.NO_DATA
                else -> CameraPhase.LIVE
            }

            // Camera setup (AVFoundation)
            val captureSession = remember { AVCaptureSession() }
            var isTorchOn by remember { mutableStateOf(false) }
            var hasTorchState by remember { mutableStateOf(false) }
            val cameraQueue =
                remember { dispatch_queue_create("me.juliana.hellomeds.camera.start", null) }

            LaunchedEffect(Unit) {
                hasTorchState = setupCaptureSession(captureSession, ocrDelegate)
            }

            DisposableEffect(Unit) {
                dispatch_async(cameraQueue) { captureSession.startRunning() }
                onDispose { dispatch_async(cameraQueue) { captureSession.stopRunning() } }
            }

            val isFrozen = flowState != IOSCameraEntryState.CAMERA_ACTIVE
            LaunchedEffect(isFrozen) {
                if (isFrozen) {
                    dispatch_async(cameraQueue) { captureSession.stopRunning() }
                } else {
                    dispatch_async(cameraQueue) { captureSession.startRunning() }
                }
            }

            LaunchedEffect(isTorchOn) { setTorchMode(isTorchOn) }

            // Shared layout handles all UI
            CameraDetectionLayout(
                cameraPreview = {
                    @Suppress("DEPRECATION")
                    UIKitView(
                        factory = { CameraPreviewUIView(session = captureSession) },
                        modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                            screenWidth = size.width.toFloat()
                            screenHeight = size.height.toFloat()
                        },
                    )
                },
                phase = phase,
                wordCount = wordCount,
                countdownSeconds = countdownSeconds,
                isDragging = isDragging,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropRight = cropRight,
                cropBottom = cropBottom,
                cropInitialized = cropInitialized,
                onCropBoundsChanged = { l, t, r, b ->
                    cropLeft = l
                    cropTop = t
                    cropRight = r
                    cropBottom = b
                },
                onCropDragStart = {
                    isDragging = true
                    // If results are showing, clear them to restart the scan cycle
                    if (flowState == IOSCameraEntryState.SHOW_RESULTS) {
                        detectionResult = null
                        flowState = IOSCameraEntryState.FROZEN_GRACE_PERIOD
                    }
                },
                onCropDragEnd = {
                    isDragging = false
                    gracePeriodVersion++
                },
                onNavigateBack = {
                    if (flowState == IOSCameraEntryState.CAMERA_ACTIVE) {
                        onNavigateBack()
                    } else {
                        flowState = IOSCameraEntryState.CAMERA_ACTIVE
                        countdownSeconds = 0
                        extractedText = ""
                        wordCount = 0
                        detectionResult = null
                        cropInitialized = false
                    }
                },
                onShutterPress = {
                    // Turn off torch before analyzing
                    isTorchOn = false
                    // Score rects by center proximity + size
                    val bestRect = if (sensorWidth > 0f) {
                        detectedRectsFromCamera
                            .filter { it.width > 0.05f && it.height > 0.05f }
                            .maxByOrNull { rect ->
                                val area = rect.width * rect.height
                                val cx = rect.x + rect.width / 2f
                                val cy = rect.y + rect.height / 2f
                                val dist = kotlin.math.sqrt((cx - 0.5f) * (cx - 0.5f) + (cy - 0.5f) * (cy - 0.5f))
                                val centerScore = 1f - dist.coerceIn(0f, 1f)
                                area * 0.7f + centerScore * 0.3f
                            }
                    } else {
                        null
                    }

                    if (bestRect != null && sensorWidth > 0f) {
                        val sr = visionToScreen(
                            bestRect,
                            sensorWidth,
                            sensorHeight,
                            screenWidth,
                            screenHeight,
                            isCurrentOrientationPortrait(),
                        )
                        cropLeft = sr.left.coerceIn(0f, screenWidth)
                        cropTop = sr.top.coerceIn(0f, screenHeight)
                        cropRight = sr.right.coerceIn(0f, screenWidth)
                        cropBottom = sr.bottom.coerceIn(0f, screenHeight)
                    } else {
                        val cropSize = 600f
                        cropLeft = (screenWidth - cropSize) / 2f
                        cropTop = (screenHeight - cropSize) / 2f
                        cropRight = cropLeft + cropSize
                        cropBottom = cropTop + cropSize
                    }
                    cropInitialized = true
                    gracePeriodVersion++
                    flowState = IOSCameraEntryState.FROZEN_GRACE_PERIOD
                },
                onTryAgain = {
                    extractedText = ""
                    wordCount = 0
                    detectionResult = null
                    cropInitialized = false
                    flowState = IOSCameraEntryState.CAMERA_ACTIVE
                },
                onUseResult = { result -> onDetectionComplete(result) },
                hasTorch = hasTorchState,
                isTorchOn = isTorchOn,
                onTorchToggle = { isTorchOn = !isTorchOn },
                detectionResult = detectionResult,
                modifier = modifier,
            )
        }
    }
}

// iOS-specific camera and OCR helpers
// =============================================================================

/**
 * Checks the current iOS camera authorization status and returns the
 * appropriate flow state.
 */
private fun checkCameraPermissionState(): IOSCameraEntryState {
    return when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusAuthorized -> IOSCameraEntryState.CAMERA_ACTIVE
        AVAuthorizationStatusNotDetermined -> IOSCameraEntryState.REQUESTING_PERMISSION
        AVAuthorizationStatusDenied,
        AVAuthorizationStatusRestricted,
        -> IOSCameraEntryState.PERMISSION_DENIED

        else -> IOSCameraEntryState.REQUESTING_PERMISSION
    }
}

/**
 * Requests camera permission from the user.
 */
private fun requestCameraPermission(onResult: (Boolean) -> Unit) {
    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
        dispatch_async(dispatch_get_main_queue()) {
            onResult(granted)
        }
    }
}

/**
 * Sets up the AVCaptureSession with video input and output.
 * Returns true if the camera device has a torch (flash).
 */
private fun setupCaptureSession(session: AVCaptureSession, ocrDelegate: IOSOCROutputDelegate): Boolean {
    session.beginConfiguration()
    session.sessionPreset = AVCaptureSessionPresetHigh

    var deviceHasTorch = false

    // Add camera input
    val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    if (camera != null) {
        deviceHasTorch = camera.hasTorch
        val input = try {
            AVCaptureDeviceInput(device = camera, error = null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create camera input: ${e.message}")
            session.commitConfiguration()
            return false
        }

        if (session.canAddInput(input)) {
            session.addInput(input)
        }
    }

    // Add video output for OCR processing
    val videoOutput = AVCaptureVideoDataOutput()
    val processingQueue = dispatch_queue_create("me.juliana.hellomeds.ocr", null)
    videoOutput.setSampleBufferDelegate(ocrDelegate, processingQueue)
    videoOutput.alwaysDiscardsLateVideoFrames = true

    if (session.canAddOutput(videoOutput)) {
        session.addOutput(videoOutput)
    }

    session.commitConfiguration()
    return deviceHasTorch
}

/**
 * Sets the torch (flashlight) mode on the default camera device.
 */
private fun setTorchMode(on: Boolean) {
    val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
    if (!camera.hasTorch) return

    try {
        camera.lockForConfiguration(null)
        camera.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff
        camera.unlockForConfiguration()
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to set torch mode: ${e.message}")
    }
}

/**
 * Custom UIView subclass that hosts an AVCaptureVideoPreviewLayer.
 * Overrides layoutSubviews() to keep the preview layer frame in sync with the view bounds.
 *
 * This is REQUIRED because CMP 1.10's UIKitView does not support onResize().
 * The correct mechanism is to override layoutSubviews() in a UIView subclass.
 * See: https://kotlinlang.org/docs/multiplatform/compose-uikit-integration.html
 */
private class CameraPreviewUIView(
    session: AVCaptureSession,
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)
    private var sessionObserver: Any? = null

    init {
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(previewLayer)

        // Set orientation when session starts (connection is nil until then)
        sessionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVCaptureSessionDidStartRunningNotification,
            `object` = session,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            updateOrientation()
        }
    }

    override fun removeFromSuperview() {
        sessionObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        sessionObserver = null
        super.removeFromSuperview()
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer.setFrame(bounds)
        updateOrientation()
        CATransaction.commit()
    }

    private fun updateOrientation() {
        previewLayer.connection?.let { connection ->
            val angle = currentVideoRotationAngle()
            if (connection.isVideoRotationAngleSupported(angle)) {
                connection.videoRotationAngle = angle
            }
        }
    }

    private fun currentVideoRotationAngle(): Double {
        // Use interface orientation (from the window scene), NOT device orientation.
        // UIDevice.orientation returns FaceUp/FaceDown when iPad is flat on a table,
        // which doesn't tell us the actual screen rotation.
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull()
        val windowScene = scene as? UIWindowScene
        val interfaceOrientation = windowScene?.interfaceOrientation ?: 1L // default portrait

        val angle = when (interfaceOrientation) {
            1L -> 90.0 // UIInterfaceOrientationPortrait
            2L -> 270.0 // UIInterfaceOrientationPortraitUpsideDown
            3L -> 0.0 // UIInterfaceOrientationLandscapeLeft
            4L -> 180.0 // UIInterfaceOrientationLandscapeRight
            else -> 90.0
        }
        return angle
    }
}

/**
 * Returns the CGImagePropertyOrientation matching the current interface orientation.
 * Uses window scene interface orientation (not device orientation) to handle
 * iPad face-up/face-down correctly.
 */
private fun currentCGImageOrientation(): UInt {
    val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull()
    val windowScene = scene as? UIWindowScene
    val interfaceOrientation = windowScene?.interfaceOrientation ?: 1L

    return when (interfaceOrientation) {
        1L -> kCGImagePropertyOrientationRight // Portrait
        2L -> kCGImagePropertyOrientationLeft // PortraitUpsideDown
        3L -> kCGImagePropertyOrientationUp // LandscapeLeft
        4L -> kCGImagePropertyOrientationDown // LandscapeRight
        else -> kCGImagePropertyOrientationRight
    }
}

/**
 * Runs OCR on a specific region of a stored pixel buffer.
 * Used after the grace period countdown to scan only the cropped area.
 */
private fun runRegionOCR(
    pixelBuffer: CVPixelBufferRef,
    regionOfInterest: NormalizedRect,
    onResult: (String, Int) -> Unit,
) {
    val handler = VNImageRequestHandler(
        pixelBuffer,
        orientation = currentCGImageOrientation(),
        options = emptyMap<Any?, Any>(),
    )
    val request = VNRecognizeTextRequest { req, error ->
        if (error != null) {
            dispatch_async(dispatch_get_main_queue()) { onResult("", 0) }
            return@VNRecognizeTextRequest
        }
        val observations = req?.results
            ?.filterIsInstance<VNRecognizedTextObservation>()
            ?: emptyList()
        val text = observations
            .mapNotNull { obs ->
                obs.topCandidates(1u).firstOrNull()
                    ?.let { (it as? platform.Vision.VNRecognizedText)?.string }
            }
            .joinToString(" ")
        val words = text.split(" ").count { it.isNotBlank() }
        dispatch_async(dispatch_get_main_queue()) { onResult(text, words) }
    }
    request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    request.usesLanguageCorrection = true
    request.regionOfInterest = CGRectMake(
        regionOfInterest.x.toDouble(),
        regionOfInterest.y.toDouble(),
        regionOfInterest.width.toDouble(),
        regionOfInterest.height.toDouble(),
    )
    try {
        handler.performRequests(listOf(request), null)
    } catch (e: Exception) {
        AppLogger.e(TAG, "Region OCR failed: ${e.message}")
        dispatch_async(dispatch_get_main_queue()) { onResult("", 0) }
    }
}

/**
 * AVCaptureVideoDataOutput delegate that runs Vision OCR + rectangle detection on each frame.
 * Throttled to one run every 300ms to avoid overwhelming the Vision framework.
 */
private class IOSOCROutputDelegate(
    private val onTextResult: (String, Int) -> Unit,
    private val onRectsDetected: (List<NormalizedRect>) -> Unit = {},
    private val onFrameDimensions: (Float, Float) -> Unit = { _, _ -> },
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {

    private var lastProcessTime: NSDate = NSDate(timeIntervalSinceReferenceDate = 0.0)
    private val processingInterval: Double = 0.3 // 300ms
    private var reportedDimensions = false

    // Store last pixel buffer for region OCR after countdown
    private var _lastPixelBuffer: CVPixelBufferRef? = null
    fun getLastPixelBuffer(): CVPixelBufferRef? = _lastPixelBuffer

    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: platform.AVFoundation.AVCaptureConnection,
    ) {
        val sampleBuffer = didOutputSampleBuffer ?: return

        // Throttle processing
        val now = NSDate()
        if (now.timeIntervalSinceDate(lastProcessTime) < processingInterval) return
        lastProcessTime = now

        val pixelBuffer: CVPixelBufferRef = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return

        // Store the latest pixel buffer (retain it so it survives after the sample buffer is recycled)
        _lastPixelBuffer?.let { CVPixelBufferRelease(it) }
        CVPixelBufferRetain(pixelBuffer)
        _lastPixelBuffer = pixelBuffer

        // Report sensor frame dimensions once
        if (!reportedDimensions) {
            val fw = CVPixelBufferGetWidth(pixelBuffer).toFloat()
            val fh = CVPixelBufferGetHeight(pixelBuffer).toFloat()
            reportedDimensions = true
            dispatch_async(dispatch_get_main_queue()) { onFrameDimensions(fw, fh) }
        }

        val requestHandler = VNImageRequestHandler(
            pixelBuffer,
            orientation = currentCGImageOrientation(),
            options = emptyMap<Any?, Any>(),
        )

        // Text recognition request
        val textRequest = VNRecognizeTextRequest { request, error ->
            if (error != null) return@VNRecognizeTextRequest
            val observations = request?.results
                ?.filterIsInstance<VNRecognizedTextObservation>()
                ?: return@VNRecognizeTextRequest

            val text = observations
                .mapNotNull { observation ->
                    observation.topCandidates(1u).firstOrNull()
                        ?.let { (it as? platform.Vision.VNRecognizedText)?.string }
                }
                .joinToString(" ")
            val words = text.split(" ").count { it.isNotBlank() }

            dispatch_async(dispatch_get_main_queue()) {
                onTextResult(text, words)
            }
        }
        textRequest.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        textRequest.usesLanguageCorrection = true

        // Rectangle detection request (for crop pre-positioning)
        val rectRequest = VNDetectRectanglesRequest { request, error ->
            if (error != null) return@VNDetectRectanglesRequest
            val rects = request?.results
                ?.filterIsInstance<VNRectangleObservation>()
                ?: return@VNDetectRectanglesRequest

            val normalizedRects = rects.mapNotNull { obs ->
                try {
                    val bb = obs.boundingBox
                    NormalizedRect(
                        x = platform.CoreGraphics.CGRectGetMinX(bb).toFloat(),
                        y = platform.CoreGraphics.CGRectGetMinY(bb).toFloat(),
                        width = platform.CoreGraphics.CGRectGetWidth(bb).toFloat(),
                        height = platform.CoreGraphics.CGRectGetHeight(bb).toFloat(),
                    )
                } catch (_: Exception) {
                    null
                }
            }
            dispatch_async(dispatch_get_main_queue()) {
                onRectsDetected(normalizedRects)
            }
        }
        // Configure for medication box-sized rectangles
        rectRequest.minimumAspectRatio = 0.3f
        rectRequest.maximumAspectRatio = 1.0f
        rectRequest.minimumSize = 0.1f
        rectRequest.maximumObservations = 5uL
        rectRequest.minimumConfidence = 0.5f

        try {
            requestHandler.performRequests(listOf(textRequest, rectRequest), null)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Vision processing failed: ${e.message}")
        }
    }
}
