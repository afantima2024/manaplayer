package com.macarmusic.app.model

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

enum class TransitionStyle { FADE, SLIDE, ZOOM, NONE }
enum class PlaybackTimeMode { ELAPSED, REMAINING }
enum class NavigationPosition { BOTTOM, LEFT, RIGHT, TOP }
enum class SpectrumPosition { LEFT, RIGHT }
enum class SpectrumOrigin {
    BOTTOM, LEFT, RIGHT, TOP;

    fun clockwise(): SpectrumOrigin = when (this) {
        BOTTOM -> LEFT
        LEFT -> TOP
        TOP -> RIGHT
        RIGHT -> BOTTOM
    }
}
enum class SpectrumStyle { BARS, LINE, OFF }
enum class SpectrumPalette { CLASSIC, FIRE, THEME }
enum class FireStyle { LED, SMOOTH }
enum class BlockStyle {
    SOLID, DASHED, ARC, CHARACTERS, DASHED_ARC,
    // Retained only to read earlier preferences and local backups.
    NONE;

    companion object { val selectable = listOf(SOLID, ARC, DASHED, DASHED_ARC, CHARACTERS) }
}
enum class LyricSide { START, END }
enum class ThemeAccent { DEFAULT, BLUE, INDIGO, PURPLE, PINK, RED, ORANGE, GREEN, TEAL }
enum class SpectrumDecay(val perSecond: Float) { SLOW(0.35f), MEDIUM(0.8f), FAST(1.8f) }

object ColorHex {
    fun parse(value: String): Int? {
        val digits = value.trim().removePrefix("#")
        return if (digits.length == 6 && digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
            digits.toInt(16) else null
    }

    fun format(value: Int): String {
        require(value in 0..0xffffff)
        return String.format(java.util.Locale.ROOT, "#%06X", value)
    }
}

@Serializable
data class BlockOutline(
    val style: BlockStyle = BlockStyle.SOLID,
    val character: String = "*",
    val arcDp: Int = 18,
    val shadow: Boolean = false,
) {
    init {
        require(isValidCharacter(character))
        require(arcDp in 0..48)
    }

    val hasBorder: Boolean get() = style != BlockStyle.NONE
    val hasArc: Boolean get() = style == BlockStyle.ARC || style == BlockStyle.DASHED_ARC

    companion object {
        fun isValidCharacter(value: String): Boolean {
            if (value.codePointCount(0, value.length) != 1) return false
            val code = value.codePointAt(0)
            return !Character.isISOControl(code) && !Character.isWhitespace(code) &&
                !Character.isSpaceChar(code) && Character.getType(code) != Character.FORMAT.toInt() &&
                code !in 0xd800..0xdfff
        }
    }
}

@Serializable
data class LyricAppearance(
    val activeSizeSp: Int = 24,
    val otherSizeSp: Int = 20,
    val activeColor: Int? = null,
    val otherColor: Int? = null,
    val karaoke: Boolean = false,
    val karaokeColor: Int = DEFAULT_KARAOKE_COLOR,
) {
    init {
        require(activeSizeSp in 12..48 && otherSizeSp in 12..48)
        require(activeColor == null || activeColor in 0..0xffffff)
        require(otherColor == null || otherColor in 0..0xffffff)
        require(karaokeColor in 0..0xffffff)
    }

    companion object {
        const val DEFAULT_KARAOKE_COLOR = 0xffd54f
    }
}

@Serializable
data class SpectrumColors(val low: Int, val middle: Int, val high: Int) {
    init { require(listOf(low, middle, high).all { it in 0..0xffffff }) }

    fun colorAt(height: Float): Int {
        require(height.isFinite())
        val position = height.coerceIn(0f, 1f)
        val start = if (position < 0.5f) low else middle
        val end = if (position < 0.5f) middle else high
        val fraction = if (position < 0.5f) position * 2 else (position - 0.5f) * 2
        fun channel(shift: Int): Int {
            val from = (start shr shift) and 0xff
            val to = (end shr shift) and 0xff
            return (from + (to - from) * fraction).roundToInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    companion object {
        val CLASSIC = SpectrumColors(0x3dcf74, 0xfcd34d, 0xf04c44)
        val FIRE = SpectrumColors(0xffdf69, 0xff9828, 0xed4529)
    }
}

@Serializable
data class SlideshowOptions(
    val intervalSeconds: Int = 5,
    val durationMs: Int = 2_000,
    val transition: TransitionStyle = TransitionStyle.FADE,
) {
    init {
        require(intervalSeconds in 1..120)
        require(durationMs in 0..minOf(10_000, intervalSeconds * 1_000))
    }

    val animationMs: Long get() = if (transition == TransitionStyle.NONE) 0 else durationMs.toLong()
    val intervalMs: Long get() = intervalSeconds * 1_000L

    fun withInterval(seconds: Int) = copy(
        intervalSeconds = seconds,
        durationMs = durationMs.coerceAtMost(seconds * 1_000),
    )
}

@Serializable
data class SpectrumOptions(
    val enabled: Boolean = false,
    val position: SpectrumPosition = SpectrumPosition.RIGHT,
    val style: SpectrumStyle = SpectrumStyle.BARS,
    val palette: SpectrumPalette = SpectrumPalette.CLASSIC,
    val bands: Int = 32,
    val peaks: Boolean = true,
    val falloff: SpectrumDecay = SpectrumDecay.MEDIUM,
    val peakFalloff: SpectrumDecay = SpectrumDecay.SLOW,
    val refreshRate: Int = DEFAULT_REFRESH_RATE,
    val fireStyle: FireStyle = FireStyle.LED,
    @SerialName("fireColors") val colors: SpectrumColors = SpectrumColors.FIRE,
    val barStyle: FireStyle = FireStyle.SMOOTH,
    val origin: SpectrumOrigin = SpectrumOrigin.BOTTOM,
) {
    init {
        require(bands in BAND_COUNTS)
        require(refreshRate in setOf(15, 30, 60))
    }

    val rendering: FireStyle get() = if (palette == SpectrumPalette.FIRE) fireStyle else barStyle

    fun withRendering(value: FireStyle) =
        if (palette == SpectrumPalette.FIRE) copy(fireStyle = value) else copy(barStyle = value)

    fun nextMode(): SpectrumOptions = when {
        style == SpectrumStyle.OFF -> copy(style = SpectrumStyle.LINE, palette = SpectrumPalette.CLASSIC)
        style == SpectrumStyle.LINE -> copy(style = SpectrumStyle.BARS,
            palette = SpectrumPalette.CLASSIC, barStyle = FireStyle.SMOOTH)
        palette == SpectrumPalette.CLASSIC && barStyle == FireStyle.SMOOTH -> copy(barStyle = FireStyle.LED)
        palette == SpectrumPalette.CLASSIC -> copy(palette = SpectrumPalette.FIRE, fireStyle = FireStyle.SMOOTH)
        palette == SpectrumPalette.FIRE && fireStyle == FireStyle.SMOOTH -> copy(fireStyle = FireStyle.LED)
        palette == SpectrumPalette.FIRE -> copy(style = SpectrumStyle.OFF)
        else -> copy(style = SpectrumStyle.LINE, palette = SpectrumPalette.CLASSIC)
    }

    companion object {
        const val DEFAULT_REFRESH_RATE = 15
        val BAND_COUNTS = listOf(3, 8, 16, 32)
    }
}

object BackgroundImages {
    const val MAX_COUNT = 20
    const val LEGACY = "background.png"
    private val namePattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png")

    fun isImportedName(name: String) = namePattern.matches(name)
}
