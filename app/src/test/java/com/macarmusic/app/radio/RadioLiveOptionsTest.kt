package com.macarmusic.app.radio

import androidx.media3.common.C
import com.macarmusic.app.playback.RadioBuffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RadioLiveOptionsTest {
    @Test fun panelVisibilityUsesTheExistingBackupKeyWithoutChangingOtherRadioOptions() {
        assertTrue(RadioPreferences().showPanel)
        assertTrue(Json.decodeFromString<RadioFavorites>("{}").showPanel)
        val json = Json { encodeDefaults = true }
        for (visible in listOf(false, true)) {
            val settings = RadioFavorites(showPanel = visible, navigationMode = RadioNavigationMode.PINNED_ONLY)
            val encoded = json.encodeToString(settings)
            assertTrue(encoded.contains("\"enabled\":$visible"))
            assertFalse(encoded.contains("\"showPanel\""))
            assertEquals(settings, json.decodeFromString<RadioFavorites>(encoded))
            assertEquals(visible, json.decodeFromString<RadioFavorites>("""{"enabled":$visible}""").showPanel)
        }
    }

    @Test fun navigationAndFallbackChoicesHaveCompatibleDefaultsAndRoundTripIndependently() {
        val legacy = Json.decodeFromString<RadioFavorites>("{}")
        assertEquals(RadioNavigationMode.GLOBAL, legacy.navigationMode)
        assertEquals(RadioButtonLogo.BRANDED_RADIO, legacy.buttonLogo)
        for (mode in RadioNavigationMode.entries) for (logo in RadioButtonLogo.entries) {
            val settings = RadioFavorites(navigationMode = mode, buttonLogo = logo)
            assertEquals(settings, Json.decodeFromString<RadioFavorites>(Json.encodeToString(settings)))
        }
    }

    @Test fun stationSelectionStaysOnRadioByDefaultAndBothChoicesRoundTrip() {
        assertFalse(RadioPreferences().openPlayerOnSelection)
        assertFalse(Json.decodeFromString<RadioFavorites>("{}").openPlayerOnSelection)
        for (openPlayer in listOf(false, true)) {
            val value = RadioFavorites(openPlayerOnSelection = openPlayer)
            assertEquals(value, Json.decodeFromString<RadioFavorites>(Json.encodeToString(value)))
        }
    }

    @Test fun defaultPinsHaveTheRequestedOrderAndDoNotFillAllTwelveSlots() {
        assertEquals(listOf("13", "zgzs", "jjzs", "yyzs", "gsgljtgb", "905", "xczs", "wyzs", "dszs"),
            RadioDefaults.pins.map { it.substringAfterLast(':') })
        assertEquals(12, RadioDefaults.MAX_PINS)
        assertEquals(18, RadioDefaults.stations.size)
    }

    @Test fun knownCountryAliasesShareOneLocalizedGroupAndUnknownGroupsKeepTheirNames() {
        fun group(name: String) = RadioGroups.of(RadioProgram("radio:custom:" + "a".repeat(64),
            "Unrelated station", "https://example.com/audio", name))
        for (aliases in listOf(listOf("US", "USA", "United States", "美国", "美國"),
            listOf("UK", "GB", "United Kingdom", "英国", "英國"),
            listOf("HK", "Hong Kong", "Hong Kong SAR China", "香港"),
            listOf("TW", "Taiwan", "台湾", "台灣"), listOf("Macao", "Macau", "Macao SAR China", "澳门"),
            listOf("CN", "China", "中国"), listOf("Canada", "加拿大"))) {
            assertEquals(aliases.toString(), 1, aliases.map(::group).distinct().size)
        }
        for (name in listOf("Beijing", "China", "Hong Kong SAR China", "Taiwan", "Macao"))
            assertEquals(name, RadioSection.CHINA, group(name).section)
        for (name in listOf("US", "UK", "Canada", "Japan", "Singapore", "德国"))
            assertEquals(name, RadioSection.INTERNATIONAL, group(name).section)
        assertEquals("My channels", group("My channels").imported)
        assertEquals(RadioSection.OTHER, group("My channels").section)
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

    @Test fun cctvCategoryRecognizesLegacySourcesAliasesAndRenamedStations() {
        val original = RadioProgram("radio:custom:" + "a".repeat(64), "My renamed television",
            "https://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8", "National")
        for (group in listOf("", "National", "National stations", "全国", "China", "CCTV Television", "CCTV 电视", "CCTV 電視", "央视电视")) {
            assertEquals(group, RadioGroups.cctvTelevision, RadioGroups.of(original.copy(group = group)))
        }
        val alternate = original.copy(uri = "https://example.com/local-relay", name = "CNR renamed",
            alternateUris = listOf("HTTPS://PICCPNDKS.V.KCDNVIP.COM/audio/cctv1_2.m3u8"), sourceIndex = 1)
        assertEquals(RadioGroups.cctvTelevision, RadioGroups.of(alternate))
        assertEquals(RadioGroups.cctvTelevision, RadioGroups.of(alternate.copy(group = "")))
        assertEquals(RadioGroups.cctvTelevision, RadioGroups.of(
            RadioDefaults.corePrograms.single { it.id == RadioDefaults.CCTV_ID }.copy(name = "News", uri = "https://example.com/news")))
        assertEquals(RadioGroups.cctvTelevision, RadioGroups.named(RadioGroups.cctvTelevision.id))
        assertEquals("National", original.group)
        assertEquals(1, alternate.sourceIndex)
    }

    @Test fun cctvInferenceDoesNotReplaceCustomGroupsOrClassifyUnrelatedNamesAndUrls() {
        val original = RadioProgram("radio:custom:" + "b".repeat(64), "CCTV community discussion",
            "https://piccpndali.v.myalicdn.com/audio/cctv1_2.m3u8", "My channels")
        assertEquals("import:my channels", RadioGroups.of(original).id)
        for (uri in listOf("https://example.com/audio/cctv1_2.m3u8",
            "https://piccpndali.v.myalicdn.com/video/cctv1_2.m3u8",
            "https://piccpndali.v.myalicdn.com.evil.example/audio/cctv1_2.m3u8")) {
            assertEquals(RadioGroups.national, RadioGroups.of(original.copy(uri = uri, group = "National")))
            assertEquals(RadioGroups.other, RadioGroups.of(original.copy(uri = uri, group = "")))
        }
        assertEquals(RadioGroups.national, RadioGroups.of(CnrRadio.stations.first().program))
        assertTrue(RadioGroups.order.compare(RadioGroups.national, RadioGroups.cctvTelevision) < 0)
        assertEquals(RadioSection.CHINA, RadioGroups.cctvTelevision.section)
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
