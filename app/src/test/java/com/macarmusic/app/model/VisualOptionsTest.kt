package com.macarmusic.app.model

import com.macarmusic.app.data.DisplayOptions
import org.junit.Assert.*
import org.junit.Test

class VisualOptionsTest {
    @Test fun startupPlaybackIsOptInAndSurvivesBackupSerializationAndNormalization() {
        val json = kotlinx.serialization.json.Json
        assertFalse(DisplayOptions().autoPlayOnLaunch)
        assertFalse(json.decodeFromString<DisplayOptions>("{}").autoPlayOnLaunch)
        for (enabled in listOf(false, true)) {
            val options = DisplayOptions(autoPlayOnLaunch = enabled, blockOutline = BlockOutline(BlockStyle.NONE))
            val restored = json.decodeFromString<DisplayOptions>(json.encodeToString(DisplayOptions.serializer(), options))
            assertEquals(options, restored)
            assertEquals(enabled, restored.normalized().autoPlayOnLaunch)
        }
    }

    @Test fun appearanceDefaultsUseTheLogoGradientAndAllNineChoicesRoundTrip() {
        val json = kotlinx.serialization.json.Json
        val legacy = json.decodeFromString<DisplayOptions>("{}")
        assertEquals(AppLogo.MUSIC, legacy.appLogo)
        assertEquals(ButtonIconStyle.LOGO, legacy.buttonIcons)
        assertEquals(9, ButtonIconStyle.entries.size)
        assertEquals(ButtonIconStyle.MONOCHROME,
            json.decodeFromString<DisplayOptions>("""{"buttonIcons":"MONOCHROME"}""").buttonIcons)
        for (logo in AppLogo.entries) for (icons in ButtonIconStyle.entries) {
            val options = DisplayOptions(appLogo = logo, buttonIcons = icons)
            assertEquals(options, json.decodeFromString<DisplayOptions>(
                json.encodeToString(DisplayOptions.serializer(), options)))
            assertEquals(logo, options.copy(blockOutline = BlockOutline(BlockStyle.NONE)).normalized().appLogo)
            assertEquals(icons, options.copy(blockOutline = BlockOutline(BlockStyle.NONE)).normalized().buttonIcons)
        }
    }

    @Test fun optionalNavigationPagesDefaultToVisibleAndRoundTripIndependently() {
        val json = kotlinx.serialization.json.Json
        val legacy = json.decodeFromString<DisplayOptions>("{}")
        assertTrue(legacy.showLibrary)
        assertTrue(legacy.showQueue)
        assertTrue(legacy.showPlaylists)
        for (bits in 0..7) {
            val options = DisplayOptions(showLibrary = bits and 1 != 0, showQueue = bits and 2 != 0,
                showPlaylists = bits and 4 != 0)
            assertEquals(options, json.decodeFromString<DisplayOptions>(
                json.encodeToString(DisplayOptions.serializer(), options)))
            val normalized = options.copy(blockOutline = BlockOutline(BlockStyle.NONE)).normalized()
            assertEquals(options.showLibrary, normalized.showLibrary)
            assertEquals(options.showQueue, normalized.showQueue)
            assertEquals(options.showPlaylists, normalized.showPlaylists)
        }
    }

    @Test fun spectrumOriginsAndBandCountsRoundTripWithoutMovingTheWindow() {
        val json = kotlinx.serialization.json.Json
        assertEquals(SpectrumOrigin.BOTTOM, SpectrumOptions().origin)
        assertEquals(SpectrumPosition.LEFT, SpectrumOptions().position)
        assertEquals(listOf(3, 8, 16, 32), SpectrumOptions.BAND_COUNTS)
        for (origin in SpectrumOrigin.entries) for (bands in SpectrumOptions.BAND_COUNTS) {
            val options = SpectrumOptions(origin = origin, bands = bands)
            assertEquals(options, json.decodeFromString<SpectrumOptions>(
                json.encodeToString(SpectrumOptions.serializer(), options)))
            assertEquals(origin, options.nextMode().origin)
            assertEquals(bands, options.nextMode().bands)
            assertEquals(SpectrumPosition.LEFT, options.nextMode().position)
        }
        assertEquals(SpectrumOrigin.BOTTOM, json.decodeFromString<SpectrumOptions>("{}").origin)
        for (bands in listOf(0, 1, 2, 4, 7, 17, 64)) {
            assertThrows(IllegalArgumentException::class.java) { SpectrumOptions(bands = bands) }
        }
    }

    @Test fun slideshowAndSettingsDefaultToTheRequestedFade() {
        val options = DisplayOptions()
        assertEquals(5, options.slideshow.intervalSeconds)
        assertEquals(2_000, options.slideshow.durationMs)
        assertEquals(TransitionStyle.FADE, options.slideshow.transition)
        assertEquals(TransitionStyle.FADE, options.settingsTransition)
        assertEquals(PlaybackTimeMode.ELAPSED, options.timeMode)
        assertTrue(options.spectrum.enabled)
        assertTrue(options.controlBlocks)
        assertEquals(BlockOutline(), options.blockOutline)
        assertEquals(LyricAppearance(), options.lyricAppearance)
        assertEquals(LyricSide.START, options.landscapeLyrics)
        assertEquals(LyricSide.START, options.portraitLyrics)
        assertEquals(ThemeAccent.DEFAULT, options.themeAccent)
        assertNull(options.customAccentColor)
        assertNull(options.primaryControlColor)
        assertEquals(FireStyle.LED, options.spectrum.barStyle)
        assertEquals(NavigationPosition.BOTTOM, options.navigationPosition)
        assertEquals(FireStyle.LED, options.spectrum.fireStyle)
        assertEquals(PlaybackTimeMode.ELAPSED, options.mainTimeMode)
        assertFalse(options.lyricsDragSeek)
        assertTrue(options.lyricsSmoothScroll)
    }

    @Test fun spectrumDefaultsAreLeftEightBottomLedFireBandsAtFifteenFrames() {
        val options = SpectrumOptions()
        assertTrue(options.enabled)
        assertEquals(SpectrumPosition.LEFT, options.position)
        assertEquals(SpectrumOrigin.BOTTOM, options.origin)
        assertEquals(SpectrumStyle.BARS, options.style)
        assertEquals(SpectrumPalette.FIRE, options.palette)
        assertEquals(8, options.bands)
        assertEquals(15, options.refreshRate)
        assertEquals(FireStyle.LED, options.rendering)
        assertEquals(SpectrumColors(0x3dcf74, 0xfcd34d, 0xf04c44), options.colors)
        assertEquals(options, kotlinx.serialization.json.Json.decodeFromString<SpectrumOptions>("{}"))
        val saved = kotlinx.serialization.json.Json.decodeFromString<SpectrumOptions>(
            """{"enabled":false,"position":"RIGHT","palette":"CLASSIC","bands":32,"refreshRate":60,"barStyle":"SMOOTH",
                "fireColors":{"low":16768873,"middle":16750632,"high":15549737}}""")
        assertFalse(saved.enabled)
        assertEquals(SpectrumPosition.RIGHT, saved.position)
        assertEquals(32, saved.bands)
        assertEquals(60, saved.refreshRate)
        assertEquals(FireStyle.SMOOTH, saved.rendering)
        assertEquals(SpectrumColors(0xffdf69, 0xff9828, 0xed4529), saved.colors)
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

    @Test fun primaryControlColorDefaultsToThemeAndAcceptsOnlyRgb() {
        val json = kotlinx.serialization.json.Json
        assertNull(json.decodeFromString<DisplayOptions>("{}").primaryControlColor)
        assertTrue(json.decodeFromString<DisplayOptions>("{}").primaryControlHighlightEnabled)
        for (enabled in listOf(true, false)) for (color in listOf(null, 0, 0xffffff, 0x13579b)) {
            val options = DisplayOptions(primaryControlColor = color, primaryControlHighlightEnabled = enabled)
            assertEquals(options, json.decodeFromString<DisplayOptions>(json.encodeToString(DisplayOptions.serializer(), options)))
        }
        assertFalse(json.decodeFromString<DisplayOptions>("""{"primaryControlHighlightEnabled":false}""").primaryControlHighlightEnabled)
        for (color in listOf(-1, 0x1000000)) {
            assertThrows(IllegalArgumentException::class.java) { DisplayOptions(primaryControlColor = color) }
            assertThrows(IllegalArgumentException::class.java) {
                json.decodeFromString<DisplayOptions>("""{"primaryControlColor":$color}""")
            }
        }
    }

    @Test fun fireBlendsContinuouslyBetweenThreeEditableAnchors() {
        val colors = SpectrumColors.FIRE
        assertEquals(20, (0 until 20).map { colors.colorAt((it + 0.5f) / 20) }.toSet().size)
        assertEquals(0x3dcf74, colors.colorAt(0f))
        assertEquals(0xfcd34d, colors.colorAt(0.5f))
        assertEquals(0xf04c44, colors.colorAt(1f))
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
        assertFalse(LyricAppearance().karaoke)
        assertEquals(0xffd54f, LyricAppearance().karaokeColor)
        assertEquals(12, LyricAppearance(activeSizeSp = 12, otherSizeSp = 48).activeSizeSp)
        for (value in listOf(11, 49)) {
            assertThrows(IllegalArgumentException::class.java) { LyricAppearance(activeSizeSp = value) }
            assertThrows(IllegalArgumentException::class.java) { LyricAppearance(otherSizeSp = value) }
        }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(activeColor = -1) }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(otherColor = 0x1000000) }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(karaokeColor = -1) }
        assertThrows(IllegalArgumentException::class.java) { LyricAppearance(karaokeColor = 0x1000000) }
    }

    @Test fun oldLyricAppearanceKeepsKaraokeOffAndNewOptionsRoundTripIndependently() {
        val json = kotlinx.serialization.json.Json
        val old = json.decodeFromString<LyricAppearance>("""{"activeSizeSp":31,"otherSizeSp":18,"activeColor":1193046}""")
        assertEquals(LyricAppearance(31, 18, 0x123456), old)
        val next = old.copy(karaoke = true, karaokeColor = 0xabcdef)
        assertEquals(next, json.decodeFromString<LyricAppearance>(json.encodeToString(LyricAppearance.serializer(), next)))
        assertEquals(0x123456, next.activeColor)
        assertNull(next.otherColor)
        assertEquals(0xabcdef, next.copy(karaoke = false).karaokeColor)
    }

    @Test fun arcAndShadowAreOptInAndBounded() {
        assertFalse(BlockOutline().shadow)
        assertFalse(BlockOutline().hasArc)
        assertTrue(BlockOutline(BlockStyle.DASHED_ARC).hasArc)
        assertEquals(18, BlockOutline(BlockStyle.ARC).arcDp)
        assertThrows(IllegalArgumentException::class.java) { BlockOutline(arcDp = -1) }
        assertThrows(IllegalArgumentException::class.java) { BlockOutline(arcDp = 49) }
    }

    @Test fun noBorderKeepsSavedDecorationOptionsWithoutEnablingAnOutline() {
        val outlined = BlockOutline(BlockStyle.DASHED_ARC, "+", 36, true)
        val none = outlined.copy(style = BlockStyle.NONE)
        assertFalse(none.hasBorder)
        assertFalse(none.hasArc)
        assertEquals(outlined, none.copy(style = BlockStyle.DASHED_ARC))
        assertTrue(BlockOutline().hasBorder)
        assertEquals(BlockStyle.NONE, BlockStyle.valueOf("NONE"))
    }

    @Test fun legacyNoneBecomesDisabledControlsAndKeepsDecorationPreferences() {
        val outline = BlockOutline(BlockStyle.NONE, "+", 36, true)
        val old = DisplayOptions(blockOutline = outline)
        val migrated = old.normalized()
        assertFalse(migrated.controlBlocks)
        assertEquals(outline.copy(style = BlockStyle.SOLID), migrated.blockOutline)
        assertFalse(BlockStyle.NONE in BlockStyle.selectable)
        assertEquals(5, BlockStyle.selectable.size)
        assertSame(migrated, migrated.normalized())
        val disabled = DisplayOptions(controlBlocks = false, blockOutline = outline.copy(style = BlockStyle.DASHED_ARC))
        assertSame(disabled, disabled.normalized())
    }

    @Test fun tappingCyclesSixModesIncludingOffWithoutChangingOtherSpectrumPreferences() {
        val start = SpectrumOptions(enabled = true, style = SpectrumStyle.LINE, bands = 16, refreshRate = 15,
            colors = SpectrumColors(0, 0x808080, 0xffffff))
        val expected = listOf(
            Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.SMOOTH),
            Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.LED),
            Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.SMOOTH),
            Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.LED),
            Triple(SpectrumStyle.OFF, SpectrumPalette.FIRE, FireStyle.LED),
            Triple(SpectrumStyle.LINE, SpectrumPalette.CLASSIC, FireStyle.LED),
        )
        var options = start
        repeat(2) {
            for ((style, palette, rendering) in expected) {
                options = options.nextMode()
                assertEquals(style, options.style)
                assertEquals(palette, options.palette)
                assertEquals(rendering, options.rendering)
                assertEquals(start.colors, options.colors)
                assertEquals(16, options.bands)
                assertEquals(15, options.refreshRate)
                assertTrue(options.enabled)
            }
        }
    }

    @Test fun themeAndClassicBothHaveSmoothAndLedRendering() {
        for (palette in listOf(SpectrumPalette.CLASSIC, SpectrumPalette.THEME)) {
            val options = SpectrumOptions(palette = palette)
            assertEquals(FireStyle.LED, options.rendering)
            assertEquals(FireStyle.SMOOTH, options.withRendering(FireStyle.SMOOTH).rendering)
            assertEquals(FireStyle.LED, options.withRendering(FireStyle.LED).rendering)
            assertEquals(FireStyle.LED, options.fireStyle)
        }
        assertEquals(SpectrumStyle.LINE, SpectrumOptions(palette = SpectrumPalette.THEME).nextMode().style)
    }

    @Test fun classicAndFireShareColorsWithoutChangingTheirRenderingChoices() {
        val customized = SpectrumColors(0x112233, 0x445566, 0x778899)
        val options = SpectrumOptions(colors = customized, fireStyle = FireStyle.SMOOTH)
        assertEquals(SpectrumColors.FIRE, SpectrumOptions().colors)
        for (palette in SpectrumPalette.entries) {
            assertEquals(customized, options.copy(palette = palette).colors)
            assertEquals(customized, options.copy(palette = palette).withRendering(FireStyle.LED).colors)
        }
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
