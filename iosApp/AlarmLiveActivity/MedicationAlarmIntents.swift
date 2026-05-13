// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import AlarmKit
import AppIntents

// Widget extension copy — provides intent TYPES for button construction only.
// LiveActivityIntents always execute in the main app process where the full
// implementation lives (see HelloMeds/Notifications/MedicationAlarmIntents.swift).

@available(iOS 26.0, *)
struct TakeMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_take_title"
    static var description = IntentDescription("Mark medications as taken and dismiss the alarm")

    @Parameter(title: "Alarm ID") var alarmID: String
    @Parameter(title: "Slot Time Seconds") var slotTimeSec: Int
    @Parameter(title: "Schedule IDs") var scheduleIds: String

    init() { self.alarmID = ""; self.slotTimeSec = 0; self.scheduleIds = "" }
    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID; self.slotTimeSec = slotTimeSec; self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        try AlarmManager.shared.stop(id: UUID(uuidString: alarmID)!)
        return .result()
    }
}

@available(iOS 26.0, *)
struct SnoozeMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_snooze_title"
    static var description = IntentDescription("Snooze the medication alarm")

    @Parameter(title: "Alarm ID") var alarmID: String
    @Parameter(title: "Slot Time Seconds") var slotTimeSec: Int
    @Parameter(title: "Schedule IDs") var scheduleIds: String

    init() { self.alarmID = ""; self.slotTimeSec = 0; self.scheduleIds = "" }
    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID; self.slotTimeSec = slotTimeSec; self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        try AlarmManager.shared.countdown(id: UUID(uuidString: alarmID)!)
        return .result()
    }
}

@available(iOS 26.0, *)
struct SkipMedicationIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "intent_skip_title"
    static var description = IntentDescription("Skip the medication alarm")

    @Parameter(title: "Alarm ID") var alarmID: String
    @Parameter(title: "Slot Time Seconds") var slotTimeSec: Int
    @Parameter(title: "Schedule IDs") var scheduleIds: String

    init() { self.alarmID = ""; self.slotTimeSec = 0; self.scheduleIds = "" }
    init(alarmID: String, slotTimeSec: Int, scheduleIds: String) {
        self.alarmID = alarmID; self.slotTimeSec = slotTimeSec; self.scheduleIds = scheduleIds
    }

    func perform() async throws -> some IntentResult {
        try AlarmManager.shared.stop(id: UUID(uuidString: alarmID)!)
        return .result()
    }
}
