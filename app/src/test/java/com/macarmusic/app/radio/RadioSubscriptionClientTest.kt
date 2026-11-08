package com.macarmusic.app.radio

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

class RadioSubscriptionClientTest {
    private val text = "#EXTM3U\n#EXTINF:-1 group-title=\"United States\",Fixture radio\nhttps://audio.example.com/live\n"
    private val subscription = RadioSubscription(UUID.randomUUID().toString(), "Test", "https://lists.example.com/a")
    private class Connection(url: URL, private val status: Int = 200,
        private val body: ByteArray = byteArrayOf(), private val headers: Map<String, String> = emptyMap(),
        private val length: Int = body.size, val stream: InputStream = ByteArrayInputStream(body)) : HttpURLConnection(url) {
        var disconnected = false
        var reads = 0
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLength() = length
        override fun getContentType() = getHeaderField("Content-Type")
        override fun getHeaderField(name: String?) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override fun getInputStream(): InputStream { reads++; return stream }
    }
    private fun connection(body: String = text, status: Int = 200, headers: Map<String, String> = emptyMap(),
        url: String = subscription.url) = Connection(URL(url), status, body.toByteArray(), headers)
    private fun cached() = subscription.copy(programs = RadioSubscriptions.programs(
        RadioList.parse(text.byteInputStream()).entries, emptyMap()),
        etag = "\"previous\"", lastModified = "Mon, 26 Oct 2026 00:00:00 GMT", validatorUrl = subscription.url)
    private fun rejected(expected: RadioSubscriptionProblem, connection: Connection, saved: RadioSubscription = subscription) = runBlocking {
        try { RadioSubscriptionClient({ connection }).fetch(saved); fail("Invalid response accepted") }
        catch (error: RadioSubscriptionException) { assertEquals(expected, error.failure.problem) }
        assertTrue(connection.disconnected)
    }

    @Test fun unsupportedBroadcastProtocolsAndInvalidLogosHaveExplicitCountsWithoutExecutingThem() = runBlocking {
        val fixture = text + "#EXTINF:-1,Unsupported\nrtmp://radio.example.com/live\n" +
            "#EXTINF:-1 tvg-logo=\"h\",Valid audio\nhttps://audio.example.com/another\n"
        var requests = 0
        val result = RadioSubscriptionClient({ requests++; connection(fixture) }).fetch(subscription)
        assertEquals(1, requests)
        assertEquals(2, result.entries!!.size)
        assertEquals(1, result.unavailableStations)
        assertEquals(1, result.ignoredLogos)
        assertEquals("", result.entries.last().logoUri)
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(text + "Invalid,javascript:bad"))
        assertEquals(2, RadioList.parse(fixture.byteInputStream()).invalid)
    }

    @Test fun downloadsOnlyThePlaylistWithBoundedRequestsAndReleasesTheResponse() = runBlocking {
        val response = connection(headers = mapOf("ETag" to "\"v1\"", "Last-Modified" to "Mon, 26 Oct 2026 00:00:00 GMT"))
        var requests = 0
        val result = RadioSubscriptionClient({ requests++; response }).fetch(subscription)
        assertEquals(1, requests)
        assertEquals(1, result.entries!!.size)
        assertEquals("https://audio.example.com/live", result.entries.single().uri)
        assertEquals("\"v1\"", result.etag)
        assertEquals(subscription.url, result.validatorUrl)
        assertEquals(8_000, response.connectTimeout)
        assertEquals(8_000, response.readTimeout)
        assertFalse(response.instanceFollowRedirects)
        assertFalse(response.useCaches)
        assertNull(response.getRequestProperty("If-None-Match"))
        assertTrue(response.disconnected)
    }

    @Test fun cached304UsesConditionalHeadersWithoutReadingAResponseBody() = runBlocking {
        val response = connection(status = 304)
        val cached = cached()
        val result = RadioSubscriptionClient({ response }).fetch(cached)
        assertEquals(cached.etag, response.getRequestProperty("If-None-Match"))
        assertEquals(cached.lastModified, response.getRequestProperty("If-Modified-Since"))
        assertNull(result.entries)
        assertEquals(cached.etag, result.etag)
        assertEquals(cached.lastModified, result.lastModified)
        assertEquals(0, response.reads)
        assertTrue(response.disconnected)
    }

    @Test fun unexpected304CannotEraseAnEmptyCacheOrReuseAnotherOriginsValidators() {
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(status = 304))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(status = 304),
            cached().copy(validatorUrl = "https://different.example.com/a"))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(status = 304),
            cached().copy(etag = "", lastModified = ""))
    }

    @Test fun redirectsResolveRelativeUrlsAndOnlySendValidatorsToTheirOriginalFinalUrl() = runBlocking {
        val redirect = connection(status = 302, headers = mapOf("Location" to "/final"))
        val final = connection(status = 304, url = "https://lists.example.com/final")
        val requests = mutableListOf<URL>()
        val result = RadioSubscriptionClient({
            requests.add(it)
            if (it.path == "/a") redirect else final
        }).fetch(cached().copy(validatorUrl = final.url.toString()))
        assertEquals(listOf("/a", "/final"), requests.map { it.path })
        assertNull(redirect.getRequestProperty("If-None-Match"))
        assertEquals("\"previous\"", final.getRequestProperty("If-None-Match"))
        assertEquals(final.url.toString(), result.validatorUrl)
        assertTrue(redirect.disconnected)
        assertTrue(final.disconnected)
    }

    @Test fun httpsDowngradesAndUnusableRedirectsFailWithoutFollowingThem() {
        rejected(RadioSubscriptionProblem.INSECURE_REDIRECT,
            connection(status = 302, headers = mapOf("Location" to "http://lists.example.com/a")))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(status = 302))
    }

    @Test fun redirectLoopsAreBoundedAndEveryConnectionIsDisconnected() = runBlocking {
        val requests = mutableListOf<Connection>()
        try {
            RadioSubscriptionClient({ url -> connection(status = 302, headers = mapOf("Location" to "/a"), url = url.toString())
                .also(requests::add) }).fetch(subscription)
            fail("Redirect loop accepted")
        } catch (error: RadioSubscriptionException) { assertEquals(RadioSubscriptionProblem.INVALID_LIST, error.failure.problem) }
        assertEquals(6, requests.size)
        assertTrue(requests.all { it.disconnected })
    }

    @Test fun emptyHtmlHlsPartialPlaylistsAndBadUtf8AreRejectedAsAWhole() {
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(""))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection("<html><body>Not a playlist</body></html>"))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(text, headers = mapOf("Content-Type" to "text/html; charset=UTF-8")))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nsegment.ts"))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(text + "Broken station,ftp://bad.example.com/stream"))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(text + "#EXTVLCOPT:http-referrer=https://example.com/\n"))
        rejected(RadioSubscriptionProblem.INVALID_LIST,
            Connection(URL(subscription.url), body = text.toByteArray() + byteArrayOf(0xc3.toByte(), 0x28)))
    }

    @Test fun reportedAndActualDownloadLimitsAreEnforcedEvenForUnknownLengths() {
        rejected(RadioSubscriptionProblem.LIMIT, Connection(URL(subscription.url), length = RadioList.MAX_BYTES + 1))
        rejected(RadioSubscriptionProblem.LIMIT, Connection(URL(subscription.url), length = -1,
            body = ByteArray(RadioList.MAX_BYTES + 1) { ' '.code.toByte() }))
    }

    @Test fun blankInteriorPlaceholdersAreReportedButIncompleteOrTruncatedEntriesStillFail() = runBlocking {
        val unavailable = "#EXTINF:-1,Unavailable station\n\n"
        val fixture = "#EXTM3U\n" + unavailable + text.substringAfter('\n')
        val parsed = RadioList.parse(fixture.byteInputStream())
        assertEquals("Manual import must continue reporting incomplete entries", 1, parsed.invalid)
        val result = RadioSubscriptionClient({ connection(fixture) }).fetch(subscription)
        assertEquals(1, result.entries!!.size)
        assertEquals(1, result.unavailableStations)
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(text + unavailable))
        rejected(RadioSubscriptionProblem.INVALID_LIST,
            connection("#EXTM3U\n#EXTINF:-1,Broken\n" + text.substringAfter('\n')))
        rejected(RadioSubscriptionProblem.INVALID_LIST,
            connection("#EXTM3U\n" + unavailable + "#EXTINF:-1,Still unavailable\n\n"))
        val cached = cached().copy(unavailableStations = 5)
        val unchanged = RadioSubscriptionClient({ connection(status = 304) }).fetch(cached)
        assertEquals(5, unchanged.unavailableStations)
    }

    @Test fun httpFailuresKeepTheirStatusAndDoNotParseErrorPages() = runBlocking {
        val response = connection("<html>Error</html>", status = 503)
        try { RadioSubscriptionClient({ response }).fetch(subscription); fail() }
        catch (error: RadioSubscriptionException) {
            assertEquals(RadioSubscriptionFailure(RadioSubscriptionProblem.HTTP, 503), error.failure)
        }
        assertEquals(0, response.reads)
        assertTrue(response.disconnected)
    }

    @Test fun slowOrFailingReadsHaveExplicitErrorsAndReleaseConnections() = runBlocking {
        var time = 0L
        val response = connection()
        try {
            RadioSubscriptionClient({ response }) { time.also { time += TimeUnit.SECONDS.toNanos(10) } }.fetch(subscription)
            fail("Unbounded download")
        } catch (error: RadioSubscriptionException) { assertEquals(RadioSubscriptionProblem.NETWORK, error.failure.problem) }
        assertTrue(response.disconnected)
        rejected(RadioSubscriptionProblem.NETWORK, Connection(URL(subscription.url), stream = object : InputStream() {
            override fun read(): Int = throw IOException("Fixture read failed")
        }))
    }

    @Test fun cancelledReadsDoNotBecomeNetworkErrors() = runBlocking {
        val began = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val response = Connection(URL(subscription.url), stream = object : InputStream() {
            override fun read(): Int {
                began.complete(Unit)
                runBlocking { release.await() }
                return -1
            }
        })
        val job = async(Dispatchers.IO) { RadioSubscriptionClient({ response }).fetch(subscription) }
        began.await()
        job.cancel()
        release.complete(Unit)
        try { job.await(); fail("Cancellation ignored") } catch (_: CancellationException) { }
        assertTrue(response.disconnected)
    }

    @Test fun unsafeResponseValidatorsAreNotSavedOrSentLater() = runBlocking {
        val result = RadioSubscriptionClient({
            connection(headers = mapOf("ETag" to "bad\r\nInjected: yes", "Last-Modified" to "x".repeat(1025)))
        }).fetch(subscription)
        assertEquals("", result.etag)
        assertEquals("", result.lastModified)
    }

    @Test fun cctvPresetFiltersOnlyAudioAndIgnoresPublisherMetadataWithoutFollowingIt() = runBlocking {
        val fixture = "#EXTM3U x-tvg-url=\"https://example.com/epg\"\n" +
            "#EXT-X-APP APTV\n#EXT-X-APTV-TYPE remote\n#EXT-X-SUB-URL https://example.com/ignored\n" +
            "#EXTINF:-1,Unrelated missing station\n" +
            "#EXTINF:-1 tvg-logo=\"https://example.com/logo\" group-title=\"TV, China\",CCTV-1 General, audio\n" +
            "https://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8\n" +
            "#EXTINF:-1,Unrelated TV\nhttps://example.com/video.m3u8\n" +
            "#EXTINF:-1,CCTV-5+ Sports events\nhttps://piccpndks.v.kcdnvip.com/audio/cctv5plus_2.m3u8\n"
        val response = connection(fixture, url = RadioSubscriptions.CCTV_AUDIO_URL)
        var requests = 0
        val result = RadioSubscriptionClient({ requests++; response }).fetch(
            subscription.copy(url = RadioSubscriptions.CCTV_AUDIO_URL))
        assertEquals(1, requests)
        assertEquals(listOf("CCTV-1 General, audio", "CCTV-5+ Sports events"), result.entries!!.map { it.name })
        assertTrue(result.entries.all { it.group == RadioGroups.CCTV_TELEVISION && it.logoUri.isEmpty() })
        assertEquals(0, result.unavailableStations)
        assertTrue(response.disconnected)
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(fixture))
    }

    @Test fun cctvFilteringStillRejectsOversizedAndMalformedUtf8Bodies() {
        val cctv = subscription.copy(url = RadioSubscriptions.CCTV_AUDIO_URL)
        rejected(RadioSubscriptionProblem.LIMIT, Connection(URL(cctv.url), length = -1,
            body = ByteArray(RadioList.MAX_BYTES + 1) { ' '.code.toByte() }), cctv)
        rejected(RadioSubscriptionProblem.INVALID_LIST, Connection(URL(cctv.url),
            body = "#EXTM3U\n".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28)), cctv)
    }

    @Test fun cctvPresetRejectsEmptyTruncatedVideoAndHlsResponsesWithoutChangingTheCache() {
        val saved = cached().copy(url = RadioSubscriptions.CCTV_AUDIO_URL)
        val valid = "#EXTM3U\n#EXTINF:-1,CCTV-1 General\nhttps://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8\n"
        for (fixture in listOf(text, valid + "#EXTINF:-1,CCTV-2\n",
            valid.replace("/audio/", "/video/"),
            valid.replace("piccpndali.v.myalicdn.com", "example.com"),
            valid + "#EXT-X-TARGETDURATION:6\n", valid + "#KODIPROP:unsafe=true\n",
            "<html>\n$valid")) {
            rejected(RadioSubscriptionProblem.INVALID_LIST, connection(fixture), saved)
        }
    }

    @Test fun explicitlyProvidedCctvFixtureContainsAllTwentyAudioChannels() = runBlocking {
        val path = System.getenv("MANAPLAYER_CCTV_FIXTURE")
        assumeTrue("Set MANAPLAYER_CCTV_FIXTURE to verify the full upstream list", !path.isNullOrBlank())
        val file = File(path!!)
        val result = RadioSubscriptionClient({ Connection(it, body = file.readBytes()) })
            .fetch(subscription.copy(url = RadioSubscriptions.CCTV_AUDIO_URL))
        val entries = checkNotNull(result.entries)
        assertEquals(20, RadioSubscriptions.programs(entries, emptyMap()).size)
        assertTrue(entries.all { it.group == RadioGroups.CCTV_TELEVISION && it.uri.contains("/audio/cctv") && it.logoUri.isEmpty() })
        println("CCTV upstream: ${file.length()} bytes; ${entries.size} audio entries; no other stations imported")
    }

    @Test fun stationUrlFragmentsAreNonNetworkLabelsNotHttpHeadersOrDifferentSources() = runBlocking {
        val fixture = text.replace("https://audio.example.com/live", "https://audio.example.com/live#STATION_LABEL")
        val result = RadioSubscriptionClient({ connection(fixture) }).fetch(subscription)
        assertEquals("https://audio.example.com/live", result.entries!!.single().uri)
        assertEquals(1, RadioList.parse(fixture.byteInputStream()).invalid)
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(fixture + "#KODIPROP:unsafe=true\n"))
        rejected(RadioSubscriptionProblem.INVALID_LIST, connection(fixture.replace("STATION_LABEL", "\u0001invalid")))
    }

    @Test fun explicitlyProvidedPublicPresetFixturesUseTheRealStrictParser() = runBlocking {
        val directory = System.getenv("MANAPLAYER_RADIO_PRESET_FIXTURES")
        assumeTrue("Set MANAPLAYER_RADIO_PRESET_FIXTURES to verify downloaded preset snapshots", !directory.isNullOrBlank())
        val names = listOf("fanmingming", "hk", "tw", "us", "gb", "top500", "cctv")
        RadioSubscriptions.presets.forEachIndexed { index, preset ->
            val file = File(directory!!, "${names[index]}.m3u")
            assertTrue("Missing preset fixture ${file.name}", file.isFile)
            val parsed = file.inputStream().use { RadioSubscriptions.parsePlaylist(preset.url, it) }
            assertEquals("PRESET ${names[index]} rejected lines: ${parsed.issues}", 0, parsed.invalid)
            val result = RadioSubscriptionClient({
                Connection(it, body = file.readBytes())
            }).fetch(subscription.copy(url = preset.url))
            val entries = checkNotNull(result.entries)
            val stations = RadioSubscriptions.programs(entries, emptyMap())
            assertTrue(stations.isNotEmpty())
            println("PRESET ${names[index]} bytes=${file.length()} entries=${entries.size} stations=${stations.size} unavailable=${result.unavailableStations} ignoredLogos=${result.ignoredLogos}")
        }
    }
}
