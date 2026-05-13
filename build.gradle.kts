// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        licenseHeader(
            "// SPDX-License-Identifier: AGPL-3.0-or-later\n// Copyright (C) \$YEAR HelloMeds Contributors\n\n",
            "^(package|@file|import)",
        )
        ktlint("1.2.1")
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_max-line-length" to "disabled",
                    "ktlint_standard_value-parameter-comment" to "disabled",
                    "ktlint_standard_backing-property-naming" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_property-naming" to "disabled",
                    "ktlint_standard_comment-wrapping" to "disabled",
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint("1.2.1")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
