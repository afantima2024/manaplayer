package com.macarmusic.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.BackupEdits
import com.macarmusic.app.model.BackupMerge
import com.macarmusic.app.model.CollectionPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

data class PreparedBackup(val manifest: BackupManifest, val directory: File)
data class BackupRestoreResult(val language: String, val unresolved: Int, val positionError: String? = null)

class LocalBackupRepository(private val app: MaCarMusicApp) {
    private val context get() = app.localizedContext
    private val preferences get() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    @Synchronized private fun origin(): String {
        preferences.getString("backupOrigin", null)?.let { return it }
        val value = UUID.randomUUID().toString()
        if (!preferences.edit().putString("backupOrigin", value).commit()) throw IOException(context.getString(R.string.backup_save_failed))
        return value
    }

    private fun settings(language: String) = BackupSettings(
        app.displaySettings.options.value, app.playbackSettings.options.value, app.sortOrder,
        preferences.getInt("theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES),
        language, ColorSlotStore(context).read(),
    )

    suspend fun sharedExport(language: String): Uri = withContext(Dispatchers.IO) {
        val directory = File(app.cacheDir, "backup-exports")
        if (!directory.isDirectory && !directory.mkdir()) throw IOException(context.getString(R.string.backup_save_failed))
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("ManaMusicPlayer-") &&
            it.name.endsWith(".zip") && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach {
            if (!it.delete()) Log.w("LocalBackup", "Could not remove expired shared backup")
        }
        val file = File(directory, "ManaMusicPlayer-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip")
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.backups", file)
        var complete = false
        try {
            export(uri, language)
            complete = true
            uri
        } finally {
            if (!complete && file.exists() && !file.delete()) Log.w("LocalBackup", "Could not remove incomplete shared backup")
        }
    }

    suspend fun export(uri: Uri, language: String) = withContext(Dispatchers.IO) {
        app.repository.documents.requireContentUri(uri)
        app.repository.awaitReady()
        if (!app.repository.isReadable) throw IOException(context.getString(R.string.library_read_only))
        val options = settings(language)
        val library = app.repository.library.value
        val position = CollectionPositions.capture(app.playback.read(), System.currentTimeMillis())
        val positions = CollectionPositions.reconcile(library, app.collections.read() + listOfNotNull(position))
        val coroutine = currentCoroutineContext()
        app.backgrounds.withImageFiles(options.display) { files ->
            val images = files.mapKeys { (name, _) ->
                if (name == BackgroundImages.LEGACY) "${UUID.randomUUID()}.png" else name
            }
            val backup = BackupManifest(origin = origin(), createdAtMs = System.currentTimeMillis(),
                settings = options.copy(display = options.display.copy(imageIds = images.keys.toList())),
                library = library, positions = positions)
            BackupArchive.validate(backup)
            coroutine.ensureActive()
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException(context.getString(R.string.document_unavailable))
            output.use { BackupArchive.write(it, backup, images) { coroutine.ensureActive() } }
        }
    }

    suspend fun prepare(uri: Uri): PreparedBackup = withContext(Dispatchers.IO) {
        app.repository.documents.requireContentUri(uri)
        val directory = File(app.cacheDir, "music-backup-${UUID.randomUUID()}")
        if (!directory.mkdir()) throw IOException(context.getString(R.string.backup_save_failed))
        var complete = false
        try {
            val coroutine = currentCoroutineContext()
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException(context.getString(R.string.document_unavailable))
            val manifest = input.use { BackupArchive.read(it, directory) { coroutine.ensureActive() } }
            manifest.settings.display.imageIds.forEach {
                coroutine.ensureActive()
                app.backgrounds.validateBackupImage(File(directory, it))
            }
            complete = true
            PreparedBackup(manifest, directory)
        } finally { if (!complete) discardDirectory(directory) }
    }

    suspend fun discard(backup: PreparedBackup) = withContext(Dispatchers.IO) { discardDirectory(backup.directory) }

    suspend fun restore(backup: PreparedBackup): BackupRestoreResult = withContext(Dispatchers.IO) {
        BackupArchive.validate(backup.manifest)
        app.repository.awaitReady()
        if (!app.repository.isReadable) throw IOException(context.getString(R.string.library_read_only))
        app.collections.read()
        var committed = false
        var preserveImages = false
        try {
            app.backgrounds.restoreImages(
                backup.manifest.settings.display.imageIds.map { File(backup.directory, it) },
                preserveOnError = { committed || preserveImages },
            ) { images ->
                val original = settings("")
                val replacement = backup.manifest.settings.copy(display = backup.manifest.settings.display.copy(
                    imageIds = images, imageVersion = if (images.isEmpty()) 0 else maxOf(System.currentTimeMillis(), original.display.imageVersion + 1)).normalized())
                var merged: BackupMerge? = null
                var preferencesChanged = false
                try {
                    val currentOrigin = origin()
                    app.repository.update { current ->
                        val result = BackupEdits.merge(current, backup.manifest, currentOrigin)
                        preferencesChanged = true
                        if (!edit(replacement).commit()) throw IOException(context.getString(R.string.backup_settings_failed))
                        merged = result
                        result.library
                    }
                    committed = true
                } catch (error: IOException) {
                    if (preferencesChanged && !edit(original).commit()) {
                        preserveImages = true
                        throw IOException(context.getString(R.string.backup_rollback_failed), error)
                    }
                    throw error
                } catch (error: IllegalArgumentException) {
                    if (preferencesChanged && !edit(original).commit()) {
                        preserveImages = true
                        throw IOException(context.getString(R.string.backup_rollback_failed), error)
                    }
                    throw error
                }
                var positionError: String? = null
                try { app.collections.merge(checkNotNull(merged).positions) }
                catch (error: IOException) {
                    Log.e("LocalBackup", "Settings and library restored, but position restore failed", error)
                    positionError = context.getString(R.string.backup_positions_partial, error.localizedMessage.orEmpty())
                } catch (error: IllegalArgumentException) {
                    Log.e("LocalBackup", "Settings and library restored, but position restore failed", error)
                    positionError = context.getString(R.string.backup_positions_partial, error.localizedMessage.orEmpty())
                }
                withContext(Dispatchers.Main.immediate) {
                    app.playbackSettings.publishRestored(replacement.playback)
                    app.displaySettings.publishRestored(replacement.display)
                }
                BackupRestoreResult(replacement.language,
                    checkNotNull(merged).library.tracks.count { BackupEdits.unavailable(it.uri) }, positionError)
            }
        } finally {
            withContext(NonCancellable) {
                discard(backup)
            }
        }
    }

    private fun edit(settings: BackupSettings): SharedPreferences.Editor = app.displaySettings.write(settings.display)
        .putString("sort", settings.sort.name)
        .putInt("theme", settings.nightMode)
        .putString("loopMode", settings.playback.loop.name)
        .putString("playOrder", settings.playback.order.name)
        .putString("customColorSlots", Json.encodeToString(settings.colors))

    private fun discardDirectory(directory: File) {
        require(directory.parentFile?.canonicalFile == app.cacheDir.canonicalFile &&
            directory.name.matches(Regex("music-backup-[a-f0-9-]{36}")))
        directory.listFiles()?.forEach { file ->
            check(file.isFile)
            if (!file.delete()) Log.w("LocalBackup", "Could not remove staged image ${file.name}")
        }
        if (directory.exists() && !directory.delete()) Log.w("LocalBackup", "Could not remove backup staging directory")
    }
}
