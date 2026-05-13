// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import Security
import shared

private let service = "me.juliana.hellomeds"
private let account = "db_encryption_key"
private let keyLength = 32

func setupKeychainBridge() {
    KeychainBridgeSetupKt.setupKeychainBridge(
        read: { readDatabaseKey() },
        write: { keyData in KotlinBoolean(value: storeDatabaseKey(keyData)) }
    )
}

private func readDatabaseKey() -> KotlinByteArray? {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account,
        kSecReturnData as String: true,
        kSecMatchLimit as String: kSecMatchLimitOne
    ]

    var result: AnyObject?
    let status = SecItemCopyMatching(query as CFDictionary, &result)

    guard status == errSecSuccess, let data = result as? Data else {
        return nil
    }

    let byteArray = KotlinByteArray(size: Int32(data.count))
    for (index, byte) in data.enumerated() {
        byteArray.set(index: Int32(index), value: Int8(bitPattern: byte))
    }
    return byteArray
}

private func storeDatabaseKey(_ keyBytes: KotlinByteArray) -> Bool {
    var data = Data(count: Int(keyBytes.size))
    for i in 0..<keyBytes.size {
        data[Int(i)] = UInt8(bitPattern: keyBytes.get(index: i))
    }

    // Delete any existing key first
    let deleteQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account
    ]
    SecItemDelete(deleteQuery as CFDictionary)

    // Add new key with AfterFirstUnlock accessibility
    let addQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account,
        kSecValueData as String: data,
        kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
    ]

    let status = SecItemAdd(addQuery as CFDictionary, nil)
    return status == errSecSuccess
}
