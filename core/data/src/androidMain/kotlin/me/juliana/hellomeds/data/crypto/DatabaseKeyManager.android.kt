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
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom

actual class DatabaseKeyManager(private val context: Context) : KoinComponent, KeyLossCleanup {

    // Lazy injection: ReliabilityPreferences resolves only when recovery actually fires,
    // never during DatabaseKeyManager construction. AppDatabase depends on this manager,
    // and ReliabilityPreferences is registered in the same platform module — eager
    // resolution here would risk circular initialization on cold start.
    private val reliabilityPrefs: ReliabilityPreferences by inject()

    private val quarantine = KeyLossQuarantine(context, DATABASE_NAME, PREFS_FILE)

    actual fun getOrCreateKey(): ByteArray? {
        var attempt = 1
        while (true) {
            try {
                return maintainQuarantine(readOrCreateKey())
            } catch (e: GeneralSecurityException) {
                // KeyStoreException is a GeneralSecurityException. Both can be transient, for
                // example while the key store is still starting up after a reboot.
                if (attempt < MAX_KEY_ATTEMPTS) {
                    AppLogger.w(TAG, "Key read failed (attempt $attempt), retrying: ${e.message}")
                    Thread.sleep(RETRY_DELAYS_MS[attempt - 1])
                    attempt++
                } else {
                    AppLogger.w(TAG, "Key read failed after $attempt attempts, recovering: ${e.message}")
                    return recoverFromKeyFailure()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to access database encryption key", e)
                return null
            }
        }
    }

    private fun readOrCreateKey(): ByteArray {
        val prefs = getEncryptedPrefs()
        val existing = prefs.getString(KEY_PREF, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val key = ByteArray(KEY_SIZE)
        SecureRandom().nextBytes(key)
        prefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    /**
     * Retries a set-aside database before the user is told anything. Moves the old data back when
     * its key works again and the new database is still empty, and deletes it once it is too old.
     */
    private fun maintainQuarantine(liveKey: ByteArray): ByteArray {
        if (!quarantine.exists()) return liveKey
        return try {
            val now = System.currentTimeMillis()
            val oldData = quarantine.readOldData(::readKeyFrom)
            val action = QuarantinePolicy.decide(
                now = now,
                quarantinedAt = quarantine.quarantinedAt() ?: now,
                quarantinedMedications = oldData?.second,
                liveMedications = quarantine.liveMedicationCount(liveKey),
            )
            when (action) {
                QuarantinePolicy.Action.KEEP -> liveKey
                QuarantinePolicy.Action.DELETE -> {
                    AppLogger.i(TAG, "Deleting the set-aside database")
                    quarantine.deleteQuarantine()
                    liveKey
                }
                QuarantinePolicy.Action.SWAP_BACK -> {
                    val oldKey = oldData!!.first
                    AppLogger.i(TAG, "Old key works again, moving the set-aside database back")
                    quarantine.swapBack()
                    getEncryptedPrefs().edit()
                        .putString(KEY_PREF, Base64.encodeToString(oldKey, Base64.NO_WRAP))
                        .commit()
                    clearRecoveredFlag()
                    oldKey
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Set-aside database check failed", e)
            liveKey
        }
    }

    /** Reads the key from a set-aside key file by opening a copy of it under another name. */
    private fun readKeyFrom(lostFile: File): ByteArray? {
        if (!lostFile.exists()) return null
        val probe = File(lostFile.parentFile, "$PROBE_PREFS_FILE.xml")
        return try {
            lostFile.copyTo(probe, overwrite = true)
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PROBE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            prefs.getString(KEY_PREF, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        } catch (e: Exception) {
            null
        } finally {
            context.deleteSharedPreferences(PROBE_PREFS_FILE)
            probe.delete()
        }
    }

    private fun clearRecoveredFlag() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reliabilityPrefs.setDatabaseRecovered(false)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to clear databaseRecovered flag", e)
            }
        }
    }

    override fun deleteQuarantine() = quarantine.deleteQuarantine()

    override fun deleteExpiredQuarantine() {
        val at = quarantine.quarantinedAt() ?: return
        if (System.currentTimeMillis() - at >= QuarantinePolicy.RETENTION_MS) quarantine.deleteQuarantine()
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
     * Recovery from key invalidation (for example a lock screen change on some OEM devices).
     * Sets the unreadable database and key file aside rather than deleting them, generates a
     * fresh key, and signals the UI through ReliabilityPreferences.databaseRecovered so the
     * user can restore from a backup. A later launch retries the old key, see [maintainQuarantine].
     *
     * The old database cannot be opened with the new key, so it must move out of the way or
     * SQLCipher would throw "file is not a database" and the app would crash-loop at startup.
     */
    private fun recoverFromKeyFailure(): ByteArray? {
        return try {
            val hadData = quarantine.databaseExists()
            if (hadData) {
                AppLogger.w(TAG, "Setting the undecryptable database aside and generating a fresh key")
                quarantine.quarantine(System.currentTimeMillis())
            } else {
                context.deleteSharedPreferences(PREFS_FILE)
            }
            val key = ByteArray(KEY_SIZE)
            SecureRandom().nextBytes(key)
            val prefs = getEncryptedPrefs()
            prefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()

            if (hadData) {
                // Fire-and-forget: a DataStore write should not block returning the new key
                // (Room is waiting on this method).
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        reliabilityPrefs.setDatabaseRecovered(true)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to record databaseRecovered flag", e)
                    }
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
        private const val PROBE_PREFS_FILE = "hellomeds_db_key_probe"
        private const val MAX_KEY_ATTEMPTS = 3
        private val RETRY_DELAYS_MS = longArrayOf(100, 300)

        // Must match the name passed to getDatabasePath in DatabaseBuilder.android.kt
        private const val DATABASE_NAME = "hellomeds_database"
    }
}
