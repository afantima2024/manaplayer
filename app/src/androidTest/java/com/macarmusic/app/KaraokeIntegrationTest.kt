package com.macarmusic.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.lyrics.LyricPlayback
import com.macarmusic.app.lyrics.LyricsParser
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.Track
import com.macarmusic.app.model.WidgetPlayback
import com.macarmusic.app.model.WidgetPresentation
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.playback.toMediaItem
import com.macarmusic.app.ui.KaraokeTextView
import com.macarmusic.app.ui.LyricsAdapter
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicViewModel
import com.macarmusic.app.ui.SettingsCategory
import com.macarmusic.app.widgets.HomeWidgetKind
import com.macarmusic.app.widgets.WidgetViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class KaraokeIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val scheduled = KaraokeTextView::class.java.getDeclaredField("frameScheduled").apply { isAccessible = true }

    @Before fun prepare() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        onMain {
            app.notice.value?.let { app.dismissNotice(it.id) }
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.displaySettings.update { it.copy(onlineLyrics = false, lyricsEnabled = true,
                customTheme = false, lyricAppearance = LyricAppearance()) }
        }
    }

    @Test fun coverageUsesGlyphBoundsAndKeepsGeometryBaseColorAndDisabledRendering() = onMain {
        for (size in listOf(12f, 24f, 48f)) {
            val view = lyricView("MMMMMMMM", 1_000, size)
            val height = view.height
            val plain = bitmap(view)
            var previous = -1
            for (position in listOf(0L, 2_500L, 5_000L, 7_500L, 10_000L)) {
                view.configure(true, 0x00ff00, 0, 10_000, LyricPlayback(position))
                val image = bitmap(view)
                val green = count(image, Color.GREEN)
                val red = count(image, Color.RED)
                assertTrue("Coverage must increase at $position", green >= previous)
                if (position == 0L) assertTrue(plain.sameAs(image))
                if (position in 2_500..7_500) {
                    assertTrue("Partial text needs both colors", green > 0 && red > 0)
                    assertEquals(position / 10_000f, green.toFloat() / (green + red), 0.12f)
                }
                if (position == 10_000L) { assertTrue(green > 0); assertEquals(0, red) }
                assertEquals(Color.RED, view.currentTextColor)
                assertEquals(height, view.height)
                previous = green
                image.recycle()
            }
            view.stopKaraoke()
            bitmap(view).also { assertTrue(plain.sameAs(it)); it.recycle() }
            plain.recycle()
        }
    }

    @Test fun wrappedLinesFillInReadingOrderAndTranslationsShareTheTimestamp() = onMain {
        val view = lyricView("MMMM MMMM MMMM MMMM MMMM\nNNNN NNNN", 240, 32f)
        val layout = view.layout
        assertTrue(layout.lineCount >= 4)
        view.configure(true, 0x00ff00, 0, 10_000, LyricPlayback(5_000))
        val image = bitmap(view)
        val boundary = (0 until layout.lineCount).first { line ->
            view.text[layout.getLineEnd(line) - 1] == '\n'
        }
        fun colored(line: Int, color: Int) = count(image, color,
            view.totalPaddingTop + layout.getLineTop(line), view.totalPaddingTop + layout.getLineBottom(line))
        assertTrue(colored(0, Color.GREEN) > 0)
        assertEquals(0, colored(0, Color.RED))
        assertEquals(0, colored(boundary, Color.GREEN))
        assertTrue(colored(boundary, Color.RED) > 0)
        assertTrue(colored(boundary + 1, Color.GREEN) > 0)
        image.recycle()
    }

    @Test fun replacingTextRecomputesCoverageEvenWhenAndroidReusesItsLayout() = onMain {
        val view = lyricView("MMMM", 1_000, 36f)
        view.layoutParams = FrameLayout.LayoutParams(1_000, 160)
        view.measure(View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1_000, 160)
        view.configure(true, 0x00ff00, 0, 10_000, LyricPlayback(5_000))
        bitmap(view).recycle()
        val previous = view.layout
        view.text = "MMMMMMMMMMMM"
        assertSame("Exercise the in-place text-layout replacement", previous, view.layout)
        val image = bitmap(view)
        val green = count(image, Color.GREEN)
        val red = count(image, Color.RED)
        assertTrue(green > 0 && red > 0)
        assertEquals(0.5f, green.toFloat() / (green + red), 0.08f)
        image.recycle()
    }

    @Test fun rightToLeftTextStartsItsOverlayOnTheRight() = onMain {
        val view = lyricView("\u05de\u05de\u05de\u05de\u05de\u05de\u05de\u05de", 1_000, 36f)
        assertTrue(view.layout.getParagraphDirection(0) < 0)
        view.configure(true, 0x00ff00, 0, 10_000, LyricPlayback(5_000))
        val image = bitmap(view)
        val green = ArrayList<Int>()
        val red = ArrayList<Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            when (image.getPixel(x, y)) { Color.GREEN -> green.add(x); Color.RED -> red.add(x) }
        }
        assertTrue(green.isNotEmpty() && red.isNotEmpty())
        assertTrue(green.average() > red.average())
        image.recycle()
    }

    @Test fun localAnimationIsBoundedAndStopsWhenPausedHiddenDetachedOrDisabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: KaraokeTextView
            lateinit var parent: FrameLayout
            var draws = 0
            val observer = ViewTreeObserver.OnDrawListener { draws++ }
            scenario.onActivity {
                parent = it.findViewById(android.R.id.content)
                view = KaraokeTextView(it).apply {
                    text = "MMMMMMMM"
                    textSize = 36f
                    setTextColor(Color.RED)
                    setBackgroundColor(Color.BLACK)
                    gravity = Gravity.CENTER
                }
                parent.addView(view, FrameLayout.LayoutParams(800, 160))
            }
            await { onMain { view.isShown && view.width > 0 } }
            SystemClock.sleep(1_200)
            val clock = LyricPlayback(2_000, 20_000, true, 1f, SystemClock.uptimeMillis())
            onMain {
                view.viewTreeObserver.addOnDrawListener(observer)
                view.configure(true, 0x00ff00, 0, 20_000, clock)
            }
            await { onMain { scheduled.getBoolean(view) } }
            val before = onMain {
                val image = bitmap(view)
                try { count(image, Color.GREEN) } finally { image.recycle() }
            }
            onMain { draws = 0 }
            SystemClock.sleep(650)
            onMain {
                assertTrue("Unbounded redraws: $draws", draws in 1..25)
                val image = bitmap(view)
                assertTrue(count(image, Color.GREEN) > before)
                image.recycle()
                view.configure(true, 0x00ff00, 0, 20_000,
                    clock.copy(positionMs = clock.positionAt(SystemClock.uptimeMillis()), advancing = false))
                assertFalse(scheduled.getBoolean(view))
            }
            val frozen = onMain { bitmap(view) }
            SystemClock.sleep(250)
            onMain { bitmap(view).also { assertTrue(frozen.sameAs(it)); it.recycle() }; frozen.recycle() }
            onMain { view.configure(true, 0x00ff00, 0, 20_000, clock) }
            await { onMain { scheduled.getBoolean(view) } }
            onMain { view.visibility = View.GONE; assertFalse(scheduled.getBoolean(view)) }
            onMain { view.visibility = View.VISIBLE }
            await { onMain { scheduled.getBoolean(view) } }
            onMain { view.stopKaraoke(); assertFalse(scheduled.getBoolean(view)) }
            onMain { view.configure(true, 0x00ff00, 0, 20_000, clock) }
            await { onMain { scheduled.getBoolean(view) } }
            onMain {
                view.viewTreeObserver.removeOnDrawListener(observer)
                parent.removeView(view)
                assertFalse(scheduled.getBoolean(view))
            }
        }
    }

    @Test fun adapterDoesNotRebindRowsForProgressAndRecycledRowsLoseTheOverlay() = onMain {
        val adapter = LyricsAdapter()
        adapter.configure(LyricAppearance(activeColor = 0xff0000, otherColor = 0xff0000, karaoke = true, karaokeColor = 0x00ff00))
        adapter.submit(LyricsParser.parse("[00:00]MMMMMMMM\n[00:10]NNNNNNNN\n[00:20]Last"))
        adapter.highlight(2_000, 30_000)
        val holder = adapter.onCreateViewHolder(FrameLayout(ContextThemeWrapper(app, R.style.Theme_ManaPlayer)), 0)
        adapter.onBindViewHolder(holder, 0)
        measure(holder.text, 1_000)
        var changes = 0
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() { changes += 1_000 }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { changes += itemCount }
        })
        repeat(50) { assertFalse(adapter.highlight(2_000 + it * 100L, 30_000)) }
        assertEquals(0, changes)
        bitmap(holder.text).also { assertTrue(count(it, Color.GREEN) > 0); it.recycle() }
        assertTrue(adapter.highlight(12_000, 30_000))
        assertEquals(2, changes)
        adapter.onBindViewHolder(holder, 0)
        measure(holder.text, 1_000)
        bitmap(holder.text).also { assertEquals(0, count(it, Color.GREEN)); it.recycle() }
        adapter.onViewRecycled(holder)
        assertFalse(scheduled.getBoolean(holder.text))
    }

    @Test fun widgetSpansSurviveFullAndPartialRemoteViewsWithoutColoringFutureLines() = onMain {
        val appearance = LyricAppearance(karaoke = true, karaokeColor = 0x00ff00)
        val lyrics = LyricsParser.parse("[00:02]AB\uD83C\uDFB5D\n[00:06]Next\n[00:10]\n[00:14]Last")!!
        fun text(position: Long) = WidgetViews.karaokeLyric(WidgetPresentation.lyric(lyrics, position)!!,
            lyrics, position, 20_000, appearance)
        assertFalse(text(0) is Spanned)
        assertFalse(text(11_000) is Spanned)
        val value = text(5_000) as Spanned
        val span = value.getSpans(0, value.length, ForegroundColorSpan::class.java).single()
        assertEquals(4, value.getSpanEnd(span))
        assertEquals(Color.GREEN, span.foregroundColor)
        val state = WidgetPlayback(positionMs = 5_000, durationMs = 20_000)
        val remote = WidgetViews.full(app, HomeWidgetKind.LYRICS, 5, state, null, value,
            DisplayOptions(lyricAppearance = appearance))
        val host = remote.apply(app, FrameLayout(app))
        fun assertRows(activeSpans: Int) {
            val active = host.findViewById<TextView>(R.id.widget_lyric).text as Spanned
            assertEquals(activeSpans, active.getSpans(0, active.length, ForegroundColorSpan::class.java).size)
            val next = host.findViewById<TextView>(R.id.widget_lyric_next).text
            assertTrue(next !is Spanned || next.getSpans(0, next.length, ForegroundColorSpan::class.java).isEmpty())
        }
        assertRows(1)
        WidgetViews.dynamic(app, HomeWidgetKind.LYRICS, state.copy(positionMs = 2_000), text(2_000)).reapply(app, host)
        val cleared = host.findViewById<TextView>(R.id.widget_lyric).text
        assertTrue(cleared !is Spanned || cleared.getSpans(0, cleared.length, ForegroundColorSpan::class.java).isEmpty())
        val translations = LyricsParser.parse("[00:02]ABCD\n[00:02]EFGH\n[00:06]Next")!!
        val translated = WidgetViews.karaokeLyric(WidgetPresentation.lyric(translations, 4_000)!!,
            translations, 4_000, 20_000, appearance) as Spanned
        assertEquals(2, translated.getSpans(0, translated.length, ForegroundColorSpan::class.java).size)
        assertFalse(WidgetViews.karaokeLyric("Plain", LyricsParser.parse("Plain"), 5_000, 20_000, appearance) is Spanned)
        assertFalse(WidgetViews.karaokeLyric("Last", lyrics, 15_000, 0, appearance) is Spanned)
        assertEquals("ABCD\nEFGH", WidgetViews.karaokeLyric("ABCD\nEFGH", translations, 4_000, 20_000, LyricAppearance()))
    }

    @Test fun settingsApplyColorAndPersistWhilePlaybackPauseSeekAndLifecycleControlAnimation() {
        val root = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.documents", "music")
        instrumentation.context.startActivity(Intent().setClassName("com.macarmusic.app.test",
            FixtureGrantActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await { app.checkUriPermission(root, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED }
        app.contentResolver.call(root, "setUnavailable", "false", null)
        app.contentResolver.call(root, "setLyricsMode", "scrollable", null)
        app.contentResolver.call(root, "setLyricReadDelay", "0", null)
        val tracks = runBlocking(Dispatchers.IO) {
            app.repository.awaitReady()
            app.repository.update { Library() }
            val source = app.repository.addSource(root, 0)
            app.repository.scan(source, null) {}.tracks
        }
        val controller = onMain {
            MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
        }.get(20, TimeUnit.SECONDS)
        try {
            onMain {
                app.playbackSettings.setLoop(LoopMode.OFF)
                app.playbackSettings.setOrder(PlayOrder.SEQUENTIAL)
                controller.setMediaItems(tracks.map(Track::toMediaItem), 0, 20_500)
                controller.prepare()
                controller.pause()
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                await { onMain { controller.playbackState == Player.STATE_READY && activeRow() != null } }
                onView(withId(R.id.action_settings)).perform(click())
                onMain { ViewModelProvider(resumed())[MusicViewModel::class.java].settingsCategory(SettingsCategory.LYRICS) }
                await { onMain { resumed().findViewById<View>(R.id.settingsLyricKaraoke).isShown } }
                onView(withId(R.id.settingsLyricKaraoke)).perform(scrollTo(), click())
                assertTrue(DisplaySettings(app) {}.options.value.lyricAppearance.karaoke)
                onView(withId(R.id.lyricKaraokeColor)).perform(scrollTo(), click())
                onView(withTagValue(equalTo("rainbow:2"))).inRoot(isDialog()).perform(click())
                onView(withText(R.string.apply)).inRoot(isDialog()).perform(click())
                await { app.displaySettings.options.value.lyricAppearance.karaokeColor == ColorSlots.RAINBOW[2] }
                val saved = DisplaySettings(app) {}.options.value.lyricAppearance
                scenario.recreate()
                onMain { ViewModelProvider(resumed())[MusicViewModel::class.java].closeSettings() }
                await { onMain { activeRow()?.isShown == true } }
                onMain {
                    assertEquals(saved, app.displaySettings.options.value.lyricAppearance)
                    assertFalse(scheduled.getBoolean(activeRow()))
                    assertFalse(controller.playWhenReady)
                }
                onMain { controller.play() }
                await { onMain { controller.isPlaying && activeRow()?.let(scheduled::getBoolean) == true } }
                onMain { controller.pause() }
                await { onMain { !controller.isPlaying && activeRow()?.let(scheduled::getBoolean) == false } }
                onMain { controller.seekTo(3_000) }
                await { onMain { lyricAdapter().activeLine == 1 && activeRow() != null } }
                onMain {
                    val image = bitmap(activeRow()!!)
                    assertTrue(count(image, saved.karaokeColor or Color.BLACK) > 0)
                    image.recycle()
                }
                screenshot("update129-karaoke-playing.png")
                onMain { controller.play() }
                await { onMain { activeRow()?.let(scheduled::getBoolean) == true } }
                val old = onMain { activeRow()!! }
                onView(withId(R.id.action_settings)).perform(click())
                await { onMain { !old.isShown && !scheduled.getBoolean(old) } }
                assertTrue(onMain { controller.isPlaying })
                onMain { ViewModelProvider(resumed())[MusicViewModel::class.java].closeSettings() }
                await { onMain { activeRow()?.let(scheduled::getBoolean) == true } }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertFalse(onMain { scheduled.getBoolean(old) })
                scenario.moveToState(Lifecycle.State.RESUMED)
                await { onMain { activeRow()?.let(scheduled::getBoolean) == true } }
                onMain { controller.pause() }
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.lyricResetAppearance)).perform(scrollTo(), click())
                assertEquals(LyricAppearance(), DisplaySettings(app) {}.options.value.lyricAppearance)
            }
        } finally { onMain { controller.pause(); controller.clearMediaItems(); controller.release() } }
    }

    @Test fun muteTargetAlignsWithVolumeContainerInBothOrientationsAndControlStyles() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                scenario.onActivity { it.requestedOrientation = orientation }
                await { onMain {
                    resumedOrNull()?.let { it.resources.configuration.orientation == expected &&
                        it.window.decorView.width > 0 && !it.findViewById<View>(R.id.volumeBlock).isLayoutRequested } == true
                } }
                for ((blocks, style) in listOf(true to BlockStyle.SOLID, true to BlockStyle.CHARACTERS, false to BlockStyle.SOLID)) {
                    onMain { app.displaySettings.update { it.copy(controlBlocks = blocks, blockOutline = BlockOutline(style)) } }
                    instrumentation.waitForIdleSync()
                    onMain {
                        val activity = resumed()
                        val block = activity.findViewById<ViewGroup>(R.id.volumeBlock)
                        val mute = activity.findViewById<View>(R.id.volumeMute)
                        assertEquals(0, mute.left)
                        assertEquals(0, block.paddingLeft)
                        assertEquals((48 * activity.resources.displayMetrics.density).roundToInt(), mute.width)
                        assertEquals(mute.width, mute.height)
                        assertEquals(mute.right, activity.findViewById<View>(R.id.volume).left)
                        assertTrue(block.width > mute.width + activity.findViewById<View>(R.id.volumeValue).width)
                    }
                }
                screenshot("update129-volume-$expected.png")
            }
        }
    }

    private fun lyricView(value: String, width: Int, size: Float): KaraokeTextView =
        KaraokeTextView(ContextThemeWrapper(app, R.style.Theme_ManaPlayer)).apply {
            text = value
            textSize = size
            typeface = Typeface.MONOSPACE
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(12, 10, 12, 10)
            minHeight = 96
            setTextColor(Color.RED)
            setBackgroundColor(Color.BLACK)
            measure(this, width)
        }

    private fun measure(view: View, width: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
    }

    private fun bitmap(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        .also { view.draw(Canvas(it)) }

    private fun count(image: Bitmap, color: Int, top: Int = 0, bottom: Int = image.height): Int {
        var found = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(image.height)) for (x in 0 until image.width) {
            if (image.getPixel(x, y) == color) found++
        }
        return found
    }

    private fun activeRow(): KaraokeTextView? {
        val list = resumedOrNull()?.findViewById<RecyclerView>(R.id.lyricsList) ?: return null
        val adapter = list.adapter as? LyricsAdapter ?: return null
        return list.findViewHolderForAdapterPosition(adapter.activeLine)?.itemView as? KaraokeTextView
    }

    private fun lyricAdapter() = resumed().findViewById<RecyclerView>(R.id.lyricsList).adapter as LyricsAdapter
    private fun resumedOrNull() = ActivityLifecycleMonitorRegistry.getInstance()
        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().singleOrNull()
    private fun resumed() = checkNotNull(resumedOrNull())
    private fun <T> onMain(block: () -> T): T = FutureTask(Callable(block)).also(instrumentation::runOnMainSync).get()
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(40)
        }
        assertTrue("Timed out waiting for karaoke UI", condition())
    }

    private fun screenshot(name: String) {
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(app.getExternalFilesDir(null), "test-screenshots")
            assertTrue(directory.isDirectory || directory.mkdirs())
            File(directory, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { image.recycle() }
    }
}
