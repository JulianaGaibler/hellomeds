# Database Encryption

HelloMeds encrypts its local Room database at rest using SQLCipher (AES-256). The encryption key is generated automatically, stored in platform-native secure storage, and never exposed to the user. This protects medication data if the device is lost or physically accessed.

The database remains accessible to background processes — notification handlers and alarm reconciliation — while the device is locked, provided it has been unlocked at least once since the last reboot.

## How the Key Is Managed

Each platform stores the 32-byte encryption key differently, but both follow the same principle: the key lives in hardware-backed secure storage and is available after first unlock.

### Android

The key is stored in `EncryptedSharedPreferences` (file: `hellomeds_db_key`), backed by an AES-256-GCM MasterKey in the Android Keystore. On first launch, `DatabaseKeyManager` generates 32 random bytes via `java.security.SecureRandom`, Base64-encodes them, and writes them to the encrypted preferences file.

On some OEM devices, changing the lock screen (switching from PIN to pattern, for example) can invalidate the Keystore MasterKey. When this happens, `EncryptedSharedPreferences` becomes unreadable. `DatabaseKeyManager` catches the resulting `KeyStoreException` or `GeneralSecurityException`, deletes the corrupted preferences file, and generates a fresh key. The old database cannot be decrypted with the new key, so Room's `fallbackToDestructiveMigration` recreates the schema. This causes data loss but avoids a permanent crash loop. Automatic backups exist to mitigate this scenario — see `docs/backups.md`.

### iOS

The key is stored in the iOS Keychain with service `me.juliana.hellomeds` and account `db_encryption_key`. Accessibility is set to `kSecAttrAccessibleAfterFirstUnlock`, which means the key is available after the device has been unlocked at least once since reboot, including when the device is subsequently locked.

The Kotlin code does not access the Keychain directly. Instead, a Swift bridge callback is registered during app initialization. `registerKeychainBridge()` in `core/data` accepts read and write functions, which are implemented in `KeychainBridge.swift` using the Security framework. The bridge must be registered before Koin initializes the database.

Key generation uses `arc4random` via Kotlin/Native on first run. The key is then stored through the bridge and retrieved on subsequent launches.

## How the Database Is Opened

The database configuration differs by platform because Room KMP's `openHelperFactory()` and `setDriver()` APIs are mutually exclusive and have different platform availability.

### Android

The Android build uses `openHelperFactory(SupportOpenHelperFactory(key))` from the `net.zetetic:sqlcipher-android` library. This replaces the standard `BundledSQLiteDriver` with SQLCipher's encrypted implementation.

`System.loadLibrary("sqlcipher")` is called in `HelloMedsApplication.onCreate()` before any database code runs. If you move this call to a lazier initialization point, the database will fail to open.

### iOS

The iOS build uses `IOSEncryptedSQLiteDriver`, a decorator around `NativeSQLiteDriver` from `androidx.sqlite:sqlite-framework`. On every `open()` call, the driver:

1. Delegates to `NativeSQLiteDriver.open(fileName)` to get a connection
2. Executes `PRAGMA key = "x'<hex>'"` as the first statement — this must happen before any other interaction with the connection
3. Sets `PRAGMA journal_mode = WAL` for Room's multi-connection handling
4. Runs `PRAGMA cipher_version` and checks the result — if it returns null or blank, SQLCipher is not linked and the driver throws immediately
5. Verifies data access with `SELECT count(*) FROM sqlite_master`

`NativeSQLiteDriver` uses whatever `sqlite3` symbols are linked at runtime. When SQLCipher is properly linked via SPM, its implementation replaces the system sqlite3 library. The `PRAGMA key` command activates encryption because SQLCipher adds it to the sqlite3 API. Standard sqlite3 ignores unknown pragmas silently, which is why the `PRAGMA cipher_version` check exists — without it, the app would create an unencrypted database and you would not know until someone tried to read the file.

System `libsqlite3` must not be linked alongside SQLCipher. If both are present, the linker may resolve symbols to the system library, and encryption will silently not work.

## iOS File Protection

The database directory has its file protection attribute set to `NSFileProtectionCompleteUntilFirstUserAuthentication`. This allows background processes (BGTaskScheduler handlers for alarm reconciliation and cleanup) to read and write the database while the device is locked, as long as it has been unlocked once since reboot. Without this attribute, iOS would block file access from background tasks on a locked device.

## Background Access and the Post-Reboot Window

Both the iOS Keychain (`kSecAttrAccessibleAfterFirstUnlock`) and the Android Keystore are unavailable in the brief window between device boot and the first user unlock. During this window, the encryption key cannot be retrieved and the database cannot be opened.

On Android, the `BootReceiver` checks `UserManager.isUserUnlocked()` before attempting database access. If the device has not been unlocked yet, it skips immediate reconciliation and defers to the periodic `NotificationSchedulerWorker`, which will retry later. All workers wrap database access in try/catch and return `Result.retry()` on failure.

On iOS, BGTaskScheduler handlers guard against nil Keychain reads. If the key is unavailable, the task completes without accessing the database and relies on the next scheduled run.

This window is typically short — it lasts from boot until the user enters their PIN, password, or biometric. Once the device is unlocked, the key remains available even when the device locks again.

## Xcode Setup

SQLCipher is not managed by Gradle. You need to add it to the Xcode project manually:

1. In Xcode, go to File > Add Package Dependencies
2. Add the SQLCipher SPM package (e.g., `https://github.com/nicklama/swift-sqlcipher`)
3. Link the `SQLCipher` product to the HelloMeds target
4. Verify that system `libsqlite3` is not linked — if both are present, the app may silently use unencrypted sqlite3

The `PRAGMA cipher_version` fail-fast check in `IOSEncryptedSQLiteDriver` catches incorrect linking at runtime. If you see an `IllegalStateException` with "SQLCipher is NOT active," the linking is wrong.

## Architecture Diagram

```
┌─────────────────────────────────────────────────┐
│                    commonMain                    │
│                                                  │
│  DataModule.kt                                   │
│    └─ configureEncryptedDriver(builder, km)      │
│                                                  │
│  expect DatabaseKeyManager                       │
│  expect configureEncryptedDriver()               │
└──────────────────┬──────────────┬────────────────┘
                   │              │
     ┌─────────────▼───────┐  ┌──▼───────────────────┐
     │     androidMain      │  │       iosMain         │
     │                      │  │                       │
     │  SupportOpen-        │  │  IOSEncrypted-        │
     │  HelperFactory       │  │  SQLiteDriver         │
     │  (sqlcipher-android) │  │  (NativeSQLiteDriver  │
     │                      │  │   + PRAGMA key)       │
     │  EncryptedShared-    │  │                       │
     │  Preferences         │  │  Keychain bridge      │
     │  (Android Keystore)  │  │  (Swift callback)     │
     └─────────────────────┘  └───────────────────────┘
```

## Files

| File | Purpose |
|------|---------|
| `core/data/src/commonMain/.../crypto/DatabaseKeyManager.kt` | expect class for key retrieval |
| `core/data/src/commonMain/.../crypto/EncryptedDriverConfig.kt` | expect function for driver setup |
| `core/data/src/androidMain/.../crypto/DatabaseKeyManager.android.kt` | EncryptedSharedPreferences implementation |
| `core/data/src/androidMain/.../crypto/EncryptedDriverConfig.android.kt` | SupportOpenHelperFactory configuration |
| `core/data/src/iosMain/.../crypto/DatabaseKeyManager.ios.kt` | Keychain bridge callback registration |
| `core/data/src/iosMain/.../crypto/EncryptedDriverConfig.ios.kt` | NativeSQLiteDriver decorator setup |
| `core/data/src/iosMain/.../crypto/IOSEncryptedSQLiteDriver.kt` | Driver that keys connections via PRAGMA |
| `shared/src/iosMain/.../util/KeychainBridgeSetup.kt` | Re-export for Swift visibility |
| `iosApp/HelloMeds/Crypto/KeychainBridge.swift` | Keychain read/write implementation |

## Testing

To verify encryption is working:

- Run `PRAGMA cipher_version` through the database connection. It should return a version string. If it returns nothing, SQLCipher is not linked.
- Open the `.sqlite` file with a standard SQLite browser. It should fail to read. If you can see tables, the database is not encrypted.
- Test multiple concurrent database opens, particularly on iOS where Room manages a connection pool. Every connection must be keyed.
- Restart the app (open, close, reopen) and verify the database opens without errors. Key persistence across restarts confirms the key is being stored and retrieved correctly.
- On Android, reboot the device and verify the `BootReceiver` does not crash before the user unlocks the device.
