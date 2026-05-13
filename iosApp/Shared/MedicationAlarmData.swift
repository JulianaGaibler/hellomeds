// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import AlarmKit

/// Custom metadata attached to AlarmKit alarms for medication reminders.
/// Shared between the main app target and the AlarmLiveActivity widget extension.
///
/// Contains the information needed for:
/// - Live Activity rendering (medication names, criticality)
/// - Intent action handling (slot time, schedule IDs)
struct MedicationAlarmData: AlarmMetadata {
    /// The original time slot in epoch milliseconds.
    let slotTimeMs: Int64

    /// Comma-separated schedule IDs for this time slot.
    let scheduleIds: String

    /// Comma-separated medication names for display.
    let medicationNames: String

    /// Whether any medication in this slot has critical importance.
    let isCritical: Bool
}
