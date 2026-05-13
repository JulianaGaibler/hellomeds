// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.data.model.enums.MedicationContainer
import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType
import me.juliana.hellomeds.data.model.enums.TimeZoneMode
import me.juliana.hellomeds.data.model.enums.TrackingPrecision
import me.juliana.hellomeds.data.util.currentTimeMillis

@Entity(
    tableName = "medications",
    foreignKeys = [
        ForeignKey(
            entity = ImportanceLabel::class,
            parentColumns = ["id"],
            childColumns = ["importanceLabelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("importanceLabelId")],
)
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: MedicationType,
    val shape: String,
    val importanceLabelId: Int,
    val strengthValue: Double? = null,
    val strengthUnit: MedicationStrengthUnit? = null,
    val displayName: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = currentTimeMillis(),
    // Shape appearance properties
    val foregroundShape: String, // MedicationForegroundShape enum stored as string
    val backgroundShape: String, // MedicationBackgroundShape enum stored as string
    val shapeColor: String? = null, // Medication color (MedicationColor enum stored as string, nullable for default)
    // Stock tracking fields
    val stockTrackingEnabled: Boolean = false,
    val trackingPrecision: TrackingPrecision? = null, // EXACT or ESTIMATED
    val currentStockQuantity: Double? = null, // Cached: doses for EXACT, containers for ESTIMATED
    val lowStockThreshold: Double? = null, // Doses for EXACT, containers for ESTIMATED
    val lowStockAlertSent: Boolean = false,
    val packagingQuantity: Double? = null, // Doses per container for both modes
    val medicationContainer: MedicationContainer? = null, // e.g., BOTTLE, BOX, TUBE
    val depletionReminderEnabled: Boolean = false, // ESTIMATED only: reminder when container should be depleted
    val depletionAlertSent: Boolean = false, // ESTIMATED only: one-shot flag for depletion reminder notification
    val containerStartedAt: Long? = null, // ESTIMATED only: when the current open container was started (millis)
    // Cycle / blister pack fields
    val cycleType: CycleType = CycleType.NONE,
    val cycleDaysActive: Int? = null, // Active pill days (e.g., 21); must be > 0 when CYCLIC
    val cycleDaysBreak: Int? = null, // Break/off days (e.g., 7); must be >= 0 when CYCLIC
    val cycleHasPlacebos: Boolean = false, // Whether break days have placebo pills
    val cycleStartDate: LocalDate? = null, // Anchor for cycle math (stored as TEXT via TypeConverter)
    // Timezone handling
    val timeZoneMode: TimeZoneMode = TimeZoneMode.LOCAL, // How schedule times behave across timezone changes
    val anchorTimeZone: String? = null, // IANA timezone ID for FIXED mode (e.g., "America/New_York")
    // Custom sorting
    val displayOrder: Int = 0, // Custom display order for user-defined sorting
)
