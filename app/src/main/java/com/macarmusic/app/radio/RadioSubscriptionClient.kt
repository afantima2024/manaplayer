package com.macarmusic.app.radio

import com.macarmusic.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class RadioSubscriptionClient(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val clock: () -> Long = System::nanoTime,
) {
    suspend fun fetch(subscription: RadioSubscription): RadioSubscriptionDownload = withContext(Dispatchers.IO) {
        var target = URL(subscription.url)
        val start = clock()
        val coroutine = currentCoroutineContext()
        fun checkActive() {
            coroutine.ensureActive()
            if (clock() - start > TimeUnit.SECONDS.toNanos(25))
                throw IOException("Radio subscription download timed out")
        }
        try {
            repeat(6) {
                checkActive()
                if (RadioAddress.parse(target.toString()) == null) fail(RadioSubscriptionProblem.INVALID_LIST)
                val connection = connect(target)
                try {
                    connection.apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        instanceFollowRedirects = false
                        useCaches = false
                        setRequestProperty("User-Agent", "ManaMusicRadio")
                        setRequestProperty("Accept", "audio/x-mpegurl,application/x-mpegurl,text/plain,*/*")
                        setRequestProperty("Accept-Encoding", "identity")
                        if (subscription.programs.isNotEmpty() && subscription.validatorUrl == target.toString()) {
                            if (subscription.etag.isNotEmpty()) setRequestProperty("If-None-Match", subscription.etag)
                            if (subscription.lastModified.isNotEmpty()) setRequestProperty("If-Modified-Since", subscription.lastModified)
                        }
                    }
                    val status = connection.responseCode
                    checkActive()
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        val next = URL(target, connection.getHeaderField("Location")
                            ?: fail(RadioSubscriptionProblem.INVALID_LIST))
                        if (target.protocol == "https" && next.protocol != "https")
                            fail(RadioSubscriptionProblem.INSECURE_REDIRECT)
                        target = next
                    } else {
                        fun header(name: String) = connection.getHeaderField(name).orEmpty()
                            .takeIf(RadioSubscriptions::validValidator).orEmpty()
                        if (status == 304) {
                            if (subscription.programs.isEmpty() || subscription.validatorUrl != target.toString() ||
                                subscription.etag.isEmpty() && subscription.lastModified.isEmpty())
                                fail(RadioSubscriptionProblem.INVALID_LIST)
                            return@withContext RadioSubscriptionDownload(etag = header("ETag").ifEmpty { subscription.etag },
                                lastModified = header("Last-Modified").ifEmpty { subscription.lastModified },
                                validatorUrl = target.toString(), unavailableStations = subscription.unavailableStations,
                                ignoredLogos = subscription.ignoredLogos)
                        }
                        if (status != 200) throw RadioSubscriptionException(
                            RadioSubscriptionFailure(RadioSubscriptionProblem.HTTP, status.coerceIn(0, 599)))
                        if (connection.contentLength > RadioList.MAX_BYTES) fail(RadioSubscriptionProblem.LIMIT)
                        if (connection.contentType.orEmpty().substringBefore(';').trim()
                                .equals("text/html", ignoreCase = true)) fail(RadioSubscriptionProblem.INVALID_LIST)
                        val parsed = connection.inputStream.use {
                            RadioSubscriptions.parsePlaylist(subscription.url, it, checkCancelled = ::checkActive)
                        }
                        if (parsed.entries.isEmpty() || parsed.invalid != 0) fail(RadioSubscriptionProblem.INVALID_LIST)
                        checkActive()
                        return@withContext RadioSubscriptionDownload(parsed.entries, header("ETag"),
                            header("Last-Modified"), target.toString(), parsed.unavailableStations, parsed.ignoredLogos)
                    }
                } finally { connection.disconnect() }
            }
            fail(RadioSubscriptionProblem.INVALID_LIST)
        } catch (error: RadioSubscriptionException) {
            throw error
        } catch (error: RadioListException) {
            throw RadioSubscriptionException(RadioSubscriptionFailure(
                if (error.messageId in listOf(R.string.radio_list_too_large, R.string.radio_list_too_many))
                    RadioSubscriptionProblem.LIMIT else RadioSubscriptionProblem.INVALID_LIST), error)
        } catch (error: IOException) {
            coroutine.ensureActive()
            throw RadioSubscriptionException(RadioSubscriptionFailure(RadioSubscriptionProblem.NETWORK), error)
        }
    }

    private fun fail(problem: RadioSubscriptionProblem): Nothing =
        throw RadioSubscriptionException(RadioSubscriptionFailure(problem))
}
