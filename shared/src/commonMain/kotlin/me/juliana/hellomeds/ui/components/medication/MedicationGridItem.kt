// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.ui.theme.ContrastLevel
import me.juliana.hellomeds.ui.theme.LocalContrastLevel
import me.juliana.hellomeds.ui.theme.MedicationColor

@Composable
fun MedicationGridItem(
    medicationName: String,
    typeAndStrength: String,
    scheduleSummary: String,
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    color1: MedicationColor?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isArchived: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (isArchived) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = if (isArchived) {
            BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            null
        },
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            MedicationShapeIcon(
                foregroundShape = foregroundShape,
                backgroundShape = backgroundShape,
                color1 = color1,
                size = 64.dp,
                modifier = Modifier.align(Alignment.Start),
            )

            // In high contrast mode, onSecondaryContainer becomes white (intended for dark containers),
            // but this card uses surfaceContainerLowest (light). Switch to onSurface for readability.
            val isHighContrast = LocalContrastLevel.current == ContrastLevel.High
            val textColor = if (isHighContrast) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            val mutedTextColor = if (isHighContrast) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    text = medicationName,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = typeAndStrength,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = scheduleSummary,
                    style = MaterialTheme.typography.titleSmall,
                    color = mutedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
