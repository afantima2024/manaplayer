package com.macarmusic.app.radio

import org.junit.Assert.*
import org.junit.Test

class RadioPinOrderTest {
    @Test fun allTwelvePinsCanMoveAcrossRowsWithoutChangingMembershipOrTheInput() {
        val pins = List(12) { "pin:$it" }
        val reordered = pins.toMutableList().apply { add(10, removeAt(0)) }
        assertEquals(reordered, reorderedRadioPins(pins, pins, reordered))
        assertEquals(List(12) { "pin:$it" }, pins)
        assertEquals(pins.toSet(), reordered.toSet())
        assertEquals(pins, reorderedRadioPins(reordered, reordered, pins))
    }

    @Test fun filteredAndDisabledPinsKeepTheirSlotsWhileVisiblePinsAreReordered() {
        val pins = listOf("a", "filtered", "b", "disabled", "c")
        assertEquals(listOf("c", "filtered", "a", "disabled", "b"),
            reorderedRadioPins(pins, listOf("a", "b", "c"), listOf("c", "a", "b")))
        assertEquals(pins, reorderedRadioPins(pins, listOf("a"), listOf("a")))
    }

    @Test fun newPinsOutsideTheDraggedSubsetArePreserved() {
        assertEquals(listOf("b", "new", "a", "also-new"),
            reorderedRadioPins(listOf("a", "new", "b", "also-new"), listOf("a", "b"), listOf("b", "a")))
    }

    @Test fun removalOrConcurrentReorderingOfDraggedPinsRejectsTheStaleGesture() {
        assertNull(reorderedRadioPins(listOf("a"), listOf("a", "b"), listOf("b", "a")))
        assertNull(reorderedRadioPins(listOf("b", "a"), listOf("a", "b"), listOf("b", "a")))
    }

    @Test fun duplicatesForeignIdsAndIncompletePermutationsAreRejected() {
        val pins = listOf("a", "b", "c")
        for (reordered in listOf(listOf("a", "a", "c"), listOf("a", "b"), listOf("a", "b", "new"),
            listOf("a", "b", "c", "c"), emptyList()))
            assertNull(reordered.toString(), reorderedRadioPins(pins, pins, reordered))
        assertNull(reorderedRadioPins(pins, listOf("a", "a"), listOf("a", "a")))
        assertNull(reorderedRadioPins(pins, emptyList(), emptyList()))
    }
}
