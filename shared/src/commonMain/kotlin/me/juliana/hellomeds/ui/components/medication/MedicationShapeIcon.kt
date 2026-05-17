// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.components.medication

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.launch
import me.juliana.hellomeds.data.model.enums.MedicationBackgroundShape
import me.juliana.hellomeds.data.model.enums.MedicationForegroundShape
import me.juliana.hellomeds.shared.Res
import me.juliana.hellomeds.shared.content_description_medication_icon
import me.juliana.hellomeds.shared.shape_blister_pack_grid
import me.juliana.hellomeds.shared.shape_blister_pack_grid_1
import me.juliana.hellomeds.shared.shape_blister_pack_grid_2
import me.juliana.hellomeds.shared.shape_capsule_pill
import me.juliana.hellomeds.shared.shape_capsule_pill_1
import me.juliana.hellomeds.shared.shape_capsule_pill_2
import me.juliana.hellomeds.shared.shape_contraceptive_ring
import me.juliana.hellomeds.shared.shape_diamond_pill
import me.juliana.hellomeds.shared.shape_diamond_pill_notch
import me.juliana.hellomeds.shared.shape_diamond_pill_tall
import me.juliana.hellomeds.shared.shape_diamond_pill_tall_notch
import me.juliana.hellomeds.shared.shape_dispenser
import me.juliana.hellomeds.shared.shape_dispenser_1
import me.juliana.hellomeds.shared.shape_dispenser_2
import me.juliana.hellomeds.shared.shape_double_circle_pill
import me.juliana.hellomeds.shared.shape_dropper_bottle
import me.juliana.hellomeds.shared.shape_effervescent_tablet
import me.juliana.hellomeds.shared.shape_effervescent_tablet_1
import me.juliana.hellomeds.shared.shape_effervescent_tablet_2
import me.juliana.hellomeds.shared.shape_inhaler
import me.juliana.hellomeds.shared.shape_injection_pen
import me.juliana.hellomeds.shared.shape_injection_pen_1
import me.juliana.hellomeds.shared.shape_injection_pen_2
import me.juliana.hellomeds.shared.shape_iud_t_shape
import me.juliana.hellomeds.shared.shape_iud_t_shape_1
import me.juliana.hellomeds.shared.shape_iud_t_shape_2
import me.juliana.hellomeds.shared.shape_jar
import me.juliana.hellomeds.shared.shape_jar_1
import me.juliana.hellomeds.shared.shape_jar_2
import me.juliana.hellomeds.shared.shape_liquid
import me.juliana.hellomeds.shared.shape_measurement_cup
import me.juliana.hellomeds.shared.shape_oval_pill
import me.juliana.hellomeds.shared.shape_oval_pill_notch
import me.juliana.hellomeds.shared.shape_patch
import me.juliana.hellomeds.shared.shape_patch_1
import me.juliana.hellomeds.shared.shape_patch_2
import me.juliana.hellomeds.shared.shape_pentagon_pill
import me.juliana.hellomeds.shared.shape_pill_bottle
import me.juliana.hellomeds.shared.shape_pill_bottle_1
import me.juliana.hellomeds.shared.shape_pill_bottle_2
import me.juliana.hellomeds.shared.shape_powder_sachet
import me.juliana.hellomeds.shared.shape_rectangle_pill
import me.juliana.hellomeds.shared.shape_rectangle_pill_notch
import me.juliana.hellomeds.shared.shape_round_pill
import me.juliana.hellomeds.shared.shape_round_pill_notch
import me.juliana.hellomeds.shared.shape_square_pill
import me.juliana.hellomeds.shared.shape_suppository
import me.juliana.hellomeds.shared.shape_syringe
import me.juliana.hellomeds.shared.shape_syringe_1
import me.juliana.hellomeds.shared.shape_syringe_2
import me.juliana.hellomeds.shared.shape_tablet_pill
import me.juliana.hellomeds.shared.shape_tablet_pill_notch
import me.juliana.hellomeds.shared.shape_triangle_pill
import me.juliana.hellomeds.shared.shape_tube
import me.juliana.hellomeds.ui.compat.MaterialShapes
import me.juliana.hellomeds.ui.compat.toComposePath
import me.juliana.hellomeds.ui.theme.MedicationColor
import me.juliana.hellomeds.ui.util.toBackgroundColor
import me.juliana.hellomeds.ui.util.toForegroundColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal fun MedicationForegroundShape.toNameRes(): StringResource = when (this) {
    // Common pills
    MedicationForegroundShape.CAPSULE_PILL -> Res.string.shape_capsule_pill
    MedicationForegroundShape.TABLET_PILL -> Res.string.shape_tablet_pill
    MedicationForegroundShape.TABLET_PILL_NOTCH -> Res.string.shape_tablet_pill_notch
    MedicationForegroundShape.ROUND_PILL -> Res.string.shape_round_pill
    MedicationForegroundShape.ROUND_PILL_NOTCH -> Res.string.shape_round_pill_notch
    MedicationForegroundShape.OVAL_PILL -> Res.string.shape_oval_pill
    MedicationForegroundShape.OVAL_PILL_NOTCH -> Res.string.shape_oval_pill_notch
    MedicationForegroundShape.RECTANGLE_PILL -> Res.string.shape_rectangle_pill
    MedicationForegroundShape.RECTANGLE_PILL_NOTCH -> Res.string.shape_rectangle_pill_notch
    MedicationForegroundShape.SQUARE_PILL -> Res.string.shape_square_pill
    MedicationForegroundShape.DIAMOND_PILL -> Res.string.shape_diamond_pill
    MedicationForegroundShape.DIAMOND_PILL_NOTCH -> Res.string.shape_diamond_pill_notch
    MedicationForegroundShape.DIAMOND_PILL_TALL -> Res.string.shape_diamond_pill_tall
    MedicationForegroundShape.DIAMOND_PILL_TALL_NOTCH -> Res.string.shape_diamond_pill_tall_notch
    MedicationForegroundShape.PENTAGON_PILL -> Res.string.shape_pentagon_pill
    MedicationForegroundShape.TRIANGLE_PILL -> Res.string.shape_triangle_pill
    MedicationForegroundShape.DOUBLE_CIRCLE_PILL -> Res.string.shape_double_circle_pill
    MedicationForegroundShape.EFFERVESCENT_TABLET -> Res.string.shape_effervescent_tablet
    // Containers & packaging
    MedicationForegroundShape.PILL_BOTTLE -> Res.string.shape_pill_bottle
    MedicationForegroundShape.JAR -> Res.string.shape_jar
    MedicationForegroundShape.BLISTER_PACK_GRID -> Res.string.shape_blister_pack_grid
    MedicationForegroundShape.TUBE -> Res.string.shape_tube
    MedicationForegroundShape.DROPPER_BOTTLE -> Res.string.shape_dropper_bottle
    MedicationForegroundShape.DISPENSER -> Res.string.shape_dispenser
    // Liquids & powders
    MedicationForegroundShape.LIQUID -> Res.string.shape_liquid
    MedicationForegroundShape.MEASUREMENT_CUP -> Res.string.shape_measurement_cup
    MedicationForegroundShape.POWDER_SACHET -> Res.string.shape_powder_sachet
    // Delivery devices & equipment
    MedicationForegroundShape.SYRINGE -> Res.string.shape_syringe
    MedicationForegroundShape.INJECTION_PEN -> Res.string.shape_injection_pen
    MedicationForegroundShape.INHALER -> Res.string.shape_inhaler
    MedicationForegroundShape.PATCH -> Res.string.shape_patch
    MedicationForegroundShape.SUPPOSITORY -> Res.string.shape_suppository
    MedicationForegroundShape.CONTRACEPTIVE_RING -> Res.string.shape_contraceptive_ring
    MedicationForegroundShape.IUD_T_SHAPE -> Res.string.shape_iud_t_shape
}

/**
 * White complement color for duo-tone shapes and backgrounds.
 * In light mode: pure white. In dark mode: a soft dark-mode white equivalent.
 */
private val WhiteComplementLight = Color(0xFFFFFFFF)
private val WhiteComplementDark = Color(0xFFE0E0E0)

private val WhiteBackgroundLight = Color(0xFFFFFFFF)
private val WhiteBackgroundDark = Color(0xFF2A2A2A)

/**
 * Drawable resources for a foreground shape. Single-color shapes have only [primary];
 * duo-tone shapes also have [secondary] (rendered with the white complement).
 */
internal data class ShapeDrawables(
    val primary: DrawableResource,
    val secondary: DrawableResource? = null,
)

internal fun MedicationForegroundShape.toDrawables(): ShapeDrawables = when (this) {
    // Common pills
    MedicationForegroundShape.CAPSULE_PILL -> ShapeDrawables(
        Res.drawable.shape_capsule_pill_1,
        Res.drawable.shape_capsule_pill_2,
    )
    MedicationForegroundShape.TABLET_PILL -> ShapeDrawables(Res.drawable.shape_tablet_pill)
    MedicationForegroundShape.TABLET_PILL_NOTCH -> ShapeDrawables(Res.drawable.shape_tablet_pill_notch)
    MedicationForegroundShape.ROUND_PILL -> ShapeDrawables(Res.drawable.shape_round_pill)
    MedicationForegroundShape.ROUND_PILL_NOTCH -> ShapeDrawables(Res.drawable.shape_round_pill_notch)
    MedicationForegroundShape.OVAL_PILL -> ShapeDrawables(Res.drawable.shape_oval_pill)
    MedicationForegroundShape.OVAL_PILL_NOTCH -> ShapeDrawables(Res.drawable.shape_oval_pill_notch)
    MedicationForegroundShape.RECTANGLE_PILL -> ShapeDrawables(Res.drawable.shape_rectangle_pill)
    MedicationForegroundShape.RECTANGLE_PILL_NOTCH -> ShapeDrawables(Res.drawable.shape_rectangle_pill_notch)
    MedicationForegroundShape.SQUARE_PILL -> ShapeDrawables(Res.drawable.shape_square_pill)
    MedicationForegroundShape.DIAMOND_PILL -> ShapeDrawables(Res.drawable.shape_diamond_pill)
    MedicationForegroundShape.DIAMOND_PILL_NOTCH -> ShapeDrawables(Res.drawable.shape_diamond_pill_notch)
    MedicationForegroundShape.DIAMOND_PILL_TALL -> ShapeDrawables(Res.drawable.shape_diamond_pill_tall)
    MedicationForegroundShape.DIAMOND_PILL_TALL_NOTCH -> ShapeDrawables(Res.drawable.shape_diamond_pill_tall_notch)
    MedicationForegroundShape.PENTAGON_PILL -> ShapeDrawables(Res.drawable.shape_pentagon_pill)
    MedicationForegroundShape.TRIANGLE_PILL -> ShapeDrawables(Res.drawable.shape_triangle_pill)
    MedicationForegroundShape.DOUBLE_CIRCLE_PILL -> ShapeDrawables(Res.drawable.shape_double_circle_pill)
    MedicationForegroundShape.EFFERVESCENT_TABLET -> ShapeDrawables(
        Res.drawable.shape_effervescent_tablet_1,
        Res.drawable.shape_effervescent_tablet_2,
    )
    // Containers & packaging
    MedicationForegroundShape.PILL_BOTTLE -> ShapeDrawables(
        Res.drawable.shape_pill_bottle_1,
        Res.drawable.shape_pill_bottle_2,
    )
    MedicationForegroundShape.JAR -> ShapeDrawables(Res.drawable.shape_jar_1, Res.drawable.shape_jar_2)
    MedicationForegroundShape.BLISTER_PACK_GRID -> ShapeDrawables(
        Res.drawable.shape_blister_pack_grid_1,
        Res.drawable.shape_blister_pack_grid_2,
    )
    MedicationForegroundShape.TUBE -> ShapeDrawables(Res.drawable.shape_tube)
    MedicationForegroundShape.DROPPER_BOTTLE -> ShapeDrawables(Res.drawable.shape_dropper_bottle)
    MedicationForegroundShape.DISPENSER -> ShapeDrawables(
        Res.drawable.shape_dispenser_1,
        Res.drawable.shape_dispenser_2,
    )
    // Liquids & powders
    MedicationForegroundShape.LIQUID -> ShapeDrawables(Res.drawable.shape_liquid)
    MedicationForegroundShape.MEASUREMENT_CUP -> ShapeDrawables(Res.drawable.shape_measurement_cup)
    MedicationForegroundShape.POWDER_SACHET -> ShapeDrawables(Res.drawable.shape_powder_sachet)
    // Delivery devices & equipment
    MedicationForegroundShape.SYRINGE -> ShapeDrawables(Res.drawable.shape_syringe_1, Res.drawable.shape_syringe_2)
    MedicationForegroundShape.INJECTION_PEN -> ShapeDrawables(
        Res.drawable.shape_injection_pen_1,
        Res.drawable.shape_injection_pen_2,
    )
    MedicationForegroundShape.INHALER -> ShapeDrawables(Res.drawable.shape_inhaler)
    MedicationForegroundShape.PATCH -> ShapeDrawables(Res.drawable.shape_patch_1, Res.drawable.shape_patch_2)
    MedicationForegroundShape.SUPPOSITORY -> ShapeDrawables(Res.drawable.shape_suppository)
    MedicationForegroundShape.CONTRACEPTIVE_RING -> ShapeDrawables(Res.drawable.shape_contraceptive_ring)
    MedicationForegroundShape.IUD_T_SHAPE -> ShapeDrawables(
        Res.drawable.shape_iud_t_shape_1,
        Res.drawable.shape_iud_t_shape_2,
    )
}

/**
 * Single-color medication shape icon. Duo-tone shapes get an automatic white complement for the
 * second part. Background is white (light) / dark-mode equivalent (dark) when a color is set.
 */
@Composable
fun MedicationShapeIcon(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    modifier: Modifier = Modifier,
    color1: MedicationColor? = null,
    size: Dp = 48.dp,
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = color1?.toBackgroundColor() ?: MaterialTheme.colorScheme.primaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.onSurface
    val fgColor1 = color1?.toForegroundColor() ?: surfaceContainer
    val fgColor2 = if (color1 != null) {
        if (isDark) WhiteComplementDark else WhiteComplementLight
    } else {
        surfaceContainer.copy(alpha = 0.5f)
    }

    val shapeDescription = stringResource(
        Res.string.content_description_medication_icon,
        stringResource(foregroundShape.toNameRes()),
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = shapeDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Background shape
        Box(
            modifier = Modifier
                .size(size)
                .clip(backgroundShape.toComposeShape())
                .background(bgColor),
        )

        // Foreground shape(s) — secondary (_2/white) renders below primary (_1)
        val drawables = foregroundShape.toDrawables()
        drawables.secondary?.let { secondary ->
            Icon(
                painter = painterResource(secondary),
                contentDescription = null,
                tint = fgColor2,
                modifier = Modifier.size(size * 0.7f),
            )
        }
        Icon(
            painter = painterResource(drawables.primary),
            contentDescription = null,
            tint = fgColor1,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}

/** Animated version that morphs between two background shapes — used to preview shape changes. */
@Composable
fun AnimatedMorphingMedicationIcon(
    foregroundShape: MedicationForegroundShape,
    fromShape: MedicationBackgroundShape,
    toShape: MedicationBackgroundShape,
    modifier: Modifier = Modifier,
    color1: MedicationColor? = null,
    size: Dp = 64.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morph animation")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "morph progress",
    )

    val startPolygon = remember(fromShape) {
        fromShape.toRoundedPolygon()
    }

    val endPolygon = remember(toShape) {
        toShape.toRoundedPolygon()
    }

    val morph = remember(startPolygon, endPolygon) {
        Morph(startPolygon, endPolygon)
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = color1?.toBackgroundColor() ?: MaterialTheme.colorScheme.primaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.onSurface
    val fgColor1 = color1?.toForegroundColor() ?: surfaceContainer
    val fgColor2 = if (color1 != null) {
        if (isDark) WhiteComplementDark else WhiteComplementLight
    } else {
        surfaceContainer.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Background shape with morph animation
        Box(
            modifier = Modifier
                .size(size)
                .clip(MorphPolygonShape(morph, morphProgress, startPolygon))
                .background(bgColor),
        )

        // Foreground shape(s) — secondary (_2/white) renders below primary (_1)
        val drawables = foregroundShape.toDrawables()
        drawables.secondary?.let { secondary ->
            Icon(
                painter = painterResource(secondary),
                contentDescription = null,
                tint = fgColor2,
                modifier = Modifier.size(size * 0.7f),
            )
        }
        Icon(
            painter = painterResource(drawables.primary),
            contentDescription = null,
            tint = fgColor1,
            modifier = Modifier.size(size * 0.7f),
        )
    }
}

/**
 * Plays a one-shot transition on shape/color changes (vs. [AnimatedMorphingMedicationIcon] which
 * loops continuously).
 */
@Composable
fun TransitioningMedicationIcon(
    foregroundShape: MedicationForegroundShape,
    backgroundShape: MedicationBackgroundShape,
    modifier: Modifier = Modifier,
    color1: MedicationColor? = null,
    size: Dp = 64.dp,
) {
    var currentFromShape by remember { mutableStateOf(backgroundShape) }
    var currentToShape by remember { mutableStateOf(backgroundShape) }

    // Animatable lets us snap progress back to 0 when the source/target shapes change mid-animation.
    val morphProgress = remember { Animatable(1f) }

    var previousForegroundShape by remember { mutableStateOf(foregroundShape) }
    val foregroundScale = remember { Animatable(1f) }
    val foregroundRotation = remember { Animatable(0f) }

    // When background shape changes, update the shapes and restart animation
    LaunchedEffect(backgroundShape) {
        if (backgroundShape != currentToShape) {
            currentFromShape = currentToShape
            currentToShape = backgroundShape
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    // Animate foreground shape changes with spring and rotation (only on shape change)
    LaunchedEffect(foregroundShape) {
        if (foregroundShape != previousForegroundShape) {
            // Shrink out with spring while rotating to -45 degrees (in parallel)
            val shrinkJob = launch {
                foregroundScale.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            val rotate1Job = launch {
                foregroundRotation.animateTo(
                    targetValue = -45f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            // Wait for both animations to complete
            shrinkJob.join()
            rotate1Job.join()

            // Update the shape while invisible (at scale 0)
            previousForegroundShape = foregroundShape
            // Snap rotation to +45 degrees (opposite side) while invisible
            foregroundRotation.snapTo(45f)

            // Grow back in with bouncy spring while rotating back to 0 degrees
            val growJob = launch {
                foregroundScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            val rotate2Job = launch {
                foregroundRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            // Wait for both animations to complete
            growJob.join()
            rotate2Job.join()
        }
    }

    val startPolygon = remember(currentFromShape) {
        currentFromShape.toRoundedPolygon()
    }

    val endPolygon = remember(currentToShape) {
        currentToShape.toRoundedPolygon()
    }

    val morph = remember(startPolygon, endPolygon) {
        Morph(startPolygon, endPolygon)
    }

    // Animate colors with linear tween for smooth transitions
    val isDark = isSystemInDarkTheme()
    val bgColor = color1?.toBackgroundColor() ?: MaterialTheme.colorScheme.primaryContainer
    val surfaceContainer = MaterialTheme.colorScheme.onSurface
    val fgColor1 = color1?.toForegroundColor() ?: surfaceContainer
    val fgColor2 = if (color1 != null) {
        if (isDark) WhiteComplementDark else WhiteComplementLight
    } else {
        surfaceContainer.copy(alpha = 0.5f)
    }

    val animatedBgColor by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(durationMillis = 300),
        label = "background color",
    )
    val animatedFgColor1 by animateColorAsState(
        targetValue = fgColor1,
        animationSpec = tween(durationMillis = 300),
        label = "foreground color 1",
    )
    val animatedFgColor2 by animateColorAsState(
        targetValue = fgColor2,
        animationSpec = tween(durationMillis = 300),
        label = "foreground color 2",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Background shape with morph animation
        Box(
            modifier = Modifier
                .size(size)
                .clip(MorphPolygonShape(morph, morphProgress.value, startPolygon))
                .background(animatedBgColor),
        )

        // Foreground shape(s) - use previousForegroundShape so it only changes at scale 0
        val drawables = previousForegroundShape.toDrawables()
        val fgModifier = Modifier
            .size(size * 0.7f)
            .graphicsLayer {
                scaleX = foregroundScale.value
                scaleY = foregroundScale.value
                rotationZ = foregroundRotation.value
            }
        drawables.secondary?.let { secondary ->
            Icon(
                painter = painterResource(secondary),
                contentDescription = null,
                tint = animatedFgColor2,
                modifier = fgModifier,
            )
        }
        Icon(
            painter = painterResource(drawables.primary),
            contentDescription = null,
            tint = animatedFgColor1,
            modifier = fgModifier,
        )
    }
}

internal class RoundedPolygonShape(
    private val polygon: RoundedPolygon,
) : Shape {
    private var path = Path()
    private var cachedSize: Size = Size.Unspecified

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size == cachedSize) return Outline.Generic(path)
        path.rewind()
        path = polygon.toComposePath()
        // Match M3's toShape() approach: scale to fill, then center
        val scaleMatrix = Matrix().apply { scale(x = size.width, y = size.height) }
        path.transform(scaleMatrix)
        val pathBounds = path.getBounds()
        path.translate(
            Offset(
                size.width / 2f - pathBounds.center.x,
                size.height / 2f - pathBounds.center.y,
            ),
        )
        cachedSize = size
        return Outline.Generic(path)
    }
}

private class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val startPolygon: RoundedPolygon,
) : Shape {
    private var path = Path()
    private var cachedSize: Size = Size.Unspecified
    private var cachedProgress: Float = Float.NaN

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size == cachedSize && progress == cachedProgress) return Outline.Generic(path)
        path.rewind()
        path = morph.toComposePath(progress = progress)
        val scaleMatrix = Matrix().apply { scale(x = size.width, y = size.height) }
        path.transform(scaleMatrix)
        val pathBounds = path.getBounds()
        path.translate(
            Offset(
                size.width / 2f - pathBounds.center.x,
                size.height / 2f - pathBounds.center.y,
            ),
        )
        cachedSize = size
        cachedProgress = progress
        return Outline.Generic(path)
    }
}

private fun MedicationBackgroundShape.toRoundedPolygon() = when (this) {
    MedicationBackgroundShape.CIRCLE -> MaterialShapes.Circle
    MedicationBackgroundShape.SQUARE -> MaterialShapes.Square
    MedicationBackgroundShape.SLANTED -> MaterialShapes.Slanted
    MedicationBackgroundShape.ARCH -> MaterialShapes.Arch
    MedicationBackgroundShape.PILL -> MaterialShapes.Pill
    MedicationBackgroundShape.DIAMOND -> MaterialShapes.Diamond
    MedicationBackgroundShape.CLAMSHELL -> MaterialShapes.ClamShell
    MedicationBackgroundShape.PENTAGON -> MaterialShapes.Pentagon
    MedicationBackgroundShape.GEM -> MaterialShapes.Gem
    MedicationBackgroundShape.SUNNY -> MaterialShapes.Sunny
    MedicationBackgroundShape.VERY_SUNNY -> MaterialShapes.VerySunny
    MedicationBackgroundShape.FOUR_SIDED_COOKIE -> MaterialShapes.Cookie4Sided
    MedicationBackgroundShape.SEVEN_SIDED_COOKIE -> MaterialShapes.Cookie7Sided
    MedicationBackgroundShape.TWELVE_SIDED_COOKIE -> MaterialShapes.Cookie12Sided
    MedicationBackgroundShape.FOUR_LEAF_CLOVER -> MaterialShapes.Clover4Leaf
    MedicationBackgroundShape.EIGHT_LEAF_CLOVER -> MaterialShapes.Clover8Leaf
    MedicationBackgroundShape.SOFT_BURST -> MaterialShapes.SoftBurst
    MedicationBackgroundShape.SOFT_BOOM -> MaterialShapes.SoftBoom
    MedicationBackgroundShape.FLOWER -> MaterialShapes.Flower
    MedicationBackgroundShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
    MedicationBackgroundShape.BUN -> MaterialShapes.Bun
}

private fun MedicationBackgroundShape.toComposeShape() = RoundedPolygonShape(toRoundedPolygon())
