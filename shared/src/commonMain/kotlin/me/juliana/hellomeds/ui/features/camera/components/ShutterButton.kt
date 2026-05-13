// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_scan
import me.juliana.hellomeds.shared.outline_scan_24px
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shutter button with animated border ring.
 * 72dp white circle with scan icon, animated pulsating blue border when text detected.
 * Shared across Android and iOS camera screens.
 */
@Composable
fun ShutterButton(
    shutterBorderWidth: Float,
    shutterBorderColor: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = lerp(Color.White, Color(0xFF2196F3), shutterBorderColor)
    val borderAlpha = androidx.compose.ui.util.lerp(0.5f, 0.9f, shutterBorderColor)

    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp + shutterBorderWidth.dp * 2)
                .border(
                    width = shutterBorderWidth.dp,
                    color = borderColor.copy(alpha = borderAlpha),
                    shape = CircleShape,
                ),
        )

        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .background(Color.White, CircleShape),
        ) {
            Icon(
                painter = painterResource(Res.drawable.outline_scan_24px),
                contentDescription = stringResource(Res.string.camera_detection_scan),
                tint = Color.Black,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
