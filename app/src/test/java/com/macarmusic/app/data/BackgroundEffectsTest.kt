package com.macarmusic.app.data

import org.junit.Assert.*
import org.junit.Test

class BackgroundEffectsTest {
    @Test fun zeroBlurLeavesPixelsUnchanged() {
        val original = intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt())
        assertArrayEquals(original, BackgroundEffects.blur(original, 3, 1, 0))
        assertEquals(0, BackgroundEffects.radius(0))
    }

    @Test fun blurSoftensEdgesWithoutChangingSolidColors() {
        val solid = IntArray(8 * 8) { 0xff2255aa.toInt() }
        assertArrayEquals(solid, BackgroundEffects.blur(solid, 8, 8, 24))
        val edges = IntArray(20) { if (it < 10) 0xff000000.toInt() else 0xffffffff.toInt() }
        val result = BackgroundEffects.blur(edges, 20, 1, 3)
        assertTrue((result[9] and 255) in 1..254)
        assertTrue((result[10] and 255) in 1..254)
        assertTrue(result.all { it ushr 24 == 255 })
        assertEquals(24, BackgroundEffects.radius(100))
    }

    @Test fun whiteAndBlackTintsAreIndependentOfBlur() {
        assertEquals(0, BackgroundEffects.tintColor(BackgroundTint.ORIGINAL, 100))
        assertEquals(0xff000000.toInt(), BackgroundEffects.tintColor(BackgroundTint.DARK, 100))
        assertEquals(0xffffffff.toInt(), BackgroundEffects.tintColor(BackgroundTint.LIGHT, 100))
        assertEquals(127, BackgroundEffects.tintColor(BackgroundTint.LIGHT, 50) ushr 24)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidDimensionsCannotOverflowAllocation() {
        BackgroundEffects.blur(IntArray(1), 65_536, 65_536, 3)
    }

    @Test(expected = IllegalStateException::class) fun blurHonorsCancellationBetweenRows() {
        BackgroundEffects.blur(IntArray(10_000), 100, 100, 10) { error("Cancelled") }
    }
}
