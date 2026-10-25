// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.crypto

import android.content.Context
import me.juliana.hellomeds.data.util.AppLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Sets the database and key file aside when the encryption key is lost, instead of deleting them.
 * A later launch can still read the old key after a transient key store failure, and then the old
 * data is moved back. Anything older than [QuarantinePolicy.RETENTION_MS] is deleted.
 */
internal class KeyLossQuarantine(
    private val context: Context,
    private val databaseName: String,
    private val keyPrefsFile: String,
) {
    private val databaseFile = context.getDatabasePath(databaseName)
    private val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
    private val liveKeyPrefs = File(prefsDir, "$keyPrefsFile.xml")
    private val lostKeyPrefs = File(prefsDir, "$keyPrefsFile.xml.lost")
    private val lostAtMarker = File(databaseFile.parentFile, "$databaseName.lost.at")

    fun databaseExists(): Boolean = databaseFile.exists()

    fun exists(): Boolean = lostAtMarker.exists()

    /** Moves the live database and key file aside. Replaces any earlier quarantine. */
    fun quarantine(now: Long) {
        deleteQuarantine()
        databaseSuffixes.forEach { suffix ->
            val live = File(databaseFile.path + suffix)
            if (live.exists()) live.renameTo(File(live.path + LOST_SUFFIX))
        }
        if (liveKeyPrefs.exists()) liveKeyPrefs.renameTo(lostKeyPrefs)
        context.getSharedPreferences(keyPrefsFile, Context.MODE_PRIVATE).edit().clear().commit()
        lostAtMarker.writeText(now.toString())
    }

    fun deleteQuarantine() {
        databaseSuffixes.forEach { File(databaseFile.path + it + LOST_SUFFIX).delete() }
        lostKeyPrefs.delete()
        lostAtMarker.delete()
    }

    fun quarantinedAt(): Long? = runCatching { lostAtMarker.readText().trim().toLong() }.getOrNull()

    /** The old key and the number of medications in the old database, or null when either is unreadable. */
    fun readOldData(readKey: (File) -> ByteArray?): Pair<ByteArray, Int>? {
        val key = readKey(lostKeyPrefs) ?: return null
        val count = countMedications(File(databaseFile.path + LOST_SUFFIX), key) ?: return null
        return key to count
    }

    fun liveMedicationCount(key: ByteArray): Int? = countMedications(databaseFile, key)

    /** Replaces the live database with the quarantined one. The caller restores the old key. */
    fun swapBack() {
        databaseSuffixes.forEach { File(databaseFile.path + it).delete() }
        databaseSuffixes.forEach { suffix ->
            val lost = File(databaseFile.path + suffix + LOST_SUFFIX)
            if (lost.exists()) lost.renameTo(File(databaseFile.path + suffix))
        }
        lostKeyPrefs.delete()
        lostAtMarker.delete()
    }

    fun lostKeyPrefsPath(): File = lostKeyPrefs

    private fun countMedications(file: File, key: ByteArray): Int? {
        if (!file.exists()) return 0
        return try {
            SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READONLY, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM medications", emptyArray<String>()).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not read ${file.name}: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "KeyLossQuarantine"
        const val LOST_SUFFIX = ".lost"
        val databaseSuffixes = listOf("", "-wal", "-shm", "-journal")
    }
}
