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

class AutoBackupPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val BACKUP_RETENTION_COUNT = intPreferencesKey("backup_retention_count")
        private val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        private val LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        private val LAST_BACKUP_ERROR_MESSAGE = stringPreferencesKey("last_backup_error_message")
        private val LAST_BACKUP_MEDICATION_COUNT = intPreferencesKey("last_backup_medication_count")
        private val CONSECUTIVE_FAILURES = intPreferencesKey("consecutive_failures")
        private val BACKUP_DESTINATION_URI = stringPreferencesKey("backup_destination_uri")
        private val PASSPHRASE_HINT = stringPreferencesKey("passphrase_hint")
        private val ONBOARDING_COMPLETED_TIMESTAMP =
            longPreferencesKey("onboarding_completed_timestamp")
        private val BACKUP_NUDGE_DISMISSED = booleanPreferencesKey("backup_nudge_dismissed")

        // TODO(BETA_ROLLBACK): closed-beta survey nudge
        private val CLOSED_BETA_SURVEY_NUDGE_DISMISSED =
            booleanPreferencesKey("closed_beta_survey_nudge_dismissed")
    }

    val autoBackupEnabled: Flow<Boolean> = dataStore.data.map { it[AUTO_BACKUP_ENABLED] ?: false }
    val backupRetentionCount: Flow<Int> = dataStore.data.map { it[BACKUP_RETENTION_COUNT] ?: 7 }
    val lastBackupTimestamp: Flow<Long> = dataStore.data.map { it[LAST_BACKUP_TIMESTAMP] ?: 0L }
    val lastBackupStatus: Flow<String> = dataStore.data.map { it[LAST_BACKUP_STATUS] ?: "NEVER" }
    val lastBackupErrorMessage: Flow<String?> = dataStore.data.map { it[LAST_BACKUP_ERROR_MESSAGE] }
    val lastBackupMedicationCount: Flow<Int> =
        dataStore.data.map { it[LAST_BACKUP_MEDICATION_COUNT] ?: 0 }
    val consecutiveFailures: Flow<Int> = dataStore.data.map { it[CONSECUTIVE_FAILURES] ?: 0 }
    val backupDestinationUri: Flow<String?> = dataStore.data.map { it[BACKUP_DESTINATION_URI] }
    val onboardingCompletedTimestamp: Flow<Long> =
        dataStore.data.map { it[ONBOARDING_COMPLETED_TIMESTAMP] ?: 0L }
    val backupNudgeDismissed: Flow<Boolean> =
        dataStore.data.map { it[BACKUP_NUDGE_DISMISSED] ?: false }

    // TODO(BETA_ROLLBACK): closed-beta survey nudge
    val closedBetaSurveyNudgeDismissed: Flow<Boolean> =
        dataStore.data.map { it[CLOSED_BETA_SURVEY_NUDGE_DISMISSED] ?: false }

    val passphraseHint: Flow<String?> = dataStore.data.map { it[PASSPHRASE_HINT] }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setBackupRetentionCount(count: Int) {
        dataStore.edit { it[BACKUP_RETENTION_COUNT] = count }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        dataStore.edit { it[LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun setLastBackupStatus(status: String) {
        dataStore.edit { it[LAST_BACKUP_STATUS] = status }
    }

    suspend fun setLastBackupErrorMessage(message: String?) {
        dataStore.edit {
            if (message != null) {
                it[LAST_BACKUP_ERROR_MESSAGE] = message
            } else {
                it.remove(LAST_BACKUP_ERROR_MESSAGE)
            }
        }
    }

    suspend fun setLastBackupMedicationCount(count: Int) {
        dataStore.edit { it[LAST_BACKUP_MEDICATION_COUNT] = count }
    }

    suspend fun setConsecutiveFailures(count: Int) {
        dataStore.edit { it[CONSECUTIVE_FAILURES] = count }
    }

    suspend fun setBackupDestinationUri(uri: String?) {
        dataStore.edit {
            if (uri != null) {
                it[BACKUP_DESTINATION_URI] = uri
            } else {
                it.remove(BACKUP_DESTINATION_URI)
            }
        }
    }

    suspend fun setPassphraseHint(hint: String?) {
        dataStore.edit {
            if (hint != null) {
                it[PASSPHRASE_HINT] = hint
            } else {
                it.remove(PASSPHRASE_HINT)
            }
        }
    }

    suspend fun recordSuccess(timestamp: Long, medicationCount: Int) {
        dataStore.edit {
            it[LAST_BACKUP_TIMESTAMP] = timestamp
            it[LAST_BACKUP_STATUS] = "SUCCESS"
            it[LAST_BACKUP_MEDICATION_COUNT] = medicationCount
            it[CONSECUTIVE_FAILURES] = 0
            it.remove(LAST_BACKUP_ERROR_MESSAGE)
        }
    }

    suspend fun recordFailure(status: String, errorMessage: String?) {
        dataStore.edit {
            it[LAST_BACKUP_STATUS] = status
            if (errorMessage != null) it[LAST_BACKUP_ERROR_MESSAGE] = errorMessage
            it[CONSECUTIVE_FAILURES] = (it[CONSECUTIVE_FAILURES] ?: 0) + 1
        }
    }

    suspend fun setOnboardingCompletedTimestamp(timestamp: Long) {
        dataStore.edit { it[ONBOARDING_COMPLETED_TIMESTAMP] = timestamp }
    }

    suspend fun setBackupNudgeDismissed(dismissed: Boolean) {
        dataStore.edit { it[BACKUP_NUDGE_DISMISSED] = dismissed }
    }

    // TODO(BETA_ROLLBACK): closed-beta survey nudge
    suspend fun setClosedBetaSurveyNudgeDismissed(dismissed: Boolean) {
        dataStore.edit { it[CLOSED_BETA_SURVEY_NUDGE_DISMISSED] = dismissed }
    }
}
