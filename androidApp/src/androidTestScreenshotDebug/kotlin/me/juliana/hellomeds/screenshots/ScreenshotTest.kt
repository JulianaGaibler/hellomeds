// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.juliana.hellomeds.MainActivity
import me.juliana.hellomeds.data.dao.MedicationDao
import me.juliana.hellomeds.data.dao.ScheduleDao
import me.juliana.hellomeds.data.model.enums.CycleType
import me.juliana.hellomeds.designsystem.testing.ScreenshotTestTags
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Generates the Play Store screenshots for HelloMeds.
 *
 * The database is seeded once per test process by [TestScreenshotRunner] in
 * `callApplicationOnCreate`, before MainActivity launches. Each `@Test` here
 * represents one screen captured per locale per device form factor.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val medicationDao by lazy { GlobalContext.get().get<MedicationDao>() }
    private val scheduleDao by lazy { GlobalContext.get().get<ScheduleDao>() }

    private val singleScheduleMedId: Int by lazy {
        runBlocking {
            val meds = medicationDao.getActive().first().sortedBy { it.displayOrder }
            meds.first { med ->
                scheduleDao.getByMedicationId(med.id).first().size == 1
            }.id
        }
    }

    private val stockMedId: Int by lazy {
        runBlocking {
            val active = medicationDao.getActive().first().sortedBy { it.displayOrder }
            // Prefer the allergy tablet (Cetirizin/Cetirizine), then any
            // blister-pack med, then any stock-tracked med.
            val allergyTablet = active.firstOrNull { med ->
                med.stockTrackingEnabled &&
                    (
                        med.displayName?.contains("Allerg", ignoreCase = true) == true ||
                            med.name.contains("Cetiriz", ignoreCase = true)
                        )
            }
            val anyBlister = active.firstOrNull {
                it.stockTrackingEnabled && it.medicationContainer?.name == "BLISTER_PACK"
            }
            (allergyTablet ?: anyBlister ?: active.first { it.stockTrackingEnabled }).id
        }
    }

    private val cyclicMedId: Int by lazy {
        runBlocking {
            medicationDao.getActive().first()
                .sortedBy { it.displayOrder }
                .first { it.cycleType == CycleType.CYCLIC }
                .id
        }
    }

    // Topmost stock-tracked medication — used as a settle signal for the
    // stock list. Items further down the LazyColumn aren't in the semantics
    // tree until they scroll into view.
    private val firstStockMedId: Int by lazy {
        runBlocking {
            medicationDao.getActive().first()
                .sortedBy { it.displayOrder }
                .first { it.stockTrackingEnabled }
                .id
        }
    }

    @Before
    fun configureScreenshotStrategy() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @Test
    fun s01_tracking() {
        // Wait for the "Taken" section header to appear — it only renders
        // once the history StateFlow has emitted the past-event TAKEN rows
        // seeded by TestScreenshotRunner. Deterministic vs a fixed sleep.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag(ScreenshotTestTags.TRACKING_SECTION_TAKEN)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        Screengrab.screenshot("01_tracking")
    }

    @Test
    fun s02_newScheduleDialogDaysOfWeek() {
        composeRule.waitForIdle()

        // On phones the medication grid sits behind the MEDICATION nav tab.
        // On wide tablets (≥1024dp) the adaptive layout renders all three panes
        // simultaneously — there's no nav tab — so tapping is optional.
        tapIfPresent(ScreenshotTestTags.navTab("MEDICATION"))
        tapFirstByTag(ScreenshotTestTags.medicationGridItem(singleScheduleMedId))
        tapFirstByTag(ScreenshotTestTags.MEDICATION_ACTION_SCHEDULE)
        // The schedule action opens a list of existing schedules first; tap
        // the "Add Schedule" card to open the actual bottom sheet.
        tapFirstByTag(ScreenshotTestTags.SCHEDULE_ADD_BUTTON)
        tapFirstByTag(ScreenshotTestTags.SCHEDULE_FREQ_DAYS_OF_WEEK)
        tapFirstByTag(ScreenshotTestTags.scheduleDayChip("MONDAY"))
        tapFirstByTag(ScreenshotTestTags.scheduleDayChip("THURSDAY"))

        Screengrab.screenshot("02_schedule_days_of_week")
    }

    @Test
    fun s03_importanceLabels() {
        composeRule.waitForIdle()

        tapFirstByTag(ScreenshotTestTags.OVERFLOW_MENU_BUTTON)
        tapFirstByTag(ScreenshotTestTags.OVERFLOW_MENU_SETTINGS)
        tapFirstByTag(ScreenshotTestTags.SETTINGS_IMPORTANCE_LABELS)

        Screengrab.screenshot("03_importance_labels")
    }

    @Test
    fun s04_stockDetail() {
        composeRule.waitForIdle()

        // Navigate via the medication grid — its 2-column layout puts the
        // allergy tablet on-screen without scrolling, unlike the stock tab's
        // single-column LazyColumn where it lives below the fold. The nav-tab
        // tap is a no-op on wide tablets where all panes are already visible.
        tapIfPresent(ScreenshotTestTags.navTab("MEDICATION"))
        tapFirstByTag(ScreenshotTestTags.medicationGridItem(stockMedId))
        tapFirstByTag(ScreenshotTestTags.MEDICATION_ACTION_STOCK)

        Screengrab.screenshot("04_stock_detail")
    }

    @Test
    fun s05_medicationList() {
        composeRule.waitForIdle()

        // Compact phone layout: tap into the MEDICATION tab. Wide tablet
        // already shows the grid as part of its three-pane layout.
        tapIfPresent(ScreenshotTestTags.navTab("MEDICATION"))
        // Wait until at least one grid item is in the semantics tree — the
        // medication list is fed by a Flow that emits after Koin builds the
        // DAO.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag(ScreenshotTestTags.medicationGridItem(stockMedId))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        Screengrab.screenshot("05_medication_list")
    }

    @Test
    fun s06_logMedicationAsNeeded() {
        composeRule.waitForIdle()

        // Open the FAB menu, then tap the "Log as-needed" item. Both are
        // tagged so the sequence works regardless of locale.
        tapFirstByTag(ScreenshotTestTags.TRACKING_FAB_TOGGLE)
        tapFirstByTag(ScreenshotTestTags.TRACKING_FAB_LOG_AS_NEEDED)
        composeRule.waitForIdle()

        Screengrab.screenshot("06_log_medication_as_needed")
    }

    @Test
    fun s07_medicationDetailCyclic() {
        composeRule.waitForIdle()

        tapIfPresent(ScreenshotTestTags.navTab("MEDICATION"))
        tapFirstByTag(ScreenshotTestTags.medicationGridItem(cyclicMedId))
        composeRule.waitForIdle()

        Screengrab.screenshot("07_medication_detail_cyclic")
    }

    @Test
    fun s08_stockList() {
        composeRule.waitForIdle()

        tapIfPresent(ScreenshotTestTags.navTab("STOCK"))
        // The list rows render once the stock-status flow emits; settle on
        // the topmost stock row, which is always above the fold.
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag(ScreenshotTestTags.stockListItem(firstStockMedId))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        Screengrab.screenshot("08_stock_list")
    }

    /**
     * Waits for at least one node with [tag] to appear, then clicks the first.
     *
     * Plain `waitForIdle()` doesn't cover navigation overlay opens or
     * `flatMapLatest { ... }.stateIn(...)` flows. And LazyColumn items below
     * the fold are absent from the semantics tree — so we fall back to a
     * swipe-up loop to bring off-screen targets into view.
     */
    /**
     * Taps a node by tag if it exists *right now*. Doesn't wait or scroll.
     * Use for steps that are conditional on form factor (e.g. a nav tab that
     * only exists in the compact layout).
     */
    private fun tapIfPresent(tag: String) {
        val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            composeRule.onAllNodesWithTag(tag).onFirst().performClick()
            composeRule.waitForIdle()
        }
    }

    private fun tapFirstByTag(tag: String, timeoutMs: Long = 5_000L) {
        fun nodes() = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        try {
            composeRule.waitUntil(timeoutMs) { nodes().isNotEmpty() }
        } catch (_: Throwable) {
            // Tag may live below the fold of a LazyColumn — off-screen items
            // are absent from the semantics tree entirely. Swipe up until it
            // appears or we run out of patience.
            for (i in 0 until 8) {
                composeRule.onRoot().performTouchInput { swipeUp() }
                composeRule.waitForIdle()
                if (nodes().isNotEmpty()) break
            }
            // Final assertion — throws with a clear error if still missing.
            composeRule.waitUntil(2_000L) { nodes().isNotEmpty() }
        }
        composeRule.onAllNodesWithTag(tag).onFirst().performClick()
        composeRule.waitForIdle()
    }

    companion object {
        @ClassRule
        @JvmField
        val localeTestRule = LocaleTestRule()

        // Fastlane's reinstall_app(true) triggers an Android post-install
        // heads-up notification ("App installed" / Play Protect scan) that
        // overlays the first screenshot. Wait it out before launching the
        // activity in the first @Test.
        @BeforeClass
        @JvmStatic
        fun waitForPostInstallNotificationToDismiss() {
            Thread.sleep(5_000)
        }
    }
}
