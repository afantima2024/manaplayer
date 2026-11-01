package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import org.hamcrest.Matchers.equalTo
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.NavigationPosition
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.RadioDataSource
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.withCollection
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.radio.RadioSettings
import com.macarmusic.app.radio.RadioAddress
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioImportMode
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioDefaults
import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioTuning
import com.macarmusic.app.radio.RadioLogoRepository
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.QueueMode
import com.macarmusic.app.ui.SettingsCategory
import com.macarmusic.app.data.DisplaySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RadioIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val network = FixtureRadioNetwork.install()
    private val root = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
    private val audio = DocumentsContract.buildDocumentUriUsingTree(root, "music/one").toString()
    private val tracks = List(3) { Track("cnr-music-$it", audio, "local-$it.wav", "Local $it", durationMs = 60_000) }
    private lateinit var controller: MediaController

    @Before fun prepare() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true)
            .putStringSet("radioFavorites", emptySet()).putBoolean("radioFavoritesOnly", false)
            .putBoolean("radioEnabled", true).remove("radioPins").remove("radioTuning").remove("radioPinColor")
            .remove("radioOpenPlayerOnSelection").commit()
        instrumentation.context.startActivity(Intent().setClassName("${app.packageName}.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        runBlocking { app.repository.awaitReady() }
        onMain {
            app.radio.publishRestored()
            app.notice.value?.let { app.dismissNotice(it.id) }
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false,
                navigationPosition = NavigationPosition.BOTTOM, showLibrary = true, showQueue = true, showPlaylists = true) }
        }
        controller = connect()
        command(RadioCommands.music)
        runBlocking { app.radioCatalog.withReplacement(emptyList()) { Unit } }
        onMain {
            controller.pause()
            app.playbackSettings.setLoop(LoopMode.SINGLE_FILE)
            app.playbackSettings.setOrder(PlayOrder.RANDOM)
            app.playbackSettings.setMuted(true)
        }
        runBlocking(Dispatchers.IO) {
            app.repository.update { Library(tracks = tracks, playlists = listOf(
                LocalPlaylist("cnr-fixture", "Saved music", tracks.take(2).map { it.id }))) }
        }
        onMain {
            controller.setMediaItems(tracks.take(2).mapIndexed { index, track ->
                if (index == 0) track.toMediaItem().withCollection(PlaybackCollection.playlist("cnr-fixture"))
                else track.toMediaItem()
            }, 1, 17_000)
        }
        await { controller.mediaItemCount == 2 && controller.currentMediaItem?.mediaId == tracks[1].id }
        await { network.active.get() == 0 }
        runBlocking { app.radioLogos.clear() }
        network.reset()
    }

    @After fun cleanup() {
        if (::controller.isInitialized && onMain { controller.isConnected }) {
            onMain { controller.pause() }
            command(RadioCommands.music)
            onMain { controller.release() }
        }
        await { network.active.get() == 0 }
        stopService()
        runBlocking { app.radioCatalog.withReplacement(emptyList()) { Unit } }
        network.failStreams = 0
        network.redirect = null
        network.customPlaylist = null
        onMain {
            app.radio.setOpenPlayerOnSelection(false)
            app.displaySettings.update { it.copy(showLibrary = true, showQueue = true, showPlaylists = true) }
        }
    }

    @Test fun livePlaybackStopsConnectionsAndRestoresMusicPositionOrderAndCollection() {
        playStation(1)
        assertTrue(network.totalCatalogues.get() >= 1)
        assertTrue(network.manifests.get() >= 1)
        assertTrue(network.segments.get() >= 1)
        assertTrue(network.requests.filter { it.startsWith("ngcdn") }.all { it.startsWith("ngcdn002.") })
        val saved = checkNotNull(app.radio.savedMusic.value)
        assertEquals(tracks.take(2).map { it.id }, saved.queue.map { it.id })
        assertEquals(1, saved.index)
        assertEquals(17_000L, saved.positionMs)
        assertEquals(PlaybackCollection.playlist("cnr-fixture"), saved.collection)
        assertEquals(saved.queue, runBlocking { app.playback.read() }.queue)
        onMain {
            assertFalse(controller.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_OFF, controller.repeatMode)
            assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_BACK))
            assertFalse(controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
            controller.pause()
        }
        await { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady && network.active.get() == 0 }
        val stopped = network.requests.size
        SystemClock.sleep(1200)
        assertEquals(stopped, network.requests.size)
        onMain { controller.play() }
        await { controller.isPlaying }
        assertTrue(network.requests.size > stopped)
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id && controller.mediaItemCount == 2 }
        onMain {
            assertFalse(controller.playWhenReady)
            assertEquals(Player.STATE_IDLE, controller.playbackState)
            assertEquals(17_000L, controller.currentPosition)
            assertTrue(controller.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
        }
        assertNull(app.radio.savedMusic.value)
        assertFalse(app.radio.options.value.active)
    }

    @Test fun platformPreviousSwitchesStationsRatherThanSeekingInsideTheBroadcast() {
        playStation(1)
        val connected = CountDownLatch(1)
        lateinit var browser: MediaBrowser
        onMain {
            browser = MediaBrowser(app, ComponentName(app, PlaybackService::class.java),
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() { connected.countDown() }
                    override fun onConnectionFailed() { connected.countDown() }
                }, Bundle.EMPTY)
            browser.connect()
        }
        try {
            assertTrue(connected.await(15, TimeUnit.SECONDS))
            onMain {
                assertTrue(browser.isConnected)
                assertTrue(controller.currentPosition > 3000)
                android.media.session.MediaController(app, browser.sessionToken).transportControls.skipToPrevious()
            }
            await { controller.currentMediaItem?.mediaId == CnrRadio.stations.first().id && controller.isPlaying }
            onMain { android.media.session.MediaController(app, browser.sessionToken).transportControls.pause() }
            await { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady }
        } finally { onMain { browser.disconnect() } }
    }

    @Test fun reselectingCurrentStationSourceAndQueueItemKeepsTheExistingConnection() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Uninterrupted,https://radio.example/current/index.m3u8\n" +
            "Uninterrupted,https://radio.example/alternate/index.m3u8").single()
        playProgram(station)
        val firstManifest = network.manifests.get()
        val savedMusic = app.radio.savedMusic.value
        var interruptions = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) interruptions++ }
        }
        onMain { controller.addListener(listener) }
        try {
            repeat(5) { command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) }) }
            repeat(5) { command(RadioCommands.play, Bundle().apply {
                putString(RadioCommands.STATION, station.id)
                putInt(RadioCommands.SOURCE, 0)
                putString(RadioCommands.SOURCE_URI, station.streamUri)
            }) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            assertEquals("Repeated selections must not reopen the station", firstManifest, network.manifests.get())
            onMain { assertTrue(controller.isPlaying); assertEquals("Station/source taps interrupted playback", 0, interruptions) }
            assertEquals(savedMusic, app.radio.savedMusic.value)
        } finally { onMain { controller.removeListener(listener) } }
        onMain {
            controller.seekToDefaultPosition(controller.currentMediaItemIndex)
            controller.play()
        }
        await { controller.isPlaying }
        // Controllers can mask a seek as buffering before the session confirms the unchanged source.
        SystemClock.sleep(300)
        assertEquals("Selecting the current radio queue item must retain its connection", firstManifest, network.manifests.get())
        command(RadioCommands.play, Bundle().apply {
            putString(RadioCommands.STATION, station.id)
            putInt(RadioCommands.SOURCE, 1)
            putString(RadioCommands.SOURCE_URI, station.sources[1])
        })
        await { controller.isPlaying && app.radioCatalog.find(station.id)?.sourceIndex == 1 }
        assertEquals(firstManifest + 1, network.manifests.get())
        onMain { controller.pause() }
        await { controller.playbackState == Player.STATE_IDLE }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { controller.isPlaying }
        assertEquals("Selecting a stopped station must reconnect", firstManifest + 2, network.manifests.get())
    }

    @Test fun rapidReselectionDuringBufferingKeepsThePendingConnection() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Connecting,https://radio.example/connecting/index.m3u8").single()
        network.manifestStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        network.manifestRelease = release
        try {
            command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
            assertTrue(network.manifestStarted.await(5, TimeUnit.SECONDS))
            await { controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady }
            val requests = onMain { List(8) {
                controller.sendCustomCommand(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
            } }
            requests.forEach { assertEquals(SessionResult.RESULT_SUCCESS, it.get(10, TimeUnit.SECONDS).resultCode) }
            onMain { controller.seekToDefaultPosition(controller.currentMediaItemIndex); controller.play() }
            instrumentation.waitForIdleSync()
            assertEquals(1, network.manifests.get())
        } finally { release.countDown(); network.manifestRelease = null }
        await { controller.isPlaying }
        assertEquals("Buffering taps must not start a second connection", 1, network.manifests.get())
    }

    @Test fun reselectingFailedStationRetriesButDifferentStationOnTheSameUrlStillSwitches() {
        network.customPlaylist = finitePlaylist()
        val programs = importPrograms("Retry selection,https://radio.example/retry/index.m3u8\n" +
            "Different identity,https://radio.example/retry/index.m3u8")
        onMain { app.radio.tune(RadioTuning(retries = 0, autoSource = false)) }
        network.failStreams = 503
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, programs[0].id) })
        await { controller.playerError != null && !controller.playWhenReady }
        network.failStreams = 0
        playProgram(programs[0])
        val connections = network.manifests.get()
        playProgram(programs[1])
        assertEquals(connections + 1, network.manifests.get())
        assertEquals(programs[1].id, onMain { controller.currentMediaItem?.mediaId })
    }

    @Test fun removingMusicDuringRadioReconcilesOnlyTheSuspendedQueue() {
        playStation(0)
        runBlocking(Dispatchers.IO) {
            app.repository.update { it.copy(tracks = listOf(tracks[0].copy(title = "Updated title")), playlists = emptyList()) }
        }
        await { app.radio.savedMusic.value?.queue?.singleOrNull()?.title == "Updated title" }
        onMain {
            assertEquals(CnrRadio.stations.first().id, controller.currentMediaItem?.mediaId)
            assertEquals(RadioDefaults.bundled.size, controller.mediaItemCount)
            assertTrue(controller.isPlaying)
        }
        command(RadioCommands.music)
        await { controller.mediaItemCount == 1 && controller.currentMediaItem?.mediaMetadata?.title == "Updated title" }
        onMain { assertFalse(controller.playWhenReady) }
    }

    @Test fun laterStationCommandWinsOverAnInFlightLargeQueueRestore() {
        val many = List(1000) { tracks[0].copy(id = "many-$it", title = "Song $it") }
        runBlocking(Dispatchers.IO) { app.repository.update { Library(tracks = many) } }
        onMain { controller.setMediaItems(many.map(Track::toMediaItem), 500, 12_000) }
        await { controller.mediaItemCount == 1000 }
        playStation(0)
        val operations = onMain {
            listOf(
                controller.sendCustomCommand(RadioCommands.music, Bundle.EMPTY),
                controller.sendCustomCommand(RadioCommands.play, stationArguments(2)),
            )
        }
        operations.forEach { assertEquals(SessionResult.RESULT_SUCCESS, it.get(20, TimeUnit.SECONDS).resultCode) }
        await { controller.currentMediaItem?.mediaId == CnrRadio.stations[2].id && controller.isPlaying }
        assertEquals(1000, app.radio.savedMusic.value?.queue?.size)
        command(RadioCommands.music)
        await { controller.mediaItemCount == 1000 && controller.currentMediaItemIndex == 500 }
        onMain { assertEquals(12_000L, controller.currentPosition); assertFalse(controller.playWhenReady) }
    }

    @Test fun serviceRestartRestoresRadioPausedWithoutFetchingAndKeepsFavorites() {
        playStation(3)
        onMain {
            app.radio.favorite(CnrRadio.stations[3])
            app.radio.filter(true)
            controller.pause()
        }
        await { controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
        onMain { controller.release() }
        stopService()
        val before = network.requests.size
        controller = connect()
        await { controller.currentMediaItem?.mediaId == CnrRadio.stations[3].id }
        SystemClock.sleep(1200)
        onMain {
            assertFalse(controller.playWhenReady)
            assertEquals(Player.STATE_IDLE, controller.playbackState)
            assertEquals("Legacy favorites-only preferences must not hide unpinned stations",
                app.radioCatalog.state.value.programs.size, controller.mediaItemCount)
        }
        assertEquals(before, network.requests.size)
        assertEquals(app.radio.options.value, RadioSettings(app).options.value)
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id }
        onMain { assertEquals(17_000L, controller.currentPosition) }
    }

    @Test fun deniedStreamsStopOnceWithoutTryingOtherStationsAndCanBeRetried() {
        network.failStreams = 403
        command(RadioCommands.play, stationArguments(2))
        await { controller.playerError != null && !controller.playWhenReady }
        assertEquals(1, network.manifests.get())
        assertEquals(0, network.segments.get())
        val requests = network.requests.size
        SystemClock.sleep(1500)
        assertEquals(requests, network.requests.size)
        network.failStreams = 0
        onMain { controller.play() }
        await { controller.isPlaying }
        assertEquals(CnrRadio.stations[2].id, onMain { controller.currentMediaItem?.mediaId })
    }

    @Test fun backupRestoresRadioFavoritesWithoutSwitchingTheActiveSourceOrSavingStreamUrls() {
        onMain {
            app.radio.favorite(CnrRadio.stations[1])
            app.radio.filter(true)
            app.radio.setOpenPlayerOnSelection(true)
            app.displaySettings.update { it.copy(showLibrary = false, showQueue = false, showPlaylists = false) }
        }
        val uri = runBlocking { app.backups.sharedExport("en") }
        val backup = runBlocking { app.backups.prepare(uri) }
        assertEquals(setOf(CnrRadio.stations[1].id), backup.manifest.settings.radio?.stations)
        assertEquals(true, backup.manifest.settings.radio?.favoritesOnly)
        assertEquals(true, backup.manifest.settings.radio?.openPlayerOnSelection)
        assertFalse(backup.manifest.settings.display.showLibrary)
        assertFalse(backup.manifest.settings.display.showQueue)
        assertFalse(backup.manifest.settings.display.showPlaylists)
        onMain {
            app.radio.favorite(CnrRadio.stations[1])
            app.radio.filter(false)
            app.radio.setOpenPlayerOnSelection(false)
            app.displaySettings.update { it.copy(showLibrary = true, showQueue = true, showPlaylists = true) }
        }
        playStation(2)
        onMain { controller.pause() }
        await { controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
        val before = network.requests.size
        runBlocking { app.backups.restore(backup) }
        assertEquals(setOf(CnrRadio.stations[1].id), app.radio.options.value.favorites)
        assertTrue(app.radio.options.value.favoritesOnly)
        assertTrue(app.radio.options.value.openPlayerOnSelection)
        val display = DisplaySettings(app) {}.options.value
        assertFalse(display.showLibrary)
        assertFalse(display.showQueue)
        assertFalse(display.showPlaylists)
        assertEquals(display, app.displaySettings.options.value)
        assertTrue(app.radio.options.value.active)
        assertEquals(CnrRadio.stations[2].id, app.radio.options.value.stationId)
        onMain {
            assertEquals(CnrRadio.stations[2].id, controller.currentMediaItem?.mediaId)
            assertFalse(controller.playWhenReady)
        }
        assertEquals(before, network.requests.size)
        assertEquals(app.radio.options.value, RadioSettings(app).options.value)
    }

    @Test fun redirectsAreCheckedBeforeConnectingAndByteRangesAreBounded() {
        val station = CnrRadio.stations.first()
        val source = RadioDataSource({ it })
        network.redirect = "https://example.com/forbidden.m3u8"
        try {
            source.open(DataSpec.Builder().setUri(station.uri).build())
            fail("Unapproved redirect accepted")
        } catch (_: IOException) { } finally { source.close() }
        assertEquals(0, network.active.get())
        assertTrue(network.requests.none { it.startsWith("example.com") })
        network.redirect = "redirected.m3u8"
        source.open(DataSpec.Builder().setUri(station.uri).build())
        assertTrue(source.uri.toString().endsWith("/redirected.m3u8"))
        source.close()
        network.redirect = null
        val bytes = ByteArray(20)
        val segment = station.uri.substringBeforeLast('/') + "/tone0.aac"
        assertEquals(20L, source.open(DataSpec.Builder().setUri(segment).setPosition(7).setLength(20).build()))
        assertEquals(20, source.read(bytes, 0, bytes.size))
        assertEquals(-1, source.read(bytes, 0, bytes.size))
        source.close()
        assertEquals(0, network.active.get())
    }

    private fun importPrograms(text: String): List<RadioProgram> = runBlocking {
        app.radioCatalog.merge(RadioList.parse(text.byteInputStream()).entries, emptyMap()).programs
    }

    private fun playProgram(program: RadioProgram) {
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, program.id) })
        await { controller.currentMediaItem?.mediaId == program.id && controller.isPlaying }
    }

    @Test fun importedHttpHlsPlaysWithoutCnrResolutionAndUpdatesStopItWithoutLosingMusic() {
        val original = importPrograms("Custom,http://radio.example/live/index.m3u8").single()
        assertEquals(0, network.requests.size)
        playProgram(original)
        assertEquals(0, network.catalogues.get())
        assertTrue(network.segments.get() > 0)
        assertFalse(onMain { controller.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) })
        onMain { app.radio.favorite(original) }
        val updated = importPrograms("${original.id}\tRenamed\thttps://radio.example/new/index.m3u8").single()
        await { !controller.playWhenReady && controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
        assertEquals(original.id, onMain { controller.currentMediaItem?.mediaId })
        val requests = network.requests.size
        SystemClock.sleep(800)
        assertEquals(requests, network.requests.size)
        assertTrue(original.id in app.radio.options.value.favorites)
        playProgram(updated)
        assertTrue(network.requests.contains("radio.example/new/index.m3u8"))
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id && controller.mediaItemCount == 2 }
        onMain {
            assertEquals(17_000L, controller.currentPosition)
            assertFalse(controller.playWhenReady)
            assertTrue(controller.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
        }
    }

    @Test fun overriddenBuiltinUsesImportedAddressAndResetRestoresAllDefaultsWhileKeepingCustoms() {
        val builtin = CnrRadio.stations.first()
        val saved = importPrograms("${builtin.id}\tOverridden\thttps://radio.example/live/index.m3u8\n" +
            "Custom,https://radio.example/second/index.m3u8")
        val custom = saved.last()
        onMain { app.radio.favorite(custom) }
        playProgram(saved.first())
        assertEquals(0, network.catalogues.get())
        runBlocking { app.radioCatalog.resetBuiltins() }
        await { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady && network.active.get() == 0 }
        assertEquals(RadioDefaults.bundled.size + 1, onMain { controller.mediaItemCount })
        assertEquals(builtin.program, app.radioCatalog.find(builtin.id))
        assertEquals(custom, app.radioCatalog.find(custom.id))
        assertTrue(custom.id in app.radio.options.value.favorites)
        playStation(0)
        assertEquals(1, network.catalogues.get())
    }

    @Test fun customCatalogBackupAndServiceRestartRestorePausedWithoutAFileGrantOrNetwork() {
        val program = importPrograms("Custom,https://radio.example/live/index.m3u8").single()
        onMain { app.radio.favorite(program) }
        val uri = runBlocking { app.backups.sharedExport("en") }
        val backup = runBlocking { app.backups.prepare(uri) }
        assertEquals(listOf(program), backup.manifest.settings.radioPrograms)
        playProgram(program)
        onMain { controller.pause() }
        await { network.active.get() == 0 && controller.playbackState == Player.STATE_IDLE }
        onMain { controller.release() }
        stopService()
        val before = network.requests.size
        controller = connect()
        await { controller.currentMediaItem?.mediaId == program.id }
        onMain { assertFalse(controller.playWhenReady); assertEquals(Player.STATE_IDLE, controller.playbackState) }
        assertEquals(before, network.requests.size)
        assertEquals(app.radio.options.value, RadioSettings(app).options.value)
        importPrograms("${program.id}\tModified\thttps://radio.example/changed/index.m3u8")
        runBlocking { app.backups.restore(backup) }
        await { app.radioCatalog.find(program.id) == program && !controller.playWhenReady }
        assertEquals(before, network.requests.size)
        assertTrue(program.id in app.radio.options.value.favorites)
    }

    @Test fun progressiveAudioStopsAtEndRatherThanAutoplayingAnotherStation() {
        val programs = importPrograms("First,http://radio.example/live/tone0.aac\nNext,http://radio.example/next/tone0.aac")
        playProgram(programs.first())
        await { !controller.playWhenReady && controller.playbackState == Player.STATE_IDLE }
        assertEquals(programs.first().id, onMain { controller.currentMediaItem?.mediaId })
        assertEquals(0, network.catalogues.get())
        assertTrue(network.requests.none { it.contains("/next/") })
        assertEquals(0, network.active.get())
    }

    @Test fun importsDoNotInterruptLocalMusicAndUnknownRemoteItemsCannotBecomeSongs() {
        onMain { controller.prepare(); controller.play() }
        await { controller.isPlaying }
        val position = onMain { controller.currentPosition }
        importPrograms("Custom,https://radio.example/live/index.m3u8")
        runBlocking { app.radioCatalog.resetBuiltins() }
        onMain {
            assertTrue(controller.isPlaying)
            assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
            assertTrue(controller.currentPosition >= position)
        }
        val commandResult = onMain {
            controller.sendCustomCommand(RadioCommands.play, Bundle().apply {
                putString(RadioCommands.STATION, "radio:custom:" + "a".repeat(64))
            })
        }.get(20, TimeUnit.SECONDS)
        assertNotEquals(SessionResult.RESULT_SUCCESS, commandResult.resultCode)
        assertEquals(0, network.requests.size)
    }

    @Test fun exportAndFailedWritesNeverChangeTheCatalogOrInterruptLocalPlayback() {
        val cache = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.cache", "root")
        val output = DocumentsContract.createDocument(app.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(cache, "root"), "audio/x-mpegurl", "Export-${System.nanoTime()}.m3u")!!
        importPrograms("Offline music,https://radio.example/first\nOffline music,https://radio.example/second")
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { controller.prepare(); controller.play() }
            await { controller.isPlaying }
            val catalog = app.radioCatalog.state.value
            onMain { model.exportRadioList(output) }
            await { !model.radioImportBusy.value && app.notice.value?.text ==
                app.text(R.string.radio_export_done, catalog.programs.size, catalog.programs.sumOf { it.sources.size }) }
            val bytes = app.contentResolver.openInputStream(output)!!.use { stream -> stream.readBytes() }
            val parsed = RadioList.parse(bytes.inputStream())
            assertEquals(catalog.programs.sumOf { station -> station.sources.size }, parsed.entries.size)
            assertSame(catalog, app.radioCatalog.state.value)
            assertEquals(0, network.requests.size)
            onMain { assertTrue(controller.isPlaying); assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId) }
            app.contentResolver.call(cache, "cacheFixture", null, Bundle().apply { putInt("failAfterWrites", 0) })
            try {
                onMain { app.notice.value?.let { notice -> app.dismissNotice(notice.id) }; model.exportRadioList(output) }
                await { !model.radioImportBusy.value && app.notice.value?.isError == true }
                assertArrayEquals(bytes, app.contentResolver.openInputStream(output)!!.use { stream -> stream.readBytes() })
                assertSame(catalog, app.radioCatalog.state.value)
                assertEquals(0, network.requests.size)
                onMain { assertTrue(controller.isPlaying) }
            } finally { app.contentResolver.call(cache, "cacheFixture", null, Bundle()) }
            app.contentResolver.openOutputStream(output, "wt")!!.use { stream ->
                stream.write("#EXTM3U\n#EXTINF:-1,Broken\nfile:///not-radio.mp3".toByteArray())
            }
            onMain { model.previewRadioList(output) }
            await { model.preparedRadioImport.value != null && !model.radioImportBusy.value }
            assertTrue(model.preparedRadioImport.value!!.parsed.entries.isEmpty())
            onMain { model.importRadioList(RadioImportMode.REPLACE_ALL) }
            assertSame(catalog, app.radioCatalog.state.value)
            onMain { model.dismissRadioImport(); assertTrue(controller.isPlaying) }
        }
    }

    @Test fun replaceAllStopsRemovedStationPreservesMusicAndSurvivesBackupRestore() {
        val cache = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.cache", "root")
        val input = DocumentsContract.createDocument(app.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(cache, "root"), "text/plain", "Replace-${System.nanoTime()}.txt")!!
        app.contentResolver.openOutputStream(input, "wt")!!.use {
            it.write("Only imported,https://radio.example/new/index.m3u8".toByteArray())
        }
        playStation(0)
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.previewRadioList(input) }
            await { model.preparedRadioImport.value != null && !model.radioImportBusy.value }
            onMain { model.importRadioList(RadioImportMode.REPLACE_ALL) }
            await { !model.radioImportBusy.value && controller.mediaItemCount == 1 &&
                !controller.playWhenReady && controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
            val station = app.radioCatalog.state.value.programs.single()
            assertEquals("Only imported", station.name)
            assertFalse(app.radioCatalog.state.value.includeDefaults)
            assertTrue(app.radio.options.value.pins.isEmpty())
            assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
            assertEquals(tracks.take(2).map { track -> track.id }, app.radio.savedMusic.value!!.queue.map { track -> track.id })
            val calls = network.requests.size
            val backup = runBlocking { app.backups.prepare(app.backups.sharedExport("en")) }
            assertFalse(backup.manifest.settings.radioIncludeDefaults)
            runBlocking { app.radioCatalog.resetBuiltins() }
            await { controller.mediaItemCount == RadioDefaults.bundled.size + 1 }
            runBlocking { app.backups.restore(backup) }
            await { controller.mediaItemCount == 1 && !controller.playWhenReady }
            assertEquals(listOf(station), app.radioCatalog.state.value.programs)
            assertFalse(app.radioCatalog.state.value.includeDefaults)
            assertEquals(calls, network.requests.size)
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id && controller.mediaItemCount == 2 }
            onMain { assertEquals(17_000L, controller.currentPosition); assertFalse(controller.playWhenReady) }
        }
    }

    @Test fun anEmptyRestoredCatalogReturnsToMusicInsteadOfCrashingOrAutoplaying() {
        playStation(0)
        runBlocking { app.radioCatalog.withReplacement(emptyList(), includeDefaults = false) { Unit } }
        await { controller.currentMediaItem?.mediaId == tracks[1].id && !controller.playWhenReady &&
            !app.radio.options.value.active && network.active.get() == 0 }
        assertTrue(app.radioCatalog.state.value.programs.isEmpty())
        onMain { assertEquals(17_000L, controller.currentPosition); assertEquals(2, controller.mediaItemCount) }
    }

    @Test fun importedTransportRejectsHttpsDowngradesCredentialsAndNonNetworkRedirects() {
        for (redirect in listOf("http://cdn.radio.example/live.m3u8",
            "file:///private", "https://user:pass@cdn.radio.example/live.m3u8")) {
            network.redirect = redirect
            val source = RadioDataSource(resolve = { it }, allowed = { RadioAddress.permits(it, allowHttp = true) })
            try {
                source.open(DataSpec.Builder().setUri("https://radio.example/live/index.m3u8").build())
                fail("Unsafe redirect accepted")
            } catch (_: IOException) { } finally { source.close() }
            assertEquals(0, network.active.get())
        }
        assertTrue(network.requests.none { it.startsWith("cdn.radio.example") })
        network.redirect = null
        network.customPlaylist = "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4,\nhttp://cdn.radio.example/tone0.aac\n"
        val program = importPrograms("Secure,https://radio.example/live/index.m3u8").single()
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, program.id) })
        await { controller.playerError != null && !controller.playWhenReady }
        assertTrue(network.requests.none { it.startsWith("cdn.radio.example") })
    }

    @Test fun optionalNavigationSwitchesRetainPlaybackAndRequiredPagesAcrossRecreationAndDockPositions() {
        val library = app.repository.library.value
        onMain { controller.prepare(); controller.play() }
        await { controller.isPlaying }
        val discontinuities = mutableListOf<Int>()
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                discontinuities.add(reason)
            }
        }
        onMain { controller.addListener(listener) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var activity = settledMain()
                var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                val switches = listOf(R.id.nav_library to R.id.settingsShowLibrary,
                    R.id.nav_queue to R.id.settingsShowQueue, R.id.nav_playlists to R.id.settingsShowPlaylists)
                val buttons = onMain { switches.map { activity.findViewById<View>(it.first) } }
                for ((page, toggle) in switches) {
                    onMain {
                        activity.findViewById<View>(page).performClick()
                        assertEquals(page, model.selectedScreen.value)
                        model.settingsCategory(SettingsCategory.GENERAL)
                        model.openSettings()
                    }
                    await { activity.findViewById<MaterialSwitch>(toggle)?.isChecked == true }
                    onView(withId(toggle)).perform(scrollTo(), click())
                    await { activity.findViewById<View>(page).visibility == View.GONE }
                    onMain { model.closeSettings() }
                    await { model.selectedScreen.value == R.id.nav_player }
                    onMain {
                        model.screen(page)
                        assertEquals(R.id.nav_player, model.selectedScreen.value)
                        assertTrue(controller.isPlaying)
                    }
                }
                assertFalse(DisplaySettings(app) {}.options.value.showLibrary)
                assertFalse(DisplaySettings(app) {}.options.value.showQueue)
                assertFalse(DisplaySettings(app) {}.options.value.showPlaylists)
                onMain {
                    switches.forEachIndexed { index, (page, _) ->
                        assertSame("Visibility toggles must reuse navigation views", buttons[index], activity.findViewById<View>(page))
                    }
                }
                onMain { model.setRadioEnabled(false) }
                await { activity.findViewById<View>(R.id.nav_radio).visibility == View.GONE }
                for (position in NavigationPosition.entries) {
                    onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                    await { !activity.findViewById<View>(R.id.navigation).isLayoutRequested }
                    instrumentation.waitForIdleSync()
                    onMain {
                        for (page in listOf(R.id.nav_player, R.id.nav_folders)) {
                            val button = activity.findViewById<View>(page)
                            assertTrue(button.isShown)
                            assertTrue(button.width >= 48 * activity.resources.displayMetrics.density)
                            assertTrue(button.height >= 48 * activity.resources.displayMetrics.density)
                        }
                        for ((page, _) in switches) {
                            val button = activity.findViewById<View>(page)
                            assertEquals(View.GONE, button.visibility)
                            assertFalse(button.requestFocus())
                        }
                        model.screen(R.id.nav_folders)
                        assertEquals(R.id.nav_folders, model.selectedScreen.value)
                    }
                }
                onMain { app.displaySettings.update { it.copy(navigationPosition = NavigationPosition.BOTTOM) } }
                scenario.recreate()
                scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
                activity = settledMain()
                await { model.selectedScreen.value == R.id.nav_folders &&
                    activity.findViewById<View>(R.id.nav_playlists).visibility == View.GONE }
                screenshot("navigation1360-required-pages")
                onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
                await { activity.findViewById<MaterialSwitch>(R.id.settingsShowLibrary)?.isShown == true }
                for ((page, toggle) in switches) {
                    onView(withId(toggle)).perform(scrollTo(), click())
                    await { activity.findViewById<View>(page).visibility == View.VISIBLE }
                }
                screenshot("navigation1360-general-settings")
                onMain {
                    assertTrue(controller.isPlaying)
                    assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                    assertEquals(tracks.take(2).map { it.id }, (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId })
                    assertTrue("Visibility settings must not seek or replace playback", discontinuities.isEmpty())
                    model.closeSettings()
                }
                assertEquals(library, app.repository.library.value)
                assertEquals(0, network.requests.size)
            }
        } finally { onMain { controller.removeListener(listener) } }
    }

    @Test fun restoredHiddenScreensAndSettingsReturnAlwaysResolveToPlaying() {
        onMain {
            app.displaySettings.update { it.copy(showLibrary = false, showQueue = false, showPlaylists = false) }
            app.radio.setEnabled(false)
            for (page in listOf(R.id.nav_library, R.id.nav_queue, R.id.nav_playlists, R.id.nav_radio)) {
                val store = ViewModelStore()
                try {
                    val model = MusicViewModel(app, SavedStateHandle(mapOf("screen" to page, "settingsReturn" to page)))
                    store.put("navigation", model)
                    assertEquals(R.id.nav_player, model.selectedScreen.value)
                    model.screen(R.id.action_settings)
                    model.closeSettings()
                    assertEquals(R.id.nav_player, model.selectedScreen.value)
                    for (required in listOf(R.id.nav_player, R.id.nav_folders, R.id.action_settings)) {
                        assertTrue(model.isScreenVisible(required))
                        model.screen(required)
                        assertEquals(required, model.selectedScreen.value)
                    }
                } finally { store.clear() }
            }
        }
        assertEquals(0, network.requests.size)
    }

    @Test fun radioSelectionKeepsSearchGroupAndPlaybackUntilOpenPlayerIsEnabled() {
        assertFalse(RadioSettings(app).options.value.openPlayerOnSelection)
        network.customPlaylist = finitePlaylist()
        val programs = importPrograms("#EXTM3U\n" +
            "#EXTINF:-1 group-title=\"Picker\",Picker One\nhttps://radio.example/one/index.m3u8\n" +
            "#EXTINF:-1 group-title=\"Picker\",Picker Two\nhttps://radio.example/two/index.m3u8\n" +
            "#EXTINF:-1 group-title=\"Picker\",Picker Two\nhttps://radio.example/two-b/index.m3u8")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<EditText>(R.id.radioSearch) != null }
            onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("Picker") }
            await { model.radioChannels.value.size == 1 }
            val heading = radioGroupButton(activity, RadioGroups.of(programs.first()).id)
            onMain { heading.performClick() }
            await { model.radioChannels.value.size == 3 }
            val rows = onMain { model.radioChannels.value }
            onMain { radioStationButton(activity, programs[0].id).performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == programs[0].id }
            val connections = network.manifests.get()
            repeat(3) { onMain { radioStationButton(activity, programs[0].id).performClick() } }
            SystemClock.sleep(300)
            onMain {
                assertEquals(R.id.nav_radio, model.selectedScreen.value)
                assertEquals("Picker", activity.findViewById<EditText>(R.id.radioSearch).text.toString())
                assertSame(rows, model.radioChannels.value)
                assertTrue(radioStationButton(activity, programs[0].id).isSelected)
            }
            assertEquals(connections, network.manifests.get())
            onMain { radioStationButton(activity, programs[1].id).performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == programs[1].id }
            onMain { radioStationButton(activity, programs[1].id).performLongClick() }
            onView(withText(app.text(R.string.radio_sources, 2))).inRoot(isDialog()).perform(click())
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(1).perform(click())
            await { controller.isPlaying && app.radioCatalog.find(programs[1].id)?.sourceIndex == 1 }
            onMain {
                assertEquals(R.id.nav_radio, model.selectedScreen.value)
                assertEquals(rows.map { it.key to it.expanded }, model.radioChannels.value.map { it.key to it.expanded })
                assertEquals("Picker", model.radioQuery.value)
            }
            screenshot("navigation1360-radio-stays")
            val selectedConnections = network.manifests.get()
            onMain { activity.findViewById<View>(R.id.radioSettings).performClick() }
            await { activity.findViewById<MaterialSwitch>(R.id.radioOpenPlayer)?.isShown == true }
            onView(withId(R.id.radioOpenPlayer)).perform(scrollTo(), click())
            await { app.radio.options.value.openPlayerOnSelection }
            assertTrue(RadioSettings(app).options.value.openPlayerOnSelection)
            assertEquals(selectedConnections, network.manifests.get())
            screenshot("navigation1360-radio-setting")
            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            onMain { model.closeSettings() }
            await { model.selectedScreen.value == R.id.nav_radio &&
                activity.findViewById<RecyclerView>(R.id.radioList).childCount == 3 }
            onMain { radioStationButton(activity, programs[1].id).performClick() }
            await { model.selectedScreen.value == R.id.nav_player }
            assertEquals(selectedConnections, network.manifests.get())
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList).isShown }
            onMain { radioStationButton(activity, programs[1].id).performLongClick() }
            onView(withText(app.text(R.string.radio_sources, 2))).inRoot(isDialog()).perform(click())
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(0).perform(click())
            await { model.selectedScreen.value == R.id.nav_player && controller.isPlaying &&
                app.radioCatalog.find(programs[1].id)?.sourceIndex == 0 }
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    @Test fun nativeRadioSearchPinsRemoteNavigationAndMusicQueueActionsWorkTogether() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var model: MusicViewModel
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            activity = settledMain()
            onMain { model = ViewModelProvider(activity)[MusicViewModel::class.java] }
            instrumentation.setInTouchMode(false)
            for (position in NavigationPosition.entries) {
                android.util.Log.i("CnrRadioUi", "Navigating dock $position")
                onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                instrumentation.waitForIdleSync()
                await {
                    val target = activity.findViewById<View>(R.id.nav_player)
                    activity.hasWindowFocus() && target.isShown && target.width > 0 && !target.isLayoutRequested
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                instrumentation.waitForIdleSync()
                onMain { assertTrue("Navigation must accept remote focus", activity.findViewById<View>(R.id.nav_player).requestFocus()) }
                await { activity.findViewById<View>(R.id.nav_player).hasFocus() }
                val key = if (position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT)
                    KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_RIGHT
                repeat(12) {
                    if (!onMain { activity.findViewById<View>(R.id.nav_radio).hasFocus() }) {
                        instrumentation.sendKeyDownUpSync(key)
                        instrumentation.waitForIdleSync()
                    }
                }
                await { activity.findViewById<View>(R.id.nav_radio).hasFocus() }
            }
            android.util.Log.i("CnrRadioUi", "Opening radio and searching")
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == radioRowCount() }
            assertEquals(0, network.requests.size)
            onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("zgzs") }
            await { activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 2 }
            onMain {
                val row = activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0)
                assertTrue(row.isFocusable)
                row.performClick()
            }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == CnrRadio.stations.first().id }
            android.util.Log.i("CnrRadioUi", "Playing and updating pins")
            onMain { activity.findViewById<View>(R.id.nav_player).performClick() }
            await { activity.findViewById<View>(R.id.favorite).isShown }
            onMain {
                assertFalse(activity.findViewById<View>(R.id.seek).isEnabled)
                assertFalse(activity.findViewById<View>(R.id.rewind).isEnabled)
                activity.findViewById<View>(R.id.favorite).performClick()
                activity.findViewById<View>(R.id.nav_radio).performClick()
            }
            assertFalse(CnrRadio.stations.first().id in app.radio.options.value.pins)
            android.util.Log.i("CnrRadioUi", "Recreating the radio page")
            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == 1 }
            android.util.Log.i("CnrRadioUi", "Appending to the suspended music queue")
            onMain { model.requestPlay(listOf(tracks[2]), mode = QueueMode.APPEND) }
            await { controller.mediaItemCount == 3 && controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain {
                assertEquals(tracks.map { it.id }, (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId })
                assertEquals(17_000L, controller.currentPosition)
                assertFalse(controller.playWhenReady)
            }
            playStation(0)
            android.util.Log.i("CnrRadioUi", "Inserting the next song after a second source switch")
            onMain { model.requestPlay(listOf(tracks[0]), mode = QueueMode.NEXT) }
            await { controller.mediaItemCount == 4 && controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain { assertEquals(tracks[0].id, controller.getMediaItemAt(2).mediaId) }
        }
    }

    @Test fun pinnedButtonsQuickSwitchRemoteMenuAndResponsiveControlsShareTheSameStations() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.let {
                it.adapter?.itemCount == radioRowCount() && it.childCount >= 2
            } == true }
            onMain {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                assertEquals(radioRowCount(), grid.adapter?.itemCount)
                assertTrue((grid.layoutManager as GridLayoutManager).spanCount >= 2)
                for (index in 0 until minOf(8, grid.childCount)) {
                    val button = grid.getChildAt(index) as MaterialButton
                    assertTrue(button.isFocusable)
                    assertNotNull(button.icon)
                    assertFalse("Browsing must not fetch station artwork", button.icon is BitmapDrawable)
                }
                val search = activity.findViewById<View>(R.id.radioSearch)
                val configure = activity.findViewById<View>(R.id.radioSettings)
                assertFalse(activity.findViewById<View>(R.id.radioImport)?.isShown == true)
                fun y(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1] + view.height / 2
                if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    assertTrue(kotlin.math.abs(y(search) - y(configure)) <= 24 * activity.resources.displayMetrics.density)
                } else {
                    assertTrue(y(search) < y(configure))
                }
            }
            assertEquals(0, network.requests.size)
            screenshot("radio133-buttons")
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            instrumentation.waitForIdleSync()
            onMain { assertTrue(activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            onView(withText(R.string.radio_unpin)).inRoot(isDialog()).perform(click())
            await { CnrRadio.stations.first().id !in app.radio.options.value.pins }
            onMain { assertTrue(app.radio.pin(CnrRadio.stations.first().program)) }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == radioRowCount() }
            onMain { app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("radioPins", RadioDefaults.pins.joinToString("\n")).commit(); app.radio.publishRestored() }
            await { activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).contentDescription
                .toString().contains(app.text(CnrRadio.stations.first().title)) }
            onMain { activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == CnrRadio.stations.first().id }
            onMain { activity.findViewById<View>(R.id.nav_player).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations)?.let {
                it.isShown && it.adapter?.itemCount == 8 && it.childCount >= 2
            } == true }
            await { (activity.findViewById<RecyclerView>(R.id.radioQuickStations).getChildAt(0) as MaterialButton).icon is BitmapDrawable }
            assertEquals(1, network.logos.get())
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            instrumentation.waitForIdleSync()
            onMain {
                assertFalse(activity.findViewById<View>(R.id.artwork).isShown)
                assertFalse(activity.findViewById<View>(R.id.lyricsList).isShown)
                assertTrue("Quick station buttons must accept D-pad focus",
                    activity.findViewById<RecyclerView>(R.id.radioQuickStations).getChildAt(0).requestFocus())
            }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations).getChildAt(0).hasFocus() }
            val nextKey = onMain {
                if ((activity.findViewById<RecyclerView>(R.id.radioQuickStations).layoutManager as GridLayoutManager).spanCount > 1)
                    KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN
            }
            instrumentation.sendKeyDownUpSync(nextKey)
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations).getChildAt(1).hasFocus() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == CnrRadio.stations[1].id }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations).getChildAt(1).isSelected }
            screenshot("radio133-quick-switch")
            onMain { controller.pause() }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations).adapter?.itemCount == 8 &&
                controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
            val updates = java.util.concurrent.atomic.AtomicInteger()
            val observer = object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() { updates.incrementAndGet() }
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) { updates.incrementAndGet() }
            }
            val adapter = onMain { activity.findViewById<RecyclerView>(R.id.radioQuickStations).adapter!! }
            onMain { adapter.registerAdapterDataObserver(observer); app.playbackSettings.setMuted(false) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            onMain { adapter.unregisterAdapterDataObserver(observer) }
            assertEquals("Unchanged station buttons must not rebind on a volume event", 0, updates.get())
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id &&
                !activity.findViewById<View>(R.id.radioQuickStations).isShown &&
                activity.findViewById<View>(R.id.artwork).isShown }
        }
    }

    @Test fun largeImportedCatalogUsesOnlyEightBlocksAndRecyclesGroupedRowsWithoutNetwork() {
        importPrograms((0 until 500).joinToString("\n") { "Station $it,https://radio.example/$it/index.m3u8" })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == radioRowCount() }
            val group = radioGroupButton(activity, RadioGroups.other.id)
            onMain { group.performClick() }
            await { ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value
                .any { it.station?.name == "Station 499" } &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount ==
                    app.radio.options.value.pins.size + ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value.size }
            val last = onMain {
                app.radio.options.value.pins.size + ViewModelProvider(activity)[MusicViewModel::class.java]
                    .radioChannels.value.indexOfLast { it.station?.name == "Station 499" }
            }
            onMain {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                assertTrue("A large catalog must not inflate every station", grid.childCount in 1..75)
                grid.scrollToPosition(last)
            }
            await { activity.findViewById<RecyclerView>(R.id.radioList).findViewHolderForAdapterPosition(last) != null }
            onMain {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                assertTrue(grid.childCount in 1..75)
                val button = grid.findViewHolderForAdapterPosition(last)!!.itemView as MaterialButton
                assertTrue(button.isFocusable)
                assertNull(button.icon)
                assertTrue(button.text.toString().contains("Station 499"))
                assertTrue(button.width >= 48 * activity.resources.displayMetrics.density)
                assertTrue(button.height >= 48 * activity.resources.displayMetrics.density)
            }
            assertEquals(0, network.requests.size)
        }
    }

    @Test fun pinningKeepsGroupedRowsAndColorPickerPersistsCancelsAndResetsWithoutRegrouping() {
        val station = importPrograms("#EXTM3U\n#EXTINF:-1 group-title=\"Pin fixtures\",Pin fixture\nhttps://radio.example/pin.mp3").single()
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            })
            assertTrue(ColorUtils.calculateContrast(ContextCompat.getColor(context, R.color.radio_pinned),
                ContextCompat.getColor(context, R.color.surface)) >= 4.5)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            lateinit var model: MusicViewModel
            onMain {
                model = ViewModelProvider(activity)[MusicViewModel::class.java]
                activity.findViewById<View>(R.id.nav_radio).performClick()
                activity.findViewById<EditText>(R.id.radioSearch).setText("Pin fixture")
            }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == 1 }
            val group = radioGroupButton(activity, "import:pin fixtures")
            onMain { group.performClick() }
            await { model.radioChannels.value.any { it.station?.id == station.id } &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 2 }
            val rows = onMain { model.radioChannels.value }
            fun stationButton(): MaterialButton? {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList) ?: return null
                val index = model.radioChannels.value.indexOfFirst { it.station?.id == station.id }
                if (index < 0) return null
                val count = grid.adapter?.itemCount ?: return null
                return grid.findViewHolderForAdapterPosition(count - model.radioChannels.value.size + index)
                    ?.itemView as? MaterialButton
            }
            onMain { checkNotNull(stationButton()).performLongClick() }
            onView(withText(R.string.radio_pin)).inRoot(isDialog()).perform(click())
            await { station.id in app.radio.options.value.pins &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 3 && stationButton()?.icon != null }
            onMain {
                assertSame("Pinning must not rebuild or remove grouped rows", rows, model.radioChannels.value)
                assertEquals(ContextCompat.getColor(activity, R.color.radio_pinned), stationButton()!!.currentTextColor)
                assertEquals(app.text(R.string.radio_pinned), ViewCompat.getStateDescription(stationButton()!!))
            }
            screenshot("radio1342-pinned-in-group")
            onMain { activity.findViewById<View>(R.id.radioSettings).performClick() }
            await { activity.findViewById<View>(R.id.radioPinColor).isShown }
            onView(withId(R.id.radioPinColor)).perform(scrollTo(), click())
            onView(withTagValue(equalTo("rainbow:2"))).inRoot(isDialog()).perform(click())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            assertNull(app.radio.options.value.pinColor)
            onView(withId(R.id.radioPinColor)).perform(scrollTo(), click())
            onView(withTagValue(equalTo("rainbow:2"))).inRoot(isDialog()).perform(click())
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { app.radio.options.value.pinColor == ColorSlots.RAINBOW[2] }
            assertEquals(ColorSlots.RAINBOW[2], RadioSettings(app).options.value.pinColor)
            screenshot("radio1342-pin-color-setting")
            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            onMain { model.closeSettings(); model.screen(R.id.nav_radio) }
            await { stationButton()?.currentTextColor == (ColorSlots.RAINBOW[2] or android.graphics.Color.BLACK) }
            onMain {
                assertTrue(model.radioChannels.value.any { it.station?.id == station.id })
                checkNotNull(stationButton()).performLongClick()
            }
            onView(withText(R.string.radio_unpin)).inRoot(isDialog()).perform(click())
            await { station.id !in app.radio.options.value.pins &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 2 && stationButton()?.icon == null }
            onMain {
                assertEquals(2, model.radioChannels.value.size)
                assertNotEquals(ColorSlots.RAINBOW[2] or android.graphics.Color.BLACK, stationButton()!!.currentTextColor)
                activity.findViewById<View>(R.id.radioSettings).performClick()
            }
            onView(withId(R.id.radioPinColor)).perform(scrollTo(), click())
            onView(withText(R.string.use_default)).inRoot(isDialog()).perform(click())
            await { app.radio.options.value.pinColor == null }
            assertNull(RadioSettings(app).options.value.pinColor)
            assertEquals(0, network.requests.size)
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    @Test fun disablingRadioRestoresPausedMusicPreservesDataAndNeverInterruptsLocalPlayback() {
        val imported = importPrograms("Keep this station,https://radio.example/keep/index.m3u8").single()
        onMain { app.radio.favorite(imported) }
        playProgram(imported)
        val catalog = runBlocking { app.radioCatalog.savedPrograms() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
                ViewModelProvider(it)[MusicViewModel::class.java].settingsCategory(SettingsCategory.RADIO)
            }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.action_settings).performClick() }
            await { activity.findViewById<View>(R.id.settingsRadio).isShown }
            onMain { activity.findViewById<MaterialSwitch>(R.id.settingsRadio).performClick() }
            await { !app.radio.options.value.enabled && !app.radio.options.value.active &&
                controller.currentMediaItem?.mediaId == tracks[1].id && network.active.get() == 0 }
            onMain {
                assertFalse(activity.findViewById<View>(R.id.nav_radio).isShown)
                assertFalse(controller.playWhenReady)
                assertEquals(17_000L, controller.currentPosition)
                assertEquals(2, controller.mediaItemCount)
                assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
                assertTrue(controller.shuffleModeEnabled)
            }
            assertEquals(catalog, runBlocking { app.radioCatalog.savedPrograms() })
            assertFalse(RadioSettings(app).options.value.enabled)
            assertTrue(imported.id in app.radio.options.value.favorites)
            val requests = network.requests.size
            val rejected = onMain { controller.sendCustomCommand(RadioCommands.play, stationArguments(0)) }
                .get(20, TimeUnit.SECONDS)
            assertNotEquals(SessionResult.RESULT_SUCCESS, rejected.resultCode)
            assertEquals(requests, network.requests.size)
            onMain { activity.findViewById<MaterialSwitch>(R.id.settingsRadio).performClick() }
            await { app.radio.options.value.enabled && activity.findViewById<View>(R.id.nav_radio).isShown }
            onMain { assertFalse(controller.playWhenReady); controller.prepare(); controller.play() }
            await { controller.isPlaying }
            onMain { activity.findViewById<MaterialSwitch>(R.id.settingsRadio).performClick() }
            await { !app.radio.options.value.enabled && !activity.findViewById<View>(R.id.nav_radio).isShown }
            onMain {
                assertTrue("Disabling unused radio must leave local music playing", controller.isPlaying)
                assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                controller.pause()
                activity.findViewById<MaterialSwitch>(R.id.settingsRadio).performClick()
            }
            await { app.radio.options.value.enabled }
            assertEquals(requests, network.requests.size)
        }
    }

    @Test fun eightDefaultPinsCanBeRemovedAndReplacedUpToTenWithoutAHiddenFavoritesFilter() {
        assertEquals(RadioDefaults.pins, app.radio.options.value.pins)
        val channels = importPrograms((1..3).joinToString("\n") { "Added $it,https://radio.example/$it/index.m3u8" })
        onMain {
            assertTrue(app.radio.pin(channels[0]))
            assertTrue(app.radio.pin(channels[1]))
            assertFalse(app.radio.pin(channels[2]))
            assertEquals(10, app.radio.options.value.pins.size)
            assertTrue(app.radio.pin(CnrRadio.stations.first().program))
            assertTrue(app.radio.pin(channels[2]))
        }
        assertEquals(app.radio.options.value.pins, RadioSettings(app).options.value.pins)
        onMain {
            app.radio.options.value.pins.toList().forEach { app.radio.pin(app.radioCatalog.find(it)!!) }
        }
        assertTrue(RadioSettings(app).options.value.pins.isEmpty())
        val chinese = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        })
        assertEquals(listOf("中国之声", "经济之声", "音乐之声", "中国交通广播", "环球资讯广播",
            "中国乡村之声", "文艺之声", "经典音乐广播"), RadioDefaults.pins.map { app.radioCatalog.find(it)!!.title(chinese) })
        assertEquals(0, network.requests.size)
    }

    @Test fun sourceArrowsAreBoundedIgnoreRapidDuplicatesAndRestoreLocalSeeking() {
        val station = importPrograms("Switchable,https://radio.example/first/index.m3u8\n" +
            "Switchable,https://radio.example/second/index.m3u8\n" +
            "Switchable,https://radio.example/third/index.m3u8").single()
        playProgram(station)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_player).performClick() }
            await { activity.findViewById<View>(R.id.forward).isEnabled }
            onMain {
                assertEquals(app.text(R.string.radio_source_next),
                    activity.findViewById<View>(R.id.forward).contentDescription.toString())
                assertEquals(app.text(R.string.radio_source_previous),
                    activity.findViewById<View>(R.id.rewind).contentDescription.toString())
                assertFalse(activity.findViewById<View>(R.id.rewind).isEnabled)
                assertFalse(activity.findViewById<View>(R.id.seek).isEnabled)
                activity.findViewById<View>(R.id.forward).performClick()
                activity.findViewById<View>(R.id.forward).performClick()
            }
            await { app.radioCatalog.find(station.id)?.sourceIndex == 1 && controller.isPlaying &&
                activity.findViewById<View>(R.id.forward).isEnabled }
            assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
            assertFalse(network.requests.any { it.contains("/third/") })
            onMain { activity.findViewById<View>(R.id.forward).performClick() }
            await { app.radioCatalog.find(station.id)?.sourceIndex == 2 && controller.isPlaying &&
                activity.findViewById<View>(R.id.rewind).isEnabled && !activity.findViewById<View>(R.id.forward).isEnabled }
            assertEquals(2, runBlocking { app.radioCatalog.savedPrograms() }.single().sourceIndex)
            screenshot("radio134-source-buttons")
            scenario.recreate()
            scenario.onActivity { activity = it }
            activity = settledMain()
            await { activity.findViewById<View>(R.id.rewind).isEnabled }
            onMain {
                assertFalse(activity.findViewById<View>(R.id.forward).isEnabled)
                activity.findViewById<View>(R.id.forward).performClick()
            }
            assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
            assertEquals(2, app.radioCatalog.find(station.id)?.sourceIndex)
            onMain { activity.findViewById<View>(R.id.rewind).performClick() }
            await { app.radioCatalog.find(station.id)?.sourceIndex == 1 && controller.isPlaying &&
                activity.findViewById<View>(R.id.rewind).isEnabled }
            onMain { controller.pause() }
            await { controller.playbackState == Player.STATE_IDLE }
            onMain { activity.findViewById<View>(R.id.rewind).performClick() }
            await { app.radioCatalog.find(station.id)?.sourceIndex == 0 && controller.isPlaying &&
                !activity.findViewById<View>(R.id.rewind).isEnabled }
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain { controller.prepare() }
            await { controller.playbackState == Player.STATE_READY && activity.findViewById<View>(R.id.forward).isEnabled }
            onMain {
                assertFalse(controller.playWhenReady)
                assertEquals(app.text(R.string.forward), activity.findViewById<View>(R.id.forward).contentDescription.toString())
                assertEquals(app.text(R.string.rewind), activity.findViewById<View>(R.id.rewind).contentDescription.toString())
                activity.findViewById<View>(R.id.forward).performClick()
            }
            await { controller.currentPosition == 27_000L }
            onMain { activity.findViewById<View>(R.id.rewind).performClick() }
            await { controller.currentPosition == 17_000L }
        }
    }

    @Test fun radioGroupsStartCollapsedSwitchExclusivelyAndRetainRemoteFocus() {
        importPrograms("#EXTM3U\n" + (0 until 42).joinToString("\n") { index ->
            val group = if (index < 40) "Accordion A" else "Accordion B"
            "#EXTINF:-1 group-title=\"$group\",Accordion station $index\nhttps://radio.example/$index.mp3"
        })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            lateinit var model: MusicViewModel
            onMain {
                model = ViewModelProvider(activity)[MusicViewModel::class.java]
                activity.findViewById<View>(R.id.nav_radio).performClick()
                activity.findViewById<EditText>(R.id.radioSearch).setText("Accordion")
            }
            fun settledRows(count: Int): Boolean {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList) ?: return false
                return model.radioChannels.value.size == count && grid.adapter?.itemCount == count &&
                    !grid.isLayoutRequested && !grid.isComputingLayout && grid.childCount > 0 &&
                    (0 until grid.childCount).all { index ->
                        val child = grid.getChildAt(index)
                        val row = model.radioChannels.value.getOrNull(grid.getChildAdapterPosition(child))
                        row != null && child.contentDescription == (row.station?.title(activity) ?: row.group.title(activity)) &&
                            (row.station != null || ViewCompat.getStateDescription(child) == activity.getString(
                                if (row.expanded) R.string.radio_group_expanded else R.string.radio_group_collapsed))
                    }
            }
            await { settledRows(2) }
            onMain { assertTrue(model.radioChannels.value.all { it.station == null && !it.expanded }) }
            screenshot("radio1341-groups-collapsed")
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            val first = radioGroupButton(activity, "import:accordion a")
            onMain {
                assertTrue(first.requestFocus())
                assertTrue(ViewCompat.isAccessibilityHeading(first))
                assertEquals(app.text(R.string.radio_group_collapsed), ViewCompat.getStateDescription(first))
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { settledRows(42) }
            onMain {
                assertTrue(model.radioChannels.value.filter { it.station != null }.all { it.group.id == "import:accordion a" })
                assertEquals(app.text(R.string.radio_group_expanded), ViewCompat.getStateDescription(first))
            }
            val second = radioGroupButton(activity, "import:accordion b")
            onMain { assertTrue(second.requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { settledRows(4) }
            await {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                grid.findViewHolderForAdapterPosition(1)?.itemView?.hasFocus() == true
            }
            onMain {
                assertEquals(1, model.radioChannels.value.count { it.expanded })
                assertTrue(model.radioChannels.value.filter { it.station != null }.all { it.group.id == "import:accordion b" })
            }
            screenshot("radio1341-groups-switched")
            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            await { settledRows(4) }
            val reopened = radioGroupButton(activity, "import:accordion b")
            onMain { reopened.performClick() }
            await { settledRows(2) }
            onMain { assertTrue(model.radioChannels.value.all { it.station == null && !it.expanded }) }
            val openAgain = radioGroupButton(activity, "import:accordion b")
            onMain { openAgain.performClick() }
            await { settledRows(4) }
            onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("Accordion station 39") }
            await { settledRows(1) }
            onMain { assertFalse(model.radioChannels.value.single().expanded) }
            val filtered = radioGroupButton(activity, "import:accordion a")
            onMain { filtered.performClick() }
            await { settledRows(2) && model.radioChannels.value.last().station?.name == "Accordion station 39" }
            onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("") }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == radioRowCount() }
            onMain { assertTrue(model.radioChannels.value.all { it.station == null && !it.expanded }) }
            assertEquals(0, network.requests.size)
        }
    }

    @Test fun regionalRowsHaveEqualColumnsFullWidthHeadersAndRemoteFocusAfterResizing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == radioRowCount() }
            val group = radioGroupButton(activity, "beijing")
            onMain { group.performClick() }
            await { ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value.any {
                it.group.id == "beijing" && it.station != null
            } }
            val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            val headerPosition = onMain {
                app.radio.options.value.pins.size + ViewModelProvider(activity)[MusicViewModel::class.java]
                    .radioChannels.value.indexOfFirst { it.group.id == "beijing" && it.station == null }
            }
            assertTrue(headerPosition >= 8)
            for (width in listOf(600, 540, 360, 540)) {
                val columns = if (width >= 520) 2 else 1
                onMain {
                    grid.layoutParams = grid.layoutParams.apply {
                        this.width = (width * activity.resources.displayMetrics.density).toInt()
                    }
                    (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(headerPosition, 0)
                }
                await {
                    val manager = grid.layoutManager as GridLayoutManager
                    grid.findViewHolderForAdapterPosition(headerPosition + 2) != null &&
                        manager.spanSizeLookup.getSpanSize(headerPosition + 1) * columns == manager.spanCount
                }
                instrumentation.waitForIdleSync()
                onMain {
                    val manager = grid.layoutManager as GridLayoutManager
                    val heading = grid.findViewHolderForAdapterPosition(headerPosition)!!.itemView
                    val first = grid.findViewHolderForAdapterPosition(headerPosition + 1)!!.itemView
                    val second = grid.findViewHolderForAdapterPosition(headerPosition + 2)!!.itemView
                    assertEquals(manager.spanCount, manager.spanSizeLookup.getSpanSize(headerPosition))
                    assertTrue(heading.width > grid.width * 0.9f)
                    assertTrue(kotlin.math.abs(first.width - second.width) <= 1)
                    if (columns == 2) {
                        assertEquals(first.top, second.top)
                        assertTrue(first.width < heading.width * 0.55f)
                    } else {
                        assertTrue(first.top < second.top)
                        assertEquals(heading.width, first.width)
                    }
                    assertTrue(first.isFocusable)
                    assertTrue(first.height >= 48 * activity.resources.displayMetrics.density)
                    assertTrue(grid.childCount < 75)
                }
                instrumentation.setInTouchMode(false)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                instrumentation.waitForIdleSync()
                onMain { assertTrue(grid.findViewHolderForAdapterPosition(headerPosition + 1)!!.itemView.requestFocus()) }
                instrumentation.sendKeyDownUpSync(if (columns == 2) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN)
                await { grid.findViewHolderForAdapterPosition(headerPosition + 2)?.itemView?.hasFocus() == true }
                screenshot("radio134-list-$width")
            }
            assertEquals(0, network.requests.size)
        }
    }

    @Test fun nativeEditorSourceSelectorAndDedicatedSettingsPersistWithoutChangingMusic() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.childCount?.let { it > 0 } == true }
            onMain { activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).performLongClick() }
            onView(withText(R.string.radio_edit)).inRoot(isDialog()).perform(click())
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            screenshot("radio133-editor-open")
            onView(withId(R.id.radioEditSources)).inRoot(isDialog())
                .perform(scrollTo(), replaceText("https://radio.example/first/index.m3u8\nhttps://radio.example/backup/index.m3u8"), closeSoftKeyboard())
            onView(withId(R.id.radioEditGroup)).inRoot(isDialog()).perform(scrollTo(), replaceText("My stations"), closeSoftKeyboard())
            screenshot("radio133-editor")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            val id = CnrRadio.stations.first().id
            await { app.radioCatalog.find(id)?.sources?.size == 2 }
            assertEquals(0, network.requests.size)
            onMain { activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).performLongClick() }
            onView(withText(app.text(R.string.radio_sources, 2))).inRoot(isDialog()).perform(click())
            screenshot("radio133-sources")
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(1).perform(click())
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == id &&
                app.radioCatalog.find(id)?.sourceIndex == 1 }
            assertEquals("Only the chosen address can load", 0, network.requests.count { it.contains("/first/") })
            assertEquals(0, network.catalogues.get())
            onMain { activity.findViewById<View>(R.id.nav_player).performClick() }
            await { activity.findViewById<View>(R.id.radioStreamStatus)?.isShown == true }
            onMain {
                assertEquals(app.text(R.string.radio_unpin), activity.findViewById<View>(R.id.favorite).contentDescription.toString())
                assertTrue((activity.findViewById<View>(R.id.album) as android.widget.TextView).text.toString().contains("2/2"))
                assertTrue(controller.totalBufferedDuration > 0)
            }
            screenshot("radio133-playing-status")
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<View>(R.id.radioSettings)?.isShown == true }
            onMain { activity.findViewById<View>(R.id.radioSettings).performClick() }
            await { activity.findViewById<View>(R.id.radioBuffer)?.isShown == true }
            onMain { activity.findViewById<View>(R.id.radioBuffer).performClick() }
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(3).perform(click())
            onMain { activity.findViewById<View>(R.id.radioStartBuffer).performClick() }
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(2).perform(click())
            onMain { activity.findViewById<MaterialSwitch>(R.id.radioAutoSource).performClick() }
            await { app.radio.options.value.tuning == RadioTuning(30, 3, 2, true) }
            screenshot("radio133-settings")
            assertEquals(app.radio.options.value.tuning, RadioSettings(app).options.value.tuning)
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain { assertEquals(17_000L, controller.currentPosition); assertFalse(controller.playWhenReady) }
        }
    }

    @Test fun automaticFallbackPlaysOnlyTheNextAddressOfTheSameChannel() {
        val programs = importPrograms("Fallback,https://radio.example/unavailable/index.m3u8\n" +
            "Fallback,https://radio.example/working/index.m3u8\nOther,https://radio.example/never/index.m3u8")
        val station = programs.first()
        network.failPathPrefix = "/unavailable/"
        onMain { app.radio.tune(RadioTuning(retries = 0, autoSource = true)) }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { controller.isPlaying && app.radioCatalog.find(station.id)?.sourceIndex == 1 }
        assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
        assertEquals(1, network.requests.count { it.contains("/unavailable/") })
        assertTrue(network.requests.none { it.contains("/never/") })
        assertEquals("https://radio.example/working/index.m3u8", runBlocking { app.radioCatalog.savedPrograms() }.first().streamUri)
    }

    @Test fun exhaustedSourcesStopWithoutLoopsOrSkippingToAnotherStation() {
        val station = importPrograms((1..3).joinToString("\n") { "Failing,https://radio.example/failure$it/index.m3u8" }).single()
        network.failStreams = 503
        onMain { app.radio.tune(RadioTuning(retries = 0, autoSource = true)) }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { network.manifests.get() == 3 && controller.playerError != null && !controller.playWhenReady }
        val requests = network.requests.size
        SystemClock.sleep(1200)
        assertEquals(requests, network.requests.size)
        assertEquals(0, network.active.get())
        assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
        assertEquals(0, network.catalogues.get())
    }

    @Test fun stopCancelsRecoveryWaitingForCatalogIo() = assertRecoveryCancelled { controller.stop() }

    @Test fun pauseCancelsRecoveryEvenWhenTheFailedPlayerIsAlreadyPaused() = assertRecoveryCancelled { controller.pause() }

    private fun assertRecoveryCancelled(stop: () -> Unit) = runBlocking {
        val station = importPrograms("Waiting,https://radio.example/unavailable/index.m3u8\n" +
            "Waiting,https://radio.example/working/index.m3u8").single()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val saved = app.radioCatalog.savedPrograms()
        val blocker = async(Dispatchers.IO) {
            app.radioCatalog.withReplacement(saved) {
                locked.countDown()
                check(release.await(20, TimeUnit.SECONDS))
            }
        }
        try {
            assertTrue(locked.await(5, TimeUnit.SECONDS))
            network.failPathPrefix = "/unavailable/"
            onMain { app.radio.tune(RadioTuning(retries = 0, autoSource = true)) }
            command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
            await { controller.playerError != null && !controller.playWhenReady }
            onMain(stop)
            instrumentation.waitForIdleSync()
        } finally { release.countDown(); blocker.await() }
        SystemClock.sleep(1200)
        assertTrue(network.requests.none { it.contains("/working/") })
        onMain { assertFalse(controller.playWhenReady); assertEquals(Player.STATE_IDLE, controller.playbackState) }
    }

    @Test fun downloadedLogosSurviveRepositoryRestartAndClearDoesNotTouchStationData() = runBlocking {
        val station = importPrograms("Logo,https://radio.example/live/index.m3u8").single()
            .copy(logoUri = "https://radio.example/logo.png")
        assertNull(app.radioLogos.cached(station))
        assertEquals(0, network.requests.size)
        app.radioLogos.download(station)
        assertNotNull(app.radioLogos.cached(station))
        assertEquals(1, network.logos.get())
        val directory = File(app.filesDir, "radio-logos")
        val cached = directory.listFiles()!!.single()
        assertTrue(cached.renameTo(File(cached.path + ".bak")))
        val restored = RadioLogoRepository(app)
        assertNotNull(restored.cached(station))
        restored.download(station)
        assertEquals(1, network.logos.get())
        assertTrue(cached.isFile)
        for (name in listOf("oversize", "invalid")) {
            try { restored.download(station.copy(logoUri = "https://radio.example/$name.png")); fail("Invalid logo accepted") }
            catch (_: IOException) { }
        }
        val saved = app.radioCatalog.savedPrograms()
        assertTrue(app.radioLogos.clear() > 0)
        assertNull(app.radioLogos.cached(station))
        assertEquals(saved, app.radioCatalog.savedPrograms())
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun clearCachePreventsAnInflightLogoFromReappearing() = runBlocking {
        val station = importPrograms("Delayed,https://radio.example/live/index.m3u8").single()
            .copy(logoUri = "https://radio.example/delayed.png")
        network.logoStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        network.logoRelease = release
        val pending = async(Dispatchers.IO) { app.radioLogos.download(station) }
        try {
            assertTrue(network.logoStarted!!.await(5, TimeUnit.SECONDS))
            app.radioLogos.clear()
        } finally { release.countDown(); pending.await(); network.logoRelease = null; network.logoStarted = null }
        assertNull(app.radioLogos.cached(station))
        assertEquals(0, network.active.get())
    }

    @Test fun invalidSavedTuningIsReportedWithoutLosingPinsOrCatalog() {
        val original = runBlocking { app.radioCatalog.savedPrograms() }
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("radioTuning", "{broken").commit()
        onMain { app.radio.publishRestored() }
        assertEquals(RadioTuning(), app.radio.options.value.tuning)
        assertEquals(RadioDefaults.pins, app.radio.options.value.pins)
        assertEquals(original, runBlocking { app.radioCatalog.savedPrograms() })
        assertTrue(app.notice.value?.isError == true)
        assertEquals("{broken", app.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("radioTuning", null))
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("radioTuning").commit()
        onMain { app.radio.publishRestored() }
    }

    @Test fun invalidSavedPinColorIsReportedWithoutLosingPinsOrOverwritingTheValue() {
        val saved = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        saved.edit().putInt("radioPinColor", 0x1000000).commit()
        onMain { app.radio.publishRestored() }
        assertNull(app.radio.options.value.pinColor)
        assertEquals(RadioDefaults.pins, app.radio.options.value.pins)
        assertTrue(app.notice.value?.isError == true)
        assertEquals(0x1000000, saved.getInt("radioPinColor", -1))
        saved.edit().putString("radioPinColor", "invalid").commit()
        onMain { app.radio.publishRestored() }
        assertNull(app.radio.options.value.pinColor)
        assertEquals("invalid", saved.getString("radioPinColor", null))
        saved.edit().remove("radioPinColor").commit()
        onMain { app.radio.publishRestored() }
    }

    @Test fun receiveRateIgnoresAnotherSourceAndDecaysWithoutNetworkProbes() {
        var clock = 1_000L
        val traffic = com.macarmusic.app.radio.RadioTraffic { clock }
        val original = CnrRadio.stations.first().program
        val alternate = original.copy(uri = "https://radio.example/other/index.m3u8")
        val source = RadioDataSource({ it })
        val spec = DataSpec(android.net.Uri.parse(original.uri))
        traffic.select(original.id, original.uri)
        val listener = traffic.listener(original)
        listener.onBytesTransferred(source, spec, true, 4096)
        clock += 1000
        assertEquals(4096, traffic.bytesPerSecond(original.id))
        traffic.select(alternate.id, alternate.uri)
        listener.onBytesTransferred(source, spec, true, 4096)
        assertEquals(0, traffic.bytesPerSecond(original.id))
        traffic.listener(alternate).onBytesTransferred(source, spec, true, 2048)
        assertEquals(2048, traffic.bytesPerSecond(alternate.id))
        clock += 4000
        assertEquals(0, traffic.bytesPerSecond(alternate.id))
        assertEquals(0, network.requests.size)
    }

    private fun settledMain(): MainActivity {
        var current: MainActivity? = null
        var since = 0L
        await {
            val active = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
            if (active !== current) { current = active; since = SystemClock.uptimeMillis() }
            active != null && active.hasWindowFocus() && active.findViewById<View>(R.id.play).isEnabled &&
                SystemClock.uptimeMillis() - since >= 500
        }
        return checkNotNull(current)
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val folder = File(app.getExternalFilesDir(null), "test-screenshots")
            check(folder.isDirectory || folder.mkdirs())
            File(folder, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { image.recycle() }
    }

    private fun radioRowCount(): Int {
        val programs = app.radioCatalog.state.value.programs
        val pins = app.radio.options.value.pins
        return programs.count { it.id in pins } + programs.map(RadioGroups::of).distinct().size
    }

    private fun radioGroupButton(activity: MainActivity, groupId: String): View {
        val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
        val position = onMain {
            val rows = ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value
            val index = rows.indexOfFirst { it.group.id == groupId && it.station == null }
            check(index >= 0) { "Missing radio group: $groupId" }
            grid.adapter!!.itemCount - rows.size + index
        }
        onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
        await { grid.findViewHolderForAdapterPosition(position) != null && !grid.isLayoutRequested }
        return onMain { grid.findViewHolderForAdapterPosition(position)!!.itemView }
    }

    private fun radioStationButton(activity: MainActivity, stationId: String): View {
        val grid = activity.findViewById<RecyclerView>(R.id.radioList)
        val rows = ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value
        val index = rows.indexOfFirst { it.station?.id == stationId }
        check(index >= 0) { "Missing station row: $stationId" }
        return checkNotNull(grid.findViewHolderForAdapterPosition(grid.adapter!!.itemCount - rows.size + index)).itemView
    }

    private fun stopService() {
        instrumentation.waitForIdleSync()
        await {
            if (app.playbackReady.value) app.stopService(Intent(app, PlaybackService::class.java))
            !app.playbackReady.value
        }
    }

    private fun connect(): MediaController {
        val result = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }.get(20, TimeUnit.SECONDS)
        await { app.playbackReady.value }
        return result
    }

    private fun stationArguments(index: Int) = Bundle().apply {
        putString(RadioCommands.STATION, CnrRadio.stations[index].id)
    }

    private fun finitePlaylist() = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
        (0 until 30).joinToString("\n") { "#EXTINF:4.0,\ntone$it.aac" } + "\n#EXT-X-ENDLIST\n"

    private fun playStation(index: Int) {
        command(RadioCommands.play, stationArguments(index))
        await { controller.currentMediaItem?.mediaId == CnrRadio.stations[index].id && controller.isPlaying }
    }

    private fun command(command: SessionCommand, arguments: Bundle = Bundle.EMPTY) {
        val future = onMain { controller.sendCustomCommand(command, arguments) }
        assertEquals(SessionResult.RESULT_SUCCESS, future.get(20, TimeUnit.SECONDS).resultCode)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(25)
        }
        fail("Radio state did not settle: " + onMain {
            if (::controller.isInitialized) "${controller.currentMediaItem?.mediaId}, state=${controller.playbackState}, " +
                "error=${controller.playerError?.errorCodeName}, active=${app.radio.options.value.active}, " +
                "play=${controller.playWhenReady}, notice=${app.notice.value}" else "connecting"
        })
    }

    private fun <T> onMain(action: () -> T): T {
        val future = FutureTask(Callable(action))
        instrumentation.runOnMainSync(future)
        return future.get()
    }
}
