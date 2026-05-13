// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_loading
import me.juliana.hellomeds.shared.illustration_empty_tracking
import me.juliana.hellomeds.shared.screen_stock
import me.juliana.hellomeds.shared.stock_days_left_approximate
import me.juliana.hellomeds.shared.stock_days_left_exact
import me.juliana.hellomeds.shared.stock_days_left_range
import me.juliana.hellomeds.shared.stock_empty_description
import me.juliana.hellomeds.shared.stock_empty_title
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.LoadingIndicator
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.ToggleButtonDefaults
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.common.TopAppBarWithMenu
import me.juliana.hellomeds.ui.components.graph.StockLevelGraph
import me.juliana.hellomeds.ui.components.graph.models.GraphConfig
import me.juliana.hellomeds.ui.components.graph.models.ZoomLevel
import me.juliana.hellomeds.ui.components.list.LazySmartListItem
import me.juliana.hellomeds.ui.components.list.smartListSegmentedShapes
import me.juliana.hellomeds.ui.components.medication.MedicationShapeIcon
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.viewmodel.StockTrackingViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stock Tracking Dashboard Screen.
 * Displays tracked medications with stock levels and a multi-line overview graph.
 */
@Composable
fun StockScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToSupport: () -> Unit,
    onNavigateToStockDetail: (medicationId: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StockTrackingViewModel = koinViewModel(),
) {
    val context = platformContext()
    val trackedMedications by viewModel.trackedMedications.collectAsStateWithLifecycle()
    val hasLoaded by viewModel.hasLoaded.collectAsStateWithLifecycle()

    // Stock statuses computed in parallel in ViewModel
    val stockStatuses by viewModel.stockStatuses.collectAsStateWithLifecycle()

    // Stock graph lines cached in ViewModel with debounce
    val allStockLines by viewModel.allStockGraphLines.collectAsStateWithLifecycle()

    // Use medication icon colors when available, fall back to theme colors
    val isDark = isSystemInDarkTheme()
    val medicationColorMap = remember(trackedMedications, isDark) {
        trackedMedications.mapNotNull { display ->
            val med = display.medication
            val color = med.shapeColor?.let { MedicationColor.fromName(it) }
            if (color != null) {
                med.id to if (isDark) color.foregroundDark else color.foregroundLight
            } else {
                null
            }
        }.toMap()
    }
    val fallbackColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.secondary,
    )
    val coloredLines = remember(allStockLines, medicationColorMap, fallbackColors) {
        var fallbackIndex = 0
        allStockLines.map { line ->
            val color = medicationColorMap[line.medicationId]
                ?: fallbackColors[fallbackIndex++ % fallbackColors.size]
            line.copy(color = color)
        }
    }

    var zoomLevel by remember { mutableStateOf(ZoomLevel.WEEK) }

    AppScaffold(
        topBar = {
            TopAppBarWithMenu(
                title = stringResource(Res.string.screen_stock),
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToSupport = onNavigateToSupport,
            )
        },
    ) { paddingValues ->
        when {
            !hasLoaded -> {
                val loadingDescription = stringResource(Res.string.accessibility_loading)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                        .semantics {
                            contentDescription = loadingDescription
                            liveRegion = LiveRegionMode.Polite
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
            trackedMedications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.illustration_empty_tracking),
                            contentDescription = null,
                            modifier = Modifier.size(200.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.stock_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.stock_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // Graph section (always rendered to reserve space and avoid layout shift)
                    item {
                        StockLevelGraph(
                            lines = coloredLines,
                            config = GraphConfig(
                                zoomLevel = zoomLevel,
                                showLegend = true,
                                showGrid = false,
                                snapToPoints = false,
                                enablePanning = true,
                                showYAxis = false,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        )
                    }

                    item {
                        OverviewZoomLevelSelector(
                            selectedLevel = zoomLevel,
                            onLevelSelected = { zoomLevel = it },
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                    }

                    // Medication list
                    itemsIndexed(
                        items = trackedMedications,
                        key = { _, display -> display.medication.id },
                    ) { index, display ->
                        val medication = display.medication
                        val status = stockStatuses[medication.id]

                        // Parse shape properties (same pattern as TrackingScreen)
                        val foregroundShape = MedicationForegroundShape.fromNameOrDefault(medication.foregroundShape)
                        val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)
                        val color1 = medication.shapeColor?.let { MedicationColor.fromName(it) }

                        // Build supporting text: stock + run-out info
                        val supportingText = buildString {
                            append(display.formattedStock)
                            val daysRemaining = status?.daysRemaining
                            val earlyRunOutDate = status?.earlyRunOutDate
                            val lateRunOutDate = status?.lateRunOutDate
                            if (daysRemaining != null && daysRemaining > 0) {
                                if (status.isEstimated && earlyRunOutDate != null && lateRunOutDate != null) {
                                    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
                                    val today = kotlin.time.Clock.System.now().toLocalDateTime(tz).date
                                    val earlyDays = today.daysUntil(earlyRunOutDate).coerceAtLeast(0)
                                    val lateDays = today.daysUntil(lateRunOutDate).coerceAtLeast(0)
                                    append(stringResource(Res.string.stock_days_left_range, earlyDays, lateDays))
                                } else if (status.isEstimated) {
                                    append(
                                        stringResource(
                                            Res.string.stock_days_left_approximate,
                                            daysRemaining,
                                        ),
                                    )
                                } else {
                                    append(
                                        stringResource(
                                            Res.string.stock_days_left_exact,
                                            daysRemaining,
                                        ),
                                    )
                                }
                            }
                        }

                        LazySmartListItem(
                            headlineContent = {
                                Text(medication.displayName ?: medication.name)
                            },
                            supportingContent = {
                                Text(supportingText)
                            },
                            leadingContent = {
                                MedicationShapeIcon(
                                    foregroundShape = foregroundShape,
                                    backgroundShape = backgroundShape,
                                    color1 = color1,
                                    size = 48.dp,
                                )
                            },
                            shapes = smartListSegmentedShapes(
                                index = index,
                                count = trackedMedications.size,
                            ),
                            onClick = { onNavigateToStockDetail(medication.id) },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewZoomLevelSelector(
    selectedLevel: ZoomLevel,
    onLevelSelected: (ZoomLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val levels = listOf(ZoomLevel.WEEK, ZoomLevel.MONTH, ZoomLevel.QUARTER)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        levels.forEachIndexed { index, level ->
            ToggleButton(
                checked = selectedLevel == level,
                onCheckedChange = { onLevelSelected(level) },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    levels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(level.label)
            }
        }
    }
}
