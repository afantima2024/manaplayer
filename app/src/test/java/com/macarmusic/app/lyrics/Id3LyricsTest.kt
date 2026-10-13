package com.macarmusic.app.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class Id3LyricsTest {
    @Test fun version23Utf8UsltIsRead() {
        assertEquals("[00:01]Synthetic lyric", Id3Lyrics.read(tag(3, "[00:01]Synthetic lyric").inputStream()))
    }

    @Test fun version24SynchsafeFramesAreRead() {
        val text = "Synthetic lyric ".repeat(30).trim()
        assertEquals(text, Id3Lyrics.read(tag(4, text).inputStream()))
    }

    @Test fun utf16DescriptionAndLyricsAreRead() {
        val payload = byteArrayOf(1) + "eng".toByteArray() + "description".toByteArray(Charsets.UTF_16) +
            byteArrayOf(0, 0) + "Synthetic lyric".toByteArray(Charsets.UTF_16)
        assertEquals("Synthetic lyric", Id3Lyrics.read(wrap(3, payload).inputStream()))
    }

    @Test fun nonId3AudioDoesNotRequireParsingOrReadingTheWholeSong() {
        assertNull(Id3Lyrics.read("RIFF1234567890WAVE".byteInputStream()))
    }

    @Test(expected = IOException::class) fun truncatedFramesFailExplicitly() {
        Id3Lyrics.read(tag(3, "Synthetic").dropLast(3).toByteArray().inputStream())
    }

    @Test(expected = IOException::class) fun oversizedTagIsRejectedBeforeAllocation() {
        val header = byteArrayOf(73, 68, 51, 3, 0, 0) + size(3 * 1024 * 1024, true)
        Id3Lyrics.read(header.inputStream())
    }

    private fun tag(version: Int, text: String): ByteArray =
        wrap(version, byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray())

    private fun wrap(version: Int, payload: ByteArray): ByteArray {
        val frame = "USLT".toByteArray() + size(payload.size, version == 4) + byteArrayOf(0, 0) + payload
        return byteArrayOf(73, 68, 51, version.toByte(), 0, 0) + size(frame.size, true) + frame
    }

    private fun size(value: Int, sync: Boolean): ByteArray {
        val bits = if (sync) 7 else 8
        val mask = if (sync) 127 else 255
        return ByteArray(4) { (value ushr ((3 - it) * bits) and mask).toByte() }
    }
}
