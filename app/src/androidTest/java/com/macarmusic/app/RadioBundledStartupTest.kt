package com.macarmusic.app

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.radio.RadioBundledDefaults
import com.macarmusic.app.radio.RadioDefaults
import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioSettings
import com.macarmusic.app.radio.RadioStationLogo
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.SettingsCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RadioBundledStartupTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()

    @Test fun defaultsAreLazyLocalAndPersistentWithRadioToggleBelowPlaylist() {
        assertFalse("Run this case in a fresh process with cleared test-app data", app.radioCatalogInitialized)
        val saved = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        assertFalse(saved.contains("radioDefaultsApplied"))
        saved.edit().putBoolean("parkedNoticeSeen", true).commit()
        val network = FixtureRadioNetwork.install()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }
            await { app.playbackReady.value && activity.hasWindowFocus() }
            await { app.radioCatalogInitialized && app.radioCatalog.state.value.ready && !app.radio.needsBundledDefaults }
            val warmed = app.radioCatalog
            assertFalse(app.radioPreparing.value)
            assertFalse(app.radio.options.value.active)
            assertNull(activity.findViewById<View>(R.id.radioQuickStations))
            assertEquals(0, network.requests.size)
            onMain {
                val model = ViewModelProvider(activity)[MusicViewModel::class.java]
                model.settingsCategory(SettingsCategory.GENERAL)
                model.openSettings()
            }
            await { activity.findViewById<View>(R.id.settingsRadio)?.isShown == true }
            onMain {
                val playlist = activity.findViewById<View>(R.id.settingsShowPlaylists)
                val radio = activity.findViewById<CompoundButton>(R.id.settingsRadio)
                assertSame(playlist.parent, radio.parent)
                assertTrue(radio.top >= playlist.bottom)
                assertTrue(radio.isChecked)
                ViewModelProvider(activity)[MusicViewModel::class.java].settingsCategory(SettingsCategory.RADIO)
            }
            await { activity.findViewById<View>(R.id.settingsRadioPanel)?.isShown == true }
            onMain { assertFalse(activity.findViewById<View>(R.id.settingsRadio).isShown) }
            assertSame("Opening Settings must reuse the offline warm-up", warmed, app.radioCatalog)
            onMain { activity.findViewById<View>(R.id.nav_radio).performClick() }
            await { app.radioCatalogInitialized && app.radioCatalog.state.value.ready && !app.radio.needsBundledDefaults }
            val catalog = app.radioCatalog.state.value
            assertEquals(RadioBundledDefaults.REVISION, catalog.bundledRevision)
            assertEquals(4, catalog.subscriptions.size)
            assertEquals(20, catalog.subscriptions.last().programs.size)
            assertTrue(catalog.subscriptions.last().programs.all { RadioGroups.of(it).id !in app.radio.options.value.hiddenGroups })
            assertTrue(catalog.subscriptions.all { it.enabled && it.checkedAtMs > 0 && it.programs.isNotEmpty() })
            assertEquals(RadioDefaults.CCTV_ID, app.radio.options.value.pins.first())
            assertEquals(RadioStationLogo.CCTV, app.radio.options.value.stationLogos[RadioDefaults.CCTV_ID])
            assertEquals(RadioGroups.defaultVisibleIds, catalog.programs.map { RadioGroups.of(it).id }.toSet() -
                app.radio.options.value.hiddenGroups)
            val first = catalog.subscriptions.first()
            runBlocking { app.radioCatalog.changeSubscription(first, enabled = false) }
            val second = app.radio.options.value.pins[1]
            onMain {
                app.radio.showGroups(setOf("country:de"))
                app.radio.setStationLogo(RadioDefaults.CCTV_ID, RadioStationLogo.JAZZ)
                val pins = app.radio.options.value.pins
                assertTrue(app.radio.reorderPins(pins, pins.drop(1) + pins.first()))
            }
            scenario.recreate()
            scenario.onActivity { activity = it }
            await { activity.hasWindowFocus() }
            val restored = RadioSettings(app)
            restored.applyBundledDefaults(RadioGroups.defaultHiddenGroups(catalog.programs))
            assertEquals(second, restored.options.value.pins.first())
            assertEquals(RadioStationLogo.JAZZ, restored.options.value.stationLogos[RadioDefaults.CCTV_ID])
            assertFalse("country:de" in restored.options.value.hiddenGroups)
            assertFalse(app.radioCatalog.state.value.subscriptions.first().enabled)
            assertEquals(0, network.requests.size)
            assertFalse(app.radio.options.value.active)
            assertFalse(app.displaySettings.options.value.autoPlayOnLaunch)
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (!onMain(condition)) {
            assertTrue("Radio defaults did not settle", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(25)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(Callable(block))
        instrumentation.runOnMainSync(task)
        return task.get(20, TimeUnit.SECONDS)
    }
}
