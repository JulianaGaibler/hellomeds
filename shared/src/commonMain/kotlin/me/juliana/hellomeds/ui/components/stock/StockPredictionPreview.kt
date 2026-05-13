// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.stock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.stock_prediction_container_days
import me.juliana.hellomeds.shared.stock_prediction_container_empty
import me.juliana.hellomeds.shared.stock_prediction_per_container_days
import me.juliana.hellomeds.shared.stock_prediction_preview_days
import me.juliana.hellomeds.shared.stock_prediction_preview_unavailable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Controls which string variant [StockPredictionPreview] uses.
 *
 * - [TOTAL] — "~14 days remaining" (total stock across all containers)
 * - [THIS_CONTAINER] — "~1 day left in this container" / "This container may be empty"
 * - [PER_CONTAINER] — "~13 days per container" (onboarding: how long one full container lasts)
 */
enum class PredictionContext { TOTAL, THIS_CONTAINER, PER_CONTAINER }

/**
 * Stateless composable that displays a live stock depletion prediction.
 *
 * Shows "~X days remaining" when computable, or a fallback message when
 * the daily consumption rate is zero (no active schedules).
 *
 * Reused in [AdjustStockLevelBottomSheet], the setup flow's EstimatedDosesStep,
 * and (indirectly) the stock detail screen.
 */
@Composable
fun StockPredictionPreview(
    remainingDoses: Double?,
    dailyConsumption: Double,
    context: PredictionContext = PredictionContext.TOTAL,
    modifier: Modifier = Modifier,
) {
    if (remainingDoses == null || remainingDoses.isNaN() || remainingDoses < 0) return

    if (dailyConsumption <= 0.0) {
        val text = stringResource(Res.string.stock_prediction_preview_unavailable)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.semantics { contentDescription = text },
        )
        return
    }

    val daysRemaining = ceil(remainingDoses / dailyConsumption).roundToInt()

    if (daysRemaining <= 0 && context == PredictionContext.THIS_CONTAINER) {
        val text = stringResource(Res.string.stock_prediction_container_empty)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.semantics { contentDescription = text },
        )
        return
    }
    if (daysRemaining <= 0) return

    val text = when (context) {
        PredictionContext.THIS_CONTAINER ->
            pluralStringResource(Res.plurals.stock_prediction_container_days, daysRemaining, daysRemaining)
        PredictionContext.PER_CONTAINER ->
            pluralStringResource(Res.plurals.stock_prediction_per_container_days, daysRemaining, daysRemaining)
        PredictionContext.TOTAL ->
            pluralStringResource(Res.plurals.stock_prediction_preview_days, daysRemaining, daysRemaining)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { contentDescription = text },
    )
}
