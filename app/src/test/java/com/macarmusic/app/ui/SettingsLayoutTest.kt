package com.macarmusic.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class SettingsLayoutTest {
    private fun layout(name: String): Element = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(Paths.get("src", "main", "res", "layout", "$name.xml").toFile()).documentElement

    private fun elements(root: Element): List<Element> {
        val nodes = root.getElementsByTagName("*")
        return listOf(root) + (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun id(view: Element) = view.getAttribute("android:id").substringAfter('/')
    private fun panel(root: Element, target: String) = elements(root).single { id(it) == target }
    private fun controls(root: Element) = elements(root).filter {
        it.tagName.endsWith("Button") || it.tagName.endsWith("Switch") ||
            it.tagName.endsWith("Spinner") || it.tagName == "SeekBar"
    }.map(::id)

    private fun order(root: Element, expected: String) {
        assertEquals(expected.trim().split(Regex("\\s+")), controls(root))
    }

    @Test fun playerActionRowSeparatesModeSwitchFromCentralTransportWithPinSecond() {
        val actions = panel(layout("view_player_controls"), "actionRow")
        order(actions, "switchPlaybackMode favorite details")
        for (name in listOf("switchPlaybackMode", "favorite", "details")) {
            val button = panel(actions, name)
            assertEquals("48dp", button.getAttribute("android:layout_height"))
            assertEquals("1", button.getAttribute("android:layout_weight"))
            assertTrue(button.getAttribute("android:contentDescription").isNotEmpty())
        }
    }

    @Test fun categoriesStayUnchangedAndGeneralPrioritizesEverydayControls() {
        val root = layout("view_settings")
        order(panel(root, "settingsCategories"),
            "categoryGeneral categoryPlayback categoryRadio categorySpectrum categoryLyrics categoryFolders categoryAbout")
        order(panel(root, "settingsGeneralPanel"), """
            settingsLanguage settingsAutoPlay settingsKeepScreenOn settingsNoticeDuration
            settingsTheme settingsBackgroundImage backgroundInterval backgroundDuration backgroundTransition
            backgroundBlur settingsBackgroundTint backgroundTintStrength
            settingsAccent settingsAppLogo settingsNavigationPosition
            settingsShowLibrary settingsShowQueue settingsShowPlaylists settingsRadio settingsButtonIcons settingsPrimaryControlColor
            settingsControlBlocks settingsBlockStyle settingsBlockCharacter blockArc settingsBlockShadow
            settingsTransition settingsDefaultPlayer
            settingsWidgets widgetTransparency settingsBackup settingsRestore settingsSystemInfo
        """)
    }

    @Test fun playbackLyricsAndFoldersKeepTheirControlsInTaskOrder() {
        val root = layout("view_settings")
        order(panel(root, "settingsPlaybackPanel"),
            "settingsLoop settingsOrder settingsMainTimeMode settingsTimeMode miniPlayerTransparency")
        order(panel(root, "settingsLyricsPanel"), """
            settingsLyrics settingsOnlineLyrics settingsLyricDragSeek settingsLyricSmoothScroll
            lyricsLandscapePosition lyricsPortraitPosition lyricActiveSize lyricActiveColor
            lyricOtherSize lyricOtherColor settingsLyricKaraoke lyricKaraokeColor lyricResetAppearance
            lyricsCacheMove lyricsCacheInternal lyricsCacheInternalExpiry lyricsCachePortableExpiry
            lyricsCacheInspect lyricsCacheClear
        """)
        order(panel(root, "settingsFoldersPanel"), """
            settingsAddFolder settingsDeviceMusic settingsFolders settingsPlayFolders
            settingsPinFolders settingsManageFolders settingsRemoveAllFolders
        """)
    }

    @Test fun radioStartsWithSubscriptionsAndKeepsDestructiveMaintenanceAtTheEnd() {
        order(layout("view_radio_settings"), """
            radioSubscriptions radioRefreshInterval radioNavigation radioOpenPlayer radioReturnMusic
            radioManageGroups radioShowGroups radioBuffer radioStartBuffer radioRetries radioAutoSource radioShowStatus
            radioPinColor radioButtonLogo radioDownloadLogos radioDownloadPinnedLogos radioCancelLogos radioClearCache
            radioImport radioExport radioFormat radioReset radioAbout
        """)
    }

    @Test fun spectrumGroupsShapeBeforeColorAndRetainsPerformanceControls() {
        order(layout("view_spectrum_settings"), """
            spectrumPosition spectrumStyle spectrumBands spectrumFireStyle spectrumOrigin spectrumPalette
            spectrumColorLow spectrumColorMiddle spectrumColorHigh spectrumResetColors spectrumPeaks
            spectrumFalloff spectrumPeakFalloff spectrumRefreshRate
        """)
    }

    @Test fun settingsKeepLabelsAndDynamicStatusWithoutInlineExplanations() {
        for (name in listOf("view_settings", "view_radio_settings", "view_spectrum_settings")) {
            val views = elements(layout(name))
            val ids = views.map(::id).filter(String::isNotEmpty)
            assertEquals("Duplicate ID in $name", ids.size, ids.toSet().size)
            assertFalse("Provider guidance belongs in Help", "lyricsProviderOrder" in ids)
            for (view in views.filter { it.tagName == "TextView" }) {
                assertFalse(view.getAttribute("android:text").endsWith("_help"))
                if (id(view).isEmpty()) assertEquals("Unlabeled explanation in $name",
                    "@style/SettingLabel", view.getAttribute("style"))
            }
            for (view in views.filter { it.tagName.endsWith("Spinner") || it.tagName == "SeekBar" }) {
                assertEquals("Missing label for ${id(view)}", 1,
                    views.count { it.getAttribute("android:labelFor").substringAfter('/') == id(view) })
            }
        }
        val status = elements(layout("view_settings")) + elements(layout("view_radio_settings"))
        for (name in listOf("settingsBackupStatus", "settingsFolderSummary", "backgroundSummary",
            "lyricsCacheLocation", "lyricsCacheProgress", "radioSubscriptionsStatus", "radioLogosStatus", "radioLogosProgress")) {
            assertEquals("Keep live status $name", 1, status.count { id(it) == name })
        }
    }

    @Test fun conditionalAppearanceControlsKeepTheirOriginalContainers() {
        val root = layout("view_settings")
        val general = panel(root, "settingsGeneralPanel")
        val children = (0 until general.childNodes.length).mapNotNull { general.childNodes.item(it) as? Element }.map(::id)
        assertEquals("settingsBackgroundPanel", children[children.indexOf("settingsTheme") + 1])
        assertEquals("settingsAppLogo", children[children.indexOf("settingsAccent") + 1])
        assertEquals("gone", panel(root, "settingsBackgroundPanel").getAttribute("android:visibility"))
        assertEquals("gone", panel(root, "settingsSlideshow").getAttribute("android:visibility"))
        assertTrue("settingsBackgroundImage" in controls(panel(root, "settingsBackgroundPanel")))
        assertTrue("backgroundDuration" in controls(panel(root, "settingsSlideshow")))
        assertEquals(listOf("blockArc"), controls(panel(root, "blockArcControls")))
        assertTrue("settingsBlockShadow" in controls(panel(root, "blockStyleControls")))
        val spectrum = layout("view_spectrum_settings")
        assertEquals(listOf("spectrumFireStyle"), controls(panel(spectrum, "fireStyleControls")))
        assertEquals(listOf("spectrumColorLow", "spectrumColorMiddle", "spectrumColorHigh", "spectrumResetColors"),
            controls(panel(spectrum, "spectrumColorControls")))
    }
}
