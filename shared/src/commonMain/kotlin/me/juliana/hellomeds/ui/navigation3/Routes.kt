// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.navigation3

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for Navigation 3.
 * All routes are @Serializable NavKey objects instead of string-based routes.
 *
 * Route categories:
 * - ModalFlowNavKey: Modal flows (bottom sheets on phones, dialogs on tablets)
 */

// ============================================================================
// Marker Interfaces (extend official Navigation 3 NavKey)
// ============================================================================

/**
 * Marker interface for NavKeys that represent modal flows (multi-step processes)
 * that should be presented as bottom sheets on phones and dialogs on tablets.
 */
interface ModalFlowNavKey : NavKey

// ============================================================================
// Root Navigation Routes
// ============================================================================

/**
 * Onboarding route - first-launch permission setup flow.
 *
 * Shown on first app launch to guide users through granting necessary permissions
 * for reliable medication reminders (notifications, exact alarms, DnD access).
 *
 * Can also be triggered from Debug screen with different modes.
 *
 * @param showAllSteps If true, shows all permission screens regardless of whether they're granted.
 *                     Used for debug/testing. If false, skips screens for already-granted permissions.
 */
@Serializable
data class OnboardingRoute(val showAllSteps: Boolean = false) : NavKey

/**
 * Main app route - the root container for the three main screens (Tracking, Medication, History).
 *
 * This is the base of the navigation stack. When pushed onto the stack, it renders the full
 * adaptive main screen with navigation bars/rails.
 *
 * Tabs within this route are managed by local state (NOT navigation backstack):
 * - Tab switching doesn't trigger navigation transitions
 * - State is preserved when window size changes (mobile <-> tablet <-> desktop)
 * - All three screens can be shown simultaneously on wide layouts
 *
 * When overlay routes (Settings, Edit, etc.) are pushed on top of this route,
 * they completely replace it (including hiding the navigation bars).
 */
@Serializable
data object MainAppRoute : NavKey

// ============================================================================
// Detail Screens (Modal Overlays)
// ============================================================================

/**
 * Medication detail screen - shows details for a specific medication.
 * Shown as modal overlay (full screen on phones, centered card on tablets).
 *
 * @param medicationId The ID of the medication to display
 */
@Serializable
data class MedicationDetailRoute(val medicationId: Int) : ModalFlowNavKey

// ============================================================================
// Modal Flows (Bottom Sheets -> Dialogs)
// ============================================================================

/**
 * Add medication flow - multi-step process to add a new medication.
 * Shown as bottom sheet on phones, dialog on tablets.
 */
@Serializable
data object AddMedicationRoute : ModalFlowNavKey

/**
 * Edit medication flow - edit visual appearance of a medication.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication to edit
 */
@Serializable
data class EditMedicationRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Edit base data flow - edit name, type, and strength of a medication.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication to edit
 */
@Serializable
data class EditBaseDataRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Edit schedule flow - manage schedules for a medication.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication whose schedules to edit
 */
@Serializable
data class EditScheduleRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Edit label flow - change importance label for a medication.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication whose label to edit
 */
@Serializable
data class EditLabelRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Stock tracking detail screen - shows stock tracking status and management options.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication to view stock tracking for
 */
@Serializable
data class StockTrackingDetailRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Stock tracking settings screen - configure tracking parameters.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication whose stock settings to edit
 */
@Serializable
data class StockTrackingSettingsRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Add stock tracking flow - multi-step wizard for configuring stock tracking.
 * Shown as bottom sheet on phones, dialog on tablets.
 *
 * @param medicationId The ID of the medication to add stock tracking to
 */
@Serializable
data class AddStockTrackingFlowRoute(val medicationId: Int) : ModalFlowNavKey

/**
 * Camera detection flow - use ML Kit to detect medication from camera.
 * Shown as full screen modal.
 */
@Serializable
data object CameraDetectionRoute : ModalFlowNavKey

// ============================================================================
// Settings Hierarchy (Hierarchical Navigation)
// ============================================================================

/**
 * Settings screen - app settings and preferences.
 * Shown as modal overlay (full screen on phone, dialog on tablet).
 */
@Serializable
data object SettingsRoute : ModalFlowNavKey

/**
 * Importance labels screen - manage custom importance labels.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object ImportanceLabelsRoute : ModalFlowNavKey

/**
 * Advanced notification settings screen - grouping, privacy, channels, and permissions.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object NotificationSettingsRoute : ModalFlowNavKey

/**
 * Camera detection settings — privacy notice and detection-method picker.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object CameraSettingsRoute : ModalFlowNavKey

/**
 * Debug screen - debug information and developer tools.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object DebugRoute : ModalFlowNavKey

/**
 * Export data screen - export medications, schedules, and stock settings to JSON.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object AutoBackupSettingsRoute : ModalFlowNavKey

@Serializable
data object SupportRoute : ModalFlowNavKey

@Serializable
data object ExportDataRoute : ModalFlowNavKey

/**
 * Import data screen - import medications from a backup JSON file.
 * Child of Settings screen. Shown as modal overlay.
 */
@Serializable
data object ImportDataRoute : ModalFlowNavKey

// ============================================================================
// Helper Extensions
// ============================================================================

// These helper extension functions are kept for backwards compatibility
// but are not used in the new NavHost-based architecture
