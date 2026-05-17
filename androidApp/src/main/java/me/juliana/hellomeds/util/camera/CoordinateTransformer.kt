// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.util.camera

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log

/**
 * Handles coordinate transformations between camera bitmap space and screen space.
 *
 * This class accounts for:
 * - Image rotation (90°, 180°, 270°) - ML Kit returns coordinates in rotated space
 * - PreviewView FILL_CENTER scaling (crops sides/top/bottom to fill screen)
 * - Aspect ratio differences between bitmap and screen
 *
 * COORDINATE SPACES:
 * - Bitmap space: Camera bitmap in sensor orientation (e.g., 640×480 landscape)
 * - ML Kit space: Coordinates after rotation applied (e.g., 480×640 for 90° rotation)
 * - Screen space: The UI canvas coordinates (e.g., 1080×2400)
 *
 * ML Kit receives rotation parameter and returns coordinates in the rotated space.
 * For 90° rotation: 640×480 bitmap → ML Kit returns coordinates in 480×640 space.
 *
 * IMPORTANT: Create a new instance when screen size, bitmap dimensions, or rotation change.
 *
 * @param bitmapWidth Width of the bitmap in SENSOR orientation
 * @param bitmapHeight Height of the bitmap in SENSOR orientation
 * @param screenWidth Width of the screen/canvas in pixels
 * @param screenHeight Height of the screen/canvas in pixels
 * @param rotation Camera rotation (0, 90, 180, 270) passed to ML Kit
 */
class CoordinateTransformer(
    val bitmapWidth: Int,
    val bitmapHeight: Int,
    val screenWidth: Float,
    val screenHeight: Float,
    val rotation: Int = 0,
) {
    companion object {
        private const val TAG = "CoordinateTransformer"
    }

    // ML Kit returns coordinates in rotated space.
    val rotatedWidth: Int = if (rotation == 90 || rotation == 270) bitmapHeight else bitmapWidth
    val rotatedHeight: Int = if (rotation == 90 || rotation == 270) bitmapWidth else bitmapHeight

    // FILL_CENTER scaling: use max so the bitmap fills the screen.
    val scaleX: Float = screenWidth / rotatedWidth
    val scaleY: Float = screenHeight / rotatedHeight
    val scale: Float = maxOf(scaleX, scaleY)

    val scaledBitmapWidth: Float = rotatedWidth * scale
    val scaledBitmapHeight: Float = rotatedHeight * scale
    val offsetX: Float = (screenWidth - scaledBitmapWidth) / 2f
    val offsetY: Float = (screenHeight - scaledBitmapHeight) / 2f

    // Visible bitmap bounds after FILL_CENTER crop, in rotated (ML Kit) space.
    val visibleBitmapLeft: Int = if (offsetX < 0) (-offsetX / scale).toInt() else 0
    val visibleBitmapTop: Int = if (offsetY < 0) (-offsetY / scale).toInt() else 0
    val visibleBitmapRight: Int =
        if (offsetX < 0) (rotatedWidth - (-offsetX / scale)).toInt() else rotatedWidth
    val visibleBitmapBottom: Int =
        if (offsetY < 0) (rotatedHeight - (-offsetY / scale)).toInt() else rotatedHeight

    init {
        Log.d(TAG, "Created transformer:")
        Log.d(TAG, "  Bitmap (sensor): $bitmapWidth×$bitmapHeight")
        Log.d(TAG, "  Rotation: $rotation°")
        Log.d(TAG, "  ML Kit space: $rotatedWidth×$rotatedHeight")
        Log.d(TAG, "  Screen: $screenWidth×$screenHeight")
        Log.d(TAG, "  Scale: $scale (scaleX=$scaleX, scaleY=$scaleY)")
        Log.d(TAG, "  Offset: ($offsetX, $offsetY)")
        Log.d(
            TAG,
            "  Visible area: [$visibleBitmapLeft,$visibleBitmapTop - $visibleBitmapRight,$visibleBitmapBottom]",
        )
    }

    /**
     * Convert a point from bitmap coordinates to screen coordinates.
     * Formula: screen = bitmap * scale + offset
     */
    fun bitmapToScreen(bitmapX: Float, bitmapY: Float): Pair<Float, Float> {
        val screenX = bitmapX * scale + offsetX
        val screenY = bitmapY * scale + offsetY
        return Pair(screenX, screenY)
    }

    /**
     * Convert a rectangle from bitmap coordinates to screen coordinates.
     */
    fun bitmapRectToScreen(rect: Rect): RectF {
        val topLeft = bitmapToScreen(rect.left.toFloat(), rect.top.toFloat())
        val bottomRight = bitmapToScreen(rect.right.toFloat(), rect.bottom.toFloat())
        return RectF(topLeft.first, topLeft.second, bottomRight.first, bottomRight.second)
    }

    /**
     * Convert a point from screen coordinates to bitmap coordinates.
     * Formula: bitmap = (screen - offset) / scale
     */
    fun screenToBitmap(screenX: Float, screenY: Float): Pair<Float, Float> {
        val bitmapX = (screenX - offsetX) / scale
        val bitmapY = (screenY - offsetY) / scale
        return Pair(bitmapX, bitmapY)
    }

    /**
     * Convert a rectangle from screen coordinates to ML Kit rotated space.
     * Returns coordinates in the same space that ML Kit uses (after rotation applied).
     * Use this for displaying bounding boxes from ML Kit.
     */
    fun screenRectToBitmap(left: Float, top: Float, right: Float, bottom: Float): Rect {
        val topLeft = screenToBitmap(left, top)
        val bottomRight = screenToBitmap(right, bottom)

        // Clamp to rotated dimensions (ML Kit space)
        val bitmapLeft = topLeft.first.toInt().coerceIn(0, rotatedWidth)
        val bitmapTop = topLeft.second.toInt().coerceIn(0, rotatedHeight)
        val bitmapRight = bottomRight.first.toInt().coerceIn(0, rotatedWidth)
        val bitmapBottom = bottomRight.second.toInt().coerceIn(0, rotatedHeight)

        return Rect(bitmapLeft, bitmapTop, bitmapRight, bitmapBottom)
    }

    /**
     * Convert a rectangle from screen coordinates to SENSOR bitmap space.
     * Returns coordinates suitable for cropping the actual sensor bitmap.
     * This applies reverse rotation to get back to sensor coordinates.
     */
    fun screenRectToSensorBitmap(left: Float, top: Float, right: Float, bottom: Float): Rect {
        val rotatedRect = screenRectToBitmap(left, top, right, bottom)

        // Inverse rotation from ML Kit space back to sensor space.
        return when (rotation) {
            90 -> Rect(
                rotatedRect.top,
                rotatedWidth - rotatedRect.right,
                rotatedRect.bottom,
                rotatedWidth - rotatedRect.left,
            )

            180 -> Rect(
                rotatedWidth - rotatedRect.right,
                rotatedHeight - rotatedRect.bottom,
                rotatedWidth - rotatedRect.left,
                rotatedHeight - rotatedRect.top,
            )

            270 -> Rect(
                rotatedHeight - rotatedRect.bottom,
                rotatedRect.left,
                rotatedHeight - rotatedRect.top,
                rotatedRect.right,
            )

            else -> rotatedRect
        }
    }

    /**
     * Check if a bitmap rectangle is mostly within the visible screen area.
     *
     * @param bitmapRect Rectangle in bitmap coordinate space
     * @param threshold Minimum fraction of the rectangle that must be visible (0.0 to 1.0)
     * @return true if at least `threshold` fraction of the rectangle is visible on screen
     */
    fun isInVisibleArea(bitmapRect: Rect, threshold: Float = 0.5f): Boolean {
        val intersectLeft = maxOf(bitmapRect.left, visibleBitmapLeft)
        val intersectTop = maxOf(bitmapRect.top, visibleBitmapTop)
        val intersectRight = minOf(bitmapRect.right, visibleBitmapRight)
        val intersectBottom = minOf(bitmapRect.bottom, visibleBitmapBottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return false
        }

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val totalArea = bitmapRect.width() * bitmapRect.height()

        val visibleFraction = intersectionArea.toFloat() / totalArea.toFloat()

        Log.d(TAG, "Visible fraction: $visibleFraction (threshold: $threshold)")
        return visibleFraction >= threshold
    }

    /**
     * Get the visible portion of a bitmap rectangle (intersection with visible area).
     * Returns null if the rectangle is entirely outside the visible area.
     */
    fun getVisiblePortion(bitmapRect: Rect): Rect? {
        val intersectLeft = maxOf(bitmapRect.left, visibleBitmapLeft)
        val intersectTop = maxOf(bitmapRect.top, visibleBitmapTop)
        val intersectRight = minOf(bitmapRect.right, visibleBitmapRight)
        val intersectBottom = minOf(bitmapRect.bottom, visibleBitmapBottom)

        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return null
        }

        return Rect(intersectLeft, intersectTop, intersectRight, intersectBottom)
    }
}
