// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.notifications

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.MainActivity
import me.juliana.hellomeds.R
import me.juliana.hellomeds.data.database.entities.Medication
import me.juliana.hellomeds.data.util.AppLogger
import me.juliana.hellomeds.data.model.ProjectedEvent
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.depletion_notification_action_mark_depleted
import me.juliana.hellomeds.shared.depletion_notification_text
import me.juliana.hellomeds.shared.depletion_notification_title
import me.juliana.hellomeds.shared.depletion_notification_title_discreet
import me.juliana.hellomeds.shared.low_stock_notification_text
import me.juliana.hellomeds.shared.low_stock_notification_title
import me.juliana.hellomeds.shared.low_stock_notification_title_discreet
import me.juliana.hellomeds.shared.notification_action_all_skipped
import me.juliana.hellomeds.shared.notification_action_all_taken
import me.juliana.hellomeds.shared.notification_action_skipped
import me.juliana.hellomeds.shared.notification_action_snooze
import me.juliana.hellomeds.shared.notification_action_taken
import me.juliana.hellomeds.shared.notification_text_time_log_generic
import me.juliana.hellomeds.shared.notification_text_time_log_named
import me.juliana.hellomeds.shared.notification_title_followup
import me.juliana.hellomeds.shared.notification_title_followup_numbered
import me.juliana.hellomeds.shared.notification_title_reminder
import me.juliana.hellomeds.shared.notification_title_snoozed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds medication reminder notifications.
 * Replaces NotificationHelper with a simplified API for the reconciler architecture.
 *
 * Key difference: channel ID and notification ID are passed in by the caller
 * (GlobalAlarmReceiver / AlarmReconciler) instead of being computed internally.
 */
class NotificationBuilder(private val context: Context) {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Build a medication-reminder notification.
     *
     * @param events Pending projected events to include.
     * @param medications Matching entities in the same order as [events].
     * @param notificationId Stable ID from NotificationIdGenerator.
     * @param scheduledTime Original time for the event group.
     * @param groupKey Always set for time-slot notifications — drives visual grouping.
     * @param isFollowUpAlert When true, sets `setOnlyAlertOnce(false)`. Ignored for children when
     *   `GROUP_ALERT_SUMMARY` is active on the summary, since children are muted by the OS.
     */
    fun buildNotification(
        events: List<ProjectedEvent>,
        medications: List<Medication>,
        channelId: String,
        notificationId: Int,
        scheduledTime: Long,
        lockScreenVisibility: LockScreenVisibility,
        isFollowUp: Boolean = false,
        isSnoozed: Boolean = false,
        groupKey: String? = null,
        isFollowUpAlert: Boolean = false,
        followUpNumber: Int = 0,
    ): Notification {
        // Downgrade to NORMAL channel if all events are placebo (cycle break days)
        val effectiveChannelId = if (events.all { it.isPlacebo }) {
            NotificationChannels.NORMAL_CHANNEL_ID
        } else {
            channelId
        }
        val isCritical = effectiveChannelId == NotificationChannels.CRITICAL_CHANNEL_ID
        val isAlarm = effectiveChannelId == NotificationChannels.ALARM_CHANNEL_ID

        val timeStr = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(scheduledTime),
            ZoneId.systemDefault(),
        ).format(timeFormatter)

        val title = buildNotificationTitle(medications, isFollowUp, isSnoozed, followUpNumber)
        val text = buildNotificationText(medications, timeStr, lockScreenVisibility)

        val visibility = when (lockScreenVisibility) {
            LockScreenVisibility.SHOW_WITH_NAMES -> NotificationCompat.VISIBILITY_PUBLIC
            LockScreenVisibility.SHOW_WITHOUT_NAMES -> NotificationCompat.VISIBILITY_PRIVATE
            LockScreenVisibility.HIDE -> NotificationCompat.VISIBILITY_SECRET
        }

        val scheduleIds = events.map { it.scheduleId }.toIntArray()

        val builder = NotificationCompat.Builder(context, effectiveChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.notification_pill_24dp)
            .setPriority(
                if (isCritical || isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH,
            )
            .setCategory(
                if (isCritical || isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER,
            )
            .setVisibility(visibility)
            .setAutoCancel(false)
            .setOngoing(isAlarm) // Alarm notifications cannot be swiped away
            .setOnlyAlertOnce(!isFollowUpAlert)

        // Always attach groupKey even for single notifications. Android treats a lone grouped
        // notification as a normal standalone. If a sibling arrives later (e.g., staggered snooze),
        // this notification seamlessly joins the group without needing to be rebuilt.
        if (groupKey != null) {
            builder.setGroup(groupKey)
        }

        // Full Screen Intent: only for alarm-type notifications.
        // Wakes the screen and shows AlarmActivity over the lock screen.
        if (isAlarm) {
            builder.setFullScreenIntent(
                createFullScreenIntent(scheduleIds, scheduledTime, notificationId, medications, lockScreenVisibility),
                true,
            )
        }

        // Actions: Take All / Skip All / Snooze for multiple meds, or Take / Skip / Snooze for single
        if (events.size > 1) {
            builder.addAction(createAllTakenAction(scheduleIds, scheduledTime, notificationId))
            builder.addAction(createAllSkippedAction(scheduleIds, scheduledTime, notificationId))
            builder.addAction(createSnoozeAction(scheduleIds, scheduledTime, notificationId))
        } else {
            builder.addAction(createTakenAction(scheduleIds, scheduledTime, notificationId))
            builder.addAction(createSkippedAction(scheduleIds, scheduledTime, notificationId))
            builder.addAction(createSnoozeAction(scheduleIds, scheduledTime, notificationId))
        }

        builder.setContentIntent(createContentIntent(scheduleIds, scheduledTime))

        return builder.build()
    }

    /**
     * Build a group summary notification that visually groups individual medication notifications.
     *
     * The summary owns the alert (GROUP_ALERT_SUMMARY) — children are muted by the OS.
     * When a follow-up fires, re-post the summary with isFollowUpAlert=true to trigger
     * Heads-Up screen wake on behalf of the child.
     *
     * @param events ALL pending events in this time slot group
     * @param medications ALL matching medications
     * @param channelId CRITICAL if any child is critical, else NORMAL
     * @param notificationId Stable summary ID (from generateSessionNotificationId)
     * @param scheduledTime Original scheduled time for the time slot
     * @param lockScreenVisibility Privacy setting for lock screen
     * @param groupKey The group key shared with all children
     * @param isFollowUp Whether this is a follow-up reminder
     * @param isSnoozed Whether this is a snoozed reminder
     * @param isFollowUpAlert When true, setOnlyAlertOnce(false) to force Heads-Up screen wake
     */
    fun buildGroupSummaryNotification(
        events: List<ProjectedEvent>,
        medications: List<Medication>,
        channelId: String,
        notificationId: Int,
        scheduledTime: Long,
        lockScreenVisibility: LockScreenVisibility,
        groupKey: String,
        isFollowUp: Boolean = false,
        isSnoozed: Boolean = false,
        isFollowUpAlert: Boolean = false,
    ): Notification {
        // Downgrade to NORMAL channel if all events are placebo (cycle break days)
        val effectiveChannelId = if (events.all { it.isPlacebo }) {
            NotificationChannels.NORMAL_CHANNEL_ID
        } else {
            channelId
        }
        val isCritical = effectiveChannelId == NotificationChannels.CRITICAL_CHANNEL_ID
        val isAlarm = effectiveChannelId == NotificationChannels.ALARM_CHANNEL_ID

        val timeStr = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(scheduledTime),
            ZoneId.systemDefault(),
        ).format(timeFormatter)

        val title = buildNotificationTitle(medications, isFollowUp, isSnoozed)
        val text = buildNotificationText(medications, timeStr, lockScreenVisibility)

        val visibility = when (lockScreenVisibility) {
            LockScreenVisibility.SHOW_WITH_NAMES -> NotificationCompat.VISIBILITY_PUBLIC
            LockScreenVisibility.SHOW_WITHOUT_NAMES -> NotificationCompat.VISIBILITY_PRIVATE
            LockScreenVisibility.HIDE -> NotificationCompat.VISIBILITY_SECRET
        }

        val scheduleIds = events.map { it.scheduleId }.toIntArray()

        val builder = NotificationCompat.Builder(context, effectiveChannelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.notification_pill_24dp)
            .setPriority(
                if (isCritical || isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH,
            )
            .setCategory(
                if (isCritical || isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER,
            )
            .setVisibility(visibility)
            .setAutoCancel(false)
            .setOngoing(isAlarm)
            .setOnlyAlertOnce(!isFollowUpAlert)
            .setGroup(groupKey)
            .setGroupSummary(true)
            // GROUP_ALERT_SUMMARY: the summary notification owns all sound/vibration/screen wake.
            // Children are completely muted by the OS, even if they set setOnlyAlertOnce(false).
            // This avoids Android's burst spam filter — posting 2-3 children simultaneously with
            // alert privileges triggers the anti-spam filter and mutes ALL of them. By giving
            // alert privileges exclusively to the summary, we get one clean buzz/screen wake.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        // Full Screen Intent: only for alarm-type notifications.
        if (isAlarm) {
            builder.setFullScreenIntent(
                createFullScreenIntent(scheduleIds, scheduledTime, notificationId, medications, lockScreenVisibility),
                true,
            )
        }

        // Summary always gets Take All / Skip All / Snooze
        builder.addAction(createAllTakenAction(scheduleIds, scheduledTime, notificationId))
        builder.addAction(createAllSkippedAction(scheduleIds, scheduledTime, notificationId))
        builder.addAction(createSnoozeAction(scheduleIds, scheduledTime, notificationId))

        builder.setContentIntent(createContentIntent(scheduleIds, scheduledTime))

        return builder.build()
    }

    fun buildNotificationTitle(
        medications: List<Medication>,
        isFollowUp: Boolean,
        isSnoozed: Boolean = false,
        followUpNumber: Int = 0,
    ): String {
        val count = medications.size

        return runBlocking {
            try {
                when {
                    isSnoozed -> getPluralString(Res.plurals.notification_title_snoozed, count)

                    // Show "#N" starting from follow-up #2. First follow-up shows unnumbered title.
                    // Numbering serves two purposes: (1) user sees escalating urgency,
                    // (2) unique text prevents Android from suppressing alerts for identical content.
                    isFollowUp && followUpNumber >= 2 -> getPluralString(
                        Res.plurals.notification_title_followup_numbered,
                        count,
                        followUpNumber,
                    )

                    isFollowUp -> getPluralString(Res.plurals.notification_title_followup, count)

                    else -> getPluralString(Res.plurals.notification_title_reminder, count)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to build notification title", e)
                "Medication reminder"
            }
        }
    }

    fun buildNotificationText(
        medications: List<Medication>,
        timeStr: String,
        lockScreenVisibility: LockScreenVisibility,
    ): String {
        val count = medications.size

        return runBlocking {
            try {
                when (lockScreenVisibility) {
                    LockScreenVisibility.SHOW_WITH_NAMES -> {
                        val nameList = medications.map { it.displayName ?: it.name }
                        val formattedNames = android.icu.text.ListFormatter.getInstance(
                            java.util.Locale.getDefault(),
                            android.icu.text.ListFormatter.Type.AND,
                            android.icu.text.ListFormatter.Width.WIDE,
                        ).format(nameList)
                        getPluralString(
                            Res.plurals.notification_text_time_log_named,
                            count,
                            timeStr,
                            formattedNames,
                        )
                    }

                    LockScreenVisibility.SHOW_WITHOUT_NAMES -> {
                        getPluralString(
                            Res.plurals.notification_text_time_log_generic,
                            count,
                            timeStr,
                            count,
                        )
                    }

                    LockScreenVisibility.HIDE -> ""
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to build notification text", e)
                "You have medications to log."
            }
        }
    }

    // --- Action button builders ---

    private fun createTakenAction(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "${context.packageName}.NOTIFICATION_ACTION"
            putExtra("action", "TAKEN")
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        // Action request codes use notificationId * 10 + offset.
        // All notification IDs are < 4_000_000, so max request code is ~40M (well within Int.MAX_VALUE).
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            0,
            safeGetString(Res.string.notification_action_taken, "Taken"),
            pi,
        ).build()
    }

    private fun createSkippedAction(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "${context.packageName}.NOTIFICATION_ACTION"
            putExtra("action", "SKIPPED")
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            0,
            safeGetString(Res.string.notification_action_skipped, "Skipped"),
            pi,
        ).build()
    }

    private fun createSnoozeAction(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "${context.packageName}.NOTIFICATION_ACTION"
            putExtra("action", "SNOOZED")
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            0,
            safeGetString(Res.string.notification_action_snooze, "Snooze"),
            pi,
        ).build()
    }

    private fun createAllTakenAction(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "${context.packageName}.NOTIFICATION_ACTION"
            putExtra("action", "TAKEN")
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            0,
            safeGetString(Res.string.notification_action_all_taken, "All taken"),
            pi,
        ).build()
    }

    private fun createAllSkippedAction(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = "${context.packageName}.NOTIFICATION_ACTION"
            putExtra("action", "SKIPPED")
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            0,
            safeGetString(Res.string.notification_action_all_skipped, "All skipped"),
            pi,
        ).build()
    }

    // --- Weight measurement notification ---

    // --- Medication reminder content intent ---

    private fun createContentIntent(scheduleIds: IntArray, scheduledTime: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_SCHEDULE_IDS", scheduleIds)
            putExtra("NOTIFICATION_SCHEDULED_TIME", scheduledTime)
            putExtra("OPEN_LOG_SHEET", true)
        }
        val requestCode =
            CONTENT_INTENT_BASE + ((scheduleIds.contentHashCode() and 0x7FFFFFFF) % 1_000_000)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // --- Full Screen Intent ---

    private fun createFullScreenIntent(
        scheduleIds: IntArray,
        scheduledTime: Long,
        notificationId: Int,
        medications: List<Medication>,
        lockScreenVisibility: LockScreenVisibility = LockScreenVisibility.SHOW_WITH_NAMES,
    ): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("scheduleIds", scheduleIds)
            putExtra("scheduledTime", scheduledTime)
            putExtra("notificationId", notificationId)
            putExtra("medicationNames", medications.map { it.displayName ?: it.name }.toTypedArray())
            putExtra("lockScreenVisibility", lockScreenVisibility.name)
        }
        val requestCode = FSI_INTENT_BASE + (notificationId % 1_000_000)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // --- Low stock notification ---

    /**
     * Build a notification alerting the user that a medication is running low.
     * Posted once when stock crosses below the user-defined threshold.
     *
     * @param medication The medication with low stock
     * @param notificationId Stable ID from NotificationIdGenerator.generateLowStockNotificationId
     */
    fun buildLowStockNotification(
        medication: Medication,
        notificationId: Int,
        discreet: Boolean = false,
        lockScreenVisibility: LockScreenVisibility = LockScreenVisibility.SHOW_WITH_NAMES,
    ): Notification {
        val title = try {
            runBlocking {
                if (discreet) {
                    getString(Res.string.low_stock_notification_title_discreet)
                } else {
                    getString(Res.string.low_stock_notification_title, medication.displayName ?: medication.name)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build low stock title", e)
            "Low stock alert"
        }
        val text = try {
            runBlocking { getString(Res.string.low_stock_notification_text) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build low stock text", e)
            "A medication is running low."
        }

        val visibility = when (lockScreenVisibility) {
            LockScreenVisibility.SHOW_WITH_NAMES -> NotificationCompat.VISIBILITY_PUBLIC
            LockScreenVisibility.SHOW_WITHOUT_NAMES -> NotificationCompat.VISIBILITY_PRIVATE
            LockScreenVisibility.HIDE -> NotificationCompat.VISIBILITY_SECRET
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.STOCK_ALERTS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.notification_logo_24dp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(visibility)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        builder.setContentIntent(createStockDetailIntent(medication.id))

        return builder.build()
    }

    // --- Depletion reminder notification ---

    /**
     * Build a notification reminding the user that a container may be empty.
     * Includes a "Mark Depleted" action button for quick container depletion logging.
     *
     * @param medication The medication whose container may be depleted
     * @param dosesSinceDepletion Number of doses taken since last container change
     * @param notificationId Stable ID from NotificationIdGenerator.generateDepletionNotificationId
     */
    fun buildDepletionReminderNotification(
        medication: Medication,
        dosesSinceDepletion: Int,
        notificationId: Int,
        discreet: Boolean = false,
        lockScreenVisibility: LockScreenVisibility = LockScreenVisibility.SHOW_WITH_NAMES,
    ): Notification {
        val title = try {
            runBlocking {
                if (discreet) {
                    getString(Res.string.depletion_notification_title_discreet)
                } else {
                    val displayName = medication.displayName ?: medication.name
                    getString(Res.string.depletion_notification_title, displayName)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build depletion title", e)
            "Container reminder"
        }
        val text = try {
            runBlocking { getString(Res.string.depletion_notification_text, dosesSinceDepletion) }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to build depletion text", e)
            "A medication container may need replacing."
        }

        val visibility = when (lockScreenVisibility) {
            LockScreenVisibility.SHOW_WITH_NAMES -> NotificationCompat.VISIBILITY_PUBLIC
            LockScreenVisibility.SHOW_WITHOUT_NAMES -> NotificationCompat.VISIBILITY_PRIVATE
            LockScreenVisibility.HIDE -> NotificationCompat.VISIBILITY_SECRET
        }

        val builder =
            NotificationCompat.Builder(context, NotificationChannels.DEPLETION_REMINDERS_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.notification_logo_24dp)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(visibility)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)

        builder.setContentIntent(createStockDetailIntent(medication.id))
        builder.addAction(createContainerDepletedAction(medication.id, notificationId))

        return builder.build()
    }

    private fun createContainerDepletedAction(medicationId: Int, notificationId: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            putExtra("action", "CONTAINER_DEPLETED")
            putExtra("medicationId", medicationId)
            putExtra("notificationId", notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DEPLETION_INTENT_BASE + medicationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.notification_logo_24dp,
            safeGetString(Res.string.depletion_notification_action_mark_depleted, "Mark depleted"),
            pendingIntent,
        ).build()
    }

    private fun createStockDetailIntent(medicationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_STOCK_DETAIL", true)
            putExtra("STOCK_DETAIL_MEDICATION_ID", medicationId)
        }
        return PendingIntent.getActivity(
            context,
            LOW_STOCK_INTENT_BASE + medicationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Resolves a CMP string resource with a hardcoded fallback if resolution fails. */
    private fun safeGetString(resource: StringResource, fallback: String): String = try {
        runBlocking { getString(resource) }
    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to resolve string resource", e)
        fallback
    }

    companion object {
        private const val TAG = "NotificationBuilder"
        private const val CONTENT_INTENT_BASE = 5_000_000
        private const val LOW_STOCK_INTENT_BASE = 6_000_000
        private const val DEPLETION_INTENT_BASE = 7_000_000
        private const val FSI_INTENT_BASE = 8_000_000
    }
}
