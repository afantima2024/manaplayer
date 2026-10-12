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
        assertEquals(listOf("ManaPlayer"), english["app_name"])
        assertEquals(listOf("小悦播放器"), chinese["app_name"])
        for (values in english.values + chinese.values) {
            assertTrue("Old player name remains visible", values.none { it.contains("MaCarMusic") })
        }
    }
}
