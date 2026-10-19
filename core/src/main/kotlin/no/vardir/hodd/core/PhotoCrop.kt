package no.vardir.hodd.core

/** Fractions of the upright image, independent of preview size and screen orientation. */
data class PhotoCrop(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(left >= 0 && top >= 0 && right <= 1 && bottom <= 1 && right > left && bottom > top)
    }
    fun move(dx: Float, dy: Float): PhotoCrop {
        val x = dx.coerceIn(-left, 1 - right)
        val y = dy.coerceIn(-top, 1 - bottom)
        return PhotoCrop((left + x).coerceAtLeast(0f), (top + y).coerceAtLeast(0f), (right + x).coerceAtMost(1f), (bottom + y).coerceAtMost(1f))
    }
    fun resize(handle: String, dx: Float, dy: Float): PhotoCrop = PhotoCrop(
        if ('l' in handle) (left + dx).coerceIn(0f, right - MIN_SIZE) else left,
        if ('t' in handle) (top + dy).coerceIn(0f, bottom - MIN_SIZE) else top,
        if ('r' in handle) (right + dx).coerceIn(left + MIN_SIZE, 1f) else right,
        if ('b' in handle) (bottom + dy).coerceIn(top + MIN_SIZE, 1f) else bottom,
    )
    companion object { const val MIN_SIZE = 0.05f }
}
