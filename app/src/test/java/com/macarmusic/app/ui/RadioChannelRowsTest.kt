package com.macarmusic.app.ui

import com.macarmusic.app.radio.RadioGroups
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.radio.RadioDefaults
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
        assertEquals(listOf(a, b), rows.map { it.group })
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

    @Test fun defaultPinnedStationsStayInTheirGroupAlongsideUnpinnedStations() {
        val rows = RadioChannelRows(RadioDefaults.corePrograms).visible(RadioGroups.national.id)
        val ids = rows.mapNotNull { it.station?.id }
        assertTrue(ids.containsAll(RadioDefaults.pins))
        assertEquals(RadioDefaults.corePrograms.map { it.id }, ids)
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun headerIdentityIsStableWhileExpandedStateChanges() {
        val groups = RadioChannelRows(listOf(first, second, third))
        val closed = groups.visible()
        val opened = groups.visible(a.id)
        assertEquals(closed.map { it.key }, opened.filter { it.station == null }.map { it.key })
        assertNotEquals(closed.first(), opened.first())
        assertEquals(opened.size, opened.map { it.key }.distinct().size)
    }

    @Test fun largeCollapsedCatalogDoesNotCreateHiddenStationRows() {
        val stations = List(5000) { station(it, "Group ${it % 40}") }
        val groups = RadioChannelRows(stations)
        assertEquals(40, groups.visible().size)
        val opened = groups.visible(RadioGroups.of(stations.first()).id)
        assertEquals(40 + 125, opened.size)
        assertEquals(125, opened.count { it.station != null })
        assertEquals(1, opened.count { it.expanded })
    }

    @Test fun emptyOrFilteredOutGroupsNeverRevealUnrelatedStations() {
        assertTrue(RadioChannelRows(emptyList()).visible(a.id).isEmpty())
        assertEquals(listOf(RadioChannelRow(b)), RadioChannelRows(listOf(third)).visible(a.id))
        assertEquals(listOf(second), RadioChannelRows(listOf(second)).visible(a.id).mapNotNull { it.station })
    }
}
