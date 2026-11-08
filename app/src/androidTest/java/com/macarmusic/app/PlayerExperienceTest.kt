package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.model.ButtonIconStyle
import com.macarmusic.app.model.FolderSource
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Track
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.collection
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.playback.withCollection
import com.macarmusic.app.ui.AccentTheme
import com.macarmusic.app.ui.ExplorerLocation
import com.macarmusic.app.ui.ExplorerPlace
import com.macarmusic.app.ui.FileExplorerActivity
import com.macarmusic.app.ui.FileExplorerViewModel
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.equalTo
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerExperienceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val tree = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
    private val previousDisplay = app.displaySettings.options.value
    private val previousTheme = app.themeMode
    private lateinit var source: FolderSource
    private lateinit var tracks: List<Track>

    @Before fun prepare() {
        assumeTrue("Only disposable emulator fixtures are allowed", Build.PRODUCT.contains("sdk"))
        instrumentation.setInTouchMode(true)
        assertTrue(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioActive", false)
            .putString("radioRefreshInterval", "MANUAL").commit())
        onMain {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            app.radio.publishRestored()
            app.displaySettings.update { it.copy(autoPlayOnLaunch = false, onlineLyrics = false,
                lyricsEnabled = false, customTheme = false, noticeDurationSeconds = 5,
                primaryControlColor = null,
                primaryControlHighlightEnabled = true,
                spectrum = it.spectrum.copy(enabled = false)) }
            app.playbackSettings.setMuted(true)
        }
        instrumentation.context.startActivity(Intent().setClassName("${app.packageName}.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Fixture grant") {
            app.checkUriPermission(tree, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        unavailable(false)
        app.contentResolver.call(tree, "scanFixtureState", null, Bundle().apply {
            putBoolean("resetReads", true); putLong("audioDelayMs", 0); putInt("extraRows", 0)
        })
        runBlocking {
            app.repository.awaitReady()
            app.repository.update { Library() }
            source = app.repository.addSource(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val scanned = app.repository.scan(source, null) { }
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, "music/one").toString()
            val first = scanned.tracks.first { it.uri == uri }
            tracks = listOf(first, first.copy(id = "ux-next", title = "Next fixture"))
            app.repository.update { it.copy(tracks = tracks, favorites = setOf(first.id),
                playlists = listOf(LocalPlaylist("ux-playlist", "Saved fixture", listOf(first.id)))) }
            app.playback.read()
            app.playback.save(PlaybackSnapshot())
        }
        onMain { app.notice.value?.let { app.dismissNotice(it.id) } }
    }

    @After fun restore() {
        if (!::source.isInitialized) return
        unavailable(false)
        onMain {
            app.playbackProblem.value = null
            app.displaySettings.update { previousDisplay }
            app.themeMode = previousTheme
        }
        instrumentation.setInTouchMode(true)
    }

    @Test fun firstSettingsFramePrecedesContentConstructionAndReturnDoesNotReload() = withPlayer { _, _ ->
        val loaded = CountDownLatch(1)
        val activity = onMain { mainActivity() }
        val root = activity.window.decorView
        onMain {
            assertEquals("Unused recovery controls must not be inflated at startup", 0,
                activity.findViewById<android.view.ViewGroup>(R.id.recoveryStatus).childCount)
        }
        lateinit var draw: ViewTreeObserver.OnDrawListener
        draw = ViewTreeObserver.OnDrawListener {
            if (activity.findViewById<View>(R.id.screenLoading)?.isShown == true &&
                activity.findViewById<View>(R.id.settingsScroll) == null) loaded.countDown()
        }
        onMain {
            root.viewTreeObserver.addOnDrawListener(draw)
            model(activity).openSettings()
        }
        try {
            assertTrue("The destination must draw before Settings is inflated", loaded.await(10, TimeUnit.SECONDS))
            await("Settings content") { mainActivity().findViewById<View>(R.id.settingsScroll)?.isShown == true }
            val settings = onMain { mainActivity().findViewById<View>(R.id.settingsScroll) }
            onMain { model(mainActivity()).screen(R.id.nav_player) }
            await("Playing") { mainActivity().findViewById<View>(R.id.play).isShown }
            onMain { model(mainActivity()).openSettings() }
            await("Return to Settings") { settings.isShown }
            onMain {
                assertSame(settings, mainActivity().findViewById<View>(R.id.settingsScroll))
                assertFalse(mainActivity().findViewById<View>(R.id.screenLoading).isShown)
            }
        } finally { onMain { root.viewTreeObserver.removeOnDrawListener(draw) } }
    }

    @Test fun navigatingAwayFromTheLoadingFrameCancelsTheUnbuiltDestination() = withPlayer { _, _ ->
        val cancelled = CountDownLatch(1)
        val activity = onMain { mainActivity() }
        val root = activity.window.decorView
        var posted = false
        val draw = ViewTreeObserver.OnDrawListener {
            if (!posted && activity.findViewById<View>(R.id.screenLoading)?.isShown == true) {
                posted = true
                root.post { model(activity).screen(R.id.nav_player); cancelled.countDown() }
            }
        }
        onMain {
            root.viewTreeObserver.addOnDrawListener(draw)
            model(activity).openSettings()
        }
        try {
            assertTrue(cancelled.await(10, TimeUnit.SECONDS))
            await("Cancelled destination") { activity.findViewById<View>(R.id.play).isShown }
            instrumentation.waitForIdleSync()
            onMain { assertNull("Cancelled first-use work should not be inflated", activity.findViewById<View>(R.id.settingsScroll)) }
        } finally { onMain { root.viewTreeObserver.removeOnDrawListener(draw) } }
    }

    @Test fun playbackRecoveryOutlivesThePopupAndRecreationAndRetriesTheSameSong() = withPlayer { scenario, player ->
        onMain { player.prepare(); player.play() }
        await("Fixture playback") { player.isPlaying }
        recordPlaybackState("recovery-before-stop", player)
        onMain { player.stop() }
        await("Stopped fixture") { player.playbackState == Player.STATE_IDLE }
        recordPlaybackState("recovery-stopped", player)
        unavailable(true)
        val opensBefore = recordPlaybackState("recovery-unavailable", player)
        try {
            onView(withId(R.id.play)).perform(click())
            recordPlaybackState("recovery-after-play", player)
            await("Real playback failure") {
                player.playerError != null && app.playbackProblem.value != null && !player.playWhenReady
            }
            await("Recovery bar") { mainActivity().findViewById<View>(R.id.recoveryStatus).isShown }
        } finally {
            recordPlaybackState("recovery-await-end", player)
        }
        assertTrue("Play must reopen the unavailable source", checkNotNull(app.contentResolver
            .call(tree, "scanFixtureState", null, null)).getInt("openRequests") > opensBefore)
        await("Five-second popup expires", 10_000) { app.notice.value == null }
        onMain {
            assertTrue(mainActivity().findViewById<View>(R.id.recoveryStatus).isShown)
            assertEquals(tracks.first().id, player.currentMediaItem?.mediaId)
            assertEquals(2, player.mediaItemCount)
        }
        scenario.recreate()
        await("Recovery after recreation") {
            (resumedActivity() as? MainActivity)?.findViewById<View>(R.id.recoveryRetry)
                ?.let { it.isShown && it.isEnabled } == true
        }
        unavailable(false)
        onMain { mainActivity().findViewById<View>(R.id.recoveryRetry).performClick() }
        await("Manual retry succeeds") { player.isPlaying && app.playbackProblem.value == null }
        onMain {
            assertEquals(tracks.first().id, player.currentMediaItem?.mediaId)
            assertEquals(2, player.mediaItemCount)
            assertFalse(mainActivity().findViewById<View>(R.id.recoveryStatus).isShown)
        }
    }

    @Test fun stoppedAndEndedMusicResumesFromMainAndMiniPlay() = withPlayer { _, player ->
        val options = app.playbackSettings.options.value
        try {
            onMain {
                app.playbackSettings.setLoop(LoopMode.OFF)
                app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
            }
            for (button in listOf(R.id.play, R.id.miniPlay)) for (state in listOf(Player.STATE_IDLE, Player.STATE_ENDED)) {
                val name = "${app.resources.getResourceEntryName(button)}-$state"
                onMain {
                    model(mainActivity()).screen(if (button == R.id.play) R.id.nav_player else R.id.nav_queue)
                    player.pause()
                    player.setMediaItems(tracks.map(Track::toMediaItem), if (state == Player.STATE_ENDED) 1 else 0, 5_000)
                    player.prepare()
                    player.play()
                }
                await("Playing $name") {
                    player.isPlaying && player.repeatMode == Player.REPEAT_MODE_OFF && !player.shuffleModeEnabled &&
                        mainActivity().findViewById<View>(button).isShown
                }
                val transitionListener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        android.util.Log.i("PlayerExperienceTest", "MUSIC_PLAYBACK $name-state-event=$playbackState: " +
                            playbackStateDetails(player))
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
                    ) {
                        android.util.Log.i("PlayerExperienceTest", "MUSIC_PLAYBACK $name-position-event: reason=$reason, " +
                            "oldIndex=${oldPosition.mediaItemIndex}, oldPosition=${oldPosition.positionMs}, " +
                            "newIndex=${newPosition.mediaItemIndex}, newPosition=${newPosition.positionMs}, " +
                            playbackStateDetails(player))
                    }
                }
                try {
                    onMain {
                        player.addListener(transitionListener)
                        val seekTargetMs = if (state == Player.STATE_ENDED) player.duration else null
                        android.util.Log.i("PlayerExperienceTest", "MUSIC_PLAYBACK $name-transition-request: " +
                            "targetState=$state, seekTargetMs=$seekTargetMs, " + playbackStateDetails(player))
                        if (state == Player.STATE_IDLE) player.stop() else player.seekTo(checkNotNull(seekTargetMs))
                    }
                    await("Stopped/ended $name") { player.playbackState == state }
                } finally {
                    onMain { player.removeListener(transitionListener) }
                    recordPlaybackState("$name-after-transition", player)
                }
                recordPlaybackState("$name-before-play", player)
                onMain { assertTrue("The fixture must retain play intent in $name", player.playWhenReady) }
                await("Play action for $name") {
                    listOf(R.id.play, R.id.miniPlay).all {
                        mainActivity().findViewById<View>(it).contentDescription == mainActivity().getString(R.string.play)
                    }
                }
                val mediaId = onMain { player.currentMediaItem?.mediaId }
                val position = onMain { player.currentPosition }
                onView(withId(button)).perform(click())
                try {
                    await("One tap resumes $name") { player.isPlaying }
                } finally {
                    recordPlaybackState("$name-after-play", player)
                }
                onMain {
                    assertEquals(mediaId, player.currentMediaItem?.mediaId)
                    assertEquals(tracks.size, player.mediaItemCount)
                    if (state == Player.STATE_ENDED) assertTrue(player.currentPosition < 5_000)
                    else assertTrue(player.currentPosition >= position)
                }
            }
        } finally {
            onMain {
                app.playbackSettings.setLoop(options.loop)
                app.playbackSettings.setOrder(options.order)
            }
        }
    }

    @Test fun folderRetryPreservesFavoritesAndPlaylistsAndDoesNotAutoplay() = withPlayer { _, player ->
        val before = app.repository.library.value
        unavailable(true)
        onMain { model(mainActivity()).scanFolders(listOf(source)) }
        await("Saved source failure") { app.repository.library.value.sources.single().unavailable }
        await("Folder recovery bar") {
            mainActivity().findViewById<View>(R.id.recoveryRetry).let { it.isShown && it.isEnabled }
        }
        unavailable(false)
        onMain { mainActivity().findViewById<View>(R.id.recoveryRetry).performClick() }
        await("Retry rescans the same source") {
            !app.repository.library.value.sources.single().unavailable && model(mainActivity()).scan.value == null
        }
        onMain {
            assertEquals(before.favorites, app.repository.library.value.favorites)
            assertEquals(before.playlists, app.repository.library.value.playlists)
            assertFalse(player.playWhenReady)
            assertFalse(mainActivity().findViewById<View>(R.id.recoveryStatus).isShown)
        }
    }

    @Test fun reconnectedSongRetryPreservesCollectionPositionAndOtherQueueItems() = withPlayer { _, player ->
        val collection = PlaybackCollection(CollectionKind.PLAYLIST, "ux-playlist", "")
        val missing = DocumentsContract.buildDocumentUriUsingTree(tree, "music/missing")
        val replacementQueue = tracks.map { it.toMediaItem().withCollection(collection) }.toMutableList()
        replacementQueue[0] = replacementQueue[0].buildUpon().setUri(missing).build()
        onMain {
            player.setMediaItems(replacementQueue, 0, 20_000)
            player.prepare()
            player.play()
        }
        await("Previous document address fails") { app.playbackProblem.value?.uri == missing.toString() }
        await("Retry after reconnect") {
            mainActivity().findViewById<View>(R.id.recoveryRetry).let { it.isShown && it.isEnabled }
        }
        onMain { mainActivity().findViewById<View>(R.id.recoveryRetry).performClick() }
        await("Reconnected file plays") { player.isPlaying && app.playbackProblem.value == null }
        onMain {
            assertEquals(tracks.first().id, player.currentMediaItem?.mediaId)
            assertEquals(tracks.first().uri, player.currentMediaItem?.localConfiguration?.uri.toString())
            assertEquals(collection, player.currentMediaItem?.collection())
            val next = player.getMediaItemAt(1)
            assertEquals(replacementQueue[1].mediaId, next.mediaId)
            assertEquals(replacementQueue[1].localConfiguration?.uri, next.localConfiguration?.uri)
            assertEquals(collection, next.collection())
            assertEquals(0, player.currentMediaItemIndex)
            assertEquals(2, player.mediaItemCount)
            assertTrue("Recovery retains the requested position", player.currentPosition in 19_000L..28_000L)
        }
    }

    @Test fun recoveryReselectsTheFolderThatOwnsTheFailedAddress() = withPlayer { _, player ->
        val other = source.copy(uri = DocumentsContract.buildDocumentUriUsingTree(tree, "music/other").toString(),
            name = "Another saved folder")
        val linked = tracks.first().copy(sourceUris = linkedMapOf(
            other.uri to DocumentsContract.buildDocumentUriUsingTree(tree, "music/other/one").toString(),
            source.uri to tracks.first().uri,
        ))
        runBlocking {
            app.repository.update { it.copy(sources = listOf(other, source),
                tracks = it.tracks.map { song -> if (song.id == linked.id) linked else song }) }
        }
        unavailable(true)
        onMain { player.prepare(); player.play() }
        await("Shared song fails") { app.playbackProblem.value != null }
        unavailable(false)
        val owner = onMain { model(mainActivity()) }
        await("Recovery grant action") {
            mainActivity().findViewById<View>(R.id.recoveryReselect).let { it.isShown && it.isEnabled }
        }
        onMain { mainActivity().findViewById<View>(R.id.recoveryReselect).performClick() }
        try {
            await("Reselect the matching grant") { resumedActivity() is FileExplorerActivity }
            onMain { assertEquals(source.uri, owner.consumeFolderRecovery()) }
        } finally {
            onMain { (resumedActivity() as? FileExplorerActivity)?.finish() }
            await("Return from picker") { resumedActivity() is MainActivity }
        }
    }

    @Test fun folderReselectionUsesTheNativePickerAndRetainsSavedReferences() = withPlayer { _, player ->
        val before = app.repository.library.value
        runBlocking { app.repository.markSourceError(source.uri, "Fixture access was lost") }
        await("Reselect action") {
            mainActivity().findViewById<View>(R.id.recoveryReselect).let { it.isShown && it.isEnabled }
        }
        onMain { mainActivity().findViewById<View>(R.id.recoveryReselect).performClick() }
        await("Native folder picker") { resumedActivity() is FileExplorerActivity }
        val explorer = onMain { resumedActivity() as FileExplorerActivity }
        val picker = onMain {
            ViewModelProvider(explorer)[FileExplorerViewModel::class.java].also {
                it.navigate(ExplorerLocation(ExplorerPlace.FOLDER, source.name, source.uri))
            }
        }
        await("Original folder is selectable") { picker.state.value.canSelectFolder && !picker.state.value.loading }
        onMain { explorer.findViewById<View>(R.id.explorerSelect).performClick() }
        await("Same source restored") {
            resumedActivity() is MainActivity && model(mainActivity()).scan.value == null &&
                !app.repository.library.value.sources.single().unavailable
        }
        onMain {
            assertEquals(source.uri, app.repository.library.value.sources.single().uri)
            assertEquals(before.favorites, app.repository.library.value.favorites)
            assertEquals(before.playlists, app.repository.library.value.playlists)
            assertFalse("Reselecting storage is not a playback request", player.playWhenReady)
        }
    }

    @Test fun selectingADifferentFolderDoesNotRewriteTheLibrary() = withPlayer { _, player ->
        val before = app.repository.library.value
        val other = DocumentsContract.buildDocumentUriUsingTree(tree, "music/different")
        onMain { model(mainActivity()).reselectFolder(source.uri, other, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        await("Rejected wrong folder") { model(mainActivity()).recoveringSource.value == null && app.notice.value?.isError == true }
        assertEquals(before, app.repository.library.value)
        onMain { assertFalse(player.playWhenReady) }
    }

    @Test fun explorerFailureHasPersistentDirectRetryAndClearsOnSuccess() {
        val intent = FileExplorerActivity.intent(app, com.macarmusic.app.model.ExplorerMode.MUSIC_FOLDER)
        ActivityScenario.launch<FileExplorerActivity>(intent).use { scenario ->
            lateinit var activity: FileExplorerActivity
            lateinit var picker: FileExplorerViewModel
            unavailable(true)
            scenario.onActivity {
                activity = it
                picker = ViewModelProvider(it)[FileExplorerViewModel::class.java]
                picker.navigate(ExplorerLocation(ExplorerPlace.FOLDER, source.name, source.uri))
            }
            await("Explorer failure") { picker.state.value.error != null }
            await("Explorer recovery action") {
                activity.findViewById<View>(R.id.recoveryRetry).let { it.isShown && it.isEnabled }
            }
            unavailable(false)
            onMain { activity.findViewById<View>(R.id.recoveryRetry).performClick() }
            await("Explorer retry") { !picker.state.value.loading && picker.state.value.error == null && picker.state.value.items.isNotEmpty() }
            onMain { assertFalse(activity.findViewById<View>(R.id.recoveryStatus).isShown) }
        }
    }

    @Test fun appearanceSettingsKeepCustomBackgroundAndLogoBesideTheirParents() = withPlayer { scenario, player ->
        onMain { model(mainActivity()).openSettings() }
        await("General appearance settings") { mainActivity().findViewById<View>(R.id.settingsTheme)?.isShown == true }
        for (theme in listOf(DisplaySettings.CUSTOM_THEME, AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES, DisplaySettings.CUSTOM_THEME)) {
            val backgroundVisibility = if (theme == DisplaySettings.CUSTOM_THEME) View.VISIBLE else View.GONE
            onMain { app.themeMode = theme }
            await("Conditional background controls") {
                val activity = resumedActivity() as? MainActivity
                activity?.hasWindowFocus() == true &&
                    activity.findViewById<View>(R.id.settingsTheme)?.isShown == true &&
                    activity.findViewById<View>(R.id.settingsBackgroundPanel)?.visibility == backgroundVisibility
            }
            scenario.onActivity { activity ->
                val parent = activity.findViewById<android.widget.LinearLayout>(R.id.settingsGeneralPanel)
                fun nextAfter(id: Int) = parent.getChildAt(parent.indexOfChild(activity.findViewById(id)) + 1).id
                assertEquals(R.id.settingsBackgroundPanel, nextAfter(R.id.settingsTheme))
                assertEquals(R.id.settingsAppLogo, nextAfter(R.id.settingsAccent))
                assertSame(activity.findViewById<View>(R.id.settingsBackgroundPanel),
                    activity.findViewById<View>(R.id.settingsBackgroundImage).parent)
                assertFalse(player.playWhenReady)
            }
        }
    }

    @Test fun primaryTransportAndCurrentModeStayClearWithoutMovingControls() = withPlayer { scenario, _ ->
        for (night in listOf(true, false)) for (blocks in listOf(true, false)) {
            onMain {
                app.themeMode = if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                app.displaySettings.update { it.copy(controlBlocks = blocks) }
            }
            await("Themed player") {
                val activity = resumedActivity() as? MainActivity
                activity != null && activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    (if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO) &&
                    activity.findViewById<View>(R.id.play).let { it.isShown && it.isEnabled && it.width > 0 && it.height > 0 }
            }
            instrumentation.waitForIdleSync()
            val originalBounds = onMain {
                listOf(R.id.play, R.id.switchPlaybackMode).map { id ->
                    mainActivity().findViewById<View>(id).let { Rect(it.left, it.top, it.right, it.bottom) }
                }
            }
            for (custom in listOf(null, 0x000000, 0xffffff, 0x13579b)) {
                onMain { app.displaySettings.update { it.copy(primaryControlColor = custom, buttonIcons = ButtonIconStyle.MONOCHROME) } }
                val color = onMain { custom?.or(Color.BLACK) ?:
                    AccentTheme.color(mainActivity(), com.google.android.material.R.attr.colorPrimaryContainer) }
                await("Primary button fill") {
                    listOf(R.id.play, R.id.miniPlay, R.id.switchPlaybackMode).all {
                        backgroundCenter(mainActivity().findViewById(it)) == color
                    }
                }
                scenario.onActivity { activity ->
                    val play = activity.findViewById<View>(R.id.play)
                    val next = activity.findViewById<View>(R.id.next)
                    assertEquals("Transport widths remain unchanged", play.width, next.width)
                    assertTrue(play.width >= 48 * activity.resources.displayMetrics.density)
                    assertEquals(color, backgroundCenter(play))
                    val mode = activity.findViewById<MaterialButton>(R.id.switchPlaybackMode)
                    assertEquals(color, backgroundCenter(mode))
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(mode.currentTextColor, color) >= 4.5)
                    val enabled = intArrayOf(android.R.attr.state_enabled)
                    for (id in listOf(R.id.play, R.id.miniPlay)) {
                        val ink = requireNotNull(activity.findViewById<ImageView>(id).imageTintList).getColorForState(enabled, 0)
                        assertTrue("Monochrome playback icon must contrast with its fill",
                            androidx.core.graphics.ColorUtils.calculateContrast(ink, color) >= 4.5)
                    }
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(
                        requireNotNull(mode.iconTint).getColorForState(enabled, 0), color) >= 4.5)
                    val status = activity.findViewById<TextView>(R.id.status).text.toString()
                    assertTrue(status, status.startsWith(activity.getString(R.string.music_mode)))
                    assertFalse("Mode emphasis must not be keyboard selection", play.isSelected)
                    assertTrue(mode.requestFocusFromTouch())
                    assertFalse("Mode focus must not be a selected state", mode.isSelected)
                    val position = Rect().also { assertTrue(play.getGlobalVisibleRect(it)) }
                    assertTrue(position.height() >= 48 * activity.resources.displayMetrics.density)
                    assertTrue(mode.width >= 48 * activity.resources.displayMetrics.density &&
                        mode.height >= 48 * activity.resources.displayMetrics.density)
                    assertEquals(originalBounds, listOf(R.id.play, R.id.switchPlaybackMode).map { id ->
                        activity.findViewById<View>(id).let { Rect(it.left, it.top, it.right, it.bottom) }
                    })
                    PlayerLayoutAssertions.assertModeActions(activity)
                }
            }
        }
    }

    @Test fun primaryButtonColorAppliesImmediatelyPersistsAndResets() = withPlayer { scenario, player ->
        val activity = onMain { mainActivity() }
        val accent = app.displaySettings.options.value.customAccentColor
        onMain { model(activity).openSettings() }
        await("Highlight color setting") {
            mainActivity().findViewById<View>(R.id.settingsPrimaryControlColor)?.isShown == true
        }
        onView(withId(R.id.settingsPrimaryControlColor)).perform(scrollTo(), click())
        onView(withTagValue(equalTo("rainbow:0"))).inRoot(isDialog()).perform(click())
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        assertNull(app.displaySettings.options.value.primaryControlColor)
        onView(withId(R.id.settingsPrimaryControlColor)).perform(scrollTo(), click())
        onView(withTagValue(equalTo("rainbow:0"))).inRoot(isDialog()).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        await("Applied highlight") {
            app.displaySettings.options.value.primaryControlColor == 0xff0000 &&
                backgroundCenter(mainActivity().findViewById(R.id.miniPlay)) == Color.RED &&
                backgroundCenter(mainActivity().findViewById(R.id.switchPlaybackMode)) == Color.RED
        }
        onMain {
            assertSame("Highlight changes do not recreate the screen", activity, mainActivity())
            assertEquals(accent, app.displaySettings.options.value.customAccentColor)
            assertEquals(0xff0000, DisplaySettings(app) {}.options.value.primaryControlColor)
            assertFalse(player.playWhenReady)
        }
        scenario.recreate()
        await("Restored highlight") {
            mainActivity() !== activity && mainActivity().hasWindowFocus() &&
                mainActivity().findViewById<View>(R.id.settingsPrimaryControlColor)?.isShown == true &&
                backgroundCenter(mainActivity().findViewById(R.id.miniPlay)) == Color.RED
        }
        onView(withId(R.id.settingsPrimaryControlColor)).perform(scrollTo(), click())
        onView(withId(R.id.colorNone)).inRoot(isDialog()).perform(click())
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        assertTrue(app.displaySettings.options.value.primaryControlHighlightEnabled)
        onView(withId(R.id.settingsPrimaryControlColor)).perform(scrollTo(), click())
        onView(withId(R.id.colorNone)).inRoot(isDialog()).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        await("Disabled highlights") {
            !app.displaySettings.options.value.primaryControlHighlightEnabled &&
                listOf(R.id.play, R.id.miniPlay, R.id.switchPlaybackMode).all {
                    backgroundCenter(mainActivity().findViewById(it)) != Color.RED
                }
        }
        onMain {
            assertFalse(DisplaySettings(app) {}.options.value.primaryControlHighlightEnabled)
            assertEquals(0xff0000, DisplaySettings(app) {}.options.value.primaryControlColor)
            assertFalse(player.playWhenReady)
        }
        scenario.recreate()
        await("Restored No highlights") {
            mainActivity().hasWindowFocus() &&
                mainActivity().findViewById<View>(R.id.settingsPrimaryControlColor)?.isShown == true &&
                backgroundCenter(mainActivity().findViewById(R.id.miniPlay)) != Color.RED
        }
        onView(withId(R.id.settingsPrimaryControlColor)).perform(scrollTo(), click())
        onView(withId(android.R.id.button3)).inRoot(isDialog()).perform(click())
        await("Automatic theme highlight") {
            val current = mainActivity()
            app.displaySettings.options.value.primaryControlHighlightEnabled &&
                app.displaySettings.options.value.primaryControlColor == null &&
                backgroundCenter(current.findViewById(R.id.miniPlay)) ==
                AccentTheme.color(current, com.google.android.material.R.attr.colorPrimaryContainer)
        }
        onView(withId(R.id.categoryAbout)).perform(scrollTo(), click())
        await("Highlight help") { mainActivity().findViewById<View>(R.id.aboutBody).isShown }
        onMain {
            val current = mainActivity()
            val help = current.findViewById<TextView>(R.id.aboutBody).text.toString()
            assertTrue(help.contains(current.getString(R.string.primary_control_color_help)))
            assertTrue(help.contains(current.getString(R.string.playback_mode_help)))
            assertNull(DisplaySettings(app) {}.options.value.primaryControlColor)
            assertFalse(player.playWhenReady)
            assertEquals(tracks.first().id, player.currentMediaItem?.mediaId)
        }
    }

    private fun backgroundCenter(view: View): Int {
        val image = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        val background = requireNotNull(view.background)
        val bounds = Rect(background.bounds)
        val state = background.state
        try {
            background.state = if (view.isEnabled) intArrayOf(android.R.attr.state_enabled) else intArrayOf()
            background.jumpToCurrentState()
            background.setBounds(0, 0, 80, 80)
            background.draw(Canvas(image))
            return image.getPixel(40, 40)
        } finally {
            background.bounds = bounds
            background.state = state
            background.jumpToCurrentState()
            image.recycle()
        }
    }

    private fun withPlayer(action: (ActivityScenario<MainActivity>, MediaController) -> Unit) {
        ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_MAIN)).use { scenario ->
            await("Player ready") { app.playbackReady.value && mainActivity().findViewById<View>(R.id.play).isEnabled }
            val future = onMain { MediaController.Builder(app, SessionToken(app,
                ComponentName(app, PlaybackService::class.java))).buildAsync() }
            val player = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    player.pause()
                    player.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                }
                await("Known music queue") { player.mediaItemCount == tracks.size && player.currentMediaItem?.mediaId == tracks.first().id }
                action(scenario, player)
            } finally {
                onMain { player.pause(); player.stop(); MediaController.releaseFuture(future) }
            }
        }
    }

    private fun unavailable(value: Boolean) {
        val state = checkNotNull(app.contentResolver.call(tree, "setUnavailable", null,
            Bundle().apply { putBoolean("value", value) }))
        assertTrue("Provider must acknowledge its state", state.containsKey("unavailable"))
        assertEquals(value, state.getBoolean("unavailable"))
    }

    private fun recordPlaybackState(stage: String, player: MediaController): Int {
        val provider = checkNotNull(app.contentResolver.call(tree, "scanFixtureState", null, null))
        assertTrue(provider.containsKey("unavailable") && provider.containsKey("openRequests"))
        val state = onMain { playbackStateDetails(player) }
        android.util.Log.i("PlayerExperienceTest", "MUSIC_PLAYBACK $stage: $state, " +
            "providerUnavailable=${provider.getBoolean("unavailable")}, " +
            "openRequests=${provider.getInt("openRequests")}, audioReads=${provider.getInt("audioReads")}")
        return provider.getInt("openRequests")
    }

    private fun playbackStateDetails(player: MediaController): String =
        "state=${player.playbackState}, playWhenReady=${player.playWhenReady}, playing=${player.isPlaying}, " +
            "loading=${player.isLoading}, suppression=${player.playbackSuppressionReason}, " +
            "error=${player.playerError?.errorCode}:${player.playerError?.message}, " +
            "mediaId=${player.currentMediaItem?.mediaId}, index=${player.currentMediaItemIndex}, count=${player.mediaItemCount}, " +
            "position=${player.currentPosition}, duration=${player.duration}, buffered=${player.bufferedPosition}, " +
            "repeat=${player.repeatMode}, shuffle=${player.shuffleModeEnabled}, " +
            "seekable=${player.isCurrentMediaItemSeekable}, " +
            "seekCommand=${player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)}, " +
            "options=${app.playbackSettings.options.value}, " +
            "mainAction=${mainActivity().findViewById<View>(R.id.play).contentDescription}, " +
            "miniAction=${mainActivity().findViewById<View>(R.id.miniPlay).contentDescription}"

    private fun resumedActivity() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).firstOrNull()

    private fun mainActivity() = requireNotNull(resumedActivity() as? MainActivity)
    private fun model(activity: MainActivity) = ViewModelProvider(activity)[MusicViewModel::class.java]

    private fun await(message: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!onMain(condition)) {
            assertTrue(message, SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(20)
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask(Callable(action))
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
