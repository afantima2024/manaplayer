package com.macarmusic.app

import android.content.Context
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioCommands
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.ui.MainActivity
import androidx.lifecycle.ViewModelProvider
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.SettingsCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class RadioStartupTest {
    @Test fun localStartupDefersLargeCatalogWithRadioEnabledOrDisabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        val enabled = InstrumentationRegistry.getArguments().getString("radioEnabled", "true").toBooleanStrict()
        assertFalse("Run this case in a fresh process", app.radioCatalogInitialized)
        val network = FixtureRadioNetwork.install()
        val programs = List(5000) { RadioProgram(
            "radio:custom:" + it.toString(16).padStart(64, '0'), "Lazy station $it",
            "https://radio.example/$it/index.m3u8",
        ) }
        val file = File(app.filesDir, "radio-stations.json")
        val original = RadioList.encode(programs)
        file.writeText(original)
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("parkedNoticeSeen", true).putBoolean("radioEnabled", enabled)
            .putBoolean("radioActive", !enabled).putString("radioStation", CnrRadio.stations.first().id).commit()
        fun <T> onMain(action: () -> T): T {
            val future = FutureTask(Callable(action))
            instrumentation.runOnMainSync(future)
            return future.get()
        }
        onMain {
            app.radio.publishRestored()
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false,
                showLibrary = false, showQueue = false, showPlaylists = false) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            val deadline = SystemClock.uptimeMillis() + 20_000
            while (!onMain { app.playbackReady.value && activity.findViewById<View>(R.id.play).isEnabled }) {
                assertTrue("Local startup did not settle", SystemClock.uptimeMillis() < deadline)
                SystemClock.sleep(25)
            }
            SystemClock.sleep(500)
            onMain {
                assertFalse("Local startup must not initialize a 5,000-entry radio catalog", app.radioCatalogInitialized)
                assertEquals(enabled, activity.findViewById<View>(R.id.nav_radio).visibility == View.VISIBLE)
                for (id in listOf(R.id.nav_library, R.id.nav_queue, R.id.nav_playlists))
                    assertEquals(View.GONE, activity.findViewById<View>(id).visibility)
                assertTrue(activity.findViewById<View>(R.id.nav_player).isShown)
                assertTrue(activity.findViewById<View>(R.id.nav_folders).isShown)
                assertFalse(app.radio.options.value.active)
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
                assertFalse("Selecting music again must not initialize the radio catalog", app.radioCatalogInitialized)
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
            }
            instrumentation.waitForIdleSync()
            onMain {
                assertTrue(activity.findViewById<View>(R.id.nav_library).isShown)
                assertFalse("Navigation settings must keep radio lazy", app.radioCatalogInitialized)
            }
            assertEquals(0, network.requests.size)
            assertEquals(original, file.readText())
        }
    }
}
