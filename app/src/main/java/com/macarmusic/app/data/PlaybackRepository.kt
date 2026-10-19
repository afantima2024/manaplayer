package com.macarmusic.app.data

import android.content.Context
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.PlaybackEdits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class PlaybackRepository(
    context: Context,
    scope: CoroutineScope,
    private val report: (String) -> Unit,
    private val rememberCollection: (PlaybackSnapshot) -> Unit,
) {
    private val store = PlaybackStateStore(
        AtomicTextStore(File(context.filesDir, "playback.json")),
        AtomicTextStore(File(context.filesDir, "playback-position.json")),
    )
    private val ready = CompletableDeferred<Unit>()
    private val writes = Channel<PlaybackSnapshot>(Channel.CONFLATED)
    @Volatile private var current = PlaybackSnapshot()
    @Volatile private var writable = true
    @Volatile private var writeFailed = false

    init {
        scope.launch {
            try {
                val loaded = store.read()
                current = loaded.snapshot
                if (loaded.ignoredPosition) Log.w(TAG, "Ignored a position checkpoint from another queue generation")
                loaded.positionError?.let { error ->
                    writable = false
                    Log.e(TAG, "Unreadable position checkpoint preserved; using the queue recovery position", error)
                    report(context.getString(R.string.playback_position_load_failed, error.localizedMessage.orEmpty()))
                }
            } catch (error: IOException) {
                readFailure(context, error)
            } catch (error: SerializationException) {
                readFailure(context, error)
            } catch (error: IllegalArgumentException) {
                readFailure(context, error)
            } finally {
                ready.complete(Unit)
            }
            for (snapshot in writes) {
                try {
                    store.write(snapshot)
                    writeFailed = false
                } catch (error: IOException) {
                    writeFailed = true
                    Log.e(TAG, "Playback state write failed", error)
                    report(context.getString(R.string.playback_save_failed, error.localizedMessage.orEmpty()))
                }
            }
        }
    }

    suspend fun read(): PlaybackSnapshot {
        ready.await()
        return current
    }

    fun allowReplacingUnreadableQueue() {
        writable = true
    }

    suspend fun reconcile(library: Library) {
        val snapshot = read()
        val reconciled = withContext(Dispatchers.Default) { PlaybackEdits.reconcile(snapshot, library.tracks) }
        synchronized(this) {
            if (current === snapshot) save(reconciled)
            else Log.d(TAG, "A newer playback change superseded saved-queue reconciliation")
        }
    }

    @Synchronized fun save(snapshot: PlaybackSnapshot) {
        if (!ready.isCompleted || !writable || (snapshot == current && !writeFailed)) return
        if (current.collection != null && current.collection != snapshot.collection) rememberCollection(current)
        current = snapshot
        check(writes.trySend(snapshot).isSuccess)
    }

    private fun readFailure(context: Context, error: Exception) {
        writable = false
        Log.e(TAG, "Unreadable playback state preserved", error)
        report(context.getString(R.string.playback_load_failed, error.localizedMessage.orEmpty()))
    }

    private companion object {
        const val TAG = "PlaybackRepository"
    }
}
