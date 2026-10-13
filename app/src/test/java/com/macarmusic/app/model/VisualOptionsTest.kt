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
        assertEquals(LyricAppearance(), options.lyricAppearance)
        assertEquals(LyricSide.START, options.landscapeLyrics)
        assertEquals(LyricSide.START, options.portraitLyrics)
        assertEquals(ThemeAccent.DEFAULT, options.themeAccent)
        assertNull(options.customAccentColor)
        assertEquals(FireStyle.SMOOTH, options.spectrum.barStyle)
        assertEquals(NavigationPosition.BOTTOM, options.navigationPosition)
        assertEquals(FireStyle.LED, options.spectrum.fireStyle)
        assertEquals(PlaybackTimeMode.ELAPSED, options.mainTimeMode)
        assertFalse(options.lyricsDragSeek)
        assertTrue(options.lyricsSmoothScroll)
    }

    @Test fun customColorsAcceptExactlySixHexDigitsAndRoundTrip() {
        for (value in listOf(0, 1, 0x123abc, 0xffffff)) {
            val formatted = ColorHex.format(value)
            assertEquals(7, formatted.length)
            assertEquals(value, ColorHex.parse(formatted))
            assertEquals(value, ColorHex.parse(formatted.drop(1).lowercase()))
            assertEquals(value, ColorHex.parse(" \t$formatted\n"))
        }
        assertEquals("#00ABCD", ColorHex.format(0xabcd))
    }

    @Test fun incompleteAndMalformedCustomColorsAreRejected() {
        for (value in listOf("", "#", "#abc", "12345", "1234567", "#12345678", "0x123456",
            "#12GG00", "##123456", "-000001", "+000001", "12 456", "\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16")) {
            assertNull(value, ColorHex.parse(value))
        }
    }

    @Test fun customAccentCannotContainAlphaOrInvalidRgbValues() {
        assertEquals(0, DisplayOptions(customAccentColor = 0).customAccentColor)
        assertEquals(0xffffff, DisplayOptions(customAccentColor = 0xffffff).customAccentColor)
        for (value in listOf(-1, 0x1000000)) {
            assertThrows(IllegalArgumentException::class.java) { DisplayOptions(customAccentColor = value) }
            assertThrows(IllegalArgumentException::class.java) { ColorHex.format(value) }
        }
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

    @Test fun lyricSizesAndColorsAreIndependentAndBounded() {
        assertEquals(24, LyricAppearance().activeSizeSp)
        assertEquals(20, LyricAppearance().otherSizeSp)
        assertNull(LyricAppearance().activeColor)
        assertNull(LyricAppearance().otherColor)
        assertEquals(12, LyricAppearance(activeSizeSp = 12, otherSizeSp = 48).activeSizeSp)
        for (value in listOf(11, 49)) {
            assertThrows(IllegalArgumentException::class.java) { LyricAppearance(activeSizeSp = value) }
            assertThrows(IllegalArgumentException::class.java) { LyricAppearance(otherSizeSp = value) }
        }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(activeColor = -1) }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(otherColor = 0x1000000) }
    }

    @Test fun arcAndShadowAreOptInAndBounded() {
        assertFalse(BlockOutline().shadow)
        assertFalse(BlockOutline().hasArc)
        assertTrue(BlockOutline(BlockStyle.DASHED_ARC).hasArc)
        assertEquals(18, BlockOutline(BlockStyle.ARC).arcDp)
        assertThrows(IllegalArgumentException::class.java) { BlockOutline(arcDp = -1) }
        assertThrows(IllegalArgumentException::class.java) { BlockOutline(arcDp = 49) }
    }

    @Test fun tappingCyclesExactlyFiveModesWithoutChangingOtherSpectrumPreferences() {
        val start = SpectrumOptions(enabled = true, style = SpectrumStyle.LINE, bands = 16, refreshRate = 15,
            classicColors = SpectrumColors(0, 0x808080, 0xffffff))
        val expected = listOf(
            Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.SMOOTH),
            Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.LED),
            Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.SMOOTH),
            Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.LED),
            Triple(SpectrumStyle.LINE, SpectrumPalette.CLASSIC, FireStyle.LED),
        )
        var options = start
        repeat(2) {
            for ((style, palette, rendering) in expected) {
                options = options.nextMode()
                assertEquals(style, options.style)
                assertEquals(palette, options.palette)
                assertEquals(rendering, options.rendering)
                assertEquals(start.classicColors, options.classicColors)
                assertEquals(16, options.bands)
                assertEquals(15, options.refreshRate)
                assertTrue(options.enabled)
            }
        }
    }

    @Test fun themeAndClassicBothHaveSmoothAndLedRendering() {
        for (palette in listOf(SpectrumPalette.CLASSIC, SpectrumPalette.THEME)) {
            val options = SpectrumOptions(palette = palette)
            assertEquals(FireStyle.SMOOTH, options.rendering)
            assertEquals(FireStyle.LED, options.withRendering(FireStyle.LED).rendering)
            assertEquals(FireStyle.LED, options.fireStyle)
        }
        assertEquals(SpectrumStyle.LINE, SpectrumOptions(palette = SpectrumPalette.THEME).nextMode().style)
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
