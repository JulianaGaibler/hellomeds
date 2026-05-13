// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import ActivityKit
import AlarmKit
import AppIntents
import SwiftUI

/// Swift wrapper around the AlarmKit framework.
/// Provides static methods called from Kotlin via the callback bridge pattern.
///
/// AlarmKit is iOS 26+ only — all methods are guarded with `@available`.
/// The bridge translates between Kotlin primitive parameters and AlarmKit's Swift API.
@available(iOS 26.0, *)
class AlarmKitBridge {

    private typealias MedAlarmConfiguration = AlarmManager.AlarmConfiguration<MedicationAlarmData>

    // MARK: - Availability & Authorization

    static func isAvailable() -> Bool { true }

    static func requestAuthorization() async -> Bool {
        do {
            let state = try await AlarmManager.shared.requestAuthorization()
            return state == .authorized
        } catch {
            print("AlarmKitBridge: Authorization request failed: \(error)")
            return false
        }
    }

    static func isAuthorized() -> Bool {
        return AlarmManager.shared.authorizationState == .authorized
    }

    // MARK: - Scheduling

    /// Schedules an AlarmKit alarm for a medication time slot.
    ///
    /// - Parameters:
    ///   - id: UUID for the alarm
    ///   - fireDate: When the alarm should fire
    ///   - title: Alarm title text
    ///   - body: Alarm body text (medication names)
    ///   - slotTimeMs: Original time slot in epoch milliseconds (for intent metadata)
    ///   - scheduleIds: Comma-separated schedule IDs (for intent metadata)
    ///   - medicationNames: Comma-separated medication names (for Live Activity display)
    ///   - isCritical: Whether any medication in this slot is critical
    static func scheduleAlarm(
        id: UUID,
        fireDate: Date,
        title: String,
        body: String,
        slotTimeMs: Int64,
        scheduleIds: String,
        medicationNames: String,
        isCritical: Bool,
        snoozeDurationSeconds: Int
    ) async throws {
        // Lock Screen alert buttons: Taken (stop) + Snooze (secondary with countdown)
        let stopButton = AlarmButton(
            text: LocalizedStringResource("alarm_button_taken"),
            textColor: .white,
            systemImageName: "checkmark.circle"
        )
        let snoozeButton = AlarmButton(
            text: LocalizedStringResource("alarm_button_snooze"),
            textColor: .black,
            systemImageName: "moon.zzz"
        )

        let alertContent = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: stopButton,
            secondaryButton: snoozeButton,
            secondaryButtonBehavior: .countdown
        )

        // Snooze uses postAlert countdown duration (native AlarmKit snooze behavior)
        let countdownDuration: Alarm.CountdownDuration? = snoozeDurationSeconds > 0
            ? .init(preAlert: nil, postAlert: TimeInterval(snoozeDurationSeconds))
            : nil

        let presentation = AlarmPresentation(alert: alertContent)

        // Fixed schedule — medication alarms fire at specific absolute times
        let schedule = Alarm.Schedule.fixed(fireDate)

        // Metadata for Live Activity rendering
        let metadata = MedicationAlarmData(
            slotTimeMs: slotTimeMs,
            scheduleIds: scheduleIds,
            medicationNames: medicationNames,
            isCritical: isCritical
        )

        let attributes = AlarmAttributes(
            presentation: presentation,
            metadata: metadata,
            tintColor: isCritical ? Color.red : Color.accentColor
        )

        // Epoch seconds for the intent (Int64 ms → Int seconds)
        let slotTimeSec = Int(slotTimeMs / 1000)

        // Build configuration with intents for button actions
        let configuration = MedAlarmConfiguration(
            countdownDuration: countdownDuration,
            schedule: schedule,
            attributes: attributes,
            stopIntent: TakeMedicationIntent(
                alarmID: id.uuidString,
                slotTimeSec: slotTimeSec,
                scheduleIds: scheduleIds
            ),
            secondaryIntent: SnoozeMedicationIntent(
                alarmID: id.uuidString,
                slotTimeSec: slotTimeSec,
                scheduleIds: scheduleIds
            ),
            sound: .default
        )

        let _: Alarm = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
    }

    // MARK: - Cancellation

    /// Cancels a scheduled alarm (removes from schedule before it fires).
    static func cancelAlarm(id: UUID) {
        try? AlarmManager.shared.cancel(id: id)
    }

    /// Stops a ringing alarm (dismisses an active alarm).
    static func stopAlarm(id: UUID) {
        try? AlarmManager.shared.stop(id: id)
    }

    /// Cancels all HelloMeds AlarmKit alarms.
    static func cancelAllAlarms() {
        do {
            let alarms = try AlarmManager.shared.alarms
            for alarm in alarms {
                try? AlarmManager.shared.cancel(id: alarm.id)
            }
        } catch {
            print("AlarmKitBridge: Error cancelling all alarms: \(error)")
        }
    }
}
