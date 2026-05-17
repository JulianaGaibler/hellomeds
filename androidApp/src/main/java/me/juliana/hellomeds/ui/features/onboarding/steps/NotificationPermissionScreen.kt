// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.steps

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import me.juliana.hellomeds.R
import me.juliana.hellomeds.data.preferences.NotificationPreferences
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.onboarding_grant_permission
import me.juliana.hellomeds.shared.onboarding_notifications_description
import me.juliana.hellomeds.shared.onboarding_notifications_disable
import me.juliana.hellomeds.shared.onboarding_notifications_footer
import me.juliana.hellomeds.shared.onboarding_notifications_title
import me.juliana.hellomeds.ui.features.onboarding.components.PermissionOnboardingScreen
import me.juliana.hellomeds.util.PermissionUtils
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun NotificationPermissionScreen(onContinue: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val notifPrefs = koinInject<NotificationPreferences>()

    var isGranted by remember {
        mutableStateOf(PermissionUtils.areNotificationsEnabled(context))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            isGranted = granted
        },
    )

    // Re-check on resume so the Settings fallback path updates the granted state.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isGranted = PermissionUtils.areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PermissionOnboardingScreen(
        icon = painterResource(R.drawable.notifications_active_48px),
        title = stringResource(Res.string.onboarding_notifications_title),
        description = stringResource(Res.string.onboarding_notifications_description),
        primaryButtonText = stringResource(Res.string.onboarding_grant_permission),
        isPrimaryActionCompleted = isGranted,

        onPrimaryButtonClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!isGranted) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // Android 12 and below: the system dialog isn't available, so settings is the only path.
                PermissionUtils.openNotificationSettings(context)
            }
        },

        secondaryButtonText = stringResource(Res.string.onboarding_notifications_disable),
        onSecondaryButtonClick = {
            scope.launch {
                notifPrefs.setNotificationsEnabled(false)
            }
            onContinue()
        },
        footerInfo = stringResource(Res.string.onboarding_notifications_footer),
        onBackClick = onBack,
        onContinueClick = onContinue,
        isContinueEnabled = isGranted,
    )
}
