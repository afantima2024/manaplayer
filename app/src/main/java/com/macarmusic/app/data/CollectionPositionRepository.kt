package com.macarmusic.app.data

import android.content.Context
import android.util.Log
import com.macarmusic.app.R
import com.macarmusic.app.model.CollectionPosition
import com.macarmusic.app.model.CollectionPositions
import com.macarmusic.app.model.PlaybackSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class CollectionPositionRepository(
    context: Context,
    scope: CoroutineScope,
    private val report: (String) -> Unit,
) {
    private val store = AtomicTextStore(File(context.filesDir, "collection-positions.json"))
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private var saved: List<CollectionPosition>? = null
    private var readError: Exception? = null

    private sealed interface Request {
        data class Read(val reply: CompletableDeferred<List<CollectionPosition>>) : Request
        data class Save(val value: CollectionPosition) : Request
        data class Merge(val values: List<CollectionPosition>, val reply: CompletableDeferred<Unit>) : Request
    }

    init {
        scope.launch {
            for (request in requests) {
                try {
                    readError?.let { throw IOException("Saved collection positions are unreadable", it) }
                    if (saved == null) {
                        try { saved = store.read()?.let(CollectionPositions::decode).orEmpty() }
                        catch (error: IOException) { readError = error; throw error }
                        catch (error: SerializationException) { readError = error; throw error }
                        catch (error: IllegalArgumentException) { readError = error; throw error }
                    }
                    when (request) {
                        is Request.Read -> request.reply.complete(checkNotNull(saved))
                        is Request.Save -> {
                            val current = checkNotNull(saved)
                            val previous = current.firstOrNull { it.collection == request.value.collection }
                            if (previous?.trackId != request.value.trackId || previous?.positionMs != request.value.positionMs) {
                                write(current.filterNot { it.collection == request.value.collection } + request.value)
                            }
                        }
                        is Request.Merge -> {
                            val merged = request.values.associateByTo(linkedMapOf()) { it.collection }
                            checkNotNull(saved).forEach { merged[it.collection] = it }
                            write(merged.values.toList())
                            request.reply.complete(Unit)
                        }
                    }
                } catch (error: IOException) {
                    failed(context, request, error)
                } catch (error: SerializationException) {
                    failed(context, request, error)
                } catch (error: IllegalArgumentException) {
                    failed(context, request, error)
                }
            }
        }
    }

    fun remember(snapshot: PlaybackSnapshot) {
        val position = CollectionPositions.capture(snapshot, System.currentTimeMillis()) ?: return
        check(requests.trySend(Request.Save(position)).isSuccess)
    }

    suspend fun read(): List<CollectionPosition> {
        val reply = CompletableDeferred<List<CollectionPosition>>()
        requests.send(Request.Read(reply))
        return reply.await()
    }

    suspend fun merge(positions: List<CollectionPosition>) {
        val reply = CompletableDeferred<Unit>()
        requests.send(Request.Merge(positions, reply))
        reply.await()
    }

    private fun write(values: List<CollectionPosition>) {
        require(values.size <= 100_000)
        store.write(CollectionPositions.encode(values))
        saved = values
    }

    private fun failed(context: Context, request: Request, error: Exception) {
        Log.e("CollectionPositions", "Could not read or save collection positions", error)
        when (request) {
            is Request.Read -> request.reply.completeExceptionally(error)
            is Request.Merge -> request.reply.completeExceptionally(error)
            is Request.Save -> report(context.getString(R.string.collection_position_failed, error.localizedMessage.orEmpty()))
        }
    }
}
