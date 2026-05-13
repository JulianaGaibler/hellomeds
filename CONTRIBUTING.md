# Contributing to HelloMeds

This guide covers the project architecture, build process, and development patterns you will need to work on HelloMeds. For a general overview of the app, see the [README](README.md).

## Prerequisites

- **Android Studio** (latest stable) with the Android SDK (compileSdk 36, minSdk 31)
- **Xcode** (latest stable) for iOS builds
- **JDK** is bundled with Android Studio; the build scripts use its JBR automatically

## Architecture

Compose Multiplatform handles the shared UI; platform-specific code lives in `androidApp/` and `iosApp/`.

```
┌───────────────┐   ┌─────────────┐
│    Android    │   │     iOS     │
│ (androidApp/) │   │  (iosApp/)  │
└──────┬────────┘   └─────┬───────┘
       │                  │
       └────────┬─────────┘
                │
       ┌────────┴────────┐
       │     shared/     │   Compose Multiplatform UI
       │   (commonMain)  │   Screens, ViewModels, Components
       └────────┬────────┘
                │
    ┌───────────┼────────────┐
    │           │            │
┌───┴───┐   ┌───┴────┐  ┌────┴────┐
│ core/ │   │ core/  │  │ core/   │
│ data  │   │ domain │  │ design  │
│       │   │        │  │ system  │
└───────┘   └────────┘  └─────────┘
```

### Modules

| Module | Purpose |
|--------|---------|
| `androidApp` | Android shell: MainActivity, workers, Android-only screens |
| `shared` | Compose Multiplatform: all shared screens, ViewModels, navigation, compat layer |
| `core/data` | Room KMP database, DAOs, repositories, backup services, preferences |
| `core/domain` | Business logic, ML engine interfaces |
| `core/designsystem` | Theme, colors, typography |
| `iosApp` | Xcode project: Swift entry point, Foundation Models bridge, CryptoKit bridge |

### Tech Stack

- Kotlin Multiplatform with Compose Multiplatform 1.10
- Room KMP 2.8 with SQLCipher encryption
- Navigation 3 (type-safe, JetBrains CMP fork)
- Koin 4 for dependency injection
- kotlinx-datetime and kotlinx-serialization
- ML Kit + Gemini Nano (Android) / Vision + Apple Intelligence (iOS)
- CameraX (Android) / AVFoundation (iOS)

## Getting Started

```bash
./hm setup    # Check prerequisites, generate iOS version config
```

### Building

```bash
./hm build android    # Debug APK
./hm build ios        # iOS framework
./hm build            # Both platforms
```

For iOS, after building the framework, open `iosApp/HelloMeds.xcodeproj` in Xcode to build and run.

### Quick Verification

After making changes to shared code, always verify both platforms compile:

```bash
./hm verify    # Both-platform compilation check
```

### Raw Gradle Commands

If you need finer control, you can invoke Gradle directly. Java is not on PATH by default, so set `JAVA_HOME` first:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :shared:compileAndroidMain :shared:compileKotlinIosArm64   # Both targets
./gradlew :androidApp:compileDebugKotlin                              # Full Android app
./gradlew testDebugUnitTest                                           # Unit tests
```

## Platform Abstraction Patterns

The app uses several patterns to share code across platforms while still accessing native APIs.

**Expect/Actual** is used for platform APIs like `PermissionUtils`, `PlatformCapabilities`, `BackupEncryption`, and `MaterialShapes`.

**NavigationScreenProviders** is an injection pattern for screens that require app-module dependencies (DebugScreen, CameraDetection).

**Callback bridges** handle Swift-only APIs. Kotlin defines callback registration functions; Swift implements and registers them at startup in `ContentView.swift`. This is how Foundation Models and CryptoKit are accessed.

**Compat layer** provides M3 Expressive components (`ToggleButton`, `LoadingIndicator`, `MaterialShapes`) with fallbacks on iOS where the native implementations are not yet available.

## Backup Format

Exports to `.json` (plain) or `.hmeds` (encrypted). Encryption uses AES-256-GCM with PBKDF2-SHA256 key derivation (210,000 iterations). Backup files are cross-platform compatible between Android and iOS.

## Versioning

App version is defined in `version.properties` at the project root. This is the single source of truth for both platforms. Android reads it via Gradle; iOS uses a generated `.xcconfig` file created by `./hm setup`.

## Technical Documentation

Detailed documentation on specific subsystems lives in `docs/`:

- [Backup System](docs/backups.md) - File format, encryption details, automatic backup scheduling, import/export flow
- [Database Encryption](docs/database-encryption.md) - SQLCipher setup, key management, platform-specific implementation
- [Medication Cycles](docs/cycles.md) - Cycle logic, schedule masking, notification behavior during active and break periods
- [Timezone Handling](docs/timezones.md) - Per-medication timezone modes, travel behavior, timezone change detection
