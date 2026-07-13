# HelloMeds

> [!NOTE]
> Android closed beta is running. **[sign up here](https://juliana.me/projects/hellomeds/closedbeta/)** to help test before public release.

A medication tracking app for Android and iOS. Learn more at [juliana.me/projects/hellomeds](https://juliana.me/projects/hellomeds).

## Overview

HelloMeds handles scheduling, reminders, dose logging, and stock tracking. It is designed to adapt to how different medications actually need to be managed. All data stays on your device, encrypted at rest. 

## Features

**Scheduling.** Medications can be scheduled daily, on specific days of the week, or at custom intervals. Each medication can have multiple schedules at different times. For medications that follow a cycle pattern, HelloMeds supports configurable active/break periods.

**Reminders and notification types.** Each medication is assigned a notification type that controls its entire notification behavior. Types are user-configurable and determine how many follow-up reminders are sent if a dose goes unconfirmed, and whether those follow-ups escalate to critical alerts or alarm sounds. This lets you set up aggressive escalation chains for essential medications while keeping supplement reminders low-key or silent.

**Stock tracking.** Additionally, you can track your medication stock levels and get low-stock reminders. When you log a dose as taken, the app automatically deducts from your stock based on the dose amount and unit.

**Camera detection.** HelloMeds can optionally identify medications using your device's camera with on-device machine learning.

**Dose logging.** When a scheduled dose comes due, you can mark it as taken, skip it, or let it auto-skip after the reminder window passes. The app keeps a full history of every action for each medication.

**Backups.** Data can be exported manually or backed up automatically on a daily schedule. Backups are encrypted and stored locally.

**Privacy.** All medication data is stored locally in an encrypted database. There are no accounts, no cloud sync, and no data collection.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture, build instructions, and development patterns.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

## Releasing

Releases are produced automatically by `.github/workflows/release.yml`. On every push to `main`, the workflow reads `APP_VERSION_NAME` from `version.properties`; if no GitHub release with the matching `v<name>` tag exists yet, it builds the signed Google-flavor AAB and F-Droid-flavor APK, then publishes them as a tagged release with the changelog as its body.

**One-time setup**

1. Generate an upload keystore: `keytool -genkey -v -keystore upload.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias hellomeds-upload`
2. Base64-encode it: `base64 -i upload.keystore -o upload.keystore.b64`
3. Add four GitHub repository secrets: `HM_KEYSTORE_BASE64` (paste the `.b64` contents), `HM_KEYSTORE_PASSWORD`, `HM_KEY_ALIAS` (`hellomeds-upload`), `HM_KEY_PASSWORD`.
4. In Play Console, enroll in Play App Signing and upload `upload.keystore` as the upload certificate (Google holds the actual signing key).
5. For local signed builds, create `keystore.properties` at the repo root with `storeFile=`, `storePassword=`, `keyAlias=`, `keyPassword=` (gitignored).

**Per release**

1. Bump `APP_VERSION_NAME` and `APP_VERSION_CODE` in `version.properties`.
2. Run `./hm prepare-release` — moves `unreleased.notes` in `changelog.yaml` into a new versioned entry and writes the F-Droid `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` files.
3. Commit the version bump, `changelog.yaml`, and the new changelog files together; merge to `main`.
4. When the workflow finishes, download the AAB from the GitHub release and upload it to Play Console manually. The attached F-Droid APK is for sideloaders and as a reference artifact for future reproducible-build verification.
