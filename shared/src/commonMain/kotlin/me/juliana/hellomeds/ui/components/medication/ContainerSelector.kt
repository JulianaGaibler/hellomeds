// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListRadioItem
import me.juliana.hellomeds.ui.util.displayNameRes
import org.jetbrains.compose.resources.stringResource

/**
 * Composable for selecting a medication container type.
 *
 * Displays all available container types as radio buttons in a scrollable list.
 *
 * @param selectedContainer The currently selected container type (null if none selected)
 * @param onContainerSelected Callback when a container is selected
 * @param modifier Modifier for the column
 * @param containersToShow Optional list of specific containers to show (defaults to all)
 */

@Composable
fun ContainerSelector(
    selectedContainer: MedicationContainer?,
    onContainerSelected: (MedicationContainer?) -> Unit,
    modifier: Modifier = Modifier,
    containersToShow: List<MedicationContainer> = MedicationContainer.entries,
) {
    val allContainers = containersToShow.map { container ->
        container to stringResource(container.displayNameRes)
    }

    // Common containers surfaced first.
    val commonContainers = listOf(
        MedicationContainer.BOTTLE,
        MedicationContainer.PACKAGE,
        MedicationContainer.BLISTER_PACK,
    )

    // Separate common containers from remaining ones
    val topGroupContainers = allContainers.filter { (container, _) -> container in commonContainers }
    val topGroupEnums = topGroupContainers.map { it.first }
    val remainingContainers = allContainers.filter { (container, _) -> container !in topGroupEnums }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Show common containers first (most frequently used)
        if (topGroupContainers.isNotEmpty()) {
            AutoSmartList(
                items = topGroupContainers.map { (container, label) ->
                    SmartListItemConfig { shapes, visible ->
                        SmartListRadioItem(
                            label = label,
                            selected = selectedContainer == container,
                            onClick = { onContainerSelected(container) },
                            shapes = shapes,
                            visible = visible,
                        )
                    }
                },
            )
        }

        // Show remaining container types
        if (remainingContainers.isNotEmpty()) {
            AutoSmartList(
                items = remainingContainers.map { (container, label) ->
                    SmartListItemConfig { shapes, visible ->
                        SmartListRadioItem(
                            label = label,
                            selected = selectedContainer == container,
                            onClick = { onContainerSelected(container) },
                            shapes = shapes,
                            visible = visible,
                        )
                    }
                },
            )
        }
    }
}
