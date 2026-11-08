package com.macarmusic.app.radio

import com.macarmusic.app.R
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InputStream
import java.util.UUID

@Serializable
enum class RadioSubscriptionProblem { NETWORK, HTTP, INVALID_LIST, LIMIT, INSECURE_REDIRECT }

@Serializable
data class RadioSubscriptionFailure(val problem: RadioSubscriptionProblem, val httpStatus: Int = 0)

class RadioSubscriptionException(val failure: RadioSubscriptionFailure, cause: Exception? = null) :
    IOException("Radio subscription ${failure.problem} (${failure.httpStatus})", cause)

@Serializable
data class RadioSubscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val programs: List<RadioProgram> = emptyList(),
    val checkedAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    val etag: String = "",
    val lastModified: String = "",
    val validatorUrl: String = "",
    val failure: RadioSubscriptionFailure? = null,
    val unavailableStations: Int = 0,
    val ignoredLogos: Int = 0,
)

data class RadioSubscriptionPreset(val title: Int, val description: Int, val url: String)
data class RadioSubscriptionDownload(
    val entries: List<RadioListEntry>? = null,
    val etag: String = "",
    val lastModified: String = "",
    val validatorUrl: String = "",
    val unavailableStations: Int = 0,
    val ignoredLogos: Int = 0,
)
enum class RadioSubscriptionResult { UPDATED, UNCHANGED, FAILED }
data class RadioSubscriptionProgress(val name: String, val completed: Int, val total: Int, val cancelling: Boolean = false)

object RadioSubscriptions {
    const val MAX_SUBSCRIPTIONS = 10
    const val MAX_CACHED_SOURCES = 5000
    const val CCTV_AUDIO_URL = "https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/radio.m3u"
    private const val PLAYLISTS = "https://raw.githubusercontent.com/AlonDrilich/radio-playlists/main"
    private val cctvAudioPath = Regex("/audio/cctv(?:[1-9][0-9]?|5plus|europe|america)_2\\.m3u8")
    private val cctvAudioHosts = setOf("piccpndali.v.myalicdn.com", "piccpndks.v.kcdnvip.com")
    private val cctvTitle = Regex("CCTV(?:-|\\s|\\d).*", RegexOption.IGNORE_CASE)
    private val cctvPublisherHeaders = listOf("#EXT-X-APP ", "#EXT-X-APTV-TYPE ", "#EXT-X-SUB-URL ")
    val presets = listOf(
        RadioSubscriptionPreset(R.string.radio_subscription_china, R.string.radio_subscription_china_help,
            "https://live.fanmingming.com/radio/m3u/index.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_hongkong, R.string.radio_subscription_directory_help,
            "$PLAYLISTS/countries/hk.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_taiwan, R.string.radio_subscription_directory_help,
            "$PLAYLISTS/countries/tw.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_us, R.string.radio_subscription_directory_help,
            "$PLAYLISTS/countries/us.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_uk, R.string.radio_subscription_directory_help,
            "$PLAYLISTS/countries/gb.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_world, R.string.radio_subscription_world_help,
            "$PLAYLISTS/top/top-500.m3u"),
        RadioSubscriptionPreset(R.string.radio_subscription_cctv, R.string.radio_subscription_cctv_help,
            CCTV_AUDIO_URL),
    )

    fun parsePlaylist(url: String, input: InputStream, checkCancelled: () -> Unit = {}): ParsedRadioList {
        if (url != CCTV_AUDIO_URL) return RadioList.parse(input, subscription = true, checkCancelled)
        val text = RadioList.readText(input, checkCancelled)
        if (!text.trimStart().takeWhile { !it.isWhitespace() }.equals("#EXTM3U", true))
            throw RadioListException(R.string.radio_list_bad_format)
        // APTV's three app headers are metadata, not HLS tags or instructions to follow another URL.
        val selected = buildString {
            append("#EXTM3U\n")
            var include = false
            for (raw in text.lineSequence()) {
                checkCancelled()
                val line = raw.trim()
                if (cctvPublisherHeaders.any { line.startsWith(it, true) }) continue
                if (line.startsWith("#EXT-X-", true)) throw RadioListException(R.string.radio_list_hls_manifest)
                if (line.startsWith("#EXTINF:", true))
                    include = RadioList.entryTitle(line)?.matches(cctvTitle) == true
                if (include) append(line).append('\n')
            }
        }
        val parsed = RadioList.parseText(selected, subscription = true, checkCancelled)
        val entries = parsed.entries.map { entry ->
            if (!isCctvAudio(entry.uri)) throw RadioListException(R.string.radio_list_bad_url)
            entry.copy(group = RadioGroups.CCTV_TELEVISION, logoUri = "", clearGroup = false, clearLogo = true)
        }
        return parsed.copy(entries = entries)
    }

    internal fun isCctvAudio(uri: String): Boolean {
        val address = RadioAddress.parse(uri) ?: return false
        return address.scheme.equals("https", true) && cctvAudioHosts.any { it.equals(address.host, true) } &&
            cctvAudioPath.matches(address.path.orEmpty())
    }

    fun address(value: String): String? {
        val url = RadioAddress.canonical(value) ?: return null
        return if (url.trimEnd('/') == "https://iptv.hacks.tools/content/fm-radio/m3u")
            "https://live.hacks.tools/radio/m3u/index.m3u" else url
    }

    fun validName(value: String) = value.isNotBlank() && value.length <= 80 && value.none(Char::isISOControl)

    fun validate(subscriptions: List<RadioSubscription>, selections: Map<String, String> = emptyMap()) {
        require(subscriptions.size <= MAX_SUBSCRIPTIONS) { "Too many radio subscriptions" }
        require(subscriptions.distinctBy { it.id }.size == subscriptions.size &&
            subscriptions.distinctBy { address(it.url) }.size == subscriptions.size) { "Duplicate radio subscription" }
        var sources = 0
        for (subscription in subscriptions) {
            require(UUID.fromString(subscription.id).toString() == subscription.id &&
                validName(subscription.name) && address(subscription.url) == subscription.url &&
                subscription.checkedAtMs >= 0 && subscription.updatedAtMs in 0..subscription.checkedAtMs &&
                subscription.unavailableStations in 0..RadioList.MAX_PROGRAMS &&
                subscription.ignoredLogos in 0..RadioList.MAX_PROGRAMS &&
                listOf(subscription.etag, subscription.lastModified).all(::validValidator) &&
                (subscription.validatorUrl.isEmpty() || RadioAddress.parse(subscription.validatorUrl) != null) &&
                (subscription.failure?.httpStatus ?: 0) in 0..599) { "Invalid saved radio subscription" }
            RadioList.validate(subscription.programs)
            sources += subscription.programs.sumOf { it.sources.size }
            if (sources > MAX_CACHED_SOURCES) throw RadioSubscriptionException(
                RadioSubscriptionFailure(RadioSubscriptionProblem.LIMIT))
        }
        require(selections.size <= RadioList.MAX_PROGRAMS && selections.all {
            RadioProgram.validId(it.key) && RadioAddress.parse(it.value) != null
        }) { "Invalid subscribed source selection" }
    }

    fun validValidator(value: String) = value.length <= 1024 && value.none(Char::isISOControl)

    fun failureMessage(failure: RadioSubscriptionFailure): Int = when (failure.problem) {
        RadioSubscriptionProblem.NETWORK -> R.string.radio_subscription_network_error
        RadioSubscriptionProblem.HTTP -> R.string.radio_subscription_http_error
        RadioSubscriptionProblem.INVALID_LIST -> R.string.radio_subscription_invalid_list
        RadioSubscriptionProblem.LIMIT -> R.string.radio_subscription_cache_limit
        RadioSubscriptionProblem.INSECURE_REDIRECT -> R.string.radio_subscription_insecure
    }

    fun programs(entries: List<RadioListEntry>, aliases: Map<String, String>): List<RadioProgram> =
        // Provider-specific IDs cannot join unrelated stations across different subscriptions.
        RadioList.merge(emptyList(), entries.map { it.copy(id = null) }, aliases,
            RadioImportMode.REPLACE_ALL, emptyList()).programs

    fun catalog(base: List<RadioProgram>, subscriptions: List<RadioSubscription>,
        selections: Map<String, String>,
        visitOwnership: ((RadioSubscription, RadioProgram, String) -> Unit)? = null): List<RadioProgram> {
        val contributes = subscriptions.any { it.enabled && it.programs.isNotEmpty() }
        if (!contributes && visitOwnership == null) {
            return if (selections.isEmpty()) base else base.map { withSelectedSource(it, selections) }
        }
        val all = base.associateByTo(linkedMapOf()) { it.id }
        val names = base.filter { it.name.isNotEmpty() }.groupBy { RadioList.key(it.name) }
            .filterValues { it.size == 1 }.mapValues { it.value.single().id }.toMutableMap()
        val addresses = mutableMapOf<String, String?>()
        fun index(station: RadioProgram) {
            for (source in station.sources) {
                val address = checkNotNull(RadioAddress.canonical(source))
                if (address !in addresses) addresses[address] = station.id
                else if (addresses[address] != station.id) addresses[address] = null
            }
        }
        base.forEach(::index)
        fun resolveId(station: RadioProgram): String {
            val sourceIds = station.sources.mapNotNull { addresses[RadioAddress.canonical(it)] }.distinct()
            return station.id.takeIf(all::containsKey) ?: names[RadioList.key(station.name)]
                ?: sourceIds.singleOrNull() ?: station.id
        }
        for (subscription in subscriptions) {
            if (!subscription.enabled) continue
            for (station in subscription.programs) {
                val id = resolveId(station)
                visitOwnership?.invoke(subscription, station, id)
                val old = all[id]
                val sources = (old?.sources.orEmpty() + station.sources).distinctBy(RadioAddress::canonical)
                if (sources.size > RadioList.MAX_SOURCES) throw RadioListException(R.string.radio_sources_too_many)
                val combined = (old ?: station).copy(id = id, uri = sources.first(), alternateUris = sources.drop(1),
                    sourceIndex = sources.indexOf(old?.streamUri ?: station.streamUri).coerceAtLeast(0))
                all[id] = old?.takeIf { it == combined } ?: combined
                if (station.name.isNotEmpty()) names[RadioList.key(station.name)] = id
                index(combined)
            }
        }
        if (visitOwnership != null) subscriptions.filterNot { it.enabled }.forEach { subscription ->
            subscription.programs.forEach { visitOwnership(subscription, it, resolveId(it)) }
        }
        if (contributes && all.size > RadioList.MAX_PROGRAMS) throw RadioListException(R.string.radio_list_too_many)
        return all.values.map { withSelectedSource(it, selections) }
    }

    private fun withSelectedSource(station: RadioProgram, selections: Map<String, String>): RadioProgram {
        val index = station.sources.indexOf(selections[station.id])
        return if (index < 0 || index == station.sourceIndex) station else station.copy(sourceIndex = index)
    }
}
