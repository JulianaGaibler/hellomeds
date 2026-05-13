// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEncryptionTest {

    @Test
    fun `encrypt and decrypt round-trip preserves data`() {
        val original =
            """{"version": 1, "medications": [{"name": "Test", "importanceLabel": "Silent"}]}"""
        val passphrase = "test-passphrase-123"

        val encrypted = BackupEncryption.encrypt(original, passphrase)
        val decrypted = BackupEncryption.decrypt(encrypted, passphrase)

        assertEquals(original, decrypted)
    }

    @Test
    fun `isEncrypted returns true for encrypted data`() {
        val encrypted = BackupEncryption.encrypt("test", "pass")
        assertTrue(BackupEncryption.isEncrypted(encrypted))
    }

    @Test
    fun `isEncrypted returns false for plain JSON`() {
        val plainJson = """{"version": 1}""".toByteArray(Charsets.UTF_8)
        assertFalse(BackupEncryption.isEncrypted(plainJson))
    }

    @Test
    fun `isEncrypted returns false for short data`() {
        assertFalse(BackupEncryption.isEncrypted(ByteArray(3)))
        assertFalse(BackupEncryption.isEncrypted(ByteArray(0)))
    }

    @Test(expected = BadPassphraseException::class)
    fun `decrypt with wrong passphrase throws BadPassphraseException`() {
        val encrypted = BackupEncryption.encrypt("secret data", "correct-pass")
        BackupEncryption.decrypt(encrypted, "wrong-pass")
    }

    @Test
    fun `different encryptions of same data produce different ciphertext`() {
        val data = "same data"
        val passphrase = "same-pass"

        val encrypted1 = BackupEncryption.encrypt(data, passphrase)
        val encrypted2 = BackupEncryption.encrypt(data, passphrase)

        // Due to random salt + IV, ciphertext should differ
        assertFalse(encrypted1.contentEquals(encrypted2))

        // But both should decrypt to the same plaintext
        assertEquals(data, BackupEncryption.decrypt(encrypted1, passphrase))
        assertEquals(data, BackupEncryption.decrypt(encrypted2, passphrase))
    }

    @Test
    fun `encrypt handles unicode content`() {
        val original = """{"name": "Médicament avec accénts 日本語"}"""
        val passphrase = "unicode-pass-ÄÖÜ"

        val encrypted = BackupEncryption.encrypt(original, passphrase)
        val decrypted = BackupEncryption.decrypt(encrypted, passphrase)

        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypt handles large data`() {
        val original = "x".repeat(1_000_000) // 1MB of data
        val passphrase = "large-data-pass"

        val encrypted = BackupEncryption.encrypt(original, passphrase)
        val decrypted = BackupEncryption.decrypt(encrypted, passphrase)

        assertEquals(original, decrypted)
    }
}
