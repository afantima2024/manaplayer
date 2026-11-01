package com.macarmusic.app

import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioCommands
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class CnrLiveSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()

    @Test fun publicCnrStreamsDecodeWithoutRecordingAndReturnToPausedMusic() {
        assumeTrue("Run separately from synthetic fixtures, only with explicit network opt-in",
            InstrumentationRegistry.getArguments().getString("cnrLive") == "true")
        val controller = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }.get(20, TimeUnit.SECONDS)
        try {
            await { app.playbackReady.value }
            onMain { app.playbackSettings.setMuted(true); app.radio.filter(false) }
            for (station in CnrRadio.stations.take(3)) {
                val result = onMain {
                    controller.sendCustomCommand(RadioCommands.play, Bundle().apply {
                        putString(RadioCommands.STATION, station.id)
                    })
                }.get(20, TimeUnit.SECONDS)
                assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
                await {
                    controller.playerError?.let { throw AssertionError("${station.id}: ${it.errorCodeName}", it) }
                    controller.currentMediaItem?.mediaId == station.id && controller.isPlaying
                }
                val position = onMain { controller.currentPosition }
                SystemClock.sleep(1200)
                onMain {
                    assertTrue(controller.isPlaying)
                    assertTrue(controller.currentPosition > position)
                    controller.pause()
                }
                await { controller.playbackState == Player.STATE_IDLE && !controller.playWhenReady }
            }
        } finally {
            val result = onMain { controller.sendCustomCommand(RadioCommands.music, Bundle.EMPTY) }
            result.get(20, TimeUnit.SECONDS)
            onMain { controller.pause(); controller.release() }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 40_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(100)
        }
        fail("Live CNR playback did not reach the expected state")
    }

    private fun <T> onMain(action: () -> T): T {
        val future = FutureTask(Callable(action))
        instrumentation.runOnMainSync(future)
        return future.get()
    }
}
