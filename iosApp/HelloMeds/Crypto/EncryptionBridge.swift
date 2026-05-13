// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import CryptoKit
import CommonCrypto
import shared

/// Registers the CryptoKit-based encryption bridge with the Kotlin framework.
/// Called once during app initialization.
func setupEncryptionBridge() {
    EncryptionBridgeSetupKt.setupEncryptionBridge(
        encrypt: { json, passphrase in
            return encryptBackup(json: json, passphrase: passphrase)
        },
        decrypt: { data, passphrase in
            return (try? decryptBackup(data: data, passphrase: passphrase)) ?? ""
        }
    )
}

// MARK: - Constants (must match Android)

private let magic = Data("HMEDS01\0".utf8)
private let saltLength = 16
private let ivLength = 12
private let keyLength = 32  // 256 bits
private let pbkdf2Iterations: UInt32 = 210_000
private let headerSize = 8 + saltLength + ivLength  // 36 bytes

// MARK: - Encryption

private func encryptBackup(json: String, passphrase: String) -> KotlinByteArray {
    let salt = randomBytes(count: saltLength)
    let iv = randomBytes(count: ivLength)
    let key = deriveKey(passphrase: passphrase, salt: salt)

    let plaintext = Data(json.utf8)
    let symmetricKey = SymmetricKey(data: key)
    let nonce = try! AES.GCM.Nonce(data: iv)

    let sealed = try! AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce)

    // Format: magic + salt + iv + ciphertext + tag
    var result = Data()
    result.append(magic)
    result.append(salt)
    result.append(iv)
    result.append(sealed.ciphertext)
    result.append(sealed.tag)  // GCM tag (16 bytes)

    return result.toKotlinByteArray()
}

// MARK: - Decryption

private func decryptBackup(data: KotlinByteArray, passphrase: String) throws -> String {
    let bytes = data.toData()

    guard bytes.count > headerSize + 16 else {
        throw NSError(domain: "BackupEncryption", code: 1, userInfo: [NSLocalizedDescriptionKey: "File too short"])
    }

    let salt = bytes.subdata(in: magic.count..<(magic.count + saltLength))
    let iv = bytes.subdata(in: (magic.count + saltLength)..<headerSize)
    let sealed = bytes.subdata(in: headerSize..<bytes.count)

    // Split ciphertext and tag (Android appends tag to ciphertext)
    let ciphertext = sealed.subdata(in: 0..<(sealed.count - 16))
    let tag = sealed.subdata(in: (sealed.count - 16)..<sealed.count)

    let key = deriveKey(passphrase: passphrase, salt: salt)
    let symmetricKey = SymmetricKey(data: key)
    let nonce = try AES.GCM.Nonce(data: iv)

    let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag)
    let plaintext = try AES.GCM.open(sealedBox, using: symmetricKey)

    guard let result = String(data: plaintext, encoding: .utf8) else {
        throw NSError(domain: "BackupEncryption", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid UTF-8"])
    }
    return result
}

// MARK: - Key Derivation (PBKDF2-SHA256)

private func deriveKey(passphrase: String, salt: Data) -> Data {
    let passphraseData = Data(passphrase.utf8)
    var derivedKey = Data(count: keyLength)

    let result = derivedKey.withUnsafeMutableBytes { derivedKeyPtr in
        salt.withUnsafeBytes { saltPtr in
            passphraseData.withUnsafeBytes { passphrasePtr in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passphrasePtr.baseAddress?.assumingMemoryBound(to: Int8.self),
                    passphraseData.count,
                    saltPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    pbkdf2Iterations,
                    derivedKeyPtr.baseAddress?.assumingMemoryBound(to: UInt8.self),
                    keyLength
                )
            }
        }
    }

    guard result == kCCSuccess else {
        fatalError("PBKDF2 key derivation failed: \(result)")
    }

    return derivedKey
}

// MARK: - Helpers

private func randomBytes(count: Int) -> Data {
    var bytes = Data(count: count)
    let result = bytes.withUnsafeMutableBytes { ptr in
        SecRandomCopyBytes(kSecRandomDefault, count, ptr.baseAddress!)
    }
    guard result == errSecSuccess else {
        fatalError("SecRandomCopyBytes failed")
    }
    return bytes
}

// MARK: - Data ↔ KotlinByteArray conversions

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(self.count))
        self.withUnsafeBytes { ptr in
            let bytes = ptr.bindMemory(to: Int8.self)
            for i in 0..<self.count {
                array.set(index: Int32(i), value: bytes[i])
            }
        }
        return array
    }
}

extension KotlinByteArray {
    func toData() -> Data {
        var data = Data(count: Int(self.size))
        for i in 0..<Int(self.size) {
            data[i] = UInt8(bitPattern: self.get(index: Int32(i)))
        }
        return data
    }
}
