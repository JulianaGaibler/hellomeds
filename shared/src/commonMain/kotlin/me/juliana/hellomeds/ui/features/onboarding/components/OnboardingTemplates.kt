// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_back
import me.juliana.hellomeds.shared.onboarding_continue
import me.juliana.hellomeds.shared.permission_granted
import org.jetbrains.compose.resources.stringResource

/**
 * TEMPLATE 1: Splash/Intro Screens
 *
 * Used for: "Get Started" (Screen 1) and "All Set!" (Screen 6)
 *
 * Features:
 * - Large title text positioned at bottom-left
 * - Optional subtitle for additional context
 * - Primary button (always shown)
 * - Optional secondary button
 * - Background shapes from onboardingBackgroundShapes()
 */
@Composable
fun SplashOnboardingScreen(
    title: String,
    subTitle: String? = null,
    onPrimaryButtonClick: () -> Unit,
    primaryButtonText: String,
    secondaryButtonText: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .onboardingBackgroundShapes(tertiary, primary)
            .padding(32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Title and Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subTitle != null) {
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Optional Secondary Button (e.g., "Later")
                if (secondaryButtonText != null && onSecondaryButtonClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryButtonClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = SolidColor(MaterialTheme.colorScheme.outline),
                        ),
                    ) {
                        Text(secondaryButtonText)
                    }
                } else {
                    Spacer(Modifier.width(1.dp)) // Spacer to keep Primary button on right if alone
                }

                // Primary Button
                FilledTonalButton(onClick = onPrimaryButtonClick) {
                    Text(primaryButtonText)
                }
            }
        }
    }
}

/**
 * TEMPLATE 2: Instructional List Screens
 *
 * Used for: "Quick Start" (Screen 2)
 *
 * Features:
 * - Icon at top
 * - Large title
 * - Numbered list of steps with title and description
 * - Back button (optional)
 * - Continue button
 * - Scrollable content
 */
@Composable
fun StepsOnboardingScreen(
    icon: Painter,
    title: String,
    steps: List<Pair<String, String>>, // Pair<Title, Body>
    onContinueClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
                FilledTonalButton(onClick = onContinueClick) {
                    Text(stringResource(Res.string.onboarding_continue))
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 32.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Icon
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Headline
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Steps
            steps.forEachIndexed { index, (stepTitle, stepBody) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Large number
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp),
                    )
                    // Title and body
                    Column {
                        Text(
                            text = stepTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stepBody,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * TEMPLATE 3: Permission Request Screens
 *
 * Used for: Notifications (Screen 3), Exact Alarms (Screen 4), DND (Screen 5)
 *
 * Features:
 * - Icon at top
 * - Large title
 * - Description text
 * - Primary action button (grant permission)
 * - Optional secondary action (skip/use alternative)
 * - Footer info text with technical details
 * - Back button (optional)
 * - Continue button
 * - Shows checkmark when permission granted
 */
@Composable
fun PermissionOnboardingScreen(
    icon: Painter,
    title: String,
    description: String,
    primaryButtonText: String,
    onPrimaryButtonClick: () -> Unit,
    isPrimaryActionCompleted: Boolean = false, // Show checkmark when permission granted
    secondaryButtonText: String? = null,
    onSecondaryButtonClick: (() -> Unit)? = null,
    footerInfo: String,
    onBackClick: (() -> Unit)? = null,
    onContinueClick: () -> Unit,
    isContinueEnabled: Boolean = true, // Enable continue button (disabled until permission granted or skipped)
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        bottomBar = {
            // Navigation Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.content_description_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                FilledTonalButton(
                    onClick = onContinueClick,
                    enabled = isContinueEnabled,
                ) {
                    Text(stringResource(Res.string.onboarding_continue))
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 32.dp, vertical = 32.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Main Permission Button
            FilledTonalButton(
                onClick = onPrimaryButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isPrimaryActionCompleted,
            ) {
                if (isPrimaryActionCompleted) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.permission_granted))
                } else {
                    Text(primaryButtonText)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Action (hidden when permission is granted)
            if (secondaryButtonText != null && onSecondaryButtonClick != null && !isPrimaryActionCompleted) {
                TextButton(
                    onClick = onSecondaryButtonClick,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = secondaryButtonText,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Push footer down

            Spacer(modifier = Modifier.height(32.dp))

            // Technical Footer
            Text(
                text = footerInfo,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SplashOnboardingScreenPreview() {
    MaterialTheme {
        SplashOnboardingScreen(
            title = "Say HelloMeds",
            subTitle = "Your personal medication tracker",
            primaryButtonText = "Get Started",
            onPrimaryButtonClick = {},
            secondaryButtonText = "Later",
            onSecondaryButtonClick = {},
        )
    }
}
