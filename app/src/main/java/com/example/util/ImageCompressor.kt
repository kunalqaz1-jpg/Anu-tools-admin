package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageCompressor {
    fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        quality: Int = 80
    ): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (max(width, height) > maxDimension) {
                maxDimension.toFloat() / max(width, height)
            } else {
                1.0f
            }

            val matrix = Matrix().apply { postScale(scale, scale) }
            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, true)
            } else {
                originalBitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()
            bytes
        } catch (e: Exception) {
            null
        }
    }
}
