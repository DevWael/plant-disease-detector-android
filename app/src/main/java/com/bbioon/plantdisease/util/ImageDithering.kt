package com.bbioon.plantdisease.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Floyd–Steinberg dithering: converts a color/grayscale bitmap to 1-bit black/white,
 * optimized for thermal printer output.
 */
object ImageDithering {

    /**
     * Scale and dither a bitmap to a target width with 1-bit output.
     * @param source  The source bitmap (any color depth).
     * @param targetWidth  The target width in pixels (384 for 57mm thermal printers).
     * @return A new [Bitmap] at [targetWidth] width, ARGB_8888, with only black/white pixels.
     */
    fun dither(source: Bitmap, targetWidth: Int = 384): Bitmap {
        // Scale to target width preserving aspect ratio
        val scale = targetWidth.toFloat() / source.width
        val targetHeight = (source.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

        val width = scaled.width
        val height = scaled.height

        // Extract grayscale values as floats (0-255)
        val gray = Array(height) { y ->
            FloatArray(width) { x ->
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Luminance formula
                0.299f * r + 0.587f * g + 0.114f * b
            }
        }

        // Floyd-Steinberg dithering
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldVal = gray[y][x].coerceIn(0f, 255f)
                val newVal = if (oldVal < 128f) 0f else 255f
                gray[y][x] = newVal
                val error = oldVal - newVal

                if (x + 1 < width)                    gray[y][x + 1]     += error * 7f / 16f
                if (y + 1 < height && x - 1 >= 0)     gray[y + 1][x - 1] += error * 3f / 16f
                if (y + 1 < height)                    gray[y + 1][x]     += error * 5f / 16f
                if (y + 1 < height && x + 1 < width)  gray[y + 1][x + 1] += error * 1f / 16f
            }
        }

        // Build output bitmap
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = if (gray[y][x] < 128f) Color.BLACK else Color.WHITE
                result.setPixel(x, y, c)
            }
        }

        if (scaled !== source) scaled.recycle()
        return result
    }
}
