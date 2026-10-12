package com.macarmusic.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.macarmusic.app.model.Track

fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString("fileName", fileName)
        putString("trackNumber", trackNumber)
        putString("year", year)
        putString("mimeType", mimeType)
        putLong("durationMs", durationMs)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setGenre(genre)
        .setDurationMs(durationMs.takeIf { it > 0 })
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setTrackNumber(trackNumber.substringBefore('/').toIntOrNull())
        .setReleaseYear(year.take(4).toIntOrNull())
        .setArtworkUri(Uri.parse(uri))
        .setExtras(extras)
        .build()
    return MediaItem.Builder().setMediaId(id).setUri(uri).setMediaMetadata(metadata).build()
}

fun MediaItem.toTrack(known: Track? = null): Track {
    val data = mediaMetadata
    val extras = data.extras
    val localUri = localConfiguration?.uri?.toString() ?: known?.uri.orEmpty()
    return (known ?: Track(
        id = mediaId,
        uri = localUri,
        fileName = extras?.getString("fileName").orEmpty(),
        title = data.title?.toString().orEmpty(),
    )).copy(
        uri = localUri,
        title = data.title?.toString()?.takeIf(String::isNotBlank) ?: known?.title.orEmpty(),
        artist = data.artist?.toString().orEmpty().ifBlank { known?.artist.orEmpty() },
        album = data.albumTitle?.toString().orEmpty().ifBlank { known?.album.orEmpty() },
        genre = data.genre?.toString().orEmpty().ifBlank { known?.genre.orEmpty() },
        durationMs = extras?.getLong("durationMs")?.takeIf { it > 0 } ?: known?.durationMs ?: 0,
        trackNumber = extras?.getString("trackNumber") ?: known?.trackNumber.orEmpty(),
        year = extras?.getString("year") ?: known?.year.orEmpty(),
        mimeType = extras?.getString("mimeType") ?: known?.mimeType.orEmpty(),
    )
}
