package com.macarmusic.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.DocumentsContract
import android.system.Os
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.macarmusic.app.data.DocumentUris
import com.macarmusic.app.data.LegacyStorage
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.lyrics.CachedLyric
import com.macarmusic.app.lyrics.InternalLyricCache
import com.macarmusic.app.lyrics.LocalLyricsLoader
import com.macarmusic.app.lyrics.LyricCache
import com.macarmusic.app.lyrics.LyricOrigin
import com.macarmusic.app.lyrics.SafLyricCache
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.model.BlockStyle
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.model.ExplorerMode
import com.macarmusic.app.model.LegacyDocumentPaths
import com.macarmusic.app.model.Library
import com.macarmusic.app.ui.ExplorerLocation
import com.macarmusic.app.ui.ExplorerPlace
import com.macarmusic.app.ui.FileExplorerActivity
import com.macarmusic.app.ui.FileExplorerViewModel
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicRowAdapter
import com.macarmusic.app.ui.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class FileExplorerTest {
    private val app = ApplicationProvider.getApplicationContext<MaCarMusicApp>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val documents get() = app.repository.documents
    private val resolver get() = app.contentResolver
    private val music = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.documents", "music")
    private val cacheRoot = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.cache", "root")
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    private val persist = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    @Suppress("DEPRECATION")
    private val files by lazy { File(Environment.getExternalStorageDirectory(), "ManaExplorer-128-test") }

    @Before fun setup() {
        assumeTrue("Synthetic storage tests require an emulator", Build.PRODUCT.contains("sdk"))
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("parkedNoticeSeen", true).commit()
        val grantToken = java.util.UUID.randomUUID().toString()
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureGrantActivity::class.java.name)
            putExtra("cacheGrantToken", grantToken)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { app.checkUriPermission(music, Process.myPid(), Process.myUid(), read) == PackageManager.PERMISSION_GRANTED }
        await { resolver.call(cacheRoot, "cacheFixture", null, null)?.getString("grantToken") == grantToken }
        runBlocking {
            app.repository.awaitReady()
            app.repository.update { Library() }
            app.lyrics.cache.useInternal()
        }
        resolver.call(music, "setUnavailable", null, Bundle().apply { putBoolean("value", false) })
        resolver.call(music, "scanFixtureState", null, Bundle().apply { putBoolean("resetReads", true) })
        resolver.call(cacheRoot, "cacheFixture", null, Bundle().apply { putBoolean("reset", true) })
        onMain {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false) }
        }
    }

    @After fun cleanup() {
        resolver.call(music, "scanFixtureState", null, Bundle())
        runBlocking {
            app.lyrics.cache.useInternal()
            app.repository.update { Library() }
        }
        for (grant in resolver.persistedUriPermissions.filter { it.uri.authority?.startsWith("com.macarmusic.app.test.") == true }) {
            resolver.releasePersistableUriPermission(grant.uri, (if (grant.isReadPermission) read else 0) or
                if (grant.isWritePermission) write else 0)
        }
        if (LegacyStorage.permitted(app, true) && files.isDirectory) {
            check(files.name == "ManaExplorer-128-test")
            assertTrue(files.deleteRecursively())
        }
    }

    private fun seedFiles() {
        assumeTrue(LegacyStorage.available())
        for (permission in LegacyStorage.permissions(true)) shell("pm grant ${app.packageName} $permission")
        await { LegacyStorage.permitted(app, true) }
        assertTrue(files.isDirectory || files.mkdirs())
        val nested = File(files, "Album").apply { assertTrue(isDirectory || mkdir()) }
        val original = DocumentsContract.buildDocumentUriUsingTree(music, "music/one")
        resolver.openInputStream(original)!!.use { input -> File(files, "消防.wav").outputStream().use(input::copyTo) }
        File(files, "消防.wav").copyTo(File(nested, "Second.wav"), overwrite = true)
        File(files, "消防.lrc").writeText("[00:00]Local explorer lyric\n[00:55]Ending")
        File(files, "notes.txt").writeText("Not an audio selection")
        val picture = DocumentsContract.buildDocumentUri("com.macarmusic.app.test.documents", "pictures/background")
        val bytes = resolver.openInputStream(picture)!!.use { it.readBytes() }
        val second = resolver.openInputStream(DocumentsContract.buildDocumentUri(
            "com.macarmusic.app.test.documents", "pictures/second"))!!.use { it.readBytes() }
        for (index in 1..22) File(files, "Picture-%02d.png".format(index)).writeBytes(if (index == 2) second else bytes)
    }

    private fun uri(file: File = files): Uri {
        val root = LegacyStorage.roots(app).single { file.canonicalPath.startsWith(it.directory.path + File.separator) }
        val id = LegacyDocumentPaths.document(root, file.canonicalFile).id
        return DocumentsContract.buildDocumentUriUsingTree(LegacyStorage.tree(app, root), id)
    }

    @Test fun legacyProviderScansOnlyTheChosenFolderAndReadsLocalLyrics() = runBlocking(Dispatchers.IO) {
        seedFiles()
        val root = uri()
        val source = app.repository.addSources(listOf(root), read).single()
        assertTrue(source.persisted)
        assertEquals(DocumentsContract.getDocumentId(root), documents.rootId(root.toString()))
        assertTrue(resolver.persistedUriPermissions.none { LegacyStorage.owns(app, it.uri) })
        val children = documents.children(source.uri, documents.rootId(source.uri))
        assertEquals(listOf("Album", "消防.wav"), children.map { it.name })
        val result = app.repository.scan(source, null) {}
        assertEquals(2, result.tracks.size)
        assertTrue(result.tracks.all { it.durationMs in 59_000..61_000 })
        val first = result.tracks.single { it.fileName == "消防.wav" }
        val lyrics = LocalLyricsLoader(app, app.repository).load(first)
        assertEquals(LyricOrigin.LOCAL_FILE, lyrics.origin)
        assertEquals("Local explorer lyric", lyrics.lyrics!!.lines.first().text)
        assertEquals(2, app.repository.scan(source, null) {}.reusedMetadata)
        val nested = app.repository.addSources(listOf(uri(File(files, "Album"))), read).single()
        assertEquals(listOf("Second.wav"), app.repository.scan(nested, null) {}.tracks.map { it.fileName })
        val before = app.repository.library.value
        val unavailable = File(files.parentFile, "ManaExplorer-128-detached")
        assertFalse(unavailable.exists())
        assertTrue(files.renameTo(unavailable))
        try {
            try {
                app.repository.scan(source, null) {}
                fail("Unavailable storage must not be committed as an empty library")
            } catch (_: IOException) {
                assertEquals(before, app.repository.library.value)
            }
        } finally { assertTrue(unavailable.renameTo(files)) }
    }

    @Test fun localProviderDoesNotOverwriteMusicOrEscapeSharedStorage() {
        seedFiles()
        val song = File(files, "消防.wav")
        val before = song.readBytes()
        rejected { resolver.openOutputStream(uri(song), "wt")!!.close() }
        rejected { DocumentsContract.deleteDocument(resolver, uri(song)) }
        for (name in listOf("../escape.zip", "/escape.zip", "bad\\name.zip")) {
            rejected { DocumentsContract.createDocument(resolver, uri(), "application/zip", name) }
        }
        val parent = DocumentUris.rootId(uri()).substringBefore(':')
        val privateUri = DocumentsContract.buildDocumentUriUsingTree(uri(), "$parent:Android/data")
        rejected { documents.describe(privateUri) }
        val outside = DocumentsContract.buildDocumentUriUsingTree(uri(), "$parent:../outside.wav")
        rejected { documents.describe(outside) }
        assertArrayEquals(before, song.readBytes())
        val created = DocumentsContract.createDocument(resolver, uri(), "application/zip", "New backup.zip")!!
        resolver.openOutputStream(created, "wt")!!.use { it.write("Synthetic backup".toByteArray()) }
        assertEquals("Synthetic backup", File(files, "New backup.zip").readText())
        rejected { DocumentsContract.createDocument(resolver, uri(), "application/zip", "New backup.zip") }
        assertEquals("Synthetic backup", File(files, "New backup.zip").readText())
    }

    @Test fun pathPolicyRejectsSymlinkFilesDirectoriesAndCycles() {
        val base = File(app.cacheDir, "explorer-links-test").apply { mkdirs() }
        val root = LegacyDocumentPaths.Root(File(base, "root").apply { mkdir() }.canonicalFile)
        val outside = File(base, "outside.txt").apply { writeText("private synthetic data") }
        try {
            Os.symlink(outside.path, File(root.directory, "linked.mp3").path)
            Os.symlink(base.path, File(root.directory, "parent").path)
            Os.symlink("cycle", File(root.directory, "cycle").path)
            rejected { LegacyDocumentPaths.resolve(listOf(root), root.key + ":linked.mp3") }
            rejected { LegacyDocumentPaths.resolve(listOf(root), root.key + ":parent/outside.txt") }
            rejected { LegacyDocumentPaths.resolve(listOf(root), root.key + ":cycle/file.mp3") }
            assertEquals("private synthetic data", outside.readText())
        } finally {
            for (name in listOf("linked.mp3", "parent", "cycle")) File(root.directory, name).delete()
            root.directory.delete()
            outside.delete()
            base.delete()
        }
    }

    @Test fun safDescendantSelectionKeepsTheAncestorGrantAndSiblingAccess() = runBlocking(Dispatchers.IO) {
        resolver.takePersistableUriPermission(music, read)
        val child = DocumentsContract.buildDocumentUriUsingTree(music, "music/album")
        val source = app.repository.addSources(listOf(child), 0).single()
        assertTrue(source.persisted)
        assertEquals("music/album", documents.rootId(source.uri))
        assertEquals(listOf("02 - Nested.WAV"), app.repository.scan(source, null) {}.tracks.map { it.fileName })
        assertTrue(documents.releaseReadPermissions(setOf(music.toString()), setOf(child.toString())).isEmpty())
        assertTrue(resolver.persistedUriPermissions.any { it.uri == music && it.isReadPermission })
        val standalone = DocumentsContract.buildDocumentUriUsingTree(music, "music/one")
        assertTrue(documents.retainReadPermission(standalone, 0))
        assertTrue(documents.releaseReadPermissions(setOf(child.toString()), setOf(standalone.toString())).isEmpty())
        assertTrue(resolver.persistedUriPermissions.any { it.uri == music })
        assertTrue(documents.releaseReadPermissions(setOf(child.toString())).isEmpty())
        assertTrue(resolver.persistedUriPermissions.none { it.uri == music })
    }

    @Test fun movingCacheBetweenSafSubfoldersDoesNotReleaseTheirSharedGrant() = runBlocking(Dispatchers.IO) {
        resolver.takePersistableUriPermission(cacheRoot, read or write)
        val first = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot),
            DocumentsContract.Document.MIME_TYPE_DIR, "Cache parent A")!!
        val second = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot),
            DocumentsContract.Document.MIME_TYPE_DIR, "Cache parent B")!!
        val isolated = isolatedContext()
        val retained = mutableSetOf(first.toString())
        val cache = LyricCache(isolated, { retained }, { fail(it) }, System::currentTimeMillis, documents.grantMutex)
        val value = CachedLyric("[00:01]Portable native fixture\n[00:55]End", LyricOrigin.QQ, System.currentTimeMillis())
        val key = "a".repeat(64)
        InternalLyricCache(File(isolated.cacheDir, "lyrics"), System::currentTimeMillis).write(key, value)
        assertEquals(1L, cache.moveInternalTo(first, 0).files)
        assertEquals(value, cache.read(key).value)
        assertTrue(cache.info.value.folder!!.name.startsWith("Cache parent A / "))
        assertEquals(listOf("Cache parent A", "Cache parent B"),
            documents.children(cacheRoot.toString(), "root", allFiles = true).map { it.name })
        cache.moveInternalTo(second, 0)
        assertTrue(cache.info.value.folder!!.name.startsWith("Cache parent B / "))
        assertTrue(resolver.persistedUriPermissions.any { it.uri == cacheRoot && it.isReadPermission && it.isWritePermission })
        cache.useInternal()
        val remaining = resolver.persistedUriPermissions.single { it.uri == cacheRoot }
        assertTrue(remaining.isReadPermission)
        assertFalse(remaining.isWritePermission)
    }

    @Test fun legacyCacheUsesRuntimeAccessWithoutInventingAPersistedUriGrant() = runBlocking(Dispatchers.IO) {
        seedFiles()
        val isolated = isolatedContext()
        val value = CachedLyric("[00:00]Legacy cache fixture\n[00:55]End", LyricOrigin.QQ, System.currentTimeMillis())
        val key = "b".repeat(64)
        InternalLyricCache(File(isolated.cacheDir, "lyrics"), System::currentTimeMillis).write(key, value)
        val grants = resolver.persistedUriPermissions.map { it.uri }
        val cache = LyricCache(isolated, { emptySet() }, { fail(it) }, System::currentTimeMillis, documents.grantMutex)
        assertEquals(1L, cache.moveInternalTo(uri(), read or write).files)
        assertTrue(File(files, "${SafLyricCache.DIRECTORY}/cache-info.json").isFile)
        assertEquals(value, cache.read(key).value)
        assertEquals(1L, cache.clear().files)
        assertTrue(File(files, "消防.lrc").isFile)
        cache.useInternal()
        assertEquals(grants, resolver.persistedUriPermissions.map { it.uri })
    }

    @Test fun nativeMusicPickerBrowsesWithoutScanningAndReturnsOneContentUri() {
        seedFiles()
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            goToFiles(scenario)
            assertTrue(app.repository.library.value.tracks.isEmpty())
            val items = model(scenario).state.value.items
            assertEquals(listOf("Album", "消防.wav"), items.map { it.name })
            scenario.recreate()
            await { !model(scenario).state.value.loading }
            assertEquals("ManaExplorer-128-test", model(scenario).state.value.path.last().name)
            screenshot("explorer-native-music")
            tapRow(scenario, "消防.wav")
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(uri(File(files, "消防.wav")), scenario.result.resultData!!.data)
            assertEquals(read, scenario.result.resultData!!.flags and (read or write or persist))
            assertTrue(app.repository.library.value.tracks.isEmpty())
        }
    }

    @Test fun nativeImagePickerKeepsTwentySelectionsThroughNavigationAndRotation() {
        seedFiles()
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.IMAGES)).use { scenario ->
            goToFiles(scenario)
            assertEquals(23, model(scenario).state.value.items.size)
            for (index in 1..20) tapRow(scenario, "Picture-%02d.png".format(index))
            tapRow(scenario, "Picture-21.png")
            assertEquals(20, model(scenario).state.value.selected.size)
            assertNotNull(model(scenario).state.value.error)
            tap(scenario, R.id.explorerUp)
            await { !model(scenario).state.value.loading }
            tapRow(scenario, files.name)
            scenario.recreate()
            await { !model(scenario).state.value.loading }
            assertEquals(20, model(scenario).state.value.selected.size)
            screenshot("explorer-native-images")
            tap(scenario, R.id.explorerSelect)
            assertEquals(20, scenario.result.resultData!!.clipData!!.itemCount)
        }
    }

    @Test fun backupExportCreatesANewFileAndRefusesExistingNames() {
        seedFiles()
        File(files, "Keep.zip").writeText("Existing backup must stay")
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.BACKUP_EXPORT, "Keep.zip")).use { scenario ->
            goToFiles(scenario)
            tap(scenario, R.id.explorerSelect)
            await { model(scenario).state.value.error != null && !model(scenario).state.value.saving }
            assertEquals("Existing backup must stay", File(files, "Keep.zip").readText())
            onView(withId(R.id.explorerFilename)).perform(click(), replaceText("New backup"), pressImeActionButton())
            await {
                var ready = false
                scenario.onActivity {
                    ready = !it.findViewById<View>(R.id.explorerFilename).hasFocus() &&
                        it.findViewById<View>(R.id.explorerList).height >= 144 * it.resources.displayMetrics.density
                }
                ready
            }
            tap(scenario, R.id.explorerSelect)
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertTrue(File(files, "New backup.zip").isFile)
            assertEquals(uri(File(files, "New backup.zip")), scenario.result.resultData!!.data)
        }
    }

    @Test fun readOnlySafFolderCannotBecomeAWritableDestination() {
        val readonly = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.cache", "readonly")
        resolver.takePersistableUriPermission(readonly, read or write)
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.LYRIC_CACHE)).use { scenario ->
            await { !model(scenario).state.value.loading }
            val row = model(scenario).state.value.items.single { it.location?.uri == readonly.toString() }
            tapRow(scenario, row.name)
            assertFalse(model(scenario).state.value.canSelectFolder)
            scenario.onActivity {
                assertFalse(it.findViewById<View>(R.id.explorerSelect).isEnabled)
                assertEquals(it.getString(R.string.explorer_read_only), it.findViewById<TextView>(R.id.explorerStatus).text)
            }
        }
    }

    @Test fun newerAndroidDoesNotOfferOrPermitRawStorageAndStillUsesSaf() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        assertFalse(LegacyStorage.permitted(app))
        val raw = DocumentsContract.buildDocumentUri(LegacyStorage.authority(app), "0".repeat(32) + ":Music")
        rejected { documents.describe(raw) }
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
            await { !model(scenario).state.value.loading }
            assertTrue(model(scenario).state.value.items.none { it.location?.place == ExplorerPlace.DEVICE })
            assertTrue(model(scenario).state.value.items.any { it.location?.place == ExplorerPlace.INDEX })
            tapRow(scenario, model(scenario).state.value.items.single { it.location?.uri == music.toString() }.name)
            tapRow(scenario, "Album")
            screenshot("explorer-scoped-saf")
            tap(scenario, R.id.explorerSelect)
            assertEquals("music/album", DocumentUris.rootId(scenario.result.resultData!!.data!!))
        }
    }

    @Test fun cancelledSlowFolderQueryCannotReplaceANewerLocation() {
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            await { !model(scenario).state.value.loading }
            resolver.call(music, "scanFixtureState", null, Bundle().apply {
                putLong("childDelayMs", 1_500); putBoolean("resetReads", true)
            })
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java]
                .navigate(ExplorerLocation(ExplorerPlace.FOLDER, "Slow synthetic folder", music.toString())) }
            await { (resolver.call(music, "scanFixtureState", null, null)?.getInt("childReads") ?: 0) > 0 }
            tap(scenario, R.id.explorerLocations)
            await { !model(scenario).state.value.loading }
            assertTrue(model(scenario).state.value.path.isEmpty())
            SystemClock.sleep(1_700)
            assertTrue(model(scenario).state.value.path.isEmpty())
            assertTrue(model(scenario).state.value.items.any { it.grant })
            assertNull(model(scenario).state.value.error)
        }
    }

    @Test fun largeDirectoryRecyclesRowsWithoutReadingAudioOrImportingTracks() {
        resolver.call(music, "scanFixtureState", null, Bundle().apply {
            putInt("extraRows", 2_048); putBoolean("resetReads", true)
        })
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            val start = SystemClock.elapsedRealtime()
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java]
                .navigate(ExplorerLocation(ExplorerPlace.FOLDER, "Large synthetic folder", music.toString())) }
            await { !model(scenario).state.value.loading }
            assertNull(model(scenario).state.value.error)
            assertEquals(2_050, model(scenario).state.value.items.size)
            val elapsed = SystemClock.elapsedRealtime() - start
            await {
                var ready = false
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.explorerList)
                    ready = list.adapter!!.itemCount == 2_050 && list.childCount > 0
                }
                ready
            }
            scenario.onActivity {
                assertTrue(it.findViewById<RecyclerView>(R.id.explorerList).childCount < 40)
            }
            val childReads = resolver.call(music, "scanFixtureState", null, null)!!.getInt("childReads")
            remoteKey(KeyEvent.KEYCODE_DPAD_UP)
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.explorerRefresh).requestFocus()) }
            for (position in 0..60) {
                remoteKey(KeyEvent.KEYCODE_DPAD_DOWN)
                assertEquals("D-pad must traverse recycled rows", position, remotePosition(scenario))
            }
            await {
                var idle = false
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.explorerList)
                    idle = list.scrollState == RecyclerView.SCROLL_STATE_IDLE && !list.isLayoutRequested
                }
                idle
            }
            scenario.onActivity {
                val count = it.findViewById<RecyclerView>(R.id.explorerList).childCount
                assertTrue("Settled D-pad scrolling must recycle rows, attached=$count", count < 40)
            }
            assertEquals("Moving focus must not query the provider again", childReads,
                resolver.call(music, "scanFixtureState", null, null)!!.getInt("childReads"))
            assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
            assertTrue(app.repository.library.value.tracks.isEmpty())
            assertTrue(app.repository.library.value.sources.isEmpty())
            File(app.getExternalFilesDir(null), "explorer-listing.txt")
                .writeText("2050 displayed entries; 61 D-pad rows; audio reads=0; no focus-time queries; recycled rows<40; listingMs=$elapsed\n")
        }
    }

    @Test fun remoteOpensNestedFoldersAndSelectsAFileWithoutResettingFocus() {
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            await { !model(scenario).state.value.loading }
            val rootPosition = model(scenario).state.value.items.indexOfFirst { it.location?.uri == music.toString() }
            assertTrue(rootPosition >= 0)
            remoteKey(KeyEvent.KEYCODE_DPAD_UP)
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.explorerRefresh).requestFocus()) }
            repeat(rootPosition + 1) { remoteKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            assertEquals(rootPosition, remotePosition(scenario))
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !model(scenario).state.value.loading &&
                model(scenario).state.value.items.any { it.name == "Album" } }
            await { remotePosition(scenario) >= 0 }
            assertEquals("New folder must keep remote focus in its list", 0, remotePosition(scenario))
            assertEquals("Album", model(scenario).state.value.items[0].name)
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !model(scenario).state.value.loading &&
                model(scenario).state.value.items.any { it.name == "02 - Nested.WAV" } }
            await { remotePosition(scenario) >= 0 }
            assertEquals(0, remotePosition(scenario))
            val selected = model(scenario).state.value.items.single().document!!.uri
            assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
            assertTrue(app.repository.library.value.tracks.isEmpty())
            screenshot("remote-nested-file")
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(Uri.parse(selected), scenario.result.resultData!!.data)
        }
    }

    @Test fun slowFolderCompletionDoesNotTakeFocusBackFromTheToolbarOrTouchInput() {
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java]
                .navigate(ExplorerLocation(ExplorerPlace.FOLDER, "Synthetic folder", music.toString())) }
            await { !model(scenario).state.value.loading }
            remoteKey(KeyEvent.KEYCODE_DPAD_UP)
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.explorerRefresh).requestFocus()) }
            remoteKey(KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(0, remotePosition(scenario))
            lateinit var activity: FileExplorerActivity
            scenario.onActivity { activity = it }
            val viewModel = model(scenario)
            resolver.call(music, "scanFixtureState", null, Bundle().apply { putLong("childDelayMs", 2_000) })
            onMain { assertTrue(activity.currentFocus!!.performClick()) }
            // ActivityScenario waits for UI idleness; observe/move focus while the query is still running.
            await { onMainResult { viewModel.state.value.loading &&
                activity.findViewById<View>(R.id.explorerList).isFocused } }
            onMain { assertTrue(activity.findViewById<View>(R.id.explorerRefresh).requestFocus()) }
            await { !viewModel.state.value.loading }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue(it.findViewById<View>(R.id.explorerRefresh).isFocused) }
            resolver.call(music, "scanFixtureState", null, Bundle())
            tap(scenario, R.id.explorerUp)
            await { !model(scenario).state.value.loading }
            instrumentation.setInTouchMode(true)
            tapRow(scenario, "Album")
            instrumentation.waitForIdleSync()
            assertEquals("Touch browsing must not create a keyboard selection", RecyclerView.NO_POSITION,
                remotePosition(scenario))
        }
    }

    @Test fun mainPickerWorkflowsApplyNativeSelectionsWithoutInterruptingPlayback() {
        seedFiles()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val future = MediaController.Builder(app, SessionToken(app, ComponentName(app,
                com.macarmusic.app.playback.PlaybackService::class.java))).buildAsync()
            val controller = future.get(20, TimeUnit.SECONDS)
            try {
                await { app.playbackReady.value }
                onMain { controller.pause(); controller.clearMediaItems() }
                mainTap(scenario, R.id.nav_folders)
                onView(withText(R.string.add_folder)).perform(click())
                var picker = explorerAtFiles(ExplorerMode.MUSIC_FOLDER)
                onMain { assertTrue(picker.findViewById<View>(R.id.explorerSelect).performClick()) }
                await { app.repository.library.value.tracks.size == 2 &&
                    app.repository.library.value.sources.singleOrNull()?.lastScanCount == 2 }
                assertFalse(onMainResult { controller.playWhenReady })
                onView(withText(R.string.open_file)).perform(click())
                picker = explorerAtFiles(ExplorerMode.MUSIC_FILE)
                nativeRow(picker, "消防.wav")
                await { onMainResult { controller.isPlaying && controller.mediaItemCount == 1 } }
                onMain { controller.repeatMode = Player.REPEAT_MODE_ONE }

                mainTap(scenario, R.id.action_settings)
                mainTap(scenario, R.id.settingsTheme)
                onView(withText(R.string.theme_custom)).inRoot(isDialog()).perform(click())
                await { app.displaySettings.options.value.customTheme }
                mainTap(scenario, R.id.settingsBackgroundImage)
                picker = explorerAtFiles(ExplorerMode.IMAGES)
                nativeRow(picker, "Picture-01.png")
                nativeRow(picker, "Picture-02.png")
                onMain { assertTrue(picker.findViewById<View>(R.id.explorerSelect).performClick()) }
                await { app.displaySettings.options.value.backgroundImages.size == 2 }
                assertTrue(onMainResult { controller.isPlaying })

                val lyric = CachedLyric("[00:00]Explorer roundtrip\n[00:55]Ending",
                    LyricOrigin.QQ, System.currentTimeMillis())
                val key = "c".repeat(64)
                InternalLyricCache(File(app.cacheDir, "lyrics"), System::currentTimeMillis).write(key, lyric)
                mainTap(scenario, R.id.categoryLyrics)
                mainTap(scenario, R.id.lyricsCacheMove)
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                picker = explorerAtFiles(ExplorerMode.LYRIC_CACHE)
                onMain { assertTrue(picker.findViewById<View>(R.id.explorerSelect).performClick()) }
                await { app.lyrics.cache.info.value.folder != null && !app.lyrics.cache.info.value.busy }
                assertEquals(lyric, runBlocking { app.lyrics.cache.read(key).value })
                assertTrue(onMainResult { controller.isPlaying })

                mainTap(scenario, R.id.categoryGeneral)
                mainTap(scenario, R.id.settingsBackup)
                picker = explorerAtFiles(ExplorerMode.BACKUP_EXPORT)
                onMain {
                    picker.findViewById<TextView>(R.id.explorerFilename).text = "Explorer roundtrip"
                    assertTrue(picker.findViewById<View>(R.id.explorerSelect).performClick())
                }
                val backup = File(files, "Explorer roundtrip.zip")
                lateinit var mainModel: MusicViewModel
                scenario.onActivity { mainModel = ViewModelProvider(it)[MusicViewModel::class.java] }
                await { backup.length() > 0 && !mainModel.backupBusy.value }
                ZipFile(backup).use { assertNotNull(it.getEntry("manifest.json")) }
                val blocks = app.displaySettings.options.value.controlBlocks
                onMain { app.displaySettings.update { it.copy(controlBlocks = !blocks) } }
                mainTap(scenario, R.id.settingsRestore)
                picker = explorerAtFiles(ExplorerMode.BACKUP_IMPORT)
                nativeRow(picker, backup.name)
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { app.displaySettings.options.value.controlBlocks == blocks && !mainModel.backupBusy.value }
                assertFalse(onMainResult { controller.playWhenReady })
                assertEquals(2, app.displaySettings.options.value.backgroundImages.size)

                val restoredId = "content://${BackupEdits.RESTORED_AUTHORITY}/track/explorer-test"
                runBlocking {
                    app.repository.update { library ->
                        val missing = library.tracks.first().copy(id = restoredId, uri = restoredId,
                            title = "Restored explorer song", missing = true, sourceUris = emptyMap(),
                            standaloneUri = restoredId, standalonePersisted = false,
                            sourcePaths = emptyMap(), sourceParents = emptyMap(), metadataStamp = null)
                        library.copy(tracks = library.tracks + missing, favorites = library.favorites + restoredId)
                    }
                }
                mainTap(scenario, R.id.nav_library)
                lateinit var list: RecyclerView
                scenario.onActivity { list = it.findViewById(R.id.libraryList) }
                var position = -1
                await { onMainResult {
                    position = (list.adapter as MusicRowAdapter).currentList.indexOfFirst { it.key == restoredId }
                    position >= 0 && !list.hasPendingAdapterUpdates()
                } }
                onMain { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
                await { onMainResult { list.findViewHolderForAdapterPosition(position) != null } }
                onMain { assertTrue(list.findViewHolderForAdapterPosition(position)!!.itemView
                    .findViewById<View>(R.id.more).performClick()) }
                onView(withText(R.string.reconnect_track)).inRoot(isDialog()).perform(click())
                picker = explorerAtFiles(ExplorerMode.MUSIC_FILE)
                nativeRow(picker, "消防.wav")
                await { app.repository.library.value.tracks.none { it.id == restoredId } }
                assertTrue(app.repository.library.value.tracks.any {
                    restoredId in it.restoredIds && !it.missing && it.id in app.repository.library.value.favorites
                })
                assertFalse(onMainResult { controller.playWhenReady })
                screenshot("explorer-main-roundtrip")
            } finally {
                onMain { controller.pause(); controller.clearMediaItems(); MediaController.releaseFuture(future) }
            }
        }
    }

    @Test fun largeFontAndPlayerBordersKeepExplorerActionsInsideTheWindow() {
        val previous = android.provider.Settings.System.getFloat(resolver, "font_scale", 1f)
        shell("settings put system font_scale 2.0")
        try {
            for ((language, night) in listOf("en" to AppCompatDelegate.MODE_NIGHT_YES, "zh" to AppCompatDelegate.MODE_NIGHT_NO)) {
                onMain {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                    app.themeMode = night
                    app.displaySettings.update { it.copy(controlBlocks = true,
                        blockOutline = BlockOutline(style = BlockStyle.CHARACTERS, character = "+", arcDp = 16)) }
                }
                ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
                    await { !model(scenario).state.value.loading }
                    await {
                        var applied = false
                        scenario.onActivity { applied = kotlin.math.abs(it.resources.configuration.fontScale - 2f) < 0.01f }
                        applied
                    }
                    scenario.onActivity { activity ->
                        assertEquals(2f, activity.resources.configuration.fontScale, 0.01f)
                        val root = activity.findViewById<View>(R.id.explorerRoot)
                        for (id in listOf(R.id.explorerAndroidPicker, R.id.explorerCancel)) {
                            val view = activity.findViewById<TextView>(id)
                            assertNotNull(view.foreground)
                            assertTrue(view.width >= 48 * activity.resources.displayMetrics.density)
                            val location = IntArray(2).also(view::getLocationOnScreen)
                            assertTrue(location[1] + view.height <= root.height)
                            assertTrue(view.layout.height <= view.height - view.totalPaddingTop - view.totalPaddingBottom)
                        }
                    }
                    screenshot("explorer-$language-theme")
                }
            }
        } finally { shell("settings put system font_scale $previous") }
    }

    private fun isolatedContext(): Context = object : ContextWrapper(app) {
        override fun getCacheDir() = File(app.cacheDir, "explorer-cache-test").apply { mkdirs() }
        override fun getFilesDir() = File(app.filesDir, "explorer-cache-test").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) =
            app.getSharedPreferences("explorer-cache-test-$name", mode)
    }.also {
        it.getSharedPreferences("lyric_cache", Context.MODE_PRIVATE).edit().clear().commit()
        InternalLyricCache(File(it.cacheDir, "lyrics"), System::currentTimeMillis).clear()
    }

    private fun goToFiles(scenario: ActivityScenario<FileExplorerActivity>) {
        await { !model(scenario).state.value.loading }
        if (model(scenario).mode == ExplorerMode.BACKUP_EXPORT) {
            screenshot("explorer-backup-start")
            scenario.onActivity {
                assertTrue("Backup browser has no room for navigation: list height=" +
                    it.findViewById<View>(R.id.explorerList).height,
                    it.findViewById<View>(R.id.explorerList).height >= 144 * it.resources.displayMetrics.density)
                assertFalse(it.findViewById<View>(R.id.explorerFilename).hasFocus())
            }
        }
        tapRow(scenario, app.text(R.string.explorer_device_storage))
        tapRow(scenario, app.text(R.string.device_shared_storage))
        tapRow(scenario, files.name)
    }

    private fun mainTap(scenario: ActivityScenario<MainActivity>, id: Int) {
        await {
            var ready = false
            scenario.onActivity { ready = it.findViewById<View>(id)?.let { view -> view.isShown && view.isEnabled } == true }
            ready
        }
        scenario.onActivity { assertTrue(it.findViewById<View>(id).performClick()) }
        instrumentation.waitForIdleSync()
    }

    private fun explorerAtFiles(mode: ExplorerMode): FileExplorerActivity {
        var picker: FileExplorerActivity? = null
        await { onMainResult {
            picker = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FileExplorerActivity>().singleOrNull()
            picker != null
        } }
        val activity = requireNotNull(picker)
        val model = onMainResult { ViewModelProvider(activity)[FileExplorerViewModel::class.java] }
        assertEquals(mode, model.mode)
        onMain { model.navigate(ExplorerLocation(ExplorerPlace.FOLDER, files.name, uri().toString())) }
        await { !model.state.value.loading }
        assertNull(model.state.value.error)
        return activity
    }

    private fun nativeRow(activity: FileExplorerActivity, name: String) {
        val model = onMainResult { ViewModelProvider(activity)[FileExplorerViewModel::class.java] }
        val position = model.state.value.items.indexOfFirst { it.name == name }
        assertTrue("Missing native entry $name", position >= 0)
        val list = onMainResult { activity.findViewById<RecyclerView>(R.id.explorerList) }
        await { onMainResult { list.adapter!!.itemCount == model.state.value.items.size && !list.hasPendingAdapterUpdates() } }
        onMain { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
        await { onMainResult { list.findViewHolderForAdapterPosition(position) != null } }
        onMain { assertTrue(list.findViewHolderForAdapterPosition(position)!!.itemView.performClick()) }
        instrumentation.waitForIdleSync()
    }

    private fun model(scenario: ActivityScenario<FileExplorerActivity>): FileExplorerViewModel {
        val value = AtomicReference<FileExplorerViewModel>()
        scenario.onActivity { value.set(ViewModelProvider(it)[FileExplorerViewModel::class.java]) }
        return value.get()
    }

    private fun tapRow(scenario: ActivityScenario<FileExplorerActivity>, name: String) {
        await { !model(scenario).state.value.loading }
        val viewModel = model(scenario)
        val position = viewModel.state.value.items.indexOfFirst { it.name == name }
        assertTrue("Missing explorer row $name: ${model(scenario).state.value}", position >= 0)
        val returnsFile = viewModel.state.value.items[position].document?.isDirectory == false &&
            viewModel.mode != ExplorerMode.IMAGES
        await {
            var ready = false
            scenario.onActivity {
                val list = it.findViewById<RecyclerView>(R.id.explorerList)
                ready = list.adapter!!.itemCount == viewModel.state.value.items.size && !list.hasPendingAdapterUpdates()
            }
            ready
        }
        scenario.onActivity {
            (it.findViewById<RecyclerView>(R.id.explorerList).layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(position, 0)
        }
        await {
            var ready = false
            scenario.onActivity { ready = it.findViewById<RecyclerView>(R.id.explorerList)
                .findViewHolderForAdapterPosition(position) != null }
            ready
        }
        scenario.onActivity {
            val list = it.findViewById<RecyclerView>(R.id.explorerList)
            assertTrue(list.findViewHolderForAdapterPosition(position)!!.itemView.performClick())
        }
        instrumentation.waitForIdleSync()
        if (!returnsFile) await { !model(scenario).state.value.loading }
    }

    private fun tap(scenario: ActivityScenario<FileExplorerActivity>, id: Int) {
        scenario.onActivity { assertTrue(it.findViewById<View>(id).performClick()) }
        instrumentation.waitForIdleSync()
    }

    private fun remoteKey(code: Int) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(down, SystemClock.uptimeMillis(), action, code, 0, 0,
                KeyEvent.KEYCODE_UNKNOWN, 0, 0, InputDevice.SOURCE_DPAD))
        }
        instrumentation.waitForIdleSync()
    }

    private fun remotePosition(scenario: ActivityScenario<FileExplorerActivity>): Int {
        var position = RecyclerView.NO_POSITION
        scenario.onActivity {
            val list = it.findViewById<RecyclerView>(R.id.explorerList)
            val row = it.currentFocus?.let(list::findContainingItemView)
            if (row != null) position = list.getChildAdapterPosition(row)
        }
        return position
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        try {
            val directory = File(app.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun rejected(action: () -> Any?) {
        try {
            val result = action()
            assertTrue("Unsafe storage operation succeeded", result == null || result == false)
        }
        catch (_: IOException) { }
        catch (_: SecurityException) { }
        catch (_: IllegalArgumentException) { }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun <T> onMainResult(action: () -> T): T {
        val result = AtomicReference<T>()
        onMain { result.set(action()) }
        return result.get()
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!predicate()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Timed out waiting for explorer state")
            SystemClock.sleep(40)
        }
    }
}
