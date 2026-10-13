package com.macarmusic.app.model

import com.macarmusic.app.data.DisplayOptions
import org.junit.Assert.*
import org.junit.Test

class VisualOptionsTest {
    @Test fun slideshowAndSettingsDefaultToTheRequestedFade() {
        val options = DisplayOptions()
        assertEquals(5, options.slideshow.intervalSeconds)
        assertEquals(2_000, options.slideshow.durationMs)
        assertEquals(TransitionStyle.FADE, options.slideshow.transition)
        assertEquals(TransitionStyle.FADE, options.settingsTransition)
        assertEquals(PlaybackTimeMode.ELAPSED, options.timeMode)
        assertFalse(options.spectrum.enabled)
        assertTrue(options.controlBlocks)
        assertEquals(BlockOutline(), options.blockOutline)
        assertEquals(20, options.lyricEnlargementPercent)
        assertEquals(NavigationPosition.BOTTOM, options.navigationPosition)
        assertEquals(FireStyle.LED, options.spectrum.fireStyle)
        assertEquals(PlaybackTimeMode.ELAPSED, options.mainTimeMode)
        assertFalse(options.lyricsDragSeek)
        assertTrue(options.lyricsSmoothScroll)
    }

    @Test fun fireBlendsContinuouslyBetweenThreeEditableAnchors() {
        val colors = SpectrumColors.FIRE
        assertEquals(20, (0 until 20).map { colors.colorAt((it + 0.5f) / 20) }.toSet().size)
        assertEquals(0xffdf69, colors.colorAt(0f))
        assertEquals(0xff9828, colors.colorAt(0.5f))
        assertEquals(0xed4529, colors.colorAt(1f))
        assertEquals(colors.low, colors.colorAt(-1f))
        assertEquals(colors.high, colors.colorAt(2f))
        val gray = SpectrumColors(0x000000, 0x808080, 0xffffff)
        assertEquals(0x404040, gray.colorAt(0.25f))
        assertEquals(0xc0c0c0, gray.colorAt(0.75f))
        for (shift in listOf(0, 8, 16)) {
            val below = (colors.colorAt(0.499f) shr shift) and 0xff
            val above = (colors.colorAt(0.501f) shr shift) and 0xff
            assertTrue(kotlin.math.abs(above - below) <= 1)
        }
    }

    @Test fun borderCharactersAcceptOneVisibleCodePointOnly() {
        for (value in listOf("*", "+", "#", "A", "\u661f", "\uD83D\uDE00")) assertTrue(BlockOutline.isValidCharacter(value))
        for (value in listOf("", " ", "\n", "\t", "ab", "\u200b", "\uD83D")) assertFalse(BlockOutline.isValidCharacter(value))
    }

    @Test fun nonFiniteSpectrumHeightsAreRejected() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { SpectrumColors.FIRE.colorAt(value) }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun borderRejectsInvisibleCharacter() {
        BlockOutline(character = "\n")
    }

    @Test fun lyricEnlargementIsBounded() {
        assertEquals(0, DisplayOptions(lyricEnlargementPercent = 0).lyricEnlargementPercent)
        assertEquals(60, DisplayOptions(lyricEnlargementPercent = 60).lyricEnlargementPercent)
        for (value in listOf(-1, 61)) {
            assertThrows(IllegalArgumentException::class.java) { DisplayOptions(lyricEnlargementPercent = value) }
        }
    }

    @Test fun palettesAndRenderingRemainIndependent() {
        val customized = SpectrumColors(0x112233, 0x445566, 0x778899)
        val options = SpectrumOptions(fireColors = customized, fireStyle = FireStyle.SMOOTH)
        assertEquals(SpectrumColors.CLASSIC, options.classicColors)
        assertEquals(customized, options.fireColors)
        assertEquals(customized, options.copy(fireStyle = FireStyle.LED).fireColors)
    }

    @Test(expected = IllegalArgumentException::class) fun paletteRejectsNonRgbColor() {
        SpectrumColors(-1, 0, 0)
    }

    @Test fun transitionDurationCannotOutlastTheChangeInterval() {
        val shorter = SlideshowOptions().withInterval(1)
        assertEquals(1_000, shorter.durationMs)
        assertEquals(1_000L, shorter.intervalMs)
        assertEquals(0L, shorter.copy(transition = TransitionStyle.NONE).animationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimingCannotEnterTheSlideshow() {
        SlideshowOptions(intervalSeconds = 1, durationMs = 2_000)
    }

    @Test fun theOldSinglePictureRemainsReadableWithoutMigrationCopies() {
        assertEquals(emptyList<String>(), DisplayOptions().backgroundImages)
        assertEquals(listOf(BackgroundImages.LEGACY), DisplayOptions(imageVersion = 123).backgroundImages)
    }

    @Test fun newGalleriesPreserveTheirOrder() {
        val ids = listOf("10000000-0000-0000-0000-000000000000.png", "20000000-0000-0000-0000-000000000000.png")
        assertEquals(ids, DisplayOptions(imageVersion = 456, imageIds = ids).backgroundImages)
    }

    @Test fun privateImageNamesCannotEscapeTheirDirectory() {
        assertTrue(BackgroundImages.isImportedName("12345678-1234-1234-abcd-123456789012.png"))
        for (name in listOf("../background.png", "..\\background.png", "C:\\picture.png", "image.jpg", BackgroundImages.LEGACY)) {
            assertFalse(name, BackgroundImages.isImportedName(name))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun spectrumRefreshIsBounded() {
        SpectrumOptions(refreshRate = 1_000)
    }
}
