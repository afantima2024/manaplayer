package com.macarmusic.app.radio

import android.content.Context
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.macarmusic.app.R
import java.net.URI

data class RadioStation(
    val channel: Int, val code: String, @StringRes val title: Int, val logoUri: String,
    val network: String = "cnr", private val address: String? = null,
) {
    val id: String get() = "radio:$network:$code"
    val uri: String get() = address ?: "https://ngcdn${channel.toString().padStart(3, '0')}.cnr.cn/live/$code/index.m3u8"
    val program: RadioProgram get() = RadioProgram(id, "", uri)

    fun mediaItem(context: Context): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMimeType(MimeTypes.APPLICATION_M3U8)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(context.getString(title))
            .setArtist(context.getString(R.string.radio_cnr))
            .setAlbumTitle(context.getString(R.string.radio_live))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build())
        .build()
}

object CnrRadio {
    val stations: List<RadioStation> = listOf(
        RadioStation(1, "zgzs", R.string.radio_cnr_1, "https://cnvod.cnr.cn/audio2017/ondemand/media/1100/20210425/1619317448858.jpg"),
        RadioStation(2, "jjzs", R.string.radio_cnr_2, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20230315/1678868245687.png"),
        RadioStation(3, "yyzs", R.string.radio_cnr_3, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20191224/1577155745280.png"),
        RadioStation(4, "dszs", R.string.radio_cnr_4, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20200108/1578449381052.png"),
        RadioStation(5, "zhzs", R.string.radio_cnr_5, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20230612/1686554758307.png"),
        RadioStation(6, "szzs", R.string.radio_cnr_6, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20230612/1686554715652.png"),
        RadioStation(7, "hxzs", R.string.radio_cnr_7, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20230612/1686554680065.png"),
        RadioStation(8, "xgzs", R.string.radio_cnr_8, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20230612/1686554792374.png"),
        RadioStation(9, "mzzs", R.string.radio_cnr_9, "https://cnvod.cnr.cn/audio2016/default/2014/9/18/1411026604061.jpg"),
        RadioStation(10, "wyzs", R.string.radio_cnr_10, "https://cnvod.cnr.cn/audio2016/default/2014/9/18/1411025674178.jpg"),
        RadioStation(11, "lnzs", R.string.radio_cnr_11, "https://cnvod.cnr.cn/audio2016/default/2014/9/18/1411025684693.jpg"),
        RadioStation(12, "zygb", R.string.radio_cnr_12, "https://cnvod.cnr.cn/audio2016/default/2014/9/18/1411025710246.jpg"),
        RadioStation(13, "wygb", R.string.radio_cnr_13, "https://cnvod.cnr.cn/audio2016/default/2014/9/18/1411025697139.jpg"),
        RadioStation(16, "gsgljtgb", R.string.radio_cnr_16, "https://cnvod.cnr.cn/audio2017/ondemand/img/1100/20221211/1670743148470.jpg"),
        RadioStation(17, "xczs", R.string.radio_cnr_17, "https://cnvod.cnr.cn/audio2017/ondemand/media/1100/20210425/1619318057428.jpg"),
        RadioStation(25, "hygb", R.string.radio_cnr_25, "https://cnvod.cnr.cn/audio2016/default/2014/12/29/1419837253068.jpg"),
    )
    private val byId = stations.associateBy { it.id }
    private val byHost = stations.associateBy { URI(it.uri).host }
    fun find(id: String?): RadioStation? = byId[id]

    fun queue(station: RadioStation, favorites: Set<String>, favoritesOnly: Boolean): List<RadioStation> =
        if (favoritesOnly) stations.filter { it.id in favorites || it.id == station.id } else stations

    fun manifestStation(value: String): RadioStation? = address(value)?.let { uri ->
        byHost[uri.host.lowercase(java.util.Locale.ROOT)]?.takeIf { uri.path == URI(it.uri).path }
    }

    fun allowedRequest(value: String): Boolean = address(value) != null

    private fun address(value: String): URI? = try {
        val uri = URI(value)
        val host = uri.host?.lowercase(java.util.Locale.ROOT).orEmpty()
        val station = byHost[host]
        uri.takeIf {
            station != null && uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null &&
                uri.port in listOf(-1, 443) && uri.rawFragment == null && uri.normalize() == uri &&
                uri.rawPath.startsWith("/live/${station.code}/") && !uri.rawPath.contains('%')
        }
    } catch (_: java.net.URISyntaxException) {
        null
    }
}
