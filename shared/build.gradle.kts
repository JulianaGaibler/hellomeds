import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

composeCompiler {
    stabilityConfigurationFile = rootProject.layout.projectDirectory.file("stability_config.conf")
}

kotlin {
    androidLibrary {
        namespace = "me.juliana.hellomeds.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        androidResources.enable = true
    }

    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // Opt-in for M3 Expressive API
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    // iOS targets with shared framework
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            api(projects.core.domain)
            api(projects.core.designsystem)

            // Dependency injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            // Graphics shapes (KMP — needed for MaterialShapes compat)
            implementation(libs.androidx.graphics.shapes)

            // Navigation 3 (CMP fork)
            implementation(libs.cmp.navigation3.ui)
            implementation(libs.cmp.material3.adaptive.navigation3)

            // Serialization (for @Serializable routes)
            implementation(libs.kotlinx.serialization.core)

            // Coroutines + datetime
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Reorderable (drag & drop for lazy grids/lists)
            implementation(libs.reorderable)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)

            // Lifecycle-aware Flow collection (collectAsStateWithLifecycle)
            implementation(libs.androidx.lifecycle.runtime.compose)

            // M3 Expressive (upgrades CMP's bundled M3 to enable Expressive APIs)
            implementation(libs.androidx.material3.expressive)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.juliana.hellomeds.shared"
    generateResClass = auto
}

tasks.register("generateVersionXcconfig") {
    val versionFile = rootProject.file("version.properties")
    val outputFile = rootProject.file("iosApp/Version.generated.xcconfig")
    inputs.file(versionFile)
    outputs.file(outputFile)
    doLast {
        val props = Properties().apply {
            versionFile.inputStream().use { load(it) }
        }
        outputFile.writeText(
            "// Auto-generated from version.properties — do not edit\n" +
                "MARKETING_VERSION = ${props["APP_VERSION_NAME"]}\n" +
                "CURRENT_PROJECT_VERSION = ${props["APP_VERSION_CODE"]}\n",
        )
    }
}

tasks.configureEach {
    if (name.contains("embedAndSignAppleFrameworkForXcode")) {
        dependsOn("generateVersionXcconfig")
    }
}
