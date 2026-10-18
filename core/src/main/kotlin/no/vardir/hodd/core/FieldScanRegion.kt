package no.vardir.hodd.core

import kotlin.math.roundToInt

/** Shared by the camera overlay and OCR after the photo is oriented upright. */
object FieldScanRegion {
    const val LEFT = 0.06f
    const val TOP = 0.375f
    const val RIGHT = 0.94f
    const val BOTTOM = 0.625f

    data class Bounds(val left: Int, val top: Int, val width: Int, val height: Int)

    fun bounds(width: Int, height: Int): Bounds {
        require(width > 0 && height > 0)
        val left = (width * LEFT).roundToInt().coerceIn(0, width - 1)
        val top = (height * TOP).roundToInt().coerceIn(0, height - 1)
        val right = (width * RIGHT).roundToInt().coerceIn(left + 1, width)
        val bottom = (height * BOTTOM).roundToInt().coerceIn(top + 1, height)
        return Bounds(left, top, right - left, bottom - top)
    }
}
