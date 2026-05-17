// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.juliana.hellomeds.data.model.enums.LockScreenVisibility
import me.juliana.hellomeds.data.model.enums.NotificationGroupingMode

class NotificationPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val USE_EXACT_ALARMS = booleanPreferencesKey("use_exact_alarms")
        private val SNOOZE_INTERVAL_MINUTES = intPreferencesKey("snooze_interval_minutes")
        private val LOCK_SCREEN_VISIBILITY = stringPreferencesKey("lock_screen_visibility")
        private val GROUPING_MODE = stringPreferencesKey("grouping_mode")
        private val SCHEDULING_WINDOW_HOURS = intPreferencesKey("scheduling_window_hours")
        private val LAST_SCHEDULING_TIMESTAMP = longPreferencesKey("last_scheduling_timestamp")
        private val HAS_SEEN_CRITICAL_CHANNEL_DIALOG =
            booleanPreferencesKey("has_seen_critical_channel_dialog")
        private val HAS_SEEN_REMINDER_TYPE_HINT =
            booleanPreferencesKey("has_seen_reminder_type_hint")
    }

    // Flows for reading preferences
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED] ?: true
    }

    val useExactAlarms: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_EXACT_ALARMS] ?: true
    }

    val snoozeIntervalMinutes: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SNOOZE_INTERVAL_MINUTES] ?: 10
    }

    val lockScreenVisibility: Flow<LockScreenVisibility> =
        dataStore.data.map { preferences ->
            val value = preferences[LOCK_SCREEN_VISIBILITY] ?: "SHOW_WITH_NAMES"
            try {
                LockScreenVisibility.valueOf(value)
            } catch (e: IllegalArgumentException) {
                LockScreenVisibility.SHOW_WITH_NAMES
            }
        }

    val groupingMode: Flow<NotificationGroupingMode> =
        dataStore.data.map { preferences ->
            val value = preferences[GROUPING_MODE] ?: "COMBINED"
            NotificationGroupingMode.fromValue(value)
        }

    val schedulingWindowHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[SCHEDULING_WINDOW_HOURS] ?: 24
    }

    val lastSchedulingTimestamp: Flow<Long> = dataStore.data.map { preferences ->
        preferences[LAST_SCHEDULING_TIMESTAMP] ?: 0L
    }

    // Setter methods
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setUseExactAlarms(useExact: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_EXACT_ALARMS] = useExact
        }
    }

    suspend fun setSnoozeInterval(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[SNOOZE_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setLockScreenVisibility(visibility: LockScreenVisibility) {
        dataStore.edit { preferences ->
            preferences[LOCK_SCREEN_VISIBILITY] = visibility.name
        }
    }

    suspend fun setGroupingMode(mode: NotificationGroupingMode) {
        dataStore.edit { preferences ->
            preferences[GROUPING_MODE] = mode.value
        }
    }

    suspend fun setSchedulingWindowHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[SCHEDULING_WINDOW_HOURS] = hours
        }
    }

    suspend fun setLastSchedulingTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_SCHEDULING_TIMESTAMP] = timestamp
        }
    }

    val hasSeenCriticalChannelDialog: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[HAS_SEEN_CRITICAL_CHANNEL_DIALOG] ?: false
        }

    suspend fun setHasSeenCriticalChannelDialog(seen: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_CRITICAL_CHANNEL_DIALOG] = seen
        }
    }

    val hasSeenReminderTypeHint: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[HAS_SEEN_REMINDER_TYPE_HINT] ?: false
        }

    suspend fun setHasSeenReminderTypeHint(seen: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_SEEN_REMINDER_TYPE_HINT] = seen
        }
    }
}
