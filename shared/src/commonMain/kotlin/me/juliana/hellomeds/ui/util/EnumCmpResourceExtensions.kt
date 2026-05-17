// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.util

import androidx.compose.runtime.Composable
import me.juliana.hellomeds.data.database.DefaultLabelType
import me.juliana.hellomeds.data.database.entities.ImportanceLabel
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.container_ampoule
import me.juliana.hellomeds.shared.container_ampoule_lower
import me.juliana.hellomeds.shared.container_blister_pack
import me.juliana.hellomeds.shared.container_blister_pack_lower
import me.juliana.hellomeds.shared.container_bottle
import me.juliana.hellomeds.shared.container_bottle_lower
import me.juliana.hellomeds.shared.container_canister
import me.juliana.hellomeds.shared.container_canister_lower
import me.juliana.hellomeds.shared.container_dispenser
import me.juliana.hellomeds.shared.container_dispenser_lower
import me.juliana.hellomeds.shared.container_inhaler
import me.juliana.hellomeds.shared.container_inhaler_lower
import me.juliana.hellomeds.shared.container_jar
import me.juliana.hellomeds.shared.container_jar_lower
import me.juliana.hellomeds.shared.container_label_ampoule
import me.juliana.hellomeds.shared.container_label_blister_pack
import me.juliana.hellomeds.shared.container_label_bottle
import me.juliana.hellomeds.shared.container_label_canister
import me.juliana.hellomeds.shared.container_label_dispenser
import me.juliana.hellomeds.shared.container_label_inhaler
import me.juliana.hellomeds.shared.container_label_jar
import me.juliana.hellomeds.shared.container_label_package
import me.juliana.hellomeds.shared.container_label_pen
import me.juliana.hellomeds.shared.container_label_tube
import me.juliana.hellomeds.shared.container_label_vial
import me.juliana.hellomeds.shared.container_package
import me.juliana.hellomeds.shared.container_package_lower
import me.juliana.hellomeds.shared.container_pen
import me.juliana.hellomeds.shared.container_pen_lower
import me.juliana.hellomeds.shared.container_tube
import me.juliana.hellomeds.shared.container_tube_lower
import me.juliana.hellomeds.shared.container_vial
import me.juliana.hellomeds.shared.container_vial_lower
import me.juliana.hellomeds.shared.default_label_alarm
import me.juliana.hellomeds.shared.default_label_critical_follow_ups
import me.juliana.hellomeds.shared.default_label_follow_ups
import me.juliana.hellomeds.shared.default_label_once
import me.juliana.hellomeds.shared.default_label_silent
import me.juliana.hellomeds.shared.dosage_capsule
import me.juliana.hellomeds.shared.dosage_cream
import me.juliana.hellomeds.shared.dosage_device
import me.juliana.hellomeds.shared.dosage_drops
import me.juliana.hellomeds.shared.dosage_foam
import me.juliana.hellomeds.shared.dosage_gel
import me.juliana.hellomeds.shared.dosage_inhaler
import me.juliana.hellomeds.shared.dosage_injection
import me.juliana.hellomeds.shared.dosage_liquid
import me.juliana.hellomeds.shared.dosage_lotion
import me.juliana.hellomeds.shared.dosage_ointment
import me.juliana.hellomeds.shared.dosage_patch
import me.juliana.hellomeds.shared.dosage_powder
import me.juliana.hellomeds.shared.dosage_spray
import me.juliana.hellomeds.shared.dosage_suppository
import me.juliana.hellomeds.shared.dosage_tablet
import me.juliana.hellomeds.shared.dosage_topical
import me.juliana.hellomeds.shared.dose_unit_capsule
import me.juliana.hellomeds.shared.dose_unit_cream
import me.juliana.hellomeds.shared.dose_unit_device
import me.juliana.hellomeds.shared.dose_unit_drops
import me.juliana.hellomeds.shared.dose_unit_foam
import me.juliana.hellomeds.shared.dose_unit_gel
import me.juliana.hellomeds.shared.dose_unit_inhaler
import me.juliana.hellomeds.shared.dose_unit_injection
import me.juliana.hellomeds.shared.dose_unit_liquid
import me.juliana.hellomeds.shared.dose_unit_lotion
import me.juliana.hellomeds.shared.dose_unit_ointment
import me.juliana.hellomeds.shared.dose_unit_patch
import me.juliana.hellomeds.shared.dose_unit_powder
import me.juliana.hellomeds.shared.dose_unit_spray
import me.juliana.hellomeds.shared.dose_unit_suppository
import me.juliana.hellomeds.shared.dose_unit_tablet
import me.juliana.hellomeds.shared.dose_unit_topical
import me.juliana.hellomeds.shared.medication_type_capsule
import me.juliana.hellomeds.shared.medication_type_capsule_plural
import me.juliana.hellomeds.shared.medication_type_cream
import me.juliana.hellomeds.shared.medication_type_cream_plural
import me.juliana.hellomeds.shared.medication_type_device
import me.juliana.hellomeds.shared.medication_type_device_plural
import me.juliana.hellomeds.shared.medication_type_drops
import me.juliana.hellomeds.shared.medication_type_drops_plural
import me.juliana.hellomeds.shared.medication_type_foam
import me.juliana.hellomeds.shared.medication_type_foam_plural
import me.juliana.hellomeds.shared.medication_type_gel
import me.juliana.hellomeds.shared.medication_type_gel_plural
import me.juliana.hellomeds.shared.medication_type_inhaler
import me.juliana.hellomeds.shared.medication_type_inhaler_plural
import me.juliana.hellomeds.shared.medication_type_injection
import me.juliana.hellomeds.shared.medication_type_injection_plural
import me.juliana.hellomeds.shared.medication_type_liquid
import me.juliana.hellomeds.shared.medication_type_liquid_plural
import me.juliana.hellomeds.shared.medication_type_lotion
import me.juliana.hellomeds.shared.medication_type_lotion_plural
import me.juliana.hellomeds.shared.medication_type_ointment
import me.juliana.hellomeds.shared.medication_type_ointment_plural
import me.juliana.hellomeds.shared.medication_type_patch
import me.juliana.hellomeds.shared.medication_type_patch_plural
import me.juliana.hellomeds.shared.medication_type_powder
import me.juliana.hellomeds.shared.medication_type_powder_plural
import me.juliana.hellomeds.shared.medication_type_spray
import me.juliana.hellomeds.shared.medication_type_spray_plural
import me.juliana.hellomeds.shared.medication_type_suppository
import me.juliana.hellomeds.shared.medication_type_suppository_plural
import me.juliana.hellomeds.shared.medication_type_tablet
import me.juliana.hellomeds.shared.medication_type_tablet_plural
import me.juliana.hellomeds.shared.medication_type_topical
import me.juliana.hellomeds.shared.medication_type_topical_plural
import me.juliana.hellomeds.shared.stock_summary_current_ampoule
import me.juliana.hellomeds.shared.stock_summary_current_blister_pack
import me.juliana.hellomeds.shared.stock_summary_current_bottle
import me.juliana.hellomeds.shared.stock_summary_current_canister
import me.juliana.hellomeds.shared.stock_summary_current_dispenser
import me.juliana.hellomeds.shared.stock_summary_current_inhaler
import me.juliana.hellomeds.shared.stock_summary_current_jar
import me.juliana.hellomeds.shared.stock_summary_current_package
import me.juliana.hellomeds.shared.stock_summary_current_pen
import me.juliana.hellomeds.shared.stock_summary_current_tube
import me.juliana.hellomeds.shared.stock_summary_current_vial
import me.juliana.hellomeds.shared.stock_summary_full_ampoule
import me.juliana.hellomeds.shared.stock_summary_full_blister_pack
import me.juliana.hellomeds.shared.stock_summary_full_bottle
import me.juliana.hellomeds.shared.stock_summary_full_canister
import me.juliana.hellomeds.shared.stock_summary_full_dispenser
import me.juliana.hellomeds.shared.stock_summary_full_inhaler
import me.juliana.hellomeds.shared.stock_summary_full_jar
import me.juliana.hellomeds.shared.stock_summary_full_package
import me.juliana.hellomeds.shared.stock_summary_full_pen
import me.juliana.hellomeds.shared.stock_summary_full_tube
import me.juliana.hellomeds.shared.stock_summary_full_vial
import me.juliana.hellomeds.shared.unit_g
import me.juliana.hellomeds.shared.unit_iu
import me.juliana.hellomeds.shared.unit_mcg
import me.juliana.hellomeds.shared.unit_meq
import me.juliana.hellomeds.shared.unit_mg
import me.juliana.hellomeds.shared.unit_ml
import me.juliana.hellomeds.shared.unit_percent
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ── MedicationType extensions ────────────────────────────────────────────────

val MedicationType.displayNameRes: StringResource
    get() = when (this) {
        MedicationType.CAPSULE -> Res.string.medication_type_capsule
        MedicationType.TABLET -> Res.string.medication_type_tablet
        MedicationType.LIQUID -> Res.string.medication_type_liquid
        MedicationType.TOPICAL -> Res.string.medication_type_topical
        MedicationType.CREAM -> Res.string.medication_type_cream
        MedicationType.DEVICE -> Res.string.medication_type_device
        MedicationType.DROPS -> Res.string.medication_type_drops
        MedicationType.FOAM -> Res.string.medication_type_foam
        MedicationType.GEL -> Res.string.medication_type_gel
        MedicationType.INHALER -> Res.string.medication_type_inhaler
        MedicationType.INJECTION -> Res.string.medication_type_injection
        MedicationType.LOTION -> Res.string.medication_type_lotion
        MedicationType.OINTMENT -> Res.string.medication_type_ointment
        MedicationType.PATCH -> Res.string.medication_type_patch
        MedicationType.POWDER -> Res.string.medication_type_powder
        MedicationType.SPRAY -> Res.string.medication_type_spray
        MedicationType.SUPPOSITORY -> Res.string.medication_type_suppository
    }

val MedicationType.pluralFormRes: StringResource
    get() = when (this) {
        MedicationType.CAPSULE -> Res.string.medication_type_capsule_plural
        MedicationType.TABLET -> Res.string.medication_type_tablet_plural
        MedicationType.LIQUID -> Res.string.medication_type_liquid_plural
        MedicationType.TOPICAL -> Res.string.medication_type_topical_plural
        MedicationType.CREAM -> Res.string.medication_type_cream_plural
        MedicationType.DEVICE -> Res.string.medication_type_device_plural
        MedicationType.DROPS -> Res.string.medication_type_drops_plural
        MedicationType.FOAM -> Res.string.medication_type_foam_plural
        MedicationType.GEL -> Res.string.medication_type_gel_plural
        MedicationType.INHALER -> Res.string.medication_type_inhaler_plural
        MedicationType.INJECTION -> Res.string.medication_type_injection_plural
        MedicationType.LOTION -> Res.string.medication_type_lotion_plural
        MedicationType.OINTMENT -> Res.string.medication_type_ointment_plural
        MedicationType.PATCH -> Res.string.medication_type_patch_plural
        MedicationType.POWDER -> Res.string.medication_type_powder_plural
        MedicationType.SPRAY -> Res.string.medication_type_spray_plural
        MedicationType.SUPPOSITORY -> Res.string.medication_type_suppository_plural
    }

val MedicationType.dosagePluralRes: PluralStringResource
    get() = when (this) {
        MedicationType.CAPSULE -> Res.plurals.dosage_capsule
        MedicationType.TABLET -> Res.plurals.dosage_tablet
        MedicationType.LIQUID -> Res.plurals.dosage_liquid
        MedicationType.TOPICAL -> Res.plurals.dosage_topical
        MedicationType.CREAM -> Res.plurals.dosage_cream
        MedicationType.DEVICE -> Res.plurals.dosage_device
        MedicationType.DROPS -> Res.plurals.dosage_drops
        MedicationType.FOAM -> Res.plurals.dosage_foam
        MedicationType.GEL -> Res.plurals.dosage_gel
        MedicationType.INHALER -> Res.plurals.dosage_inhaler
        MedicationType.INJECTION -> Res.plurals.dosage_injection
        MedicationType.LOTION -> Res.plurals.dosage_lotion
        MedicationType.OINTMENT -> Res.plurals.dosage_ointment
        MedicationType.PATCH -> Res.plurals.dosage_patch
        MedicationType.POWDER -> Res.plurals.dosage_powder
        MedicationType.SPRAY -> Res.plurals.dosage_spray
        MedicationType.SUPPOSITORY -> Res.plurals.dosage_suppository
    }

val MedicationType.doseUnitPluralRes: PluralStringResource
    get() = when (this) {
        MedicationType.CAPSULE -> Res.plurals.dose_unit_capsule
        MedicationType.TABLET -> Res.plurals.dose_unit_tablet
        MedicationType.LIQUID -> Res.plurals.dose_unit_liquid
        MedicationType.TOPICAL -> Res.plurals.dose_unit_topical
        MedicationType.CREAM -> Res.plurals.dose_unit_cream
        MedicationType.DEVICE -> Res.plurals.dose_unit_device
        MedicationType.DROPS -> Res.plurals.dose_unit_drops
        MedicationType.FOAM -> Res.plurals.dose_unit_foam
        MedicationType.GEL -> Res.plurals.dose_unit_gel
        MedicationType.INHALER -> Res.plurals.dose_unit_inhaler
        MedicationType.INJECTION -> Res.plurals.dose_unit_injection
        MedicationType.LOTION -> Res.plurals.dose_unit_lotion
        MedicationType.OINTMENT -> Res.plurals.dose_unit_ointment
        MedicationType.PATCH -> Res.plurals.dose_unit_patch
        MedicationType.POWDER -> Res.plurals.dose_unit_powder
        MedicationType.SPRAY -> Res.plurals.dose_unit_spray
        MedicationType.SUPPOSITORY -> Res.plurals.dose_unit_suppository
    }

// ── MedicationContainer extensions ───────────────────────────────────────────

val MedicationContainer.displayNameRes: StringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.string.container_package
        MedicationContainer.BOTTLE -> Res.string.container_bottle
        MedicationContainer.DISPENSER -> Res.string.container_dispenser
        MedicationContainer.BLISTER_PACK -> Res.string.container_blister_pack
        MedicationContainer.TUBE -> Res.string.container_tube
        MedicationContainer.VIAL -> Res.string.container_vial
        MedicationContainer.INHALER -> Res.string.container_inhaler
        MedicationContainer.PEN -> Res.string.container_pen
        MedicationContainer.AMPOULE -> Res.string.container_ampoule
        MedicationContainer.CANISTER -> Res.string.container_canister
        MedicationContainer.JAR -> Res.string.container_jar
    }

val MedicationContainer.displayNameLowerRes: StringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.string.container_package_lower
        MedicationContainer.BOTTLE -> Res.string.container_bottle_lower
        MedicationContainer.DISPENSER -> Res.string.container_dispenser_lower
        MedicationContainer.BLISTER_PACK -> Res.string.container_blister_pack_lower
        MedicationContainer.TUBE -> Res.string.container_tube_lower
        MedicationContainer.VIAL -> Res.string.container_vial_lower
        MedicationContainer.INHALER -> Res.string.container_inhaler_lower
        MedicationContainer.PEN -> Res.string.container_pen_lower
        MedicationContainer.AMPOULE -> Res.string.container_ampoule_lower
        MedicationContainer.CANISTER -> Res.string.container_canister_lower
        MedicationContainer.JAR -> Res.string.container_jar_lower
    }

val MedicationContainer.pluralRes: PluralStringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.plurals.container_package
        MedicationContainer.BOTTLE -> Res.plurals.container_bottle
        MedicationContainer.DISPENSER -> Res.plurals.container_dispenser
        MedicationContainer.BLISTER_PACK -> Res.plurals.container_blister_pack
        MedicationContainer.TUBE -> Res.plurals.container_tube
        MedicationContainer.VIAL -> Res.plurals.container_vial
        MedicationContainer.INHALER -> Res.plurals.container_inhaler
        MedicationContainer.PEN -> Res.plurals.container_pen
        MedicationContainer.AMPOULE -> Res.plurals.container_ampoule
        MedicationContainer.CANISTER -> Res.plurals.container_canister
        MedicationContainer.JAR -> Res.plurals.container_jar
    }

val MedicationContainer.labelPluralRes: PluralStringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.plurals.container_label_package
        MedicationContainer.BOTTLE -> Res.plurals.container_label_bottle
        MedicationContainer.DISPENSER -> Res.plurals.container_label_dispenser
        MedicationContainer.BLISTER_PACK -> Res.plurals.container_label_blister_pack
        MedicationContainer.TUBE -> Res.plurals.container_label_tube
        MedicationContainer.VIAL -> Res.plurals.container_label_vial
        MedicationContainer.INHALER -> Res.plurals.container_label_inhaler
        MedicationContainer.PEN -> Res.plurals.container_label_pen
        MedicationContainer.AMPOULE -> Res.plurals.container_label_ampoule
        MedicationContainer.CANISTER -> Res.plurals.container_label_canister
        MedicationContainer.JAR -> Res.plurals.container_label_jar
    }

val MedicationContainer.currentLabelRes: StringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.string.stock_summary_current_package
        MedicationContainer.BOTTLE -> Res.string.stock_summary_current_bottle
        MedicationContainer.DISPENSER -> Res.string.stock_summary_current_dispenser
        MedicationContainer.BLISTER_PACK -> Res.string.stock_summary_current_blister_pack
        MedicationContainer.TUBE -> Res.string.stock_summary_current_tube
        MedicationContainer.VIAL -> Res.string.stock_summary_current_vial
        MedicationContainer.INHALER -> Res.string.stock_summary_current_inhaler
        MedicationContainer.PEN -> Res.string.stock_summary_current_pen
        MedicationContainer.AMPOULE -> Res.string.stock_summary_current_ampoule
        MedicationContainer.CANISTER -> Res.string.stock_summary_current_canister
        MedicationContainer.JAR -> Res.string.stock_summary_current_jar
    }

val MedicationContainer.fullRemainingPluralRes: PluralStringResource
    get() = when (this) {
        MedicationContainer.PACKAGE -> Res.plurals.stock_summary_full_package
        MedicationContainer.BOTTLE -> Res.plurals.stock_summary_full_bottle
        MedicationContainer.DISPENSER -> Res.plurals.stock_summary_full_dispenser
        MedicationContainer.BLISTER_PACK -> Res.plurals.stock_summary_full_blister_pack
        MedicationContainer.TUBE -> Res.plurals.stock_summary_full_tube
        MedicationContainer.VIAL -> Res.plurals.stock_summary_full_vial
        MedicationContainer.INHALER -> Res.plurals.stock_summary_full_inhaler
        MedicationContainer.PEN -> Res.plurals.stock_summary_full_pen
        MedicationContainer.AMPOULE -> Res.plurals.stock_summary_full_ampoule
        MedicationContainer.CANISTER -> Res.plurals.stock_summary_full_canister
        MedicationContainer.JAR -> Res.plurals.stock_summary_full_jar
    }

// ── MedicationStrengthUnit extensions ────────────────────────────────────────

val MedicationStrengthUnit.displayNameRes: StringResource
    get() = when (this) {
        MedicationStrengthUnit.MG -> Res.string.unit_mg
        MedicationStrengthUnit.MCG -> Res.string.unit_mcg
        MedicationStrengthUnit.G -> Res.string.unit_g
        MedicationStrengthUnit.ML -> Res.string.unit_ml
        MedicationStrengthUnit.IU -> Res.string.unit_iu
        MedicationStrengthUnit.MEQ -> Res.string.unit_meq
        MedicationStrengthUnit.PERCENT -> Res.string.unit_percent
    }

// ── ImportanceLabel display name ─────────────────────────────────────────────

private val defaultLabelDisplayNameRes: Map<String, StringResource> = mapOf(
    "SILENT" to Res.string.default_label_silent,
    "ONCE" to Res.string.default_label_once,
    "FOLLOW_UPS" to Res.string.default_label_follow_ups,
    "CRITICAL_FOLLOW_UPS" to Res.string.default_label_critical_follow_ups,
    "ALARM" to Res.string.default_label_alarm,
)

/**
 * Returns the localized display name for an importance label.
 *
 * Built-in labels that haven't been renamed by the user return the localized
 * name from string resources. User-renamed or custom labels return the stored name.
 */
@Composable
fun ImportanceLabel.displayName(): String {
    val type = defaultType ?: return name
    val defaultDef = DefaultLabelType.entries.find { it.defaultType == type } ?: return name
    if (name != defaultDef.defaultName) return name
    val res = defaultLabelDisplayNameRes[type] ?: return name
    return stringResource(res)
}
