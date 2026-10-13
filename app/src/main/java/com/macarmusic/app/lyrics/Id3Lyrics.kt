package com.macarmusic.app.lyrics

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Reads only bounded ID3 text frames; album art and audio data are never decoded here. */
object Id3Lyrics {
    fun read(input: InputStream): String? {
        val header = ByteArray(10)
        if (!readFully(input, header)) return null
        if (String(header, 0, 3, Charsets.US_ASCII) != "ID3") return null
        val version = header[3].toInt()
        if (version !in 2..4) return null
        val size = synchsafe(header, 6)
        if (size > 2 * 1024 * 1024) throw IOException("ID3 tag exceeds the lyric scan limit")
        val bytes = ByteArray(size)
        if (!readFully(input, bytes)) throw EOFException("Incomplete ID3 tag")
        if (version == 2 && header[5].toInt() and 0x40 != 0) return null
        val tag = if (version < 4 && header[5].toInt() and 0x80 != 0) deunsync(bytes) else bytes
        var position = 0
        if (version >= 3 && header[5].toInt() and 0x40 != 0) {
            if (tag.size < 4) throw IOException("Incomplete ID3 extended header")
            position = if (version == 4) synchsafe(tag, 0) else integer(tag, 0, 4) + 4
            if (position < 4 || position > tag.size) throw IOException("Invalid ID3 extended header")
        }
        val headerSize = if (version == 2) 6 else 10
        while (position <= tag.size - headerSize) {
            if (tag[position] == 0.toByte()) break
            val id = String(tag, position, if (version == 2) 3 else 4, Charsets.US_ASCII)
            val length = when (version) {
                2 -> integer(tag, position + 3, 3)
                3 -> integer(tag, position + 4, 4)
                else -> synchsafe(tag, position + 4)
            }
            val flags = if (version == 2) 0 else tag[position + 9].toInt() and 0xff
            position += headerSize
            if (length < 0 || length > tag.size - position) throw IOException("Invalid ID3 frame size")
            val unsupportedFlags = if (version == 3) 0xe0 else 0x4d
            if (id in setOf("USLT", "ULT") && flags and unsupportedFlags == 0) {
                if (length > LyricsParser.MAX_BYTES) throw IOException("Embedded lyrics exceed the size limit")
                var frame = tag.copyOfRange(position, position + length)
                if (version == 4 && (flags and 2 != 0 || header[5].toInt() and 0x80 != 0)) frame = deunsync(frame)
                decodeUslt(frame)?.takeIf(String::isNotBlank)?.let { return it }
            }
            position += length
        }
        return null
    }

    private fun decodeUslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val encoding = frame[0].toInt()
        val width = if (encoding in 1..2) 2 else 1
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        var end = 4
        while (end + width <= frame.size) {
            if (frame[end] == 0.toByte() && (width == 1 || frame[end + 1] == 0.toByte())) {
                val start = end + width
                val lyricCharset = if (encoding == 1 && start + 1 < frame.size &&
                    !(frame[start] == 0xff.toByte() || frame[start] == 0xfe.toByte())
                ) {
                    if (frame[4] == 0xff.toByte()) Charsets.UTF_16LE else Charsets.UTF_16BE
                } else charset
                return String(frame, start, frame.size - start, lyricCharset).trim('\u0000', '\uFEFF', ' ', '\n', '\r')
            }
            end += width
        }
        return null
    }

    private fun synchsafe(data: ByteArray, offset: Int): Int {
        var result = 0
        repeat(4) {
            val value = data[offset + it].toInt() and 0xff
            if (value and 0x80 != 0) throw IOException("Invalid ID3 size")
            result = (result shl 7) or value
        }
        return result
    }

    private fun integer(data: ByteArray, offset: Int, size: Int): Int {
        var value = 0L
        repeat(size) { value = (value shl 8) or (data[offset + it].toLong() and 0xff) }
        if (value > Int.MAX_VALUE) throw IOException("Invalid ID3 frame size")
        return value.toInt()
    }

    private fun deunsync(bytes: ByteArray): ByteArray {
        val output = ByteArray(bytes.size)
        var written = 0
        var previous = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            if (!(previous == 0xff && value == 0)) output[written++] = byte
            previous = value
        }
        return output.copyOf(written)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) return false
            offset += count
        }
        return true
    }
}
