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
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import androidx.recyclerview.widget.ConcatAdapter
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
import com.google.android.material.textfield.TextInputLayout
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LocalPlaylist
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.NavigationPosition
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
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
import com.macarmusic.app.radio.RadioSection
import com.macarmusic.app.radio.RadioSubscriptions
import com.macarmusic.app.radio.RadioTuning
import com.macarmusic.app.radio.RadioLogoRepository
import com.macarmusic.app.radio.RadioSubscription
import com.macarmusic.app.radio.RadioSubscriptionResult
import com.macarmusic.app.radio.RadioSubscriptionDownload
import com.macarmusic.app.radio.RadioListEntry
import com.macarmusic.app.radio.RadioRefreshInterval
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.QueueMode
import com.macarmusic.app.ui.SettingsCategory
import com.macarmusic.app.ui.RadioButtonAdapter
import com.macarmusic.app.ui.awaitDraw
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
        instrumentation.setInTouchMode(true)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true)
            .putStringSet("radioFavorites", emptySet()).putBoolean("radioFavoritesOnly", false)
            .putBoolean("radioEnabled", true).remove("radioPins").remove("radioTuning").remove("radioPinColor")
            .remove("radioOpenPlayerOnSelection").remove("radioHiddenGroups")
            .remove("radioNavigationMode").remove("radioButtonLogo").remove("radioStationLogos").commit()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .remove("radioRefreshInterval").remove("radioAutomaticRefreshAttempt").commit()
        instrumentation.context.startActivity(Intent().setClassName("${app.packageName}.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        runBlocking { app.repository.awaitReady() }
        onMain {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                    android.os.LocaleList.forLanguageTags("en")
            } else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("en"))
            app.radio.publishRestored()
            app.notice.value?.let { app.dismissNotice(it.id) }
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false,
                autoPlayOnLaunch = false,
                navigationPosition = NavigationPosition.BOTTOM, showLibrary = true, showQueue = true, showPlaylists = true,
                appLogo = com.macarmusic.app.model.AppLogo.MUSIC, buttonIcons = com.macarmusic.app.model.ButtonIconStyle.MONOCHROME) }
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
        network.subscriptionRelease?.countDown()
        onMain { app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL) }
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
            app.radio.showGroups(app.radio.options.value.hiddenGroups)
            app.displaySettings.update { it.copy(showLibrary = true, showQueue = true, showPlaylists = true,
                autoPlayOnLaunch = false,
                appLogo = com.macarmusic.app.model.AppLogo.MUSIC, buttonIcons = com.macarmusic.app.model.ButtonIconStyle.MONOCHROME) }
        }
    }

    @Test fun radioRowsAndStickyHeadingUseTheGeneralButtonOutline() {
        val original = app.displaySettings.options.value
        importPrograms("#EXTM3U\n" + (0 until 60).joinToString("\n") {
            "#EXTINF:-1 group-title=\"US\",Outline station $it\nhttps://radio.example/outline-$it.mp3"
        })
        try {
            onMain { app.displaySettings.update {
                it.copy(controlBlocks = true, blockOutline = BlockOutline(BlockStyle.DASHED_ARC, "+", 28, true))
            } }
            ActivityScenario.launch(MainActivity::class.java).use {
                val activity = settledMain()
                val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
                await { activity.findViewById<View>(R.id.settingsGeneralPanel)?.isShown == true }
                onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
                await { activity.findViewById<View>(R.id.radioSearch)?.isShown == true }
                onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("Outline station") }
                await {
                    val stations = model.radioPage.value
                    val groups = model.radioChannels.value.filter { it.section == null }
                    stations.size == 60 && stations.all { it.name.startsWith("Outline station ") } &&
                        groups.size == 1 && groups.single().let {
                            it.station == null && it.group.id == "united states" && !it.expanded
                        }
                }
                val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
                val styles = listOf(BlockStyle.DASHED_ARC) + BlockStyle.selectable.filterNot { it == BlockStyle.DASHED_ARC } + listOf(null)
                for (style in styles) {
                    println("RADIO_OUTLINE_STYLE ${style?.name ?: "disabled"}")
                    val outline = BlockOutline(style ?: BlockStyle.DASHED_ARC, "+", 28, true)
                    onMain { app.displaySettings.update { it.copy(controlBlocks = style != null, blockOutline = outline) } }
                    instrumentation.waitForIdleSync()
                    val group = radioGroupButton(activity, "united states") as MaterialButton
                    onMain {
                        assertGeneralBorder(group, style != null, outline)
                        if (model.radioChannels.value.none { it.expanded }) group.performClick()
                    }
                    await { model.radioChannels.value.count { it.station != null } == 60 && radioRowsSettled(activity, grid) }
                    val position = onMain { model.radioChannels.value.indexOfFirst { it.station?.name == "Outline station 40" } }
                    assertTrue("The filtered fixture must contain station 40", position >= 0)
                    onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
                    await { activity.findViewById<View>(R.id.radioStickyGroup).isShown &&
                        grid.findViewHolderForAdapterPosition(position) != null && !grid.isLayoutRequested }
                    onMain {
                        val row = grid.findViewHolderForAdapterPosition(position)!!.itemView as MaterialButton
                        val sticky = activity.findViewById<MaterialButton>(R.id.radioStickyGroup)
                        assertGeneralBorder(row, style != null, outline)
                        assertGeneralBorder(sticky, style != null, outline)
                        assertEquals(255, android.graphics.Color.alpha(sticky.backgroundTintList!!.defaultColor))
                        assertTrue(row.requestFocusFromTouch())
                        assertTrue(row.foreground.state.contains(android.R.attr.state_focused))
                    }
                    println("RADIO_OUTLINE_STYLE_PASSED ${style?.name ?: "disabled"}")
                    if (style in listOf(BlockStyle.DASHED_ARC, BlockStyle.CHARACTERS, null))
                        screenshot("update1501-channel-outline-${style?.name ?: "disabled"}")
                }
                assertEquals(0, network.connectionAttempts.get())
                assertFalse(onMain { controller.playWhenReady })
            }
        } finally {
            onMain { app.displaySettings.update { it.copy(controlBlocks = original.controlBlocks, blockOutline = original.blockOutline) } }
        }
    }

    @Test fun subscriptionRowsReuseGeneralOutlinesForUpdatesAndNewEntries() {
        val original = app.displaySettings.options.value
        val subscription = runBlocking { app.radioCatalog.saveSubscription("Outline playlist", "https://radio.example/list.m3u") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val activity = settledMain()
                onMain {
                    ViewModelProvider(activity)[MusicViewModel::class.java].apply {
                        settingsCategory(SettingsCategory.RADIO)
                        openSettings()
                    }
                }
                await { activity.findViewById<View>(R.id.radioSubscriptions)?.isShown == true }
                onMain { activity.findViewById<View>(R.id.radioSubscriptions).performClick() }
                lateinit var rows: android.widget.LinearLayout
                onView(withId(R.id.radioSubscriptionRows)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    rows = view as android.widget.LinearLayout
                }
                await { rows.findViewWithTag<View>(subscription.id)?.isEnabled == true }
                val row = onMain { rows.findViewWithTag<MaterialButton>(subscription.id) }
                for (style in BlockStyle.selectable + listOf(null)) {
                    val outline = BlockOutline(style ?: BlockStyle.DASHED_ARC, "+", 28, true)
                    onMain { app.displaySettings.update { it.copy(controlBlocks = style != null, blockOutline = outline) } }
                    instrumentation.waitForIdleSync()
                    onMain {
                        assertSame(row, rows.findViewWithTag<MaterialButton>(subscription.id))
                        assertGeneralBorder(row, style != null, outline)
                        assertTrue(row.requestFocusFromTouch())
                        assertTrue(row.foreground.state.contains(android.R.attr.state_focused))
                    }
                    if (style == BlockStyle.DASHED_ARC) {
                        val added = runBlocking { app.radioCatalog.saveSubscription("Second outline playlist", "https://radio.example/second.m3u") }
                        await { rows.findViewWithTag<View>(added.id)?.let {
                            it.isEnabled && it.width > 0 && !it.isLayoutRequested
                        } == true }
                        onMain { assertGeneralBorder(rows.findViewWithTag(added.id), true, outline) }
                        val foreground = onMain { row.foreground }
                        runBlocking { app.radioCatalog.changeSubscription(subscription, enabled = false) }
                        await { row.isEnabled && !app.radioCatalog.state.value.subscriptions.first().enabled }
                        onMain { assertSame("State updates must reuse the row decoration", foreground, row.foreground) }
                        screenshot("update1501-subscription-outline")
                    }
                }
                assertEquals(0, network.connectionAttempts.get())
                assertFalse(onMain { controller.playWhenReady })
            }
        } finally {
            onMain { app.displaySettings.update { it.copy(controlBlocks = original.controlBlocks, blockOutline = original.blockOutline) } }
        }
    }

    @Test fun radioSearchAndSettingsHaveAlignedVisibleBounds() {
        val original = app.displaySettings.options.value
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                var activity = settledMain()
                for (orientation in listOf(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                    onMain { activity.requestedOrientation = orientation }
                    activity = settledMain()
                    await { activity.resources.configuration.orientation ==
                        if (orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT }
                    onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
                    await { activity.findViewById<View>(R.id.radioSettings)?.isShown == true }
                    for (style in listOf(BlockStyle.DASHED_ARC, BlockStyle.CHARACTERS, null)) {
                        onMain { app.displaySettings.update { it.copy(controlBlocks = style != null,
                            blockOutline = BlockOutline(style ?: BlockStyle.SOLID)) } }
                        instrumentation.waitForIdleSync()
                        val input = onMain { activity.findViewById<EditText>(R.id.radioSearch) }
                        val settings = onMain { activity.findViewById<MaterialButton>(R.id.radioSettings) }
                        onMain {
                            fun bounds(view: View) = IntArray(2).also(view::getLocationOnScreen).let {
                                android.graphics.Rect(it[0], it[1], it[0] + view.width, it[1] + view.height)
                            }
                            val field = bounds(input)
                            val action = bounds(settings).apply { top += settings.insetTop; bottom -= settings.insetBottom }
                            if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                assertEquals("Search and Radio settings top edges", field.top, action.top)
                                assertEquals("Search and Radio settings bottom edges", field.bottom, action.bottom)
                                assertTrue(field.right < action.left)
                            } else assertTrue("Narrow layout keeps settings below search", action.top >= field.bottom)
                            assertTrue(input.height >= 48 * activity.resources.displayMetrics.density)
                            assertTrue(settings.height >= 48 * activity.resources.displayMetrics.density)
                            assertTrue(input.requestFocusFromTouch())
                            input.setText("Clear this query")
                        }
                        await { input.text.toString() == "Clear this query" }
                        lateinit var layout: TextInputLayout
                        onMain {
                            var parent = input.parent
                            while (parent !is TextInputLayout) parent = parent.parent
                            layout = parent
                        }
                        val clear = onMain { layout.findViewById<View>(com.google.android.material.R.id.text_input_end_icon) }
                        await { clear.isShown }
                        onMain { clear.performClick() }
                        await { input.text.isNullOrEmpty() }
                        screenshot("update1501-radio-aligned-$orientation-${style?.name ?: "disabled"}")
                    }
                    onMain { activity.findViewById<View>(R.id.radioSettings).performClick() }
                    await { activity.findViewById<View>(R.id.settingsRadioPanel)?.isShown == true }
                }
            }
        } finally {
            onMain { app.displaySettings.update { it.copy(controlBlocks = original.controlBlocks, blockOutline = original.blockOutline) } }
        }
    }

    @Test fun cachedCctvProgramsHaveTheirOwnVisibleCategoryWithoutChangingSavedStations() {
        val entries = checkNotNull(com.macarmusic.app.radio.RadioBundledDefaults::class.java
            .getResourceAsStream("/radio/subscriptions/cctv.m3u")).use { RadioList.parse(it, subscription = true).entries }
        runBlocking {
            val subscription = app.radioCatalog.saveSubscription("Saved CCTV audio", RadioSubscriptions.CCTV_AUDIO_URL)
            app.radioCatalog.refreshSubscription(subscription.id, emptyMap(), {
                RadioSubscriptionDownload(entries.map { it.copy(group = "National") })
            })
        }
        val snapshot = runBlocking { app.radioCatalog.snapshot() }
        assertEquals(20, app.radioCatalog.state.value.programs.count { RadioGroups.of(it).id == "cctv television" })
        onMain { app.radio.hideGroups(setOf("national")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await(diagnostic = { "CCTV rows: ${model.radioChannels.value}; query: ${model.radioQuery.value}" }) {
                model.radioChannels.value.any { it.group.id == "cctv television" }
            }
            val heading = radioGroupButton(activity, "cctv television") as MaterialButton
            onMain { assertEquals("CCTV Television", heading.text.toString()); heading.performClick() }
            await { model.radioChannels.value.count { it.station != null } == 20 }
            assertTrue(model.radioChannels.value.filter { it.station != null }.all { it.group.id == "cctv television" })
            screenshot("update1501-cctv-television")
            network.customPlaylist = finitePlaylist()
            playProgram(app.radioCatalog.state.value.programs.single { it.id == RadioDefaults.CCTV_ID })
            val connections = network.manifests.get()
            onMain { app.radio.hideGroups(setOf("cctv television")) }
            await { model.radioChannels.value.none { it.group.id == "cctv television" } }
            assertTrue(onMain { controller.isPlaying })
            assertEquals(connections, network.manifests.get())
            assertEquals(snapshot, runBlocking { app.radioCatalog.snapshot() })
            assertTrue(RadioSettings(app).options.value.hiddenGroups.containsAll(setOf("national", "cctv television")))
            onMain { app.radio.showGroups(setOf("cctv television")) }
            await { model.radioChannels.value.any { it.group.id == "cctv television" } }
            assertTrue(app.radio.options.value.hiddenGroups.contains("national"))
        }
    }

    private fun assertGeneralBorder(button: MaterialButton, enabled: Boolean, outline: BlockOutline) {
        fun find(drawable: android.graphics.drawable.Drawable?): com.macarmusic.app.ui.PlayerBlockDrawable? = when (drawable) {
            is com.macarmusic.app.ui.PlayerBlockDrawable -> drawable
            is android.graphics.drawable.LayerDrawable ->
                (0 until drawable.numberOfLayers).firstNotNullOfOrNull { find(drawable.getDrawable(it)) }
            else -> null
        }
        val border = find(button.foreground)
        if (!enabled) {
            assertNull("Disabled blocks must restore the ordinary button border", border)
            assertTrue(button.strokeWidth > 0)
            return
        }
        assertNotNull("Playlist and channel buttons must use the shared block drawable", border)
        assertEquals("No second Material outline", 0, button.strokeWidth)
        val actual = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        val expected = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        try {
            border!!.draw(android.graphics.Canvas(actual))
            com.macarmusic.app.ui.PlayerBlockDrawable(outline,
                com.google.android.material.color.MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline),
                button.resources.displayMetrics.density).apply {
                setBounds(0, 0, button.width, button.height)
                draw(android.graphics.Canvas(expected))
            }
            assertTrue("The border must match the saved style, radius, character and shadow", actual.sameAs(expected))
        } finally { actual.recycle(); expected.recycle() }
    }

    @Test fun upKeyReachesMainHeaderFromPinnedRadio() {
        network.customPlaylist = finitePlaylist()
        playStation(0)
        val playing = onMain { controller.currentMediaItem?.mediaId }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            val stationId = app.radio.options.value.pins.first()
            fun button() = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
                ?.findViewWithTag<View>(stationId)
            await { button()?.isShown == true }
            onMain { assertTrue(button()!!.requestFocusFromTouch()) }
            await { activity.currentFocus?.tag == stationId }
            onMain {
                val logo = activity.findViewById<View>(R.id.homeLogo)
                val settings = activity.findViewById<View>(R.id.action_settings)
                val root = activity.findViewById<android.view.ViewGroup>(R.id.root)
                val next = android.view.FocusFinder.getInstance().findNextFocus(root, button(), View.FOCUS_UP)
                println("HEADER_FOCUS logo=${logo.isFocusable}/${logo.hasFocusable()}, " +
                    "settings=${settings.isFocusable}/${settings.hasFocusable()}, " +
                    "next=${next?.id}/${next?.javaClass?.simpleName}, " +
                    "search=${button()!!.focusSearch(View.FOCUS_UP)?.id}, " +
                    "blocks=${activity.findViewById<android.view.ViewGroup>(R.id.toolbar).touchscreenBlocksFocus}")
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            await(diagnostic = {
                "Up from first pin: focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}"
            }) {
                activity.currentFocus?.id in listOf(R.id.homeLogo, R.id.action_settings)
            }
            onMain {
                assertEquals(R.id.homeLogo, activity.currentFocus?.id)
                assertNotNull("The logo must visibly show remote focus", activity.currentFocus?.foreground)
                assertTrue(activity.currentFocus!!.foreground.state.contains(android.R.attr.state_focused))
            }
            screenshot("update1500-header-logo-focus")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await(diagnostic = { "Right in header: focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}" }) {
                activity.currentFocus?.id == R.id.action_settings
            }
            onMain {
                assertNotNull("Settings must visibly show remote focus", activity.currentFocus?.foreground)
                assertTrue(activity.currentFocus!!.foreground.state.contains(android.R.attr.state_focused))
            }
            screenshot("update1500-header-settings-focus")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            await { activity.currentFocus?.id == R.id.homeLogo }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await { activity.currentFocus?.tag == stationId }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            await { activity.currentFocus?.id == R.id.homeLogo }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { activity.currentFocus?.id == R.id.action_settings }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { activity.findViewById<View>(R.id.settingsPanel)?.isShown == true }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await { button()?.isShown == true && activity.currentFocus?.tag == stationId }
            assertEquals(playing, onMain { controller.currentMediaItem?.mediaId })
            assertTrue(onMain { controller.isPlaying })
        }
    }

    @Test fun musicCanNavigateIntoTheHeaderAndActivateBothButtons() {
        onMain { controller.prepare(); controller.play() }
        await { controller.isPlaying }
        val playing = onMain { controller.currentMediaItem?.mediaId }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            await { activity.findViewById<View>(R.id.favorite)?.isShown == true }
            onMain { assertTrue(activity.findViewById<View>(R.id.favorite).requestFocusFromTouch()) }
            for (step in 0 until 12) {
                if (onMain { activity.currentFocus?.id in setOf(R.id.homeLogo, R.id.action_settings) }) break
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            }
            await { activity.currentFocus?.id in setOf(R.id.homeLogo, R.id.action_settings) }
            if (onMain { activity.currentFocus?.id } == R.id.homeLogo)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { activity.currentFocus?.id == R.id.action_settings }
            onMain { assertTrue(activity.currentFocus!!.foreground.state.contains(android.R.attr.state_focused)) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { activity.findViewById<View>(R.id.settingsPanel)?.isShown == true }
            onMain { assertTrue(activity.findViewById<View>(R.id.homeLogo).requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { activity.findViewById<View>(R.id.favorite).isShown &&
                !activity.findViewById<View>(R.id.settingsPanel).isShown }
            assertEquals(playing, onMain { controller.currentMediaItem?.mediaId })
            assertTrue(onMain { controller.isPlaying })
            screenshot("update1500-music-header")
        }
    }

    @Test fun cctvUpgradeOnlyRevealsNationalAndDoesNotResetRadioChoices() {
        onMain {
            val pins = app.radio.options.value.pins
            assertTrue(app.radio.reorderPins(pins, pins.reversed()))
            app.radio.hideGroups(setOf(RadioGroups.national.id, "country:de", "guangdong"))
            app.radio.setStationLogo(RadioDefaults.CCTV_ID, com.macarmusic.app.radio.RadioStationLogo.JAZZ)
            app.radio.setPanelVisible(false)
        }
        val before = app.radio.options.value
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putInt("radioDefaultsApplied", 1).commit()
        val upgraded = RadioSettings(app)
        upgraded.applyBundledDefaults(RadioGroups.defaultHiddenGroups(app.radioCatalog.state.value.programs))
        assertEquals(before.copy(hiddenGroups = before.hiddenGroups - RadioGroups.national.id), upgraded.options.value)
        assertFalse(upgraded.needsBundledDefaults)
        upgraded.hideGroups(setOf(RadioGroups.national.id))
        val restarted = RadioSettings(app)
        restarted.applyBundledDefaults(emptySet())
        assertEquals(upgraded.options.value, restarted.options.value)
    }

    @Test fun cctvSubscriptionPlaysAllTwentyChannelsAndKeepsAudioAndCacheThroughUpdates() {
        val url = RadioSubscriptions.CCTV_AUDIO_URL
        val body = checkNotNull(com.macarmusic.app.radio.RadioBundledDefaults::class.java
            .getResourceAsStream("/radio/subscriptions/cctv.m3u")).bufferedReader().use { it.readText() }
        network.subscriptionBodies[url] = body.replace("#EXTM3U\n",
            "#EXTM3U\n#EXT-X-APP APTV\n#EXT-X-SUB-URL https://example.com/not-followed\n") +
            "#EXTINF:-1,Unrelated station\nhttps://radio.example/unrelated\n"
        network.subscriptionEtags[url] = "\"cctv-one\""
        network.customPlaylist = finitePlaylist()
        val subscription = runBlocking {
            app.radioCatalog.saveSubscription("CCTV audio", url).also {
                assertEquals(RadioSubscriptionResult.UPDATED, app.radioCatalog.refreshSubscription(it.id, emptyMap()))
            }
        }
        assertEquals(1, network.subscriptions.get())
        assertEquals(0, network.manifests.get())
        assertEquals(tracks[1].id, onMain { controller.currentMediaItem?.mediaId })
        assertFalse(onMain { controller.playWhenReady })
        val cached = app.radioCatalog.state.value.subscriptions.single()
        assertEquals(20, cached.programs.size)
        assertTrue(cached.programs.all { RadioGroups.of(it) == RadioGroups.cctvTelevision && it.logoUri.isEmpty() })
        assertEquals(20, app.radioCatalog.state.value.programs.count { program ->
            program.sources.any { "/audio/cctv" in it } })
        assertEquals(1, app.radioCatalog.state.value.programs.count { it.id == RadioDefaults.CCTV_ID })
        for (station in cached.programs) playProgram(station)
        val playing = onMain { controller.currentMediaItem?.mediaId }
        val connections = network.manifests.get()
        assertEquals(RadioSubscriptionResult.UNCHANGED,
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) })
        assertEquals(1, network.subscriptionConditionals.get())
        network.subscriptionEtags[url] = "\"broken\""
        network.subscriptionBodies[url] = "#EXTM3U\n#EXTINF:-1,CCTV-1 Truncated"
        assertEquals(RadioSubscriptionResult.FAILED,
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) })
        val failed = app.radioCatalog.state.value.subscriptions.single()
        assertEquals(cached.programs, failed.programs)
        assertNotNull(failed.failure)
        runBlocking { app.radioCatalog.changeSubscription(failed, enabled = false) }
        assertNull(app.radioCatalog.find(checkNotNull(playing)))
        assertEquals(playing, onMain { controller.currentMediaItem?.mediaId })
        assertTrue(onMain { controller.isPlaying })
        assertEquals(connections, network.manifests.get())
        runBlocking { app.radioCatalog.changeSubscription(app.radioCatalog.state.value.subscriptions.single(), enabled = true) }
        assertEquals(20, app.radioCatalog.state.value.subscriptions.single().programs.size)
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id && !controller.playWhenReady }
        assertEquals(17_000, onMain { controller.currentPosition })
        assertEquals(0, network.logos.get())
    }

    @Test fun pinnedFocusSurvivesPageReturnsReorderAndActivityRecreation() {
        network.customPlaylist = finitePlaylist()
        playStation(2)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            val stationId = app.radio.options.value.pins[4]
            fun quick() = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
            fun button() = quick()?.findViewWithTag<View>(stationId)
            await { quick()?.isShown == true && !quick().isLayoutRequested }
            onMain { quick().scrollToPosition(app.radio.options.value.pins.indexOf(stationId)) }
            await { button()?.isShown == true }
            onMain { assertTrue(button()!!.requestFocusFromTouch()) }
            await { activity.currentFocus?.tag == stationId }
            val stream = onMain { controller.currentMediaItem?.mediaId }
            val connections = network.manifests.get()
            val initialPins = app.radio.options.value.pins
            for (screen in listOf(R.id.nav_folders, R.id.nav_library, R.id.nav_queue, R.id.nav_playlists, R.id.nav_radio)) {
                onMain { ViewModelProvider(activity)[MusicViewModel::class.java].screen(screen) }
                await { activity.findViewById<View>(R.id.miniPlayer).isShown }
                onMain { ViewModelProvider(activity)[MusicViewModel::class.java].screen(R.id.nav_player) }
                await(diagnostic = { "Return from $screen: focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}" }) {
                    activity.currentFocus?.tag == stationId && button()?.isFocused == true
                }
            }
            onMain {
                ViewModelProvider(activity)[MusicViewModel::class.java].openSettings()
                assertTrue(app.radio.reorderPins(initialPins, initialPins.reversed()))
            }
            await { activity.findViewById<View>(R.id.settingsPanel)?.isShown == true }
            onMain { ViewModelProvider(activity)[MusicViewModel::class.java].closeSettings() }
            await { activity.currentFocus?.tag == stationId && button()?.isFocused == true }
            onMain {
                assertEquals(initialPins.reversed().indexOf(stationId),
                    quick().getChildAdapterPosition(button()!!))
                assertEquals(stream, controller.currentMediaItem?.mediaId)
                assertTrue(controller.isPlaying)
            }
            scenario.recreate()
            scenario.onActivity { activity = it }
            await(diagnostic = { "Recreated focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}" }) {
                activity.currentFocus?.tag == stationId && button()?.isFocused == true
            }
            screenshot("focus1490-returned-pin")
            assertEquals("Focus restoration must not reconnect radio", connections, network.manifests.get())
        }
    }

    @Test fun touchedPinsRemainTheRemoteAnchorAfterSettingsAndLocaleChanges() {
        network.customPlaylist = finitePlaylist()
        playStation(0)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            val id = app.radio.options.value.pins[4]
            fun quick() = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
            await { quick()?.findViewWithTag<View>(id)?.isShown == true }
            onMain {
                quick().findViewWithTag<View>(id).apply {
                    requestRectangleOnScreen(android.graphics.Rect(0, 0, width, height), true)
                }
            }
            await {
                quick().findViewWithTag<View>(id)?.let {
                    val bounds = android.graphics.Rect()
                    it.getGlobalVisibleRect(bounds) && bounds.width() == it.width && bounds.height() == it.height
                } == true
            }
            onView(org.hamcrest.Matchers.allOf(withTagValue(equalTo(id)),
                androidx.test.espresso.matcher.ViewMatchers.isDisplayed())).perform(click())
            await { controller.currentMediaItem?.mediaId == id && controller.isPlaying }
            val manifests = network.manifests.get()
            onView(withId(R.id.action_settings)).perform(click())
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.closeSettings() }
            await { quick().isShown }
            if (onMain { quick().isInTouchMode }) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await(diagnostic = { "Touched pin return: focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}" }) {
                activity.currentFocus?.tag == id
            }
            val previous = onMain { quick() }
            onMain {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.forLanguageTags("zh"))
            }
            await(diagnostic = {
                "Locale return: focus=${activity.currentFocus?.id}/${activity.currentFocus?.tag}, " +
                    "memory=${model.focusPositions}"
            }) {
                quick() !== previous && quick()?.isShown == true && activity.currentFocus?.tag == id
            }
            screenshot("focus1490-localized-pin")
            onMain {
                model.openSettings()
                assertTrue(app.radio.pin(checkNotNull(app.radioCatalog.find(id))))
            }
            await { activity.findViewById<View>(R.id.settingsPanel)?.isShown == true }
            onMain { model.closeSettings() }
            await { quick().isShown && quick().findViewWithTag<View>(id) == null }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await { activity.currentFocus?.let { it.isShown && it.isFocusable && it.isEnabled && it.tag != id } == true }
            assertEquals(manifests, network.manifests.get())
            assertEquals(id, onMain { controller.currentMediaItem?.mediaId })
        }
    }

    @Test fun settingsAndRadioListRememberSeparateLastUsedControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            onMain { ViewModelProvider(activity)[MusicViewModel::class.java].openSettings() }
            await { activity.findViewById<View>(R.id.settingsButtonIcons)?.isShown == true }
            onMain { assertTrue(activity.findViewById<View>(R.id.settingsButtonIcons).requestFocusFromTouch()) }
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.LYRICS) }
            await { activity.findViewById<View>(SettingsCategory.LYRICS.panelId).isShown }
            onMain { model.settingsCategory(SettingsCategory.GENERAL) }
            await { activity.findViewById<View>(R.id.settingsButtonIcons).isFocused }
            onMain { model.screen(R.id.nav_radio) }
            val pin = app.radio.options.value.pins[2]
            val list = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            await { pin in pinIds(list) }
            onMain {
                val preceding = (list.adapter as? ConcatAdapter)?.adapters.orEmpty()
                    .takeWhile { it !is RadioButtonAdapter }.sumOf { it.itemCount }
                (list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).scrollToPositionWithOffset(
                    preceding + pinIds(list).indexOf(pin), 0)
            }
            await { !list.isLayoutRequested && !list.hasPendingAdapterUpdates() &&
                list.findViewWithTag<View>(pin)?.getGlobalVisibleRect(android.graphics.Rect()) == true }
            onMain {
                assertTrue(list.findViewWithTag<View>(pin).requestFocus())
                model.screen(R.id.nav_folders)
            }
            await { activity.findViewById<View>(R.id.folderRemoveAll)?.isShown == true }
            onMain { model.screen(R.id.nav_radio) }
            await { activity.currentFocus?.tag == pin }
            onMain { model.openSettings() }
            await { activity.findViewById<View>(R.id.settingsButtonIcons).isFocused }
            screenshot("focus1490-returned-setting")
            assertEquals("Moving focus must not tune a station", 0, network.manifests.get())
        }
    }

    @Test fun subscriptionUpdatesKeepCurrentBroadcastAndLocalQueueWhileSourcesComeAndGo() {
        network.customPlaylist = finitePlaylist()
        val url = "https://radio.example/subscriptions/one.m3u"
        network.subscriptionBodies[url] = "Subscribed live,https://audio_station.radio.example/old/index.m3u8"
        network.subscriptionEtags[url] = "\"one\""
        val subscription = runBlocking { app.radioCatalog.saveSubscription("One", url) }
        assertEquals(0, network.requests.size)
        assertEquals(RadioSubscriptionResult.UPDATED,
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) })
        val station = app.radioCatalog.state.value.programs.single { it.name == "Subscribed live" }
        assertEquals(0, network.manifests.get())
        assertEquals(0, network.logos.get())
        onMain { app.radio.pin(station) }
        playProgram(station)
        val manifests = network.manifests.get()
        val savedMusic = app.radio.savedMusic.value
        val startingPosition = onMain { controller.currentPosition }
        var interruptions = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) interruptions++ }
        }
        onMain { controller.addListener(listener) }
        try {
            val before = app.radioCatalog.state.value.programs
            assertEquals(RadioSubscriptionResult.UNCHANGED,
                runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) })
            assertSame(before, app.radioCatalog.state.value.programs)
            assertEquals("Saved ${app.radioCatalog.state.value.subscriptions.single().etag} at " +
                "${app.radioCatalog.state.value.subscriptions.single().validatorUrl}; headers ${network.subscriptionRequestHeaders}",
                1, network.subscriptionConditionals.get())
            network.subscriptionBodies[url] = "Subscribed live,https://radio.example/new/index.m3u8\n" +
                "Another station,https://radio.example/another/index.m3u8"
            network.subscriptionEtags[url] = "\"two\""
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
            await { controller.mediaItemCount == app.radioCatalog.state.value.programs.size }
            assertEquals(station.streamUri, app.radio.currentStation!!.streamUri)
            assertEquals("https://radio.example/new/index.m3u8", app.radioCatalog.find(station.id)!!.streamUri)
            val current = runBlocking { app.radioCatalog.snapshot() }.subscriptions.single()
            runBlocking { app.radioCatalog.changeSubscription(current, enabled = false) }
            await { controller.currentMediaItemIndex == 0 }
            assertNull(app.radioCatalog.find(station.id))
            assertTrue(station.id in app.radioCatalog.state.value.knownIds)
            assertTrue(station.id in app.radio.options.value.pins)
            runBlocking { app.radioCatalog.changeSubscription(app.radioCatalog.snapshot().subscriptions.single(), remove = true) }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(controller.isPlaying)
                assertEquals(station.id, controller.currentMediaItem!!.mediaId)
                assertTrue(controller.currentPosition >= startingPosition)
                assertEquals(0, interruptions)
            }
            assertEquals(manifests, network.manifests.get())
            assertEquals(savedMusic, app.radio.savedMusic.value)
            assertTrue(runBlocking { app.radioCatalog.savedPrograms() }.isEmpty())
            ActivityScenario.launch(MainActivity::class.java).use {
                val activity = settledMain()
                onMain {
                    assertEquals(station.name, activity.findViewById<android.widget.TextView>(R.id.title).text.toString())
                    assertFalse(activity.findViewById<View>(R.id.rewind).isEnabled)
                    assertFalse(activity.findViewById<View>(R.id.forward).isEnabled)
                }
                screenshot("subscription-retired-active-broadcast")
            }
        } finally { onMain { controller.removeListener(listener) } }
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id }
        onMain { assertEquals(17_000L, controller.currentPosition); controller.prepare(); controller.play() }
        await { controller.isPlaying }
        val localPosition = onMain { controller.currentPosition }
        val next = runBlocking { app.radioCatalog.saveSubscription("New subscription", url) }
        runBlocking { app.radioCatalog.refreshSubscription(next.id, emptyMap()) }
        onMain {
            assertTrue(controller.isPlaying)
            assertEquals(tracks[1].id, controller.currentMediaItem!!.mediaId)
            assertTrue(controller.currentPosition >= localPosition)
        }
        assertEquals(manifests, network.manifests.get())
        assertEquals(0, network.logos.get())
    }

    @Test fun nativeSubscriptionManagerAddsUpdatesEditsAndRemovesIndependentListsAcrossRecreation() {
        onMain { app.radio.setPanelVisible(false) }
        val firstUrl = "https://radio.example/subscriptions/one.m3u"
        val secondUrl = "https://radio.example/subscriptions/two.m3u"
        network.subscriptionBodies[firstUrl] = "Shared,https://radio.example/one/index.m3u8\n" +
            "Only first,https://radio.example/first/index.m3u8"
        network.subscriptionBodies[secondUrl] = "Shared,https://radio.example/two/index.m3u8\n" +
            "Only second,https://radio.example/second/index.m3u8"
        network.subscriptionEtags[firstUrl] = "\"one\""
        network.subscriptionEtags[secondUrl] = "\"two\""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            lateinit var model: MusicViewModel
            onMain {
                model = ViewModelProvider(activity)[MusicViewModel::class.java]
                model.settingsCategory(SettingsCategory.RADIO)
                model.openSettings()
            }
            await { activity.findViewById<View>(R.id.radioSubscriptions).isShown }
            onMain { activity.findViewById<View>(R.id.radioSubscriptions).performClick() }
            onView(withId(R.id.playerDialogTitle)).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            assertEquals(0, network.requests.size)
            fun add(name: String, url: String) {
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                onData(equalTo(activity.getString(R.string.radio_subscription_custom))).inRoot(isDialog()).perform(click())
                onView(withId(R.id.radioSubscriptionName)).inRoot(isDialog()).perform(replaceText(name), closeSoftKeyboard())
                onView(withId(R.id.radioSubscriptionUrl)).inRoot(isDialog()).perform(replaceText(url), closeSoftKeyboard())
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { model.subscriptionProgress.value == null &&
                    app.radioCatalog.state.value.subscriptions.any { it.name == name && it.checkedAtMs > 0 } }
            }
            fun row(subscription: RadioSubscription) {
                onView(withTagValue(equalTo<Any>(subscription.id))).inRoot(isDialog()).perform(scrollTo(), click())
            }
            add("First list", firstUrl)
            var first = app.radioCatalog.state.value.subscriptions.single()
            assertEquals(2, first.programs.size)
            row(first)
            onView(withText(R.string.radio_subscription_edit)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.radioSubscriptionName)).inRoot(isDialog()).perform(replaceText("Renamed list"), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { app.radioCatalog.state.value.subscriptions.single().name == "Renamed list" && !model.radioImportBusy.value }
            assertEquals(1, network.subscriptions.get())
            first = app.radioCatalog.state.value.subscriptions.single()
            row(first)
            onView(withText(R.string.radio_subscription_disable)).inRoot(isDialog()).perform(click())
            await { !app.radioCatalog.state.value.subscriptions.single().enabled && !model.radioImportBusy.value }
            assertNull(app.radioCatalog.find(first.programs.first().id))
            row(first)
            onView(withText(R.string.radio_subscription_enable)).inRoot(isDialog()).perform(click())
            await { app.radioCatalog.state.value.subscriptions.single().enabled && !model.radioImportBusy.value }
            assertEquals(1, network.subscriptions.get())
            add("Second list", secondUrl)
            assertEquals(3, app.radioCatalog.state.value.programs.count { it.name in setOf("Shared", "Only first", "Only second") })
            assertEquals(2, app.radioCatalog.state.value.programs.single { it.name == "Shared" }.sources.size)
            screenshot("subscriptions-two-independent-lists")
            onView(withId(android.R.id.button3)).inRoot(isDialog()).perform(click())
            await { model.subscriptionProgress.value == null && network.subscriptions.get() == 4 }
            assertEquals("Saved ${app.radioCatalog.state.value.subscriptions.map { it.etag to it.validatorUrl }}; " +
                "headers ${network.subscriptionRequestHeaders}", 2, network.subscriptionConditionals.get())
            assertEquals(1, network.maxSubscriptionInFlight.get())
            val snapshot = runBlocking { app.radioCatalog.snapshot() }
            network.subscriptionStarted = CountDownLatch(1)
            val release = CountDownLatch(1)
            network.subscriptionRelease = release
            try {
                onView(withId(android.R.id.button3)).inRoot(isDialog()).perform(click())
                assertTrue(network.subscriptionStarted!!.await(5, TimeUnit.SECONDS))
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                assertNotNull(model.subscriptionProgress.value)
                onMain { model.setRadioPanelVisible(true); model.setRadioPanelVisible(false) }
                assertTrue(model.subscriptionProgress.value?.cancelling == false)
                onMain { activity.findViewById<View>(R.id.radioSubscriptions).performClick() }
                scenario.recreate()
                scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
                onView(withId(R.id.radioSubscriptionProgress)).inRoot(isDialog()).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
                screenshot("subscriptions-progress-after-recreation")
                onView(withId(android.R.id.button3)).inRoot(isDialog()).perform(click())
                assertTrue(model.subscriptionProgress.value?.cancelling == true)
            } finally {
                release.countDown()
                network.subscriptionRelease = null
                network.subscriptionStarted = null
            }
            await { model.subscriptionProgress.value == null && network.subscriptionInFlight.get() == 0 }
            assertEquals(snapshot, runBlocking { app.radioCatalog.snapshot() })
            assertEquals(5, network.subscriptions.get())
            row(first)
            onView(withText(R.string.radio_subscription_remove)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { app.radioCatalog.state.value.subscriptions.size == 1 && !model.radioImportBusy.value }
            assertEquals("Second list", app.radioCatalog.state.value.subscriptions.single().name)
            assertEquals(1, app.radioCatalog.state.value.programs.single { it.name == "Shared" }.sources.size)
            assertFalse(app.radioCatalog.state.value.programs.any { it.name == "Only first" })
            assertEquals(0, network.manifests.get())
            assertEquals(0, network.logos.get())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertFalse(model.radioSubscriptionsOpen.value)
            onMain { assertFalse(controller.playWhenReady); assertEquals(tracks[1].id, controller.currentMediaItem!!.mediaId) }
        }
    }

    @Test fun subscriptionRefreshChoicesPersistAndManualNeverDownloads() {
        val url = "https://radio.example/manual-refresh.m3u"
        network.subscriptionBodies[url] = "Scheduled station,https://radio.example/manual/index.m3u8"
        runBlocking { app.radioCatalog.saveSubscription("Manual feed", url) }
        assertEquals(RadioRefreshInterval.MANUAL, app.radio.options.value.refreshInterval)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (language in listOf("en", "zh")) {
                onMain {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                            android.os.LocaleList.forLanguageTags(language)
                    } else androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(language))
                }
                val activity = settledMain()
                onMain {
                    ViewModelProvider(activity)[MusicViewModel::class.java].apply {
                        settingsCategory(SettingsCategory.RADIO)
                        openSettings()
                    }
                }
                await { activity.findViewById<View>(R.id.radioRefreshInterval)?.isShown == true }
                for (interval in RadioRefreshInterval.entries) {
                    onView(withId(R.id.radioRefreshInterval)).perform(scrollTo(), click())
                    onView(withId(R.id.playerDialogList)).inRoot(isDialog()).check { view, error ->
                        if (error != null) throw error
                        assertEquals(app.radio.options.value.refreshInterval.ordinal,
                            (view as android.widget.ListView).checkedItemPosition)
                    }
                    onData(equalTo(activity.getString(interval.label))).inRoot(isDialog()).perform(click())
                    assertEquals(interval, app.radio.options.value.refreshInterval)
                    assertEquals(interval, RadioSettings(app).options.value.refreshInterval)
                    assertEquals(interval, app.radio.backup().refreshInterval)
                }
                screenshot("radio151-refresh-settings-$language")
            }
            onMain { app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL) }
            scenario.recreate()
            settledMain()
            SystemClock.sleep(65_000)
            assertEquals("Manual must not refresh even with overdue lists and extended foreground use",
                0, network.subscriptions.get())
            assertEquals(0L, app.radio.lastAutomaticRefreshAttemptAtMs)
        }
    }

    @Test fun automaticRefreshWaitsForForegroundUseAndKeepsThePlayingConnection() {
        val now = System.currentTimeMillis()
        val station = RadioSubscriptions.programs(
            listOf(RadioListEntry("Scheduled station", "https://radio.example/before/index.m3u8")), emptyMap()).single()
        val due = RadioSubscription("00000000-0000-0000-0000-000000000151", "Due",
            "https://radio.example/due.m3u", programs = listOf(station), checkedAtMs = now - TimeUnit.DAYS.toMillis(2))
        val fresh = due.copy(id = "00000000-0000-0000-0000-000000000152", name = "Fresh",
            url = "https://radio.example/fresh.m3u", checkedAtMs = now, programs = emptyList())
        val disabled = due.copy(id = "00000000-0000-0000-0000-000000000153", name = "Disabled",
            url = "https://radio.example/disabled.m3u", enabled = false, programs = emptyList())
        runBlocking {
            app.radioCatalog.withReplacement(emptyList(), false, subscriptions = listOf(due, fresh, disabled)) { Unit }
        }
        for (subscription in listOf(due, fresh, disabled))
            network.subscriptionBodies[subscription.url] = "Scheduled station,https://radio.example/after/index.m3u8"
        onMain {
            app.radio.pin(station)
            app.radio.setPanelVisible(false)
        }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { controller.isPlaying }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { app.radio.setRefreshInterval(RadioRefreshInterval.DAILY) }
            SystemClock.sleep(5_000)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            SystemClock.sleep(60_000)
            assertEquals("Leaving the foreground must cancel the initial delay", 0, network.subscriptions.get())
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            activity = settledMain()
            model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            SystemClock.sleep(55_000)
            assertEquals("A new foreground session must get a full startup grace period", 0, network.subscriptions.get())
            val beforePosition = onMain { controller.currentPosition }
            await { network.subscriptions.get() == 1 && model.subscriptionProgress.value == null }
            val catalog = app.radioCatalog.state.value
            assertEquals("https://radio.example/after/index.m3u8", catalog.find(station.id)!!.streamUri)
            assertEquals(fresh, catalog.subscriptions.single { it.id == fresh.id })
            assertEquals(disabled, catalog.subscriptions.single { it.id == disabled.id })
            assertEquals(1, network.maxSubscriptionInFlight.get())
            assertTrue(station.id in app.radio.options.value.pins)
            assertFalse(app.radio.options.value.showPanel)
            assertTrue(app.radio.lastAutomaticRefreshAttemptAtMs >= now)
            assertEquals(app.radio.lastAutomaticRefreshAttemptAtMs, RadioSettings(app).lastAutomaticRefreshAttemptAtMs)
            onMain {
                assertTrue(controller.isPlaying)
                assertEquals(station.id, controller.currentMediaItem!!.mediaId)
                assertEquals(station.streamUri, app.radio.currentStation!!.streamUri)
                assertTrue("Automatic refresh must not reset the playing position", controller.currentPosition >= beforePosition)
            }
            assertFalse("Refreshed addresses must not replace the active connection",
                network.requests.any { it.startsWith("radio.example/after/") })
            screenshot("radio151-automatic-refresh-playing")
            onMain { app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL) }
        }
    }

    @Test fun cancelledAutomaticRefreshKeepsCachesAndDoesNotRetryOnReopen() {
        val station = RadioSubscriptions.programs(
            listOf(RadioListEntry("Cached station", "https://radio.example/cached/index.m3u8")), emptyMap()).single()
        val first = RadioSubscription("00000000-0000-0000-0000-000000000151", "First",
            "https://radio.example/cancel-first.m3u", programs = listOf(station))
        val second = first.copy(id = "00000000-0000-0000-0000-000000000152", name = "Second",
            url = "https://radio.example/cancel-second.m3u", programs = emptyList())
        runBlocking {
            app.radioCatalog.withReplacement(emptyList(), false, subscriptions = listOf(first, second)) { Unit }
        }
        network.subscriptionBodies[first.url] = "Changed first,https://radio.example/first-new/index.m3u8"
        network.subscriptionBodies[second.url] = "Changed second,https://radio.example/second-new/index.m3u8"
        val release = CountDownLatch(1)
        network.subscriptionRelease = release
        network.subscriptionStarted = CountDownLatch(1)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val activity = settledMain()
                val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                onMain { app.radio.setRefreshInterval(RadioRefreshInterval.DAILY) }
                assertTrue(network.subscriptionStarted.await(75, TimeUnit.SECONDS))
                assertTrue(model.subscriptionProgress.value != null)
                assertEquals(1, network.subscriptions.get())
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                release.countDown()
                network.subscriptionRelease = null
                await { model.subscriptionProgress.value == null && network.subscriptionInFlight.get() == 0 }
                assertEquals(listOf(first, second), app.radioCatalog.state.value.subscriptions)
                assertEquals(1, network.subscriptions.get())
                assertTrue(RadioSettings(app).lastAutomaticRefreshAttemptAtMs > 0)
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                settledMain()
                SystemClock.sleep(65_000)
                assertEquals("A cancelled automatic attempt must not restart on every foreground entry",
                    1, network.subscriptions.get())
                onMain {
                    app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL)
                    model.refreshRadioSubscriptions()
                }
                await { model.subscriptionProgress.value == null && network.subscriptions.get() == 3 }
                assertEquals(1, network.maxSubscriptionInFlight.get())
                assertTrue(app.radioCatalog.state.value.programs.any { it.name == "Changed first" })
                assertTrue(app.radioCatalog.state.value.programs.any { it.name == "Changed second" })
                network.subscriptionStatuses[first.url] = 503
                onMain { model.refreshRadioSubscriptions(first.id) }
                await { model.subscriptionProgress.value == null && network.subscriptions.get() == 4 }
                assertTrue(app.radioCatalog.state.value.programs.any { it.name == "Changed first" })
                assertEquals(503, app.radioCatalog.state.value.subscriptions.single { it.id == first.id }.failure!!.httpStatus)
                assertNotNull(app.notice.value)
            }
        } finally {
            release.countDown()
            network.subscriptionRelease = null
        }
    }

    @Test fun subscriptionPresetsMarkAddedDisabledAndRenamedListsWithoutDownloading() {
        onMain { app.radio.setPanelVisible(false) }
        val us = RadioSubscriptions.presets.single { it.title == R.string.radio_subscription_us }
        val uk = RadioSubscriptions.presets.single { it.title == R.string.radio_subscription_uk }
        val original = runBlocking {
            app.radioCatalog.saveSubscription("My renamed US list",
                us.url.replace("https://raw.githubusercontent.com/", "HTTPS://RAW.GITHUBUSERCONTENT.COM:443/"))
        }
        runBlocking { app.radioCatalog.changeSubscription(original, enabled = false) }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain {
                ViewModelProvider(activity)[MusicViewModel::class.java].also {
                    it.settingsCategory(SettingsCategory.RADIO)
                    it.openSettings()
                }
            }
            await { activity.findViewById<View>(R.id.radioSubscriptions)?.isShown == true }
            onMain { activity.findViewById<View>(R.id.radioSubscriptions).performClick() }
            lateinit var rows: android.widget.LinearLayout
            onView(withId(R.id.radioSubscriptionRows)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                rows = view as android.widget.LinearLayout
            }
            await { rows.findViewWithTag<View>(original.id)?.isEnabled == true }
            val subscriptionRow = onMain { rows.findViewWithTag<MaterialButton>(original.id) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            onMain { assertTrue(subscriptionRow.requestFocus()) }
            await { subscriptionRow.isFocused }
            val rowFocus = onMain {
                assertTrue("Dynamically added subscription rows need a visible focus outline", focusInk(subscriptionRow) > 0)
                subscriptionRow.foreground
            }
            screenshot("focus1482-subscription-row")
            fun presets() = onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            fun label(disabled: Boolean) = activity.getString(
                if (disabled) R.string.radio_subscription_preset_disabled else R.string.radio_subscription_preset_added,
                activity.getString(us.title))
            presets()
            onData(equalTo(label(true))).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            onData(equalTo(activity.getString(uk.title))).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            screenshot("radio145-subscription-added-disabled")
            onData(equalTo(label(true))).inRoot(isDialog()).perform(click())
            onView(withText(R.string.radio_subscription_enable)).inRoot(isDialog()).perform(click())
            await { !model.radioImportBusy.value && app.radioCatalog.state.value.subscriptions.single().enabled }
            onMain {
                assertSame(subscriptionRow, rows.findViewWithTag<MaterialButton>(original.id))
                assertSame("Updating a subscription must reuse its focus drawable", rowFocus, subscriptionRow.foreground)
            }
            assertEquals(0, network.subscriptions.get())
            assertEquals("My renamed US list", app.radioCatalog.state.value.subscriptions.single().name)
            presets()
            onData(equalTo(label(false))).inRoot(isDialog()).perform(click())
            onView(withText(R.string.radio_subscription_remove)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { !model.radioImportBusy.value && app.radioCatalog.state.value.subscriptions.isEmpty() }
            presets()
            onData(equalTo(activity.getString(us.title))).inRoot(isDialog()).perform(click())
            onView(withId(R.id.radioSubscriptionUrl)).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(withText(us.url)))
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertEquals(0, network.requests.size)
        }
    }

    @Test fun radioControlsAndCompactPinsFitWhileNoticesFloat() {
        val pins = (listOf(CnrRadio.stations.first().program) + RadioDefaults.corePrograms)
            .distinctBy { it.id }.take(12)
        onMain {
            app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString("radioPins", pins.joinToString("\n") { it.id }).commit()
            app.radio.publishRestored()
            app.displaySettings.update { it.copy(spectrum = it.spectrum.copy(enabled = true),
                controlBlocks = true, blockOutline = it.blockOutline.copy(style = com.macarmusic.app.model.BlockStyle.SOLID)) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.getChildAt(0)?.tag == pins.first().id }
            onMain { activity.findViewById<RecyclerView>(R.id.radioList).getChildAt(0).performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == pins.first().id }
            onMain {
                controller.pause()
                activity.findViewById<View>(R.id.nav_player).performClick()
            }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations)?.let {
                it.isShown && it.adapter?.itemCount == 12 && it.childCount > 0
            } == true && !controller.playWhenReady }
            for (style in listOf(com.macarmusic.app.model.BlockStyle.SOLID, com.macarmusic.app.model.BlockStyle.CHARACTERS)) {
                onMain { app.displaySettings.update { it.copy(blockOutline = it.blockOutline.copy(style = style)) } }
                instrumentation.waitForIdleSync()
                screenshot("radio145-radio-initial-${style.name.lowercase()}")
                scenario.onActivity { PlayerLayoutAssertions.assertFits(it) }
                val before = onMain { PlayerLayoutAssertions.snapshot(activity) }
                val labels = onMain { PlayerLayoutAssertions.labels(activity) }
                val message = "Cannot play this station (ERROR_CODE_IO_BAD_HTTP_STATUS). " +
                    "Check your connection or try Play again. Some stations may be off air. ".repeat(3)
                onMain { app.report(message) }
                await { activity.findViewById<View>(R.id.noticeBar).isShown }
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(before, PlayerLayoutAssertions.snapshot(it))
                    assertEquals(labels, PlayerLayoutAssertions.labels(it))
                    PlayerLayoutAssertions.assertFits(it)
                }
                screenshot("radio145-radio-notice-${style.name.lowercase()}")
                onView(withId(R.id.dismissNotice)).perform(click())
                instrumentation.waitForIdleSync()
                val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioQuickStations) }
                onMain {
                    if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        val first = grid.getChildAt(0) as MaterialButton
                        assertEquals((28 * activity.resources.displayMetrics.density).toInt(), first.iconSize)
                        assertTrue("Portrait pin must be compact", first.height <= 80 * activity.resources.displayMetrics.density)
                    }
                    grid.scrollToPosition(11)
                }
                await { grid.findViewHolderForAdapterPosition(11) != null }
                scenario.onActivity {
                    assertEquals(before, PlayerLayoutAssertions.snapshot(it))
                    PlayerLayoutAssertions.assertFits(it)
                }
                screenshot("radio145-radio-pins-${style.name.lowercase()}")
                onMain { grid.scrollToPosition(0) }
                await { grid.findViewHolderForAdapterPosition(0) != null }
            }
            assertEquals(pins.first().id, onMain { controller.currentMediaItem?.mediaId })
            assertFalse(onMain { controller.playWhenReady })
        }
    }

    @Test fun subscriptionZipBackupRestoresDisabledOwnershipAndPinsWithoutNetworkRequests() {
        val url = "https://radio.example/subscriptions/backup.m3u"
        network.subscriptionBodies[url] = "Backup station,https://radio.example/backup/index.m3u8"
        val subscription = runBlocking { app.radioCatalog.saveSubscription("Backup feed", url) }
        runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
        val station = app.radioCatalog.state.value.programs.single { it.name == "Backup station" }
        onMain { app.radio.pin(station) }
        runBlocking { app.radioCatalog.changeSubscription(app.radioCatalog.snapshot().subscriptions.single(), enabled = false) }
        runBlocking { app.radioCatalog.deleteGroups(setOf("national"), app.radioCatalog.snapshot()) }
        val hidden = setOf("united states", "country:ca")
        onMain { app.radio.hideGroups(hidden) }
        onMain {
            app.radio.setNavigationMode(com.macarmusic.app.radio.RadioNavigationMode.PINNED_ONLY)
            app.radio.setButtonLogo(com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO)
            app.radio.setRefreshInterval(RadioRefreshInterval.WEEKLY)
            app.displaySettings.update { it.copy(appLogo = com.macarmusic.app.model.AppLogo.RADIO,
                buttonIcons = com.macarmusic.app.model.ButtonIconStyle.COLORFUL, autoPlayOnLaunch = true) }
        }
        val snapshot = runBlocking { app.radioCatalog.snapshot() }
        val uri = runBlocking { app.backups.sharedExport("en") }
        val backup = runBlocking { app.backups.prepare(uri) }
        assertEquals(snapshot.subscriptions, backup.manifest.settings.radioSubscriptions)
        assertEquals(snapshot.removedDefaults, backup.manifest.settings.radioRemovedDefaults)
        assertEquals(hidden, backup.manifest.settings.radio!!.hiddenGroups)
        assertEquals(com.macarmusic.app.radio.RadioNavigationMode.PINNED_ONLY, backup.manifest.settings.radio!!.navigationMode)
        assertEquals(com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO, backup.manifest.settings.radio!!.buttonLogo)
        assertEquals(RadioRefreshInterval.WEEKLY, backup.manifest.settings.radio!!.refreshInterval)
        assertTrue(station.id in backup.manifest.settings.radio!!.pins!!)
        runBlocking { app.radioCatalog.changeSubscription(app.radioCatalog.snapshot().subscriptions.single(), remove = true) }
        onMain {
            app.radio.showGroups(hidden)
            app.radio.setNavigationMode(com.macarmusic.app.radio.RadioNavigationMode.GLOBAL)
            app.radio.setButtonLogo(com.macarmusic.app.radio.RadioButtonLogo.BRANDED_RADIO)
            app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL)
            app.displaySettings.update { it.copy(appLogo = com.macarmusic.app.model.AppLogo.MUSIC,
                buttonIcons = com.macarmusic.app.model.ButtonIconStyle.MONOCHROME, autoPlayOnLaunch = false) }
        }
        val requests = network.requests.size
        runBlocking { app.backups.restore(backup) }
        assertEquals(snapshot, runBlocking { app.radioCatalog.snapshot() })
        assertEquals(hidden, RadioSettings(app).options.value.hiddenGroups)
        assertEquals(com.macarmusic.app.radio.RadioNavigationMode.PINNED_ONLY, RadioSettings(app).options.value.navigationMode)
        assertEquals(com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO, RadioSettings(app).options.value.buttonLogo)
        assertEquals(RadioRefreshInterval.WEEKLY, RadioSettings(app).options.value.refreshInterval)
        assertEquals(com.macarmusic.app.model.AppLogo.RADIO, DisplaySettings(app) {}.options.value.appLogo)
        assertEquals(com.macarmusic.app.model.ButtonIconStyle.COLORFUL, DisplaySettings(app) {}.options.value.buttonIcons)
        assertTrue(DisplaySettings(app) {}.options.value.autoPlayOnLaunch)
        assertTrue(station.id in app.radio.options.value.pins)
        assertNull(app.radioCatalog.find(station.id))
        assertEquals(requests, network.requests.size)
        onMain { assertFalse(controller.playWhenReady); assertEquals(tracks[1].id, controller.currentMediaItem!!.mediaId) }
    }

    @Test fun largeSubscriptionUpdatesKeepMusicResponsiveAndStatusChecksReuseTheCatalog() = runBlocking {
        app.radioCatalog.withReplacement(emptyList(), false) { Unit }
        val subscription = app.radioCatalog.saveSubscription("Large cache", "https://radio.example/large.m3u")
        val entries = List(5000) { RadioListEntry("Cached station $it", "https://radio.example/$it/index.m3u8") }
        onMain { controller.prepare(); controller.play() }
        await { controller.isPlaying }
        val position = onMain { controller.currentPosition }
        val started = SystemClock.elapsedRealtime()
        val pending = async(Dispatchers.IO) {
            app.radioCatalog.refreshSubscription(subscription.id, emptyMap(), { RadioSubscriptionDownload(entries) })
        }
        val beforeMain = SystemClock.elapsedRealtime()
        onMain { assertTrue(controller.isPlaying) }
        val mainMs = SystemClock.elapsedRealtime() - beforeMain
        assertTrue("Subscription work blocked the main thread for $mainMs ms", mainMs < 1500)
        assertEquals(RadioSubscriptionResult.UPDATED, pending.await())
        val updateMs = SystemClock.elapsedRealtime() - started
        val programs = app.radioCatalog.state.value.programs
        assertEquals(5000, programs.size)
        val checked = SystemClock.elapsedRealtime()
        assertEquals(RadioSubscriptionResult.UNCHANGED,
            app.radioCatalog.refreshSubscription(subscription.id, emptyMap(), { RadioSubscriptionDownload() }))
        assertSame(programs, app.radioCatalog.state.value.programs)
        onMain {
            assertTrue(controller.isPlaying)
            assertEquals(tracks[1].id, controller.currentMediaItem!!.mediaId)
            assertTrue(controller.currentPosition >= position)
        }
        println("SUBSCRIPTION_PERFORMANCE stations=5000 updateMs=$updateMs " +
            "unchangedMs=${SystemClock.elapsedRealtime() - checked} mainRoundTripMs=$mainMs")
        assertEquals(0, network.requests.size)
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
            assertEquals(Player.REPEAT_MODE_ALL, controller.repeatMode)
            assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE))
            assertFalse(controller.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE))
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

    @Test fun renamingCurrentStationKeepsStreamBufferAndVisibleMetadata() {
        network.customPlaylist = finitePlaylist()
        val imported = importPrograms("Rename fixture,https://radio.example/rename/primary.m3u8\n" +
            "Rename fixture,https://radio.example/rename/secondary.m3u8").single()
        val station = runBlocking { app.radioCatalog.selectSource(imported.id, 1) }
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)); app.radio.pin(station) }
        playProgram(station)
        val manifests = network.manifests.get()
        val music = app.radio.savedMusic.value
        var interruptions = 0
        var discontinuities = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) interruptions++ }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                discontinuities++
            }
        }
        onMain { controller.addListener(listener) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var activity = settledMain()
                val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                onMain { com.macarmusic.app.ui.RadioStationEditor.show(activity, station) { model.editRadio(station, it) } }
                onView(withId(R.id.radioEditName)).inRoot(isDialog())
                    .perform(scrollTo(), replaceText("Renamed live station"), closeSoftKeyboard())
                val position = onMain { controller.currentPosition }
                val buffered = onMain { controller.bufferedPosition }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await {
                    app.radio.currentStation?.name == "Renamed live station" &&
                        controller.mediaMetadata.title.toString() == "Renamed live station" && !model.radioImportBusy.value
                }
                await { activity.findViewById<android.widget.TextView>(R.id.title).text.toString() == "Renamed live station" }
                // A metadata event resynchronizes the controller's projected time with the audio clock.
                await({ "Rename did not retain advancing playback: $position -> ${controller.currentPosition}" }) {
                    controller.currentPosition >= position
                }
                onMain {
                    assertTrue(controller.isPlaying)
                    assertTrue("Rename discarded the buffered audio: $buffered -> ${controller.bufferedPosition}",
                        controller.bufferedPosition >= buffered)
                    assertEquals(0, interruptions)
                    assertEquals(0, discontinuities)
                    assertEquals(station.streamUri, app.radio.currentStation?.streamUri)
                }
                assertEquals(manifests, network.manifests.get())
                assertEquals(music, app.radio.savedMusic.value)
                screenshot("radio140-rename-playing")
                val renamed = checkNotNull(app.radioCatalog.find(station.id))
                val reordered = renamed.copy(uri = renamed.streamUri, alternateUris = listOf(renamed.uri), sourceIndex = 0)
                runBlocking { app.radioCatalog.edit(renamed, reordered) }
                await { app.radio.currentStation == reordered }
                assertEquals("A different source index with the same URL must retain the stream", manifests, network.manifests.get())
                scenario.recreate()
                activity = settledMain()
                await { activity.findViewById<android.widget.TextView>(R.id.title).text.toString() == reordered.name }
                onMain {
                    assertTrue(controller.isPlaying)
                    assertEquals(0, interruptions)
                    assertEquals(0, discontinuities)
                }
                onMain { controller.pause() }
                await { controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 }
                runBlocking { app.radioCatalog.edit(reordered, reordered.copy(name = "Renamed while stopped")) }
                await { controller.mediaMetadata.title.toString() == "Renamed while stopped" }
                onMain { assertFalse(controller.playWhenReady); assertEquals(Player.STATE_IDLE, controller.playbackState) }
                assertEquals(manifests, network.manifests.get())
            }
        } finally { onMain { controller.removeListener(listener) } }
    }

    @Test fun renamingWhileBufferingKeepsPendingConnectionAndSourceChangesStillPause() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Rename buffering,https://radio.example/renaming/index.m3u8").single()
        network.manifestStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        network.manifestRelease = release
        try {
            command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
            assertTrue(network.manifestStarted.await(5, TimeUnit.SECONDS))
            await { controller.playbackState == Player.STATE_BUFFERING && controller.playWhenReady }
            runBlocking { app.radioCatalog.edit(station, station.copy(name = "Renamed pending")) }
            await { controller.mediaMetadata.title.toString() == "Renamed pending" }
            onMain { assertTrue(controller.playWhenReady); assertEquals(Player.STATE_BUFFERING, controller.playbackState) }
            assertEquals(1, network.manifests.get())
        } finally { release.countDown(); network.manifestRelease = null }
        await { controller.isPlaying }
        val renamed = checkNotNull(app.radioCatalog.find(station.id))
        runBlocking { app.radioCatalog.edit(renamed, renamed.copy(uri = "https://radio.example/changed/index.m3u8")) }
        await { !controller.playWhenReady && controller.playbackState == Player.STATE_IDLE && network.active.get() == 0 &&
            app.radio.currentStation?.streamUri == "https://radio.example/changed/index.m3u8" }
        assertEquals("Changing the actual URL must not silently continue using the old address", 1, network.manifests.get())
        assertEquals("https://radio.example/changed/index.m3u8", app.radio.currentStation?.streamUri)
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

    @Test fun progressiveAndRetiredSubscriptionSourcesContinueThroughMetadataEdits() {
        val progressive = importPrograms("Progressive rename,https://radio.example/rename.aac").single()
        playProgram(progressive)
        val opens = network.connectionAttempts.get()
        val position = onMain { controller.currentPosition }
        var interruptions = 0
        var discontinuities = 0
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) interruptions++ }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                discontinuities++
            }
        }
        onMain { controller.addListener(listener) }
        try {
            runBlocking { app.radioCatalog.edit(progressive, progressive.copy(name = "Progressive renamed")) }
            await { controller.mediaMetadata.title.toString() == "Progressive renamed" }
            onMain {
                assertTrue(controller.isPlaying)
                assertTrue(controller.currentPosition >= position)
                assertEquals(0, interruptions)
                assertEquals(0, discontinuities)
            }
            assertEquals(opens, network.connectionAttempts.get())
        } finally { onMain { controller.removeListener(listener) } }
        command(RadioCommands.music)
        network.customPlaylist = finitePlaylist()
        val url = "https://radio.example/subscriptions/rename.m3u"
        network.subscriptionBodies[url] = "Subscribed rename,https://radio.example/old-rename/index.m3u8"
        val subscription = runBlocking { app.radioCatalog.saveSubscription("Rename list", url) }
        runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
        val station = app.radioCatalog.state.value.programs.single { it.name == "Subscribed rename" }
        playProgram(station)
        val manifests = network.manifests.get()
        val oldPosition = onMain { controller.currentPosition }
        interruptions = 0
        discontinuities = 0
        onMain { controller.addListener(listener) }
        try {
            network.subscriptionBodies[url] = "Subscribed rename,https://radio.example/new-rename/index.m3u8"
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
            instrumentation.waitForIdleSync()
            val updated = checkNotNull(app.radioCatalog.find(station.id))
            assertEquals("https://radio.example/new-rename/index.m3u8", updated.streamUri)
            runBlocking { app.radioCatalog.edit(updated, updated.copy(name = "Retired source renamed")) }
            await { controller.mediaMetadata.title.toString() == "Retired source renamed" }
            assertEquals(station.streamUri, app.radio.currentStation?.streamUri)
            onMain {
                assertTrue(controller.isPlaying)
                assertTrue(controller.currentPosition >= oldPosition)
                assertEquals(0, interruptions)
                assertEquals(0, discontinuities)
            }
            assertEquals(manifests, network.manifests.get())
        } finally { onMain { controller.removeListener(listener) } }
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
            app.radio.setPanelVisible(false)
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
        assertFalse(app.radio.options.value.showPanel)
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
            app.radio.setPanelVisible(false)
            app.displaySettings.update { it.copy(showLibrary = false, showQueue = false, showPlaylists = false) }
        }
        val uri = runBlocking { app.backups.sharedExport("en") }
        val backup = runBlocking { app.backups.prepare(uri) }
        assertEquals(setOf(CnrRadio.stations[1].id), backup.manifest.settings.radio?.stations)
        assertEquals(true, backup.manifest.settings.radio?.favoritesOnly)
        assertEquals(true, backup.manifest.settings.radio?.openPlayerOnSelection)
        assertEquals(false, backup.manifest.settings.radio?.showPanel)
        assertFalse(backup.manifest.settings.display.showLibrary)
        assertFalse(backup.manifest.settings.display.showQueue)
        assertFalse(backup.manifest.settings.display.showPlaylists)
        onMain {
            app.radio.favorite(CnrRadio.stations[1])
            app.radio.filter(false)
            app.radio.setOpenPlayerOnSelection(false)
            app.radio.setPanelVisible(true)
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
        assertFalse(app.radio.options.value.showPanel)
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
                onMain { model.setRadioPanelVisible(false) }
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
            app.radio.setPanelVisible(false)
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
            await { model.radioChannels.value.size == 2 }
            val heading = radioGroupButton(activity, RadioGroups.of(programs.first()).id)
            onMain { heading.performClick() }
            await { model.radioChannels.value.size == 4 }
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
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 4 &&
                !activity.findViewById<RecyclerView>(R.id.radioList).isLayoutRequested }
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
            await { activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 3 }
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
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == 2 }
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
                val layout = grid.layoutManager as GridLayoutManager
                val columns = layout.spanCount / layout.spanSizeLookup.getSpanSize(0)
                val availableDp = (grid.width - grid.paddingLeft - grid.paddingRight) / grid.resources.displayMetrics.density
                if (availableDp < 272 * grid.resources.configuration.fontScale.coerceAtLeast(1f))
                    assertEquals("Narrow and large-text layouts need one readable tile column", 1, columns)
                else assertTrue(columns >= 2)
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
            assertPinLabels(onMain { activity.findViewById<RecyclerView>(R.id.radioList) })
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
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            fun quickButton(position: Int) = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
                ?.findViewHolderForAdapterPosition(position)?.itemView as? MaterialButton
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations)?.let {
                it.isShown && it.adapter?.itemCount == 8 && quickButton(0) != null
            } == true }
            await { quickButton(0)?.icon is BitmapDrawable }
            assertPinLabels(onMain { activity.findViewById<RecyclerView>(R.id.radioQuickStations) })
            assertEquals(1, network.logos.get())
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            instrumentation.waitForIdleSync()
            // Finish the outer player's D-pad scroll before focusing its nested station grid.
            SystemClock.sleep(350)
            onMain {
                assertFalse(activity.findViewById<View>(R.id.artwork).isShown)
                assertFalse(activity.findViewById<View>(R.id.lyricsList).isShown)
                assertTrue("Quick station buttons must accept D-pad focus",
                    checkNotNull(quickButton(0)).requestFocus())
            }
            await(diagnostic = {
                val button = quickButton(0)
                val bounds = android.graphics.Rect()
                button?.getGlobalVisibleRect(bounds)
                val grid = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
                "First quick tile: focus=${button?.hasFocus()}, height=${button?.height}, visible=$bounds, " +
                    "grid=${grid.width}x${grid.height}"
            }) {
                quickButton(0)?.let { button ->
                    val bounds = android.graphics.Rect()
                    button.hasFocus() && button.getGlobalVisibleRect(bounds) && bounds.height() == button.height
                } == true
            }
            val nextKey = onMain {
                if ((activity.findViewById<RecyclerView>(R.id.radioQuickStations).layoutManager as GridLayoutManager).spanCount > 1)
                    KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN
            }
            instrumentation.sendKeyDownUpSync(nextKey)
            await(diagnostic = {
                val grid = activity.findViewById<RecyclerView>(R.id.radioQuickStations)
                "Quick focus=${activity.currentFocus?.javaClass?.simpleName}/${activity.currentFocus?.tag}, " +
                    "grid=${grid.width}x${grid.height}, children=" +
                    (0 until grid.childCount).map { grid.getChildAt(it).tag }
            }) { quickButton(1)?.hasFocus() == true }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == CnrRadio.stations[1].id }
            await { quickButton(1)?.isSelected == true }
            screenshot("radio141-remote-switch")
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
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == 2 }
            val group = radioGroupButton(activity, "import:pin fixtures")
            onMain { group.performClick() }
            await { model.radioChannels.value.any { it.station?.id == station.id } &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 3 }
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
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 4 && stationButton()?.icon != null }
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
            await { model.radioChannels.value.any { it.station?.id == station.id } &&
                activity.findViewById<RecyclerView>(R.id.radioList)?.adapter?.itemCount == 4 }
            onMain {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                val index = model.radioChannels.value.indexOfFirst { it.station?.id == station.id }
                grid.scrollToPosition(grid.adapter!!.itemCount - model.radioChannels.value.size + index)
            }
            await { stationButton()?.currentTextColor == (ColorSlots.RAINBOW[2] or android.graphics.Color.BLACK) }
            onMain {
                assertTrue(model.radioChannels.value.any { it.station?.id == station.id })
                checkNotNull(stationButton()).performLongClick()
            }
            onView(withText(R.string.radio_unpin)).inRoot(isDialog()).perform(click())
            await { station.id !in app.radio.options.value.pins &&
                activity.findViewById<RecyclerView>(R.id.radioList).adapter?.itemCount == 3 && stationButton()?.icon == null }
            onMain {
                assertEquals(3, model.radioChannels.value.size)
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

    @Test fun hidingRadioPanelKeepsPlaybackSourcesWidgetsAndSavedData() {
        network.customPlaylist = finitePlaylist()
        val programs = importPrograms("Keep 101.5,https://radio.example/keep/first.m3u8\n" +
            "Keep 101.5,https://radio.example/keep/second.m3u8\n" +
            "Next 102.5,https://radio.example/next/index.m3u8")
        val station = programs.first { it.name == "Keep 101.5" }
        val next = programs.first { it.name == "Next 102.5" }
        onMain {
            app.radio.favorite(station)
            assertTrue(app.radio.pin(station))
            assertTrue(app.radio.pin(next))
            val pins = app.radio.options.value.pins
            assertTrue(app.radio.reorderPins(pins, listOf(station.id, next.id) + pins.filter { it !in setOf(station.id, next.id) }))
        }
        playProgram(station)
        val catalog = runBlocking { app.radioCatalog.savedPrograms() }
        val pins = app.radio.options.value.pins
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
            await { activity.findViewById<View>(R.id.settingsRadio).isShown }
            val manifests = network.manifests.get()
            onView(withId(R.id.settingsRadio)).perform(scrollTo(), click())
            await { !app.radio.options.value.showPanel && !activity.findViewById<View>(R.id.nav_radio).isShown }
            onMain {
                assertTrue(controller.isPlaying)
                assertTrue(app.radio.options.value.active)
                assertEquals(station.id, controller.currentMediaItem?.mediaId)
                assertEquals(station.streamUri, app.radio.currentStation?.streamUri)
                assertEquals(17_000L, app.radio.savedMusic.value!!.positionMs)
            }
            screenshot("radio146-hidden-general")
            assertEquals(catalog, runBlocking { app.radioCatalog.savedPrograms() })
            assertFalse(RadioSettings(app).options.value.showPanel)
            assertTrue(station.id in app.radio.options.value.favorites)
            assertEquals(pins, app.radio.options.value.pins)
            scenario.recreate()
            activity = settledMain()
            model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            await { activity.findViewById<MaterialSwitch>(R.id.settingsRadio)?.isShown == true }
            onMain {
                assertFalse(activity.findViewById<MaterialSwitch>(R.id.settingsRadio).isChecked)
                assertTrue(controller.isPlaying)
                model.closeSettings()
            }
            await { activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true }
            assertPinLabels(onMain { activity.findViewById(R.id.radioQuickStations) })
            assertEquals("Hiding/recreating must retain the existing stream", manifests, network.manifests.get())
            screenshot("radio146-hidden-playing")
            onMain { activity.findViewById<View>(R.id.forward).performClick() }
            await { controller.isPlaying && app.radio.currentStation?.streamUri == station.sources[1] }
            command(com.macarmusic.app.widgets.HomeWidgetAction.NEXT.command)
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == next.id }
            command(com.macarmusic.app.widgets.HomeWidgetAction.PREVIOUS.command)
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == station.id }
            assertEquals(station.sources[1], app.radio.currentStation?.streamUri)
            command(com.macarmusic.app.widgets.HomeWidgetAction.TOGGLE.command)
            await { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady }
            command(com.macarmusic.app.widgets.HomeWidgetAction.TOGGLE.command)
            await { controller.isPlaying }
            onMain { controller.pause() }
            await { controller.playbackState == Player.STATE_IDLE }
            val stoppedManifests = network.manifests.get()
            onMain { model.setRadioPanelVisible(true) }
            await { activity.findViewById<View>(R.id.nav_radio).isShown }
            onMain { assertFalse("Showing the panel must not autoplay", controller.playWhenReady) }
            assertEquals(stoppedManifests, network.manifests.get())
            command(RadioCommands.music)
            onMain {
                assertEquals(17_000L, controller.currentPosition)
                assertEquals(2, controller.mediaItemCount)
                assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
                assertTrue(controller.shuffleModeEnabled)
                controller.prepare()
                controller.play()
            }
            await { controller.isPlaying }
            onMain { model.setRadioPanelVisible(false) }
            await { !activity.findViewById<View>(R.id.nav_radio).isShown }
            onMain {
                assertTrue("Hiding unused radio must leave local music playing", controller.isPlaying)
                assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
            }
            assertEquals(stoppedManifests, network.manifests.get())
            assertEquals(pins, app.radio.options.value.pins)
        }
    }

    @Test fun stationTilesAndRowsShowOnlyNamesWhileSourceDetailsRemain() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Station 101.5,https://radio.example/labels/first.m3u8\n" +
            "Station 101.5,https://radio.example/labels/second.m3u8").single()
        onMain {
            assertTrue(app.radio.pin(station))
            val pins = app.radio.options.value.pins
            assertTrue(app.radio.reorderPins(pins, listOf(station.id) + pins.filterNot { it == station.id }))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.screen(R.id.nav_radio); model.radioQuery(station.name) }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.childCount?.let { it > 0 } == true }
            val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            assertPinLabels(grid)
            onMain { model.toggleRadioGroup(RadioGroups.of(station)) }
            await { model.radioChannels.value.any { it.station?.id == station.id } }
            val position = onMain {
                (grid.adapter as ConcatAdapter).adapters.first().itemCount +
                    model.radioChannels.value.indexOfFirst { it.station?.id == station.id }
            }
            onMain { grid.scrollToPosition(position) }
            await { grid.findViewHolderForAdapterPosition(position)?.itemView?.isShown == true }
            onMain {
                val row = grid.findViewHolderForAdapterPosition(position)!!.itemView as MaterialButton
                assertEquals(station.name, row.text.toString())
                assertEquals(station.name, row.contentDescription)
            }
            screenshot("radio146-name-only-list")
            playProgram(station)
            onMain { model.screen(R.id.nav_player) }
            await { activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true }
            assertPinLabels(onMain { activity.findViewById(R.id.radioQuickStations) })
            onMain {
                assertEquals(app.text(R.string.radio_sources, 2), activity.findViewById<View>(R.id.details).contentDescription)
                assertTrue(activity.findViewById<View>(R.id.forward).isEnabled)
                activity.findViewById<View>(R.id.details).performClick()
            }
            onView(withText(app.text(R.string.radio_source_item, 2, station.sources[1]))).inRoot(isDialog()).check(
                androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            activity = settledMain()
            await { activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true }
            assertPinLabels(onMain { activity.findViewById(R.id.radioQuickStations) })
            screenshot("radio146-name-only-playing")
        }
    }

    @Test fun defaultPinsCanBeRemovedAndReplacedUpToTwelveWithoutAHiddenFavoritesFilter() {
        assertEquals(RadioDefaults.pins, app.radio.options.value.pins)
        val channels = importPrograms((1..5).joinToString("\n") { "Added $it,https://radio.example/$it/index.m3u8" })
        onMain {
            assertTrue(app.radio.pin(channels[0]))
            assertTrue(app.radio.pin(channels[1]))
            assertTrue(app.radio.pin(channels[2]))
            assertFalse(app.radio.pin(channels[3]))
            assertFalse(app.radio.pin(channels[4]))
            assertEquals(12, app.radio.options.value.pins.size)
            assertTrue(app.radio.pin(CnrRadio.stations.first().program))
            assertTrue(app.radio.pin(channels[4]))
        }
        assertEquals(app.radio.options.value.pins, RadioSettings(app).options.value.pins)
        onMain {
            app.radio.options.value.pins.toList().forEach { app.radio.pin(app.radioCatalog.find(it)!!) }
        }
        assertTrue(RadioSettings(app).options.value.pins.isEmpty())
        val chinese = app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
        })
        assertEquals(listOf("CCTV-13 新闻", "中国之声", "经济之声", "音乐之声", "中国交通广播", "环球资讯广播",
            "中国乡村之声", "文艺之声", "经典音乐广播"), RadioDefaults.pins.map { app.radioCatalog.find(it)!!.title(chinese) })
        assertEquals(0, network.requests.size)
    }

    @Test fun pinnedMenusAppearBeforeTouchMouseAndConfirmKeyReleaseWithoutPlaying() {
        network.customPlaylist = finitePlaylist()
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)) }
        playStation(0)
        val connections = network.manifests.get()
        val originalPins = app.radio.options.value.pins
        val playing = onMain { controller.currentMediaItem!!.mediaId }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            val field = RadioButtonAdapter::class.java.getDeclaredField("heldDialog").apply { isAccessible = true }
            for (screen in listOf(R.id.nav_radio, R.id.nav_player)) {
                onMain { model.screen(screen) }
                val gridId = if (screen == R.id.nav_radio) R.id.radioList else R.id.radioQuickStations
                await { activity.findViewById<RecyclerView>(gridId)?.isShown == true }
                val grid = onMain { activity.findViewById<RecyclerView>(gridId) }
                onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0) }
                await { grid.findViewHolderForAdapterPosition(2) != null && !grid.isLayoutRequested }
                val button = onMain { grid.findViewHolderForAdapterPosition(2)!!.itemView }
                val adapter = onMain { grid.findViewHolderForAdapterPosition(2)!!.bindingAdapter as RadioButtonAdapter }
                assertNotEquals(playing, onMain { button.tag })
                val heldFlags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                val inputs = "touch mouse enter center numpad space cancel-touch cancel-enter".split(' ')
                for (input in inputs) {
                    await { activity.hasWindowFocus() && button.isShown }
                    val key = when (input) {
                        "enter", "cancel-enter" -> KeyEvent.KEYCODE_ENTER
                        "center" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "numpad" -> KeyEvent.KEYCODE_NUMPAD_ENTER
                        "space" -> KeyEvent.KEYCODE_SPACE
                        else -> null
                    }
                    if (key == null) {
                        instrumentation.setInTouchMode(true)
                        runBlocking(Dispatchers.Main) {
                            kotlinx.coroutines.withTimeout(5_000) { activity.findViewById<View>(R.id.root).awaitDraw() }
                        }
                    } else {
                        instrumentation.setInTouchMode(false)
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                        await { !button.isInTouchMode }
                        onMain { assertTrue("$input cannot focus ${button.tag}, attached=${button.isAttachedToWindow}", button.requestFocus()) }
                    }
                    val down = SystemClock.uptimeMillis()
                    val position = onMain {
                        val location = IntArray(2).also(button::getLocationOnScreen)
                        val visible = android.graphics.Rect()
                        assertTrue(button.getLocalVisibleRect(visible))
                        location[0] + visible.exactCenterX() to location[1] + visible.exactCenterY()
                    }
                    fun pointer(action: Int) {
                        val mouse = input == "mouse"
                        val properties = MotionEvent.PointerProperties().apply {
                            id = 0
                            toolType = if (mouse) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER
                        }
                        val coordinates = MotionEvent.PointerCoords().apply {
                            x = position.first; y = position.second
                            pressure = if (action == MotionEvent.ACTION_DOWN) 1f else 0f
                            size = 1f
                        }
                        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 1,
                            arrayOf(properties), arrayOf(coordinates), 0,
                            if (mouse && action == MotionEvent.ACTION_DOWN) MotionEvent.BUTTON_PRIMARY else 0,
                            1f, 1f, 0, 0, if (mouse) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN, 0)
                        try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                    }
                    fun keyEvent(action: Int, repeat: Int = 0, flags: Int = 0) =
                        instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), action, requireNotNull(key),
                            repeat, 0, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_KEYBOARD))
                    if (key == null) pointer(MotionEvent.ACTION_DOWN) else keyEvent(KeyEvent.ACTION_DOWN)
                    SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 180L)
                    val dialog = onMain { field.get(adapter) as? com.macarmusic.app.ui.PlayerDialog }
                    assertNotNull("$input must open its menu before UP on screen $screen", dialog)
                    onMain {
                        assertTrue(dialog!!.isShowing && dialog.window!!.decorView.isShown)
                        assertEquals(heldFlags, dialog.window!!.attributes.flags and heldFlags)
                        assertTrue("The held input must stay with the original window", activity.hasWindowFocus())
                        assertEquals(playing, controller.currentMediaItem?.mediaId)
                        assertTrue(controller.isPlaying)
                    }
                    if (input == "touch" || input == "enter") screenshot("radio148-held-$screen-$input")
                    if (key != null) keyEvent(KeyEvent.ACTION_DOWN, repeat = 2, flags = KeyEvent.FLAG_LONG_PRESS)
                    val cancelled = input.startsWith("cancel")
                    if (key == null) pointer(if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP)
                    else keyEvent(KeyEvent.ACTION_UP, flags = if (cancelled) KeyEvent.FLAG_CANCELED else 0)
                    if (cancelled) await { !dialog!!.isShowing && activity.hasWindowFocus() }
                    else {
                        await { dialog!!.isShowing && dialog.window!!.decorView.hasWindowFocus() }
                        onMain { assertEquals(0, dialog!!.window!!.attributes.flags and heldFlags) }
                        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                    }
                    assertEquals(connections, network.manifests.get())
                    assertEquals(playing, onMain { controller.currentMediaItem?.mediaId })
                    assertEquals(originalPins, app.radio.options.value.pins)
                }
            }
            val quick = onMain { activity.findViewById<RecyclerView>(R.id.radioQuickStations) }
            val button = onMain { quick.findViewHolderForAdapterPosition(2)!!.itemView }
            await { activity.hasWindowFocus() }
            instrumentation.setInTouchMode(false)
            onMain { assertTrue(button.requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            await { controller.currentMediaItem?.mediaId == button.tag && controller.isPlaying }
            assertEquals("A normal short Enter press still starts playback exactly once", connections + 1, network.manifests.get())
        }
    }

    @Test fun languageChangesKeepOneWindowTranslatedViewsAndUninterruptedRadioAndMusic() {
        val oldLanguage = onMain { androidx.appcompat.app.AppCompatDelegate.getApplicationLocales() }
        val oldTheme = app.themeMode
        network.customPlaylist = finitePlaylist()
        onMain {
            app.themeMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
            app.radio.tune(RadioTuning(downloadLogos = false))
        }
        playStation(0)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                val activity = settledMain()
                val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                val decor = onMain { activity.window.decorView }
                val token = onMain { decor.windowToken }
                val controllerField = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
                val uiController = onMain { controllerField.get(activity) }
                var detachments = 0
                var interruptions = 0
                var sampledFrames = 0
                val attachment = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view: View) = Unit
                    override fun onViewDetachedFromWindow(view: View) { detachments++ }
                }
                val playback = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) interruptions++ }
                }
                onMain { decor.addOnAttachStateChangeListener(attachment); controller.addListener(playback) }
                try {
                    for (screen in listOf(R.id.nav_library, R.id.nav_folders, R.id.nav_queue, R.id.nav_playlists, R.id.nav_radio)) {
                        onMain { model.screen(screen) }
                        instrumentation.waitForIdleSync()
                    }
                    onMain { model.openSettings(); model.settingsCategory(SettingsCategory.GENERAL) }
                    await { activity.findViewById<View>(R.id.settingsGeneralPanel)?.isShown == true }
                    fun switchLanguage(tags: String, fromPicker: Boolean = false) {
                        var blackFrames = 0
                        val oldRoot = onMain { activity.findViewById<View>(R.id.settingsHeading) }
                        val oldJob = onMain {
                            MainActivity::class.java.getDeclaredField("contentJob").apply { isAccessible = true }
                                .get(activity) as kotlinx.coroutines.Job
                        }
                        val oldLocale = onMain { androidx.core.os.ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language }
                        val expected = if (tags.isEmpty()) requireNotNull(androidx.core.os.ConfigurationCompat.getLocales(
                            android.content.res.Resources.getSystem().configuration)[0]).language else java.util.Locale.forLanguageTag(tags).language
                        if (fromPicker) {
                            onMain { activity.findViewById<View>(R.id.settingsLanguage).performClick() }
                            val list = dialogList()
                            onMain { decor.postDelayed({
                                val index = if (tags == "zh-CN") 1 else if (tags == "en") 0 else 2
                                list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
                            }, 100) }
                        } else onMain { decor.postDelayed({
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                androidx.core.os.LocaleListCompat.forLanguageTags(tags))
                        }, 100) }
                        repeat(12) {
                            val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                            try {
                                var visible = 0
                                var samples = 0
                                for (y in image.height / 8 until image.height * 7 / 8 step 12) {
                                    for (x in image.width / 8 until image.width * 7 / 8 step 12) {
                                        val pixel = image.getPixel(x, y)
                                        if (maxOf(android.graphics.Color.red(pixel), android.graphics.Color.green(pixel),
                                                android.graphics.Color.blue(pixel)) > 24) visible++
                                        samples++
                                    }
                                }
                                if (visible <= samples / 2) {
                                    blackFrames++
                                    val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
                                    File(directory, "radio148-language-dark-$sampledFrames.png").outputStream().use {
                                        assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                                    }
                                    onMain {
                                        android.util.Log.e("LocaleFrame", "frame=$sampledFrames visible=$visible/$samples " +
                                            "destroyed=${activity.isDestroyed} decorAttached=${decor.isAttachedToWindow} " +
                                            "night=${activity.resources.configuration.uiMode} lang=${androidx.core.os.ConfigurationCompat.getLocales(activity.resources.configuration)} " +
                                            "heading=${activity.findViewById<android.widget.TextView>(R.id.settingsHeading)?.text}")
                                    }
                                }
                                sampledFrames++
                            } finally { image.recycle() }
                            SystemClock.sleep(20)
                        }
                        await {
                            activity.isDestroyed || androidx.core.os.ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language == expected &&
                                activity.findViewById<android.widget.TextView>(R.id.settingsHeading)?.text ==
                                activity.getString(model.settingsCategory.value.label) && activity.hasWindowFocus()
                        }
                        onMain {
                            assertFalse("Locale change recreated the activity", activity.isDestroyed)
                            assertSame(decor, activity.window.decorView)
                            assertSame(token, decor.windowToken)
                            assertSame(uiController, controllerField.get(activity))
                            if (oldLocale != expected) {
                                assertNotSame(oldRoot, activity.findViewById<View>(R.id.settingsHeading))
                                assertTrue("Previous view collectors must finish before the new tree starts", oldJob.isCompleted)
                            }
                            assertEquals(R.id.action_settings, model.selectedScreen.value)
                            assertEquals(0, detachments)
                            assertEquals(0, interruptions)
                            assertTrue(controller.isPlaying)
                        }
                        assertEquals("Real compositor frames went black during $tags", 0, blackFrames)
                    }
                    val connections = network.manifests.get()
                    val savedMusic = app.radio.savedMusic.value
                    val station = onMain { controller.currentMediaItem!!.mediaId }
                    switchLanguage("zh-CN", fromPicker = true)
                    screenshot("radio148-language-chinese-same-window")
                    switchLanguage("en", fromPicker = true)
                    switchLanguage("")
                    switchLanguage("zh-CN")
                    switchLanguage("en")
                    assertEquals(connections, network.manifests.get())
                    assertEquals(station, onMain { controller.currentMediaItem?.mediaId })
                    assertEquals(savedMusic, app.radio.savedMusic.value)
                    onMain { controller.removeListener(playback) }
                    command(RadioCommands.music)
                    onMain { controller.play() }
                    await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks[1].id }
                    onMain { interruptions = 0; controller.addListener(playback) }
                    val musicPosition = onMain { controller.currentPosition }
                    switchLanguage("zh-CN", fromPicker = true)
                    switchLanguage("en", fromPicker = true)
                    onMain {
                        assertTrue(controller.currentPosition > musicPosition)
                        assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                        assertEquals(2, controller.mediaItemCount)
                    }
                    val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
                    File(directory, "radio148-language-continuity.json").writeText(
                        """{"compositorFrames":$sampledFrames,"windowDetachments":$detachments,"audioInterruptions":$interruptions}""")
                    screenshot("radio148-language-english-same-window")
                } finally {
                    onMain { decor.removeOnAttachStateChangeListener(attachment); controller.removeListener(playback) }
                }
            }
        } finally {
            onMain {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(oldLanguage)
                app.themeMode = oldTheme
            }
        }
    }

    @Test fun pinnedDragOrderIsSharedPersistedFilteredAndCancelledWithoutRestartingPlayback() {
        network.customPlaylist = finitePlaylist()
        onMain {
            app.radio.tune(RadioTuning(downloadLogos = false))
            RadioDefaults.corePrograms.filter { it.id !in app.radio.options.value.pins }
                .take(RadioDefaults.MAX_PINS - app.radio.options.value.pins.size).forEach {
                assertTrue(app.radio.pin(it))
            }
        }
        assertEquals(12, app.radio.options.value.pins.size)
        playStation(0)
        val connections = network.manifests.get()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioList)?.let {
                pinIds(it).size == 12 && model.radioChannels.value.isNotEmpty() &&
                    it.adapter?.itemCount == 12 + model.radioChannels.value.size &&
                    it.childCount > 0 && !it.isLayoutRequested
            } == true }
            var grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            val rows = onMain { model.radioChannels.value }
            val original = app.radio.options.value.pins
            val nextRow = onMain {
                val layout = grid.layoutManager as GridLayoutManager
                layout.spanCount / layout.spanSizeLookup.getSpanSize(0)
            }
            val expected = original.toMutableList().apply { add(nextRow, removeAt(0)) }
            dragPin(grid, 0, nextRow)
            await(diagnostic = { "Expected pins=$expected; saved=${app.radio.options.value.pins}; grid=${pinIds(grid)}" }) {
                app.radio.options.value.pins == expected && pinIds(grid) == expected
            }
            assertSame(rows, onMain { model.radioChannels.value })
            assertEquals(expected, RadioSettings(app).options.value.pins)
            assertPinLabels(grid)
            assertEquals(connections, network.manifests.get())
            screenshot("radio138-pins-reordered-list")

            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            activity = settledMain()
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            screenshot("radio140-pins-recreated-before-keyboard-dismissal")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            await { pinIds(grid) == expected }
            onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0) }
            assertPinLabels(grid)
            onMain { activity.findViewById<View>(R.id.nav_player).performClick() }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations)?.let { it.isShown && pinIds(it) == expected } == true }
            val quick = onMain { activity.findViewById<RecyclerView>(R.id.radioQuickStations) }
            val quickOrder = expected.toMutableList().apply { add(1, removeAt(0)) }
            dragPin(quick, 0, 1)
            await { app.radio.options.value.pins == quickOrder && pinIds(quick) == quickOrder }
            assertPinLabels(quick)
            screenshot("radio141-pins-playing")
            screenshot("radio138-pins-reordered-playing")
            onMain {
                activity.findViewById<View>(R.id.nav_radio).performClick()
                activity.findViewById<EditText>(R.id.radioSearch).setText("music")
            }
            await { pinIds(grid).size in 2..11 }
            val subset = onMain { pinIds(grid) }
            val filteredOrder = quickOrder.toMutableList().apply {
                val first = indexOf(subset[0])
                val second = indexOf(subset[1])
                this[first] = subset[1]
                this[second] = subset[0]
            }
            dragPin(grid, 0, 1)
            await { app.radio.options.value.pins == filteredOrder && pinIds(grid).first() == subset[1] }
            assertPinLabels(grid)
            screenshot("radio141-filtered-pins")
            val visible = onMain { pinIds(grid) }
            dragPin(grid, 0, 1, cancel = true)
            await { pinIds(grid) == visible }
            assertPinLabels(grid)
            assertEquals(filteredOrder, app.radio.options.value.pins)
            assertEquals(filteredOrder, RadioSettings(app).options.value.pins)
            onMain { grid.findViewHolderForAdapterPosition(0)!!.itemView.performLongClick() }
            onData(equalTo(activity.getString(R.string.radio_move_pin))).inRoot(isDialog()).perform(click())
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(11).perform(click())
            val menuOrder = filteredOrder.toMutableList().apply { add(removeAt(indexOf(visible.first()))) }
            await { app.radio.options.value.pins == menuOrder }
            assertPinLabels(grid)
            assertEquals(menuOrder, RadioSettings(app).options.value.pins)
            assertEquals(connections, network.manifests.get())
            assertEquals(0, network.logos.get())
            onMain {
                assertTrue(controller.isPlaying)
                assertEquals(CnrRadio.stations.first().id, controller.currentMediaItem?.mediaId)
            }
            command(RadioCommands.music)
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    @Test fun countryMultiSelectionPersistsHidingAndRestoresDeletedSubscriptionStationsWithoutRestarting() {
        val url = "https://radio.example/subscriptions/countries.m3u"
        val entries = listOf("US" to "Country US", "UK" to "Country UK", "Canada" to "Country Canada")
        network.subscriptionBodies[url] = "#EXTM3U\n" + entries.mapIndexed { index, (group, name) ->
            "#EXTINF:-1 group-title=\"$group\",$name\nhttps://radio.example/country-$index/index.m3u8"
        }.joinToString("\n")
        val subscription = runBlocking { app.radioCatalog.saveSubscription("Countries", url) }
        runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
        val station = app.radioCatalog.state.value.programs.single { it.name == "Country US" }
        network.customPlaylist = finitePlaylist()
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)); app.radio.pin(station) }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { controller.isPlaying }
        val connections = network.manifests.get()
        val catalog = app.radioCatalog.state.value.programs
        val pins = app.radio.options.value.pins
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            fun quiet() = onMain { app.notice.value?.let { app.dismissNotice(it.id) } }
            fun openRadio() {
                quiet()
                onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
                await { model.radioChannels.value.isNotEmpty() }
            }
            fun choose(group: Int) = onData(org.hamcrest.Matchers.startsWith(activity.getString(group) + "\n"))
                .inAdapterView(withId(R.id.playerDialogList)).inRoot(isDialog()).perform(click())
            fun manageUs() {
                val header = radioGroupButton(activity, "united states")
                onView(org.hamcrest.Matchers.sameInstance(header)).perform(androidx.test.espresso.action.ViewActions.longClick())
                await { !activity.hasWindowFocus() }
            }
            openRadio()
            manageUs()
            choose(R.string.radio_region_uk)
            val selection = dialogList()
            assertEquals(2, onMain { selection.checkedItemCount })
            screenshot("radio139-multi-country-selection")
            countryAction(android.R.id.button1)
            val hidden = setOf("united states", "united kingdom")
            await { app.radio.options.value.hiddenGroups == hidden &&
                model.radioChannels.value.none { it.group.id in hidden } }
            countryAction(android.R.id.button2)
            assertSame(catalog, app.radioCatalog.state.value.programs)
            assertEquals(pins, app.radio.options.value.pins)
            assertEquals(hidden, RadioSettings(app).options.value.hiddenGroups)
            assertEquals(connections, network.manifests.get())
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
            assertEquals(hidden, app.radio.options.value.hiddenGroups)
            assertEquals(connections, network.manifests.get())
            scenario.recreate()
            activity = settledMain()
            model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            await { model.radioChannels.value.isNotEmpty() && model.radioChannels.value.none { it.group.id in hidden } }
            assertEquals(pins, onMain { pinIds(activity.findViewById(R.id.radioList)) })
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            quiet()
            onMain { model.settingsCategory(SettingsCategory.RADIO); model.openSettings() }
            await {
                val panel = activity.findViewById<View>(R.id.settingsRadioPanel)
                panel?.isShown == true && !panel.isLayoutRequested && panel.width > 0
            }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            await {
                val scroll = activity.findViewById<View>(R.id.settingsScroll)
                scroll.height >= 100 * activity.resources.displayMetrics.density && !scroll.isLayoutRequested
            }
            onView(withId(R.id.radioShowGroups)).perform(scrollTo(), click())
            choose(R.string.radio_region_uk)
            countryAction(android.R.id.button1)
            await { app.radio.options.value.hiddenGroups == setOf("united kingdom") }
            countryAction(android.R.id.button2)
            openRadio()
            await { model.radioChannels.value.any { it.group.id == "united states" } }
            manageUs()
            countryAction(android.R.id.button3)
            screenshot("radio139-delete-country-confirmation")
            val beforeCancel = app.radioCatalog.state.value
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertSame(beforeCancel, app.radioCatalog.state.value)
            manageUs()
            countryAction(android.R.id.button3)
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { app.radioCatalog.find(station.id) == null && !model.radioImportBusy.value &&
                model.radioChannels.value.none { it.group.id == "united states" } }
            assertFalse(station.id in app.radio.options.value.pins)
            assertTrue(app.radioCatalog.state.value.removedDefaults.isNotEmpty())
            onMain {
                assertTrue(controller.isPlaying)
                assertEquals(station.id, controller.currentMediaItem?.mediaId)
            }
            assertEquals(connections, network.manifests.get())
            assertEquals(1, app.radioCatalog.state.value.subscriptions.size)
            assertTrue(app.radioCatalog.state.value.subscriptions.single().etag.isEmpty())
            runBlocking { app.radioCatalog.refreshSubscription(subscription.id, emptyMap()) }
            await { app.radioCatalog.find(station.id) != null && model.radioChannels.value.any { it.group.id == "united states" } }
            assertEquals(setOf("united kingdom"), RadioSettings(app).options.value.hiddenGroups)
            assertEquals(connections, network.manifests.get())
            onMain { assertTrue(controller.isPlaying) }
            screenshot("radio139-restored-country-list")
        }
    }

    @Test fun countryVisibilityTogglesMixedSelectionsAndSupportsSelectAllInBothWindows() {
        val programs = importPrograms("#EXTM3U\n#EXTINF:-1 group-title=\"US\",Visibility US\nhttps://radio.example/visible-us.mp3\n" +
            "#EXTINF:-1 group-title=\"UK\",Visibility UK\nhttps://radio.example/visible-uk.mp3\n" +
            "#EXTINF:-1 group-title=\"Canada\",Visibility Canada\nhttps://radio.example/visible-ca.mp3")
        runBlocking { app.radioCatalog.withReplacement(programs, includeDefaults = false) { Unit } }
        onMain { app.radio.hideGroups(setOf("united states")) }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.RADIO); model.openSettings() }
            onView(withId(R.id.radioManageGroups)).perform(scrollTo(), click())
            var list = dialogList()
            fun action(label: Int?, enabled: Boolean = true) {
                onView(withId(android.R.id.button1)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    assertEquals(enabled, view.isEnabled)
                    if (label != null) assertEquals(activity.getString(label), (view as android.widget.TextView).text.toString())
                }
            }
            fun all(value: Boolean) {
                countryAction(if (value) R.id.playerDialogSelectAll else R.id.playerDialogDeselectAll)
                assertEquals(if (value) 3 else 0, onMain { list.checkedItemCount })
            }
            fun toggle() = countryAction(android.R.id.button1)
            val everyGroup = setOf("united states", "united kingdom", "country:ca")
            action(null, false)
            all(true)
            action(R.string.radio_groups_hide)
            toggle()
            await { app.radio.options.value.hiddenGroups == everyGroup }
            assertSame(list, dialogList())
            action(R.string.radio_groups_show)
            assertCountryFooter(list, hasDelete = true)
            screenshot("radio141-country-footer")
            toggle()
            await { app.radio.options.value.hiddenGroups.isEmpty() }
            action(R.string.radio_groups_hide)
            assertTrue(onMain { (0 until list.count).none {
                list.getItemAtPosition(it).toString().contains(activity.getString(R.string.radio_group_hidden))
            } })
            all(false)
            action(null, false)
            all(true)
            toggle()
            await { app.radio.options.value.hiddenGroups == everyGroup }
            countryAction(android.R.id.button2)
            onView(withId(R.id.radioShowGroups)).perform(scrollTo(), click())
            list = dialogList()
            assertEquals(3, onMain { list.checkedItemCount })
            action(R.string.radio_groups_show)
            all(false)
            action(null, false)
            all(true)
            onData(org.hamcrest.Matchers.startsWith(activity.getString(R.string.radio_region_usa) + "\n"))
                .inAdapterView(withId(R.id.playerDialogList)).inRoot(isDialog()).perform(click())
            toggle()
            await { app.radio.options.value.hiddenGroups == setOf("united states") }
            action(R.string.radio_groups_hide)
            all(true)
            action(R.string.radio_groups_hide)
            toggle()
            action(R.string.radio_groups_show)
            assertCountryFooter(list, hasDelete = false)
            screenshot("radio141-hidden-footer")
            toggle()
            await { app.radio.options.value.hiddenGroups.isEmpty() }
            all(false)
            action(null, false)
            onMain {
                assertEquals(3, list.count)
                assertTrue(list.height >= 48 * list.resources.displayMetrics.density)
                assertFalse(controller.playWhenReady)
                assertEquals(17_000L, controller.currentPosition)
            }
            countryAction(android.R.id.button2)
        }
        assertEquals(0, network.connectionAttempts.get())
    }

    @Test fun remoteDialogSelectionIsVisibleAndSeparateFromCheckedItems() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.RADIO); model.openSettings() }
            onView(withId(R.id.radioSubscriptions)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            var list = dialogList()
            focusChoice(list, 0)
            val labels = RadioSubscriptions.presets.map { activity.getString(it.title) } +
                activity.getString(R.string.radio_subscription_custom)
            for (index in labels.indices) {
                if (index > 0) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                await { list.selectedItemPosition == index && !list.isLayoutRequested }
                if (index == 0) screenshot("focus1490-preset-first")
                onMain {
                    assertEquals(labels[index], (list.selectedView as android.widget.TextView).text.toString())
                    assertTrue("Preset $index has no visible keyboard selector: enabled=${list.isEnabled}/" +
                        "${list.selectedView.isEnabled}, viewState=${list.drawableState.joinToString()}, " +
                        "selector=${drawableState(list.selector)}", focusInk(list, selector = true) > 0)
                    val bounds = android.graphics.Rect()
                    assertTrue(list.selectedView.getGlobalVisibleRect(bounds))
                    assertTrue(bounds.height() >= 48 * activity.resources.displayMetrics.density)
                }
                if (index == 3) screenshot("radio139-preset-remote-focus")
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            onView(withId(R.id.radioSubscriptionName)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                assertEquals("", (view as EditText).text.toString())
            }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            var activated = -1
            val single = onMain { com.macarmusic.app.ui.AccentTheme.dialog(activity).setTitle("Single choice fixture")
                .setSingleChoiceItems(arrayOf("One", "Two", "Three"), 0) { _, index -> activated = index }
                .setNegativeButton(R.string.close, null).show() }
            list = single.listView
            focusChoice(list, 0)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await { list.selectedItemPosition == 1 }
            onMain {
                assertTrue(list.isItemChecked(0))
                assertFalse(list.isItemChecked(1))
                assertEquals(-1, activated)
                assertTrue(focusInk(list, true) > 0)
            }
            screenshot("radio139-single-choice-focus")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { activated == 1 && list.isItemChecked(1) && !list.isItemChecked(0) }
            onMain {
                single.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).requestFocus()
                assertEquals("List focus must disappear when a footer receives focus", 0, focusInk(list, true))
                single.dismiss()
            }
            val checked = booleanArrayOf(true, false, false)
            val multiple = onMain { com.macarmusic.app.ui.AccentTheme.dialog(activity).setTitle("Multiple choice fixture")
                .setMultiChoiceItems(arrayOf("One", "Two", "Three"), checked) { _, _, _ -> }
                .setNegativeButton(R.string.close, null).show() }
            list = multiple.listView
            focusChoice(list, 0)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await { list.selectedItemPosition == 1 }
            onMain {
                assertTrue(checked[0])
                assertFalse(checked[1])
                assertTrue(focusInk(list, true) > 0)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await { checked[0] && checked[1] }
            screenshot("radio139-multiple-choice-focus")
            onMain { multiple.dismiss() }
            assertEquals(0, network.connectionAttempts.get())
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    @Test fun navigationSwitchesShowRemoteFocusAndToggleInDarkAndLightThemes() {
        val appearance = app.displaySettings.options.value
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                for (mode in listOf(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)) {
                    var activity = settledMain()
                    onMain { activity.delegate.localNightMode = mode }
                    activity = settledMain()
                    val night = mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    onMain { app.displaySettings.update { it.copy(customAccentColor = if (night) 0x101010 else 0xfafafa) } }
                    activity = settledMain()
                    onMain { app.displaySettings.update { it.copy(controlBlocks = night,
                        showLibrary = true, showQueue = true, showPlaylists = true) } }
                    val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                    onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
                    onView(withId(R.id.settingsShowLibrary)).perform(scrollTo())
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                    await { !activity.findViewById<View>(R.id.settingsShowLibrary).isInTouchMode }
                    val ids = listOf(R.id.settingsShowLibrary, R.id.settingsShowQueue, R.id.settingsShowPlaylists)
                    val switches = onMain { ids.map { activity.findViewById<MaterialSwitch>(it) } }
                    onMain { assertTrue(switches[0].requestFocus()) }
                    for ((index, toggle) in switches.withIndex()) {
                        if (index > 0) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                        await { toggle.isFocused && !toggle.isLayoutRequested }
                        onMain {
                            assertTrue("Unchecked switch $index, night=$night, preferences=${app.displaySettings.options.value}, " +
                                "destroyed=${activity.isDestroyed}", toggle.isChecked)
                            assertTrue("Switch needs an obvious outline even with a low-contrast accent", focusInk(toggle) > 0)
                            assertTrue(toggle.paddingStart >= 10 * toggle.resources.displayMetrics.density)
                        }
                        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                        await { !toggle.isChecked }
                        if (index == 0) screenshot("radio139-navigation-first-toggle-${if (night) "dark" else "light"}")
                        onMain {
                            assertTrue("Focus moved to ${activity.currentFocus}; screen=${model.selectedScreen.value}, " +
                                "destroyed=${activity.isDestroyed}, touch=${toggle.isInTouchMode}", toggle.isFocused)
                            assertTrue(focusInk(toggle) > 0)
                            val nav = listOf(R.id.nav_library, R.id.nav_queue, R.id.nav_playlists)[index]
                            assertEquals(View.GONE, activity.findViewById<View>(nav).visibility)
                        }
                        if (index == 1) screenshot("radio139-navigation-focus-${if (night) "dark" else "light"}")
                    }
                    onMain {
                        assertTrue(activity.findViewById<View>(R.id.nav_player).isShown)
                        assertTrue(activity.findViewById<View>(R.id.nav_folders).isShown)
                        assertFalse(controller.playWhenReady)
                        assertEquals(17_000L, controller.currentPosition)
                    }
                }
            }
        } finally { onMain { app.displaySettings.update { appearance } } }
        assertEquals(0, network.connectionAttempts.get())
    }

    private fun dialogList(): android.widget.ListView {
        lateinit var list: android.widget.ListView
        onView(withId(R.id.playerDialogList)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            list = view as android.widget.ListView
        }
        return list
    }

    private fun focusChoice(list: android.widget.ListView, index: Int) {
        await({ "Dialog not ready: window=${list.hasWindowFocus()}, layout=${list.isLayoutRequested}, children=${list.childCount}" }) {
            list.hasWindowFocus() && !list.isLayoutRequested && list.childCount > 0
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        await { !list.isInTouchMode }
        onMain { assertTrue(list.requestFocus()); list.setSelection(index) }
        await({ "Choice focus=${list.isFocused}, position=${list.selectedItemPosition}, wanted=$index, " +
            "window=${list.hasWindowFocus()}, touch=${list.isInTouchMode}, layout=${list.isLayoutRequested}, " +
            "selector=${list.selector.bounds}/${list.selector.state.joinToString()}" }) {
            list.isFocused && list.selectedItemPosition == index && list.selectedView != null && !list.isLayoutRequested &&
                !list.selector.bounds.isEmpty && android.R.attr.state_focused in list.selector.state
        }
    }

    private fun focusInk(view: View, selector: Boolean = false): Int {
        val foreground = if (selector) (view as android.widget.ListView).selector else view.foreground
        val drawable = if (!selector && foreground is android.graphics.drawable.LayerDrawable)
            foreground.getDrawable(foreground.numberOfLayers - 1) else foreground
        assertNotNull("Missing focus drawable", drawable)
        if (!selector) drawable.bounds = android.graphics.Rect(0, 0, view.width, view.height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            drawable.draw(android.graphics.Canvas(bitmap))
            return (0 until bitmap.height).sumOf { y -> (0 until bitmap.width).count { x ->
                android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0
            } }
        } finally { bitmap.recycle() }
    }

    private fun drawableState(drawable: android.graphics.drawable.Drawable?): String {
        if (drawable == null) return "null"
        val children = when (drawable) {
            is android.graphics.drawable.LayerDrawable -> (0 until drawable.numberOfLayers).map { drawable.getDrawable(it) }
            is android.graphics.drawable.InsetDrawable -> listOf(drawable.drawable)
            is android.graphics.drawable.StateListDrawable -> listOf(drawable.current)
            else -> emptyList()
        }
        return "${drawable.javaClass.simpleName}[${drawable.bounds};${drawable.state.joinToString()};" +
            "${drawable.isVisible};${drawable.alpha}]${children.joinToString { drawableState(it) }}"
    }

    @Test fun countrySectionsAndStickyHeadersStayReachableAndCollapseLongGroups() {
        val entries = (0 until 60).map { "US" to "Sticky US $it" } +
            listOf("United Kingdom" to "Sticky UK", "Canada" to "Sticky Canada", "Taiwan" to "Sticky Taiwan",
                "Hong Kong SAR China" to "Sticky Hong Kong", "My channels" to "Sticky custom")
        importPrograms("#EXTM3U\n" + entries.mapIndexed { index, (group, name) ->
            "#EXTINF:-1 group-title=\"$group\",$name\nhttps://radio.example/sticky-$index.mp3"
        }.joinToString("\n"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain {
                activity.findViewById<View>(R.id.nav_radio).performClick()
                activity.findViewById<EditText>(R.id.radioSearch).setText("Sticky")
            }
            await { model.radioChannels.value.count { it.section != null } == 3 &&
                activity.findViewById<RecyclerView>(R.id.radioList).let {
                    it.adapter?.itemCount == 9 && it.childCount > 0 && !it.isLayoutRequested
                } }
            onMain {
                assertEquals(RadioSection.entries, model.radioChannels.value.mapNotNull { it.section })
                assertEquals(listOf("united states", "united kingdom", "country:ca"),
                    model.radioChannels.value.filter { it.section == null && it.group.section == RadioSection.INTERNATIONAL }
                        .map { it.group.id })
            }
            screenshot("radio138-country-sections")
            val group = radioGroupButton(activity, "united states")
            onMain { group.performClick() }
            await { model.radioChannels.value.count { it.station != null } == 60 }
            var grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            val position = onMain { model.radioChannels.value.indexOfFirst { it.station?.name == "Sticky US 40" } }
            onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
            await(diagnostic = {
                val sticky = activity.findViewById<View>(R.id.radioStickyGroup)
                "Sticky=${sticky.visibility}/${sticky.height}/${sticky.isLayoutRequested}; " +
                    "grid=${grid.isShown}/${grid.isLayoutRequested}/${grid.adapter?.itemCount}; " +
                    "first=${(grid.layoutManager as GridLayoutManager).findFirstVisibleItemPosition()}; target=$position"
            }) { activity.findViewById<View>(R.id.radioStickyGroup).let {
                it.isShown && it.height > 0 && !it.isLayoutRequested
            } && grid.findViewHolderForAdapterPosition(position) != null && !grid.isLayoutRequested }
            onMain {
                val sticky = activity.findViewById<MaterialButton>(R.id.radioStickyGroup)
                assertEquals(activity.getString(R.string.radio_region_usa), sticky.text.toString())
                assertTrue(sticky.isClickable)
                assertTrue(sticky.height >= 48 * activity.resources.displayMetrics.density)
                assertEquals(activity.getString(R.string.radio_group_expanded), ViewCompat.getStateDescription(sticky))
                assertTrue(grid.childCount < 30)
            }
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            onMain { assertTrue(grid.findViewHolderForAdapterPosition(position)!!.itemView.requestFocus()) }
            await {
                val row = grid.findViewHolderForAdapterPosition(position)?.itemView
                val sticky = activity.findViewById<View>(R.id.radioStickyGroup)
                row?.hasFocus() == true && row.top >= sticky.bottom + sticky.translationY
            }
            screenshot("radio138-sticky-country-header")
            onView(withId(R.id.radioStickyGroup)).perform(androidx.test.espresso.action.ViewActions.longClick())
            val touchChoices = dialogList()
            assertEquals(1, onMain { touchChoices.checkedItemCount })
            screenshot("radio139-sticky-country-management")
            countryAction(android.R.id.button2)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            onMain { assertTrue(activity.findViewById<View>(R.id.radioStickyGroup).requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MENU)
            val remoteChoices = dialogList()
            assertEquals(1, onMain { remoteChoices.checkedItemCount })
            countryAction(android.R.id.button2)
            scenario.recreate()
            scenario.onActivity { activity = it; model = ViewModelProvider(it)[MusicViewModel::class.java] }
            activity = settledMain()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            await { model.radioChannels.value.count { it.station != null } == 60 && grid.adapter?.itemCount == 69 }
            onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
            await { activity.findViewById<View>(R.id.radioStickyGroup).isShown }
            onView(withId(R.id.radioStickyGroup)).perform(click())
            await { model.radioChannels.value.none { it.expanded || it.station != null } &&
                grid.adapter?.itemCount == 9 && !activity.findViewById<View>(R.id.radioStickyGroup).isShown &&
                !grid.isLayoutRequested }
            onMain {
                val header = model.radioChannels.value.indexOfFirst { it.group.id == "united states" && it.section == null }
                val visible = grid.findViewHolderForAdapterPosition(header)?.itemView
                assertNotNull("Collapse must return to the surviving country heading", visible)
                assertTrue(visible!!.top >= 0 && visible.top < grid.height / 3)
            }
            screenshot("radio138-sticky-collapsed")
            assertEquals(0, network.connectionAttempts.get())
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    @Test fun leftRightExpandsCategoriesAndCollapsesStickyHeadingsWithoutPlaying() {
        importPrograms("#EXTM3U\n" + (0 until 70).joinToString("\n") {
            "#EXTINF:-1 group-title=\"US\",Directional station $it\nhttps://radio.example/direction-$it.mp3"
        })
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain {
                activity.findViewById<View>(R.id.nav_radio).performClick()
                activity.findViewById<EditText>(R.id.radioSearch).setText("Directional")
            }
            await { model.radioChannels.value.any { it.group.id == "united states" } }
            val group = radioGroupButton(activity, "united states")
            instrumentation.setInTouchMode(false)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            onMain { assertTrue(group.requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { model.radioChannels.value.count { it.station != null } == 70 }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            onMain {
                activity.dispatchKeyEvent(KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 2))
                assertEquals(70, model.radioChannels.value.count { it.station != null })
                assertFalse(controller.playWhenReady)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            await { model.radioChannels.value.none { it.station != null } }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { model.radioChannels.value.count { it.station != null } == 70 }
            val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
            val position = onMain { model.radioChannels.value.indexOfFirst { it.station?.name == "Directional station 50" } }
            onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
            await { activity.findViewById<View>(R.id.radioStickyGroup).isShown && !grid.isLayoutRequested }
            onMain { assertTrue(activity.findViewById<View>(R.id.radioStickyGroup).requestFocus()) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(70, model.radioChannels.value.count { it.station != null })
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT)
            await { model.radioChannels.value.none { it.station != null } &&
                !activity.findViewById<View>(R.id.radioStickyGroup).isShown }
            val restored = radioGroupButton(activity, "united states")
            await(diagnostic = { "Collapsed heading focus=${activity.currentFocus?.id}" }) { restored.isFocused }
            onMain {
                assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                assertFalse(controller.playWhenReady)
                assertEquals(0, network.connectionAttempts.get())
            }
            screenshot("car154-category-keys")
        }
    }

    @Test fun screenOffRadioContinuesAndKeepScreenOnOnlyAppliesDuringVisiblePlayback() {
        org.junit.Assume.assumeTrue("Screen/power automation requires an emulator", android.os.Build.PRODUCT.contains("sdk"))
        fun shell(command: String) {
            instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            }
        }
        val previous = app.displaySettings.options.value
        network.customPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            (0 until 60).joinToString("") { "#EXTINF:4.0,\ntone$it.aac\n" } + "#EXT-X-ENDLIST\n"
        onMain { app.displaySettings.update { it.copy(keepScreenOn = false) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val activity = settledMain()
                command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, CnrRadio.stations.first().id) })
                await { controller.isPlaying && app.playbackActive.value }
                onMain { assertFalse(activity.findViewById<View>(R.id.root).keepScreenOn) }
                onMain {
                    ViewModelProvider(activity)[MusicViewModel::class.java].openSettings()
                    ViewModelProvider(activity)[MusicViewModel::class.java].settingsCategory(SettingsCategory.GENERAL)
                }
                onView(withId(R.id.settingsKeepScreenOn)).perform(scrollTo(), click())
                await { app.displaySettings.options.value.keepScreenOn &&
                    activity.findViewById<View>(R.id.root).keepScreenOn }
                onMain {
                    activity.startActivity(com.macarmusic.app.ui.FileExplorerActivity.intent(activity,
                        com.macarmusic.app.model.ExplorerMode.MUSIC_FILE))
                }
                var explorer: com.macarmusic.app.ui.FileExplorerActivity? = null
                await {
                    explorer = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<com.macarmusic.app.ui.FileExplorerActivity>().singleOrNull()
                    explorer?.findViewById<View>(R.id.explorerRoot)?.keepScreenOn == true &&
                        !activity.findViewById<View>(R.id.root).keepScreenOn
                }
                onMain {
                    assertFalse(activity.findViewById<View>(R.id.root).keepScreenOn)
                    checkNotNull(explorer).finish()
                }
                await { activity.hasWindowFocus() && activity.findViewById<View>(R.id.root).keepScreenOn }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                onMain { assertFalse(activity.findViewById<View>(R.id.root).keepScreenOn) }
                try {
                    shell("input keyevent KEYCODE_SLEEP")
                    await { !app.getSystemService(android.os.PowerManager::class.java).isInteractive }
                    val position = onMain { controller.currentPosition }
                    SystemClock.sleep(1_500)
                    onMain {
                        assertTrue(controller.isPlaying)
                        assertTrue("Radio position must continue with the screen off", controller.currentPosition > position + 500)
                    }
                    assertTrue(app.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any {
                        it.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0
                    })
                } finally { shell("input keyevent KEYCODE_WAKEUP") }
                scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                await { activity.findViewById<View>(R.id.root).keepScreenOn }
                onMain { controller.pause() }
                await { !app.playbackActive.value && !activity.findViewById<View>(R.id.root).keepScreenOn }
                onMain {
                    app.displaySettings.update { it.copy(keepScreenOn = false) }
                    controller.play()
                }
                await { controller.isPlaying && !activity.findViewById<View>(R.id.root).keepScreenOn }
                onMain { assertFalse(activity.findViewById<View>(R.id.root).keepScreenOn) }
                assertEquals(0, network.subscriptions.get())
                screenshot("car154-screen-policy")
            }
        } finally {
            onMain { controller.pause(); app.displaySettings.update { previous } }
        }
    }

    @Test fun subscriptionEditorsKeepLabelsFieldsErrorsAndButtonsApartWithoutDownloads() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.RADIO); model.openSettings() }
            await { activity.findViewById<View>(R.id.radioSubscriptions).isShown }
            onMain { activity.findViewById<View>(R.id.radioSubscriptions).performClick() }
            for ((label, suffix) in listOf(R.string.radio_subscription_hongkong to "hk", R.string.radio_subscription_taiwan to "tw")) {
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                onView(withId(R.id.playerDialogList)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    val list = view as android.widget.ListView
                    assertEquals(7, list.adapter.count)
                    assertEquals(RadioSubscriptions.presets.map { activity.getString(it.title) } +
                        activity.getString(R.string.radio_subscription_custom),
                        (0 until list.adapter.count).map { list.adapter.getItem(it).toString() })
                }
                onData(equalTo(activity.getString(label))).inRoot(isDialog()).perform(click())
                onView(withId(R.id.radioSubscriptionUrl)).inRoot(isDialog()).perform(scrollTo()).check { view, error ->
                    if (error != null) throw error
                    assertTrue((view as EditText).text.toString().endsWith("/countries/$suffix.m3u"))
                    assertSubscriptionEditorSpacing(view)
                }
                screenshot("radio138-editor-$suffix-font-${activity.resources.configuration.fontScale}")
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            }
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onData(equalTo(activity.getString(R.string.radio_subscription_custom))).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.radioSubscriptionUrl)).inRoot(isDialog()).perform(scrollTo()).check { view, error ->
                if (error != null) throw error
                assertSubscriptionEditorSpacing(view)
                val fields = generateSequence(view.parent) { it.parent }.filterIsInstance<TextInputLayout>()
                assertNotNull(fields.first().error)
            }
            screenshot("radio138-editor-errors-font-${activity.resources.configuration.fontScale}")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertTrue(app.radioCatalog.state.value.subscriptions.isEmpty())
            assertEquals(0, network.connectionAttempts.get())
            onMain { assertFalse(controller.playWhenReady); assertEquals(17_000L, controller.currentPosition) }
        }
    }

    private fun assertSubscriptionEditorSpacing(url: View) {
        val name = url.rootView.findViewById<EditText>(R.id.radioSubscriptionName)
        fun layout(view: View) = generateSequence(view.parent) { it.parent }.filterIsInstance<TextInputLayout>().first()
        val nameLayout = layout(name)
        val urlLayout = layout(url)
        val content = nameLayout.parent as android.widget.LinearLayout
        val density = url.resources.displayMetrics.density
        assertTrue("Help overlaps the name label", nameLayout.top - content.getChildAt(0).bottom >= 12 * density - 1)
        assertTrue("Name/error overlaps the URL label", urlLayout.top - nameLayout.bottom >= 12 * density - 1)
        assertTrue("Missing clearance beneath the URL selection handle", content.paddingBottom >= 16 * density - 1)
        for (input in listOf(name, url as EditText)) {
            assertTrue(input.height >= 56 * density - 1)
            assertTrue("Editor text is clipped", input.height - input.compoundPaddingTop - input.compoundPaddingBottom >=
                input.layout.height)
        }
        val footer = url.rootView.findViewById<View>(android.R.id.button1)
        val bodyBounds = android.graphics.Rect()
        val footerBounds = android.graphics.Rect()
        content.parent.let { it as View }.getGlobalVisibleRect(bodyBounds)
        assertTrue(footer.getGlobalVisibleRect(footerBounds))
        assertTrue("Editor body overlaps its footer", bodyBounds.bottom <= footerBounds.top)
    }

    private fun pinIds(grid: RecyclerView): List<String> {
        val adapter = (grid.adapter as? ConcatAdapter)?.adapters?.filterIsInstance<RadioButtonAdapter>()?.singleOrNull()
            ?: grid.adapter as? RadioButtonAdapter
        return adapter?.currentList.orEmpty().map { it.id }
    }

    private fun assertPinLabels(grid: RecyclerView) {
        await(diagnostic = {
            "Pin labels: shown=${grid.isShown}, layout=${grid.isLayoutRequested}, updates=${grid.hasPendingAdapterUpdates()}, " +
                "saved=${app.radio.options.value.pins}, visible=" +
                (0 until grid.childCount).map { index ->
                    val button = grid.getChildAt(index)
                    "${button.tag}:${(button as? android.widget.TextView)?.text}:${ViewCompat.getStateDescription(button)}"
                }
        }) {
            val buttons = (0 until grid.childCount).map { grid.getChildAt(it) }
                .filterIsInstance<MaterialButton>().filter { it.tag in pinIds(grid) }
            buttons.isNotEmpty() && !grid.isLayoutRequested && !grid.hasPendingAdapterUpdates() && buttons.all { button ->
                val station = checkNotNull(app.radioCatalog.find(button.tag as String))
                button.text.toString() == station.title(button.context) &&
                    button.contentDescription == station.title(button.context) &&
                    ViewCompat.getStateDescription(button).toString().contains(
                        button.context.getString(R.string.radio_pinned))
            }
        }
    }

    private fun countryAction(id: Int) = onView(withId(id)).inRoot(isDialog()).perform(scrollTo(), click())

    private fun assertCountryFooter(list: android.widget.ListView, hasDelete: Boolean) {
        val ids = listOf(R.id.playerDialogSelectAll, R.id.playerDialogDeselectAll) +
            (if (hasDelete) listOf(android.R.id.button3) else emptyList()) +
            listOf(android.R.id.button2, android.R.id.button1)
        val controls = onMain { ids.map { list.rootView.findViewById<View>(it) } }
        onMain {
            val row = controls.first().parent as android.view.ViewGroup
            val footer = row.parent as android.widget.HorizontalScrollView
            assertTrue(controls.all { it.parent === row })
            assertTrue("Every country action must share one footer row", controls.all { it.top == controls.first().top })
            assertEquals("Selection starts at the bottom left", 0, controls.first().left)
            assertEquals("The final action stays at the right end", row.width, controls.last().right)
            assertTrue(controls.zipWithNext().all { (left, right) -> left.right < right.left })
            val bodyBounds = android.graphics.Rect()
            val footerBounds = android.graphics.Rect()
            assertTrue(list.getGlobalVisibleRect(bodyBounds))
            assertTrue(footer.getGlobalVisibleRect(footerBounds))
            assertTrue("Country rows must not overlap the footer", bodyBounds.bottom <= footerBounds.top)
        }
        instrumentation.setInTouchMode(false)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(350)
        onMain { assertTrue(controls.first().requestFocus()) }
        for ((index, control) in controls.withIndex()) {
            if (index > 0) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            await(diagnostic = {
                val visible = android.graphics.Rect()
                control.getGlobalVisibleRect(visible)
                val scroll = control.parent.parent as android.widget.HorizontalScrollView
                "Footer focus=${control.hasFocus()}, control=${(control as android.widget.TextView).text}, " +
                    "size=${control.width}x${control.height}, visible=$visible, scroll=${scroll.scrollX}/${scroll.width}"
            }) {
                val visible = android.graphics.Rect()
                control.hasFocus() && control.getGlobalVisibleRect(visible) && visible.width() == control.width &&
                    visible.height() >= 48 * control.resources.displayMetrics.density
            }
        }
    }

    private fun dragPin(grid: RecyclerView, from: Int, to: Int, cancel: Boolean = false) {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0) }
        await(diagnostic = {
            "Drag $from->$to; grid=${grid.isShown}/${grid.isAttachedToWindow}/${grid.width}x${grid.height}/" +
                "${grid.isLayoutRequested}; pins=${pinIds(grid)}; children=" +
                (0 until grid.childCount).map { grid.getChildAdapterPosition(grid.getChildAt(it)) }
        }) { grid.findViewHolderForAdapterPosition(from) != null && grid.findViewHolderForAdapterPosition(to) != null &&
            !grid.isLayoutRequested && !grid.isComputingLayout }
        val points = onMain {
            listOf(from, to).map { position ->
                val view = grid.findViewHolderForAdapterPosition(position)!!.itemView
                val bounds = android.graphics.Rect()
                assertTrue(view.getGlobalVisibleRect(bounds))
                assertTrue("Drag target needs a visible 48dp touch area", bounds.height() >= 48 * view.resources.displayMetrics.density)
                val location = IntArray(2).also(view::getLocationOnScreen)
                val x = location[0] + view.width / 2f
                val y = location[1] + view.height / 2f
                assertTrue("Tile center must be visible", bounds.contains(x.toInt(), y.toInt()))
                x to y
            }
        }
        val extra = 8 * grid.resources.displayMetrics.density
        val endX = points[1].first + (points[1].first - points[0].first).coerceIn(-1f, 1f) * extra
        val endY = points[1].second + (points[1].second - points[0].second).coerceIn(-1f, 1f) * extra
        instrumentation.setInTouchMode(true)
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, points[0].first, points[0].second)
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout() + 150L)
        for (step in 1..12) {
            val fraction = step / 12f
            send(MotionEvent.ACTION_MOVE, points[0].first + (endX - points[0].first) * fraction,
                points[0].second + (endY - points[0].second) * fraction)
            SystemClock.sleep(30)
        }
        send(if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP, endX, endY)
        instrumentation.waitForIdleSync()
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
                return model.radioChannels.value.count { it.section == null } == count &&
                    grid.adapter?.itemCount == model.radioChannels.value.size &&
                    !grid.isLayoutRequested && !grid.isComputingLayout && grid.childCount > 0 &&
                    (0 until grid.childCount).all { index ->
                        val child = grid.getChildAt(index)
                        val row = model.radioChannels.value.getOrNull(grid.getChildAdapterPosition(child))
                        row != null && (row.section != null ||
                            child.contentDescription == (row.station?.title(activity) ?: row.group.title(activity)) &&
                            (row.station != null || ViewCompat.getStateDescription(child) == activity.getString(
                                if (row.expanded) R.string.radio_group_expanded else R.string.radio_group_collapsed)))
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
                val index = model.radioChannels.value.indexOfFirst { it.group.id == "import:accordion b" && it.station == null }
                grid.findViewHolderForAdapterPosition(index)?.itemView?.hasFocus() == true
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
            onMain { assertFalse(model.radioChannels.value.single { it.section == null }.expanded) }
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
            val id = CnrRadio.stations.first().id
            fun openStationMenu() {
                val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
                val position = onMain { pinIds(grid).indexOf(id) }
                assertTrue("The CNR fixture must be pinned", position >= 0)
                onMain { grid.scrollToPosition(position) }
                await { !grid.isLayoutRequested && !grid.hasPendingAdapterUpdates() &&
                    grid.findViewHolderForAdapterPosition(position) != null }
                onMain { grid.findViewHolderForAdapterPosition(position)!!.itemView.performLongClick() }
            }
            openStationMenu()
            onView(withText(R.string.radio_edit)).inRoot(isDialog()).perform(click())
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            screenshot("radio133-editor-open")
            onView(withId(R.id.radioEditSources)).inRoot(isDialog())
                .perform(scrollTo(), replaceText("https://radio.example/first/index.m3u8\nhttps://radio.example/backup/index.m3u8"), closeSoftKeyboard())
            onView(withId(R.id.radioEditGroup)).inRoot(isDialog()).perform(scrollTo(), replaceText("My stations"), closeSoftKeyboard())
            screenshot("radio133-editor")
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            await { app.radioCatalog.find(id)?.sources?.size == 2 }
            assertEquals(0, network.requests.size)
            openStationMenu()
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
            onView(withId(R.id.radioBuffer)).perform(scrollTo(), click())
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(3).perform(click())
            onView(withId(R.id.radioStartBuffer)).perform(scrollTo(), click())
            onData(org.hamcrest.Matchers.anything()).inRoot(isDialog()).atPosition(2).perform(click())
            onView(withId(R.id.radioAutoSource)).perform(scrollTo(), click())
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
        onMain {
            app.radio.setPanelVisible(false)
            app.radio.tune(RadioTuning(retries = 0, autoSource = true))
        }
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
        onMain {
            app.radio.setPanelVisible(false)
            app.radio.tune(RadioTuning(retries = 0, autoSource = true))
        }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { network.manifests.get() == 3 && controller.playerError != null && !controller.playWhenReady }
        val requests = network.requests.size
        SystemClock.sleep(1200)
        assertEquals(requests, network.requests.size)
        assertEquals(0, network.active.get())
        assertEquals(station.id, onMain { controller.currentMediaItem?.mediaId })
        assertEquals(0, network.catalogues.get())
    }

    @Test fun persistentRadioRecoveryRetriesManuallyWithoutChangingStationOrVisibility() {
        val station = importPrograms((1..3).joinToString("\n") {
            "Recovery station,https://radio.example/recovery-$it/index.m3u8"
        }).single()
        network.failStreams = 503
        onMain {
            app.radio.setPanelVisible(false)
            app.radio.tune(RadioTuning(retries = 0, autoSource = true))
            app.displaySettings.update { it.copy(noticeDurationSeconds = 5) }
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
            await { network.manifests.get() == 3 && app.playbackProblem.value != null && !controller.playWhenReady }
            await { app.notice.value == null }
            onMain {
                assertTrue(activity.findViewById<View>(R.id.recoveryStatus).isShown)
                assertFalse(activity.findViewById<View>(R.id.recoveryReselect).isShown)
                assertTrue(activity.findViewById<android.widget.TextView>(R.id.status).text.toString().startsWith(app.text(R.string.radio)))
            }
            val attempts = network.manifests.get()
            onMain { activity.findViewById<View>(R.id.recoveryRetry).performClick() }
            await { network.manifests.get() == attempts + 3 && controller.playerError != null && !controller.playWhenReady }
            SystemClock.sleep(800)
            assertEquals("A manual retry remains bounded", attempts + 3, network.manifests.get())
            network.failStreams = 0
            await { activity.findViewById<View>(R.id.recoveryRetry).isEnabled }
            onMain { activity.findViewById<View>(R.id.recoveryRetry).performClick() }
            await { controller.isPlaying && app.playbackProblem.value == null }
            onMain {
                assertEquals(station.id, controller.currentMediaItem?.mediaId)
                assertFalse(app.radio.options.value.showPanel)
                assertFalse(activity.findViewById<View>(R.id.recoveryStatus).isShown)
            }
            command(RadioCommands.music)
            await { !app.radio.options.value.active && controller.currentMediaItem?.mediaId == tracks[1].id }
            onMain {
                assertTrue(activity.findViewById<android.widget.TextView>(R.id.status).text.toString().startsWith(app.text(R.string.music_mode)))
                assertFalse(controller.playWhenReady)
            }
        }
    }

    @Test fun changingAwayFromFailedRadioClearsItsRecoveryWithoutAutoplay() {
        val station = importPrograms("Failed station,https://radio.example/error/index.m3u8").single()
        network.failStreams = 503
        onMain { app.radio.tune(RadioTuning(retries = 0, autoSource = false)) }
        command(RadioCommands.play, Bundle().apply { putString(RadioCommands.STATION, station.id) })
        await { app.playbackProblem.value != null }
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id && app.playbackProblem.value == null }
        onMain { assertFalse(controller.playWhenReady) }
    }

    @Test fun errorPauseDoesNotCancelRecoveryWaitingForCatalogIo() = assertRecoveryAfterCatalogIo()

    @Test fun stopCancelsRecoveryWaitingForCatalogIo() = assertRecoveryAfterCatalogIo { controller.stop() }

    @Test fun pauseCancelsRecoveryEvenWhenTheFailedPlayerIsAlreadyPaused() = assertRecoveryAfterCatalogIo { controller.pause() }

    private fun assertRecoveryAfterCatalogIo(cancel: (() -> Unit)? = null) = runBlocking {
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
            if (cancel != null) {
                onMain(cancel)
                instrumentation.waitForIdleSync()
            }
        } finally { release.countDown(); blocker.await() }
        if (cancel == null) {
            await { controller.isPlaying && app.radioCatalog.find(station.id)?.sourceIndex == 1 }
            assertEquals(1, network.requests.count { it.contains("/unavailable/") })
            assertTrue(network.requests.any { it.endsWith("/working/index.m3u8") })
            onMain {
                assertEquals(station.id, controller.currentMediaItem?.mediaId)
                assertNull(app.playbackProblem.value)
            }
        } else {
            SystemClock.sleep(1200)
            assertTrue(network.requests.none { it.contains("/working/") })
            onMain { assertFalse(controller.playWhenReady); assertEquals(Player.STATE_IDLE, controller.playbackState) }
        }
    }

    @Test fun pinnedAndGlobalNavigationWrapWithoutReconnectingOnPreferenceChanges() {
        network.customPlaylist = finitePlaylist()
        val programs = navigationFixtures()
        runBlocking { app.radioCatalog.withReplacement(programs, false) { Unit } }
        val pins = programs.subList(1, 3).map { it.id }
        setFixturePins(pins)
        onMain {
            app.radio.tune(RadioTuning(downloadLogos = false))
            app.radio.hideGroups(setOf(RadioGroups.of(programs[0]).id))
        }
        fun ids() = onMain { (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId } }
        fun step(next: Boolean, station: RadioProgram) {
            onMain { if (next) controller.seekToNextMediaItem() else controller.seekToPreviousMediaItem() }
            await { controller.currentMediaItem?.mediaId == station.id && controller.isPlaying }
        }
        playProgram(programs[2])
        assertEquals(programs.drop(1).map { it.id }, ids())
        step(true, programs[3])
        playProgram(programs.last())
        step(true, programs[1])
        step(false, programs.last())
        playProgram(programs[2])
        val requests = network.manifests.get()
        val position = onMain { controller.currentPosition }
        onMain { app.radio.setNavigationMode(com.macarmusic.app.radio.RadioNavigationMode.PINNED_ONLY) }
        await { controller.mediaItemCount == 2 }
        assertEquals(pins, ids())
        onMain {
            assertTrue("Changing navigation must keep playback active", controller.isPlaying)
            assertTrue("Position jumped from $position to ${controller.currentPosition}",
                controller.currentPosition >= position - 100)
        }
        assertEquals(requests, network.manifests.get())
        step(true, programs[1])
        step(false, programs[2])
        val beforeReorder = network.manifests.get()
        setFixturePins(pins.reversed())
        await { controller.getMediaItemAt(0).mediaId == pins.last() }
        assertEquals(beforeReorder, network.manifests.get())
        setFixturePins(listOf(programs[2].id))
        await { controller.mediaItemCount == 1 && !controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) }
        onMain { controller.seekToNextMediaItem(); controller.seekToPreviousMediaItem() }
        instrumentation.waitForIdleSync()
        assertEquals(beforeReorder, network.manifests.get())
        setFixturePins(emptyList())
        await { !controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) }
        assertEquals(beforeReorder, network.manifests.get())
        setFixturePins(pins)
        playProgram(programs[4])
        step(true, programs[1])
        assertEquals("Excluded current stations must not remain in the subsequent loop", pins, ids())
        playProgram(programs[4])
        step(false, programs[2])
        assertEquals(pins, ids())
        command(RadioCommands.music)
        await { controller.currentMediaItem?.mediaId == tracks[1].id }
        onMain {
            assertFalse(controller.playWhenReady)
            assertEquals(17_000L, controller.currentPosition)
            assertEquals(PlayOrder.RANDOM, app.playbackSettings.options.value.order)
            assertEquals(LoopMode.SINGLE_FILE, app.playbackSettings.options.value.loop)
        }
    }

    @Test fun openingRadioRevealsUnpinnedPlaybackAndCentersSearchClearWithoutChangingTheStream() {
        network.customPlaylist = finitePlaylist()
        val programs = List(80) { index -> RadioProgram(
            "radio:custom:${(1000 + index).toString(16).padStart(64, '0')}",
            "Reveal $index", "https://radio.example/reveal$index/index.m3u8", if (index < 10) "US" else "UK")
        }
        runBlocking { app.radioCatalog.withReplacement(programs, false) { Unit } }
        setFixturePins(listOf(programs.first().id))
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)) }
        playProgram(programs[60])
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain {
                model.radioQuery("not present")
                model.screen(R.id.nav_radio)
            }
            await { model.radioRevealId == null && model.radioQuery.value.isEmpty() &&
                model.radioChannels.value.any { it.station?.id == programs[60].id } }
            await {
                val grid = activity.findViewById<RecyclerView>(R.id.radioList)
                val row = model.radioChannels.value.indexOfFirst { it.station?.id == programs[60].id }
                grid.findViewHolderForAdapterPosition(row + 1)?.itemView?.let { view ->
                    val rect = android.graphics.Rect()
                    view.isSelected && view.getGlobalVisibleRect(rect) && rect.height() == view.height
                } == true
            }
            val manifests = network.manifests.get()
            screenshot("radio142-revealed-station")
            onView(withId(R.id.radioSearch)).perform(click(), replaceText("Reveal"), closeSoftKeyboard())
            await {
                activity.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)?.isShown == true &&
                    !activity.findViewById<View>(R.id.radioSearch).isLayoutRequested
            }
            onMain {
                val clear = activity.findViewById<android.widget.ImageView>(com.google.android.material.R.id.text_input_end_icon)
                assertCenteredImageInk(clear)
                assertTrue(clear.width >= (48 * activity.resources.displayMetrics.density).toInt())
                clear.performClick()
            }
            await { model.radioQuery.value.isEmpty() }
            assertEquals(manifests, network.manifests.get())
            onMain {
                model.screen(R.id.nav_player)
                app.radio.hideGroups(setOf(RadioGroups.of(programs[60]).id))
                model.screen(R.id.nav_radio)
            }
            await { app.notice.value?.text == app.text(R.string.radio_current_group_hidden) }
            onMain {
                assertTrue(RadioGroups.of(programs[60]).id in app.radio.options.value.hiddenGroups)
                assertTrue(controller.isPlaying)
                assertEquals(programs[60].id, controller.currentMediaItem?.mediaId)
            }
            assertEquals(manifests, network.manifests.get())
        }
    }

    @Test fun pinnedLogoDownloadsAreExplicitCachedReportedAndCancellableAcrossRecreation() {
        onMain { app.radio.setPanelVisible(false) }
        val programs = List(4) { index -> RadioProgram(
            "radio:custom:${(1100 + index).toString(16).padStart(64, '0')}", "Logo $index",
            "https://radio.example/logo$index/index.m3u8", logoUri = when (index) {
                0, 1 -> "https://radio.example/shared.png"
                2 -> ""
                else -> "https://radio.example/invalid.png"
            })
        }
        runBlocking { app.radioCatalog.withReplacement(programs, false) { Unit } }
        setFixturePins(programs.map { it.id })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.RADIO); model.openSettings() }
            await { activity.findViewById<View>(R.id.radioDownloadPinnedLogos)?.isShown == true }
            assertEquals(0, network.logos.get())
            onView(withId(R.id.radioDownloadPinnedLogos)).perform(scrollTo(), click())
            await { model.logoProgress.value == null && network.logos.get() == 2 }
            assertEquals(app.text(R.string.radio_logos_done, 1, 1, 1, 1), app.notice.value?.text)
            assertTrue(app.notice.value?.isError == true)
            assertNotNull(runBlocking { RadioLogoRepository(app).cached(programs.first()) })
            assertEquals(2, network.logos.get())
            onView(withId(R.id.radioDownloadPinnedLogos)).perform(scrollTo(), click())
            await { model.logoProgress.value == null && network.logos.get() == 3 }
            assertEquals(app.text(R.string.radio_logos_done, 0, 2, 1, 1), app.notice.value?.text)
            runBlocking { app.radioLogos.clear() }
            network.logoStarted = CountDownLatch(1)
            val release = CountDownLatch(1)
            network.logoRelease = release
            try {
                onView(withId(R.id.radioDownloadPinnedLogos)).perform(scrollTo(), click())
                assertTrue(network.logoStarted!!.await(5, TimeUnit.SECONDS))
                onMain { model.setRadioPanelVisible(true); model.setRadioPanelVisible(false) }
                assertTrue(model.logoProgress.value?.cancelling == false)
                scenario.recreate()
                activity = settledMain()
                model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                await { model.logoProgress.value != null &&
                    activity.findViewById<View>(R.id.radioCancelLogos).visibility == View.VISIBLE }
                onView(withId(R.id.radioCancelLogos)).perform(scrollTo())
                screenshot("radio142-logo-download-progress")
                onView(withId(R.id.radioCancelLogos)).perform(click())
                await { model.logoProgress.value?.cancelling == true }
            } finally { release.countDown(); network.logoRelease = null; network.logoStarted = null }
            await { model.logoProgress.value == null && network.active.get() == 0 }
            assertEquals(4, network.logos.get())
            assertNull(runBlocking { app.radioLogos.cached(programs.first()) })
            assertEquals(app.text(R.string.radio_logos_cancelled), app.notice.value?.text)
            onMain {
                assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                assertFalse(controller.playWhenReady)
                assertEquals(17_000L, controller.currentPosition)
            }
        }
    }

    @Test fun appearanceChoicesPersistAndUnavailableRadioControlsStayGreyAndCentered() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Appearance,https://radio.example/appearance/index.m3u8").single()
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)) }
        playProgram(station)
        val connections = network.manifests.get()
        ActivityScenario.launch(MainActivity::class.java).use {
            var activity = settledMain()
            var model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
            await { activity.findViewById<View>(R.id.settingsAppLogo)?.isShown == true }
            onView(withId(R.id.settingsAppLogo)).perform(scrollTo(), click())
            assertAppearanceChoices(R.id.appearanceMusic, R.id.appearanceRadio)
            screenshot("radio142-app-logo-choices")
            onView(withId(R.id.appearanceRadio)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.appLogo == com.macarmusic.app.model.AppLogo.RADIO }
            val launchers = app.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName), 0)
            assertEquals(listOf("${app.packageName}.RadioLauncher"), launchers.map { it.activityInfo.name })
            onView(withId(R.id.settingsButtonIcons)).perform(scrollTo(), click())
            assertAppearanceChoices(*com.macarmusic.app.ui.AppearanceChoices.buttonThemes.map { choice -> choice.id }.toIntArray())
            screenshot("radio142-icon-style-choices")
            onView(withId(R.id.iconsColorful)).inRoot(isDialog()).perform(click())
            await { app.displaySettings.options.value.buttonIcons == com.macarmusic.app.model.ButtonIconStyle.COLORFUL }
            assertEquals(com.macarmusic.app.model.AppLogo.RADIO, DisplaySettings(app) {}.options.value.appLogo)
            assertEquals(com.macarmusic.app.model.ButtonIconStyle.COLORFUL, DisplaySettings(app) {}.options.value.buttonIcons)
            onMain { model.settingsCategory(SettingsCategory.RADIO) }
            onView(withId(R.id.radioButtonLogo)).perform(scrollTo(), click())
            assertAppearanceChoices(R.id.radioLogoClassic, R.id.radioLogoBranded)
            screenshot("radio142-radio-logo-choices")
            onView(withId(R.id.radioLogoClassic)).inRoot(isDialog()).perform(click())
            await { app.radio.options.value.buttonLogo == com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO }
            assertEquals(com.macarmusic.app.radio.RadioButtonLogo.CLASSIC_RADIO, RadioSettings(app).options.value.buttonLogo)
            onView(withId(R.id.radioButtonLogo)).perform(scrollTo(), click())
            onView(withId(R.id.radioLogoBranded)).inRoot(isDialog()).perform(click())
            onMain { model.screen(R.id.nav_player) }
            for (theme in listOf(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES,
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)) {
                onMain { app.themeMode = theme }
                activity = settledMain()
                model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                onMain { model.screen(R.id.nav_player) }
                await { activity.findViewById<View>(R.id.shuffle)?.isShown == true &&
                    !activity.findViewById<View>(R.id.shuffle).isEnabled }
                for (style in com.macarmusic.app.model.ButtonIconStyle.entries) {
                    onMain { app.displaySettings.update { display -> display.copy(buttonIcons = style) } }
                    instrumentation.waitForIdleSync()
                    onMain {
                        for (id in listOf(R.id.shuffle, R.id.repeat, R.id.rewind, R.id.forward)) {
                            val button = activity.findViewById<android.widget.ImageView>(id)
                            assertFalse(button.isEnabled)
                            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                            val drawable = button.drawable
                            val bounds = android.graphics.Rect(drawable.bounds)
                            try {
                                drawable.setBounds(0, 0, 96, 96)
                                drawable.draw(android.graphics.Canvas(bitmap))
                                val pixels = IntArray(96 * 96).also { bitmap.getPixels(it, 0, 96, 0, 0, 96, 96) }
                                assertTrue(pixels.count { android.graphics.Color.alpha(it) > 200 } > 30)
                                val grey = com.macarmusic.app.ui.PlayerIcons.tint(activity, 0)
                                    .getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                                assertTrue("Unavailable controls must be dimmed, not white",
                                    pixels.filter { android.graphics.Color.alpha(it) == 255 }.all { it == grey })
                            } finally { drawable.bounds = bounds; bitmap.recycle() }
                        }
                        assertFalse(activity.findViewById<View>(R.id.shuffle).isSelected)
                        assertFalse(activity.findViewById<View>(R.id.repeat).isSelected)
                        assertTrue(activity.findViewById<View>(R.id.play).isEnabled)
                        assertTrue(activity.findViewById<View>(R.id.favorite).isEnabled)
                        assertTrue(activity.findViewById<View>(R.id.volumeMute).isEnabled)
                        assertCenteredImageInk(activity.findViewById(R.id.navigationArtwork))
                    }
                    screenshot("radio142-controls-$theme-${style.name.lowercase()}")
                }
            }
            assertEquals(connections, network.manifests.get())
            assertEquals(0, network.logos.get())
            command(RadioCommands.music)
            await { controller.currentMediaItem?.mediaId == tracks[1].id && activity.findViewById<View>(R.id.shuffle).isEnabled }
            onMain {
                assertTrue(activity.findViewById<View>(R.id.shuffle).isSelected)
                assertTrue(activity.findViewById<View>(R.id.repeat).isSelected)
            }
        }
    }

    @Test fun nineThemeTilesFitAndReflowWithWindowSizeWhileRemoteSelectionPreservesPlayback() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Gradient picker,https://radio.example/gradient/index.m3u8").single()
        onMain {
            app.radio.tune(RadioTuning(downloadLogos = false))
            app.displaySettings.update { it.copy(buttonIcons = com.macarmusic.app.model.ButtonIconStyle.LOGO) }
        }
        playProgram(station)
        val connections = network.manifests.get()
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
            val choices = com.macarmusic.app.ui.AppearanceChoices.buttonThemes
            assertEquals(com.macarmusic.app.model.ButtonIconStyle.entries, choices.map { choice -> choice.style })
            for (selected in listOf(R.id.iconsCopilotAurora, R.id.iconsCopilotSunrise, R.id.iconsLogo)) {
                onView(withId(R.id.settingsButtonIcons)).perform(scrollTo(), click())
                lateinit var grid: android.widget.GridLayout
                onView(withId(R.id.appearanceGrid)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    grid = view as android.widget.GridLayout
                    assertEquals(9, grid.childCount)
                    if (activity.resources.configuration.fontScale <= 1f) {
                        assertEquals(3, grid.columnCount)
                        for (index in 0 until grid.childCount) {
                            val tile = grid.getChildAt(index)
                            val visible = android.graphics.Rect()
                            assertTrue("All nine normal-size tiles fit without scrolling", tile.getGlobalVisibleRect(visible))
                            assertEquals(tile.height, visible.height())
                        }
                    }
                    val checked = (0 until grid.childCount).map { grid.getChildAt(it) as MaterialButton }.filter { it.isChecked }
                    assertEquals(1, checked.size)
                    assertEquals(app.displaySettings.options.value.buttonIcons, choices.single { choice -> choice.id == checked.single().id }.style)
                }
                assertAppearanceChoices(*choices.map { choice -> choice.id }.toIntArray())
                screenshot("radio147-nine-themes-${selected}")
                val originalWidth = onMain { (grid.rootView.layoutParams as android.view.WindowManager.LayoutParams).width }
                onMain {
                    val rootView = grid.rootView
                    val parameters = rootView.layoutParams as android.view.WindowManager.LayoutParams
                    parameters.width = (240 * activity.resources.displayMetrics.density).toInt()
                    activity.windowManager.updateViewLayout(rootView, parameters)
                }
                await { grid.columnCount < 3 }
                assertAppearanceChoices(*choices.map { choice -> choice.id }.toIntArray())
                onMain {
                    val rootView = grid.rootView
                    val parameters = rootView.layoutParams as android.view.WindowManager.LayoutParams
                    parameters.width = originalWidth
                    activity.windowManager.updateViewLayout(rootView, parameters)
                }
                instrumentation.waitForIdleSync()
                onView(withId(selected)).inRoot(isDialog()).perform(scrollTo())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                onMain { assertTrue(grid.findViewById<View>(selected).requestFocus()) }
                screenshot("radio147-theme-focus-${selected}")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                val style = choices.single { choice -> choice.id == selected }.style
                await { app.displaySettings.options.value.buttonIcons == style }
                assertEquals(style, DisplaySettings(app) {}.options.value.buttonIcons)
            }
            onMain { model.screen(R.id.nav_player); assertTrue(controller.isPlaying) }
            assertEquals(connections, network.manifests.get())
            assertEquals(0, network.logos.get())
        }
    }

    private fun setFixturePins(ids: List<String>) = onMain {
        assertTrue(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("radioPins", ids.joinToString("\n")).commit())
        app.radio.publishRestored()
    }

    private fun navigationFixtures() = listOf("Canada", "UK", "US", "Beijing", "Beijing", "Custom").mapIndexed { index, group ->
        RadioProgram("radio:custom:${(900 + index).toString(16).padStart(64, '0')}",
            "Navigation $index", "https://radio.example/nav$index/index.m3u8", group)
    }

    private fun assertAppearanceChoices(vararg ids: Int) {
        for (id in ids) onView(withId(id)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            val button = view as MaterialButton
            assertNotNull("The preview belongs above its label", button.compoundDrawablesRelative[1])
            assertNull(button.compoundDrawablesRelative[0])
            val layout = requireNotNull(button.layout)
            assertTrue(layout.lineCount in 1..button.maxLines)
            for (line in 0 until layout.lineCount) assertEquals(0, layout.getEllipsisCount(line))
            assertTrue(layout.getLineBottom(layout.lineCount - 1) <=
                button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
        }
    }

    private fun assertCenteredImageInk(view: android.widget.ImageView) {
        assertEquals(view.paddingLeft, view.paddingRight)
        assertEquals(view.paddingTop, view.paddingBottom)
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(image).apply {
                translate(view.paddingLeft.toFloat(), view.paddingTop.toFloat())
                concat(view.imageMatrix)
                view.drawable.draw(this)
            }
            val pixels = IntArray(view.width * view.height).also { image.getPixels(it, 0, view.width, 0, 0, view.width, view.height) }
            val ink = pixels.indices.filter { android.graphics.Color.alpha(pixels[it]) > 32 }
            assertTrue("The icon must have visible ink", ink.isNotEmpty())
            val centerX = (ink.minOf { it % view.width } + ink.maxOf { it % view.width } + 1) / 2f
            val centerY = (ink.minOf { it / view.width } + ink.maxOf { it / view.width } + 1) / 2f
            val tolerance = 1.5f * view.resources.displayMetrics.density
            assertEquals("Icon X center", view.width / 2f, centerX, tolerance)
            assertEquals("Icon Y center", view.height / 2f, centerY, tolerance)
        } finally { image.recycle() }
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

    @Test fun twelveBundledMarksAreDistinctCachedAndSurviveRenameRefreshAndClearWithoutReconnects() {
        network.customPlaylist = finitePlaylist()
        val feed = runBlocking { app.radioCatalog.saveSubscription("Logo subscription", "https://radio.example/logo-list") }
        val entries = listOf(RadioListEntry("Fixed logo station", "https://radio.example/logo-choice/index.m3u8"))
        runBlocking { app.radioCatalog.refreshSubscription(feed.id, emptyMap(), { RadioSubscriptionDownload(entries) }) }
        val station = app.radioCatalog.state.value.programs.single { it.name == "Fixed logo station" }
        onMain {
            app.radio.tune(RadioTuning(downloadLogos = false))
            assertTrue(app.radio.pin(station))
        }
        playProgram(station)
        val manifests = network.manifests.get()
        val selections = com.macarmusic.app.radio.RadioStationLogo.entries
        assertEquals(12, selections.size)
        val sheet = Bitmap.createBitmap(4 * 160, 3 * 160, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        canvas.drawColor(0xff171c27.toInt())
        val rendered = selections.mapIndexed { index, logo ->
            onMain { app.radio.setStationLogo(station.id, logo) }
            val bitmap = runBlocking { requireNotNull(app.radioLogos.cached(station)) }
            assertEquals(128, bitmap.width)
            assertEquals(128, bitmap.height)
            assertSame(bitmap, runBlocking { app.radioLogos.cached(station) })
            assertEquals(logo, RadioSettings(app).options.value.stationLogos[station.id])
            canvas.drawBitmap(bitmap, (index % 4 * 160 + 16).toFloat(), (index / 4 * 160 + 16).toFloat(), null)
            val pixels = IntArray(128 * 128).also { bitmap.getPixels(it, 0, 128, 0, 0, 128, 128) }
            assertTrue(pixels.count { android.graphics.Color.alpha(it) > 250 } > 10_000)
            pixels.toList()
        }
        try {
            val target = File(app.getExternalFilesDir(null), "test-screenshots/radio147-twelve-marks.png")
            target.parentFile!!.mkdirs()
            target.outputStream().use { assertTrue(sheet.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { sheet.recycle() }
        assertEquals("All twelve marks need visibly distinct artwork", 12, rendered.distinct().size)
        onMain { app.radio.setStationLogo(station.id, com.macarmusic.app.radio.RadioStationLogo.JAZZ) }
        val before = runBlocking { requireNotNull(app.radioLogos.cached(station)) }
        runBlocking {
            app.radioCatalog.refreshSubscription(feed.id, emptyMap(), { RadioSubscriptionDownload(entries) })
        }
        val original = app.radioCatalog.find(station.id)!!
        runBlocking { app.radioCatalog.edit(original, original.copy(name = "Renamed logo station")) }
        val renamed = app.radioCatalog.find(station.id)!!
        assertEquals(com.macarmusic.app.radio.RadioStationLogo.JAZZ, app.radioLogos.selected(renamed))
        assertSame(before, runBlocking { app.radioLogos.cached(renamed) })
        runBlocking { app.radioLogos.clear() }
        val after = runBlocking { requireNotNull(app.radioLogos.cached(renamed)) }
        assertNotSame(before, after)
        assertTrue(before.sameAs(after))
        assertTrue(after.sameAs(runBlocking { RadioLogoRepository(app).cached(renamed) }))
        assertEquals(RadioLogoRepository.Download.CACHED, runBlocking { app.radioLogos.download(renamed) })
        await { controller.isPlaying }
        assertEquals(manifests, network.manifests.get())
        assertEquals(0, network.logos.get())
        assertEquals(station.streamUri, app.radio.currentStation!!.streamUri)
    }

    @Test fun stationPopupOffersOnlyTwelveFixedMarksAndAutomaticWithImmediatePlayerUpdates() {
        network.customPlaylist = finitePlaylist()
        val station = importPrograms("Logo picker,https://radio.example/logo-picker/index.m3u8").single()
        onMain { app.radio.tune(RadioTuning(downloadLogos = false)) }
        setFixturePins(listOf(station.id))
        playProgram(station)
        val manifests = network.manifests.get()
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.screen(R.id.nav_radio) }
            fun button(gridId: Int = R.id.radioList) = activity.findViewById<RecyclerView>(gridId).let { grid ->
                (0 until grid.childCount).map { grid.getChildAt(it) }.filterIsInstance<MaterialButton>()
                    .firstOrNull { it.contentDescription == station.title(activity) }
            }
            await { button()?.isShown == true }
            onMain { button()!!.performLongClick() }
            onView(withText(R.string.radio_station_logo)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.radio_mark_news)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                val grid = view.parent as android.widget.GridLayout
                assertEquals(12, grid.childCount)
                assertTrue((0 until grid.childCount).all { grid.getChildAt(it) is MaterialButton })
            }
            screenshot("radio144-station-mark-picker")
            onView(withText(R.string.radio_mark_cctv)).inRoot(isDialog()).perform(scrollTo())
            onView(withText(R.string.radio_mark_cctv)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                val preview = view as MaterialButton
                val layout = requireNotNull(preview.layout)
                assertEquals(0, layout.getEllipsisCount(layout.lineCount - 1))
                assertTrue(preview.height >= 96 * preview.resources.displayMetrics.density)
            }.perform(click())
            await { app.radio.options.value.stationLogos[station.id] == com.macarmusic.app.radio.RadioStationLogo.CCTV }
            val bitmap = runBlocking { requireNotNull(app.radioLogos.cached(station)) }
            await { (button()?.icon as? android.graphics.drawable.BitmapDrawable)?.bitmap?.sameAs(bitmap) == true }
            onMain { model.screen(R.id.nav_player) }
            await { activity.findViewById<RecyclerView>(R.id.radioQuickStations)?.isShown == true &&
                (button(R.id.radioQuickStations)?.icon as? android.graphics.drawable.BitmapDrawable)?.bitmap?.sameAs(bitmap) == true }
            screenshot("radio144-custom-logo-playing")
            onMain { model.screen(R.id.nav_radio) }
            await { button()?.isShown == true }
            onMain { button()!!.performLongClick() }
            onView(withText(R.string.radio_station_logo)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.radio_station_logo_auto)).inRoot(isDialog()).perform(click())
            await { station.id !in app.radio.options.value.stationLogos }
            assertNull(runBlocking { app.radioLogos.cached(station) })
            assertEquals(manifests, network.manifests.get())
            assertEquals(0, network.logos.get())
            onMain { assertTrue(controller.isPlaying) }
        }
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

    @Test fun modeButtonResumesSavedMusicAndLastRadioSourceWithoutChangingLocalOptions() {
        network.customPlaylist = finitePlaylist()
        val imported = importPrograms("Mode switch,https://radio.example/modes/first.m3u8\n" +
            "Mode switch,https://radio.example/modes/second.m3u8").single()
        val station = runBlocking { app.radioCatalog.selectSource(imported.id, 1) }
        playProgram(station)
        command(RadioCommands.music)
        onMain {
            app.radio.setPanelVisible(false)
            assertFalse(controller.playWhenReady)
            assertEquals(17_000L, controller.currentPosition)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var activity = settledMain()
            val button = activity.findViewById<MaterialButton>(R.id.switchPlaybackMode)
            onMain {
                val favorite = activity.findViewById<View>(R.id.favorite)
                val details = activity.findViewById<View>(R.id.details)
                val row = button.parent as android.view.ViewGroup
                assertEquals(row.indexOfChild(button) + 1, row.indexOfChild(favorite))
                assertEquals(row.indexOfChild(favorite) + 1, row.indexOfChild(details))
                val minimum = (48 * activity.resources.displayMetrics.density).toInt()
                for (id in listOf(R.id.favorite, R.id.switchPlaybackMode, R.id.details))
                    activity.findViewById<View>(id).let { assertTrue(it.width >= minimum); assertTrue(it.height >= minimum) }
                assertEquals(activity.getString(R.string.resume_radio), button.contentDescription)
                assertFalse(activity.findViewById<View>(R.id.nav_radio).isShown)
                assertTrue(button.isEnabled)
            }
            val before = network.manifests.get()
            onMain { button.performClick(); button.performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == station.id && button.isEnabled }
            assertEquals("Rapid duplicate mode taps must open one stream", before + 1, network.manifests.get())
            assertEquals(station.sources[1], app.radio.currentStation?.streamUri)
            assertEquals(17_000L, app.radio.savedMusic.value!!.positionMs)
            val pinned = station.id in app.radio.options.value.pins
            val connections = network.manifests.get()
            onMain { activity.findViewById<View>(R.id.favorite).performClick() }
            await { (station.id in app.radio.options.value.pins) != pinned }
            onMain { activity.findViewById<View>(R.id.favorite).performClick() }
            await { (station.id in app.radio.options.value.pins) == pinned }
            onMain {
                assertEquals(station.id, controller.currentMediaItem?.mediaId)
                assertTrue(controller.isPlaying)
                assertEquals("Pin action must not switch or reconnect playback", connections, network.manifests.get())
            }
            screenshot("radio143-switch-radio")
            onMain { button.performClick(); button.performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks[1].id && button.isEnabled }
            onMain {
                assertTrue(controller.currentPosition in 17_000..21_000)
                assertEquals(2, controller.mediaItemCount)
                assertTrue(controller.shuffleModeEnabled)
                assertEquals(Player.REPEAT_MODE_ONE, controller.repeatMode)
                assertEquals(activity.getString(R.string.resume_radio), button.contentDescription)
                controller.pause()
                controller.seekTo(23_000)
            }
            await { !controller.playWhenReady && controller.currentPosition == 23_000L }
            onMain { button.performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == station.id && button.isEnabled }
            assertEquals(23_000L, app.radio.savedMusic.value!!.positionMs)
            assertEquals(station.sources[1], app.radio.currentStation?.streamUri)
            onMain { controller.pause() }
            await { controller.playbackState == Player.STATE_IDLE }
            onMain { button.performClick() }
            await { controller.isPlaying && controller.currentMediaItem?.mediaId == tracks[1].id && button.isEnabled }
            onMain { assertTrue(controller.currentPosition in 23_000..27_000) }
            screenshot("radio143-switch-music")
            onMain { controller.pause() }
            await { !controller.playWhenReady }
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { model.settingsCategory(SettingsCategory.GENERAL); model.openSettings() }
            await { activity.findViewById<View>(R.id.settingsAutoPlay)?.isShown == true }
            onView(withId(R.id.settingsAutoPlay)).perform(scrollTo(), click())
            assertTrue(DisplaySettings(app) {}.options.value.autoPlayOnLaunch)
            onMain { assertFalse(controller.playWhenReady) }
            screenshot("radio143-autoplay-setting")
            scenario.recreate()
            activity = settledMain()
            await { activity.findViewById<MaterialSwitch>(R.id.settingsAutoPlay)?.isChecked == true }
            onMain { assertFalse("Changing settings or recreation must not resume audio", controller.playWhenReady) }
        }
    }

    @Test fun currentStationTilesShareThePlaybackHighlightAcrossPages() {
        network.customPlaylist = finitePlaylist()
        val programs = importPrograms("#EXTM3U\n" + (1..3).joinToString("\n") {
            "#EXTINF:-1 group-title=\"Highlight fixtures\",Highlight $it\nhttps://radio.example/highlight-$it/index.m3u8"
        })
        val first = programs[0]
        val second = programs[1]
        val groupId = RadioGroups.of(first).id
        setFixturePins(listOf(first.id, second.id))
        val original = app.displaySettings.options.value
        val theme = app.themeMode
        val pinColor = app.radio.options.value.pinColor
        onMain {
            app.radio.setPinColor(0x008844)
            app.radio.tune(app.radio.options.value.tuning.copy(downloadLogos = false))
            app.radio.setRefreshInterval(RadioRefreshInterval.MANUAL)
        }
        playProgram(first)
        val connections = network.manifests.get()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                for (night in listOf(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)) {
                    onMain { app.themeMode = night }
                    val activity = settledMain()
                    val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                    for (surface in listOf("playing", "pins", "channels")) {
                        val playing = surface == "playing"
                        onMain { model.screen(if (playing) R.id.nav_player else R.id.nav_radio) }
                        val gridId = if (playing) R.id.radioQuickStations else R.id.radioList
                        await { activity.findViewById<RecyclerView>(gridId)?.isShown == true }
                        val grid = onMain { activity.findViewById<RecyclerView>(gridId) }
                        if (!playing) {
                            onMain { activity.findViewById<EditText>(R.id.radioSearch).setText("Highlight") }
                            await { model.radioPage.value.size == programs.size && pinIds(grid).size == 2 }
                        }
                        if (surface == "channels") {
                            val group = radioGroupButton(activity, groupId)
                            onMain { if (model.radioChannels.value.none { it.expanded }) group.performClick() }
                            await { model.radioChannels.value.count { it.station != null } == programs.size }
                            onMain {
                                val rows = model.radioChannels.value
                                val position = grid.adapter!!.itemCount - rows.size + rows.indexOfFirst { it.station?.id == first.id }
                                (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0)
                            }
                        } else onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(0, 0) }
                        fun tile(id: String): MaterialButton? {
                            if (surface != "channels") return grid.findViewWithTag(id)
                            val rows = model.radioChannels.value
                            val position = rows.indexOfFirst { it.station?.id == id }
                            if (position < 0) return null
                            return grid.findViewHolderForAdapterPosition(grid.adapter!!.itemCount - rows.size + position)
                                ?.itemView as? MaterialButton
                        }
                        await { tile(first.id) != null && tile(second.id) != null && !grid.isLayoutRequested }
                        for (blocks in listOf(true, false)) for (color in listOf(null, 0, 0xffffff, 0x13579b)) {
                            onMain {
                                app.displaySettings.update { options -> options.copy(primaryControlColor = color,
                                    primaryControlHighlightEnabled = true,
                                    controlBlocks = blocks, blockOutline = BlockOutline(BlockStyle.DASHED_ARC, "+", 18, true)) }
                            }
                            val highlight = onMain { color?.or(android.graphics.Color.BLACK)
                                ?: com.macarmusic.app.ui.AccentTheme.color(activity, com.google.android.material.R.attr.colorPrimaryContainer) }
                            await {
                                tile(first.id)?.backgroundTintList?.defaultColor == highlight &&
                                    tile(first.id)?.isSelected == true && tile(second.id)?.isSelected == false &&
                                    !grid.isLayoutRequested
                            }
                            onMain {
                                val current = checkNotNull(tile(first.id))
                                val other = checkNotNull(tile(second.id))
                                assertTrue(ColorUtils.calculateContrast(current.currentTextColor, highlight) >= 4.5)
                                current.iconTint?.let { tint ->
                                    assertTrue(ColorUtils.calculateContrast(
                                        tint.getColorForState(intArrayOf(android.R.attr.state_enabled), tint.defaultColor), highlight) >= 4.5)
                                }
                                assertNotEquals(highlight, other.backgroundTintList?.defaultColor)
                                assertEquals(0xff008844.toInt(), other.currentTextColor)
                                assertTrue(other.requestFocusFromTouch())
                                assertTrue(other.foreground.state.contains(android.R.attr.state_focused))
                                assertFalse("Keyboard focus is not the playing station", other.isSelected)
                                assertTrue(current.isSelected)
                                assertTrue(ViewCompat.getStateDescription(current).toString()
                                    .contains(activity.getString(R.string.radio_selected_station)))
                                assertTrue(controller.isPlaying)
                                assertEquals(first.id, controller.currentMediaItem?.mediaId)
                                assertEquals(connections, network.manifests.get())
                                assertSame(activity, androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                                    .filterIsInstance<MainActivity>().single())
                            }
                        }
                        onMain { app.displaySettings.update { it.copy(primaryControlHighlightEnabled = false) } }
                        await {
                            tile(first.id)?.backgroundTintList?.defaultColor == tile(second.id)?.backgroundTintList?.defaultColor &&
                                tile(first.id)?.isSelected == true && !grid.isLayoutRequested
                        }
                        onMain {
                            assertEquals(0xff008844.toInt(), checkNotNull(tile(first.id)).currentTextColor)
                            assertTrue(controller.isPlaying)
                            assertEquals(first.id, controller.currentMediaItem?.mediaId)
                            assertEquals(connections, network.manifests.get())
                            app.displaySettings.update { it.copy(primaryControlHighlightEnabled = true) }
                        }
                        await { tile(first.id)?.backgroundTintList?.defaultColor == 0xff13579b.toInt() }
                        screenshot("ux155-current-station-$night-$surface")
                    }
                }
                val activity = settledMain()
                val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
                val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
                fun currentRow(id: String): MaterialButton? {
                    val rows = model.radioChannels.value
                    val position = rows.indexOfFirst { it.station?.id == id }
                    if (position < 0) return null
                    return grid.findViewHolderForAdapterPosition(grid.adapter!!.itemCount - rows.size + position)
                        ?.itemView as? MaterialButton
                }
                playProgram(second)
                await { currentRow(second.id)?.isSelected == true && currentRow(first.id)?.isSelected == false }
                onMain {
                    assertEquals(0xff13579b.toInt(), currentRow(second.id)!!.backgroundTintList!!.defaultColor)
                    assertNotEquals(0xff13579b.toInt(), currentRow(first.id)!!.backgroundTintList?.defaultColor)
                    assertTrue(app.radio.pin(second))
                }
                await { currentRow(second.id)?.icon == null && second.id !in app.radio.options.value.pins }
                onMain {
                    assertTrue(currentRow(second.id)!!.isSelected)
                    assertEquals(0xff13579b.toInt(), currentRow(second.id)!!.backgroundTintList!!.defaultColor)
                    assertEquals(connections + 1, network.manifests.get())
                    assertTrue(controller.isPlaying)
                }
                command(RadioCommands.music)
                await { currentRow(second.id)?.isSelected == false }
                onMain {
                    assertNotEquals(0xff13579b.toInt(), currentRow(second.id)!!.backgroundTintList?.defaultColor)
                    assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                    assertEquals(connections + 1, network.manifests.get())
                }
            }
        } finally {
            onMain {
                app.themeMode = theme
                app.radio.setPinColor(pinColor)
                app.displaySettings.update { it.copy(primaryControlColor = original.primaryControlColor,
                    primaryControlHighlightEnabled = original.primaryControlHighlightEnabled,
                    controlBlocks = original.controlBlocks, blockOutline = original.blockOutline) }
            }
        }
    }

    @Test fun modeSwitchWithoutHistoryPausesMusicOnlyWhenOpeningRadio() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("radioStation").remove("radioActive").commit()
        onMain { app.radio.publishRestored(); controller.prepare(); controller.play() }
        await { controller.isPlaying }
        ActivityScenario.launch(MainActivity::class.java).use {
            val activity = settledMain()
            val model = onMain { ViewModelProvider(activity)[MusicViewModel::class.java] }
            onMain { activity.findViewById<View>(R.id.switchPlaybackMode).performClick() }
            await { model.selectedScreen.value == R.id.nav_radio && !controller.playWhenReady }
            onMain { assertFalse(controller.isPlaying); assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId) }
            assertEquals(0, network.connectionAttempts.get())
            onMain { model.screen(R.id.nav_player); app.radio.setPanelVisible(false); controller.play() }
            await { !activity.findViewById<View>(R.id.nav_radio).isShown && controller.isPlaying }
            onMain {
                assertTrue(activity.findViewById<View>(R.id.switchPlaybackMode).isEnabled)
                activity.findViewById<View>(R.id.switchPlaybackMode).performClick()
                assertTrue(controller.isPlaying)
                assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                assertEquals(R.id.nav_player, model.selectedScreen.value)
            }
            await { app.notice.value?.text == app.text(R.string.resume_radio_choose_hidden) }
            assertEquals(0, network.connectionAttempts.get())
            onMain { app.radio.setPanelVisible(true) }
            await { activity.findViewById<View>(R.id.switchPlaybackMode).isEnabled }
            onMain { controller.pause(); controller.clearMediaItems() }
            await { controller.mediaItemCount == 0 }
            playStation(0)
            val manifests = network.manifests.get()
            onMain { activity.findViewById<View>(R.id.switchPlaybackMode).performClick() }
            await { model.selectedScreen.value == R.id.nav_folders }
            onMain { assertTrue(controller.isPlaying) }
            assertEquals(manifests, network.manifests.get())
            assertTrue(app.radio.options.value.active)
        }
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
        SystemClock.sleep(250)
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
        val groups = programs.map(RadioGroups::of).distinct()
        return programs.count { it.id in pins } + groups.size + groups.map { it.section }.distinct().size
    }

    private fun radioRowsSettled(activity: MainActivity, grid: RecyclerView): Boolean {
        val model = ViewModelProvider(activity)[MusicViewModel::class.java]
        val rows = model.radioChannels.value
        val groups = (grid.adapter as ConcatAdapter).adapters
            .filterIsInstance<com.macarmusic.app.ui.RadioChannelAdapter>().single()
        val pins = model.radioPage.value.count { it.id in app.radio.options.value.pins }
        return groups.currentList == rows && grid.adapter!!.itemCount == pins + rows.size &&
            !grid.hasPendingAdapterUpdates() && !grid.isLayoutRequested
    }

    private fun radioGroupButton(activity: MainActivity, groupId: String): View {
        val grid = onMain { activity.findViewById<RecyclerView>(R.id.radioList) }
        await {
            val rows = ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value
            rows.any { it.group.id == groupId && it.station == null && it.section == null } &&
                radioRowsSettled(activity, grid)
        }
        val position = onMain {
            val rows = ViewModelProvider(activity)[MusicViewModel::class.java].radioChannels.value
            val index = rows.indexOfFirst { it.group.id == groupId && it.station == null && it.section == null }
            check(index >= 0) { "Missing radio group: $groupId" }
            grid.adapter!!.itemCount - rows.size + index
        }
        onMain { (grid.layoutManager as GridLayoutManager).scrollToPositionWithOffset(position, 0) }
        await(diagnostic = {
            val model = ViewModelProvider(activity)[MusicViewModel::class.java]
            val rows = model.radioChannels.value
            val index = rows.indexOfFirst { it.group.id == groupId && it.station == null && it.section == null }
            val currentPosition = if (index < 0) -1 else grid.adapter!!.itemCount - rows.size + index
            val layout = grid.layoutManager as GridLayoutManager
            "group=$groupId, style=${app.displaySettings.options.value.blockOutline.style}, " +
                "position=$position, currentPosition=$currentPosition, programs=${model.radioPage.value.size}, " +
                "rows=${rows.size}, items=${grid.adapter!!.itemCount}, children=${grid.childCount}, " +
                "holder=${grid.findViewHolderForAdapterPosition(position) != null}, " +
                "layoutRequested=${grid.isLayoutRequested}, pendingUpdates=${grid.hasPendingAdapterUpdates()}, " +
                "currentGrid=${grid === activity.findViewById<RecyclerView>(R.id.radioList)}, " +
                "shown=${grid.isShown}, size=${grid.width}x${grid.height}, " +
                "visible=${layout.findFirstVisibleItemPosition()}..${layout.findLastVisibleItemPosition()}, " +
                "focus=${activity.currentFocus?.id}:${activity.currentFocus?.getTag(R.id.playerFocusKey)}"
        }) { grid.findViewHolderForAdapterPosition(position) != null && !grid.isLayoutRequested }
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

    private fun await(diagnostic: (() -> String)? = null, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(25)
        }
        fail("Radio state did not settle: " + onMain {
            diagnostic?.let { return@onMain it() }
            if (::controller.isInitialized) "${controller.currentMediaItem?.mediaId}, state=${controller.playbackState}, " +
                "error=${controller.playerError?.errorCodeName}, active=${app.radio.options.value.active}, " +
                "play=${controller.playWhenReady}, notice=${app.notice.value}, " +
                "manifests=${network.manifests.get()}, problem=${app.playbackProblem.value}, " +
                "source=${controller.currentMediaItem?.localConfiguration?.uri}, " +
                "requests=${synchronized(network.requests) { network.requests.takeLast(6) }}" else "connecting"
        })
    }

    private fun <T> onMain(action: () -> T): T {
        val future = FutureTask(Callable(action))
        instrumentation.runOnMainSync(future)
        return future.get()
    }
}
