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
import me.juliana.hellomeds.data.backup.BackupDestinationMode
import me.juliana.hellomeds.data.backup.BackupNudgePolicy

class AutoBackupPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        private val BACKUP_RETENTION_COUNT = intPreferencesKey("backup_retention_count")
        private val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        private val LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        private val LAST_BACKUP_ERROR_MESSAGE = stringPreferencesKey("last_backup_error_message")
        private val LAST_BACKUP_MEDICATION_COUNT = intPreferencesKey("last_backup_medication_count")
        private val CONSECUTIVE_FAILURES = intPreferencesKey("consecutive_failures")
        private val BACKUP_DESTINATION_MODE = stringPreferencesKey("backup_destination_mode")
        private val BACKUP_DESTINATION_URI = stringPreferencesKey("backup_destination_uri")
        private val BACKUP_DESTINATION_NAME = stringPreferencesKey("backup_destination_name")
        private val PASSPHRASE_HINT = stringPreferencesKey("passphrase_hint")
        private val ONBOARDING_COMPLETED_TIMESTAMP =
            longPreferencesKey("onboarding_completed_timestamp")
        private val BACKUP_NUDGE_DISMISSED = booleanPreferencesKey("backup_nudge_dismissed")
        private val BACKUP_NUDGE_NEXT_AT = longPreferencesKey("backup_nudge_next_at")
        private val BACKUP_NUDGE_OPT_OUT = booleanPreferencesKey("backup_nudge_opt_out")
        private val BACKUP_NUDGE_AFTER_IMPORT = booleanPreferencesKey("backup_nudge_after_import")

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
    val backupDestinationMode: Flow<BackupDestinationMode> = dataStore.data.map {
        when (it[BACKUP_DESTINATION_MODE]) {
            BackupDestinationMode.CUSTOM_FOLDER.name -> BackupDestinationMode.CUSTOM_FOLDER
            else -> BackupDestinationMode.ICLOUD
        }
    }
    val backupDestinationUri: Flow<String?> = dataStore.data.map { it[BACKUP_DESTINATION_URI] }
    val backupDestinationName: Flow<String?> = dataStore.data.map { it[BACKUP_DESTINATION_NAME] }
    val onboardingCompletedTimestamp: Flow<Long> =
        dataStore.data.map { it[ONBOARDING_COMPLETED_TIMESTAMP] ?: 0L }
    val backupNudgeDismissed: Flow<Boolean> =
        dataStore.data.map { it[BACKUP_NUDGE_DISMISSED] ?: false }

    val backupNudgeNextAt: Flow<Long> = dataStore.data.map { it[BACKUP_NUDGE_NEXT_AT] ?: 0L }
    val backupNudgeOptOut: Flow<Boolean> = dataStore.data.map { it[BACKUP_NUDGE_OPT_OUT] ?: false }
    val backupNudgeAfterImport: Flow<Boolean> = dataStore.data.map { it[BACKUP_NUDGE_AFTER_IMPORT] ?: false }

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

    suspend fun setBackupDestinationMode(mode: BackupDestinationMode) {
        dataStore.edit { it[BACKUP_DESTINATION_MODE] = mode.name }
    }

    /** Clearing the URI also clears the cached display name; pair with [setBackupDestination] to set both. */
    suspend fun setBackupDestinationUri(uri: String?) {
        dataStore.edit {
            if (uri != null) {
                it[BACKUP_DESTINATION_URI] = uri
            } else {
                it.remove(BACKUP_DESTINATION_URI)
                it.remove(BACKUP_DESTINATION_NAME)
            }
        }
    }

    suspend fun setBackupDestination(uri: String, displayName: String?) {
        dataStore.edit {
            it[BACKUP_DESTINATION_URI] = uri
            if (displayName != null) {
                it[BACKUP_DESTINATION_NAME] = displayName
            } else {
                it.remove(BACKUP_DESTINATION_NAME)
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

    /** Carries the old one-time dismissal over to the repeating schedule. */
    suspend fun migrateLegacyBackupNudge(now: Long) {
        dataStore.edit {
            if (it[BACKUP_NUDGE_DISMISSED] == true && (it[BACKUP_NUDGE_NEXT_AT] ?: 0L) == 0L) {
                it[BACKUP_NUDGE_NEXT_AT] = BackupNudgePolicy.afterSnooze(now)
            }
            it.remove(BACKUP_NUDGE_DISMISSED)
        }
    }

    suspend fun scheduleNudgeAfterMedicationAdded(now: Long) {
        dataStore.edit {
            it[BACKUP_NUDGE_NEXT_AT] = BackupNudgePolicy.afterMedicationAdded(
                now,
                it[BACKUP_NUDGE_NEXT_AT] ?: 0L,
                it[ONBOARDING_COMPLETED_TIMESTAMP] ?: 0L,
            )
        }
    }

    suspend fun scheduleNudgeAfterImport(now: Long) {
        dataStore.edit {
            it[BACKUP_NUDGE_NEXT_AT] = now
            it[BACKUP_NUDGE_AFTER_IMPORT] = true
        }
    }

    suspend fun snoozeBackupNudge(now: Long) {
        dataStore.edit {
            it[BACKUP_NUDGE_NEXT_AT] = BackupNudgePolicy.afterSnooze(now)
            it.remove(BACKUP_NUDGE_AFTER_IMPORT)
        }
    }

    suspend fun optOutOfBackupNudge() {
        dataStore.edit {
            it[BACKUP_NUDGE_OPT_OUT] = true
            it.remove(BACKUP_NUDGE_AFTER_IMPORT)
        }
    }

    // TODO(BETA_ROLLBACK): closed-beta survey nudge
    suspend fun setClosedBetaSurveyNudgeDismissed(dismissed: Boolean) {
        dataStore.edit { it[CLOSED_BETA_SURVEY_NUDGE_DISMISSED] = dismissed }
    }
}
