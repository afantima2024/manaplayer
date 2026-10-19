package com.macarmusic.app.lyrics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.LyricCacheExpiry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

data class LyricCacheInfo(
    val internalPath: String,
    val folder: LyricCacheFolder? = null,
    val invalidLocation: Boolean = false,
    val busy: Boolean = false,
    val unavailable: Boolean = false,
    val size: LyricCacheSize? = null,
) {
    val internal: Boolean get() = folder == null && !invalidLocation
}

internal data class CachedLyricRead(val value: CachedLyric? = null, val unavailable: Boolean = false)

class LyricCache internal constructor(
    private val context: Context,
    private val retainedReadUris: suspend () -> Set<String>,
    private val onProblem: (String) -> Unit,
    private val nowMs: () -> Long,
    private val grantMutex: Mutex,
    private val internalExpiry: () -> LyricCacheExpiry = { LyricCacheExpiry.THIRTY_DAYS },
    private val portableExpiry: () -> LyricCacheExpiry = { LyricCacheExpiry.NEVER },
) {
    private val preferences = context.getSharedPreferences("lyric_cache", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val internal = InternalLyricCache(File(context.cacheDir, "lyrics"), nowMs, internalExpiry)
    private val mutex = Mutex()
    private val epoch = AtomicLong()
    private val initial = readLocation()
    private val mutableInfo = MutableStateFlow(initial)
    val info = mutableInfo.asStateFlow()
    val generation: Long get() = epoch.get()
    private var external: SafLyricCache? = null

    private fun readLocation(): LyricCacheInfo {
        val saved = preferences.getString("folder", null) ?: return LyricCacheInfo(internal.path)
        return try {
            LyricCacheInfo(internal.path, Json.decodeFromString<LyricCacheFolder>(saved))
        } catch (error: SerializationException) {
            Log.w(TAG, "Invalid cache location; keeping it unavailable rather than changing storage", error)
            LyricCacheInfo(internal.path, invalidLocation = true, unavailable = true)
        }
    }

    private fun store(): LyricCacheStore {
        val state = info.value
        if (state.invalidLocation) throw IOException(context.getString(R.string.lyrics_cache_unavailable))
        val folder = state.folder ?: return internal
        return external ?: SafLyricCache(resolver, folder, nowMs, portableExpiry).also { external = it }
    }

    internal suspend fun read(key: String): CachedLyricRead = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val found = store().read(key)
                healthy()
                CachedLyricRead(found)
            } catch (error: IOException) { unavailable(error); CachedLyricRead(unavailable = true) }
            catch (error: SecurityException) { unavailable(error); CachedLyricRead(unavailable = true) }
            catch (error: IllegalArgumentException) { unavailable(error); CachedLyricRead(unavailable = true) }
            catch (error: UnsupportedOperationException) { unavailable(error); CachedLyricRead(unavailable = true) }
        }
    }

    internal suspend fun write(key: String, value: CachedLyric, expectedGeneration: Long): Boolean =
        update(key, expectedGeneration) { value } != null

    internal suspend fun retain(
        key: String, value: CachedLyric, expectedGeneration: Long, best: String? = null, changeBest: Boolean = false,
        preserveActive: Boolean = false,
    ): CachedLyric? = update(key, expectedGeneration) { active ->
        val previous = active.read(key)
        val version = value.version()
        val next = if (preserveActive && previous != null) previous.copy(
            versions = listOf(version) + value.versions + previous.versions,
            offlineSelected = true,
        ) else value
        val merged = LyricVersions.merge(next, previous, if (changeBest) best else previous?.bestVersionId, version.id)
        val retained = LyricVersions.expire(merged, if (info.value.internal) internalExpiry() else portableExpiry(), nowMs())
            ?: throw IOException("The lyric versions have expired")
        if (retained.version().id != version.id && retained.versions.none { it.id == version.id }) {
            throw IOException("The selected lyric version has expired")
        }
        retained
    }

    private suspend fun update(
        key: String, expectedGeneration: Long, value: (LyricCacheStore) -> CachedLyric,
    ): CachedLyric? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (expectedGeneration != generation) return@withLock null
                try {
                    val active = store()
                    val document = value(active)
                    currentCoroutineContext().ensureActive()
                    active.write(key, document)
                    if (document.selected || document.offlineSelected || document.bestVersionId != null) epoch.incrementAndGet()
                    healthy()
                    mutableInfo.value = info.value.copy(size = null)
                    document
                } catch (error: IOException) { unavailable(error); null }
                catch (error: SecurityException) { unavailable(error); null }
                catch (error: IllegalArgumentException) { unavailable(error); null }
                catch (error: UnsupportedOperationException) { unavailable(error); null }
            }
        }

    suspend fun inspect(): LyricCacheSize = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val active = store()
                val coroutine = currentCoroutineContext()
                active.pruneExpired { coroutine.ensureActive() }
                active.size().also { healthy(); mutableInfo.value = info.value.copy(size = it) }
            } catch (error: IOException) { unavailable(error); throw error }
            catch (error: SecurityException) { unavailable(error); throw error }
            catch (error: IllegalArgumentException) { unavailable(error); throw error }
            catch (error: UnsupportedOperationException) { unavailable(error); throw IOException(error) }
        }
    }

    suspend fun clear(): LyricCacheSize = change {
        epoch.incrementAndGet()
        try {
            store().clear().also { healthy(); mutableInfo.value = info.value.copy(size = LyricCacheSize()) }
        } catch (error: IOException) {
            unavailable(error)
            throw ActionFailure(context.getString(R.string.lyrics_cache_clear_failed), error)
        } catch (error: SecurityException) {
            unavailable(error)
            throw ActionFailure(context.getString(R.string.lyrics_cache_clear_failed), error)
        } catch (error: IllegalArgumentException) {
            unavailable(error)
            throw ActionFailure(context.getString(R.string.lyrics_cache_clear_failed), error)
        } catch (error: UnsupportedOperationException) {
            unavailable(error)
            throw ActionFailure(context.getString(R.string.lyrics_cache_clear_failed), error)
        }
    }

    suspend fun moveInternalTo(tree: Uri, flags: Int): LyricCacheSize = change {
        if (tree.scheme != "content" || tree.authority.isNullOrBlank() || !SafLyricCache.isTreeUri(tree)) {
            throw ActionFailure(context.getString(R.string.lyrics_cache_write_grant_needed))
        }
        val oldFolder = info.value.folder
        var previous = 0
        var adopted = false
        try {
            grantMutex.withLock {
                previous = grantFlags(tree)
                if (previous != READ_WRITE) {
                    if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 || flags and READ_WRITE != READ_WRITE) {
                        throw ActionFailure(context.getString(R.string.lyrics_cache_write_grant_needed))
                    }
                    resolver.takePersistableUriPermission(tree, READ_WRITE)
                }
                if (grantFlags(tree) != READ_WRITE) throw ActionFailure(context.getString(R.string.lyrics_cache_write_grant_needed))
            }
            val destination = SafLyricCache.prepare(resolver, tree, nowMs, portableExpiry)
            val keys = internal.keys()
            val size = internal.size()
            var copied = 0L
            for (key in keys) {
                currentCoroutineContext().ensureActive()
                // An orphan AtomicFile .new file was never committed and is not a cached lyric.
                val value = internal.stored(key) ?: continue
                destination.write(key, value)
                copied++
            }
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                saveLocation(destination.folder)
                adopted = true
                external = destination
                epoch.incrementAndGet()
                mutableInfo.value = LyricCacheInfo(internal.path, folder = destination.folder, busy = true)
                try { keys.forEach(internal::remove) }
                catch (error: IOException) {
                    throw ActionFailure(context.getString(R.string.lyrics_cache_move_cleanup_failed), error)
                } finally {
                    oldFolder?.takeIf { it.treeUri != tree.toString() }?.let {
                        release(Uri.parse(it.treeUri), READ_WRITE)
                    }
                }
            }
            size.copy(files = copied)
        } finally {
            if (!adopted) withContext(NonCancellable) {
                release(tree, READ_WRITE and previous.inv())
            }
        }
    }

    suspend fun useInternal() = change {
        val oldFolder = info.value.folder
        withContext(NonCancellable) {
            saveLocation(null)
            external = null
            epoch.incrementAndGet()
            mutableInfo.value = LyricCacheInfo(internal.path, busy = true)
            oldFolder?.let { release(Uri.parse(it.treeUri), READ_WRITE) }
        }
    }

    private suspend fun <T> change(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableInfo.value = info.value.copy(busy = true)
            try { action() }
            catch (error: ActionFailure) { throw error }
            catch (error: IOException) {
                throw ActionFailure(context.getString(R.string.lyrics_cache_operation_failed), error)
            }
            catch (error: UnsupportedOperationException) {
                throw ActionFailure(context.getString(R.string.lyrics_cache_provider_unsupported), error)
            } catch (error: SecurityException) {
                throw ActionFailure(context.getString(R.string.lyrics_cache_write_grant_needed), error)
            } catch (error: IllegalArgumentException) {
                throw ActionFailure(context.getString(R.string.lyrics_cache_operation_failed), error)
            } finally { mutableInfo.value = info.value.copy(busy = false) }
        }
    }

    private fun saveLocation(folder: LyricCacheFolder?) {
        val old = preferences.getString("folder", null)
        val value = folder?.let { Json.encodeToString(it) }
        if (!preferences.edit().putString("folder", value).commit()) {
            if (!preferences.edit().putString("folder", old).commit()) Log.w(TAG, "Could not restore cache preference")
            throw ActionFailure(context.getString(R.string.lyrics_cache_location_save_failed))
        }
    }

    private fun grantFlags(uri: Uri): Int {
        val grant = resolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return 0
        return (if (grant.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
            (if (grant.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
    }

    private suspend fun release(uri: Uri, requested: Int) = grantMutex.withLock {
        try {
            var flags = requested and grantFlags(uri)
            if (flags == 0) return@withLock
            if (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 && uri.toString() in retainedReadUris()) {
                flags = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION.inv()
            }
            if (flags != 0) resolver.releasePersistableUriPermission(uri, flags)
        }
        catch (error: SecurityException) {
            releaseFailure(error)
        } catch (error: IOException) {
            releaseFailure(error)
        } catch (error: IllegalArgumentException) {
            releaseFailure(error)
        }
    }

    private fun releaseFailure(error: Exception) {
        Log.w(TAG, "Keeping a cache-folder grant because safe release could not be established", error)
        onProblem(context.getString(R.string.lyrics_cache_grant_release_failed))
    }

    private fun healthy() {
        if (info.value.unavailable) mutableInfo.value = info.value.copy(unavailable = false)
    }

    private fun unavailable(error: Exception) {
        Log.w(TAG, "Lyric cache unavailable; playback is unchanged", error)
        external?.invalidate()
        val wasUnavailable = info.value.unavailable
        mutableInfo.value = info.value.copy(unavailable = true, size = null)
        if (!wasUnavailable) onProblem(context.getString(R.string.lyrics_cache_unavailable))
    }

    private companion object {
        const val TAG = "LyricCache"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }

    private class ActionFailure(message: String, cause: Throwable? = null) : IOException(message, cause)
}
