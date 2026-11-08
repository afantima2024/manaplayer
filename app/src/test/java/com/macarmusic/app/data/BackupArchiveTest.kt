package com.macarmusic.app.data

import com.macarmusic.app.model.*
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioFavorites
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioListEntry
import com.macarmusic.app.radio.RadioSubscription
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val imageName = "00000000-0000-0000-0000-000000000001.png"
    private val settings = BackupSettings(DisplayOptions(), PlaybackOptions(), SortOrder.ALBUM, 2, "zh-CN",
        ColorSlots().define(0, 0x123abc))
    private val source = FolderSource("content://disk/tree/music", "Music", true, pinned = true)
    private val song = Track("song", "content://disk/song", "song.mp3", "Song", "Artist", "Album",
        durationMs = 60_000, sourceUris = mapOf(source.uri to "content://disk/song"), sourcePaths = mapOf(source.uri to "Album/song.mp3"))
    private val library = Library(sources = listOf(source), tracks = listOf(song), favorites = setOf(song.id),
        playlists = listOf(LocalPlaylist("p", "Mix", listOf(song.id), listOf(PlaylistFolder(source.uri, "album", "Album", listOf(song.id), "Album")), emptyList())))
    private fun backup() = BackupManifest(origin = "00000000-0000-0000-0000-000000000099", createdAtMs = 1000,
        settings = settings, library = library, positions = listOf(CollectionPosition(PlaybackCollection.playlist("p"), song.id, 8000, 999)))

    private fun raw(backup: BackupManifest, entries: Map<String, ByteArray> = emptyMap(), text: String? = null): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write((text ?: Json.encodeToString(backup)).toByteArray())
            zip.closeEntry()
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun read(bytes: ByteArray): BackupManifest =
        BackupArchive.read(ByteArrayInputStream(bytes), temp.newFolder())

    @Test fun roundTripKeepsSettingsFavoritesPlaylistsPinsAndIndependentPositions() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(
            blockOutline = BlockOutline(BlockStyle.NONE, "+", 36, true),
            spectrum = SpectrumOptions(refreshRate = 60), navigationPosition = NavigationPosition.LEFT,
            lyricAppearance = LyricAppearance(30, 16, 0x123456, 0xabcdef, true, 0x00aaff), widgetTransparency = 85,
            keepScreenOn = true, noticeDurationSeconds = 15, miniPlayerTransparency = 65, primaryControlColor = 0x13579b,
            internalLyricExpiry = LyricCacheExpiry.NEVER, portableLyricExpiry = LyricCacheExpiry.SEVEN_DAYS)))
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, emptyMap())
        assertEquals(value, read(output.toByteArray()))
    }

    @Test fun subscriptionsBackupKeepsDisabledCachesPinsValidatorsAndSourceSelections() {
        val programs = RadioList.merge(emptyList(), listOf(
            RadioListEntry("Subscribed station", "https://example.com/one"),
            RadioListEntry("Subscribed station", "https://example.com/two")), emptyMap()).programs
        val station = programs.single()
        val subscription = RadioSubscription("00000000-0000-0000-0000-000000000001", "My subscription",
            "https://example.com/list.m3u", enabled = false, programs = programs,
            checkedAtMs = 200, updatedAtMs = 100, etag = "\"one\"", validatorUrl = "https://example.com/list.m3u")
        val value = backup().copy(settings = settings.copy(
            radio = RadioFavorites(setOf(station.id), pins = listOf(station.id)), radioPrograms = emptyList(),
            radioIncludeDefaults = false, radioSubscriptions = listOf(subscription),
            radioSourceSelections = mapOf(station.id to station.sources[1])))
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, emptyMap())
        assertEquals(value, read(output.toByteArray()))
        val legacy = read(raw(backup()))
        assertTrue(legacy.settings.radioSubscriptions.isEmpty())
        assertTrue(legacy.settings.radioSourceSelections.isEmpty())
    }

    @Test fun allFixedStationMarksSurviveZipBackupsWithoutCustomImagePaths() {
        val logos = com.macarmusic.app.radio.RadioStationLogo.entries
        assertEquals(12, logos.size)
        val programs = RadioList.merge(emptyList(), logos.map {
            RadioListEntry("Logo ${it.name}", "https://example.com/${it.name}")
        }, emptyMap()).programs
        val overrides = programs.mapIndexed { index, program -> program.id to logos[index] }.toMap()
        val value = backup().copy(settings = settings.copy(
            radio = RadioFavorites(pins = programs.map { it.id }, stationLogos = overrides),
            radioPrograms = programs, radioIncludeDefaults = false))
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, emptyMap())
        assertEquals(overrides, read(output.toByteArray()).settings.radio!!.stationLogos)
        assertTrue(Json.decodeFromString<RadioFavorites>("{}").stationLogos.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            RadioFavorites(stationLogos = mapOf("file:///custom.png" to logos.first()))
        }
    }

    @Test fun subscriptionRefreshChoiceSurvivesZipBackupAndOldBackupsStayManual() {
        for (interval in com.macarmusic.app.radio.RadioRefreshInterval.entries) {
            val value = backup().copy(settings = settings.copy(
                radio = RadioFavorites(refreshInterval = interval, showPanel = false)))
            val output = ByteArrayOutputStream()
            BackupArchive.write(output, value, emptyMap())
            assertEquals(interval, read(output.toByteArray()).settings.radio!!.refreshInterval)
        }
        val legacy = backup().copy(settings = settings.copy(radio = RadioFavorites()))
        assertEquals(com.macarmusic.app.radio.RadioRefreshInterval.MANUAL,
            read(raw(legacy)).settings.radio!!.refreshInterval)
    }

    @Test fun allNineButtonThemesAndExplicitOlderSpectrumChoicesSurviveZipBackups() {
        assertEquals(9, ButtonIconStyle.entries.size)
        val savedSpectrum = SpectrumOptions(enabled = false, position = SpectrumPosition.RIGHT,
            palette = SpectrumPalette.CLASSIC, bands = 32, barStyle = FireStyle.SMOOTH,
            refreshRate = 30, colors = SpectrumColors(0xffdf69, 0xff9828, 0xed4529))
        for (style in ButtonIconStyle.entries) {
            val display = DisplayOptions(buttonIcons = style, spectrum = savedSpectrum)
            val output = ByteArrayOutputStream()
            BackupArchive.write(output, backup().copy(settings = settings.copy(display = display)), emptyMap())
            assertEquals(display, read(output.toByteArray()).settings.display)
        }
        val restored = read(raw(backup())).settings.display
        assertEquals(ButtonIconStyle.LOGO, restored.buttonIcons)
        assertEquals(SpectrumOptions(), restored.spectrum)
    }

    @Test fun incompleteOrDuplicateSubscriptionBackupCannotReplaceSavedState() {
        val subscription = RadioSubscription("00000000-0000-0000-0000-000000000001", "My subscription",
            "https://example.com/list.m3u")
        for (invalid in listOf(settings.copy(radioSubscriptions = listOf(subscription)),
            settings.copy(radioPrograms = emptyList(), radioSubscriptions = listOf(subscription, subscription)),
            settings.copy(radioPrograms = emptyList(), radioSourceSelections = mapOf("invalid" to "file:///music")))) {
            try { read(raw(backup().copy(settings = invalid))); fail("Invalid subscription backup accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    private val fire = SpectrumColors(0x123456, 0xabcdef, 0x654321)
    private val fullJson = Json { encodeDefaults = true }

    private fun legacy(light: String = "true", classic: String = Json.encodeToString(SpectrumColors.CLASSIC)): String {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(
            onlineLyrics = true, blur = 65, settingsTransition = TransitionStyle.ZOOM,
            spectrum = SpectrumOptions(colors = fire, refreshRate = 60))))
        return fullJson.encodeToString(value)
            .replace("\"display\":{", "\"display\":{\"lightMode\":$light,")
            .replace("\"spectrum\":{", "\"spectrum\":{\"classicColors\":$classic,")
    }

    @Test fun legacyBackupsKeepFireColorsAndDoNotApplyRetiredLightModeOverrides() {
        for (light in listOf("true", "false")) {
            val restored = read(raw(backup(), text = legacy(light)))
            assertEquals(fire, restored.settings.display.spectrum.colors)
            assertEquals(60, restored.settings.display.spectrum.refreshRate)
            assertTrue(restored.settings.display.onlineLyrics)
            assertEquals(65, restored.settings.display.blur)
            assertEquals(TransitionStyle.ZOOM, restored.settings.display.settingsTransition)
            val rewritten = fullJson.encodeToString(restored)
            assertFalse(rewritten.contains("\"lightMode\""))
            assertFalse(rewritten.contains("\"classicColors\""))
            assertTrue(rewritten.contains("\"fireColors\""))
        }
    }

    @Test fun invalidLegacyLightModeValuesAreNotSilentlyDiscarded() {
        for (value in listOf("null", "1", "\"true\"", "[]", "{}")) {
            try {
                read(raw(backup(), text = legacy(light = value)))
                fail("Invalid legacy value must be rejected: $value")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun invalidLegacyColorsAreNotSilentlyDiscarded() {
        for (value in listOf("null", "[]", "{\"low\":-1,\"middle\":0,\"high\":0}",
            "{\"low\":0,\"middle\":0,\"high\":0,\"unexpected\":true}")) {
            try {
                read(raw(backup(), text = legacy(classic = value)))
                fail("Invalid legacy colors must be rejected: $value")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test(expected = SerializationException::class) fun retiringKnownFieldsDoesNotAllowArbitraryUnknownSettings() {
        read(raw(backup(), text = legacy().replace("\"lightMode\":true", "\"futureSetting\":true")))
    }

    @Test fun olderBackupsKeepOmittedPlaybackAndTimeDefaults() {
        val restored = read(raw(backup()))
        assertEquals(LoopMode.OFF, restored.settings.playback.loop)
        assertEquals(PlayOrder.SEQUENTIAL, restored.settings.playback.order)
        assertEquals(PlaybackTimeMode.ELAPSED, restored.settings.display.timeMode)
        assertEquals(PlaybackTimeMode.ELAPSED, restored.settings.display.mainTimeMode)
        assertEquals(50, restored.settings.display.widgetTransparency)
        assertFalse(restored.settings.display.keepScreenOn)
        assertEquals(5, restored.settings.display.noticeDurationSeconds)
        assertNull(restored.settings.display.miniPlayerTransparency)
        assertNull(restored.settings.display.primaryControlColor)
        assertEquals(0, restored.settings.display.effectiveMiniPlayerTransparency)
        assertEquals(LyricCacheExpiry.THIRTY_DAYS, restored.settings.display.internalLyricExpiry)
        assertEquals(LyricCacheExpiry.NEVER, restored.settings.display.portableLyricExpiry)
        assertEquals(LyricAppearance(), restored.settings.display.lyricAppearance)
        assertNull(restored.settings.radio)
        assertNull(restored.settings.radioPrograms)
    }

    @Test fun compactPlayerKeepsLegacyThemeOpacityUntilExplicitlyChangedAndRejectsInvalidSettings() {
        assertEquals(100, DisplayOptions(customTheme = true, imageVersion = 1).effectiveMiniPlayerTransparency)
        assertEquals(0, DisplayOptions(customTheme = true).effectiveMiniPlayerTransparency)
        for (transparency in listOf(0, 50, 100)) {
            val display = DisplayOptions(customTheme = true, miniPlayerTransparency = transparency)
            assertEquals(transparency, display.effectiveMiniPlayerTransparency)
            assertEquals(display, read(raw(backup().copy(settings = settings.copy(display = display)))).settings.display)
        }
        for (seconds in DisplayOptions.NOTICE_DURATIONS) {
            val display = DisplayOptions(noticeDurationSeconds = seconds)
            assertEquals(seconds, read(raw(backup().copy(settings = settings.copy(display = display))))
                .settings.display.noticeDurationSeconds)
        }
        for (transparency in listOf(-1, 101)) assertThrows(IllegalArgumentException::class.java) {
            DisplayOptions(miniPlayerTransparency = transparency)
        }
        for (seconds in listOf(0, 4, 60)) assertThrows(IllegalArgumentException::class.java) {
            DisplayOptions(noticeDurationSeconds = seconds)
        }
    }

    @Test fun primaryControlColorKeepsAutomaticAndCustomValuesAcrossBackups() {
        for (enabled in listOf(true, false)) for (color in listOf(null, 0, 0xffffff, 0x13579b)) {
            val display = DisplayOptions(primaryControlColor = color, primaryControlHighlightEnabled = enabled)
            val output = ByteArrayOutputStream()
            BackupArchive.write(output, backup().copy(settings = settings.copy(display = display)), emptyMap())
            assertEquals(color, read(output.toByteArray()).settings.display.primaryControlColor)
            assertEquals(enabled, read(output.toByteArray()).settings.display.primaryControlHighlightEnabled)
        }
    }

    @Test fun radioFavoritesRoundTripWithoutSavingStreamUrlsOrSwitchingPlayback() {
        val value = backup().copy(settings = settings.copy(
            radio = RadioFavorites(CnrRadio.stations.take(3).map { it.id }.toSet(), true)))
        assertEquals(value, read(raw(value)))
        val text = fullJson.encodeToString(value)
        assertFalse(text.contains("m3u8"))
        assertFalse(text.contains("radioActive"))
    }

    @Test fun hiddenRadioPanelRoundTripsWithTheLegacyKeyAndOldBackupsDefaultToVisible() {
        val radio = RadioFavorites(setOf(CnrRadio.stations.first().id), favoritesOnly = true, showPanel = false)
        val value = backup().copy(settings = settings.copy(radio = radio))
        assertEquals(radio, read(raw(value)).settings.radio)
        val encoded = fullJson.encodeToString(value)
        assertTrue(encoded.contains("\"enabled\":false"))
        assertFalse(encoded.contains("\"showPanel\""))
        val oldJson = encoded.replace(",\"enabled\":false", "")
        val restored = checkNotNull(read(raw(value, text = oldJson)).settings.radio)
        assertTrue(restored.showPanel)
        assertEquals(radio.stations, restored.stations)
        assertTrue(restored.favoritesOnly)
    }

    @Test(expected = IllegalArgumentException::class) fun arbitraryRadioSourcesCannotEnterABackup() {
        read(raw(backup().copy(settings = settings.copy(radio = RadioFavorites(setOf("https://example.com/live"))))))
    }

    @Test fun editableRadioProgramsAndTheirFavoritesRoundTripWithoutPlaybackState() {
        val programs = RadioList.merge(emptyList(), listOf(RadioListEntry("Custom", "https://example.com/live")), emptyMap()).programs
        val value = backup().copy(settings = settings.copy(radioPrograms = programs,
            radio = RadioFavorites(setOf(programs.single().id, CnrRadio.stations.first().id))))
        assertEquals(value, read(raw(value)))
        assertFalse(fullJson.encodeToString(value).contains("radioActive"))
        val missing = value.copy(settings = value.settings.copy(radioPrograms = emptyList()))
        try { read(raw(missing)); fail("Dangling custom favorite accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun radioPinsSourcesAndLiveSettingsRoundTripWhileLegacyFormatRemainsReadable() {
        val station = CnrRadio.stations.first().program.copy(alternateUris = listOf("https://example.com/backup"),
            sourceIndex = 1, logoUri = "https://example.com/logo.png", group = "My group")
        val radio = RadioFavorites(pins = listOf(station.id), tuning = com.macarmusic.app.radio.RadioTuning(
            bufferSeconds = 60, startSeconds = 5, retries = 1, autoSource = true, downloadLogos = false))
        val value = backup().copy(settings = settings.copy(radioPrograms = listOf(station), radio = radio))
        assertEquals(value, read(raw(value)))
        assertEquals("ManaMusicPlayer", value.format)
        for (pins in listOf(List(13) { station.id }, listOf("radio:unknown"))) {
            try {
                read(raw(value.copy(settings = value.settings.copy(radio = radio.copy(pins = pins)))))
                fail("Invalid pins accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun allTwelveOrderedPinsRoundTripAndThirteenDistinctPinsAreRejected() {
        val ids = com.macarmusic.app.radio.RadioDefaults.corePrograms.take(13).map { it.id }
        val value = backup().copy(settings = settings.copy(radio = RadioFavorites(pins = ids.take(12).reversed())))
        assertEquals(value, read(raw(value)))
        try {
            read(raw(value.copy(settings = value.settings.copy(radio = RadioFavorites(pins = ids)))))
            fail("Thirteen distinct pins accepted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun newBundledChannelPinsRoundTripWithoutRequiringAUserOverride() {
        val station = com.macarmusic.app.radio.RadioDefaults.bundled.single { it.name == "RTHK Radio 1" }
        val value = backup().copy(settings = settings.copy(
            radio = RadioFavorites(stations = setOf(station.id), pins = listOf(station.id)),
            radioPrograms = emptyList()))
        assertEquals(value, read(raw(value)))
    }

    @Test fun radioPinColorsRoundTripAndOlderBackupsRetainThemeAwareDefaults() {
        val value = backup().copy(settings = settings.copy(
            radio = RadioFavorites(pins = listOf(CnrRadio.stations.first().id), pinColor = 0x123456)))
        assertEquals(value, read(raw(value)))
        val oldJson = fullJson.encodeToString(value).replace(",\"pinColor\":1193046", "")
        val restored = checkNotNull(read(raw(value, text = oldJson)).settings.radio)
        assertNull(restored.pinColor)
        assertEquals(value.settings.radio!!.pins, restored.pins)
    }

    @Test fun navigationAndRadioSelectionChoicesRoundTripWithCompatibleLegacyDefaults() {
        for (bits in 0..7) {
            val display = settings.display.copy(showLibrary = bits and 1 != 0, showQueue = bits and 2 != 0,
                showPlaylists = bits and 4 != 0)
            val value = backup().copy(settings = settings.copy(display = display,
                radio = RadioFavorites(openPlayerOnSelection = true)))
            assertEquals(value, read(raw(value)))
        }
        val value = backup().copy(settings = settings.copy(
            display = settings.display.copy(showLibrary = false, showQueue = false, showPlaylists = false),
            radio = RadioFavorites(openPlayerOnSelection = true)))
        val legacy = fullJson.encodeToString(value).replace(",\"showLibrary\":false", "")
            .replace(",\"showQueue\":false", "").replace(",\"showPlaylists\":false", "")
            .replace(",\"openPlayerOnSelection\":true", "")
        val restored = read(raw(value, text = legacy)).settings
        assertTrue(restored.display.showLibrary)
        assertTrue(restored.display.showQueue)
        assertTrue(restored.display.showPlaylists)
        assertFalse(checkNotNull(restored.radio).openPlayerOnSelection)
    }

    @Test(expected = IllegalArgumentException::class) fun invalidRadioPinColorIsRejectedByBackupImport() {
        val value = backup().copy(settings = settings.copy(radio = RadioFavorites(pinColor = 0x123456)))
        read(raw(value, text = fullJson.encodeToString(value).replace("\"pinColor\":1193046", "\"pinColor\":16777216")))
    }

    @Test fun replacementCatalogBackupRoundTripsWithoutAddingUnlistedBuiltins() {
        val stations = RadioList.merge(emptyList(), listOf(RadioListEntry("Only", "https://example.com/live")),
            emptyMap()).programs
        val value = backup().copy(settings = settings.copy(
            radioPrograms = stations, radioIncludeDefaults = false,
            radio = RadioFavorites(pins = listOf(stations.single().id))))
        assertEquals(value, read(raw(value)))
        val legacy = fullJson.encodeToString(value).replace(",\"radioIncludeDefaults\":false", "")
        assertTrue(read(raw(value, text = legacy)).settings.radioIncludeDefaults)
    }

    @Test fun hiddenCountriesAndRemovedEmbeddedStationsRoundTripInTheBackup() {
        val removed = CnrRadio.stations.first().id
        val value = backup().copy(settings = settings.copy(radioPrograms = emptyList(),
            radioRemovedDefaults = setOf(removed),
            radio = RadioFavorites(pins = emptyList(), hiddenGroups = setOf("united states", "country:ca"))))
        assertEquals(value, read(raw(value)))
        try {
            read(raw(value.copy(settings = value.settings.copy(radio = RadioFavorites(pins = listOf(removed))))))
            fail("A deleted embedded pin was accepted")
        } catch (_: IllegalArgumentException) { }
        val legacy = fullJson.encodeToString(value).replace(",\"radioRemovedDefaults\":[\"$removed\"]", "")
            .replace(",\"hiddenGroups\":[\"united states\",\"country:ca\"]", "")
        val restored = read(raw(value, text = legacy)).settings
        assertTrue(restored.radioRemovedDefaults.isEmpty())
        assertTrue(checkNotNull(restored.radio).hiddenGroups.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun replacementBackupRejectsPinsForMissingBuiltins() {
        val invalid = backup().copy(settings = settings.copy(radioPrograms = emptyList(), radioIncludeDefaults = false,
            radio = RadioFavorites(pins = listOf(CnrRadio.stations.first().id))))
        read(raw(invalid))
    }

    @Test(expected = IllegalArgumentException::class) fun replacementBackupMustIncludeItsCatalog() {
        read(raw(backup().copy(settings = settings.copy(radioIncludeDefaults = false))))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidKaraokeColorIsRejectedByBackupImport() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(
            lyricAppearance = LyricAppearance(karaoke = true, karaokeColor = 0x123456))))
        read(raw(value, text = Json.encodeToString(value).replace("\"karaokeColor\":1193046", "\"karaokeColor\":-1")))
    }

    @Test fun imageDataIsStoredOnlyUnderValidatedPrivateNames() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        val file = temp.newFile("input.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val output = ByteArrayOutputStream()
        BackupArchive.write(output, value, mapOf(imageName to file))
        val directory = temp.newFolder()
        assertEquals(value, BackupArchive.read(ByteArrayInputStream(output.toByteArray()), directory))
        assertArrayEquals(file.readBytes(), File(directory, imageName).readBytes())
    }

    @Test(expected = IllegalArgumentException::class) fun unknownVersionIsRejectedBeforeExtraction() {
        read(raw(backup().copy(version = 2)))
    }

    @Test(expected = IllegalArgumentException::class) fun unrelatedZipIsRejected() {
        read(raw(backup().copy(format = "Other product")))
    }

    @Test fun traversalEntryCannotWriteOutsideTheStagingDirectory() {
        val outside = File(temp.root, "outside")
        try {
            read(raw(backup(), mapOf("../outside" to byteArrayOf(1))))
            fail("Traversal must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertFalse(outside.exists())
    }

    @Test(expected = IllegalArgumentException::class) fun unexpectedFilesAreRejected() {
        read(raw(backup(), mapOf("music.mp3" to byteArrayOf(1))))
    }

    @Test(expected = IllegalArgumentException::class) fun missingImagesAreRejected() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        read(raw(value))
    }

    @Test(expected = IOException::class) fun oversizedImageIsBoundedWhileReading() {
        val value = backup().copy(settings = settings.copy(display = DisplayOptions(imageVersion = 1, imageIds = listOf(imageName))))
        read(raw(value, mapOf("images/$imageName" to ByteArray(BackupArchive.MAX_IMAGE_BYTES + 1))))
    }

    @Test(expected = IOException::class) fun highlyCompressedMetadataStillHasADecompressedLimit() {
        read(raw(backup(), text = " ".repeat(BackupArchive.MAX_METADATA_BYTES + 1)))
    }

    @Test(expected = SerializationException::class) fun malformedJsonIsNotAcceptedAsAnEmptyBackup() {
        read(raw(backup(), text = "{broken"))
    }

    @Test(expected = IllegalArgumentException::class) fun remoteUrisCannotBeImported() {
        read(raw(backup().copy(library = library.copy(tracks = listOf(song.copy(uri = "https://example.invalid/song.mp3"))))))
    }

    @Test(expected = IllegalArgumentException::class) fun danglingPlaylistEntriesAreRejected() {
        val broken = library.copy(playlists = listOf(LocalPlaylist("p", "Mix", listOf("missing"))))
        read(raw(backup().copy(library = broken)))
    }

    @Test(expected = IllegalArgumentException::class) fun duplicateTrackIdsAreRejected() {
        read(raw(backup().copy(library = library.copy(tracks = listOf(song, song)))))
    }

    @Test(expected = IllegalArgumentException::class) fun unknownSettingsEnumsAreRejected() {
        read(raw(backup(), text = Json.encodeToString(backup()).replace("\"ALBUM\"", "\"FUTURE_SORT\"")))
    }

    @Test fun cancelledCopyDoesNotReturnAValidBackup() {
        try {
            BackupArchive.read(ByteArrayInputStream(raw(backup())), temp.newFolder()) { throw IOException("cancelled") }
            fail("Cancellation must abort")
        } catch (error: IOException) { assertEquals("cancelled", error.message) }
    }
}
