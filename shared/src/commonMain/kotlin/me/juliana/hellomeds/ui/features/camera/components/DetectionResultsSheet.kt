// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.domain.ml.MedicationDetectionResult
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_detected_info
import me.juliana.hellomeds.shared.camera_detection_edit_note
import me.juliana.hellomeds.shared.camera_detection_medication_name
import me.juliana.hellomeds.shared.camera_detection_medication_strength
import me.juliana.hellomeds.shared.camera_detection_medication_type
import me.juliana.hellomeds.shared.camera_detection_method_basic
import me.juliana.hellomeds.shared.camera_detection_method_gemini
import me.juliana.hellomeds.shared.camera_detection_try_again
import me.juliana.hellomeds.shared.camera_detection_use_this
import me.juliana.hellomeds.ui.util.displayNameRes
import me.juliana.hellomeds.ui.util.formatDecimal
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet content displaying medication detection results.
 * Shows detected name, type, and strength with formatted chips.
 *
 * This is the shared (commonMain) version used by both Android and iOS.
 */
@Composable
fun DetectionResultsSheet(
    detectionResult: MedicationDetectionResult?,
    onTryAgain: () -> Unit,
    onUseThis: (MedicationDetectionResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (detectionResult != null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            // Header with detection method pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.camera_detection_detected_info),
                    style = MaterialTheme.typography.headlineSmall,
                )
                // Detection method pill
                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(64.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (detectionResult.usedAI) {
                                Res.string.camera_detection_method_gemini
                            } else {
                                Res.string.camera_detection_method_basic
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Medication Names
            if (detectionResult.nameSuggestions.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.camera_detection_medication_name),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(all = 8.dp),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detectionResult.nameSuggestions.forEach { name ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(50),
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Medication Types
            if (detectionResult.typeSuggestions.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.camera_detection_medication_type),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(all = 8.dp),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detectionResult.typeSuggestions.forEach { type ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = RoundedCornerShape(50),
                                )
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                        ) {
                            Text(
                                text = stringResource(type.displayNameRes),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Medication Strength
            detectionResult.strengthSuggestion?.let { strength ->
                Text(
                    text = stringResource(Res.string.camera_detection_medication_strength),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(all = 8.dp),
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(50),
                        )
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                ) {
                    Text(
                        text = "${formatDecimal(strength.value)}${strength.unit.value}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text(stringResource(Res.string.camera_detection_try_again))
                }

                Button(
                    onClick = { onUseThis(detectionResult) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Text(stringResource(Res.string.camera_detection_use_this))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer text
            Text(
                text = stringResource(Res.string.camera_detection_edit_note),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    } else {
        // Empty sheet content when no result
        Spacer(modifier = Modifier.height(1.dp))
    }
}
