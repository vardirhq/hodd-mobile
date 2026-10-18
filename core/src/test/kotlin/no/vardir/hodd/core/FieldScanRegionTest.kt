package no.vardir.hodd.core

import kotlin.test.*
import org.junit.Test

class FieldScanRegionTest {
    @Test fun portraitAndLandscapeUseTheSameNormalizedFrame() {
        assertEquals(FieldScanRegion.Bounds(60, 750, 880, 500), FieldScanRegion.bounds(1000, 2000))
        assertEquals(FieldScanRegion.Bounds(120, 375, 1760, 250), FieldScanRegion.bounds(2000, 1000))
    }

    @Test fun smallImagesAlwaysHaveAValidCrop() {
        assertEquals(FieldScanRegion.Bounds(0, 0, 1, 1), FieldScanRegion.bounds(1, 1))
        assertFailsWith<IllegalArgumentException> { FieldScanRegion.bounds(0, 100) }
    }
}
