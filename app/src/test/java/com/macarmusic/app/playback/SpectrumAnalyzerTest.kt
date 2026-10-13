package com.macarmusic.app.playback

import com.macarmusic.app.model.SpectrumDecay
import com.macarmusic.app.model.SpectrumOptions
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sin

class SpectrumAnalyzerTest {
    private fun tone(frequency: Double, amplitude: Float = 1f, rate: Int = 48_000) =
        FloatArray(SpectrumAnalyzer.SAMPLE_COUNT) { (sin(2 * PI * frequency * it / rate) * amplitude).toFloat() }

    private fun band(frequency: Double, rate: Int = 48_000) =
        (ln(frequency / 20) / ln(minOf(20_000.0, rate / 2.0) / 20) * SpectrumAnalyzer.BAND_COUNT).toInt()

    @Test fun silenceAndDcDoNotInventSpectrumData() {
        val analyzer = SpectrumAnalyzer()
        assertArrayEquals(FloatArray(32), analyzer.analyze(arrayOf(FloatArray(2_048)), 48_000), 0f)
        assertArrayEquals(FloatArray(32), analyzer.analyze(arrayOf(FloatArray(2_048) { 0.5f }), 48_000), 0f)
    }

    @Test fun knownTonePeaksInTheCorrectLogFrequencyBand() {
        val levels = SpectrumAnalyzer().analyze(arrayOf(tone(1_000.0)), 48_000)
        assertEquals(band(1_000.0), levels.indices.maxBy { levels[it] })
        assertTrue(levels[band(1_000.0)] > 0.9f)
        assertTrue(levels.all { it.isFinite() && it in 0f..1f })
    }

    @Test fun sampleRateDeterminesFrequencyMapping() {
        val levels = SpectrumAnalyzer().analyze(arrayOf(tone(440.0, rate = 22_050)), 22_050)
        assertEquals(band(440.0, 22_050), levels.indices.maxBy { levels[it] })
    }

    @Test fun opposingStereoChannelsCannotCancelTheVisualization() {
        val samples = tone(1_000.0)
        val analyzer = SpectrumAnalyzer()
        val mono = analyzer.analyze(arrayOf(samples), 48_000)
        val stereo = analyzer.analyze(arrayOf(samples, samples.map { -it }.toFloatArray()), 48_000)
        assertArrayEquals(mono, stereo, 0.00001f)
    }

    @Test fun bothStereoChannelsContributeIndependentFrequencies() {
        val levels = SpectrumAnalyzer().analyze(arrayOf(tone(220.0), tone(4_400.0)), 48_000)
        assertTrue(levels[band(220.0)] > 0.8f)
        assertTrue(levels[band(4_400.0)] > 0.8f)
    }

    @Test fun amplitudeIsMeasuredRatherThanRandomlyAnimated() {
        val analyzer = SpectrumAnalyzer()
        val loud = analyzer.analyze(arrayOf(tone(1_000.0)), 48_000).max()
        val quiet = analyzer.analyze(arrayOf(tone(1_000.0, 0.1f)), 48_000).max()
        assertEquals(20f / 60f, loud - quiet, 0.01f)
    }

    @Test fun nonFinitePcmCannotPoisonSubsequentFrames() {
        val analyzer = SpectrumAnalyzer()
        assertArrayEquals(FloatArray(32), analyzer.analyze(arrayOf(FloatArray(2_048) { Float.NaN }), 48_000), 0f)
        assertTrue(analyzer.analyze(arrayOf(tone(1_000.0)), 48_000).max() > 0.9f)
    }

    @Test fun barsAndPeaksUseIndependentTimeBasedFalloff() {
        val slow = SpectrumEnvelope()
        val fast = SpectrumEnvelope()
        val signal = FloatArray(32) { 1f }
        val silence = FloatArray(32)
        val options = SpectrumOptions(falloff = SpectrumDecay.SLOW, peakFalloff = SpectrumDecay.SLOW)
        slow.update(signal, 0f, options)
        fast.update(signal, 0f, options)
        slow.update(silence, 0.5f, options)
        fast.update(silence, 0.5f, options.copy(falloff = SpectrumDecay.FAST))
        assertTrue(slow.bars[0] > fast.bars[0])
        assertTrue(fast.peaks[0] > fast.bars[0])
        assertEquals(slow.peaks[0], fast.peaks[0], 0.001f)
        fast.reset()
        assertArrayEquals(silence, fast.bars, 0f)
        assertArrayEquals(silence, fast.peaks, 0f)
    }
}
