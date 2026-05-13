// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ml.detector.MedicationDetector
import me.juliana.hellomeds.ui.components.CameraConsentDialog
import me.juliana.hellomeds.ui.components.GeminiDownloadDialog
import me.juliana.hellomeds.ui.components.GeminiDownloadErrorDialog
import org.koin.compose.koinInject

/**
 * Flow states for camera entry screen
 */
private enum class CameraEntryState {
    LOADING, // Checking Gemini status and loading preferences
    SHOW_CONSENT_DIALOG, // Showing initial consent dialog
    SHOW_DOWNLOAD_DIALOG, // Downloading Gemini model
    SHOW_ERROR_DIALOG, // Showing download error
    READY, // All set, show camera
}

/**
 * Entry screen for camera detection that handles consent flow before showing camera.
 * This ensures dialogs are shown BEFORE entering the camera view.
 *
 * Uses an explicit state machine to ensure camera is never composed until ready.
 */
@Composable
fun CameraDetectionEntryScreen(
    onNavigateBack: () -> Unit,
    onDetectionComplete: (MedicationDetectionResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Preferences for first-time flow
    val cameraPreferences = koinInject<CameraPreferences>()
    val hasConsented by cameraPreferences.hasConsented.collectAsState(initial = null)
    val detectionMethod by cameraPreferences.detectionMethod.collectAsState(initial = DetectionMethod.GEMINI)
    val hasShownDialog by cameraPreferences.hasShownDialog.collectAsState(initial = null)

    // Medication detector for Gemini status
    val medicationDetector = remember { MedicationDetector(context) }

    // Gemini status
    var geminiStatus by remember { mutableStateOf<Int?>(null) }
    var geminiStatusLoaded by remember { mutableStateOf(false) }

    // Flow state
    var flowState by remember { mutableStateOf(CameraEntryState.LOADING) }

    var downloadErrorMessage by remember { mutableStateOf("") }

    // Download progress
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMB by remember { mutableIntStateOf(0) }
    var totalMB by remember { mutableIntStateOf(60) }

    // Check Gemini status on launch
    LaunchedEffect(Unit) {
        geminiStatus = medicationDetector.checkGeminiStatus()
        geminiStatusLoaded = true
    }

    // Determine flow state based on preferences and Gemini status
    LaunchedEffect(hasConsented, hasShownDialog, geminiStatus, geminiStatusLoaded, detectionMethod) {
        // Stay in LOADING until everything is loaded
        if (!geminiStatusLoaded || geminiStatus == null || hasConsented == null || hasShownDialog == null) {
            flowState = CameraEntryState.LOADING
            return@LaunchedEffect
        }

        // Determine what to show based on consent status
        when {
            // User hasn't consented or hasn't seen dialog - show appropriate consent dialog
            hasConsented == false || hasShownDialog == false -> {
                flowState = CameraEntryState.SHOW_CONSENT_DIALOG
            }

            // User previously chose Gemini but it's not downloaded - go straight to download
            hasConsented == true &&
                hasShownDialog == true &&
                detectionMethod == DetectionMethod.GEMINI &&
                geminiStatus == FeatureStatus.DOWNLOADABLE -> {
                flowState = CameraEntryState.SHOW_DOWNLOAD_DIALOG
                // Start download
                scope.launch {
                    medicationDetector.downloadGemini().collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadStarted -> {
                                downloadProgress = 0f
                            }

                            is DownloadStatus.DownloadProgress -> {
                                downloadedMB = (status.totalBytesDownloaded / (1024 * 1024)).toInt()
                                downloadProgress = downloadedMB.toFloat() / totalMB.toFloat()
                            }

                            is DownloadStatus.DownloadCompleted -> {
                                downloadProgress = 1f
                                geminiStatus = FeatureStatus.AVAILABLE
                                flowState = CameraEntryState.READY
                            }

                            is DownloadStatus.DownloadFailed -> {
                                downloadErrorMessage = status.e.message ?: "Unknown error"
                                flowState = CameraEntryState.SHOW_ERROR_DIALOG
                            }
                        }
                    }
                }
            }

            // All set - proceed to camera
            else -> {
                flowState = CameraEntryState.READY
            }
        }
    }

    // Render UI based on flow state
    when (flowState) {
        CameraEntryState.LOADING -> {
            // Show blank background while loading
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        CameraEntryState.SHOW_CONSENT_DIALOG -> {
            // Show blank background behind dialog
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )

            val isAiAvailable = geminiStatus == FeatureStatus.DOWNLOADABLE ||
                geminiStatus == FeatureStatus.AVAILABLE ||
                geminiStatus == FeatureStatus.DOWNLOADING

            CameraConsentDialog(
                isApplePlatform = false,
                isAiAvailable = isAiAvailable,
                geminiNeedsDownload = geminiStatus != FeatureStatus.AVAILABLE,
                onContinue = { method ->
                    scope.launch {
                        cameraPreferences.setConsent(true)
                        cameraPreferences.setDetectionMethod(method)
                        cameraPreferences.markDialogShown()

                        // If user chose Gemini and it's not downloaded, start download
                        if (method == DetectionMethod.GEMINI &&
                            geminiStatus == FeatureStatus.DOWNLOADABLE
                        ) {
                            flowState = CameraEntryState.SHOW_DOWNLOAD_DIALOG
                            medicationDetector.downloadGemini().collect { status ->
                                when (status) {
                                    is DownloadStatus.DownloadStarted -> {
                                        downloadProgress = 0f
                                    }

                                    is DownloadStatus.DownloadProgress -> {
                                        downloadedMB = (status.totalBytesDownloaded / (1024 * 1024)).toInt()
                                        downloadProgress = downloadedMB.toFloat() / totalMB.toFloat()
                                    }

                                    is DownloadStatus.DownloadCompleted -> {
                                        downloadProgress = 1f
                                        geminiStatus = FeatureStatus.AVAILABLE
                                        flowState = CameraEntryState.READY
                                    }

                                    is DownloadStatus.DownloadFailed -> {
                                        downloadErrorMessage = status.e.message ?: "Unknown error"
                                        flowState = CameraEntryState.SHOW_ERROR_DIALOG
                                    }
                                }
                            }
                        } else {
                            // Proceed to camera immediately
                            flowState = CameraEntryState.READY
                        }
                    }
                },
                onCancel = {
                    onNavigateBack()
                },
            )
        }

        CameraEntryState.SHOW_DOWNLOAD_DIALOG -> {
            // Show blank background behind dialog
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )

            GeminiDownloadDialog(
                downloadProgress = downloadProgress,
                downloadedMB = downloadedMB,
                totalMB = totalMB,
                onCancel = {
                    // Fall back to heuristic
                    scope.launch {
                        cameraPreferences.setDetectionMethod(DetectionMethod.HEURISTIC)
                        flowState = CameraEntryState.READY
                    }
                },
            )
        }

        CameraEntryState.SHOW_ERROR_DIALOG -> {
            // Show blank background behind dialog
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )

            GeminiDownloadErrorDialog(
                errorMessage = downloadErrorMessage,
                onRetry = {
                    flowState = CameraEntryState.SHOW_DOWNLOAD_DIALOG
                    scope.launch {
                        medicationDetector.downloadGemini().collect { status ->
                            when (status) {
                                is DownloadStatus.DownloadStarted -> {
                                    downloadProgress = 0f
                                }

                                is DownloadStatus.DownloadProgress -> {
                                    downloadedMB = (status.totalBytesDownloaded / (1024 * 1024)).toInt()
                                    downloadProgress = downloadedMB.toFloat() / totalMB.toFloat()
                                }

                                is DownloadStatus.DownloadCompleted -> {
                                    downloadProgress = 1f
                                    geminiStatus = FeatureStatus.AVAILABLE
                                    flowState = CameraEntryState.READY
                                }

                                is DownloadStatus.DownloadFailed -> {
                                    downloadErrorMessage = status.e.message ?: "Unknown error"
                                    flowState = CameraEntryState.SHOW_ERROR_DIALOG
                                }
                            }
                        }
                    }
                },
                onDismiss = {
                    // Fall back to heuristic
                    scope.launch {
                        cameraPreferences.setDetectionMethod(DetectionMethod.HEURISTIC)
                        flowState = CameraEntryState.READY
                    }
                },
            )
        }

        CameraEntryState.READY -> {
            // All consent and download flows complete - show camera
            CameraDetectionScreen(
                onNavigateBack = onNavigateBack,
                onDetectionComplete = onDetectionComplete,
                modifier = modifier,
            )
        }
    }
}
