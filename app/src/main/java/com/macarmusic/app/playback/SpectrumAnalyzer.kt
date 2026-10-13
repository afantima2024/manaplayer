package com.macarmusic.app.playback

import com.macarmusic.app.model.SpectrumOptions
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

class SpectrumAnalyzer {
    private val window = DoubleArray(SAMPLE_COUNT) { 0.5 - 0.5 * cos(2 * PI * it / SAMPLE_COUNT) }
    private val reverse = IntArray(SAMPLE_COUNT) { Integer.reverse(it) ushr (32 - 11) }
    private val cosine = DoubleArray(SAMPLE_COUNT / 2) { cos(-2 * PI * it / SAMPLE_COUNT) }
    private val sine = DoubleArray(SAMPLE_COUNT / 2) { sin(-2 * PI * it / SAMPLE_COUNT) }
    private val real = DoubleArray(SAMPLE_COUNT)
    private val imaginary = DoubleArray(SAMPLE_COUNT)
    private val power = DoubleArray(SAMPLE_COUNT / 2 + 1)
    private val amplitudeScale = 2.0 / window.sum()

    fun analyze(channels: Array<FloatArray>, sampleRate: Int): FloatArray {
        require(sampleRate > 0 && channels.size in 1..8 && channels.all { it.size == SAMPLE_COUNT })
        power.fill(0.0)
        for (samples in channels) {
            val mean = samples.sumOf { if (it.isFinite()) it.toDouble() else 0.0 } / SAMPLE_COUNT
            for (index in samples.indices) {
                real[reverse[index]] = ((samples[index].takeIf(Float::isFinite) ?: 0f) - mean) * window[index]
                imaginary[reverse[index]] = 0.0
            }
            var size = 2
            while (size <= SAMPLE_COUNT) {
                val half = size / 2
                val step = SAMPLE_COUNT / size
                for (start in 0 until SAMPLE_COUNT step size) {
                    for (offset in 0 until half) {
                        val odd = start + offset + half
                        val even = start + offset
                        val c = cosine[offset * step]
                        val s = sine[offset * step]
                        val x = c * real[odd] - s * imaginary[odd]
                        val y = s * real[odd] + c * imaginary[odd]
                        real[odd] = real[even] - x
                        imaginary[odd] = imaginary[even] - y
                        real[even] += x
                        imaginary[even] += y
                    }
                }
                size *= 2
            }
            // Analyze channels separately so opposite-phase stereo cannot cancel the spectrum.
            for (bin in power.indices) {
                power[bin] = max(power[bin],
                    (real[bin] * real[bin] + imaginary[bin] * imaginary[bin]) * amplitudeScale * amplitudeScale)
            }
        }
        val upper = minOf(20_000.0, sampleRate / 2.0)
        if (upper <= 20) return FloatArray(BAND_COUNT)
        return FloatArray(BAND_COUNT) { band ->
            val low = 20.0 * (upper / 20.0).pow(band.toDouble() / BAND_COUNT)
            val high = 20.0 * (upper / 20.0).pow((band + 1.0) / BAND_COUNT)
            val first = ceil(low * SAMPLE_COUNT / sampleRate).toInt().coerceIn(1, power.lastIndex)
            val end = max(first + 1, ceil(high * SAMPLE_COUNT / sampleRate).toInt()).coerceAtMost(power.size)
            var peak = 0.0
            for (bin in first until end) peak = max(peak, power[bin])
            ((10 * log10(max(peak, 1e-12)) + 60) / 60).toFloat().coerceIn(0f, 1f)
        }
    }

    companion object {
        const val SAMPLE_COUNT = 2_048
        const val BAND_COUNT = 32
    }
}

class SpectrumEnvelope {
    val bars = FloatArray(SpectrumAnalyzer.BAND_COUNT)
    val peaks = FloatArray(SpectrumAnalyzer.BAND_COUNT)
    private val hold = FloatArray(SpectrumAnalyzer.BAND_COUNT)

    fun update(levels: FloatArray, seconds: Float, options: SpectrumOptions) {
        require(levels.size == bars.size && seconds.isFinite() && seconds >= 0)
        for (index in bars.indices) {
            val level = (levels[index].takeIf(Float::isFinite) ?: 0f).coerceIn(0f, 1f)
            bars[index] = max(level, bars[index] - options.falloff.perSecond * seconds)
            if (bars[index] >= peaks[index]) {
                peaks[index] = bars[index]
                hold[index] = 0.2f
            } else {
                val falling = (seconds - hold[index]).coerceAtLeast(0f)
                hold[index] = (hold[index] - seconds).coerceAtLeast(0f)
                peaks[index] = max(bars[index], peaks[index] - options.peakFalloff.perSecond * falling)
            }
        }
    }

    fun reset() {
        bars.fill(0f)
        peaks.fill(0f)
        hold.fill(0f)
    }
}
