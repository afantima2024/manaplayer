package com.macarmusic.app.ui

import org.junit.Assert.*
import org.junit.Test

class RadioGridColumnsTest {
    @Test fun phonesUseOneListColumnAndWideCarScreensUseTwo() {
        for (width in listOf(0f, 320f, 390f, 519f))
            assertEquals(1, RadioGridColumns.forWidth(width, 1f, true).channels)
        for (width in listOf(520f, 600f, 800f, 1024f))
            assertEquals(2, RadioGridColumns.forWidth(width, 1f, true).channels)
    }

    @Test fun oddTileCountsStillHaveTwoEqualListColumnsAndFullWidthHeaders() {
        for (width in listOf(520f, 800f, 1024f)) {
            val columns = RadioGridColumns.forWidth(width, 1f, true)
            assertEquals(1, columns.tiles % 2)
            assertEquals(columns.spans, columns.tileSpan * columns.tiles)
            assertEquals(columns.spans, columns.channelSpan * 2)
        }
    }

    @Test fun largeFontsKeepReadableCellsInsteadOfForcingTwoColumns() {
        assertEquals(1, RadioGridColumns.forWidth(600f, 1.2f, true).channels)
        assertEquals(2, RadioGridColumns.forWidth(624f, 1.2f, true).channels)
        assertEquals(1, RadioGridColumns.forWidth(800f, 2f, true).channels)
        assertEquals(2, RadioGridColumns.forWidth(1040f, 2f, true).channels)
    }

    @Test fun pinnedQuickSwitchGridRetainsItsOriginalTileSizing() {
        for (width in listOf(320f, 520f, 800f, 1024f)) {
            val columns = RadioGridColumns.forWidth(width, 1f, false)
            assertEquals((width / 136).toInt(), columns.tiles)
            assertEquals(columns.tiles, columns.spans)
            assertEquals(1, columns.tileSpan)
        }
    }
}
