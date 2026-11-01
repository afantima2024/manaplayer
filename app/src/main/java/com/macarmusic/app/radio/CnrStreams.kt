package com.macarmusic.app.radio

import com.macarmusic.app.data.BoundedInput
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal class CnrStreams(
    private val request: () -> String = ::fetchCatalogue,
    private val clock: () -> Long = System::nanoTime,
) {
    private var streams = emptyMap<String, String>()
    private var fetchedAt: Long? = null

    @Synchronized fun resolve(address: String): String {
        val station = CnrRadio.manifestStation(address) ?: return address
        val time = clock()
        if (fetchedAt == null || time - checkNotNull(fetchedAt) >= TimeUnit.MINUTES.toNanos(1)) {
            streams = parse(request())
            fetchedAt = time
        }
        return streams[station.id] ?: throw IOException("CNR no longer lists this station")
    }

    companion object {
        const val CATALOGUE = "https://apppc.cnr.cn/national"
        private const val MAX_BYTES = 256 * 1024

        fun parse(text: String): Map<String, String> {
            if (text.length > MAX_BYTES) throw IOException("CNR catalogue exceeds the size limit")
            try {
                val root = Json.parseToJsonElement(text) as? JsonObject
                    ?: throw IOException("Invalid CNR catalogue")
                if ((root["code"] as? JsonPrimitive)?.contentOrNull != "200")
                    throw IOException("CNR catalogue is unavailable")
                val data = root["data"] as? JsonObject
                val categories = data?.get("categories") as? JsonArray
                val category = categories?.firstOrNull() as? JsonObject
                val stations = category?.get("detail") as? JsonArray
                    ?: throw IOException("CNR catalogue has no station list")
                return buildMap {
                    for (entry in stations) {
                        val variants = (entry as? JsonObject)?.get("other_info11") as? JsonArray ?: continue
                        for (variant in variants) {
                            val url = (((variant as? JsonObject)?.get("url")) as? JsonPrimitive)
                                ?.contentOrNull?.trim() ?: continue
                            val station = CnrRadio.manifestStation(url) ?: continue
                            if (!containsKey(station.id)) put(station.id, url)
                        }
                    }
                }.also { if (it.isEmpty()) throw IOException("CNR catalogue has no approved streams") }
            } catch (error: SerializationException) {
                throw IOException("Invalid CNR catalogue", error)
            }
        }

        private fun fetchCatalogue(): String {
            // The public CNR web player obtains expiring URLs here; never persist or manufacture them.
            val connection = URL(CATALOGUE).openConnection() as HttpsURLConnection
            try {
                connection.apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    instanceFollowRedirects = false
                    useCaches = false
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("User-Agent", "ManaMusicRadio")
                    setRequestProperty("Content-Type", "application/json")
                    setFixedLengthStreamingMode(2)
                }
                connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode != 200)
                    throw IOException("CNR catalogue HTTP ${connection.responseCode}")
                if (connection.contentLength > MAX_BYTES) throw IOException("CNR catalogue exceeds the size limit")
                return connection.inputStream.use { String(BoundedInput.read(it, MAX_BYTES), Charsets.UTF_8) }
            } finally {
                connection.disconnect()
            }
        }
    }
}
