// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

// NotificationPermissionScreen is injected via parameter (platform-specific)
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.preferences.AutoBackupPreferences
import me.juliana.hellomeds.data.preferences.OnboardingPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.accessibility_pager_state
import me.juliana.hellomeds.ui.compat.PlatformBackHandler
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.features.onboarding.steps.BetaThankYouScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.CompletionScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.ExactAlarmPermissionScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.FullScreenIntentPermissionScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.QuickStartScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.WelcomeScreen
import me.juliana.hellomeds.ui.util.PermissionUtils
import me.juliana.hellomeds.ui.util.PlatformCapabilities
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Main onboarding flow coordinator
 *
 * Screens:
 * 1. Welcome (Splash) - "Say HelloMeds"
 * 2. Notifications Permission
 * 3. Exact Alarms Permission (Precise Timing)
 * 4. Quick Start - 3-step guide
 * 5. Completion (Splash) - "All set!" with add medication options
 *
 * Note: Dark theme is applied at the navigation level in HelloMedsNavigation3.kt
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToManualAdd: () -> Unit,
    onNavigateToImport: () -> Unit,
    showAllSteps: Boolean = false, // Debug flag to show all steps even if granted
    onExitApp: () -> Unit = {}, // Platform-specific app exit (finish Activity on Android)
    notificationPermissionScreen: @Composable (onContinue: () -> Unit, onBack: () -> Unit) -> Unit = { _, _ -> },
    alarmKitPermissionScreen: (@Composable (onContinue: () -> Unit, onBack: () -> Unit) -> Unit)? = null,
    criticalAlertsPermissionScreen: (@Composable (onContinue: () -> Unit, onBack: () -> Unit) -> Unit)? = null,
) {
    val context = platformContext()
    val scope = rememberCoroutineScope()
    val prefs = koinInject<OnboardingPreferences>()
    val autoBackupPrefs = koinInject<AutoBackupPreferences>()

    // Check if this is initial onboarding or a review/debug launch
    val wasAlreadyCompleted by prefs.onboardingCompleted.collectAsStateWithLifecycle(initial = false)

    suspend fun completeOnboarding() {
        prefs.setOnboardingCompleted(true)
        autoBackupPrefs.setOnboardingCompletedTimestamp(Clock.System.now().toEpochMilliseconds())
    }

    // Build dynamic page list - skip already granted permissions unless showAllSteps
    val pages = remember(showAllSteps) {
        buildList {
            // Screen 1: Always show welcome
            add(OnboardingPage.Welcome)

            // BETA: closed-beta thank-you. Remove per BETA_ROLLBACK.md before release.
            add(OnboardingPage.BetaThankYou)

            // Notifications (skip if already granted, unless showAllSteps)
            if (showAllSteps || !PermissionUtils.areNotificationsEnabled(context)) {
                add(OnboardingPage.Notifications)
            }

            // Screen 4: Exact Alarms (skip if already granted, unless showAllSteps)
            if (showAllSteps || !PermissionUtils.canScheduleExactAlarms(context)) {
                add(OnboardingPage.ExactAlarms)
            }

            // Screen 5: Full Screen Intent (Android 14+ only, skip if already granted)
            if (showAllSteps || (
                    PlatformCapabilities.supportsFullScreenIntentPermission() &&
                        !PermissionUtils.canUseFullScreenIntent(context)
                    )
            ) {
                add(OnboardingPage.FullScreenIntent)
            }

            // Screen: Critical Alerts (iOS only, skip if already authorized)
            if (criticalAlertsPermissionScreen != null &&
                (showAllSteps || !PlatformCapabilities.canScheduleCriticalAlerts())
            ) {
                add(OnboardingPage.CriticalAlerts)
            }

            // Screen: AlarmKit (iOS 26+ only, skip if already authorized)
            if (alarmKitPermissionScreen != null &&
                (showAllSteps || !PlatformCapabilities.isAlarmKitAuthorized())
            ) {
                add(OnboardingPage.AlarmKit)
            }

            // Screen 6: Always show quick start guide
            add(OnboardingPage.QuickStart)

            // Screen 6: Always show completion
            add(OnboardingPage.Complete)
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Handle back button
    // Page 0 (Welcome): Exit app
    // Page > 0: Go to previous page
    PlatformBackHandler {
        if (pagerState.currentPage == 0) {
            // Exit the app (initial onboarding, can't go back)
            onExitApp()
        } else {
            // Go to previous page
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    val pagerStateDescription = stringResource(
        Res.string.accessibility_pager_state,
        pagerState.currentPage + 1,
        pages.size,
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .semantics { stateDescription = pagerStateDescription },
        userScrollEnabled = false, // Disable swiping - only programmatic navigation
    ) { pageIndex ->
        val page = pages[pageIndex]
        val isLastPage = pageIndex == pages.size - 1

        when (page) {
            OnboardingPage.Welcome -> WelcomeScreen(
                onContinue = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
            )

            // BETA: closed-beta thank-you. Remove per BETA_ROLLBACK.md before release.
            OnboardingPage.BetaThankYou -> BetaThankYouScreen(
                onContinue = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.QuickStart -> QuickStartScreen(
                onContinue = {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.Notifications -> notificationPermissionScreen(
                {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.ExactAlarms -> ExactAlarmPermissionScreen(
                onContinue = {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.FullScreenIntent -> FullScreenIntentPermissionScreen(
                onContinue = {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                onBack = {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.AlarmKit -> alarmKitPermissionScreen?.invoke(
                {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.CriticalAlerts -> criticalAlertsPermissionScreen?.invoke(
                {
                    scope.launch {
                        if (!isLastPage) pagerState.animateScrollToPage(pageIndex + 1)
                    }
                },
                {
                    scope.launch {
                        pagerState.animateScrollToPage(pageIndex - 1)
                    }
                },
            )

            OnboardingPage.Complete -> CompletionScreen(
                onComplete = {
                    scope.launch {
                        // Only mark onboarding as complete if this is the initial onboarding
                        // (not a debug/review launch)
                        if (!wasAlreadyCompleted) {
                            completeOnboarding()
                        }
                        // Navigate to main app or close overlay
                        onComplete()
                    }
                },
                onNavigateToCamera = {
                    scope.launch {
                        // Mark onboarding complete before navigating to camera
                        if (!wasAlreadyCompleted) {
                            completeOnboarding()
                        }
                        onNavigateToCamera()
                    }
                },
                onNavigateToManualAdd = {
                    scope.launch {
                        // Mark onboarding complete before navigating to manual add
                        if (!wasAlreadyCompleted) {
                            completeOnboarding()
                        }
                        onNavigateToManualAdd()
                    }
                },
                onNavigateToImport = {
                    scope.launch {
                        // Mark onboarding complete before navigating to import
                        if (!wasAlreadyCompleted) {
                            completeOnboarding()
                        }
                        onNavigateToImport()
                    }
                },
            )
        }
    }
}

private enum class OnboardingPage {
    Welcome,
    // BETA: Remove per BETA_ROLLBACK.md before release.
    BetaThankYou,
    QuickStart,
    Notifications,
    ExactAlarms,
    FullScreenIntent,
    AlarmKit,
    CriticalAlerts,
    Complete,
}
