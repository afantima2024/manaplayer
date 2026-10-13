package com.macarmusic.app

import android.content.ComponentName
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.app.Notification
import android.app.NotificationManager
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.media.session.PlaybackState as PlatformPlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.provider.DocumentsContract
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ContextThemeWrapper
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.data.LibraryCodec
import com.macarmusic.app.data.PlaybackSettings
import com.macarmusic.app.data.BackgroundEffects
import com.macarmusic.app.data.BackgroundRepository
import com.macarmusic.app.data.BackgroundTint
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.lyrics.LocalLyricsLoader
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.LyricQueries
import com.macarmusic.app.lyrics.LyricQuery
import com.macarmusic.app.lyrics.LyricsRepository
import com.macarmusic.app.lyrics.OnlineLyricsClient
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackTimeMode
import com.macarmusic.app.model.NavigationPosition
import com.macarmusic.app.model.FireStyle
import com.macarmusic.app.model.SpectrumColors
import com.macarmusic.app.model.SlideshowOptions
import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.SpectrumPalette
import com.macarmusic.app.model.SpectrumPosition
import com.macarmusic.app.model.SpectrumStyle
import com.macarmusic.app.model.TransitionStyle
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Track
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.SpectrumAnalyzer
import com.macarmusic.app.playback.SpectrumSource
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.LyricsAdapter
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.TimeText
import com.macarmusic.app.ui.SettingsCategory
import com.macarmusic.app.ui.SpectrumView
import com.macarmusic.app.ui.PlayerNavigationView
import com.macarmusic.app.ui.PlayerLayout
import com.macarmusic.app.ui.PlayerBlockDrawable
import com.macarmusic.app.ui.AboutDialog
import com.macarmusic.app.ui.PhotoViewerDialog
import com.macarmusic.app.ui.ZoomImageView
import com.macarmusic.app.ui.awaitDraw
import com.macarmusic.app.playback.SpectrumFrame
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
        lyricMode("available")
        app.contentResolver.call(root, "setLyricReadDelay", "0", null)
        runBlocking { app.repository.awaitReady() }
        onMain {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            app.displaySettings.update {
                it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false,
                    blur = 0, tint = BackgroundTint.DARK, tintStrength = 35,
                    slideshow = SlideshowOptions(), settingsTransition = TransitionStyle.FADE,
                    timeMode = PlaybackTimeMode.ELAPSED, spectrum = SpectrumOptions(), controlBlocks = true,
                    mainTimeMode = PlaybackTimeMode.ELAPSED, navigationPosition = NavigationPosition.BOTTOM,
                    lyricsDragSeek = false, lyricsSmoothScroll = true, lyricEnlargementPercent = 20,
                    blockOutline = BlockOutline())
            }
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.playbackSettings.setLoop(LoopMode.OFF)
            app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
        }
    }

    @Test fun firstFrameBuildsOnlyThePlayerThenLoadsOtherScreensOnDemand() {
        @Suppress("DEPRECATION")
        fun serviceRunning() = app.getSystemService(android.app.ActivityManager::class.java)
            .getRunningServices(50).any { it.service.className == PlaybackService::class.java.name }
        val tracks = fixtureTracks()
        app.stopService(Intent(app, PlaybackService::class.java))
        await { !app.playbackReady.value && !serviceRunning() }
        runBlocking { app.playback.read() }
        app.playback.allowReplacingUnreadableQueue()
        app.playback.save(PlaybackSnapshot(queue = tracks, index = 1, positionMs = 12_000))
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        runBlocking {
            app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background")),
                app.displaySettings::installImages)
        }
        var firstDrawChildren = -1
        var lyricsAtFirstDraw = true
        var serviceAtFirstDraw = true
        var backgroundAtFirstDraw = true
        var controlsDrawAt = 0L
        var backgroundDrawAt = 0L
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (activity !is MainActivity || firstDrawChildren >= 0) return
                val container = activity.findViewById<ViewGroup>(R.id.screenContainer)
                val model = ViewModelProvider(activity)[MusicViewModel::class.java]
                val observer = container.viewTreeObserver
                val listener = object : ViewTreeObserver.OnDrawListener {
                    override fun onDraw() {
                        val background = activity.findViewById<View>(R.id.backgroundImage)
                        if (firstDrawChildren < 0) {
                            firstDrawChildren = container.childCount
                            lyricsAtFirstDraw = model.lyricsActive
                            serviceAtFirstDraw = serviceRunning()
                            backgroundAtFirstDraw = background.visibility == View.VISIBLE
                        }
                        if (controlsDrawAt == 0L && activity.findViewById<View>(R.id.play).isEnabled) {
                            controlsDrawAt = SystemClock.elapsedRealtime()
                        }
                        if (background.visibility == View.VISIBLE && backgroundDrawAt == 0L) {
                            backgroundDrawAt = SystemClock.elapsedRealtime()
                            container.post { if (observer.isAlive) observer.removeOnDrawListener(this) }
                        }
                    }
                }
                observer.addOnDrawListener(listener)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { firstDrawChildren >= 0 }
                assertEquals(1, firstDrawChildren)
                assertFalse("Lyric lookup must wait for the first usable player frame", lyricsAtFirstDraw)
                assertFalse("Cold service creation must not precede the first player draw", serviceAtFirstDraw)
                assertFalse("Background decoding must not delay the first frame", backgroundAtFirstDraw)
                await {
                    resumedActivity().findViewById<View>(R.id.play).isEnabled &&
                        ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricsActive
                }
                scenario.onActivity { activity ->
                    assertNull(activity.findViewById<View>(R.id.settingsPanel))
                    assertTrue(ViewModelProvider(activity)[MusicViewModel::class.java].visibleTracks.value.isEmpty())
                    assertEquals(TimeText.format(12_000), activity.findViewById<TextView>(R.id.position).text.toString())
                }
                await { backgroundDrawAt > 0 }
                assertTrue("Backgrounds need a separate one-second grace period after usable controls",
                    controlsDrawAt > 0 && backgroundDrawAt - controlsDrawAt >= 950)
                val future = MediaController.Builder(app, SessionToken(app,
                    ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    assertEquals(2, onMainResult { controller.mediaItemCount })
                    assertEquals(1, onMainResult { controller.currentMediaItemIndex })
                    assertFalse(onMainResult { controller.playWhenReady })
                    assertEquals(Player.STATE_IDLE, onMainResult { controller.playbackState })
                } finally {
                    onMain { MediaController.releaseFuture(future) }
                }
                val destinations = listOf(R.id.nav_library, R.id.nav_queue, R.id.nav_playlists, R.id.nav_folders)
                destinations.forEachIndexed { index, id ->
                    onView(withId(id)).perform(click())
                    scenario.onActivity {
                        assertEquals(index + 2, it.findViewById<ViewGroup>(R.id.screenContainer).childCount)
                    }
                }
                onView(withId(R.id.action_settings)).perform(click())
                scenario.onActivity { assertEquals(6, it.findViewById<ViewGroup>(R.id.screenContainer).childCount) }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals("Recreation restores the selected page, not every hidden page",
                        2, it.findViewById<ViewGroup>(R.id.screenContainer).childCount)
                    assertTrue(it.findViewById<View>(R.id.settingsPanel).isShown)
                }
                pressBack()
                onView(withId(R.id.nav_player)).perform(click())
                var cancelledDrawResumed = false
                scenario.onActivity { activity ->
                    val job = activity.lifecycleScope.launch {
                        activity.findViewById<View>(R.id.root).awaitDraw()
                        cancelledDrawResumed = true
                    }
                    assertFalse(cancelledDrawResumed)
                    job.cancel()
                }
                instrumentation.waitForIdleSync()
                assertFalse("A cancelled frame callback must not start deferred work", cancelledDrawResumed)
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    @Test fun lyricRequestsStayDeferredUntilTheUiEnablesThemAndUseTheLatestTrack() {
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        val store = ViewModelStore()
        val model = onMainResult { MusicViewModel(app, SavedStateHandle()).also { store.put("startup", it) } }
        try {
            onMain { model.requestLyrics(tracks.first()) }
            assertNull(model.lyrics.value.trackId)
            assertEquals(0, app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("reads"))
            onMain {
                model.requestLyrics(tracks.last())
                model.setLyricsActive(true)
            }
            await { model.lyrics.value.result?.lyrics?.lines?.firstOrNull()?.text == "Nested text fixture" }
            assertEquals(tracks.last().id, model.lyrics.value.trackId)
            onMain { model.setLyricsActive(false) }
            assertNull(model.lyrics.value.trackId)
        } finally {
            onMain { store.clear() }
        }
    }

    @Test fun slowLyricsCannotDelayPlaybackOrNavigationAndCancelledResultsStayHidden() {
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        app.contentResolver.call(root, "setLyricReadDelay", "5000", null)
        val future = MediaController.Builder(app, SessionToken(app,
            ComponentName(app, PlaybackService::class.java))).buildAsync()
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            await { app.playbackReady.value }
            onMain {
                controller.pause()
                controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await {
                    app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") > 0
                }
                scenario.onActivity {
                    assertTrue(it.findViewById<View>(R.id.play).isEnabled)
                    assertTrue(ViewModelProvider(it)[MusicViewModel::class.java].lyrics.value.loading)
                }
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await(3_000) { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.settingsPanel).isShown) }
                assertTrue("Playback and Settings must work while the lyric provider is still blocked",
                    app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") > 0)
                onMain {
                    controller.seekTo(1, 0)
                    app.displaySettings.update { it.copy(lyricsEnabled = false) }
                }
                pressBack()
                await {
                    ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyrics.value.trackId == null
                }
                await {
                    app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") == 0
                }
                scenario.onActivity {
                    assertNull((it.findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter).lyrics)
                }
                onMain { app.displaySettings.update { it.copy(lyricsEnabled = true) } }
                await {
                    ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyrics.value
                        .result?.lyrics?.lines?.firstOrNull()?.text == "Nested text fixture"
                }
                assertEquals(tracks.last().id, onMainResult { controller.currentMediaItem!!.mediaId })
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { assertFalse(ViewModelProvider(it)[MusicViewModel::class.java].lyricsActive) }
                assertTrue(onMainResult { controller.isPlaying })
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricsActive }
            }
        } finally {
            app.contentResolver.call(root, "setLyricReadDelay", "0", null)
            onMain { controller.pause(); MediaController.releaseFuture(future) }
        }
    }

    @Test fun launchWindowUsesTheSmallBrandedDrawableAndHandsOffToTheSavedTheme() {
        @Suppress("DEPRECATION")
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        assertEquals(R.style.Theme_ManaPlayer_Launch, info.theme)
        val launch = ContextThemeWrapper(app, info.theme)
        val value = TypedValue()
        assertTrue(launch.theme.resolveAttribute(android.R.attr.windowBackground, value, true))
        assertEquals(R.drawable.launch_background, value.resourceId)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            val drawable = requireNotNull(launch.getDrawable(value.resourceId))
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            assertEquals(0xff101b24.toInt(), bitmap.getPixel(0, 0))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("The starting window must include the music icon, not just a blank fill",
                pixels.any { it == 0xff67e3cb.toInt() })
        } finally {
            bitmap.recycle()
        }
        if (Build.VERSION.SDK_INT >= 31) {
            assertTrue(launch.theme.resolveAttribute(android.R.attr.windowSplashScreenBackground, value, true))
            assertEquals(0xff101b24.toInt(), launch.getColor(value.resourceId))
            assertTrue(launch.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
            assertEquals(R.drawable.ic_launcher, value.resourceId)
        }
        onMain { app.themeMode = AppCompatDelegate.MODE_NIGHT_NO }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals(Configuration.UI_MODE_NIGHT_NO, it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                assertTrue(it.theme.resolveAttribute(android.R.attr.windowBackground, value, true))
                assertNotEquals("The launch icon must not remain behind the actual player", R.drawable.launch_background, value.resourceId)
            }
            screenshot("startup-day-handoff")
        }
    }

    @Test fun manifestAllowsOptionalAudioLibraryAccessWithoutAllFilesOrUnnecessaryPermissions() {
        @Suppress("DEPRECATION")
        val permissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertTrue(permissions.contains(com.macarmusic.app.data.MediaStoreAccess.requiredPermission()))
        assertFalse(permissions.contains("android.permission.WRITE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_VIDEO"))
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(permissions.contains("android.permission.RECORD_AUDIO"))
        assertFalse(permissions.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertFalse(permissions.contains("android.permission.POST_NOTIFICATIONS"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"))
    }

    @Test fun localLyricsWinAndPreviousLibrariesDiscoverOpaqueParents() = runBlocking(Dispatchers.IO) {
        val tracks = fixtureTracks()
        val oldTrack = tracks.first().copy(sourceParents = emptyMap())
        app.repository.update { it.copy(tracks = it.tracks.map { track -> track.copy(sourceParents = emptyMap()) }) }
        var requests = 0
        val repository = LyricsRepository(app, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> requests++; error("Local lyrics must take precedence") }) { _, _ -> })
        val result = repository.load(oldTrack, online = true, refresh = true)
        assertEquals(LyricOrigin.LOCAL_FILE, result.origin)
        val lyrics = requireNotNull(result.lyrics)
        assertEquals("Synthetic middle", lyrics.lines[lyrics.activeLine(15_000)].text)
        assertEquals(0, requests)
        assertEquals("music", app.repository.library.value.tracks.first().sourceParents[root.toString()])
        assertEquals(2, app.repository.documents.children(root.toString(), "music").size)
        assertEquals(4, app.repository.documents.children(root.toString(), "music", includeLyrics = true).size)
        val direct = app.repository.openDocument(DocumentsContract.buildDocumentUri(AUTHORITY, "music/one"), 0).track
        assertTrue(direct.sourceParents.isEmpty())
        assertEquals(LyricIssue.FOLDER_ACCESS_NEEDED, LocalLyricsLoader(app, app.repository).load(direct).issue)
        val browser = app.repository.browserTrack(tracks.last().uri, root.toString(), "music/album").track
        assertEquals("music/album", browser.sourceParents[root.toString()])
        assertEquals("Nested text fixture", LocalLyricsLoader(app, app.repository).load(browser).lyrics!!.lines.first().text)
    }

    @Test fun onlineFallbackCachesAcrossRepositoriesButNewLocalLyricsAlwaysWin() = runBlocking(Dispatchers.IO) {
        lyricMode("missing")
        val track = fixtureTracks().first()
        var requests = 0
        val client = syntheticLyricClient(track) { requests++ }
        val repository = LyricsRepository(app, app.repository, app.applicationScope, client)
        val fetched = repository.load(track, online = true, refresh = true)
        assertEquals(LyricOrigin.KUGOU, fetched.origin)
        assertFalse(fetched.cached)
        assertEquals(3, requests)
        val restored = LyricsRepository(app, app.repository, app.applicationScope, client).load(track, online = true)
        assertTrue(restored.cached)
        assertEquals(fetched.lyrics, restored.lyrics)
        assertEquals(3, requests)
        assertNull(repository.load(track, online = false).lyrics)
        lyricMode("available")
        val local = repository.load(track, online = true)
        assertEquals(LyricOrigin.LOCAL_FILE, local.origin)
        assertEquals("Synthetic start", local.lyrics!!.lines.first().text)
        assertEquals(3, requests)
    }

    @Test fun unreadableLocalLyricsAndFailedOnlineLookupHaveBoundedExplicitResults() = runBlocking(Dispatchers.IO) {
        val track = fixtureTracks().first()
        lyricMode("invalid")
        assertEquals(LyricIssue.LOCAL_UNREADABLE, LocalLyricsLoader(app, app.repository).load(track).issue)
        lyricMode("unreadable")
        assertEquals(LyricIssue.LOCAL_UNREADABLE, LocalLyricsLoader(app, app.repository).load(track).issue)
        lyricMode("missing")
        var requests = 0
        val lookupTrack = track.copy(title = "Synthetic failed lookup fixture")
        val repository = LyricsRepository(app, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> requests++; throw java.io.IOException("Synthetic connection failure") }) { _, _ -> })
        val result = repository.load(lookupTrack, online = true, refresh = true)
        assertNull(result.lyrics)
        assertEquals(LyricIssue.LOOKUP_FAILED, result.issue)
        assertEquals(2, requests)
        assertEquals(result, repository.load(lookupTrack, online = true))
        assertEquals("Failed services should not be polled repeatedly", 2, requests)
        assertEquals(2, app.repository.library.value.tracks.size)
    }

    @Test fun lyricPanelTracksSeekSongChangesAndTheSettingsSwitch() {
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun adapter(): LyricsAdapter = onMainResult {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                activity.findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter
            }
            try {
                onMain {
                    controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && adapter().lyrics?.timed == true }
                onView(withId(R.id.lyricsList)).check(matches(isDisplayed()))
                onMain { controller.seekTo(15_000) }
                await { adapter().activeLine == 1 }
                assertEquals("Synthetic middle", adapter().lyrics!!.lines[1].text)
                screenshot("lyrics-landscape")
                onMain { controller.seekTo(1_000) }
                await { adapter().activeLine == -1 }
                onMain { controller.seekTo(6_000) }
                await { adapter().activeLine == 0 }
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                await { adapter().activeLine == 0 }
                screenshot("lyrics-portrait")
                onMain { controller.seekTo(1, 0) }
                await { adapter().lyrics?.lines?.firstOrNull()?.text == "Nested text fixture" }
                assertFalse(adapter().lyrics!!.timed)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsLyrics).perform(scrollTo(), click())
                await { !app.displaySettings.options.value.lyricsEnabled }
                pressBack()
                onView(withId(R.id.artwork)).perform(scrollTo()).check(matches(isDisplayed()))
                assertNull(adapter().lyrics)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsLyrics).perform(scrollTo(), click())
                pressBack()
                await { adapter().lyrics?.lines?.firstOrNull()?.text == "Nested text fixture" }
                assertTrue(DisplaySettings(app) {}.options.value.lyricsEnabled)
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun settingsToolbarBackSurvivesRecreationLanguageAndThemeChanges() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_library)).perform(click())
            onView(withId(R.id.action_settings)).perform(click())
            scenario.recreate()
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            onView(withId(R.id.navigation)).check(matches(isDisplayed()))
            scenario.onActivity {
                assertEquals(R.id.nav_library, ViewModelProvider(it)[MusicViewModel::class.java].selectedScreen.value)
            }
            onView(withId(R.id.action_settings)).perform(click())
            chooseSetting(R.id.settingsLanguage, R.string.language_chinese)
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            onView(withId(R.id.navigation)).check(matches(isDisplayed()))
            onView(withId(R.id.action_settings)).perform(click())
            chooseSetting(R.id.settingsTheme, R.string.theme_day)
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            onView(withId(R.id.navigation)).check(matches(isDisplayed()))
        }
    }

    @Test fun settingsCategoriesKeepSelectionAndIndependentScrollingAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            onView(withId(R.id.nav_library)).perform(click())
            onView(withId(R.id.action_settings)).perform(click())
            chooseSetting(R.id.settingsLanguage, R.string.language_english)
            onView(withId(R.id.settingsHeading)).check(matches(withText("General")))
            for (category in SettingsCategory.entries) {
                selectCategory(category)
                scenario.onActivity { activity ->
                    assertEquals(category, ViewModelProvider(activity)[MusicViewModel::class.java].settingsCategory.value)
                    assertEquals(activity.getString(category.label), activity.findViewById<TextView>(R.id.settingsHeading).text)
                    SettingsCategory.entries.forEach {
                        assertEquals(it == category, activity.findViewById<MaterialButton>(it.buttonId).isChecked)
                        assertEquals(if (it == category) View.VISIBLE else View.GONE,
                            activity.findViewById<View>(it.panelId).visibility)
                    }
                    val rail = activity.findViewById<View>(R.id.settingsCategoryScroll)
                    val details = activity.findViewById<View>(R.id.settingsDetails)
                    assertTrue("Categories must stay to the left of the detail pane", rail.right <= details.left)
                    assertTrue(details.width > rail.width)
                    assertTrue(backgroundIsTransparent(details))
                    val heading = activity.findViewById<View>(R.id.settingsHeading)
                    val scroller = activity.findViewById<ScrollView>(R.id.settingsScroll)
                    assertTrue("Configuration must stay below its fixed heading", scroller.top >= heading.bottom)
                    assertTrue("Configuration must fit above the mini-player", scroller.bottom <= details.height)
                }
            }
            selectCategory(SettingsCategory.SPECTRUM)
            onView(withId(R.id.settingsSpectrum)).perform(click())
            onView(withId(R.id.spectrumRefreshRate)).perform(scrollTo())
            val scroll = onMainResult { resumedActivity().findViewById<ScrollView>(R.id.settingsScroll).scrollY }
            assertTrue(scroll > 0)
            selectCategory(SettingsCategory.GENERAL)
            await { resumedActivity().findViewById<ScrollView>(R.id.settingsScroll).scrollY == 0 }
            selectCategory(SettingsCategory.SPECTRUM)
            await { resumedActivity().findViewById<ScrollView>(R.id.settingsScroll).scrollY == scroll }
            scenario.recreate()
            await {
                val activity = resumedActivity()
                activity.findViewById<View>(R.id.settingsSpectrumPanel).isShown &&
                    activity.findViewById<ScrollView>(R.id.settingsScroll).scrollY == scroll
            }
            screenshot("categories-spectrum-landscape")
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsSpectrumPanel).isShown)
                assertTrue(activity.findViewById<View>(R.id.settingsCategoryScroll).right <
                    activity.findViewById<View>(R.id.settingsDetails).right)
            }
            screenshot("categories-spectrum-portrait")
            scenario.onActivity { activity ->
                SettingsCategory.entries.forEach { category ->
                    val button = activity.findViewById<MaterialButton>(category.buttonId)
                    assertEquals("English category ${button.text} should fit without splitting a word", 1, button.lineCount)
                }
            }
            chooseSetting(R.id.settingsLanguage, R.string.language_chinese)
            selectCategory(SettingsCategory.APPEARANCE)
            chooseSetting(R.id.settingsTheme, R.string.theme_day)
            onView(withId(R.id.categoryAppearance)).check(matches(withText("外观")))
            onView(withId(R.id.settingsAppearancePanel)).check(matches(isDisplayed()))
            screenshot("categories-chinese-day")
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            onView(withId(R.id.navigation)).check(matches(isDisplayed()))
            scenario.onActivity {
                assertEquals(R.id.nav_library, ViewModelProvider(it)[MusicViewModel::class.java].selectedScreen.value)
            }
        }
    }

    @Test fun paletteEditorsKeepClassicAndFireIndependentAndPersistRendering() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            chooseSetting(R.id.settingsLanguage, R.string.language_english)
            setting(R.id.settingsSpectrum).perform(scrollTo(), click())
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_fire))
            chooseDropdown(R.id.spectrumFireStyle, app.text(R.string.spectrum_smooth))
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withId(R.id.colorRed)).perform(GeneralSwipeAction(Swipe.SLOW,
                { seekCoordinates(it, 0.9f) }, { seekCoordinates(it, 0.15f) }, Press.FINGER))
            screenshot("fire-color-editor")
            onView(withText(app.text(R.string.cancel))).inRoot(isDialog()).perform(click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.fireColors)
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withId(R.id.colorRed)).perform(GeneralSwipeAction(Swipe.SLOW,
                { seekCoordinates(it, 0.9f) }, { seekCoordinates(it, 0.15f) }, Press.FINGER))
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            val fire = app.displaySettings.options.value.spectrum.fireColors
            assertNotEquals(SpectrumColors.FIRE.low, fire.low)
            assertEquals(SpectrumColors.FIRE.middle, fire.middle)
            assertEquals(SpectrumColors.CLASSIC, app.displaySettings.options.value.spectrum.classicColors)
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_classic))
            setting(R.id.spectrumColorHigh).perform(scrollTo(), click())
            onView(withId(R.id.colorBlue)).perform(GeneralSwipeAction(Swipe.SLOW,
                { seekCoordinates(it, 0.1f) }, { seekCoordinates(it, 0.9f) }, Press.FINGER))
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            val classic = app.displaySettings.options.value.spectrum.classicColors
            assertNotEquals(SpectrumColors.CLASSIC.high, classic.high)
            assertEquals(fire, app.displaySettings.options.value.spectrum.fireColors)
            scenario.recreate()
            val restored = DisplaySettings(app) {}.options.value.spectrum
            assertEquals(classic, restored.classicColors)
            assertEquals(fire, restored.fireColors)
            assertEquals(FireStyle.SMOOTH, restored.fireStyle)
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_fire))
            chooseDropdown(R.id.spectrumFireStyle, app.text(R.string.spectrum_led))
            setting(R.id.spectrumResetColors).perform(scrollTo(), click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.fireColors)
            assertEquals(classic, app.displaySettings.options.value.spectrum.classicColors)
            assertEquals(FireStyle.LED, app.displaySettings.options.value.spectrum.fireStyle)
            screenshot("fire-palette-settings")
        }
    }

    @Test fun navigationMovesToEveryEdgeAndArtworkOpensPlayerWithoutChangingPlayback() {
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(tint = BackgroundTint.ORIGINAL, tintStrength = 0,
            spectrum = it.spectrum.copy(enabled = true, palette = SpectrumPalette.FIRE)) } }
        runBlocking {
            app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second")),
                app.displaySettings::installImages)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 15_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && pictureColor() == 0xff82386f.toInt() }
                val labels = listOf(R.string.navigation_bottom, R.string.navigation_left, R.string.navigation_right, R.string.navigation_top)
                for (position in NavigationPosition.entries) {
                    onView(withId(R.id.action_settings)).perform(click())
                    chooseDropdown(R.id.settingsNavigationPosition, app.text(labels[position.ordinal]))
                    onView(withContentDescription(app.text(R.string.back))).perform(click())
                    onView(withId(R.id.spectrum)).perform(scrollTo())
                    scenario.onActivity { activity ->
                        val navigation = activity.findViewById<PlayerNavigationView>(R.id.navigation)
                        val body = activity.findViewById<View>(R.id.playerBody)
                        assertEquals(position, DisplaySettings(app) {}.options.value.navigationPosition)
                        when (position) {
                            NavigationPosition.BOTTOM -> assertTrue(navigation.top >= body.bottom)
                            NavigationPosition.TOP -> assertTrue(navigation.bottom <= body.top)
                            NavigationPosition.LEFT -> assertTrue(navigation.right <= body.left)
                            NavigationPosition.RIGHT -> assertTrue(navigation.left >= body.right)
                        }
                        assertEquals(0, (navigation.artwork.parent as LinearLayout).indexOfChild(navigation.artwork))
                        assertNotNull(navigation.artwork.drawable)
                        val spectrum = activity.findViewById<View>(R.id.spectrum)
                        val spectrumBounds = android.graphics.Rect()
                        assertTrue(spectrum.getGlobalVisibleRect(spectrumBounds))
                        assertEquals(spectrum.width, spectrumBounds.width())
                        assertContinuousBackground(activity)
                        assertTrue(activity.findViewById<View>(R.id.toolbar).height <= 48 * activity.resources.displayMetrics.density)
                    }
                    screenshot("navigation-${position.name.lowercase()}-landscape")
                    if (position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT) {
                        onView(withId(R.id.nav_playlists)).perform(scrollTo(), click())
                        onView(withId(R.id.navigationArtwork)).perform(scrollTo(), click())
                        onView(withId(R.id.nav_library)).perform(scrollTo(), click())
                        onView(withId(R.id.navigationArtwork)).perform(scrollTo(), click())
                    } else {
                        onView(withId(R.id.nav_library)).perform(click())
                        onView(withId(R.id.navigationArtwork)).perform(click())
                    }
                    onView(withId(R.id.play)).perform(scrollTo()).check(matches(isDisplayed()))
                    assertEquals(tracks.first().id, onMainResult { controller.currentMediaItem!!.mediaId })
                    assertEquals(15_000, onMainResult { controller.currentPosition })
                    assertFalse(onMainResult { controller.playWhenReady })
                }
                scenario.recreate()
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                assertEquals(NavigationPosition.TOP, app.displaySettings.options.value.navigationPosition)
                onView(withId(R.id.nav_library)).perform(click())
                onView(withId(R.id.navigationArtwork)).perform(click())
                screenshot("navigation-top-portrait")
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun tappingMainTimeTogglesRemainingWithoutChangingTotalOrMiniTime() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 15_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY }
                scenario.onActivity { activity ->
                    val time = bounds(activity.findViewById(R.id.position))
                    val seek = bounds(activity.findViewById(R.id.seek))
                    val duration = bounds(activity.findViewById(R.id.duration))
                    assertTrue(time.right <= seek.left)
                    assertTrue(seek.right <= duration.left)
                    assertEquals(time.centerY(), seek.centerY())
                    assertEquals(duration.centerY(), seek.centerY())
                }
                onView(withId(R.id.position)).check(matches(withText("0:15"))).perform(scrollTo(), click())
                onView(withId(R.id.position)).check(matches(withText("-0:45")))
                onView(withId(R.id.duration)).check(matches(withText("1:00")))
                assertEquals(PlaybackTimeMode.REMAINING, DisplaySettings(app) {}.options.value.mainTimeMode)
                assertEquals(PlaybackTimeMode.ELAPSED, app.displaySettings.options.value.timeMode)
                scenario.recreate()
                onView(withId(R.id.position)).check(matches(withText("-0:45"))).perform(scrollTo(), click())
                onView(withId(R.id.position)).check(matches(withText("0:15")))
                assertEquals(15_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun responsivePlayerFillsFreedDockSpaceAndKeepsEveryScreenTopStable() {
        onMain { app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true)) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                rotate(scenario, orientation)
                val geometry = mutableMapOf<NavigationPosition, Pair<Int, Int>>()
                for (position in NavigationPosition.entries) {
                    onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                    await { resumedActivity().findViewById<View>(R.id.navigation).let {
                        if (position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT) it.width < it.height
                        else it.width > it.height
                    } }
                    instrumentation.waitForIdleSync()
                    val top = onMainResult {
                        val activity = resumedActivity()
                        val container = activity.findViewById<android.view.ViewGroup>(R.id.screenContainer)
                        val player = container.children.filterIsInstance<PlayerLayout>().single()
                        val content = player.getChildAt(0) as LinearLayout
                        val controls = activity.findViewById<View>(R.id.controls)
                        val information = activity.findViewById<View>(R.id.metadataRow)
                        val cover = activity.findViewById<View>(R.id.cover)
                        assertEquals("Only minimum-size overflow may make the player scroll",
                            maxOf(player.height, content.height), content.height)
                        assertEquals("Do not reserve empty space below the controls",
                            content.height - content.paddingBottom, controls.bottom)
                        assertEquals(information.height, activity.findViewById<View>(R.id.spectrum).height)
                        assertEquals(information.height, activity.findViewById<View>(R.id.trackInformation).height)
                        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            val gap = activity.resources.getDimensionPixelSize(R.dimen.player_gap)
                            assertTrue("Lyrics, spectrum, and information should have equal column widths",
                                kotlin.math.abs(cover.width * 2 + gap - controls.width) <= 2)
                        }
                        geometry[position] = player.height to information.height
                        val toolbar = bounds(activity.findViewById(R.id.toolbar))
                        val screen = bounds(container)
                        toolbar to screen.top
                    }
                    for (id in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists, R.id.nav_player)) {
                        onMain { resumedActivity().findViewById<View>(id).performClick() }
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { activity ->
                            assertEquals("Changing screens must not move or resize the toolbar", top.first,
                                bounds(activity.findViewById(R.id.toolbar)))
                            assertEquals("Changing screens must not shift content down", top.second,
                                bounds(activity.findViewById(R.id.screenContainer)).top)
                        }
                    }
                }
                val bottom = requireNotNull(geometry[NavigationPosition.BOTTOM])
                for (edge in listOf(NavigationPosition.LEFT, NavigationPosition.RIGHT)) {
                    val side = requireNotNull(geometry[edge])
                    val dockHeight = (80 * app.resources.displayMetrics.density).toInt() +
                        app.resources.getDimensionPixelSize(R.dimen.player_gap)
                    assertEquals("Side docking must release all of the bottom toolbar height", dockHeight, side.first - bottom.first)
                    if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE &&
                        bottom.first >= 420 * app.resources.displayMetrics.density) {
                        assertTrue("Song information/spectrum must receive the freed height",
                            kotlin.math.abs(dockHeight - (side.second - bottom.second)) <= 2)
                    }
                }
                screenshot("responsive-${orientation}-layout")
            }
        }
    }

    @Test fun portraitControlsAndCharacterBordersFitWithConsistentGuttersOnEveryDock() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain {
            app.displaySettings.update { it.copy(lyricsEnabled = true,
                spectrum = it.spectrum.copy(enabled = true), blockOutline = BlockOutline(BlockStyle.CHARACTERS, "+")) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 20_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 &&
                    lyricList().children.any { it.isSelected } }
                for (position in NavigationPosition.entries) {
                    onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val container = activity.findViewById<ViewGroup>(R.id.screenContainer)
                        val player = container.children.filterIsInstance<PlayerLayout>().single()
                        val viewport = bounds(container)
                        val gap = activity.resources.getDimensionPixelSize(R.dimen.player_gap)
                        assertEquals("Portrait must fit without scrolling on the supported test display",
                            player.height, player.getChildAt(0).height)
                        assertEquals(0, player.scrollY)
                        val minimumTouch = (48 * activity.resources.displayMetrics.density).toInt()
                        val buttons = listOf(R.id.shuffle, R.id.previous, R.id.play, R.id.next, R.id.repeat)
                            .map { activity.findViewById<View>(it) }
                        for (button in buttons + listOf(activity.findViewById(R.id.favorite), activity.findViewById(R.id.details))) {
                            val geometry = listOf(R.id.cover, R.id.controls, R.id.metadataRow, R.id.seekBlock,
                                R.id.transportRow, R.id.footerRow, R.id.actionRow, R.id.volumeBlock).joinToString { id ->
                                val view = activity.findViewById<View>(id)
                                "${activity.resources.getResourceEntryName(id)}=${bounds(view)}/${view.measuredHeight}"
                            }
                            assertTrue("Controls must remain on screen: ${button.id}, ${bounds(button)}, $viewport; $geometry",
                                viewport.contains(bounds(button)))
                            assertTrue("Keep a 48dp touch target", button.width >= minimumTouch && button.height >= minimumTouch)
                        }
                        buttons.zipWithNext().forEach { (left, right) ->
                            assertEquals(gap, bounds(right).left - bounds(left).right)
                        }
                        for ((above, below) in listOf(R.id.cover to R.id.controls, R.id.metadataRow to R.id.seekBlock,
                            R.id.seekBlock to R.id.transportRow, R.id.transportRow to R.id.footerRow)) {
                            assertEquals("Every panel uses the same gap", gap,
                                bounds(activity.findViewById(below)).top - bounds(activity.findViewById(above)).bottom)
                        }
                        val cover = activity.findViewById<View>(R.id.cover)
                        assertTrue("Characters must not overlap lyric content", cover.paddingLeft >= (12 * activity.resources.displayMetrics.density).toInt())
                        assertTrue(viewport.contains(bounds(activity.findViewById(R.id.volumeBlock))))
                        val dock = bounds(activity.findViewById(R.id.navigation))
                        if (position == NavigationPosition.LEFT) assertEquals(gap, bounds(cover).left - dock.right)
                        if (position == NavigationPosition.RIGHT) assertEquals(gap, dock.left - bounds(cover).right)
                    }
                    screenshot("release-portrait-${position.name.lowercase()}")
                }
                assertFalse(onMainResult { controller.playWhenReady })
                assertEquals(20_000, onMainResult { controller.currentPosition })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun allBorderStylesStayTransparentAndCharacterEditorPersists() {
        val labels = listOf(R.string.block_solid, R.string.block_dashed, R.string.block_arc, R.string.block_characters)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            tapAfterWindowTransition(R.id.action_settings)
            await { resumedActivity().findViewById<View>(R.id.settingsLanguage).isShown }
            chooseSetting(R.id.settingsLanguage, R.string.language_english)
            for (style in BlockStyle.entries) {
                chooseDropdown(R.id.settingsBlockStyle, app.text(labels[style.ordinal]))
                assertEquals(style, DisplaySettings(app) {}.options.value.blockOutline.style)
                scenario.onActivity { activity ->
                    assertNull("Transport buttons must not have a second touching outline", activity.findViewById<View>(R.id.transportRow).foreground)
                    for (id in listOf(R.id.miniPlayer, R.id.cover, R.id.favorite, R.id.details,
                        R.id.navigationArtwork, R.id.nav_player, R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                        assertOutlinedWithoutFill(requireNotNull(activity.findViewById<View>(id).foreground))
                    }
                    for (id in listOf(R.id.trackInformation, R.id.spectrum, R.id.seekBlock, R.id.play, R.id.miniPlay)) {
                        assertOutlinedWithoutFill(activity.findViewById<View>(id))
                    }
                }
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                screenshot("border-${style.name.lowercase()}")
                onView(withId(R.id.action_settings)).perform(click())
            }
            setting(R.id.settingsBlockCharacter).perform(scrollTo(), click())
            onView(withId(R.id.blockCharacterInput)).perform(replaceText("+"), closeSoftKeyboard())
            onView(withText(app.text(R.string.cancel))).inRoot(isDialog()).perform(click())
            assertEquals("*", app.displaySettings.options.value.blockOutline.character)
            setting(R.id.settingsBlockCharacter).perform(scrollTo(), click())
            onView(withId(R.id.blockCharacterInput)).perform(replaceText(" "), closeSoftKeyboard())
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            onView(withText(app.text(R.string.block_character_invalid))).inRoot(isDialog()).check(matches(isDisplayed()))
            assertEquals("*", app.displaySettings.options.value.blockOutline.character)
            onView(withId(R.id.blockCharacterInput)).perform(replaceText("+"), closeSoftKeyboard())
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            scenario.recreate()
            assertEquals(BlockOutline(BlockStyle.CHARACTERS, "+"), DisplaySettings(app) {}.options.value.blockOutline)
            screenshot("border-character-settings")
        }
        onMain {
            val color = Color.rgb(38, 146, 204)
            val bitmaps = BlockStyle.entries.map { style ->
                Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888).also { bitmap ->
                    PlayerBlockDrawable(BlockOutline(style, "+"), color, 1f).apply {
                        setBounds(0, 0, bitmap.width, bitmap.height)
                        draw(Canvas(bitmap))
                    }
                }
            }
            try {
                bitmaps.forEachIndexed { index, bitmap ->
                    var painted = 0
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        val pixel = bitmap.getPixel(x, y)
                        if (Color.alpha(pixel) == 255) {
                            assertEquals("Outlines must use the supplied theme color", color and 0xffffff, pixel and 0xffffff)
                        }
                        // Small glyphs need not contain fully opaque pixels on every Android font rasterizer.
                        if (Color.alpha(pixel) >= 128) painted++
                        if (x in 20..159 && y in 20..99) assertEquals(0, Color.alpha(pixel))
                    }
                    assertTrue("${BlockStyle.entries[index]} must paint a visible outline: $painted pixels", painted > 50)
                }
                for (a in bitmaps.indices) for (b in a + 1 until bitmaps.size) {
                    assertFalse("Each outline option must actually draw differently", bitmaps[a].sameAs(bitmaps[b]))
                }
            } finally {
                bitmaps.forEach(Bitmap::recycle)
            }
        }
    }

    @Test fun activeLyricSizeIsConfigurableAndOnlyThePlayingLineKeepsExtraSpace() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun activeCentered(): Boolean {
                val list = lyricList()
                val active = list.children.singleOrNull { it.isSelected } ?: return false
                return list.scrollState == RecyclerView.SCROLL_STATE_IDLE && !list.hasPendingAdapterUpdates() &&
                    !list.isLayoutRequested && list.getChildAdapterPosition(active) == (list.adapter as LyricsAdapter).activeLine &&
                    kotlin.math.abs(active.top + active.height / 2 - list.height / 2) <= 2
            }
            fun assertSize(percent: Int) = onMainResult {
                val list = lyricList()
                val geometry = "enlargement=$percent, list=${bounds(list)}, active=${(list.adapter as LyricsAdapter).activeLine}, " +
                    "rows=${list.children.joinToString { "${list.getChildAdapterPosition(it)}:${bounds(it)}/${it.isSelected}" }}"
                assertTrue("The active line must be visible: $geometry", list.children.any { it.isSelected })
                val active = list.children.filterIsInstance<TextView>().single { it.isSelected }
                // A short viewport may fit only the active row; bind an inactive row at the same width.
                val adapter = list.adapter as LyricsAdapter
                val other = adapter.createViewHolder(list, 0).also {
                    adapter.bindViewHolder(it, 0)
                    it.text.measure(View.MeasureSpec.makeMeasureSpec(list.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                }.text
                assertEquals(1f + percent / 100f, active.textSize * active.scaleX / (other.textSize * other.scaleX), 0.001f)
                val density = list.resources.displayMetrics.density
                assertEquals((10 * density).toInt(), active.paddingTop)
                assertEquals((2 * density).toInt(), other.paddingTop)
                assertTrue("Inactive lyrics must not reserve active-line height", active.height > other.measuredHeight)
                other.measuredHeight
            }
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 20_000)
                    controller.prepare()
                    controller.pause()
                }
                try {
                    await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 &&
                        activeCentered() }
                } catch (error: AssertionError) {
                    screenshot("lyric-highlight-failure")
                    val state = onMainResult {
                        val list = lyricList()
                        "player=${controller.playbackState}/${controller.currentPosition}, active=${(list.adapter as LyricsAdapter).activeLine}, " +
                            "list=${bounds(list)}, padding=${list.paddingTop}/${list.paddingBottom}, scroll=${list.scrollState}, " +
                            "rows=${list.children.joinToString { "${list.getChildAdapterPosition(it)}:${bounds(it)}/${it.isSelected}" }}"
                    }
                    throw AssertionError(state, error)
                }
                val oldHeight = assertSize(20)
                onMain { controller.seekTo(22_000) }
                await { (lyricList().adapter as LyricsAdapter).activeLine == 11 && activeCentered() }
                assertEquals(oldHeight, assertSize(20))
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.lyricEnlargement).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.4f) }, { seekCoordinates(it, 1f) }, Press.FINGER))
                assertEquals(60, DisplaySettings(app) {}.options.value.lyricEnlargementPercent)
                scenario.recreate()
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { activeCentered() }
                assertSize(60)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.lyricEnlargement).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.9f) }, { seekCoordinates(it, 0f) }, Press.FINGER))
                assertEquals(0, DisplaySettings(app) {}.options.value.lyricEnlargementPercent)
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { activeCentered() }
                assertSize(0)
                assertEquals(22_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun lyricDragGuideShowsTheActualCenterAndClearsOnEveryCancellation() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, lyricsDragSeek = true) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 20_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 &&
                    lyricList().scrollState == RecyclerView.SCROLL_STATE_IDLE }
                for (cancel in listOf(MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
                    val down = SystemClock.uptimeMillis()
                    onMain {
                        val list = lyricList()
                        for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE).withIndex()) {
                            val event = MotionEvent.obtain(down, down + index * 50L, action, list.width / 2f,
                                list.height * (0.85f - index * 0.15f), 0)
                            try { list.dispatchTouchEvent(event) } finally { event.recycle() }
                        }
                        val guide = resumedActivity().findViewById<View>(R.id.lyricDragGuide)
                        assertEquals(View.VISIBLE, guide.visibility)
                    }
                    await { resumedActivity().findViewById<View>(R.id.lyricDragGuide).height > 0 }
                    onMain {
                        val list = lyricList()
                        val guide = resumedActivity().findViewById<View>(R.id.lyricDragGuide)
                        assertTrue(kotlin.math.abs(bounds(list).centerY() - bounds(guide).centerY()) <= 1)
                        assertEquals(list.width, guide.width)
                        val label = resumedActivity().findViewById<TextView>(R.id.lyricSeekPreview)
                        val line = resumedActivity().findViewById<View>(R.id.lyricDragLine)
                        assertEquals(TimeText.format(requireNotNull(lyricCenterTime(list))), label.text.toString())
                        assertTrue("Drag time belongs beside the guide, not at the bottom",
                            kotlin.math.abs(bounds(label).centerY() - bounds(line).centerY()) <= 1)
                        assertTrue(bounds(label).right <= bounds(line).left)
                    }
                    screenshot("lyric-center-guide")
                    onMain {
                        val list = lyricList()
                        val event = if (cancel == MotionEvent.ACTION_POINTER_DOWN) {
                            val properties = Array(2) { id -> MotionEvent.PointerProperties().apply {
                                this.id = id
                                toolType = MotionEvent.TOOL_TYPE_FINGER
                            } }
                            val coordinates = Array(2) { id -> MotionEvent.PointerCoords().apply {
                                x = list.width / 2f + id * 12
                                y = list.height / 2f
                                pressure = 1f
                                size = 1f
                            } }
                            MotionEvent.obtain(down, down + 200L, cancel or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                                2, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                        } else MotionEvent.obtain(down, down + 200L, cancel, list.width / 2f, list.height / 2f, 0)
                        try { list.dispatchTouchEvent(event) } finally { event.recycle() }
                        assertEquals(View.GONE, resumedActivity().findViewById<View>(R.id.lyricDragGuide).visibility)
                        val end = MotionEvent.obtain(down, down + 250L, MotionEvent.ACTION_CANCEL, list.width / 2f, list.height / 2f, 0)
                        try { list.dispatchTouchEvent(end) } finally { end.recycle() }
                        list.stopScroll()
                    }
                    instrumentation.waitForIdleSync()
                    assertEquals(20_000, onMainResult { controller.currentPosition })
                    assertFalse(onMainResult { controller.playWhenReady })
                }
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun aboutPhotoOpensOriginalResolutionAndZoomsWithoutChangingPlayback() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun photo() = ((resumedActivity().supportFragmentManager.findFragmentByTag("about-photo")
                as PhotoViewerDialog).requireDialog().findViewById<ZoomImageView>(R.id.fullResolutionPhoto))
            fun openAbout() {
                onMain {
                    assertTrue((resumedActivity().findViewById<View>(R.id.toolbar) as com.google.android.material.appbar.MaterialToolbar)
                        .menu.performIdentifierAction(R.id.action_about, 0))
                }
                await {
                    (resumedActivity().supportFragmentManager.findFragmentByTag("about") as? AboutDialog)
                        ?.dialog?.window?.decorView?.hasWindowFocus() == true
                }
            }
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 15_000)
                    controller.prepare()
                    controller.pause()
                }
                openAbout()
                onView(withId(R.id.aboutPhoto)).check(matches(isDisplayed()))
                onMain {
                    val dialog = (resumedActivity().supportFragmentManager.findFragmentByTag("about") as AboutDialog).requireDialog()
                    val thumbnail = dialog.findViewById<ImageView>(R.id.aboutPhoto)
                    val heading = dialog.findViewById<View>(R.id.aboutHeading)
                    assertTrue(bounds(thumbnail).left >= bounds(heading).right)
                    assertTrue(bounds(thumbnail).centerY() in bounds(heading).top..bounds(heading).bottom)
                }
                screenshot("about-photo-thumbnail")
                onView(withId(R.id.aboutPhoto)).perform(click())
                onView(withId(R.id.fullResolutionPhoto)).check(matches(isDisplayed()))
                onMain {
                    val view = photo()
                    val bitmap = (view.drawable as BitmapDrawable).bitmap
                    assertEquals(878, bitmap.width)
                    assertEquals(932, bitmap.height)
                    assertEquals(1f, view.zoom, 0.001f)
                    val window = requireNotNull((resumedActivity().supportFragmentManager.findFragmentByTag("about-photo")
                        as PhotoViewerDialog).requireDialog().window)
                    val light = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
                    assertEquals(light, androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
                }
                onView(withId(R.id.photoZoomIn)).perform(click())
                assertEquals(1.5f, onMainResult { photo().zoom }, 0.001f)
                onView(withId(R.id.photoZoomOut)).perform(click())
                assertEquals(1f, onMainResult { photo().zoom }, 0.001f)
                val gestureTrace = mutableListOf<String>()
                onMain {
                    val view = photo()
                    view.setOnTouchListener { _, event ->
                        gestureTrace.add("${event.actionMasked}@${event.eventTime},${event.x}/${event.y},zoom=${view.zoom}")
                        false
                    }
                    view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        gestureTrace.add("layout=$left/$top/$right/$bottom,zoom=${view.zoom}")
                    }
                }
                onView(withId(R.id.fullResolutionPhoto)).perform(doubleClick())
                assertEquals(onMainResult { gestureTrace.joinToString() }, 2f, onMainResult { photo().zoom }, 0.001f)
                val old = onMainResult { android.graphics.Matrix(photo().imageMatrix) }
                onView(withId(R.id.fullResolutionPhoto)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { view -> seekCoordinates(view, 0.5f).apply { this[1] += view.height * 0.25f } },
                    { view -> seekCoordinates(view, 0.5f).apply { this[1] -= view.height * 0.25f } }, Press.FINGER))
                assertNotEquals(old, onMainResult { android.graphics.Matrix(photo().imageMatrix) })
                onView(withId(R.id.photoReset)).perform(click())
                assertEquals(1f, onMainResult { photo().zoom }, 0.001f)
                screenshot("about-original-photo-viewer")
                pressBack()
                onView(withId(R.id.aboutPhoto)).check(matches(isDisplayed()))
                onView(withText(app.text(R.string.close))).inRoot(isDialog()).perform(click())
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                openAbout()
                onView(withId(R.id.aboutPhoto)).perform(click())
                onView(withId(R.id.fullResolutionPhoto)).check(matches(isDisplayed()))
                onView(withId(R.id.photoZoomIn)).perform(click())
                onView(withId(R.id.photoReset)).perform(click())
                assertEquals(1f, onMainResult { photo().zoom }, 0.001f)
                screenshot("about-original-photo-portrait")
                onView(withContentDescription(app.text(R.string.close))).perform(click())
                onView(withId(R.id.aboutPhoto)).check(matches(isDisplayed()))
                onView(withText(app.text(R.string.close))).inRoot(isDialog()).perform(click())
                assertEquals(tracks.first().id, onMainResult { controller.currentMediaItem!!.mediaId })
                assertEquals(15_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    private fun bounds(view: View): Rect {
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        return Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
    }

    @Test fun timedLyricsAnimateAndDragOnlySeeksWhenExplicitlyEnabled() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 10_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 }
                await { (lyricList().adapter as LyricsAdapter).activeLine == 5 && lyricList().scrollState == RecyclerView.SCROLL_STATE_IDLE }
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.lyricsList)
                    val bounds = android.graphics.Rect()
                    assertTrue(list.getGlobalVisibleRect(bounds))
                    assertEquals("The lyric center must remain on screen on compact displays", list.height, bounds.height())
                }
                var animationFrames = 0
                scenario.onActivity {
                    it.findViewById<RecyclerView>(R.id.lyricsList).addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING) animationFrames++
                        }
                    })
                }
                onMain { controller.seekTo(14_000) }
                await { lyricList().scrollState == RecyclerView.SCROLL_STATE_SETTLING }
                await { (lyricList().adapter as LyricsAdapter).activeLine == 7 && lyricList().scrollState == RecyclerView.SCROLL_STATE_IDLE }
                assertTrue("Automatic following must animate over multiple frames", onMainResult { animationFrames } > 2)
                onView(withId(R.id.lyricsList)).perform(lyricSwipe())
                assertEquals(14_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsLyricDragSeek).perform(scrollTo(), click())
                setting(R.id.settingsLyricSmoothScroll).perform(scrollTo(), click())
                scenario.onActivity {
                    val field = it.findViewById<TextView>(R.id.lyricsProviderOrder)
                    val layout = requireNotNull(field.layout)
                    for (line in listOf(0, layout.lineCount - 1)) {
                        val row = android.graphics.Rect(0, field.totalPaddingTop + layout.getLineTop(line),
                            field.width, field.totalPaddingTop + layout.getLineBottom(line))
                        field.requestRectangleOnScreen(android.graphics.Rect(row), true)
                        val visible = android.graphics.Rect()
                        assertTrue(field.getLocalVisibleRect(visible))
                        assertTrue("Provider line $line must be reachable: row=$row, visible=$visible, height=${field.height}",
                            visible.top <= row.top && visible.bottom >= row.bottom)
                    }
                    val order = field.text.toString()
                    assertTrue(order.indexOf(it.getString(R.string.provider_kugou)) < order.indexOf(it.getString(R.string.provider_netease)))
                    assertTrue(order.contains(it.getString(R.string.lyrics_online_disabled)))
                }
                screenshot("lyrics-interaction-settings")
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                onMain { animationFrames = 0; controller.seekTo(18_000) }
                await { (lyricList().adapter as LyricsAdapter).activeLine == 9 && lyricList().scrollState == RecyclerView.SCROLL_STATE_IDLE }
                assertEquals("Disabling smooth scrolling must stop animated following", 0, onMainResult { animationFrames })
                assertFalse(DisplaySettings(app) {}.options.value.lyricsSmoothScroll)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsLyricSmoothScroll).perform(scrollTo(), click())
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                var releaseTime: Long? = null
                scenario.onActivity {
                    it.findViewById<RecyclerView>(R.id.lyricsList).addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                        override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                            if (event.actionMasked == MotionEvent.ACTION_UP) releaseTime = lyricCenterTime(recyclerView)
                            return false
                        }
                    })
                }
                onView(withId(R.id.lyricsList)).perform(lyricSwipe())
                await { controller.isPlaying }
                assertEquals(View.GONE, onMainResult { resumedActivity().findViewById<View>(R.id.lyricDragGuide).visibility })
                val target = requireNotNull(onMainResult { releaseTime })
                assertTrue("The released line must set playback time", onMainResult { controller.currentPosition } in target..target + 1_500)
                assertEquals(tracks.first().id, onMainResult { controller.currentMediaItem!!.mediaId })
                assertTrue(DisplaySettings(app) {}.options.value.lyricsDragSeek)
                screenshot("smooth-draggable-lyrics")
                onMain { controller.pause(); controller.seekTo(1, 5_000) }
                await { (lyricList().adapter as LyricsAdapter).lyrics?.timed == false }
                onView(withId(R.id.lyricsList)).perform(lyricSwipe())
                assertEquals(5_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun trackChangeAndCancellationDuringLyricDragCannotSeekOrStartNewTrack() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, lyricsDragSeek = true) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 10_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 }
                var switched = false
                scenario.onActivity {
                    it.findViewById<RecyclerView>(R.id.lyricsList).setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_MOVE && !switched) {
                            switched = true
                            controller.seekTo(1, 5_000)
                        }
                        false
                    }
                }
                onView(withId(R.id.lyricsList)).perform(lyricSwipe())
                await { controller.currentMediaItemIndex == 1 && controller.playbackState == Player.STATE_READY }
                assertEquals(View.GONE, onMainResult { resumedActivity().findViewById<View>(R.id.lyricDragGuide).visibility })
                assertTrue(switched)
                assertEquals(5_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
                onMain { lyricList().setOnTouchListener(null); controller.seekTo(0, 10_000) }
                await { lyricList().adapter?.itemCount == 30 }
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.lyricsList)
                    val down = SystemClock.uptimeMillis()
                    for ((index, action) in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL).withIndex()) {
                        val event = MotionEvent.obtain(down, down + index * 50L, action, list.width / 2f,
                            list.height * (0.8f - index * 0.15f), 0)
                        try { list.dispatchTouchEvent(event) } finally { event.recycle() }
                    }
                }
                instrumentation.waitForIdleSync()
                assertEquals(10_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    private fun lyricList() = resumedActivity().findViewById<RecyclerView>(R.id.lyricsList)

    private fun lyricCenterTime(list: RecyclerView): Long? {
        val row = list.children.minByOrNull { kotlin.math.abs(it.top + it.height / 2f - list.height / 2f) } ?: return null
        return (list.adapter as LyricsAdapter).lyrics?.lines?.getOrNull(list.getChildAdapterPosition(row))?.timeMs
    }

    private fun lyricSwipe() = GeneralSwipeAction(Swipe.SLOW, { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        floatArrayOf(location[0] + view.width / 2f, location[1] + view.height * 0.8f)
    }, { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        floatArrayOf(location[0] + view.width / 2f, location[1] + view.height * 0.25f)
    }, Press.FINGER)

    @Test fun transparentControlBlocksPreserveOriginalBackgroundAndToggleWithoutRestartingPlayback() {
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(tint = BackgroundTint.ORIGINAL, tintStrength = 0) } }
        runBlocking {
            app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second")),
                app.displaySettings::installImages)
        }
        val original = app.displaySettings.options.value
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                    controller.play()
                    app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true, palette = SpectrumPalette.FIRE)) }
                }
                await { controller.isPlaying && pictureColor() == 0xff82386f.toInt() && app.spectrum.frames.value.hasAudio }
                val blocks = listOf(R.id.trackInformation, R.id.spectrum, R.id.seekBlock, R.id.volumeBlock,
                    R.id.shuffle, R.id.previous, R.id.play, R.id.next, R.id.repeat)
                scenario.onActivity { activity ->
                    assertContinuousBackground(activity)
                    blocks.forEach { id ->
                        val view = activity.findViewById<View>(id)
                        assertOutlinedWithoutFill(view)
                        assertEquals(0f, view.elevation, 0.001f)
                    }
                    assertEquals(0, Color.alpha((activity.findViewById<View>(R.id.backgroundTint).background as ColorDrawable).color))
                    if (activity.resources.configuration.screenWidthDp >= 1_000 &&
                        activity.resources.configuration.screenHeightDp >= 550
                    ) {
                        val volume = activity.findViewById<View>(R.id.volumeBlock)
                        val bounds = android.graphics.Rect()
                        assertTrue(volume.getGlobalVisibleRect(bounds))
                        assertEquals("The volume block should not be clipped on a 1024 x 600 head unit",
                            volume.height, bounds.height())
                    }
                }
                screenshot("transparent-blocks-fire-landscape")
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await { !controller.isPlaying }
                onView(withId(R.id.play)).perform(click())
                await { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsControlBlocks).perform(scrollTo(), click())
                assertFalse(app.displaySettings.options.value.controlBlocks)
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                scenario.onActivity { activity ->
                    for (id in blocks) assertTrue(backgroundIsTransparent(activity.findViewById(id)))
                }
                assertEquals(original.tint, app.displaySettings.options.value.tint)
                assertEquals(original.tintStrength, app.displaySettings.options.value.tintStrength)
                assertEquals(original.imageIds, app.displaySettings.options.value.imageIds)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsControlBlocks).perform(scrollTo(), click())
                assertTrue(DisplaySettings(app) {}.options.value.controlBlocks)
                scenario.recreate()
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                onView(withId(R.id.play)).perform(scrollTo())
                scenario.onActivity { activity -> blocks.forEach { assertOutlinedWithoutFill(activity.findViewById(it)) } }
                assertTrue(onMainResult { controller.isPlaying })
                screenshot("transparent-blocks-fire-portrait")
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun fireSpectrumDrawsSeparatedWarmSegmentsWithoutAnOpaquePanel() {
        onMain {
            val context = androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_ManaPlayer)
            val view = SpectrumView(context)
            val density = app.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val height = (240 * density).toInt()
            view.layout(0, 0, width, height)
            val options = SpectrumOptions(enabled = true, palette = SpectrumPalette.FIRE, bands = 16, peaks = false)
            view.configure(options)
            view.setPlaybackActive(true)
            val levels = FloatArray(32).apply { this[0] = 1f; this[1] = 1f }
            fun render(expectedBatches: Int = 1): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                view.submit(SpectrumFrame(levels, true, 44_100))
                var batches = 0
                val canvas = object : Canvas(it) {
                    override fun drawVertices(mode: VertexMode, vertexCount: Int, verts: FloatArray, vertOffset: Int,
                        texs: FloatArray?, texOffset: Int, colors: IntArray?, colorOffset: Int,
                        indices: ShortArray?, indexOffset: Int, indexCount: Int, paint: android.graphics.Paint,
                    ) {
                        batches++
                        super.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors,
                            colorOffset, indices, indexOffset, indexCount, paint)
                    }
                }
                view.draw(canvas)
                assertEquals("All Fire bars must share one draw batch", expectedBatches, batches)
            }
            val left = 4 * density
            val top = 12 * app.resources.displayMetrics.scaledDensity + 8 * density
            val bottom = height - top
            val step = (bottom - top) / 20
            val x = (left + (width - 2 * left) / 16 * 0.35f).toInt()
            val picture = render()
            try {
                val rowColors = (0 until 20).map { row ->
                    picture.getPixel(x, (bottom - row * step - step * 0.35f).toInt())
                }
                assertTrue(rowColors.all { Color.alpha(it) == 255 })
                assertEquals(20, rowColors.toSet().size)
                rowColors.forEachIndexed { row, color ->
                    val y = (bottom - row * step - step * 0.35f).toInt()
                    val litTop = (top + maxOf(1f, step * 0.25f).coerceAtMost(step * 0.5f)).roundToInt().toFloat()
                    val expected = SpectrumColors.FIRE.colorAt((bottom - y - 0.5f) / (bottom - litTop))
                    for (shift in listOf(0, 8, 16)) {
                        assertTrue("LED segments must contain a smooth per-bar gradient",
                            kotlin.math.abs(((color shr shift) and 0xff) - ((expected shr shift) and 0xff)) <= 2)
                    }
                }
                for (row in 1 until 20) {
                    val gap = (bottom - row * step + step * 0.1f).toInt()
                    assertEquals("Fire cells must have transparent gaps", 0, Color.alpha(picture.getPixel(x, gap)))
                }
                assertEquals("Unlit space must not cover the background", 0,
                    Color.alpha(picture.getPixel(width / 2, (top + step).toInt())))
                val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
                File(directory, "fire-segment-rendering.png").outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                picture.recycle()
            }
            view.configure(options.copy(fireStyle = FireStyle.SMOOTH))
            val smooth = render()
            try {
                for (row in 1 until 20) {
                    assertEquals("Smooth Fire bars must not have LED gaps", 255,
                        Color.alpha(smooth.getPixel(x, (bottom - row * step + step * 0.1f).toInt())))
                }
            } finally {
                smooth.recycle()
            }
            val customized = SpectrumColors(0x1122ff, 0xaa22dd, 0x44ddcc)
            for (palette in listOf(SpectrumPalette.CLASSIC, SpectrumPalette.FIRE)) {
                view.configure(options.copy(palette = palette, fireStyle = FireStyle.SMOOTH,
                    classicColors = customized, fireColors = customized))
                val colored = render(if (palette == SpectrumPalette.FIRE) 1 else 0)
                try {
                    val pixels = (1 until 19).map { row ->
                        val y = (bottom - row * step).toInt()
                        val pixel = colored.getPixel(x, y)
                        val expected = customized.colorAt((bottom - y - 0.5f) / (bottom - top))
                        for (shift in listOf(0, 8, 16)) {
                            assertTrue("Continuous bars must interpolate the selected palette",
                                kotlin.math.abs(((pixel shr shift) and 0xff) - ((expected shr shift) and 0xff)) <= 2)
                        }
                        pixel
                    }
                    assertEquals(pixels.size, pixels.toSet().size)
                    assertEquals(0, Color.alpha(colored.getPixel(width / 2, (top + step).toInt())))
                } finally {
                    colored.recycle()
                }
            }
            view.setPlaybackActive(false)
        }
    }

    private fun assertOutlinedWithoutFill(view: View) {
        assertOutlinedWithoutFill(requireNotNull(view.background))
    }

    @Test fun fireUsesAllThreeColorsEvenForOnePercentBarsWhileClassicKeepsOneHeightScale() {
        onMain {
            val view = SpectrumView(androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_ManaPlayer))
            val density = app.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val height = (1040 * density).toInt()
            view.layout(0, 0, width, height)
            val top = 12 * app.resources.displayMetrics.scaledDensity + 8 * density
            val bottom = height - top
            val segment = (bottom - top) / 20
            val segmentGap = maxOf(1f, segment * 0.25f).coerceAtMost(segment * 0.5f)
            val colors = SpectrumColors(0x00ff00, 0xffff00, 0xff0000)
            val levels = FloatArray(32).apply {
                this[0] = 1f
                this[2] = 0.5f
                this[4] = 0.01f
            }
            for (palette in listOf(SpectrumPalette.FIRE, SpectrumPalette.CLASSIC)) {
                for (style in FireStyle.entries) {
                    view.configure(SpectrumOptions(enabled = true, palette = palette, fireStyle = style,
                        bands = 16, peaks = false, classicColors = colors, fireColors = colors))
                    view.setPlaybackActive(false)
                    view.setPlaybackActive(true)
                    view.submit(SpectrumFrame(levels, true, 44_100))
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bitmap))
                        for ((band, level) in listOf(1f, 0.5f, 0.01f).withIndex()) {
                            val x = (4 * density + (width - 8 * density) / 16 * (band + 0.35f)).toInt()
                            val led = palette == SpectrumPalette.FIRE && style == FireStyle.LED
                            val litTop = if (led) (bottom - kotlin.math.ceil(level * 20) * segment + segmentGap).roundToInt().toFloat()
                                else bottom - level * (bottom - top)
                            val gradientTop = if (palette == SpectrumPalette.FIRE) litTop else top
                            val visible = (kotlin.math.ceil(litTop).toInt() until bottom.toInt())
                                .filter { Color.alpha(bitmap.getPixel(x, it)) == 255 }
                            assertTrue("Even 1% must produce visible pixels", visible.size >= 3)
                            for (y in visible) {
                                val pixel = bitmap.getPixel(x, y)
                                val expected = colors.colorAt((bottom - y - 0.5f) / (bottom - gradientTop))
                                for (shift in listOf(0, 8, 16)) {
                                    assertTrue("$palette/$style/$level must use the correct gradient height at $y",
                                        kotlin.math.abs(((pixel shr shift) and 0xff) - ((expected shr shift) and 0xff)) <= 2)
                                }
                            }
                            if (palette == SpectrumPalette.FIRE) {
                                assertTrue("Every Fire bar needs the high color", Color.red(bitmap.getPixel(x, visible.first())) > 220)
                                assertTrue("Every Fire bar needs the low color", Color.green(bitmap.getPixel(x, visible.last())) > 220)
                                val middle = visible.minBy { kotlin.math.abs(it - (bottom + litTop) / 2) }
                                assertTrue("Every Fire bar needs the middle color", Color.red(bitmap.getPixel(x, middle)) > 200 &&
                                    Color.green(bitmap.getPixel(x, middle)) > 200)
                            } else if (level < 0.1f) {
                                assertTrue("Short Classic bars stay at the low end", Color.red(bitmap.getPixel(x, visible.first())) < 10)
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            view.setPlaybackActive(false)
        }
    }

    @Test fun fireGradientsAlsoRenderThroughTheHardwareWindowPipeline() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: android.app.Dialog
            lateinit var view: SpectrumView
            val palette = SpectrumColors(0x00ff00, 0xffff00, 0xff0000)
            val levels = FloatArray(32).apply { this[0] = 0.05f; this[2] = 1f }
            val frames = java.util.concurrent.atomic.AtomicInteger()
            scenario.onActivity { activity ->
                view = SpectrumView(activity)
                view.viewTreeObserver.addOnDrawListener {
                    frames.incrementAndGet()
                    view.submit(SpectrumFrame(levels, true, 44_100))
                }
                dialog = android.app.Dialog(activity).apply {
                    requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                    setContentView(view)
                    window!!.setBackgroundDrawable(ColorDrawable(Color.BLACK))
                    show()
                    window!!.setLayout(360, 320)
                }
            }
            try {
                for (style in FireStyle.entries) {
                    val drawn = java.util.concurrent.CountDownLatch(1)
                    onMain {
                        view.configure(SpectrumOptions(enabled = true, palette = SpectrumPalette.FIRE,
                            fireStyle = style, bands = 16, peaks = false, fireColors = palette))
                        view.setPlaybackActive(false)
                        view.setPlaybackActive(true)
                        view.postOnAnimation { view.postOnAnimation { drawn.countDown() } }
                    }
                    assertTrue(drawn.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    val picture = onMainResult {
                        assertTrue("Exercise the GPU-backed window, not only a software bitmap", view.isHardwareAccelerated)
                        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    }
                    try {
                        val copied = java.util.concurrent.CountDownLatch(1)
                        val result = java.util.concurrent.atomic.AtomicInteger(-1)
                        onMain {
                            val location = IntArray(2)
                            view.getLocationInWindow(location)
                            android.view.PixelCopy.request(dialog.window!!,
                                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                                picture, { code -> result.set(code); copied.countDown() },
                                android.os.Handler(android.os.Looper.getMainLooper()))
                        }
                        assertTrue(copied.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        assertEquals(android.view.PixelCopy.SUCCESS, result.get())
                        val density = app.resources.displayMetrics.density
                        val top = 12 * app.resources.displayMetrics.scaledDensity + 8 * density
                        val bottom = picture.height - top
                        val step = (bottom - top) / 20
                        val gap = maxOf(1f, step * 0.25f).coerceAtMost(step * 0.5f)
                        for ((band, level) in listOf(0.05f, 1f).withIndex()) {
                            val x = (4 * density + (picture.width - 8 * density) / 16 * (band + 0.35f)).toInt()
                            val litTop = if (style == FireStyle.LED)
                                (bottom - kotlin.math.ceil(level * 20) * step + gap).roundToInt().toFloat()
                                else bottom - level * (bottom - top)
                            val visible = (kotlin.math.ceil(litTop).toInt() until bottom.toInt())
                                .filter { picture.getPixel(x, it) != Color.BLACK }
                            assertTrue("Both short and tall bars must be visible", visible.size >= 3)
                            for (y in visible) {
                                val actual = picture.getPixel(x, y)
                                val expected = palette.colorAt((bottom - y - 0.5f) / (bottom - litTop))
                                for (shift in listOf(0, 8, 16)) {
                                    assertTrue("Hardware $style/$level gradient mismatch at $y",
                                        kotlin.math.abs(((actual shr shift) and 0xff) - ((expected shr shift) and 0xff)) <= 3)
                                }
                            }
                        }
                        assertEquals("Unlit chart space must preserve the background", Color.BLACK,
                            picture.getPixel(picture.width / 2, (top + step).toInt()))
                    } finally {
                        picture.recycle()
                    }
                }
                for (rate in listOf(15, 30)) {
                    onMain {
                        view.configure(SpectrumOptions(enabled = true, palette = SpectrumPalette.FIRE,
                            refreshRate = rate, peaks = false))
                    }
                    repeat(12) { onMain { view.invalidate() }; SystemClock.sleep(10) }
                    val before = frames.get()
                    val started = SystemClock.elapsedRealtime()
                    SystemClock.sleep(1_100)
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val count = frames.get() - before
                    assertTrue("Redraws accumulated beyond $rate fps: $count in ${elapsed}ms",
                        count in 1..(elapsed * rate / 1_000 + 3).toInt())
                }
                onMain { view.setPlaybackActive(false) }
                SystemClock.sleep(200)
                val pausedFrames = frames.get()
                SystemClock.sleep(300)
                assertEquals("A paused spectrum must stop scheduling frames", pausedFrames, frames.get())
            } finally {
                onMain { view.setPlaybackActive(false); dialog.dismiss() }
            }
        }
    }

    @Test fun slideshowCacheReusesThreePicturesAndEvictsTheLeastRecentlyUsedCopy() = runBlocking {
        val directory = File(app.filesDir, "backgrounds").apply { mkdirs() }
        val files = List(4) { File(directory, "${java.util.UUID.randomUUID()}.png") }
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
        try {
            withContext(Dispatchers.IO) {
                files.forEach { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            val options = app.displaySettings.options.value.copy(customTheme = true, imageVersion = 1,
                imageIds = files.map { it.name }, blur = 0)
            val repository = BackgroundRepository(app)
            val first = repository.render(options, 0)
            val second = repository.render(options, 1)
            val third = repository.render(options, 2)
            assertSame(first, repository.render(options, 0))
            assertSame(second, repository.render(options, 1))
            assertSame(third, repository.render(options, 2))
            assertSame(first, repository.render(options, 0))
            repository.render(options, 3)
            assertSame(first, repository.render(options, 0))
            assertNotSame("Do not retain the entire gallery", second, repository.render(options, 1))
        } finally {
            bitmap.recycle()
            files.forEach { assertTrue(it.delete()) }
        }
    }

    @Test fun backgroundTransitionsLimitUpdatesWithoutChangingRequestedDuration() = runBlocking(Dispatchers.Main) {
        var updates = 0
        val incoming = object : View(app) {
            override fun setAlpha(alpha: Float) { updates++; super.setAlpha(alpha) }
        }.apply { layout(0, 0, 320, 180) }
        val outgoing = View(app).apply { layout(0, 0, 320, 180) }
        val start = SystemClock.uptimeMillis()
        com.macarmusic.app.ui.ViewTransitions.playBackground(incoming, TransitionStyle.FADE, 500, outgoing)
        assertTrue(SystemClock.uptimeMillis() - start >= 500)
        assertTrue("A 500ms background fade should use at most 17 updates, not display-rate updates: $updates", updates in 2..17)
        assertEquals(1f, incoming.alpha, 0.001f)
        assertEquals(1f, outgoing.alpha, 0.001f)
    }

    private fun assertOutlinedWithoutFill(original: Drawable) {
        val drawable = requireNotNull(original.constantState).newDrawable().mutate()
        drawable.state = intArrayOf(android.R.attr.state_enabled)
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            var outline = false
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                val alpha = Color.alpha(bitmap.getPixel(x, y))
                if (x in 16..79 && y in 16..79) assertEquals("Block interiors must remain transparent", 0, alpha)
                else if (alpha > 0) outline = true
            }
            assertTrue("The block outline is missing", outline)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun backgroundGalleryMigratesLegacyPictureAndRollsBackIncompleteImports() = runBlocking(Dispatchers.IO) {
        val first = DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background")
        val second = DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second")
        val legacy = File(app.filesDir, BackgroundImages.LEGACY)
        app.contentResolver.openInputStream(first)!!.use { input -> legacy.outputStream().use(input::copyTo) }
        onMain { app.displaySettings.update { it.copy(customTheme = true, imageVersion = 123, imageIds = emptyList()) } }
        val original = BackgroundRepository(app).render(app.displaySettings.options.value)!!
        assertEquals(0xff3474c0.toInt(), original.getPixel(8, 8))
        original.recycle()
        app.backgrounds.importImages(listOf(first, second, first), app.displaySettings::installImages)
        val saved = app.displaySettings.options.value
        assertEquals(2, saved.imageIds.size)
        assertEquals(saved, DisplaySettings(app) {}.options.value)
        assertFalse(legacy.exists())
        val directory = File(app.filesDir, "backgrounds")
        val before = saved.imageIds.associateWith { File(directory, it).readBytes() }
        assertEquals(0xff82386f.toInt(), app.backgrounds.render(saved, 1)!!.getPixel(8, 8))
        try {
            app.backgrounds.importImages(listOf(first, DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/invalid")),
                app.displaySettings::installImages)
            fail("A partly invalid selection replaced the saved gallery")
        } catch (_: java.io.IOException) { }
        assertEquals(saved, app.displaySettings.options.value)
        assertEquals(saved.imageIds.toSet(), directory.listFiles()!!.map { it.name }.toSet())
        before.forEach { (id, bytes) -> assertArrayEquals(bytes, File(directory, id).readBytes()) }
        try {
            app.backgrounds.importImages(listOf(first)) { throw kotlinx.coroutines.CancellationException("Synthetic cancelled import") }
            fail("Cancelled import succeeded")
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(saved.imageIds.toSet(), directory.listFiles()!!.map { it.name }.toSet())
        try {
            app.backgrounds.importImages((0..BackgroundImages.MAX_COUNT).map {
                DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/too-many-$it")
            }, app.displaySettings::installImages)
            fail("Unbounded gallery accepted")
        } catch (error: java.io.IOException) {
            assertEquals(app.text(R.string.background_too_many, BackgroundImages.MAX_COUNT), error.message)
        }
        available(false)
        try {
            val copy = BackgroundRepository(app).render(saved, 1)!!
            assertEquals(0xff82386f.toInt(), copy.getPixel(8, 8))
            copy.recycle()
        } finally {
            available(true)
        }
    }

    @Test fun slideshowUsesRequestedTimingAnimationsAndStopsWhenHidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            onMain { app.displaySettings.update { it.copy(tint = BackgroundTint.ORIGINAL, tintStrength = 0) } }
            val previous = app.displaySettings.options.value.imageVersion
            scenario.onActivity {
                ViewModelProvider(it)[MusicViewModel::class.java].importBackgrounds(listOf(
                    DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background"),
                    DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second"),
                ))
            }
            await { app.displaySettings.options.value.imageVersion > previous && pictureColor() == 0xff3474c0.toInt() }
            val firstShown = SystemClock.elapsedRealtime()
            await(8_000) {
                val incoming = resumedActivity().findViewById<ImageView>(R.id.backgroundIncoming)
                incoming.visibility == View.VISIBLE && incoming.alpha > 0f && incoming.alpha < 0.7f
            }
            val fadingAt = SystemClock.elapsedRealtime()
            assertTrue("A change should begin after the default 5-second interval", fadingAt - firstShown in 4_300..6_300)
            scenario.onActivity {
                assertEquals(1f, it.findViewById<View>(R.id.backgroundImage).alpha, 0.001f)
                assertContinuousBackground(it)
            }
            await(4_000) { pictureColor() == 0xff82386f.toInt() }
            assertTrue("The default fade should take about 2 seconds", SystemClock.elapsedRealtime() - fadingAt in 1_200..2_700)
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.backgroundInterval).perform(scrollTo(), click())
            assertNotEquals(5, app.displaySettings.options.value.slideshow.intervalSeconds)
            setting(R.id.backgroundDuration).perform(scrollTo(), click())
            assertNotEquals(2_000, app.displaySettings.options.value.slideshow.durationMs)
            chooseDropdown(R.id.backgroundTransition, app.text(R.string.transition_zoom))
            assertEquals(TransitionStyle.ZOOM, app.displaySettings.options.value.slideshow.transition)
            assertEquals(app.displaySettings.options.value, DisplaySettings(app) {}.options.value)
            screenshot("slideshow-settings")
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            for (style in TransitionStyle.entries) {
                onMain { app.displaySettings.update { it.copy(slideshow = SlideshowOptions(2, 500, style)) } }
                val color = onMainResult { pictureColor() }
                if (style != TransitionStyle.NONE) {
                    await(4_000) {
                        val incoming = resumedActivity().findViewById<ImageView>(R.id.backgroundIncoming)
                        incoming.visibility == View.VISIBLE && when (style) {
                            TransitionStyle.FADE -> incoming.alpha in 0.01f..0.99f
                            TransitionStyle.SLIDE -> incoming.translationX > 2f
                            TransitionStyle.ZOOM -> incoming.scaleX > 1.001f
                            TransitionStyle.NONE -> false
                        }
                    }
                }
                await(4_000) { pictureColor() != color }
                scenario.onActivity {
                    val current = it.findViewById<View>(R.id.backgroundImage)
                    assertEquals(1f, current.alpha, 0.001f)
                    assertEquals(0f, current.translationX, 0.001f)
                    assertEquals(1f, current.scaleX, 0.001f)
                }
            }
            var model: MusicViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[MusicViewModel::class.java] }
            scenario.moveToState(Lifecycle.State.CREATED)
            val saved = app.displaySettings.options.value
            val heldIndex = onMainResult { model!!.backgroundIndex(saved.imageVersion, saved.backgroundImages.size) }
            Thread.sleep(2_500)
            assertEquals(heldIndex, onMainResult { model!!.backgroundIndex(saved.imageVersion, saved.backgroundImages.size) })
            scenario.moveToState(Lifecycle.State.RESUMED)
            await { pictureColor() != null }
            scenario.recreate()
            await { pictureColor() != null }
            assertEquals(saved, app.displaySettings.options.value)
            onMain { app.displaySettings.update { it.copy(slideshow = SlideshowOptions()) } }
            scenario.onActivity {
                ViewModelProvider(it)[MusicViewModel::class.java]
                    .importBackground(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background"))
            }
            await { app.displaySettings.options.value.backgroundImages.size == 1 }
            tapAfterWindowTransition(R.id.action_settings)
            await { resumedActivity().findViewById<View>(R.id.settingsPanel)?.isShown == true }
            scenario.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.settingsSlideshow).visibility) }
        }
    }

    @Test fun backgroundPickerHandlesMultipleSingleAndCancelledResults() {
        val first = DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background")
        val second = DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second")
        fun choose(result: Instrumentation.ActivityResult) {
            val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("image/*")
            }
            val monitor = instrumentation.addMonitor(filter, result, true)
            try {
                setting(R.id.settingsBackgroundImage).perform(scrollTo(), click())
                instrumentation.waitForIdleSync()
                assertEquals("The image picker must be launched exactly once", 1, monitor.hits)
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
        onMain {
            app.displaySettings.update { it.copy(customTheme = true, tint = BackgroundTint.ORIGINAL, tintStrength = 0) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            val before = app.displaySettings.options.value.imageVersion
            choose(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().apply {
                clipData = ClipData.newRawUri("Backgrounds", first).apply {
                    addItem(ClipData.Item(second))
                    addItem(ClipData.Item(first))
                    addItem(ClipData.Item("Not an image URI"))
                }
                data = first
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }))
            await {
                val saved = app.displaySettings.options.value
                saved.imageVersion > before && saved.imageIds.size == 2 && pictureColor() == 0xff3474c0.toInt()
            }
            val gallery = app.displaySettings.options.value
            assertEquals(gallery, DisplaySettings(app) {}.options.value)
            scenario.onActivity { assertEquals(View.VISIBLE, it.findViewById<View>(R.id.settingsSlideshow).visibility) }
            choose(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent().setData(second)))
            assertEquals(gallery, app.displaySettings.options.value)
            choose(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(second)))
            await {
                val saved = app.displaySettings.options.value
                saved.imageVersion > gallery.imageVersion && saved.imageIds.size == 1 && pictureColor() == 0xff82386f.toInt()
            }
            scenario.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.settingsSlideshow).visibility) }
            val single = app.displaySettings.options.value
            choose(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().apply {
                clipData = ClipData.newPlainText("Invalid selection", "No image URI")
            }))
            await { app.notice.value?.text == app.text(R.string.picker_no_document) }
            assertEquals(single, app.displaySettings.options.value)
        }
    }

    @Test fun miniPlayerSeekingTimeModesAndSettingsTransitionsPreservePlayback() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY }
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.7f) }, Press.FINGER))
                await { controller.currentPosition in 38_000..45_000 }
                assertFalse(onMainResult { controller.playWhenReady })
                await {
                    resumedActivity().findViewById<TextView>(R.id.miniTime).text ==
                        TimeText.progress(controller.currentPosition, controller.duration, PlaybackTimeMode.ELAPSED, "--:--")
                }
                chooseDropdown(R.id.settingsTimeMode, app.text(R.string.time_remaining))
                await {
                    resumedActivity().findViewById<TextView>(R.id.miniTime).text ==
                        TimeText.progress(controller.currentPosition, controller.duration, PlaybackTimeMode.REMAINING, "--:--")
                }
                if (Build.VERSION.SDK_INT >= 24) {
                    scenario.onActivity {
                        assertTrue(it.findViewById<SeekBar>(R.id.miniSeek).performAccessibilityAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
                            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 2_000f) },
                        ))
                    }
                    await { controller.currentPosition in 11_500..12_500 }
                }
                onMain { controller.play() }
                await { controller.isPlaying }
                for ((style, label) in listOf(
                    TransitionStyle.SLIDE to R.string.transition_slide,
                    TransitionStyle.ZOOM to R.string.transition_zoom,
                    TransitionStyle.NONE to R.string.transition_none,
                    TransitionStyle.FADE to R.string.transition_fade,
                )) {
                    chooseDropdown(R.id.settingsTransition, app.text(label))
                    assertEquals(style, app.displaySettings.options.value.settingsTransition)
                    onView(withContentDescription(app.text(R.string.back))).perform(click())
                    onView(withId(R.id.navigation)).check(matches(isDisplayed()))
                    scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].openSettings() }
                    if (style != TransitionStyle.NONE) {
                        await(1_000) {
                            val view = resumedActivity().findViewById<View>(R.id.settingsPanel)
                            when (style) {
                                TransitionStyle.FADE -> view.alpha in 0.01f..0.99f
                                TransitionStyle.SLIDE -> view.translationX > 1f
                                TransitionStyle.ZOOM -> view.scaleX > 1.001f
                                TransitionStyle.NONE -> false
                            }
                        }
                    }
                    await {
                        val view = resumedActivity().findViewById<View>(R.id.settingsPanel)
                        view.alpha == 1f && view.translationX == 0f && view.scaleX == 1f
                    }
                }
                assertTrue(onMainResult { controller.isPlaying })
                val saved = app.displaySettings.options.value
                assertEquals(saved, DisplaySettings(app) {}.options.value)
                scenario.recreate()
                assertEquals(PlaybackTimeMode.REMAINING, app.displaySettings.options.value.timeMode)
                screenshot("settings-mini-progress")
                onMain { controller.pause(); controller.clearMediaItems() }
                await { !resumedActivity().findViewById<SeekBar>(R.id.miniSeek).isEnabled }
                assertEquals("--:-- / --:--", onMainResult {
                    resumedActivity().findViewById<TextView>(R.id.miniTime).text.toString()
                })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun spectrumUsesPlayingAudioAndPersistsBothPositionsAndClassicControls() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsSpectrum).perform(scrollTo(), click())
                chooseDropdown(R.id.spectrumPosition, app.text(R.string.position_left))
                chooseDropdown(R.id.spectrumBands, app.text(R.string.spectrum_thick))
                chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_fire))
                chooseDropdown(R.id.spectrumFalloff, app.text(R.string.speed_fast))
                chooseDropdown(R.id.spectrumPeakFalloff, app.text(R.string.speed_medium))
                chooseDropdown(R.id.spectrumRefreshRate, app.text(R.string.spectrum_fps, 15))
                setting(R.id.spectrumPeaks).perform(scrollTo(), click())
                assertFalse(app.displaySettings.options.value.spectrum.peaks)
                setting(R.id.spectrumPeaks).perform(click())
                screenshot("spectrum-settings")
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { app.spectrum.frames.value.hasAudio && app.spectrum.frames.value.levels.max() > 0.3f }
                assertEquals(22_050, app.spectrum.frames.value.sampleRate)
                scenario.onActivity {
                    val row = it.findViewById<LinearLayout>(R.id.metadataRow)
                    assertEquals(R.id.spectrum, row.getChildAt(0).id)
                    assertTrue(row.getChildAt(0).width > 80)
                    assertTrue(it.findViewById<View>(R.id.trackInformation).width > 80)
                }
                screenshot("spectrum-left-landscape")
                onMain { controller.pause() }
                await { !app.spectrum.frames.value.hasAudio }
                onMain { controller.play() }
                await { app.spectrum.frames.value.hasAudio }
                onView(withId(R.id.action_settings)).perform(click())
                await { !app.spectrum.frames.value.hasAudio }
                chooseDropdown(R.id.spectrumPosition, app.text(R.string.position_right))
                chooseDropdown(R.id.spectrumStyle, app.text(R.string.spectrum_line))
                chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_theme))
                chooseDropdown(R.id.spectrumRefreshRate, app.text(R.string.spectrum_fps, 60))
                assertEquals(SpectrumPosition.RIGHT, app.displaySettings.options.value.spectrum.position)
                assertEquals(SpectrumStyle.LINE, app.displaySettings.options.value.spectrum.style)
                assertEquals(SpectrumPalette.THEME, app.displaySettings.options.value.spectrum.palette)
                assertEquals(app.displaySettings.options.value, DisplaySettings(app) {}.options.value)
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                await { app.spectrum.frames.value.hasAudio }
                scenario.onActivity {
                    val row = it.findViewById<LinearLayout>(R.id.metadataRow)
                    assertEquals(R.id.spectrum, row.getChildAt(row.childCount - 1).id)
                }
                screenshot("spectrum-right-portrait")
                scenario.moveToState(Lifecycle.State.CREATED)
                await { !app.spectrum.frames.value.hasAudio }
                assertTrue(onMainResult { controller.isPlaying })
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { app.spectrum.frames.value.hasAudio }
                await { resumedActivity().hasWindowFocus() }
                tapAfterWindowTransition(R.id.action_settings)
                await {
                    val view = resumedActivity().findViewById<View>(R.id.settingsPanel)
                    view != null && view.isShown && view.width > 0 && view.alpha == 1f
                }
                setting(R.id.settingsSpectrum).perform(scrollTo(), click())
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                scenario.onActivity { assertEquals(View.GONE, it.findViewById<View>(R.id.spectrum).visibility) }
                assertFalse(app.spectrum.frames.value.hasAudio)
                assertTrue(onMainResult { controller.isPlaying })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun miniPlayerIgnoresAnOldDragWhenTheTrackChanges() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY }
                onView(withId(R.id.action_settings)).perform(click())
                var changed = false
                scenario.onActivity {
                    it.findViewById<SeekBar>(R.id.miniSeek).setOnTouchListener { _, event ->
                        if (!changed && event.actionMasked == MotionEvent.ACTION_MOVE) {
                            changed = true
                            controller.seekTo(1, 5_000)
                        }
                        false
                    }
                }
                onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.75f) }, Press.FINGER))
                await { controller.currentMediaItemIndex == 1 && controller.playbackState == Player.STATE_READY }
                assertTrue("The synthetic track change must happen during the drag", changed)
                val position = onMainResult { controller.currentPosition }
                assertTrue("A stale drag sought the new song to $position ms", position in 4_500..5_500)
                assertFalse(onMainResult { controller.playWhenReady })
                onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.5f) }, Press.FINGER))
                await { controller.currentPosition in 28_000..32_000 }
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                scenario.onActivity { it.findViewById<SeekBar>(R.id.miniSeek).setOnTouchListener(null) }
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun spectrumPcmTapLeavesAudioBytesUnchangedAndStopsWithoutViewers() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val source = SpectrumSource(scope)
        val owner = Any()
        val processor = source.audioProcessor()
        val bytes = ByteBuffer.allocate(SpectrumAnalyzer.SAMPLE_COUNT * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (index in 0 until SpectrumAnalyzer.SAMPLE_COUNT) {
                putShort((kotlin.math.sin(2 * Math.PI * 440 * index / 22_050) * 10_000).toInt().toShort())
            }
        }.array()
        fun send(): ByteArray {
            val input = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN).put(bytes).apply { flip() }
            processor.queueInput(input)
            assertFalse(input.hasRemaining())
            return ByteArray(bytes.size).also { processor.output.get(it) }
        }
        try {
            processor.configure(AudioProcessor.AudioFormat(22_050, 1, C.ENCODING_PCM_16BIT))
            processor.flush()
            source.setVisible(owner, true)
            source.setPlaying(true)
            assertArrayEquals(bytes, send())
            await { source.frames.value.hasAudio && source.frames.value.levels.max() > 0.5f }
            source.setVisible(owner, false)
            assertArrayEquals(bytes, send())
            assertFalse(source.frames.value.hasAudio)
            source.setVisible(owner, true)
            assertArrayEquals(bytes, send())
            await { source.frames.value.hasAudio }
            source.setPlaying(false)
            assertFalse(source.frames.value.hasAudio)
        } finally {
            processor.reset()
            scope.cancel()
        }
    }

    private fun resumedActivity(): MainActivity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()

    private fun pictureColor(): Int? =
        (resumedActivity().findViewById<ImageView>(R.id.backgroundImage).drawable as? BitmapDrawable)?.bitmap?.getPixel(8, 8)

    private fun chooseDropdown(viewId: Int, label: String) {
        setting(viewId).perform(scrollTo(), click())
        onData(equalTo(label)).perform(click())
        instrumentation.waitForIdleSync()
    }

    private fun selectCategory(category: SettingsCategory) {
        onView(withId(category.buttonId)).perform(scrollTo())
        tapAfterWindowTransition(category.buttonId)
        await { resumedActivity().findViewById<View>(category.panelId).isShown }
    }

    private fun setting(viewId: Int): ViewInteraction {
        val category = onMainResult {
            val view = resumedActivity().findViewById<View>(viewId)
            SettingsCategory.entries.single { category ->
                generateSequence(view) { it.parent as? View }.any { it.id == category.panelId }
            }
        }
        if (!onMainResult { resumedActivity().findViewById<View>(category.panelId).isShown }) selectCategory(category)
        return onView(withId(viewId))
    }

    private fun seekCoordinates(view: View, fraction: Float): FloatArray {
        val position = IntArray(2)
        view.getLocationOnScreen(position)
        return floatArrayOf(position[0] + view.paddingLeft + (view.width - view.paddingLeft - view.paddingRight) * fraction,
            position[1] + view.height / 2f)
    }

    private fun tapAfterWindowTransition(viewId: Int) {
        // UiAutomation synchronizes input with rotation and task-return animations outside the app looper.
        val point = onMainResult {
            val view = resumedActivity().findViewById<View>(viewId)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            floatArrayOf(location[0] + view.width / 2f, location[1] + view.height / 2f)
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point[0], point[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("Android rejected the post-resume tap", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun customImageBlurTintAndSettingsPersistWhilePlaybackContinues() {
        val tracks = fixtureTracks()
        val picture = DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
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
                chooseSetting(R.id.settingsTheme, R.string.theme_custom)
                val previous = app.displaySettings.options.value.imageVersion
                scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].importBackground(picture) }
                await { app.displaySettings.options.value.imageVersion > previous }
                awaitBackground()
                val sharp = runBlocking { app.backgrounds.render(app.displaySettings.options.value)!! }
                assertEquals(256, sharp.width)
                assertEquals(0xff3474c0.toInt(), sharp.getPixel(8, 8))
                setting(R.id.backgroundBlur).perform(scrollTo(), click())
                await { app.displaySettings.options.value.blur > 0 }
                val blur = app.displaySettings.options.value.blur
                val softened = runBlocking { app.backgrounds.render(app.displaySettings.options.value)!! }
                assertNotEquals(sharp.getPixel(15, 15), softened.getPixel(15, 15))
                chooseSetting(R.id.settingsBackgroundTint, R.string.tint_light)
                setting(R.id.backgroundTintStrength).perform(scrollTo(), click())
                await { app.displaySettings.options.value.tintStrength != 35 }
                val light = app.displaySettings.options.value
                assertEquals(blur, light.blur)
                awaitBackground()
                scenario.onActivity {
                    assertEquals(Configuration.UI_MODE_NIGHT_NO, it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                    assertEquals(BackgroundEffects.tintColor(BackgroundTint.LIGHT, light.tintStrength),
                        (it.findViewById<View>(R.id.backgroundTint).background as ColorDrawable).color)
                    assertContinuousBackground(it)
                }
                pressBack()
                screenshot("custom-light-blur")
                onView(withId(R.id.action_settings)).perform(click())
                chooseSetting(R.id.settingsBackgroundTint, R.string.tint_original)
                onMain { app.displaySettings.update { it.copy(blur = 0) } }
                val saved = app.displaySettings.options.value
                assertEquals(saved, DisplaySettings(app) {}.options.value)
                scenario.recreate()
                setting(R.id.settingsTheme).check(matches(withText(containsString(app.text(R.string.theme_custom)))))
                assertEquals(saved, app.displaySettings.options.value)
                assertEquals(DisplaySettings.CUSTOM_THEME, app.themeMode)
                assertTrue(onMainResult { controller.isPlaying })
                pressBack()
                awaitBackground()
                scenario.onActivity(::assertContinuousBackground)
                screenshot("custom-original")
                available(false)
                try {
                    val privateCopy = runBlocking { BackgroundRepository(app).render(saved)!! }
                    assertEquals(sharp.getPixel(8, 8), privateCopy.getPixel(8, 8))
                    privateCopy.recycle()
                } finally {
                    available(true)
                }
                val privateImage = File(File(app.filesDir, "backgrounds"), saved.imageIds.single())
                val before = privateImage.readBytes()
                try {
                    runBlocking {
                        app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/invalid")),
                            app.displaySettings::installImages)
                    }
                    fail("Invalid image accepted")
                } catch (_: java.io.IOException) { }
                assertArrayEquals(before, privateImage.readBytes())
                assertEquals(saved, app.displaySettings.options.value)
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
                available(true)
            }
        }
    }

    @Test fun customizedPictureStaysContinuousAcrossScreensAndNormalThemeRestores() {
        fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            onMain {
                app.themeMode = DisplaySettings.CUSTOM_THEME
                app.displaySettings.update { it.copy(blur = 0, tint = BackgroundTint.ORIGINAL, tintStrength = 0) }
            }
            val previous = app.displaySettings.options.value.imageVersion
            scenario.onActivity {
                ViewModelProvider(it)[MusicViewModel::class.java]
                    .importBackground(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background"))
            }
            await { app.displaySettings.options.value.imageVersion > previous }
            awaitBackground()
            onMain { app.inform("Synthetic visible status") }
            for (screen in listOf(R.id.nav_player, R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                onView(withId(screen)).perform(click())
                scenario.onActivity(::assertContinuousBackground)
                if (screen == R.id.nav_player) screenshot("custom-continuous-player")
                if (screen == R.id.nav_library) screenshot("custom-continuous-library")
            }
            onView(withId(R.id.action_settings)).perform(click())
            scenario.onActivity(::assertContinuousBackground)
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            awaitBackground()
            scenario.onActivity(::assertContinuousBackground)
            screenshot("custom-continuous-settings-portrait")
            onMain { app.themeMode = AppCompatDelegate.MODE_NIGHT_YES }
            await {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
                activity?.findViewById<ImageView>(R.id.backgroundImage)?.visibility == View.GONE
            }
            scenario.onActivity {
                val navigation = it.findViewById<PlayerNavigationView>(R.id.navigation)
                assertTrue(navigation.isItemActiveIndicatorEnabled)
                assertFalse(backgroundIsTransparent(navigation))
            }
        }
    }

    private fun awaitBackground() = await {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().singleOrNull()
        val image = activity?.findViewById<ImageView>(R.id.backgroundImage)
        activity != null && image?.drawable is BitmapDrawable && image.isShown &&
            image.height == activity.window.decorView.height
    }

    private fun assertContinuousBackground(activity: MainActivity) {
        val panels = listOf(R.id.toolbar, R.id.navigation, R.id.miniPlayer, R.id.noticeBar, R.id.controls, R.id.cover)
            .map { activity.findViewById<View>(it) } +
            activity.findViewById<android.widget.FrameLayout>(R.id.screenContainer).children.toList()
        panels.forEach { panel ->
            assertTrue("Custom theme painted a separate panel: ${panel.id}", backgroundIsTransparent(panel))
        }
        val navigation = activity.findViewById<PlayerNavigationView>(R.id.navigation)
        assertFalse(navigation.isItemActiveIndicatorEnabled)
        assertEquals(0f, navigation.elevation, 0.001f)
        if (Build.VERSION.SDK_INT >= 29) {
            assertFalse(activity.window.isStatusBarContrastEnforced)
            assertFalse(activity.window.isNavigationBarContrastEnforced)
        }
        val image = activity.findViewById<View>(R.id.backgroundImage)
        val geometry = generateSequence(image) { it.parent as? View }.joinToString(" <- ") {
            "${it.javaClass.simpleName}:height=${it.height},padding=${it.paddingTop}/${it.paddingBottom},fits=${it.fitsSystemWindows}"
        }
        assertEquals("The image must cover the window: $geometry", activity.window.decorView.height, image.height)
    }

    private fun backgroundIsTransparent(view: View): Boolean {
        val drawable = view.background ?: return true
        val previous = drawable.copyBounds()
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        return try {
            drawable.setBounds(0, 0, 16, 16)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(16 * 16)
            bitmap.getPixels(pixels, 0, 16, 0, 0, 16, 16)
            pixels.all { Color.alpha(it) == 0 }
        } finally {
            drawable.bounds = previous
            bitmap.recycle()
        }
    }

    private fun fixtureTracks(): List<Track> = runBlocking(Dispatchers.IO) {
        app.repository.update { Library() }
        val source = app.repository.addSource(root, 0)
        app.repository.scan(source, null) {}.tracks
    }

    private fun lyricMode(value: String) { app.contentResolver.call(root, "setLyricsMode", value, null) }

    private fun syntheticLyricClient(track: Track, called: () -> Unit): OnlineLyricsClient {
        val title = LyricQueries.candidates(LyricQuery.from(track), track.fileName).first().title
        return OnlineLyricsClient({ url, _, _ ->
            called()
            if (url.contains("song_search_v2")) {
                """{"status":1,"data":{"lists":[{"SongName":"$title","SingerName":"","Duration":60,"FileHash":"${"a".repeat(32)}"}]}}"""
            } else if (url.contains("/search?")) {
                """{"status":200,"candidates":[{"id":"42","accesskey":"test-only","song":"$title","singer":"","duration":60000}]}"""
            } else {
                val encoded = android.util.Base64.encodeToString("[00:01]Synthetic online marker".toByteArray(), android.util.Base64.NO_WRAP)
                """{"status":200,"content":"$encoded"}"""
            }
        }) { _, error -> throw AssertionError("Unexpected synthetic provider failure", error) }
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
        runBlocking { app.playback.read() }
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
                assertEquals("小悦音乐播放器", app.text(R.string.app_name))
                setting(R.id.settingsLanguage).check(matches(withText(containsString("语言"))))
                assertEquals("设置", app.text(R.string.settings))
                chooseSetting(R.id.settingsTheme, R.string.theme_day)
                assertEquals(AppCompatDelegate.MODE_NIGHT_NO, app.themeMode)
                await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks.first().id }
                scenario.recreate()
                setting(R.id.settingsLanguage).check(matches(withText(containsString("简体中文"))))
                screenshot("settings-chinese-day")
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                setting(R.id.settingsLanguage).check(matches(isDisplayed()))
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
                assertEquals("ManaMusicPlayer", app.text(R.string.app_name))
                chooseSetting(R.id.settingsTheme, R.string.theme_night)
                setting(R.id.settingsLanguage).perform(scrollTo())
                setting(R.id.settingsLanguage).check(matches(withText(containsString("Language"))))
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
                setting(R.id.settingsFolders).perform(scrollTo(), click())
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
                setting(R.id.settingsPlayFolders).perform(scrollTo(), click())
                await { controller.isPlaying && controller.mediaItemCount == 1 }
                assertEquals(nested.id, onMainResult { controller.currentMediaItem!!.mediaId })
                onView(withId(R.id.shuffle)).perform(scrollTo(), click())
                await { controller.shuffleModeEnabled && app.playbackSettings.options.value.order == PlayOrder.RANDOM }
                onView(withId(R.id.play)).perform(scrollTo(), click())
                await { !controller.playWhenReady }
                onView(withId(R.id.action_settings)).perform(click())
                runBlocking { app.repository.addSource(root, 0) }
                assertFalse(app.repository.library.value.sources.first { it.uri == root.toString() }.selectedForPlayback)
                setting(R.id.settingsFolders).perform(scrollTo(), click())
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
                setting(R.id.settingsFolders).perform(scrollTo(), click())
                for (source in app.repository.library.value.sources) {
                    onView(withText(app.text(R.string.folder_choice, source.name, source.lastScanCount)))
                        .inRoot(isDialog()).perform(click())
                }
                onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
                await { controller.mediaItemCount == 0 && !controller.playWhenReady }
                assertEquals(2, app.repository.library.value.tracks.size)
                setting(R.id.settingsFolders).perform(scrollTo())
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
        var original: MainActivity? = null
        var needsRecreation = false
        scenario.onActivity {
            original = it
            needsRecreation = it.resources.configuration.orientation != expected
            it.requestedOrientation = orientation
        }
        await {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>()
                .any {
                    val decor = it.window.decorView
                    val portrait = decor.height > decor.width
                    (!needsRecreation || it !== original) && !it.isChangingConfigurations &&
                        it.hasWindowFocus() && it.resources.configuration.orientation == expected &&
                        decor.width > 0 && portrait == (expected == Configuration.ORIENTATION_PORTRAIT)
                }
        }
        instrumentation.waitForIdleSync()
    }

    private fun chooseSetting(viewId: Int, label: Int) {
        setting(viewId).perform(scrollTo(), click())
        onData(equalTo(app.text(label))).inRoot(isDialog()).perform(click())
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
        runBlocking { app.playback.read() }
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
        runBlocking { app.playback.read() }
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
        // Playback updates can keep accessibility busy even when the app's UI queue is idle.
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun onMain(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else instrumentation.runOnMainSync(block)
    }

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
