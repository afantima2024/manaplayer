package com.macarmusic.app.radio

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class RadioBundledCatalogTest {
    private fun sample() = File("src/main/resources/radio/sample-stations.m3u").inputStream().use { RadioList.parse(it) }

    @Test fun everySuppliedChannelAndCanonicalAddressIsIncludedWithoutDuplicatingIds() {
        val source = File("src/main/resources/radio/sample-stations.m3u")
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("4b0b705748e5ceacda8a35abfd264f1d409274a5acb585ee682ed0ab98a390f9", hash)
        val parsed = sample()
        assertEquals(424, parsed.entries.size)
        assertEquals(0, parsed.invalid)
        val bundled = RadioDefaults.bundled
        val addresses = bundled.flatMap { it.sources }.toSet()
        parsed.entries.forEach { assertTrue("Missing address for ${it.name}: ${it.uri}", it.uri in addresses) }
        val imported = RadioList.merge(emptyList(), parsed.entries, emptyMap()).programs
        assertTrue(bundled.map { it.id }.containsAll(imported.map { it.id }))
        assertEquals(bundled.size, bundled.distinctBy { it.id }.size)
        assertEquals(417, bundled.size)
        assertTrue(bundled.filter { it.builtin != null }.all { it.name.isEmpty() })
        RadioList.validate(bundled)
        println("Bundled stations=${bundled.size}, addresses=${addresses.size}, groups=" +
            bundled.groupingBy { RadioGroups.of(it).id }.eachCount())
        println("Unclassified: " + bundled.filter { RadioGroups.of(it) == RadioGroups.other }.map { it.name })
    }

    @Test fun existingImportsAndUserOverridesKeepTheirIdsAddressesNamesAndSelectedSources() {
        val imported = RadioList.merge(emptyList(), sample().entries, emptyMap()).programs
        val original = imported.first { it.builtin == null }
        val edited = original.copy(name = "My retained name", uri = "https://example.com/primary",
            alternateUris = listOf("https://example.com/secondary"), sourceIndex = 1, group = "My retained group")
        val saved = imported.map { if (it.id == edited.id) edited else it }
        val catalog = RadioList.catalog(saved, RadioDefaults.bundled)
        assertEquals(RadioDefaults.bundled.size, catalog.size)
        assertEquals(edited, catalog.single { it.id == edited.id })
        saved.forEach { assertEquals(it, catalog.single { entry -> entry.id == it.id }) }
    }

    @Test fun importedAddressesMatchBundledChannelsAndResetKeepsGenuinelyAddedChannels() {
        val defaults = RadioDefaults.bundled
        val original = defaults.single { it.name == "RTHK Radio 1" }
        val result = RadioList.merge(emptyList(), listOf(RadioListEntry(original.name, "https://example.com/new")),
            emptyMap(), defaults = defaults)
        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        assertEquals(original.id, result.programs.single().id)
        assertEquals(listOf("https://example.com/new"), result.programs.single().sources)
        val extra = RadioList.merge(result.programs, listOf(RadioListEntry("My new channel", "https://example.com/custom")),
            emptyMap(), defaults = defaults).programs
        val reset = RadioList.resetBuiltins(extra, defaults)
        assertEquals(listOf("My new channel"), reset.map { it.name })
        assertEquals(original, RadioList.catalog(reset, defaults).single { it.id == original.id })
        assertEquals(defaults.size + 1, RadioList.catalog(reset, defaults).size)
    }

    @Test fun regionalAndInternationalNamesAreCategorizedWhileExplicitUserGroupsWin() {
        val cases = mapOf(
            "巴渝之声" to "chongqing", "兵团广播" to "xinjiang", "楚天音乐" to "hubei",
            "沉阳音乐广播" to "liaoning", "第一财经" to "shanghai", "龙广乡村" to "heilongjiang",
            "呼伦贝尔交通" to "inner mongolia", "黔西南综合" to "guizhou", "香格里拉" to "yunnan",
            "DRHK" to "hong kong", "RTHK Radio 3" to "hong kong", "TDM Radio Macau FM98" to "macao",
            "BCC中广音乐" to "taiwan", "Capital FM" to "singapore", "CNA938" to "singapore",
            "NPR News" to "united states", "KEXP" to "united states", "Classic FM" to "united kingdom",
            "Times Radio" to "united kingdom", "CRI Easy FM" to "national", "China Plus Radio" to "national",
            "轻松调频" to "national", "Power FM" to "united states", "Unknown radio" to "other",
        )
        val template = RadioDefaults.bundled.first { it.builtin == null }
        cases.forEach { (name, group) ->
            assertEquals(name, group, RadioGroups.of(template.copy(name = name, group = "")).id)
        }
        val imported = RadioGroups.of(template.copy(name = "RTHK Radio 1", group = "My travel channels"))
        assertEquals("import:my travel channels", imported.id)
        assertEquals("My travel channels", imported.imported)
    }
}
