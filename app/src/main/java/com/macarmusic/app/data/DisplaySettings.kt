package com.macarmusic.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.LyricAppearance
import com.macarmusic.app.model.LyricCacheExpiry
import com.macarmusic.app.model.LyricSide
import com.macarmusic.app.model.ThemeAccent
import com.macarmusic.app.model.PlaybackTimeMode
import com.macarmusic.app.model.NavigationPosition
import com.macarmusic.app.model.FireStyle
import com.macarmusic.app.model.SpectrumColors
import com.macarmusic.app.model.SlideshowOptions
import com.macarmusic.app.model.SpectrumDecay
import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.SpectrumPalette
import com.macarmusic.app.model.SpectrumPosition
import com.macarmusic.app.model.SpectrumOrigin
import com.macarmusic.app.model.SpectrumStyle
import com.macarmusic.app.model.TransitionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

enum class BackgroundTint { ORIGINAL, DARK, LIGHT }

@Serializable
data class DisplayOptions(
    val lyricsEnabled: Boolean = true,
    val onlineLyrics: Boolean = true,
    val customTheme: Boolean = false,
    val imageVersion: Long = 0,
    val blur: Int = 0,
    val tint: BackgroundTint = BackgroundTint.DARK,
    val tintStrength: Int = 35,
    val imageIds: List<String> = emptyList(),
    val slideshow: SlideshowOptions = SlideshowOptions(),
    val settingsTransition: TransitionStyle = TransitionStyle.FADE,
    val timeMode: PlaybackTimeMode = PlaybackTimeMode.ELAPSED,
    val spectrum: SpectrumOptions = SpectrumOptions(),
    val controlBlocks: Boolean = true,
    val blockOutline: BlockOutline = BlockOutline(),
    val navigationPosition: NavigationPosition = NavigationPosition.BOTTOM,
    val mainTimeMode: PlaybackTimeMode = PlaybackTimeMode.ELAPSED,
    val lyricsDragSeek: Boolean = false,
    val lyricsSmoothScroll: Boolean = true,
    val lyricAppearance: LyricAppearance = LyricAppearance(),
    val landscapeLyrics: LyricSide = LyricSide.START,
    val portraitLyrics: LyricSide = LyricSide.START,
    val themeAccent: ThemeAccent = ThemeAccent.DEFAULT,
    val customAccentColor: Int? = null,
    val widgetTransparency: Int = 50,
    val internalLyricExpiry: LyricCacheExpiry = LyricCacheExpiry.THIRTY_DAYS,
    val portableLyricExpiry: LyricCacheExpiry = LyricCacheExpiry.NEVER,
) {
    init {
        require(customAccentColor == null || customAccentColor in 0..0xffffff)
        require(widgetTransparency in 0..100)
    }

    val backgroundImages: List<String> get() = if (imageVersion == 0L) emptyList()
        else imageIds.ifEmpty { listOf(BackgroundImages.LEGACY) }

    fun normalized(): DisplayOptions = if (blockOutline.style == BlockStyle.NONE)
        copy(controlBlocks = false, blockOutline = blockOutline.copy(style = BlockStyle.SOLID)) else this

}

class DisplaySettings(private val context: Context, private val themeChanged: () -> Unit) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val interval = preferences.getInt("backgroundIntervalSeconds", 5).coerceIn(1, 120)
    private val mutableOptions = MutableStateFlow(DisplayOptions(
        lyricsEnabled = preferences.getBoolean("lyricsEnabled", true),
        onlineLyrics = preferences.getBoolean("onlineLyrics", true),
        customTheme = preferences.getBoolean("customTheme", false),
        imageVersion = preferences.getLong("backgroundVersion", 0),
        blur = preferences.getInt("backgroundBlur", 0).coerceIn(0, 100),
        tint = BackgroundTint.entries.firstOrNull { it.name == preferences.getString("backgroundTint", null) }
            ?: BackgroundTint.DARK,
        tintStrength = preferences.getInt("backgroundTintStrength", 35).coerceIn(0, 100),
        imageIds = readImages(),
        slideshow = SlideshowOptions(
            intervalSeconds = interval,
            durationMs = preferences.getInt("backgroundDurationMs", 2_000).coerceIn(0, minOf(10_000, interval * 1_000)),
            transition = choice("backgroundTransition", TransitionStyle.FADE),
        ),
        settingsTransition = choice("settingsTransition", TransitionStyle.FADE),
        timeMode = choice("playbackTimeMode", PlaybackTimeMode.REMAINING),
        controlBlocks = preferences.getBoolean("controlBlocks", true),
        blockOutline = BlockOutline(choice("blockStyle", BlockStyle.SOLID), readBlockCharacter(),
            preferences.getInt("blockArcDp", 18).coerceIn(0, 48), preferences.getBoolean("blockShadow", false)),
        navigationPosition = choice("navigationPosition", NavigationPosition.BOTTOM),
        mainTimeMode = choice("mainTimeMode", PlaybackTimeMode.REMAINING),
        widgetTransparency = preferences.getInt("widgetTransparency", 50).coerceIn(0, 100),
        internalLyricExpiry = choice("internalLyricExpiry", LyricCacheExpiry.THIRTY_DAYS),
        portableLyricExpiry = choice("portableLyricExpiry", LyricCacheExpiry.NEVER),
        lyricsDragSeek = preferences.getBoolean("lyricsDragSeek", false),
        lyricsSmoothScroll = preferences.getBoolean("lyricsSmoothScroll", true),
        lyricAppearance = LyricAppearance(
            activeSizeSp = preferences.getInt("lyricActiveSizeSp",
                20 + preferences.getInt("lyricEnlargementPercent", 20).coerceIn(0, 60) / 5).coerceIn(12, 48),
            otherSizeSp = preferences.getInt("lyricOtherSizeSp", 20).coerceIn(12, 48),
            activeColor = optionalColor("lyricActiveColor"),
            otherColor = optionalColor("lyricOtherColor"),
        ),
        landscapeLyrics = choice("landscapeLyrics", LyricSide.START),
        portraitLyrics = choice("portraitLyrics", LyricSide.START),
        themeAccent = choice("themeAccent", ThemeAccent.DEFAULT),
        customAccentColor = optionalColor("customAccentColor"),
        spectrum = SpectrumOptions(
            enabled = preferences.getBoolean("spectrumEnabled", false),
            position = choice("spectrumPosition", SpectrumPosition.RIGHT),
            origin = choice("spectrumOrigin", SpectrumOrigin.BOTTOM),
            style = choice("spectrumStyle", SpectrumStyle.BARS),
            palette = choice("spectrumPalette", SpectrumPalette.CLASSIC),
            bands = preferences.getInt("spectrumBands", 32).takeIf { it in SpectrumOptions.BAND_COUNTS } ?: 32,
            peaks = preferences.getBoolean("spectrumPeaks", true),
            falloff = choice("spectrumFalloff", SpectrumDecay.MEDIUM),
            peakFalloff = choice("spectrumPeakFalloff", SpectrumDecay.SLOW),
            refreshRate = preferences.getInt("spectrumRefreshRate", SpectrumOptions.DEFAULT_REFRESH_RATE)
                .takeIf { it in setOf(15, 30, 60) } ?: SpectrumOptions.DEFAULT_REFRESH_RATE,
            fireStyle = choice("spectrumFireStyle", FireStyle.LED),
            barStyle = choice("spectrumBarStyle", FireStyle.SMOOTH),
            colors = colors("fire", SpectrumColors.FIRE),
        ),
    ).normalized())
    val options = mutableOptions.asStateFlow()

    init {
        if (preferences.getString("blockStyle", null) == BlockStyle.NONE.name) write(mutableOptions.value).apply()
    }

    @Synchronized fun update(edit: (DisplayOptions) -> DisplayOptions) {
        val old = mutableOptions.value
        val next = edit(old).normalized()
        require(next.blur in 0..100 && next.tintStrength in 0..100 && next.imageVersion >= 0)
        require(next.imageIds.size <= BackgroundImages.MAX_COUNT &&
            next.imageIds.distinct() == next.imageIds && next.imageIds.all(BackgroundImages::isImportedName))
        if (next == old) return
        write(next).apply()
        mutableOptions.value = next
        if (old.customTheme != next.customTheme || old.tint != next.tint) themeChanged()
    }

    suspend fun installImages(ids: List<String>) = withContext(Dispatchers.IO) {
        require(ids.isNotEmpty() && ids.size <= BackgroundImages.MAX_COUNT &&
            ids.distinct() == ids && ids.all(BackgroundImages::isImportedName))
        val changedTheme = synchronized(this@DisplaySettings) {
            val old = mutableOptions.value
            val next = old.copy(imageIds = ids.toList(), customTheme = true,
                imageVersion = maxOf(System.currentTimeMillis(), old.imageVersion + 1))
            // Commit the new gallery before BackgroundRepository removes any previous private copies.
            if (!write(next).commit()) {
                write(old).apply()
                throw IOException(context.getString(R.string.background_save_failed))
            }
            mutableOptions.value = next
            !old.customTheme
        }
        if (changedTheme) withContext(Dispatchers.Main.immediate) { themeChanged() }
    }

    internal fun publishRestored(next: DisplayOptions) {
        mutableOptions.value = next.normalized()
        themeChanged()
    }

    internal fun write(options: DisplayOptions): SharedPreferences.Editor {
        val next = options.normalized()
        return preferences.edit()
            .remove("lightMode")
            .putInt("widgetTransparency", next.widgetTransparency)
            .putString("internalLyricExpiry", next.internalLyricExpiry.name)
            .putString("portableLyricExpiry", next.portableLyricExpiry.name)
            .putBoolean("lyricsEnabled", next.lyricsEnabled)
            .putBoolean("onlineLyrics", next.onlineLyrics)
            .putBoolean("customTheme", next.customTheme)
            .putLong("backgroundVersion", next.imageVersion)
            .putInt("backgroundBlur", next.blur)
            .putString("backgroundTint", next.tint.name)
            .putInt("backgroundTintStrength", next.tintStrength)
            .putString("backgroundImages", Json.encodeToString(next.imageIds))
            .putInt("backgroundIntervalSeconds", next.slideshow.intervalSeconds)
            .putInt("backgroundDurationMs", next.slideshow.durationMs)
            .putString("backgroundTransition", next.slideshow.transition.name)
            .putString("settingsTransition", next.settingsTransition.name)
            .putString("playbackTimeMode", next.timeMode.name)
            .putBoolean("controlBlocks", next.controlBlocks)
            .putString("blockStyle", next.blockOutline.style.name)
            .putString("blockCharacter", next.blockOutline.character)
            .putInt("blockArcDp", next.blockOutline.arcDp)
            .putBoolean("blockShadow", next.blockOutline.shadow)
            .putString("navigationPosition", next.navigationPosition.name)
            .putString("mainTimeMode", next.mainTimeMode.name)
            .putBoolean("lyricsDragSeek", next.lyricsDragSeek)
            .putBoolean("lyricsSmoothScroll", next.lyricsSmoothScroll)
            .putInt("lyricActiveSizeSp", next.lyricAppearance.activeSizeSp)
            .putInt("lyricOtherSizeSp", next.lyricAppearance.otherSizeSp)
            .putInt("lyricActiveColor", next.lyricAppearance.activeColor ?: -1)
            .putInt("lyricOtherColor", next.lyricAppearance.otherColor ?: -1)
            .putString("landscapeLyrics", next.landscapeLyrics.name)
            .putString("portraitLyrics", next.portraitLyrics.name)
            .putString("themeAccent", next.themeAccent.name)
            .putInt("customAccentColor", next.customAccentColor ?: -1)
            .putBoolean("spectrumEnabled", next.spectrum.enabled)
            .putString("spectrumPosition", next.spectrum.position.name)
            .putString("spectrumOrigin", next.spectrum.origin.name)
            .putString("spectrumStyle", next.spectrum.style.name)
            .putString("spectrumPalette", next.spectrum.palette.name)
            .putInt("spectrumBands", next.spectrum.bands)
            .putBoolean("spectrumPeaks", next.spectrum.peaks)
            .putString("spectrumFalloff", next.spectrum.falloff.name)
            .putString("spectrumPeakFalloff", next.spectrum.peakFalloff.name)
            .putInt("spectrumRefreshRate", next.spectrum.refreshRate)
            .putString("spectrumFireStyle", next.spectrum.fireStyle.name)
            .putString("spectrumBarStyle", next.spectrum.barStyle.name)
            .remove("classicColorLow")
            .remove("classicColorMiddle")
            .remove("classicColorHigh")
            .putInt("fireColorLow", next.spectrum.colors.low)
            .putInt("fireColorMiddle", next.spectrum.colors.middle)
            .putInt("fireColorHigh", next.spectrum.colors.high)
    }

    private inline fun <reified T : Enum<T>> choice(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == preferences.getString(key, null) } ?: fallback

    private fun optionalColor(key: String): Int? = preferences.getInt(key, -1).takeIf { it in 0..0xffffff }

    private fun colors(prefix: String, defaults: SpectrumColors) = SpectrumColors(
        preferences.getInt("${prefix}ColorLow", defaults.low) and 0xffffff,
        preferences.getInt("${prefix}ColorMiddle", defaults.middle) and 0xffffff,
        preferences.getInt("${prefix}ColorHigh", defaults.high) and 0xffffff,
    )

    private fun readBlockCharacter(): String {
        val value = preferences.getString("blockCharacter", "*").orEmpty()
        if (BlockOutline.isValidCharacter(value)) return value
        Log.w("DisplaySettings", "Saved border character is invalid; restoring the default")
        return "*"
    }

    private fun readImages(): List<String> {
        val raw = preferences.getString("backgroundImages", null) ?: return emptyList()
        return try {
            val images = Json.decodeFromString<List<String>>(raw)
            require(images.size <= BackgroundImages.MAX_COUNT && images.distinct() == images &&
                images.all(BackgroundImages::isImportedName))
            images
        } catch (error: SerializationException) {
            Log.w("DisplaySettings", "Saved background gallery is malformed", error)
            emptyList()
        } catch (error: IllegalArgumentException) {
            Log.w("DisplaySettings", "Saved background gallery contains invalid names", error)
            emptyList()
        }
    }

    companion object { const val CUSTOM_THEME = 100 }
}
