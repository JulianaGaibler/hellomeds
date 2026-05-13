// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import ActivityKit
import AlarmKit
import AppIntents
import SwiftUI
import WidgetKit

/// Live Activity widget for AlarmKit medication alarms.
///
/// Renders the Lock Screen and Dynamic Island views when an AlarmKit alarm
/// fires for medication reminders. Uses [MedicationAlarmData] metadata to
/// display medication names and criticality.
///
/// The widget extension only renders views — all business logic (mark as taken,
/// cancel follow-ups) runs in the main app via LiveActivityIntents.
@available(iOS 26.0, *)
struct MedicationAlarmLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: AlarmAttributes<MedicationAlarmData>.self) { context in
            lockScreenView(attributes: context.attributes, state: context.state)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    alarmTitle(attributes: context.attributes, state: context.state)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    medicationIcon(isCritical: context.attributes.metadata?.isCritical ?? false)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    expandedControls(attributes: context.attributes, state: context.state)
                }
            } compactLeading: {
                Image(systemName: "pills.fill")
                    .foregroundStyle(context.attributes.tintColor)
            } compactTrailing: {
                AlarmProgressView(state: context.state, tint: context.attributes.tintColor)
            } minimal: {
                Image(systemName: "pills.fill")
                    .foregroundStyle(context.attributes.tintColor)
            }
            .keylineTint(context.attributes.tintColor)
        }
    }

    // MARK: - Lock Screen View

    func lockScreenView(attributes: AlarmAttributes<MedicationAlarmData>, state: AlarmPresentationState) -> some View {
        VStack(spacing: 12) {
            HStack(alignment: .top) {
                alarmTitle(attributes: attributes, state: state)
                Spacer()
                medicationIcon(isCritical: attributes.metadata?.isCritical ?? false)
            }

            // Medication names
            if let metadata = attributes.metadata {
                let names = metadata.medicationNames.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(names.prefix(5), id: \.self) { name in
                        HStack(spacing: 6) {
                            Image(systemName: "pill.fill")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Text(name)
                                .font(.body)
                                .lineLimit(1)
                        }
                    }
                    if names.count > 5 {
                        Text("+ \(names.count - 5) more")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            alarmControls(attributes: attributes, state: state)
        }
        .padding(.all, 12)
    }

    // MARK: - Components

    @ViewBuilder
    func alarmTitle(attributes: AlarmAttributes<MedicationAlarmData>, state: AlarmPresentationState) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(attributes.presentation.alert.title)
                .font(.headline)
                .fontWeight(.semibold)
                .lineLimit(1)
        }
    }

    @ViewBuilder
    func medicationIcon(isCritical: Bool) -> some View {
        Image(systemName: isCritical ? "exclamationmark.triangle.fill" : "pills.fill")
            .font(.title3)
            .foregroundStyle(isCritical ? .red : .accentColor)
    }

    @ViewBuilder
    func alarmControls(attributes: AlarmAttributes<MedicationAlarmData>, state: AlarmPresentationState) -> some View {
        HStack(spacing: 8) {
            Spacer()
            AlarmControlButtons(state: state)
        }
    }

    /// Dynamic Island expanded view — includes Skipped button alongside Taken + Snooze
    @ViewBuilder
    func expandedControls(attributes: AlarmAttributes<MedicationAlarmData>, state: AlarmPresentationState) -> some View {
        HStack(spacing: 4) {
            // Skip button (only in Dynamic Island expanded)
            Button(intent: SkipMedicationIntent(
                alarmID: state.alarmID.uuidString,
                slotTimeSec: 0,
                scheduleIds: ""
            )) {
                Label(String(localized: "alarm_button_skipped"), systemImage: "forward.fill")
                    .lineLimit(1)
            }
            .tint(.orange)
            .buttonStyle(.borderedProminent)
            .frame(height: 30)

            Spacer()

            AlarmControlButtons(state: state)
        }
    }
}

/// Progress view for Dynamic Island compact trailing.
@available(iOS 26.0, *)
struct AlarmProgressView: View {
    var state: AlarmPresentationState
    var tint: Color

    var body: some View {
        Group {
            switch state.mode {
            case .countdown(let countdown):
                ProgressView(
                    timerInterval: Date.now ... countdown.fireDate,
                    countsDown: true,
                    label: { EmptyView() },
                    currentValueLabel: {
                        Image(systemName: "pills.fill")
                            .scaleEffect(0.8)
                    }
                )
            default:
                Image(systemName: "bell.fill")
                    .foregroundStyle(tint)
            }
        }
        .progressViewStyle(.circular)
        .foregroundStyle(tint)
        .tint(tint)
    }
}

/// Standard AlarmKit control buttons (Taken + Snooze).
@available(iOS 26.0, *)
struct AlarmControlButtons: View {
    var state: AlarmPresentationState

    var body: some View {
        HStack(spacing: 4) {
            Button(intent: TakeMedicationIntent(
                alarmID: state.alarmID.uuidString,
                slotTimeSec: 0,
                scheduleIds: ""
            )) {
                Label(String(localized: "alarm_button_taken"), systemImage: "checkmark.circle.fill")
                    .lineLimit(1)
            }
            .tint(.green)
            .buttonStyle(.borderedProminent)
            .frame(height: 30)

            Button(intent: SnoozeMedicationIntent(
                alarmID: state.alarmID.uuidString,
                slotTimeSec: 0,
                scheduleIds: ""
            )) {
                Label(String(localized: "alarm_button_snooze"), systemImage: "clock.arrow.circlepath")
                    .lineLimit(1)
            }
            .tint(.blue)
            .buttonStyle(.borderedProminent)
            .frame(height: 30)
        }
    }
}
