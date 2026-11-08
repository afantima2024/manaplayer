package com.macarmusic.app.radio

import com.macarmusic.app.model.AudioFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit

class CnrRadioTest {
    private val station = CnrRadio.stations.first()
    private fun catalogue(vararg urls: String): String =
        """{"code":"200","data":{"categories":[{"detail":[{"other_info11":[${
            urls.joinToString { """{"url":"$it"}""" }
        }]}]}]}}"""

    @Test fun onlySixteenDistinctCnrChannelsAreBuiltIn() {
        assertEquals(16, CnrRadio.stations.size)
        assertEquals(16, CnrRadio.stations.map { it.id }.toSet().size)
        assertEquals(16, CnrRadio.stations.map { it.uri }.toSet().size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 25),
            CnrRadio.stations.map { it.channel })
        for (station in CnrRadio.stations) {
            assertEquals(station, CnrRadio.find(station.id))
            assertEquals(station, CnrRadio.manifestStation(station.uri))
            assertTrue(CnrRadio.allowedRequest(station.uri))
            assertFalse(station.uri.contains('?'))
        }
    }

    @Test fun originalCnrAndCriLogosRemainRemoteReferencesAlongsideTheNewCctvMark() {
        val sources = File("src/main/assets/cnr-logo-sources.txt").readText()
        val remote = RadioDefaults.stations.filter { it.logoUri.isNotEmpty() }
        assertEquals(17, remote.map { it.logoUri }.toSet().size)
        for (station in remote) {
            assertFalse(File("src/main/res/drawable-nodpi/radio_cnr_${station.code}.webp").exists())
            assertTrue(sources.contains(station.logoUri))
            assertEquals(station.logoUri, station.program.imageUri)
        }
        assertEquals(9, RadioDefaults.pins.size)
        assertEquals(9, RadioDefaults.pins.distinct().size)
        assertEquals(RadioDefaults.CCTV_ID, RadioDefaults.pins.first())
        assertTrue(RadioDefaults.pins.all { RadioDefaults.find(it) != null })
        assertEquals("https://sk.cri.cn/905.m3u8", RadioDefaults.find(RadioDefaults.CRI_ID)?.uri)
    }

    @Test fun onlyKnownHttpsStreamPathsAreAllowedIncludingRedirectsAndSegments() {
        assertTrue(CnrRadio.allowedRequest(station.uri.substringBeforeLast('/') + "/123456.ts"))
        assertTrue(CnrRadio.allowedRequest(station.uri + "?key=public-test-value&time=123"))
        for (url in listOf(
            station.uri.replace("https:", "http:"),
            station.uri.replace("ngcdn001.cnr.cn", "ngcdn001.cnr.cn.evil.example"),
            station.uri.replace("ngcdn001.cnr.cn", "evilcnr.cn"),
            station.uri.replace("ngcdn001.cnr.cn", "127.0.0.1"),
            station.uri.replace("ngcdn001.cnr.cn", "localhost"),
            station.uri.replace("ngcdn001.cnr.cn", "ngcdn001.cnr.cn:444"),
            station.uri.replace("ngcdn001.cnr.cn", "user:password@ngcdn001.cnr.cn"),
            station.uri.replace("/index.m3u8", "/../index.m3u8"),
            station.uri.replace("/index.m3u8", "/%2e%2e/index.m3u8"),
            station.uri.replace("/zgzs/", "/other/"),
            station.uri + "#fragment", "content://music/track", "file:///test", "https://", "not a URL",
            "https://apppc.cnr.cn/national",
        )) assertFalse(url, CnrRadio.allowedRequest(url))
    }

    @Test fun favoriteQueueKeepsCatalogueOrderAndTheSelectedStation() {
        val favorite = CnrRadio.stations[5]
        assertEquals(CnrRadio.stations, CnrRadio.queue(station, emptySet(), false))
        assertEquals(listOf(station), CnrRadio.queue(station, emptySet(), true))
        assertEquals(listOf(station, favorite), CnrRadio.queue(station, setOf(favorite.id, "invalid"), true))
        assertEquals(listOf(favorite), CnrRadio.queue(favorite, setOf(favorite.id), true))
    }

    @Test fun publicCatalogueUsesIssuedUrlsButRejectsUnrelatedAndUnsafeSources() {
        val url = station.uri + "?key=public-fixture&time=123"
        assertEquals(mapOf(station.id to url), CnrStreams.parse(catalogue(
            "  $url  ", url, "https://sk.cri.cn/905.m3u8", "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8")))
        for (text in listOf("{}", "{", """{"code":"403"}""", catalogue("https://example.com/a.m3u8"),
            catalogue(station.uri.replace("https:", "http:")), " ".repeat(256 * 1024 + 1))) {
            try { CnrStreams.parse(text); fail("Invalid catalogue accepted") } catch (_: IOException) { }
        }
    }

    @Test fun catalogueIsLazySharedBrieflyAndRefetchedRatherThanPersistingExpiringUrls() {
        var requests = 0
        var now = 0L
        val streams = CnrStreams(request = {
            requests++
            catalogue(station.uri + "?key=fixture-$requests")
        }, clock = { now })
        assertEquals(0, requests)
        val segment = station.uri.substringBeforeLast('/') + "/123.ts"
        assertEquals(segment, streams.resolve(segment))
        assertEquals(0, requests)
        assertTrue(streams.resolve(station.uri).endsWith("fixture-1"))
        assertTrue(streams.resolve(station.uri + "?key=old").endsWith("fixture-1"))
        assertEquals(1, requests)
        now = TimeUnit.MINUTES.toNanos(1)
        assertTrue(streams.resolve(station.uri).endsWith("fixture-2"))
        assertEquals(2, requests)
        try { streams.resolve(CnrRadio.stations[1].uri); fail("Missing station accepted") } catch (_: IOException) { }
    }

    @Test fun stationSearchMatchesChineseEnglishAndPinyinInitials() {
        val title = "中国之声 Voice of China CNR 1"
        for (query in listOf("", "中国", "voice china", "zgzs", "CNR 1")) {
            assertTrue(query, AudioFiles.searchMatcher(query)(title))
        }
        assertFalse(AudioFiles.searchMatcher("经济")(title))
    }
}
