package com.macarmusic.app.radio

import com.macarmusic.app.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

object RadioBundledDefaults {
    const val REVISION = 2
    @Serializable private data class Source(val key: String, val url: String, val checkedAtMs: Long, val sha256: String)
    private val titles = linkedMapOf("world" to R.string.radio_subscription_world,
        "us" to R.string.radio_subscription_us, "tw" to R.string.radio_subscription_taiwan,
        "cctv" to R.string.radio_subscription_cctv)

    fun load(title: (Int) -> String): List<RadioSubscription> {
        val manifest = resource("manifest.json").use { it.readBytes().toString(Charsets.UTF_8) }
        val sources = Json.decodeFromString<List<Source>>(manifest)
        require(sources.map { it.key } == titles.keys.toList()) { "Unexpected bundled subscriptions" }
        return sources.map { source ->
            require(source.checkedAtMs > 0 && RadioSubscriptions.presets.any {
                it.title == titles.getValue(source.key) && it.url == source.url
            }) { "Invalid bundled subscription metadata" }
            val bytes = resource("${source.key}.m3u").use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(hash == source.sha256) { "Bundled subscription checksum mismatch" }
            val parsed = RadioSubscriptions.parsePlaylist(source.url, bytes.inputStream())
            require(parsed.invalid == 0 && parsed.entries.isNotEmpty()) { "Invalid bundled subscription playlist" }
            RadioSubscription(UUID.nameUUIDFromBytes(source.url.toByteArray(Charsets.UTF_8)).toString(),
                title(titles.getValue(source.key)), source.url,
                programs = RadioSubscriptions.programs(parsed.entries, emptyMap()),
                checkedAtMs = source.checkedAtMs, updatedAtMs = source.checkedAtMs,
                unavailableStations = parsed.unavailableStations, ignoredLogos = parsed.ignoredLogos)
        }.also { RadioSubscriptions.validate(it) }
    }

    fun install(saved: RadioCatalogSnapshot, defaults: List<RadioSubscription>): RadioCatalogSnapshot {
        if (saved.bundledRevision >= REVISION) return saved
        val previous = saved.subscriptions.associateBy { it.url }
        if (saved.bundledRevision > 0) {
            val added = defaults.filter { it.url == RadioSubscriptions.CCTV_AUDIO_URL && it.url !in previous }
            val subscriptions = saved.subscriptions + added
            RadioSubscriptions.validate(subscriptions, saved.sourceSelections)
            return saved.copy(subscriptions = subscriptions, bundledRevision = REVISION, initializeBundledSettings = true)
        }
        val subscriptions = defaults.map { bundled ->
            val old = previous[bundled.url]
            when {
                old == null -> bundled
                old.programs.isNotEmpty() && (old.updatedAtMs > bundled.checkedAtMs ||
                    old.failure == null && old.checkedAtMs > bundled.checkedAtMs) -> old.copy(enabled = true)
                else -> bundled.copy(id = old.id, name = old.name)
            }
        } + saved.subscriptions.filter { old -> defaults.none { it.url == old.url } }
        RadioSubscriptions.validate(subscriptions, saved.sourceSelections)
        return saved.copy(subscriptions = subscriptions, bundledRevision = REVISION, initializeBundledSettings = true)
    }

    private fun resource(name: String) = RadioBundledDefaults::class.java.getResourceAsStream("/radio/subscriptions/$name")
        ?: throw IOException("Missing bundled subscription: $name")
}
