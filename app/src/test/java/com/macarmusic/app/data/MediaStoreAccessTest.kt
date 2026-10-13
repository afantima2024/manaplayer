package com.macarmusic.app.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class MediaStoreAccessTest {
    @Test fun permissionMatchesEverySupportedAndroidGeneration() {
        for (sdk in 29..32) assertEquals("android.permission.READ_EXTERNAL_STORAGE", MediaStoreAccess.requiredPermission(sdk))
        for (sdk in 33..36) assertEquals("android.permission.READ_MEDIA_AUDIO", MediaStoreAccess.requiredPermission(sdk))
    }

    @Test fun manifestBoundsLegacyPermissionAndDoesNotRequestAllFilesOrOtherMedia() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Paths.get("src", "main", "AndroidManifest.xml").toFile())
        val permissions = xml.getElementsByTagName("uses-permission")
        val values = (0 until permissions.length).associate { index ->
            val attributes = permissions.item(index).attributes
            attributes.getNamedItem("android:name").nodeValue to attributes.getNamedItem("android:maxSdkVersion")?.nodeValue
        }
        assertEquals("32", values["android.permission.READ_EXTERNAL_STORAGE"])
        assertTrue(values.containsKey("android.permission.READ_MEDIA_AUDIO"))
        for (name in listOf("MANAGE_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "READ_MEDIA_IMAGES",
            "READ_MEDIA_VIDEO", "QUERY_ALL_PACKAGES")) assertFalse(values.containsKey("android.permission.$name"))
    }

    @Test fun android10IsTheMinimumSupportedVersion() {
        val build = Paths.get("build.gradle.kts").toFile().readText()
        assertTrue(Regex("""\bminSdk\s*=\s*29\b""").containsMatchIn(build))
    }

    @Test fun directoryBoundariesExcludeSimilarlyNamedSiblingsAndUnknownLocations() {
        assertTrue(MediaStoreAccess.contains("Music/", "Music/"))
        assertTrue(MediaStoreAccess.contains("Music/", "Music/Album/"))
        assertFalse(MediaStoreAccess.contains("Music/", "Music 2/"))
        assertFalse(MediaStoreAccess.contains("Music/Album/", "Music/Albums/"))
        assertTrue(MediaStoreAccess.contains("", ""))
        assertFalse(MediaStoreAccess.contains("", "Music/"))
    }

    @Test fun nestedAudioProducesOnlyTheImmediateChildDirectory() {
        assertEquals("Music/A/", MediaStoreAccess.directChild("Music/", "Music/A/B/"))
        assertEquals("Music/A/B/", MediaStoreAccess.directChild("Music/A/", "Music/A/B/"))
        assertNull(MediaStoreAccess.directChild("Music/", "Music/"))
        assertNull(MediaStoreAccess.directChild("Music/", "Other/"))
        assertNull(MediaStoreAccess.directChild("", "Music/"))
    }

    @Test fun queryPrefixesTreatPercentUnderscoreAndBackslashAsLiteralFolderNames() {
        assertEquals("Music/100\\%\\_Mix\\\\Set/%", MediaStoreAccess.likePrefix("Music/100%_Mix\\Set/"))
        assertEquals("Music/O'Brien/%", MediaStoreAccess.likePrefix("Music/O'Brien/"))
        assertEquals("Music/", MediaStoreAccess.normalizeFolder("Music"))
        assertEquals("Music/", MediaStoreAccess.normalizeFolder("Music/"))
        assertEquals("", MediaStoreAccess.normalizeFolder(""))
    }
}
