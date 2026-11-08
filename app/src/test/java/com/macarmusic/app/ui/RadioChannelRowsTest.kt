package com.macarmusic.app.ui

import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioDefaults
import com.macarmusic.app.radio.RadioSection
import org.junit.Assert.*
import org.junit.Test

class RadioChannelRowsTest {
    private fun station(index: Int, group: String) = RadioProgram(
        "radio:custom:${index.toString(16).padStart(64, '0')}", "Station $index",
        "https://radio.example/$index.mp3", group,
    )
    private val first = station(1, "Group A")
    private val second = station(2, "Group A")
    private val third = station(3, "Group B")
    private val a = RadioGroups.of(first)
    private val b = RadioGroups.of(third)

    @Test fun initialRowsContainOnlySortedCollapsedGroupNames() {
        val rows = RadioChannelRows(listOf(third, first, second)).visible()
        assertEquals(listOf(a, b), rows.filter { it.section == null }.map { it.group })
        assertEquals(listOf(RadioSection.OTHER), rows.mapNotNull { it.section })
        assertTrue(rows.all { it.station == null && !it.expanded })
    }

    @Test fun switchingGroupsExposesOnlyTheRequestedGroupsStations() {
        val groups = RadioChannelRows(listOf(first, second, third))
        val openA = groups.visible(a.id)
        assertEquals(listOf(first, second), openA.mapNotNull { it.station })
        assertEquals(listOf(a), openA.filter { it.expanded }.map { it.group })
        val openB = groups.visible(b.id)
        assertEquals(listOf(third), openB.mapNotNull { it.station })
        assertEquals(listOf(b), openB.filter { it.expanded }.map { it.group })
        assertEquals(listOf(first, second), openA.mapNotNull { it.station })
        assertTrue(groups.visible().all { it.station == null && !it.expanded })
    }

    @Test fun defaultPinnedStationsStayInTheirOwnGroupsAlongsideUnpinnedStations() {
        val groups = RadioChannelRows(RadioDefaults.corePrograms)
        val ids = listOf(RadioGroups.national.id, RadioGroups.cctvTelevision.id)
            .flatMap { groups.visible(it).mapNotNull { it.station?.id } }
        assertTrue(ids.containsAll(RadioDefaults.pins))
        assertEquals(RadioDefaults.corePrograms.map { it.id }.toSet(), ids.toSet())
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun cctvHasAnIndependentCollapsedVisibleGroupBetweenNationalAndChina() {
        val television = (1..17).map { station(100 + it, "National").copy(
            uri = "https://piccpndali.v.myalicdn.com/audio/cctv${it}_2.m3u8") }
        val national = com.macarmusic.app.radio.CnrRadio.stations.first().program
        val source = listOf(station(200, "China"), station(201, "US"), station(202, "UK"), national) + television
        val rows = RadioChannelRows(source)
        assertEquals(listOf("national", "cctv television", "china", "united states", "united kingdom"),
            rows.visible().filter { it.section == null }.map { it.group.id })
        assertEquals(television, rows.visible("cctv television", setOf("national")).mapNotNull { it.station })
        assertTrue(rows.visible("cctv television", setOf("cctv television")).none { it.station != null })
        assertEquals(listOf(national), rows.visible("national", setOf("cctv television")).mapNotNull { it.station })
        assertEquals(television, rows.visible("cctv television").mapNotNull { it.station })
        assertFalse(RadioGroups.cctvTelevision.id in RadioGroups.defaultHiddenGroups(source))
    }

    @Test fun headerIdentityIsStableWhileExpandedStateChanges() {
        val groups = RadioChannelRows(listOf(first, second, third))
        val closed = groups.visible()
        val opened = groups.visible(a.id)
        assertEquals(closed.map { it.key }, opened.filter { it.station == null }.map { it.key })
        assertNotEquals(closed.first { it.group == a }, opened.first { it.group == a })
        assertEquals(opened.size, opened.map { it.key }.distinct().size)
    }

    @Test fun largeCollapsedCatalogDoesNotCreateHiddenStationRows() {
        val stations = List(5000) { station(it, "Group ${it % 40}") }
        val groups = RadioChannelRows(stations)
        assertEquals(41, groups.visible().size)
        val opened = groups.visible(RadioGroups.of(stations.first()).id)
        assertEquals(41 + 125, opened.size)
        assertEquals(125, opened.count { it.station != null })
        assertEquals(1, opened.count { it.expanded })
    }

    @Test fun emptyOrFilteredOutGroupsNeverRevealUnrelatedStations() {
        assertTrue(RadioChannelRows(emptyList()).visible(a.id).isEmpty())
        assertEquals(listOf(RadioChannelRow(b)), RadioChannelRows(listOf(third)).visible(a.id).filter { it.section == null })
        assertEquals(listOf(second), RadioChannelRows(listOf(second)).visible(a.id).mapNotNull { it.station })
    }

    @Test fun geographicSectionsKeepChinaAboveUsUkAndOtherCountriesWithCustomGroupsLast() {
        val stations = listOf(station(10, "Canada"), station(11, "Taiwan"), station(12, "Hong Kong SAR China"),
            station(13, "Beijing"), station(14, "United Kingdom"), station(15, "United States"),
            station(16, "My channels"), station(17, "USA"), station(18, "德国"))
        val groups = RadioChannelRows(stations)
        val rows = groups.visible()
        assertEquals(RadioSection.entries, rows.mapNotNull { it.section })
        val headers = rows.filter { it.section == null }
        assertEquals(listOf("beijing", "hong kong", "taiwan"),
            headers.filter { it.group.section == RadioSection.CHINA }.map { it.group.id })
        assertEquals(listOf("united states", "united kingdom", "country:ca", "country:de"),
            headers.filter { it.group.section == RadioSection.INTERNATIONAL }.map { it.group.id })
        assertEquals("import:my channels", headers.last().group.id)
        assertEquals(listOf(stations[5], stations[7]), groups.visible("united states").mapNotNull { it.station })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }

    @Test fun hiddenGroupsRemoveTheirHeadersRowsAndEmptySectionsWithoutChangingTheCatalog() {
        val stations = listOf(station(30, "USA"), station(31, "美国"), station(32, "UK"),
            station(33, "Taiwan"), station(34, "Custom"))
        val groups = RadioChannelRows(stations)
        val original = groups.visible("united states")
        val hidden = groups.visible("united states", setOf("united states", "taiwan"))
        assertEquals(listOf(RadioSection.INTERNATIONAL, RadioSection.OTHER), hidden.mapNotNull { it.section })
        assertEquals(listOf("united kingdom", "import:custom"), hidden.filter { it.section == null }.map { it.group.id })
        assertTrue(hidden.none { it.expanded || it.station != null })
        assertEquals(original, groups.visible("united states"))
        assertTrue(groups.visible("united states", stations.mapTo(hashSetOf()) { RadioGroups.of(it).id }).isEmpty())
        assertEquals(5, stations.size)
    }
}
