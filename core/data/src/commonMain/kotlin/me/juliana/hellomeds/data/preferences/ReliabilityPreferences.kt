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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.juliana.hellomeds.data.util.AwaySummary

/**
 * Persistent flags that drive the reliability/recovery surfaces in the UI.
 *
 * [lastReconciliationTimestamp] backs the missed-dose detector's lookback window.
 * The detector queries `[max(this, now - 24h), now]` and shows past doses with
 * no history record.
 *
 * [exactAlarmsDisabled] is written by the Android `AlarmReconciler` whenever it
 * falls back from `setAlarmClock` to `setAndAllowWhileIdle` because the user (or
 * the OS) revoked `SCHEDULE_EXACT_ALARM`. Read by shared UI to surface a banner.
 *
 * [iosNotificationBudgetExhausted] is written by `IOSScheduleReconciler` when the
 * projected slot count exceeds the per-app local-notification quota (60 minus a
 * 5-slot snooze reserve = 55 events). Read by shared UI to surface a banner.
 *
 * [databaseRecovered] is written by `DatabaseKeyManager` when an encryption-key
 * failure forced a destructive wipe of the local database. The shared UI surfaces
 * a "data lost — restore from backup" banner, and the user clears it via
 * `setDatabaseRecovered(false)` once acknowledged.
 *
 * [awaySummary] accumulates the doses `UnloggedDoseResolver` logged after a long absence
 * until the user dismisses the banner with [clearAwaySummary].
 */
class ReliabilityPreferences(private val dataStore: DataStore<Preferences>) : ResolverStateStore {

    companion object {
        private val AUTO_RESOLVE_WATERMARK = longPreferencesKey("auto_resolve_watermark")
        private val LAST_SEEN_TIME_ZONE_ID = stringPreferencesKey("last_seen_time_zone_id")
        private val LAST_RECONCILIATION_TIMESTAMP =
            longPreferencesKey("last_reconciliation_timestamp")
        private val EXACT_ALARMS_DISABLED = booleanPreferencesKey("exact_alarms_disabled")
        private val IOS_NOTIFICATION_BUDGET_EXHAUSTED =
            booleanPreferencesKey("ios_notification_budget_exhausted")
        private val DATABASE_RECOVERED = booleanPreferencesKey("database_recovered")
        private val AWAY_SKIPPED = intPreferencesKey("away_summary_skipped")
        private val AWAY_TAKEN = intPreferencesKey("away_summary_taken")
    }

    /**
     * Last time the missed-dose detector was acknowledged by the user.
     * `null` on first run, so the caller defaults to `now - 24h` for the lookback.
     */
    val lastReconciliationTimestamp: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[LAST_RECONCILIATION_TIMESTAMP]
    }

    val exactAlarmsDisabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[EXACT_ALARMS_DISABLED] ?: false
    }

    val iosNotificationBudgetExhausted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[IOS_NOTIFICATION_BUDGET_EXHAUSTED] ?: false
    }

    val databaseRecovered: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DATABASE_RECOVERED] ?: false
    }

    val awaySummary: Flow<AwaySummary?> = dataStore.data.map { prefs ->
        val skipped = prefs[AWAY_SKIPPED] ?: 0
        val taken = prefs[AWAY_TAKEN] ?: 0
        if (skipped + taken > 0) AwaySummary(skipped, taken) else null
    }

    override suspend fun getWatermark(): Long? = dataStore.data.first()[AUTO_RESOLVE_WATERMARK]

    override suspend fun setWatermark(value: Long) {
        dataStore.edit { it[AUTO_RESOLVE_WATERMARK] = value }
    }

    override suspend fun getLastSeenTimeZoneId(): String? = dataStore.data.first()[LAST_SEEN_TIME_ZONE_ID]

    override suspend fun setLastSeenTimeZoneId(id: String) {
        dataStore.edit { it[LAST_SEEN_TIME_ZONE_ID] = id }
    }

    override suspend fun addAwaySummary(skipped: Int, taken: Int) {
        dataStore.edit {
            it[AWAY_SKIPPED] = (it[AWAY_SKIPPED] ?: 0) + skipped
            it[AWAY_TAKEN] = (it[AWAY_TAKEN] ?: 0) + taken
        }
    }

    suspend fun clearAwaySummary() {
        dataStore.edit {
            it.remove(AWAY_SKIPPED)
            it.remove(AWAY_TAKEN)
        }
    }

    suspend fun setLastReconciliationTimestamp(timestamp: Long) {
        dataStore.edit { it[LAST_RECONCILIATION_TIMESTAMP] = timestamp }
    }

    suspend fun setExactAlarmsDisabled(disabled: Boolean) {
        dataStore.edit { it[EXACT_ALARMS_DISABLED] = disabled }
    }

    suspend fun setIosNotificationBudgetExhausted(exhausted: Boolean) {
        dataStore.edit { it[IOS_NOTIFICATION_BUDGET_EXHAUSTED] = exhausted }
    }

    suspend fun setDatabaseRecovered(recovered: Boolean) {
        dataStore.edit { it[DATABASE_RECOVERED] = recovered }
    }
}
