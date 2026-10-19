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
    private val text = (0..7).joinToString("\n") { "[00:0$it]Synthetic marker $it" }
    private val search = """{"status":200,"candidates":[{"id":"42","accesskey":"test-only","song":"Synthetic song","singer":"Fixture singer","duration":60000}]}"""
    private val mobile = """{"status":1,"data":{"info":[{"songname":"Synthetic song","singername":"Fixture singer","hash":"$hash"}]}}"""
    private fun encoded(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
    private fun download(value: String = text) = """{"status":200,"content":"${encoded(value)}"}"""
    private fun client(answer: suspend (String) -> String) = OnlineLyricsClient({ url, body, _ ->
        assertNull("The reference local routes use GET", body)
        answer(url)
    }) { _, error -> throw AssertionError(error) }

    @Test fun fiveRoutesUseTheReferenceStandaloneOrder() {
        assertEquals(listOf(LyricSearchRoute.KUGOU_DIRECT, LyricSearchRoute.KUGOU_MOBILE,
            LyricSearchRoute.QQ, LyricSearchRoute.NETEASE, LyricSearchRoute.KUWO), OnlineLyricsClient.ROUTES)
        assertEquals(listOf(LyricOrigin.KUGOU, LyricOrigin.QQ, LyricOrigin.NETEASE, LyricOrigin.KUWO),
            OnlineLyricsClient.PROVIDERS)
    }

    @Test fun copiedNumberedFilenameSearchesTheSongAndSingerNotCopy() = runBlocking {
        val calls = mutableListOf<String>()
        val client = client { url ->
            calls.add(URLDecoder.decode(url, "UTF-8"))
            when {
                "/search?" in url -> search.replace("Synthetic song", "演员").replace("Fixture singer", "薛之谦")
                "/download?" in url -> download()
                else -> throw AssertionError("A matching direct result must stop the search")
            }
        }
        val filename = "1586.演员-薛之谦 - Copy (2).mp3"
        assertFound(text, LyricOrigin.KUGOU, client.find(LyricQuery(filename, "", 261_000), filename))
        assertTrue(calls.first().contains("keyword=演员 薛之谦&duration=261"))
        assertTrue(calls.none { "Copy" in it || "1586." in it })
        assertEquals(2, calls.size)
    }

    @Test fun numberedDuetAndUnusableTagsStillFindTheFirstDirectResult() = runBlocking {
        val calls = mutableListOf<String>()
        val client = client { url ->
            calls.add(URLDecoder.decode(url, "UTF-8"))
            if ("/search?" in url) search.replace("Synthetic song", "当爱已成往事")
                .replace("Fixture singer", "李宗盛、林忆莲") else download()
        }
        assertEquals(LyricOrigin.KUGOU, client.find(
            LyricQuery("1542. 当爱已成往事-林忆莲_李宗盛.MP3", "小高网分享", 279_000))?.origin)
        assertTrue(calls.first().contains("keyword=当爱已成往事 林忆莲_李宗盛&"))
        assertEquals(2, calls.size)
    }

    @Test fun validEmptyResultsVisitEachRouteExactlyOnceWithoutQueryExpansion() = runBlocking {
        val calls = mutableListOf<String>()
        val client = client { url -> calls.add(url); empty(url) }
        assertNull(client.find(LyricQuery("One - Two", "", 60_000), "Three - Four.mp3"))
        assertEquals(5, calls.size)
        assertTrue("lyrics.kugou.com/search" in calls[0])
        assertTrue("mobilecdn.kugou.com/api/v3/search/song" in calls[1])
        assertTrue("client_search_cp" in calls[2])
        assertTrue("music.163.com/api/search/get/web?" in calls[3])
        assertTrue("search.kuwo.cn/r.s?" in calls[4])
    }

    @Test fun directSearchUsesSecondsOrAnEmptyDurationAndDownloadsOnlyTheBest() = runBlocking {
        for (duration in listOf(60_000L, 0L)) {
            val calls = mutableListOf<String>()
            val client = client { url ->
                calls.add(url)
                when {
                    "/search?" in url -> search.replace("]}", """,{"id":"43","accesskey":"test-only","song":"Synthetic song","singer":"Fixture singer","duration":60000}]}""")
                    else -> download()
                }
            }
            assertEquals(text, client.find(query.copy(durationMs = duration))?.text)
            assertEquals(2, calls.size)
            assertTrue(calls[0].endsWith("&duration=${if (duration > 0) "60" else ""}"))
            assertTrue(calls[1].contains("id=42&"))
        }
    }

    @Test fun directCandidatesUseWeightedMatchingInsteadOfHardRejection() = runBlocking {
        val client = client { url ->
            if ("/search?" in url) search.replace("Synthetic song", "Synthetic song (Live)")
                .replace("60000", "100000") else download()
        }
        assertEquals(LyricOrigin.KUGOU, client.find(query)?.origin)
    }

    @Test fun directScoreBelowTenDoesNotDownloadAnything() = runBlocking {
        val calls = mutableListOf<String>()
        val client = client { url ->
            calls.add(url)
            if ("lyrics.kugou.com/search" in url)
                search.replace("Synthetic song", "Unrelated").replace("Fixture singer", "Different")
            else empty(url)
        }
        assertNull(client.find(query))
        assertEquals(5, calls.size)
        assertFalse(calls.any { "/download?" in it })
    }

    @Test fun mobileFallbackUsesItsHighestRankedHashWithNoCatalogCooldown() = runBlocking {
        val calls = mutableListOf<String>()
        val client = client { url ->
            calls.add(url)
            when {
                "mobilecdn.kugou.com" in url -> mobile
                "/download?" in url -> download()
                else -> empty(url)
            }
        }
        repeat(2) { assertEquals(text, client.find(query)?.text) }
        assertEquals(6, calls.size)
        assertTrue(calls[2].contains("id=$hash&accesskey=$hash&fmt=lrc"))
        assertFalse(calls.any { "songsearch.kugou.com" in it || "&hash=" in it })
    }

    @Test fun mobileSkipsEmptyHashesButHasNoMinimumScoreGate() = runBlocking {
        val client = client { url ->
            when {
                "mobilecdn.kugou.com" in url -> """{"status":1,"data":{"info":[
                    {"songname":"Synthetic song","singername":"Fixture singer","hash":""},
                    {"songname":"Different","singername":"Different","hash":"$hash"}]}}"""
                "/download?" in url -> download()
                else -> empty(url)
            }
        }
        assertEquals(text, client.find(query)?.text)
    }

    @Test fun kugouRejectsSevenTimedLinesAndContinuesToQq() = runBlocking {
        val client = client { url ->
            when {
                "lyrics.kugou.com/search" in url -> search
                "/download?" in url -> download(text.lines().take(7).joinToString("\n"))
                "client_search_cp" in url -> qqSearch()
                "fcg_query_lyric_new" in url -> """{"code":0,"lyric":"${encoded("[00:01]Synthetic QQ")}"}"""
                else -> empty(url)
            }
        }
        assertEquals(LyricOrigin.QQ, client.find(query)?.origin)
    }

    @Test fun qqUsesTheFirstSingerAndDecodesBase64EntitiesWithoutADurationGate() = runBlocking {
        val client = OnlineLyricsClient({ url, _, referer ->
            when {
                "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[
                    {"songname":"Synthetic song","singer":[{"name":"Wrong"},{"name":"Fixture singer"}],"songmid":"wrong"},
                    {"songname":"Synthetic song","singer":[{"name":"Fixture singer"}],"interval":999,"songmid":"correct"}]}}}"""
                "fcg_query_lyric_new" in url -> {
                    assertEquals("https://y.qq.com/", referer)
                    assertTrue("songmid=correct&g_tk=5381&format=json&nobase64=0" in url)
                    """{"code":0,"lyric":"${encoded("&#91;00:01&#93;Synthetic &amp; marker")}"}"""
                }
                else -> empty(url)
            }
        }) { _, error -> throw AssertionError(error) }
        assertFound("[00:01]Synthetic & marker", LyricOrigin.QQ, client.find(query))
    }

    @Test fun qqFallsBackToTranslationWhenDecodedOriginalIsEmpty() = runBlocking {
        val client = client { url ->
            when {
                "client_search_cp" in url -> qqSearch()
                "fcg_query_lyric_new" in url ->
                    """{"code":0,"lyric":"${encoded("[]")}","trans":"${encoded(text)}"}"""
                else -> empty(url)
            }
        }
        assertFound(text, LyricOrigin.QQ, client.find(query))
    }

    @Test fun neteaseUsesReferenceGetParametersAndTranslatedFallback() = runBlocking {
        val client = OnlineLyricsClient({ url, body, referer ->
            assertNull(body)
            when {
                "/get/web?" in url -> {
                    assertEquals("https://music.163.com", referer)
                    assertTrue(url.endsWith("&type=1&offset=0&total=true&limit=10"))
                    """{"code":200,"result":{"songs":[{"id":7,"name":"Synthetic song","artists":[{"name":"Fixture singer"}],"duration":999999}]}}"""
                }
                "/api/song/lyric?" in url -> {
                    assertTrue(url.endsWith("id=7&lv=1&kv=1&tv=-1"))
                    """{"code":200,"lrc":{"lyric":"[]"},"tlyric":{"lyric":"[00:01]Synthetic translation"}}"""
                }
                else -> empty(url)
            }
        }) { _, error -> throw AssertionError(error) }
        assertFound("[00:01]Synthetic translation", LyricOrigin.NETEASE, client.find(query))
    }

    @Test fun aChosenSongWithNoLyricsDoesNotTryTheSecondSong() = runBlocking {
        val downloads = mutableListOf<String>()
        val client = client { url ->
            when {
                "/get/web?" in url -> """{"code":200,"result":{"songs":[
                    {"id":7,"name":"Synthetic song","artists":[{"name":"Fixture singer"}]},
                    {"id":8,"name":"Synthetic song","artists":[{"name":"Fixture singer"}]}]}}"""
                "/api/song/lyric?" in url -> {
                    downloads.add(url)
                    """{"code":200,"lrc":{"lyric":""}}"""
                }
                else -> empty(url)
            }
        }
        assertNull(client.find(query))
        assertEquals(1, downloads.size)
        assertTrue("id=7&" in downloads.single())
    }

    @Test fun kuwoUsesTheReferenceFieldsEndpointAndCentisecondFormatting() = runBlocking {
        val client = client { url ->
            when {
                "search.kuwo.cn" in url -> """prefix{'abslist':[{'SONGNAME':'Synthetic&nbsp;song','ARTIST':'Fixture singer','MUSICRID':'MUSIC_123'}]}"""
                "/openapi/v1/www/lyric/getlyric?" in url -> {
                    assertTrue(url.endsWith("musicId=123"))
                    """{"data":{"lrclist":[${(0..7).joinToString(",") {
                        """{"time":"${it + 1}.25","lineLyric":"Synthetic &amp; marker $it"}"""
                    }}]}}"""
                }
                else -> empty(url)
            }
        }
        val found = client.find(query)
        assertEquals(LyricOrigin.KUWO, found?.origin)
        assertEquals(LyricLine(1250, "Synthetic & marker 0"), found?.text?.let(LyricsParser::parse)?.lines?.first())
        assertEquals(8, found?.text?.let(LyricsParser::parse)?.lines?.size)
    }

    @Test fun slowRoutesAreReportedAndLeaveTimeForTheNextRoute() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            when {
                "kugou" in url -> { delay(5_000); "" }
                "client_search_cp" in url -> qqSearch()
                "fcg_query_lyric_new" in url -> """{"code":0,"lyric":"${encoded(text)}"}"""
                else -> empty(url)
            }
        }, providerTimeoutMs = 100) { origin, error ->
            assertTrue(error is IOException && error.cause is TimeoutCancellationException)
            failures.add(origin)
        }
        assertEquals(LyricOrigin.QQ, withTimeout(2_000) { client.find(query) }?.origin)
        assertEquals(listOf(LyricOrigin.KUGOU, LyricOrigin.KUGOU), failures)
    }

    @Test fun failuresAreReportedWithoutPreventingLaterRoutes() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if ("kugou" in url) throw IOException("Synthetic unavailable service")
            empty(url)
        }) { origin, _ -> failures.add(origin) }
        assertThrows(IOException::class.java) { runBlocking { client.find(query) } }
        assertEquals(listOf(LyricOrigin.KUGOU, LyricOrigin.KUGOU), failures)
    }

    @Test fun malformedCatalogsCannotMasqueradeAsNoMatch() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ _, _, _ -> "{}" }) { origin, _ -> failures.add(origin) }
        assertThrows(IOException::class.java) { runBlocking { client.find(query) } }
        assertEquals(OnlineLyricsClient.ROUTES.map { it.origin }, failures)
    }

    @Test fun mobileErrorCodeIsNotHiddenBySuccessStatus() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            if ("mobilecdn" in url) """{"status":1,"error_code":123,"data":{"info":[]}}""" else empty(url)
        }) { origin, _ -> failures.add(origin) }
        assertThrows(IOException::class.java) { runBlocking { client.find(query) } }
        assertEquals(listOf(LyricOrigin.KUGOU), failures)
    }

    @Test fun overallCancellationNeverStartsAnotherRouteOrReportsAFailure() = runBlocking {
        var calls = 0
        val client = OnlineLyricsClient({ _, _, _ -> calls++; delay(5_000); "" }) { _, _ -> fail("Cancellation reported") }
        try { withTimeout(30) { client.find(query) }; fail("Cancellation swallowed") }
        catch (_: TimeoutCancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun trackChangeCancellationNeverFallsThrough() = runBlocking {
        var calls = 0
        val client = OnlineLyricsClient({ _, _, _ -> calls++; throw CancellationException("Track changed") }) { _, _ ->
            fail("Cancellation reported")
        }
        try { client.find(query); fail("Cancellation swallowed") } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun nextTriesCurrentProviderAlternativesBeforeFollowingProvidersWithoutRepeats() = runBlocking {
        val calls = mutableListOf<String>()
        val alternative = text.replace("marker", "alternative")
        val last = text.replace("marker", "later provider")
        val client = client { url ->
            calls.add(url)
            when {
                "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[
                    {"songname":"Synthetic song","singer":[{"name":"Fixture singer"}],"songmid":"first"},
                    {"songname":"Synthetic song","singer":[{"name":"Fixture singer"}],"songmid":"duplicate"},
                    {"songname":"Synthetic song","singer":[{"name":"Fixture singer"}],"songmid":"second"}]}}}"""
                "songmid=duplicate" in url -> """{"code":0,"lyric":"${encoded("[by:Another catalog]\n$text")}"}"""
                "songmid=second" in url -> """{"code":0,"lyric":"${encoded(alternative)}"}"""
                "songmid=first" in url -> throw AssertionError("Already selected candidate was downloaded again")
                "/get/web?" in url -> """{"code":200,"result":{"songs":[
                    {"id":7,"name":"Synthetic song","artists":[{"name":"Fixture singer"}]}]}}"""
                "/api/song/lyric?" in url -> """{"code":200,"lrc":{"lyric":"${last.replace("\n", "\\n")}"}}"""
                "kugou" in url -> throw AssertionError("Next must start with the current provider, not restart")
                else -> empty(url)
            }
        }
        val initial = LyricSearchHistory(listOf(LyricCandidateId(LyricSearchRoute.QQ, "first")))
            .including(requireNotNull(LyricsParser.parse(text)))
        val second = requireNotNull(client.find(query, alternatives = LyricAlternatives(LyricOrigin.QQ, initial)))
        assertEquals(alternative, second.text)
        assertEquals(LyricOrigin.QQ, second.origin)
        assertEquals(3, calls.size)
        assertEquals(listOf("first", "duplicate", "second"), second.history.candidates.map { it.id })
        assertEquals(2, second.history.contents.size)
        calls.clear()
        val third = requireNotNull(client.find(query, alternatives = LyricAlternatives(second.origin, second.history)))
        assertEquals(LyricOrigin.NETEASE, third.origin)
        assertEquals(last, third.text)
        assertEquals(3, calls.size)
        assertFalse(calls.any { "fcg_query_lyric_new" in it })
        calls.clear()
        assertNull(client.find(query, alternatives = LyricAlternatives(third.origin, third.history)))
        assertEquals(2, calls.size)
        assertTrue(calls[0].contains("/get/web?"))
        assertTrue(calls[1].contains("search.kuwo.cn"))
    }

    @Test fun legacyCachedLyricsAreSkippedByContentWithoutCandidateMetadata() = runBlocking {
        var downloads = 0
        val client = client { url ->
            when {
                "client_search_cp" in url -> qqSearch()
                "fcg_query_lyric_new" in url -> { downloads++; """{"code":0,"lyric":"${encoded(text)}"}""" }
                else -> empty(url)
            }
        }
        val history = LyricSearchHistory().including(requireNotNull(LyricsParser.parse(text)))
        assertNull(client.find(query, alternatives = LyricAlternatives(LyricOrigin.QQ, history)))
        assertEquals(1, downloads)
    }

    @Test fun nextContinuesPastOneFailedCandidateButDoesNotReportFailuresAsExhaustion() = runBlocking {
        val failures = mutableListOf<LyricOrigin>()
        val client = OnlineLyricsClient({ url, _, _ ->
            when {
                "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[
                    {"songname":"Synthetic song","songmid":"unavailable"},
                    {"songname":"Synthetic song","songmid":"valid"}]}}}"""
                "songmid=unavailable" in url -> throw IOException("Synthetic unavailable download")
                "songmid=valid" in url -> """{"code":0,"lyric":"${encoded(text)}"}"""
                else -> empty(url)
            }
        }) { origin, _ -> failures.add(origin) }
        val found = client.find(query, alternatives = LyricAlternatives(LyricOrigin.QQ, LyricSearchHistory()))
        assertEquals(text, found?.text)
        assertEquals(listOf(LyricOrigin.QQ), failures)
        assertThrows(IOException::class.java) { runBlocking {
            client.find(query, alternatives = LyricAlternatives(LyricOrigin.QQ, requireNotNull(found).history))
        } }
        Unit
    }

    @Test fun providerConfigurationKeepsHostsAndRoutesTogether() {
        assertEquals(OnlineLyricsClient.ROUTES, LyricProviderConfig.providers.filter { it.enabled }.flatMap { it.routes })
        assertEquals(setOf("lyrics.kugou.com", "mobilecdn.kugou.com", "c.y.qq.com", "music.163.com",
            "search.kuwo.cn", "www.kuwo.cn"), LyricProviderConfig.allowedHosts)
        assertTrue(LyricProviderConfig.providers.all { provider -> provider.routes.all { it.origin == provider.origin } })
    }

    @Test fun paddedAndUnpaddedBase64WorkWithoutAndroid26Apis() {
        val client = client { "" }
        for (value in listOf("a", "ab", "abc", text)) {
            assertArrayEquals(value.toByteArray(), client.decodeBase64(encoded(value)))
            assertArrayEquals(value.toByteArray(), client.decodeBase64(encoded(value).trimEnd('=')))
        }
        for (value in listOf("!!!!", "A", "AA=A", "AA==AAAA")) {
            assertThrows(IOException::class.java) { client.decodeBase64(value) }
        }
    }

    private fun qqSearch() =
        """{"code":0,"data":{"song":{"list":[{"songname":"Synthetic song","singer":[{"name":"Fixture singer"}],"songmid":"fixture"}]}}}"""

    private fun assertFound(text: String, origin: LyricOrigin, found: OnlineLyric?) {
        val value = requireNotNull(found)
        assertEquals(text, value.text)
        assertEquals(origin, value.origin)
        assertEquals(origin, value.history.candidates.last().route.origin)
        assertEquals(listOf(LyricSearchHistory.fingerprint(requireNotNull(LyricsParser.parse(text)))), value.history.contents)
    }

    private fun empty(url: String): String = when {
        "lyrics.kugou.com/search" in url -> """{"status":200,"candidates":[]}"""
        "mobilecdn.kugou.com" in url -> """{"status":1,"data":{"info":[]}}"""
        "client_search_cp" in url -> """{"code":0,"data":{"song":{"list":[]}}}"""
        "music.163.com" in url -> """{"code":200,"result":{"songCount":0}}"""
        "search.kuwo.cn" in url -> """{'TOTAL':'0','abslist':[]}"""
        else -> throw AssertionError("Unexpected request: $url")
    }
}
