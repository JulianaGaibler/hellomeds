// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import Foundation
import Security
import shared

private let service = "me.juliana.hellomeds"
private let account = "autobackup_passphrase"

func setupPassphraseBridge() {
    PassphraseBridgeSetupKt.setupPassphraseBridge(
        read: { readPassphrase() },
        write: { passphrase in KotlinBoolean(value: storePassphrase(passphrase)) },
        delete: { deletePassphrase() }
    )
}

private func readPassphrase() -> String? {
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

    return String(data: data, encoding: .utf8)
}

private func storePassphrase(_ passphrase: String) -> Bool {
    guard let data = passphrase.data(using: .utf8) else { return false }

    // Delete any existing entry first
    let deleteQuery: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account
    ]
    SecItemDelete(deleteQuery as CFDictionary)

    // Add with AfterFirstUnlock accessibility for background access
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

private func deletePassphrase() {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: account
    ]
    SecItemDelete(query as CFDictionary)
}
