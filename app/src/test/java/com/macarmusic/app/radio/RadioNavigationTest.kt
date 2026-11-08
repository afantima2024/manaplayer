package com.macarmusic.app.radio

import com.macarmusic.app.ui.RadioChannelRows
import org.junit.Assert.*
import org.junit.Test

class RadioNavigationTest {
    private fun station(index: Int, group: String) = RadioProgram(
        "radio:custom:${index.toString(16).padStart(64, '0')}", "Station $index", "https://radio.example/$index.mp3", group)
    private val canada = station(1, "Canada")
    private val us = station(2, "US")
    private val uk = station(3, "UK")
    private val china = station(4, "Beijing")
    private val other = station(5, "Custom")
    private val anotherUs = station(6, "USA")
    private val stations = listOf(canada, us, uk, china, other, anotherUs)

    @Test fun globalOrderPlacesPinsFirstThenVisibleGroupedRowsWithoutDuplicates() {
        val options = RadioNavigationOptions(RadioNavigationMode.GLOBAL, listOf(uk.id, canada.id, uk.id, "missing"), emptySet())
        val ordered = RadioNavigation.ordered(stations, options)
        assertEquals(listOf(uk, canada, china, us, anotherUs, other), ordered)
        assertEquals(ordered.size, ordered.map { it.id }.distinct().size)
        val groups = RadioGroups.grouped(stations)
        val unpinnedRows = groups.flatMap { (group, _) -> RadioChannelRows(stations).visible(group.id).mapNotNull { it.station } }
            .filter { it.id !in options.pins }
        assertEquals(unpinnedRows, ordered.drop(2))
    }

    @Test fun hiddenCountriesRemoveUnpinnedStationsButKeepExplicitPins() {
        val options = RadioNavigationOptions(RadioNavigationMode.GLOBAL, listOf(us.id),
            setOf(RadioGroups.of(us).id, RadioGroups.of(canada).id))
        assertEquals(listOf(us, china, uk, other), RadioNavigation.ordered(stations, options))
        assertEquals(stations.size, 6)
    }

    @Test fun pinnedOnlyUsesSavedOrderRegardlessOfCountryVisibilityAndIgnoresMissingPins() {
        val options = RadioNavigationOptions(RadioNavigationMode.PINNED_ONLY, listOf(other.id, us.id, "missing"),
            stations.mapTo(hashSetOf()) { RadioGroups.of(it).id })
        assertEquals(listOf(other, us), RadioNavigation.ordered(stations, options))
        assertTrue(RadioNavigation.ordered(stations, options.copy(pins = emptyList())).isEmpty())
    }

    @Test fun adjacentWrapsBothWaysAndUnpinnedStationsEnterAtTheNearestEnd() {
        val ids = listOf("first", "middle", "last")
        assertEquals("first", RadioNavigation.adjacent(ids, "last", 1))
        assertEquals("last", RadioNavigation.adjacent(ids, "first", -1))
        assertEquals("middle", RadioNavigation.adjacent(ids, "first", 1))
        assertEquals("middle", RadioNavigation.adjacent(ids, "last", -1))
        assertEquals("first", RadioNavigation.adjacent(ids, "unlisted", 1))
        assertEquals("last", RadioNavigation.adjacent(ids, "unlisted", -1))
        assertEquals("only", RadioNavigation.adjacent(listOf("only"), "only", 1))
        assertEquals("only", RadioNavigation.adjacent(listOf("only"), "outside", -1))
        assertNull(RadioNavigation.adjacent(emptyList(), "outside", 1))
        assertThrows(IllegalArgumentException::class.java) { RadioNavigation.adjacent(ids, "first", 0) }
    }

    @Test fun largeOrderingIsStableAndDoesNotMutateTheInput() {
        val input = List(5000) { station(it, "Group ${it % 50}") }
        val pins = input.takeLast(12).reversed().map { it.id }
        val options = RadioNavigationOptions(RadioNavigationMode.GLOBAL, pins, setOf("import:group 7"))
        val result = RadioNavigation.ordered(input, options)
        assertEquals(pins, result.take(12).map { it.id })
        assertEquals(input.size - 100, result.size)
        assertEquals(result, RadioNavigation.ordered(input, options))
        assertEquals((0 until 5000).map { "Station $it" }, input.map { it.name })
    }
}
