package com.macarmusic.app.lyrics

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricResponseTest {
    @Test fun legacyStringsRetainApostrophesQuotesAndEscapedBackslashes() {
        val parsed = LyricResponse.json(
            """{'name':'Singer\'s "song"','path':'C:\\fixture','abslist':[{"name":"Other's song"}]}""", true)
        assertEquals("Singer's \"song\"", parsed["name"]?.jsonPrimitive?.content)
        assertEquals("C:\\fixture", parsed["path"]?.jsonPrimitive?.content)
        assertEquals("Other's song", parsed["abslist"]?.jsonArray?.first()?.jsonObject?.get("name")?.jsonPrimitive?.content)
    }

    @Test fun numericEntitiesAndLegacySingerSeparatorsDecodeWithoutAnHtmlRenderer() {
        assertEquals("[00:01]One & Two\n", LyricResponse.decode("&#91;00&#58;01&#93;One &amp; Two&#10;"))
        assertEquals("A&B", LyricResponse.decode("""A\\u0026B"""))
        assertEquals("A&B", LyricResponse.decode("""A\u0026B"""))
        assertEquals("\"A\" 'B' C", LyricResponse.decode("&quot;A&quot;&nbsp;&apos;B&apos; C"))
        assertEquals("&#9999999; &#xd800;", LyricResponse.decode("&#9999999; &#xd800;"))
    }

    @Test fun malformedLegacyResponsesAreRejectedRatherThanEvaluatedOrSilentlyEmptied() {
        for (text in listOf("{'name':'unfinished}", "{'name':'bad\\'}", "{'value':call()}", "{'value':NaN}",
            "{'value':'bad\\x41'}", "[]", "{'value':true,}")) {
            try {
                LyricResponse.json(text, true)
                fail("Accepted invalid response: $text")
            } catch (_: IOException) { }
        }
    }

    @Test(expected = IOException::class) fun oversizedResponsesAreRejected() {
        LyricResponse.json(" ".repeat(1024 * 1024 + 1), true)
    }
}
