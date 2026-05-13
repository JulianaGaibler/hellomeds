// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.add_medication_camera
import me.juliana.hellomeds.shared.add_medication_camera_description
import me.juliana.hellomeds.shared.add_medication_import
import me.juliana.hellomeds.shared.add_medication_import_description
import me.juliana.hellomeds.shared.add_medication_manual
import me.juliana.hellomeds.shared.add_medication_manual_description
import me.juliana.hellomeds.shared.add_medication_options_title
import me.juliana.hellomeds.shared.outline_scan_24px
import me.juliana.hellomeds.shared.restore_page_24px
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that shows options for adding medication:
 * - Use camera (scan medication)
 * - Add manually
 * - Import from backup
 *
 * Displayed from the completion screen when user taps "Add medication"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationOptionsBottomSheet(
    onDismiss: () -> Unit,
    onCameraSelected: () -> Unit,
    onManualSelected: () -> Unit,
    onImportSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = stringResource(Res.string.add_medication_options_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )

            // Group 1: Camera (if available) + Manual
            val showCamera = PlatformCapabilities.supportsCameraDetection()
            val manualCount = if (showCamera) 2 else 1
            SmartList {
                if (showCamera) {
                    SmartListItem(
                        headlineContent = { Text(stringResource(Res.string.add_medication_camera)) },
                        supportingContent = { Text(stringResource(Res.string.add_medication_camera_description)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(Res.drawable.outline_scan_24px),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        shapes = smartListSegmentedShapes(index = 0, count = manualCount),
                        onClick = onCameraSelected,
                    )
                }
                SmartListItem(
                    headlineContent = { Text(stringResource(Res.string.add_medication_manual)) },
                    supportingContent = { Text(stringResource(Res.string.add_medication_manual_description)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    shapes = smartListSegmentedShapes(
                        index = if (showCamera) 1 else 0,
                        count = manualCount,
                    ),
                    onClick = onManualSelected,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group 2: Import from backup
            SmartList {
                SmartListItem(
                    headlineContent = { Text(stringResource(Res.string.add_medication_import)) },
                    supportingContent = { Text(stringResource(Res.string.add_medication_import_description)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(Res.drawable.restore_page_24px),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    shapes = smartListSegmentedShapes(index = 0, count = 1),
                    onClick = onImportSelected,
                )
            }
        }
    }
}
