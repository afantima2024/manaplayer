package com.macarmusic.app.radio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.macarmusic.app.R
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

@Serializable
data class RadioProgram(
    val id: String, val name: String, val uri: String, val group: String = "",
    val alternateUris: List<String> = emptyList(), val sourceIndex: Int = 0, val logoUri: String = "",
) {
    val builtin get() = RadioDefaults.find(id)
    @kotlinx.serialization.Transient val sources = listOf(uri) + alternateUris
    val streamUri get() = if (sourceIndex == 0) uri else alternateUris[sourceIndex - 1]
    val imageUri get() = logoUri.ifEmpty { builtin?.logoUri.orEmpty() }
    val hls get() = URI(streamUri).path.orEmpty().endsWith(".m3u8", true)
    val official get() = CnrRadio.manifestStation(streamUri) != null

    fun title(context: Context): String = name.ifEmpty { context.getString(checkNotNull(builtin).title) }
    fun publisher(context: Context): String = group.ifEmpty {
        context.getString(when {
            id == RadioDefaults.CRI_ID -> R.string.radio_cri
            builtin != null -> R.string.radio_cnr
            else -> R.string.radio_internet
        })
    }

    fun mediaItem(context: Context): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUri)
        .setTag(this)
        .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else null)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title(context)).setArtist(publisher(context))
            .setAlbumTitle(context.getString(R.string.radio_live))
            .setIsPlayable(true).setIsBrowsable(false).build())
        .build()

    companion object {
        fun isRadio(id: String?): Boolean = id?.startsWith("radio:") == true
        fun validId(id: String): Boolean = RadioDefaults.find(id) != null || CUSTOM_ID.matches(id)
        private val CUSTOM_ID = Regex("radio:custom:[a-f0-9]{64}")
    }
}

object RadioAddress {
    fun parse(value: String): URI? = try {
        URI(value).takeIf {
            value.length <= 8192 && value.none(Char::isISOControl) &&
                it.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !it.host.isNullOrBlank() && it.rawUserInfo == null && it.rawFragment == null &&
                (it.port == -1 || it.port in 1..65535)
        }
    } catch (_: URISyntaxException) { null }

    fun canonical(value: String): String? {
        val uri = parse(value.trim()) ?: return null
        // Old CNR playlists contain unsigned HTTP addresses and expired query tokens.
        val https = if (uri.scheme.equals("http", true)) "https:${value.trim().substringAfter(':')}"
            else value.trim()
        CnrRadio.manifestStation(https)?.let { return it.uri }
        val ascii = URI(uri.toASCIIString())
        val scheme = ascii.scheme.lowercase(Locale.ROOT)
        return buildString {
            append(scheme).append("://").append(ascii.host.lowercase(Locale.ROOT))
            if (ascii.port != -1 && !(scheme == "http" && ascii.port == 80 || scheme == "https" && ascii.port == 443))
                append(':').append(ascii.port)
            append(ascii.rawPath.ifEmpty { "/" })
            ascii.rawQuery?.let { append('?').append(it) }
        }
    }

    fun permits(value: String, allowHttp: Boolean): Boolean =
        parse(value)?.let { allowHttp || it.scheme.equals("https", true) } == true
}
