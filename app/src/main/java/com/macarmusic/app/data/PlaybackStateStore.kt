package com.macarmusic.app.data

import com.macarmusic.app.model.PlaybackEdits
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.model.PlaybackCollection
import com.macarmusic.app.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

internal class PlaybackStateStore(
    private val queueStore: TextStore,
    private val positionStore: TextStore,
) {
    data class Loaded(
        val snapshot: PlaybackSnapshot,
        val positionError: Exception? = null,
        val ignoredPosition: Boolean = false,
    )

    private var savedQueue: PlaybackSnapshot? = null
    private var generation: String? = null

    fun read(): Loaded {
        val text = queueStore.read()
        if (text == null) {
            if (positionStore.read() != null) throw IOException("Saved playback queue is missing")
            return Loaded(PlaybackSnapshot())
        }
        val record = json.decodeFromString<QueueRecord>(text)
        val snapshot = PlaybackEdits.normalized(record.snapshot())
        val revision = record.checkpointGeneration?.also {
            require(it.isNotBlank() && it.length <= 128) { "Invalid playback queue generation" }
        }
        savedQueue = snapshot
        generation = revision
        return try {
            val positionText = positionStore.read() ?: return Loaded(snapshot)
            // A legacy writer or interrupted queue replacement must never reuse another queue's index.
            if (revision == null) return Loaded(snapshot, ignoredPosition = true)
            val position = json.decodeFromString<Position>(positionText)
            require(position.version == 1) { "Unsupported playback position version" }
            require(position.generation.isNotBlank()) { "Invalid playback position generation" }
            if (position.generation != revision) Loaded(snapshot, ignoredPosition = true)
            else Loaded(PlaybackEdits.normalized(snapshot.copy(
                index = position.index,
                positionMs = position.positionMs,
                shuffle = position.shuffle,
                repeat = position.repeat,
            )))
        } catch (error: IOException) {
            Loaded(snapshot, positionError = error)
        } catch (error: SerializationException) {
            Loaded(snapshot, positionError = error)
        } catch (error: IllegalArgumentException) {
            Loaded(snapshot, positionError = error)
        }
    }

    fun write(snapshot: PlaybackSnapshot) {
        if (generation == null || savedQueue?.queue != snapshot.queue || savedQueue?.collection != snapshot.collection) {
            val next = UUID.randomUUID().toString()
            queueStore.write(json.encodeToString(QueueRecord(snapshot, next)))
            generation = next
        }
        // Share the live queue instead of retaining an equivalent, previously decoded copy.
        savedQueue = snapshot
        // Commit the queue first. Its embedded position is the recovery point if this write is interrupted.
        positionStore.write(json.encodeToString(Position(
            generation = checkNotNull(generation),
            index = snapshot.index,
            positionMs = snapshot.positionMs,
            shuffle = snapshot.shuffle,
            repeat = snapshot.repeat,
        )))
    }

    @Serializable
    private data class QueueRecord(
        val version: Int = 1,
        val queue: List<Track> = emptyList(),
        val index: Int = 0,
        val positionMs: Long = 0,
        val shuffle: Boolean = false,
        val repeat: Int = 0,
        val checkpointGeneration: String? = null,
        val collection: PlaybackCollection? = null,
    ) {
        constructor(snapshot: PlaybackSnapshot, generation: String) : this(
            snapshot.version, snapshot.queue, snapshot.index, snapshot.positionMs, snapshot.shuffle, snapshot.repeat, generation, snapshot.collection,
        )
        fun snapshot() = PlaybackSnapshot(version, queue, index, positionMs, shuffle, repeat, collection)
    }

    @Serializable
    private data class Position(
        val version: Int = 1,
        val generation: String,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeat: Int,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
