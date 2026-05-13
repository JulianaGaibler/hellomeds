// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.camera_detection_instruction_no_object
import me.juliana.hellomeds.shared.camera_detection_instruction_no_text
import me.juliana.hellomeds.shared.camera_detection_instruction_ready
import org.jetbrains.compose.resources.stringResource

/**
 * Semi-transparent pill showing instruction text based on word count.
 * Shared across Android and iOS camera screens.
 */
@Composable
fun InstructionPill(wordCount: Int, modifier: Modifier = Modifier) {
    val instructionText = when {
        wordCount >= 4 -> stringResource(Res.string.camera_detection_instruction_ready)
        wordCount > 0 -> stringResource(Res.string.camera_detection_instruction_no_text)
        else -> stringResource(Res.string.camera_detection_instruction_no_object)
    }

    Text(
        text = instructionText,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.6f),
                RoundedCornerShape(64.dp),
            )
            .padding(horizontal = 32.dp, vertical = 12.dp),
    )
}
