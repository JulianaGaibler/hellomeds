import java.util.Properties

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.juliana.hellomeds"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "me.juliana.hellomeds"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionProps["APP_VERSION_CODE"].toString().toInt()
        versionName = versionProps["APP_VERSION_NAME"].toString()

        testInstrumentationRunner = "me.juliana.hellomeds.screenshots.TestScreenshotRunner"
    }

    // Run instrumentation tests against the screenshotDebug variant so the
    // Fastlane Screengrab APIs are available and the regular debug build
    // stays free of test-only deps.
    testBuildType = "screenshotDebug"

    flavorDimensions += "distribution"
    productFlavors {
        create("google") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
        create("screenshotDebug") {
            initWith(getByName("debug"))
            isPseudoLocalesEnabled = false
            applicationIdSuffix = ".screenshot"
            matchingFallbacks += "debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("profile") {
            initWith(getByName("release"))
            isDebuggable = false
            isProfileable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.time.ExperimentalTime",
        )
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.material3.expressive)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.material.icons.core)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // kotlinx-datetime (KMP-ready replacement for java.time)
    implementation(libs.kotlinx.datetime)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Navigation 3 (official)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    // CameraX (google flavor only — proprietary, not F-Droid compatible)
    "googleImplementation"(libs.androidx.camera.camera2)
    "googleImplementation"(libs.androidx.camera.lifecycle)
    "googleImplementation"(libs.androidx.camera.view)

    // ML Kit (google flavor only — proprietary, not F-Droid compatible)
    "googleImplementation"(libs.object1.detection)
    "googleImplementation"(libs.text.recognition)
    "googleImplementation"(libs.genai.prompt)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.workmanager)

    // Reorderable
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)

    // Screengrab (used only by the screenshotDebug instrumentation test variant)
    "androidTestScreenshotDebugImplementation"(libs.fastlane.screengrab)
    "androidTestScreenshotDebugImplementation"(libs.androidx.test.uiautomator)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
