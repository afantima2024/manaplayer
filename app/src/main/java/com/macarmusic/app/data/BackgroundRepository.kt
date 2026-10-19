package com.macarmusic.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.macarmusic.app.R
import com.macarmusic.app.model.BackgroundImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

class BackgroundRepository(private val context: Context) {
    private val legacyImage = AtomicFile(File(context.filesDir, BackgroundImages.LEGACY))
    private val directory = File(context.filesDir, "backgrounds")
    private val mutex = Mutex()
    private val cached = LinkedHashMap<Triple<Long, String, Int>, Bitmap>(4, 0.75f, true)
    private val cacheLimit = minOf(12L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8)

    suspend fun <T> withImageFiles(options: DisplayOptions, block: suspend (Map<String, File>) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                block(options.backgroundImages.associateWith { id ->
                    if (id == BackgroundImages.LEGACY) {
                        legacyImage.openRead().close()
                        legacyImage.baseFile
                    } else {
                        require(BackgroundImages.isImportedName(id))
                        File(directory, id).also { if (!it.isFile) throw IOException(context.getString(R.string.background_invalid)) }
                    }
                })
            }
        }

    suspend fun <T> restoreImages(
        files: List<File>,
        preserveOnError: () -> Boolean,
        commit: suspend (List<String>) -> T,
    ): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException(context.getString(R.string.background_save_failed))
            val staged = mutableListOf<File>()
            var complete = false
            try {
                for (file in files) {
                    currentCoroutineContext().ensureActive()
                    validateBackupImage(file)
                    val destination = File(directory, "${UUID.randomUUID()}.png")
                    staged.add(destination)
                    file.inputStream().use { input -> destination.outputStream().use {
                        input.copyTo(it, 16_384)
                        it.fd.sync()
                    } }
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    val ids = staged.map { it.name }
                    val result = commit(ids)
                    complete = true
                    cached.clear()
                    directory.listFiles()?.filter { BackgroundImages.isImportedName(it.name) && it.name !in ids }
                        ?.forEach(::deletePrivateCopy)
                    legacyImage.delete()
                    result
                }
            } finally { if (!complete && !preserveOnError()) staged.forEach(::deletePrivateCopy) }
        }
    }

    fun validateBackupImage(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048 || bounds.outMimeType != "image/png") {
            throw IOException(context.getString(R.string.background_invalid))
        }
    }

    suspend fun importImages(uris: List<Uri>, save: suspend (List<String>) -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val selected = uris.distinct()
            if (selected.isEmpty()) throw IOException(context.getString(R.string.picker_no_document))
            if (selected.size > BackgroundImages.MAX_COUNT) {
                throw IOException(context.getString(R.string.background_too_many, BackgroundImages.MAX_COUNT))
            }
            selected.forEach { DocumentAccess(context).requireContentUri(it) }
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException(context.getString(R.string.background_save_failed))
            }
            val previous = directory.listFiles()?.filter { BackgroundImages.isImportedName(it.name) }
                ?: throw IOException(context.getString(R.string.background_save_failed))
            val staged = mutableListOf<File>()
            var committed = false
            try {
                for (uri in selected) {
                    currentCoroutineContext().ensureActive()
                    val file = File(directory, "${UUID.randomUUID()}.png")
                    staged.add(file)
                    importImage(uri, file)
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    save(staged.map { it.name })
                    committed = true
                    cached.clear()
                    previous.forEach(::deletePrivateCopy)
                    legacyImage.delete()
                }
            } finally {
                if (!committed) staged.forEach(::deletePrivateCopy)
            }
        }
    }

    private suspend fun importImage(uri: Uri, image: File) {
        val temporary = File(context.cacheDir, "background-${UUID.randomUUID()}.tmp")
        var decoded: Bitmap? = null
        var upright: Bitmap? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    var total = 0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 20 * 1024 * 1024) throw IOException(context.getString(R.string.background_too_large))
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException(context.getString(R.string.document_unavailable))
            decoded = decode(temporary, 1_280)
            val exif = ExifInterface(temporary)
            val transform = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            upright = if (transform.isIdentity) decoded else Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height, transform, true,
            )
            currentCoroutineContext().ensureActive()
            image.outputStream().use { output ->
                if (!upright.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException(context.getString(R.string.background_invalid))
                }
                output.fd.sync()
            }
        } finally {
            if (upright !== decoded) upright?.recycle()
            decoded?.recycle()
            if (temporary.exists() && !temporary.delete()) Log.w(TAG, "Temporary image cleanup failed")
        }
    }

    suspend fun render(options: DisplayOptions, index: Int = 0): Bitmap? = withContext(Dispatchers.Default) {
        if (!options.customTheme || options.imageVersion == 0L) return@withContext null
        mutex.withLock {
            val id = options.backgroundImages.getOrNull(index)
                ?: throw IOException(context.getString(R.string.background_invalid))
            val key = Triple(options.imageVersion, id, options.blur)
            cached[key]?.let { return@withLock it }
            val image = if (id == BackgroundImages.LEGACY) {
                withContext(Dispatchers.IO) { legacyImage.openRead().close() }
                legacyImage.baseFile
            } else {
                require(BackgroundImages.isImportedName(id))
                File(directory, id)
            }
            val bitmap = decode(image, if (options.blur == 0) 1_280 else 480)
            val rendered = if (options.blur == 0) bitmap else try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val coroutine = currentCoroutineContext()
                val blurred = BackgroundEffects.blur(pixels, bitmap.width, bitmap.height,
                    BackgroundEffects.radius(options.blur)) { coroutine.ensureActive() }
                Bitmap.createBitmap(blurred, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            } finally {
                bitmap.recycle()
            }
            currentCoroutineContext().ensureActive()
            cached[key] = rendered
            while (cached.size > 3 || cached.values.sumOf { it.allocationByteCount.toLong() } > cacheLimit) {
                cached.remove(cached.keys.first())
            }
            rendered
        }
    }

    private fun deletePrivateCopy(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "Previous private image cleanup failed: ${file.name}")
    }

    private fun decode(file: File, maximum: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException(context.getString(R.string.background_invalid))
        var sample = 1
        while (bounds.outWidth / sample > maximum || bounds.outHeight / sample > maximum) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException(context.getString(R.string.background_invalid))
    }

    private companion object { const val TAG = "BackgroundRepository" }
}
