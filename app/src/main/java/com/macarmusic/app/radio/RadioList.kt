package com.macarmusic.app.radio

import com.macarmusic.app.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class RadioListEntry(
    val name: String, val uri: String, val id: String? = null, val group: String = "", val logoUri: String = "",
    val clearGroup: Boolean = false, val clearLogo: Boolean = false,
)
enum class RadioImportMode { APPEND, REPLACE, REPLACE_ALL }
data class RadioCatalogSnapshot(val programs: List<RadioProgram>, val includeDefaults: Boolean = true)
data class RadioListIssue(val line: Int, val message: Int)
data class ParsedRadioList(val entries: List<RadioListEntry>, val issues: List<RadioListIssue>, val invalid: Int)
class RadioListException(val messageId: Int) : IOException("Invalid radio list ($messageId)")
data class RadioListMerge(
    val programs: List<RadioProgram>, val added: Int, val updated: Int, val unchanged: Int, val duplicates: Int,
    val removed: Int = 0,
)

object RadioList {
    const val MAX_BYTES = 1024 * 1024
    const val MAX_PROGRAMS = 5000
    const val MAX_SOURCES = 32
    private const val MAX_SAVED_BYTES = 4 * MAX_BYTES
    private val json = Json { encodeDefaults = true }
    private val attributes = Regex("""([a-zA-Z][\w-]*)\s*=\s*"((?:\\.|[^"\\])*)"""")
    private val escapedAttribute = Regex("""\\(["\\])""")

    @Serializable private data class Saved(
        val version: Int = 3, val programs: List<RadioProgram>, val includeDefaults: Boolean = true,
    )

    fun parse(input: InputStream, checkCancelled: () -> Unit = {}): ParsedRadioList {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkCancelled()
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MAX_BYTES) throw RadioListException(R.string.radio_list_too_large)
            bytes.write(buffer, 0, count)
        }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: CharacterCodingException) { throw RadioListException(R.string.radio_list_encoding) }
        return parseText(text.removePrefix("\uFEFF"), checkCancelled)
    }

    private fun parseText(text: String, checkCancelled: () -> Unit): ParsedRadioList {
        val entries = mutableListOf<RadioListEntry>()
        val issues = mutableListOf<RadioListIssue>()
        var invalid = 0
        var pending: RadioListEntry? = null
        var pendingLine = 0
        var extended = false
        var textGroup = ""
        fun issue(line: Int, message: Int) {
            invalid++
            if (issues.size < 20) issues.add(RadioListIssue(line, message))
        }
        fun add(entry: RadioListEntry, line: Int) {
            val uri = RadioAddress.canonical(entry.uri)
            val name = entry.name.trim()
            val id = entry.id?.trim()?.takeIf(String::isNotEmpty)
            val problem = when {
                !validText(name, 256) || !validText(entry.group, 256, empty = true) -> R.string.radio_list_bad_name
                uri == null -> R.string.radio_list_bad_url
                entry.logoUri.isNotEmpty() && RadioAddress.parse(entry.logoUri) == null -> R.string.radio_list_bad_url
                id != null && (!validText(id, 256) || id.startsWith("radio:") && !RadioProgram.validId(id)) ->
                    R.string.radio_list_bad_id
                else -> null
            }
            if (problem != null) issue(line, problem) else {
                entries.add(entry.copy(name = name, uri = checkNotNull(uri), id = id, group = entry.group.trim()))
                if (entries.size > MAX_PROGRAMS) throw RadioListException(R.string.radio_list_too_many)
            }
        }
        for ((index, raw) in text.lineSequence().withIndex()) {
            checkCancelled()
            val lineNumber = index + 1
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-", true)) throw RadioListException(R.string.radio_list_hls_manifest)
            if (line.equals("#EXTM3U", true)) { extended = true; continue }
            if (line.startsWith("#EXTINF:", true)) {
                extended = true
                if (pending != null) issue(pendingLine, R.string.radio_list_missing_url)
                val comma = separator(line)
                if (comma < 0) {
                    pending = null
                    issue(lineNumber, R.string.radio_list_bad_format)
                } else {
                    val attrs = attributes.findAll(line.substring(0, comma)).associate {
                        it.groupValues[1].lowercase(Locale.ROOT) to escapedAttribute.replace(it.groupValues[2]) { match ->
                            match.groupValues[1]
                        }
                    }
                    pending = RadioListEntry(line.substring(comma + 1), "", attrs["tvg-id"],
                        attrs["group-title"].orEmpty(), attrs["tvg-logo"].orEmpty(),
                        clearGroup = attrs["group-title"] == "", clearLogo = attrs["tvg-logo"] == "")
                    pendingLine = lineNumber
                }
            } else if (line.startsWith("#EXTGRP:", true) && pending != null) {
                val group = line.substringAfter(':').trim()
                pending = pending.copy(group = group, clearGroup = group.isEmpty())
            } else if (line.startsWith('#')) {
                // Provider-specific request headers and playlist directives are never executed.
                if (line.startsWith("#EXTVLCOPT", true) || line.startsWith("#KODIPROP", true))
                    issue(lineNumber, R.string.radio_list_ignored_option)
            } else if (pending != null) {
                add(pending.copy(uri = line), lineNumber)
                pending = null
            } else if (extended) {
                issue(lineNumber, R.string.radio_list_bad_format)
            } else {
                val fields = if ('\t' in line) line.split('\t', limit = 3) else line.split(',', limit = 2)
                when (fields.size) {
                    2 -> if (fields[1].trim().equals("#genre#", true)) {
                        if (validText(fields[0], 256)) textGroup = fields[0].trim()
                        else issue(lineNumber, R.string.radio_list_bad_name)
                    } else add(RadioListEntry(fields[0], fields[1], group = textGroup), lineNumber)
                    3 -> add(RadioListEntry(fields[1], fields[2], fields[0], textGroup), lineNumber)
                    else -> issue(lineNumber, R.string.radio_list_bad_format)
                }
            }
        }
        if (pending != null) issue(pendingLine, R.string.radio_list_missing_url)
        return ParsedRadioList(entries, issues, invalid)
    }

    private fun separator(line: String): Int {
        var quoted = false
        var escaped = false
        for (index in line.indices) {
            if (escaped) { escaped = false; continue }
            if (quoted && line[index] == '\\') { escaped = true; continue }
            if (line[index] == '"') quoted = !quoted
            if (line[index] == ',' && !quoted) return index
        }
        return -1
    }

    fun key(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun merge(current: List<RadioProgram>, entries: List<RadioListEntry>, aliases: Map<String, String>,
        mode: RadioImportMode = RadioImportMode.REPLACE,
        defaults: List<RadioProgram> = RadioDefaults.corePrograms): RadioListMerge {
        validate(current)
        val all = catalog(current, defaults).associateByTo(linkedMapOf()) { it.id }
        val byName = all.values.filter { it.name.isNotEmpty() }.groupBy { key(it.name) }
            .filterValues { it.size == 1 }.mapValues { it.value.single().id }.toMutableMap()
        val replacing = mode == RadioImportMode.REPLACE_ALL
        if (replacing && entries.isEmpty()) throw RadioListException(R.string.radio_import_no_stations)
        val changes = (if (replacing) emptyList() else current).associateByTo(linkedMapOf()) { it.id }
        val original = all.toMap()
        val visited = hashSetOf<String>()
        var duplicates = 0
        for (entry in entries) {
            val uri = RadioAddress.canonical(entry.uri) ?: throw RadioListException(R.string.radio_list_bad_url)
            val explicit = entry.id?.let { if (RadioProgram.validId(it)) it else customId("id:$it") }
            val id = explicit?.takeIf(all::containsKey) ?: RadioDefaults.addressId(uri) ?:
                aliases[key(entry.name)] ?: byName[key(entry.name)] ?: explicit ?: customId("name:${key(entry.name)}")
            val repeated = !visited.add(id)
            if (repeated) duplicates++
            val previous = all[id].takeUnless { replacing && !repeated }
            val addresses = ((if (repeated || mode == RadioImportMode.APPEND) previous?.sources.orEmpty()
                else emptyList()) + uri).distinctBy(RadioAddress::canonical)
            if (addresses.size > MAX_SOURCES) throw RadioListException(R.string.radio_sources_too_many)
            val name = if (RadioDefaults.find(id) != null && aliases[key(entry.name)] == id) "" else entry.name
            val next = RadioProgram(id, name, addresses.first(),
                entry.group.ifEmpty { if (entry.clearGroup) "" else previous?.group.orEmpty() }, addresses.drop(1),
                addresses.indexOfFirst { RadioAddress.canonical(it) ==
                    (original[id]?.streamUri ?: previous?.streamUri)?.let(RadioAddress::canonical) }.coerceAtLeast(0),
                entry.logoUri.ifEmpty { if (entry.clearLogo) "" else previous?.logoUri.orEmpty() })
            if (replacing || next != original[id] || id in changes) changes[id] = next
            all[id] = next
            byName[key(entry.name)] = id
        }
        val programs = changes.values.toList()
        validate(programs)
        return RadioListMerge(programs, visited.count { it !in original },
            visited.count { it in original && original[it] != all[it] },
            visited.count { original[it] == all[it] }, duplicates,
            if (replacing) original.keys.count { it !in visited } else 0)
    }

    fun exportM3u(programs: List<RadioProgram>, title: (RadioProgram) -> String,
        checkCancelled: () -> Unit = {}): ByteArray {
        validate(programs)
        if (programs.isEmpty()) throw RadioListException(R.string.radio_export_empty)
        val output = ByteArrayOutputStream()
        fun append(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            if (output.size() + bytes.size > MAX_BYTES) throw RadioListException(R.string.radio_export_too_large)
            output.write(bytes)
        }
        fun attribute(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        append("#EXTM3U\n")
        var entries = 0
        for (station in programs) {
            checkCancelled()
            val name = title(station)
            require(validText(name, 256)) { "Invalid exported station name" }
            val header = "#EXTINF:-1 tvg-id=\"${station.id}\" group-title=\"${attribute(station.group)}\" " +
                "tvg-logo=\"${attribute(station.logoUri)}\",$name\n"
            for (source in station.sources) {
                checkCancelled()
                if (++entries > MAX_PROGRAMS) throw RadioListException(R.string.radio_export_too_large)
                append("$header$source\n")
            }
        }
        return output.toByteArray()
    }

    fun catalog(saved: List<RadioProgram>, defaults: List<RadioProgram> = RadioDefaults.corePrograms): List<RadioProgram> {
        val overrides = saved.associateByTo(linkedMapOf()) { it.id }
        return defaults.map { overrides.remove(it.id) ?: it } + overrides.values
    }

    fun resetBuiltins(saved: List<RadioProgram>, defaults: List<RadioProgram> = RadioDefaults.corePrograms): List<RadioProgram> {
        val ids = defaults.mapTo(hashSetOf()) { it.id }
        return saved.filter { it.id !in ids }
    }

    fun validate(programs: List<RadioProgram>) {
        if (programs.size > MAX_PROGRAMS) throw RadioListException(R.string.radio_list_too_many)
        require(programs.distinctBy { it.id }.size == programs.size) { "Duplicate radio station ID" }
        for (program in programs) {
            require(RadioProgram.validId(program.id) && validText(program.name, 256, empty = program.builtin != null) &&
                validText(program.group, 256, empty = true) && program.sources.size <= MAX_SOURCES &&
                program.sources.distinct().size == program.sources.size && program.sourceIndex in program.sources.indices &&
                program.sources.all { RadioAddress.parse(it) != null } &&
                (program.logoUri.isEmpty() || RadioAddress.parse(program.logoUri) != null)) { "Invalid saved radio station" }
        }
    }

    fun encode(programs: List<RadioProgram>, includeDefaults: Boolean = true): String {
        validate(programs)
        return json.encodeToString(Saved(programs = programs, includeDefaults = includeDefaults)).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_BYTES) { "Saved radio list is too large" }
        }
    }

    fun decode(value: String): RadioCatalogSnapshot {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_SAVED_BYTES) { "Saved radio list is too large" }
        val saved = json.decodeFromString<Saved>(value)
        require(saved.version in 1..3) { "Unsupported radio list version" }
        validate(saved.programs)
        return RadioCatalogSnapshot(saved.programs, saved.includeDefaults)
    }

    private fun validText(value: String, max: Int, empty: Boolean = false): Boolean =
        (empty || value.isNotBlank()) && value.length <= max && value.none(Char::isISOControl)

    private fun customId(value: String): String = "radio:custom:" +
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
