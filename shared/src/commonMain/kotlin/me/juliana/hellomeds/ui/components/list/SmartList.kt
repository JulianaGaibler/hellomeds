// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_error_invalid_input
import me.juliana.hellomeds.shared.content_description_dropdown
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.SegmentedListGap
import me.juliana.hellomeds.ui.compat.segmentedListItemShapes
import org.jetbrains.compose.resources.stringResource

/**
 * Input transformation that only allows integer input.
 * @param allowNegative When true, permits an optional leading minus sign.
 */
class IntegerInputTransformation(private val allowNegative: Boolean = false) : InputTransformation {
    override val keyboardOptions: KeyboardOptions =
        KeyboardOptions(
            keyboardType = KeyboardType.Number,
        )

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        val valid = if (allowNegative) {
            text.all { it.isDigit() || it == '-' } &&
                text.count { it == '-' } <= 1 &&
                (text.indexOf('-') <= 0) // minus only at position 0
        } else {
            text.all { it.isDigit() }
        }
        if (!valid) {
            revertAllChanges()
            return
        }
        // Strip leading zeros (keep standalone "0")
        val str = text.toString()
        val startIndex = if (str.startsWith("-")) 1 else 0
        val numPart = str.substring(startIndex)
        if (numPart.length > 1 && numPart.startsWith("0")) {
            val stripped = numPart.trimStart('0').ifEmpty { "0" }
            val result = if (startIndex > 0) "-$stripped" else stripped
            replace(0, length, result)
            placeCursorAtEnd()
        }
    }
}

/**
 * Input transformation that allows decimal numbers with locale-aware decimal separator
 * Only allows the decimal separator from the system locale (e.g., '.' in US, ',' in Europe)
 */
class DecimalInputTransformation : InputTransformation {
    override val keyboardOptions: KeyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Decimal)

    private val decimalSeparator =
        '.' // KMP-safe default; locale formatting handled by Formatters layer

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()

        // Count decimal separators (only the locale-specific one)
        val separatorCount = text.count { it == decimalSeparator }

        // Revert if invalid: more than one separator, or contains invalid characters
        if (separatorCount > 1 || !text.all {
                it.isDigit() || it == decimalSeparator || (
                    it == '-' && text.indexOf(
                        it,
                    ) == 0
                    )
            }
        ) {
            revertAllChanges()
            return
        }
        // Strip leading zeros (keep "0", "0." prefix, and "-0.x")
        val startIndex = if (text.startsWith("-")) 1 else 0
        val numPart = text.substring(startIndex)
        if (numPart.length > 1 && numPart.startsWith("0") && !numPart.startsWith("0$decimalSeparator")) {
            val stripped = numPart.trimStart('0').ifEmpty { "0" }
            val result = if (startIndex > 0) "-$stripped" else stripped
            replace(0, length, result)
            placeCursorAtEnd()
        }
    }
}

/**
 * A container for grouped smart list items with proper spacing and border radius.
 * Uses Material3 SegmentedGap for standard spacing between segmented items.
 */

@Composable
fun SmartList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SegmentedListGap),
    ) {
        content()
    }
}

/**
 * Computes segmented shapes for SmartList items, with proper rounded corners for single items.
 * Material3's [ListItemDefaults.segmentedShapes] returns default (small) corners when count <= 1.
 * This helper ensures standalone items get fully rounded corners matching the segmented style.
 */

@Composable
fun smartListSegmentedShapes(index: Int, count: Int): ListItemShapes {
    if (count <= 1) {
        val shape = RoundedCornerShape(16.dp)
        return ListItemShapes(
            shape = shape,
            selectedShape = shape,
            pressedShape = shape,
            focusedShape = shape,
            hoveredShape = shape,
            draggedShape = shape,
        )
    }
    return segmentedListItemShapes(index = index, count = count)
}

/**
 * Builds a SmartList with automatic position calculation and divider insertion.
 * This is the recommended way to create smart lists as it handles all positioning logic automatically.
 * Uses Material3 segmentedShapes for automatic corner radius calculation.
 *
 * Example usage:
 * ```
 * AutoSmartList(
 *     items = listOf(
 *         SmartListItemConfig(visible = true) { shapes, visible ->
 *             SmartListItem(
 *                 headlineContent = { Text("Option 1") },
 *                 shapes = shapes,
 *                 visible = visible
 *             )
 *         },
 *         SmartListItemConfig(visible = shouldShowOption2) { shapes, visible ->
 *             SmartListItem(
 *                 headlineContent = { Text("Option 2") },
 *                 shapes = shapes,
 *                 visible = visible
 *             )
 *         }
 *     )
 * )
 * ```
 *
 * The list automatically:
 * - Filters out non-visible items
 * - Calculates correct segmented shapes based on visible items
 * - Adds dividers between items
 * - Animates items in/out when visibility changes
 */

@Composable
fun AutoSmartList(modifier: Modifier = Modifier, items: List<SmartListItemConfig>) {
    // Calculate positions based on visible items only
    val visibleCount = items.count { it.visible }
    var currentVisibleIndex = 0

    SmartList(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val shapes = if (item.visible) {
                val s = smartListSegmentedShapes(
                    index = currentVisibleIndex,
                    count = visibleCount,
                )
                currentVisibleIndex++
                s
            } else {
                smartListSegmentedShapes(index = 0, count = 1)
            }

            // Always render the item, let AnimatedVisibility handle the animation
            item.content(shapes, item.visible)

            // Add divider after visible items (except the last one)
            if (item.visible && currentVisibleIndex < visibleCount) {
                SmartListDivider()
            }
        }
    }
}

/**
 * Configuration for a single settings item.
 *
 * @param visible Whether this item should be shown. When false, the item animates out.
 * @param content The composable content for this item, receiving the calculated shapes and visibility state.
 */

data class SmartListItemConfig(
    val visible: Boolean = true,
    val content: @Composable (shapes: ListItemShapes, visible: Boolean) -> Unit,
)

/**
 * Individual setting list item with Material 3 styling.
 * Use within SmartList or AutoSmartList for proper grouping and corner radius.
 *
 * @param shapes The Material3 ListItemShapes for this item's position in the list
 * @param visible Whether this item should be visible (for animations)
 * @param onClick Optional click handler to make the item clickable
 */

@Composable
fun SmartListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    visible: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    // Track if this item has ever transitioned from invisible to visible
    val hasAnimated = remember { mutableStateOf(!visible) }

    // Update animation state when visibility changes
    LaunchedEffect(visible) {
        if (visible && !hasAnimated.value) {
            // First time becoming visible - mark as animated
            hasAnimated.value = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (!hasAnimated.value) {
            // First time visible - no animation
            fadeIn(tween(0)) + expandVertically(tween(0))
        } else {
            // Subsequent visibility changes - animate normally
            fadeIn() + expandVertically()
        },
        exit = fadeOut() + shrinkVertically(),
    ) {
        val itemModifier = modifier
            .clip(shapes.shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )

        ListItem(
            headlineContent = headlineContent,
            modifier = itemModifier,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
    }
}

/**
 * LazyColumn-compatible version of SmartListItem for use with Modifier.animateItem().
 * This version does NOT wrap content in AnimatedVisibility, making it suitable for
 * direct use as a child of LazyColumn items() where the item animation is handled
 * by Modifier.animateItem() instead.
 *
 * Use this in LazyColumn contexts where you need smooth enter/exit/reorder animations.
 * For non-lazy contexts (like settings screens), continue using the regular SmartListItem.
 *
 * @param shapes The Material3 ListItemShapes for this item's position in the list
 * @param onClick Optional click handler to make the item clickable
 */

@Composable
fun LazySmartListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    onClick: (() -> Unit)? = null,
) {
    val itemModifier = modifier
        .clip(shapes.shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        )

    ListItem(
        headlineContent = headlineContent,
        modifier = itemModifier,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
}

/**
 * A specialized list item for text input fields on the right side.
 * The text field has no visible border and matches the text style of list items.
 *
 * @param shapes The Material3 ListItemShapes for this item's position in the list
 * @param suffix Optional text to display after the input field (e.g., "minutes")
 * @param supportingText Optional supporting text to display below the label
 * @param visible Whether this item should be visible (for animations)
 * @param validator Optional validation function that returns true if the value is valid (for form validation, not visual feedback)
 * @param inputTransformation Optional input transformation to filter/transform user input (e.g., IntegerInputTransformation(), DecimalInputTransformation())
 */

@Composable
fun SmartListTextItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    suffix: String? = null,
    supportingText: String? = null,
    visible: Boolean = true,
    validator: ((String) -> Boolean)? = null,
    inputTransformation: InputTransformation? = null,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    val textFieldState = rememberTextFieldState(initialText = value)
    var hasError by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    // Extract string resource for accessibility (must be in composable context)
    val errorMessage = stringResource(Res.string.accessibility_error_invalid_input)

    // Sync external value changes to TextFieldState
    LaunchedEffect(value) {
        if (textFieldState.text.toString() != value) {
            textFieldState.edit {
                replace(0, length, value)
                placeCursorAtEnd()
            }
        }
    }

    // Sync TextFieldState changes to external state and validate
    LaunchedEffect(textFieldState.text) {
        val currentText = textFieldState.text.toString()
        if (currentText != value) {
            onValueChange(currentText)
        }
        // Update error state if validator is provided
        hasError = validator != null && currentText.isNotEmpty() && !validator(currentText)
    }

    // Track if this item has ever transitioned from invisible to visible
    val hasAnimated = remember { mutableStateOf(!visible) }

    // Update animation state when visibility changes
    LaunchedEffect(visible) {
        if (visible && !hasAnimated.value) {
            hasAnimated.value = true
        }
    }

    // Position cursor at end when field gains focus (with delay for tap handling)
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(50) // Wait for BasicTextField's tap handling to complete
            textFieldState.edit { placeCursorAtEnd() }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (!hasAnimated.value) {
            fadeIn(tween(0)) + expandVertically(tween(0))
        } else {
            fadeIn() + expandVertically()
        },
        exit = fadeOut() + shrinkVertically(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            supportingContent = supportingText?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    BasicTextField(
                        state = textFieldState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            }
                            .then(
                                if (hasError) {
                                    Modifier.semantics {
                                        error(errorMessage)
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                        textStyle = LocalTextStyle.current.copy(
                            color = if (hasError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.End,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = keyboardOptions,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        inputTransformation = inputTransformation,
                    )
                    if (suffix != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (trailingAction != null) {
                        trailingAction()
                    }
                }
            },
            modifier = modifier.clip(shapes.shape),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
    }
}

/**
 * A smart list item with a dropdown menu for selecting from predefined options.
 */

@Composable
fun SmartListDropdownItem(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    visible: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    // Track if this item has ever transitioned from invisible to visible
    val hasAnimated = remember { mutableStateOf(!visible) }

    // Update animation state when visibility changes
    LaunchedEffect(visible) {
        if (visible && !hasAnimated.value) {
            hasAnimated.value = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (!hasAnimated.value) {
            fadeIn(tween(0)) + expandVertically(tween(0))
        } else {
            fadeIn() + expandVertically()
        },
        exit = fadeOut() + shrinkVertically(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            trailingContent = {
                Box {
                    TextButton(
                        onClick = { expanded = true },
                    ) {
                        Text(
                            text = selectedValue,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(Res.string.content_description_dropdown),
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onValueChange(option)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            },
            modifier = modifier.clip(shapes.shape),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
    }
}

/**
 * A divider to be used between list items within SmartList.
 * This is invisible and only provides spacing between items.
 */
@Composable
fun SmartListDivider() {
    Spacer(modifier = Modifier)
}

/**
 * Standardized header for SmartList sections.
 */
@Composable
fun SmartListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    )
}

/**
 * Subsection label with optional description for grouping items within a section.
 * Smaller than SmartListHeader, suitable for creating visual groups within a larger section.
 */
@Composable
fun SmartListLabel(text: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================================
// Specialized SmartList Item Types
// ============================================================================

/**
 * SmartList item with a switch control
 */

@Composable
fun SmartListSwitchItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingText: String? = null,
    visible: Boolean = true,
) {
    SmartListItem(
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { { Text(it) } },
        trailingContent = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        shapes = shapes,
        visible = visible,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        modifier = modifier,
    )
}

/**
 * SmartList item with a radio button
 */

@Composable
fun SmartListRadioItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    visible: Boolean = true,
) {
    SmartListItem(
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { { Text(it) } },
        leadingContent = {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = onClick,
            )
        },
        shapes = shapes,
        visible = visible,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * SmartList item that navigates to another screen (with trailing arrow)
 */

@Composable
fun SmartListNavigationItem(
    label: String,
    onClick: () -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    supportingText: String? = null,
    trailingText: String? = null,
    visible: Boolean = true,
) {
    SmartListItem(
        headlineContent = { Text(label) },
        supportingContent = supportingText?.let { { Text(it) } },
        leadingContent = leadingIcon,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        shapes = shapes,
        visible = visible,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * An info card within a SmartList that displays important messages with custom colors.
 * Properly integrates with SmartList's border radius and animations.
 *
 * @param headlineContent The main title/headline text
 * @param supportingContent Optional body text and additional content (buttons, etc.)
 * @param containerColor The background color of the card
 * @param contentColor The text color for content inside the card
 * @param shapes The Material3 ListItemShapes for this item's position in the list
 * @param visible Whether this item should be visible (for animations)
 */

@Composable
fun SmartListInfoCard(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    shapes: ListItemShapes = smartListSegmentedShapes(index = 0, count = 1),
    visible: Boolean = true,
) {
    // Track if this item has ever transitioned from invisible to visible
    val hasAnimated = remember { mutableStateOf(!visible) }

    // Update animation state when visibility changes
    LaunchedEffect(visible) {
        if (visible && !hasAnimated.value) {
            hasAnimated.value = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (!hasAnimated.value) {
            fadeIn(tween(0)) + expandVertically(tween(0))
        } else {
            fadeIn() + expandVertically()
        },
        exit = fadeOut() + shrinkVertically(),
    ) {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shapes.shape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            ListItem(
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                colors = ListItemDefaults.colors(
                    containerColor = containerColor,
                    headlineColor = contentColor,
                    supportingColor = contentColor,
                ),
            )
        }
    }
}
