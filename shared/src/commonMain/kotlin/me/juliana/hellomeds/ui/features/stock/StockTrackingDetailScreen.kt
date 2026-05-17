// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.StockDataPoint
import me.juliana.hellomeds.data.model.StockEvent
import me.juliana.hellomeds.data.model.StockStatus
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_cancel
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.illustration_empty_tracking
import me.juliana.hellomeds.shared.stock_action_container_depleted
import me.juliana.hellomeds.shared.stock_action_top_up
import me.juliana.hellomeds.shared.stock_action_update
import me.juliana.hellomeds.shared.stock_container_generic
import me.juliana.hellomeds.shared.stock_containers_generic
import me.juliana.hellomeds.shared.stock_depletion_confirm_message
import me.juliana.hellomeds.shared.stock_depletion_confirm_title
import me.juliana.hellomeds.shared.stock_detail_add_button
import me.juliana.hellomeds.shared.stock_detail_description
import me.juliana.hellomeds.shared.stock_detail_no_changes
import me.juliana.hellomeds.shared.stock_detail_no_tracking
import me.juliana.hellomeds.shared.stock_detail_settings
import me.juliana.hellomeds.shared.stock_detail_title_dynamic
import me.juliana.hellomeds.shared.stock_event_adjusted_to
import me.juliana.hellomeds.shared.stock_event_adjustment
import me.juliana.hellomeds.shared.stock_event_balanced_with_new
import me.juliana.hellomeds.shared.stock_event_cleared_overdraft
import me.juliana.hellomeds.shared.stock_event_container_added
import me.juliana.hellomeds.shared.stock_event_container_depleted
import me.juliana.hellomeds.shared.stock_event_containers_added
import me.juliana.hellomeds.shared.stock_event_corrected_stock
import me.juliana.hellomeds.shared.stock_event_each
import me.juliana.hellomeds.shared.stock_event_full
import me.juliana.hellomeds.shared.stock_event_full_container
import me.juliana.hellomeds.shared.stock_event_full_container_added
import me.juliana.hellomeds.shared.stock_event_marked_empty
import me.juliana.hellomeds.shared.stock_event_name_transition
import me.juliana.hellomeds.shared.stock_event_opening_next
import me.juliana.hellomeds.shared.stock_event_packaging_updated
import me.juliana.hellomeds.shared.stock_event_predicted_transition
import me.juliana.hellomeds.shared.stock_event_refilled
import me.juliana.hellomeds.shared.stock_event_started_tracking
import me.juliana.hellomeds.shared.stock_event_switched
import me.juliana.hellomeds.shared.stock_event_took_doses
import me.juliana.hellomeds.shared.stock_prediction_container_days
import me.juliana.hellomeds.shared.stock_prediction_container_empty
import me.juliana.hellomeds.shared.stock_prediction_estimated_approximate
import me.juliana.hellomeds.shared.stock_prediction_estimated_range
import me.juliana.hellomeds.shared.stock_prediction_single_prefix
import me.juliana.hellomeds.shared.stock_rationale_estimated_container
import me.juliana.hellomeds.shared.stock_rationale_exact_total
import me.juliana.hellomeds.shared.stock_rationale_rate_daily
import me.juliana.hellomeds.shared.stock_rationale_rate_weekly
import me.juliana.hellomeds.shared.stock_rationale_separator
import me.juliana.hellomeds.shared.stock_summary_discrete_current
import me.juliana.hellomeds.shared.stock_summary_total
import me.juliana.hellomeds.shared.stock_summary_units_remaining
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.LoadingIndicator
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.ToggleButtonDefaults
import me.juliana.hellomeds.ui.components.common.EmptyState
import me.juliana.hellomeds.ui.components.graph.StockLevelGraph
import me.juliana.hellomeds.ui.components.graph.models.GraphConfig
import me.juliana.hellomeds.ui.components.graph.models.StockLine
import me.juliana.hellomeds.ui.components.graph.models.ZoomLevel
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.stock.preview.StockPreviewSelector
import me.juliana.hellomeds.ui.util.currentLabelRes
import me.juliana.hellomeds.ui.util.displayNameLowerRes
import me.juliana.hellomeds.ui.util.displayNameRes
import me.juliana.hellomeds.ui.util.dosagePluralRes
import me.juliana.hellomeds.ui.util.doseUnitPluralRes
import me.juliana.hellomeds.ui.util.formatDate
import me.juliana.hellomeds.ui.util.fullRemainingPluralRes
import me.juliana.hellomeds.ui.util.labelPluralRes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Stock Tracking Detail Screen
 *
 * Redesigned screen with visual stock illustration, human-readable summaries,
 * action buttons, schedule-based predictions, graph, and day-focused event list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockTrackingDetailScreen(
    medication: Medication,
    currentStock: Double?,
    stockStatus: StockStatus?,
    onNavigateBack: () -> Unit,
    onSettings: () -> Unit,
    onAddTracking: () -> Unit,
    stockLine: StockLine? = null,
    onTopUp: () -> Unit = {},
    onUpdateStock: () -> Unit = {},
    onContainerDepleted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED
    var showDepletionDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            Res.string.stock_detail_title_dynamic,
                            medication.displayName ?: medication.name,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                actions = {
                    if (medication.stockTrackingEnabled) {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(Res.string.stock_detail_settings),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        // Empty/loading states render outside the scrollable Column so they can occupy
        // the full viewport and center vertically — Modifier.weight doesn't behave
        // inside a verticalScroll-modified Column.
        if (!medication.stockTrackingEnabled) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(Res.string.stock_detail_no_tracking),
                    description = stringResource(Res.string.stock_detail_description),
                    illustration = painterResource(Res.drawable.illustration_empty_tracking),
                    action = {
                        Button(onClick = onAddTracking) {
                            Text(stringResource(Res.string.stock_detail_add_button))
                        }
                    },
                )
            }
        } else if (stockStatus == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Fill-level of the current container.
                val previewStock = stockStatus.currentContainerRemaining
                    ?: currentStock ?: medication.currentStockQuantity ?: 0.0
                StockPreviewSelector(
                    medication = medication,
                    currentStock = previewStock,
                    modifier = Modifier
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .heightIn(max = 256.dp)
                        .padding(vertical = 8.dp),
                )

                // Stock summary text (two lines, centered)
                StockSummaryText(
                    medication = medication,
                    stockStatus = stockStatus,
                    isEstimated = isEstimated,
                )

                // Action buttons: FlowRow wraps to grid on small screens, single row on tablets
                val showDepleted = isEstimated && stockStatus.totalQuantity > 0
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledTonalButton(
                        onClick = onTopUp,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(Res.string.stock_action_top_up))
                    }
                    FilledTonalButton(
                        onClick = onUpdateStock,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(stringResource(Res.string.stock_action_update))
                    }
                    if (showDepleted) {
                        FilledTonalButton(
                            onClick = { showDepletionDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                        ) {
                            Text(stringResource(Res.string.stock_action_container_depleted))
                        }
                    }
                }

                // Prediction text
                PredictionText(stockStatus = stockStatus)

                // Stock Graph Section
                if (stockLine != null && stockLine.dataPoints.isNotEmpty()) {
                    var centeredPoint by remember { mutableStateOf<StockDataPoint?>(null) }
                    var livePoint by remember { mutableStateOf<StockDataPoint?>(null) }
                    var zoomLevel by remember { mutableStateOf(ZoomLevel.WEEK) }

                    // Pre-compute date lookup map once when stockLine changes
                    val eventsByDate = remember(stockLine) {
                        stockLine.dataPoints
                            .filter { it.event != null }
                            .groupBy { dp ->
                                Instant.fromEpochMilliseconds(dp.timestamp)
                                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            }
                    }

                    // Live event count for bubble (updates during scroll)
                    val liveDayEventCount by remember(eventsByDate) {
                        derivedStateOf {
                            val point = livePoint ?: return@derivedStateOf 0
                            val pointDate = Instant.fromEpochMilliseconds(point.timestamp)
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            eventsByDate[pointDate]?.count { !it.isFuture } ?: 0
                        }
                    }

                    // Snap-only events for the list below the graph
                    val centeredDayEvents by remember(eventsByDate) {
                        derivedStateOf {
                            val point = centeredPoint ?: return@derivedStateOf emptyList()
                            val pointDate = Instant.fromEpochMilliseconds(point.timestamp)
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            eventsByDate[pointDate]
                                ?.filter { !it.isFuture || it.event is StockEvent.ContainerSwitch }
                                ?: emptyList()
                        }
                    }

                    // Graph
                    StockLevelGraph(
                        lines = listOf(stockLine),
                        config = GraphConfig(
                            zoomLevel = zoomLevel,
                            showLegend = false,
                            showGrid = false,
                            snapToPoints = true,
                            enablePanning = true,
                        ),
                        onCenteredPointChanged = { centeredPoint = it },
                        onLivePointChanged = { livePoint = it },
                        centeredDayEventCount = liveDayEventCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )

                    // Zoom level selector (connected button group)
                    ZoomLevelSelector(
                        selectedLevel = zoomLevel,
                        onLevelSelected = { zoomLevel = it },
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    // Day event list (always show date; empty state when no changes)
                    val point = centeredPoint
                    if (point != null) {
                        DayEventList(
                            events = centeredDayEvents,
                            timestamp = point.timestamp,
                            medication = medication,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDepletionDialog) {
        AlertDialog(
            onDismissRequest = { showDepletionDialog = false },
            title = { Text(stringResource(Res.string.stock_depletion_confirm_title)) },
            text = { Text(stringResource(Res.string.stock_depletion_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDepletionDialog = false
                    onContainerDepleted()
                }) {
                    Text(stringResource(Res.string.stock_action_container_depleted))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDepletionDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun StockSummaryText(medication: Medication, stockStatus: StockStatus?, isEstimated: Boolean) {
    if (stockStatus == null) return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        val container = medication.medicationContainer
        val headerStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        val subStyle = MaterialTheme.typography.bodyLarge
        val color = MaterialTheme.colorScheme.onSurface

        if (container != null) {
            // Two-line layout (always show both lines, including a "0 full X remaining" sub-line).
            val sealedCount: Int
            val headerText: String
            if (isEstimated) {
                val totalContainers = stockStatus.totalQuantity.toInt()
                sealedCount = (totalContainers - 1).coerceAtLeast(0)
                headerText = stringResource(container.currentLabelRes)
            } else {
                val containerRemaining = stockStatus.currentContainerRemaining?.toInt() ?: 0
                sealedCount = stockStatus.sealedContainerCount
                headerText = stringResource(
                    Res.string.stock_summary_discrete_current,
                    containerRemaining,
                    stringResource(container.displayNameLowerRes),
                )
            }
            Text(
                text = headerText,
                style = headerStyle,
                color = color,
                textAlign = TextAlign.Center,
            )
            Text(
                text = pluralStringResource(container.fullRemainingPluralRes, sealedCount, sealedCount),
                style = subStyle,
                color = color,
                textAlign = TextAlign.Center,
            )
        } else if (!isEstimated) {
            // Exact, no container: "N units remaining" using the medication's dose unit plural.
            val totalInt = stockStatus.totalQuantity.toInt()
            val unitLabel = pluralStringResource(medication.type.doseUnitPluralRes, totalInt)
            Text(
                text = stringResource(Res.string.stock_summary_units_remaining, totalInt, unitLabel),
                style = headerStyle,
                color = color,
                textAlign = TextAlign.Center,
            )
        } else {
            // Estimated, no container: fall back to a raw total count.
            Text(
                text = stringResource(Res.string.stock_summary_total, stockStatus.totalQuantity.toInt()),
                style = headerStyle,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PredictionText(stockStatus: StockStatus?) {
    val daysRemaining = stockStatus?.daysRemaining
    if (daysRemaining == null || daysRemaining <= 0) return

    val runOutDate = stockStatus.runOutDate ?: return
    val earlyRunOutDate = stockStatus.earlyRunOutDate
    val lateRunOutDate = stockStatus.lateRunOutDate

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp),
    ) {
        val text =
            if (stockStatus.isEstimated && earlyRunOutDate != null && lateRunOutDate != null) {
                // ESTIMATED mode with range: "Estimated to run out between X and Y"
                stringResource(
                    Res.string.stock_prediction_estimated_range,
                    formatDate(earlyRunOutDate),
                    formatDate(lateRunOutDate),
                )
            } else if (stockStatus.isEstimated) {
                // ESTIMATED mode without range: "Estimated to run out around X"
                stringResource(
                    Res.string.stock_prediction_estimated_approximate,
                    formatDate(runOutDate),
                )
            } else {
                // EXACT mode: "You'll run out on X" (deterministic)
                buildString {
                    append(stringResource(Res.string.stock_prediction_single_prefix))
                    append(" ")
                    append(formatDate(runOutDate))
                }
            }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Per-container prediction when there are sealed containers behind the open one
        val containerRemaining = stockStatus.currentContainerRemaining
        val dailyRate = stockStatus.rationale?.dailyRate
        if (stockStatus.isEstimated && stockStatus.sealedContainerCount > 0 &&
            containerRemaining != null && dailyRate != null && dailyRate > 0
        ) {
            val containerDays = kotlin.math.ceil(containerRemaining / dailyRate).toInt()
            Text(
                text = if (containerDays > 0) {
                    pluralStringResource(
                        Res.plurals.stock_prediction_container_days,
                        containerDays,
                        containerDays,
                    )
                } else {
                    stringResource(Res.string.stock_prediction_container_empty)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // Show calculation rationale
        stockStatus.rationale?.let { rationale ->
            val rateValue = if (rationale.dailyRate >= 1) rationale.dailyRate else rationale.dailyRate * 7
            val formattedRate = if (rateValue == rateValue.toLong().toDouble()) {
                rateValue.toLong().toString()
            } else {
                me.juliana.hellomeds.ui.util.formatDecimal(rateValue)
            }
            val rateText = if (rationale.dailyRate >= 1) {
                stringResource(Res.string.stock_rationale_rate_daily, formattedRate)
            } else {
                stringResource(Res.string.stock_rationale_rate_weekly, formattedRate)
            }
            val dosesPerContainer = rationale.dosesPerContainer
            val totalText = if (dosesPerContainer != null) {
                stringResource(Res.string.stock_rationale_estimated_container, dosesPerContainer)
            } else {
                pluralStringResource(
                    Res.plurals.stock_rationale_exact_total,
                    rationale.totalDoses,
                    rationale.totalDoses,
                )
            }
            Text(
                text = stringResource(Res.string.stock_rationale_separator, totalText, rateText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ZoomLevelSelector(
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
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 32.dp),
            ) {
                Text(level.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DayEventList(
    events: List<StockDataPoint>,
    timestamp: Long,
    medication: Medication,
    modifier: Modifier = Modifier,
) {
    val date = remember(timestamp) {
        Instant.fromEpochMilliseconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    val isEstimated = medication.trackingPrecision == TrackingPrecision.ESTIMATED

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatDate(date),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Group and merge events by type
        val mergedEvents = mergeEvents(events, medication, isEstimated)

        if (mergedEvents.isEmpty()) {
            Text(
                text = stringResource(Res.string.stock_detail_no_changes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            AutoSmartList(
                items = mergedEvents.map { (headline, supporting) ->
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(headline) },
                            supportingContent = { Text(supporting) },
                            shapes = shapes,
                            visible = visible,
                        )
                    }
                },
            )
        }
    }
}

/** Format a quantity, dropping .0 for whole numbers. */
private fun formatQty(value: Double): String = if (value % 1.0 == 0.0) {
    value.toInt().toString()
} else {
    me.juliana.hellomeds.ui.util.formatDecimal(
        value,
    )
}

/**
 * Merge events on the same day by type into human-readable headline + supporting text pairs.
 */
@Composable
private fun mergeEvents(
    events: List<StockDataPoint>,
    medication: Medication,
    isEstimated: Boolean,
): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val containerEnum = medication.medicationContainer
    val container = containerEnum?.let { stringResource(it.displayNameLowerRes) }
        ?: stringResource(Res.string.stock_container_generic)

    val doseTakenEvents = events.filter { it.event is StockEvent.DoseTaken }
    val refillEvents = events.filter { it.event is StockEvent.Refill }
    val containerSwitchEvents = events.filter { it.event is StockEvent.ContainerSwitch }
    val adjustmentEvents = events.filter {
        it.event is StockEvent.Adjustment &&
            (it.event as StockEvent.Adjustment).adjustmentType != "PREDICTED_EMPTY"
    }

    // Doses: merge into single entry
    if (doseTakenEvents.isNotEmpty()) {
        val count = doseTakenEvents.size
        val headline = pluralStringResource(Res.plurals.stock_event_took_doses, count, count)
        val supporting = if (isEstimated) {
            pluralStringResource(medication.type.dosagePluralRes, count, count)
        } else {
            val totalConsumed = doseTakenEvents.sumOf {
                kotlin.math.abs((it.event as StockEvent.DoseTaken).quantity)
            }
            val totalInt = totalConsumed.toInt()
            val unitStr = pluralStringResource(medication.type.doseUnitPluralRes, totalInt)
            "-${formatQty(totalConsumed)} $unitStr"
        }
        result.add(headline to supporting)
    }

    // Refills
    refillEvents.forEach { event ->
        val qty = (event.event as StockEvent.Refill).quantity
        val headline = stringResource(Res.string.stock_event_refilled)
        if (isEstimated) {
            result.add(
                headline to stringResource(
                    Res.string.stock_event_full_container_added,
                    container,
                ),
            )
        } else {
            val qtyInt = qty.toInt()
            val unitStr = pluralStringResource(medication.type.doseUnitPluralRes, qtyInt)
            result.add(headline to "+${formatQty(qty)} $unitStr")
        }
    }

    // Adjustments: differentiate by type and notes
    // First, separate sealed containers for merging
    val sealedContainerEvents = adjustmentEvents.filter {
        val adj = it.event as StockEvent.Adjustment
        adj.adjustmentType == "INITIAL_STOCK" && adj.notes?.contains("Sealed container") == true
    }
    val otherAdjustments = adjustmentEvents - sealedContainerEvents.toSet()

    // Merge sealed containers into a single entry
    if (sealedContainerEvents.isNotEmpty()) {
        val count = sealedContainerEvents.size
        val qty = (sealedContainerEvents.first().event as StockEvent.Adjustment).quantity
        val containerLabel = if (count == 1) {
            val name = containerEnum?.let { stringResource(it.displayNameRes) }
                ?: stringResource(Res.string.stock_container_generic)
            stringResource(Res.string.stock_event_container_added, name)
        } else {
            val pluralLabel = containerEnum?.let {
                pluralStringResource(it.labelPluralRes, count)
            } ?: pluralStringResource(Res.plurals.stock_containers_generic, count, count)
            stringResource(Res.string.stock_event_containers_added, count, pluralLabel)
        }
        val supporting = if (isEstimated) {
            if (count == 1) {
                stringResource(Res.string.stock_event_full_container, container)
            } else {
                stringResource(Res.string.stock_event_full)
            }
        } else {
            val qtyInt = qty.toInt()
            val unitStr = pluralStringResource(medication.type.doseUnitPluralRes, qtyInt)
            if (count == 1) {
                "${formatQty(qty)} $unitStr"
            } else {
                stringResource(Res.string.stock_event_each, "${formatQty(qty)} $unitStr")
            }
        }
        result.add(containerLabel to supporting)
    }

    // Container switches (historical or predicted transitions)
    containerSwitchEvents.forEach { event ->
        val switchEvent = event.event as StockEvent.ContainerSwitch
        val switchName = switchEvent.container?.let { stringResource(it.displayNameRes) }
            ?: stringResource(Res.string.stock_container_generic).replaceFirstChar { it.uppercase() }
        if (event.isFuture) {
            result.add(
                stringResource(Res.string.stock_event_opening_next, container) to
                    stringResource(Res.string.stock_event_predicted_transition),
            )
        } else {
            result.add(
                stringResource(Res.string.stock_event_switched, container) to
                    stringResource(Res.string.stock_event_name_transition, switchName),
            )
        }
    }

    // Other adjustments
    otherAdjustments.forEach { event ->
        val adj = event.event as StockEvent.Adjustment
        val qty = adj.quantity
        val notes = adj.notes
        val qtyInt = kotlin.math.abs(qty.toInt()).coerceAtLeast(1)
        val unitStr = pluralStringResource(medication.type.doseUnitPluralRes, qtyInt)

        when (adj.adjustmentType) {
            "INITIAL_STOCK" -> {
                result.add(
                    stringResource(Res.string.stock_event_started_tracking) to
                        "${formatQty(qty)} $unitStr",
                )
            }

            "CONTAINER_DEPLETED" -> {
                result.add(
                    stringResource(Res.string.stock_event_container_depleted) to
                        stringResource(Res.string.stock_event_marked_empty),
                )
            }

            "MANUAL_CORRECTION" -> when {
                notes?.contains("Overdraft absorbed") == true ->
                    result.add(
                        stringResource(Res.string.stock_event_cleared_overdraft) to
                            stringResource(Res.string.stock_event_balanced_with_new, container),
                    )

                notes?.contains("Packaging size update") == true -> {
                    val detail = notes.substringAfter("(", "").substringBefore(")", notes)
                    result.add(stringResource(Res.string.stock_event_packaging_updated) to detail)
                }

                else -> {
                    val qtyStr = if (qty >= 0) "+${formatQty(qty)}" else formatQty(qty)
                    val valInt = event.value.toInt().coerceAtLeast(1)
                    val valUnitStr = pluralStringResource(medication.type.doseUnitPluralRes, valInt)
                    result.add(
                        stringResource(Res.string.stock_event_corrected_stock) to
                            stringResource(
                                Res.string.stock_event_adjusted_to,
                                "${formatQty(event.value)} $valUnitStr",
                                "$qtyStr $unitStr",
                            ),
                    )
                }
            }

            else -> {
                val qtyStr = if (qty >= 0) "+${formatQty(qty)}" else formatQty(qty)
                result.add(
                    stringResource(Res.string.stock_event_adjustment) to "$qtyStr $unitStr",
                )
            }
        }
    }

    return result
}
