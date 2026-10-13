package com.macarmusic.app.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.util.Base64

class OnlineLyricsClientTest {
    private val query = LyricQuery("Synthetic song", "Fixture singer", 60_000)
    private val hash = "a".repeat(32)
    private val catalog = """{"status":1,"data":{"lists":[{"SongName":"Synthetic song","SingerName":"Fixture singer","Duration":60,"FileHash":"$hash"}]}}"""
    private val search = """{"status":200,"candidates":[{"id":"42","accesskey":"public-test-token","song":"Synthetic song","singer":"Fixture singer","duration":60000}]}"""
    private val text = "[00:00]Synthetic first marker\n[00:10]Synthetic second marker"
    private val download = """{"status":200,"content":"${Base64.getEncoder().encodeToString(text.toByteArray())}"}"""

    @Test fun numberedLocalTitleAndSharingSiteArtistFindTheScreenshotRecording() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OnlineLyricsClient({ url, _, _ ->
            calls.add(URLDecoder.decode(url, "UTF-8"))
            when {
                url.contains("song_search_v2") ->
                    """{"status":1,"data":{"lists":[{"SongName":"如果你也听说","SingerName":"张惠妹","Duration":311,"FileHash":"$hash"}]}}"""
                url.contains("lyrics.kugou.com/search") ->
                    """{"status":200,"candidates":[{"id":"42","accesskey":"public-test-token","song":"如果你也听说","singer":"张惠妹","duration":311875}]}"""
                url.contains("/download?") -> download
                else -> """{"code":200,"result":{"songs":[]}}"""
            }
        }) { _, error -> throw AssertionError(error) }
        val localTags = LyricQuery("1562.如果你也听说-张惠妹", "小高网分享", 311_000)
        assertEquals(OnlineLyric(text, LyricOrigin.KUGOU), client.find(localTags))
        assertTrue(calls.first().contains("keyword=如果你也听说 张惠妹&"))
        assertTrue(calls.none { "1562." in it || "小高网分享" in it })
    }

    @Test fun reportedDuetFindsLyricsWithReversedCatalogAndLyricArtistOrder() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OnlineLyricsClient({ url, _, _ ->
            calls.add(URLDecoder.decode(url, "UTF-8"))
            when {
                url.contains("song_search_v2") ->
                    """{"status":1,"data":{"lists":[{"SongName":"当爱已成往事","SingerName":"李宗盛、林忆莲","Duration":279,"FileHash":"$hash"}]}}"""
                url.contains("lyrics.kugou.com/search") ->
                    """{"status":200,"candidates":[{"id":"42","accesskey":"public-test-token","song":"当爱已成往事","singer":"李宗盛、林忆莲","duration":279483}]}"""
                url.contains("/download?") -> download
                else -> throw AssertionError("A valid duet should not require the next provider")
            }
        }) { _, error -> throw AssertionError(error) }
        val filename = "1542. 当爱已成往事-林忆莲_李宗盛.MP3"
        assertEquals(OnlineLyric(text, LyricOrigin.KUGOU),
            client.find(LyricQuery("Unknown title", "小高网分享", 279_000), filename))
        assertTrue(calls.first().contains("keyword=当爱已成往事 林忆莲 李宗盛&"))
        assertEquals(3, calls.size)
    }

    @Test fun providerOrderMatchesThePublishedSettingsOrder() {
        assertEquals(listOf(LyricOrigin.KUGOU, LyricOrigin.NETEASE), OnlineLyricsClient.PROVIDERS)
    }

    @Test fun catalogRequestsAreSpacedWithoutDelayingTheFirstOrAlreadyQuietRequest() = runBlocking {
        var now = -20_000L
        val waits = mutableListOf<Long>()
        val pacer = CatalogRequestPacer({ now }) { waits.add(it); now += it }
        pacer.await()
        assertTrue(waits.isEmpty())
        pacer.await()
        assertEquals(listOf(10_000L), waits)
        now += 3_000
        pacer.await()
        assertEquals(listOf(10_000L, 7_000L), waits)
        now += 12_000
        pacer.await()
        assertEquals(2, waits.size)
    }

    @Test fun cancelledCatalogWaitDoesNotReserveAnotherRequestSlot() = runBlocking {
        var now = 0L
        var cancelled = true
        val waits = mutableListOf<Long>()
        val pacer = CatalogRequestPacer({ now }) {
            if (cancelled) throw CancellationException("Track changed")
            waits.add(it)
            now += it
        }
        pacer.await()
        try { pacer.await(); fail("The cancelled request was sent") } catch (_: CancellationException) { }
        now += 2_000
        cancelled = false
        pacer.await()
        assertEquals(listOf(8_000L), waits)
    }

    @Test fun slowProviderIsReportedAndLeavesTimeForTheNextProvider() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if (url.contains("kugou")) delay(5_000)
            if (url.endsWith("/get/web")) {
                """{"code":200,"result":{"songs":[{"id":7,"name":"Synthetic song","artists":[{"name":"Fixture singer"}],"duration":60000}]}}"""
            } else """{"code":200,"lrc":{"lyric":"[00:01]Synthetic fallback"}}"""
        }, providerTimeoutMs = 100) { provider, error ->
            assertTrue(error is IOException && error.cause is TimeoutCancellationException)
            failures.add(provider)
        }
        assertEquals(LyricOrigin.NETEASE, withTimeout(2_000) { client.find(query) }?.origin)
        assertEquals(listOf(LyricOrigin.KUGOU), failures)
    }

    @Test fun overallTimeoutDoesNotStartAnotherProviderOrBecomeALookupError() = runBlocking {
        var requests = 0
        val client = OnlineLyricsClient({ _, _, _ -> requests++; delay(5_000); "" }) { _, _ ->
            fail("Overall cancellation must not be reported as a provider failure")
        }
        try {
            withTimeout(30) { client.find(query) }
            fail("Overall timeout was swallowed")
        } catch (_: TimeoutCancellationException) { }
        assertEquals(1, requests)
    }

    @Test fun verifiedKugouShapeIsDecodedWithoutQueryingAnotherProvider() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OnlineLyricsClient({ url, body, _ ->
            calls.add(url)
            assertNull(body)
            when {
                url.contains("song_search_v2") -> catalog
                url.contains("/search?") -> search
                else -> download
            }
        }) { _, error -> throw AssertionError(error) }
        assertEquals(OnlineLyric(text, LyricOrigin.KUGOU), client.find(query))
        assertEquals(3, calls.size)
        assertTrue(calls.first().startsWith("https://songsearch.kugou.com/"))
        assertTrue(calls.drop(1).all { it.startsWith("https://lyrics.kugou.com/") })
        assertTrue(calls[1].contains("hash=${hash.uppercase()}"))
    }

    @Test fun wrongMatchIsNeverDownloadedAndFallbackUsesPost() = runBlocking {
        var requests = 0
        val client = OnlineLyricsClient({ url, body, _ ->
            requests++
            when {
                url.contains("song_search_v2") -> catalog
                url.contains("kugou") -> search.replace("Fixture singer", "Wrong singer")
                url.endsWith("/get/web") -> {
                    assertNotNull(body)
                    """{"code":200,"result":{"songs":[{"id":7,"name":"Synthetic song","artists":[{"name":"Fixture singer"}],"duration":60000}]}}"""
                }
                else -> """{"code":200,"lrc":{"lyric":"[00:01]Synthetic fallback"}}"""
            }
        }) { _, error -> throw AssertionError(error) }
        assertEquals(LyricOrigin.NETEASE, client.find(query)?.origin)
        assertEquals(5, requests)
    }

    @Test fun failedProviderIsReportedBeforeTryingFallback() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if (url.contains("kugou")) throw IOException("Simulated unavailable service")
            """{"code":200,"result":{"songs":[]}}"""
        }) { provider, _ -> failures.add(provider) }
        try { client.find(query); fail("A failed lookup was hidden") } catch (_: IOException) { }
        assertEquals(listOf(LyricOrigin.KUGOU), failures)
    }

    @Test fun validEmptyResultsReturnNoMatch() = runBlocking {
        val client = OnlineLyricsClient({ url, _, _ ->
            if (url.contains("kugou")) """{"status":1,"data":{"lists":[]}}""" else """{"code":200,"result":{"songCount":0}}"""
        }) { _, error -> throw AssertionError(error) }
        assertNull(client.find(query))
    }

    @Test fun incompleteSuccessResponsesAreReportedInsteadOfNoMatch() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if (url.contains("kugou")) """{"status":1}""" else """{"code":200}"""
        }) { provider, _ -> failures.add(provider) }
        try { client.find(query); fail("Incomplete catalogs were treated as valid empty results") } catch (_: IOException) { }
        assertEquals(listOf(LyricOrigin.KUGOU, LyricOrigin.NETEASE), failures)
    }

    @Test fun providerErrorCodeIsNotHiddenByASuccessStatus() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if (url.contains("kugou")) """{"status":1,"error_code":123,"data":{"lists":[]}}"""
            else """{"code":200,"result":{"songs":[]}}"""
        }) { provider, _ -> failures.add(provider) }
        try { client.find(query); fail("A provider error was treated as no match") } catch (_: IOException) { }
        assertEquals(listOf(LyricOrigin.KUGOU), failures)
    }

    @Test fun cancelledLookupDoesNotContinueToAnotherProvider() = runBlocking {
        var requests = 0
        val client = OnlineLyricsClient({ _, _, _ -> requests++; throw CancellationException("Track changed") }) { _, _ ->
            fail("Cancellation must not be reported as a provider failure")
        }
        try { client.find(query); fail("Cancellation was swallowed") } catch (_: CancellationException) { }
        assertEquals(1, requests)
    }

    @Test fun catalogDurationUsesSecondsAndWrongRecordingIsNotDownloaded() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OnlineLyricsClient({ url, _, _ ->
            calls.add(url)
            if (url.contains("song_search_v2")) catalog.replace("\"Duration\":60", "\"Duration\":120")
            else """{"code":200,"result":{"songs":[]}}"""
        }) { _, error -> throw AssertionError(error) }
        assertNull(client.find(query))
        assertEquals(3, calls.size)
        assertFalse(calls.any { it.startsWith("https://lyrics.kugou.com/") })
    }

    @Test fun titleOnlyCatalogFallbackStillRequiresTheCorrectArtist() = runBlocking {
        val calls = mutableListOf<String>()
        val client = OnlineLyricsClient({ url, _, _ ->
            val decoded = URLDecoder.decode(url, "UTF-8")
            calls.add(decoded)
            when {
                decoded.contains("keyword=Synthetic song Fixture singer&") -> """{"status":1,"data":{"lists":[]}}"""
                url.contains("song_search_v2") -> catalog.replace("\"lists\":[",
                    """"lists":[{"SongName":"Synthetic song","SingerName":"Impostor","Duration":60,"FileHash":"${"b".repeat(32)}"},""")
                url.contains("/search?") -> search
                else -> download
            }
        }) { _, error -> throw AssertionError(error) }
        assertEquals(OnlineLyric(text, LyricOrigin.KUGOU), client.find(query))
        assertEquals(4, calls.size)
        assertTrue(calls[1].contains("keyword=Synthetic song&"))
        assertTrue(calls[2].contains("hash=${hash.uppercase()}"))
    }

    @Test fun secondMatchingCatalogHashIsUsedWhenTheFirstHasNoLyrics() = runBlocking {
        val calls = mutableListOf<String>()
        val alternate = "b".repeat(32)
        val client = OnlineLyricsClient({ url, _, _ ->
            calls.add(url)
            when {
                url.contains("song_search_v2") -> catalog.replace("]}}",
                    """,{"SongName":"Synthetic song","SingerName":"Fixture singer","Duration":60,"FileHash":"$alternate"}]}}""")
                url.contains("hash=${hash.uppercase()}") -> """{"status":200,"candidates":[]}"""
                url.contains("hash=${alternate.uppercase()}") -> search
                else -> download
            }
        }) { _, error -> throw AssertionError(error) }
        assertEquals(OnlineLyric(text, LyricOrigin.KUGOU), client.find(query))
        assertEquals(4, calls.size)
    }

    @Test(expected = IOException::class) fun invalidBase64IsRejected() {
        OnlineLyricsClient({ _, _, _ -> "" }) { _, _ -> }.decodeBase64("!!!!")
    }
}
