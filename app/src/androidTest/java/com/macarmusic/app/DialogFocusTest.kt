package com.macarmusic.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.data.ColorSlotStore
import com.macarmusic.app.model.ColorSlots
import com.macarmusic.app.lyrics.LyricChoice
import com.macarmusic.app.lyrics.LyricChoices
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.ui.AccentTheme
import com.macarmusic.app.ui.AppearanceChoices
import com.macarmusic.app.ui.ColorSwatchDialog
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.LyricVersionsDialog
import com.macarmusic.app.ui.PlayerDialogChoice
import com.macarmusic.app.ui.awaitDraw
import com.macarmusic.app.ui.installPlayerInputFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask

@RunWith(AndroidJUnit4::class)
class DialogFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val previousDisplay = app.displaySettings.options.value
    private val previousTheme = app.themeMode
    private val store = ColorSlotStore(app)
    private val previousSlots = store.read()

    @Before fun prepare() {
        instrumentation.setInTouchMode(true)
        assertTrue(app.getSharedPreferences("settings", 0).edit().putBoolean("parkedNoticeSeen", true).commit())
        onMain {
            app.notice.value?.let { app.dismissNotice(it.id) }
            app.displaySettings.update { it.copy(autoPlayOnLaunch = false, onlineLyrics = false) }
        }
    }

    @After fun restore() {
        runBlocking { store.save(previousSlots) }
        onMain {
            app.displaySettings.update { previousDisplay }
            app.themeMode = previousTheme
        }
        instrumentation.setInTouchMode(true)
    }

    @Test fun swatchFocusIsObviousAndSeparateFromTheSelectedColor() {
        val slots = ColorSlots(listOf(0x000000, 0xffffff, 0x777777, 0x101010, null))
        runBlocking { store.save(slots) }
        for (night in listOf(true, false)) for (blocks in listOf(false, true)) {
            appearance(night, blocks)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var saved: Int? = null
                scenario.onActivity { activity ->
                    ColorSwatchDialog.show(activity, activity.getString(R.string.custom_color),
                        ColorSlots.RAINBOW.first()) { saved = it }
                }

                val grid = dialogView(R.id.colorGrid) as GridLayout
                val swatches = onMain { grid.children.filterIsInstance<TextView>().toList() }
                val first = swatches.first()
                focus(first)
                screenshot("focus1482-swatches-${if (night) "night" else "day"}-$blocks")
                onMain {
                    assertFocus(first)
                    val action = grid.rootView.findViewById<TextView>(android.R.id.button2)
                    assertReadable(action)
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                await { swatches[1].isFocused }
                onMain {
                    assertTrue(first.isSelected)
                    assertFalse(swatches[1].isSelected)
                    assertEquals(0, focusColors(first).size)
                    assertFocus(swatches[1])
                    assertNull("Moving focus must not apply a color", saved)
                }
                for ((index, color) in (ColorSlots.RAINBOW + slots.colors).withIndex()) {
                    val swatch = swatches[index]
                    focus(swatch)
                    onMain { assertFocus(swatch) }
                    if (color == null) continue
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                    await { swatch.isSelected }
                    onMain {
                        assertEquals("\u2713", swatch.text.toString())
                        assertEquals(1, swatches.count { it.isSelected })
                        assertTrue("The selected check must contrast with #${color.toString(16)}",
                            ColorUtils.calculateContrast(swatch.currentTextColor, color or Color.BLACK) >= 4.5)
                        assertNull(saved)
                    }
                }
                focus(swatches.last())
                onMain { assertFocus(swatches.last()) }
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                assertNull(saved)
                assertEquals(slots, store.read())
            }
        }
    }

    @Test fun dialogLogosAndGlobalMessageTimingPreserveTheOpenEditor() {
        onMain { app.displaySettings.update { it.copy(noticeDurationSeconds = 5) } }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (logo in com.macarmusic.app.model.AppLogo.entries) {
                lateinit var dialog: com.macarmusic.app.ui.PlayerDialog
                lateinit var input: androidx.appcompat.widget.AppCompatEditText
                scenario.onActivity { activity ->
                    app.displaySettings.update { it.copy(appLogo = logo) }
                    input = androidx.appcompat.widget.AppCompatEditText(activity).apply {
                        id = R.id.customColorInput
                        setText("Keep this unfinished edit")
                    }
                    dialog = AccentTheme.dialog(activity).setTitle(R.string.custom_color).setView(input)
                        .setNegativeButton(R.string.cancel, null).show()
                }
                closeSoftKeyboard()
                val image = dialogView(R.id.playerDialogLogo) as android.widget.ImageView
                val title = dialogView(R.id.playerDialogTitle)
                onMain {
                    val expected = checkNotNull(androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        image.context, com.macarmusic.app.ui.Branding.icon(logo)))
                    assertEquals(opaqueColors(expected, 40, 40), opaqueColors(image.drawable, 40, 40))
                    assertFalse(image.isFocusable)
                    assertTrue(image.isShown && image.width > 0 && image.right <= title.left)
                }
                val started = SystemClock.elapsedRealtime()
                onMain { app.inform("Timed message inside an editor") }
                await { dialog.findViewById<View>(R.id.noticeBar)?.isShown == true }
                screenshot("car154-dialog-${logo.name.lowercase()}")
                SystemClock.sleep((4_000 - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
                onMain {
                    assertNotNull("The default message must remain for five seconds", app.notice.value)
                    assertTrue(dialog.isShowing)
                    assertEquals("Keep this unfinished edit", input.text.toString())
                }
                await { app.notice.value == null }
                val elapsed = SystemClock.elapsedRealtime() - started
                assertTrue("Default message timeout was ${elapsed}ms", elapsed in 4_900..6_500)
                onMain {
                    assertTrue("The editor must not time out with the message", dialog.isShowing)
                    assertFalse(dialog.findViewById<View>(R.id.noticeBar)!!.isShown)
                    assertEquals("Keep this unfinished edit", input.text.toString())
                    app.displaySettings.update { it.copy(noticeDurationSeconds = 3) }
                    app.report("Shorter configured message")
                }
                val configured = SystemClock.elapsedRealtime()
                await { app.notice.value == null }
                assertTrue("The configured duration was ignored",
                    SystemClock.elapsedRealtime() - configured in 2_900..4_500)
                onMain {
                    assertTrue(dialog.isShowing)
                    dialog.dismiss()
                    app.displaySettings.update { it.copy(noticeDurationSeconds = 5) }
                }
            }
        }
    }

    @Test fun appearanceStartsOnTheCurrentChoiceBeforeDirectionalNavigation() {
        appearance(night = true, blocks = true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var chosen = -1
            scenario.onActivity {
                AppearanceChoices.show(it, R.string.appearance_icons, AppearanceChoices.buttonThemes, 7) { index ->
                    chosen = index
                }
            }
            val grid = dialogView(R.id.appearanceGrid) as GridLayout
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await(diagnostic = { "Theme focus=${grid.rootView.findFocus()?.id}, touch=${grid.isInTouchMode}" }) {
                grid.findViewById<View>(R.id.iconsCopilotAurora).isFocused
            }
            onMain {
                val selected = grid.findViewById<MaterialButton>(R.id.iconsCopilotAurora)
                assertTrue(selected.isChecked)
                assertFocus(selected)
                assertEquals(-1, chosen)
            }
            screenshot("focus1490-current-theme")
            val nextDirection = onMain {
                if (grid.findViewById<View>(R.id.iconsCopilotSunrise).top >
                    grid.findViewById<View>(R.id.iconsCopilotAurora).top) KeyEvent.KEYCODE_DPAD_DOWN
                else KeyEvent.KEYCODE_DPAD_RIGHT
            }
            instrumentation.sendKeyDownUpSync(nextDirection)
            await(diagnostic = { "Next theme focus=${grid.rootView.findFocus()?.id}, columns=${grid.columnCount}" }) {
                grid.findViewById<View>(R.id.iconsCopilotSunrise).isFocused
            }
            onMain {
                assertTrue(grid.findViewById<MaterialButton>(R.id.iconsCopilotAurora).isChecked)
                assertEquals(-1, chosen)
            }
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        }
    }

    @Test fun choiceListsStartAtExistingSingleAndMultipleSelections() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var calls = 0
            for (multiple in listOf(false, true)) {
                scenario.onActivity {
                    val labels = Array<CharSequence>(12) { index -> "Choice $index" }
                    val builder = AccentTheme.dialog(it).setTitle(R.string.custom_color)
                        .setNegativeButton(R.string.cancel, null)
                    if (multiple) builder.setMultiChoiceItems(labels, BooleanArray(12) { index -> index == 8 }) { _, _, _ -> calls++ }
                    else builder.setSingleChoiceItems(labels, 8) { _, _ -> calls++ }
                    builder.show()
                }
                val list = dialogView(R.id.playerDialogList) as ListView
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                await { list.hasFocus() && list.selectedItemPosition == 8 }
                onMain {
                    assertTrue(list.isItemChecked(8))
                    assertEquals(0, calls)
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                await(diagnostic = { "Choice focus=${list.hasFocus()}, index=${list.selectedItemPosition}, touch=${list.isInTouchMode}" }) {
                    list.selectedItemPosition == 7
                }
                onMain {
                    assertTrue(list.isItemChecked(8))
                    assertFalse(list.isItemChecked(7))
                    assertEquals(0, calls)
                }
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            }
        }
    }

    @Test fun swatchStartsOnSelectedColorAndNestedCancelRestoresTheEditButton() {
        val slots = ColorSlots(listOf(0x123456, 0x777777, null, null, null))
        runBlocking { store.save(slots) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var saved: Int? = null
            scenario.onActivity {
                ColorSwatchDialog.show(it, it.getString(R.string.custom_color), 0x123456) { color -> saved = color }
            }
            val grid = dialogView(R.id.colorGrid) as GridLayout
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            val selected = onMain { grid.findViewWithTag<TextView>("custom:0") }
            await { selected.isFocused }
            onMain { assertFocus(selected); assertTrue(selected.isSelected) }
            val edit = dialogView(R.id.colorEditSlot)
            focus(edit)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            dialogView(R.id.customColorInput)
            closeSoftKeyboard()
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            dialogView(R.id.colorGrid)
            await { grid.hasWindowFocus() }
            if (onMain { grid.isInTouchMode }) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            await(diagnostic = { "Parent focus=${grid.rootView.findFocus()?.id}, touch=${grid.isInTouchMode}" }) {
                edit.isFocused
            }
            onMain { assertFocus(edit); assertNull(saved) }
            assertEquals(slots, store.read())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        }
    }

    @Test fun loadedLyricChoicesStartAtTheCurrentVersionAndRestoreTheirOwnMoreButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var actions = 0
            val choices = List(12) { LyricChoice("version-$it", "Version $it", LyricOrigin.LOCAL_FILE) }
            val state = LyricChoices("track", choices, currentKey = choices[9].key, loading = false)
            lateinit var dialog: LyricVersionsDialog
            scenario.onActivity {
                dialog = LyricVersionsDialog(it, { app.displaySettings.options.value },
                    { actions++ }, { actions++ }, { actions++ }, {})
                dialog.show()
                dialog.update(state)
            }
            val list = dialogView(R.id.lyricsVersionsList) as RecyclerView
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            fun focusedRow() = list.findFocus()?.let(list::findContainingItemView)
            await {
                focusedRow()?.getTag(R.id.playerFocusKey) == "lyric:version-9" &&
                    list.findFocus()?.id == R.id.lyricsVersionChoose
            }
            val more = onMain { checkNotNull(focusedRow()).findViewById<View>(R.id.lyricsVersionMore) }
            focus(more)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            dialogView(R.id.lyricsVersionActions)
            onMain { dialog.update(state.copy(choices = choices.reversed())) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await(diagnostic = {
                "Lyric return: focus=${list.rootView.findFocus()?.id}, row=${focusedRow()?.getTag(R.id.playerFocusKey)}, " +
                    "window=${list.hasWindowFocus()}, touch=${list.isInTouchMode}"
            }) {
                focusedRow()?.getTag(R.id.playerFocusKey) == "lyric:version-9" &&
                    list.findFocus()?.id == R.id.lyricsVersionMore
            }
            onMain {
                assertEquals(2, list.getChildAdapterPosition(checkNotNull(focusedRow())))
                assertEquals(0, actions)
            }
            screenshot("focus1490-lyric-row-return")
            onMain { dialog.dismiss() }
        }
    }

    @Test fun loadingChoicesDoNotTakeFocusFromTheCloseButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: LyricVersionsDialog
            scenario.onActivity {
                dialog = LyricVersionsDialog(it, { app.displaySettings.options.value }, {}, {}, {}, {})
                dialog.show()
                dialog.update(LyricChoices("track"))
            }
            val close = dialogView(android.R.id.button2)
            focus(close)
            onMain {
                dialog.update(LyricChoices("track",
                    listOf(LyricChoice("current", "Current", LyricOrigin.LOCAL_FILE)),
                    currentKey = "current", loading = false))
            }
            val list = dialogView(R.id.lyricsVersionsList) as RecyclerView
            await { list.childCount == 1 && !list.isLayoutRequested }
            onMain { assertTrue(close.isFocused); dialog.dismiss() }
        }
    }

    @Test fun rgbInputsAndRebuiltCustomSlotsKeepTheirFocusWithoutSavingOnCancel() {
        runBlocking { store.save(ColorSlots()) }
        appearance(night = true, blocks = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var saved: Int? = null
            scenario.onActivity { activity ->
                ColorSwatchDialog.show(activity, activity.getString(R.string.custom_color), 0x123456) { saved = it }
            }
            onView(withId(R.id.colorAddSlot)).inRoot(isDialog()).perform(scrollTo(), click())
            closeSoftKeyboard()
            val field = dialogView(R.id.customColorInput)
            focus(field)
            onMain { assertFocus(field) }
            for (id in listOf(R.id.colorRed, R.id.colorGreen, R.id.colorBlue)) {
                val slider = dialogView(id) as SeekBar
                focus(slider)
                val old = onMain {
                    assertFocus(slider)
                    assertTrue(ColorUtils.calculateContrast(
                        slider.thumbTintList!!.getColorForState(slider.drawableState, 0),
                        AccentTheme.color(slider, com.google.android.material.R.attr.colorSurface)) >= 3)
                    slider.progress
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                await { slider.progress > old }
                onMain { assertFocus(slider) }
            }
            screenshot("focus1482-rgb-editor")
            onView(withId(R.id.customColorInput)).inRoot(isDialog()).perform(scrollTo(), replaceText("#777777"))
            closeSoftKeyboard()
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            val grid = dialogView(R.id.colorGrid) as GridLayout
            val added = onMain { grid.findViewWithTag<TextView>("custom:5") }
            assertNotNull(added)
            focus(added)
            onMain {
                assertFocus(added)
                assertTrue(added.isSelected)
                assertEquals("\u2713", added.text.toString())
                assertEquals(14, grid.childCount)
                assertNull(saved)
            }
            screenshot("focus1482-rebuilt-slot")
            onView(withId(R.id.colorEditSlot)).inRoot(isDialog()).perform(scrollTo(), click())
            closeSoftKeyboard()
            val red = dialogView(R.id.colorRed)
            focus(red)
            onMain { assertFocus(red) }
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            assertNull(saved)
            assertEquals(ColorSlots(), store.read())
        }
    }

    @Test fun appearanceTilesAndChoiceMarksRemainVisibleWithLowContrastAccents() {
        for (night in listOf(true, false)) {
            appearance(night, blocks = night)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var activity: MainActivity
                var selected = -1
                scenario.onActivity {
                    activity = it
                    AppearanceChoices.show(it, R.string.appearance_icons,
                        AppearanceChoices.buttonThemes, 6) { choice -> selected = choice }
                }
                val grid = dialogView(R.id.appearanceGrid) as GridLayout
                val buttons = onMain { grid.children.filterIsInstance<MaterialButton>().toList() }
                for (button in buttons) {
                    focus(button)
                    onMain {
                        assertFocus(button)
                        assertReadable(button)
                        assertEquals(-1, selected)
                        assertTrue(buttons[6].isChecked)
                        assertEquals(1, buttons.count { it.isChecked })
                    }
                }
                onMain {
                    val surface = AccentTheme.color(grid, com.google.android.material.R.attr.colorSurface)
                    assertTrue("The saved theme's border must not disappear into a custom accent",
                        ColorUtils.calculateContrast(buttons[6].strokeColor!!.defaultColor, surface) >= 3)
                }
                screenshot("focus1482-appearance-${if (night) "night" else "day"}")
                focus(buttons.first())
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                await { selected == 0 }
                for (mode in listOf(ListView.CHOICE_MODE_SINGLE, ListView.CHOICE_MODE_MULTIPLE)) {
                    onMain {
                        val row = PlayerDialogChoice(activity, mode)
                        for (checked in listOf(false, true)) {
                            row.isChecked = checked
                            val background = AccentTheme.color(row, if (checked)
                                com.google.android.material.R.attr.colorPrimaryContainer
                                else com.google.android.material.R.attr.colorSurface)
                            val mark = row.compoundDrawablesRelative[0]!!
                            val colors = opaqueColors(mark, mark.intrinsicWidth, mark.intrinsicHeight)
                            assertTrue(colors.isNotEmpty())
                            assertTrue("Choice mark is lost in the background, mode=$mode checked=$checked",
                                colors.all { ColorUtils.calculateContrast(it, background) >= 3 })
                        }
                    }
                }
            }
        }
    }

    @Test fun systemInformationCanBeClosedByDirectionKeysWithoutTrappingSelectableText() {
        for (blocks in listOf(false, true)) {
            onMain { app.displaySettings.update { it.copy(controlBlocks = blocks) } }
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.categoryGeneral)).perform(scrollTo(), click())
                onView(withId(R.id.settingsSystemInfo)).perform(scrollTo(), click())
                val message = dialogView(android.R.id.message) as TextView
                val close = dialogView(android.R.id.button1)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                onMain {
                    assertTrue("The first directional key must focus the visible close action", close.isFocused)
                    assertTrue(message.isTextSelectable)
                    assertFocus(close)
                }
                screenshot("radio151-system-info-close-$blocks")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
                await { message.isFocused }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
                await { close.isFocused }
                onMain { assertFocus(close); assertTrue(message.isTextSelectable) }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                await { !message.isAttachedToWindow }
                onView(withId(R.id.settingsSystemInfo)).check { view, error ->
                    if (error != null) throw error
                    assertTrue(view.isShown)
                }
            }
        }
    }

    @Test fun installingDialogFocusTwiceDoesNotNestDrawablesOrChangeGeometry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val row = LinearLayout(activity)
                val button = MaterialButton(activity)
                val swatch = TextView(activity).apply { isClickable = true; isFocusable = true }
                val slider = SeekBar(activity)
                val label = TextView(activity)
                for (view in listOf(button, swatch, slider, label)) row.addView(view)
                val padding = row.children.associateWith { Rect(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
                val backgrounds = row.children.associateWith { it.background }
                row.installPlayerInputFocus()
                val foregrounds = row.children.associateWith { it.foreground }
                repeat(20) { row.installPlayerInputFocus() }
                for (view in listOf(button, swatch, slider)) {
                    assertNotNull(view.foreground)
                    assertSame(foregrounds[view], view.foreground)
                    assertSame(backgrounds[view], view.background)
                    assertEquals(padding[view], Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom))
                }
                assertNull("Plain labels must not acquire an interactive outline", label.foreground)
            }
        }
    }

    private fun appearance(night: Boolean, blocks: Boolean) = onMain {
        app.themeMode = if (night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        app.displaySettings.update { it.copy(controlBlocks = blocks, customAccentColor = if (night) 0x101010 else 0xfafafa) }
    }

    private fun dialogView(id: Int): View {
        lateinit var result: View
        onView(withId(id)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            result = view
        }
        return result
    }

    private fun focus(view: View) {
        await { view.isShown && view.hasWindowFocus() && view.width > 0 }
        onMain {
            // Synthetic focus jumps must not race a previous D-pad scroll animation.
            generateSequence(view.parent) { it.parent }.filterIsInstance<ScrollView>()
                .forEach { it.isSmoothScrollingEnabled = false }
        }
        instrumentation.setInTouchMode(false)
        if (onMain { view.isInTouchMode }) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
        onMain {
            assertTrue("Cannot focus ${view.tag ?: view.javaClass.simpleName}", view.requestFocus())
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        }
        await {
            val visible = Rect()
            view.isFocused && !view.isLayoutRequested && view.getGlobalVisibleRect(visible) &&
                visible.width() == view.width && visible.height() == view.height
        }
        runBlocking(Dispatchers.Main) { withTimeout(5_000) { view.rootView.awaitDraw() } }
    }

    private fun assertFocus(view: View) {
        assertTrue(view.isFocused)
        val visible = Rect()
        assertTrue("The focused control must be fully visible", view.getGlobalVisibleRect(visible) &&
            visible.width() == view.width && visible.height() == view.height)
        val colors = focusColors(view)
        assertTrue("Missing visible focus outline for ${view.tag ?: view.javaClass.simpleName}", colors.isNotEmpty())
        assertTrue("Focus must use contrasting inner and outer outlines",
            colors.any { first -> colors.any { second -> ColorUtils.calculateContrast(first, second) >= 3 } })
    }

    private fun assertReadable(view: TextView) {
        assertTrue("Dialog labels must remain readable with a low-contrast custom accent",
            ColorUtils.calculateContrast(view.currentTextColor,
                AccentTheme.color(view, com.google.android.material.R.attr.colorSurface)) >= 4.5)
    }

    private fun focusColors(view: View): Set<Int> {
        val foreground = view.foreground ?: return emptySet()
        val focus = if (foreground is LayerDrawable) foreground.getDrawable(foreground.numberOfLayers - 1) else foreground
        return opaqueColors(focus, view.width, view.height)
    }

    private fun opaqueColors(drawable: Drawable, width: Int, height: Int): Set<Int> {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return pixels.filter { Color.alpha(it) == 255 }.toSet()
        } finally { bitmap.recycle() }
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val directory = File(app.getExternalFilesDir(null), "test-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun await(diagnostic: (() -> String)? = null, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (onMain(condition)) return
            SystemClock.sleep(25)
        }
        fail("Dialog focus did not settle: ${diagnostic?.let { onMain(it) }.orEmpty()}")
    }

    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask(Callable(action))
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
