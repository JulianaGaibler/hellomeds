// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.ReliabilityPreferences
import me.juliana.hellomeds.data.util.AppLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.SecureRandom

actual class DatabaseKeyManager(private val context: Context) : KoinComponent {

    // Lazy injection: ReliabilityPreferences resolves only when recovery actually fires,
    // never during DatabaseKeyManager construction. AppDatabase depends on this manager,
    // and ReliabilityPreferences is registered in the same platform module — eager
    // resolution here would risk circular initialization on cold start.
    private val reliabilityPrefs: ReliabilityPreferences by inject()

    actual fun getOrCreateKey(): ByteArray? {
        return try {
            val prefs = getEncryptedPrefs()
            val existing = prefs.getString(KEY_PREF, null)
            if (existing != null) {
                Base64.decode(existing, Base64.NO_WRAP)
            } else {
                val key = ByteArray(KEY_SIZE)
                SecureRandom().nextBytes(key)
                prefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
                key
            }
        } catch (e: KeyStoreException) {
            AppLogger.w(TAG, "KeyStore unavailable, attempting recovery: ${e.message}")
            recoverFromKeyFailure()
        } catch (e: GeneralSecurityException) {
            AppLogger.w(TAG, "Security exception accessing key, attempting recovery: ${e.message}")
            recoverFromKeyFailure()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to access database encryption key", e)
            null
        }
    }

    actual fun hasKey(): Boolean {
        return try {
            getEncryptedPrefs().contains(KEY_PREF)
        } catch (e: Exception) {
            false
        }
    }

    private fun getEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Recovery from key invalidation (e.g., lock screen change on some OEM devices).
     * Deletes the undecryptable database AND the corrupted key prefs, generates a
     * fresh key, then signals the UI via ReliabilityPreferences.databaseRecovered
     * so the user can be told to restore from backup.
     *
     * Without the deleteDatabase call, the freshly-generated key could not open the
     * existing on-disk DB (encrypted with the lost key) — SQLCipher would throw
     * "file is not a database" on the next connection and the app would crash-loop
     * at startup. We accept the data loss because crash-looping is unrecoverable
     * for end users.
     */
    private fun recoverFromKeyFailure(): ByteArray? {
        return try {
            AppLogger.w(
                TAG,
                "Deleting corrupted key prefs AND undecryptable database, generating fresh key. " +
                    "Local DB data will be lost.",
            )
            context.deleteDatabase(DATABASE_NAME)
            context.deleteSharedPreferences(PREFS_FILE)
            val key = ByteArray(KEY_SIZE)
            SecureRandom().nextBytes(key)
            val prefs = getEncryptedPrefs()
            prefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()

            // Surface the recovery to the UI. Fire-and-forget: a DataStore write should
            // not block returning the new key (Room is waiting on this method).
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    reliabilityPrefs.setDatabaseRecovered(true)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to record databaseRecovered flag", e)
                }
            }

            key
        } catch (e: Exception) {
            AppLogger.e(TAG, "Recovery failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "DatabaseKeyManager"
        private const val PREFS_FILE = "hellomeds_db_key"
        private const val KEY_PREF = "db_encryption_key"
        private const val KEY_SIZE = 32

        // Must match the name passed to getDatabasePath in DatabaseBuilder.android.kt
        private const val DATABASE_NAME = "hellomeds_database"
    }
}
