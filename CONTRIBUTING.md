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
