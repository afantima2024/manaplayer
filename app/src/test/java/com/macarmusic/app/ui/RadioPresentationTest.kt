package com.macarmusic.app.ui

import org.junit.Assert.*
import org.junit.Test

class RadioPresentationTest {
    private val savedStation = "radio:cnr:1"
    private val otherStation = "radio:cnr:2"
    private val musicTrack = "content://local/music/1"

    @Test fun radioLayoutDoesNotWaitForCatalogMetadata() {
        for (stationAvailable in listOf(false, true, false, true)) {
            assertTrue(RadioPresentation.radioControls(active = true, preparing = false, stationAvailable))
        }
    }

    @Test fun serviceReadinessDoesNotClearAnEmptyControllersSavedStation() {
        assertEquals(savedStation, RadioPresentation.stationId(
            mediaId = null, savedId = savedStation, active = true,
            controllerConnected = true, playbackReady = true,
        ))
    }

    @Test fun selectionSurvivesBothControllerAndServiceReadinessOrders() {
        val paths = listOf(
            listOf(false to false, true to false, true to true),
            listOf(false to false, false to true, true to true),
        )
        for (path in paths) {
            val ids = path.map { (connected, ready) ->
                RadioPresentation.stationId(null, savedStation, true, connected, ready)
            } + RadioPresentation.stationId(savedStation, savedStation, true, true, true)
            assertEquals(List(path.size + 1) { savedStation }, ids)
        }
    }

    @Test fun musicStartupNeverUsesTheSavedRadioHistory() {
        for (connected in listOf(false, true)) for (ready in listOf(false, true)) {
            for (mediaId in listOf(null, musicTrack)) {
                val station = RadioPresentation.stationId(mediaId, savedStation, false, connected, ready)
                assertNull(station)
                assertFalse(RadioPresentation.radioControls(false, false, station != null))
            }
        }
    }

    @Test fun explicitMusicTimelineDoesNotUseAnOldRadioSelection() {
        assertNull(RadioPresentation.stationId(musicTrack, savedStation, true, true, true))
    }

    @Test fun playingRadioWinsOverTheSavedSelection() {
        for (active in listOf(false, true)) {
            assertEquals(otherStation, RadioPresentation.stationId(otherStation, savedStation, active, true, true))
            assertTrue(RadioPresentation.radioControls(active, false, true))
        }
    }

    @Test fun pendingSwitchUsesRadioControlsWithoutInventingAStation() {
        assertTrue(RadioPresentation.radioControls(false, true, false))
        assertNull(RadioPresentation.stationId(musicTrack, savedStation, false, true, true))
    }

    @Test fun stoppedRadioKeepsItsLayoutWhenTheControllerDisconnects() {
        assertTrue(RadioPresentation.radioControls(true, false, true))
        assertEquals(savedStation, RadioPresentation.stationId(null, savedStation, true, false, false))
        assertTrue(RadioPresentation.radioControls(true, false, false))
    }

    @Test fun leavingRadioClearsTheFallbackWithoutErasingHistory() {
        assertNull(RadioPresentation.stationId(null, savedStation, false, true, true))
        assertFalse(RadioPresentation.radioControls(false, false, false))
    }

    @Test fun missingMetadataDoesNotTurnCommittedRadioIntoMusic() {
        assertNull(RadioPresentation.stationId(null, null, true, true, true))
        assertTrue(RadioPresentation.radioControls(true, false, false))
    }
}
