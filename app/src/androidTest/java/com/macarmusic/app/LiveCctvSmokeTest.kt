package com.macarmusic.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.radio.RadioDefaults
import com.macarmusic.app.radio.RadioTuning
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class LiveCctvSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()

    @Test fun explicitLiveCheckDecodesCctvAudioWithoutVideo() {
        assumeTrue("Live network checks are opt-in and need a fresh process",
            InstrumentationRegistry.getArguments().getString("liveCctv") == "true")
        val saved = app.radio.options.value
        onMain {
            app.radio.setPanelVisible(true)
            app.radio.tune(RadioTuning(downloadLogos = false))
            app.playbackSettings.setMuted(true)
        }
        runBlocking { app.radioCatalog.awaitReady() }
        val future = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }
        val controller = future.get(20, TimeUnit.SECONDS)
        try {
            val result = onMain { controller.sendCustomCommand(RadioCommands.play, Bundle().apply {
                putString(RadioCommands.STATION, RadioDefaults.CCTV_ID)
            }) }.get(20, TimeUnit.SECONDS)
            assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
            val deadline = SystemClock.uptimeMillis() + 60_000
            while (!onMain { controller.isPlaying }) {
                onMain { assertNull("Live broadcaster rejected playback: ${controller.playerError}", controller.playerError) }
                assertTrue("The live audio source did not become ready", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(100)
            }
            val start = onMain { controller.currentPosition }
            SystemClock.sleep(2500)
            onMain {
                assertTrue(controller.isPlaying)
                assertTrue(controller.currentPosition >= start + 1500)
                assertEquals(RadioDefaults.CCTV_ID, controller.currentMediaItem?.mediaId)
                assertFalse(controller.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO })
                val audio = controller.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                assertTrue("An actual decoded audio track must be selected", audio.isNotEmpty())
                println("CCTV_LIVE_AUDIO source=${app.radio.currentStation?.streamUri}, " +
                    "formats=${audio.flatMap { group -> (0 until group.length).map {
                        group.getTrackFormat(it).sampleMimeType
                    } }}, position=${controller.currentPosition}, state=${controller.playbackState}")
            }
        } finally {
            onMain {
                controller.pause()
                controller.stop()
                MediaController.releaseFuture(future)
                app.radio.tune(saved.tuning)
                app.radio.setPanelVisible(saved.showPanel)
            }
            app.stopService(Intent(app, PlaybackService::class.java))
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(Callable(block))
        instrumentation.runOnMainSync(task)
        return task.get(20, TimeUnit.SECONDS)
    }
}
