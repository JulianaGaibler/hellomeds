// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.domain.validation.MedicationValidation
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.action_save
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.medication_has_strength
import me.juliana.hellomeds.shared.medication_name_label
import me.juliana.hellomeds.shared.medication_strength_unit
import me.juliana.hellomeds.shared.medication_strength_value
import me.juliana.hellomeds.shared.medication_type_label
import me.juliana.hellomeds.shared.screen_edit_base_data
import me.juliana.hellomeds.ui.components.common.AppScaffold
import me.juliana.hellomeds.ui.components.list.AutoSmartList
import me.juliana.hellomeds.ui.components.list.DecimalInputTransformation
import me.juliana.hellomeds.ui.components.list.SmartListDropdownItem
import me.juliana.hellomeds.ui.components.list.SmartListItem
import me.juliana.hellomeds.ui.components.list.SmartListItemConfig
import me.juliana.hellomeds.ui.components.list.SmartListTextItem
import me.juliana.hellomeds.ui.components.medication.MedicationTypeSelector
import me.juliana.hellomeds.ui.theme.HelloMedsTheme
import org.jetbrains.compose.resources.stringResource

// Helper function to format decimal values intelligently
private fun formatDecimal(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBaseDataScreen(
    medication: Medication,
    onNavigateBack: () -> Unit,
    onSave: (String, MedicationType, Double?, MedicationStrengthUnit?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(medication) {
        mutableStateOf(medication.name)
    }

    var type by remember(medication) {
        mutableStateOf(medication.type)
    }

    var hasStrength by remember(medication) {
        mutableStateOf(medication.strengthValue != null)
    }

    var strengthValue by remember(medication) {
        mutableStateOf(
            medication.strengthValue?.let { formatDecimal(it) } ?: "",
        )
    }

    var strengthUnit by remember(medication) {
        mutableStateOf(medication.strengthUnit ?: MedicationStrengthUnit.MG)
    }

    // Validation
    val nameError = MedicationValidation.validateMedicationName(name)
    val strengthError = MedicationValidation.validateStrengthValue(strengthValue, hasStrength)
    val isValid = nameError == null && strengthError == null

    AppScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_edit_base_data)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (isValid) {
                                onSave(
                                    name,
                                    type,
                                    if (hasStrength) strengthValue.toDoubleOrNull() else null,
                                    if (hasStrength) strengthUnit else null,
                                )
                                onNavigateBack()
                            }
                        },
                        enabled = isValid,
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Medication name field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= MedicationValidation.MAX_NAME_LENGTH) {
                        name = it
                    }
                },
                label = { Text(stringResource(Res.string.medication_name_label)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            // Strength information group
            AutoSmartList(
                items = listOf(
                    SmartListItemConfig(visible = true) { shapes, visible ->
                        SmartListItem(
                            headlineContent = { Text(stringResource(Res.string.medication_has_strength)) },
                            trailingContent = {
                                Switch(
                                    checked = hasStrength,
                                    onCheckedChange = { hasStrength = it },
                                )
                            },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                    SmartListItemConfig(visible = hasStrength) { shapes, visible ->
                        SmartListTextItem(
                            label = stringResource(Res.string.medication_strength_value),
                            value = strengthValue,
                            onValueChange = { strengthValue = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shapes = shapes,
                            visible = visible,
                            validator = { text ->
                                text.isEmpty() || text.toDoubleOrNull()?.let { it > 0 } == true
                            },
                            inputTransformation = DecimalInputTransformation(),
                        )
                    },
                    SmartListItemConfig(visible = hasStrength) { shapes, visible ->
                        SmartListDropdownItem(
                            label = stringResource(Res.string.medication_strength_unit),
                            selectedValue = strengthUnit.value,
                            options = MedicationStrengthUnit.allDisplayValues(),
                            onValueChange = {
                                strengthUnit = MedicationStrengthUnit.fromValue(it) ?: MedicationStrengthUnit.MG
                            },
                            shapes = shapes,
                            visible = visible,
                        )
                    },
                ),
            )

            // Show strength validation error
            if (hasStrength && strengthError != null) {
                Text(
                    text = strengthError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Type section label
            Text(
                text = stringResource(Res.string.medication_type_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )

            // Type selector
            MedicationTypeSelector(
                selectedType = type,
                onTypeSelected = { type = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditBaseDataScreenPreview() {
    HelloMedsTheme {
        EditBaseDataScreen(
            medication = Medication(
                id = 1,
                name = "Vitamin D3",
                type = MedicationType.CAPSULE,
                shape = "",
                importanceLabelId = 1,
                strengthValue = 1000.0,
                strengthUnit = MedicationStrengthUnit.IU,
                foregroundShape = "CAPSULE_PILL",
                backgroundShape = "CIRCLE",
                shapeColor = null,
            ),
            onNavigateBack = {},
            onSave = { _, _, _, _ -> },
        )
    }
}
