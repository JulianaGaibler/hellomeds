// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Modal flows presented as bottom sheets on phones and dialogs on tablets. */
interface ModalFlowNavKey : NavKey

/**
 * @param showAllSteps If true, shows all permission screens regardless of grant state (debug/testing).
 */
@Serializable
data class OnboardingRoute(val showAllSteps: Boolean = false) : NavKey

/**
 * Root container for the three main tabs (Tracking, Medication, History).
 *
 * Tabs are managed by local state, not the backstack — switching doesn't transition, state survives
 * window-size changes, and all three can render simultaneously on wide layouts. Overlay routes pushed
 * on top fully replace this (including the navigation bars).
 */
@Serializable
data object MainAppRoute : NavKey

@Serializable
data class MedicationDetailRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data object AddMedicationRoute : ModalFlowNavKey

@Serializable
data class EditMedicationRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data class EditScheduleRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data class EditLabelRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data class StockTrackingDetailRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data class StockTrackingSettingsRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data class AddStockTrackingFlowRoute(val medicationId: Int) : ModalFlowNavKey

@Serializable
data object CameraDetectionRoute : ModalFlowNavKey

@Serializable
data object SettingsRoute : ModalFlowNavKey

@Serializable
data object ImportanceLabelsRoute : ModalFlowNavKey

@Serializable
data object NotificationSettingsRoute : ModalFlowNavKey

@Serializable
data object CameraSettingsRoute : ModalFlowNavKey

@Serializable
data object DebugRoute : ModalFlowNavKey

@Serializable
data object AutoBackupSettingsRoute : ModalFlowNavKey

@Serializable
data object SupportRoute : ModalFlowNavKey

@Serializable
data object ExportDataRoute : ModalFlowNavKey

@Serializable
data object ImportDataRoute : ModalFlowNavKey
