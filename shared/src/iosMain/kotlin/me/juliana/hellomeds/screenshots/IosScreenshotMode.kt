// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.data.backup.BackupDataDateShifter
import me.juliana.hellomeds.data.backup.BackupImportService
import me.juliana.hellomeds.data.dao.ImportanceLabelDao
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.MedicationHistoryDao
import me.juliana.hellomeds.data.database.entities.MedicationHistory
import me.juliana.hellomeds.data.database.seedDefaultImportanceLabels
import me.juliana.hellomeds.data.preferences.AppearancePreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.data.service.ScheduleProjector
import org.koin.mp.KoinPlatform
import platform.Foundation.NSLocale
import platform.Foundation.NSProcessInfo
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Gated by `Platform.isDebugBinary` so a release binary can never seed
 * even with a spoofed launch argument.
 */
object IosScreenshotMode {
    private const val ARG_SCREENSHOT_MODE = "--hm-screenshot-mode"

    @OptIn(ExperimentalNativeApi::class)
    fun isActive(): Boolean {
        if (!Platform.isDebugBinary) return false
        val args = NSProcessInfo.processInfo.arguments
        return args.any { (it as? String) == ARG_SCREENSHOT_MODE }
    }

    @OptIn(ExperimentalNativeApi::class)
    fun seedIfRequested() {
        if (!isActive()) return

        runBlocking(Dispatchers.Default) {
            val koin = KoinPlatform.getKoin()
            val service = koin.get<BackupImportService>()
            val onboarding = koin.get<OnboardingPreferences>()
            val appearance = koin.get<AppearancePreferences>()
            val labelDao = koin.get<ImportanceLabelDao>()
            val medicationDao = koin.get<MedicationDao>()
            val historyDao = koin.get<MedicationHistoryDao>()
            val projector = koin.get<ScheduleProjector>()

            val languageTag = NSLocale.currentLocale.languageCode ?: "en"
            val json = ScreenshotDemoData.loadJson(languageTag)
            val parsed = service.parseBackup(json).getOrThrow()
            val shifted = BackupDataDateShifter.shift(parsed)

            onboarding.setOnboardingCompleted(true)
            appearance.setUseDynamicColor(false)

            // DataModule seeds default labels asynchronously when AppDatabase
            // is first constructed; wait for that to settle before wiping or
            // the seeder will race the wipe and produce duplicates.
            waitForDefaultLabelsToSeed(labelDao)
            wipeUserData(medicationDao, labelDao)
            seedDefaultImportanceLabels(labelDao)

            service.executeImport(shifted, decisions = emptyMap())

            generateTakenHistoryForPastEvents(projector, historyDao)
        }
    }

    /**
     * Room KMP doesn't expose `clearAllTables` on iOS. `Flow.first()` on a
     * Room query can race the first emission on cold start and skip rows,
     * so the one-shot `getAllSuspend()` queries are load-bearing here.
     */
    private suspend fun wipeUserData(medicationDao: MedicationDao, labelDao: ImportanceLabelDao) {
        medicationDao.getAllSuspend().forEach { medicationDao.delete(it) }
        labelDao.getAllSuspend().forEach { labelDao.delete(it) }
    }

    private suspend fun waitForDefaultLabelsToSeed(labelDao: ImportanceLabelDao) {
        repeat(40) {
            if (labelDao.getAll().first().size >= 5) return
            delay(50)
        }
    }

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
        for (event in events) {
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
            } catch (_: Exception) {
                // Dedup index already has this slot.
            }
        }
    }
}
