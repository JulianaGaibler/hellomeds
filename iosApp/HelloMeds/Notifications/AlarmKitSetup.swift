// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import shared

/// Registers the AlarmKit callback bridge during app initialization.
/// Must be called synchronously before Koin init (same timing as other bridges).
///
/// On iOS 26+: registers real AlarmKit callbacks.
/// On older iOS: registers with isAvailable=false (Kotlin falls back to critical notifications).
func setupAlarmKitBridge() {
    if #available(iOS 26.0, *) {
        AlarmKitCallbackKt.registerAlarmKitBridge(
            isAvailable: true,
            scheduleAlarm: { idStr, title, body, fireDateMs, slotTimeMs, scheduleIds, medNames, isCritical, snoozeDurationSec, completion in
                guard let uuid = UUID(uuidString: idStr) else {
                    _ = completion(KotlinBoolean(value: false))
                    return
                }
                let fireDate = Date(timeIntervalSince1970: Double(truncating: fireDateMs) / 1000.0)

                Task {
                    do {
                        try await AlarmKitBridge.scheduleAlarm(
                            id: uuid,
                            fireDate: fireDate,
                            title: title,
                            body: body,
                            slotTimeMs: slotTimeMs.int64Value,
                            scheduleIds: scheduleIds,
                            medicationNames: medNames,
                            isCritical: isCritical.boolValue,
                            snoozeDurationSeconds: snoozeDurationSec.intValue
                        )
                        _ = completion(KotlinBoolean(value: true))
                    } catch {
                        print("AlarmKitSetup: Schedule failed: \(error)")
                        _ = completion(KotlinBoolean(value: false))
                    }
                }
            },
            cancelAlarm: { idStr in
                guard let uuid = UUID(uuidString: idStr) else { return }
                AlarmKitBridge.cancelAlarm(id: uuid)
            },
            stopAlarm: { idStr in
                guard let uuid = UUID(uuidString: idStr) else { return }
                AlarmKitBridge.stopAlarm(id: uuid)
            },
            cancelAllAlarms: { completion in
                AlarmKitBridge.cancelAllAlarms()
                _ = completion()
            },
            requestAuth: { completion in
                Task {
                    let granted = await AlarmKitBridge.requestAuthorization()
                    _ = completion(KotlinBoolean(value: granted))
                }
            },
            checkAuth: { completion in
                let authorized = AlarmKitBridge.isAuthorized()
                _ = completion(KotlinBoolean(value: authorized))
            }
        )
    } else {
        // iOS < 26: AlarmKit unavailable — register with false flag
        AlarmKitCallbackKt.registerAlarmKitBridge(
            isAvailable: false,
            scheduleAlarm: nil,
            cancelAlarm: nil,
            stopAlarm: nil,
            cancelAllAlarms: nil,
            requestAuth: nil,
            checkAuth: nil
        )
    }
}
