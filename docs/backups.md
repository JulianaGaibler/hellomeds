# Backup System

HelloMeds stores all medication data locally in an encrypted database. Backups exist so users can recover that data if the device is lost, replaced, or the database encryption key becomes inaccessible. This document describes how backup files are structured, encrypted, and managed.

## File Format

All backup files use the `.hmbackup` extension and the MIME type `application/vnd.hellomeds.backup`.

There are two file layouts: the base encrypted format and the metadata-wrapped format used by automatic backups.

### Encrypted Format (HMEDS01)

Manual and automatic backups both use the same underlying encryption. The binary layout is:

```
Offset  Length  Content
──────  ──────  ─────────────────────────────
0       8       Magic bytes: "HMEDS01\0"
8       16      Salt (random, per-file)
24      12      IV / Nonce (random, per-file)
36      ...     Ciphertext + GCM authentication tag (16 bytes)
```

A file is identified as encrypted by checking whether the first 8 bytes match `HMEDS01\0`. Unencrypted backups are plain JSON and do not contain these magic bytes.

### Encryption Parameters

| Parameter          | Value                          |
|--------------------|--------------------------------|
| Algorithm          | AES-256-GCM                    |
| Key derivation     | PBKDF2-HMAC-SHA256             |
| Iterations         | 210,000                        |
| Key length         | 256 bits (32 bytes)            |
| Salt length        | 16 bytes (random per file)     |
| IV length          | 12 bytes (random per file)     |
| GCM tag length     | 128 bits (16 bytes)            |

Both platforms produce identical byte-level output. An encrypted file created on Android can be decrypted on iOS and vice versa.

The passphrase entered by the user is fed into PBKDF2 along with the random salt to derive the AES key. A new salt and IV are generated for every encryption operation, so encrypting the same data twice produces different output.

If decryption fails due to a wrong passphrase, the GCM authentication tag will not verify. This surfaces as a `BadPassphraseException` rather than returning corrupted data.

### Metadata Wrapper (Automatic Backups)

Automatic backups prepend an unencrypted JSON metadata line before the encrypted payload. This allows the import screen to display backup details — date, medication count, and passphrase hint — before the user enters their passphrase.

```
┌───────────────────────────────────────────────────────────────────┐
│ UTF-8 JSON metadata (one line)                                    │
│ {"exportedAt":"...","medicationCount":12,"passphraseHint":"..."}  │
├───────────────────────────────────────────────────────────────────┤
│ Newline byte (0x0A)                                               │
├───────────────────────────────────────────────────────────────────┤
│ HMEDS01 encrypted payload (same as above)                         │
└───────────────────────────────────────────────────────────────────┘
```

The metadata JSON contains:

| Field             | Type     | Description                                    |
|-------------------|----------|------------------------------------------------|
| `exportedAt`      | String   | ISO-8601 timestamp of when the backup was created |
| `appVersion`      | String   | App version that created the backup            |
| `medicationCount` | Int      | Number of medications in the backup            |
| `passphraseHint`  | String?  | User-provided hint to help remember the passphrase |
| `deviceName`      | String?  | Device model (reserved for future use)         |
| `autoBackup`      | Boolean  | Always `true` for automatic backups            |

The passphrase hint is stored unencrypted deliberately. Its purpose is to help the user remember their passphrase during a recovery scenario — if it were encrypted, the user would need the passphrase to read the hint, defeating the purpose.

Manual backups do not include a metadata prefix. The import logic detects both formats: if the file starts with `HMEDS01\0`, it is a plain encrypted file. If it starts with a JSON object followed by a newline and then `HMEDS01\0`, the metadata is extracted and the encrypted payload is everything after the newline.

## Backup Contents

A backup contains a JSON object with two top-level arrays: importance labels and medications. Each medication includes its schedules, medication history, stock settings, and stock adjustments as nested arrays.

The full JSON structure is defined in [`hellomeds-backup.schema.json`](hellomeds-backup.schema.json). The schema includes descriptions for every field and can be used for validation or editor autocompletion.

### Data Structure

```
BackupData
├── version: 1
├── appVersion: "1.0.0"
├── exportedAt: "2026-03-24T14:30:00Z"
├── importanceLabels[]
│   ├── name, shouldRemind, isCritical
│   ├── hasFollowUps, followUpCount, followUpIntervalMinutes
│   └── criticalAfterFollowUp
└── medications[]
    ├── name, displayName, type, shape, notes
    ├── strengthValue, strengthUnit
    ├── importanceLabel (name reference, not ID)
    ├── visual properties (foregroundShape, backgroundShape, shapeColor)
    ├── stock: BackupStockSettings?
    │   └── trackingEnabled, precision, quantity, threshold, container
    ├── schedules[]
    │   └── dose, timeOfDay, frequencyType, frequencyValue, daysOfWeek, startDate, endDate
    ├── history[]
    │   └── scheduleId, scheduledTime, takenTime, status, actualDose, notes
    └── stockAdjustments[]
        └── quantityChange, timestamp, adjustmentType, notes
```

Importance labels are referenced by name, not by database ID. During import, labels are matched by exact field comparison. If a label with the same name but different settings already exists, the imported label is created with `(imported)` appended to its name.

### What Manual Export Includes

Manual exports let the user choose which medications and which data types to include:

- **Medications**: User selects specific medications (including option to include archived ones)
- **Schedules**: Optional, enabled by default
- **Stock settings**: Optional, enabled by default
- **Medication history**: Optional, disabled by default (can increase file size)
- **Stock adjustments**: Included when history is included

### What Automatic Backups Include

Automatic backups always include everything: all medications (including archived), all schedules, all stock settings, all history, and all stock adjustments. There is no selection UI — the purpose is a complete safety net.

## JSON Serialization

The JSON serializer is configured with:

- `ignoreUnknownKeys = true` — forward compatibility with newer backup versions
- `coerceInputValues = true` — invalid enum values fall back to defaults
- `isLenient = true` — tolerant parsing
- `encodeDefaults = true` — all fields present even if default
- `prettyPrint = true` — human-readable output

Enum values are serialized as their Kotlin name strings (e.g., `"TABLET"`, `"INTERVAL"`, `"TAKEN"`). Timestamps are ISO-8601 strings. Dates are `YYYY-MM-DD` strings.

## Import Process

Importing a backup is a three-phase process: parse, analyze, execute.

### Phase 1: Parse

The raw bytes are checked for encryption. If the file is encrypted (with or without metadata prefix), the user is prompted for their passphrase. If a metadata wrapper is present, the passphrase hint is shown alongside the password field. After decryption, the JSON is parsed into a `BackupData` object.

### Phase 2: Analyze

Before any data is written, the import service analyzes the backup for conflicts:

- **Duplicate detection**: Medications are matched by `name` and `displayName` (case-insensitive). If a match is found, the user chooses how to handle it.
- **Label conflicts**: Labels are matched by all fields (name, remind, critical, follow-up settings). A name-only match with different settings is flagged.
- **Validation warnings**: Unknown enum values, missing required fields, and future backup versions generate warnings but do not block import.

### Phase 3: Execute

For each medication, the user picks one of three actions:

- **Import as new**: Creates a new medication regardless of duplicates
- **Replace**: Updates the existing matched medication (deletes old schedules and stock adjustments, replaces with imported data)
- **Skip**: Ignores this medication entirely

Schedule IDs are remapped during import. History entries reference schedules by ID, so the import maintains a mapping from old schedule IDs to newly assigned IDs.

After import completes, the alarm reconciler runs to sync notifications with the new data.

## Automatic Backups

Automatic backups run daily in the background. They are separate from manual backups but use the same export and encryption code.

### Scheduling

- **Android**: WorkManager periodic task, runs at approximately 4 AM daily. Requires battery not low. Checks `UserManager.isUserUnlocked` before accessing encrypted storage (Direct Boot guard).
- **iOS**: BGProcessingTask registered as `me.juliana.hellomeds.autobackup`. iOS controls the exact timing. If a backup is overdue by more than 36 hours when the app comes to the foreground, a backup is triggered immediately.

### Storage Destinations

- **Android**: The user selects a folder through the system file picker (SAF). This can be a local folder, Google Drive, OneDrive, or any other storage provider that integrates with Android's document picker. The URI permission is persisted across app restarts. If the permission is revoked (e.g., the user clears app data), the destination is reset and the user is prompted to select a new folder.
- **iOS**: Backups are written to the app's iCloud Drive ubiquity container (`Documents/AutoBackups/`). If iCloud is not available, they fall back to the local `Documents/AutoBackups/` directory.

### Retention

After each successful backup, the system keeps only the N most recent files (configurable, default 7). Older files are deleted. Only files matching the `hellomeds-auto-*` naming pattern are counted — manual backups in the same folder are never touched.

### File Naming

Automatic backup files follow the pattern:

```
hellomeds-auto-YYYY-MM-DD-HHMMSS.hmbackup
```

The timestamp uses the local timezone. The ISO-like format ensures alphabetical sorting matches chronological order, which the retention logic relies on.

## Passphrase Management

Automatic backups require a user-chosen passphrase. This passphrase is stored on the device so backups can run without user interaction.

### How the Passphrase Is Stored

- **Android**: `EncryptedSharedPreferences` backed by an AES-256-GCM MasterKey in the Android Keystore. The file is `hellomeds_autobackup_passphrase`.
- **iOS**: The iOS Keychain with accessibility set to `kSecAttrAccessibleAfterFirstUnlock`. The service is `me.juliana.hellomeds`, account is `autobackup_passphrase`.

Both platforms make the passphrase available to background processes after the device has been unlocked at least once since the last reboot. Before first unlock (e.g., a background task fires at 4 AM after a 2 AM reboot), the passphrase is inaccessible. In this case, the backup simply fails and retries on the next cycle. Settings are not cleared.

### Passphrase vs. Database Key

The auto-backup passphrase and the SQLCipher database encryption key are stored separately and serve different purposes:

|                  | Database Key                           | Backup Passphrase             |
|------------------|----------------------------------------|-------------------------------|
| **Generated by** | System (random 32 bytes)               | User (chosen string)          |
| **Recoverable**  | No — if lost, data is gone             | Yes — user knows it           |
| **Purpose**      | Encrypt database at rest               | Encrypt backup files          |
| **Storage**      | Keychain / EncryptedSharedPreferences  | Same, different file/account  |

This separation is the reason automatic backups exist: the database key is a single point of failure tied to the device. The passphrase is knowledge the user carries with them.

### Passphrase Hint

When setting a passphrase, the user can optionally provide a hint. The hint is:

- Stored unencrypted in `AutoBackupPreferences` (DataStore) on the device
- Written into the metadata header of each automatic backup file
- Displayed on the import screen before the user enters their passphrase

The hint is unencrypted because its only purpose is to help the user remember the passphrase during recovery — a scenario where they may not have access to the original device.

### Key Invalidation Recovery

On some Android devices, changing the lock screen can invalidate the Keystore MasterKey, making `EncryptedSharedPreferences` unreadable. When this happens, the `PassphraseManager` deletes the corrupted preferences file. The stored passphrase is lost, but the user knows it. Automatic backups pause until the user re-enters their passphrase in settings. Existing backup files remain decryptable with the original passphrase.

## Platform Encryption Implementations

### Android

Uses the Java Cryptography Architecture:

- `javax.crypto.Cipher` with `AES/GCM/NoPadding`
- `javax.crypto.SecretKeyFactory` with `PBKDF2WithHmacSHA256`
- `java.security.SecureRandom` for salt and IV generation
- `AEADBadTagException` maps to `BadPassphraseException`

### iOS

Uses Apple CryptoKit via a Swift bridge:

- `AES.GCM` for encryption/decryption
- `PBKDF2` via CommonCrypto (`CCKeyDerivationPBKDF` with `kCCPRFHmacAlgSHA256`)
- `SecRandomCopyBytes` for random byte generation

The Kotlin code delegates to registered Swift callbacks. The callbacks must be registered during app initialization before the encryption system is used.

## Recovery Path

When a user loses their device or the database encryption key is invalidated:

1. Install HelloMeds on the new device
2. Open Settings, then Import Data
3. Select the `.hmbackup` file from iCloud Drive, Google Drive, or local storage
4. If the file has a metadata wrapper, the passphrase hint is shown
5. Enter the passphrase
6. Review the medications to import and choose how to handle any conflicts
7. Data is restored
