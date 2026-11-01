package com.macarmusic.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.HttpUtil
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.CnrStreams
import com.macarmusic.app.radio.RadioAddress
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.MaCarMusicApp
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerMediaSources(
    context: Context,
    private val local: MediaSource.Factory = ProgressiveMediaSource.Factory(DataSource.Factory { LocalDataSource(context) }),
    private val radioData: DataSource.Factory? = null,
) : MediaSource.Factory by local {
    private val app = context.applicationContext as MaCarMusicApp
    private val streams by lazy { CnrStreams() }
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        if (RadioProgram.isRadio(mediaItem.mediaId)) {
            if (!app.radio.options.value.enabled) throw IOException(app.text(com.macarmusic.app.R.string.radio_disabled))
            // Session callbacks create this non-parceled tag from a saved entry. Keep that snapshot
            // while constructing the queue; an import may publish a newer catalog on an IO thread.
            val station = mediaItem.localConfiguration?.tag as? RadioProgram
                ?: app.radioCatalog.find(mediaItem.mediaId) ?: throw IOException("Unknown saved radio station")
            if (station.id != mediaItem.mediaId || mediaItem.localConfiguration?.uri.toString() != station.streamUri)
                throw IOException("Unknown saved radio URL")
            val tuning = app.radio.options.value.tuning
            val errors = object : DefaultLoadErrorHandlingPolicy(tuning.retries) {
                override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                    val error = info.exception
                    return if (info.errorCount > tuning.retries ||
                        error is HttpDataSource.InvalidResponseCodeException && error.responseCode in 400..499)
                        C.TIME_UNSET else super.getRetryDelayMsFor(info)
                }
            }
            val factory = DataSource.Factory {
                (radioData?.createDataSource() ?: if (station.official) RadioDataSource(streams::resolve) else RadioDataSource(
                    resolve = { it },
                    allowed = { RadioAddress.permits(it, allowHttp = station.streamUri.startsWith("http:", true)) },
                )).also { it.addTransferListener(app.radioTraffic.listener(station)) }
            }
            val item = mediaItem.buildUpon().setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(tuning.bufferSeconds * 1000L).build()).build()
            return if (station.hls) HlsMediaSource.Factory(factory).setLoadErrorHandlingPolicy(errors).createMediaSource(item)
                else ProgressiveMediaSource.Factory(factory).setLoadErrorHandlingPolicy(errors).createMediaSource(mediaItem)
        }
        return local.createMediaSource(mediaItem)
    }

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class RadioDataSource(
    private val resolve: (String) -> String,
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val allowed: (String) -> Boolean = CnrRadio::allowedRequest,
) : BaseDataSource(true) {
    private var connection: HttpURLConnection? = null
    private var input: InputStream? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET ||
            !allowed(dataSpec.uri.toString())) throw IOException("Untrusted radio stream request")
        transferInitializing(dataSpec)
        var target = resolve(dataSpec.uri.toString())
        try {
            for (redirect in 0..5) {
                if (!allowed(target)) throw IOException("Untrusted radio stream address")
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                val http = connect(URL(target)).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = false
                    useCaches = false
                    setRequestProperty("User-Agent", "ManaMusicRadio")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Accept-Encoding", "identity")
                    HttpUtil.buildRangeRequestHeader(dataSpec.position, dataSpec.length)?.let {
                        setRequestProperty("Range", it)
                    }
                }
                connection = http
                val status = http.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = http.getHeaderField("Location") ?: throw IOException("Missing radio redirect address")
                    target = URL(http.url, location).toString()
                    if (http.url.protocol.equals("https", true) && !target.startsWith("https:", true))
                        throw IOException("Radio redirect cannot downgrade HTTPS")
                    http.disconnect()
                    connection = null
                    continue
                }
                if (status !in 200..299) throw HttpDataSource.InvalidResponseCodeException(
                    status, http.responseMessage, null, responseHeaders, dataSpec, byteArrayOf())
                var skip = if (status == 200) dataSpec.position else 0
                val length = http.getHeaderField("Content-Length")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
                remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
                    else if (length >= 0) length - skip else C.LENGTH_UNSET.toLong()
                if (length >= 0 && skip > length) throw EOFException("CNR stream range is unavailable")
                input = http.inputStream
                opened = true
                transferStarted(dataSpec)
                if (skip > 0) {
                    val buffer = ByteArray(4096)
                    while (skip > 0) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                        val count = checkNotNull(input).read(buffer, 0, minOf(skip, buffer.size.toLong()).toInt())
                        if (count < 0) throw EOFException("CNR stream ended before the requested range")
                        skip -= count
                        bytesTransferred(count)
                    }
                }
                return remaining
            }
            throw IOException("Too many CNR stream redirects")
        } catch (error: IOException) {
            try { close() } catch (closing: IOException) { error.addSuppressed(closing) }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
        val count = checkNotNull(input).read(buffer, offset,
            if (remaining < 0) length else minOf(length.toLong(), remaining).toInt())
        if (count < 0) {
            if (remaining > 0) throw EOFException("CNR stream response was truncated")
            return C.RESULT_END_OF_INPUT
        }
        if (remaining >= 0) remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = connection?.url?.toString()?.let(Uri::parse)
    override fun getResponseHeaders(): Map<String, List<String>> = connection?.headerFields?.entries
        ?.mapNotNull { (key, value) -> if (key == null || value == null) null else key to value }?.toMap().orEmpty()

    override fun close() {
        try { input?.close() } finally {
            input = null
            connection?.disconnect()
            connection = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
