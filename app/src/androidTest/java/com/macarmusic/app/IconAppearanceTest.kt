package com.macarmusic.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.macarmusic.app.model.ButtonIconStyle
import com.macarmusic.app.data.DisplayOptions
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.model.FireStyle
import com.macarmusic.app.model.SpectrumColors
import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.SpectrumPalette
import com.macarmusic.app.model.SpectrumPosition
import com.macarmusic.app.model.WidgetPlayback
import com.macarmusic.app.ui.PlayerIcons
import com.macarmusic.app.widgets.HomeWidgetKind
import com.macarmusic.app.widgets.WidgetViews
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconAppearanceTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val icons = listOf(R.drawable.ic_play, R.drawable.ic_pause, R.drawable.ic_stop, R.drawable.ic_previous,
        R.drawable.ic_next, R.drawable.ic_rewind, R.drawable.ic_forward, R.drawable.ic_shuffle, R.drawable.ic_repeat,
        R.drawable.ic_repeat_one, R.drawable.ic_pin, R.drawable.ic_favorite, R.drawable.ic_favorite_border,
        R.drawable.ic_volume_on, R.drawable.ic_volume_off, R.drawable.ic_radio, R.drawable.ic_close,
        R.drawable.ic_folder, R.drawable.ic_queue, R.drawable.ic_library, R.drawable.ic_playlist,
        R.drawable.ic_note, R.drawable.ic_info, R.drawable.ic_more, R.drawable.ic_settings,
        R.drawable.ic_lyrics_versions, R.drawable.ic_lyrics_next, R.drawable.ic_lyrics_timing,
        R.drawable.ic_back, R.drawable.ic_expand_less, R.drawable.ic_expand_more)

    @Test fun repeatedIconBindingsReuseDrawablesAndInvalidateChangedInputs() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(app, R.style.Theme_ManaPlayer)
            val image = androidx.appcompat.widget.AppCompatImageView(context)
            val button = com.google.android.material.button.MaterialButton(context)
            for (style in ButtonIconStyle.entries) {
                for (resource in listOf(R.drawable.ic_play, R.drawable.ic_radio)) {
                    for (color in listOf(0xff225588.toInt(), 0xff883322.toInt())) {
                        val oldImage = image.drawable
                        val oldButton = button.icon
                        PlayerIcons.image(image, resource, color, style)
                        PlayerIcons.button(button, resource, color, style)
                        assertNotSame(oldImage, image.drawable)
                        assertNotSame(oldButton, button.icon)
                        val appliedImage = image.drawable
                        val appliedButton = button.icon
                        val imageTag = image.getTag(R.id.playerIconSpec)
                        val buttonTag = button.getTag(R.id.playerIconSpec)
                        repeat(20) {
                            image.isEnabled = it % 2 == 0
                            button.isEnabled = image.isEnabled
                            PlayerIcons.image(image, resource, color, style)
                            PlayerIcons.button(button, resource, color, style)
                            assertSame(appliedImage, image.drawable)
                            assertSame(appliedButton, button.icon)
                            assertSame(imageTag, image.getTag(R.id.playerIconSpec))
                            assertSame(buttonTag, button.getTag(R.id.playerIconSpec))
                        }
                        image.setImageDrawable(null)
                        button.icon = null
                        PlayerIcons.image(image, resource, color, style)
                        PlayerIcons.button(button, resource, color, style)
                        assertNotNull(image.drawable)
                        assertNotNull(button.icon)
                        assertNotSame(appliedImage, image.drawable)
                        assertNotSame(appliedButton, button.icon)
                    }
                }
            }
        }
    }

    @Test fun allButtonThemePixelsRemainStableAcrossDayNightReloads() {
        fun render(night: Int): Bitmap {
            val context = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }), R.style.Theme_ManaPlayer)
            val size = 48
            val image = Bitmap.createBitmap(icons.size * size, ButtonIconStyle.entries.size * size * 2, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            for ((row, style) in ButtonIconStyle.entries.withIndex()) {
                for ((column, resource) in icons.withIndex()) {
                    for (enabled in listOf(true, false)) {
                        val top = (row * 2 + if (enabled) 0 else 1) * size
                        PlayerIcons.drawable(context, resource, style, 0xff225588.toInt()).apply {
                            state = intArrayOf(if (enabled) android.R.attr.state_enabled else -android.R.attr.state_enabled)
                            setBounds(column * size, top, (column + 1) * size, top + size)
                            draw(canvas)
                        }
                    }
                }
            }
            return image
        }
        val directory = java.io.File(app.getExternalFilesDir(null), "test-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        val day = render(Configuration.UI_MODE_NIGHT_NO)
        val night = render(Configuration.UI_MODE_NIGHT_YES)
        try {
            for ((name, mode, first) in listOf(Triple("day", Configuration.UI_MODE_NIGHT_NO, day),
                Triple("night", Configuration.UI_MODE_NIGHT_YES, night))) {
                val reloaded = render(mode)
                try { assertTrue("$name must not retain the other theme's cached colors", first.sameAs(reloaded)) }
                finally { reloaded.recycle() }
                java.io.File(directory, "button-atlas-$name.png").outputStream().use {
                    assertTrue(first.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            assertFalse("Day/night palettes must remain distinct", day.sameAs(night))
        } finally { day.recycle(); night.recycle() }
    }

    @Test fun freshPreferencesUseLogoAndSpectrumDefaultsWithoutReplacingExplicitSavedChoices() {
        val context = object : android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("appearance147-test-$name", mode)
        }
        val preferences = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
        try {
            val defaults = DisplaySettings(context) {}.options.value
            assertEquals(ButtonIconStyle.LOGO, defaults.buttonIcons)
            assertEquals(SpectrumOptions(), defaults.spectrum)
            assertFalse(defaults.autoPlayOnLaunch)
            assertTrue(preferences.edit().putString("buttonIcons", "MONOCHROME")
                .putBoolean("spectrumEnabled", false).putString("spectrumPosition", "RIGHT")
                .putString("spectrumPalette", "CLASSIC").putInt("spectrumBands", 32)
                .putInt("spectrumRefreshRate", 60).putString("spectrumBarStyle", "SMOOTH")
                .putInt("fireColorLow", 0xffdf69).putInt("fireColorMiddle", 0xff9828)
                .putInt("fireColorHigh", 0xed4529).commit())
            val saved = DisplaySettings(context) {}
            val oldSpectrum = saved.options.value.spectrum
            assertEquals(ButtonIconStyle.MONOCHROME, saved.options.value.buttonIcons)
            assertFalse(oldSpectrum.enabled)
            assertEquals(SpectrumPosition.RIGHT, oldSpectrum.position)
            assertEquals(SpectrumPalette.CLASSIC, oldSpectrum.palette)
            assertEquals(32, oldSpectrum.bands)
            assertEquals(60, oldSpectrum.refreshRate)
            assertEquals(FireStyle.SMOOTH, oldSpectrum.rendering)
            assertEquals(SpectrumColors(0xffdf69, 0xff9828, 0xed4529), oldSpectrum.colors)
            for (style in ButtonIconStyle.entries) {
                saved.update { it.copy(buttonIcons = style) }
                assertEquals(saved.options.value, DisplaySettings(context) {}.options.value)
                assertEquals(oldSpectrum, saved.options.value.spectrum)
            }
        } finally { assertTrue(preferences.edit().clear().commit()) }
    }

    @Test fun widgetsRenderTheSelectedPaletteWhenCachedIconsAreReusedAndDisabledControlsStayGrey() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val night = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }), R.style.Theme_ManaPlayer)
            val size = (40 * app.resources.displayMetrics.density).toInt().coerceIn(24, 128)
            fun pixels(drawable: android.graphics.drawable.Drawable): List<Int> {
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(Canvas(bitmap))
                    return IntArray(size * size).also { bitmap.getPixels(it, 0, size, 0, 0, size, size) }.toList()
                } finally { bitmap.recycle() }
            }
            val root = WidgetViews.full(app, HomeWidgetKind.CONTROLS, 147, WidgetPlayback(), null, "",
                DisplayOptions(buttonIcons = ButtonIconStyle.MONOCHROME)).apply(app, android.widget.FrameLayout(app))
            val rendered = linkedMapOf<ButtonIconStyle, List<Int>>()
            for (style in ButtonIconStyle.entries + listOf(ButtonIconStyle.MONOCHROME, ButtonIconStyle.LOGO)) {
                WidgetViews.full(app, HomeWidgetKind.CONTROLS, 147, WidgetPlayback(), null, "",
                    DisplayOptions(buttonIcons = style)).reapply(app, root)
                val actual = pixels(root.findViewById<android.widget.ImageView>(R.id.widget_play).drawable)
                if (style == ButtonIconStyle.MONOCHROME) {
                    val opaque = actual.filter { Color.alpha(it) == 255 }
                    assertTrue(opaque.isNotEmpty())
                    assertTrue("Monochrome widgets must not retain an old color filter", opaque.all { it == Color.WHITE })
                    continue
                }
                val expected = pixels(PlayerIcons.drawable(night, R.drawable.ic_play, style, Color.WHITE).apply {
                    state = intArrayOf(android.R.attr.state_enabled)
                })
                assertTrue("$style must reach the widget, not a cached previous palette or flat tint", expected == actual)
                rendered[style] = actual
                val previous = root.findViewById<android.widget.ImageView>(R.id.widget_previous)
                assertFalse(previous.isEnabled)
                assertEquals(255, previous.imageAlpha)
                val grey = PlayerIcons.tint(night, Color.WHITE)
                    .getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                val disabled = pixels(previous.drawable).filter { Color.alpha(it) == 255 }
                assertTrue(disabled.isNotEmpty())
                assertTrue("Unavailable widget controls stay grey in $style", disabled.all { it == grey })
            }
            assertEquals(8, rendered.values.distinct().size)
        }
    }

    @Test fun colorfulIconsKeepTheirShapesInBothThemesAndAllDisabledIconsAreGrey() {
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val context = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }), R.style.Theme_ManaPlayer)
            for (resource in icons) {
                fun render(style: ButtonIconStyle, enabled: Boolean): IntArray {
                    val image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    return try {
                        PlayerIcons.drawable(context, resource, style, 0xff225588.toInt()).apply {
                            state = if (enabled) intArrayOf(android.R.attr.state_enabled) else intArrayOf(-android.R.attr.state_enabled)
                            setBounds(0, 0, 96, 96)
                            draw(Canvas(image))
                        }
                        IntArray(96 * 96).also { image.getPixels(it, 0, 96, 0, 0, 96, 96) }
                    } finally { image.recycle() }
                }
                val mono = render(ButtonIconStyle.MONOCHROME, true)
                val label = "${app.resources.getResourceEntryName(resource)} night=$night"
                val palettes = ButtonIconStyle.entries.filter { it != ButtonIconStyle.MONOCHROME }.map { style ->
                    val colorful = render(style, true)
                    assertTrue("$label $style must have visible ink", colorful.count { Color.alpha(it) > 200 } > 30)
                    assertTrue("$label $style should use a real multicolor gradient",
                        colorful.filter { Color.alpha(it) > 250 }.map { it and 0xffffff }.distinct().size > 8)
                    assertTrue("$label $style must preserve the familiar silhouette",
                        mono.indices.all { kotlin.math.abs(Color.alpha(mono[it]) - Color.alpha(colorful[it])) <= 2 })
                    colorful.toList()
                }
                assertEquals("$label must have eight distinct color palettes", 8, palettes.distinct().size)
                for (style in ButtonIconStyle.entries) {
                    val disabled = render(style, false).filter { Color.alpha(it) == 255 }
                    val grey = PlayerIcons.tint(context, 0xff225588.toInt())
                        .getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                    assertTrue("$label $style must have visible disabled ink", disabled.size > 30)
                    assertTrue("$label $style must be dimmed grey when unavailable", disabled.all { it == grey })
                }
            }
        }
    }

    @Test fun bothBrandMarksHaveMatchingCenteredBoundsAndTransparentBackgrounds() {
        for (density in listOf(160, 240, 320, 480, 640)) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { densityDpi = density })
            val bounds = listOf(R.drawable.ic_launcher, R.drawable.ic_launcher_radio).map { resource ->
                val image = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                try {
                    requireNotNull(AppCompatResources.getDrawable(context, resource)).apply {
                        setBounds(0, 0, 192, 192)
                        draw(Canvas(image))
                    }
                    val pixels = IntArray(192 * 192).also { image.getPixels(it, 0, 192, 0, 0, 192, 192) }
                    assertEquals(0, Color.alpha(pixels.first()))
                    val points = pixels.indices.filter { Color.alpha(pixels[it]) > 32 }
                    val rectangle = android.graphics.Rect(points.minOf { it % 192 }, points.minOf { it / 192 },
                        points.maxOf { it % 192 } + 1, points.maxOf { it / 192 } + 1)
                    assertEquals(96f, rectangle.exactCenterX(), 1.5f)
                    assertEquals(96f, rectangle.exactCenterY(), 2.5f)
                    rectangle
                } finally { image.recycle() }
            }
            assertTrue("Both marks should use the same visual footprint at $density dpi",
                kotlin.math.abs(bounds[0].width() - bounds[1].width()) <= 3 &&
                    kotlin.math.abs(bounds[0].height() - bounds[1].height()) <= 3)
        }
    }

    @Test fun copilotAuroraReversesTheLogoTransitionAtActualSmallIconSizes() {
        for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val context = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }), R.style.Theme_ManaPlayer)
            for (size in listOf(24, 32, 48)) {
                fun render(style: ButtonIconStyle): IntArray {
                    val image = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    return try {
                        PlayerIcons.drawable(context, R.drawable.ic_play, style).apply {
                            state = intArrayOf(android.R.attr.state_enabled)
                            setBounds(0, 0, size, size)
                            draw(Canvas(image))
                        }
                        IntArray(size * size).also { image.getPixels(it, 0, size, 0, 0, size, size) }
                    } finally { image.recycle() }
                }
                val logo = render(ButtonIconStyle.LOGO)
                val copilot = render(ButtonIconStyle.COPILOT_AURORA)
                val opaque = logo.indices.filter { Color.alpha(logo[it]) == 255 && Color.alpha(copilot[it]) == 255 }
                fun redness(color: Int) = Color.red(color) - Color.green(color)
                fun projection(index: Int) = ((index % size + index / size + 1) * 24f / size - 10) / 28
                val early = opaque.filter { projection(it) < 0.4f }
                val late = opaque.filter { projection(it) > 0.6f }
                assertTrue(early.isNotEmpty() && late.isNotEmpty())
                val label = "$size px, night=$night"
                assertTrue("$label: Aurora starts pink/purple rather than Logo's teal",
                    early.map { redness(copilot[it]) - redness(logo[it]) }.average() > 60)
                assertTrue("$label: Aurora ends blue/teal rather than Logo's purple/pink",
                    late.map { redness(logo[it]) - redness(copilot[it]) }.average() > 35)
                val difference = opaque.map {
                    val first = DoubleArray(3)
                    val second = DoubleArray(3)
                    androidx.core.graphics.ColorUtils.colorToLAB(logo[it], first)
                    androidx.core.graphics.ColorUtils.colorToLAB(copilot[it], second)
                    kotlin.math.sqrt(first.indices.sumOf { channel ->
                        (first[channel] - second[channel]) * (first[channel] - second[channel])
                    })
                }.average()
                assertTrue("$label: Mean perceptual separation must exceed 22, was $difference", difference > 22)
            }
        }
    }

    @Test fun materialButtonsRetainColoredPreviewsAndGreyDisabledIconsInBothThemes() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }), R.style.Theme_ManaPlayer)
                for (resource in listOf(R.drawable.ic_play, R.drawable.ic_favorite_border)) {
                    for (style in ButtonIconStyle.entries) {
                        val button = com.google.android.material.button.MaterialButton(context)
                        PlayerIcons.button(button, resource, color = 0xff225588.toInt(), iconStyle = style)
                        for (enabled in listOf(true, false)) {
                            button.isEnabled = enabled
                            val image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                            try {
                                requireNotNull(button.icon).apply {
                                    setBounds(0, 0, 96, 96)
                                    draw(Canvas(image))
                                }
                                val pixels = IntArray(96 * 96).also { image.getPixels(it, 0, 96, 0, 0, 96, 96) }
                                val opaque = pixels.filter { Color.alpha(it) == 255 }
                                val label = "$resource $style enabled=$enabled night=$night"
                                assertTrue("$label has visible ink", opaque.size > 30)
                                if (!enabled) {
                                    val grey = PlayerIcons.tint(context, 0xff225588.toInt())
                                        .getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                                    assertTrue("$label is dimmed grey", opaque.all { it == grey })
                                } else if (style != ButtonIconStyle.MONOCHROME)
                                    assertTrue("$label retains its gradient", opaque.map { it and 0xffffff }.distinct().size > 8)
                                else assertTrue("$label retains its requested contrast", opaque.all { it == 0xff225588.toInt() })
                            } finally { image.recycle() }
                        }
                    }

                }
            }
        }
    }

    @Test fun imageButtonsStayDimmedWhenAnIconIsReplacedWhileAlreadyDisabled() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = ContextThemeWrapper(app.createConfigurationContext(Configuration(app.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }), R.style.Theme_ManaPlayer)
                for (custom in listOf(false, true)) {
                    for (style in ButtonIconStyle.entries) {
                        for (enabledAtReplacement in listOf(true, false)) {
                            val view = if (custom) com.macarmusic.app.ui.PlayerIconButton(context)
                                else androidx.appcompat.widget.AppCompatImageButton(context)
                            view.isEnabled = enabledAtReplacement
                            PlayerIcons.image(view, R.drawable.ic_shuffle, color = 0xff225588.toInt(), iconStyle = style)
                            for (enabled in listOf(enabledAtReplacement, !enabledAtReplacement)) {
                                view.isEnabled = enabled
                                val image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                                try {
                                    view.drawable.apply {
                                        setBounds(0, 0, 96, 96)
                                        draw(Canvas(image))
                                    }
                                    val pixels = IntArray(96 * 96).also { image.getPixels(it, 0, 96, 0, 0, 96, 96) }
                                    val opaque = pixels.filter { Color.alpha(it) == 255 }
                                    assertTrue(opaque.size > 30)
                                    if (!enabled) {
                                        val grey = PlayerIcons.tint(context, 0xff225588.toInt())
                                            .getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)
                                        assertTrue("Disabled $style icons must use the dimmed grey, not white", opaque.all { it == grey })
                                    } else if (style != ButtonIconStyle.MONOCHROME)
                                        assertTrue(opaque.map { it and 0xffffff }.distinct().size > 8)
                                    else assertTrue(opaque.all { it == 0xff225588.toInt() })
                                } finally { image.recycle() }
                            }
                        }
                    }
                }
            }
        }
    }
}
