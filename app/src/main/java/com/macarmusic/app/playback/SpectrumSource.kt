package com.macarmusic.app.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.macarmusic.app.model.SpectrumOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

data class SpectrumFrame(
    val levels: FloatArray = FloatArray(SpectrumAnalyzer.BAND_COUNT),
    val hasAudio: Boolean = false,
    val sampleRate: Int = 0,
)

@androidx.annotation.OptIn(UnstableApi::class)
class SpectrumSource(scope: CoroutineScope) {
    private data class Window(val channels: Array<FloatArray>, val rate: Int, val generation: Long)
    private val pending = Channel<Window>(Channel.CONFLATED)
    private val generation = AtomicLong()
    private val visibleClients = mutableSetOf<Any>()
    private var playing = false
    @Volatile private var capturing = false
    @Volatile private var analysisIntervalNs = 1_000_000_000L / SpectrumOptions.DEFAULT_REFRESH_RATE
    private val mutableFrames = MutableStateFlow(SpectrumFrame())
    val frames = mutableFrames.asStateFlow()

    init {
        scope.launch(Dispatchers.Default) {
            val analyzer = SpectrumAnalyzer()
            for (window in pending) {
                if (!capturing || window.generation != generation.get()) continue
                val levels = analyzer.analyze(window.channels, window.rate)
                synchronized(this@SpectrumSource) {
                    if (capturing && window.generation == generation.get()) {
                        mutableFrames.value = SpectrumFrame(levels, true, window.rate)
                    }
                }
            }
        }
    }

    @Synchronized fun setVisible(owner: Any, visible: Boolean) {
        if (visible) visibleClients.add(owner) else visibleClients.remove(owner)
        updateCapture()
    }

    @Synchronized fun setPlaying(value: Boolean) {
        playing = value
        updateCapture()
    }

    fun setRefreshRate(rate: Int) {
        require(rate in setOf(15, 30, 60))
        analysisIntervalNs = 1_000_000_000L / rate
    }

    private fun updateCapture() {
        val next = playing && visibleClients.isNotEmpty()
        if (capturing != next) {
            capturing = next
            reset()
        }
    }

    @Synchronized fun reset() {
        generation.incrementAndGet()
        mutableFrames.value = SpectrumFrame()
    }

    fun audioProcessor(): AudioProcessor = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
        private var sampleRate = 0
        private var encoding = C.ENCODING_INVALID
        private var rings = emptyArray<FloatArray>()
        private var cursor = 0
        private var filled = 0
        private var lastSent = 0L
        private var previousGeneration = -1L

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            sampleRate = sampleRateHz
            this.encoding = encoding
            rings = if (sampleRateHz > 0 && channelCount in 1..8 &&
                encoding in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)
            ) Array(channelCount) { FloatArray(SpectrumAnalyzer.SAMPLE_COUNT) } else {
                Log.w("SpectrumSource", "Spectrum unavailable for PCM format $sampleRateHz/$channelCount/$encoding")
                emptyArray()
            }
            previousGeneration = -1L
            reset()
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            if (!capturing || rings.isEmpty()) return
            val revision = generation.get()
            if (revision != previousGeneration) {
                cursor = 0
                filled = 0
                lastSent = 0
                previousGeneration = revision
            }
            val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            while (buffer.remaining() >= bytesPerSample * rings.size) {
                for (channel in rings.indices) {
                    val sample = if (encoding == C.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f
                    rings[channel][cursor] = (sample.takeIf(Float::isFinite) ?: 0f).coerceIn(-1f, 1f)
                }
                cursor = (cursor + 1) % SpectrumAnalyzer.SAMPLE_COUNT
                filled = (filled + 1).coerceAtMost(SpectrumAnalyzer.SAMPLE_COUNT)
            }
            val now = System.nanoTime()
            if (filled < SpectrumAnalyzer.SAMPLE_COUNT || now - lastSent < analysisIntervalNs) return
            val snapshot = Array(rings.size) { channel ->
                FloatArray(SpectrumAnalyzer.SAMPLE_COUNT) { rings[channel][(cursor + it) % SpectrumAnalyzer.SAMPLE_COUNT] }
            }
            // Never wait for FFT/UI work on ExoPlayer's audio thread.
            pending.trySend(Window(snapshot, sampleRate, revision))
            lastSent = now
        }
    })
}
