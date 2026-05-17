# Database Encryption

The Room database is encrypted at rest with SQLCipher (AES-256). The key
is generated automatically, stored in platform-native secure storage, and
never exposed to the user. Background processes such as notification
handlers and alarm reconciliation can still access the database while
the device is locked, as long as it has been unlocked at least once
since boot.

## Key management

Both platforms store a 32-byte key in hardware-backed secure storage
that is available after first unlock.

On Android the key lives in `EncryptedSharedPreferences` file
`hellomeds_db_key`, backed by an AES-256-GCM MasterKey in the Keystore.
`DatabaseKeyManager` generates 32 random bytes via `SecureRandom` on
first launch. On some OEM devices, changing the lock screen invalidates
the Keystore MasterKey and `EncryptedSharedPreferences` becomes
unreadable. `DatabaseKeyManager` catches `KeyStoreException` and
`GeneralSecurityException`, deletes the corrupted file, and generates a
new key. The old database can no longer be decrypted, so
`fallbackToDestructiveMigration` recreates the schema. This causes data
loss but avoids a permanent crash loop, and automatic backups exist to
mitigate it (see `docs/backups.md`).

On iOS the key lives in the Keychain under service
`me.juliana.hellomeds`, account `db_encryption_key`, with accessibility
`kSecAttrAccessibleAfterFirstUnlock`. Kotlin does not touch the Keychain
directly. A Swift bridge is registered during app init via
`registerKeychainBridge()` in `core/data`, and it must be installed
before Koin initializes the database. First-run key generation uses
`arc4random` from Kotlin/Native, after which the key is written through
the bridge and read back on later launches.

## Opening the database

Room KMP's `openHelperFactory()` and `setDriver()` are mutually
exclusive and have different platform availability, so each platform
takes a different path.

Android uses `openHelperFactory(SupportOpenHelperFactory(key))` from
`net.zetetic:sqlcipher-android`, which replaces the standard
`BundledSQLiteDriver`. `System.loadLibrary("sqlcipher")` must run in
`HelloMedsApplication.onCreate()` before any database code. Moving it
later breaks database opens.

iOS uses `IOSEncryptedSQLiteDriver`, a decorator around
`NativeSQLiteDriver` from `androidx.sqlite:sqlite-framework`. On every
`open()` the driver calls `NativeSQLiteDriver.open(fileName)`, then runs
`PRAGMA key = "x'<hex>'"` as the first statement on the connection
before any other interaction, sets `PRAGMA journal_mode = WAL` for
Room's multi-connection handling, runs `PRAGMA cipher_version` (which
throws if null or blank, meaning SQLCipher is not linked), and finally
verifies access with `SELECT count(*) FROM sqlite_master`.

`NativeSQLiteDriver` resolves whatever `sqlite3` symbols are linked.
When SQLCipher is linked via SPM it replaces the system sqlite3
library. Standard sqlite3 ignores unknown pragmas silently, which is
why the `PRAGMA cipher_version` check matters. Without it, an
unencrypted database would be created with no obvious sign of failure.
System `libsqlite3` must not be linked alongside SQLCipher. If both are
present, the linker may bind to the system library and encryption
silently does not work.

## iOS file protection

The database directory uses
`NSFileProtectionCompleteUntilFirstUserAuthentication`. This lets
BGTaskScheduler handlers read and write the database on a locked device
once it has been unlocked at least once since boot.

## The post-boot window

The iOS Keychain (`kSecAttrAccessibleAfterFirstUnlock`) and the Android
Keystore are both unavailable between boot and first unlock, so during
that window the encryption key cannot be read and the database cannot
open.

On Android, `BootReceiver` checks `UserManager.isUserUnlocked()` first
and skips immediate reconciliation if the user has not unlocked yet,
relying on the periodic `NotificationSchedulerWorker` to retry. All
workers wrap database access in try/catch and return `Result.retry()`
on failure. On iOS, BGTaskScheduler handlers guard against nil Keychain
reads, and if the key is unavailable the task finishes without touching
the database and waits for its next scheduled run. Once the device has
been unlocked, the key stays available even when the device locks
again.

## Xcode setup

SQLCipher is not managed by Gradle, so it must be added manually:

1. File, Add Package Dependencies
2. Add the SQLCipher SPM package (for example
   `https://github.com/nicklama/swift-sqlcipher`)
3. Link the `SQLCipher` product to the HelloMeds target
4. Confirm system `libsqlite3` is not also linked

The `PRAGMA cipher_version` check in `IOSEncryptedSQLiteDriver` catches
a wrong setup at runtime. An `IllegalStateException` with "SQLCipher is
NOT active" means the linking is wrong.

## Testing

Run `PRAGMA cipher_version` on the connection and expect a version
string, since empty means SQLCipher is not linked. Opening the
`.sqlite` file in a standard SQLite browser should fail, and readable
tables mean the file is not encrypted. Exercise concurrent opens
(especially on iOS, where Room manages a connection pool) and confirm
every connection is keyed. Close and reopen the app to confirm the key
persists. On Android, reboot and verify `BootReceiver` does not crash
before the user unlocks.
