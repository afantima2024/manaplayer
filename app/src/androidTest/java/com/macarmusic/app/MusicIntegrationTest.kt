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
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.children
import androidx.core.view.doOnAttach
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
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withSpinnerText
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
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
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.lyrics.LocalLyricsLoader
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.InternalLyricCache
import com.macarmusic.app.lyrics.SafLyricCache
import com.macarmusic.app.lyrics.LyricIssue
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.LyricQueries
import com.macarmusic.app.lyrics.LyricQuery
import com.macarmusic.app.lyrics.LyricDownloadPhase
import com.macarmusic.app.lyrics.LyricsRepository
import com.macarmusic.app.lyrics.OnlineLyricsClient
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.LyricSide
import com.macarmusic.app.model.ThemeAccent
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
import com.macarmusic.app.model.LibraryMode
import com.macarmusic.app.model.LibraryBrowsing
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.CollectionKind
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.PlaylistFolder
import com.macarmusic.app.model.BrowserLocation
import com.macarmusic.app.data.ColorSlotStore
import androidx.core.content.FileProvider
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.SpectrumAnalyzer
import com.macarmusic.app.playback.SpectrumSource
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.AccentTheme
import com.macarmusic.app.ui.PlayerDialog
import com.macarmusic.app.ui.MiniPlayerLayout
import com.macarmusic.app.ui.LyricsAdapter
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.TimeText
import com.macarmusic.app.ui.SettingsCategory
import com.macarmusic.app.ui.SpectrumView
import com.macarmusic.app.model.SpectrumOrigin
import com.macarmusic.app.ui.PlayerNavigationView
import com.macarmusic.app.ui.PlayerLayout
import com.macarmusic.app.ui.PlayerBlockDrawable
import com.macarmusic.app.ui.BlockButtonStyler
import com.macarmusic.app.ui.ListPanel
import com.macarmusic.app.ui.awaitDraw
import com.macarmusic.app.playback.SpectrumFrame
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.anything
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
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true)
            .remove("customColorSlots").commit()
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
        app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply { putBoolean("resetReads", true) })
        runBlocking { app.repository.awaitReady() }
        onMain {
            if (Build.VERSION.SDK_INT >= 33) {
                app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                    android.os.LocaleList.forLanguageTags("en")
            } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            app.displaySettings.update {
                it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false,
                    blur = 0, tint = BackgroundTint.DARK, tintStrength = 35,
                    slideshow = SlideshowOptions(), settingsTransition = TransitionStyle.FADE,
                    timeMode = PlaybackTimeMode.ELAPSED, spectrum = SpectrumOptions(), controlBlocks = true,
                    mainTimeMode = PlaybackTimeMode.ELAPSED, navigationPosition = NavigationPosition.BOTTOM,
                    lyricsDragSeek = false, lyricsSmoothScroll = true, lyricAppearance = LyricAppearance(),
                    landscapeLyrics = LyricSide.START, portraitLyrics = LyricSide.START, themeAccent = ThemeAccent.DEFAULT,
                    blockOutline = BlockOutline(), customAccentColor = null, widgetTransparency = 50)
            }
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.playbackSettings.setLoop(LoopMode.OFF)
            app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
        }
    }

    @Test fun fineTuningLegacySettingsMigrateAndNewOptionsRoundTrip() {
        val context = instrumentation.context
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        preferences.edit().clear().putInt("lyricEnlargementPercent", 60)
            .putString("spectrumFireStyle", "LED").commit()
        try {
            val settings = DisplaySettings(context) {}
            assertEquals(32, settings.options.value.lyricAppearance.activeSizeSp)
            assertEquals(20, settings.options.value.lyricAppearance.otherSizeSp)
            assertEquals(FireStyle.SMOOTH, settings.options.value.spectrum.barStyle)
            settings.update { it.copy(
                lyricAppearance = LyricAppearance(31, 18, 0x448aff, 0xffffff),
                landscapeLyrics = LyricSide.END, portraitLyrics = LyricSide.END,
                themeAccent = ThemeAccent.BLUE, customAccentColor = 0x123abc,
                blockOutline = BlockOutline(BlockStyle.DASHED_ARC, "+", 36, true),
                spectrum = it.spectrum.copy(barStyle = FireStyle.LED, fireStyle = FireStyle.SMOOTH),
            ) }
            preferences.edit().putInt("lyricEnlargementPercent", 0).commit()
            assertEquals(settings.options.value, DisplaySettings(context) {}.options.value)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun compactUpdateDefaultsPreserveExplicitChoicesAndLegacyQueues() {
        val context = object : android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("compact-update116-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        try {
            val playback = PlaybackSettings(context)
            assertEquals(LoopMode.FOLDER, playback.options.value.loop)
            assertEquals(PlayOrder.RANDOM, playback.options.value.order)
            playback.migrate(PlaybackSnapshot())
            assertEquals(LoopMode.FOLDER, playback.options.value.loop)
            assertEquals(PlayOrder.RANDOM, playback.options.value.order)
            val display = DisplaySettings(context) {}
            assertEquals(PlaybackTimeMode.REMAINING, display.options.value.timeMode)
            assertEquals(PlaybackTimeMode.REMAINING, display.options.value.mainTimeMode)
            assertEquals(50, display.options.value.widgetTransparency)
            display.update { it.copy(widgetTransparency = 85, timeMode = PlaybackTimeMode.ELAPSED,
                mainTimeMode = PlaybackTimeMode.ELAPSED) }
            playback.setLoop(LoopMode.OFF)
            playback.setOrder(PlayOrder.SEQUENTIAL)
            PlaybackSettings(context).migrate(PlaybackSnapshot(repeat = 2, shuffle = true))
            assertEquals(LoopMode.OFF, PlaybackSettings(context).options.value.loop)
            assertEquals(PlayOrder.SEQUENTIAL, PlaybackSettings(context).options.value.order)
            assertEquals(display.options.value, DisplaySettings(context) {}.options.value)
            assertTrue(preferences.edit().clear().commit())
            val legacy = PlaybackSettings(context)
            legacy.migrate(PlaybackSnapshot(queue = listOf(Track("old", "content://fixture/old", "old.wav", "Old")),
                repeat = 0, shuffle = false))
            assertEquals(LoopMode.OFF, legacy.options.value.loop)
            assertEquals(PlayOrder.SEQUENTIAL, legacy.options.value.order)
            assertTrue(preferences.edit().putInt("widgetTransparency", 200).commit())
            assertEquals(100, DisplaySettings(context) {}.options.value.widgetTransparency)
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun generalDefaultPlayerActionOpensPlatformSettingsAndListsBordersInOrder() {
        val filter = IntentFilter(if (Build.VERSION.SDK_INT >= 24)
            android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS
            else android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            if (Build.VERSION.SDK_INT < 24) addDataScheme("package")
        }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsDefaultPlayer).perform(scrollTo(), click())
                onView(withText(R.string.default_music_player_help)).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText(R.string.open_system_settings)).inRoot(isDialog()).perform(click())
                assertEquals(1, monitor.hits)
                scenario.onActivity { activity ->
                    val spinner = activity.findViewById<android.widget.Spinner>(R.id.settingsBlockStyle)
                    val labels = listOf(R.string.block_solid, R.string.block_arc, R.string.block_dashed,
                        R.string.block_dashed_arc, R.string.block_characters).map(activity::getString)
                    assertEquals(labels, (0 until spinner.adapter.count).map { spinner.adapter.getItem(it).toString() })
                    assertTrue(activity.findViewById<View>(R.id.settingsDefaultPlayer).foreground is PlayerBlockDrawable)
                }
                setting(R.id.widgetTransparency).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.2f) }, { seekCoordinates(it, 1f) }, Press.FINGER))
                setting(R.id.widgetTransparency).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE, { seekCoordinates(it, 1f) }, Press.FINGER))
                val value = DisplaySettings(app) {}.options.value.widgetTransparency
                assertEquals(100, value)
                scenario.recreate()
                scenario.onActivity { assertEquals(value, it.findViewById<SeekBar>(R.id.widgetTransparency).progress) }
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun defaultFolderLoopDoesNotPreventExplicitPlaybackOfStandaloneLibraryFiles() {
        val tracks = fixtureTracks().map { it.copy(sourceUris = emptyMap(), sourceParents = emptyMap(), sourcePaths = emptyMap()) }
        runBlocking { app.repository.update { Library(tracks = tracks) } }
        lateinit var model: MusicViewModel
        lateinit var store: ViewModelStore
        onMain {
            app.playbackSettings.setLoop(LoopMode.FOLDER)
            app.playbackSettings.setOrder(PlayOrder.RANDOM)
            model = MusicViewModel(app, SavedStateHandle())
            store = ViewModelStore().apply { put("standalone-folder-default", model) }
            model.requestPlay(tracks, 1, useLoopScope = true)
        }
        try {
            await { model.playRequest.value != null }
            val single = checkNotNull(model.playRequest.value)
            assertEquals(listOf(tracks[1]), single.tracks)
            assertEquals(0, single.index)
            assertTrue(single.playWhenReady)
            assertEquals(app.text(R.string.folder_loop_song_selection), app.notice.value?.text)
            onMain { model.requestPlay(tracks, useLoopScope = true) }
            await { model.playRequest.value?.token != single.token }
            val all = checkNotNull(model.playRequest.value)
            assertEquals(tracks, all.tracks)
            assertTrue(all.index in tracks.indices)
            assertEquals(LoopMode.FOLDER, app.playbackSettings.options.value.loop)
            onMain {
                app.playbackSettings.setLoop(LoopMode.SELECTED_FOLDERS)
                model.requestPlay(tracks, 1, useLoopScope = true)
            }
            await { app.notice.value?.text == app.text(R.string.selected_folders_empty) }
            assertEquals("An empty explicit folder selection must not play unrelated tracks", all, model.playRequest.value)
        } finally { onMain { store.clear() } }
    }

    @Test fun everyLibraryPageAndRecycledRowUsesTheGlobalButtonStyle() {
        val tracks = fixtureTracks()
        runBlocking { app.repository.update { it.copy(playlists = listOf(LocalPlaylist("style-list", "Style list",
            tracks.map { track -> track.id }))) } }
        val future = onMainResult {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            await { app.playbackReady.value }
            onMain { controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0); controller.prepare(); controller.pause() }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for (style in BlockStyle.selectable) {
                    onMain { app.displaySettings.update { it.copy(controlBlocks = true, blockOutline = BlockOutline(style, "+")) } }
                    for (page in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                        onView(withId(page)).perform(click())
                        await {
                            resumedActivity().findViewById<ViewGroup>(R.id.screenContainer).children
                                .filterIsInstance<ListPanel>().firstOrNull { it.isShown }?.list?.childCount?.let { it > 0 } == true
                        }
                        scenario.onActivity { activity ->
                            val panel = activity.findViewById<ViewGroup>(R.id.screenContainer).children
                                .filterIsInstance<ListPanel>().single { it.isShown }
                            fun check(view: View) {
                                if (view is MaterialButton || view is android.widget.ImageButton) {
                                    assertTrue("Unstyled page button ${view.id}", view.foreground is PlayerBlockDrawable)
                                    if (view is MaterialButton) assertEquals(0, view.strokeWidth)
                                } else if (view is ViewGroup) view.children.forEach(::check)
                            }
                            check(panel.top)
                            for (row in panel.list.children) {
                                assertTrue(row.findViewById<View>(R.id.more).foreground is PlayerBlockDrawable)
                            }
                        }
                    }
                }
                screenshot("compact-update-playlist-buttons")
                onMain { app.displaySettings.update { it.copy(controlBlocks = false) } }
                for (page in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                    onView(withId(page)).perform(click())
                    scenario.onActivity { activity ->
                        val panel = activity.findViewById<ViewGroup>(R.id.screenContainer).children
                            .filterIsInstance<ListPanel>().single { it.isShown }
                        fun check(view: View) {
                            if (view is MaterialButton || view is android.widget.ImageButton) {
                                assertFalse(view.foreground is PlayerBlockDrawable)
                                if (view is MaterialButton) assertTrue(view.strokeWidth > 0)
                            } else if (view is ViewGroup) view.children.forEach(::check)
                        }
                        check(panel.top)
                        for (row in panel.list.children) assertFalse(row.findViewById<View>(R.id.more).foreground is PlayerBlockDrawable)
                    }
                }
            }
        } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
    }

    @Test fun legacyDisplayPreferencesIgnoreLightModeAndKeepFireColorsAtEverySpectrumRate() {
        val context = object : android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("display-update125-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        try {
            assertEquals(15, DisplaySettings(context) {}.options.value.spectrum.refreshRate)
            for (rate in listOf(15, 30, 60)) {
                assertTrue(preferences.edit().putInt("spectrumRefreshRate", rate).commit())
                val settings = DisplaySettings(context) {}
                settings.update { it.copy(
                    onlineLyrics = true, blur = 65, settingsTransition = TransitionStyle.ZOOM,
                    spectrum = it.spectrum.copy(enabled = true, refreshRate = rate),
                    imageVersion = 1, imageIds = listOf(
                        "00000000-0000-0000-0000-000000000001.png", "00000000-0000-0000-0000-000000000002.png"),
                ) }
                val original = settings.options.value
                val fire = SpectrumColors(0x123456, 0xabcdef, 0x654321)
                assertTrue(preferences.edit().putBoolean("lightMode", true)
                    .putInt("classicColorLow", 0).putInt("classicColorMiddle", 0).putInt("classicColorHigh", 0)
                    .putInt("fireColorLow", fire.low).putInt("fireColorMiddle", fire.middle).putInt("fireColorHigh", fire.high)
                    .commit())
                val reloaded = DisplaySettings(context) {}
                val expected = original.copy(spectrum = original.spectrum.copy(colors = fire))
                assertEquals(expected, reloaded.options.value)
                assertEquals(rate, reloaded.options.value.spectrum.refreshRate)
                assertTrue(reloaded.options.value.spectrum.enabled)
                assertTrue(reloaded.options.value.onlineLyrics)
                assertEquals(2, reloaded.options.value.backgroundImages.size)
                reloaded.update { it.copy(blur = 64) }
                assertEquals(expected.copy(blur = 64), DisplaySettings(context) {}.options.value)
                for (key in listOf("lightMode", "classicColorLow", "classicColorMiddle", "classicColorHigh"))
                    assertFalse(preferences.contains(key))
            }
        } finally { assertTrue(preferences.edit().clear().commit()) }
    }

    @Test fun pausedProgressStopsPollingAndHiddenMainControlsStayIdle() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun polling(name: String): Boolean {
                val field = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
                return (field.get(resumedActivity()) as kotlinx.coroutines.flow.StateFlow<*>).value == true
            }
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying && polling("progressPolling") && polling("volumePolling") }
                onView(withId(R.id.action_settings)).perform(click())
                await { polling("progressPolling") && !polling("volumePolling") }
                val hiddenTime = onMainResult { resumedActivity().findViewById<TextView>(R.id.position).text.toString() }
                val miniTime = onMainResult { resumedActivity().findViewById<TextView>(R.id.miniTime).text.toString() }
                await { resumedActivity().findViewById<TextView>(R.id.miniTime).text.toString() != miniTime }
                assertEquals(hiddenTime, onMainResult { resumedActivity().findViewById<TextView>(R.id.position).text.toString() })
                onMain { controller.pause() }
                await { !polling("progressPolling") && !polling("volumePolling") }
                selectCategory(SettingsCategory.GENERAL)
                val blocks = app.displaySettings.options.value.controlBlocks
                setting(R.id.settingsControlBlocks).perform(scrollTo(), click())
                assertEquals(!blocks, app.displaySettings.options.value.controlBlocks)
                assertFalse(onMainResult { controller.playWhenReady })
                setting(R.id.settingsControlBlocks).perform(click())
                assertEquals(blocks, app.displaySettings.options.value.controlBlocks)
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { !polling("progressPolling") && polling("volumePolling") }
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun fineTuningLargeSelectionsDoNotOverflowActivitySavedState() {
        onMain {
            val saved = SavedStateHandle()
            val model = MusicViewModel(app, saved)
            val store = ViewModelStore().apply { put("selection", model) }
            val parcel = android.os.Parcel.obtain()
            try {
                repeat(2_000) { model.selectTrack("selected-$it".padEnd(512, 'x')) }
                assertEquals(2_000, model.librarySelection.value.size)
                parcel.writeBundle(saved.savedStateProvider().saveState())
                assertTrue("Large multi-selections must stay outside the activity Bundle", parcel.dataSize() < 16_384)
                model.selectLibrary(false)
                assertTrue(model.librarySelection.value.isEmpty())
            } finally { parcel.recycle(); store.clear() }
        }
    }

    @Test fun polishLegacyAppearanceStateRestoresIntoGeneral() {
        onMain {
            for (legacy in listOf(SettingsCategory.APPEARANCE, SettingsCategory.BACKGROUNDS)) {
                val saved = SavedStateHandle(mapOf("settingsCategory" to legacy))
                val model = MusicViewModel(app, saved)
                val store = ViewModelStore().apply { put("settings", model) }
                try {
                    assertEquals(SettingsCategory.GENERAL, model.settingsCategory.value)
                    assertEquals(SettingsCategory.GENERAL, saved.get<SettingsCategory>("settingsCategory"))
                    assertEquals(6, SettingsCategory.pages.size)
                    model.settingsCategory(legacy)
                    assertEquals(SettingsCategory.GENERAL, model.settingsCategory.value)
                } finally { store.clear() }
            }
        }
    }

    @Test fun customBackgroundControlsLiveOnlyInGeneralAndRetainTheirSettingsWhenHidden() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            for (appearance in listOf(R.string.theme_custom, R.string.theme_day, R.string.theme_night, R.string.theme_custom)) {
                chooseSetting(R.id.settingsTheme, appearance)
                awaitPlayerFrame()
                onMain {
                    val activity = resumedActivity()
                    val background = activity.findViewById<View>(R.id.settingsBackgroundPanel)
                    assertSame(activity.findViewById<View>(R.id.settingsGeneralPanel), background.parent)
                    assertEquals(appearance == R.string.theme_custom, background.visibility == View.VISIBLE)
                    assertEquals(0, activity.resources.getIdentifier("categoryBackgrounds", "id", activity.packageName))
                }
                if (appearance == R.string.theme_custom) onMain {
                    app.displaySettings.update { it.copy(blur = 7) }
                }
                assertEquals(7, app.displaySettings.options.value.blur)
            }
            scenario.recreate()
            awaitPlayerFrame()
            onMain {
                assertTrue(resumedActivity().findViewById<View>(R.id.settingsBackgroundPanel).isShown)
                assertEquals(7, app.displaySettings.options.value.blur)
                assertEquals(0, resumedActivity().resources.getIdentifier("settingsLightMode", "id", app.packageName))
                val theme = resumedActivity().findViewById<TextView>(R.id.settingsTheme)
                assertEquals(android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL, theme.gravity)
            }
            screenshot("settings-general-custom-backgrounds")
        }
    }

    @Test fun selectedMainControlsGrowOnlyTheirContentAndPreserveButtonBounds() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.pause(); controller.setMediaItems(tracks.map(Track::toMediaItem)); controller.prepare() }
                await { onMainResult { controller.playbackState == Player.STATE_READY } }
                for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                    rotate(scenario, orientation)
                    val ids = listOf(R.id.shuffle, R.id.repeat, R.id.volumeMute, R.id.favorite, R.id.details)
                    val before = onMainResult { ids.associateWith { bounds(resumedActivity().findViewById(it)) } }
                    val content = onMainResult {
                        val favorite = resumedActivity().findViewById<MaterialButton>(R.id.favorite)
                        favorite.textSize to favorite.iconSize
                    }
                    val detailsSize = onMainResult { resumedActivity().findViewById<MaterialButton>(R.id.details).textSize }
                    onMain {
                        app.playbackSettings.setOrder(PlayOrder.RANDOM)
                        app.playbackSettings.setLoop(LoopMode.FOLDER)
                        app.playbackSettings.setMuted(true)
                    }
                    onView(withId(R.id.favorite)).perform(click())
                    await { onMainResult { ids.filterNot { it == R.id.details }.all {
                        resumedActivity().findViewById<View>(it).isSelected
                    } } }
                    awaitPlayerFrame()
                    onMain {
                        val activity = resumedActivity()
                        for (id in ids) {
                            val target = activity.findViewById<View>(id)
                            assertEquals("Selection must not change button width", before[id]!!.width(), target.width)
                            assertEquals("Selection must not change button height", before[id]!!.height(), target.height)
                        }
                        val favorite = activity.findViewById<MaterialButton>(R.id.favorite)
                        assertEquals(content.first * 1.5f, favorite.textSize, 0.01f)
                        assertEquals((content.second * 1.5f).roundToInt(), favorite.iconSize)
                        assertEquals(detailsSize, activity.findViewById<MaterialButton>(R.id.details).textSize, 0.01f)
                        for (id in listOf(R.id.favorite, R.id.details)) {
                            val button = activity.findViewById<MaterialButton>(id)
                            assertEquals(android.view.Gravity.CENTER, button.gravity)
                            assertEquals(if (button.text.isEmpty()) MaterialButton.ICON_GRAVITY_TEXT_START
                                else MaterialButton.ICON_GRAVITY_START, button.iconGravity)
                            if (button.text.isNotEmpty()) {
                                val textCenter = button.totalPaddingLeft +
                                    (button.layout.getLineLeft(0) + button.layout.getLineRight(0)) / 2
                                assertEquals("The label must be centered independently of the leading icon",
                                    button.width / 2f, textCenter, 1f)
                                assertEquals(button.totalPaddingLeft, button.totalPaddingRight)
                            }
                        }
                    }
                    screenshot("selected-controls-$orientation")
                    onView(withId(R.id.favorite)).perform(click())
                    onMain {
                        app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
                        app.playbackSettings.setLoop(LoopMode.OFF)
                        app.playbackSettings.setMuted(false)
                    }
                    await { onMainResult { ids.all { !resumedActivity().findViewById<View>(it).isSelected } } }
                }
                onMain {
                    val button = com.macarmusic.app.ui.PlayerIconButton(resumedActivity()).apply {
                        background = null
                        scaleType = ImageView.ScaleType.CENTER
                        setImageDrawable(android.graphics.drawable.GradientDrawable().apply {
                            setColor(Color.MAGENTA); setSize(20, 20)
                        })
                        measure(View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY))
                        layout(0, 0, 60, 60)
                    }
                    for (selected in listOf(false, true, false)) {
                        button.isSelected = selected
                        val bitmap = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888)
                        try {
                            button.draw(Canvas(bitmap))
                            val pixels = Rect()
                            for (y in 0 until 60) for (x in 0 until 60) if (Color.alpha(bitmap.getPixel(x, y)) > 0)
                                pixels.union(x, y, x + 1, y + 1)
                            assertEquals(if (selected) 30 else 20, pixels.width())
                            assertEquals(if (selected) 30 else 20, pixels.height())
                            assertEquals(30f, pixels.exactCenterX(), 0f)
                            assertEquals(30f, pixels.exactCenterY(), 0f)
                            assertEquals(60, button.width)
                            assertEquals(60, button.height)
                        } finally { bitmap.recycle() }
                    }
                }
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun allMainPageActionsKeepLeadingIconsAndIndependentlyCenteredWords() {
        fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                rotate(scenario, orientation)
                for (id in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                    onView(withId(id)).perform(click())
                    awaitPlayerFrame()
                    onMain {
                        var count = 0
                        fun inspect(view: View) {
                            if (view is com.macarmusic.app.ui.PlayerTextButton && view.isShown && view.text.isNotEmpty()) {
                                count++
                                assertEquals(0, view.scrollX)
                                for (line in 0 until view.layout.lineCount) {
                                    val center = view.totalPaddingLeft +
                                        (view.layout.getLineLeft(line) + view.layout.getLineRight(line)) / 2
                                    assertEquals(view.text.toString(), view.width / 2f, center, 1f)
                                }
                                if (view.icon != null) {
                                    assertEquals(MaterialButton.ICON_GRAVITY_START, view.iconGravity)
                                    assertEquals(view.totalPaddingLeft, view.totalPaddingRight)
                                }
                            }
                            if (view is ViewGroup) view.children.forEach(::inspect)
                        }
                        inspect(resumedActivity().findViewById(R.id.screenContainer))
                        assertTrue("Every main page should expose real action buttons", count > 0)
                    }
                }
            }
        }
    }

    @Test fun polishCustomColorEntryValidatesWithoutSavingAndKeepsPaletteBlocks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsSpectrum).perform(scrollTo(), click())
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withId(R.id.colorGrid)).check { view, _ -> assertEquals(13, (view as android.widget.GridLayout).childCount) }
            onView(withTagValue(equalTo<Any>("rainbow:0"))).perform(scrollTo(), click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.colors)
            onView(withTagValue(equalTo<Any>("custom:0"))).perform(scrollTo(), click())
            onView(withId(R.id.customColorInput)).perform(scrollTo(), replaceText("#XYZ"), closeSoftKeyboard())
            onView(withText(R.string.color_use)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.customColorLayout)).check { view, _ ->
                assertEquals(app.text(R.string.custom_color_invalid),
                    (view as com.google.android.material.textfield.TextInputLayout).error)
            }
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.colors)
            onView(withId(R.id.customColorInput)).perform(replaceText("12ab67"), closeSoftKeyboard())
            onView(withId(R.id.colorEditorPreview)).check { view, _ ->
                assertEquals(0xff12ab67.toInt(), (view.background as ColorDrawable).color)
            }
            onView(withId(R.id.colorRed)).check { view, _ -> assertEquals(18, (view as SeekBar).progress) }
            onView(withId(R.id.colorGreen)).check { view, _ -> assertEquals(171, (view as SeekBar).progress) }
            onView(withId(R.id.colorBlue)).check { view, _ -> assertEquals(103, (view as SeekBar).progress) }
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.colors)
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withTagValue(equalTo<Any>("custom:0"))).perform(scrollTo(), click())
            onView(withId(R.id.customColorInput)).perform(scrollTo(), replaceText("#12ab67"), closeSoftKeyboard())
            onView(withText(R.string.color_use)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.spectrum.colors.low == 0x12ab67 }
            assertEquals(0x12ab67, DisplaySettings(app) {}.options.value.spectrum.colors.low)
            scenario.recreate()
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withTagValue(equalTo<Any>("custom:0"))).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.colorEditSlot)).perform(scrollTo(), click())
            onView(withId(R.id.customColorInput)).check(matches(withText("#12AB67")))
            onView(withId(R.id.customColorInput)).perform(scrollTo(), replaceText("#NOTHEX"), closeSoftKeyboard())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            onView(withTagValue(equalTo<Any>("rainbow:4"))).perform(scrollTo(), click())
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.spectrum.colors.low == 0x0000ff }
            assertEquals(0x12ab67, com.macarmusic.app.data.ColorSlotStore(app).read().colors[0])
            setting(R.id.spectrumColorMiddle).perform(scrollTo(), click())
            previewCustomColor(0x2468ac)
            onView(withId(R.id.colorGrid)).check { view, _ -> assertEquals(14, (view as android.widget.GridLayout).childCount) }
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { com.macarmusic.app.data.ColorSlotStore(app).read().colors.size == 6 }
            assertEquals(0x2468ac, com.macarmusic.app.data.ColorSlotStore(app).read().colors.last())
        }
    }

    private fun previewCustomColor(color: Int) {
        onView(withId(R.id.colorAddSlot)).perform(scrollTo(), click())
        onView(withId(R.id.customColorInput)).perform(scrollTo(),
            replaceText(com.macarmusic.app.model.ColorHex.format(color)), closeSoftKeyboard())
        onView(withText(R.string.color_use)).inRoot(isDialog()).perform(click())
    }

    @Test fun polishCustomAccentPersistsAndTintsNativeControlsInBothThemes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val beforeAccent = onMainResult { resumedActivity() }
            val changingAccent = app.displaySettings.options.value.customAccentColor != 0x13579b
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsAccent).perform(scrollTo(), click())
            previewCustomColor(0x13579b)
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { onMainResult {
                app.displaySettings.options.value.customAccentColor == 0x13579b &&
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any {
                            (!changingAccent || it !== beforeAccent) && it.findViewById<View>(R.id.settingsGeneralPanel).isShown
                        }
            } }
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.MODE_NIGHT_NO)) {
                val beforeTheme = onMainResult { resumedActivity() }
                val changingTheme = app.themeMode != mode
                onMain { app.themeMode = mode }
                if (changingTheme) await { onMainResult {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any { it !== beforeTheme }
                } }
                awaitPlayerFrame()
                scenario.onActivity { activity ->
                    val rootView = activity.findViewById<View>(R.id.root)
                    assertEquals(0xff13579b.toInt(), AccentTheme.color(rootView,
                        com.google.android.material.R.attr.colorPrimary))
                    assertEquals(0xff13579b.toInt(), activity.findViewById<SeekBar>(R.id.miniSeek)
                        .thumbTintList!!.getColorForState(intArrayOf(android.R.attr.state_enabled), 0))
                    assertEquals(0xff13579b.toInt(), activity.findViewById<MaterialButton>(R.id.categoryGeneral).currentTextColor)
                    val filled = MaterialButton(activity)
                    AccentTheme.tintControls(filled)
                    AccentTheme.tintControls(filled)
                    assertTrue("Repeated tinting must preserve readable filled buttons",
                        androidx.core.graphics.ColorUtils.calculateContrast(filled.currentTextColor,
                            filled.backgroundTintList!!.getColorForState(filled.drawableState, 0)) >= 4.5)
                }
                assertEquals(0x13579b, DisplaySettings(app) {}.options.value.customAccentColor)
            }
            setting(R.id.settingsAccent).perform(scrollTo(), click())
            onView(withId(R.id.colorEditSlot)).perform(scrollTo(), click())
            onView(withId(R.id.customColorInput)).check(matches(withText("#13579B")))
            onView(withId(R.id.customColorInput)).perform(replaceText("#000001"), closeSoftKeyboard())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            assertEquals(0x13579b, app.displaySettings.options.value.customAccentColor)
            setting(R.id.settingsAccent).perform(scrollTo(), click())
            onView(withText(R.string.use_default)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.customAccentColor == null }
            assertEquals(ThemeAccent.DEFAULT, DisplaySettings(app) {}.options.value.themeAccent)
            setting(R.id.settingsAccent).perform(scrollTo(), click())
            previewCustomColor(0x90caf9)
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.customAccentColor == 0x90caf9 }
            assertEquals("Typed colors must stay exact even when they match a day/night preset",
                ThemeAccent.DEFAULT, app.displaySettings.options.value.themeAccent)
        }
    }

    @Test fun compactMiniMatchesSettingsOnEveryPageWithoutMovingNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                rotate(scenario, orientation)
                for (position in NavigationPosition.entries) {
                    onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                    awaitPlayerFrame()
                    val dock = onMainResult { bounds(resumedActivity().findViewById(R.id.navigation)) }
                    val playerBounds = onMainResult { bounds(resumedActivity().findViewById(R.id.screenContainer)) }
                    onView(withId(R.id.action_settings)).perform(click())
                    awaitPlayerFrame()
                    scenario.onActivity { activity ->
                        val mini = activity.findViewById<MiniPlayerLayout>(R.id.miniPlayer)
                        assertTrue(mini.singleRow)
                        assertEquals((48 * activity.resources.displayMetrics.density).toInt(), mini.height)
                        assertEquals(dock, bounds(activity.findViewById(R.id.navigation)))
                        val controls = listOf(R.id.miniPlay, R.id.miniNext, R.id.miniSeek, R.id.miniTime)
                            .map { bounds(activity.findViewById(it)) }
                        controls.zipWithNext().forEach { (left, right) ->
                            assertTrue(left.right <= right.left)
                            assertTrue(kotlin.math.abs(left.exactCenterY() - right.exactCenterY()) <= 1)
                        }
                        assertTrue(bounds(mini).contains(controls.first()))
                        assertTrue(bounds(mini).contains(controls.last()))
                        val categories = activity.findViewById<LinearLayout>(R.id.settingsCategories)
                        assertEquals(SettingsCategory.pages.size * 2 - 1, categories.childCount)
                        SettingsCategory.pages.forEach { category ->
                            val button = activity.findViewById<MaterialButton>(category.buttonId)
                            assertEquals(View.TEXT_ALIGNMENT_CENTER, button.textAlignment)
                            assertEquals(android.view.Gravity.CENTER, button.gravity)
                        }
                        assertTrue(activity.findViewById<View>(R.id.settingsTheme).parent ===
                            activity.findViewById<View>(R.id.settingsLanguage).parent)
                        assertEquals(SettingsCategory.pages.size - 1, categories.children.count {
                            it.id == R.id.settingsCategoryDivider && it.background is PlayerBlockDrawable
                        })
                    }
                    screenshot("polish-settings-${orientation}-${position.name.lowercase()}")
                    val geometryIds = listOf(R.id.miniPlayer, R.id.miniPlay, R.id.miniNext, R.id.miniSeek,
                        R.id.miniTime, R.id.navigation, R.id.screenContainer)
                    val settingsBounds = onMainResult {
                        geometryIds.associateWith { bounds(resumedActivity().findViewById(it)) }
                    }
                    for (page in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                        onMain { resumedActivity().findViewById<View>(page).performClick() }
                        awaitPlayerFrame()
                        scenario.onActivity { activity ->
                            assertFalse(activity.findViewById<View>(R.id.settingsPanel).isShown)
                            assertTrue(activity.findViewById<MiniPlayerLayout>(R.id.miniPlayer).singleRow)
                            assertEquals("Every browsing page must use the exact Settings playback geometry",
                                settingsBounds, geometryIds.associateWith { bounds(activity.findViewById(it)) })
                        }
                    }
                    scenario.recreate()
                    awaitPlayerFrame()
                    scenario.onActivity { activity ->
                        assertTrue(activity.findViewById<MiniPlayerLayout>(R.id.miniPlayer).singleRow)
                        assertEquals(settingsBounds, geometryIds.associateWith { bounds(activity.findViewById(it)) })
                    }
                    screenshot("compact-mini-${orientation}-${position.name.lowercase()}")
                    onMain { resumedActivity().findViewById<View>(R.id.nav_player).performClick() }
                    awaitPlayerFrame()
                    scenario.onActivity { activity ->
                        assertFalse(activity.findViewById<View>(R.id.miniPlayer).isShown)
                        assertTrue(activity.findViewById<View>(R.id.controls).isShown)
                        assertEquals("Now Playing must retain its full layout", playerBounds,
                            bounds(activity.findViewById(R.id.screenContainer)))
                    }
                }
            }
        }
    }

    @Test fun polishBrickShadowsHaveSolidEdgesAndTransparentInteriorsForEveryBorder() {
        onMain {
            for (style in BlockStyle.entries) {
                val bitmap = Bitmap.createBitmap(208, 128, Bitmap.Config.ARGB_8888)
                val drawable = PlayerBlockDrawable(BlockOutline(style, "*", 24, true), Color.CYAN, 1f)
                try {
                    drawable.setBounds(4, 4, 204, 124)
                    drawable.draw(Canvas(bitmap))
                    assertEquals(Color.TRANSPARENT, bitmap.getPixel(100, 60))
                    if (style == BlockStyle.NONE) {
                        assertTransparentBitmap(bitmap)
                        continue
                    }
                    val side = if (style == BlockStyle.CHARACTERS) 196 else 202
                    val bottom = if (style == BlockStyle.CHARACTERS) 116 else 122
                    for (y in 36..84) assertEquals(255, Color.alpha(bitmap.getPixel(side, y)))
                    for (x in 40..160) assertEquals(255, Color.alpha(bitmap.getPixel(x, bottom)))
                    val solidPixel = (36..84).first { bitmap.getPixel(side, it) == Color.rgb(0, 85, 85) }
                    for (x in 0 until bitmap.width) {
                        assertEquals(Color.TRANSPARENT, bitmap.getPixel(x, 0))
                        assertEquals(Color.TRANSPARENT, bitmap.getPixel(x, 127))
                    }
                    bitmap.eraseColor(Color.TRANSPARENT)
                    drawable.alpha = 128
                    drawable.draw(Canvas(bitmap))
                    assertEquals(128, Color.alpha(bitmap.getPixel(side, solidPixel)))
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun polishCategoryDividersRenderEveryStyleWithoutPanelFillOrShadow() {
        onMain {
            val samples = mutableListOf<Bitmap>()
            try {
                for (style in BlockStyle.entries) {
                    fun render(shadow: Boolean) = Bitmap.createBitmap(144, 16, Bitmap.Config.ARGB_8888).also {
                        PlayerBlockDrawable(BlockOutline(style, "*", 24, shadow), Color.CYAN, 1f, divider = true).apply {
                            setBounds(0, 0, 144, 16)
                            draw(Canvas(it))
                        }
                    }
                    val sample = render(false)
                    val shadow = render(true)
                    samples.add(sample)
                    try { assertTrue("Separators must not inherit panel extrusion", sample.sameAs(shadow)) }
                    finally { shadow.recycle() }
                    if (style == BlockStyle.NONE) assertTransparentBitmap(sample)
                    else assertTrue((0 until 144).any { Color.alpha(sample.getPixel(it, 7)) > 0 })
                    assertEquals(Color.TRANSPARENT, sample.getPixel(72, 15))
                    assertTrue("Each border style should produce its own divider", samples.dropLast(1).none { it.sameAs(sample) })
                }
            } finally { samples.forEach(Bitmap::recycle) }
        }
    }

    @Test fun polishSingleRowMiniMeasuresNarrowWidthsAndLargeFontsWithoutClipping() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply { fontScale = 2f }
                val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_ManaPlayer)
                val binding = com.macarmusic.app.databinding.ActivityMainBinding.inflate(android.view.LayoutInflater.from(context))
                val mini = binding.miniPlayer
                (mini.parent as ViewGroup).removeView(mini)
                mini.singleRow = true
                binding.miniTime.text = "-12:34:56 / 123:45:56"
                val density = context.resources.displayMetrics.density
                binding.miniProgress.setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                for (widthDp in listOf(1000, 240, 600, 280, 360, 1000)) {
                    val width = (widthDp * density).toInt()
                    mini.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    mini.layout(0, 0, width, mini.measuredHeight)
                    val scroll = mini.getChildAt(0) as android.widget.HorizontalScrollView
                    assertEquals((48 * density).toInt(), mini.height)
                    assertEquals(1, binding.miniTime.lineCount)
                    assertTrue(binding.miniSeek.width >= (48 * density).toInt())
                    assertTrue(binding.miniTime.width >=
                        binding.miniTime.paint.measureText(binding.miniTime.text.toString()).toInt())
                    assertEquals("Horizontal overflow must stay reachable", widthDp <= 360, scroll.canScrollHorizontally(1))
                }
                mini.singleRow = false
                assertEquals(2, mini.childCount)
                assertEquals(View.VISIBLE, binding.miniArtist.visibility)
                assertEquals(View.VISIBLE, binding.miniArtwork.visibility)
            }
        }
    }

    @Test fun fineTuningSpectrumTapCycleAndThemeLedUseOneBatch() {
        onMain { app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true, style = SpectrumStyle.LINE)) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val expected = listOf(
                Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.SMOOTH),
                Triple(SpectrumStyle.BARS, SpectrumPalette.CLASSIC, FireStyle.LED),
                Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.SMOOTH),
                Triple(SpectrumStyle.BARS, SpectrumPalette.FIRE, FireStyle.LED),
                Triple(SpectrumStyle.OFF, SpectrumPalette.FIRE, FireStyle.LED),
                Triple(SpectrumStyle.LINE, SpectrumPalette.CLASSIC, FireStyle.LED),
            )
            for ((style, palette, rendering) in expected) {
                onView(withId(R.id.spectrum)).perform(click())
                val actual = DisplaySettings(app) {}.options.value.spectrum
                assertEquals(Triple(style, palette, rendering), Triple(actual.style, actual.palette, actual.rendering))
            }
            scenario.onActivity { activity ->
                val view = SpectrumView(activity)
                val primary = com.google.android.material.color.MaterialColors.getColor(view,
                    com.google.android.material.R.attr.colorPrimary)
                view.layout(0, 0, 360, 280)
                val top = 12 * activity.resources.displayMetrics.scaledDensity + 8 * activity.resources.displayMetrics.density
                val bottom = 280 - top
                val step = (bottom - top) / 20
                for (style in FireStyle.entries) {
                    view.configure(SpectrumOptions(enabled = true, palette = SpectrumPalette.THEME, barStyle = style,
                        bands = 16, peaks = false))
                    view.setPlaybackActive(false)
                    view.setPlaybackActive(true)
                    view.submit(SpectrumFrame(FloatArray(32).apply { this[0] = 1f }, true, 44_100))
                    val bitmap = Bitmap.createBitmap(360, 280, Bitmap.Config.ARGB_8888)
                    var batches = 0
                    try {
                        view.draw(object : Canvas(bitmap) {
                            override fun drawVertices(mode: VertexMode, vertexCount: Int, verts: FloatArray, vertOffset: Int,
                                texs: FloatArray?, texOffset: Int, colors: IntArray?, colorOffset: Int,
                                indices: ShortArray?, indexOffset: Int, indexCount: Int, paint: android.graphics.Paint,
                            ) {
                                batches++
                                super.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors,
                                    colorOffset, indices, indexOffset, indexCount, paint)
                            }
                        })
                        assertEquals(if (Build.VERSION.SDK_INT >= 29) 1 else 0, batches)
                        for (row in 1 until 19) {
                            assertEquals(primary, bitmap.getPixel(10, (bottom - row * step - step * 0.4f).toInt()))
                            val gap = bitmap.getPixel(10, (bottom - row * step + step * 0.1f).toInt())
                            assertEquals(if (style == FireStyle.LED) 0 else 255, Color.alpha(gap))
                        }
                    } finally { bitmap.recycle(); view.setPlaybackActive(false) }
                }
            }
        }
    }

    @Test fun refinementSpectrumOffStopsCaptureAndCanBeTappedBackOn() {
        val tracks = fixtureTracks()
        val future = onMainResult {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            onMain {
                app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true,
                    style = SpectrumStyle.BARS, palette = SpectrumPalette.FIRE, fireStyle = FireStyle.LED)) }
                controller.setMediaItems(tracks.map(Track::toMediaItem))
                controller.prepare()
                controller.play()
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { controller.isPlaying && app.spectrum.frames.value.hasAudio }
                onView(withId(R.id.spectrum)).perform(click())
                await { app.displaySettings.options.value.spectrum.style == SpectrumStyle.OFF &&
                    !app.spectrum.frames.value.hasAudio }
                val capturing = app.spectrum.javaClass.getDeclaredField("capturing").apply { isAccessible = true }
                assertFalse(capturing.getBoolean(app.spectrum))
                val stopped = app.spectrum.frames.value
                SystemClock.sleep(1_200)
                assertSame(stopped, app.spectrum.frames.value)
                scenario.onActivity { activity ->
                    val spectrum = activity.findViewById<SpectrumView>(R.id.spectrum)
                    assertTrue(spectrum.isShown)
                    assertFalse(spectrum.javaClass.getDeclaredField("redrawPosted").apply { isAccessible = true }.getBoolean(spectrum))
                    val actual = Bitmap.createBitmap(spectrum.width, spectrum.height, Bitmap.Config.ARGB_8888)
                    val expected = Bitmap.createBitmap(spectrum.width, spectrum.height, Bitmap.Config.ARGB_8888)
                    try {
                        spectrum.draw(Canvas(actual))
                        spectrum.background?.draw(Canvas(expected))
                        val logo = requireNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, R.drawable.ic_launcher))
                        val size = minOf(spectrum.width - spectrum.paddingLeft - spectrum.paddingRight,
                            spectrum.height - spectrum.paddingTop - spectrum.paddingBottom,
                            (96 * spectrum.resources.displayMetrics.density).roundToInt())
                        val left = spectrum.paddingLeft + (spectrum.width - spectrum.paddingLeft - spectrum.paddingRight - size) / 2
                        val top = spectrum.paddingTop + (spectrum.height - spectrum.paddingTop - spectrum.paddingBottom - size) / 2
                        logo.setBounds(left, top, left + size, top + size)
                        logo.draw(Canvas(expected))
                        spectrum.foreground?.draw(Canvas(expected))
                        assertTrue("Off must show the actual program icon rather than text", actual.sameAs(expected))
                    } finally { actual.recycle(); expected.recycle() }
                    assertTrue(controller.isPlaying)
                }
                onView(withId(R.id.spectrum)).perform(click())
                await { app.displaySettings.options.value.spectrum.style == SpectrumStyle.LINE &&
                    app.spectrum.frames.value.hasAudio }
                assertTrue(capturing.getBoolean(app.spectrum))
            }
        } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
    }

    @Test fun cacheExpiryChoicesKeepWholeTextWithBordersAndLargeFonts() {
        onMain {
            for (language in listOf("en", "zh")) for (scale in listOf(1f, 1.3f, 2f)) {
                val configuration = Configuration(app.resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(language))
                    fontScale = scale
                }
                val context = ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.Theme_ManaPlayer)
                val root = View.inflate(context, R.layout.view_settings, null) as ViewGroup
                val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val labels = listOf(context.getString(R.string.lyrics_cache_expiry_never),
                    context.resources.getQuantityString(R.plurals.lyrics_cache_expiry_days, 1, 1),
                    context.resources.getQuantityString(R.plurals.lyrics_cache_expiry_days, 90, 90))
                val selectors = listOf(R.id.lyricsCacheInternalExpiry, R.id.lyricsCachePortableExpiry).map { id ->
                    root.findViewById<Spinner>(id).also { spinner ->
                        (spinner.parent as ViewGroup).removeView(spinner)
                        container.addView(spinner)
                        spinner.adapter = ArrayAdapter(context, R.layout.row_choice, labels).apply {
                            setDropDownViewResource(R.layout.row_choice)
                        }
                    }
                }
                val styler = BlockButtonStyler(container)
                for (style in listOf<BlockStyle?>(null) + BlockStyle.selectable) {
                    styler.apply(style != null, BlockOutline(style ?: BlockStyle.SOLID))
                    for (index in labels.indices) {
                        selectors.forEach { it.setSelection(index, false); it.requestLayout() }
                        container.measure(
                            View.MeasureSpec.makeMeasureSpec((320 * context.resources.displayMetrics.density).roundToInt(),
                                View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        )
                        container.layout(0, 0, container.measuredWidth, container.measuredHeight)
                        for (spinner in selectors) {
                            val text = spinner.selectedView as TextView
                            val layout = requireNotNull(text.layout)
                            val available = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
                            val description = "language=$language font=$scale border=$style text=${labels[index]}"
                            assertTrue("$description: text needs ${layout.height}px but has $available",
                                available >= layout.height)
                            assertTrue("$description: selected row is clipped by its spinner",
                                text.top >= spinner.paddingTop &&
                                    text.bottom <= spinner.height - spinner.paddingBottom)
                        }
                    }
                }
            }
        }
    }

    @Test fun cacheInspectionReportsFreshEmptyAndPopulatedResults() {
        val oldExpiry = app.displaySettings.options.value.internalLyricExpiry
        val directory = File(app.cacheDir, "lyrics")
        val internal = InternalLyricCache(directory, System::currentTimeMillis)
        val original = File(directory, "inspection-original.lrc")
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        onMain { app.displaySettings.update { it.copy(internalLyricExpiry = com.macarmusic.app.model.LyricCacheExpiry.NEVER) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.action_settings)).perform(click())
                selectCategory(SettingsCategory.LYRICS)
                await { !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
                app.notice.value?.let { app.dismissNotice(it.id) }
                for (count in listOf(0L, 2L, 2L)) {
                    if (count > 0 && internal.keys().isEmpty()) {
                        internal.write("a".repeat(64), CachedLyric("[00:01]Synthetic first cache entry", LyricOrigin.QQ))
                        internal.write("b".repeat(64), CachedLyric("[00:01]Synthetic second cache entry", LyricOrigin.KUGOU))
                        original.writeText("[00:01]Synthetic original sidecar")
                    }
                    val expectedSize = internal.size()
                    assertEquals(count, expectedSize.files)
                    val expected = app.text(R.string.lyrics_cache_size, count,
                        android.text.format.Formatter.formatFileSize(app, expectedSize.bytes))
                    val previous = app.notice.value?.id ?: 0L
                    setting(R.id.lyricsCacheInspect).perform(scrollTo(), click())
                    try {
                        await(5_000) { app.notice.value?.let { it.id > previous && !it.isError && it.text == expected } == true }
                    } catch (error: AssertionError) {
                        throw AssertionError("Manual cache inspection did not show a fresh size result: $expected", error)
                    }
                    await { !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
                    onView(withId(R.id.noticeText)).check(matches(withText(expected)))
                    onView(withId(R.id.noticeBar)).check(matches(isDisplayed()))
                    assertEquals(expectedSize, app.lyrics.cache.info.value.size)
                    if (count > 0) assertTrue(original.isFile)
                }
                screenshot("cache-inspection-visible-result")
                app.notice.value?.let { app.dismissNotice(it.id) }
                onMain { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].inspectLyricCache() }
                await { !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
                assertNull("Automatic inspection should stay quiet on success", app.notice.value)
                scenario.recreate()
                selectCategory(SettingsCategory.LYRICS)
                assertEquals(com.macarmusic.app.model.LyricCacheExpiry.NEVER,
                    app.displaySettings.options.value.internalLyricExpiry)
            }
        } finally {
            runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
            if (original.exists()) assertTrue(original.delete())
            onMain { app.displaySettings.update { it.copy(internalLyricExpiry = oldExpiry) } }
        }
    }

    @Test fun systemInformationShowsReportedApiAndBuildInBothLanguages() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem))
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.prepare()
                    controller.play()
                }
                await { controller.isPlaying }
                onView(withId(R.id.action_settings)).perform(click())
                for ((language, label) in listOf("zh" to R.string.language_chinese, "en" to R.string.language_english)) {
                    chooseSetting(R.id.settingsLanguage, label)
                    setting(R.id.settingsSystemInfo).perform(scrollTo(), click())
                    onView(withText(R.string.system_info)).inRoot(isDialog()).check(matches(isDisplayed()))
                    onView(withId(android.R.id.message)).inRoot(isDialog()).check { view, error ->
                        if (error != null) throw error
                        val text = view as TextView
                        assertTrue(text.isTextSelectable)
                        fun reported(value: String?) = value?.takeIf(String::isNotBlank)
                            ?: text.context.getString(R.string.system_info_unreported)
                        assertEquals(text.context.getString(R.string.system_info_details,
                            reported(Build.VERSION.RELEASE), Build.VERSION.SDK_INT, reported(Build.VERSION.SECURITY_PATCH),
                            reported(Build.MANUFACTURER), reported(Build.MODEL), reported(Build.DISPLAY),
                            reported(System.getProperty("os.version"))), text.text.toString())
                    }
                    assertTrue(onMainResult { controller.isPlaying })
                    screenshot("system-info-$language")
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                    onMain {
                        assertTrue(resumedActivity().findViewById<View>(R.id.settingsSystemInfo).foreground is PlayerBlockDrawable)
                        assertEquals(1, resumedActivity()
                            .findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu.size())
                    }
                    if (language == "zh") {
                        chooseDropdown(R.id.lyricsCacheInternalExpiry, app.text(R.string.lyrics_cache_expiry_never))
                        chooseDropdown(R.id.lyricsCachePortableExpiry, app.text(R.string.lyrics_cache_expiry_never))
                        onMain {
                            val activity = resumedActivity()
                            val selector = activity.findViewById<View>(R.id.lyricsCacheInternalExpiry)
                            activity.findViewById<ScrollView>(R.id.settingsScroll).scrollTo(0,
                                (selector.top - 48 * activity.resources.displayMetrics.density).roundToInt().coerceAtLeast(0))
                        }
                        screenshot("cache-expiry-zh")
                    }
                }
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun refinementBordersAboutAndExpirySettingsSurviveRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            chooseDropdown(R.id.lyricsCacheInternalExpiry, app.text(R.string.lyrics_cache_expiry_never))
            await { !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
            chooseDropdown(R.id.lyricsCachePortableExpiry,
                app.resources.getQuantityString(R.plurals.lyrics_cache_expiry_days, 7, 7))
            scenario.recreate()
            val restored = DisplaySettings(app) {}.options.value
            assertEquals(com.macarmusic.app.model.LyricCacheExpiry.NEVER, restored.internalLyricExpiry)
            assertEquals(com.macarmusic.app.model.LyricCacheExpiry.SEVEN_DAYS, restored.portableLyricExpiry)
            selectCategory(SettingsCategory.ABOUT)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.settingsHeading).visibility)
                assertTrue(activity.findViewById<View>(R.id.settingsAboutPanel).foreground is PlayerBlockDrawable)
                assertAboutBranding(activity)
            }
            screenshot("update118-about-bordered")
            for (page in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue)) {
                onMain { resumedActivity().findViewById<View>(page).performClick() }
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].selectedScreen.value == page }
                scenario.onActivity { activity ->
                    val progress = activity.findViewById<View>(R.id.miniProgress)
                    assertTrue(progress.isShown)
                    assertTrue(progress.background is PlayerBlockDrawable)
                    assertTrue(activity.findViewById<View>(R.id.miniSeek).height >=
                        (48 * activity.resources.displayMetrics.density).toInt())
                }
            }
            onMain { app.displaySettings.update { it.copy(controlBlocks = false) } }
            await { resumedActivity().findViewById<View>(R.id.miniProgress).background == null }
            onView(withId(R.id.action_settings)).perform(click())
            selectCategory(SettingsCategory.ABOUT)
            scenario.onActivity { activity ->
                assertNull(activity.findViewById<View>(R.id.settingsAboutPanel).foreground)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.settingsHeading).visibility)
            }
        }
    }

    @Test fun fineTuningAccentAndBorderEffectsKeepNavigationAvailableInSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsAccent).perform(scrollTo(), click())
            previewCustomColor(0x90caf9)
            onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
            await { resumedActivity().findViewById<View>(R.id.settingsAccent).isShown &&
                app.displaySettings.options.value.customAccentColor == 0x90caf9 &&
                AccentTheme.color(resumedActivity().findViewById(R.id.root),
                    com.google.android.material.R.attr.colorPrimary) == 0xff90caf9.toInt() }
            scenario.onActivity { activity ->
                val rootView = activity.findViewById<View>(R.id.root)
                assertEquals(0xff90caf9.toInt(),
                    AccentTheme.color(rootView,
                        com.google.android.material.R.attr.colorPrimary))
                assertEquals(1, activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).menu.size())
            }
            chooseDropdown(R.id.settingsBlockStyle, app.text(R.string.block_dashed_arc))
            setting(R.id.blockArc).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                { seekCoordinates(it, 0.2f) }, { seekCoordinates(it, 1f) }, Press.FINGER))
            setting(R.id.blockArc).perform(androidx.test.espresso.action.GeneralClickAction(
                androidx.test.espresso.action.Tap.SINGLE, { seekCoordinates(it, 1f) }, Press.FINGER))
            setting(R.id.settingsBlockShadow).perform(scrollTo(), click())
            assertEquals(BlockOutline(BlockStyle.DASHED_ARC, "*", 48, true), DisplaySettings(app) {}.options.value.blockOutline)
            scenario.recreate()
            for (position in NavigationPosition.entries) {
                val label = when (position) {
                    NavigationPosition.TOP -> R.string.navigation_top
                    NavigationPosition.BOTTOM -> R.string.navigation_bottom
                    NavigationPosition.LEFT -> R.string.navigation_left
                    NavigationPosition.RIGHT -> R.string.navigation_right
                }
                chooseDropdown(R.id.settingsNavigationPosition, app.text(label))
                scenario.onActivity { activity ->
                    val dock = activity.findViewById<View>(R.id.navigation)
                    assertTrue(dock.isShown)
                    assertFalse(Rect.intersects(bounds(dock), bounds(activity.findViewById(R.id.settingsPanel))))
                    assertFalse(Rect.intersects(bounds(dock), bounds(activity.findViewById(R.id.miniPlayer))))
                    assertTrue("Mini-player outlines must have the same safe control gap",
                        bounds(activity.findViewById(R.id.miniNext)).left -
                            bounds(activity.findViewById(R.id.miniPlay)).right >=
                            activity.resources.getDimensionPixelSize(R.dimen.player_gap))
                }
                onMain { resumedActivity().findViewById<View>(R.id.nav_library).performClick() }
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].selectedScreen.value == R.id.nav_library }
                onView(withId(R.id.action_settings)).perform(click())
            }
            screenshot("fine-accent-dashed-arc-settings")
        }
        onMain {
            fun render(radius: Int, shadow: Boolean) = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888).also {
                PlayerBlockDrawable(BlockOutline(BlockStyle.DASHED_ARC, "*", radius, shadow), Color.CYAN, 1f).apply {
                    setBounds(0, 0, 200, 120)
                    draw(Canvas(it))
                }
            }
            val square = render(0, false)
            val curved = render(44, false)
            val shadowed = render(44, true)
            try {
                assertFalse(square.sameAs(curved))
                assertFalse(curved.sameAs(shadowed))
                assertEquals(Color.TRANSPARENT, shadowed.getPixel(100, 60))
                assertEquals("The right side must be solid, not a second dashed outline",
                    Color.rgb(0, 85, 85), shadowed.getPixel(198, 60))
                assertEquals(Color.rgb(0, 85, 85), shadowed.getPixel(100, 118))
            } finally { square.recycle(); curved.recycle(); shadowed.recycle() }
        }
    }

    @Test fun lyricCacheSettingsConfirmActionsPreservePlaybackAndSurviveRotation() {
        val cacheTree = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.cache", "root")
        await { app.checkUriPermission(cacheTree, Process.myPid(), Process.myUid(),
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED }
        app.contentResolver.call(cacheTree, "cacheFixture", null, Bundle().apply { putBoolean("reset", true) })
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        val key = "1".repeat(64)
        val record = CachedLyric("[00:01]Synthetic UI cache lyric", LyricOrigin.QQ, System.currentTimeMillis())
        val internal = InternalLyricCache(File(app.cacheDir, "lyrics"), System::currentTimeMillis)
        internal.write(key, record)
        val tracks = fixtureTracks()
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        val monitor = android.app.Instrumentation.ActivityMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(cacheTree).addFlags(flags)), true)
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    onMain {
                        controller.setMediaItems(tracks.map(Track::toMediaItem))
                        controller.repeatMode = Player.REPEAT_MODE_ONE
                        controller.prepare()
                        controller.play()
                    }
                    await { controller.isPlaying }
                    onView(withId(R.id.action_settings)).perform(click())
                    setting(R.id.lyricsCacheInspect).perform(scrollTo(), click())
                    await { app.lyrics.cache.info.value.size?.files == 1L }
                    setting(R.id.lyricsCacheClear).perform(scrollTo(), click())
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    assertEquals(1L, runBlocking { app.lyrics.cache.inspect().files })
                    setting(R.id.lyricsCacheClear).perform(scrollTo(), click())
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                    await { app.lyrics.cache.info.value.size?.files == 0L && !app.lyrics.cache.info.value.busy }
                    assertTrue(onMainResult { controller.isPlaying })
                    internal.write(key, record)
                    setting(R.id.lyricsCacheMove).perform(scrollTo(), click())
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    assertEquals(0, monitor.hits)
                    setting(R.id.lyricsCacheMove).perform(scrollTo(), click())
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                    await { app.lyrics.cache.info.value.folder != null && !app.lyrics.cache.info.value.busy }
                    assertEquals(1, monitor.hits)
                    assertTrue(internal.keys().isEmpty())
                    assertEquals(record, runBlocking { app.lyrics.cache.read(key).value })
                    screenshot("cache-portable-landscape")
                    val folder = app.lyrics.cache.info.value.folder!!
                    scenario.recreate()
                    rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    await { resumedActivity().findViewById<View>(R.id.settingsLyricsPanel).isShown }
                    scenario.onActivity { activity ->
                        for (id in listOf(R.id.lyricsCacheClear, R.id.lyricsCacheMove, R.id.lyricsCacheInternal, R.id.lyricsCacheInspect)) {
                            assertNotNull("Cache buttons follow the shared block setting", activity.findViewById<View>(id).foreground)
                        }
                    }
                    setting(R.id.lyricsCacheLocation).check(matches(withText(containsString(folder.name))))
                    val previousNotice = app.notice.value?.id ?: 0L
                    setting(R.id.lyricsCacheInspect).perform(scrollTo(), click())
                    await { app.lyrics.cache.info.value.size?.files == 1L &&
                        app.notice.value?.let { it.id > previousNotice && !it.isError } == true &&
                        !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
                    val portableSize = requireNotNull(app.lyrics.cache.info.value.size)
                    val expectedNotice = app.text(R.string.lyrics_cache_size, portableSize.files,
                        android.text.format.Formatter.formatFileSize(app, portableSize.bytes))
                    assertEquals(expectedNotice, app.notice.value?.text)
                    onView(withId(R.id.noticeText)).check(matches(withText(expectedNotice)))
                    screenshot("cache-portable-portrait")
                    app.contentResolver.call(cacheTree, "cacheFixture", null, Bundle().apply { putBoolean("unavailable", true) })
                    setting(R.id.lyricsCacheInspect).perform(scrollTo(), click())
                    await { app.lyrics.cache.info.value.unavailable && app.notice.value?.isError == true &&
                        !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].lyricCacheBusy.value }
                    assertTrue(onMainResult { controller.isPlaying })
                    assertEquals(folder, app.lyrics.cache.info.value.folder)
                    setting(R.id.lyricsCacheInternal).perform(scrollTo(), click())
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    assertEquals(folder, app.lyrics.cache.info.value.folder)
                    setting(R.id.lyricsCacheInternal).perform(scrollTo(), click())
                    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                    await { app.lyrics.cache.info.value.internal && !app.lyrics.cache.info.value.busy }
                    app.contentResolver.call(cacheTree, "cacheFixture", null, Bundle())
                    assertEquals(record, SafLyricCache(app.contentResolver, folder).read(key))
                    assertTrue(onMainResult { controller.isPlaying })
                } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            app.contentResolver.call(cacheTree, "cacheFixture", null, Bundle())
            runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
            app.contentResolver.call(cacheTree, "cacheFixture", null, Bundle().apply { putBoolean("reset", true) })
        }
    }

    @Test fun fineTuningLyricPositionsColorsAndCacheLocationWorkInBothOrientations() {
        lyricMode("scrollable")
        val tracks = fixtureTracks()
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 20_000); controller.prepare(); controller.pause() }
                await { controller.playbackState == Player.STATE_READY && lyricList().adapter?.itemCount == 30 }
                onView(withId(R.id.action_settings)).perform(click())
                chooseDropdown(R.id.lyricsLandscapePosition, app.text(R.string.position_right))
                chooseDropdown(R.id.lyricsPortraitPosition, app.text(R.string.navigation_bottom))
                setting(R.id.lyricActiveColor).perform(scrollTo(), click())
                previewCustomColor(0xffdf69)
                onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
                setting(R.id.lyricOtherColor).perform(scrollTo(), click())
                previewCustomColor(0xffffff)
                onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
                setting(R.id.lyricActiveSize).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.1f) }, { seekCoordinates(it, 0.5f) }, Press.FINGER))
                // Tap inside the step: Android 6 truncates touch-derived progress instead of rounding it.
                setting(R.id.lyricActiveSize).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE,
                    { seekCoordinates(it, 0.5f + 0.25f / (it as SeekBar).max) }, Press.FINGER))
                assertEquals(30, app.displaySettings.options.value.lyricAppearance.activeSizeSp)
                setting(R.id.lyricsCacheLocation).check(matches(withText(containsString(File(app.cacheDir, "lyrics").absolutePath))))
                onView(withId(R.id.nav_player)).perform(click())
                await { lyricList().children.any { it.isSelected } }
                scenario.onActivity { activity ->
                    assertTrue(bounds(activity.findViewById(R.id.cover)).left >= bounds(activity.findViewById(R.id.controls)).right)
                    val active = lyricList().children.filterIsInstance<TextView>().single { it.isSelected }
                    assertEquals(0xffffdf69.toInt(), active.currentTextColor)
                    val adapter = lyricList().adapter as LyricsAdapter
                    val other = adapter.createViewHolder(lyricList(), 0).also { adapter.bindViewHolder(it, 0) }.text
                    assertEquals(Color.WHITE, other.currentTextColor)
                    assertEquals(1f, other.alpha, 0f)
                }
                screenshot("fine-lyrics-right")
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                scenario.onActivity { activity ->
                    assertTrue(bounds(activity.findViewById(R.id.cover)).top >= bounds(activity.findViewById(R.id.controls)).bottom)
                }
                screenshot("fine-lyrics-bottom")
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsLyrics).perform(scrollTo(), click())
                onView(withId(R.id.nav_player)).perform(click())
                await { resumedActivity().findViewById<View>(R.id.lyricsList).visibility == View.GONE }
                onView(withId(R.id.artwork)).check(matches(isDisplayed()))
                assertEquals(20_000, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun fineTuningTransportSeeksWithinTheSongAndQueueScrollsToCurrentOccurrence() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(List(100) { tracks[it % tracks.size].toMediaItem() }, 76, 20_000)
                    controller.prepare(); controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY && resumedActivity().findViewById<View>(R.id.forward).isEnabled }
                onView(withId(R.id.rewind)).perform(scrollTo(), click())
                await { controller.currentPosition == 10_000L }
                onView(withId(R.id.forward)).perform(scrollTo(), click())
                await { controller.currentPosition == 20_000L }
                onMain { controller.seekTo(2_000) }
                onView(withId(R.id.rewind)).perform(scrollTo(), click())
                await { controller.currentPosition == 0L }
                onMain { controller.seekTo(controller.duration - 2_000) }
                onView(withId(R.id.forward)).perform(scrollTo(), click())
                await { controller.currentPosition == controller.duration - 1 }
                assertEquals(76, onMainResult { controller.currentMediaItemIndex })
                assertFalse(onMainResult { controller.playWhenReady })
                onMain { controller.seekTo(20_000); controller.play() }
                await { controller.isPlaying }
                onView(withId(R.id.forward)).perform(scrollTo(), click())
                await { controller.currentPosition in 29_900..34_000 && controller.isPlaying }
                onMain { controller.pause() }
                onView(withId(R.id.nav_queue)).perform(click())
                await {
                    resumedActivity().findViewById<RecyclerView>(R.id.queueList)
                        .findViewHolderForAdapterPosition(76)?.itemView?.isShown == true
                }
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.queueList)
                    val rows = (list.adapter as com.macarmusic.app.ui.MusicRowAdapter).currentList
                    assertTrue(rows[76].active)
                    assertEquals(1, rows.count { it.active })
                    assertTrue(Rect.intersects(bounds(list), bounds(list.findViewHolderForAdapterPosition(76)!!.itemView)))
                }
                screenshot("fine-queue-current-occurrence")
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun fineTuningNoticesExpireAfterTenSecondsWithoutClosingNewerNotices() {
        ActivityScenario.launch(MainActivity::class.java).use {
            await { resumedActivity().hasWindowFocus() }
            app.inform("First operation")
            val first = requireNotNull(app.notice.value)
            SystemClock.sleep(1_500)
            app.report("New operation")
            app.dismissNotice(first.id)
            SystemClock.sleep(8_600)
            assertEquals("New operation", app.notice.value?.text)
            onView(withId(R.id.noticeText)).check(matches(withText("New operation")))
            await(4_000) { app.notice.value == null && resumedActivity().findViewById<View>(R.id.noticeBar).visibility == View.GONE }
            app.inform("Manual dismissal")
            onView(withId(R.id.dismissNotice)).perform(click())
            assertNull(app.notice.value)
        }
    }

    @Test fun queueSelectionAndNextKeepTheScrolledAnchorAndCompactOptionsWork() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun list() = resumedActivity().findViewById<RecyclerView>(R.id.queueList)
            fun manager() = list().layoutManager as androidx.recyclerview.widget.LinearLayoutManager
            fun anchor() = onMainResult {
                val position = manager().findFirstVisibleItemPosition()
                position to checkNotNull(manager().findViewByPosition(position)).top
            }
            fun rows() = onMainResult { (list().adapter as com.macarmusic.app.ui.MusicRowAdapter).currentList }
            try {
                onMain {
                    controller.setMediaItems(List(100) { tracks[it % tracks.size].toMediaItem() }, 76, 20_000)
                    controller.prepare()
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_READY }
                onView(withId(R.id.nav_queue)).perform(click())
                await { rows().size == 100 && anchor().first == 76 }
                onMain { manager().scrollToPositionWithOffset(24, -11) }
                await { anchor() == (24 to -11) }
                val before = anchor()
                val keys = rows().map { row -> row.key }
                onView(withId(R.id.queueList)).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE, { view ->
                        val row = checkNotNull((view as RecyclerView).findViewHolderForAdapterPosition(26)).itemView
                        val rect = bounds(row)
                        floatArrayOf(rect.left + rect.width() * 0.3f, rect.exactCenterY())
                    }, Press.FINGER))
                await { controller.currentMediaItemIndex == 26 && controller.isPlaying && rows()[26].active }
                instrumentation.waitForIdleSync()
                assertEquals("Selecting a song must preserve the first visible row and its pixel offset", before, anchor())
                assertEquals(keys, rows().map { row -> row.key })
                assertEquals(1, rows().count { row -> row.active })
                screenshot("queue-selected-without-scrolling")
                onView(withId(R.id.miniNext)).perform(click())
                await { controller.currentMediaItemIndex == 27 && rows()[27].active }
                instrumentation.waitForIdleSync()
                assertEquals("Playback transitions must not move a browsed Queue", before, anchor())
                onView(withId(R.id.nav_library)).perform(click())
                onView(withId(R.id.nav_queue)).perform(click())
                await { anchor() == (27 to 0) }
                onView(withId(R.id.queueList)).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE, { view ->
                        val row = checkNotNull((view as RecyclerView).findViewHolderForAdapterPosition(27)).itemView
                        val rect = bounds(row.findViewById(R.id.more))
                        floatArrayOf(rect.exactCenterX(), rect.exactCenterY())
                    }, Press.FINGER))
                onView(withText(R.string.remove_queue)).inRoot(isDialog()).perform(click())
                await { controller.mediaItemCount == 99 && rows().size == 99 }
                assertEquals(2, app.repository.library.value.tracks.size)
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun compactQueueRowsKeepInlineTypographyColorsAndOtherListsUnchanged() {
        onMain { app.displaySettings.update { it.copy(customAccentColor = 0xff9800) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                fun paintAt(view: TextView, offset: Int): android.text.TextPaint {
                    val paint = android.text.TextPaint(view.paint).apply { color = view.currentTextColor }
                    (view.text as? android.text.Spanned)?.getSpans(offset, offset + 1,
                        android.text.style.CharacterStyle::class.java)?.forEach { it.updateDrawState(paint) }
                    return paint
                }
                val samples = listOf(
                    com.macarmusic.app.ui.MusicRow("active", "测试歌曲 - Sample", "Fixture artist · Fixture album", "4:24", active = true),
                    com.macarmusic.app.ui.MusicRow("long", "A long song title ".repeat(12), "Artist ".repeat(20), "4:24"),
                    com.macarmusic.app.ui.MusicRow("empty", "Song without tags", "", "4:24"),
                )
                for (night in listOf(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO)) {
                    for (scale in listOf(1f, 2f)) {
                        val configuration = Configuration(activity.resources.configuration).apply {
                            fontScale = scale
                            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                        }
                        val context = ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.Theme_ManaPlayer)
                        val parent = android.widget.FrameLayout(context)
                        val queue = com.macarmusic.app.ui.MusicRowAdapter({}, {}, compact = true)
                        val ordinary = com.macarmusic.app.ui.MusicRowAdapter({}, {})
                        queue.submitList(samples.map { it.copy(inlineSubtitle = true) })
                        ordinary.submitList(samples)
                        val dense = queue.createViewHolder(parent, queue.getItemViewType(0))
                        val original = ordinary.createViewHolder(parent, 0)
                        val density = context.resources.displayMetrics.density
                        for (outline in listOf(BlockOutline(), BlockOutline(BlockStyle.DASHED_ARC, "*", 18, true),
                            BlockOutline(BlockStyle.CHARACTERS, "+"))) {
                            queue.configureBlocks(true, outline)
                            for (index in samples.indices) {
                                queue.onBindViewHolder(dense, index)
                                ordinary.onBindViewHolder(original, index)
                                val sample = samples[index]
                                val expected = sample.title + if (sample.subtitle.isEmpty()) "" else "  " + sample.subtitle
                                assertEquals(expected, dense.binding.title.text.toString())
                                assertEquals(View.GONE, dense.binding.subtitle.visibility)
                                assertEquals(sample.title, original.binding.title.text.toString())
                                assertEquals(if (sample.subtitle.isEmpty()) View.GONE else View.VISIBLE, original.binding.subtitle.visibility)
                                val title = paintAt(dense.binding.title, 0)
                                assertEquals(original.binding.title.textSize, title.textSize, 0.01f)
                                assertEquals(original.binding.title.currentTextColor, title.color)
                                assertTrue(title.typeface.isBold)
                                if (sample.subtitle.isNotEmpty()) {
                                    val subtitle = paintAt(dense.binding.title, sample.title.length + 2)
                                    assertEquals(original.binding.subtitle.textSize, subtitle.textSize, 0.01f)
                                    assertEquals(original.binding.subtitle.currentTextColor, subtitle.color)
                                    assertEquals(original.binding.subtitle.typeface.style, subtitle.typeface.style)
                                }
                                for (widthDp in listOf(280, 600, 1000)) {
                                    val width = (widthDp * density).roundToInt()
                                    dense.itemView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                                    dense.itemView.layout(0, 0, width, dense.itemView.measuredHeight)
                                    assertEquals(1, dense.binding.title.lineCount)
                                    val touch = (48 * density).toInt()
                                    assertEquals(touch, dense.itemView.minimumHeight)
                                    if (scale == 1f) assertEquals(touch, dense.itemView.height)
                                    else assertTrue(dense.itemView.height >= touch)
                                    assertTrue(dense.binding.title.height >= dense.binding.title.layout.getLineBottom(0) +
                                        dense.binding.title.compoundPaddingTop + dense.binding.title.compoundPaddingBottom)
                                    assertEquals((48 * density).toInt(), dense.binding.more.width)
                                    assertEquals((48 * density).toInt(), dense.binding.more.height)
                                    assertEquals((8 * density).toInt(), dense.binding.more.left - dense.binding.detail.right)
                                    assertTrue(dense.binding.more.right <= dense.itemView.width)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun playlistSongRowsMatchQueueAndKeepPlaylistActionsAndGroupRows() {
        val tracks = fixtureTracks().map { it.copy(artist = "Fixture artist", album = "Fixture album") }
        val playlist = LocalPlaylist("compact-playlist", "Compact mix", tracks.map { it.id })
        runBlocking {
            app.repository.update { LibraryEdits.upsertTracks(it, tracks).copy(playlists = listOf(playlist)) }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            fun list() = resumedActivity().findViewById<RecyclerView>(R.id.playlistList)
            fun rows() = (list().adapter as com.macarmusic.app.ui.MusicRowAdapter).currentList
            fun groupRow() {
                onView(withId(R.id.playlistList)).check { view, missing ->
                    if (missing != null) throw missing
                    val row = (view as RecyclerView).findViewHolderForAdapterPosition(0)!!.itemView
                    assertFalse(rows().single().inlineSubtitle)
                    assertEquals(View.VISIBLE, row.findViewById<View>(R.id.subtitle).visibility)
                    assertFalse(row.findViewById<TextView>(R.id.title).text is android.text.Spanned)
                }
            }
            fun songRows(count: Int) {
                await { rows().size == count && list().findViewHolderForAdapterPosition(count - 1) != null }
                onView(withId(R.id.playlistList)).check { view, missing ->
                    if (missing != null) throw missing
                    val songs = view as RecyclerView
                    val density = songs.resources.displayMetrics.density
                    assertTrue(rows().all { it.inlineSubtitle })
                    for (row in songs.children) {
                        val title = row.findViewById<TextView>(R.id.title)
                        val subtitle = row.findViewById<TextView>(R.id.subtitle)
                        val more = row.findViewById<View>(R.id.more)
                        assertEquals((48 * density).toInt(), row.height)
                        assertEquals(View.GONE, subtitle.visibility)
                        assertTrue(title.text is android.text.Spanned)
                        assertTrue(title.text.toString().endsWith("Fixture artist · Fixture album"))
                        assertEquals(1, title.lineCount)
                        assertEquals((8 * density).toInt(), more.left - row.findViewById<View>(R.id.detail).right)
                        assertEquals((48 * density).toInt(), more.width)
                        assertEquals((48 * density).toInt(), more.height)
                    }
                }
            }
            try {
                onMain { controller.setMediaItems(tracks.map(Track::toMediaItem)); controller.prepare(); controller.pause() }
                await { controller.playbackState == Player.STATE_READY }
                onView(withId(R.id.nav_playlists)).perform(click())
                await { rows().size == 1 && list().findViewHolderForAdapterPosition(0) != null }
                groupRow()
                onView(withText(playlist.name)).perform(click())
                songRows(2)
                onView(withId(R.id.playlistList)).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE, { view ->
                        val rect = bounds((view as RecyclerView).findViewHolderForAdapterPosition(1)!!.itemView)
                        floatArrayOf(rect.left + rect.width() * 0.3f, rect.exactCenterY())
                    }, Press.FINGER))
                await { controller.currentMediaItem?.mediaId == tracks[1].id && controller.isPlaying }
                onView(withId(R.id.nav_playlists)).perform(click())
                songRows(2)
                screenshot("compact-playlist-song-rows")
                onView(withId(R.id.playlistList)).perform(androidx.test.espresso.action.GeneralClickAction(
                    androidx.test.espresso.action.Tap.SINGLE, { view ->
                        val row = (view as RecyclerView).findViewHolderForAdapterPosition(1)!!.itemView
                        val rect = bounds(row.findViewById(R.id.more))
                        floatArrayOf(rect.exactCenterX(), rect.exactCenterY())
                    }, Press.FINGER))
                onView(withText(R.string.remove_playlist_song)).inRoot(isDialog()).perform(click())
                songRows(1)
                onMain {
                    assertEquals(2, app.repository.library.value.tracks.size)
                    assertEquals(2, controller.mediaItemCount)
                    assertTrue(controller.isPlaying)
                }
                onView(withText(R.string.back_playlists)).perform(scrollTo(), click())
                await { rows().size == 1 && rows().single().key == playlist.id }
                groupRow()
                onView(withText(playlist.name)).perform(click())
                songRows(1)
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun fineTuningLibrarySelectionFolderPlaylistsAndPinsWorkEndToEnd() {
        val tracks = fixtureTracks()
        val source = app.repository.library.value.sources.single()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun fillName(name: String) {
                onView(isAssignableFrom(com.google.android.material.textfield.TextInputEditText::class.java))
                    .inRoot(isDialog()).perform(replaceText(name), closeSoftKeyboard())
            }
            fun updateMenu(label: Int) {
                onView(withId(R.id.playlistUpdate)).perform(scrollTo(), click())
                onData(equalTo(app.text(label))).inRoot(isDialog()).perform(click())
            }
            fun togglePin() {
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.settingsPinFolders).perform(scrollTo(), click())
                onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
                onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.nav_playlists)).perform(click())
            }
            onView(withId(R.id.nav_library)).perform(click())
            await {
                resumedActivity().findViewById<RecyclerView>(R.id.libraryList).let {
                    it.adapter?.itemCount == tracks.size && it.findViewHolderForAdapterPosition(0) != null
                }
            }
        awaitPlayerFrame()
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.libraryList)
                val touch = (48 * activity.resources.displayMetrics.density).toInt()
                assertTrue("List headers must leave at least one usable row: list=${list.height}, " +
                    "header=${(list.parent.parent as ViewGroup).getChildAt(0).height}, " +
                    "screen=${activity.findViewById<View>(R.id.screenContainer).height}, " +
                    "mini=${activity.findViewById<View>(R.id.miniPlayer).height}, " +
                    "dock=${activity.findViewById<View>(R.id.navigation).height}", list.height >= touch)
                for (id in listOf(R.id.miniPlay, R.id.miniNext, R.id.miniSeek, R.id.navigation)) {
                    val view = activity.findViewById<View>(id)
                    assertTrue("Compact chrome must preserve 48dp controls", view.width >= touch && view.height >= touch)
                    assertTrue("Compact controls must remain visible", bounds(activity.findViewById(R.id.root)).contains(bounds(view)))
                }
                assertTrue(list.findViewHolderForAdapterPosition(0)!!.itemView.performLongClick())
            }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].librarySelecting.value }
            onMain { resumedActivity().findViewById<RecyclerView>(R.id.libraryList).scrollToPosition(1) }
            await {
                resumedActivity().findViewById<RecyclerView>(R.id.libraryList)
                    .findViewHolderForAdapterPosition(1)?.itemView?.isShown == true
            }
            scenario.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.libraryList).findViewHolderForAdapterPosition(1)!!.itemView.performClick()
            }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].librarySelection.value.size == 2 }
            onView(withHint(R.string.search_hint)).perform(replaceText("Nested"), closeSoftKeyboard())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.tracks.size == 1 }
            scenario.recreate()
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[MusicViewModel::class.java]
                assertTrue(model.librarySelecting.value)
                assertEquals(tracks.map { it.id }.toSet(), model.librarySelection.value.toSet())
                activity.currentFocus?.clearFocus()
            }
            screenshot("library-restored-before-playlist")
            onView(isRoot()).perform(closeSoftKeyboard())
            awaitPlayerFrame()
            onView(withId(R.id.libraryAddSelected)).perform(scrollTo(), click())
            onView(withText(R.string.create_playlist)).inRoot(isDialog()).perform(click())
            fillName("Selected mix")
            onView(withText(R.string.create)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.any { it.name == "Selected mix" && it.trackIds.size == 2 } }
            onView(withId(R.id.playlistSaveFolders)).perform(scrollTo(), click())
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            fillName("Folder mix")
            onView(withText(R.string.create)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.any { it.name == "Folder mix" && it.folders.size == 1 } }
            val saved = app.repository.library.value.playlists.first { it.name == "Folder mix" }
            assertEquals(tracks.map { it.id }.toSet(), saved.trackIds.toSet())
            assertEquals(source.uri, saved.folders.single().sourceUri)
            togglePin()
            await { app.repository.library.value.sources.single().pinned }
            updateMenu(R.string.edit_playlist_songs)
            onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            assertEquals(saved.trackIds, app.repository.library.value.playlists.first { it.id == saved.id }.trackIds)
            val previousScan = app.repository.library.value.sources.single().lastScanMs
            updateMenu(R.string.edit_playlist_folders)
            onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            await { app.notice.value?.text == app.text(R.string.playlist_updated) &&
                app.repository.library.value.sources.single().lastScanMs > previousScan &&
                ViewModelProvider(resumedActivity())[MusicViewModel::class.java].scan.value == null }
            assertEquals(saved.folders, app.repository.library.value.playlists.first { it.id == saved.id }.folders)
            updateMenu(R.string.delete_playlist)
            onView(withText(R.string.remove)).inRoot(isDialog()).perform(click())
            await { app.notice.value?.text == app.text(R.string.unpin_first) }
            assertTrue(app.repository.library.value.playlists.any { it.id == saved.id })
            available(false)
            try {
                updateMenu(R.string.refresh_playlist_folders)
                await { app.repository.library.value.sources.single().unavailable &&
                    ViewModelProvider(resumedActivity())[MusicViewModel::class.java].scan.value == null }
                assertEquals(saved, app.repository.library.value.playlists.first { it.id == saved.id })
            } finally { available(true) }
            updateMenu(R.string.refresh_playlist_folders)
            await { !app.repository.library.value.sources.single().unavailable &&
                app.notice.value?.text == app.text(R.string.playlist_updated) }
            togglePin()
            await { !app.repository.library.value.sources.single().pinned }
            updateMenu(R.string.edit_playlist_songs)
            onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.first { it.id == saved.id }.trackIds.size == 1 }
            updateMenu(R.string.rename_playlist)
            fillName("Updated folder mix")
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.any { it.id == saved.id && it.name == "Updated folder mix" } }
            screenshot("fine-folder-playlist")
            updateMenu(R.string.edit_playlist_folders)
            onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
            onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.first { it.id == saved.id }.let {
                it.folders.isEmpty() && it.trackIds.isEmpty()
            } }
            updateMenu(R.string.delete_playlist)
            onView(withText(R.string.remove)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.playlists.none { it.id == saved.id } }
            assertEquals(tracks.size, app.repository.library.value.tracks.size)
            assertEquals("Selected mix", app.repository.library.value.playlists.single().name)
        }
    }

    @Test fun dismissingIndexedDetailsCancelsWithoutReportingAnError() {
        val track = fixtureTracks().first().copy(metadataFromIndex = true)
        runBlocking { app.repository.update { LibraryEdits.upsertTracks(it, listOf(track)) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.setMediaItem(track.toMediaItem()); controller.prepare(); controller.pause() }
                await { controller.playbackState == Player.STATE_READY }
                app.notice.value?.let { app.dismissNotice(it.id) }
                app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply {
                    putLong("audioDelayMs", 1_000)
                    putBoolean("resetReads", true)
                })
                onView(withId(R.id.details)).perform(click())
                await { app.contentResolver.call(root, "scanFixtureState", null, null)!!.getInt("activeAudioReads") > 0 }
                onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
                await { app.contentResolver.call(root, "scanFixtureState", null, null)!!.getInt("activeAudioReads") == 0 }
                android.os.SystemClock.sleep(300)
                instrumentation.waitForIdleSync()
                assertNull("Closing Details is not a metadata error", app.notice.value)
                assertTrue(app.repository.library.value.tracks.single { it.id == track.id }.metadataFromIndex)
            } finally {
                app.contentResolver.call(root, "scanFixtureState", null, Bundle())
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun fineTuningDetailsShowReadablePathsAndSubfolderPlaylistsKeepTheirScope() {
        val tracks = fixtureTracks()
        val nested = tracks.first { it.fileName.startsWith("02") }
        val source = app.repository.library.value.sources.single()
        val expected = "${source.name} / Album/${nested.fileName}"
        assertEquals(expected, runBlocking(Dispatchers.IO) {
            app.repository.documents.location(nested, app.repository.library.value.sources)
        })
        assertEquals("Album/${nested.fileName}", nested.sourcePaths.getValue(source.uri))
        val partial = app.repository.documents.partialLocation(nested.copy(sourcePaths = emptyMap()), listOf(source))
        assertFalse(partial.contains("content://"))
        assertTrue(partial.contains(app.text(R.string.file_location_partial, "${source.name} / ${nested.fileName}")))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.setMediaItem(nested.toMediaItem()); controller.prepare(); controller.pause() }
                await { controller.playbackState == Player.STATE_READY }
                onView(withId(R.id.details)).perform(click())
                var message: TextView? = null
                onView(withId(android.R.id.message)).inRoot(isDialog()).check { view, _ -> message = view as TextView }
                await { message?.text?.contains(expected) == true }
                onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText(containsString(expected))))
                screenshot("fine-song-location")
                onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.nav_folders)).perform(click())
                onView(withText(source.name)).perform(click())
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.entries.any { it.name == "Album" } }
                onView(withText("Album")).perform(click())
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.path.lastOrNull()?.documentId == "music/album" }
                onView(withText(R.string.save_folder_playlist)).perform(scrollTo(), click())
                onView(withText(R.string.save)).inRoot(isDialog()).perform(click())
                onView(isAssignableFrom(com.google.android.material.textfield.TextInputEditText::class.java))
                    .inRoot(isDialog()).perform(replaceText("Nested folder"), closeSoftKeyboard())
                onView(withText(R.string.create)).inRoot(isDialog()).perform(click())
                await { app.repository.library.value.playlists.any { it.name == "Nested folder" } }
                val playlist = app.repository.library.value.playlists.first { it.name == "Nested folder" }
                assertEquals(listOf(nested.id), playlist.trackIds)
                assertEquals("music/album", playlist.folders.single().documentId)
                assertEquals(app.repository.library.value, LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText()))
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
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
                            container.post {
                                val observer = container.viewTreeObserver
                                if (observer.isAlive) observer.removeOnDrawListener(this)
                            }
                        }
                    }
                }
                container.doOnAttach { it.viewTreeObserver.addOnDrawListener(listener) }
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
                    assertTrue(ViewModelProvider(activity)[MusicViewModel::class.java].libraryPage.value.tracks.isEmpty())
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
                pixels.count { it != 0xff101b24.toInt() } > 200)
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
        assertEquals(2, requests)
        val restored = LyricsRepository(app, app.repository, app.applicationScope, client).load(track, online = true)
        assertTrue(restored.cached)
        assertEquals(fetched.lyrics, restored.lyrics)
        assertEquals(2, requests)
        assertNull(repository.load(track, online = false).lyrics)
        lyricMode("available")
        val local = repository.load(track, online = true)
        assertEquals(LyricOrigin.LOCAL_FILE, local.origin)
        assertEquals("Synthetic start", local.lyrics!!.lines.first().text)
        assertEquals(2, requests)
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
        assertEquals(5, requests)
        assertEquals(result, repository.load(lookupTrack, online = true))
        assertEquals("Failed services should not be polled repeatedly", 5, requests)
        assertEquals(2, app.repository.library.value.tracks.size)
    }

    @Test fun passiveWidgetReadsDoNotWaitBehindOnlineLookupOrOverwriteItsResult() = runBlocking(Dispatchers.IO) {
        lyricMode("missing")
        val track = fixtureTracks().first().copy(title = "Synthetic passive lookup fixture")
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val client = OnlineLyricsClient({ url, _, _ ->
            entered.complete(Unit)
            released.await()
            if (url.contains("/search?")) {
                """{"status":200,"candidates":[{"id":"42","accesskey":"test-only","song":"${track.title}","singer":"","duration":60000}]}"""
            } else {
                val encoded = android.util.Base64.encodeToString(syntheticOnlineLyrics("[00:01]Synthetic online marker").toByteArray(), android.util.Base64.NO_WRAP)
                """{"status":200,"content":"$encoded"}"""
            }
        }) { _, error -> throw AssertionError(error) }
        val repository = LyricsRepository(app, app.repository, app.applicationScope, client)
        val online = async { repository.load(track, online = true, refresh = true) }
        try {
            withTimeout(2_000) { entered.await() }
            val passive = withTimeout(2_000) {
                repository.load(track, online = true, allowNetwork = false, publishResult = false)
            }
            assertNull(passive.lyrics)
            assertFalse(online.isCompleted)
            released.complete(Unit)
            val result = online.await()
            assertNotNull(result.lyrics)
            repository.load(track.copy(title = "Another uncached fixture"), online = true,
                allowNetwork = false, publishResult = false)
            assertEquals(result, repository.latest.value.result)
            assertEquals(track.id, repository.latest.value.trackId)
        } finally { released.complete(Unit); online.cancel() }
    }

    @Test fun transientLyricFailuresRetrySoonWithoutRepeatedPolling() = runBlocking(Dispatchers.IO) {
        lyricMode("missing")
        val track = fixtureTracks().first().copy(title = "Synthetic retry lifetime fixture")
        var now = 0L
        var requests = 0
        val repository = LyricsRepository(app, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> requests++; throw java.io.IOException("Synthetic transient failure") }) { _, _ -> },
            nowMs = { now })
        assertEquals(LyricIssue.LOOKUP_FAILED, repository.load(track, online = true).issue)
        val initial = requests
        assertTrue(initial > 0)
        now = 29_999
        repository.load(track, online = true)
        assertEquals(initial, requests)
        now = 30_001
        repository.load(track, online = true)
        assertEquals(initial * 2, requests)
    }

    @Test fun explicitCacheOnlyRequestsNeverAccessNetworkEvenWhenRefreshed() = runBlocking(Dispatchers.IO) {
        lyricMode("missing")
        val track = fixtureTracks().first().copy(title = "Synthetic cache-only request")
        val fetched = LyricsRepository(app, app.repository, app.applicationScope, syntheticLyricClient(track) {})
            .load(track, online = true, refresh = true)
        var requests = 0
        val offline = LyricsRepository(app, app.repository, app.applicationScope,
            OnlineLyricsClient({ _, _, _ -> requests++; error("Cache-only mode must not access the network") }) { _, _ -> })
        for (refresh in listOf(false, true)) {
            val cached = offline.load(track, online = true, refresh = refresh, allowNetwork = false)
            assertTrue(cached.cached)
            assertEquals(fetched.lyrics, cached.lyrics)
            assertNull(offline.load(track.copy(title = "Synthetic uncached request"),
                online = true, refresh = refresh, allowNetwork = false).lyrics)
        }
        assertEquals(0, requests)
        assertNull(offline.load(track, online = false, allowNetwork = false).lyrics)

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
            for (category in SettingsCategory.pages) {
                selectCategory(category)
                scenario.onActivity { activity ->
                    assertEquals(category, ViewModelProvider(activity)[MusicViewModel::class.java].settingsCategory.value)
                    assertEquals(activity.getString(category.label), activity.findViewById<TextView>(R.id.settingsHeading).text)
                    SettingsCategory.pages.forEach {
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
                    assertEquals(category != SettingsCategory.ABOUT, heading.isShown)
                    if (heading.isShown) {
                        assertTrue("Configuration must stay below its fixed heading", scroller.top >= heading.bottom)
                    } else {
                        assertEquals("About must not reserve an empty heading", details.paddingTop, scroller.top)
                    }
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
                SettingsCategory.pages.forEach { category ->
                    val button = activity.findViewById<MaterialButton>(category.buttonId)
                    assertTrue("Category ${button.text} must stay readable", button.lineCount in
                        1..(if (category == SettingsCategory.ABOUT) 2 else 1))
                }
            }
            chooseSetting(R.id.settingsLanguage, R.string.language_chinese)
            selectCategory(SettingsCategory.GENERAL)
            chooseSetting(R.id.settingsTheme, R.string.theme_day)
            onView(withId(R.id.categoryGeneral)).check(matches(withText("常规")))
            onView(withId(R.id.settingsGeneralPanel)).check(matches(isDisplayed()))
            screenshot("categories-chinese-day")
            onView(withContentDescription(app.text(R.string.back))).perform(click())
            onView(withId(R.id.navigation)).check(matches(isDisplayed()))
            scenario.onActivity {
                assertEquals(R.id.nav_library, ViewModelProvider(it)[MusicViewModel::class.java].selectedScreen.value)
            }
        }
    }

    @Test fun paletteEditorsShareFireColorsAcrossClassicAndFireAndPersistRendering() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            chooseSetting(R.id.settingsLanguage, R.string.language_english)
            setting(R.id.settingsSpectrum).perform(scrollTo(), click())
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_fire))
            chooseDropdown(R.id.spectrumFireStyle, app.text(R.string.spectrum_smooth))
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withTagValue(equalTo<Any>("rainbow:4"))).perform(scrollTo(), click())
            screenshot("fire-color-editor")
            onView(withText(app.text(R.string.cancel))).inRoot(isDialog()).perform(click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.colors)
            setting(R.id.spectrumColorLow).perform(scrollTo(), click())
            onView(withTagValue(equalTo<Any>("rainbow:4"))).perform(scrollTo(), click())
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            val fire = app.displaySettings.options.value.spectrum.colors
            assertNotEquals(SpectrumColors.FIRE.low, fire.low)
            assertEquals(SpectrumColors.FIRE.middle, fire.middle)
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_classic))
            assertEquals(fire, app.displaySettings.options.value.spectrum.colors)
            setting(R.id.spectrumColorHigh).perform(scrollTo(), click())
            onView(withTagValue(equalTo<Any>("rainbow:3"))).perform(scrollTo(), click())
            onView(withText(app.text(R.string.apply))).inRoot(isDialog()).perform(click())
            val classic = app.displaySettings.options.value.spectrum.colors
            assertNotEquals(fire.high, classic.high)
            assertEquals(fire.low, classic.low)
            scenario.recreate()
            val restored = DisplaySettings(app) {}.options.value.spectrum
            assertEquals(classic, restored.colors)
            assertEquals(FireStyle.SMOOTH, restored.fireStyle)
            chooseDropdown(R.id.spectrumPalette, app.text(R.string.spectrum_fire))
            assertEquals(classic, app.displaySettings.options.value.spectrum.colors)
            chooseDropdown(R.id.spectrumFireStyle, app.text(R.string.spectrum_led))
            setting(R.id.spectrumResetColors).perform(scrollTo(), click())
            assertEquals(SpectrumColors.FIRE, app.displaySettings.options.value.spectrum.colors)
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

    @Test fun navigationLabelsFollowScreenOrientationOnEveryDock() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                rotate(scenario, orientation)
                val landscape = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                for (position in NavigationPosition.entries) {
                    val side = position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT
                    for (style in listOf(BlockStyle.SOLID, BlockStyle.CHARACTERS, BlockStyle.NONE)) {
                        onMain { app.displaySettings.update {
                            it.copy(navigationPosition = position, blockOutline = it.blockOutline.copy(style = style))
                        } }
                        awaitPlayerFrame()
                        scenario.onActivity { activity ->
                            val navigation = activity.findViewById<PlayerNavigationView>(R.id.navigation)
                            assertNavigationLabels(navigation, landscape && !side)
                            if (side) {
                                assertEquals("Side docks must keep their compact width with labels below icons",
                                    activity.resources.getDimensionPixelSize(R.dimen.navigation_side_width), navigation.width)
                            } else {
                                assertEquals("Landscape should use a thinner row while portrait keeps stacked labels",
                                    ((if (landscape) 48 else 80) * activity.resources.displayMetrics.density).toInt(),
                                    navigation.height)
                            }
                        }
                    }
                    screenshot("dock-labels-${orientation}-${position.name.lowercase()}")
                    scenario.recreate()
                    awaitPlayerFrame()
                    scenario.onActivity { assertNavigationLabels(it.findViewById(R.id.navigation), landscape && !side) }
                }
            }
        }
    }

    @Test fun navigationLabelsRemainVisibleInNarrowWindowsAndLargeFonts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (orientation in listOf(Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT)) {
                    for (fontScale in listOf(1f, 2f)) {
                        val config = Configuration(activity.resources.configuration).apply {
                            this.orientation = orientation
                            this.fontScale = fontScale
                        }
                        val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_ManaPlayer)
                        val navigation = PlayerNavigationView(context)
                        navigation.configureBlocks(true, BlockOutline(BlockStyle.CHARACTERS, "+"))
                        val density = context.resources.displayMetrics.density
                        for (side in listOf(false, true, false)) {
                            navigation.configure(side)
                            for (widthDp in listOf(1024, 280, 640)) {
                                repeat(2) {
                                    navigation.forceLayout()
                                    navigation.measure(
                                        View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(),
                                            if (side) View.MeasureSpec.AT_MOST else View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec((360 * density).toInt(),
                                            if (side) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST),
                                    )
                                    navigation.layout(0, 0, navigation.measuredWidth, navigation.measuredHeight)
                                }
                                try {
                                    assertNavigationLabels(navigation, orientation == Configuration.ORIENTATION_LANDSCAPE && !side)
                                } catch (failure: AssertionError) {
                                    throw AssertionError("orientation=$orientation font=$fontScale side=$side width=$widthDp", failure)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertNavigationLabels(navigation: PlayerNavigationView, besideIcons: Boolean) {
        val density = navigation.resources.displayMetrics.density
        for (id in listOf(R.id.nav_player, R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
            val button = navigation.findViewById<LinearLayout>(id)
            val icon = button.getChildAt(0)
            val label = button.getChildAt(1) as TextView
            val scale = if (button.isSelected) 1.5f else 1f
            assertEquals((24 * density * scale).toInt(), icon.width)
            assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12 * scale,
                navigation.resources.displayMetrics), label.textSize, 0.1f)
            assertEquals(if (besideIcons) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL, button.orientation)
            assertEquals(View.VISIBLE, label.visibility)
            if (besideIcons) assertEquals(1, label.maxLines) else assertTrue(label.maxLines in 1..2)
            assertTrue("Navigation must keep 48dp touch targets", button.width >= 48 * density && button.height >= 48 * density)
            fun childBounds(view: View) = Rect(0, 0, view.width, view.height).also {
                button.offsetDescendantRectToMyCoords(view, it)
            }
            val buttonBounds = Rect(0, 0, button.width, button.height)
            val iconBounds = childBounds(icon)
            val labelBounds = childBounds(label)
            assertTrue("The icon must fit inside its button", buttonBounds.contains(iconBounds))
            assertTrue("The label must fit inside its button", buttonBounds.contains(labelBounds))
            val content = Rect(iconBounds).apply { union(labelBounds) }
            assertTrue("The label must stay independently centered",
                kotlin.math.abs(labelBounds.exactCenterX() - buttonBounds.exactCenterX()) <= 1)
            assertTrue("Navigation content must be vertically centered",
                kotlin.math.abs(content.exactCenterY() - buttonBounds.exactCenterY()) <= 1)
            val bitmap = Bitmap.createBitmap(label.width, label.height, Bitmap.Config.ARGB_8888)
            try {
                // Android 6 can rebuild an autosized TextView's text layout lazily on its first draw.
                label.draw(Canvas(bitmap))
            } finally { bitmap.recycle() }
            val layout = label.layout
            assertNotNull("The navigation label must be measured: ${label.text}, ${label.width}x${label.height}", layout)
            assertTrue("Text must not be vertically clipped: ${label.text}", label.height >= layout.height)
            if (besideIcons) {
                assertEquals("Horizontal icons must stay at the leading edge", button.paddingLeft, iconBounds.left)
                assertTrue("Top/bottom landscape labels must sit beside the icon", labelBounds.left >= iconBounds.right + 8 * density)
                assertTrue(kotlin.math.abs(iconBounds.exactCenterY() - labelBounds.exactCenterY()) <= 1)
                assertEquals(1, label.lineCount)
                if (label.paint.measureText(label.text.toString()) <= label.width)
                    assertEquals("Labels must not truncate when they have room", 0, layout.getEllipsisCount(0))
                assertEquals("The full label stays accessible at enlarged sizes", label.text.toString(), button.contentDescription)
            } else {
                assertTrue("Side-docked and portrait labels must stay below the icon", labelBounds.top >= iconBounds.bottom + 2 * density)
                assertTrue("Stacked labels must stay centered", kotlin.math.abs(iconBounds.exactCenterX() - labelBounds.exactCenterX()) <= 1)
                assertEquals(0, (label.layoutParams as LinearLayout.LayoutParams).marginStart)
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
                val geometry = mutableMapOf<NavigationPosition, Triple<Int, Int, Int>>()
                for (position in NavigationPosition.entries) {
                    onMain { app.displaySettings.update { it.copy(navigationPosition = position) } }
                    await { resumedActivity().findViewById<View>(R.id.navigation).let {
                        if (position == NavigationPosition.LEFT || position == NavigationPosition.RIGHT) it.width < it.height
                        else it.width > it.height
                    } }
                    awaitPlayerFrame()
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
                        geometry[position] = Triple(player.height, information.height,
                            activity.findViewById<View>(R.id.navigation).height +
                                activity.resources.getDimensionPixelSize(R.dimen.player_gap))
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
                    val dockHeight = bottom.third
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
                        val buttons = listOf(R.id.shuffle, R.id.previous, R.id.rewind, R.id.play, R.id.forward, R.id.next, R.id.repeat)
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
                        buttons.groupBy { bounds(it).top }.values.forEach { row ->
                            row.sortedBy { bounds(it).left }.zipWithNext().forEach { (left, right) ->
                                assertTrue("Transport controls need a safe gap", bounds(right).left - bounds(left).right >= gap)
                            }
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
        val labels = listOf(R.string.block_solid, R.string.block_dashed, R.string.block_arc, R.string.block_characters,
            R.string.block_dashed_arc)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            tapAfterWindowTransition(R.id.action_settings)
            await { resumedActivity().findViewById<View>(R.id.settingsLanguage).isShown }
            chooseSetting(R.id.settingsLanguage, R.string.language_english)
            for (style in BlockStyle.selectable) {
                chooseDropdown(R.id.settingsBlockStyle, app.text(labels[style.ordinal]))
                assertEquals(style, DisplaySettings(app) {}.options.value.blockOutline.style)
                scenario.onActivity { activity ->
                    assertNull("Transport buttons must not have a second touching outline", activity.findViewById<View>(R.id.transportRow).foreground)
                    val miniOutline = activity.findViewById<View>(R.id.miniPlayer).foreground
                    assertNull("Single-row Settings mini controls must not have overlapping outer borders", miniOutline)
                    for (id in listOf(R.id.cover, R.id.favorite, R.id.details,
                        R.id.navigationArtwork, R.id.nav_player, R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
                        val foreground = activity.findViewById<View>(id).foreground
                        assertOutlinedWithoutFill(requireNotNull(foreground))
                    }
                    for (id in listOf(R.id.trackInformation, R.id.spectrum, R.id.seekBlock, R.id.play, R.id.miniPlay)) {
                        assertOutlinedWithoutFill(activity.findViewById<View>(id))
                    }
                    fun checkSettingsButtons(view: View) {
                        if (view is MaterialButton) {
                            assertTrue("Settings button ${view.id} must use the selected border",
                                view.foreground is PlayerBlockDrawable)
                            assertEquals("No duplicate native outline", 0, view.strokeWidth)
                            assertOutlinedWithoutFill(view.foreground)
                        } else if (view is ViewGroup) view.children.forEach(::checkSettingsButtons)
                    }
                    checkSettingsButtons(activity.findViewById(R.id.settingsPanel))
                }
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                screenshot("border-${style.name.lowercase()}")
                onView(withId(R.id.action_settings)).perform(click())
            }
            chooseDropdown(R.id.settingsBlockStyle, app.text(R.string.block_characters))
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
                    if (BlockStyle.entries[index] == BlockStyle.NONE) assertTransparentBitmap(bitmap)
                    else assertTrue("${BlockStyle.entries[index]} must paint a visible outline: $painted pixels", painted > 50)
                }
                for (a in bitmaps.indices) for (b in a + 1 until bitmaps.size) {
                    assertFalse("Each outline option must actually draw differently", bitmaps[a].sameAs(bitmaps[b]))
                }
            } finally {
                bitmaps.forEach(Bitmap::recycle)
            }
        }
    }

    @Test fun disabledBlocksPersistAndRestoreSavedArcAndShadowOptions() {
        val outline = BlockOutline(BlockStyle.DASHED_ARC, "+", 36, true)
        onMain { app.displaySettings.update { it.copy(blockOutline = outline) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsControlBlocks).perform(scrollTo(), click())
            assertEquals(outline, DisplaySettings(app) {}.options.value.blockOutline)
            assertFalse(app.displaySettings.options.value.controlBlocks)
            scenario.onActivity(::assertNoBlockBorders)
            scenario.recreate()
            awaitPlayerFrame()
            scenario.onActivity { activity ->
                assertNoBlockBorders(activity)
                for (id in listOf(R.id.settingsBlockCharacter, R.id.blockArcControls, R.id.settingsBlockShadow)) {
                    assertFalse(activity.findViewById<View>(id).isShown)
                }
                assertNull(activity.findViewById<View>(R.id.settingsLanguage).foreground)
                assertNull(activity.findViewById<View>(R.id.categoryGeneral).foreground)
            }
            setting(R.id.settingsControlBlocks).perform(scrollTo(), click())
            assertEquals(outline, DisplaySettings(app) {}.options.value.blockOutline)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsBlockShadow).isShown)
                assertTrue(activity.findViewById<View>(R.id.nav_player).foreground is PlayerBlockDrawable)
                assertTrue(activity.findViewById<View>(R.id.trackInformation).background is PlayerBlockDrawable)
                assertTrue(activity.findViewById<View>(R.id.settingsLanguage).foreground is PlayerBlockDrawable)
            }
        }
    }

    @Test fun legacyNonePreferencesMigrateOnceWithoutLosingBorderCustomization() {
        val context = object : android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("migration-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().putString("blockStyle", "NONE").putBoolean("controlBlocks", true)
            .putString("blockCharacter", "+").putInt("blockArcDp", 36).putBoolean("blockShadow", true).commit())
        try {
            val migrated = DisplaySettings(context) {}
            assertFalse(migrated.options.value.controlBlocks)
            assertEquals(BlockOutline(BlockStyle.SOLID, "+", 36, true), migrated.options.value.blockOutline)
            assertEquals("SOLID", preferences.getString("blockStyle", null))
            assertFalse(preferences.getBoolean("controlBlocks", true))
            migrated.update { it.copy(controlBlocks = true) }
            assertTrue(DisplaySettings(context) {}.options.value.controlBlocks)
            assertEquals(migrated.options.value.blockOutline, DisplaySettings(context) {}.options.value.blockOutline)
        } finally { preferences.edit().clear().commit() }
    }

    private fun assertNoBlockBorders(activity: MainActivity) {
        for (id in listOf(R.id.cover, R.id.favorite, R.id.details, R.id.navigationArtwork, R.id.miniPlayer,
            R.id.nav_player, R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists)) {
            assertNull(activity.findViewById<View>(id).foreground)
        }
        for (id in listOf(R.id.trackInformation, R.id.spectrum, R.id.seekBlock, R.id.volumeBlock)) {
            assertNull(activity.findViewById<View>(id).background)
        }
        for (id in listOf(R.id.play, R.id.previous, R.id.next, R.id.miniPlay, R.id.miniNext)) {
            val drawable = requireNotNull(activity.findViewById<View>(id).background.constantState).newDrawable().mutate()
            drawable.state = intArrayOf(android.R.attr.state_enabled)
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, 96, 96)
                drawable.draw(Canvas(bitmap))
                assertTransparentBitmap(bitmap)
            } finally { bitmap.recycle() }
        }
        for (id in listOf(R.id.favorite, R.id.details)) {
            assertTrue("Disabled blocks restore standard outlined actions",
                activity.findViewById<MaterialButton>(id).strokeWidth > 0)
        }
        activity.findViewById<LinearLayout>(R.id.settingsCategories).children
            .filter { it.id == R.id.settingsCategoryDivider }.forEach { assertNull(it.background) }
    }

    private fun assertTransparentBitmap(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("None must not draw an outline or shadow", pixels.all { Color.alpha(it) == 0 })
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
            fun assertSize(sizeSp: Int) = onMainResult {
                val list = lyricList()
                val geometry = "size=$sizeSp, list=${bounds(list)}, active=${(list.adapter as LyricsAdapter).activeLine}, " +
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
                assertEquals(sizeSp / 20f, active.textSize * active.scaleX / (other.textSize * other.scaleX), 0.001f)
                val density = list.resources.displayMetrics.density
                assertEquals((10 * density).toInt(), active.paddingTop)
                assertEquals((2 * density).toInt(), other.paddingTop)
                assertTrue("Inactive lyrics must not reserve active-line padding", active.paddingTop > other.paddingTop)
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
                val oldHeight = assertSize(24)
                onMain { controller.seekTo(22_000) }
                await { (lyricList().adapter as LyricsAdapter).activeLine == 11 && activeCentered() }
                assertEquals(oldHeight, assertSize(24))
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.lyricActiveSize).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.4f) }, { seekCoordinates(it, 1f) }, Press.FINGER))
                assertEquals(48, DisplaySettings(app) {}.options.value.lyricAppearance.activeSizeSp)
                scenario.recreate()
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { activeCentered() }
                assertSize(48)
                onView(withId(R.id.action_settings)).perform(click())
                setting(R.id.lyricActiveSize).perform(scrollTo(), GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.9f) }, { seekCoordinates(it, 0f) }, Press.FINGER))
                assertEquals(12, DisplaySettings(app) {}.options.value.lyricAppearance.activeSizeSp)
                onView(withContentDescription(app.text(R.string.back))).perform(click())
                await { activeCentered() }
                assertSize(12)
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

    @Test fun aboutGuideIntroducesFeaturesInBothLanguagesWithoutDeviceDetails() {
        val excludedTerms = listOf("requirement", "requested", "regression", "version code", "API ",
            "Device:", "需求", "要求", "回归", "设备：", "PHOTO VIEWER", "照片查看")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_settings)).perform(click())
            scenario.onActivity {
                assertEquals("About must stay unloaded until opened", 0,
                    it.findViewById<LinearLayout>(R.id.settingsAboutPanel).childCount)
            }
            for ((language, choice, headings) in listOf(
                Triple("en", R.string.language_english, listOf("MUSIC PLAYBACK", "FOLDERS AND USB",
                    "LIBRARY AND PLAYLISTS", "SAVED POSITIONS", "LOCAL BACKUPS", "LYRICS", "SPECTRUM", "APPEARANCE", "FORMAT SUPPORT")),
                Triple("zh", R.string.language_chinese, listOf("音乐播放", "文件夹与 USB", "音乐库与歌单",
                    "播放位置", "本地备份", "歌词", "频谱", "界面与外观", "格式支持")),
            )) {
                chooseSetting(R.id.settingsLanguage, choice)
                selectCategory(SettingsCategory.ABOUT)
                for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                    rotate(scenario, orientation)
                    scenario.onActivity { activity ->
                        val body = activity.findViewById<TextView>(R.id.aboutBody)
                        val text = body.text.toString()
                        assertEquals("Only the localized feature introduction belongs in About",
                            activity.getString(R.string.about_body) + "\n\n" +
                                activity.getString(R.string.about_system_controls) + "\n\n" +
                                activity.getString(R.string.about_lyric_cache), text)
                        for (heading in headings) assertTrue("Missing $language feature section: $heading",
                            text.contains("\n\n$heading\n"))
                        for (phrase in excludedTerms) {
                            assertFalse("About must not include request or diagnostic details: $phrase",
                                text.contains(phrase, ignoreCase = true))
                        }
                        assertEquals(2, activity.findViewById<LinearLayout>(R.id.settingsAboutPanel).childCount)
                        assertTrue(activity.findViewById<View>(R.id.navigation).isShown)
                        assertTrue(activity.findViewById<View>(R.id.miniPlayer).isShown)
                        activity.findViewById<ScrollView>(R.id.settingsScroll).scrollTo(0, 0)
                    }
                    awaitPlayerFrame()
                    onView(withId(R.id.aboutLogo)).perform(scrollTo()).check(matches(isDisplayed()))
                    scenario.onActivity(::assertAboutBranding)
                    screenshot("feature-guide-$language-$orientation-top")
                    scenario.onActivity { activity ->
                        val body = activity.findViewById<TextView>(R.id.aboutBody)
                        body.requestRectangleOnScreen(Rect(0, body.height - 1, body.width, body.height), true)
                    }
                    await {
                        val body = resumedActivity().findViewById<TextView>(R.id.aboutBody)
                        val visible = Rect()
                        body.getLocalVisibleRect(visible) && visible.bottom == body.height
                    }
                    screenshot("feature-guide-$language-$orientation-end")
                }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(it.getString(R.string.about_body) + "\n\n" + it.getString(R.string.about_system_controls) +
                        "\n\n" + it.getString(R.string.about_lyric_cache),
                        it.findViewById<TextView>(R.id.aboutBody).text.toString())
                }
            }
        }
    }

    @Test fun brandingShowsLogoAndInstalledVersionWithoutOpeningViewerOrChangingPlayback() {
        val tracks = fixtureTracks()
        for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
            onMain { app.themeMode = mode }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    onMain {
                        controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 15_000)
                        controller.prepare()
                        controller.pause()
                    }
                    for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                        rotate(scenario, orientation)
                        scenario.onActivity { activity ->
                            val toolbar = activity.findViewById<ViewGroup>(R.id.toolbar)
                            val logo = activity.findViewById<ImageView>(R.id.homeLogo)
                            val title = toolbar.children.filterIsInstance<TextView>().single {
                                it.text == activity.getString(R.string.app_name)
                            }
                            val settings = activity.findViewById<View>(R.id.action_settings)
                            val density = activity.resources.displayMetrics.density
                            assertEquals(48 * density, logo.width.toFloat(), 1f)
                            assertEquals(48 * density, logo.height.toFloat(), 1f)
                            assertEquals(32 * density, (logo.width - logo.paddingLeft - logo.paddingRight).toFloat(), 1f)
                            assertTrue(logo.isClickable && logo.isFocusable)
                            assertEquals(activity.getString(R.string.logo_now_playing), logo.contentDescription)
                            val info = logo.createAccessibilityNodeInfo()
                            try {
                                assertEquals("android.widget.Button", info.className.toString())
                            } finally { info.recycle() }
                            assertTrue("Title ${bounds(title)} must leave 8dp after logo ${bounds(logo)}",
                                bounds(title).left - bounds(logo).right >= 8 * density - 1)
                            assertTrue(bounds(title).right <= bounds(settings).left)
                            assertTrue(bounds(toolbar).contains(bounds(logo)))
                            assertTrue(settings.isShown)
                            assertNull("Brand colors must not follow the theme tint", logo.imageTintList)
                        }
                        screenshot("brand-player-$mode-$orientation")
                        tapAfterWindowTransition(R.id.action_settings)
                        await { resumedActivity().findViewById<View>(R.id.settingsPanel)?.isShown == true }
                        selectCategory(SettingsCategory.ABOUT)
                        onView(withId(R.id.aboutLogo)).perform(scrollTo(), click())
                        scenario.onActivity { activity ->
                            assertAboutBranding(activity)
                            assertTrue("Tapping the logo must not open a separate window", activity.hasWindowFocus())
                            assertTrue(activity.supportFragmentManager.fragments.none { it is androidx.fragment.app.DialogFragment })
                            assertEquals(tracks.first().id, controller.currentMediaItem!!.mediaId)
                            assertEquals(15_000, controller.currentPosition)
                            assertFalse(controller.playWhenReady)
                        }
                        screenshot("brand-about-$mode-$orientation")
                        scenario.recreate()
                        awaitPlayerFrame()
                        onView(withId(R.id.aboutLogo)).perform(scrollTo())
                        scenario.onActivity(::assertAboutBranding)
                        for (page in listOf(R.id.action_settings, R.id.nav_folders, R.id.nav_library, R.id.nav_queue,
                            R.id.nav_playlists)) {
                            if (page != R.id.action_settings) onView(withId(page)).perform(click())
                            onView(withId(R.id.homeLogo)).perform(click())
                            await { !resumedActivity().findViewById<View>(R.id.miniPlayer).isShown &&
                                resumedActivity().findViewById<View>(R.id.controls).isShown }
                            assertEquals(tracks.first().id, onMainResult { controller.currentMediaItem!!.mediaId })
                            assertEquals(15_000L, onMainResult { controller.currentPosition })
                            assertFalse(onMainResult { controller.playWhenReady })
                        }
                    }
                } finally {
                    onMain { controller.pause(); MediaController.releaseFuture(future) }
                }
            }
        }
    }

    private fun assertAboutBranding(activity: MainActivity) {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        val version = activity.findViewById<TextView>(R.id.aboutVersion)
        assertEquals(activity.getString(R.string.about_version, info.versionName,
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)), version.text.toString())
        assertTrue(version.isShown)
        val visible = Rect()
        assertTrue(version.getGlobalVisibleRect(visible))
        assertEquals("The entire version row must be visible", bounds(version), visible)
        val logo = activity.findViewById<ImageView>(R.id.aboutLogo)
        val heading = activity.findViewById<TextView>(R.id.aboutHeading)
        assertEquals(activity.getString(R.string.app_name), heading.text.toString())
        assertTrue(logo.isShown)
        assertFalse(logo.isClickable)
        assertFalse(logo.isFocusable)
        assertFalse(logo.hasOnClickListeners())
        assertNull(logo.imageTintList)
        assertTrue(logo.drawable is BitmapDrawable)
        assertTrue(bounds(logo).left >= maxOf(bounds(heading).right, bounds(version).right))
        assertTrue(bounds(version).top >= bounds(heading).bottom)
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
            fun render(expectedBatches: Int = if (Build.VERSION.SDK_INT >= 29) 1 else 0): Bitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
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
                    colors = customized))
                val colored = render()
                try {
                    val pixels = (1 until 19).map { row ->
                        val y = (bottom - row * step).toInt()
                        val pixel = colored.getPixel(x, y)
                        val expected = customized.colorAt((bottom - y - 0.5f) / (bottom - top))
                        for (shift in listOf(0, 8, 16)) {
                            assertTrue("Continuous bars must interpolate the selected palette",
                                kotlin.math.abs(((pixel shr shift) and 0xff) - ((expected shr shift) and 0xff)) <=
                                    if (Build.VERSION.SDK_INT < 29) 4 else 2)
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

    @Test fun spectrumSmallBandsCoverEveryFrequencyAndGrowFromEachInsideEdge() {
        onMain {
            val context = androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_ManaPlayer)
            val density = context.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val height = (280 * density).toInt()
            val view = SpectrumView(context)
            view.layout(0, 0, width, height)
            val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)
            val left = 4 * density
            val top = 12 * context.resources.displayMetrics.scaledDensity + 8 * density
            val right = width - left
            val bottom = height - top
            val color = SpectrumColors(0xff00ff, 0xff00ff, 0xff00ff)
            try {
                for (bands in SpectrumOptions.BAND_COUNTS) for (origin in SpectrumOrigin.entries) {
                    for (style in FireStyle.entries) for (bin in listOf(0, 10, 20, 31)) {
                        view.configure(SpectrumOptions(enabled = true, bands = bands, origin = origin,
                            barStyle = style, colors = color, peaks = false))
                        view.setPlaybackActive(false)
                        view.setPlaybackActive(true)
                        view.submit(SpectrumFrame(FloatArray(32).apply { this[bin] = 0.6f }, true, 44_100))
                        picture.eraseColor(Color.TRANSPARENT)
                        var batches = 0
                        val canvas = object : Canvas(picture) {
                            override fun drawVertices(mode: VertexMode, vertexCount: Int, verts: FloatArray, vertOffset: Int,
                                texs: FloatArray?, texOffset: Int, colors: IntArray?, colorOffset: Int,
                                indices: ShortArray?, indexOffset: Int, indexCount: Int, paint: android.graphics.Paint) {
                                batches++
                                super.drawVertices(mode, vertexCount, verts, vertOffset, texs, texOffset, colors,
                                    colorOffset, indices, indexOffset, indexCount, paint)
                            }
                        }
                        view.draw(canvas)
                        assertEquals(if (Build.VERSION.SDK_INT >= 29) 1 else 0, batches)
                        picture.getPixels(pixels, 0, width, 0, 0, width, height)
                        val side = origin == SpectrumOrigin.LEFT || origin == SpectrumOrigin.RIGHT
                        val extent = if (side) right - left else bottom - top
                        val frequency = if (side) bottom - top else right - left
                        val group = ((bin + 1) * bands - 1) / 32
                        var count = 0
                        var minimum = Float.MAX_VALUE
                        var maximum = 0f
                        for (y in top.toInt() until bottom.toInt()) for (x in left.toInt() until right.toInt()) {
                            val pixel = pixels[y * width + x]
                            if (Color.alpha(pixel) < 200 || Color.red(pixel) < 220 ||
                                Color.blue(pixel) < 220 || Color.green(pixel) > 20) continue
                            count++
                            val position = if (side) y + 0.5f - top else x + 0.5f - left
                            assertTrue("$origin/$bands/$style/bin$bin must stay in its frequency group",
                                position >= group * frequency / bands - 1 &&
                                    position <= (group + 0.8f) * frequency / bands + 2)
                            val distance = when (origin) {
                                SpectrumOrigin.BOTTOM -> bottom - y - 0.5f
                                SpectrumOrigin.TOP -> y + 0.5f - top
                                SpectrumOrigin.LEFT -> x + 0.5f - left
                                SpectrumOrigin.RIGHT -> right - x - 0.5f
                            }
                            minimum = minOf(minimum, distance)
                            maximum = maxOf(maximum, distance)
                        }
                        assertTrue("$origin/$bands/$style/bin$bin was dropped", count > 0)
                        assertTrue("Bars must start at $origin inside the same plot", minimum <= 2)
                        assertTrue("Bars must grow inward by the measured level", maximum in extent * 0.56f..extent * 0.62f)
                    }
                }
            } finally { picture.recycle(); view.setPlaybackActive(false) }
        }
    }

    @Test fun spectrumDoubleTapRotatesClockwiseWithoutAlsoChangingDisplayMode() {
        onMain { app.displaySettings.update { it.copy(spectrum = SpectrumOptions(enabled = true, bands = 8)) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { resumedActivity().findViewById<View>(R.id.spectrum)?.isShown == true }
            awaitPlayerFrame()
            val original = app.displaySettings.options.value.spectrum
            val position = onMainResult { bounds(resumedActivity().findViewById(R.id.spectrum)) }
            for (origin in listOf(SpectrumOrigin.LEFT, SpectrumOrigin.TOP, SpectrumOrigin.RIGHT, SpectrumOrigin.BOTTOM)) {
                onView(withId(R.id.spectrum)).perform(doubleClick())
                Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 80L)
                assertEquals(original.copy(origin = origin), app.displaySettings.options.value.spectrum)
                assertEquals(origin, DisplaySettings(app) {}.options.value.spectrum.origin)
                assertEquals(position, onMainResult { bounds(resumedActivity().findViewById(R.id.spectrum)) })
            }
            onView(withId(R.id.spectrum)).perform(click())
            await { app.displaySettings.options.value.spectrum == original.nextMode() }
            onMain { app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(style = SpectrumStyle.OFF)) } }
            awaitPlayerFrame()
            onView(withId(R.id.spectrum)).perform(doubleClick())
            Thread.sleep(android.view.ViewConfiguration.getDoubleTapTimeout() + 80L)
            assertEquals(SpectrumStyle.OFF, app.displaySettings.options.value.spectrum.style)
            assertEquals(SpectrumOrigin.LEFT, app.displaySettings.options.value.spectrum.origin)
            assertFalse(app.spectrum.frames.value.hasAudio)
            scenario.recreate()
            await { resumedActivity().findViewById<View>(R.id.spectrum)?.isShown == true }
            assertEquals(SpectrumOrigin.LEFT, app.displaySettings.options.value.spectrum.origin)
            onMain {
                assertTrue(resumedActivity().findViewById<View>(R.id.spectrum)
                    .performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null))
            }
            assertEquals(SpectrumStyle.LINE, app.displaySettings.options.value.spectrum.style)
            assertEquals(SpectrumOrigin.LEFT, app.displaySettings.options.value.spectrum.origin)
            assertEquals("Playing", app.text(R.string.now_playing))
        }
    }

    @Test fun libraryInitialSearchUsesBundledReadingsAndPlayerOwnedSongMenus() {
        val tracks = fixtureTracks()
        val original = app.repository.library.value
        val first = tracks[0].copy(fileName = "消防.mp3", title = "Search fixture one", artist = "周华健")
        val second = tracks[1].copy(fileName = "重慶.mp3", title = "Search fixture two")
        runBlocking { app.repository.update { library -> library.copy(tracks = library.tracks.map {
            when (it.id) { first.id -> first; second.id -> second; else -> it }
        }) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.nav_library)).perform(click())
                val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                for ((query, song) in listOf("xf" to first, "XF zhj" to first, "cq" to second, "重慶" to second)) {
                    onView(withHint(R.string.search_hint)).perform(replaceText(query), closeSoftKeyboard())
                    await { model.libraryPage.value.tracks.map(Track::id) == listOf(song.id) }
                    onView(allOf(withText(song.title), isDescendantOfA(withId(R.id.libraryList))))
                        .check(matches(isDisplayed()))
                }
                onView(allOf(withContentDescription(app.text(R.string.options_for, second.title)),
                    isDescendantOfA(withId(R.id.libraryList)))).perform(click())
                onView(withId(R.id.playerDialogPanel)).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withId(R.id.playerDialogList)).inRoot(isDialog()).check { view, _ ->
                    assertTrue((view as android.widget.ListView).getChildAt(0) is android.widget.Checkable)
                    assertTrue(view.getChildAt(0).foreground is PlayerBlockDrawable)
                }
                screenshot("player-owned-library-menu")
                pressBack()
            }
            val elapsed = runBlocking(Dispatchers.Default) {
                assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper())
                val library = Library(tracks = List(10_000) { first.copy(id = "pinyin-$it", title = "消防 $it") })
                val start = System.nanoTime()
                val page = LibraryBrowsing.page(library, "xf", false, com.macarmusic.app.model.SortOrder.TITLE,
                    com.macarmusic.app.model.LibraryLocation())
                assertEquals(10_000, page.tracks.size)
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
            }
            assertTrue("10,000-track initial filtering/sorting exceeded 1.5 seconds: $elapsed ms", elapsed < 1_500)
            val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
            File(directory, "pinyin-search-performance.json").writeText("""{"tracks":10000,"warmMilliseconds":$elapsed}""")
        } finally { runBlocking { app.repository.update { original } } }
    }

    @Test fun ownedChoicePanelsRecycleRowsAndKeepAllActionsVisibleWithLargeFonts() {
        ActivityScenario.launch(MainActivity::class.java).use {
            for ((night, mode) in listOf(
                Configuration.UI_MODE_NIGHT_YES to AppCompatDelegate.MODE_NIGHT_YES,
                Configuration.UI_MODE_NIGHT_NO to AppCompatDelegate.MODE_NIGHT_NO,
            )) for (language in listOf("en", "zh")) for (scale in listOf(1f, 2f)) {
                val previous = onMainResult { resumedActivity() }
                val changing = app.themeMode != mode ||
                    androidx.core.os.ConfigurationCompat.getLocales(previous.resources.configuration)[0]?.language != language
                onMain {
                    app.themeMode = mode
                    if (Build.VERSION.SDK_INT >= 33) {
                        app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                            android.os.LocaleList.forLanguageTags(language)
                    } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                }
                await { onMainResult {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any { activity ->
                            (!changing || activity !== previous) && activity.hasWindowFocus() &&
                                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == night &&
                                androidx.core.os.ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language == language
                        }
                } }
                lateinit var dialog: PlayerDialog
                lateinit var labels: Array<String>
                val checked = BooleanArray(500).also { it[0] = true }
                onMain {
                    val context = ContextThemeWrapper(resumedActivity(), R.style.Theme_ManaPlayer).apply {
                        applyOverrideConfiguration(Configuration(resumedActivity().resources.configuration).apply {
                            fontScale = scale
                        })
                    }
                    assertEquals(language, androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language)
                    labels = Array(500) { context.getString(R.string.lyrics_version_number, it + 1) }
                    dialog = AccentTheme.dialog(context).setTitle(R.string.lyrics_versions)
                        .setMultiChoiceItems(labels, checked) { value, index, selected ->
                            if (index == 0 && !selected) {
                                checked[0] = true
                                (value as PlayerDialog).listView.setItemChecked(0, true)
                            }
                        }.setPositiveButton(R.string.save, null)
                        .setNeutralButton(R.string.select_all_shown, null)
                        .setNegativeButton(R.string.cancel, null).show()
                    dialog.window!!.setLayout((320 * context.resources.displayMetrics.density).roundToInt(), -2)
                }
                try {
                    awaitPlayerFrame()
                    onMain {
                        assertEquals(night, dialog.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        assertEquals(language, androidx.core.os.ConfigurationCompat.getLocales(
                            dialog.context.resources.configuration)[0]?.language)
                        assertTrue(dialog.listView.childCount in 1..20)
                        for (id in listOf(-1, -2, -3)) {
                            val button = dialog.getButton(id)
                            val visible = Rect()
                            assertTrue(button.getLocalVisibleRect(visible))
                            assertEquals(button.height, visible.height())
                            assertEquals(button.width, visible.width())
                            assertTrue(button.height >= 48 * button.resources.displayMetrics.density)
                            assertTrue(button.width >= 48 * button.resources.displayMetrics.density)
                            assertEquals(0, button.scrollX)
                            assertTrue("Framed actions need readable text on the dialog surface",
                                androidx.core.graphics.ColorUtils.calculateContrast(button.currentTextColor,
                                    AccentTheme.color(button, com.google.android.material.R.attr.colorSurface)) >= 4.5)
                            assertTrue(button.height >= button.layout.height + button.compoundPaddingTop + button.compoundPaddingBottom)
                            assertDialogButtonBorder(button, true)
                        }
                    }
                    onData(equalTo(labels[0])).inAdapterView(withId(R.id.playerDialogList)).perform(click())
                    assertTrue(checked[0])
                    assertTrue(onMainResult { dialog.listView.isItemChecked(0) })
                    onData(equalTo(labels.last())).inAdapterView(withId(R.id.playerDialogList)).perform(click())
                    assertTrue(checked.last())
                    assertTrue(onMainResult { dialog.listView.isItemChecked(499) })
                    screenshot("owned-choice-$language-font$scale-night$night")
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    assertFalse(dialog.isShowing)
                } finally { onMain { dialog.dismiss() } }
            }
        }
    }

    @Test fun ownedPanelsRetainDynamicMessagesButtonOverridesAndLifecycleCleanup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: PlayerDialog
            var accepted = 0
            onMain {
                dialog = AccentTheme.dialog(resumedActivity()).setTitle(R.string.details)
                    .setMessage("Initial details").setPositiveButton(R.string.save, null)
                    .setNegativeButton(R.string.close, null).create()
                dialog.setOnShowListener {
                    dialog.getButton(-1).setOnClickListener {
                        accepted++
                        dialog.setMessage("Updated details\n".repeat(50))
                    }
                }
                dialog.show()
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            assertEquals(1, accepted)
            assertTrue(dialog.isShowing)
            onView(withId(android.R.id.message)).inRoot(isDialog()).check(matches(withText("Updated details\n".repeat(50))))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).check(matches(isDisplayed()))
            scenario.recreate()
            await { !dialog.isShowing }
            onView(withId(R.id.nav_player)).check(matches(isDisplayed()))
        }
    }

    @Test fun allThreeLyricActionGlyphsStayCenteredWithinTheirUnchangedButtons() =
        withDownloadedLyricVersions { _, _, _, _ ->
            val sizes = mutableMapOf<Int, Pair<Int, Int>>()
            for (style in BlockStyle.selectable) {
                onMain { app.displaySettings.update { it.copy(blockOutline = BlockOutline(style)) } }
                awaitPlayerFrame()
                onMain {
                    for (id in listOf(R.id.lyricVersions, R.id.nextLyrics, R.id.lyricsTiming)) {
                        val button = resumedActivity().findViewById<ImageView>(id)
                        assertTrue(button.isShown)
                        val size = button.width to button.height
                        assertEquals(sizes.getOrPut(id) { size }, size)
                        val image = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(image)
                            canvas.translate(button.paddingLeft.toFloat(), button.paddingTop.toFloat())
                            canvas.concat(button.imageMatrix)
                            button.drawable.draw(canvas)
                            var left = image.width
                            var top = image.height
                            var right = -1
                            var bottom = -1
                            for (y in 0 until image.height) for (x in 0 until image.width)
                                if (Color.alpha(image.getPixel(x, y)) > 32) {
                                    left = minOf(left, x); right = maxOf(right, x)
                                    top = minOf(top, y); bottom = maxOf(bottom, y)
                                }
                            assertTrue(right > left && bottom > top)
                            assertEquals("Horizontal glyph center, control $id", button.width / 2f, (left + right + 1) / 2f, 1f)
                            assertEquals("Vertical glyph center, control $id", button.height / 2f, (top + bottom + 1) / 2f, 1f)
                        } finally { image.recycle() }
                    }
                }
            }
            screenshot("centered-lyric-action-icons")
        }

    @Test fun spectrumOriginsPersistWithoutMovingTheSpectrumWindow() {
        val tracks = fixtureTracks()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem))
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.prepare()
                    controller.play()
                    app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true, bands = 3)) }
                }
                await { controller.isPlaying && app.spectrum.frames.value.hasAudio }
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                awaitPlayerFrame()
                val bounds = onMainResult {
                    Rect().also { assertTrue(resumedActivity().findViewById<View>(R.id.spectrum).getGlobalVisibleRect(it)) }
                }
                for (origin in SpectrumOrigin.entries) {
                    onMain { app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(origin = origin)) } }
                    instrumentation.waitForIdleSync()
                    val changed = onMainResult {
                        Rect().also { resumedActivity().findViewById<View>(R.id.spectrum).getGlobalVisibleRect(it) }
                    }
                    assertEquals("Only the bars may move, not the spectrum window", bounds, changed)
                    assertEquals(origin, DisplaySettings(app) {}.options.value.spectrum.origin)
                    screenshot("spectrum-origin-${origin.name.lowercase()}")
                }
                onView(withId(R.id.action_settings)).perform(click())
                chooseDropdown(R.id.spectrumBands, app.text(R.string.spectrum_band_count, 8))
                chooseDropdown(R.id.spectrumOrigin, app.text(R.string.position_left))
                assertEquals(8, app.displaySettings.options.value.spectrum.bands)
                assertEquals(SpectrumOrigin.LEFT, app.displaySettings.options.value.spectrum.origin)
                scenario.recreate()
                setting(R.id.spectrumBands).check(matches(withSpinnerText(app.text(R.string.spectrum_band_count, 8))))
                assertEquals(app.displaySettings.options.value.spectrum, DisplaySettings(app) {}.options.value.spectrum)
                onView(withId(R.id.nav_player)).perform(click())
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                assertEquals(SpectrumOrigin.LEFT, app.displaySettings.options.value.spectrum.origin)
                assertTrue(onMainResult { controller.isPlaying })
                screenshot("spectrum-eight-left-portrait")
            } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun inlineLyricTimingPreviewsCancelsSavesAndSurvivesRotation() {
        val tracks = fixtureTracks()
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem))
                    controller.repeatMode = Player.REPEAT_MODE_ONE
                    controller.prepare(); controller.play()
                }
                val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                fun state() = model.lyrics.value
                await { onMainResult {
                    val timing = resumedActivity().findViewById<View>(R.id.lyricsTiming)
                    controller.isPlaying && !state().loading && state().result?.document != null &&
                        timing.isShown && timing.isEnabled
                } }
                awaitPlayerFrame()
                val original = state().result!!
                onMain {
                    val source = resumedActivity().findViewById<View>(R.id.lyricsStatus)
                    val next = resumedActivity().findViewById<View>(R.id.nextLyrics)
                    val timing = resumedActivity().findViewById<View>(R.id.lyricsTiming)
                    assertSame(source.parent, next.parent)
                    assertSame(source.parent, timing.parent)
                    assertTrue(next.left >= source.right)
                    assertTrue(timing.left >= next.right)
                    assertFalse(next.isEnabled)
                    assertEquals((48 * app.resources.displayMetrics.density).roundToInt(), timing.height)
                }
                onView(withId(R.id.lyricsTiming)).perform(click())
                for (id in listOf(R.id.lyricsEarlierFine, R.id.lyricsLaterFine, R.id.lyricsEarlierCoarse,
                    R.id.lyricsLaterCoarse, R.id.lyricsTimingReset)) {
                    onView(withId(id)).inRoot(isDialog()).check { view, missing ->
                        if (missing != null) throw missing
                        val button = view as TextView
                        val surface = com.google.android.material.color.MaterialColors.getColor(button,
                            com.google.android.material.R.attr.colorSurface)
                        assertTrue("Timing buttons need readable text after their fill is removed",
                            androidx.core.graphics.ColorUtils.calculateContrast(button.currentTextColor, surface) >= 4.5)
                    }
                }
                onView(withId(R.id.lyricsEarlierCoarse)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.lyricsEarlierFine)).inRoot(isDialog()).perform(click())
                await { state().result?.document?.offsetMs == 1100L }
                assertEquals(3900L, state().result!!.lyrics!!.lines.first().timeMs)
                assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                await { state().result == original }
                onView(withId(R.id.lyricsTiming)).perform(click())
                onView(withId(R.id.lyricsLaterCoarse)).inRoot(isDialog()).perform(click())
                screenshot("lyrics-timing-preview")
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { !state().loading && state().result?.document?.selected == true }
                assertEquals(-1000L, state().result!!.document!!.offsetMs)
                assertEquals(6000L, state().result!!.lyrics!!.lines.first().timeMs)
                assertEquals(1L, runBlocking { app.lyrics.cache.inspect() }.files)
                scenario.recreate()
                await { !state().loading && state().result?.document?.offsetMs == -1000L }
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                await { !state().loading && state().result?.document?.offsetMs == -1000L }
                screenshot("lyrics-inline-portrait")
                onView(withId(R.id.lyricsTiming)).perform(click())
                onView(withId(R.id.lyricsTimingReset)).inRoot(isDialog()).perform(click())
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { !state().loading && state().result?.document?.offsetMs == 0L }
                assertEquals(original.document!!.text, state().result!!.document!!.text)
                assertEquals(original.lyrics, state().result!!.lyrics)
                assertTrue(onMainResult { controller.isPlaying })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
                runBlocking { app.lyrics.cache.clear() }
            }
        }
    }

    @Test fun onlineLyricsOpensImmediatelyAndSelectsBeforeAllProvidersFinish() {
        val first = CompletableDeferred<Unit>()
        val slow = CompletableDeferred<Unit>()
        withOnlineCatalog(before = { url ->
            if ("mobilecdn" in url) slow.await()
            else if ("fcg_query_lyric_new" !in url) first.await()
        }) { model, controller, requests, _ ->
            val original = model.lyrics.value.result
            onView(withId(R.id.nextLyrics)).perform(click())
            onView(withId(R.id.onlineLyricsList)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withId(R.id.onlineLyricsProgress)).inRoot(isDialog()).check(matches(isDisplayed()))
            assertTrue(model.onlineLyrics.value!!.candidates.isEmpty())
            val duration = model.onlineLyrics.value!!.trackDurationMs
            assertTrue(duration > 0)
            onView(withId(R.id.lyricsTrackDuration)).inRoot(isDialog())
                .check(matches(withText(app.text(R.string.lyrics_music_duration, TimeText.format(duration)))))
            assertEquals(original, model.lyrics.value.result)
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            first.complete(Unit)
            await { model.onlineLyrics.value?.candidates?.size == 3 }
            assertTrue(model.onlineLyrics.value!!.searching)
            assertFalse(requests.any { "fcg_query_lyric_new" in it })
            assertEquals(original, model.lyrics.value.result)
            for (candidate in model.onlineLyrics.value!!.candidates) {
                val label = "${candidate.title} - ${candidate.artist}"
                val time = candidate.durationMs.takeIf { it > 0 }?.let(TimeText::format)
                    ?: app.text(R.string.lyrics_time_unknown)
                onView(withContentDescription(app.text(R.string.lyrics_online_use, label, app.text(R.string.provider_qq))))
                    .inRoot(isDialog()).check(matches(withText("$label\n${app.text(R.string.lyrics_provider_duration, time)}")))
            }
            assertTrue(model.onlineLyrics.value!!.candidates.any { it.durationMs == 0L })
            assertTrue(model.onlineLyrics.value!!.candidates.any { it.durationMs == 62_000L })
            onView(withId(android.R.id.button2)).inRoot(isDialog()).check { view, _ ->
                assertDialogButtonBorder(view, true)
            }
            screenshot("lyrics-online-progressive")
            chooseOnlineMatch("second")
            await { model.onlineLyrics.value == null &&
                model.lyrics.value.result?.lyrics?.lines?.firstOrNull()?.text == "Synthetic selected online words" }
            assertEquals(1, requests.count { "fcg_query_lyric_new" in it })
            assertTrue(requests.last { "fcg_query_lyric_new" in it }.contains("songmid=second"))
            assertTrue(model.lyrics.value.result!!.document!!.offlineSelected)
            assertEquals(1L, runBlocking { app.lyrics.cache.inspect() }.files)
            assertEquals(12_000L, onMainResult { controller.currentPosition })
            assertFalse(onMainResult { controller.playWhenReady })
            screenshot("lyrics-online-selected")
        }
    }

    @Test fun onlineLyricsFailuresKeepCurrentLyricsAndAllowAnotherSelection() {
        withOnlineCatalog { model, controller, _, _ ->
            val original = model.lyrics.value.result
            onView(withId(R.id.nextLyrics)).perform(click())
            await { model.onlineLyrics.value?.searching == false }
            assertEquals(5, model.onlineLyrics.value!!.completed.size)
            chooseOnlineMatch("first")
            await { model.onlineLyrics.value?.issue == com.macarmusic.app.lyrics.OnlineLyricIssue.NO_LYRICS }
            assertEquals(original, model.lyrics.value.result)
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            chooseOnlineMatch("third")
            await { model.onlineLyrics.value?.issue == com.macarmusic.app.lyrics.OnlineLyricIssue.DOWNLOAD_FAILED }
            assertEquals(original, model.lyrics.value.result)
            assertEquals(3, model.onlineLyrics.value!!.candidates.size)
            screenshot("lyrics-online-download-failed")
            chooseOnlineMatch("second")
            await { model.onlineLyrics.value == null }
            assertEquals("Synthetic selected online words", model.lyrics.value.result!!.lyrics!!.lines.first().text)
            assertEquals(12_000L, onMainResult { controller.currentPosition })
        }
    }

    @Test fun onlineLyricsSurvivesRecreationAndCancelsOnCloseOrSongChange() {
        val never = CompletableDeferred<Unit>()
        val active = java.util.concurrent.atomic.AtomicInteger()
        withOnlineCatalog(before = {
            active.incrementAndGet()
            try { never.await() } finally { active.decrementAndGet() }
        }) { model, controller, requests, scenario ->
            val original = model.lyrics.value.result
            onView(withId(R.id.nextLyrics)).perform(click())
            await { active.get() == 2 }
            scenario.recreate()
            onView(withId(R.id.onlineLyricsList)).inRoot(isDialog()).check(matches(isDisplayed()))
            assertSame(model, onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] })
            assertEquals(2, requests.size)
            pressBack()
            await { model.onlineLyrics.value == null && active.get() == 0 }
            await { !model.lyrics.value.loading && model.lyrics.value.result != null }
            assertEquals(original, model.lyrics.value.result)
            onView(withId(R.id.nextLyrics)).perform(click())
            await { active.get() == 2 }
            onMain { controller.seekTo(1, 0) }
            await { model.onlineLyrics.value == null && active.get() == 0 }
            await { model.lyrics.value.trackId == onMainResult { controller.currentMediaItem?.mediaId } &&
                !model.lyrics.value.loading }
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            assertFalse(onMainResult { controller.playWhenReady })
        }
    }

    @Test fun closingOnlineLyricsCancelsChosenDownloadWithoutApplyingOrCachingIt() {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        withOnlineCatalog(before = { url ->
            if ("fcg_query_lyric_new" in url) {
                entered.complete(Unit)
                try { never.await() } finally { cancelled.complete(Unit) }
            }
        }) { model, controller, _, _ ->
            val original = model.lyrics.value.result
            onView(withId(R.id.nextLyrics)).perform(click())
            await { model.onlineLyrics.value?.searching == false }
            chooseOnlineMatch("second")
            await { entered.isCompleted && model.onlineLyrics.value?.busyKey != null }
            pressBack()
            await { model.onlineLyrics.value == null && cancelled.isCompleted }
            assertEquals(original, model.lyrics.value.result)
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            assertEquals(12_000L, onMainResult { controller.currentPosition })
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            assertTrue(model.lyricChoices.value!!.choices.none { it.cached })
            pressBack()
        }
    }

    @Test fun queueLyricDownloadsShowProgressSurviveRotationAndKeepPlaybackAndOfflineUse() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        withOfflineDownloads(before = { url ->
            if ("lyrics.kugou.com/search" in url) { entered.complete(Unit); release.await() }
        }) { model, controller, tracks, requests, scenario ->
            try {
                onMain {
                    controller.setMediaItems(listOf(tracks[0], tracks[0], tracks[1]).map(Track::toMediaItem), 0, 12_000)
                    controller.prepare()
                    controller.play()
                }
                await { onMainResult { controller.isPlaying } }
                onView(withId(R.id.nav_queue)).perform(click())
                onView(withId(R.id.queueDownloadLyrics)).perform(scrollTo(), click())
                runBlocking { withTimeout(10_000) { entered.await() } }
                assertEquals(2, model.lyricDownloads.value!!.total)
                assertEquals(0, model.lyricDownloads.value!!.completed)
                onView(withId(R.id.lyricsDownloadProgress)).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withId(R.id.lyricsDownloadCurrent)).inRoot(isDialog()).check(matches(withText(tracks[0].title)))
                screenshot("lyric-download-progress-landscape")
                val previous = onMainResult { resumedActivity() }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                await {
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().any { it !== previous &&
                            it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                }
                assertSame(model, onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] })
                assertEquals(1, requests.size)
                assertFalse(app.displaySettings.options.value.onlineLyrics)
                screenshot("lyric-download-progress-portrait")
                release.complete(Unit)
                assertDownloadsFinished(model, 2, 2, 0)
                assertEquals(4, requests.size)
                assertTrue(onMainResult { controller.isPlaying })
                assertEquals(3, onMainResult { controller.mediaItemCount })
                assertEquals(0, onMainResult { controller.currentMediaItemIndex })
                onView(withId(R.id.lyricsDownloadProgress)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    val bar = view as android.widget.ProgressBar
                    assertEquals(2, bar.max)
                    assertEquals(2, bar.progress)
                    assertFalse(bar.isIndeterminate)
                }
                screenshot("lyric-download-completed-portrait")
                closeDownloads(model)
                onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
                onView(withId(R.id.nav_player)).perform(click())
                await { model.lyrics.value.result?.document?.downloaded == true && !model.lyrics.value.loading }
                assertTrue(model.lyrics.value.result!!.availableOffline)
                assertEquals(4, requests.size)
            } finally { release.complete(Unit) }
        }
    }

    @Test fun folderSubfolderQueueSongAndPlaylistMenusDownloadOnlyTheirOwnSongs() =
        withOfflineDownloads { model, _, tracks, requests, _ ->
            val source = app.repository.library.value.sources.single()
            val playlist = runBlocking(Dispatchers.IO) {
                app.repository.update {
                    LibraryEdits.addToPlaylist(LibraryEdits.createPlaylist(it, "offline-menu", "Offline fixture"),
                        "offline-menu", tracks.map { song -> song.id })
                }.playlists.single()
            }
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withContentDescription(app.text(R.string.options_for, app.repository.documents.displayName(source))))
                .perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 2, 2, 0)
            assertEquals(4, requests.size)
            closeDownloads(model)
            onView(withText(app.repository.documents.displayName(source))).perform(click())
            await { !model.browser.value.loading && model.browser.value.entries.isNotEmpty() }
            val folder = model.browser.value.entries.first { it.isDirectory }
            val file = model.browser.value.entries.first { !it.isDirectory }
            onView(withContentDescription(app.text(R.string.options_for, folder.name))).perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 1, 0, 1)
            closeDownloads(model)
            onView(withContentDescription(app.text(R.string.options_for, file.name))).perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 1, 0, 1)
            closeDownloads(model)
            onView(withId(R.id.nav_queue)).perform(click())
            onView(allOf(withContentDescription(app.text(R.string.options_for, tracks[0].title)),
                isDescendantOfA(withId(R.id.queueList)))).perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 1, 0, 1)
            closeDownloads(model)
            onView(withId(R.id.nav_playlists)).perform(click())
            onView(withContentDescription(app.text(R.string.options_for, playlist.name))).perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 2, 0, 2)
            closeDownloads(model)
            onView(withText(playlist.name)).perform(click())
            onView(allOf(withContentDescription(app.text(R.string.options_for, tracks[1].title)),
                isDescendantOfA(withId(R.id.playlistList)))).perform(click())
            onView(withText(R.string.lyrics_download)).inRoot(isDialog()).perform(click())
            assertDownloadsFinished(model, 1, 0, 1)
            closeDownloads(model)
            assertEquals("Already available lyrics must not be downloaded again", 4, requests.size)
        }

    @Test fun cancellingBulkLyricsKeepsCompletedSongsAndRestartSkipsThem() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var secondTitle = ""
        withOfflineDownloads(before = { url ->
            if ("lyrics.kugou.com/search" in url &&
                Uri.parse(url).getQueryParameter("keyword") == secondTitle) {
                entered.complete(Unit)
                release.await()
            }
        }) { model, _, tracks, requests, _ ->
            try {
                secondTitle = LyricQueries.keyword(LyricQueries.candidates(LyricQuery.from(tracks[1]), tracks[1].fileName).first(), true)
                onView(withId(R.id.nav_queue)).perform(click())
                onView(withId(R.id.queueDownloadLyrics)).perform(scrollTo(), click())
                runBlocking { withTimeout(10_000) { entered.await() } }
                assertEquals(1, model.lyricDownloads.value!!.saved)
                assertEquals(3, requests.size)
                val generation = app.lyrics.cache.generation
                onMain {
                    model.clearLyricCache()
                    model.downloadLyrics("Duplicate request", tracks)
                }
                assertEquals(generation, app.lyrics.cache.generation)
                assertEquals(3, requests.size)
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                await { model.lyricDownloads.value?.phase == LyricDownloadPhase.CANCELLED && !model.lyricDownloadsBusy }
                assertEquals(1, model.lyricDownloads.value!!.saved)
                assertEquals(0, model.lyricDownloads.value!!.failed)
                assertEquals(1L, runBlocking { app.lyrics.cache.inspect().files })
                assertEquals(3, requests.size)
                screenshot("lyric-download-cancelled")
                closeDownloads(model)
                release.complete(Unit)
                onView(withId(R.id.queueDownloadLyrics)).perform(scrollTo(), click())
                assertDownloadsFinished(model, 2, 1, 1)
                assertEquals(5, requests.size)
                closeDownloads(model)
            } finally { release.complete(Unit) }
        }
    }

    private fun assertDownloadsFinished(model: MusicViewModel, total: Int, saved: Int, kept: Int) {
        await { model.lyricDownloads.value?.phase == LyricDownloadPhase.COMPLETE && !model.lyricDownloadsBusy }
        val state = model.lyricDownloads.value!!
        assertEquals(total, state.total)
        assertEquals(total, state.completed)
        assertEquals(saved, state.saved)
        assertEquals(kept, state.kept)
        assertEquals(0, state.unmatched)
        assertEquals(0, state.failed)
        onView(withId(R.id.lyricsDownloadCounts)).inRoot(isDialog()).check(matches(withText(
            app.text(R.string.lyrics_download_counts, saved, kept, 0, 0, total, total))))
    }

    private fun closeDownloads(model: MusicViewModel) {
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        await { model.lyricDownloads.value == null }
    }

    private fun withOfflineDownloads(
        before: suspend (String) -> Unit = {},
        action: (MusicViewModel, MediaController, List<Track>, List<String>, ActivityScenario<MainActivity>) -> Unit,
    ) {
        lyricMode("missing")
        val tracks = fixtureTracks()
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        val field = LyricsRepository::class.java.getDeclaredField("client").apply { isAccessible = true }
        val previous = field.get(app.lyrics)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        field.set(app.lyrics, OnlineLyricsClient({ url, _, _ ->
            requests.add(url)
            before(url)
            when {
                "lyrics.kugou.com/search" in url -> {
                    val title = kotlinx.serialization.json.JsonPrimitive(Uri.parse(url).getQueryParameter("keyword").orEmpty())
                    """{"status":200,"candidates":[{"id":"42","accesskey":"test-only","song":$title,"singer":"","duration":60000}]}"""
                }
                "lyrics.kugou.com/download" in url -> {
                    val data = android.util.Base64.encodeToString(
                        syntheticOnlineLyrics("[00:01]Synthetic offline download").toByteArray(), android.util.Base64.NO_WRAP)
                    """{"status":200,"content":"$data"}"""
                }
                else -> throw AssertionError("A suitable first provider should stop this fixture: $url")
            }
        }) { _, error -> throw AssertionError(error) })
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    onMain {
                        controller.pause()
                        controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 12_000)
                        controller.prepare()
                    }
                    await { onMainResult { controller.playbackState == Player.STATE_READY } }
                    val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                    awaitPlayerFrame()
                    action(model, controller, tracks, requests, scenario)
                } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
            }
        } finally {
            field.set(app.lyrics, previous)
            runBlocking { app.lyrics.cache.clear() }
        }
    }

    private fun chooseOnlineMatch(id: String) {
        val label = "Synthetic $id match - Fixture singer"
        onView(withContentDescription(app.text(R.string.lyrics_online_use, label, app.text(R.string.provider_qq))))
            .inRoot(isDialog()).perform(click())
    }

    private fun withOnlineCatalog(
        before: suspend (String) -> Unit = {},
        action: (MusicViewModel, MediaController, List<String>, ActivityScenario<MainActivity>) -> Unit,
    ) {
        val tracks = fixtureTracks()
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        val field = LyricsRepository::class.java.getDeclaredField("client").apply { isAccessible = true }
        val previousClient = field.get(app.lyrics)
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        val fixture = OnlineLyricsClient({ url, _, _ ->
            requests.add(url)
            before(url)
            when {
                "lyrics.kugou.com/search" in url -> """{"status":200,"candidates":[]}"""
                "mobilecdn.kugou.com" in url -> """{"status":1,"data":{"info":[]}}"""
                "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[
                    {"songname":"Synthetic first match","singer":[{"name":"Fixture singer"}],"songmid":"first","interval":61},
                    {"songname":"Synthetic second match","singer":[{"name":"Fixture singer"}],"songmid":"second","interval":62},
                    {"songname":"Synthetic third match","singer":[{"name":"Fixture singer"}],"songmid":"third"}]}}}"""
                "fcg_query_lyric_new" in url -> {
                    if ("songmid=third" in url) throw java.io.IOException("Synthetic selected download failure")
                    val text = if ("songmid=first" in url) "" else "[00:00]Synthetic selected online words\n[00:30]Synthetic ending"
                    val data = android.util.Base64.encodeToString(text.toByteArray(),
                        android.util.Base64.NO_WRAP)
                    """{"code":0,"lyric":"$data"}"""
                }
                "music.163.com" in url -> """{"code":200,"result":{"songCount":0}}"""
                "search.kuwo.cn" in url -> """{"abslist":[]}"""
                else -> throw AssertionError(url)
            }
        }) { _, error -> android.util.Log.w("OnlineFixture", "Synthetic provider failure", error) }
        field.set(app.lyrics, fixture)
        try {
            onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = true) } }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    onMain {
                        controller.pause()
                        controller.setMediaItems(tracks.map(Track::toMediaItem))
                        controller.prepare()
                    }
                    await { onMainResult { controller.playbackState == Player.STATE_READY } }
                    onMain { controller.seekTo(12_000) }
                    val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                    await { onMainResult {
                        val button = resumedActivity().findViewById<View>(R.id.nextLyrics)
                        val lyrics = model.lyrics.value
                        !lyrics.loading && lyrics.result?.origin == LyricOrigin.LOCAL_FILE &&
                            lyrics.trackId == controller.currentMediaItem?.mediaId &&
                            controller.currentPosition == 12_000L && button.isShown && button.isEnabled
                    } }
                    awaitPlayerFrame()
                    requests.clear()
                    action(model, controller, requests, scenario)
                } finally { onMain { controller.pause(); MediaController.releaseFuture(future) } }
            }
        } finally {
            field.set(app.lyrics, previousClient)
            runBlocking { app.lyrics.cache.clear() }
        }
    }

    private fun withDownloadedLyricVersions(
        action: (MusicViewModel, MediaController, List<CachedLyric>, ActivityScenario<MainActivity>) -> Unit,
    ) {
        lyricMode("missing")
        val tracks = fixtureTracks()
        val versions = (1..3).map { number ->
            CachedLyric("[00:01]Shared opening words\n[00:02]Synthetic downloaded body $number",
                LyricOrigin.QQ, System.currentTimeMillis(), offlineSelected = true)
        }
        runBlocking {
            app.lyrics.cache.useInternal()
            app.lyrics.cache.clear()
            versions.forEach { app.lyrics.saveTiming(tracks[0], it, 0) }
        }
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 12_000); controller.prepare() }
                val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                await { model.lyrics.value.result?.document?.text == versions.last().text && !model.lyrics.value.loading }
                action(model, controller, versions, scenario)
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
                runBlocking { app.lyrics.cache.clear() }
            }
        }
    }

    private fun lyricChoiceName(model: MusicViewModel, key: String): String {
        val choices = requireNotNull(model.lyricChoices.value).choices
        val index = choices.indexOfFirst { it.key == key }
        assertTrue("The chosen version must still exist", index >= 0)
        return choices[index].name.ifBlank { app.text(R.string.lyrics_version_number, index + 1) }
    }

    private fun lyricChoiceButton(model: MusicViewModel, key: String): ViewInteraction =
        onView(withContentDescription(app.text(R.string.lyrics_version_use, lyricChoiceName(model, key)))).inRoot(isDialog())

    private fun lyricMoreButton(model: MusicViewModel, key: String): ViewInteraction =
        onView(withContentDescription(app.text(R.string.options_for, lyricChoiceName(model, key)))).inRoot(isDialog())

    private fun lyricAction(model: MusicViewModel, key: String, action: Int) {
        lyricChoiceButton(model, key).perform(longClick())
        screenshot("lyrics-menu-${app.resources.getResourceEntryName(action)}")
        onView(withText(app.text(action))).inRoot(isDialog()).perform(click())
    }

    private fun assertLyricPickerRows(model: MusicViewModel, checkLayout: Boolean = false) {
        val duration = requireNotNull(model.lyricChoices.value).trackDurationMs
        assertTrue(duration > 0)
        onView(withId(R.id.lyricsTrackDuration)).inRoot(isDialog())
            .check(matches(withText(app.text(R.string.lyrics_music_duration, TimeText.format(duration)))))
        onView(withId(R.id.lyricsVersionsList)).inRoot(isDialog()).check { view, missing ->
            if (missing != null) throw missing
            val list = view as RecyclerView
            val state = requireNotNull(model.lyricChoices.value)
            assertEquals(state.choices.size, list.childCount)
            for (row in list.children) {
                val choice = state.choices[list.getChildAdapterPosition(row)]
                val choose = row.findViewById<MaterialButton>(R.id.lyricsVersionChoose)
                val best = row.findViewById<MaterialButton>(R.id.lyricsVersionBest)
                val source = row.findViewById<TextView>(R.id.lyricsVersionSource)
                val time = row.findViewById<TextView>(R.id.lyricsVersionTime)
                assertEquals(choice.lastTimestampMs?.let { app.text(R.string.lyrics_last_timestamp, TimeText.format(it)) }
                    ?: app.text(R.string.lyrics_untimed), time.text.toString())
                val selected = state.currentKey == choice.key
                assertEquals(selected, choose.isSelected)
                assertEquals(selected, row.isActivated)
                assertEquals(state.bestKey == choice.key, best.isSelected)
                val background = requireNotNull(choose.backgroundTintList).defaultColor
                if (selected) {
                    assertEquals(255, Color.alpha(background))
                    assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(choose.currentTextColor, background) >= 4.5)
                } else assertEquals(0, Color.alpha(background))
                if (checkLayout) {
                    val header = choose.text.toString().substringBefore('\n')
                    assertTrue(header.contains(choose.context.getString(R.string.lyrics_version_saved)))
                    if (selected) assertTrue(header.contains(choose.context.getString(R.string.lyrics_version_current)))
                    assertEquals("The version and status must share the first rendered line", 0,
                        choose.layout.getLineForOffset(header.length - 1))
                    val density = choose.resources.displayMetrics.density
                    assertTrue("Short source names must not reserve the old fixed-width column", source.width < 68 * density)
                    val more = row.findViewById<View>(R.id.lyricsVersionMore)
                    assertTrue(more.width >= 48 * density && more.height >= 48 * density)
                    assertTrue(choose.width >= list.width - 105 * density)
                    assertLyricCellLayout(row)
                }
            }
        }
    }

    private fun assertLyricCellLayout(row: View) {
        val choose = row.findViewById<MaterialButton>(R.id.lyricsVersionChoose)
        val source = row.findViewById<TextView>(R.id.lyricsVersionSource)
        val time = row.findViewById<TextView>(R.id.lyricsVersionTime)
        val more = bounds(row.findViewById(R.id.lyricsVersionMore))
        val best = bounds(row.findViewById(R.id.lyricsVersionBest))
        val cell = bounds(choose)
        val density = row.resources.displayMetrics.density
        assertTrue(more.right < cell.left && best.left > cell.right)
        for (button in listOf(more, best)) {
            assertEquals("Buttons and lyric cells must align at the top", cell.top, button.top)
            assertEquals("Buttons and lyric cells must align at the bottom", cell.bottom, button.bottom)
        }
        assertSame(row.findViewById(R.id.lyricsVersionCell), source.parent.parent)
        assertTrue("Source belongs inside the lyric cell", cell.contains(bounds(source)))
        assertTrue("Time belongs inside the lyric cell", cell.contains(bounds(time)))
        assertEquals(cell.right - choose.paddingEnd, bounds(source).right)
        assertEquals(android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL, source.gravity)
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, row.resources.displayMetrics),
            source.textSize, 0.1f)
        if ((source.parent as LinearLayout).orientation == LinearLayout.HORIZONTAL)
            assertTrue(bounds(time).right + 7 * density <= bounds(source).left)
        else assertTrue(bounds(time).bottom <= bounds(source).top)
        for (line in 0 until time.layout.lineCount) assertEquals("Time must be fully readable", 0, time.layout.getEllipsisCount(line))
        assertTrue(time.height >= time.layout.height)
        val contentHeight = choose.height - choose.compoundPaddingTop - choose.compoundPaddingBottom
        val textBottom = cell.top + choose.compoundPaddingTop + maxOf(0, (contentHeight - choose.layout.height) / 2) + choose.layout.height
        assertTrue("The preview must not overlap the source/time footer", textBottom <= bounds(time).top)
        if (choose.isSelected) for (text in listOf(source, time)) {
            assertTrue("Active row details must remain readable",
                androidx.core.graphics.ColorUtils.calculateContrast(text.currentTextColor,
                    requireNotNull(choose.backgroundTintList).defaultColor) >= 4.5)
        }
    }

    @Test fun lyricCellsKeepAlignedControlsReadableTimesAndStyledCloseAcrossFontsAndLanguages() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val styles = listOf(null) + BlockStyle.entries.filter { it != BlockStyle.NONE }
            for (language in listOf("en", "zh")) for (scale in listOf(1f, 2f)) for (style in styles) {
                val blocks = style != null
                lateinit var picker: com.macarmusic.app.ui.LyricVersionsDialog
                val selected = mutableListOf<String>()
                val preferred = mutableListOf<String>()
                onMain {
                    val context = ContextThemeWrapper(resumedActivity(), R.style.Theme_ManaPlayer).apply {
                        applyOverrideConfiguration(Configuration().apply {
                            setLocale(java.util.Locale.forLanguageTag(language))
                            fontScale = scale
                        })
                    }
                    val options = DisplayOptions(controlBlocks = blocks, blockOutline = BlockOutline(
                        style ?: BlockStyle.SOLID, "+"))
                    picker = com.macarmusic.app.ui.LyricVersionsDialog(context, { options },
                        { selected.add(it) }, { preferred.add(it) }, { error("Not deleting fixture") }, {})
                    picker.show()
                    picker.update(com.macarmusic.app.lyrics.LyricChoices("song", choices = listOf(
                        com.macarmusic.app.lyrics.LyricChoice("version", context.getString(R.string.lyrics_version_number, 1),
                            LyricOrigin.LOCAL_FILE, cached = true, preview = "Synthetic words",
                            lastTimestampMs = if (blocks) 221_500 else null)),
                        currentKey = "version", loading = false, trackDurationMs = 245_000))
                    val dialog = picker.javaClass.getDeclaredField("dialog").apply { isAccessible = true }
                        .get(picker) as com.macarmusic.app.ui.PlayerDialog
                    dialog.window!!.setLayout(((if (scale == 2f) 300 else 480) * context.resources.displayMetrics.density).roundToInt(), -2)
                }
                try {
                    awaitPlayerFrame()
                    onView(withId(R.id.lyricsVersionsList)).inRoot(isDialog()).check { view, _ ->
                        val row = (view as RecyclerView).getChildAt(0)
                        assertNotNull(row)
                        assertLyricCellLayout(row)
                        assertEquals(3, (view.parent as ViewGroup).childCount)
                        val visible = Rect()
                        assertTrue(view.getLocalVisibleRect(visible))
                        assertEquals("The list must fit above the dialog buttons", view.height, visible.height())
                        view.scrollBy(0, (bounds(row.findViewById(R.id.lyricsVersionSource)).bottom - bounds(view).bottom)
                            .coerceAtLeast(0))
                    }
                    awaitPlayerFrame()
                    onView(withId(R.id.lyricsVersionTime)).inRoot(isDialog()).perform(click())
                    onView(withId(R.id.lyricsVersionSource)).inRoot(isDialog()).perform(click())
                    assertEquals(listOf("version", "version"), selected)
                    onView(withId(R.id.lyricsVersionBest)).inRoot(isDialog()).perform(clickVisibleCenter())
                    await { onMainResult { preferred == listOf("version") } }
                    assertEquals(listOf("version"), preferred)
                    onView(withId(R.id.lyricsVersionSource)).inRoot(isDialog()).perform(longClick())
                    onView(withId(R.id.lyricsVersionActions)).inRoot(isDialog()).check(matches(isDisplayed()))
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).check { view, _ ->
                        assertDialogButtonBorder(view, blocks)
                    }.perform(click())
                    screenshot("lyric-cell-$language-$scale-${style ?: "unframed"}")
                    onView(withId(android.R.id.button2)).inRoot(isDialog()).check { view, _ ->
                        assertDialogButtonBorder(view, blocks)
                    }.perform(click())
                } finally {
                    if (selected.size < 2 || preferred.isEmpty())
                        screenshot("lyric-cell-unreachable-$language-$scale-${style ?: "unframed"}")
                    onMain { picker.dismiss() }
                }
            }
        }
    }

    private fun clickVisibleCenter() = object : androidx.test.espresso.ViewAction {
        override fun getConstraints() = isDisplayed()
        override fun getDescription() = "Tap the visible center of a full-height row button"
        override fun perform(controller: androidx.test.espresso.UiController, view: View) {
            val visible = Rect()
            assertTrue(view.getLocalVisibleRect(visible))
            assertTrue("The real tap must stay inside the visible viewport",
                visible.contains(view.width / 2, view.height / 2))
            click().perform(controller, view)
            controller.loopMainThreadUntilIdle()
        }
    }

    private fun assertDialogButtonBorder(view: View, framed: Boolean) {
        val border = view.foreground
        assertEquals(framed, border is PlayerBlockDrawable)
        if (!framed) return
        requireNotNull(border)
        val visible = Rect()
        assertTrue(view.getLocalVisibleRect(visible))
        assertEquals("Dialog button text must not scroll its border out of the viewport", 0, view.scrollX)
        assertEquals("Dialog button text must not scroll its top border out of the viewport", 0, view.scrollY)
        assertEquals("The full dialog button border must be visible", Rect(0, 0, view.width, view.height), visible)
        assertEquals(Rect(0, 0, view.width, view.height), border.bounds)
        assertFalse("${view.javaClass.name}: border=${border.bounds}, gravity=${view.foregroundGravity}, size=${view.width}x${view.height}",
            border.bounds.isEmpty)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            border.draw(Canvas(bitmap))
            val pixels = IntArray(view.width * view.height)
            bitmap.getPixels(pixels, 0, view.width, 0, 0, view.width, view.height)
            assertTrue("The dialog button border must actually paint visible pixels", pixels.any { Color.alpha(it) > 0 })
        } finally { bitmap.recycle() }
    }

    @Test fun downloadedBestKeepsTheSameVisibleRowAndVersionNumber() = withDownloadedLyricVersions { model, _, _, _ ->
        onView(withId(R.id.lyricVersions)).perform(click())
        await { model.lyricChoices.value?.loading == false }
        val before = model.lyricChoices.value!!
        val target = before.choices.last()
        val name = app.text(R.string.lyrics_version_number, before.choices.size)
        onView(withContentDescription(app.text(R.string.lyrics_best_mark, name))).inRoot(isDialog()).perform(click())
        try {
            await { model.lyricChoices.value?.bestKey == target.key && model.lyricChoices.value?.busy == false }
        } catch (error: AssertionError) {
            screenshot("lyrics-version-best-failure")
            throw AssertionError("Expected Best ${target.key}; picker=${model.lyricChoices.value}; " +
                "lyrics=${model.lyrics.value}; notice=${app.notice.value}", error)
        }
        assertEquals("Marking Best must not move or renumber downloaded lyrics",
            before.choices.map { it.key }, model.lyricChoices.value!!.choices.map { it.key })
        onView(withContentDescription(app.text(R.string.lyrics_best_remove, name))).inRoot(isDialog())
            .check(matches(isDisplayed()))
        assertEquals(before.currentKey, model.lyricChoices.value!!.currentKey)
        assertLyricPickerRows(model)
        for (next in before.choices.dropLast(1)) {
            val number = before.choices.indexOf(next) + 1
            onView(withContentDescription(app.text(R.string.lyrics_best_mark,
                app.text(R.string.lyrics_version_number, number)))).inRoot(isDialog()).perform(click())
            await { model.lyricChoices.value?.bestKey == next.key && model.lyricChoices.value?.busy == false }
            assertEquals(before.choices.map { it.key }, model.lyricChoices.value!!.choices.map { it.key })
            assertLyricPickerRows(model)
        }
    }

    @Test fun downloadedLyricActivationChangesDisplayedWordsAndCurrentChoice() = withDownloadedLyricVersions { model, controller, versions, _ ->
        onView(withId(R.id.lyricVersions)).perform(click())
        await { model.lyricChoices.value?.loading == false }
        val first = model.lyricChoices.value!!
        val best = first.choices.last()
        onView(withContentDescription(app.text(R.string.lyrics_best_mark,
            app.text(R.string.lyrics_version_number, first.choices.size)))).inRoot(isDialog()).perform(click())
        await { model.lyricChoices.value?.bestKey == best.key && model.lyricChoices.value?.busy == false }
        val choices = model.lyricChoices.value!!.choices
        val target = choices.single { it.version?.text == versions[1].text }
        val name = app.text(R.string.lyrics_version_number, choices.indexOf(target) + 1)
        onView(withContentDescription(app.text(R.string.lyrics_version_use, name))).inRoot(isDialog()).perform(click())
        await { model.lyricChoices.value == null && !model.lyrics.value.loading &&
            model.lyrics.value.result?.document?.text == versions[1].text }
        onMain {
            val displayed = resumedActivity().findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter
            assertEquals(versions[1].result().lyrics, displayed.lyrics)
            assertFalse(controller.playWhenReady)
            assertEquals(12_000L, controller.currentPosition)
        }
        onView(withId(R.id.lyricVersions)).perform(click())
        await { model.lyricChoices.value?.loading == false }
        assertEquals(target.key, model.lyricChoices.value!!.currentKey)
        assertEquals(best.key, model.lyricChoices.value!!.bestKey)
        assertLyricPickerRows(model)
    }

    @Test fun lyricPickerActivationDuringUiRestartKeepsTheSongIdentity() = withDownloadedLyricVersions { model, controller, versions, _ ->
        onView(withId(R.id.lyricVersions)).perform(click())
        await { model.lyricChoices.value?.loading == false }
        val choices = model.lyricChoices.value!!.choices
        val target = choices.single { it.version?.text == versions[0].text }
        val name = app.text(R.string.lyrics_version_number, choices.indexOf(target) + 1)
        onMain { model.setLyricsActive(false) }
        assertNull(model.lyrics.value.trackId)
        onView(withContentDescription(app.text(R.string.lyrics_version_use, name))).inRoot(isDialog()).perform(click())
        await { model.lyricChoices.value == null && !model.lyrics.value.loading &&
            model.lyrics.value.result?.document?.text == versions[0].text }
        assertEquals("An explicit lyric choice must keep the song identity while the UI restarts",
            onMainResult { controller.currentMediaItem?.mediaId }, model.lyrics.value.trackId)
        onMain {
            val displayed = resumedActivity().findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter
            assertEquals(versions[0].result().lyrics, displayed.lyrics)
            model.setLyricsActive(true)
        }
    }

    @Test fun activeLyricHighlightAndCompactRowsWorkInBothOrientationsAndLanguages() =
        withDownloadedLyricVersions { model, _, versions, scenario ->
            for (step in 0..3) {
                if (step == 1) rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                if (step == 2) {
                    val previous = onMainResult { resumedActivity() }
                    onMain {
                        if (Build.VERSION.SDK_INT >= 33) {
                            app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                                android.os.LocaleList.forLanguageTags("zh-CN")
                        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
                    }
                    await {
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>().any {
                                it !== previous && it.hasWindowFocus() &&
                                    androidx.core.os.ConfigurationCompat.getLocales(it.resources.configuration)[0]?.language == "zh"
                            }
                    }
                }
                if (step == 3) {
                    rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    val previous = onMainResult { resumedActivity() }
                    onMain { app.themeMode = AppCompatDelegate.MODE_NIGHT_NO }
                    await {
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<MainActivity>().any { it !== previous && it.hasWindowFocus() }
                    }
                }
                awaitPlayerFrame()
                await { !model.lyrics.value.loading && model.lyrics.value.result?.document?.text == versions.last().text }
                onView(withId(R.id.lyricVersions)).perform(click())
                await { model.lyricChoices.value?.loading == false }
                assertLyricPickerRows(model, checkLayout = true)
                screenshot("lyrics-picker-layout-$step")
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            }
        }

    @Test fun lyricMoreButtonOpensActionsWithoutActivatingAndClosesOnSongChange() =
        withDownloadedLyricVersions { model, controller, versions, _ ->
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            val target = model.lyricChoices.value!!.choices.single { it.version?.text == versions[0].text }
            lyricMoreButton(model, target.key).perform(click())
            onView(withId(R.id.lyricsVersionActions)).inRoot(isDialog()).check { view, missing ->
                if (missing != null) throw missing
                val size = (48 * view.resources.displayMetrics.density).roundToInt()
                for (id in listOf(R.id.lyricsVersionActivate, R.id.lyricsVersionToggleBest, R.id.lyricsVersionDelete)) {
                    val action = view.findViewById<MaterialButton>(id)
                    assertTrue(action.isEnabled && action.height >= size)
                    assertTrue(action.foreground is PlayerBlockDrawable)
                }
            }
            assertEquals(versions.last().text, model.lyrics.value.result!!.document!!.text)
            screenshot("lyrics-tap-actions")
            onView(withId(R.id.lyricsVersionToggleBest)).inRoot(isDialog()).perform(click())
            await { model.lyricChoices.value?.bestKey == target.key && model.lyricChoices.value?.busy == false }
            lyricMoreButton(model, target.key).perform(click())
            onView(withId(R.id.lyricsVersionActivate)).inRoot(isDialog()).perform(click())
            await { model.lyricChoices.value == null && model.lyrics.value.result?.document?.text == versions[0].text }
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            lyricMoreButton(model, target.key).perform(click())
            onView(withId(R.id.lyricsVersionActions)).inRoot(isDialog()).check(matches(isDisplayed()))
            onMain { assertFalse(controller.playWhenReady); controller.seekTo(1, 0) }
            await { model.lyricChoices.value == null }
            onView(withId(R.id.lyricsVersionActions)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        }

    @Test fun lyricContextMenuActivatesMarksBestAndConfirmsNoncurrentRemoval() =
        withDownloadedLyricVersions { model, controller, versions, _ ->
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            val best = model.lyricChoices.value!!.choices.single { it.version?.text == versions[0].text }
            lyricAction(model, best.key, R.string.lyrics_version_mark_best)
            await { model.lyricChoices.value?.bestKey == best.key && model.lyricChoices.value?.busy == false }
            val selected = model.lyricChoices.value!!.choices.single { it.version?.text == versions[1].text }
            lyricAction(model, selected.key, R.string.lyrics_version_activate)
            await { model.lyricChoices.value == null && model.lyrics.value.result?.document?.text == versions[1].text }
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            lyricAction(model, best.key, R.string.lyrics_version_delete)
            onView(withText(app.text(R.string.cancel))).inRoot(isDialog()).perform(click())
            assertEquals(3, model.lyricChoices.value!!.choices.size)
            assertEquals(best.key, model.lyricChoices.value!!.bestKey)
            lyricAction(model, best.key, R.string.lyrics_version_delete)
            screenshot("lyrics-version-delete-confirm")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { model.lyricChoices.value?.choices?.size == 2 && model.lyricChoices.value?.busy == false }
            assertNull(model.lyricChoices.value!!.bestKey)
            assertFalse(model.lyricChoices.value!!.choices.any { it.key == best.key })
            assertEquals(selected.key, model.lyricChoices.value!!.currentKey)
            assertEquals(versions[1].text, model.lyrics.value.result!!.document!!.text)
            assertLyricPickerRows(model)
            onMain {
                assertFalse(controller.playWhenReady)
                assertEquals(12_000L, controller.currentPosition)
            }
        }

    @Test fun deletingCurrentBestAndLastLyricsKeepsMusicPlayingAndEmptySelectionSurvivesRecreation() =
        withDownloadedLyricVersions { model, controller, versions, scenario ->
            val trackId = onMainResult { controller.currentMediaItem!!.mediaId }
            onMain { controller.play() }
            await { onMainResult { controller.isPlaying } }
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            val best = model.lyricChoices.value!!.choices.single { it.version?.text == versions[0].text }
            lyricAction(model, best.key, R.string.lyrics_version_mark_best)
            await { model.lyricChoices.value?.bestKey == best.key && model.lyricChoices.value?.busy == false }
            for (remaining in 2 downTo 0) {
                val current = requireNotNull(model.lyricChoices.value!!.currentKey)
                lyricAction(model, current, R.string.lyrics_version_delete)
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { model.lyricChoices.value?.choices?.size == remaining && model.lyricChoices.value?.busy == false }
                if (remaining == 2) assertEquals(best.key, model.lyricChoices.value!!.currentKey)
                if (remaining == 1) {
                    assertNull(model.lyricChoices.value!!.bestKey)
                    assertEquals(versions[1].text, model.lyrics.value.result!!.document!!.text)
                }
                assertTrue(onMainResult { controller.isPlaying })
                assertEquals(trackId, onMainResult { controller.currentMediaItem!!.mediaId })
            }
            assertNull(model.lyrics.value.result!!.lyrics)
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            screenshot("lyrics-versions-empty-after-removal")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.action_settings)).perform(click())
            onView(withId(R.id.nav_player)).perform(click())
            scenario.recreate()
            awaitPlayerFrame()
            await { model.lyrics.value.trackId == trackId && !model.lyrics.value.loading }
            assertNull(model.lyrics.value.result!!.lyrics)
            onMain {
                assertNull((resumedActivity().findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter).lyrics)
                assertTrue(controller.isPlaying)
                assertTrue(controller.currentPosition >= 12_000)
            }
        }

    @Test fun lyricRemovalFinishesAcrossRecreationWithoutStartingACompetingLookup() =
        withDownloadedLyricVersions { model, controller, versions, scenario ->
            lyricMode("versions")
            onView(withId(R.id.lyricVersions)).perform(click())
            await { model.lyricChoices.value?.loading == false }
            val current = requireNotNull(model.lyricChoices.value!!.currentKey)
            app.contentResolver.call(root, "setLyricReadDelay", "3000", null)
            try {
                lyricMoreButton(model, current).perform(click())
                onView(withId(R.id.lyricsVersionActions)).inRoot(isDialog()).check(matches(isDisplayed()))
                onMain { model.removeLyricVersion(current) }
                await { app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") > 0 }
                onView(withId(R.id.lyricsVersionActions)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                scenario.recreate()
                app.contentResolver.call(root, "setLyricReadDelay", "0", null)
                awaitPlayerFrame()
                await { model.lyricChoices.value?.busy == false && !model.lyrics.value.loading }
                assertEquals(versions[1].text, model.lyrics.value.result!!.document!!.text)
                assertEquals(onMainResult { controller.currentMediaItem!!.mediaId }, model.lyrics.value.trackId)
                assertFalse(model.lyricChoices.value!!.choices.any { it.key == current })
                assertEquals(12_000L, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally { app.contentResolver.call(root, "setLyricReadDelay", "0", null) }
        }

    @Test fun offlineLyricPickerSeparatesBestFromCurrentAndSurvivesSettingsAndRotation() {
        lyricMode("versions")
        val tracks = fixtureTracks()
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain {
                    controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0)
                    controller.prepare()
                }
                val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                await { model.lyrics.value.result?.document != null }
                val original = model.lyrics.value.result!!
                onMain {
                    val source = resumedActivity().findViewById<View>(R.id.lyricsStatus)
                    val picker = resumedActivity().findViewById<View>(R.id.lyricVersions)
                    assertSame(source.parent, picker.parent)
                    assertTrue(picker.right <= source.left)
                    assertEquals((48 * app.resources.displayMetrics.density).roundToInt(), picker.height)
                    assertOutlinedWithoutFill(picker)
                }
                onView(withId(R.id.lyricVersions)).perform(click())
                await { model.lyricChoices.value?.loading == false }
                assertEquals(3, model.lyricChoices.value!!.choices.size)
                onView(withId(R.id.lyricsVersionsList)).inRoot(isDialog()).check { view, missing ->
                    if (missing != null) throw missing
                    val list = view as RecyclerView
                    for (row in list.children) {
                        val source = row.findViewById<TextView>(R.id.lyricsVersionSource)
                        val best = row.findViewById<MaterialButton>(R.id.lyricsVersionBest)
                        val choose = row.findViewById<MaterialButton>(R.id.lyricsVersionChoose)
                        assertEquals(app.text(R.string.lyrics_source_local), source.text.toString())
                        assertLyricCellLayout(row)
                        assertTrue(choose.foreground is PlayerBlockDrawable)
                        assertTrue(best.foreground is PlayerBlockDrawable)
                        val surface = com.google.android.material.color.MaterialColors.getColor(choose,
                            com.google.android.material.R.attr.colorSurface)
                        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(choose.currentTextColor, surface) >= 4.5)
                    }
                }
                val unsaved = model.lyricChoices.value!!.choices.first()
                lyricChoiceButton(model, unsaved.key).perform(longClick())
                onView(withId(R.id.lyricsVersionDelete)).inRoot(isDialog()).check { view, missing ->
                    if (missing != null) throw missing
                    assertFalse("The action must disable deletion of an original", view.isEnabled)
                }
                onView(withId(R.id.lyricsVersionActivate)).inRoot(isDialog()).check(matches(isEnabled()))
                screenshot("lyrics-original-actions")
                onView(withText(app.text(R.string.cancel))).inRoot(isDialog()).perform(click())
                val alternate = model.lyricChoices.value!!.choices.single { "Alternate" in it.name }
                onView(withContentDescription(app.text(R.string.lyrics_best_mark, alternate.name))).inRoot(isDialog()).perform(click())
                await { model.lyricChoices.value?.bestKey == alternate.key && model.lyricChoices.value?.busy == false }
                assertEquals(original.lyrics, model.lyrics.value.result!!.lyrics)
                screenshot("lyrics-versions-best-landscape")
                val third = model.lyricChoices.value!!.choices.single { "Third" in it.name }
                onView(withContentDescription(app.text(R.string.lyrics_version_use, third.name))).inRoot(isDialog()).perform(click())
                await { model.lyricChoices.value == null && model.lyrics.value.result?.lyrics?.lines?.first()?.text == "Synthetic local third" }
                assertFalse(onMainResult { controller.playWhenReady })
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.nav_player)).perform(click())
                await { model.lyrics.value.result?.lyrics?.lines?.first()?.text == "Synthetic local third" }
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                await { model.lyrics.value.result?.lyrics?.lines?.first()?.text == "Synthetic local third" }
                onView(withId(R.id.lyricVersions)).perform(click())
                await { model.lyricChoices.value?.loading == false }
                assertEquals(alternate.key, model.lyricChoices.value!!.bestKey)
                screenshot("lyrics-versions-best-portrait")
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                onMain { controller.seekTo(1, 0) }
                await { model.lyrics.value.trackId == tracks[1].id && !model.lyrics.value.loading }
                onMain { controller.seekTo(0, 0) }
                await { model.lyrics.value.result?.lyrics?.lines?.first()?.text == "Synthetic local alternate" }
                assertEquals(alternate.key, model.lyrics.value.result!!.document!!.bestVersionId)
                assertEquals(original.document!!.text,
                    runBlocking(Dispatchers.IO) { LocalLyricsLoader(app, app.repository).load(tracks[0]).document!!.text })
                assertFalse(onMainResult { controller.playWhenReady })
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
                runBlocking { app.lyrics.cache.clear() }
            }
        }
    }

    @Test fun lyricVersionInventoryCannotReopenAfterTheSongChangesDuringARead() {
        lyricMode("versions")
        val tracks = fixtureTracks()
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        onMain { app.displaySettings.update { it.copy(lyricsEnabled = true, onlineLyrics = false) } }
        ActivityScenario.launch(MainActivity::class.java).use {
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0); controller.prepare() }
                val model = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java] }
                await { model.lyrics.value.result?.document != null }
                app.contentResolver.call(root, "setLyricReadDelay", "5000", null)
                onMain { resumedActivity().findViewById<View>(R.id.lyricVersions).performClick() }
                await { app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") > 0 }
                onMain { controller.seekTo(1, 0) }
                await { model.lyricChoices.value == null && model.lyrics.value.trackId == tracks[1].id }
                await { app.contentResolver.call(root, "lyricReadState", null, null)!!.getInt("active") == 0 }
                assertNull(model.lyricChoices.value)
                assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
                onView(withId(R.id.lyricVersions)).check(matches(isDisplayed()))
            } finally {
                app.contentResolver.call(root, "setLyricReadDelay", "0", null)
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun spectrumBandChoicesUseUniformCountsInEnglishAndChinese() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitPlayerFrame()
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsSpectrum).perform(scrollTo(), click())
            for (count in listOf(3, 8, 16, 32)) {
                chooseDropdown(R.id.spectrumBands, app.text(R.string.spectrum_band_count, count))
                assertEquals(count, app.displaySettings.options.value.spectrum.bands)
            }
            val configuration = Configuration(app.resources.configuration).apply { setLocale(java.util.Locale.SIMPLIFIED_CHINESE) }
            val chinese = app.createConfigurationContext(configuration)
            for (count in listOf(3, 8, 16, 32)) {
                assertEquals("$count 个频段", chinese.getString(R.string.spectrum_band_count, count))
            }
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
                    view.configure(SpectrumOptions(enabled = true, palette = palette, fireStyle = style, barStyle = style,
                        bands = 16, peaks = false, colors = colors))
                    view.setPlaybackActive(false)
                    view.setPlaybackActive(true)
                    view.submit(SpectrumFrame(levels, true, 44_100))
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        view.draw(Canvas(bitmap))
                        for ((band, level) in listOf(1f, 0.5f, 0.01f).withIndex()) {
                            val x = (4 * density + (width - 8 * density) / 16 * (band + 0.35f)).toInt()
                            val led = style == FireStyle.LED
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
                                    assertTrue("$palette/$style/$level must use the correct gradient height at $y: " +
                                        "${pixel.toUInt().toString(16)} / ${expected.toUInt().toString(16)}",
                                        kotlin.math.abs(((pixel shr shift) and 0xff) - ((expected shr shift) and 0xff)) <=
                                            if (Build.VERSION.SDK_INT < 29) 4 else 2)
                                }
                            }
                            if (palette == SpectrumPalette.FIRE) {
                                assertTrue("Every Fire bar needs the high color", Color.red(bitmap.getPixel(x, visible.first())) > 220)
                                assertTrue("Every Fire bar needs the low color", Color.green(bitmap.getPixel(x, visible.last())) > 220)
                                val middle = visible.minBy { kotlin.math.abs(it - (bottom + litTop) / 2) }
                                assertTrue("Every Fire bar needs the middle color", Color.red(bitmap.getPixel(x, middle)) > 200 &&
                                    Color.green(bitmap.getPixel(x, middle)) > 200)
                            } else if (level < 0.1f) {
                                assertTrue("Short Classic bars stay at the low end",
                                    Color.red(bitmap.getPixel(x, visible.first())) < if (led) 30 else 10)
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
                view.doOnAttach {
                    it.viewTreeObserver.addOnDrawListener {
                        frames.incrementAndGet()
                        view.submit(SpectrumFrame(levels, true, 44_100))
                    }
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
                for (paletteType in listOf(SpectrumPalette.FIRE, SpectrumPalette.CLASSIC)) for (style in FireStyle.entries) {
                    val previousFrames = frames.get()
                    val drawn = java.util.concurrent.CountDownLatch(1)
                    onMain {
                        view.configure(SpectrumOptions(enabled = true, palette = paletteType,
                            fireStyle = style, barStyle = style, bands = 16, peaks = false,
                            colors = palette))
                        view.setPlaybackActive(false)
                        view.setPlaybackActive(true)
                        view.postOnAnimation { view.postOnAnimation { drawn.countDown() } }
                    }
                    assertTrue(drawn.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    if (Build.VERSION.SDK_INT < 26) await { view.hasWindowFocus() && frames.get() >= previousFrames + 4 }
                    val picture = onMainResult {
                        assertTrue("Exercise the GPU-backed window, not only a software bitmap", view.isHardwareAccelerated)
                        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= 26) {
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
                        } else {
                            val location = onMainResult { IntArray(2).also(view::getLocationOnScreen) }
                            val screen = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                            try {
                                val folder = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
                                File(folder, "legacy-spectrum.png").outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                Canvas(picture).drawBitmap(screen,
                                    Rect(location[0], location[1], location[0] + picture.width, location[1] + picture.height),
                                    Rect(0, 0, picture.width, picture.height), null)
                            } finally { screen.recycle() }
                        }
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
                            val gradientTop = if (paletteType == SpectrumPalette.FIRE) litTop else top
                            val visible = (kotlin.math.ceil(litTop).toInt() until bottom.toInt())
                                .filter { picture.getPixel(x, it) != Color.BLACK }
                            assertTrue("Both short and tall bars must be visible", visible.size >= 3)
                            for (y in visible) {
                                val actual = picture.getPixel(x, y)
                                val expected = palette.colorAt((bottom - y - 0.5f) / (bottom - gradientTop))
                                for (shift in listOf(0, 8, 16)) {
                                    assertTrue("Hardware $paletteType/$style/$level gradient mismatch at $y: " +
                                        "${Integer.toHexString(actual)} vs ${Integer.toHexString(expected)}",
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
                await { resumedActivity().findViewById<SeekBar>(R.id.miniSeek).isEnabled }
                val releasePosition = java.util.concurrent.atomic.AtomicLong(-1)
                val callback = onMainResult { resumedActivity().window.callback }
                scenario.onActivity { activity ->
                    activity.window.callback = object : android.view.Window.Callback by callback {
                        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                val seek = activity.findViewById<SeekBar>(R.id.miniSeek)
                                val location = IntArray(2).also(seek::getLocationOnScreen)
                                // Android 6 resamples injected swipes; verify the delivered release point.
                                releasePosition.set(seekPositionAt(seek, event.rawX - location[0], controller.duration))
                            }
                            return callback.dispatchTouchEvent(event)
                        }
                    }
                }
                try {
                    onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                        { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.7f) }, Press.FINGER))
                } finally { scenario.onActivity { it.window.callback = callback } }
                assertTrue("A substantial seek gesture must reach the player", releasePosition.get() in 30_000..50_000)
                await { kotlin.math.abs(controller.currentPosition - releasePosition.get()) <= 150 }
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
                chooseDropdown(R.id.spectrumBands, app.text(R.string.spectrum_band_count, 16))
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
                // A launcher return can briefly resume landscape before restoring the requested portrait.
                rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                scenario.onActivity {
                    assertTrue(it.findViewById<View>(R.id.action_settings).performClick())
                }
                try {
                    await {
                        val view = resumedActivity().findViewById<View>(R.id.settingsPanel)
                        view != null && view.isShown && view.width > 0 && view.alpha == 1f
                    }
                } catch (error: AssertionError) {
                    screenshot("spectrum-resume-settings-failure")
                    val details = onMainResult {
                        val activity = resumedActivity()
                        val panel = activity.findViewById<View>(R.id.settingsPanel)
                        val action = activity.findViewById<View>(R.id.action_settings)
                        val viewModel = ViewModelProvider(activity)[MusicViewModel::class.java]
                        "screen=${viewModel.selectedScreen.value}, focus=${activity.hasWindowFocus()}, " +
                            "panel=${panel?.isShown}/${panel?.width}/${panel?.alpha}, " +
                            "action=${action?.isShown}/${action?.isEnabled}/${action?.width}"
                    }
                    throw AssertionError("Post-resume Settings transition: $details", error)
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
                onView(withId(R.id.nav_library)).perform(click())
                await { resumedActivity().findViewById<SeekBar>(R.id.miniSeek).isEnabled }
                awaitPlayerFrame()
                var changed = false
                val releasePosition = java.util.concurrent.atomic.AtomicLong(-1)
                scenario.onActivity {
                    val seek = it.findViewById<SeekBar>(R.id.miniSeek)
                    seek.setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN)
                            seek.parent.requestDisallowInterceptTouchEvent(true)
                        if (!changed && event.actionMasked == MotionEvent.ACTION_MOVE) {
                            changed = true
                            controller.seekTo(1, 5_000)
                        }
                        if (event.actionMasked == MotionEvent.ACTION_UP)
                            releasePosition.set(seekPositionAt(seek, event.x, controller.duration))
                        false
                    }
                }
                onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.75f) }, Press.FINGER))
                assertTrue("The synthetic track change must happen during the drag", changed)
                await { controller.currentMediaItemIndex == 1 && controller.playbackState == Player.STATE_READY }
                val position = onMainResult { controller.currentPosition }
                assertTrue("A stale drag sought the new song to $position ms", position in 4_500..5_500)
                assertFalse(onMainResult { controller.playWhenReady })
                releasePosition.set(-1)
                onView(withId(R.id.miniSeek)).perform(GeneralSwipeAction(Swipe.SLOW,
                    { seekCoordinates(it, 0.15f) }, { seekCoordinates(it, 0.5f) }, Press.FINGER))
                assertTrue("The new drag must reach the middle of the song", releasePosition.get() in 20_000..40_000)
                await { kotlin.math.abs(controller.currentPosition - releasePosition.get()) <= 150 }
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

    @Test fun spectrumAnalysisFollowsAllThreeSelectedRatesWithoutChangingAudio() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val source = SpectrumSource(scope)
        val owner = Any()
        val processor = source.audioProcessor()
        val frames = java.util.concurrent.atomic.AtomicInteger()
        scope.launch { source.frames.collect { if (it.hasAudio) frames.incrementAndGet() } }
        val bytes = ByteBuffer.allocate(SpectrumAnalyzer.SAMPLE_COUNT * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(SpectrumAnalyzer.SAMPLE_COUNT) {
                putShort((kotlin.math.sin(2 * Math.PI * 440 * it / 22_050) * 10_000).toInt().toShort())
            }
        }.array()
        try {
            processor.configure(AudioProcessor.AudioFormat(22_050, 1, C.ENCODING_PCM_16BIT))
            processor.flush()
            source.setVisible(owner, true)
            for (rate in listOf(15, 30, 60)) {
                source.setPlaying(false)
                source.setRefreshRate(rate)
                frames.set(0)
                source.setPlaying(true)
                val started = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - started < 1_200) {
                    val input = ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
                    processor.queueInput(input)
                    val output = ByteArray(bytes.size).also { processor.output.get(it) }
                    assertArrayEquals(bytes, output)
                    Thread.sleep(4)
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                val count = frames.get()
                assertTrue("Analysis must remain active at $rate Hz", count >= 4)
                assertTrue("Analysis exceeded the selected $rate Hz limit: $count frames / $elapsed ms",
                    count <= rate * elapsed / 1000 + 2)
                android.util.Log.i("LightweightTest", "Spectrum $rate Hz: $count frames in $elapsed ms")
            }
            source.setVisible(owner, false)
            assertFalse(source.frames.value.hasAudio)
        } finally { processor.reset(); scope.cancel() }
    }

    @Test fun brandLogoUsesTransparentRightSizedAssets() {
        assertEquals(R.drawable.ic_launcher, app.applicationInfo.icon)
        val bitmap = (requireNotNull(app.getDrawable(R.drawable.ic_launcher)) as BitmapDrawable).bitmap
        val density = app.resources.displayMetrics.density
        assertEquals(108 * density, bitmap.width.toFloat(), 1f)
        assertEquals(bitmap.width, bitmap.height)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Logo must retain its warm colors",
            pixels.any { Color.alpha(it) > 240 && Color.red(it) > 170 && Color.red(it) - Color.blue(it) > 60 })
        assertTrue("Logo must retain its cool colors",
            pixels.any { Color.alpha(it) > 240 && Color.blue(it) > 170 && Color.red(it) < 100 })
        val header = requireNotNull(app.getDrawable(R.drawable.ic_brand_logo))
        assertEquals(32 * density, header.intrinsicWidth.toFloat(), 1f)
        assertEquals(header.intrinsicWidth, header.intrinsicHeight)
        val cover = (requireNotNull(app.getDrawable(R.drawable.player_logo)) as BitmapDrawable).bitmap
        assertEquals(512, cover.width)
        assertEquals(512, cover.height)
        assertEquals(0, Color.alpha(cover.getPixel(0, 0)))
    }

    private fun resumedActivity(): MainActivity = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()

    private fun pictureColor(): Int? =
        (resumedActivity().findViewById<ImageView>(R.id.backgroundImage).drawable as? BitmapDrawable)?.bitmap?.getPixel(8, 8)

    private fun chooseDropdown(viewId: Int, label: String) {
        setting(viewId).perform(scrollTo(), click())
        onData(equalTo(label)).perform(click())
        instrumentation.waitForIdleSync()
        awaitPlayerFrame()
    }

    private fun awaitPlayerFrame() = runBlocking {
        kotlinx.coroutines.withTimeout(5_000) {
            withContext(Dispatchers.Main) { resumedActivity().findViewById<View>(R.id.root).awaitDraw() }
        }
    }

    private fun selectCategory(category: SettingsCategory) {
        onView(withId(category.buttonId)).perform(scrollTo())
        tapAfterWindowTransition(category.buttonId)
        await { resumedActivity().findViewById<View>(category.panelId).isShown }
    }

    private fun setting(viewId: Int): ViewInteraction {
        val category = onMainResult {
            val view = resumedActivity().findViewById<View>(viewId)
            SettingsCategory.pages.single { category ->
                generateSequence(view) { it.parent as? View }.any { it.id == category.panelId }
            }
        }
        if (!onMainResult { resumedActivity().findViewById<View>(category.panelId).isShown }) selectCategory(category)
        return onView(withId(viewId))
    }

    private fun seekPositionAt(seek: SeekBar, x: Float, duration: Long): Long {
        val fraction = ((x.toInt() - seek.paddingLeft).toFloat() /
            (seek.width - seek.paddingLeft - seek.paddingRight)).coerceIn(0f, 1f)
        return duration * (fraction * seek.max).toInt() / seek.max
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

    @Test fun collectionsBrowseArtistsAndAlbumsOnDemandWithSelectionAndRotation() {
        val tracks = fixtureTracks().mapIndexed { index, track ->
            track.copy(artist = if (index == 0) "Example Artist" else "Other Artist", album = "Shared album title")
        }
        runBlocking { app.repository.update { it.copy(tracks = tracks) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertTrue(ViewModelProvider(it)[MusicViewModel::class.java].libraryPage.value.groups.isEmpty())
            }
            onView(withId(R.id.nav_library)).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.tracks.size == 2 }
            onView(withId(R.id.libraryBrowse)).perform(scrollTo(), click())
            onView(withText(R.string.library_artists)).inRoot(isDialog()).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.groups.size == 2 }
            scenario.onActivity {
                assertFalse(it.findViewById<View>(R.id.librarySelection).isEnabled)
            }
            screenshot("collections-artists")
            onView(withText("Example Artist")).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.tracks.size == 1 }
            onView(withId(R.id.librarySelection)).perform(scrollTo(), click())
            onView(withText(tracks.first().title)).perform(click())
            scenario.onActivity {
                val model = ViewModelProvider(it)[MusicViewModel::class.java]
                assertEquals(setOf(tracks.first().id), model.librarySelection.value)
                model.selectLibrary(false)
            }
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            scenario.onActivity {
                assertEquals("example artist", ViewModelProvider(it)[MusicViewModel::class.java].libraryPage.value.location.group)
            }
            onView(withId(R.id.libraryBack)).perform(scrollTo(), click())
            onView(withId(R.id.libraryBrowse)).perform(scrollTo(), click())
            onView(withText(R.string.library_albums)).inRoot(isDialog()).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.groups.size == 2 }
            scenario.onActivity {
                val page = ViewModelProvider(it)[MusicViewModel::class.java].libraryPage.value
                assertEquals(setOf("Example Artist", "Other Artist"), page.groups.map { it.artist }.toSet())
                ViewModelProvider(it)[MusicViewModel::class.java].libraryGroup(LibraryBrowsing.key(tracks.first(), LibraryMode.ALBUMS))
            }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.tracks.size == 1 }
            scenario.recreate()
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.tracks.singleOrNull()?.id == tracks.first().id }
            screenshot("collections-album-portrait")
            onView(withId(R.id.nav_player)).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].libraryPage.value.groups.isEmpty() }
        }
    }

    @Test fun collectionPositionsRestoreEachPlaylistAndNestedFolderWithoutAutoplay() {
        val tracks = fixtureTracks()
        val source = app.repository.library.value.sources.single()
        val a = PlaybackCollection.playlist("collection-a")
        val b = PlaybackCollection.playlist("collection-b")
        val folder = PlaybackCollection(CollectionKind.FOLDER, source.uri, "Album")
        val nested = tracks.first { it.sourcePaths[source.uri]?.startsWith("Album/") == true }
        runBlocking {
            app.repository.update { it.copy(playlists = listOf(
                LocalPlaylist(a.id, "Collection A", tracks.map { it.id }),
                LocalPlaylist(b.id, "Collection B", tracks.reversed().map { it.id }))) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val player = future.get(20, TimeUnit.SECONDS)
            try {
                await { app.playbackReady.value }
                scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].requestPlay(tracks, 1, collection = a) }
                await { player.isPlaying && player.currentMediaItem?.mediaId == tracks[1].id }
                onMain { player.pause(); player.seekTo(19000) }
                await { runBlocking { app.playback.read().let { it.collection == a && it.positionMs in 18950..19050 } } }
                scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].requestPlay(tracks.reversed(), 1, collection = b) }
                await { player.isPlaying && player.currentMediaItem?.mediaId == tracks.first().id }
                onMain { player.pause(); player.seekTo(7000) }
                await { runBlocking { app.playback.read().let { it.collection == b && it.positionMs in 6950..7050 } } }
                scenario.onActivity {
                    ViewModelProvider(it)[MusicViewModel::class.java].apply { playlist(a.id); screen(R.id.nav_playlists) }
                }
                onView(withId(R.id.playlistRestorePosition)).perform(scrollTo(), click())
                await { !player.playWhenReady && player.currentMediaItem?.mediaId == tracks[1].id && player.currentPosition in 18950..19050 }
                Thread.sleep(250)
                assertEquals(19000L, onMainResult { player.currentPosition })
                assertFalse(onMainResult { player.isPlaying })
                scenario.recreate()
                assertFalse(onMainResult { player.playWhenReady })
                scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].restorePosition(b) }
                await { !player.playWhenReady && player.currentMediaItem?.mediaId == tracks.first().id && player.currentPosition in 6950..7050 }
                scenario.onActivity {
                    ViewModelProvider(it)[MusicViewModel::class.java].scanFolders(listOf(source),
                        BrowserLocation(source.uri, "music/album", "Album", "Album"), play = true)
                }
                await { player.isPlaying && player.mediaItemCount == 1 && player.currentMediaItem?.mediaId == nested.id }
                onMain { player.pause(); player.seekTo(31000) }
                await { runBlocking { app.playback.read().let { it.collection == folder && it.positionMs in 30950..31050 } } }
                scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].restorePosition(a) }
                await { player.mediaItemCount == 2 && !player.playWhenReady && player.currentPosition in 18950..19050 }
                onView(withId(R.id.nav_folders)).perform(click())
                onView(withText(source.name)).perform(click())
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.entries.any { it.name == "Album" } }
                onView(withText("Album")).perform(click())
                await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.path.lastOrNull()?.relativePath == "Album" }
                onView(withId(R.id.folderRestorePosition)).perform(scrollTo(), click())
                await { !player.playWhenReady && player.mediaItemCount == 1 && player.currentMediaItem?.mediaId == nested.id &&
                    player.currentPosition in 30950..31050 }
                screenshot("collections-restored-paused")
                assertFalse(onMainResult { player.isPlaying })
            } finally { onMain { player.pause(); MediaController.releaseFuture(future) } }
        }
    }

    @Test fun backupRoundTripPreservesSettingsBackgroundsAndReferenceReconnection() {
        val tracks = fixtureTracks()
        val source = app.repository.library.value.sources.single()
        val nested = tracks.first { it.sourcePaths[source.uri]?.startsWith("Album/") == true }
        runBlocking(Dispatchers.IO) {
            app.repository.update { it.copy(favorites = setOf(tracks.first().id),
                sources = it.sources.map { source -> source.copy(pinned = true) },
                playlists = listOf(LocalPlaylist("backup-mix", "Backup mix", tracks.reversed().map { track -> track.id },
                    listOf(PlaylistFolder(source.uri, "music/album", "Album", listOf(nested.id), "Album")),
                    listOf(tracks.first().id)))) }
            app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/background")),
                app.displaySettings::installImages)
            ColorSlotStore(app).save(ColorSlots().define(0, 0x123abc))
        }
        onMain { app.displaySettings.update { it.copy(blockOutline = BlockOutline(BlockStyle.NONE, "+", 36, true),
            spectrum = it.spectrum.copy(refreshRate = 60), widgetTransparency = 85) } }
        val uri = runBlocking { app.backups.sharedExport("zh-CN") }
        try {
            FileProvider.getUriForFile(app, "${app.packageName}.backups", File(app.filesDir, "library.json"))
            fail("The sharing provider must not expose the private library")
        } catch (_: IllegalArgumentException) { }
        val originalImage = app.displaySettings.options.value.imageIds.single()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("backupOrigin", java.util.UUID.randomUUID().toString()).commit()
        runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            app.backgrounds.importImages(listOf(DocumentsContract.buildDocumentUri(AUTHORITY, "pictures/second")),
                app.displaySettings::installImages)
        }
        onMain { app.displaySettings.update { it.copy(blockOutline = BlockOutline(), spectrum = it.spectrum.copy(refreshRate = 15),
            widgetTransparency = 0) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await { app.playbackReady.value }
            onView(withId(R.id.action_settings)).perform(click())
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].previewBackup(uri) }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].preparedBackup.value != null }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { !app.displaySettings.options.value.controlBlocks &&
                app.displaySettings.options.value.blockOutline.style == BlockStyle.SOLID &&
                app.repository.library.value.playlists.any { it.id == "backup-mix" } }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].backupBusy.value == false }
            assertEquals(60, app.displaySettings.options.value.spectrum.refreshRate)
            assertEquals(85, app.displaySettings.options.value.widgetTransparency)
            assertEquals(0x123abc, ColorSlotStore(app).read().colors.first())
            assertEquals(36, app.displaySettings.options.value.blockOutline.arcDp)
            assertTrue(app.displaySettings.options.value.blockOutline.shadow)
            assertNotEquals(originalImage, app.displaySettings.options.value.imageIds.single())
            val pending = app.repository.library.value.sources.single()
            assertTrue(pending.needsReconnect)
            assertTrue(app.repository.library.value.tracks.all { BackupEdits.unavailable(it.uri) })
            screenshot("collections-backup-restored")
            val target = runBlocking(Dispatchers.IO) {
                app.repository.addSource(root, 0).also { app.repository.scan(it, null) {} }
            }
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].reconnectSource(pending, target) }
            await { app.repository.library.value.sources.none { it.needsReconnect } }
            assertEquals(setOf(tracks.first().id), app.repository.library.value.favorites)
            assertEquals(tracks.reversed().map { it.id }, app.repository.library.value.playlists.single().trackIds)
            assertTrue(app.repository.library.value.sources.single().pinned)
            val playlist = app.repository.library.value.playlists.single()
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].updatePlaylistFolders(
                playlist.id, playlist.folders.map { it.location() }) }
            await { app.repository.library.value.playlists.single().folders.single().documentId == "music/album" }
            assertEquals(app.repository.library.value, LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText()))
            val picture = runBlocking { app.backgrounds.render(app.displaySettings.options.value) }
            assertNotNull(picture)
            assertEquals(0xff3474c0.toInt(), picture!!.getPixel(0, 0))
        }
    }

    @Test fun backupCancellationAndMalformedInputLeaveExistingDataUntouched() {
        fixtureTracks()
        val uri = runBlocking { app.backups.sharedExport("en") }
        val before = app.repository.library.value
        val preferences = app.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toMap()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].previewBackup(uri) }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].preparedBackup.value != null }
            val directory = onMainResult { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].preparedBackup.value!!.directory }
            scenario.recreate()
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            await { !directory.exists() }
            assertEquals(before, app.repository.library.value)
            assertEquals(preferences, app.getSharedPreferences("settings", Context.MODE_PRIVATE).all)
            app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write("{broken".toByteArray()) }
            app.notice.value?.let { app.dismissNotice(it.id) }
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].previewBackup(uri) }
            await { app.notice.value?.isError == true && !ViewModelProvider(resumedActivity())[MusicViewModel::class.java].backupBusy.value }
            assertEquals(before, app.repository.library.value)
            assertEquals(preferences, app.getSharedPreferences("settings", Context.MODE_PRIVATE).all)
            screenshot("collections-invalid-backup")
        }
    }

    private fun fixtureTracks(): List<Track> = runBlocking(Dispatchers.IO) {
        app.repository.update { Library() }
        val source = app.repository.addSource(root, 0)
        app.repository.scan(source, null) {}.tracks
    }

    @Test fun bulkFolderRemovalConfirmsKeepsPinsAndStandaloneSongsAndNeverDeletesFiles() {
        val tracks = fixtureTracks()
        val source = app.repository.library.value.sources.single()
        val nestedUri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "music/album")
        val standaloneUri = DocumentsContract.buildDocumentUri(AUTHORITY, "music/one")
        val nested = runBlocking(Dispatchers.IO) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            app.repository.addSource(root, flags)
            val added = app.repository.addSource(nestedUri, flags)
            app.repository.scan(added, null) {}
            app.repository.openDocument(standaloneUri, flags)
            app.repository.update {
                val pinned = LibraryEdits.pinSources(it, setOf(added.uri))
                pinned.copy(favorites = tracks.mapTo(hashSetOf()) { track -> track.id },
                    playlists = listOf(LocalPlaylist("bulk-test", "Bulk test", tracks.map { track -> track.id },
                        listOf(PlaylistFolder(source.uri, "music", "Music", tracks.map { track -> track.id }),
                            PlaylistFolder(added.uri, "music/album", "Album", listOf(tracks.last().id))),
                        emptyList())))
            }
            added
        }
        val before = app.repository.library.value
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                onMain { controller.pause(); controller.clearMediaItems() }
                await { app.playbackReady.value && runBlocking { app.playback.read().queue.isEmpty() } }
            } finally { onMain { MediaController.releaseFuture(future) } }
            rotate(scenario, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withText(source.name)).perform(click())
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.path.isNotEmpty() }
            app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply { putBoolean("resetReads", true) })
            onView(withId(R.id.folderRemoveAll)).perform(scrollTo(), click())
            onView(withId(android.R.id.message)).inRoot(isDialog())
                .check(matches(withText(app.text(R.string.remove_all_folders_body, 1, 1))))
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            assertEquals(before, app.repository.library.value)
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsRemoveAllFolders).perform(scrollTo(), click())
            onView(withText(R.string.remove)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.sources.size == 1 }
            await { ViewModelProvider(resumedActivity())[MusicViewModel::class.java].browser.value.path.isEmpty() }
            await { app.contentResolver.persistedUriPermissions.none { it.uri == root } }
            val kept = app.repository.library.value
            assertEquals(nested.uri, kept.sources.single().uri)
            assertTrue(kept.sources.single().pinned)
            assertEquals(2, kept.tracks.size)
            assertEquals(before.favorites, kept.favorites)
            assertTrue(kept.tracks.all { source.uri !in it.sourceUris && source.uri !in it.sourcePaths &&
                source.uri !in it.sourceParents })
            assertEquals(listOf(nested.uri), kept.playlists.single().folders.map { it.sourceUri })
            assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == nestedUri })
            assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == standaloneUri })
            setting(R.id.settingsRemoveAllFolders).perform(scrollTo(), click())
            await { app.notice.value?.text == app.text(R.string.unpin_first) }
            assertEquals(kept, app.repository.library.value)
            runBlocking { app.repository.update { LibraryEdits.pinSources(it, emptySet()) } }
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withId(R.id.folderRemoveAll)).perform(scrollTo(), click())
            onView(withText(R.string.remove)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.sources.isEmpty() }
            val after = app.repository.library.value
            assertEquals(standaloneUri.toString(), after.tracks.single().uri)
            assertTrue(after.tracks.single().standalonePersisted)
            assertEquals(setOf(after.tracks.single().id), after.favorites)
            assertEquals(listOf(after.tracks.single().id), after.playlists.single().trackIds)
            assertTrue(after.playlists.single().folders.isEmpty())
            assertEquals(after, LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText()))
            await { app.contentResolver.persistedUriPermissions.none { it.uri == nestedUri } }
            assertTrue(app.contentResolver.persistedUriPermissions.any { it.uri == standaloneUri })
            val state = requireNotNull(app.contentResolver.call(root, "scanFixtureState", null, null))
            assertEquals("Removing references must not open music files", 0, state.getInt("audioReads"))
            assertEquals("Removing references must not invoke provider deletion", 0, state.getInt("deleteRequests"))
            assertNotNull(instrumentation.context.contentResolver.openInputStream(standaloneUri)?.use { it.read() })
            scenario.recreate()
            awaitPlayerFrame()
            scenario.onActivity { assertFalse(it.findViewById<View>(R.id.folderRemoveAll).isEnabled) }
            onView(withId(R.id.action_settings)).perform(click())
            setting(R.id.settingsRemoveAllFolders).perform(scrollTo())
            scenario.onActivity { assertFalse(it.findViewById<View>(R.id.settingsRemoveAllFolders).isEnabled) }
            screenshot("settings-bulk-removal")
        }
    }

    @Test fun legacyNoneBackupRestoresAsDisabledControlsWithoutLosingOptions() {
        fixtureTracks()
        val original = app.displaySettings.options.value
        val prepared = runBlocking { app.backups.prepare(app.backups.sharedExport("en")) }
        val legacy = original.copy(controlBlocks = true, blockOutline = BlockOutline(BlockStyle.NONE, "+", 36, true))
        val backup = prepared.copy(manifest = prepared.manifest.copy(
            settings = prepared.manifest.settings.copy(display = legacy)))
        runBlocking { app.backups.restore(backup) }
        val restored = DisplaySettings(app) {}.options.value
        assertFalse(restored.controlBlocks)
        assertEquals(BlockOutline(BlockStyle.SOLID, "+", 36, true), restored.blockOutline)
        assertEquals(restored, app.displaySettings.options.value)
        val exported = runBlocking { app.backups.prepare(app.backups.sharedExport("en")) }
        try {
            assertFalse(exported.manifest.settings.display.controlBlocks)
            assertEquals(restored.blockOutline, exported.manifest.settings.display.blockOutline)
        } finally { runBlocking { app.backups.discard(exported) } }
    }

    @Test fun cachedAccentGradientSoftwareRenderingAssessment() {
        val bitmap = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff90caf9.toInt()
            textSize = 20f
            strokeWidth = 1f
        }
        val paths = List(32) { index ->
            val x = (index % 8) * 128f + 4f
            val y = (index / 8) * 150f + 4f
            android.graphics.Path().apply {
                addRoundRect(android.graphics.RectF(x, y, x + 120, y + 64), 12f, 12f, android.graphics.Path.Direction.CW)
            }
        }
        val gradients = listOf(
            android.graphics.LinearGradient(0f, 0f, 1024f, 0f, paint.color, 0xffd285ff.toInt(), android.graphics.Shader.TileMode.CLAMP),
            android.graphics.LinearGradient(0f, 0f, 0f, 600f, paint.color, 0xffd285ff.toInt(), android.graphics.Shader.TileMode.CLAMP),
            android.graphics.LinearGradient(0f, 0f, 1024f, 600f, paint.color, 0xffd285ff.toInt(), android.graphics.Shader.TileMode.CLAMP),
        )
        fun draw(shader: android.graphics.Shader?): Long {
            paint.shader = shader
            val started = System.nanoTime()
            repeat(100) {
                for (index in paths.indices) {
                    paint.style = android.graphics.Paint.Style.STROKE
                    canvas.drawPath(paths[index], paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    canvas.drawText("Music control", (index % 8) * 128f + 8, (index / 8) * 150f + 42, paint)
                }
            }
            return (System.nanoTime() - started) / 100
        }
        try {
            repeat(3) { draw(null); gradients.forEach(::draw) }
            val solid = LongArray(9)
            val colored = List(gradients.size) { LongArray(9) }
            repeat(solid.size) { run ->
                if (run % 2 == 0) solid[run] = draw(null)
                gradients.forEachIndexed { index, gradient -> colored[index][run] = draw(gradient) }
                if (run % 2 != 0) solid[run] = draw(null)
            }
            fun median(values: LongArray) = values.sorted()[values.size / 2] / 1000.0
            instrumentation.sendStatus(0, Bundle().apply {
                putString("accent_gradient_software_us",
                    "API ${Build.VERSION.SDK_INT}; solid=${median(solid)}; left-right=${median(colored[0])}; " +
                        "top-bottom=${median(colored[1])}; diagonal=${median(colored[2])}; cached shaders, 32 controls")
            })
            assertTrue(bitmap.getPixel(128, 32) != bitmap.getPixel(900, 32))
        } finally { bitmap.recycle() }
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
                val encoded = android.util.Base64.encodeToString(syntheticOnlineLyrics("[00:01]Synthetic online marker").toByteArray(), android.util.Base64.NO_WRAP)
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

    @Test fun incrementalRescansReuseOnlyTrustedUnchangedMetadataAndAllowFullRefresh() = runBlocking(Dispatchers.IO) {
        fun reset(unknown: Boolean = false, offset: Long = 0) {
            app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply {
                putBoolean("resetReads", true)
                putBoolean("unknownStamp", unknown)
                putLong("stampOffset", offset)
            })
        }
        fun reads() = app.contentResolver.call(root, "scanFixtureState", null, null)!!.getInt("audioReads")
        try {
            reset()
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            val initial = app.repository.scan(source, null) {}
            assertEquals(2, reads())
            assertEquals(0, initial.reusedMetadata)
            assertTrue(initial.tracks.all { it.metadataStamp != null })
            reset()
            val quick = app.repository.scan(source, null) {}
            assertEquals(initial.tracks, quick.tracks)
            assertEquals(2, quick.reusedMetadata)
            assertEquals(0, reads())
            reset(offset = 5000)
            assertEquals(0, app.repository.scan(source, null) {}.reusedMetadata)
            assertEquals(2, reads())
            reset(offset = 5000)
            assertEquals(0, app.repository.scan(source, null, forceMetadata = true) {}.reusedMetadata)
            assertEquals(2, reads())
            reset(unknown = true)
            assertEquals(0, app.repository.scan(source, null) {}.reusedMetadata)
            assertEquals(2, reads())
            reset(unknown = true)
            assertEquals(0, app.repository.scan(source, null) {}.reusedMetadata)
            assertEquals(2, reads())
            reset()
            app.repository.scan(source, null, forceMetadata = true) {}
            app.repository.update { library ->
                library.copy(tracks = library.tracks.mapIndexed { index, track -> track.copy(missing = index == 0) })
            }
            reset()
            assertEquals(1, app.repository.scan(source, null) {}.reusedMetadata)
            assertEquals(1, reads())
            app.repository.markSourceError(source.uri, "Synthetic reconnection")
            reset()
            assertEquals(0, app.repository.scan(source, null) {}.reusedMetadata)
            assertEquals(2, reads())
            val preserved = app.repository.library.value
            available(false)
            try { app.repository.scan(source, null) {}; fail("Disconnected provider must fail") }
            catch (_: java.io.IOException) { }
            assertEquals(preserved, app.repository.library.value)
        } finally { available(true); reset() }
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

    @Test fun musicAppDiscoverySupportsModernLegacyAndLocalAudioIntents() {
        fun offered(intent: Intent) = app.packageManager.queryIntentActivities(
            intent.setPackage(app.packageName), PackageManager.MATCH_DEFAULT_ONLY,
        ).any { it.activityInfo.name == MainActivity::class.java.name }
        assertTrue(offered(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)))
        assertTrue(offered(Intent("android.intent.action.MUSIC_PLAYER")))
        for (mime in listOf("audio/mpeg", "audio/flac", "audio/wav", "audio/ogg", "audio/opus", "application/ogg")) {
            assertTrue(mime, offered(Intent(Intent.ACTION_VIEW).setDataAndType(
                DocumentsContract.buildDocumentUriUsingTree(root, "music/one"), mime)))
        }
        assertFalse(offered(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("https://example.invalid/a.mp3"), "audio/mpeg")))
        assertFalse(offered(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/a.mp3"), "audio/mpeg")))
        assertFalse(offered(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://pictures/one"), "image/png")))
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK,
            app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0).launchMode)
    }

    @Test fun externalAudioIntentsHandleColdWarmHistoryAndRejectedUris() {
        runBlocking { app.repository.update { Library() } }
        val first = DocumentsContract.buildDocumentUriUsingTree(root, "music/one")
        val second = DocumentsContract.buildDocumentUriUsingTree(root, "music/album/two")
        fun view(uri: Uri) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/wav")
            .setClass(app, MainActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fun currentActivity() = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val controller = future.get(20, TimeUnit.SECONDS)
        var activity: MainActivity? = null
        try {
            // ActivityScenario filters by the original intent; this flow intentionally replaces it.
            app.startActivity(view(first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { currentActivity() != null }
            activity = onMainResult { requireNotNull(currentActivity()) }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == app.repository.documents.identity(first) }
            assertEquals(1, app.repository.library.value.tracks.size)
            assertFalse(app.repository.library.value.tracks.single().standalonePersisted)
            onMain { controller.pause(); controller.seekTo(7_000) }
            await { !controller.playWhenReady && controller.currentPosition == 7_000L }
            val original = activity
            onMain { original.recreate() }
            await { currentActivity() != null && currentActivity() !== original }
            activity = onMainResult { requireNotNull(currentActivity()) }
            await { controller.currentPosition == 7_000L && !controller.playWhenReady }
            val reused = activity
            onMain { reused.startActivity(view(second)) }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == app.repository.documents.identity(second) }
            onMain { assertSame("Warm audio requests must reuse the activity", reused, currentActivity()) }
            assertEquals(2, app.repository.library.value.tracks.size)
            onMain { controller.pause(); controller.seekTo(8_000) }
            await { controller.currentPosition == 8_000L && !controller.playWhenReady }
            onMain { reused.startActivity(view(first).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)) }
            instrumentation.waitForIdleSync()
            assertEquals(app.repository.documents.identity(second), onMainResult { controller.currentMediaItem?.mediaId })
            assertFalse(onMainResult { controller.playWhenReady })
            for (launch in listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
                Intent("android.intent.action.MUSIC_PLAYER"),
            )) {
                onMain {
                    androidx.lifecycle.ViewModelProvider(reused)[MusicViewModel::class.java].screen(R.id.nav_library)
                    reused.startActivity(launch.setPackage(app.packageName))
                }
                await { androidx.lifecycle.ViewModelProvider(reused)[MusicViewModel::class.java].selectedScreen.value == R.id.nav_player }
                assertEquals(8_000L, onMainResult { controller.currentPosition })
                assertFalse(onMainResult { controller.playWhenReady })
            }
            for (address in listOf("https://example.invalid/music.mp3", "file:///sdcard/music.mp3")) {
                app.notice.value?.let { app.dismissNotice(it.id) }
                onMain { reused.startActivity(view(Uri.parse(address))) }
                await { app.notice.value?.text?.contains(app.getString(R.string.local_documents_only)) == true }
                assertEquals(2, app.repository.library.value.tracks.size)
                assertEquals(app.repository.documents.identity(second), onMainResult { controller.currentMediaItem?.mediaId })
                assertFalse(onMainResult { controller.playWhenReady })
            }
            screenshot("system-audio-intents")
        } finally {
            onMain { controller.pause(); activity?.finish(); MediaController.releaseFuture(future) }
        }
    }

    @Test fun systemNotificationActionsWorkWithoutPlayerUi() {
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            app.repository.scan(app.repository.addSource(root, 0), null) {}.tracks
        }
        fun currentActivity() = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
        val launch = Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        val controller = future.get(20, TimeUnit.SECONDS)
        fun notification(): Notification? = app.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.notification.category == Notification.CATEGORY_TRANSPORT }?.notification
        var activity: MainActivity? = null
        try {
            app.startActivity(Intent(launch))
            await { currentActivity() != null }
            val active = onMainResult { requireNotNull(currentActivity()) }
            activity = active
            val taskId = onMainResult { active.taskId }
            onMain {
                controller.setMediaItems(listOf(tracks[0], tracks[1], tracks[0]).map(Track::toMediaItem), 1, 15_000)
                controller.prepare()
                controller.play()
            }
            await { controller.isPlaying && notification()?.actions?.size == 5 }
            val shown = requireNotNull(notification())
            assertEquals(tracks[1].title, shown.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(shown.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
            @Suppress("DEPRECATION")
            val token = requireNotNull(shown.extras.getParcelable<android.media.session.MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION))
            val native = android.media.session.MediaController(app, token)
            await { native.playbackState?.customActions?.size == 2 }
            onMain { active.moveTaskToBack(true) }
            await { currentActivity() == null }
            assertTrue(instrumentation.uiAutomation.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
            clickSystemMediaButton(app.getString(R.string.pause))
            await { !controller.playWhenReady }
            onMain { controller.seekTo(15_000) }
            await { controller.currentPosition == 15_000L }
            clickSystemMediaButton(app.getString(R.string.rewind))
            await { controller.currentPosition == 5_000L }
            clickSystemMediaButton(app.getString(R.string.forward))
            await { controller.currentPosition == 15_000L }
            clickSystemMediaButton(app.getString(R.string.next))
            await { controller.currentMediaItemIndex == 2 }
            clickSystemMediaButton(app.getString(R.string.previous))
            await { controller.currentMediaItemIndex == 1 && notification()?.actions?.size == 5 }
            instrumentation.uiAutomation.waitForIdle(200, 5_000)
            screenshot("system-shade-paused")
            clickSystemMediaButton(app.getString(R.string.play))
            await { controller.isPlaying }
            native.transportControls.pause()
            await { !controller.playWhenReady }
            val duration = onMainResult { controller.duration }
            onMain { controller.seekTo(duration - 500) }
            await { controller.currentPosition == duration - 500 }
            native.transportControls.sendCustomAction(
                com.macarmusic.app.playback.SystemMediaControls.forward.customAction, Bundle.EMPTY)
            await { controller.currentPosition == duration - 1 }
            assertEquals(1, onMainResult { controller.currentMediaItemIndex })
            assertFalse(onMainResult { controller.playWhenReady })
            requireNotNull(notification()).actions.single { it.title == app.getString(R.string.rewind) }.actionIntent.send()
            await { controller.currentPosition == duration - 10_001 }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            app.startActivity(Intent(launch))
            await { currentActivity()?.hasWindowFocus() == true }
            activity = onMainResult { requireNotNull(currentActivity()) }
            assertEquals(taskId, onMainResult { requireNotNull(currentActivity()).taskId })
            onView(withId(R.id.action_settings)).perform(click())
            requireNotNull(notification()).contentIntent.send()
            await {
                currentActivity()?.let { current ->
                    current.hasWindowFocus() &&
                        androidx.lifecycle.ViewModelProvider(current)[MusicViewModel::class.java].selectedScreen.value == R.id.nav_player
                } == true
            }
            activity = onMainResult { requireNotNull(currentActivity()) }
            assertEquals(taskId, onMainResult { requireNotNull(currentActivity()).taskId })
            assertFalse(onMainResult { controller.playWhenReady })
            assertEquals(3, onMainResult { controller.mediaItemCount })
        } finally {
            if (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == "com.android.systemui") {
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            }
            val closing = onMainResult {
                controller.pause()
                MediaController.releaseFuture(future)
                listOfNotNull(activity, currentActivity()).distinct().also { activities -> activities.forEach { it.finish() } }
            }
            await { closing.all { it.isDestroyed } }
        }
    }

    private fun clickSystemMediaButton(label: String) {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val name = (node.contentDescription ?: node.text)?.toString().orEmpty()
            if (node.isClickable && (name.equals(label, ignoreCase = true) ||
                    name.startsWith("$label ", ignoreCase = true) ||
                    label.startsWith("$name ", ignoreCase = true))) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child)?.let { return it } }
            return null
        }
        val deadline = SystemClock.uptimeMillis() + 10_000
        var observed = "No active window"
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            observed = "Active window: ${root?.packageName}"
            if (root?.packageName?.toString() == "com.android.systemui" && root.refresh()) {
                val button = find(root)
                observed += "; button=${button?.contentDescription}, visible=${button?.isVisibleToUser}, enabled=${button?.isEnabled}"
                // MediaStyle updates can replace a RemoteViews button between lookup and click.
                if (button != null && button.refresh() && button.isVisibleToUser && button.isEnabled &&
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return
                }
                button?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            }
            Thread.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        screenshot("system-shade-missing-button")
        fail("System media button is not visible: $label. $observed")
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
                val accent = onMainResult { resumedActivity().getColor(R.color.accent_blue) and 0xffffff }
                setting(R.id.settingsAccent).perform(scrollTo(), click())
                previewCustomColor(accent)
                onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
                await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks.first().id &&
                    app.displaySettings.options.value.customAccentColor == accent }
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
        awaitPlayerFrame()
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
            // Pre-28 platform browsers cannot identify the caller for Media3's child-list callbacks.
            if (Build.VERSION.SDK_INT >= 28) {
                assertFalse("The fixture must not have privileged media access", connected.getBoolean("trusted"))
                awaitExternal { it.getString("browsedId") == tracks.first().id }
            }
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
            if (Build.VERSION.SDK_INT >= 28) {
                externalCommand("refreshBrowser")
                awaitExternal { it.getString("browsedId") == tracks.last().id }
            }
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
