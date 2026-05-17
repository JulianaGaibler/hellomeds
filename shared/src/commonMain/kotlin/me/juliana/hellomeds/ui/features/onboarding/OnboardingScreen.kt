// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding

// NotificationPermissionScreen is injected via parameter (platform-specific)
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
import me.juliana.hellomeds.ui.compat.collectAsStateWithLifecycle
import me.juliana.hellomeds.ui.compat.platformContext
import me.juliana.hellomeds.ui.features.onboarding.steps.BetaThankYouScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.CompletionScreen
import me.juliana.hellomeds.ui.features.onboarding.steps.DisclaimerScreen
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

    val wasAlreadyCompleted by prefs.onboardingCompleted.collectAsStateWithLifecycle(initial = false)

    suspend fun completeOnboarding() {
        prefs.setOnboardingCompleted(true)
        autoBackupPrefs.setOnboardingCompletedTimestamp(Clock.System.now().toEpochMilliseconds())
    }

    // Skip already-granted permission pages unless showAllSteps is set (debug/review launch).
    val pages = remember(showAllSteps) {
        buildList {
            add(OnboardingPage.Welcome)

            // TODO(BETA_ROLLBACK): closed-beta thank-you page
            add(OnboardingPage.BetaThankYou)

            // Medical disclaimer — required for App Store compliance (iOS only).
            // Google Play accepts the in-app privacy-policy link.
            if (PlatformCapabilities.requiresAppStoreDisclaimer()) {
                add(OnboardingPage.Disclaimer)
            }

            if (showAllSteps || !PermissionUtils.areNotificationsEnabled(context)) {
                add(OnboardingPage.Notifications)
            }

            if (showAllSteps || !PermissionUtils.canScheduleExactAlarms(context)) {
                add(OnboardingPage.ExactAlarms)
            }

            // Full Screen Intent: Android 14+ only.
            if (showAllSteps || (
                    PlatformCapabilities.supportsFullScreenIntentPermission() &&
                        !PermissionUtils.canUseFullScreenIntent(context)
                    )
            ) {
                add(OnboardingPage.FullScreenIntent)
            }

            // Critical Alerts: iOS only.
            if (criticalAlertsPermissionScreen != null &&
                (showAllSteps || !PlatformCapabilities.canScheduleCriticalAlerts())
            ) {
                add(OnboardingPage.CriticalAlerts)
            }

            // AlarmKit: iOS 26+ only.
            if (alarmKitPermissionScreen != null &&
                (showAllSteps || !PlatformCapabilities.isAlarmKitAuthorized())
            ) {
                add(OnboardingPage.AlarmKit)
            }

            add(OnboardingPage.QuickStart)
            add(OnboardingPage.Complete)
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    PlatformBackHandler {
        if (pagerState.currentPage == 0) {
            // Page 0 has nowhere to go back to — exit the app.
            onExitApp()
        } else {
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

            // TODO(BETA_ROLLBACK): closed-beta thank-you page
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

            OnboardingPage.Disclaimer -> DisclaimerScreen(
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

    // TODO(BETA_ROLLBACK)
    BetaThankYou,
    Disclaimer,
    QuickStart,
    Notifications,
    ExactAlarms,
    FullScreenIntent,
    AlarmKit,
    CriticalAlerts,
    Complete,
}
