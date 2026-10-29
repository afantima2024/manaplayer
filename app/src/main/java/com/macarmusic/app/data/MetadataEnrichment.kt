package com.macarmusic.app.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.model.MetadataEdits
import com.macarmusic.app.model.MetadataProgress
import com.macarmusic.app.model.MetadataUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

internal class MetadataEnrichment(
    private val context: Context,
    private val repository: MusicRepository,
    scope: CoroutineScope,
    private val report: (String) -> Unit,
) {
    private val requests = Channel<Boolean>(Channel.CONFLATED)
    private val mutableProgress = MutableStateFlow<MetadataProgress?>(null)
    val progress = mutableProgress.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            requests.receiveAsFlow().collectLatest { run ->
                if (run) try {
                    enrich()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: IOException) {
                    failed(error)
                } catch (error: SecurityException) {
                    failed(error)
                } catch (error: IllegalArgumentException) {
                    failed(error)
                } catch (error: IllegalStateException) {
                    failed(error)
                } finally {
                    mutableProgress.value = null
                }
            }
        }
    }

    fun resume() { check(requests.trySend(true).isSuccess) }
    fun cancel() { check(requests.trySend(false).isSuccess) }

    private suspend fun enrich() {
        repository.awaitReady()
        if (!repository.isReadable) return
        var library = repository.library.value
        val pending = library.tracks.filter { it.metadataPending && !it.missing && !BackupEdits.unavailable(it.uri) }
        if (pending.isEmpty()) return
        mutableProgress.value = MetadataProgress(0, pending.size)
        // Let the first usable list/player frame render before opening audio files.
        delay(1_000)
        var known = library.tracks.associateBy { it.id }
        val updates = ArrayList<MetadataUpdate>(64)
        var lastCommit = SystemClock.elapsedRealtime()
        var completed = 0
        var warnings = 0
        suspend fun flush() {
            if (updates.isNotEmpty()) {
                repository.update { MetadataEdits.complete(it, updates) }
                updates.clear()
            }
            lastCommit = SystemClock.elapsedRealtime()
            mutableProgress.value = MetadataProgress(completed, pending.size)
        }
        try {
            for (requested in pending) {
                currentCoroutineContext().ensureActive()
                if (repository.library.value !== library) {
                    library = repository.library.value
                    known = library.tracks.associateBy { it.id }
                }
                if (MetadataEdits.matches(known[requested.id], requested)) {
                    val read = try {
                        repository.metadata.read(MetadataEdits.entry(requested), identity = requested.id)
                    } catch (error: IOException) {
                        if (MetadataEdits.matches(repository.library.value.tracks.firstOrNull { it.id == requested.id },
                            requested)) throw error
                        continue
                    } catch (error: SecurityException) {
                        if (MetadataEdits.matches(repository.library.value.tracks.firstOrNull { it.id == requested.id },
                            requested)) throw error
                        continue
                    }
                    currentCoroutineContext().ensureActive()
                    if (read.warning) warnings++
                    updates.add(MetadataUpdate(requested,
                        if (read.warning) requested.copy(metadataStamp = null) else read.track))
                }
                completed++
                if (updates.size >= 64 || SystemClock.elapsedRealtime() - lastCommit >= 1_500) flush()
            }
            flush()
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            flush()
            throw error
        } catch (error: SecurityException) {
            flush()
            throw error
        }
        if (warnings > 0) report(context.getString(R.string.metadata_background_warnings, warnings))
    }

    private fun failed(error: Exception) {
        Log.w("MetadataEnrichment", "Background details paused; discovered music references retained", error)
        report(context.getString(R.string.metadata_background_failed, error.localizedMessage.orEmpty()))
    }
}
