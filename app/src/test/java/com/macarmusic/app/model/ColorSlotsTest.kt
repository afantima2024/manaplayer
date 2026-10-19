package com.macarmusic.app.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ColorSlotsTest {
    @Test fun sevenFixedRainbowColorsAndFiveEmptySlots() {
        assertEquals(listOf(0xff0000, 0xff7f00, 0xffff00, 0x00ff00, 0x0000ff, 0x4b0082, 0x8b00ff), ColorSlots.RAINBOW)
        assertEquals(List<Int?>(5) { null }, ColorSlots().colors)
    }

    @Test fun editsAreIndependentAndAllowDuplicateColorsAndMoreSlots() {
        val original = ColorSlots()
        val changed = original.define(0, 0).define(3, 0xffffff).define(5, 0).define(6, 0xff0000)
        assertNull(original.colors[0])
        assertEquals(listOf(0, null, null, 0xffffff, null, 0, 0xff0000), changed.colors)
        assertEquals(changed, Json.decodeFromString<ColorSlots>(Json.encodeToString(changed)))
    }

    @Test fun invalidColorsOrSlotIndexesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ColorSlots().define(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ColorSlots().define(6, 0) }
        assertThrows(IllegalArgumentException::class.java) { ColorSlots().define(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { ColorSlots().define(0, 0x1000000) }
        assertThrows(IllegalArgumentException::class.java) { ColorSlots(emptyList()) }
    }
}
