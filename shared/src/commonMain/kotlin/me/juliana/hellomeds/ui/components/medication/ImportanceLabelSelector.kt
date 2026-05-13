// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.importance_label_description
import me.juliana.hellomeds.shared.importance_label_edit_labels
import me.juliana.hellomeds.shared.importance_label_settings_note
import me.juliana.hellomeds.shared.importance_label_title
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.util.displayName
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable component for selecting an importance label.
 * Used in both the Add Medication flow and the Medication Detail bottom sheet.
 *
 * @param labels List of available importance labels
 * @param selectedLabelId Currently selected label ID
 * @param onLabelSelected Callback when a label is selected
 * @param modifier Modifier for the component
 * @param showHeader Whether to show the header (title and description)
 * @param showFooter Whether to show the footer note about settings
 * @param onNavigateToSettings Optional callback for navigating to importance labels settings.
 *                             If null (add medication flow), shows centered info text.
 *                             If provided (edit flow), shows navigation button.
 */

@Composable
fun ImportanceLabelSelector(
    labels: List<ImportanceLabel>,
    selectedLabelId: Int?,
    onLabelSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showFooter: Boolean = true,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showHeader) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.importance_label_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.importance_label_description),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AutoSmartList(
            items = labels.map { label ->
                SmartListItemConfig { shapes, visible ->
                    SmartListItem(
                        headlineContent = { Text(label.displayName()) },
                        leadingContent = {
                            RadioButton(
                                selected = selectedLabelId == label.id,
                                onClick = { onLabelSelected(label.id) },
                            )
                        },
                        shapes = shapes,
                        visible = visible,
                        onClick = { onLabelSelected(label.id) },
                    )
                }
            },
        )

        if (showFooter) {
            if (onNavigateToSettings == null) {
                // Add medication flow - show centered info text
                Text(
                    text = stringResource(Res.string.importance_label_settings_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Edit flow - show navigation button
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(onClick = onNavigateToSettings) {
                        Text(stringResource(Res.string.importance_label_edit_labels))
                    }
                }
            }
        }
    }
}
