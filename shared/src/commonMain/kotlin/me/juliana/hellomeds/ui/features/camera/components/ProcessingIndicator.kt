// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_reading
import me.juliana.hellomeds.ui.compat.LoadingIndicator
import org.jetbrains.compose.resources.stringResource

/**
 * Processing indicator with M3 Expressive loading spinner and "Reading..." text.
 * Uses the real M3 LoadingIndicator on Android, CircularProgressIndicator fallback on iOS.
 */
@Composable
fun ProcessingIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LoadingIndicator(
            modifier = Modifier.size(64.dp),
            color = Color.White,
        )
        Text(
            text = stringResource(Res.string.camera_detection_reading),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
