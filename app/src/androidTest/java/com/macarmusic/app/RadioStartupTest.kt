package com.macarmusic.app

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.data.AtomicTextStore
import com.macarmusic.app.data.TextStore
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Track
import com.macarmusic.app.radio.RadioCatalogRepository
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioSubscription
import com.macarmusic.app.ui.MainActivity
import androidx.lifecycle.ViewModelProvider
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.SettingsCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RadioStartupTest {
    @Test fun musicToRadioShowsPanelBeforeCatalogReadCompletes() = verifyMusicToRadioSwitch(missing = false)

    @Test fun missingRadioSwitchRestoresMusicUiWithItsQueuePaused() = verifyMusicToRadioSwitch(missing = true)

    @Test fun warmedRadioHandsOffWithoutWaitingForTheStreamOrReloading() =
        verifyMusicToRadioSwitch(missing = false, warmBeforeSwitch = true)

    private fun verifyMusicToRadioSwitch(missing: Boolean, warmBeforeSwitch: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        assertFalse("Run each switch case in a fresh process", app.radioCatalogInitialized)
        assertFalse(app.playbackReady.value)
        fun <T> onMain(action: () -> T): T {
            val result = FutureTask(Callable(action))
            instrumentation.runOnMainSync(result)
            return result.get()
        }
        fun await(message: String, timeoutMs: Long = 20_000, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + timeoutMs
            while (!onMain(condition)) {
                assertTrue(message, SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(10)
            }
        }
        val network = FixtureRadioNetwork.install()
        val streamRelease = CountDownLatch(1)
        network.manifestStarted = CountDownLatch(1)
        network.manifestRelease = streamRelease
        network.customPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            (0 until 30).joinToString("") { "#EXTINF:4.0,\ntone$it.aac\n" } + "#EXT-X-ENDLIST\n"
        val tree = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
        instrumentation.context.startActivity(Intent().setClassName("${app.packageName}.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Fixture grant was not delivered") {
            app.checkUriPermission(tree, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        app.contentResolver.call(tree, "setUnavailable", null, Bundle().apply { putBoolean("value", false) })
        app.contentResolver.call(tree, "scanFixtureState", null, Bundle().apply {
            putBoolean("resetReads", true); putLong("audioDelayMs", 0); putInt("extraRows", 0)
        })
        val audio = DocumentsContract.buildDocumentUriUsingTree(tree, "music/one").toString()
        val tracks = List(2) { Track("switch-$it", audio, "switch-$it.wav", "Switch music $it", durationMs = 60_000) }
        runBlocking {
            app.repository.awaitReady()
            app.repository.update { Library(tracks = tracks) }
            app.playback.read()
        }
        val programs = List(4999) { RadioProgram(
            "radio:custom:" + it.toString(16).padStart(64, '0'), "Switch station $it",
            "https://radio.example/switch/$it/index.m3u8", "US",
            alternateUris = if (it == 0) listOf("https://radio.example/switch/selected/index.m3u8") else emptyList(),
        ) }
        val station = programs.first()
        val original = RadioList.encode(emptyList(), includeDefaults = false,
            subscriptions = listOf(RadioSubscription("00000000-0000-0000-0000-000000001541",
                "Switch cache", "https://radio.example/switch.m3u", programs = programs)),
            sourceSelections = mapOf(station.id to station.sources[1]),
            bundledRevision = com.macarmusic.app.radio.RadioBundledDefaults.REVISION)
        val catalogFile = File(app.filesDir, "radio-stations.json").apply { writeText(original) }
        val catalogStarted = CountDownLatch(1)
        val catalogRelease = CountDownLatch(1)
        val catalogReads = AtomicInteger()
        val catalogConstructedOnMain = AtomicBoolean()
        val musicReadyBeforeCatalog = AtomicBoolean()
        val store = AtomicTextStore(catalogFile)
        val gatedCatalog = lazy {
            catalogConstructedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            val inspectMusic = {
                val current = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
                val player = current?.let {
                    MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }.get(it)
                } as? MediaController
                val frame = current?.let {
                    MainActivity::class.java.getDeclaredField("firstFrame").apply { isAccessible = true }.get(it)
                } as? kotlinx.coroutines.CompletableDeferred<*>
                app.playbackReady.value && frame?.isCompleted == true && player?.isPlaying == true &&
                    player.currentMediaItem?.mediaId == tracks[1].id
            }
            musicReadyBeforeCatalog.set(if (catalogConstructedOnMain.get()) inspectMusic() else onMain(inspectMusic))
            RadioCatalogRepository(object : TextStore {
                override fun read(): String? {
                    catalogReads.incrementAndGet()
                    catalogStarted.countDown()
                    check(catalogRelease.await(45, TimeUnit.SECONDS)) { "Test did not release the catalog" }
                    return store.read()
                }
                override fun write(text: String) = store.write(text)
            }, app.applicationScope, loadDefaults = { com.macarmusic.app.radio.RadioDefaults.bundled }) {
                app.report(it.localizedMessage.orEmpty())
            }
        }
        MaCarMusicApp::class.java.getDeclaredField("savedRadioCatalog").apply {
            isAccessible = true
            set(app, gatedCatalog)
        }
        val panelVisible = InstrumentationRegistry.getArguments().getString("radioEnabled", "true").toBooleanStrict()
        assertTrue(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", panelVisible)
            .putBoolean("radioActive", false)
            .putString("radioStation", if (missing) "radio:custom:" + "f".repeat(64) else station.id)
            .putString("radioPins", programs.take(12).joinToString("\n") { it.id })
            .putString("radioNavigationMode", "GLOBAL")
            .putStringSet("radioHiddenGroups", emptySet()).putString("radioRefreshInterval", "MANUAL").commit())
        onMain {
            app.radio.publishRestored()
            app.radio.tune(app.radio.options.value.tuning.copy(downloadLogos = false))
            app.displaySettings.update { it.copy(autoPlayOnLaunch = true, lyricsEnabled = true, onlineLyrics = false) }
            app.playbackSettings.setMuted(true)
            app.playback.save(PlaybackSnapshot(queue = tracks, index = 1, positionMs = 17_000))
        }
        try {
            ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await("Music startup did not settle") { app.playbackReady.value && activity.findViewById<View>(R.id.play).isEnabled }
                val connection = onMain {
                    MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                }
                val controller = connection.get(20, TimeUnit.SECONDS)
                try {
                    await("Opted-in music did not start") { controller.isPlaying }
                    onMain {
                        assertEquals(app.getString(R.string.now_playing_window_title,
                            app.getString(R.string.app_name), tracks[1].title), activity.title.toString())
                    }
                    assertTrue("Music startup did not schedule the offline warm-up", catalogStarted.await(5, TimeUnit.SECONDS))
                    assertFalse("Radio construction must run off the main thread", catalogConstructedOnMain.get())
                    assertTrue("The first frame and playing Music must precede Radio work", musicReadyBeforeCatalog.get())
                    onMain {
                        assertFalse(app.radioCatalog.state.value.ready)
                        assertFalse(app.radioPreparing.value)
                        assertFalse(app.radio.options.value.active)
                        assertNull(activity.findViewById<View>(R.id.radioQuickStations))
                        assertEquals(panelVisible, activity.findViewById<View>(R.id.nav_radio).isShown)
                    }
                    assertEquals(0, network.connectionAttempts.get())
                    val positionBefore = onMain { controller.currentPosition }
                    SystemClock.sleep(150)
                    onMain {
                        assertTrue("Held background IO must not interrupt Music", controller.isPlaying)
                        assertTrue(controller.currentPosition > positionBefore)
                    }
                    if (warmBeforeSwitch) {
                        scenario.recreate()
                        scenario.onActivity { activity = it }
                        await("Music recreation did not settle during warm-up") {
                            activity.hasWindowFocus() && activity.findViewById<View>(R.id.play).isEnabled && controller.isPlaying
                        }
                        assertEquals(1, catalogReads.get())
                        catalogRelease.countDown()
                        await("Offline warm-up did not finish") { app.radioCatalog.state.value.ready }
                        await("Pinned controls were not prepared before switching") {
                            activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.radioQuickStations)?.let {
                                !it.isShown && it.adapter?.itemCount == 12 && it.findViewHolderForAdapterPosition(0) != null
                            } == true
                        }
                        assertEquals(0, network.connectionAttempts.get())
                        assertEquals(original, catalogFile.readText())
                    }
                    val started = SystemClock.elapsedRealtime()
                    onMain { activity.findViewById<View>(R.id.switchPlaybackMode).performClick() }
                    assertTrue(catalogStarted.await(5, TimeUnit.SECONDS))
                    await("Radio panel is still waiting for the catalog", timeoutMs = 1_000) {
                        activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true
                    }
                    val panelMs = SystemClock.elapsedRealtime() - started
                    if (!warmBeforeSwitch) {
                        onMain {
                            assertFalse(app.radioCatalog.state.value.ready)
                            assertFalse("An unvalidated request must not persist Radio mode", app.radio.options.value.active)
                            assertFalse("Music must be silent before the pending Radio UI", controller.isPlaying)
                            assertFalse(controller.playWhenReady)
                            assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                            assertFalse(activity.findViewById<View>(R.id.artwork).isShown)
                            assertFalse(activity.findViewById<View>(R.id.lyricsList).isShown)
                            assertFalse(activity.findViewById<View>(R.id.switchPlaybackMode).isEnabled)
                            assertFalse(activity.findViewById<View>(R.id.play).isEnabled)
                            assertEquals(app.getString(R.string.now_playing_window_title,
                                app.getString(R.string.app_name), app.getString(R.string.radio_switching)), activity.title.toString())
                        }
                        onMain { controller.play() }
                        await("A media-controller Play restarted Music behind the Radio UI") { !controller.playWhenReady }
                        scenario.recreate()
                        scenario.onActivity { activity = it }
                        await("Recreation lost the pending Radio panel") {
                            activity.hasWindowFocus() && activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true
                        }
                        onMain { assertFalse(activity.findViewById<View>(R.id.switchPlaybackMode).isEnabled) }
                        catalogRelease.countDown()
                    } else {
                        await("A warmed switch still held the Music queue", timeoutMs = 1_000) {
                            controller.currentMediaItem?.mediaId == station.id
                        }
                        assertTrue("Warmed audio handoff exceeded one second", SystemClock.elapsedRealtime() - started < 1_000)
                    }
                    if (missing) {
                        await("A missing station did not restore the music UI") {
                            activity.findViewById<View>(R.id.switchPlaybackMode).isEnabled &&
                                activity.findViewById<View>(R.id.radioQuickStations)?.isShown == false &&
                                app.notice.value?.isError == true
                        }
                        onMain {
                            assertFalse(controller.isPlaying)
                            assertFalse(controller.playWhenReady)
                            assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                            assertEquals(2, controller.mediaItemCount)
                            assertFalse(app.radio.options.value.active)
                        }
                        assertEquals(0, network.connectionAttempts.get())
                    } else {
                        assertTrue("The selected stream was not opened", network.manifestStarted.await(20, TimeUnit.SECONDS))
                        await("Cached pins did not render while streaming is still buffering") {
                            val grid = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.radioQuickStations)
                            grid?.isShown == true && grid.adapter?.itemCount == 12 && controller.currentMediaItem?.mediaId == station.id
                        }
                        onMain {
                            assertFalse(controller.isPlaying)
                            assertEquals(app.getString(R.string.now_playing_window_title,
                                app.getString(R.string.app_name), station.title(app)), activity.title.toString())
                        }
                        streamRelease.countDown()
                        await("Radio did not start with its navigation restored") {
                            controller.isPlaying && controller.mediaItemCount == programs.size
                        }
                        val music = onMain { checkNotNull(app.radio.savedMusic.value) }
                        assertEquals(tracks.map { it.id }, music.queue.map { it.id })
                        assertEquals(1, music.index)
                        assertTrue(music.positionMs >= 17_000)
                        assertEquals(station.sources[1], onMain { app.radio.currentStation?.streamUri })
                        assertEquals(1, network.manifests.get())
                        onMain { activity.findViewById<View>(R.id.switchPlaybackMode).performClick() }
                        await("Returning to music lost playback") {
                            controller.isPlaying && controller.currentMediaItem?.mediaId == tracks[1].id
                        }
                        onMain {
                            controller.pause()
                            assertEquals(2, controller.mediaItemCount)
                            assertTrue(controller.currentPosition in music.positionMs..music.positionMs + 2_000)
                            assertEquals(music.shuffle, controller.shuffleModeEnabled)
                            assertEquals(music.repeat, controller.repeatMode)
                        }
                        assertEquals(1, catalogReads.get())
                    }
                    assertEquals(0, network.subscriptions.get())
                    assertEquals(0, network.catalogues.get())
                    assertEquals(original, catalogFile.readText())
                    assertEquals(1, catalogReads.get())
                    println("RADIO_SWITCH panelMs=$panelMs missing=$missing warmed=$warmBeforeSwitch " +
                        "showPanel=$panelVisible catalogReads=${catalogReads.get()} musicReadyBeforeCatalog=${musicReadyBeforeCatalog.get()}")
                } finally { onMain { controller.pause(); MediaController.releaseFuture(connection) } }
            }
        } finally {
            catalogRelease.countDown()
            streamRelease.countDown()
            network.manifestRelease = null
            onMain { app.displaySettings.update { it.copy(autoPlayOnLaunch = false) } }
            app.stopService(Intent(app, PlaybackService::class.java))
        }
    }

    @Test fun savedRadioIsVisibleOnTheFirstFrameBeforePlaybackRestoration() =
        verifySavedRadioStartup(gated = false)

    @Test fun savedRadioLayoutStaysStableWhileCatalogIsSlow() =
        verifySavedRadioStartup(gated = true)

    private fun verifySavedRadioStartup(gated: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        assertFalse("Run this case in a fresh process", app.radioCatalogInitialized)
        assertFalse(app.playbackReady.value)
        val network = FixtureRadioNetwork.install()
        network.customPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            (0 until 30).joinToString("") { "#EXTINF:4.0,\ntone$it.aac\n" } + "#EXT-X-ENDLIST\n"
        val programs = List(5000) { RadioProgram(
            "radio:custom:" + it.toString(16).padStart(64, '0'), "Startup station $it",
            "https://radio.example/$it/index.m3u8", "US",
        ) }
        val original = RadioList.encode(emptyList(), includeDefaults = false,
            subscriptions = listOf(RadioSubscription("00000000-0000-0000-0000-000000000154",
                "Cached stations", "https://radio.example/cached.m3u", programs = programs)),
            bundledRevision = com.macarmusic.app.radio.RadioBundledDefaults.REVISION)
        val catalogFile = File(app.filesDir, "radio-stations.json")
        catalogFile.writeText(original)
        val catalogRelease = CountDownLatch(if (gated) 1 else 0)
        val catalogReads = AtomicInteger()
        if (gated) {
            val store = AtomicTextStore(catalogFile)
            val catalog = lazy {
                RadioCatalogRepository(object : TextStore {
                    override fun read(): String? {
                        catalogReads.incrementAndGet()
                        check(catalogRelease.await(45, TimeUnit.SECONDS)) { "Test did not release the catalog" }
                        return store.read()
                    }
                    override fun write(text: String) = store.write(text)
                }, app.applicationScope, loadDefaults = { com.macarmusic.app.radio.RadioDefaults.bundled }) {
                    app.report(it.localizedMessage.orEmpty())
                }
            }
            MaCarMusicApp::class.java.getDeclaredField("savedRadioCatalog").apply {
                isAccessible = true
                set(app, catalog)
            }
        }
        assertTrue(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", true)
            .putBoolean("radioActive", true).putString("radioStation", programs.first().id)
            .putString("radioPins", programs.take(12).joinToString("\n") { it.id })
            .putString("radioRefreshInterval", "MANUAL").commit())
        instrumentation.runOnMainSync {
            app.radio.publishRestored()
            app.radio.tune(app.radio.options.value.tuning.copy(downloadLogos = false))
            app.displaySettings.update { it.copy(autoPlayOnLaunch = true, onlineLyrics = false) }
            app.playbackSettings.setMuted(true)
        }
        val started = SystemClock.elapsedRealtime()
        var firstDrawMs = -1L
        var radioOnFirstDraw = false
        var readyOnFirstDraw = true
        var drawnFrames = 0
        var wrongModeFrames = 0
        var lostSelectionFrames = 0
        var sawSelectedPin = false
        var settledFrames = 0
        var activityCreations = 0
        val drawnRoots = mutableSetOf<View>()
        val drawListeners = mutableListOf<Pair<View, android.view.ViewTreeObserver.OnDrawListener>>()
        val controllerField = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
        val callbacks = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, state: Bundle?) {
                if (activity is MainActivity) activityCreations++
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                if (activity !is MainActivity) return
                val view = activity.findViewById<View>(R.id.root)
                val listener = android.view.ViewTreeObserver.OnDrawListener {
                    drawnFrames++
                    drawnRoots += activity.findViewById<View>(R.id.root)
                    val pins = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.radioQuickStations)
                    if (pins?.isShown != true ||
                        activity.findViewById<View>(R.id.playbackOptions).visibility == View.VISIBLE ||
                        activity.findViewById<View>(R.id.shuffle).isEnabled ||
                        activity.findViewById<View>(R.id.repeat).isEnabled ||
                        activity.findViewById<View>(R.id.rewind).contentDescription != activity.getString(R.string.radio_source_previous) ||
                        activity.findViewById<View>(R.id.radioStreamStatus).visibility !=
                            if (app.radio.options.value.tuning.showStatus) View.VISIBLE else View.GONE) {
                        wrongModeFrames++
                    }
                    val selected = pins?.findViewWithTag<View>(programs.first().id)?.isSelected == true
                    if (sawSelectedPin && !selected) lostSelectionFrames++
                    sawSelectedPin = sawSelectedPin || selected
                    val player = controllerField.get(activity) as? MediaController
                    if (app.playbackReady.value && player?.currentMediaItem?.mediaId == programs.first().id &&
                        player.isPlaying && selected) settledFrames++
                    if (firstDrawMs < 0) {
                        firstDrawMs = SystemClock.elapsedRealtime() - started
                        radioOnFirstDraw = pins?.isShown == true
                        readyOnFirstDraw = app.playbackReady.value
                    }
                }
                drawListeners += view to listener
                if (view.isAttachedToWindow) view.viewTreeObserver.addOnDrawListener(listener)
                else view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        v.viewTreeObserver.addOnDrawListener(listener)
                    }
                    override fun onViewDetachedFromWindow(v: View) = Unit
                })
            }
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        val intent = android.content.Intent(app, MainActivity::class.java)
            .setAction(android.content.Intent.ACTION_MAIN)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                var ready = false
                var pinsMs = -1L
                val deadline = SystemClock.uptimeMillis() + 40_000
                while (!ready) {
                    scenario.onActivity { activity ->
                        if (firstDrawMs >= 0) catalogRelease.countDown()
                        val pins = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.radioQuickStations)
                        if (pinsMs < 0 && pins?.isShown == true && pins.adapter?.itemCount == 12)
                            pinsMs = SystemClock.elapsedRealtime() - started
                        ready = firstDrawMs >= 0 && app.playbackReady.value && pinsMs >= 0 && settledFrames > 0
                    }
                    assertTrue("Radio startup did not settle: firstDrawMs=$firstDrawMs pinsMs=$pinsMs " +
                        "ready=${app.playbackReady.value} active=${app.radio.options.value.active}",
                        SystemClock.uptimeMillis() < deadline)
                    SystemClock.sleep(10)
                }
                println("${if (gated) "CAR_STARTUP_GATED" else "CAR_STARTUP"} firstFrameMs=$firstDrawMs pinsMs=$pinsMs " +
                    "radioOnFirstFrame=$radioOnFirstDraw readyOnFirstFrame=$readyOnFirstDraw " +
                    "frames=$drawnFrames wrongModeFrames=$wrongModeFrames lostSelectionFrames=$lostSelectionFrames")
                assertEquals(0, network.subscriptions.get())
                assertEquals(original, catalogFile.readText())
                assertFalse("Restoration must stay behind the first frame", readyOnFirstDraw)
                assertTrue("The saved Radio UI must replace the artwork on the first frame", radioOnFirstDraw)
                assertEquals("Radio must not briefly render Music controls", 0, wrongModeFrames)
                assertEquals("A restored station must not lose its selected fill during controller handoff", 0, lostSelectionFrames)
                assertEquals("Startup must not recreate the Activity", 1, activityCreations)
                assertEquals("Startup must not replace the content tree", 1, drawnRoots.size)
                if (gated) assertEquals("Startup must reuse one catalog read", 1, catalogReads.get())
            }
        } finally {
            catalogRelease.countDown()
            app.unregisterActivityLifecycleCallbacks(callbacks)
            instrumentation.runOnMainSync {
                drawListeners.forEach { (view, listener) ->
                    if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
                }
            }
            app.stopService(android.content.Intent(app, PlaybackService::class.java))
        }
    }

    @Test fun firstRadioPlaybackUsesCachedCatalogWithoutRefreshing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        assertFalse("Run this case in a fresh process", app.radioCatalogInitialized)
        val network = FixtureRadioNetwork.install()
        val release = java.util.concurrent.CountDownLatch(1)
        network.manifestRelease = release
        network.manifestStarted = java.util.concurrent.CountDownLatch(1)
        network.customPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            (0 until 30).joinToString("") { "#EXTINF:4.0,\ntone$it.aac\n" } + "#EXT-X-ENDLIST\n"
        val programs = List(5000) { RadioProgram(
            "radio:custom:" + it.toString(16).padStart(64, '0'), "First-use station $it",
            "https://radio.example/$it/index.m3u8", "US",
        ) }
        val file = File(app.filesDir, "radio-stations.json")
        val original = RadioList.encode(emptyList(), includeDefaults = false,
            subscriptions = listOf(RadioSubscription("00000000-0000-0000-0000-000000000151",
                "Saved catalog", "https://radio.example/first-use.m3u", programs = programs)),
            bundledRevision = com.macarmusic.app.radio.RadioBundledDefaults.REVISION)
        file.writeText(original)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", true)
            .putBoolean("radioActive", false).putString("radioStation", programs.first().id)
            .remove("radioRefreshInterval").remove("radioAutomaticRefreshAttempt")
            .putString("radioPins", programs.take(12).joinToString("\n") { it.id }).commit()
        fun <T> onMain(action: () -> T): T {
            val future = FutureTask(Callable(action))
            instrumentation.runOnMainSync(future)
            return future.get()
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 40_000
            while (!onMain(condition)) {
                assertTrue(message, SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(10)
            }
        }
        onMain {
            app.radio.publishRestored()
            app.radio.tune(app.radio.options.value.tuning.copy(downloadLogos = false))
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, autoPlayOnLaunch = false) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            await("Local startup did not settle") { app.playbackReady.value && activity.findViewById<View>(R.id.play).isEnabled }
            assertFalse("Paused Music startup must leave Radio preparation deferred", app.radioCatalogInitialized)
            assertEquals(0, network.connectionAttempts.get())
            val future = onMain {
                MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            }
            val controller = future.get(20, TimeUnit.SECONDS)
            val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
            val trace = InstrumentationRegistry.getArguments().getString("traceRadioStartup") == "true"
            val gaps = mutableListOf<Long>()
            var previous = SystemClock.uptimeMillis()
            val heartbeat = object : Runnable {
                override fun run() {
                    val now = SystemClock.uptimeMillis()
                    gaps.add(now - previous)
                    previous = now
                    activity.window.decorView.postDelayed(this, 16)
                }
            }
            try {
                onMain {
                    activity.window.decorView.post(heartbeat)
                    if (trace) android.os.Debug.startMethodTracingSampling(
                        File(directory, "radio151-first-use.trace").absolutePath, 16 * 1024 * 1024, 1000)
                }
                val started = SystemClock.elapsedRealtime()
                onMain { activity.findViewById<View>(R.id.switchPlaybackMode).performClick() }
                await("The selected radio window did not appear") {
                    controller.currentMediaItem?.mediaId == programs.first().id &&
                        activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true
                }
                val windowMs = SystemClock.elapsedRealtime() - started
                assertTrue("The stream connection did not reach the fixture",
                    network.manifestStarted.await(5, TimeUnit.SECONDS))
                onMain { assertFalse("The radio window must appear before the stream is ready", controller.isPlaying) }
                release.countDown()
                await("The saved radio did not play") { controller.isPlaying }
                val playingMs = SystemClock.elapsedRealtime() - started
                onMain {
                    activity.window.decorView.removeCallbacks(heartbeat)
                    if (trace) android.os.Debug.stopMethodTracing()
                }
                assertEquals(0, network.subscriptions.get())
                assertEquals(0, network.catalogues.get())
                assertEquals(original, file.readText())
                val result = org.json.JSONObject()
                    .put("windowMs", windowMs).put("playingMs", playingMs).put("maxMainGapMs", gaps.maxOrNull() ?: 0)
                    .put("catalogWarmed", false)
                    .put("cachedStations", programs.size).put("subscriptionRequests", network.subscriptions.get())
                    .put("traceEnabled", trace).toString()
                File(directory, "radio151-first-use.json").writeText(result)
                println("RADIO_FIRST_USE $result")
                assertTrue("First radio use blocked the UI thread", (gaps.maxOrNull() ?: 0) < 1500)
                onMain { controller.pause() }
                await("Radio did not stop") { !controller.isPlaying }
            } finally {
                release.countDown()
                network.manifestRelease = null
                onMain {
                    activity.window.decorView.removeCallbacks(heartbeat)
                    if (trace) android.os.Debug.stopMethodTracing()
                    controller.pause()
                    MediaController.releaseFuture(future)
                }
            }
        }
    }

    @Test fun localStartupDefersLargeCatalogWithRadioPanelShownOrHidden() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        val enabled = InstrumentationRegistry.getArguments().getString("radioEnabled", "true").toBooleanStrict()
        val subscriptions = InstrumentationRegistry.getArguments().getString("subscriptionCache", "false").toBooleanStrict()
        assertFalse("Run this case in a fresh process", app.radioCatalogInitialized)
        val network = FixtureRadioNetwork.install()
        val programs = List(5000) { RadioProgram(
            "radio:custom:" + it.toString(16).padStart(64, '0'), "Lazy station $it",
            "https://radio.example/$it/index.m3u8",
        ) }
        val file = File(app.filesDir, "radio-stations.json")
        val original = if (subscriptions) RadioList.encode(emptyList(), includeDefaults = false,
            subscriptions = listOf(RadioSubscription("00000000-0000-0000-0000-000000000137",
                "Cached 5,000-station subscription", "https://radio.example/startup.m3u", programs = programs)),
            bundledRevision = com.macarmusic.app.radio.RadioBundledDefaults.REVISION)
            else RadioList.encode(programs, bundledRevision = com.macarmusic.app.radio.RadioBundledDefaults.REVISION)
        file.writeText(original)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", enabled)
            .putStringSet("radioHiddenGroups", setOf("united states", "country:de"))
            .putString("radioRefreshInterval", "DAILY").remove("radioAutomaticRefreshAttempt")
            .putBoolean("radioActive", false).putString("radioStation", CnrRadio.stations.first().id).commit()
        fun <T> onMain(action: () -> T): T {
            val future = FutureTask(Callable(action))
            instrumentation.runOnMainSync(future)
            return future.get()
        }
        onMain {
            app.radio.publishRestored()
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false,
                showLibrary = false, showQueue = false, showPlaylists = false, autoPlayOnLaunch = false) }
        }
        val started = SystemClock.elapsedRealtime()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (!onMain { app.playbackReady.value && activity.findViewById<View>(R.id.play).isEnabled }) {
                assertTrue("Local startup did not settle", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(25)
            }
            println("RADIO_STARTUP enabled=$enabled subscriptionCache=$subscriptions " +
                "warmed=false settledMs=${SystemClock.elapsedRealtime() - started} bytes=${file.length()}")
            onMain {
                assertFalse(app.radioCatalogInitialized)
                assertEquals(enabled, activity.findViewById<View>(R.id.nav_radio).visibility == View.VISIBLE)
                for (id in listOf(R.id.nav_library, R.id.nav_queue, R.id.nav_playlists))
                    assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
                assertTrue(activity.findViewById<View>(R.id.nav_player).isShown)
                assertTrue(activity.findViewById<View>(R.id.nav_folders).isShown)
                assertFalse(app.radio.options.value.active)
                assertFalse(app.radioPreparing.value)
                assertNull(activity.findViewById<View>(R.id.radioQuickStations))
            }
            val connection = onMain {
                MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            }
            val controller = connection.get(15, TimeUnit.SECONDS)
            try {
                val result = onMain { controller.sendCustomCommand(RadioCommands.music, Bundle.EMPTY) }
                    .get(15, TimeUnit.SECONDS)
                assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
                assertFalse("Selecting Music again must not load Radio", app.radioCatalogInitialized)
                onMain { assertFalse(controller.playWhenReady) }
            } finally { onMain { MediaController.releaseFuture(connection) } }
            onMain {
                ViewModelProvider(activity)[MusicViewModel::class.java].apply {
                    settingsCategory(SettingsCategory.GENERAL)
                    openSettings()
                }
            }
            instrumentation.waitForIdleSync()
            onMain {
                activity.findViewById<View>(R.id.settingsShowLibrary).performClick()
                app.radio.setOpenPlayerOnSelection(true)
                app.radio.setPanelVisible(!enabled)
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(activity.findViewById<View>(R.id.nav_library).isShown)
                assertEquals(!enabled, activity.findViewById<View>(R.id.nav_radio).isShown)
                assertFalse("Navigation visibility must not load Radio", app.radioCatalogInitialized)
                assertFalse(app.radio.options.value.active)
                assertFalse(app.radioPreparing.value)
            }
            assertEquals(0, network.requests.size)
            assertEquals(0, network.connectionAttempts.get())
            assertEquals(0, network.subscriptions.get())
            assertEquals(original, file.readText())
        }
    }
}
