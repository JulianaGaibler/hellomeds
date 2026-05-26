// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.stock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.BubbleFlowDirection
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_back
import me.juliana.hellomeds.shared.stock_layout_column_decrease
import me.juliana.hellomeds.shared.stock_layout_column_increase
import me.juliana.hellomeds.shared.stock_layout_columns
import me.juliana.hellomeds.shared.stock_layout_drag_hint
import me.juliana.hellomeds.shared.stock_layout_editor_title
import me.juliana.hellomeds.shared.stock_layout_flow_direction
import me.juliana.hellomeds.shared.stock_layout_mode_auto
import me.juliana.hellomeds.shared.stock_layout_mode_manual
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.ToggleButtonDefaults
import me.juliana.hellomeds.ui.components.stock.preview.BubbleLayoutCodec
import me.juliana.hellomeds.ui.components.stock.preview.BubbleStockPreview
import me.juliana.hellomeds.ui.components.stock.preview.GridLayout
import me.juliana.hellomeds.ui.components.stock.preview.MAX_COLUMNS
import me.juliana.hellomeds.ui.components.stock.preview.MIN_COLUMNS
import me.juliana.hellomeds.ui.components.stock.preview.ManualLayout
import me.juliana.hellomeds.ui.components.stock.preview.autoLayout
import me.juliana.hellomeds.ui.util.displayNameRes
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.math.ceil
import kotlin.math.roundToInt

private val MIN_EDITOR_CELL = 48.dp

// Capping cell width prevents the editor grid from blowing up to absurd sizes in portrait or
// on tablets. With small column counts the grid would otherwise stretch each cell to ~100dp+,
// which makes the layout feel disjointed compared to the read-only preview at the top of the
// screen. The cap keeps the editor compact while staying above the 48dp touch-target floor.
private val MAX_EDITOR_CELL = 64.dp
private val EDITOR_CELL_SPACING = 6.dp

/**
 * Editor sub-screen for the bubble preview's grid layout. Users pick between auto and manual, set
 * the column count, choose the consumption flow direction, and (in manual mode) drag bubbles to
 * place empty spacer slots.
 *
 * Changes auto-save inline through [onUpdateLayout].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleLayoutEditorScreen(
    medication: Medication,
    onNavigateBack: () -> Unit,
    onUpdateLayout: (manualLayout: String?, flow: BubbleFlowDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val packagingQty = medication.packagingQuantity?.toInt() ?: 0
    val autoSeed = remember(packagingQty) { autoLayout(packagingQty) }

    // All edit state keys on medication.id only. Each persist via onUpdateLayout triggers the
    // parent flow to re-emit a new `medication` with the value we just wrote — if we keyed on
    // `bubbleManualLayout` or `bubbleFlowDirection`, that round-trip would reset our in-memory
    // state mid-drag, jumping slot ids back to their initial positions and flickering. Local
    // edits flow one-way: editor → DB.
    var flow by remember(medication.id) {
        mutableStateOf(medication.bubbleFlowDirection)
    }
    var mode by remember(medication.id) {
        val initial = decodeManualLayout(medication, packagingQty)
        mutableStateOf(if (initial != null) LayoutMode.MANUAL else LayoutMode.AUTO)
    }
    var columns by remember(medication.id) {
        val initial = decodeManualLayout(medication, packagingQty)
        val maxCols = effectiveMaxColumns(packagingQty)
        mutableStateOf((initial?.columns ?: autoSeed.columns).coerceIn(MIN_COLUMNS, maxCols))
    }
    val slots: SnapshotStateList<Slot> = remember(medication.id) {
        val initial = decodeManualLayout(medication, packagingQty)
        val maxCols = effectiveMaxColumns(packagingQty)
        val initialColumns = (initial?.columns ?: autoSeed.columns).coerceIn(MIN_COLUMNS, maxCols)
        val cells = computeCells(packagingQty, initialColumns)
        val initialSpacers = (initial?.spacerIndices ?: autoSeed.spacerIndices).toSet()
        mutableStateListOf<Slot>().apply {
            addAll(buildSlots(cells, initialSpacers))
        }
    }

    // Persist on every relevant change (mode, columns, slot reorder, flow).
    LaunchedEffect(mode, columns, slots.toList(), flow) {
        val payload = when (mode) {
            LayoutMode.AUTO -> null
            LayoutMode.MANUAL -> {
                val spacerIndices = slots.indices.filter { slots[it].kind is SlotKind.Spacer }
                BubbleLayoutCodec.encode(ManualLayout(columns, spacerIndices))
            }
        }
        onUpdateLayout(payload, flow)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.stock_layout_editor_title)) },
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
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // --- Live preview ---
            val previewLayout = when (mode) {
                LayoutMode.AUTO -> null
                LayoutMode.MANUAL -> {
                    val spacerIndices = slots.indices.filter { slots[it].kind is SlotKind.Spacer }
                    val rows = ceil(packagingQty.toDouble() / columns).toInt().coerceAtLeast(1)
                    GridLayout(rows = rows, columns = columns, spacerIndices = spacerIndices)
                }
            }
            // Show ~2/3 remaining (rounded) so a chunk of taken bubbles is visible at the
            // first-taken end — that's how the user can see which bubble flows in which direction.
            // For qty ≤ 1 there's no order to show, so leave it fully filled.
            val previewRemaining = if (packagingQty <= 1) {
                packagingQty
            } else {
                (packagingQty * 2.0 / 3.0).roundToInt().coerceIn(1, packagingQty - 1)
            }
            BubbleStockPreview(
                totalQuantity = packagingQty,
                remainingQuantity = previewRemaining,
                layoutOverride = previewLayout,
                flowDirection = flow,
                modifier = Modifier
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .heightIn(max = 160.dp),
            )

            // --- Mode toggle ---
            ModeToggle(
                mode = mode,
                onModeChange = { newMode ->
                    mode = newMode
                    if (newMode == LayoutMode.MANUAL) {
                        // Seed manual edits with the current auto picks so users start from a sane state.
                        columns = autoSeed.columns.coerceIn(MIN_COLUMNS, effectiveMaxColumns(packagingQty))
                        val cells = computeCells(packagingQty, columns)
                        val seedSpacers = autoSeed.spacerIndices.toSet()
                        slots.clear()
                        slots.addAll(buildSlots(cells, seedSpacers))
                    }
                },
            )

            // --- Flow direction (visible in both modes) ---
            FlowDirectionToggle(
                flow = flow,
                onFlowChange = { flow = it },
            )

            // --- Manual-only controls ---
            if (mode == LayoutMode.MANUAL) {
                val effectiveMaxCols = effectiveMaxColumns(packagingQty)
                ColumnStepper(
                    columns = columns,
                    canDecrease = columns > MIN_COLUMNS && columnsFitWithMinusOne(packagingQty, columns),
                    canIncrease = columns < effectiveMaxCols,
                    onChange = { newCols ->
                        val cells = computeCells(packagingQty, newCols)
                        // Preserve in-range spacer positions; append fresh ones at the end if cell count grew.
                        val currentSpacers = slots.indices
                            .filter { slots[it].kind is SlotKind.Spacer }
                            .toSet()
                        val preservedSpacers = currentSpacers.filter { it < cells }.toMutableSet()
                        val desiredSpacerCount = cells - packagingQty
                        // Add spacers from the end inward if we need more; drop the highest if too many.
                        var idx = cells - 1
                        while (preservedSpacers.size < desiredSpacerCount && idx >= 0) {
                            if (idx !in preservedSpacers) preservedSpacers.add(idx)
                            idx--
                        }
                        while (preservedSpacers.size > desiredSpacerCount) {
                            preservedSpacers.remove(preservedSpacers.max())
                        }
                        columns = newCols
                        slots.clear()
                        slots.addAll(buildSlots(cells, preservedSpacers))
                    },
                )

                GridEditor(
                    slots = slots,
                    columns = columns,
                    onSwap = { from, to ->
                        if (from in slots.indices && to in slots.indices && from != to) {
                            val tmp = slots[from]
                            slots[from] = slots[to]
                            slots[to] = tmp
                        }
                    },
                )

                Text(
                    text = stringResource(Res.string.stock_layout_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeToggle(mode: LayoutMode, onModeChange: (LayoutMode) -> Unit) {
    val entries = listOf(
        LayoutMode.AUTO to stringResource(Res.string.stock_layout_mode_auto),
        LayoutMode.MANUAL to stringResource(Res.string.stock_layout_mode_manual),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, (value, label) ->
            ToggleButton(
                checked = mode == value,
                onCheckedChange = { onModeChange(value) },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FlowDirectionToggle(flow: BubbleFlowDirection, onFlowChange: (BubbleFlowDirection) -> Unit) {
    val entries = BubbleFlowDirection.entries
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.stock_layout_flow_direction),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            entries.forEachIndexed { index, direction ->
                ToggleButton(
                    checked = flow == direction,
                    onCheckedChange = { onFlowChange(direction) },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(direction.displayNameRes),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnStepper(columns: Int, canDecrease: Boolean, canIncrease: Boolean, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.stock_layout_columns),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange((columns - 1).coerceAtLeast(MIN_COLUMNS)) },
            enabled = canDecrease,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(Res.string.stock_layout_column_decrease),
            )
        }
        Text(
            text = columns.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = { onChange((columns + 1).coerceAtMost(MAX_COLUMNS)) },
            enabled = canIncrease,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(Res.string.stock_layout_column_increase),
            )
        }
    }
}

@Composable
private fun GridEditor(slots: List<Slot>, columns: Int, onSwap: (from: Int, to: Int) -> Unit) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val cellSpacingPx = with(density) { EDITOR_CELL_SPACING.toPx() }

    // Three sizing regimes:
    //   - needsScroll: natural cell width would dip below MIN_EDITOR_CELL → switch to fixed
    //     MIN_EDITOR_CELL cells and put the grid inside a horizontal scroller.
    //   - capped: natural width exceeds MAX_EDITOR_CELL → clamp to MAX_EDITOR_CELL and centre
    //     the grid horizontally (keeps it compact on portrait phones and tablets).
    //   - natural: cell width fits within [MIN, MAX] → fill the available width.
    // The explicit `.width(gridWidthDp)` is mandatory whenever the grid sits inside any
    // horizontalScroll parent (otherwise GridCells.Fixed receives Infinity / N and crashes).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val minCellPx = with(density) { MIN_EDITOR_CELL.toPx() }
        val maxCellPx = with(density) { MAX_EDITOR_CELL.toPx() }
        val naturalCellWidthPx = (maxWidthPx - cellSpacingPx * (columns - 1)) / columns
        val needsScroll = naturalCellWidthPx < minCellPx
        val cellWidthDp = when {
            needsScroll -> MIN_EDITOR_CELL
            naturalCellWidthPx > maxCellPx -> MAX_EDITOR_CELL
            else -> maxWidth / columns
        }
        val gridWidthDp = cellWidthDp * columns + EDITOR_CELL_SPACING * (columns - 1)
        // Cells are square (Modifier.size(cellWidthDp)) so cell height == cellWidthDp. Compute
        // the grid's exact natural height so the LazyVerticalGrid doesn't try to lazy-clip its
        // overflow inside the parent verticalScroll — every row gets a real, measured slot and
        // the page scroll takes over when there are too many rows to fit.
        val rowCount = ceil(slots.size.toDouble() / columns).toInt().coerceAtLeast(1)
        val gridHeightDp = cellWidthDp * rowCount + EDITOR_CELL_SPACING * (rowCount - 1).coerceAtLeast(0)

        val lazyGridState = rememberLazyGridState()
        val reorderableState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSwap(from.index, to.index)
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = if (needsScroll) {
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                contentAlignment = Alignment.Center,
            ) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .width(gridWidthDp)
                        .height(gridHeightDp),
                    horizontalArrangement = Arrangement.spacedBy(EDITOR_CELL_SPACING),
                    verticalArrangement = Arrangement.spacedBy(EDITOR_CELL_SPACING),
                ) {
                    items(items = slots, key = { it.id }) { slot ->
                        ReorderableItem(reorderableState, key = slot.id) { _ ->
                            // draggableHandle (not longPressDraggableHandle): drag starts on
                            // touch-down so users don't have to hold to begin a swap.
                            val dragModifier = Modifier
                                .size(cellWidthDp)
                                .draggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                )
                            EditorCell(
                                kind = slot.kind,
                                size = cellWidthDp,
                                modifier = dragModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorCell(kind: SlotKind, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pillColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val dashOnPx = with(density) { 3.dp.toPx() }
    val dashOffPx = with(density) { 2.dp.toPx() }
    val strokePx = with(density) { 1.5.dp.toPx() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size).padding(2.dp)) {
            val radius = (this.size.minDimension - strokePx) / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            // Match the small-circle radius of the read-only BubbleStockPreview so the editor
            // mirrors the final visual. Both kinds render at this radius with no pouch behind
            // them — the cell sits on the surface directly, keeping the editor visually quiet.
            val dotRadius = radius * 0.55f
            when (kind) {
                is SlotKind.Pill -> {
                    drawCircle(color = pillColor, radius = dotRadius, center = center)
                }
                is SlotKind.Spacer -> {
                    // Same diameter as a pill dot. The fill plus the dashed stroke share the
                    // outline color so it reads as one muted element.
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.35f),
                        radius = dotRadius,
                        center = center,
                    )
                    drawCircle(
                        color = outlineColor,
                        radius = dotRadius,
                        center = center,
                        style = Stroke(
                            width = strokePx,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(dashOnPx, dashOffPx),
                                0f,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

private fun decodeManualLayout(medication: Medication, packagingQty: Int): ManualLayout? = medication.bubbleManualLayout
    ?.let { BubbleLayoutCodec.decode(it) }
    ?.takeIf { it.isValidFor(packagingQty) }

/**
 * Largest column count that still produces at least two rows for the given pill count.
 * Capped at [MAX_COLUMNS] and floored at [MIN_COLUMNS] so the stepper always has at least one
 * valid value (degenerate qty<4 cases collapse to MIN_COLUMNS).
 */
private fun effectiveMaxColumns(packagingQty: Int): Int {
    if (packagingQty <= 0) return MIN_COLUMNS
    return ceil(packagingQty / 2.0).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
}

private fun computeCells(packagingQty: Int, columns: Int): Int {
    if (packagingQty <= 0 || columns <= 0) return 0
    val rows = ceil(packagingQty.toDouble() / columns).toInt()
    return rows * columns
}

private fun columnsFitWithMinusOne(packagingQty: Int, columns: Int): Boolean {
    // Allow decrease only if it leaves a sane grid (rows ≤ 30) — prevents unbounded vertical growth.
    if (columns - 1 < MIN_COLUMNS) return false
    val rows = ceil(packagingQty.toDouble() / (columns - 1)).toInt()
    return rows <= 30
}

/**
 * Each grid cell carries a stable [id] that moves with the content during a swap. The reorderable
 * library tracks the dragged item by `key`; without an identity that follows the content, the
 * library re-pins the drag ghost at the old index and the layout visibly snaps back and forth
 * between recompositions (the "flicker" bug). Indexes alone don't work as keys for this reason.
 */
private data class Slot(val id: Int, val kind: SlotKind)

private fun buildSlots(cells: Int, spacers: Set<Int>): List<Slot> = List(cells) { idx ->
    Slot(id = idx, kind = if (idx in spacers) SlotKind.Spacer else SlotKind.Pill)
}

private enum class LayoutMode { AUTO, MANUAL }

private sealed interface SlotKind {
    object Pill : SlotKind
    object Spacer : SlotKind
}
