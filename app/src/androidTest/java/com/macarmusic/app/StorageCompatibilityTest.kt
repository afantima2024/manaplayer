package com.macarmusic.app

import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.containsString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.macarmusic.app.data.LibraryCodec
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.model.Library
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlaybackSnapshot
import com.macarmusic.app.playback.PlaybackService
import com.macarmusic.app.ui.DocumentPicker
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.ListPanel
import com.macarmusic.app.ui.MusicRowAdapter
import com.macarmusic.app.ui.MusicViewModel
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class StorageCompatibilityTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixture = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.documents", "music")
    private val rootFolder = MediaStoreAccess.Folder("external_primary", "Music/ManaPlayer_Compatibility/")
    private val siblingFolder = MediaStoreAccess.Folder("external_primary", "Music/ManaPlayer_Compatibility 2/")

    @Before fun setup() {
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureGrantActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await {
            app.checkUriPermission(fixture, android.os.Process.myPid(), android.os.Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        }
        runBlocking { app.repository.awaitReady() }
        language("en")
        onMain {
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false) }
            app.playbackSettings.setLoop(LoopMode.OFF)
        }
    }

    @Test fun pickerFallbacksKeepContentGrantsAndDoNotPretendToPersistTemporaryAccess() {
        val attempts = mutableListOf<Intent>()
        assertTrue(DocumentPicker.launch("image/*", multiple = true) { intent ->
            attempts.add(intent)
            if (attempts.size < 3) throw ActivityNotFoundException("Synthetic unsupported picker")
        })
        assertEquals(listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT, Intent.ACTION_GET_CONTENT),
            attempts.map { it.action })
        attempts.forEach {
            assertEquals("image/*", it.type)
            assertTrue(it.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
            assertTrue(it.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, it.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            assertEquals(0, it.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        assertTrue(attempts.first().hasCategory(Intent.CATEGORY_OPENABLE))
        assertTrue(attempts[1].hasCategory(Intent.CATEGORY_OPENABLE))
        assertFalse(attempts.last().hasCategory(Intent.CATEGORY_OPENABLE))
        assertTrue(attempts.first().flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertTrue(attempts.drop(1).all { it.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0 })
        var successfulStarts = 0
        assertTrue(DocumentPicker.launch("*/*") { successfulStarts++ })
        assertEquals("A normal launch or later user cancellation must not open a second picker", 1, successfulStarts)
    }

    @Test fun exhaustedPickersAndSecurityRestrictionsRemainExplicit() {
        var attempts = 0
        assertFalse(DocumentPicker.launch("*/*") {
            attempts++
            throw ActivityNotFoundException("Synthetic missing handler")
        })
        assertEquals(3, attempts)
        try {
            DocumentPicker.launch("*/*") { throw SecurityException("Synthetic policy restriction") }
            fail("A policy restriction must not become success or an empty selection")
        } catch (expected: SecurityException) {
            assertEquals("Synthetic policy restriction", expected.message)
        }
    }

    @Test fun indexedFolderTokensAreDistinctFromFilesProvidersAndUnnormalizedPaths() {
        val token = MediaStoreAccess.Folder("1234-abcd", "Music/100%_Mix/中文/")
        assertEquals(token, MediaStoreAccess.folder(Uri.parse(token.uri.toString())))
        for (uri in listOf(
            "content://media/external/audio/media/7?manaplayer_folder=Music%2F",
            "content://media/external/images/media?manaplayer_folder=Music%2F",
            "content://another.provider/external/audio/media?manaplayer_folder=Music%2F",
            "file:///external/audio/media?manaplayer_folder=Music%2F",
            "content://media/external/audio/media?manaplayer_folder=Music",
            "content://media/external/audio/media?manaplayer_folder=Music%2F&extra=1",
        )) assertNull(uri, MediaStoreAccess.folder(Uri.parse(uri)))
    }

    @Test fun pickerlessDeviceRequestsAudioOnlyOnChoiceThenBrowsesScansAndPlaysSavedFolders() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pickerless") == "true")
        assertFalse("Run on a fresh emulator or revoke audio permission before this test", app.repository.documents.deviceMusic.hasPermission())
        runBlocking {
            app.repository.update { Library() }
            app.playback.read()
        }
        app.playback.save(PlaybackSnapshot())
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .remove("musicPermissionBlocked.${MediaStoreAccess.requiredPermission()}").commit()
        app.contentResolver.call(fixture, "seedIndexedMusic", null, null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var model: MusicViewModel? = null
            scenario.onActivity { model = ViewModelProvider(it)[MusicViewModel::class.java] }
            await { app.playbackReady.value }
            assertFalse(app.repository.documents.deviceMusic.hasPermission())
            scenario.onActivity { assertNull(model!!.deviceMusic.value) }
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withText(R.string.add_folder)).perform(click())
            onView(withText(R.string.folder_picker_missing)).inRoot(isDialog()).check(matches(isDisplayed()))
            screenshot("pickerless-folder-alternative")
            onView(withText(R.string.device_music)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.allow_music_access)).inRoot(isDialog()).perform(click())
            permissionButton("permission_deny_button")
            await { app.notice.value?.text == app.text(R.string.music_permission_denied) }
            assertTrue(app.repository.library.value.tracks.isEmpty())
            assertFalse(app.repository.documents.deviceMusic.hasPermission())
            onView(withText(R.string.device_music)).perform(scrollTo(), click())
            onView(withText(R.string.allow_music_access)).inRoot(isDialog()).perform(click())
            screenshot("pickerless-audio-permission")
            permissionButton("permission_allow_button")
            await { onMainResult { model!!.deviceMusic.value?.loading == false } }
            val root = app.repository.documents.deviceMusic.source(rootFolder)
            val sibling = app.repository.documents.deviceMusic.source(siblingFolder)
            onData(equalTo(root.name)).inRoot(isDialog()).perform(click())
            onData(equalTo(sibling.name)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            scenario.onActivity {
                model = ViewModelProvider(it)[MusicViewModel::class.java]
                assertEquals(setOf(root.uri, sibling.uri), model!!.deviceMusic.value!!.selected)
            }
            screenshot("pickerless-multiple-music-folders")
            onView(withText(R.string.add_folder)).inRoot(isDialog()).perform(click())
            await { app.repository.library.value.sources.size == 2 &&
                app.repository.library.value.tracks.size == 3 && model!!.scan.value == null }
            val tracks = app.repository.library.value.tracks
            assertTrue(tracks.all { it.durationMs in 59_000..61_000 && it.uri.startsWith("content://media/") })
            assertFalse(tracks.any { it.fileName == "Indexed fixture 3.wav" })
            runBlocking {
                val children = app.repository.documents.children(root.uri, rootFolder.path)
                assertEquals(2, children.size)
                assertTrue(children.first().isDirectory)
                assertEquals("Child", children.first().name)
                assertEquals("Indexed fixture 0.wav", children.last().name)
                assertEquals(2, app.repository.scan(root, null) {}.tracks.size)
                app.repository.update {
                    val favorite = LibraryEdits.toggleFavorite(it, tracks.first().id)
                    val playlist = LibraryEdits.createPlaylist(favorite, "indexed-test", "Indexed fixtures")
                    LibraryEdits.addToPlaylist(playlist, "indexed-test", tracks.map { track -> track.id })
                }
                val before = app.repository.library.value
                try {
                    val missing = root.copy(uri = rootFolder.copy(volume = "missing-volume").uri.toString())
                    app.repository.scan(missing, null) {}
                    fail("An unmounted indexed volume must not look like a successful empty rescan")
                } catch (_: IOException) {
                    assertEquals(before, app.repository.library.value)
                }
            }
            assertEquals(app.repository.library.value,
                LibraryCodec.decodeLibrary(File(app.filesDir, "library.json").readText()))
            clickBrowserRow(scenario, root.name)
            clickBrowserRow(scenario, "Child")
            clickBrowserRow(scenario, "Indexed fixture 1.wav")
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                await { controller.isPlaying && controller.playbackState == Player.STATE_READY }
                assertEquals("Indexed fixture 1.wav", app.repository.library.value.tracks
                    .single { it.id == onMainResult { controller.currentMediaItem!!.mediaId } }.fileName)
                onMain {
                    controller.pause()
                    controller.seekTo(5_000)
                    app.playbackSettings.setLoop(LoopMode.SELECTED_FOLDERS)
                }
                await { controller.mediaItemCount == 3 }
                assertFalse(onMainResult { controller.playWhenReady })
                screenshot("pickerless-indexed-playback")
            } finally {
                onMain { controller.pause(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun ordinaryFileManagerFallbackOpensAudioWithARealTemporaryContentGrant() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pickerless") == "true")
        val uri = DocumentsContract.buildDocumentUriUsingTree(fixture, "music/album/two")
        val before = app.repository.library.value
        var contentRequests = 0
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_GET_CONTENT) return null
                contentRequests++
                assertEquals("*/*", intent.type)
                return Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                    Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.nav_folders)).perform(click())
                onView(withText(R.string.open_file)).perform(scrollTo(), click())
                val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
                val controller = future.get(20, TimeUnit.SECONDS)
                try {
                    await { controller.isPlaying && app.repository.library.value.tracks.any { track -> track.uri == uri.toString() } }
                    val track = app.repository.library.value.tracks.single { track -> track.uri == uri.toString() }
                    assertEquals(1, contentRequests)
                    assertFalse(track.standalonePersisted)
                    assertEquals(track.id, onMainResult { controller.currentMediaItem!!.mediaId })
                    screenshot("pickerless-file-manager-audio")
                } finally {
                    onMain { controller.pause(); MediaController.releaseFuture(future) }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking { app.repository.update { before } }
        }
    }

    @Test fun ordinaryFileManagerFallbackAlsoImportsPrivateBackgroundPictures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pickerless") == "true")
        val image = DocumentsContract.buildDocumentUri("com.macarmusic.app.test.documents", "pictures/background")
        var contentRequests = 0
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_GET_CONTENT) return null
                contentRequests++
                assertEquals("image/*", intent.type)
                assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
                return Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,
                    Intent().setData(image).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.action_settings)).perform(click())
                onView(withId(R.id.categoryBackgrounds)).perform(scrollTo(), click())
                onView(withId(R.id.settingsUseCustomTheme)).perform(scrollTo(), click())
                val previous = app.displaySettings.options.value.imageVersion
                onView(withId(R.id.settingsBackgroundImage)).perform(scrollTo(), click())
                await { app.displaySettings.options.value.imageVersion > previous }
                assertEquals(1, contentRequests)
                assertEquals(1, app.displaySettings.options.value.backgroundImages.size)
                assertFalse(app.contentResolver.persistedUriPermissions.any { it.uri == image })
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            onMain { app.displaySettings.update { it.copy(customTheme = false) } }
        }
    }

    @Test fun savedSourcesFavoritesAndPlaylistsSurviveRealAudioPermissionRevocation() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("afterAudioRevoke") == "true")
        assertFalse(app.repository.documents.deviceMusic.hasPermission())
        val before = app.repository.library.value
        assertEquals(2, before.sources.size)
        assertEquals(3, before.tracks.size)
        assertEquals(1, before.favorites.size)
        assertEquals(3, before.playlists.single().trackIds.size)
        runBlocking {
            for (source in before.sources) {
                try {
                    app.repository.scan(source, null) {}
                    fail("Revoked access must fail before MediaStore can return a filtered empty cursor")
                } catch (_: SecurityException) {
                    assertEquals(before, app.repository.library.value)
                }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withText(before.sources.first().name)).perform(click())
            await { app.notice.value?.text == app.text(R.string.permission_lost) }
            assertEquals(before.tracks, app.repository.library.value.tracks)
            assertEquals(before.favorites, app.repository.library.value.favorites)
            scenario.onActivity { ViewModelProvider(it)[MusicViewModel::class.java].browserUp() }
            screenshot("pickerless-permission-revoked")
        }
    }

    @Test fun chineseCompatibilityCopyAndSavedFolderLabelsFollowTheSelectedLanguage() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pickerless") == "true")
        val saved = app.repository.library.value
        assertTrue(saved.sources.isNotEmpty())
        language("zh")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.nav_folders)).perform(click())
                val label = app.repository.documents.displayName(saved.sources.first())
                assertTrue(label.startsWith("共享存储"))
                assertNotEquals(saved.sources.first().name, label)
                onView(withText(label)).check(matches(isDisplayed()))
                onView(withText(R.string.add_folder)).perform(click())
                onView(withText("系统文件夹选择器不可用")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText("设备音乐")).inRoot(isDialog()).check(matches(isDisplayed()))
                screenshot("pickerless-chinese-alternative")
                onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            }
        } finally {
            language("en")
        }
        assertEquals(saved, app.repository.library.value)
    }

    @Test fun emptyIndexedCatalogShowsAnExplicitLimitationAndCleansUpOwnedFixtures() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("cleanupIndexed") == "true")
        app.contentResolver.call(fixture, "clearIndexedMusic", null, null)
        runBlocking {
            app.repository.update { Library() }
            app.playback.read()
        }
        app.playback.save(PlaybackSnapshot())
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, MediaStoreAccess.requiredPermission())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.nav_folders)).perform(click())
            onView(withText(R.string.device_music)).perform(scrollTo(), click())
            await { app.notice.value?.text == app.text(R.string.device_music_empty) }
            assertTrue(app.repository.library.value.sources.isEmpty())
            scenario.onActivity { assertNull(ViewModelProvider(it)[MusicViewModel::class.java].deviceMusic.value) }
            screenshot("pickerless-empty-index")
            onView(withId(R.id.action_settings)).perform(click())
            onView(withId(R.id.categoryFolders)).perform(scrollTo(), click())
            onView(withId(R.id.settingsDeviceMusic)).perform(scrollTo())
            onView(withId(R.id.settingsDeviceMusic)).check(matches(isDisplayed()))
            scenario.onActivity {
                (it.findViewById<android.view.View>(R.id.toolbar) as com.google.android.material.appbar.MaterialToolbar)
                    .menu.performIdentifierAction(R.id.action_about, 0)
                it.supportFragmentManager.executePendingTransactions()
            }
            onView(withText(containsString("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")))
                .inRoot(isDialog()).check(matches(isDisplayed()))
        }
    }

    private fun language(tag: String) = onMain {
        if (Build.VERSION.SDK_INT >= 33) {
            app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                android.os.LocaleList.forLanguageTags(tag)
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    private fun clickBrowserRow(scenario: ActivityScenario<MainActivity>, title: String) {
        lateinit var panel: ListPanel
        scenario.onActivity { activity ->
            val screens = activity.findViewById<ViewGroup>(R.id.screenContainer)
            panel = (0 until screens.childCount).map(screens::getChildAt)
                .filterIsInstance<ListPanel>().single { it.isShown }
        }
        await { (panel.list.adapter as MusicRowAdapter).currentList.any { it.title == title } }
        scenario.onActivity {
            val rows = (panel.list.adapter as MusicRowAdapter).currentList
            val index = rows.indexOfFirst { it.title == title }
            assertTrue("Missing browser row: $title", index >= 0)
            (panel.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(index, 0)
        }
        instrumentation.waitForIdleSync()
        onView(withText(title)).perform(click())
    }

    private fun permissionButton(id: String) {
        val service = instrumentation.uiAutomation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo = service
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var lastNodes = ""
        while (System.nanoTime() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val packageName = root?.packageName?.toString()
            if (root != null && packageName?.endsWith("permissioncontroller") == true) {
                val nodes = mutableListOf<AccessibilityNodeInfo>()
                fun visit(node: AccessibilityNodeInfo) {
                    nodes.add(node)
                    for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
                }
                visit(root)
                lastNodes = nodes.joinToString { "${it.viewIdResourceName}:${it.text}" }
                val node = nodes.firstOrNull {
                    it.viewIdResourceName?.substringAfter(":id/") == id && it.isVisibleToUser && it.isEnabled
                }
                if (node != null) {
                    if (id == "permission_allow_button") screenshot("pickerless-audio-permission")
                    assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return
                }
            }
            Thread.sleep(100)
        }
        fail("Android did not show the requested runtime-permission button: $id; $lastNodes")
    }

    private fun onMain(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action()
        else instrumentation.runOnMainSync(action)
    }

    private fun <T> onMainResult(action: () -> T): T {
        var result: T? = null
        onMain { result = action() }
        return requireNotNull(result)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (onMainResult(condition)) return
            Thread.sleep(100)
        }
        fail("Storage compatibility condition was not satisfied; notice=${app.notice.value?.text}")
    }

    private fun screenshot(name: String) {
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }
}
