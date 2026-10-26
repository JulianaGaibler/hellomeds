// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.time.LocalDate

/**
 * Release helpers for HelloMeds.
 *
 * - `prepareRelease` moves unreleased changelog notes into a versioned block, writes the
 *   F-Droid and Play changelog files, and adds the version to the F-Droid metadata.
 *   Run it locally before the version bump commit.
 * - `releaseBody` renders the GitHub release body to `build/release/release-body.md`.
 * - `validateReleaseNotes` fails if the changelog file for the current version is missing
 *   or too long.
 *
 * All three read the version from `version.properties`.
 */
fun Project.registerReleaseTasks() {
    val versionFile = layout.projectDirectory.file("version.properties")
    val changelogFile = layout.projectDirectory.file("changelog.yaml")
    val fastlaneDir = layout.projectDirectory.dir("fastlane/metadata/android")

    tasks.register<PrepareReleaseTask>("prepareRelease") {
        group = "release"
        description = "Moves unreleased changelog notes into the current version and writes store notes."
        versionProperties.set(versionFile)
        changelog.set(changelogFile)
        fastlaneMetadata.set(fastlaneDir)
        fdroidMetadata.set(layout.projectDirectory.file("fdroid/me.juliana.hellomeds.yml"))
    }
    tasks.register<ReleaseBodyTask>("releaseBody") {
        group = "release"
        description = "Renders the GitHub release body for the current version."
        versionProperties.set(versionFile)
        changelog.set(changelogFile)
        output.set(layout.buildDirectory.file("release/release-body.md"))
    }
    tasks.register<ValidateReleaseNotesTask>("validateReleaseNotes") {
        group = "release"
        description = "Checks that the store changelog for the current version exists and fits the limit."
        versionProperties.set(versionFile)
        fastlaneMetadata.set(fastlaneDir)
    }
}

// Google Play caps release notes at 500 characters, and F-Droid uses the same files.
private const val CHANGELOG_CHAR_LIMIT = 500
private val FASTLANE_LOCALES = listOf("en-US", "de-DE")

private data class AppVersion(val name: String, val code: Int)

private fun readVersion(file: File): AppVersion {
    val values = file.readLines()
        .map { it.trim() }
        .filter { "=" in it && !it.startsWith("#") }
        .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    val name = values["APP_VERSION_NAME"]
    val code = values["APP_VERSION_CODE"]?.toIntOrNull()
    if (name == null || code == null) {
        throw GradleException("version.properties is missing APP_VERSION_NAME or APP_VERSION_CODE")
    }
    return AppVersion(name, code)
}

private fun yaml(): Yaml = Yaml(
    DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isAllowUnicode = true
    },
)

@Suppress("UNCHECKED_CAST")
private fun loadChangelog(file: File): MutableMap<String, Any?> {
    val data = yaml().load<MutableMap<String, Any?>>(file.readText()) ?: linkedMapOf()
    val unreleased = data.getOrPut("unreleased") { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
    if (unreleased["notes"] == null) unreleased["notes"] = mutableListOf<String>()
    if (data["releases"] == null) data["releases"] = mutableListOf<Map<String, Any?>>()
    return data
}

@Suppress("UNCHECKED_CAST")
private fun MutableMap<String, Any?>.releases(): MutableList<MutableMap<String, Any?>> =
    this["releases"] as MutableList<MutableMap<String, Any?>>

@Suppress("UNCHECKED_CAST")
private fun MutableMap<String, Any?>.unreleased(): MutableMap<String, Any?> =
    this["unreleased"] as MutableMap<String, Any?>

private fun Map<String, Any?>.notes(): List<String> = (this["notes"] as? List<*>).orEmpty().map { it.toString() }

private fun MutableMap<String, Any?>.findRelease(version: String): MutableMap<String, Any?>? =
    releases().firstOrNull { it["version"]?.toString() == version }

// version.properties sits in the project root, which keeps task actions off Task.project.
private fun File.relativeToRoot(versionProperties: RegularFileProperty): File =
    relativeTo(versionProperties.get().asFile.parentFile)

private fun storeChangelogFile(fastlaneDir: File, locale: String, versionCode: Int): File =
    fastlaneDir.resolve("$locale/changelogs/$versionCode.txt")

abstract class PrepareReleaseTask : DefaultTask() {
    @get:InputFile
    abstract val versionProperties: RegularFileProperty

    // The files below are edited in place, so they are neither plain inputs nor outputs.
    @get:Internal
    abstract val changelog: RegularFileProperty

    @get:Internal
    abstract val fastlaneMetadata: DirectoryProperty

    @get:Internal
    abstract val fdroidMetadata: RegularFileProperty

    @TaskAction
    fun prepare() {
        val version = readVersion(versionProperties.get().asFile)
        val changelogFile = changelog.get().asFile
        val data = loadChangelog(changelogFile)

        val notes = data.findRelease(version.name)?.let { existing ->
            logger.lifecycle("Reusing existing changelog entry for ${version.name} (versionCode ${version.code}).")
            existing.notes()
        } ?: run {
            val unreleased = data.unreleased().notes()
            if (unreleased.isEmpty()) {
                throw GradleException(
                    "unreleased.notes is empty and no releases entry for ${version.name} exists yet. " +
                        "Add notes to changelog.yaml first.",
                )
            }
            // The most recent release goes first.
            data.releases().add(
                0,
                linkedMapOf(
                    "version" to version.name,
                    "versionCode" to version.code,
                    "date" to LocalDate.now().toString(),
                    "notes" to unreleased,
                ),
            )
            data.unreleased()["notes"] = mutableListOf<String>()
            changelogFile.writeText(yaml().dump(data))
            val entries = if (unreleased.size == 1) "entry" else "entries"
            logger.lifecycle(
                "Moved ${unreleased.size} unreleased $entries into release ${version.name} " +
                    "(versionCode ${version.code}).",
            )
            unreleased
        }

        val body = notes.joinToString(separator = "") { "- $it\n" }
        if (body.length > CHANGELOG_CHAR_LIMIT) {
            throw GradleException(
                "Rendered changelog is ${body.length} chars. Trim it to $CHANGELOG_CHAR_LIMIT or fewer " +
                    "(Google Play and F-Droid limit) and run again.",
            )
        }

        // Every locale gets the English notes so non-English devices don't show an empty panel.
        val fastlaneDir = fastlaneMetadata.get().asFile
        for (locale in FASTLANE_LOCALES) {
            val file = storeChangelogFile(fastlaneDir, locale, version.code)
            file.parentFile.mkdirs()
            file.writeText(body)
            logger.lifecycle("Wrote ${file.relativeToRoot(versionProperties)}")
        }

        updateFdroidMetadata(fdroidMetadata.get().asFile, version)
    }

    private fun updateFdroidMetadata(file: File, version: AppVersion) {
        var text = file.readText()
        val hasBuild = Regex("^\\s*versionCode: ${version.code}\\s*$", RegexOption.MULTILINE).containsMatchIn(text)
        if (!hasBuild) {
            val anchor = Regex("^AutoUpdateMode:", RegexOption.MULTILINE).find(text)
                ?: throw GradleException("${file.name} has no AutoUpdateMode line to insert the build before")
            val build = """
                |  - versionName: ${version.name}
                |    versionCode: ${version.code}
                |    commit: v${version.name}
                |    subdir: androidApp
                |    gradle:
                |      - fdroid
                |
                |
            """.trimMargin()
            text = text.substring(0, anchor.range.first) + build + text.substring(anchor.range.first)
        }
        text = text
            .replace(Regex("^CurrentVersion: .*$", RegexOption.MULTILINE), "CurrentVersion: ${version.name}")
            .replace(Regex("^CurrentVersionCode: .*$", RegexOption.MULTILINE), "CurrentVersionCode: ${version.code}")
        file.writeText(text)
        logger.lifecycle("Updated ${file.relativeToRoot(versionProperties)}")
    }
}

abstract class ReleaseBodyTask : DefaultTask() {
    @get:InputFile
    abstract val versionProperties: RegularFileProperty

    @get:InputFile
    abstract val changelog: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun render() {
        val version = readVersion(versionProperties.get().asFile)
        val entry = loadChangelog(changelog.get().asFile).findRelease(version.name)
            ?: throw GradleException("No releases entry for ${version.name} in changelog.yaml")
        val body = buildString {
            append("## v${version.name}\n\n")
            entry.notes().forEach { append("- $it\n") }
        }
        output.get().asFile.writeText(body)
        logger.lifecycle("Wrote ${output.get().asFile.relativeToRoot(versionProperties)}")
    }
}

abstract class ValidateReleaseNotesTask : DefaultTask() {
    @get:InputFile
    abstract val versionProperties: RegularFileProperty

    @get:Internal
    abstract val fastlaneMetadata: DirectoryProperty

    @TaskAction
    fun validate() {
        val version = readVersion(versionProperties.get().asFile)
        val file = storeChangelogFile(fastlaneMetadata.get().asFile, "en-US", version.code)
        val path = file.relativeToRoot(versionProperties)
        if (!file.exists()) {
            throw GradleException("Missing $path. Run `./hm prepare-release` before bumping the version.")
        }
        val length = file.readText().length
        if (length > CHANGELOG_CHAR_LIMIT) {
            throw GradleException("$path is $length chars (more than $CHANGELOG_CHAR_LIMIT).")
        }
        logger.lifecycle("$path OK ($length chars)")
    }
}
