package com.macarmusic.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class LaunchArtworkTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val background = 0xff101b24.toInt()

    @Test fun legacyStartingWindowKeepsTheCompleteCenteredLogoAcrossDisplaySizes() {
        for (density in listOf(160, 240, 320)) {
            val context = atDensity(density)
            val size = (108 * density / 160f).roundToInt()
            for ((width, height) in listOf(360 to 640, 1024 to 600, 1280 to 720)) {
                val actual = render(context, R.drawable.launch_background, width, height)
                val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(expected)
                    canvas.drawColor(background)
                    val left = (width - size) / 2
                    val top = (height - size) / 2
                    requireNotNull(context.getDrawable(R.drawable.ic_launcher)).apply {
                        setBounds(left, top, left + size, top + size)
                        draw(canvas)
                    }
                    assertArrayEquals("Legacy launch must fit the entire 108dp logo at ${density}dpi, ${width}x$height",
                        pixels(expected), pixels(actual))
                    if (density == 160 && width == 1024) save("radio141-launch-legacy", actual)
                } finally {
                    actual.recycle()
                    expected.recycle()
                }
            }
        }
    }

    @Test fun systemSplashPaddingRetainsAllLogoPixelsInsideTheMaskAcrossDensities() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        for (density in listOf(160, 240, 320, 480, 640)) {
            val context = atDensity(density)
            val theme = ContextThemeWrapper(context, R.style.Theme_ManaPlayer_Launch)
            val value = TypedValue()
            assertTrue(theme.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
            assertEquals(R.drawable.splash_icon, value.resourceId)
            val size = (288 * density / 160f).roundToInt()
            val actual = render(context, value.resourceId, size, size)
            val unpadded = render(context, R.drawable.ic_launcher, size, size)
            val expected = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                requireNotNull(context.getDrawable(R.drawable.ic_launcher)).apply {
                    setBounds(size / 4, size / 4, size * 3 / 4, size * 3 / 4)
                    draw(Canvas(expected))
                }
                val output = pixels(actual)
                assertArrayEquals("Padding must scale the complete logo, not crop or stretch it", pixels(expected), output)
                // Android 12's 288dp icon canvas has a 192dp-diameter safe circle.
                fun outsideMask(image: IntArray): Int = image.indices.count { index ->
                    val x = index % size + 0.5 - size / 2.0
                    val y = index / size + 0.5 - size / 2.0
                    Color.alpha(image[index]) > 8 && x * x + y * y > size * size / 9.0
                }
                assertTrue("The previous unpadded launcher artwork reproduces the clipping risk", outsideMask(pixels(unpadded)) > 0)
                assertEquals("Thumb and all side bars must stay inside the splash mask at ${density}dpi", 0, outsideMask(output))
                assertTrue(output.count { Color.alpha(it) > 8 } > size * size / 40)
                if (density == 160) {
                    val preview = Bitmap.createBitmap(1024, 600, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(preview).apply {
                            drawColor(background)
                            drawBitmap(actual, null, Rect(368, 156, 656, 444), null)
                        }
                        save("radio141-launch-system", preview)
                    } finally { preview.recycle() }
                }
            } finally {
                actual.recycle()
                unpadded.recycle()
                expected.recycle()
            }
        }
    }

    private fun atDensity(density: Int): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { densityDpi = density })

    private fun render(context: Context, resource: Int, width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            requireNotNull(context.getDrawable(resource)).apply {
                setBounds(0, 0, width, height)
                draw(Canvas(bitmap))
            }
        }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun save(name: String, bitmap: Bitmap) {
        val directory = File(app.getExternalFilesDir(null), "test-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
