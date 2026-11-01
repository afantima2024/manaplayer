package com.macarmusic.app.radio

import androidx.media3.common.C
import com.macarmusic.app.playback.RadioBuffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RadioLiveOptionsTest {
    @Test fun stationSelectionStaysOnRadioByDefaultAndBothChoicesRoundTrip() {
        assertFalse(RadioPreferences().openPlayerOnSelection)
        assertFalse(Json.decodeFromString<RadioFavorites>("{}").openPlayerOnSelection)
        for (openPlayer in listOf(false, true)) {
            val value = RadioFavorites(openPlayerOnSelection = openPlayer)
            assertEquals(value, Json.decodeFromString<RadioFavorites>(Json.encodeToString(value)))
        }
    }

    @Test fun defaultPinsHaveTheRequestedOrderAndDoNotFillAllTenSlots() {
        assertEquals(listOf("zgzs", "jjzs", "yyzs", "gsgljtgb", "905", "xczs", "wyzs", "dszs"),
            RadioDefaults.pins.map { it.substringAfterLast(':') })
        assertEquals(10, RadioDefaults.MAX_PINS)
        assertEquals(17, RadioDefaults.stations.size)
    }

    @Test fun importedGroupsTakePrecedenceOverProvinceNameInference() {
        val id = "radio:custom:" + "a".repeat(64)
        fun program(name: String, group: String = "") = RadioProgram(id, name, "https://example.com/audio", group)
        assertEquals("guangdong", RadioGroups.of(program("惠州环保交通广播 FM98.8")).id)
        assertEquals("zhejiang", RadioGroups.of(program("浙江音乐调频")).id)
        assertEquals("shaanxi", RadioGroups.of(program("Shaanxi news")).id)
        assertEquals("shanxi", RadioGroups.of(program("山西交通")).id)
        assertEquals("import:my channels", RadioGroups.of(program("北京音乐", "My channels")).id)
        assertEquals(RadioGroups.other, RadioGroups.of(program("Unrecognized name")))
        assertEquals(RadioGroups.national, RadioGroups.of(CnrRadio.stations.first().program))
    }

    @Test fun liveStartThresholdCannotDeadlockOnShortHlsWindows() {
        assertEquals(2_000_000L, RadioBuffer.startUs(2_000_000, C.TIME_UNSET))
        assertEquals(1_500_000L, RadioBuffer.startUs(10_000_000, 3_000_000))
        assertEquals(10_000_000L, RadioBuffer.startUs(10_000_000, 60_000_000))
        assertEquals(0L, RadioBuffer.startUs(2_000_000, 0))
    }

    @Test fun tuningRoundTripsWithSafeDefaultsAndRejectsImpossibleValues() {
        val tuning = RadioTuning(60, 10, 3, true, false, false)
        assertEquals(tuning, Json.decodeFromString<RadioTuning>(Json.encodeToString(tuning)))
        assertEquals(RadioTuning(), Json.decodeFromString<RadioTuning>("{}"))
        for (json in listOf("""{"bufferSeconds":61}""", """{"startSeconds":0}""",
            """{"bufferSeconds":5,"startSeconds":10}""", """{"retries":4}""")) {
            try { Json.decodeFromString<RadioTuning>(json); fail("Invalid tuning accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun pinColorsRoundTripAndLegacySettingsUseTheThemeDefault() {
        assertNull(Json.decodeFromString<RadioFavorites>("{}").pinColor)
        for (color in listOf(null, 0, 0x123456, 0xffffff)) {
            val settings = RadioFavorites(pinColor = color)
            assertEquals(settings, Json.decodeFromString<RadioFavorites>(Json.encodeToString(settings)))
        }
        for (color in listOf(-1, 0x1000000)) {
            try { Json.decodeFromString<RadioFavorites>("""{"pinColor":$color}"""); fail("Invalid pin color accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
