// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

class BadPassphraseException : Exception("Incorrect passphrase")

expect object BackupEncryption {
    fun encrypt(json: String, passphrase: String): ByteArray
    fun decrypt(data: ByteArray, passphrase: String): String
    fun isEncrypted(data: ByteArray): Boolean
}
