# Backups

Backup files use the `.hmbackup` extension and the MIME type
`application/vnd.hellomeds.backup`. Manual and automatic backups share the
same encrypted payload, and automatic backups add an unencrypted metadata
header on top of it.

## File format

### Encrypted payload (`HMEDS01`)

```
Offset  Length  Content
0       8       Magic bytes: "HMEDS01\0"
8       16      Salt (random)
24      12      IV / nonce (random)
36      ...     Ciphertext + GCM auth tag (16 bytes)
```

A file is encrypted when its first 8 bytes match `HMEDS01\0`, otherwise it
is plain JSON. Encryption uses AES-256-GCM with a 32-byte key derived
through PBKDF2-HMAC-SHA256 over 210,000 iterations, with a 16-byte salt,
12-byte IV, and 16-byte tag. The output is byte-identical across
platforms, so a file written on Android decrypts on iOS and vice versa. A
wrong passphrase fails GCM auth and surfaces as `BadPassphraseException`.

### Metadata wrapper (automatic backups)

Automatic backups prepend one line of UTF-8 JSON followed by `0x0A` and
then the `HMEDS01` payload. This lets the import screen show details
before the user enters a passphrase.

The metadata fields are `exportedAt`, `appVersion`, `medicationCount`,
`passphraseHint?`, `deviceName?`, and `autoBackup` (always true). The hint
is unencrypted. Manual backups have no metadata prefix, and import detects
both layouts.

## Backup contents

A backup is a JSON object with two top-level arrays, `importanceLabels`
and `medications`. Each medication nests its schedules, history, stock
settings, and stock adjustments. See
[`hellomeds-backup.schema.json`](hellomeds-backup.schema.json) for the
full schema with field descriptions.

Importance labels are referenced by name rather than ID. If a label with
the same name but different settings already exists during import, the
imported one is created with `(imported)` appended to its name.

Manual exports let the user pick medications plus optional schedules,
stock, and history, with history defaulting off. Automatic backups always
include everything.

## JSON config

The serializer is configured with `ignoreUnknownKeys = true`,
`coerceInputValues = true`, `isLenient = true`, `encodeDefaults = true`,
and `prettyPrint = true`. Enums serialize as Kotlin name strings,
timestamps as ISO-8601, and dates as `YYYY-MM-DD`.

## Import

Import runs in three phases. First, parse detects encryption, prompts for
a passphrase if needed, and parses the JSON into `BackupData`. Second,
analyze finds duplicate medications (matched by `name` and `displayName`,
case-insensitive), flags label conflicts, and collects validation
warnings, which do not block import. Third, execute applies the user's
choice for each medication (import as new, replace existing, or skip).
Replacing deletes old schedules and stock adjustments before writing the
imported data, schedule IDs are remapped, and history follows the remap.
The alarm reconciler runs at the end.

## Automatic backups

Automatic backups run daily in the background and reuse the manual export
and encryption code.

On Android they run as a WorkManager periodic task near 4 AM, with a
battery-not-low constraint and a `UserManager.isUserUnlocked` check for
Direct Boot. On iOS they run as a `BGProcessingTask` registered as
`me.juliana.hellomeds.autobackup`, and iOS chooses the actual timing. If
a backup is overdue by more than 36 hours, the app triggers one on next
foreground.

For storage, Android lets the user pick a folder via SAF, which works for
local storage, Google Drive, OneDrive, and similar. The URI permission is
persisted, and if it is revoked the destination resets and the user is
asked to pick again. iOS writes to the iCloud ubiquity container at
`Documents/AutoBackups/`, falling back to local `Documents/AutoBackups/`
when iCloud is unavailable.

After each successful backup, retention keeps the N most recent files
(default 7) matching `hellomeds-auto-*`. Manual backups in the same
folder are never touched. Files are named
`hellomeds-auto-YYYY-MM-DD-HHMMSS.hmbackup` in local time, so
alphabetical sort matches chronological order, which retention relies
on.

## Passphrase storage

Automatic backups need a stored passphrase so they can run without user
interaction. On Android this lives in `EncryptedSharedPreferences` file
`hellomeds_autobackup_passphrase`, backed by an AES-256-GCM MasterKey in
the Keystore. On iOS it lives in the Keychain under service
`me.juliana.hellomeds`, account `autobackup_passphrase`, with
accessibility `kSecAttrAccessibleAfterFirstUnlock`. Both require the
device to have been unlocked once since reboot, and before first unlock
the backup just fails and retries on the next cycle.

The database key is system-generated and tied to the device, while the
backup passphrase is user-chosen and portable. That gap is why automatic
backups exist. The hint is stored unencrypted in `AutoBackupPreferences`
(DataStore) and copied into the metadata header of each automatic backup.

On some Android devices, changing the lock screen invalidates the
Keystore MasterKey, after which `EncryptedSharedPreferences` fails to
read. `PassphraseManager` deletes the corrupted file, the stored
passphrase is lost, automatic backups pause, and the user is asked to
re-enter the passphrase in settings. Existing backup files stay
decryptable with the original passphrase.

## Platform crypto

On Android the implementation uses `javax.crypto.Cipher` with
`AES/GCM/NoPadding`, `SecretKeyFactory` with `PBKDF2WithHmacSHA256`, and
`SecureRandom`. `AEADBadTagException` is mapped to
`BadPassphraseException`.

On iOS a Swift bridge backed by CryptoKit and CommonCrypto provides
`AES.GCM` for encryption and decryption, `CCKeyDerivationPBKDF` with
`kCCPRFHmacAlgSHA256` for key derivation, and `SecRandomCopyBytes` for
salt and IV. The Kotlin side calls registered Swift callbacks, and the
bridge must be installed during app init before any encryption call.
