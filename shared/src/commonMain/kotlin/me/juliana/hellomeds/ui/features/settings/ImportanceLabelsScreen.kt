// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.importance_labels_add_label
import me.juliana.hellomeds.shared.importance_labels_screen_description
import me.juliana.hellomeds.shared.screen_importance_labels
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.SmartList
import me.juliana.hellomeds.ui.components.list.SmartListDivider
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import me.juliana.hellomeds.ui.util.displayName
import me.juliana.hellomeds.ui.util.formatImportanceLabelDescription
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportanceLabelsScreen(
    labels: List<ImportanceLabel>,
    onNavigateBack: () -> Unit,
    onAddLabel: () -> Unit,
    onEditLabel: (ImportanceLabel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward },
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AppScaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(text = stringResource(Res.string.screen_importance_labels))
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.content_description_back),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.importance_labels_screen_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    )
                }

                item {
                    Card(
                        onClick = onAddLabel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(Res.string.importance_labels_add_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                item {
                    SmartList {
                        labels.forEachIndexed { index, label ->
                            ImportanceLabelListItem(
                                label = label,
                                onClick = { onEditLabel(label) },
                                shapes = smartListSegmentedShapes(index = index, count = labels.size),
                            )

                            if (index < labels.lastIndex) {
                                SmartListDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportanceLabelListItem(
    label: ImportanceLabel,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
) {
    val context = platformContext()
    val description = formatImportanceLabelDescription(label)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        shape = shapes.shape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportanceLabelsScreenPreview() {
    HelloMedsTheme {
        ImportanceLabelsScreen(
            labels = listOf(
                ImportanceLabel(
                    id = 1,
                    name = "Silent",
                    shouldRemind = false,
                    isCritical = false,
                    hasFollowUps = false,
                ),
                ImportanceLabel(
                    id = 2,
                    name = "Once",
                    shouldRemind = true,
                    isCritical = false,
                    hasFollowUps = false,
                ),
                ImportanceLabel(
                    id = 3,
                    name = "Follow ups",
                    shouldRemind = true,
                    isCritical = false,
                    hasFollowUps = true,
                    followUpCount = 3,
                ),
                ImportanceLabel(
                    id = 4,
                    name = "Critical follow ups",
                    shouldRemind = true,
                    isCritical = true,
                    hasFollowUps = true,
                    followUpCount = 3,
                    criticalAfterFollowUp = 2,
                ),
            ),
            onNavigateBack = {},
            onAddLabel = {},
            onEditLabel = {},
        )
    }
}
