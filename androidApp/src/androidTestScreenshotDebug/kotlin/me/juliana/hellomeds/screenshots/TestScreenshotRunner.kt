// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.backup.BackupImportService
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.database.AppDatabase
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.seedDefaultImportanceLabels
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import org.koin.core.context.GlobalContext
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Custom instrumentation runner for the Fastlane Screengrab variant.
 *
 * Three phases:
 *   1. Pre-app: grant DND policy + notification permissions BEFORE
 *      `Application.onCreate` runs, so notification channels are created
 *      with effective `canBypassDnd() == true`. This is the only way to
 *      suppress the "Critical channel cannot bypass DND" warning banner.
 *   2. Post-app: seed the database from `assets/example.<lang>.json` and
 *      flip preferences (skip onboarding, disable Material You).
 *   3. Post-seed: project past scheduled events and persist them as TAKEN
 *      history, so the Tracking screen has no missed-dose section.
 */
class TestScreenshotRunner : AndroidJUnitRunner() {

    override fun onCreate(arguments: Bundle) {
        // Screengrab passes the per-locale run via `-e testLocale <tag>`. Apply
        // it now — BEFORE the app is bound — so `Locale.getDefault()` (used by
        // our JSON picker) and CMP string resources both reflect the right
        // locale. screengrab's LocaleTestRule fires later, per-test, which is
        // too late for our `callApplicationOnCreate` seed.
        arguments.getString("testLocale")
            ?.takeIf { it.isNotBlank() }
            ?.let { applyLocale(it) }
        super.onCreate(arguments)
    }

    private fun applyLocale(tag: String) {
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        Locale.setDefault(locale)
        val res = targetContext.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        Log.i(TAG, "Applied locale $locale before app bind")
    }

    override fun callApplicationOnCreate(app: Application) {
        // Phase 1: BEFORE app.onCreate creates notification channels.
        grantRuntimePermissions(app.packageName)

        super.callApplicationOnCreate(app)

        // Phase 2 + 3: seed data + generate history.
        try {
            seedDemoData()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to seed demo data for screenshots", t)
            throw t
        }
    }

    private fun grantRuntimePermissions(pkg: String) {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("pm grant $pkg android.permission.POST_NOTIFICATIONS").close()
        ui.executeShellCommand("appops set $pkg SCHEDULE_EXACT_ALARM allow").close()
        ui.executeShellCommand("appops set $pkg USE_FULL_SCREEN_INTENT allow").close()
        // Grant DND policy access — this is what makes setBypassDnd(true) on the
        // critical channel actually take effect at channel-creation time.
        ui.executeShellCommand("cmd notification allow_dnd $pkg").close()
        ui.executeShellCommand("appops set $pkg ACCESS_NOTIFICATION_POLICY allow").close()
    }

    private fun seedDemoData() {
        val koin = GlobalContext.get()
        val database = koin.get<AppDatabase>()
        val service = koin.get<BackupImportService>()
        val onboarding = koin.get<OnboardingPreferences>()
        val appearance = koin.get<AppearancePreferences>()
        val labelDao = koin.get<ImportanceLabelDao>()
        val historyDao = koin.get<MedicationHistoryDao>()
        val projector = koin.get<ScheduleProjector>()

        val json = readDemoJson()
        val parsed = service.parseBackup(json).getOrThrow()
        val shifted = BackupDataDateShifter.shift(parsed)

        runBlocking(Dispatchers.IO) {
            onboarding.setOnboardingCompleted(true)
            appearance.setUseDynamicColor(false)

            // The DataModule launches `seedDefaultImportanceLabels` on
            // Dispatchers.Default when the AppDatabase singleton is built.
            // If we clearAllTables before it finishes, OR import labels that
            // overlap with the seeder, we get duplicates. Poll until the
            // five built-in defaults are present, *then* wipe + re-seed.
            waitForDefaultLabelsToSeed(labelDao)
            database.clearAllTables()
            seedDefaultImportanceLabels(labelDao)

            // JSON intentionally has no `importanceLabels[]` — meds bind by
            // name to the seeded defaults inserted just above.
            service.executeImport(shifted, decisions = emptyMap())

            generateTakenHistoryForPastEvents(projector, historyDao)
        }
        Log.i(TAG, "Seeded ${shifted.medications.size} medications for screenshots")
    }

    /** Poll up to 2s for the async default-label seeder to insert all 5 defaults. */
    private suspend fun waitForDefaultLabelsToSeed(labelDao: ImportanceLabelDao) {
        repeat(40) {
            if (labelDao.getAll().first().size >= 5) return
            delay(50)
        }
        // Falls through — re-running seedDefaultImportanceLabels below is idempotent.
    }

    /**
     * For every scheduled event in [now - 2 days, now] that doesn't already
     * have history, insert a TAKEN entry timestamped at its scheduled time.
     * Result: the Tracking screen shows yesterday + this-morning as taken,
     * and the rest of today as still-scheduled.
     */
    private suspend fun generateTakenHistoryForPastEvents(
        projector: ScheduleProjector,
        historyDao: MedicationHistoryDao,
    ) {
        val now = Clock.System.now()
        val start = now - 2.days
        val events = projector.projectEvents(
            startTime = start.toEpochMilliseconds(),
            endTime = now.toEpochMilliseconds(),
        )
        var inserted = 0
        for (event in events) {
            // Skip events that the projector already linked to a real history row.
            if (event.historyRecord != null) continue
            val scheduleId = event.scheduleId ?: continue
            val scheduledMs = event.scheduledTime
            if (scheduledMs > now.toEpochMilliseconds()) continue
            try {
                historyDao.insert(
                    MedicationHistory(
                        medicationId = event.medicationId,
                        scheduleId = scheduleId,
                        scheduledTime = scheduledMs,
                        takenTime = scheduledMs,
                        scheduledDose = event.dose,
                        actualDose = event.dose,
                        status = MedicationHistory.STATUS_TAKEN,
                    ),
                )
                inserted++
            } catch (e: Exception) {
                // Dedup index (medicationId,scheduleId,scheduledTime) — already
                // recorded. Safe to ignore.
                Log.d(TAG, "Skip duplicate history for ${event.medicationId}/$scheduleId: ${e.message}")
            }
        }
        Log.i(TAG, "Inserted $inserted past-event TAKEN history rows")
    }

    /**
     * Reads the locale-specific demo JSON from the *test* APK's assets
     * (androidTestScreenshotDebug/assets/), falling back to English.
     */
    private fun readDemoJson(): String {
        val testContext: Context = InstrumentationRegistry.getInstrumentation().context
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        val candidate = "example.$lang.json"
        val available = testContext.assets.list("")?.toSet().orEmpty()
        val name = if (candidate in available) candidate else "example.en.json"
        return testContext.assets.open(name).bufferedReader().use { it.readText() }
    }

    private companion object {
        const val TAG = "TestScreenshotRunner"
    }
}
