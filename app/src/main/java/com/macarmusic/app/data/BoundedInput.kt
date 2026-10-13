package com.macarmusic.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

object BoundedInput {
    fun read(input: InputStream, limit: Int, checkCancelled: () -> Unit = {}): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 16_384))
        val buffer = ByteArray(8_192)
        while (true) {
            checkCancelled()
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) throw IOException("Content exceeds the size limit")
            output.write(buffer, 0, count)
        }
    }
}
