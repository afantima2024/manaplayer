package com.macarmusic.app.model

import kotlin.math.roundToInt

enum class TransitionStyle { FADE, SLIDE, ZOOM, NONE }
enum class PlaybackTimeMode { ELAPSED, REMAINING }
enum class NavigationPosition { BOTTOM, LEFT, RIGHT, TOP }
enum class SpectrumPosition { LEFT, RIGHT }
enum class SpectrumStyle { BARS, LINE }
enum class SpectrumPalette { CLASSIC, FIRE, THEME }
enum class FireStyle { LED, SMOOTH }
enum class BlockStyle { SOLID, DASHED, ARC, CHARACTERS }
enum class SpectrumDecay(val perSecond: Float) { SLOW(0.35f), MEDIUM(0.8f), FAST(1.8f) }

data class BlockOutline(val style: BlockStyle = BlockStyle.SOLID, val character: String = "*") {
    init { require(isValidCharacter(character)) }

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

data class SpectrumOptions(
    val enabled: Boolean = false,
    val position: SpectrumPosition = SpectrumPosition.RIGHT,
    val style: SpectrumStyle = SpectrumStyle.BARS,
    val palette: SpectrumPalette = SpectrumPalette.CLASSIC,
    val bands: Int = 32,
    val peaks: Boolean = true,
    val falloff: SpectrumDecay = SpectrumDecay.MEDIUM,
    val peakFalloff: SpectrumDecay = SpectrumDecay.SLOW,
    val refreshRate: Int = 30,
    val fireStyle: FireStyle = FireStyle.LED,
    val classicColors: SpectrumColors = SpectrumColors.CLASSIC,
    val fireColors: SpectrumColors = SpectrumColors.FIRE,
) {
    init {
        require(bands in setOf(16, 32))
        require(refreshRate in setOf(15, 30, 60))
    }
}

object BackgroundImages {
    const val MAX_COUNT = 20
    const val LEGACY = "background.png"
    private val namePattern = Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png")

    fun isImportedName(name: String) = namePattern.matches(name)
}
