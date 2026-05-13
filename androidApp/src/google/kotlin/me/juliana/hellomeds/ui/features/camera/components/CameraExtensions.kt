// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.ui.features.camera.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

private const val TAG = "CameraExtensions"

/**
 * Convert ImageProxy to Bitmap with proper YUV→RGB conversion.
 * Returns bitmap in SENSOR orientation (no rotation applied).
 * Rotation should be handled via coordinate transformations, not bitmap rotation.
 *
 * @return Bitmap in sensor orientation (typically 640×480 landscape)
 */
fun ImageProxy.toBitmap(): Bitmap {
    // Convert YUV to RGB
    val yBuffer = planes[0].buffer // Y
    val vuBuffer = planes[2].buffer // VU

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage =
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

    Log.d(TAG, "toBitmap: Created bitmap ${bitmap.width}×${bitmap.height} (sensor orientation)")

    return bitmap
}

/**
 * Helper extension for formatting floats.
 */
fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
