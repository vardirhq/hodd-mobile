package no.vardir.hodd.core

import kotlin.test.*
import org.junit.Test

class PhotoCropTest {
    @Test fun movingCropPreservesSizeAndStaysInsideImage() {
        val original = PhotoCrop(0.2f, 0.3f, 0.8f, 0.9f)
        val moved = original.move(-2f, 2f)
        assertEquals(0f, moved.left)
        assertEquals(1f, moved.bottom)
        assertEquals(original.right - original.left, moved.right - moved.left, 0.0001f)
        assertEquals(original.bottom - original.top, moved.bottom - moved.top, 0.0001f)
    }
    @Test fun cornerAndEdgeResizeCannotInvertOrLeaveImage() {
        val crop = PhotoCrop().resize("lt", 2f, -2f)
        assertEquals(0.95f, crop.left)
        assertEquals(0f, crop.top)
        assertEquals(1f, crop.right)
        assertTrue(crop.resize("rb", -2f, 2f).right > crop.left)
        assertEquals(1f, crop.resize("rb", -2f, 2f).bottom)
    }
    @Test fun invalidCoordinatesFailBeforeImageWork() {
        assertFailsWith<IllegalArgumentException> { PhotoCrop(left = Float.NaN) }
        assertFailsWith<IllegalArgumentException> { PhotoCrop(left = -0.1f) }
        assertFailsWith<IllegalArgumentException> { PhotoCrop(left = 1f) }
        assertFailsWith<IllegalArgumentException> { PhotoCrop(bottom = 0f) }
    }
}
