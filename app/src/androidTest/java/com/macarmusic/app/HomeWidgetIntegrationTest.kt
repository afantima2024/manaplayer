package com.macarmusic.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Bundle
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.ThemeAccent
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.WidgetPlayback
import com.macarmusic.app.model.WidgetPresentation
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.widgets.HomeWidgetKind
import com.macarmusic.app.widgets.WidgetViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class HomeWidgetIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val root = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.documents", "music")
    private lateinit var controller: MediaController
    private lateinit var tracks: List<Track>

    @Before fun prepare() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        instrumentation.context.startActivity(Intent().setClassName("com.macarmusic.app.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {
            app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
        }
        app.contentResolver.call(root, "setUnavailable", "false", null)
        app.contentResolver.call(root, "setLyricsMode", "available", null)
        app.contentResolver.call(root, "setLyricReadDelay", "0", null)
        runBlocking { app.repository.awaitReady() }
        controller = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }.get(15, TimeUnit.SECONDS)
        await { app.playbackReady.value }
        onMain {
            controller.pause()
            controller.clearMediaItems()
            app.playbackSettings.setLoop(LoopMode.OFF)
            app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
            app.playbackSettings.setMuted(false)
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, widgetTransparency = 50,
                lyricAppearance = LyricAppearance(), themeAccent = ThemeAccent.DEFAULT, customAccentColor = null) }
            app.themeMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        }
        tracks = runBlocking(Dispatchers.IO) {
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks.sortedBy { it.uri.length }
        }
        assertEquals(2, tracks.size)
        onMain { controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 0) }
        await { onMain { controller.mediaItemCount == 2 } }
    }

    @After fun cleanup() {
        command("finish", "")
        if (::controller.isInitialized) onMain {
            controller.pause()
            controller.release()
        }
        shell("input keyevent 224")
        shell("input keyevent 82")
    }

    @Test fun fourRealWidgetsShareTransportMuteProgressAndLocalLyrics() {
        startHost()
        var status = status()
        assertNotEquals(Process.myUid(), status.getInt("uid"))
        assertEquals(4, status.getInt("count"))
        assertEquals(4, (0..3).map { status.getInt("id$it") }.toSet().size)
        for (kind in 0..3) assertFalse(status.getBoolean("providerError$kind"))
        assertTrue(status.getBoolean("artwork1"))
        assertFalse(status.getBoolean("previousPresent3"))
        assertFalse(status.containsKey("progress3"))
        assertFalse(onMain { controller.playWhenReady })
        click(3, "play")
        await { onMain { controller.isPlaying } }
        await { (0..3).all { status().getString("play$it") == app.text(R.string.pause) } }
        val start = status().getInt("progress0")
        await { status().getInt("progress0") > start + 10 }
        val audio = app.getSystemService(AudioManager::class.java)
        val systemVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        click(0, "mute")
        await { onMain { controller.volume == 0f } }
        assertTrue(app.playbackSettings.muted.value)
        assertEquals(systemVolume, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        await { (0..2).all { status().getString("mute$it") == app.text(R.string.unmute_player) } }
        click(1, "mute")
        await { onMain { controller.volume == 1f } }
        click(2, "next")
        await { onMain { controller.currentMediaItemIndex == 1 } }
        click(0, "play")
        await { onMain { !controller.playWhenReady } }
        onMain { controller.seekTo(0) }
        click(1, "previous")
        await { onMain { controller.currentMediaItemIndex == 0 } }
        assertFalse(onMain { controller.playWhenReady })
        onMain { controller.seekTo(12_000) }
        await { status().getString("lyric2") == "Synthetic middle" }
        await { status().getInt("progress0") in 195..205 }
        SystemClock.sleep(1_500)
        status = status()
        SystemClock.sleep(1_700)
        assertEquals(status.getInt("progress0"), status().getInt("progress0"))
        assertEquals(status.getInt("updates"), status().getInt("updates"))
        click(2, "next")
        await { status().getString("lyric2")?.contains("Nested text fixture") == true }
        assertFalse(onMain { controller.playWhenReady })
    }

    @Test fun updatesStopWithScreenOffOrOnlyPlayWidgetAndResumeAfterWake() {
        startHost()
        click(0, "play")
        await { onMain { controller.isPlaying } }
        await { status().getInt("progress0") > 10 }
        shell("input keyevent 223")
        await { !app.getSystemService(android.os.PowerManager::class.java).isInteractive }
        awaitNoPeriodicUpdates()
        val off = status().getInt("updates")
        val position = onMain { controller.currentPosition }
        SystemClock.sleep(2_200)
        assertEquals(off, status().getInt("updates"))
        await(timeoutMs = 5_000) {
            onMain { controller.isPlaying && controller.currentPosition > position + 1_000 }
        }
        assertEquals(off, status().getInt("updates"))
        shell("input keyevent 224")
        shell("input keyevent 82")
        await { status().getInt("updates") > off }
        for (kind in 0..2) command("delete", "$kind")
        await { status().getInt("count") == 1 }
        awaitNoPeriodicUpdates()
        SystemClock.sleep(2_200)
        // onDeleted/onDisabled can still deliver one-shot refreshes; only periodic work must stop.
        assertFalse("A play-only widget must not run the progress ticker", widgetTickerActive())
        assertEquals(app.text(R.string.pause), status().getString("play3"))
        click(3, "play")
        await { onMain { !controller.playWhenReady } }
        command("delete", "3")
        await { status().getInt("count") == 0 }
        await { HomeWidgetKind.entries.all { AppWidgetManager.getInstance(app).getAppWidgetIds(it.component(app)).isEmpty() } }
    }

    @Test fun savedLyricTimingUpdatesBothWidgetRowsWithoutStartingPlayback() {
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        startHost()
        onMain { controller.prepare() }
        await { onMain { controller.playbackState == Player.STATE_READY } }
        onMain { controller.seekTo(12_000) }
        val original = runBlocking(Dispatchers.IO) { app.lyrics.load(tracks.first(), online = false) }
        try {
            await { status().getString("lyric2") == "Synthetic middle" }
            runBlocking { app.lyrics.saveTiming(tracks.first(), original.document!!, -5000) }
            await { status().getString("lyric2") == "Synthetic start" }
            assertEquals("Synthetic middle", status().getString("lyricNext2"))
            assertFalse(onMain { controller.playWhenReady })
            assertEquals(12_000L, onMain { controller.currentPosition })
            runBlocking { app.lyrics.saveTiming(tracks.first(), original.document!!, 0) }
            await { status().getString("lyric2") == "Synthetic middle" }
            assertFalse(onMain { controller.playWhenReady })
        } finally { runBlocking { app.lyrics.cache.clear() } }
    }

    @Test fun offlineDownloadedSelectionUpdatesWidgetAndBestReturnsOnTheNextVisit() {
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        startHost()
        onMain { controller.prepare() }
        await { onMain { controller.playbackState == Player.STATE_READY } }
        onMain { controller.seekTo(12_000) }
        try {
            val original = runBlocking(Dispatchers.IO) { app.lyrics.load(tracks.first(), online = false) }
            runBlocking {
                app.lyrics.saveTiming(tracks.first(), CachedLyric(
                    "[00:00]Synthetic downloaded choice\n[00:20]Downloaded next marker", LyricOrigin.QQ), 0)
                val inventory = app.lyrics.available(tracks.first(), original)
                val local = inventory.choices.single { it.origin == LyricOrigin.LOCAL_FILE }
                val current = app.lyrics.best(tracks.first(), local, original, inventory.generation)
                val updated = app.lyrics.available(tracks.first(), current)
                app.lyrics.select(tracks.first(), updated.choices.single { it.origin == LyricOrigin.QQ }, updated.generation)
            }
            await { status().getString("lyric2") == "Synthetic downloaded choice" }
            assertEquals("Downloaded next marker", status().getString("lyricNext2"))
            assertFalse(app.displaySettings.options.value.onlineLyrics)
            assertFalse(onMain { controller.playWhenReady })
            click(2, "next")
            await { status().getString("lyric2")?.contains("Nested text fixture") == true }
            onMain { controller.seekTo(0) }
            click(2, "previous")
            await { status().getString("lyric2") == "Synthetic start" }
            assertFalse(onMain { controller.playWhenReady })
        } finally { runBlocking { app.lyrics.cache.clear() } }
    }

    @Test fun removingSavedVersionsSwitchesThenClearsBothWidgetRowsForTheCurrentVisit() {
        runBlocking { app.lyrics.cache.useInternal(); app.lyrics.cache.clear() }
        startHost()
        onMain { controller.prepare() }
        await { onMain { controller.playbackState == Player.STATE_READY } }
        onMain { controller.seekTo(12_000) }
        app.contentResolver.call(root, "setLyricsMode", "missing", null)
        try {
            val current = runBlocking {
                app.lyrics.saveTiming(tracks.first(), CachedLyric(
                    "[00:00]Synthetic saved first\n[00:20]First next marker", LyricOrigin.QQ, offlineSelected = true), 0)
                app.lyrics.saveTiming(tracks.first(), CachedLyric(
                    "[00:00]Synthetic saved second\n[00:20]Second next marker", LyricOrigin.QQ, offlineSelected = true), 0)
            }
            await { status().getString("lyric2") == "Synthetic saved second" }
            val replacement = runBlocking {
                val inventory = app.lyrics.available(tracks.first(), current)
                app.lyrics.remove(tracks.first(), inventory.choices.single { it.key == inventory.currentKey },
                    current, inventory.generation)
            }
            await { status().getString("lyric2") == "Synthetic saved first" &&
                status().getString("lyricNext2") == "First next marker" }
            runBlocking {
                app.lyrics.remove(tracks.first(), replacement.choices.choices.single(), replacement.result,
                    replacement.choices.generation)
            }
            await { status().getString("lyric2") == app.text(R.string.widget_no_lyrics) &&
                status().getString("lyricNext2") == "" && !status().getBoolean("lyricNextVisible2") }
            app.contentResolver.call(root, "setLyricsMode", "available", null)
            onMain { app.displaySettings.update { it.copy(onlineLyrics = true) } }
            awaitNoPeriodicUpdates()
            assertEquals(app.text(R.string.widget_no_lyrics), status().getString("lyric2"))
            assertEquals("", status().getString("lyricNext2"))
            assertEquals(0L, runBlocking { app.lyrics.cache.inspect() }.files)
            assertEquals(12_000L, onMain { controller.currentPosition })
            assertFalse(onMain { controller.playWhenReady })
            screenshot("widgets-removed-lyrics.png")
        } finally {
            app.contentResolver.call(root, "setLyricsMode", "available", null)
            runBlocking { app.lyrics.cache.clear() }
        }
    }

    @Test fun compactFootprintsKeepEveryControlProgressAndArtworkVisible() {
        startHost()
        val density = app.resources.displayMetrics.density
        val manager = AppWidgetManager.getInstance(app)
        for ((kind, provider) in HomeWidgetKind.entries.withIndex()) {
            val info = manager.getInstalledProviders().single { it.provider == provider.component(app) }
            if (Build.VERSION.SDK_INT >= 31) {
                assertEquals(if (kind == 3) 1 else 3, info.targetCellWidth)
                assertEquals(if (kind == 2) 2 else 1, info.targetCellHeight)
            }
        }
        for (kind in 0..3) command("resize", "$kind:" + when (kind) {
            2 -> "180x110"
            3 -> "40x40"
            else -> "180x40"
        })
        await {
            val data = status()
            data.getInt("height0") == (40 * density).roundToInt() &&
                data.getInt("width0") == (180 * density).roundToInt() &&
                (0..3).all { data.getBoolean("playVisible$it") } &&
                (0..2).all { data.getBoolean("progressVisible$it") }
        }
        val data = status()
        for (kind in 0..3) {
            assertTrue(data.getInt("playHeight$kind") >= (32 * density).toInt())
            assertTrue(data.getInt("playWidth$kind") >= (40 * density).toInt())
            assertFalse(data.getBoolean("providerError$kind"))
            if (kind != 3) for (button in listOf("previous", "next", "mute")) {
                assertTrue(data.getBoolean("${button}Visible$kind"))
            }
        }
        assertTrue(data.getBoolean("lyricVisible2"))
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff348bc0.toInt()) }
        val count = data.getInt("updates")
        onMain {
            val views = WidgetViews.full(app, HomeWidgetKind.ARTWORK, data.getInt("id1"),
                WidgetPlayback(track = tracks.first()), bitmap, "")
            AppWidgetManager.getInstance(app).updateAppWidget(data.getInt("id1"), views)
        }
        await { status().getInt("updates") > count }
        assertTrue(status().getBoolean("artwork1"))
        assertFalse(status().getBoolean("providerError1"))
    }

    @Test fun transparencyUpdatesAllBackgroundsWithoutFadingControlsOrPolling() {
        startHost()
        for (value in listOf(0, 50, 100)) {
            onMain { app.displaySettings.update { it.copy(widgetTransparency = value) } }
            val alpha = WidgetPresentation.backgroundAlpha(value)
            await { (0..3).all { status().getInt("backgroundImageAlpha$it") == alpha } }
            for (kind in 0..3) {
                assertEquals(1f, status().getFloat("backgroundViewAlpha$kind"), 0f)
                assertEquals(1f, status().getFloat("playAlpha$kind"), 0f)
                assertTrue(status().getBoolean("playVisible$kind"))
            }
            assertEquals(value, DisplaySettings(app) {}.options.value.widgetTransparency)
        }
        assertFalse(onMain { controller.playWhenReady })
        SystemClock.sleep(1_500)
        val unchanged = status().getInt("updates")
        onMain { app.displaySettings.update { it.copy(widgetTransparency = 100) } }
        SystemClock.sleep(1_500)
        assertEquals(unchanged, status().getInt("updates"))
        screenshot("widgets-compact-transparent.png")
        onMain { app.displaySettings.update { it.copy(widgetTransparency = 50) } }
    }

    @Test fun lyricsControlsStayAtTheTopAndBothRowsFitTheCompactWidget() {
        startHost()
        val density = app.resources.displayMetrics.density
        for (height in listOf(110, 180, 240)) {
            command("resize", "2:180x$height")
            await {
                val data = status()
                data.getInt("height2") == (height * density).roundToInt() &&
                    data.getBoolean("lyricVisible2") && data.getBoolean("lyricNextVisible2")
            }
            val data = status()
            assertTrue("Controls must not be vertically centered", data.getInt("playTop2") <= (16 * density).roundToInt())
            assertTrue(data.getInt("playHeight2") >= (48 * density).roundToInt())
            assertEquals("Synthetic start", data.getString("lyric2"))
            assertEquals("Synthetic middle", data.getString("lyricNext2"))
            assertEquals(1, data.getInt("lyricNextMaxLines2"))
            for (row in listOf("lyric", "lyricNext")) {
                assertTrue("The $row text must fit at ${height}dp",
                    data.getInt("${row}Height2") >= data.getInt("${row}LayoutHeight2"))
            }
            if (height != 180) screenshot("widgets-two-lines-$height.png")
        }
        onMain { controller.prepare() }
        await { onMain { controller.playbackState == Player.STATE_READY } }
        onMain { controller.seekTo(12_000) }
        await { status().getString("lyric2") == "Synthetic middle" &&
            status().getString("lyricNext2") == "Synthetic ending" }
        onMain { controller.seekTo(31_000) }
        await { status().getString("lyric2") == "Synthetic ending" &&
            status().getString("lyricNext2") == "" && !status().getBoolean("lyricNextVisible2") }
        onMain { controller.seekTo(0) }
        await { status().getString("lyricNext2") == "Synthetic middle" }
    }

    @Test fun lyricWidgetUsesLiveLyricFontsAndColorsWithoutResizingButtonsOrPolling() {
        startHost()
        command("resize", "2:320x180")
        val density = app.resources.displayMetrics.density
        await { status().getInt("height2") == (180 * density).roundToInt() }
        val original = status()
        for (appearance in listOf(
            LyricAppearance(activeSizeSp = 32, otherSizeSp = 26, activeColor = 0xffd166, otherColor = 0x61cfff,
                karaoke = true, karaokeColor = 0xff00aa),
            LyricAppearance(activeSizeSp = 48, otherSizeSp = 48, activeColor = 0x95f5a3, otherColor = 0xffa4cd),
            LyricAppearance(),
        )) {
            onMain { app.displaySettings.update { it.copy(lyricAppearance = appearance) } }
            await { status().getFloat("lyricSizeSp2") == appearance.activeSizeSp.toFloat() &&
                status().getFloat("lyricNextSizeSp2") == appearance.otherSizeSp.toFloat() }
            val data = status()
            appearance.activeColor?.let { assertEquals(it or android.graphics.Color.BLACK, data.getInt("lyricColor2")) }
            appearance.otherColor?.let { assertEquals(it or android.graphics.Color.BLACK, data.getInt("lyricNextColor2")) }
            for (row in listOf("lyric", "lyricNext")) {
                assertTrue(data.getBoolean("${row}Visible2"))
                assertTrue("$row needs ${data.getInt("${row}LayoutHeight2")}px but has ${data.getInt("${row}Height2")}px",
                    data.getInt("${row}Height2") >= data.getInt("${row}LayoutHeight2"))
            }
            for (field in listOf("width", "height", "playTop", "playWidth", "playHeight")) {
                assertEquals("Styling must not resize $field", original.getInt("${field}2"), data.getInt("${field}2"))
            }
            if (appearance.activeSizeSp == 32) screenshot("widgets-custom-lyric-appearance.png")
        }
        onMain { app.displaySettings.update { it.copy(customAccentColor = 0x61cfff) } }
        await { status().getInt("lyricColor2") == 0xff61cfff.toInt() }
        val darkOther = status().getInt("lyricNextColor2")
        onMain { app.themeMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO }
        await { status().getInt("lyricNextColor2") != darkOther }
        assertEquals(191, android.graphics.Color.alpha(status().getInt("lyricNextColor2")))
        assertEquals(24f, status().getFloat("lyricSizeSp2"), 0.01f)
        assertEquals(20f, status().getFloat("lyricNextSizeSp2"), 0.01f)
        awaitNoPeriodicUpdates()
        val count = status().getInt("updates")
        SystemClock.sleep(1_500)
        assertEquals(count, status().getInt("updates"))
        assertFalse(onMain { controller.playWhenReady })
    }

    private fun screenshot(name: String) {
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(app.getExternalFilesDir(null), "test-screenshots")
            assertTrue(directory.isDirectory || directory.mkdirs())
            File(directory, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    private fun startHost() {
        instrumentation.context.startActivity(Intent().setClassName("com.macarmusic.app.test",
            FixtureWidgetHostActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await {
            val data = status()
            data.getInt("count") == 4 && (0..3).all { data.getBoolean("playPresent$it") } &&
                data.getString("lyric2") == "Synthetic start" && data.getString("title0") == tracks.first().title
        }
    }

    private fun click(kind: Int, button: String) = command("click", "$kind:widget_$button")
    private fun command(command: String, value: String) {
        app.contentResolver.call(root, "widgetCommand", command, Bundle().apply { putString("value", value) })
    }
    private fun status(): Bundle {
        val data = checkNotNull(app.contentResolver.call(root, "widgetStatus", null, null))
        assertNull(data.getString("error"), data.getString("error"))
        return data
    }
    private fun awaitNoPeriodicUpdates() {
        // Widget broadcasts and queued RemoteViews are delivered across process boundaries.
        var count = status().getInt("updates")
        var changedAt = SystemClock.elapsedRealtime()
        await {
            val current = status().getInt("updates")
            if (current != count) {
                count = current
                changedAt = SystemClock.elapsedRealtime()
            }
            !widgetTickerActive() && SystemClock.elapsedRealtime() - changedAt >= 1_400
        }
    }
    private fun widgetTickerActive(): Boolean = onMain {
        val ticker = app.homeWidgets.javaClass.getDeclaredField("ticker").apply { isAccessible = true }
        (ticker.get(app.homeWidgets) as? kotlinx.coroutines.Job)?.isActive == true
    }
    private fun <T> onMain(block: () -> T): T =
        FutureTask(Callable(block)).also(instrumentation::runOnMainSync).get()
    private fun await(timeoutMs: Long = 20_000, test: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (test()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < end)
        val data = app.contentResolver.call(root, "widgetStatus", null, null)
        data?.keySet()
        val playback = if (::controller.isInitialized) onMain {
            "state=${controller.playbackState}, playing=${controller.isPlaying}, ready=${controller.playWhenReady}, " +
                "position=${controller.currentPosition}, connected=${controller.isConnected}, " +
                "interactive=${app.getSystemService(android.os.PowerManager::class.java).isInteractive}, " +
                listOf("state", "ticker", "player", "updateFailed").joinToString { name ->
                    val field = app.homeWidgets.javaClass.getDeclaredField(name).apply { isAccessible = true }
                    val value = field.get(app.homeWidgets)
                    "$name=$value" + if (value is Player) {
                        " sourceState=${value.playbackState} sourcePosition=${value.currentPosition} sourcePlaying=${value.isPlaying}"
                    } else ""
                }
        } else "Not connected"
        fail("Timed out. Playback: $playback. Hosted widgets: $data")
    }
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }
}
