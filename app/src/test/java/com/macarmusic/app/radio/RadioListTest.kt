package com.macarmusic.app.radio

import com.macarmusic.app.R
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class RadioListTest {
    private val builtin = CnrRadio.stations.first()
    private fun parse(text: String) = RadioList.parse(text.byteInputStream())
    private fun merge(text: String, saved: List<RadioProgram> = emptyList(),
        aliases: Map<String, String> = emptyMap()) = RadioList.merge(saved, parse(text).entries, aliases)
    private fun rejects(message: Int, action: () -> Unit) {
        try { action(); fail("Invalid list was accepted") }
        catch (error: RadioListException) { assertEquals(message, error.messageId) }
    }

    @Test fun utf8BomM3uKeepsChineseNamesGroupsQuotedCommasAndStableIds() {
        val list = parse("\uFEFF#EXTM3U\r\n# comment\r\n" +
            "#EXTINF:-1 tvg-id=\"${builtin.id}\" group-title=\"News, talk\",中国之声,直播\r\n${builtin.uri}\r\n")
        assertEquals(0, list.invalid)
        assertEquals(listOf(RadioListEntry("中国之声,直播", builtin.uri, builtin.id, "News, talk")), list.entries)
    }

    @Test fun txtAllowsCommasInsideUrlsAndOptionalTabSeparatedIdentity() {
        val list = parse("  My radio , https://example.com/a.mp3?channels=one,two \n" +
            "CNR new\t中国之声\thttps://example.com/live.m3u8\n# comment\n")
        assertEquals(0, list.invalid)
        assertEquals("https://example.com/a.mp3?channels=one,two", list.entries[0].uri)
        assertEquals("My radio", list.entries[0].name)
        assertEquals("CNR new", list.entries[1].id)
    }

    @Test fun legacyCnrUrlsUpgradeWithoutSavingExpiredSignatures() {
        val result = merge("China,${builtin.uri.replace("https:", "http:")}?key=expired&time=old")
        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        assertEquals(builtin.id, result.programs.single().id)
        assertEquals(builtin.uri, result.programs.single().uri)
        assertTrue(result.programs.single().official)
    }

    @Test fun namedAndExplicitUpdatesPreserveStableIdsAndAbsentStations() {
        val first = merge("New station,https://example.com/one.mp3\nSecond,https://example.com/two.m3u8")
        val id = first.programs.first().id
        val updated = merge(" NEW STATION ,https://example.com/replaced.mp3", first.programs)
        assertEquals(0, updated.added)
        assertEquals(1, updated.updated)
        assertEquals(id, updated.programs.first().id)
        assertEquals(first.programs.last(), updated.programs.last())
        val renamed = merge("$id\tRenamed\tHTTPS://example.com/latest.mp3", updated.programs)
        assertEquals(1, renamed.updated)
        assertEquals(id, renamed.programs.first().id)
        assertEquals("Renamed", renamed.programs.first().name)
        assertEquals(RadioDefaults.corePrograms.size + 2, RadioList.catalog(renamed.programs).size)
    }

    @Test fun externalM3uIdsRemainStableWhenNamesChange() {
        val first = merge("provider-1\tOriginal\thttps://example.com/a")
        val next = merge("provider-1\tRenamed\thttps://example.com/b", first.programs)
        assertEquals(first.programs.single().id, next.programs.single().id)
        assertEquals(1, next.updated)
    }

    @Test fun builtinAliasesAndIdsOverrideAddressesAndResetKeepsImportedEntries() {
        val result = merge("中国之声,https://example.com/replacement.m3u8\nCustom,https://example.com/custom.mp3",
            aliases = mapOf(RadioList.key("中国之声") to builtin.id))
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        val overridden = RadioList.catalog(result.programs).first()
        assertEquals(builtin.id, overridden.id)
        assertFalse(overridden.official)
        val custom = result.programs.last()
        val reset = RadioList.resetBuiltins(result.programs)
        assertEquals(listOf(custom), reset)
        assertEquals(builtin.program, RadioList.catalog(reset).first())
        val favoriteQueue = RadioCatalogState(reset).queue(custom, setOf(builtin.id, custom.id), true)
        assertEquals(listOf(builtin.program, custom), favoriteQueue)
    }

    @Test fun repeatedChannelsBecomeSourcesAndCountsDescribeUniqueChanges() {
        val original = merge("One,https://example.com/one\nTwo,https://example.com/two")
        val result = merge("One,https://example.com/changed\nOne,https://example.com/one\n" +
            "New,https://example.com/first\nNew,https://example.com/last", original.programs)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(0, result.unchanged)
        assertEquals(2, result.duplicates)
        assertEquals(listOf("https://example.com/first", "https://example.com/last"), result.programs.last().sources)
        assertEquals(original.programs[1], result.programs[1])
    }

    @Test fun previewReportsInvalidAndIgnoredLinesWithoutThrowingAwayValidEntries() {
        val list = parse("#EXTM3U\n#EXTINF:-1,Good\n#EXTVLCOPT:http-referrer=ignored\nhttps://example.com/live\n" +
            "#EXTINF:-1,Missing\n#EXTINF:-1,Bad\nfile:///private.wav\n#EXTINF:-1,Last")
        assertEquals(1, list.entries.size)
        assertEquals(4, list.invalid)
        assertEquals(listOf(3, 5, 7, 8), list.issues.map { it.line })
        val many = parse(List(100) { "invalid" }.joinToString("\n"))
        assertEquals(100, many.invalid)
        assertEquals(20, many.issues.size)
        assertTrue(parse(" \n# comments only").entries.isEmpty())
    }

    @Test fun malformedAddressesCredentialsAndIdsAreNotAccepted() {
        for (url in listOf("file:///song", "content://private/music", "ftp://example.com/stream", "relative.mp3",
            "https://user:secret@example.com/a", "https://example.com/a#fragment", "https://example.com:0/live",
            "https://example.com:65536/live", "https://", "https://example.com/a b")) {
            assertNull(url, RadioAddress.canonical(url))
            assertEquals(url, 1, parse("Station,$url").invalid)
        }
        assertEquals(1, parse("radio:unknown\tStation\thttps://example.com/live").invalid)
        assertEquals(1, parse("${"x".repeat(257)},https://example.com/live").invalid)
        assertEquals(1, parse(",https://example.com/live").invalid)
        assertTrue(RadioAddress.permits("http://example.com/live", allowHttp = true))
        assertFalse(RadioAddress.permits("http://example.com/live", allowHttp = false))
        assertTrue(RadioAddress.permits("https://example.com/live", allowHttp = false))
    }

    @Test fun wrongEncodingHlsManifestsOversizeAndCancellationFailExplicitly() {
        rejects(R.string.radio_list_encoding) { RadioList.parse(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
        rejects(R.string.radio_list_hls_manifest) { parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\nsegment.ts") }
        rejects(R.string.radio_list_too_large) { RadioList.parse(ByteArrayInputStream(ByteArray(RadioList.MAX_BYTES + 1))) }
        rejects(R.string.radio_list_too_many) { parse(List(5001) { "Radio $it,https://example.com/live" }.joinToString("\n")) }
        try {
            RadioList.parse("One,https://example.com/live".byteInputStream()) { throw CancellationException() }
            fail("Cancellation ignored")
        } catch (_: CancellationException) { }
    }

    @Test fun persistenceRoundTripsAndRejectsDuplicateIdsInvalidSchemesAndVersions() {
        val programs = merge("One,https://example.com/a.m3u8\nTwo,http://example.com/live.mp3").programs
        val text = RadioList.encode(programs)
        assertEquals(RadioCatalogSnapshot(programs), RadioList.decode(text))
        assertTrue(programs.first().hls)
        assertFalse(programs.last().hls)
        for (invalid in listOf(text.replace("\"version\":6", "\"version\":999"),
            text.replace("https://example.com/a.m3u8", "file:///private"),
            text.replace(programs[1].id, programs[0].id))) {
            try { RadioList.decode(invalid); fail("Invalid stored list accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun appendAndReplaceKeepUnlistedStationsAndDeduplicateAddresses() {
        val original = merge("One,https://example.com/one\nTwo,https://example.com/two").programs
        val entries = parse("One,https://example.com/new\nOne,https://example.com/new\nOne,https://example.com/backup").entries
        val append = RadioList.merge(original, entries, emptyMap(), RadioImportMode.APPEND).programs
        assertEquals(listOf("https://example.com/one", "https://example.com/new", "https://example.com/backup"), append.first().sources)
        assertEquals(original.last(), append.last())
        val replace = RadioList.merge(append, entries, emptyMap(), RadioImportMode.REPLACE).programs
        assertEquals(listOf("https://example.com/new", "https://example.com/backup"), replace.first().sources)
        assertEquals(original.first().id, replace.first().id)
        assertEquals(original.last(), replace.last())
        val selected = append.first().copy(sourceIndex = 2)
        val kept = RadioList.merge(listOf(selected, append.last()), entries, emptyMap(), RadioImportMode.REPLACE)
        assertEquals(1, kept.programs.first().sourceIndex)
        assertEquals(selected.streamUri, kept.programs.first().streamUri)
    }

    @Test fun m3uLogoGroupsExtgrpAndTxtGenreHeadersAreImportedWithoutRequests() {
        val parsed = parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"regional\" group-title=\"My province\" " +
            "tvg-logo=\"https://example.com/logo.png\",Local\nhttps://example.com/a\n" +
            "#EXTINF:-1,Other\n#EXTGRP:Second group\nhttps://example.com/b")
        assertEquals("My province", parsed.entries[0].group)
        assertEquals("https://example.com/logo.png", parsed.entries[0].logoUri)
        assertEquals("Second group", parsed.entries[1].group)
        val txt = parse("浙江,#genre#\n杭州音乐,https://example.com/music\n北京,#genre#\n新闻,https://example.com/news")
        assertEquals(listOf("浙江", "北京"), txt.entries.map { it.group })
        assertEquals(1, parse("#EXTM3U\n#EXTINF:-1 tvg-logo=\"file:///private\",Bad\nhttps://example.com/a").invalid)
    }

    @Test fun oldSingleSourceCatalogMigratesAndMultisourceSelectionPersists() {
        val old = """{"version":1,"programs":[{"id":"${builtin.id}","name":"","uri":"${builtin.uri}","group":""}]}"""
        assertEquals(RadioCatalogSnapshot(listOf(builtin.program)), RadioList.decode(old))
        val new = builtin.program.copy(alternateUris = listOf("https://example.com/alternative"), sourceIndex = 1,
            logoUri = "https://example.com/logo.png", group = "Custom group")
        assertEquals(RadioCatalogSnapshot(listOf(new)), RadioList.decode(RadioList.encode(listOf(new))))
        assertFalse(new.official)
        assertFalse(new.hls)
        assertEquals("https://example.com/alternative", new.streamUri)
    }

    @Test fun sourceLimitsInvalidIndicesAndDuplicateAddressesAreRejected() {
        rejects(R.string.radio_sources_too_many) {
            merge((0..32).joinToString("\n") { "One,https://example.com/$it" })
        }
        for (bad in listOf(builtin.program.copy(sourceIndex = 1),
            builtin.program.copy(alternateUris = listOf(builtin.uri)),
            builtin.program.copy(logoUri = "file:///private"))) {
            try { RadioList.validate(listOf(bad)); fail("Invalid sources accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun criAddressRecognizesBothSuppliedPlaylistNamesAsOneBuiltin() {
        val result = merge("CRI 环球资讯广播 FM90.5,http://sk.cri.cn/905.m3u8\n环球资讯,https://sk.cri.cn/905.m3u8")
        assertEquals(0, result.added)
        assertEquals(RadioDefaults.CRI_ID, result.programs.single().id)
        assertEquals(2, result.programs.single().sources.size)
        assertTrue(RadioList.resetBuiltins(result.programs).isEmpty())
    }

    @Test fun mergeMatchesExistingNamesDespiteNewProviderIdsAndSkipsEquivalentAddresses() {
        val original = merge("provider-a\tMy station\thttps://example.com/live?token=One").programs
        val entries = parse("provider-b\tMY   STATION\tHTTPS://EXAMPLE.COM:443/live?token=One\n" +
            "provider-c\tMy station\thttps://example.com/live?token=Two").entries
        val result = RadioList.merge(original, entries, emptyMap(), RadioImportMode.APPEND)
        assertEquals(0, result.added)
        assertEquals(original.single().id, result.programs.single().id)
        assertEquals(listOf("https://example.com/live?token=One", "https://example.com/live?token=Two"),
            result.programs.single().sources)
        val differentName = parse("Other station,https://example.com/live?token=One").entries
        assertEquals(1, RadioList.merge(original, differentName, emptyMap(), RadioImportMode.APPEND).added)
        assertEquals("https://example.com/Live?token=One", RadioAddress.canonical("HTTPS://EXAMPLE.COM:443/Live?token=One"))
        assertEquals("http://[::1]/", RadioAddress.canonical("HTTP://[::1]:80"))
    }

    @Test fun replaceAllContainsOnlyImportedStationsAndSourcesButKeepsKnownIdentity() {
        val original = merge("Keep,https://example.com/one\nKeep,https://example.com/old\nDrop,https://example.com/drop").programs
        val edited = original.first().copy(group = "Old group", logoUri = "https://example.com/old.png", sourceIndex = 1)
        val result = RadioList.merge(listOf(edited, original.last()),
            parse("Keep,https://example.com/new\nKeep,https://example.com/new").entries, emptyMap(), RadioImportMode.REPLACE_ALL)
        assertEquals(original.first().id, result.programs.single().id)
        assertEquals(listOf("https://example.com/new"), result.programs.single().sources)
        assertEquals("", result.programs.single().group)
        assertEquals("", result.programs.single().logoUri)
        assertEquals(RadioDefaults.corePrograms.size + 1, result.removed)
        assertEquals(RadioCatalogSnapshot(result.programs, false), RadioList.decode(RadioList.encode(result.programs, false)))
        rejects(R.string.radio_import_no_stations) {
            RadioList.merge(original, emptyList(), emptyMap(), RadioImportMode.REPLACE_ALL)
        }
    }

    @Test fun m3uExportPreservesEverySourceAndQuotedMetadataAndCanClearExplicitFields() {
        val station = merge("First,https://example.com/a?parts=one,two\nFirst,https://example.com/b").programs.single()
            .copy(name = "广播 \"Music\", live", group = "Local \"News\", C:\\Radio",
                logoUri = "https://example.com/logo.png?theme=a&size=2", sourceIndex = 1)
        val bytes = RadioList.exportM3u(listOf(station), RadioProgram::name)
        val parsed = RadioList.parse(bytes.inputStream())
        assertEquals(0, parsed.invalid)
        assertEquals(station.sources, parsed.entries.map { it.uri })
        assertTrue(parsed.entries.all { it.id == station.id && it.name == station.name &&
            it.group == station.group && it.logoUri == station.logoUri })
        val aliases = emptyMap<String, String>()
        val restored = RadioList.merge(listOf(station), parsed.entries, aliases, RadioImportMode.REPLACE_ALL)
        assertEquals(listOf(station), restored.programs)
        val blank = station.copy(group = "", logoUri = "")
        val clearing = RadioList.parse(RadioList.exportM3u(listOf(blank), RadioProgram::name).inputStream())
        assertEquals(blank, RadioList.merge(listOf(station), clearing.entries, aliases,
            RadioImportMode.APPEND).programs.single())
    }

    @Test fun fullBundledExportRoundTripsWithoutDuplicatingSourcesOrCreatingUnchangedOverrides() {
        val defaults = RadioDefaults.bundled
        fun title(station: RadioProgram) = station.name.ifEmpty { "Station ${station.id}" }
        val aliases = defaults.filter { it.name.isEmpty() }.associate { RadioList.key(title(it)) to it.id }
        val output = RadioList.exportM3u(defaults, ::title)
        assertTrue(output.size <= RadioList.MAX_BYTES)
        val parsed = RadioList.parse(output.inputStream())
        assertEquals(0, parsed.invalid)
        assertEquals(defaults.sumOf { it.sources.size }, parsed.entries.size)
        val result = RadioList.merge(emptyList(), parsed.entries, aliases, RadioImportMode.APPEND, defaults)
        assertEquals(defaults.size, result.unchanged)
        assertEquals(0, result.added)
        assertTrue(result.programs.isEmpty())
        val replacement = RadioList.merge(emptyList(), parsed.entries, aliases, RadioImportMode.REPLACE_ALL, defaults)
        assertEquals(defaults, replacement.programs)
    }

    @Test fun exportedListsNeverSilentlyTruncateOrExceedTheParserLimits() {
        rejects(R.string.radio_export_empty) { RadioList.exportM3u(emptyList(), RadioProgram::name) }
        val many = List(200) {
            RadioProgram("radio:custom:${it.toString(16).padStart(64, '0')}", "Station $it",
                "https://example.com/$it?key=" + "a".repeat(6000))
        }
        rejects(R.string.radio_export_too_large) { RadioList.exportM3u(many, RadioProgram::name) }
        val sources = List(157) { index ->
            many[index].copy(uri = "https://example.com/$index/0",
                alternateUris = (1..31).map { "https://example.com/$index/$it" })
        }
        rejects(R.string.radio_export_too_large) { RadioList.exportM3u(sources, RadioProgram::name) }
        try {
            RadioList.exportM3u(listOf(many.first()), RadioProgram::name) { throw CancellationException() }
            fail("Cancelled export continued")
        } catch (_: CancellationException) { }
    }
}
