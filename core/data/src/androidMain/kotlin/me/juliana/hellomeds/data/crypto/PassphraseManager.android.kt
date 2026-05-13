// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import android.content.Context
import android.content.SharedPreferences
import me.juliana.hellomeds.data.util.AppLogger
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.KeyStoreException

actual class PassphraseManager(private val context: Context) {

    actual fun getPassphrase(): String? {
        return try {
            getEncryptedPrefs().getString(PASSPHRASE_PREF, null)
        } catch (e: KeyStoreException) {
            AppLogger.e(TAG, "KeyStore unavailable for passphrase retrieval", e)
            null
        } catch (e: GeneralSecurityException) {
            AppLogger.e(TAG, "Security exception accessing passphrase", e)
            recoverFromCorruption()
            null
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to access backup passphrase", e)
            null
        }
    }

    actual fun setPassphrase(passphrase: String): Boolean {
        return try {
            getEncryptedPrefs().edit().putString(PASSPHRASE_PREF, passphrase).apply()
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to store backup passphrase", e)
            false
        }
    }

    actual fun hasPassphrase(): Boolean {
        return try {
            getEncryptedPrefs().contains(PASSPHRASE_PREF)
        } catch (e: Exception) {
            false
        }
    }

    actual fun clearPassphrase() {
        try {
            getEncryptedPrefs().edit().remove(PASSPHRASE_PREF).apply()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to clear backup passphrase", e)
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
     * If EncryptedSharedPreferences is corrupted (Keystore invalidation),
     * delete the file. The passphrase is lost from storage but the user
     * knows it — they can re-enter it in settings to resume auto-backups.
     */
    private fun recoverFromCorruption() {
        try {
            AppLogger.w(TAG, "Deleting corrupted passphrase prefs. User must re-enter passphrase.")
            context.deleteSharedPreferences(PREFS_FILE)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Recovery failed", e)
        }
    }

    companion object {
        private const val TAG = "PassphraseManager"
        private const val PREFS_FILE = "hellomeds_autobackup_passphrase"
        private const val PASSPHRASE_PREF = "backup_passphrase"
    }
}
