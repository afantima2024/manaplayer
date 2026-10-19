package com.macarmusic.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.lyrics.HttpsLyricsTransport
import com.macarmusic.app.lyrics.LyricQuery
import com.macarmusic.app.lyrics.LyricResponse
import com.macarmusic.app.lyrics.LyricsParser
import com.macarmusic.app.lyrics.LyricsTransport
import com.macarmusic.app.lyrics.OnlineLyricsClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class LiveLyricsSmokeTest {
    @Test fun publicMetadataCanRetrieveTimedLyricsOverAndroidHttps() {
        verifyQuery(LyricQuery("晴天", "周杰伦", 269_000))
    }

    @Test fun reportedNumberedTitleAndPromotionalArtistRetrieveTimedLyrics() {
        verifyQuery(LyricQuery("1562.如果你也听说-张惠妹", "小高网分享", 311_000))
    }

    @Test fun reportedDuetFilenameRetrievesTimedLyrics() {
        verifyQuery(LyricQuery("1542. 当爱已成往事-林忆莲_李宗盛.MP3", "小高网分享", 279_000))
    }

    @Test fun reportedCopiedFilenameRetrievesTimedLyrics() {
        verifyQuery(LyricQuery("1586.演员-薛之谦 - Copy (2).mp3", "", 261_000))
    }

    private fun verifyQuery(query: LyricQuery) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveLyrics") == "true")
        val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
        val failures = mutableListOf<String>()
        val responses = mutableListOf<String>()
        val transport = HttpsLyricsTransport(app.applicationScope)
        val client = OnlineLyricsClient(LyricsTransport { url, body, referer ->
            val response = transport.request(url, body, referer)
            val json = LyricResponse.json(response, singleQuoted = url.contains("search.kuwo.cn"))
            val data = json["data"] as? JsonObject
            val catalog = data?.get("info") as? JsonArray
                ?: (data?.get("song") as? JsonObject)?.get("list") as? JsonArray
            val candidates = json?.get("candidates") as? JsonArray
            val songs = (json?.get("result") as? JsonObject)?.get("songs") as? JsonArray
            responses.add("${url.substringBefore('?')}: status=${json?.get("status") ?: json?.get("code")}, " +
                "catalog=${catalog?.size}, candidates=${candidates?.size}, songs=${songs?.size}, " +
                "keyword=${Uri.parse(url).getQueryParameter("keyword")}, " +
                "error=${json["error_code"] ?: json["message"]}, keys=${json.keys}, dataKeys=${data?.keys}, " +
                "resultKeys=${(json["result"] as? JsonObject)?.keys}")
            response
        }) { provider, error ->
            failures.add("$provider: ${error.javaClass.simpleName}: ${error.message?.take(240)}")
        }
        val result = try {
            runBlocking { withTimeout(OnlineLyricsClient.TOTAL_TIMEOUT_MS) { client.find(query) } }
        } catch (error: IOException) {
            throw AssertionError("Live lookup failed; $failures; $responses", error)
        }
        val lyrics = requireNotNull(result?.text?.let(LyricsParser::parse)) { "No live result; $failures; $responses" }
        assertTrue(lyrics.timed)
        assertTrue(lyrics.lines.size > 5)
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "Live lyric provider=${result?.origin}, timedLines=${lyrics.lines.size}, requests=${responses.size}; lyric text not logged.\n")
        })
    }
}
