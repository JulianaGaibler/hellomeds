// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import UserNotifications
import shared

/// Configures UNMutableNotificationContent with interruption level and sound.
/// Called from Kotlin via bridge — avoids K/N issues with UNNotificationInterruptionLevel enum.
///
/// Raw level values:
/// - 0: active (standard)
/// - 1: critical (bypasses DnD + mute switch, default critical sound)
/// - 2: passive (silent)
/// - 3: timeSensitive (bypasses Focus Mode)
/// - 4: alarm (critical interruption + custom alarm sound file)
func setupNotificationBridge() {
    IOSScheduleReconcilerKt.registerNotificationConfigurator { content, level in
        if #available(iOS 15.0, *) {
            switch level {
            case 0:
                content.interruptionLevel = .active
            case 1:
                content.interruptionLevel = .critical
                content.sound = .defaultCriticalSound(withAudioVolume: 1.0)
            case 2:
                content.interruptionLevel = .passive
            case 3:
                content.interruptionLevel = .timeSensitive
            case 4:
                // Alarm: critical interruption level with custom bundled alarm sound
                content.interruptionLevel = .critical
                content.sound = .criticalSoundNamed(
                    UNNotificationSoundName("alarm_sound.caf"),
                    withAudioVolume: 1.0
                )
            default:
                content.interruptionLevel = .active
            }
        }
    }
}
