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

private enum class CameraEntryState {
    LOADING,
    SHOW_CONSENT_DIALOG,
    SHOW_DOWNLOAD_DIALOG,
    SHOW_ERROR_DIALOG,
    READY,
}

/**
 * Entry screen for camera detection. An explicit state machine guarantees
 * consent/download dialogs run before the camera is ever composed.
 */
@Composable
fun CameraDetectionEntryScreen(
    onNavigateBack: () -> Unit,
    onDetectionComplete: (MedicationDetectionResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraPreferences = koinInject<CameraPreferences>()
    val hasConsented by cameraPreferences.hasConsented.collectAsState(initial = null)
    val detectionMethod by cameraPreferences.detectionMethod.collectAsState(initial = DetectionMethod.GEMINI)
    val hasShownDialog by cameraPreferences.hasShownDialog.collectAsState(initial = null)

    val medicationDetector = remember { MedicationDetector(context) }

    var geminiStatus by remember { mutableStateOf<Int?>(null) }
    var geminiStatusLoaded by remember { mutableStateOf(false) }

    var flowState by remember { mutableStateOf(CameraEntryState.LOADING) }

    var downloadErrorMessage by remember { mutableStateOf("") }

    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMB by remember { mutableIntStateOf(0) }
    var totalMB by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        geminiStatus = medicationDetector.checkGeminiStatus()
        geminiStatusLoaded = true
    }

    LaunchedEffect(hasConsented, hasShownDialog, geminiStatus, geminiStatusLoaded, detectionMethod) {
        if (!geminiStatusLoaded || geminiStatus == null || hasConsented == null || hasShownDialog == null) {
            flowState = CameraEntryState.LOADING
            return@LaunchedEffect
        }

        when {
            hasConsented == false || hasShownDialog == false -> {
                flowState = CameraEntryState.SHOW_CONSENT_DIALOG
            }

            hasConsented == true &&
                hasShownDialog == true &&
                detectionMethod == DetectionMethod.GEMINI &&
                geminiStatus == FeatureStatus.DOWNLOADABLE -> {
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
            }

            else -> {
                flowState = CameraEntryState.READY
            }
        }
    }

    when (flowState) {
        CameraEntryState.LOADING -> {
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )
        }

        CameraEntryState.SHOW_CONSENT_DIALOG -> {
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
            Box(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.surface),
            )

            GeminiDownloadDialog(
                downloadProgress = downloadProgress,
                downloadedMB = downloadedMB,
                totalMB = totalMB,
                onCancel = {
                    // Fall back to heuristic.
                    scope.launch {
                        cameraPreferences.setDetectionMethod(DetectionMethod.HEURISTIC)
                        flowState = CameraEntryState.READY
                    }
                },
            )
        }

        CameraEntryState.SHOW_ERROR_DIALOG -> {
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
                    // Fall back to heuristic.
                    scope.launch {
                        cameraPreferences.setDetectionMethod(DetectionMethod.HEURISTIC)
                        flowState = CameraEntryState.READY
                    }
                },
            )
        }

        CameraEntryState.READY -> {
            CameraDetectionScreen(
                onNavigateBack = onNavigateBack,
                onDetectionComplete = onDetectionComplete,
                modifier = modifier,
            )
        }
    }
}
