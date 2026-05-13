// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

/**
 * Generates collision-resistant notification and alarm IDs.
 *
 * ARCHITECTURAL PRINCIPLE (Decoupled IDs):
 * - Alarm Request Codes: Unique per alarm instance (main, follow-up 1, 2...) for AlarmManager
 * - Notification IDs: Stable per session (original event + all follow-ups) for NotificationManager
 *
 * Design Goals:
 * - Uses hash-based approach, not direct timestamp cast
 * - Collision-resistant across multiple concurrent alarms
 * - Stable IDs enable Android's natural "update in place" for notifications
 *
 * ID Ranges (non-overlapping):
 * - Session IDs:     [0,         1_000_000)
 * - Depletion IDs:   [1_000_000, 2_000_000)
 * - Low stock IDs:   [2_000_000, 3_000_000)
 * - Medication IDs:  [3_000_000, 4_000_000)
 * - Escalated IDs:   [4_000_000, 5_000_000)
 * - Alarm Esc. IDs:  [5_000_000, 6_000_000)
 */
object NotificationIdGenerator {

    /**
     * Generates a unique alarm request code for AlarmManager.
     * MUST be unique for each alarm instance to prevent overwriting in AlarmManager.
     *
     * Uses minutes-since-epoch as base with large prime offset for follow-ups.
     *
     * @param scheduledTime Timestamp when alarm fires (in milliseconds)
     * @param followUpIndex Follow-up number (0 for main alarm, 1+ for follow-ups)
     * @return Unique positive integer for PendingIntent request code
     */
    fun generateAlarmRequestCode(scheduledTime: Long, followUpIndex: Int = 0): Int {
        // Use minutes-since-epoch as base (reduces collision risk vs hash)
        val minutesBase = (scheduledTime / 60_000L).toInt()

        // Large prime offset for follow-ups to prevent overlap
        val followUpOffset = followUpIndex * 1_000_003

        // Combine components
        val result = minutesBase.toLong() + followUpOffset

        // Coerce to valid Int range (0 to Int.MAX_VALUE)
        return result.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Generates a stable session notification ID for NotificationManager.
     * Uses originalScheduledTime (immutable) to maintain session continuity across snoozes.
     *
     * All notifications for the same session (main + follow-ups + snoozes) share this ID,
     * enabling Android's automatic "update in place" behavior.
     *
     * @param originalScheduledTime Original scheduled time before any snoozes (in milliseconds)
     * @return Stable positive integer for NotificationManager
     */
    fun generateSessionNotificationId(originalScheduledTime: Long): Int {
        return (originalScheduledTime.hashCode() and 0x7FFFFFFF) % SESSION_ID_CEILING
    }

    /**
     * DEPRECATED: Use generateAlarmRequestCode() instead.
     * Kept for backward compatibility.
     */
    @Deprecated(
        "Use generateAlarmRequestCode()",
        ReplaceWith("generateAlarmRequestCode(scheduledTime, salt)"),
    )
    fun generateRequestCode(scheduledTime: Long, salt: Int = 0): Int {
        return generateAlarmRequestCode(scheduledTime, salt)
    }

    /**
     * DEPRECATED: Use generateTimeBasedId() or generateSessionNotificationId() instead.
     * This method conflated alarm and notification IDs, causing stacking issues.
     */
    @Deprecated(
        "Use generateSessionNotificationId() for notifications",
        ReplaceWith("generateSessionNotificationId(scheduledTime)"),
    )
    fun generateTimeBasedId(scheduledTime: Long): Int {
        return (scheduledTime.hashCode() and 0x7FFFFFFF) % SESSION_ID_CEILING
    }

    /**
     * DEPRECATED: Follow-ups now share the same session notification ID.
     * Use generateSessionNotificationId() for the notification ID,
     * and generateAlarmRequestCode() with followUpIndex for the alarm request code.
     *
     * This method created stacking notifications by generating different IDs.
     * The new architecture uses stable session IDs for notification updates.
     */
    @Deprecated(
        "Follow-ups should share session ID, not create new IDs",
        ReplaceWith("generateSessionNotificationId(originalScheduledTime)"),
    )
    fun generateFollowUpId(baseNotifId: Int, followUpIndex: Int): Int {
        val offset = followUpIndex * 1_000_003L
        val result = (baseNotifId.toLong() + offset)

        return when {
            result > Int.MAX_VALUE -> (result - Int.MAX_VALUE).toInt()
            result < 0 -> result.toInt() + Int.MAX_VALUE
            else -> result.toInt()
        }
    }

    /**
     * Generates a stable notification ID for depletion reminder notifications.
     * Uses a fixed offset + medication ID to avoid collision with session notification IDs.
     * Only one depletion reminder notification per medication exists at a time.
     *
     * @param medicationId The medication whose container may be depleted
     * @return Stable positive integer for NotificationManager
     */
    fun generateDepletionNotificationId(medicationId: Int): Int {
        return DEPLETION_ID_OFFSET + medicationId
    }

    private const val DEPLETION_ID_OFFSET = 1_000_000
    private const val LOW_STOCK_ID_OFFSET = 2_000_000
    private const val MEDICATION_NOTIF_OFFSET = 3_000_000
    private const val ESCALATED_NOTIF_OFFSET = 4_000_000
    private const val ALARM_ESCALATED_NOTIF_OFFSET = 5_000_000
    private const val SESSION_ID_CEILING = 1_000_000
    private const val MEDICATION_NOTIF_CEILING = 1_000_000
    private const val ESCALATED_NOTIF_CEILING = 1_000_000
    private const val ALARM_ESCALATED_NOTIF_CEILING = 1_000_000

    /**
     * Generates a stable notification ID for low stock alerts.
     * Uses a fixed offset + medication ID to avoid collision with other notification IDs.
     * Only one low stock notification per medication exists at a time.
     *
     * @param medicationId The medication with low stock
     * @return Stable positive integer for NotificationManager
     */
    fun generateLowStockNotificationId(medicationId: Int): Int {
        return LOW_STOCK_ID_OFFSET + medicationId
    }

    /**
     * Generates a stable notification ID for an individual medication's notification.
     * Used in GROUPED mode and for per-medication follow-ups after combined→individual split.
     *
     * ID = hash("scheduledTime_scheduleId") masked positive, in range [3_000_000, 3_500_000).
     * Avoids collision with session IDs, measurement IDs (1M+), and low stock IDs (2M+).
     *
     * @param scheduledTime Original scheduled time (milliseconds)
     * @param scheduleId The schedule entity ID for this specific medication
     * @return Stable positive integer for NotificationManager
     */
    fun generateMedicationNotificationId(scheduledTime: Long, scheduleId: Int): Int {
        val hash = "${scheduledTime}_$scheduleId".hashCode() and 0x7FFFFFFF
        return MEDICATION_NOTIF_OFFSET + (hash % MEDICATION_NOTIF_CEILING)
    }

    /**
     * Generates a new notification ID for channel escalation (NORMAL → CRITICAL).
     *
     * Android's cancel() is asynchronous. Calling cancel(id) then notify(id) with the
     * same ID can race — the system may process notify first, then cancel, swallowing
     * the notification. Using a distinct ID eliminates this race entirely.
     *
     * @param originalId The notification ID before escalation
     * @return A new ID in range [4_000_000, 5_000_000), guaranteed different from the original
     */
    fun generateEscalatedNotificationId(originalId: Int): Int =
        ESCALATED_NOTIF_OFFSET + (originalId % ESCALATED_NOTIF_CEILING)

    /**
     * Generates a new notification ID for alarm escalation (NORMAL/CRITICAL → ALARM).
     * Uses a separate range from critical escalation to avoid collisions when
     * a notification escalates through both stages (NORMAL → CRITICAL → ALARM).
     *
     * @param originalId The notification ID before alarm escalation
     * @return A new ID in range [5_000_000, 6_000_000), guaranteed different from the original
     */
    fun generateAlarmEscalatedNotificationId(originalId: Int): Int =
        ALARM_ESCALATED_NOTIF_OFFSET + (originalId % ALARM_ESCALATED_NOTIF_CEILING)
}
