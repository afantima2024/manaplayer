package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.data.LibraryCodec
import com.macarmusic.app.data.PlaybackSettings
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class MusicIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val root = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "music")

    @Before fun setUp() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        app.notice.value?.let { app.dismissNotice(it.id) }
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureGrantActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        available(true)
        runBlocking { app.repository.awaitReady() }
        onMain {
            app.playbackSettings.setLoop(LoopMode.OFF)
            app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
        }
    }

    @Test fun manifestHasNoNetworkOrBroadStoragePermission() {
        @Suppress("DEPRECATION")
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_AUDIO"))
        assertFalse(permissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test fun safBrowserRecursiveScanMetadataAndLibraryPersistence() = runBlocking(Dispatchers.IO) {
        app.repository.update { Library() }
        val source = app.repository.addSource(root, 0)
        assertFalse(source.persisted)
        val entries = app.repository.documents.children(source.uri, "music")
        assertEquals(2, entries.size)
        assertTrue(entries.first().isDirectory)
        assertEquals("music/one", entries.last().id)
        val result = app.repository.scan(source, null) {}
        assertEquals(2, result.tracks.size)
        assertTrue(result.tracks.all { it.durationMs in 59_000..61_000 })
        assertEquals("01 - Synthetic", result.tracks.first().title)
        assertTrue(result.tracks.all { it.sourceUris.containsKey(source.uri) })
        app.repository.update {
            val favorites = LibraryEdits.toggleFavorite(it, result.tracks.first().id)
            val playlist = LibraryEdits.createPlaylist(favorites, "integration", "Test playlist")
            LibraryEdits.addToPlaylist(playlist, "integration", result.tracks.map { track -> track.id })
        }
        val saved = LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText())
        assertEquals(app.repository.library.value, saved)
        assertEquals(2, saved.playlists.single().trackIds.size)
        assertEquals(1, saved.favorites.size)
    }

    @Test fun unavailableVolumeDoesNotWipeLibraryOrFavorites() = runBlocking(Dispatchers.IO) {
        app.repository.update { Library() }
        val source = app.repository.addSource(root, 0)
        val tracks = app.repository.scan(source, null) {}.tracks
        app.repository.update { LibraryEdits.toggleFavorite(it, tracks.first().id) }
        val before = app.repository.library.value
        available(false)
        try {
            try {
                app.repository.scan(source, null) {}
                fail("An unavailable provider must fail explicitly")
            } catch (expected: java.io.IOException) {
                assertNotNull(expected.message)
            } catch (expected: IllegalStateException) {
                assertNotNull(expected.message)
            }
            assertEquals(before, app.repository.library.value)
        } finally {
            available(true)
        }
    }

    @Test fun temporaryDocumentGrantAndUnsupportedUriAreHandled() = runBlocking(Dispatchers.IO) {
        val document = DocumentsContract.buildDocumentUri(AUTHORITY, "music/one")
        val result = app.repository.openDocument(document, 0)
        assertFalse(result.track.standalonePersisted)
        assertEquals(document.toString(), result.track.standaloneUri)
        assertTrue(result.track.durationMs > 0)
        try {
            app.repository.openDocument(Uri.parse("https://example.invalid/music.mp3"), 0)
            fail("Network URI accepted")
        } catch (expected: java.io.IOException) {
            assertNotNull(expected.message)
        }
    }

    @Test fun persistedFolderGrantCanBeRetainedAndReleased() = runBlocking(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val source = app.repository.addSource(root, flags)
        try {
            assertTrue(source.persisted)
            assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission })
            assertEquals(2, app.repository.documents.children(source.uri, "music").size)
        } finally {
            app.repository.documents.releaseReadPermission(root.toString())
        }
        assertFalse(app.contentResolver.persistedUriPermissions.any { it.uri == root && it.isReadPermission })
    }

    @Test fun navigationWorksInLandscapeAndPortrait() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            for (id in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists, R.id.nav_player)) {
                onView(withId(id)).perform(click())
            }
            onView(withId(R.id.artwork)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(0, it.findViewById<View>(R.id.navigation).paddingBottom) }
            screenshot("landscape-night")
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            onView(withId(R.id.nav_library)).perform(click())
            onView(withId(R.id.nav_player)).perform(click())
            onView(withId(R.id.artwork)).check(matches(isDisplayed()))
            scenario.onActivity { assertEquals(0, it.findViewById<View>(R.id.navigation).paddingBottom) }
            screenshot("portrait-night")
            scenario.onActivity { app.themeMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO }
            instrumentation.waitForIdleSync()
            onView(withId(R.id.nav_library)).perform(click())
            screenshot("portrait-day-library")
            scenario.onActivity { app.themeMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES }
        }
    }

    @Test fun servicePlaybackSeekKeysQueueAndBackgroundAreReal() {
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying && controller.currentPosition > 100 }
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await { !controller.playWhenReady }
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await { controller.isPlaying }
                await {
                    app.getSystemService(NotificationManager::class.java).activeNotifications.any {
                        it.notification.category == Notification.CATEGORY_TRANSPORT &&
                            it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
                    }
                }
                screenshot("playing")
                onMain {
                    controller.pause()
                    controller.seekTo(7_000)
                }
                await { !controller.playWhenReady && controller.currentPosition in 6_900..7_100 }
                onMain {
                    controller.shuffleModeEnabled = true
                    controller.repeatMode = Player.REPEAT_MODE_ALL
                    controller.moveMediaItem(0, 1)
                }
                await { controller.shuffleModeEnabled && controller.repeatMode == Player.REPEAT_MODE_ALL }
                onMain {
                    mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                }
                await { controller.isPlaying }
                scenario.moveToState(Lifecycle.State.CREATED)
                try {
                    shell("input keyevent KEYCODE_SLEEP")
                    await { !app.getSystemService(PowerManager::class.java).isInteractive }
                    val backgroundPosition = onMainResult { controller.currentPosition }
                    Thread.sleep(1_200)
                    assertTrue(onMainResult { controller.isPlaying && controller.currentPosition > backgroundPosition + 500 })
                } finally {
                    shell("input keyevent KEYCODE_WAKEUP")
                }
                onMain {
                    mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                }
                await { !controller.playWhenReady }
                onMain { controller.removeMediaItem(0) }
                await { controller.mediaItemCount == 1 }
                Thread.sleep(800)
                val saved = runBlocking { app.playback.read() }
                assertEquals(1, saved.queue.size)
                assertTrue(saved.shuffle)
                assertEquals(Player.REPEAT_MODE_ALL, saved.repeat)
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun mediaVolumeSliderChangesSystemStream() {
        val audio = app.getSystemService(AudioManager::class.java)
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                assertFalse(audio.isVolumeFixed)
                onMain { audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
                onView(withId(R.id.volume)).perform(scrollTo(), click())
                await { audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0 }
            } finally {
                onMain { audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0) }
            }
        }
    }

    @Test fun invalidPlaybackStopsWithoutSkipping() {
        val tracks = runBlocking(Dispatchers.IO) {
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                val unavailable = tracks.first().copy(
                    uri = DocumentsContract.buildDocumentUriUsingTree(root, "music/missing").toString(),
                )
                onMain {
                    controller.shuffleModeEnabled = false
                    controller.repeatMode = Player.REPEAT_MODE_OFF
                    controller.setMediaItems(listOf(unavailable.toMediaItem(), tracks.last().toMediaItem()), 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.playerError != null && !controller.playWhenReady }
                assertEquals(0, onMainResult { controller.currentMediaItemIndex })
                assertEquals(2, onMainResult { controller.mediaItemCount })
                assertTrue(app.notice.value?.isError == true)
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun savedQueueRestoresPausedWithoutAutoplay() {
        val tracks = runBlocking(Dispatchers.IO) {
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        app.stopService(Intent(app, PlaybackService::class.java))
        Thread.sleep(500)
        onMain {
            app.playbackSettings.setLoop(LoopMode.SINGLE_FILE)
            app.playbackSettings.setOrder(PlayOrder.RANDOM)
        }
        val snapshot = PlaybackSnapshot(queue = tracks, index = 1, positionMs = 12_000, shuffle = true, repeat = 1)
        app.playback.allowReplacingUnreadableQueue()
        app.playback.save(snapshot)
        Thread.sleep(500)
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                await { controller.mediaItemCount == 2 }
                assertFalse(onMainResult { controller.playWhenReady })
                assertEquals(Player.STATE_IDLE, onMainResult { controller.playbackState })
                assertEquals(1, onMainResult { controller.currentMediaItemIndex })
                assertEquals(12_000, onMainResult { controller.currentPosition })
                assertTrue(onMainResult { controller.shuffleModeEnabled })
                assertEquals(1, onMainResult { controller.repeatMode })
            } finally {
                onMain { MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun settingsChangeLanguageThemeAndOrderWithoutInterruptingPlayback() {
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                chooseSetting(R.id.settingsLanguage, R.string.language_chinese)
                assertEquals("小悦播放器", app.text(R.string.app_name))
                onView(withId(R.id.settingsLanguage)).check(matches(withText(containsString("语言"))))
                assertEquals("设置", app.text(R.string.settings))
                chooseSetting(R.id.settingsTheme, R.string.theme_day)
                assertEquals(AppCompatDelegate.MODE_NIGHT_NO, app.themeMode)
                await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks.first().id }
                scenario.recreate()
                onView(withId(R.id.settingsLanguage)).check(matches(withText(containsString("简体中文"))))
                screenshot("settings-chinese-day")
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                onView(withId(R.id.settingsLanguage)).check(matches(isDisplayed()))
                screenshot("settings-chinese-portrait")
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                chooseSetting(R.id.settingsOrder, R.string.order_random)
                await { controller.shuffleModeEnabled }
                chooseSetting(R.id.settingsLoop, R.string.loop_single_file)
                await { controller.repeatMode == Player.REPEAT_MODE_ONE }
                val current = onMainResult { controller.currentMediaItem!!.mediaId }
                onMain { controller.seekTo(59_700) }
                await { controller.currentPosition < 2_000 && controller.isPlaying }
                assertEquals(current, onMainResult { controller.currentMediaItem!!.mediaId })
                chooseSetting(R.id.settingsOrder, R.string.order_sequential)
                await { !controller.shuffleModeEnabled }
                chooseSetting(R.id.settingsLanguage, R.string.language_english)
                assertEquals("ManaPlayer", app.text(R.string.app_name))
                chooseSetting(R.id.settingsTheme, R.string.theme_night)
                onView(withId(R.id.settingsLanguage)).perform(scrollTo())
                onView(withId(R.id.settingsLanguage)).check(matches(withText(containsString("Language"))))
                screenshot("settings-english-night")
                val persisted = PlaybackSettings(app).options.value
                assertEquals(LoopMode.SINGLE_FILE, persisted.loop)
                assertEquals(PlayOrder.SEQUENTIAL, persisted.order)
            } finally {
                onMain {
                    controller.pause()
                    MediaController.releaseFuture(future)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                    app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
                }
                instrumentation.waitForIdleSync()
            }
        }
    }

    @Test fun multipleFolderChoicesPersistAndLoopScopesUpdateTheRealQueue() {
        val album = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "music/album")
        val sources = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val musicSource = app.repository.addSource(root, 0)
            app.repository.scan(musicSource, null) {}
            val albumSource = app.repository.addSource(album, 0)
            app.repository.scan(albumSource, null) {}
            app.repository.update { LibraryEdits.toggleFavorite(it, it.tracks.first().id) }
            app.repository.library.value.sources
        }
        val tracks = app.repository.library.value.tracks
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.settingsFolders)).perform(scrollTo(), click())
                onView(withText(app.text(R.string.folder_choice, sources.first().name, sources.first().lastScanCount)))
                    .inRoot(isDialog()).perform(click())
                onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
                await { app.repository.library.value.sources.count { it.selectedForPlayback } == 1 }
                chooseSetting(R.id.settingsLoop, R.string.loop_selected_folders)
                await { controller.mediaItemCount == 1 && !controller.playWhenReady && controller.repeatMode == Player.REPEAT_MODE_ALL }
                val nested = tracks.first { album.toString() in it.sourceUris }
                assertEquals(nested.id, onMainResult { controller.currentMediaItem!!.mediaId })
                assertEquals(0, onMainResult { controller.currentPosition })
                val saved = LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText())
                assertFalse(saved.sources.first().selectedForPlayback)
                assertTrue(saved.sources.last().selectedForPlayback)
                assertEquals(2, saved.tracks.size)
                assertEquals(1, saved.favorites.size)
                onView(withId(R.id.settingsPlayFolders)).perform(scrollTo(), click())
                await { controller.isPlaying && controller.mediaItemCount == 1 }
                assertEquals(nested.id, onMainResult { controller.currentMediaItem!!.mediaId })
                onView(withId(R.id.shuffle)).perform(scrollTo(), click())
                await { controller.shuffleModeEnabled && app.playbackSettings.options.value.order == PlayOrder.RANDOM }
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await { !controller.playWhenReady }
                onView(withId(R.id.action_settings)).perform(click())
                runBlocking { app.repository.addSource(root, 0) }
                assertFalse(app.repository.library.value.sources.first { it.uri == root.toString() }.selectedForPlayback)
                onView(withId(R.id.settingsFolders)).perform(scrollTo(), click())
                onView(withText(app.text(R.string.folder_choice, sources.first().name, sources.first().lastScanCount)))
                    .inRoot(isDialog()).perform(click())
                onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
                await { controller.mediaItemCount == 2 }
                assertFalse(onMainResult { controller.playWhenReady })
                onMain { controller.setMediaItems(listOf(nested.toMediaItem()), 0, 4_000) }
                chooseSetting(R.id.settingsLoop, R.string.loop_folder)
                await { controller.mediaItemCount == 1 && controller.repeatMode == Player.REPEAT_MODE_ALL }
                assertEquals(nested.id, onMainResult { controller.currentMediaItem!!.mediaId })
                assertEquals(4_000, onMainResult { controller.currentPosition })
                chooseSetting(R.id.settingsLoop, R.string.loop_selected_folders)
                await { controller.mediaItemCount == 2 }
                onView(withId(R.id.settingsFolders)).perform(scrollTo(), click())
                for (source in app.repository.library.value.sources) {
                    onView(withText(app.text(R.string.folder_choice, source.name, source.lastScanCount)))
                        .inRoot(isDialog()).perform(click())
                }
                onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
                await { controller.mediaItemCount == 0 && !controller.playWhenReady }
                assertEquals(2, app.repository.library.value.tracks.size)
                onView(withId(R.id.settingsFolders)).perform(scrollTo())
                screenshot("settings-folders")
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    private fun rotate(scenario: ActivityScenario<MainActivity>, orientation: Int) {
        val expected = when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            else -> error("Unsupported test orientation: $orientation")
        }
        scenario.onActivity { it.requestedOrientation = orientation }
        await {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .any { !it.isChangingConfigurations && it.resources.configuration.orientation == expected }
        }
        instrumentation.waitForIdleSync()
    }

    private fun chooseSetting(viewId: Int, label: Int) {
        onView(withId(viewId)).perform(scrollTo(), click())
        onView(withText(app.text(label))).inRoot(isDialog()).perform(click())
        instrumentation.waitForIdleSync()
    }

    @Test fun independentSystemBrowserControlsRestoredPlaybackAndReceivesMetadata() {
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        app.stopService(Intent(app, PlaybackService::class.java))
        Thread.sleep(500)
        app.playback.save(PlaybackSnapshot(queue = tracks, positionMs = 6_000))
        Thread.sleep(500)
        startExternalController()
        try {
            awaitExternal { it.getBoolean("connected") || it.containsKey("error") }
            val connected = externalState()
            assertNull(connected.getString("error"), connected.getString("error"))
            assertTrue("Player is not discoverable as an Android music service", connected.getBoolean("discovered"))
            assertTrue("Player is not discoverable as an Android music app", connected.getBoolean("discoveredMusicApp"))
            assertNotEquals(Process.myUid(), connected.getInt("uid"))
            assertFalse("The fixture must not have privileged media access", connected.getBoolean("trusted"))
            awaitExternal { it.getString("browsedId") == tracks.first().id }
            assertNotEquals(PlatformPlaybackState.STATE_PLAYING, externalState().getInt("state"))
            externalCommand("play")
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PLAYING &&
                it.getLong("position") > 6_000 && it.getLong("duration") > 0 }
            assertEquals(tracks.first().title, externalState().getString("title"))
            externalCommand("pause")
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PAUSED }
            externalCommand("seek", "14000")
            awaitExternal { it.getLong("position") in 13_900..14_100 }
            val metadataChanges = externalState().getInt("metadataChanges")
            externalCommand("next")
            awaitExternal { it.getString("mediaId") == tracks.last().id &&
                it.getInt("metadataChanges") > metadataChanges }
            externalCommand("refreshBrowser")
            awaitExternal { it.getString("browsedId") == tracks.last().id }
            externalCommand("previous")
            awaitExternal { it.getString("mediaId") == tracks.first().id }
            externalCommand("playId", tracks.last().id)
            awaitExternal { it.getString("mediaId") == tracks.last().id &&
                it.getInt("state") == PlatformPlaybackState.STATE_PLAYING }
            externalCommand("previous")
            awaitExternal { it.getString("mediaId") == tracks.first().id }
            app.notice.value?.let { app.dismissNotice(it.id) }
            externalCommand("playUri", "content://com.macarmusic.app.test.documents/document/not-selected")
            await { app.notice.value?.isError == true }
            assertEquals(tracks.first().id, externalState().getString("mediaId"))
            externalCommand("pause")
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PAUSED }
        } finally {
            externalCommand("disconnect")
            app.stopService(Intent(app, PlaybackService::class.java))
        }
    }

    @Test fun independentRecentSongResumesItsQueueAndPositionWithoutLaunchingPlayerUi() {
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        app.stopService(Intent(app, PlaybackService::class.java))
        Thread.sleep(500)
        app.playback.save(PlaybackSnapshot(queue = tracks, index = 1, positionMs = 18_000))
        startExternalController(recent = true)
        try {
            awaitExternal { it.getString("browsedId") == tracks.last().id || it.containsKey("error") }
            assertNull(externalState().getString("error"))
            assertNotEquals(PlatformPlaybackState.STATE_PLAYING, externalState().getInt("state"))
            externalCommand("playId", externalState().getString("browsedId")!!)
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PLAYING &&
                it.getLong("position") > 18_000 }
            assertEquals(tracks.last().id, externalState().getString("mediaId"))
            assertEquals(2, externalState().getInt("queueSize"))
            externalCommand("stop")
            // Media3 maps an explicitly stopped/idle player to Android's STATE_NONE.
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_NONE }
            val stoppedPosition = externalState().getLong("position")
            Thread.sleep(300)
            assertEquals(stoppedPosition, externalState().getLong("position"))
            externalCommand("play")
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PLAYING &&
                it.getLong("position") >= stoppedPosition }
            assertEquals(tracks.last().id, externalState().getString("mediaId"))
            externalCommand("playUri", tracks.first().uri)
            awaitExternal { it.getString("mediaId") == tracks.first().id &&
                it.getInt("state") == PlatformPlaybackState.STATE_PLAYING }
            assertEquals(2, externalState().getInt("queueSize"))
            externalCommand("pause")
            awaitExternal { it.getInt("state") == PlatformPlaybackState.STATE_PAUSED }
        } finally {
            externalCommand("disconnect")
            app.stopService(Intent(app, PlaybackService::class.java))
        }
    }

    private fun startExternalController(recent: Boolean = false) {
        app.contentResolver.call(root, "resetControllerStatus", null, null)
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureMediaControllerActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("recent", recent)
        })
    }

    private fun externalState(): Bundle =
        requireNotNull(app.contentResolver.call(root, "controllerStatus", null, null))

    private fun awaitExternal(condition: (Bundle) -> Boolean) {
        try {
            await { condition(externalState()) }
        } catch (error: AssertionError) {
            val state = externalState().apply { size() }
            throw AssertionError("External controller status: $state; notice: ${app.notice.value}", error)
        }
    }

    private fun externalCommand(command: String, value: String = "") {
        app.contentResolver.call(root, "controllerCommand", command, Bundle().apply { putString("value", value) })
    }

    private fun available(value: Boolean) {
        app.contentResolver.call(root, "setUnavailable", null, Bundle().apply { putBoolean("value", !value) })
    }

    private fun mediaKey(keyCode: Int) {
        val audio = app.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(400, 5_000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> onMainResult(block: () -> T): T {
        var value: T? = null
        onMain { value = block() }
        return requireNotNull(value)
    }

    private fun await(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (onMainResult(condition)) return
            Thread.sleep(100)
        }
        fail("Condition was not satisfied within $timeoutMs ms")
    }

    private companion object {
        const val AUTHORITY = "com.macarmusic.app.test.documents"
    }
}
