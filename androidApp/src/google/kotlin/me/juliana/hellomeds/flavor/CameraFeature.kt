// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.flavor

import android.app.Activity
import androidx.compose.runtime.Composable
import com.google.mlkit.genai.common.FeatureStatus
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.ml.detector.MedicationDetector
import me.juliana.hellomeds.ui.features.camera.CameraDetectionEntryScreen
import me.juliana.hellomeds.ui.util.MlDetectionStatusValue
import me.juliana.hellomeds.ui.util.mlDetectionStatusChecker

/**
 * Google flavor: provides real camera detection screen and ML status checker.
 */
object CameraFeature {
    val isAvailable: Boolean = true

    fun getCameraScreen(): @Composable (
        onNavigateBack: () -> Unit,
        onDetectionComplete: (MedicationDetectionResult) -> Unit,
    ) -> Unit =
        { onBack, onDetectionComplete ->
            CameraDetectionEntryScreen(
                onNavigateBack = onBack,
                onDetectionComplete = onDetectionComplete,
            )
        }

    fun registerMlStatusChecker(activity: Activity) {
        mlDetectionStatusChecker = {
            val detector = MedicationDetector(activity)
            val geminiStatus = detector.checkGeminiStatus()
            when (geminiStatus) {
                FeatureStatus.AVAILABLE -> MlDetectionStatusValue.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> MlDetectionStatusValue.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> MlDetectionStatusValue.DOWNLOADING
                else -> MlDetectionStatusValue.UNAVAILABLE
            }
        }
    }
}
