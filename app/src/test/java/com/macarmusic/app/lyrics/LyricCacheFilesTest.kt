package com.macarmusic.app.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class LyricCacheFilesTest {
    private val key = "a".repeat(64)

    @Test fun legacyRecordsRemainReadable() {
        val data = """{"text":"[00:01.00]Synthetic lyric","origin":"KUGOU"}""".toByteArray()
        val parsed = LyricCacheFiles.decode(data)
        assertEquals(0L, parsed.savedAtMs)
        assertEquals(LyricOrigin.KUGOU, parsed.origin)
        assertNotNull(LyricsParser.parse(parsed.text))
    }

    @Test fun everyOnlineOriginRoundTripsWithoutLosingTextOrAge() {
        for (origin in OnlineLyricsClient.PROVIDERS) {
            val value = CachedLyric("[00:01.00]Synthetic \"quoted\" lyric\n[00:02.00]Second", origin, 10_000)
            assertEquals(value, LyricCacheFiles.decode(LyricCacheFiles.encode(value)))
        }
    }

    @Test fun cacheNamesNeverIncludeLocalLyricsOrUnrelatedFiles() {
        assertEquals(key, LyricCacheFiles.localKey("$key.json"))
        assertEquals(key, LyricCacheFiles.localKey("$key.json.bak"))
        assertEquals(key, LyricCacheFiles.localKey("$key.json.new"))
        for (name in listOf("song.lrc", "song.txt", "other.json", "../$key.json", "$key.json/more", "$key.lrc")) {
            assertNull(name, LyricCacheFiles.localKey(name))
            assertNull(name, LyricCacheFiles.portableKey(name))
        }
    }

    @Test fun portableRevisionsAreBoundedAndUnambiguous() {
        assertEquals(key to 1L, LyricCacheFiles.portableKey("$key-1.json"))
        assertEquals(key to Long.MAX_VALUE, LyricCacheFiles.portableKey("$key-${Long.MAX_VALUE}.json"))
        for (suffix in listOf("0", "-1", "01", "9223372036854775808", "abc", "1/2")) {
            assertNull(LyricCacheFiles.portableKey("$key-$suffix.json"))
        }
        assertNull(LyricCacheFiles.portableKey("cache-info.json"))
        assertNull(LyricCacheFiles.portableKey("$key.json"))
    }

    @Test fun malformedOrForeignRecordsAreRejected() {
        for (text in listOf("{}", "null", "{bad", """{"text":"hello","origin":"LOCAL_FILE"}""",
            """{"text":"hello","origin":"QQ","savedAtMs":-1}""",
            """{"text":"<html>error</html>","origin":"QQ"}""",
            """{"text":"hello","origin":"UNKNOWN"}""")) {
            assertThrows(IOException::class.java) { LyricCacheFiles.decode(text.toByteArray()) }
        }
    }

    @Test fun unknownFutureFieldsDoNotBreakExistingLyrics() {
        val value = LyricCacheFiles.decode("""{"text":"[00:00.00]Synthetic","origin":"QQ","future":"field"}""".toByteArray())
        assertEquals(LyricOrigin.QQ, value.origin)
    }

    @Test fun readsAndDecodingAreBoundedBeforeParsing() {
        val input = ByteArray(LyricCacheFiles.MAX_RECORD_BYTES + 1)
        assertThrows(IOException::class.java) { LyricCacheFiles.read(ByteArrayInputStream(input)) }
        assertThrows(IOException::class.java) { LyricCacheFiles.decode(input) }
        assertEquals(1024, LyricCacheFiles.read(ByteArrayInputStream(ByteArray(1024)), 1024).size)
    }

    @Test fun invalidUtf8IsNotSilentlyReplaced() {
        assertThrows(IOException::class.java) { LyricCacheFiles.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
    }

    @Test fun writesOnlyAcceptValidatedOnlineLyrics() {
        assertThrows(IOException::class.java) { LyricCacheFiles.encode(CachedLyric("hello", LyricOrigin.EMBEDDED)) }
        assertThrows(IOException::class.java) { LyricCacheFiles.encode(CachedLyric("", LyricOrigin.QQ)) }
        assertThrows(IllegalArgumentException::class.java) { LyricCacheFiles.requireKey("../somewhere") }
    }
}
