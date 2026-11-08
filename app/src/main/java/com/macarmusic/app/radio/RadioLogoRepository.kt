package com.macarmusic.app.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import com.macarmusic.app.MaCarMusicApp
import android.util.AtomicFile
import android.util.LruCache
import com.macarmusic.app.data.BoundedInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class RadioLogoRepository(context: Context) {
    enum class Download { SAVED, CACHED, NO_ADDRESS, CLEARED }
    private val directory = File(context.filesDir, "radio-logos")
    private val app = context.applicationContext as MaCarMusicApp
    private val gate = Mutex()
    private val downloads = Mutex()
    private var generation = 0L
    private val memory = object : LruCache<String, Bitmap>(1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val mutableVersion = MutableStateFlow(0L)
    val version = mutableVersion.asStateFlow()

    fun selected(station: RadioProgram): RadioStationLogo? = app.radio.options.value.stationLogos[station.id]
    fun identity(station: RadioProgram) = "${station.id}:${selected(station)?.name ?: station.imageUri}"

    suspend fun cached(station: RadioProgram): Bitmap? = withContext(Dispatchers.IO) {
        selected(station)?.let { logo ->
            return@withContext gate.withLock {
                val key = "bundled:${logo.name}"
                memory.get(key) ?: Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
                    requireNotNull(AppCompatResources.getDrawable(app, logo.image)).mutate().apply {
                        setBounds(0, 0, 128, 128)
                        draw(Canvas(bitmap))
                    }
                    memory.put(key, bitmap)
                }
            }
        }
        val url = station.imageUri.takeIf { it.isNotEmpty() } ?: return@withContext null
        gate.withLock {
            memory.get(url) ?: File(directory, key(url)).takeIf(::hasCache)?.let { file ->
                val bitmap = AtomicFile(file).openRead().use { decode(BoundedInput.read(it, MAX_DOWNLOAD)) }
                memory.put(url, bitmap)
                bitmap
            }
        }
    }

    suspend fun download(station: RadioProgram): Download = withContext(Dispatchers.IO) {
        if (selected(station) != null) return@withContext Download.CACHED
        val url = station.imageUri.takeIf { it.isNotEmpty() } ?: return@withContext Download.NO_ADDRESS
        downloads.withLock {
            val epoch = gate.withLock {
                if (hasCache(File(directory, key(url)))) return@withContext Download.CACHED
                generation
            }
            val bitmap = decode(fetch(url))
            try {
                currentCoroutineContext().ensureActive()
                gate.withLock {
                    if (generation != epoch) { bitmap.recycle(); return@withLock Download.CLEARED }
                    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create radio logo cache")
                    val atomic = AtomicFile(File(directory, key(url)))
                    val output = atomic.startWrite()
                    try {
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Cannot encode radio logo")
                        atomic.finishWrite(output)
                    } catch (error: IOException) {
                        atomic.failWrite(output)
                        throw error
                    }
                    memory.put(url, bitmap)
                    mutableVersion.value++
                    Download.SAVED
                }
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
        }
    }

    suspend fun clear(): Long = withContext(Dispatchers.IO) {
        gate.withLock {
            generation++
            memory.evictAll()
            val files = if (directory.exists()) directory.listFiles() ?: throw IOException("Cannot read radio logo cache")
                else emptyArray()
            var bytes = 0L
            try {
                for (file in files) {
                    if (!CACHE_FILE.matches(file.name) || !file.isFile) throw IOException("Unexpected radio cache file")
                    val size = file.length()
                    if (!file.delete()) throw IOException("Cannot remove cached radio logo")
                    bytes += size
                }
            } finally { mutableVersion.value++ }
            bytes
        }
    }

    private suspend fun fetch(address: String): ByteArray {
        var target = URL(address)
        val allowHttp = target.protocol.equals("http", true)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            if (!RadioAddress.permits(target.toString(), allowHttp)) throw IOException("Invalid radio logo URL")
            val connection = target.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "ManaMusicRadio")
                connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp")
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val next = URL(target, connection.getHeaderField("Location") ?: throw IOException("Missing logo redirect"))
                    if (target.protocol == "https" && next.protocol != "https") throw IOException("Insecure logo redirect")
                    target = next
                } else {
                    if (status != 200) throw IOException("Logo HTTP $status")
                    if (connection.contentLength > MAX_DOWNLOAD) throw IOException("Radio logo exceeds 512 KiB")
                    val coroutine = currentCoroutineContext()
                    return connection.inputStream.use {
                        BoundedInput.read(it, MAX_DOWNLOAD) { coroutine.ensureActive() }
                    }
                }
            } finally { connection.disconnect() }
        }
        throw IOException("Too many radio logo redirects")
    }

    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192 ||
            bounds.outMimeType !in setOf("image/png", "image/jpeg", "image/webp")) throw IOException("Unsupported radio logo")
        val options = BitmapFactory.Options().apply {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            inSampleSize = sample
        }
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("Invalid radio logo")
        val factor = minOf(1f, 128f / maxOf(original.width, original.height))
        if (factor == 1f) return original
        return Bitmap.createScaledBitmap(original, (original.width * factor).toInt().coerceAtLeast(1),
            (original.height * factor).toInt().coerceAtLeast(1), true).also { if (it !== original) original.recycle() }
    }

    private fun hasCache(file: File): Boolean = file.isFile || File(file.path + ".bak").isFile

    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) } + ".png"

    companion object {
        const val MAX_DOWNLOAD = 512 * 1024
        private val CACHE_FILE = Regex("[a-f0-9]{64}\\.png(?:\\.(?:bak|new))?")
    }
}
