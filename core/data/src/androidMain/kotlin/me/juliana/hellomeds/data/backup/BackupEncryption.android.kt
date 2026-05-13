// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.data.backup

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object BackupEncryption {

    private val MAGIC = "HMEDS01\u0000".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val KEY_LENGTH = 256
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 210_000
    private const val HEADER_SIZE = 8 + SALT_LENGTH + IV_LENGTH

    actual fun encrypt(json: String, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        return ByteArrayOutputStream().use { out ->
            out.write(MAGIC)
            out.write(salt)
            out.write(iv)
            out.write(ciphertext)
            out.toByteArray()
        }
    }

    actual fun decrypt(data: ByteArray, passphrase: String): String {
        if (!isEncrypted(data)) throw IllegalArgumentException("Not an encrypted HelloMeds backup")
        if (data.size <= HEADER_SIZE) throw IllegalArgumentException("Encrypted file too short")

        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LENGTH)
        val iv = data.copyOfRange(MAGIC.size + SALT_LENGTH, HEADER_SIZE)
        val ciphertext = data.copyOfRange(HEADER_SIZE, data.size)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw BadPassphraseException()
        }
        return String(plaintext, Charsets.UTF_8)
    }

    actual fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC.size) return false
        return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secret = factory.generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
