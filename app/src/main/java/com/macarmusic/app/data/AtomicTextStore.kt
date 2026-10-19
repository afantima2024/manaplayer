package com.macarmusic.app.data

import android.util.AtomicFile
import java.io.File
import java.io.IOException

interface TextStore {
    fun read(): String?
    fun write(text: String)
}

class AtomicTextStore(file: File) : TextStore {
    private val atomic = AtomicFile(file)

    @Synchronized
    override fun read(): String? {
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        return atomic.openRead().use { input ->
            if (input.channel.size() > MAX_BYTES) throw IOException("Saved data exceeds the safe size limit")
            input.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    @Synchronized
    override fun write(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Saved data exceeds the safe size limit")
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: IOException) {
            atomic.failWrite(output)
            throw error
        }
    }

    private companion object {
        const val MAX_BYTES = 64L * 1024 * 1024
    }
}
