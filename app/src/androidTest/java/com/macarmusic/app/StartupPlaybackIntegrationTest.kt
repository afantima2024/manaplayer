package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Track
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.StartupPlayback
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.ui.MainActivity
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

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class StartupPlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()

    @Test fun startupPlaybackResumesOnlyAnOptedInFreshLaunch() {
        val case = InstrumentationRegistry.getArguments().getString("startupCase", "music-on")
        // Large literal vararg arrays trigger an Android 6 x86_64 ART optimizer fault.
        require(case in ("music-on music-off radio-on radio-off passive history background background-radio " +
            "warm warm-radio warm-off warm-radio-off missing-radio hidden-radio-on hidden-radio-off empty " +
            "settings-radio settings-radio-off settings-radio-passive settings-radio-history").split(' '))
        assertFalse("Each startup case needs a fresh process", app.playbackReady.value)
        assertFalse(app.radioCatalogInitialized)
        val settingsTask = case.startsWith("settings-radio")
        val enabled = !case.endsWith("-off")
        val radio = settingsTask || case in ("radio-on radio-off missing-radio hidden-radio-on hidden-radio-off " +
            "background-radio warm-radio warm-radio-off").split(' ')
        val showRadio = case !in setOf("hidden-radio-on", "hidden-radio-off")
        val expectedPlay = case == "settings-radio" ||
            case in "music-on radio-on hidden-radio-on background background-radio warm warm-radio".split(' ')
        val network = FixtureRadioNetwork.install()
        network.customPlaylist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
            (0 until 30).joinToString("\n") { "#EXTINF:4.0,\ntone$it.aac" } + "\n#EXT-X-ENDLIST\n"
        val root = DocumentsContract.buildTreeDocumentUri("${app.packageName}.test.documents", "music")
        instrumentation.context.startActivity(Intent().setClassName("${app.packageName}.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        app.contentResolver.call(root, "setUnavailable", null, Bundle().apply { putBoolean("value", false) })
        app.contentResolver.call(root, "scanFixtureState", null, Bundle().apply {
            putBoolean("resetReads", true); putLong("audioDelayMs", 0); putInt("extraRows", 0)
        })
        val audio = DocumentsContract.buildDocumentUriUsingTree(root, "music/one").toString()
        val tracks = List(2) { Track("startup-$it", audio, "local-$it.wav", "Startup $it", durationMs = 60_000) }
        runBlocking {
            app.repository.awaitReady()
            app.repository.update { Library(tracks = tracks) }
            app.playback.read()
        }
        val station = RadioProgram("radio:custom:" + "a".repeat(64), "Startup station",
            "https://radio.example/startup/first.m3u8",
            alternateUris = listOf("https://radio.example/startup/second.m3u8"), sourceIndex = 1)
        File(app.filesDir, "radio-stations.json").writeText(RadioList.encode(
            if (case == "missing-radio") emptyList() else listOf(station), includeDefaults = false))
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", showRadio)
            .putBoolean("radioActive", radio).putString("radioStation", station.id)
            .remove("radioPins").remove("radioHiddenGroups").remove("radioTuning").commit()
        onMain {
            app.radio.publishRestored()
            app.displaySettings.update { it.copy(autoPlayOnLaunch = enabled, lyricsEnabled = false, onlineLyrics = false) }
            app.playbackSettings.setMuted(true)
            app.playback.save(if (case == "empty") PlaybackSnapshot()
                else PlaybackSnapshot(queue = tracks, index = 1, positionMs = 17_000))
        }
        fun connect() = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }
        val background = if (case in setOf("background", "background-radio"))
            connect().also { it.get(20, TimeUnit.SECONDS) } else null
        if (background != null) await { app.playbackReady.value }
        val launch = Intent(app, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(StartupPlayback.OPEN_PLAYER_ONLY, case == "passive" || case == "settings-radio-passive")
        if (case == "history" || case == "settings-radio-history")
            launch.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val probeMode = case != "missing-radio"
        var drawnFrames = 0
        var wrongModeFrames = 0
        var activityCreations = 0
        val drawnRoots = mutableSetOf<View>()
        val drawListeners = mutableListOf<Pair<View, ViewTreeObserver.OnDrawListener>>()
        val frameProbe = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && probeMode) {
                if (stage == Stage.CREATED) activityCreations++
                if (stage == Stage.RESUMED) {
                    val view = activity.findViewById<View>(R.id.root)
                    val listener = ViewTreeObserver.OnDrawListener {
                        drawnFrames++
                        drawnRoots += activity.findViewById<View>(R.id.root)
                        val pinsShown = activity.findViewById<View>(R.id.radioQuickStations)?.isShown == true
                        val musicOptions = activity.findViewById<View>(R.id.playbackOptions).visibility == View.VISIBLE
                        val previousSource = activity.getString(if (radio) R.string.radio_source_previous else R.string.rewind)
                        if (pinsShown != radio || musicOptions == radio ||
                            activity.findViewById<View>(R.id.shuffle).isEnabled == radio ||
                            activity.findViewById<View>(R.id.repeat).isEnabled == radio ||
                            activity.findViewById<View>(R.id.rewind).contentDescription != previousSource) {
                            wrongModeFrames++
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
            }
        }
        fun stopFrameProbe() {
            monitor.removeLifecycleCallback(frameProbe)
            drawListeners.forEach { (view, listener) ->
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener)
            }
            drawListeners.clear()
        }
        onMain { monitor.addLifecycleCallback(frameProbe) }
        try {
            ActivityScenario.launch<MainActivity>(launch).use { scenario ->
                lateinit var activity: MainActivity
                scenario.onActivity { activity = it }
                await {
                    app.playbackReady.value && activity.findViewById<View>(R.id.play).isEnabled &&
                        (!probeMode || drawnFrames > 0)
                }
                val connection = connect()
                val controller = connection.get(20, TimeUnit.SECONDS)
                try {
                    if (expectedPlay) await { controller.isPlaying && !app.startupPlayback.pending }
                    else SystemClock.sleep(750)
                    onMain {
                        stopFrameProbe()
                        if (probeMode) {
                            println("STARTUP_FRAMES case=$case frames=$drawnFrames wrongModeFrames=$wrongModeFrames " +
                                "activities=$activityCreations roots=${drawnRoots.size}")
                            assertEquals("Startup must keep its $case control layout on every frame", 0, wrongModeFrames)
                            assertEquals("Startup must not recreate the Activity", 1, activityCreations)
                            assertEquals("Startup must not replace the content tree", 1, drawnRoots.size)
                        }
                        assertEquals("Autoplay policy for $case", expectedPlay, controller.playWhenReady)
                        assertEquals(showRadio, activity.findViewById<View>(R.id.nav_radio).isShown)
                        if (case == "empty") assertEquals(0, controller.mediaItemCount)
                        else if (radio && case != "missing-radio")
                            assertEquals(station.id, controller.currentMediaItem?.mediaId)
                        else {
                            assertEquals(tracks[1].id, controller.currentMediaItem?.mediaId)
                            if (expectedPlay) assertTrue(controller.currentPosition in 17_000..23_000)
                            else assertEquals(17_000L, controller.currentPosition)
                        }
                        if (radio && expectedPlay) assertEquals(station.streamUri, app.radio.currentStation?.streamUri)
                        if (!radio) {
                            assertFalse("Offline warm-up must not select Radio", app.radio.options.value.active)
                            assertFalse("Offline warm-up must not show a Radio switch", app.radioPreparing.value)
                        }
                        controller.pause()
                    }
                    if (radio && expectedPlay) assertEquals(1, network.manifests.get())
                    else assertEquals(0, network.connectionAttempts.get())
                    if (!expectedPlay) onMain {
                        assertEquals("Paused restoration must not prepare audio", Player.STATE_IDLE, controller.playbackState)
                    }
                    await { !controller.playWhenReady }
                    val settingsModel = if (settingsTask) onMain {
                        ViewModelProvider(activity)[MusicViewModel::class.java].also {
                            it.settingsCategory(SettingsCategory.GENERAL)
                            it.openSettings()
                        }
                    } else null
                    if (settingsTask) await {
                        activity.findViewById<View>(R.id.settingsAutoPlay)?.isShown == true
                    }
                    scenario.recreate()
                    scenario.onActivity { activity = it }
                    await { activity.hasWindowFocus() && activity.findViewById<View>(R.id.play).isEnabled }
                    if (settingsTask) onMain {
                        assertSame("Ordinary recreation must retain launch identity", settingsModel,
                            ViewModelProvider(activity)[MusicViewModel::class.java])
                    }
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    SystemClock.sleep(500)
                    onMain {
                        assertFalse("Recreation and passive lifecycle resumes must not autoplay again", controller.playWhenReady)
                        assertFalse(app.startupPlayback.pending)
                    }
                    if (settingsTask) {
                        val release = CountDownLatch(1)
                        val started = CountDownLatch(1)
                        val manifestsBefore = network.manifests.get()
                        network.manifestStarted = started
                        network.manifestRelease = release
                        try {
                            onMain {
                                // Model a saved task whose Activity was destroyed while its service/process survived.
                                activity.viewModelStore.clear()
                            }
                            scenario.recreate()
                            scenario.onActivity { activity = it }
                            await {
                                activity.hasWindowFocus() && activity.findViewById<View>(R.id.play).isEnabled &&
                                    activity.findViewById<View>(R.id.settingsAutoPlay)?.isShown == true
                            }
                            onMain {
                                val restored = ViewModelProvider(activity)[MusicViewModel::class.java]
                                assertNotSame("The restored task needs a new launch identity", settingsModel, restored)
                                assertEquals(R.id.action_settings, restored.selectedScreen.value)
                                assertSame(app, activity.application)
                                assertEquals(enabled, app.displaySettings.options.value.autoPlayOnLaunch)
                            }
                            if (case == "settings-radio") {
                                assertTrue("Restored Settings task did not request its saved station",
                                    started.await(5, TimeUnit.SECONDS))
                                onMain {
                                    assertTrue(controller.playWhenReady)
                                    assertEquals(Player.STATE_BUFFERING, controller.playbackState)
                                    assertFalse(app.startupPlayback.pending)
                                }
                                release.countDown()
                                await { controller.isPlaying }
                                assertEquals(manifestsBefore + 1, network.manifests.get())
                            } else {
                                SystemClock.sleep(750)
                                onMain {
                                    assertFalse("Saved Settings must not bypass opt-out or passive intent rules",
                                        controller.playWhenReady)
                                    assertFalse(app.startupPlayback.pending)
                                }
                                assertEquals(manifestsBefore, network.manifests.get())
                            }
                        } finally {
                            release.countDown()
                            network.manifestRelease = null
                        }
                    }
                    if (case.startsWith("warm")) {
                        fun returnToApp(passive: Boolean = false) {
                            onMain {
                                app.startActivity(Intent(app, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    .putExtra(StartupPlayback.OPEN_PLAYER_ONLY, passive))
                            }
                            await { activity.hasWindowFocus() && activity.findViewById<View>(R.id.play).isEnabled }
                        }
                        returnToApp()
                        SystemClock.sleep(500)
                        onMain { assertFalse("A duplicate foreground launch must not autoplay", controller.playWhenReady) }
                        scenario.moveToState(Lifecycle.State.CREATED)
                        returnToApp(passive = true)
                        SystemClock.sleep(500)
                        onMain { assertFalse("A notification/widget return must not autoplay", controller.playWhenReady) }
                        scenario.moveToState(Lifecycle.State.CREATED)
                        returnToApp()
                        if (enabled) await { controller.isPlaying && !app.startupPlayback.pending }
                        else SystemClock.sleep(500)
                        onMain {
                            assertEquals("An explicit launcher return must respect autoplay", enabled, controller.playWhenReady)
                            assertFalse(app.startupPlayback.pending)
                        }
                    }
                    assertEquals(0, network.subscriptions.get())
                    println("STARTUP_PLAYBACK case=$case played=$expectedPlay manifests=${network.manifests.get()} " +
                        "startupFrames=$drawnFrames wrongModeFrames=$wrongModeFrames")
                } finally {
                    onMain { controller.pause(); MediaController.releaseFuture(connection) }
                }
            }
        } finally {
            onMain {
                stopFrameProbe()
                background?.let { MediaController.releaseFuture(it) }
                app.displaySettings.update { it.copy(autoPlayOnLaunch = false) }
            }
            app.stopService(Intent(app, PlaybackService::class.java))
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!onMain(condition)) {
            assertTrue("Startup state did not settle", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(25)
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val future = FutureTask(Callable(action))
        instrumentation.runOnMainSync(future)
        return future.get()
    }
}
