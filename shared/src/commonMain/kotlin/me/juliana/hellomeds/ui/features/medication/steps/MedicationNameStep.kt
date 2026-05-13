// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_from_camera
import me.juliana.hellomeds.shared.wizard_medication_name_headline
import me.juliana.hellomeds.shared.wizard_medication_name_placeholder
import me.juliana.hellomeds.shared.wizard_medication_name_title
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListDivider
import me.juliana.hellomeds.ui.components.list.SmartListHeader
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import org.jetbrains.compose.resources.stringResource

/**
 * Step 1: Enter medication name
 */

@Composable
internal fun MedicationNameStep(
    name: String,
    onNameChange: (String) -> Unit,
    detectedNames: List<String> = emptyList(),
) {
    val focusRequester = remember { FocusRequester() }
    var shouldRequestFocus by remember { mutableStateOf(false) }
    val textFieldState = rememberTextFieldState(initialText = name)

    // Sync external name changes to TextFieldState and position cursor at end
    LaunchedEffect(name) {
        if (textFieldState.text.toString() != name) {
            textFieldState.edit {
                replace(0, length, name)
                placeCursorAtEnd()
            }
        }
    }

    // Sync TextFieldState changes to external state
    LaunchedEffect(textFieldState.text) {
        val currentText = textFieldState.text.toString()
        if (currentText != name) {
            onNameChange(currentText)
        }
    }

    // Request focus after detected name is selected
    LaunchedEffect(shouldRequestFocus) {
        if (shouldRequestFocus) {
            focusRequester.requestFocus()
            shouldRequestFocus = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            headline = stringResource(Res.string.wizard_medication_name_headline),
            title = stringResource(Res.string.wizard_medication_name_title),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            BasicTextField(
                state = textFieldState,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorator = { innerTextField ->
                    if (textFieldState.text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.wizard_medication_name_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )
        }

        // Show detected names if available
        if (detectedNames.isNotEmpty()) {
            Column {
                SmartListHeader(
                    text = stringResource(Res.string.camera_detection_from_camera),
                )

                SmartList {
                    detectedNames.forEachIndexed { index, detectedName ->
                        val shapes = smartListSegmentedShapes(
                            index = index,
                            count = detectedNames.size,
                        )

                        SmartListItem(
                            headlineContent = { Text(detectedName) },
                            shapes = shapes,
                            onClick = {
                                onNameChange(detectedName)
                                shouldRequestFocus = true
                            },
                        )

                        if (index < detectedNames.size - 1) {
                            SmartListDivider()
                        }
                    }
                }
            }
        }
    }
}
