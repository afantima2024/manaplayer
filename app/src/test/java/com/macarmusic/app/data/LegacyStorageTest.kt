package com.macarmusic.app.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

class LegacyStorageTest {
    @Test fun legacyPermissionsAreOptInAndUnavailableOnScopedStorageAndroid() {
        for (sdk in 23..28) {
            assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE"), LegacyStorage.permissions(false, sdk))
            assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"),
                LegacyStorage.permissions(true, sdk))
        }
        for (sdk in listOf(22, 29, 30, 32, 33, 35, 36)) {
            assertFalse(LegacyStorage.available(sdk))
            assertTrue(LegacyStorage.permissions(true, sdk).isEmpty())
        }
    }

    @Test fun explorerIsPrivateAndDocumentBridgeIsSignatureProtected() {
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Paths.get("src", "main", "AndroidManifest.xml").toFile())
        val providers = xml.getElementsByTagName("provider")
        val provider = (0 until providers.length).map { providers.item(it).attributes }
            .single { it.getNamedItem("android:name").nodeValue == ".data.LocalDocumentsProvider" }
        assertEquals("android.permission.MANAGE_DOCUMENTS", provider.getNamedItem("android:permission").nodeValue)
        assertEquals("true", provider.getNamedItem("android:grantUriPermissions").nodeValue)
        val activities = xml.getElementsByTagName("activity")
        val explorer = (0 until activities.length).map { activities.item(it).attributes }
            .single { it.getNamedItem("android:name").nodeValue == ".ui.FileExplorerActivity" }
        assertEquals("false", explorer.getNamedItem("android:exported").nodeValue)
    }
}
