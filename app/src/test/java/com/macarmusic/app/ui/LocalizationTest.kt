package com.macarmusic.app.ui

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTest {
    private fun strings(directory: String): Map<String, List<String>> {
        val file = Paths.get("src", "main", "res", directory, "strings.xml").toFile()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.documentElement.childNodes
        return buildMap {
            for (index in 0 until nodes.length) {
                val node = nodes.item(index)
                if (node !is Element) continue
                val values = if (node.tagName == "plurals") {
                    val items = node.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it).textContent }
                } else listOf(node.textContent)
                put(node.getAttribute("name"), values)
            }
        }
    }

    @Test fun chineseCoversEveryEnglishResourceAndItsFormatArguments() {
        val english = strings("values")
        val chinese = strings("values-zh")
        assertEquals(english.keys, chinese.keys)
        val placeholders = Regex("%\\d+\\$[ds]")
        for ((name, values) in english) {
            val expected = placeholders.findAll(values.first()).map { it.value }.toSet()
            for (translation in chinese.getValue(name)) {
                assertTrue("$name is empty", translation.isNotBlank())
                assertEquals("Format arguments in $name", expected,
                    placeholders.findAll(translation).map { it.value }.toSet())
            }
        }
    }

    @Test fun playerCopyDoesNotAdvertiseNetworkRestrictions() {
        val wording = Regex("no network|offline|internet|无需联网|无网络|离线", RegexOption.IGNORE_CASE)
        for ((name, values) in strings("values").toList() + strings("values-zh").toList()) {
            assertTrue("Unexpected promotional wording in $name", values.none { wording.containsMatchIn(it) })
        }
    }

    @Test fun playerBrandingMatchesEachLanguage() {
        val english = strings("values")
        val chinese = strings("values-zh")
        assertEquals(listOf("ManaMusicPlayer"), english["app_name"])
        assertEquals(listOf("小悦音乐播放器"), chinese["app_name"])
        for (values in english.values + chinese.values) {
            assertTrue("Old player name remains visible",
                values.none { it.contains("MaCarMusic") || it.contains("ManaPlayer") || it.contains("小悦播放器") })
        }
    }

    @Test fun aboutHasLocalizedVersionAndNoPhotoViewerInstructions() {
        for (directory in listOf("values", "values-zh")) {
            val localized = strings(directory)
            assertTrue(localized.getValue("about_version").single().contains("%1\$s"))
            assertTrue(localized.getValue("about_version").single().contains("%2\$d"))
            val body = localized.getValue("about_body").single()
            for (removed in listOf("PHOTO VIEWER", "full resolution", "照片查看", "原始分辨率")) {
                assertFalse("Removed viewer still advertised in $directory", body.contains(removed))
            }
            assertTrue(localized.keys.none { it.startsWith("about_photo") || it.startsWith("photo_") })
            assertTrue(localized.getValue("app_logo").single().isNotBlank())
        }
    }

    @Test fun settingsUseFunctionLabelsAndNoRedundantBorderChoice() {
        val english = strings("values")
        assertEquals(listOf("Classic"), english["spectrum_classic"])
        assertEquals(listOf("Fire"), english["spectrum_fire"])
        assertEquals(listOf("Theme accent"), english["spectrum_theme"])
        for (directory in listOf("values", "values-zh")) {
            val localized = strings(directory)
            assertFalse(localized.containsKey("block_none"))
            assertFalse(localized.getValue("control_blocks_help").single().contains("None"))
            for (text in localized.values.flatten()) {
                assertFalse(Regex("as requested|your requirement|you asked|按您的要求|根据您的需求",
                    RegexOption.IGNORE_CASE).containsMatchIn(text))
            }

        }
    }

    @Test fun everyBandWidthUsesTheSameLocalizedCountFormat() {
        for ((directory, suffix) in listOf("values" to "bands", "values-zh" to "个频段")) {
            val localized = strings(directory)
            assertFalse(localized.containsKey("spectrum_thick"))
            assertFalse(localized.containsKey("spectrum_thin"))
            for (count in listOf(3, 8, 16, 32)) {
                assertEquals("$count $suffix", String.format(localized.getValue("spectrum_band_count").single(), count))
            }
        }
    }
}
