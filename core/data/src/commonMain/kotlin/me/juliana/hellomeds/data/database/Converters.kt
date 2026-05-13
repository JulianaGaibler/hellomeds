// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.data.model.SessionType
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.FrequencyType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.StockAdjustmentType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision

/**
 * Type converters for Room database.
 */
class Converters {

    @TypeConverter
    fun fromFrequencyType(value: FrequencyType): String {
        return value.name
    }

    @TypeConverter
    fun toFrequencyType(value: String): FrequencyType {
        return try {
            FrequencyType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            FrequencyType.INTERVAL
        }
    }

    @TypeConverter
    fun fromMedicationType(type: MedicationType): String {
        return type.value
    }

    @TypeConverter
    fun toMedicationType(value: String): MedicationType {
        return MedicationType.fromValue(value) ?: MedicationType.TABLET
    }

    @TypeConverter
    fun fromMedicationStrengthUnit(unit: MedicationStrengthUnit?): String? {
        return unit?.value
    }

    @TypeConverter
    fun toMedicationStrengthUnit(value: String?): MedicationStrengthUnit? {
        return value?.let { MedicationStrengthUnit.fromValue(it) }
    }

    @TypeConverter
    fun fromTrackingPrecision(value: TrackingPrecision?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTrackingPrecision(value: String?): TrackingPrecision? {
        return value?.let { enumValueOf<TrackingPrecision>(it) }
    }

    @TypeConverter
    fun fromStockAdjustmentType(type: StockAdjustmentType?): String? {
        return type?.value
    }

    @TypeConverter
    fun toStockAdjustmentType(value: String?): StockAdjustmentType? {
        return value?.let { StockAdjustmentType.fromValue(it) }
    }

    @TypeConverter
    fun fromMedicationContainer(container: MedicationContainer?): String? {
        return container?.value
    }

    @TypeConverter
    fun toMedicationContainer(value: String?): MedicationContainer? {
        return value?.let { MedicationContainer.fromValue(it) }
    }

    @TypeConverter
    fun fromIntList(list: List<Int>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        return if (value.isEmpty()) emptyList() else value.split(",").mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun fromSessionType(type: SessionType): String {
        return type.name
    }

    @TypeConverter
    fun toSessionType(value: String): SessionType {
        return SessionType.valueOf(value)
    }

    @TypeConverter
    fun fromCycleType(value: CycleType): String {
        return value.name
    }

    @TypeConverter
    fun toCycleType(value: String): CycleType {
        return try {
            CycleType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            CycleType.NONE
        }
    }

    @TypeConverter
    fun fromTimeZoneMode(value: TimeZoneMode): String {
        return value.name
    }

    @TypeConverter
    fun toTimeZoneMode(value: String): TimeZoneMode {
        return try {
            TimeZoneMode.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TimeZoneMode.LOCAL
        }
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let { LocalDate.parse(it) }
    }
}
