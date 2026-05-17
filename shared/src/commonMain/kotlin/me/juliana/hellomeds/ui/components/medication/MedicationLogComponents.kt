// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_action_collapse
import me.juliana.hellomeds.shared.accessibility_action_expand
import me.juliana.hellomeds.shared.accessibility_not_selected
import me.juliana.hellomeds.shared.accessibility_selected
import me.juliana.hellomeds.shared.log_medication_status_skipped
import me.juliana.hellomeds.shared.log_medication_status_taken
import me.juliana.hellomeds.ui.compat.ButtonGroupDefaults
import me.juliana.hellomeds.ui.compat.ListItemShapes
import me.juliana.hellomeds.ui.compat.ToggleButton
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.formatDecimalPlain
import me.juliana.hellomeds.ui.util.formatMedicationTypeAndStrength
import me.juliana.hellomeds.ui.util.getDoseUnitPluralRes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** When [onToggle] is non-null the item becomes clickable and shows an expand/collapse arrow. */
@Composable
fun medicationInfoItem(
    medication: Medication,
    isExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
): SmartListItemConfig {
    platformContext()
    val displayName = medication.displayName?.takeIf { it.isNotBlank() } ?: medication.name
    val typeAndStrength = formatMedicationTypeAndStrength(medication)

    val foregroundShape = MedicationForegroundShape.fromNameOrDefault(medication.foregroundShape)
    val backgroundShape = MedicationBackgroundShape.fromNameOrDefault(medication.backgroundShape)

    val color1 = medication.shapeColor?.let {
        MedicationColor.fromName(it)
    }

    return SmartListItemConfig(visible = true) { shapes, visible ->
        SmartListItem(
            headlineContent = { Text(displayName) },
            supportingContent = { Text(typeAndStrength) },
            leadingContent = {
                MedicationShapeIcon(
                    foregroundShape = foregroundShape,
                    backgroundShape = backgroundShape,
                    color1 = color1,
                    size = 40.dp,
                )
            },
            trailingContent = if (onToggle != null && isExpanded != null) {
                {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) {
                            stringResource(Res.string.accessibility_action_collapse)
                        } else {
                            stringResource(Res.string.accessibility_action_expand)
                        },
                    )
                }
            } else {
                null
            },
            shapes = shapes,
            visible = visible,
            onClick = onToggle,
        )
    }
}

/**
 * Dose input list item for medication logging
 */

@Composable
fun DoseInputListItem(
    dose: Double,
    medicationType: MedicationType,
    onDoseChange: (Double) -> Unit,
    shapes: ListItemShapes,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val doseUnitRes = getDoseUnitPluralRes(medicationType)
    val doseQuantity = dose.toInt()
    val doseLabel = pluralStringResource(doseUnitRes, doseQuantity)
        .replaceFirstChar { it.uppercase() }

    SmartListTextItem(
        label = doseLabel,
        value = if (dose == 0.0) "" else formatDecimalPlain(dose),
        onValueChange = { value ->
            value.toDoubleOrNull()?.let { onDoseChange(it) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shapes = shapes,
        validator = { text ->
            text.isEmpty() || text.toDoubleOrNull()?.let { it > 0 } == true
        },
        inputTransformation = DecimalInputTransformation(),
        visible = visible,
        modifier = modifier,
    )
}

/**
 * Status toggle button row for scheduled medications
 */

@Composable
fun StatusSegmentedButton(
    selectedStatus: LogStatus,
    onStatusChange: (LogStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedText = stringResource(Res.string.accessibility_selected)
    val notSelectedText = stringResource(Res.string.accessibility_not_selected)

    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = selectedStatus == LogStatus.SKIPPED,
            onCheckedChange = { onStatusChange(LogStatus.SKIPPED) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics {
                    stateDescription =
                        if (selectedStatus == LogStatus.SKIPPED) selectedText else notSelectedText
                },
        ) {
            Text(
                stringResource(Res.string.log_medication_status_skipped),
                textAlign = TextAlign.Center,
            )
        }
        ToggleButton(
            checked = selectedStatus == LogStatus.TAKEN,
            onCheckedChange = { onStatusChange(LogStatus.TAKEN) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics {
                    stateDescription =
                        if (selectedStatus == LogStatus.TAKEN) selectedText else notSelectedText
                },
        ) {
            Text(
                stringResource(Res.string.log_medication_status_taken),
                textAlign = TextAlign.Center,
            )
        }
    }
}
