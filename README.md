# HelloMeds

A medication tracking app for Android and iOS. Learn more at [juliana.me/projects/hellomeds](https://juliana.me/projects/hellomeds).

## Overview

HelloMeds is a personal medication manager that handles scheduling, reminders, dose logging, and stock tracking. It supports a wide range of medication types (tablets, inhalers, injections, patches, and more) and is designed around the idea that different medications need different levels of attention; a daily vitamin and a critical immunosuppressant should not be treated the same way.

The app runs natively on both platforms with a shared codebase and keeps all data on-device with encryption at rest.

## Features

**Scheduling.** Medications can be scheduled daily, on specific days of the week, or at custom intervals (every N days). Each medication can have multiple schedules at different times, and schedules can have start and end dates. For medications that follow a cycle pattern, HelloMeds supports configurable active/break periods with presets for common routines (21/7, 24/4, 28/0).

Schedules are timezone-aware. By default, a medication follows your local clock, so a 9 PM dose stays at 9 PM wherever you are. Medications that need consistent absolute timing can be pinned to a fixed timezone instead, so the reminder adjusts when you travel.

**Reminders and importance labels.** Each medication is assigned an importance label that controls its entire notification behavior. Labels are user-configurable and determine whether the medication gets reminders at all, how many follow-up reminders are sent if a dose goes unconfirmed, and whether those follow-ups escalate to critical alerts or alarm sounds. This lets you set up aggressive escalation chains for essential medications while keeping supplement reminders low-key or silent.

**Stock tracking.** Stock tracking is opt-in per medication. In exact mode, the app counts individual doses and automatically deducts each time you log a dose. In estimated mode, it tracks containers and projects when the current one will run out based on your schedule frequency. Both modes show remaining supply, a predicted run-out date, and low-stock warnings.

**Camera detection.** HelloMeds can optionally identify medications using your device's camera with on-device machine learning. On Android, this uses Google's ML Kit, which runs entirely on-device. On iOS, this uses Apple Intelligence, which processes requests on-device when possible but may use [Private Cloud Compute](https://www.apple.com/legal/privacy/data/en/intelligence-engine/) for tasks that require more computational power. In both cases, HelloMeds itself does not transmit any images or medication data to external servers. (Play Store and iOS only)

**Dose logging.** When a scheduled dose comes due, you can mark it as taken (with the option to adjust the actual dose and time), skip it, or let it auto-skip after the reminder window passes. The app keeps a full history of every action for each medication.

**Backups.** Data can be exported manually or backed up automatically on a daily schedule. Backups can be plain JSON or encrypted with a passphrase. Backup files work across both platforms, so you can export from Android and import on iOS or vice versa.

**Privacy.** All medication data is stored locally in an encrypted database. There are no accounts, no cloud sync, and no data collection. Camera detection on Android uses on-device ML Kit models with no network access. On iOS, Apple Intelligence may route some processing through [Private Cloud Compute](https://www.apple.com/legal/privacy/data/en/intelligence-engine/); see Apple's privacy policy for details on how that data is handled.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture, build instructions, and development patterns.

## Dependencies

The full dependency list is in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). These are the notable ones:

- **Platform**
  - [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) - Shared business logic and data layer across Android and iOS
  - [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) - Shared UI framework
  - [Navigation 3](https://developer.android.com/develop/ui/compose/navigation/navigation3) - Type-safe navigation (JetBrains CMP fork)
- **Data**
  - [Room KMP](https://developer.android.com/kotlin/multiplatform/room) - Local database with cross-platform support
  - [kotlinx-serialization](https://github.com/Kotlin/kotlinx.serialization) - JSON serialization for backups and data exchange
  - [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) - Cross-platform date and time handling
  - [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) - Preferences storage
- **Security**
  - [SQLCipher](https://www.zetetic.net/sqlcipher/) - AES-256 database encryption
  - [AndroidX Security Crypto](https://developer.android.com/reference/androidx/security/crypto/package-summary) - Encrypted key storage on Android
- **ML and Camera**
  - [ML Kit](https://developers.google.com/ml-kit) - On-device object detection and text recognition (Android)
  - [CameraX](https://developer.android.com/training/camerax) - Camera integration (Android)
- **Other**
  - [Koin](https://insert-koin.io/) - Dependency injection
  - [Material 3 Expressive](https://developer.android.com/develop/ui/compose/designsystems/material3-expressive) - UI component library

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
