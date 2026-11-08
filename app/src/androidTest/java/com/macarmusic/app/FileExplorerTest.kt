package com.macarmusic.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
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
import android.view.ViewGroup
import android.widget.HorizontalScrollView
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
import com.macarmusic.app.model.LibraryEdits
import com.macarmusic.app.radio.CnrRadio
import com.macarmusic.app.radio.RadioList
import com.macarmusic.app.radio.RadioDefaults
import com.macarmusic.app.ui.ExplorerLocation
import com.macarmusic.app.ui.ExplorerPlace
import com.macarmusic.app.ui.FileExplorerActivity
import com.macarmusic.app.ui.FileExplorerViewModel
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.MusicRowAdapter
import com.macarmusic.app.ui.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
        grantFixtures()
        assertTrue(app.getSharedPreferences("explorer", Context.MODE_PRIVATE).edit().clear().commit())
        runBlocking {
            app.repository.awaitReady()
            app.repository.update { Library() }
            app.lyrics.cache.useInternal()
        }
        resolver.call(music, "setUnavailable", null, Bundle().apply { putBoolean("value", false) })
        resolver.call(music, "scanFixtureState", null, Bundle().apply { putBoolean("resetReads", true) })
        resolver.call(cacheRoot, "cacheFixture", null, Bundle().apply { putBoolean("reset", true) })
        onMain {
            setLanguage("en")
            app.themeMode = AppCompatDelegate.MODE_NIGHT_YES
            app.displaySettings.update { it.copy(lyricsEnabled = false, onlineLyrics = false, customTheme = false) }
        }
    }

    private fun grantFixtures() {
        val grantToken = java.util.UUID.randomUUID().toString()
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName("com.macarmusic.app.test", FixtureGrantActivity::class.java.name)
            putExtra("cacheGrantToken", grantToken)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        await { app.checkUriPermission(music, Process.myPid(), Process.myUid(), read) == PackageManager.PERMISSION_GRANTED }
        await { app.checkUriPermission(cacheRoot, Process.myPid(), Process.myUid(), read) == PackageManager.PERMISSION_GRANTED }
        await { resolver.call(cacheRoot, "cacheFixture", null, null)?.getString("grantToken") == grantToken }
    }

    private fun setLanguage(tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            app.getSystemService(android.app.LocaleManager::class.java).applicationLocales =
                android.os.LocaleList.forLanguageTags(tag)
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
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

    @Test fun radioListsUseGrantedUsbStyleFoldersAndSurvivePreviewRecreationWithoutWritingSources() {
        val name = "Radio stations.M3U"
        val content = "#EXTM3U\n#EXTINF:-1 tvg-id=\"${CnrRadio.stations.first().id}\",Renamed CNR\n" +
            "https://radio.example/live/index.m3u8\n#EXTINF:-1,Custom\nhttp://radio.example/custom/index.m3u8\n"
        val document = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot), "text/plain", name)!!
        resolver.openOutputStream(document, "wt")!!.use { it.write(content.toByteArray()) }
        val original = runBlocking { app.radioCatalog.snapshot() }
        val originalOptions = app.radio.backup()
        try {
            runBlocking { app.radioCatalog.withReplacement(emptyList()) { Unit } }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var main: MainActivity
                lateinit var viewModel: MusicViewModel
                scenario.onActivity { main = it; viewModel = ViewModelProvider(it)[MusicViewModel::class.java] }
                var stableSince = SystemClock.uptimeMillis()
                await { onMainResult {
                    val active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<MainActivity>().singleOrNull()
                    if (active != null && active !== main) {
                        main = active
                        viewModel = ViewModelProvider(active)[MusicViewModel::class.java]
                        stableSince = SystemClock.uptimeMillis()
                    }
                    active?.hasWindowFocus() == true && SystemClock.uptimeMillis() - stableSince >= 500 &&
                        main.findViewById<View>(R.id.nav_radio)?.isShown == true
                } }
                onMain { main.findViewById<View>(R.id.nav_radio).performClick() }
                await { main.findViewById<View>(R.id.radioSettings)?.isShown == true }
                onMain { main.findViewById<View>(R.id.radioSettings).performClick() }
                await { main.findViewById<View>(R.id.radioImport)?.isEnabled == true }
                onMain { main.findViewById<View>(R.id.radioImport).performClick() }
                lateinit var picker: FileExplorerActivity
                lateinit var pickerModel: FileExplorerViewModel
                await {
                    val found = onMainResult { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<FileExplorerActivity>().firstOrNull() }
                    if (found == null) false else { picker = found; true }
                }
                onMain {
                    pickerModel = ViewModelProvider(picker)[FileExplorerViewModel::class.java]
                    assertEquals(ExplorerMode.RADIO_IMPORT, pickerModel.mode)
                    pickerModel.addGrant(cacheRoot, read)
                }
                await { !pickerModel.state.value.loading && pickerModel.state.value.items.any { it.name == name } }
                nativeRow(picker, name)
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                assertTrue(app.radioCatalog.state.value.saved.isEmpty())
                assertEquals(2, viewModel.preparedRadioImport.value!!.parsed.entries.size)
                assertEquals(RadioDefaults.bundled.size, viewModel.preparedRadioImport.value!!.currentCount)
                screenshot("radio131-import-preview")
                scenario.recreate()
                scenario.onActivity { main = it; viewModel = ViewModelProvider(it)[MusicViewModel::class.java] }
                await { viewModel.preparedRadioImport.value != null }
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                await { viewModel.preparedRadioImport.value == null }
                assertTrue(app.radioCatalog.state.value.saved.isEmpty())
                onMain { viewModel.previewRadioList(document) }
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                onView(withText(R.string.radio_import_replace)).inRoot(isDialog()).perform(click())
                await { app.radioCatalog.state.value.programs.size == 2 &&
                    !viewModel.radioImportBusy.value }
                assertFalse(app.radioCatalog.state.value.includeDefaults)
                assertEquals("Renamed CNR", app.radioCatalog.find(CnrRadio.stations.first().id)!!.name)
                assertEquals(content, resolver.openInputStream(document)!!.bufferedReader().use { it.readText() })
                val alternate = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot),
                    "text/plain", "Alternate.txt")!!
                val alternateText = "Custom,https://radio.example/backup/index.m3u8"
                resolver.openOutputStream(alternate, "wt")!!.use { it.write(alternateText.toByteArray()) }
                onMain { viewModel.previewRadioList(alternate) }
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                screenshot("radio133-import-modes")
                onView(withText(R.string.radio_import_append)).inRoot(isDialog()).perform(click())
                await { app.radioCatalog.state.value.programs.last().sources.size == 2 && !viewModel.radioImportBusy.value }
                assertEquals(listOf("http://radio.example/custom/index.m3u8", "https://radio.example/backup/index.m3u8"),
                    app.radioCatalog.state.value.programs.last().sources)
                onMain { viewModel.previewRadioList(alternate) }
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                onView(withText(R.string.radio_import_replace)).inRoot(isDialog()).perform(click())
                await { app.radioCatalog.state.value.programs.last().sources == listOf("https://radio.example/backup/index.m3u8") &&
                    !viewModel.radioImportBusy.value }
                assertEquals(alternateText, resolver.openInputStream(alternate)!!.bufferedReader().use { it.readText() })
                resolver.call(cacheRoot, "cacheFixture", null, Bundle().apply { putBoolean("unavailable", true) })
                assertEquals(1, app.radioCatalog.state.value.programs.size)
                onMain { main.findViewById<View>(R.id.radioReset).performClick() }
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
                await { app.radioCatalog.state.value.saved.size == 1 && app.radioCatalog.state.value.includeDefaults &&
                    !viewModel.radioImportBusy.value }
                assertEquals(RadioDefaults.bundled.size + 1, app.radioCatalog.state.value.programs.size)
                assertEquals(CnrRadio.stations.first().program, app.radioCatalog.state.value.programs.first())
                assertEquals("Custom", app.radioCatalog.state.value.programs.last().name)
                screenshot("radio131-reset-builtins")
            }
        } finally {
            resolver.call(cacheRoot, "cacheFixture", null, Bundle().apply { putBoolean("unavailable", false) })
            runBlocking { app.radioCatalog.withReplacement(original.programs, original.includeDefaults) { Unit } }
            assertTrue(app.radio.writeBackup(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(), originalOptions).commit())
            onMain { app.radio.publishRestored() }
        }
    }

    @Test fun radioM3uExportUsesTheSameSafExplorerAndCanBeEditedAndReimported() {
        val original = runBlocking { app.radioCatalog.snapshot() }
        val options = app.radio.backup()
        try {
            onMain { app.radio.setPanelVisible(true) }
            runBlocking { app.radioCatalog.withReplacement(emptyList()) { Unit } }
            val custom = runBlocking {
                app.radioCatalog.merge(RadioList.parse(("Export station,https://radio.example/one\n" +
                    "Export station,https://radio.example/two").byteInputStream()).entries, emptyMap()).programs.single()
            }
            onMain { assertTrue(app.radio.pin(custom)) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                mainTap(scenario, R.id.nav_radio)
                lateinit var viewModel: MusicViewModel
                scenario.onActivity {
                    viewModel = ViewModelProvider(it)[MusicViewModel::class.java]
                    it.findViewById<android.widget.EditText>(R.id.radioSearch).setText("Nothing matches this search")
                }
                mainTap(scenario, R.id.radioSettings)
                val before = app.radioCatalog.state.value
                mainTap(scenario, R.id.radioExport)
                var picker = explorerAtSaf(ExplorerMode.RADIO_EXPORT)
                onMain { picker.findViewById<View>(R.id.explorerCancel).performClick() }
                assertSame(before, app.radioCatalog.state.value)
                mainTap(scenario, R.id.radioExport)
                picker = explorerAtSaf(ExplorerMode.RADIO_EXPORT)
                onMain {
                    picker.findViewById<android.widget.EditText>(R.id.explorerFilename).setText("Editable stations")
                    assertTrue(picker.findViewById<View>(R.id.explorerSelect).isEnabled)
                    picker.findViewById<View>(R.id.explorerSelect).performClick()
                }
                await { !viewModel.radioImportBusy.value &&
                    app.notice.value?.text == app.text(R.string.radio_export_done, before.programs.size,
                        before.programs.sumOf { it.sources.size }) }
                val exported = runBlocking {
                    documents.children(cacheRoot.toString(), documents.rootId(cacheRoot.toString()), allFiles = true)
                        .single { it.name == "Editable stations.m3u" }
                }
                val exportedUri = Uri.parse(exported.uri)
                val text = resolver.openInputStream(exportedUri)!!.bufferedReader().use { it.readText() }
                val parsed = RadioList.parse(text.byteInputStream())
                assertEquals(0, parsed.invalid)
                assertEquals(before.programs.sumOf { it.sources.size }, parsed.entries.size)
                assertEquals(before.programs.map { it.id }.toSet(), parsed.entries.map { it.id }.toSet())
                assertSame("Export must not rewrite the catalog", before, app.radioCatalog.state.value)
                assertEquals(custom.sources, parsed.entries.filter { it.id == custom.id }.map { it.uri })
                val edited = "#EXTM3U\n#EXTINF:-1 tvg-id=\"${custom.id}\" group-title=\"Offline edits\",Renamed offline\n" +
                    "https://radio.example/two\n#EXTINF:-1 tvg-id=\"${custom.id}\" group-title=\"Offline edits\",Renamed offline\n" +
                    "https://radio.example/new\n"
                resolver.openOutputStream(exportedUri, "wt")!!.use { it.write(edited.toByteArray()) }
                mainTap(scenario, R.id.radioImport)
                picker = explorerAtSaf(ExplorerMode.RADIO_IMPORT)
                nativeRow(picker, exported.name)
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                onView(withText(R.string.radio_import_apply)).inRoot(isDialog()).perform(click())
                screenshot("radio135-replace-or-merge")
                onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
                assertSame(before, app.radioCatalog.state.value)
                onMain { viewModel.previewRadioList(exportedUri) }
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                onView(withText(R.string.radio_import_apply)).inRoot(isDialog()).perform(click())
                onView(withText(R.string.radio_import_replace)).inRoot(isDialog()).perform(click())
                await { app.radioCatalog.state.value.programs.size == 1 && !viewModel.radioImportBusy.value }
                val replaced = app.radioCatalog.state.value.programs.single()
                assertEquals(custom.id, replaced.id)
                assertEquals("Renamed offline", replaced.name)
                assertEquals("Offline edits", replaced.group)
                assertEquals(listOf("https://radio.example/two", "https://radio.example/new"), replaced.sources)
                assertFalse(app.radioCatalog.state.value.includeDefaults)
                assertEquals(listOf(custom.id), app.radio.options.value.pins)
                assertEquals(edited, resolver.openInputStream(exportedUri)!!.bufferedReader().use { it.readText() })
                scenario.recreate()
                scenario.onActivity { viewModel = ViewModelProvider(it)[MusicViewModel::class.java] }
                onMain { viewModel.previewRadioList(exportedUri) }
                await { viewModel.preparedRadioImport.value != null && !viewModel.radioImportBusy.value }
                val unchanged = app.radioCatalog.state.value
                onView(withText(R.string.radio_import_apply)).inRoot(isDialog()).perform(click())
                onView(withText(R.string.radio_import_append)).inRoot(isDialog()).perform(click())
                await { viewModel.preparedRadioImport.value == null && !viewModel.radioImportBusy.value }
                assertSame("Identical offline file must not change or duplicate the catalog", unchanged, app.radioCatalog.state.value)
                mainTap(scenario, R.id.nav_radio)
                scenario.onActivity { it.findViewById<android.widget.EditText>(R.id.radioSearch).setText("") }
                screenshot("radio135-reimported-catalog")
            }
        } finally {
            runBlocking { app.radioCatalog.withReplacement(original.programs, original.includeDefaults) { Unit } }
            assertTrue(app.radio.writeBackup(app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(), options).commit())
            onMain { app.radio.publishRestored() }
        }
    }

    @Test fun radioExportSystemPickerUsesCreateDocumentAndATemporaryWriteGrant() {
        val output = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot),
            "audio/x-mpegurl", "External.m3u")!!
        val filter = android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType("audio/x-mpegurl")
        }
        val monitor = android.app.Instrumentation.ActivityMonitor(filter,
            android.app.Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(output).addFlags(write)), true)
        val before = resolver.persistedUriPermissions.map { it.uri }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launchActivityForResult<FileExplorerActivity>(
                FileExplorerActivity.intent(app, ExplorerMode.RADIO_EXPORT, "External.m3u")).use { scenario ->
                await { !model(scenario).state.value.loading }
                tap(scenario, R.id.explorerAndroidPicker)
                assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                assertEquals(output, scenario.result.resultData!!.data)
                assertEquals(write, scenario.result.resultData!!.flags and (read or write or persist))
                assertEquals(1, monitor.hits)
                assertEquals(before, resolver.persistedUriPermissions.map { it.uri })
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun radioTxtCanBeSelectedFromLocalStorageWithExtensionFallback() {
        seedFiles()
        File(files, "Stations.txt").writeText("Local radio,https://radio.example/live/index.m3u8")
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.RADIO_IMPORT)).use { scenario ->
            goToFiles(scenario)
            val names = model(scenario).state.value.items.map { it.name }
            assertTrue("Stations.txt" in names)
            assertFalse("消防.wav" in names)
            tapRow(scenario, "Stations.txt")
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            val chosen = scenario.result.resultData!!.data!!
            val parsed = resolver.openInputStream(chosen)!!.use { RadioList.parse(it) }
            assertEquals("Local radio", parsed.entries.single().name)
            assertEquals("Local radio,https://radio.example/live/index.m3u8", File(files, "Stations.txt").readText())
        }
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
        for ((mime, name) in listOf("application/zip" to "New backup.zip", "audio/x-mpegurl" to "New radio.m3u")) {
            val created = DocumentsContract.createDocument(resolver, uri(), mime, name)!!
            resolver.openOutputStream(created, "wt")!!.use { it.write("Synthetic export".toByteArray()) }
            assertEquals("Synthetic export", File(files, name).readText())
            rejected { DocumentsContract.createDocument(resolver, uri(), mime, name) }
            assertEquals("Synthetic export", File(files, name).readText())
        }
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

    @Test fun unusedLocationRemovalIsDurableAcrossModesAndExplicitGrantRestoresIt() {
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
            removeLocation(scenario, music, confirm = false)
            assertTrue(hasLocation(scenario, music))
            assertTrue(resolver.persistedUriPermissions.any { it.uri == music })
            removeLocation(scenario, music)
            assertTrue(resolver.persistedUriPermissions.none { it.uri == music })
            assertTrue(app.repository.library.value.sources.isEmpty())
            assertTrue(app.repository.library.value.tracks.isEmpty())
            assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
            grantFixtures()
            resolver.takePersistableUriPermission(music, read)
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(cacheRoot, read or write or persist) }
            await { !model(scenario).state.value.loading }
            tap(scenario, R.id.explorerLocations)
            await { !model(scenario).state.value.loading }
            assertFalse("Granting another folder must not discard hidden locations", hasLocation(scenario, music))
            scenario.recreate()
            await { !model(scenario).state.value.loading }
            assertFalse(hasLocation(scenario, music))
        }
        for (mode in ExplorerMode.entries) {
            ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, mode)).use { scenario ->
                await { !model(scenario).state.value.loading }
                assertFalse("Removal must apply to $mode", hasLocation(scenario, music))
            }
        }
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(music, read or persist) }
            await { !model(scenario).state.value.loading }
            tap(scenario, R.id.explorerLocations)
            await { !model(scenario).state.value.loading }
            assertTrue(hasLocation(scenario, music))
            assertTrue(resolver.persistedUriPermissions.any { it.uri == music && it.isReadPermission })
            assertEquals(listOf("Album", "01 - Synthetic.wav"),
                runBlocking { documents.children(music.toString(), "music") }.map { it.name })
        }
    }

    @Test fun removingSharedLocationKeepsSongsFavoritesPlaylistsAndAncestorAccess() {
        resolver.takePersistableUriPermission(music, read)
        val child = DocumentsContract.buildDocumentUriUsingTree(music, "music/album")
        val before = runBlocking {
            val source = app.repository.addSource(child, 0)
            val scanned = app.repository.scan(source, null) {}.tracks.single()
            app.repository.openDocument(DocumentsContract.buildDocumentUriUsingTree(music, "music/one"), 0)
            app.repository.update {
                LibraryEdits.addToPlaylist(LibraryEdits.createPlaylist(LibraryEdits.toggleFavorite(it, scanned.id),
                    "location-playlist", "Keep music"), "location-playlist", listOf(scanned.id))
            }
        }
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
            removeLocation(scenario, music)
            assertEquals(before, app.repository.library.value)
            assertTrue(resolver.persistedUriPermissions.any { it.uri == music && it.isReadPermission })
            scenario.recreate()
            await { !model(scenario).state.value.loading }
            assertFalse(hasLocation(scenario, music))
            assertEquals(listOf("02 - Nested.WAV"),
                runBlocking { documents.children(child.toString(), "music/album") }.map { it.name })
            screenshot("explorer-shared-location-removed")
        }
    }

    @Test fun removingWritableCacheLocationKeepsItsLyricsAndWriteAccess() {
        resolver.takePersistableUriPermission(cacheRoot, read or write)
        val value = CachedLyric("[00:00]Keep cached lyrics\n[00:55]Ending", LyricOrigin.QQ, System.currentTimeMillis())
        val key = "c".repeat(64)
        InternalLyricCache(File(app.cacheDir, "lyrics"), System::currentTimeMillis).write(key, value)
        runBlocking { app.lyrics.cache.moveInternalTo(cacheRoot, 0) }
        val folder = app.lyrics.cache.info.value.folder
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.LYRIC_CACHE)).use { scenario ->
            removeLocation(scenario, cacheRoot)
            assertEquals(folder, app.lyrics.cache.info.value.folder)
            assertEquals(value, runBlocking { app.lyrics.cache.read(key) }.value)
            assertTrue(resolver.persistedUriPermissions.any {
                it.uri == cacheRoot && it.isReadPermission && it.isWritePermission
            })
        }
    }

    @Test fun removingLocationKeepsAccessForImagesAlreadySelectedInTheExplorer() {
        resolver.takePersistableUriPermission(cacheRoot, read or write)
        val picture = DocumentsContract.createDocument(resolver, DocumentUris.document(cacheRoot), "image/png", "Keep.png")!!
        val original = DocumentsContract.buildDocumentUri("com.macarmusic.app.test.documents", "pictures/background")
        val bytes = resolver.openInputStream(original)!!.use { it.readBytes() }
        resolver.openOutputStream(picture, "wt")!!.use { it.write(bytes) }
        resolver.releasePersistableUriPermission(cacheRoot, write)
        ActivityScenario.launchActivityForResult<FileExplorerActivity>(
            FileExplorerActivity.intent(app, ExplorerMode.IMAGES)).use { scenario ->
            await { !model(scenario).state.value.loading }
            tapRow(scenario, model(scenario).state.value.items.single { it.location?.uri == cacheRoot.toString() }.name)
            tapRow(scenario, "Keep.png")
            tap(scenario, R.id.explorerLocations)
            removeLocation(scenario, cacheRoot)
            assertEquals(setOf(picture.toString()), model(scenario).state.value.selected)
            assertTrue(resolver.persistedUriPermissions.any { it.uri == cacheRoot && it.isReadPermission })
            tap(scenario, R.id.explorerSelect)
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(picture, scenario.result.resultData!!.data)
            assertArrayEquals(bytes, resolver.openInputStream(picture)!!.use { it.readBytes() })
        }
    }

    @Test fun temporaryLocationRemovalSurvivesRecreationAndCanBeGrantedAgain() {
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(music, read) }
            await { !model(scenario).state.value.loading }
            tap(scenario, R.id.explorerLocations)
            removeLocation(scenario, music)
            scenario.recreate()
            await { !model(scenario).state.value.loading }
            assertFalse(hasLocation(scenario, music))
            assertTrue(resolver.persistedUriPermissions.none { it.uri == music })
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(music, read) }
            await { !model(scenario).state.value.loading }
            tap(scenario, R.id.explorerLocations)
            await { !model(scenario).state.value.loading }
            assertTrue(hasLocation(scenario, music))
        }
    }

    @Test fun unpluggedLocationCanBeRemovedWithoutFolderQueriesOrAudioReads() {
        resolver.takePersistableUriPermission(music, read)
        resolver.call(music, "setUnavailable", null, Bundle().apply { putBoolean("value", true) })
        try {
            ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
                removeLocation(scenario, music)
                assertTrue(resolver.persistedUriPermissions.none { it.uri == music })
                val counters = resolver.call(music, "scanFixtureState", null, null)!!
                assertEquals(0, counters.getInt("audioReads"))
                assertEquals(0, counters.getInt("childReads"))
                assertNull(model(scenario).state.value.error)
            }
        } finally { resolver.call(music, "setUnavailable", null, Bundle().apply { putBoolean("value", false) }) }
    }

    @Test fun locationRemovalIsRemoteAccessibleLocalizedAndDoesNotAddButtonsToFileRows() {
        resolver.takePersistableUriPermission(cacheRoot, read)
        for (language in listOf("en", "zh-CN")) {
            onMain {
                setLanguage(language)
                app.displaySettings.update { it.copy(controlBlocks = language == "en") }
            }
            ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
                scenario.onActivity {
                    assertEquals(language.substringBefore('-'),
                        androidx.core.os.ConfigurationCompat.getLocales(it.resources.configuration)[0]?.language)
                }
                scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(music, read or persist) }
                await { !model(scenario).state.value.loading }
                tap(scenario, R.id.explorerLocations)
                for (location in listOf(music, cacheRoot)) {
                    val row = locationRow(scenario, location)
                    remoteKey(KeyEvent.KEYCODE_DPAD_UP)
                    scenario.onActivity { assertTrue(row.requestFocus()) }
                    remoteKey(KeyEvent.KEYCODE_TAB)
                    scenario.onActivity {
                        assertSame(row.findViewById<View>(R.id.explorerRemoveLocation), it.currentFocus)
                        val button = it.currentFocus!!
                        assertTrue(button.height >= 48 * it.resources.displayMetrics.density)
                        assertEquals(if (language == "en") "Remove" else "移除", (button as TextView).text)
                    }
                    remoteKey(KeyEvent.KEYCODE_DPAD_LEFT)
                    scenario.onActivity { assertTrue(row.isFocused) }
                }
                remoteKey(KeyEvent.KEYCODE_MENU)
                screenshot("explorer-remove-location-$language")
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                assertTrue(hasLocation(scenario, music))
                assertTrue(hasLocation(scenario, cacheRoot))
                tapRow(scenario, model(scenario).state.value.items.single { it.location?.uri == music.toString() }.name)
                scenario.onActivity {
                    val list = it.findViewById<RecyclerView>(R.id.explorerList)
                    for (index in 0 until list.childCount)
                        assertNull(list.getChildAt(index).findViewById<View>(R.id.explorerRemoveLocation))
                }
            }
        }
    }

    @Test fun permissionReleaseChecksLatestOwnersInsideTheGrantLock() = runBlocking(Dispatchers.IO) {
        resolver.takePersistableUriPermission(music, read)
        val retained = java.util.concurrent.atomic.AtomicReference(emptySet<String>())
        val checked = java.util.concurrent.atomic.AtomicBoolean()
        documents.grantMutex.lock()
        val releasing = async {
            documents.releaseReadPermissions(setOf(music.toString())) {
                checked.set(true)
                retained.get()
            }
        }
        try {
            delay(50)
            assertFalse(checked.get())
            retained.set(setOf(DocumentsContract.buildDocumentUriUsingTree(music, "music/album").toString()))
        } finally { documents.grantMutex.unlock() }
        assertTrue(releasing.await().isEmpty())
        assertTrue(checked.get())
        assertTrue(resolver.persistedUriPermissions.any { it.uri == music && it.isReadPermission })
    }

    @Test fun folderActionsShareOneBottomRowAndRemainReachableInNarrowWindows() {
        val ids = listOf(R.id.explorerAndroidPicker, R.id.explorerCancel, R.id.explorerSelect,
            R.id.explorerLocations, R.id.explorerUp, R.id.explorerRefresh)
        for (language in listOf("en", "zh-CN")) {
            onMain {
                setLanguage(language)
                app.displaySettings.update { it.copy(controlBlocks = true, blockOutline = BlockOutline()) }
            }
            ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FOLDER)).use { scenario ->
                scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java].addGrant(music, read or persist) }
                await { !model(scenario).state.value.loading && model(scenario).state.value.canSelectFolder }
                lateinit var footer: HorizontalScrollView
                lateinit var controls: List<View>
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(R.id.explorerRoot)
                    footer = activity.findViewById(R.id.explorerActions)
                    controls = ids.map { activity.findViewById(it) }
                    val row = footer.getChildAt(0) as ViewGroup
                    assertSame(footer, root.getChildAt(root.childCount - 1))
                    assertTrue(activity.findViewById<View>(R.id.explorerList).bottom <= footer.top)
                    assertEquals(root.height - root.paddingBottom, footer.bottom)
                    assertTrue(row.width >= footer.width)
                    assertEquals(0, controls.first().left)
                    assertEquals(row.width, controls.last().right)
                    controls.forEach {
                        assertSame(row, it.parent)
                        assertTrue(it.isEnabled)
                        assertEquals(controls.first().top, it.top)
                        assertEquals(controls.first().bottom, it.bottom)
                        assertTrue("Footer control ${activity.resources.getResourceEntryName(it.id)} is ${it.height}px high",
                            it.height >= 48 * activity.resources.displayMetrics.density)
                        if (footer.width >= 660 * activity.resources.displayMetrics.density &&
                            activity.resources.configuration.fontScale <= 1f) {
                            val visible = Rect()
                            assertTrue("All actions must fit in a normal wide window", it.getLocalVisibleRect(visible))
                            assertEquals(it.width, visible.width())
                            assertEquals(it.height, visible.height())
                        }
                    }
                    controls.zipWithNext().forEach { (left, right) -> assertTrue(left.right < right.left) }
                }
                screenshot("explorer-bottom-actions-$language")
                remoteKey(KeyEvent.KEYCODE_DPAD_UP)
                onMain { assertTrue(controls.first().requestFocus()) }
                remoteKey(KeyEvent.KEYCODE_DPAD_UP)
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.explorerList).hasFocus()) }
                remoteKey(KeyEvent.KEYCODE_DPAD_DOWN)
                onMain { assertTrue(footer.hasFocus()) }
                scenario.onActivity { activity ->
                    val root = activity.findViewById<View>(R.id.explorerRoot)
                    root.layoutParams = root.layoutParams.apply {
                        width = (320 * activity.resources.displayMetrics.density + 0.5f).toInt()
                    }
                }
                await { onMainResult { !footer.isLayoutRequested && footer.getChildAt(0).width > footer.width } }
                remoteKey(KeyEvent.KEYCODE_DPAD_UP)
                onMain { assertTrue(controls.first().requestFocus()) }
                for (id in ids) {
                    if (id != ids.first()) remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT)
                    var observed = ""
                    await({ "Footer action ${app.resources.getResourceEntryName(id)} was not fully focused: $observed" }) { onMainResult {
                        val view = controls.single { it.id == id }
                        val visible = Rect()
                        val shown = view.getLocalVisibleRect(visible)
                        observed = "focus=${footer.findFocus()?.id}, bounds=${view.left}..${view.right}, " +
                            "visible=$visible, viewport=${footer.width}, scroll=${footer.scrollX}, " +
                            "layoutPending=${footer.isLayoutRequested}"
                        view.isFocused && shown && visible.width() == view.width
                    } }
                }
                assertTrue(onMainResult { footer.scrollX > 0 })
                for (id in ids.dropLast(1).reversed()) {
                    remoteKey(KeyEvent.KEYCODE_DPAD_LEFT)
                    await { onMainResult { controls.single { it.id == id }.isFocused } }
                }
                assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
                assertTrue(app.repository.library.value.tracks.isEmpty())
            }
        }
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

    @Test fun backupAndRadioExportsCreateNewFilesAndRefuseExistingNames() {
        seedFiles()
        for (mode in listOf(ExplorerMode.BACKUP_EXPORT, ExplorerMode.RADIO_EXPORT)) {
            val existing = mode.exportName("Keep")
            File(files, existing).writeText("Existing file must stay")
            ActivityScenario.launchActivityForResult<FileExplorerActivity>(
                FileExplorerActivity.intent(app, mode, existing)).use { scenario ->
                goToFiles(scenario)
                tap(scenario, R.id.explorerSelect)
                await { model(scenario).state.value.error != null && !model(scenario).state.value.saving }
                assertEquals("Existing file must stay", File(files, existing).readText())
                onView(withId(R.id.explorerFilename)).perform(click(), replaceText("New export"), pressImeActionButton())
                scenario.recreate()
                await { !model(scenario).state.value.loading }
                assertEquals("New export", model(scenario).filename)
                await {
                    var ready = false
                    scenario.onActivity {
                        ready = !it.findViewById<View>(R.id.explorerFilename).hasFocus() &&
                            it.findViewById<View>(R.id.explorerList).height >= 144 * it.resources.displayMetrics.density
                    }
                    ready
                }
                val viewModel = model(scenario)
                tap(scenario, R.id.explorerSelect)
                await { !viewModel.state.value.saving &&
                    (viewModel.state.value.result != null || viewModel.state.value.error != null) }
                assertNull(viewModel.state.value.error)
                assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                val file = File(files, mode.exportName("New export"))
                assertTrue(file.isFile)
                assertEquals(uri(file), scenario.result.resultData!!.data)
            }
        }
    }

    @Test fun readOnlySafFolderCannotBecomeAWritableDestination() {
        val readonly = DocumentsContract.buildTreeDocumentUri("com.macarmusic.app.test.cache", "readonly")
        resolver.takePersistableUriPermission(readonly, read or write)
        for (mode in listOf(ExplorerMode.LYRIC_CACHE, ExplorerMode.RADIO_EXPORT)) {
            ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, mode, "Radio.m3u")).use { scenario ->
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
            focusFirstRow(scenario)
            for (position in 0..60) {
                if (position > 0) remoteKey(KeyEvent.KEYCODE_DPAD_DOWN)
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
            val focusTrace = { focusState(scenario) }
            val rootPosition = model(scenario).state.value.items.indexOfFirst { it.location?.uri == music.toString() }
            assertTrue(rootPosition >= 0)
            focusFirstRow(scenario)
            repeat(rootPosition) { remoteKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            assertEquals(rootPosition, remotePosition(scenario))
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !model(scenario).state.value.loading &&
                model(scenario).state.value.items.any { it.name == "Album" } }
            await(message = focusTrace) { remotePosition(scenario) >= 0 }
            assertEquals("New folder must keep remote focus in its list", 0, remotePosition(scenario))
            assertEquals("Album", model(scenario).state.value.items[0].name)
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !model(scenario).state.value.loading &&
                model(scenario).state.value.items.any { it.name == "02 - Nested.WAV" } }
            await(message = focusTrace) { remotePosition(scenario) >= 0 }
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

    @Test fun returningToLocationsRestoresTheOpenedFolderInsteadOfTheFirstRow() {
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            await { !model(scenario).state.value.loading }
            val focusTrace = { focusState(scenario) }
            val position = model(scenario).state.value.items.indexOfFirst { it.location?.uri == music.toString() }
            assertTrue(position > 0)
            focusFirstRow(scenario)
            repeat(position) { remoteKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            assertEquals(position, remotePosition(scenario))
            remoteKey(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !model(scenario).state.value.loading && model(scenario).state.value.items.any { it.name == "Album" } }
            await(message = focusTrace) { remotePosition(scenario) >= 0 }
            remoteKey(KeyEvent.KEYCODE_BACK)
            await { !model(scenario).state.value.loading && model(scenario).state.value.path.isEmpty() }
            await(message = focusTrace) { remotePosition(scenario) == position }
            scenario.recreate()
            await { !model(scenario).state.value.loading && remotePosition(scenario) == position }
            assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
            screenshot("focus1490-explorer-parent")
        }
    }

    @Test fun leftRightOpensFoldersAndReturnsToTheRememberedParentWithoutChoosingFiles() {
        resolver.takePersistableUriPermission(music, read)
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            await { !model(scenario).state.value.loading }
            val position = model(scenario).state.value.items.indexOfFirst { it.location?.uri == music.toString() }
            assertTrue(position >= 0)
            focusFirstRow(scenario)
            repeat(position) { remoteKey(KeyEvent.KEYCODE_DPAD_DOWN) }
            remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { !model(scenario).state.value.loading && model(scenario).state.value.path.size == 1 }
            await { remotePosition(scenario) == 0 }
            assertEquals("Album", model(scenario).state.value.items[0].name)
            remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            await { !model(scenario).state.value.loading && model(scenario).state.value.path.size == 2 }
            await { remotePosition(scenario) == 0 }
            assertEquals("02 - Nested.WAV", model(scenario).state.value.items.single().name)
            val before = model(scenario).state.value
            remoteKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(before.path, model(scenario).state.value.path)
            assertNull("Right must not choose or play a file", model(scenario).state.value.result)
            focusFirstRow(scenario)
            remoteKey(KeyEvent.KEYCODE_DPAD_LEFT)
            await { !model(scenario).state.value.loading && model(scenario).state.value.path.size == 1 }
            await { remotePosition(scenario) == 0 }
            remoteKey(KeyEvent.KEYCODE_DPAD_LEFT)
            await { !model(scenario).state.value.loading && model(scenario).state.value.path.isEmpty() }
            await(message = { focusState(scenario) }) { remotePosition(scenario) == position }
            assertEquals(0, resolver.call(music, "scanFixtureState", null, null)!!.getInt("audioReads"))
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.playerDialogLogo).isShown)
                app.inform("Message in the file chooser")
            }
            await { onMainResult {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<FileExplorerActivity>().single()
                activity.findViewById<View>(R.id.noticeBar).isShown
            } }
            screenshot("car154-folder-keys")
            onMain { app.notice.value?.let { app.dismissNotice(it.id) } }
        }
    }

    @Test fun slowFolderCompletionDoesNotTakeFocusBackFromTheToolbarOrTouchInput() {
        ActivityScenario.launch<FileExplorerActivity>(FileExplorerActivity.intent(app, ExplorerMode.MUSIC_FILE)).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[FileExplorerViewModel::class.java]
                .navigate(ExplorerLocation(ExplorerPlace.FOLDER, "Synthetic folder", music.toString())) }
            await { !model(scenario).state.value.loading }
            focusFirstRow(scenario)
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
            onView(withText("Album")).perform(click())
            await { !model(scenario).state.value.loading &&
                model(scenario).state.value.items.singleOrNull()?.name == "02 - Nested.WAV" }
            instrumentation.waitForIdleSync()
            assertTrue("The real tap must put the explorer in touch mode",
                onMainResult { activity.findViewById<View>(R.id.explorerList).isInTouchMode })
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
                    setLanguage(language)
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

    private fun explorerAtSaf(mode: ExplorerMode): FileExplorerActivity {
        var picker: FileExplorerActivity? = null
        await { onMainResult {
            picker = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<FileExplorerActivity>().singleOrNull()
            picker?.hasWindowFocus() == true
        } }
        val activity = checkNotNull(picker)
        val model = onMainResult { ViewModelProvider(activity)[FileExplorerViewModel::class.java] }
        assertEquals(mode, model.mode)
        onMain { model.addGrant(cacheRoot, read or persist or if (mode.writable) write else 0) }
        await { !model.state.value.loading && model.state.value.path.lastOrNull()?.uri == cacheRoot.toString() }
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

    private fun hasLocation(scenario: ActivityScenario<FileExplorerActivity>, uri: Uri) =
        model(scenario).state.value.items.any { it.location?.uri == uri.toString() }

    private fun locationRow(scenario: ActivityScenario<FileExplorerActivity>, uri: Uri): View {
        await { !model(scenario).state.value.loading && !model(scenario).state.value.saving }
        val position = model(scenario).state.value.items.indexOfFirst { it.location?.uri == uri.toString() }
        assertTrue("Missing granted location $uri", position >= 0)
        lateinit var list: RecyclerView
        scenario.onActivity { list = it.findViewById(R.id.explorerList) }
        val count = model(scenario).state.value.items.size
        await { onMainResult { !list.hasPendingAdapterUpdates() && list.adapter!!.itemCount == count } }
        onMain { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
        await { onMainResult { list.findViewHolderForAdapterPosition(position) != null } }
        return onMainResult { list.findViewHolderForAdapterPosition(position)!!.itemView }
    }

    private fun removeLocation(scenario: ActivityScenario<FileExplorerActivity>, uri: Uri, confirm: Boolean = true) {
        val row = locationRow(scenario, uri)
        scenario.onActivity { assertTrue(row.findViewById<View>(R.id.explorerRemoveLocation).performClick()) }
        onView(withId(if (confirm) android.R.id.button1 else android.R.id.button2)).inRoot(isDialog()).perform(click())
        if (confirm) await { !model(scenario).state.value.loading && !model(scenario).state.value.saving &&
            !hasLocation(scenario, uri) }
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

    private fun focusState(scenario: ActivityScenario<FileExplorerActivity>): String {
        var state = ""
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.explorerList)
            val model = ViewModelProvider(activity)[FileExplorerViewModel::class.java]
            val focus = activity.currentFocus
            val row = focus?.let(list::findContainingItemView)
            state = "focus=${focus?.id}/${row?.getTag(R.id.playerFocusKey)}, " +
                "list=${list.isFocused}/${list.hasFocus()}/${list.descendantFocusability}, " +
                "touch=${list.isInTouchMode}, loading=${model.state.value.loading}, " +
                "rows=${list.adapter?.itemCount}/${list.childCount}, path=${model.state.value.path}, " +
                "memory=${model.focusPositions}"
        }
        return state
    }

    private fun focusFirstRow(scenario: ActivityScenario<FileExplorerActivity>) {
        remoteKey(KeyEvent.KEYCODE_DPAD_UP)
        await {
            var focused = false
            scenario.onActivity {
                focused = it.findViewById<RecyclerView>(R.id.explorerList)
                    .findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() == true
            }
            focused
        }
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
    private fun await(
        message: () -> String = { "Timed out waiting for explorer state" },
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (!predicate()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail(message())
            SystemClock.sleep(40)
        }
    }
}
