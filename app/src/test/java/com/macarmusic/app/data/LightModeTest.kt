package com.macarmusic.app.data

import com.macarmusic.app.model.SpectrumOptions
import com.macarmusic.app.model.TransitionStyle
import org.junit.Assert.*
import org.junit.Test

class LightModeTest {
    @Test fun spectrumDefaultsTo15AndStillAccepts30And60() {
        assertEquals(15, SpectrumOptions().refreshRate)
        for (rate in listOf(15, 30, 60)) {
            val original = DisplayOptions(spectrum = SpectrumOptions(enabled = true, refreshRate = rate), lightMode = true)
            assertEquals(original.spectrum, original.effective().spectrum)
            assertTrue(original.effective().spectrum.enabled)
        }
    }

    @Test fun lightModeDoesNotMutateCustomPreferencesOrRemovePictures() {
        val originals = listOf("first.png", "second.png")
        val configured = DisplayOptions(imageIds = originals, imageVersion = 1, blur = 70,
            settingsTransition = TransitionStyle.ZOOM, lyricsSmoothScroll = true, onlineLyrics = true)
        val light = configured.copy(lightMode = true)
        val effective = light.effective()
        assertEquals(listOf("first.png"), effective.backgroundImages)
        assertEquals(0, effective.blur)
        assertFalse(effective.onlineLyrics)
        assertFalse(effective.lyricsSmoothScroll)
        assertTrue(effective.lyricsEnabled)
        assertEquals(TransitionStyle.NONE, effective.settingsTransition)
        assertEquals(originals, light.imageIds)
        assertEquals(70, light.blur)
        assertEquals(configured, light.copy(lightMode = false).effective())
    }
}
