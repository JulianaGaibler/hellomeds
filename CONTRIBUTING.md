# Contributing to HelloMeds

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

## Versioning

App version is defined in `version.properties` at the project root. This is the single source of truth for both platforms. Android reads it via Gradle; iOS uses a generated `.xcconfig` file created by `./hm setup`.

## Technical Documentation

Detailed documentation on some subsystems lives in `docs/`

## F-Droid distribution

The `fdroid` product flavor in `androidApp/build.gradle.kts` excludes all
proprietary dependencies (ML Kit, CameraX, GenAI prompt) and strips the
`CAMERA` permission via `androidApp/src/fdroid/AndroidManifest.xml`. Use this
flavor when building for F-Droid:

```bash
./gradlew :androidApp:assembleFdroidRelease
```

The reference fdroiddata build metadata lives at
[`fdroid/me.juliana.hellomeds.yml`](fdroid/me.juliana.hellomeds.yml). When
preparing a release for F-Droid, copy this file into a fork of
`gitlab.com/fdroid/fdroiddata` under `metadata/me.juliana.hellomeds.yml` and
add a new entry to `Builds:` for the new `versionCode`.

### Reproducible builds

The Android build pulls SQLCipher's native `.so` files as a prebuilt artifact
from Maven Central — no NDK compilation happens locally, which removes the
biggest source of build non-determinism. To keep reproducibility working,
contributors and the F-Droid build server should use:

- **JDK 21** (the Android Studio JBR ships this; `./hm` auto-detects it).
- Pinned AGP / Kotlin / Compose versions from `gradle/libs.versions.toml`.

Avoid embedding timestamps, git SHAs, or environment paths via
`buildConfigField`. `BuildConfig.VERSION_NAME`, `VERSION_CODE`, and `DEBUG`
are the only build-config values the app currently consumes; keep it that
way unless there's a compelling reason otherwise.

After F-Droid produces a build, comparing it to the GitHub-released APK with
`apksigcopier` + `diffoscope` will surface any remaining drift.
