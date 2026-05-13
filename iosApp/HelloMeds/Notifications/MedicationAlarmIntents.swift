// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import AlarmKit
import AppIntents
import shared

/// LiveActivityIntent triggered when the user taps "Taken" on an AlarmKit alarm.
///
/// LiveActivityIntents run in the main app process, giving full access to the
/// KMP shared framework (Koin DI, Room database, repositories).
@available(iOS 26.0, *)
struct TakeMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_take_title"
    static var description = IntentDescription("Mark medications as taken and dismiss the alarm")

    @Parameter(title: "Alarm ID")
    var alarmID: String

    @Parameter(title: "Slot Time Seconds")
    var slotTimeSec: Int

    @Parameter(title: "Schedule IDs")
    var scheduleIds: String

    init() {
        self.alarmID = ""
        self.slotTimeSec = 0
        self.scheduleIds = ""
    }

    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID
        self.slotTimeSec = slotTimeSec
        self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        try AlarmManager.shared.stop(id: UUID(uuidString: alarmID)!)
        let slotTimeMs = Int64(slotTimeSec) * 1000
        AlarmKitActionBridgeKt.handleAlarmTaken(slotTimeMs: slotTimeMs, scheduleIds: scheduleIds)
        return .result()
    }
}

/// LiveActivityIntent triggered when the user taps "Snooze" on an AlarmKit alarm.
///
/// Snoozes all medications at the time slot using the configured snooze interval.
@available(iOS 26.0, *)
struct SnoozeMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_snooze_title"
    static var description = IntentDescription("Snooze the medication alarm")

    @Parameter(title: "Alarm ID")
    var alarmID: String

    @Parameter(title: "Slot Time Seconds")
    var slotTimeSec: Int

    @Parameter(title: "Schedule IDs")
    var scheduleIds: String

    init() {
        self.alarmID = ""
        self.slotTimeSec = 0
        self.scheduleIds = ""
    }

    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID
        self.slotTimeSec = slotTimeSec
        self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        // Stop the alarm (dismiss DI/Lock Screen) — snooze is handled entirely
        // via a UNNotification scheduled by the Kotlin side, no visual countdown.
        try AlarmManager.shared.stop(id: UUID(uuidString: alarmID)!)
        let slotTimeMs = Int64(slotTimeSec) * 1000
        AlarmKitActionBridgeKt.handleAlarmSnooze(slotTimeMs: slotTimeMs, scheduleIds: scheduleIds)
        return .result()
    }
}

/// LiveActivityIntent triggered when the user taps "Skipped" in the Dynamic Island expanded view.
///
/// Marks all medications at the time slot as skipped.
@available(iOS 26.0, *)
struct SkipMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_skip_title"
    static var description = IntentDescription("Skip the medication alarm")
    static var openAppWhenRun = false

    @Parameter(title: "Alarm ID")
    var alarmID: String

    @Parameter(title: "Slot Time Seconds")
    var slotTimeSec: Int

    @Parameter(title: "Schedule IDs")
    var scheduleIds: String

    init() {
        self.alarmID = ""
        self.slotTimeSec = 0
        self.scheduleIds = ""
    }

    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID
        self.slotTimeSec = slotTimeSec
        self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        try AlarmManager.shared.stop(id: UUID(uuidString: alarmID)!)
        let slotTimeMs = Int64(slotTimeSec) * 1000
        AlarmKitActionBridgeKt.handleAlarmSkipped(slotTimeMs: slotTimeMs, scheduleIds: scheduleIds)
        return .result()
    }
}
