// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_from_camera
import me.juliana.hellomeds.shared.camera_detection_medication_strength
import me.juliana.hellomeds.shared.color_blue
import me.juliana.hellomeds.shared.color_chartreuse
import me.juliana.hellomeds.shared.color_cyan
import me.juliana.hellomeds.shared.color_green
import me.juliana.hellomeds.shared.color_indigo
import me.juliana.hellomeds.shared.color_monochrome
import me.juliana.hellomeds.shared.color_none
import me.juliana.hellomeds.shared.color_orange
import me.juliana.hellomeds.shared.color_pink
import me.juliana.hellomeds.shared.color_purple
import me.juliana.hellomeds.shared.color_red
import me.juliana.hellomeds.shared.color_rose
import me.juliana.hellomeds.shared.color_teal
import me.juliana.hellomeds.shared.color_yellow
import me.juliana.hellomeds.shared.medication_color_primary
import me.juliana.hellomeds.shared.medication_shape_background
import me.juliana.hellomeds.shared.medication_shape_foreground
import me.juliana.hellomeds.shared.medication_strength_placeholder
import me.juliana.hellomeds.ui.compat.MaterialShapes
import me.juliana.hellomeds.ui.compat.MedicationIconPreviewContent
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.SmartListHeader
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListRadioItem
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.displayNameRes
import me.juliana.hellomeds.ui.util.toBackgroundColor
import me.juliana.hellomeds.ui.util.toForegroundColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable medication type selector with radio buttons
 */

@Composable
fun MedicationTypeSelector(
    selectedType: MedicationType,
    onTypeSelected: (MedicationType) -> Unit,
    modifier: Modifier = Modifier,
    detectedTypes: List<MedicationType> = emptyList(),
) {
    val allTypes = MedicationType.entries.map { type ->
        type to stringResource(type.displayNameRes)
    }

    val commonTypes = listOf(MedicationType.TABLET, MedicationType.CAPSULE, MedicationType.LIQUID)

    // Camera-detected types take precedence; otherwise fall back to the common-types shortlist.
    val topGroupTypes = if (detectedTypes.isNotEmpty()) {
        allTypes.filter { (type, _) -> type in detectedTypes }
    } else {
        allTypes.filter { (type, _) -> type in commonTypes }
    }

    val topGroupTypeEnums = topGroupTypes.map { it.first }
    val remainingTypes = allTypes.filter { (type, _) -> type !in topGroupTypeEnums }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (topGroupTypes.isNotEmpty()) {
            Column {
                // Header only fires for camera-detected types, not the fallback common-types group.
                if (detectedTypes.isNotEmpty()) {
                    SmartListHeader(text = stringResource(Res.string.camera_detection_from_camera))
                }

                AutoSmartList(
                    items = topGroupTypes.map { (type, label) ->
                        SmartListItemConfig { shapes, visible ->
                            SmartListRadioItem(
                                label = label,
                                selected = selectedType == type,
                                onClick = { onTypeSelected(type) },
                                shapes = shapes,
                                visible = visible,
                            )
                        }
                    },
                )
            }
        }

        // Show remaining types
        if (remainingTypes.isNotEmpty()) {
            AutoSmartList(
                items = remainingTypes.map { (type, label) ->
                    SmartListItemConfig { shapes, visible ->
                        SmartListRadioItem(
                            label = label,
                            selected = selectedType == type,
                            onClick = { onTypeSelected(type) },
                            shapes = shapes,
                            visible = visible,
                        )
                    }
                },
            )
        }
    }
}

/**
 * Reusable strength input fields (value + unit)
 */

@Composable
fun StrengthInputFields(
    strengthValue: String,
    strengthUnit: MedicationStrengthUnit,
    onStrengthValueChange: (String) -> Unit,
    onStrengthUnitChange: (MedicationStrengthUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = MedicationStrengthUnit.entries.map { unit ->
        unit to unit.value
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextField(
            value = strengthValue,
            onValueChange = { newValue ->
                // Only allow digits and one decimal point
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onStrengthValueChange(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.camera_detection_medication_strength)) },
            placeholder = { Text(stringResource(Res.string.medication_strength_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        AutoSmartList(
            items = units.map { (unitEnum, unitLabel) ->
                SmartListItemConfig { shapes, visible ->
                    SmartListRadioItem(
                        label = unitLabel,
                        selected = strengthUnit == unitEnum,
                        onClick = { onStrengthUnitChange(unitEnum) },
                        shapes = shapes,
                        visible = visible,
                    )
                }
            },
        )
    }
}

/**
 * Reusable foreground shape selection grid
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForegroundShapeGrid(
    selectedShape: MedicationForegroundShape,
    onShapeSelected: (MedicationForegroundShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val foregroundShapes = MedicationForegroundShape.entries.toList()

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = Int.MAX_VALUE,
    ) {
        foregroundShapes.forEach { shape ->
            ShapeGridItem(
                shape = shape,
                isSelected = selectedShape == shape,
                onClick = { onShapeSelected(shape) },
                showBackground = false,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 90.dp),
            )
        }
    }
}

/**
 * Reusable background shape selection grid
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundShapeGrid(
    selectedShape: MedicationBackgroundShape,
    onShapeSelected: (MedicationBackgroundShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundShapes = MedicationBackgroundShape.entries.toList()

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = Int.MAX_VALUE,
    ) {
        backgroundShapes.forEach { shape ->
            BackgroundShapeGridItem(
                shape = shape,
                isSelected = selectedShape == shape,
                onClick = { onShapeSelected(shape) },
                showForeground = false,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 90.dp),
            )
        }
    }
}

/**
 * Reusable color selection row with edge-to-edge layout and gradient masks
 */
@Composable
fun ColorSelectionRow(
    selectedColor: MedicationColor?,
    onColorSelected: (MedicationColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val showStartGradient by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 5
        }
    }
    val showEndGradient by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItemsCount = layoutInfo.totalItemsCount
            lastVisibleItem != null && (
                lastVisibleItem.index < totalItemsCount - 1 ||
                    lastVisibleItem.offset + lastVisibleItem.size > layoutInfo.viewportEndOffset + 5
                )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item {
                NullColorCircle(
                    isSelected = selectedColor == null,
                    onClick = { onColorSelected(null) },
                )
            }
            items(MedicationColor.all) { color ->
                ColorCircle(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorSelected(color) },
                )
            }
        }

        // Start gradient mask
        if (showStartGradient) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceContainer,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // End gradient mask
        if (showEndGradient) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(4.dp)
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ShapeGridItem(
    shape: MedicationForegroundShape,
    isSelected: Boolean,
    onClick: () -> Unit,
    showBackground: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shapeName = stringResource(shape.toNameRes())

    Card(
        onClick = onClick,
        modifier = modifier
            .semantics {
                contentDescription = shapeName
                selected = isSelected
                role = Role.RadioButton
            }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showBackground) {
                MedicationShapeIcon(
                    foregroundShape = shape,
                    backgroundShape = MedicationBackgroundShape.CIRCLE,
                    size = 48.dp,
                )
            } else {
                // Show foreground shape only
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val drawables = shape.toDrawables()
                    drawables.secondary?.let { secondary ->
                        Icon(
                            painter = painterResource(secondary),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp * 0.7f),
                        )
                    }
                    Icon(
                        painter = painterResource(drawables.primary),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(48.dp * 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundShapeGridItem(
    shape: MedicationBackgroundShape,
    isSelected: Boolean,
    onClick: () -> Unit,
    showForeground: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.medium,
                    )
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showForeground) {
                MedicationShapeIcon(
                    foregroundShape = MedicationForegroundShape.CAPSULE_PILL,
                    backgroundShape = shape,
                    size = 48.dp,
                )
            } else {
                // Show background shape only
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(shape.toComposeShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {}
            }
        }
    }
}

@Composable
private fun NullColorCircle(isSelected: Boolean, onClick: () -> Unit) {
    // Extract string resource for accessibility (must be in composable context)
    val colorNoneDescription = stringResource(Res.string.color_none)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = colorNoneDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            // Outer selection border (primary color) - 48dp
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            // White circle for contrast - 42dp
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, CircleShape),
            )
            // Inner circle with actual color - always 36dp
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "∅",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            // Unselected state - 48dp
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "∅",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: MedicationColor,
    isSelected: Boolean,
    onClick: () -> Unit,
    useBackgroundColor: Boolean = false,
) {
    val composeColor = if (useBackgroundColor) {
        color.toBackgroundColor()
    } else {
        color.toForegroundColor()
    }
    val colorName = stringResource(color.toColorNameRes())

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = colorName
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            // Outer selection border (primary color) - 48dp
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            // White circle for contrast - 42dp
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, CircleShape),
            )
            // Inner circle with actual color - always 36dp
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(composeColor, CircleShape),
            )
        } else {
            // Unselected state - 48dp
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(composeColor, CircleShape),
            )
        }
    }
}

// Helper functions
private fun MedicationColor.toColorNameRes() = when (this) {
    MedicationColor.Monochrome -> Res.string.color_monochrome
    MedicationColor.Rose -> Res.string.color_rose
    MedicationColor.Pink -> Res.string.color_pink
    MedicationColor.Red -> Res.string.color_red
    MedicationColor.Orange -> Res.string.color_orange
    MedicationColor.Yellow -> Res.string.color_yellow
    MedicationColor.Chartreuse -> Res.string.color_chartreuse
    MedicationColor.Green -> Res.string.color_green
    MedicationColor.Teal -> Res.string.color_teal
    MedicationColor.Cyan -> Res.string.color_cyan
    MedicationColor.Blue -> Res.string.color_blue
    MedicationColor.Indigo -> Res.string.color_indigo
    MedicationColor.Purple -> Res.string.color_purple
}

private fun MedicationBackgroundShape.toComposeShape(): Shape {
    val polygon = when (this) {
        MedicationBackgroundShape.CIRCLE -> MaterialShapes.Circle
        MedicationBackgroundShape.SQUARE -> MaterialShapes.Square
        MedicationBackgroundShape.SLANTED -> MaterialShapes.Slanted
        MedicationBackgroundShape.ARCH -> MaterialShapes.Arch
        MedicationBackgroundShape.PILL -> MaterialShapes.Pill
        MedicationBackgroundShape.DIAMOND -> MaterialShapes.Diamond
        MedicationBackgroundShape.CLAMSHELL -> MaterialShapes.ClamShell
        MedicationBackgroundShape.PENTAGON -> MaterialShapes.Pentagon
        MedicationBackgroundShape.GEM -> MaterialShapes.Gem
        MedicationBackgroundShape.SUNNY -> MaterialShapes.Sunny
        MedicationBackgroundShape.VERY_SUNNY -> MaterialShapes.VerySunny
        MedicationBackgroundShape.FOUR_SIDED_COOKIE -> MaterialShapes.Cookie4Sided
        MedicationBackgroundShape.SEVEN_SIDED_COOKIE -> MaterialShapes.Cookie7Sided
        MedicationBackgroundShape.TWELVE_SIDED_COOKIE -> MaterialShapes.Cookie12Sided
        MedicationBackgroundShape.FOUR_LEAF_CLOVER -> MaterialShapes.Clover4Leaf
        MedicationBackgroundShape.EIGHT_LEAF_CLOVER -> MaterialShapes.Clover8Leaf
        MedicationBackgroundShape.SOFT_BURST -> MaterialShapes.SoftBurst
        MedicationBackgroundShape.SOFT_BOOM -> MaterialShapes.SoftBoom
        MedicationBackgroundShape.FLOWER -> MaterialShapes.Flower
        MedicationBackgroundShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
        MedicationBackgroundShape.BUN -> MaterialShapes.Bun
    }
    return RoundedPolygonShape(polygon)
}

/**
 * Sticky preview card with transitioning medication icon
 */
@Composable
fun MedicationIconPreview(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MedicationIconPreviewContent(
                foregroundShape = foregroundShape,
                backgroundShape = backgroundShape,
                color1 = color1,
                size = 80.dp,
            )
        }
    }
}

/**
 * Medication shape pickers (foreground and background)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationShapePickers(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    onForegroundShapeChange: (MedicationForegroundShape) -> Unit,
    onBackgroundShapeChange: (MedicationBackgroundShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.medication_shape_foreground),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val foregroundShapes = MedicationForegroundShape.entries.toList()

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = Int.MAX_VALUE,
        ) {
            foregroundShapes.forEach { shape ->
                ShapeGridItem(
                    shape = shape,
                    isSelected = foregroundShape == shape,
                    onClick = { onForegroundShapeChange(shape) },
                    showBackground = false,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 90.dp),
                )
            }
        }

        Text(
            text = stringResource(Res.string.medication_shape_background),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val backgroundShapes = MedicationBackgroundShape.entries.toList()

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = Int.MAX_VALUE,
        ) {
            backgroundShapes.forEach { shape ->
                BackgroundShapeGridItem(
                    shape = shape,
                    isSelected = backgroundShape == shape,
                    onClick = { onBackgroundShapeChange(shape) },
                    showForeground = false,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 90.dp),
                )
            }
        }
    }
}

/**
 * Medication color picker (single color) displayed as a grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationColorPickers(
    color1: MedicationColor?,
    onColor1Change: (MedicationColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.medication_color_primary),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NullColorCircle(
                isSelected = color1 == null,
                onClick = { onColor1Change(null) },
            )
            MedicationColor.all.forEach { color ->
                ColorCircle(
                    color = color,
                    isSelected = color1 == color,
                    onClick = { onColor1Change(color) },
                    useBackgroundColor = false,
                )
            }
        }
    }
}
