// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.DetectionMethod
import me.juliana.hellomeds.data.preferences.CameraPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.camera_consent_choose_method
import me.juliana.hellomeds.shared.camera_consent_choose_method_description
import me.juliana.hellomeds.shared.camera_consent_method_ai_body_android
import me.juliana.hellomeds.shared.camera_consent_method_ai_title_android
import me.juliana.hellomeds.shared.camera_consent_method_ai_title_ios
import me.juliana.hellomeds.shared.camera_consent_method_basic_body_android
import me.juliana.hellomeds.shared.camera_consent_method_basic_body_ios
import me.juliana.hellomeds.shared.camera_consent_method_basic_title
import me.juliana.hellomeds.shared.settings_camera_section_privacy
import me.juliana.hellomeds.shared.settings_camera_title
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.components.appleIntelligenceBodyAnnotatedString
import me.juliana.hellomeds.ui.components.cameraPrivacyAnnotatedString
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListRadioItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.util.MlDetectionStatusValue
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import me.juliana.hellomeds.ui.util.rememberMlDetectionStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cameraPreferences = koinInject<CameraPreferences>()
    val detectionMethod by cameraPreferences.detectionMethod.collectAsStateWithLifecycle(
        initial = DetectionMethod.GEMINI,
    )

    val mlStatus = rememberMlDetectionStatus()
    val isAiAvailable = mlStatus != MlDetectionStatusValue.UNAVAILABLE
    val isApplePlatform = !PlatformCapabilities.showMlDetectionMethodPicker()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_camera_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SettingsHeader(stringResource(Res.string.settings_camera_section_privacy), isFirst = true)
            }
            item {
                Text(
                    text = cameraPrivacyAnnotatedString(isApplePlatform = isApplePlatform),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.settingsContentPadding(),
                )
            }

            if (isAiAvailable) {
                item {
                    SettingsHeader(stringResource(Res.string.camera_consent_choose_method))
                }
                item {
                    Text(
                        text = stringResource(Res.string.camera_consent_choose_method_description),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .settingsContentPadding()
                            .padding(bottom = 12.dp),
                    )
                }
                item {
                    SmartList {
                        SmartListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(
                                        if (isApplePlatform) {
                                            Res.string.camera_consent_method_ai_title_ios
                                        } else {
                                            Res.string.camera_consent_method_ai_title_android
                                        },
                                    ),
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = if (isApplePlatform) {
                                        appleIntelligenceBodyAnnotatedString()
                                    } else {
                                        androidx.compose.ui.text.AnnotatedString(
                                            stringResource(Res.string.camera_consent_method_ai_body_android),
                                        )
                                    },
                                )
                            },
                            leadingContent = {
                                androidx.compose.material3.RadioButton(
                                    selected = detectionMethod == DetectionMethod.GEMINI,
                                    onClick = {
                                        scope.launch {
                                            cameraPreferences.setDetectionMethod(DetectionMethod.GEMINI)
                                        }
                                    },
                                )
                            },
                            shapes = smartListSegmentedShapes(index = 0, count = 2),
                            onClick = {
                                scope.launch {
                                    cameraPreferences.setDetectionMethod(DetectionMethod.GEMINI)
                                }
                            },
                        )

                        SmartListRadioItem(
                            label = stringResource(Res.string.camera_consent_method_basic_title),
                            selected = detectionMethod == DetectionMethod.HEURISTIC,
                            onClick = {
                                scope.launch {
                                    cameraPreferences.setDetectionMethod(DetectionMethod.HEURISTIC)
                                }
                            },
                            shapes = smartListSegmentedShapes(index = 1, count = 2),
                            supportingText = stringResource(
                                if (isApplePlatform) {
                                    Res.string.camera_consent_method_basic_body_ios
                                } else {
                                    Res.string.camera_consent_method_basic_body_android
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}
