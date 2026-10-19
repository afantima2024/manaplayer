package com.macarmusic.app.lyrics

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.macarmusic.app.model.LyricCacheExpiry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class LyricCacheFolder(val treeUri: String, val documentUri: String, val name: String)

internal class SafLyricCache(
    private val resolver: ContentResolver,
    val folder: LyricCacheFolder,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val expiry: () -> LyricCacheExpiry = { LyricCacheExpiry.NEVER },
) : LyricCacheStore {
    private val tree = Uri.parse(folder.treeUri)
    private val root = Uri.parse(folder.documentUri)
    private var checked = false
    private val buckets = mutableMapOf<String, Document>()
    private data class Document(val uri: Uri, val name: String, val directory: Boolean, val bytes: Long, val modifiedMs: Long)

    init {
        if (tree.scheme != "content" || tree.authority.isNullOrBlank() || root.scheme != "content" ||
            root.authority != tree.authority || !isTreeUri(tree) || !isTreeUri(root) ||
            DocumentsContract.getTreeDocumentId(tree) != DocumentsContract.getTreeDocumentId(root)) {
            throw IOException("Invalid lyric cache folder")
        }
    }

    override fun invalidate() { checked = false; buckets.clear() }

    private fun check() {
        if (checked) return
        val children = children(root)
        val marker = children.singleOrNull { it.name == MARKER && !it.directory }
            ?: throw IOException("This folder is not a ManaMusicPlayer lyric cache")
        val value = try {
            Json.decodeFromString<Marker>(bytes(marker.uri, 1024).toString(Charsets.UTF_8))
        } catch (error: kotlinx.serialization.SerializationException) {
            throw IOException("Invalid lyric cache folder marker", error)
        }
        if (value != Marker(FORMAT, 1)) throw IOException("Unsupported lyric cache folder format")
        buckets.clear()
        children.filter { it.directory && BUCKET.matches(it.name) }.forEach { entry ->
            if (buckets.put(entry.name, entry) != null) throw IOException("Duplicate lyric cache subfolder")
        }
        checked = true
    }

    private fun bucket(key: String, createMissing: Boolean): Document? {
        LyricCacheFiles.requireKey(key)
        check()
        val name = key.take(2)
        buckets[name]?.let { return it }
        val existing = children(root).filter { it.name == name }
        if (existing.size > 1 || existing.any { !it.directory }) throw IOException("Invalid lyric cache subfolder")
        val result = existing.singleOrNull() ?: if (createMissing) create(root, name, true) else return null
        buckets[name] = result
        return result
    }

    private fun versions(key: String, create: Boolean = false): List<Pair<Document, Long>> {
        val bucket = bucket(key, create) ?: return emptyList()
        return children(bucket.uri).mapNotNull { entry ->
            val parsed = LyricCacheFiles.portableKey(entry.name)
            if (!entry.directory && parsed?.first == key) entry to parsed.second else null
        }.sortedByDescending { it.second }
    }

    override fun read(key: String): CachedLyric? = readVersions(key, versions(key))

    private fun readVersions(key: String, versions: List<Pair<Document, Long>>): CachedLyric? {
        var failure: IOException? = null
        for ((entry, _) in versions) {
            val value = try { LyricCacheFiles.decode(bytes(entry.uri)) }
            catch (error: IOException) {
                Log.w(TAG, "Unreadable cached lyric revision; checking the previous revision", error)
                failure = error
                continue
            }
            val dated = if (value.savedAtMs == 0L) value.copy(savedAtMs = entry.modifiedMs) else value
            val remaining = LyricVersions.expire(dated, expiry(), nowMs())
            if (remaining == null) {
                // A prior revision must not resurrect an expired cache entry.
                versions.forEach { delete(it.first) }
                return null
            }
            if (remaining !== dated) write(key, remaining)
            return remaining
        }
        if (failure != null) throw failure
        return null
    }

    override fun write(key: String, value: CachedLyric) {
        val data = LyricCacheFiles.encode(value)
        val bucket = checkNotNull(bucket(key, true))
        val old = versions(key)
        val revision = (old.firstOrNull()?.second ?: 0)
        if (revision == Long.MAX_VALUE) throw IOException("Lyric cache revision limit reached")
        // A new verified revision is readable before old revisions are removed; rename support is unnecessary.
        val added = create(bucket.uri, "$key-${revision + 1}.json", false)
        var verified = false
        try {
            resolver.openOutputStream(added.uri, "wt")?.use { it.write(data) }
                ?: throw IOException("Cannot write lyric cache")
            if (!bytes(added.uri).contentEquals(data)) throw IOException("Lyric cache verification failed")
            verified = true
        } finally {
            if (!verified) {
                try { delete(added) }
                catch (error: IOException) { Log.w(TAG, "Could not remove an incomplete cache revision", error) }
                catch (error: SecurityException) { Log.w(TAG, "Cache permission lost during cleanup", error) }
            }
        }
        old.forEach { delete(it.first) }
    }

    override fun size(): LyricCacheSize {
        var count = 0L
        var bytes = 0L
        visitRecords { entries ->
            count += entries.mapNotNull { LyricCacheFiles.portableKey(it.name)?.first }.distinct().size
            bytes += entries.sumOf { it.bytes }
        }
        return LyricCacheSize(count, bytes)
    }

    override fun pruneExpired(checkActive: () -> Unit) {
        if (expiry() == LyricCacheExpiry.NEVER) return
        visitRecords { entries ->
            val groups = entries.groupBy { checkNotNull(LyricCacheFiles.portableKey(it.name)).first }
            for ((key, records) in groups) {
                checkActive()
                readVersions(key, records.map { it to checkNotNull(LyricCacheFiles.portableKey(it.name)).second }
                    .sortedByDescending { it.second })
            }
        }
    }

    override fun clear(): LyricCacheSize {
        var count = 0L
        var bytes = 0L
        visitRecords { entries ->
            entries.forEach(::delete)
            count += entries.mapNotNull { LyricCacheFiles.portableKey(it.name)?.first }.distinct().size
            bytes += entries.sumOf { it.bytes }
        }
        return LyricCacheSize(count, bytes)
    }

    private fun visitRecords(action: (List<Document>) -> Unit) {
        invalidate()
        check()
        for ((prefix, bucket) in buckets.toMap()) {
            action(children(bucket.uri).filter {
                !it.directory && LyricCacheFiles.portableKey(it.name)?.first?.startsWith(prefix) == true
            })
        }
    }

    private fun delete(entry: Document) {
        if (!DocumentsContract.deleteDocument(resolver, entry.uri)) throw IOException("Cannot remove cached lyric")
    }

    private fun bytes(uri: Uri, limit: Int = LyricCacheFiles.MAX_RECORD_BYTES): ByteArray =
        resolver.openInputStream(uri)?.use { LyricCacheFiles.read(it, limit) }
            ?: throw IOException("Cannot read lyric cache")

    private fun create(parent: Uri, name: String, directory: Boolean): Document {
        val uri = DocumentsContract.createDocument(resolver, parent,
            if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "application/json", name)
            ?: throw IOException("Cannot create lyric cache document")
        if (uri.scheme != "content" || uri.authority != tree.authority) throw IOException("Invalid cache document URI")
        val normalized = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getDocumentId(uri))
        val entry = describe(normalized)
        if (entry.name != name || entry.directory != directory) throw IOException("Provider changed the cache document name")
        return entry
    }

    private fun describe(uri: Uri): Document =
        resolver.query(uri, COLUMNS, null, null, null)?.use {
            checkProvider(it.extras)
            if (!it.moveToFirst()) throw IOException("Lyric cache document is unavailable")
            document(it, uri)
        } ?: throw IOException("Cannot inspect lyric cache document")

    private fun document(cursor: Cursor, uri: Uri? = null): Document {
        val id = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
            ?.takeIf(String::isNotBlank) ?: throw IOException("Cache provider returned no document ID")
        val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            ?: throw IOException("Cache provider returned no document name")
        val mime = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
            ?: throw IOException("Cache provider returned no document type")
        val size = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return Document(uri ?: DocumentsContract.buildDocumentUriUsingTree(tree, id), name,
            mime == DocumentsContract.Document.MIME_TYPE_DIR,
            if (size < 0 || cursor.isNull(size)) 0 else cursor.getLong(size).coerceAtLeast(0),
            if (modified < 0 || cursor.isNull(modified)) 0 else cursor.getLong(modified).coerceAtLeast(0))
    }

    private fun children(parent: Uri): List<Document> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        return resolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
            checkProvider(cursor.extras)
            buildList {
                while (cursor.moveToNext()) {
                    add(document(cursor))
                }
            }
        } ?: throw IOException("Cannot list lyric cache folder")
    }

    private fun checkProvider(extras: android.os.Bundle) {
        extras.getString(DocumentsContract.EXTRA_ERROR)?.takeIf(String::isNotBlank)?.let { throw IOException(it) }
        if (extras.getBoolean(DocumentsContract.EXTRA_LOADING)) throw IOException("Lyric cache folder is still loading")
    }

    @Serializable private data class Marker(val format: String, val version: Int)

    companion object {
        private const val TAG = "LyricCache"
        const val DIRECTORY = "ManaMusicPlayer-Lyrics"
        private const val FORMAT = "ManaMusicPlayer lyric cache"
        private const val MARKER = "cache-info.json"
        private val BUCKET = Regex("[0-9a-f]{2}")
        private val COLUMNS = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)

        // DocumentsContract.isTreeUri is public only from API 24.
        fun isTreeUri(uri: Uri): Boolean = uri.pathSegments.let {
            it.size >= 2 && it[0] == "tree" && it[1].isNotEmpty()
        }

        fun prepare(
            resolver: ContentResolver,
            tree: Uri,
            nowMs: () -> Long = System::currentTimeMillis,
            expiry: () -> LyricCacheExpiry = { LyricCacheExpiry.NEVER },
        ): SafLyricCache {
            if (tree.scheme != "content" || tree.authority.isNullOrBlank() || !isTreeUri(tree)) {
                throw IOException("Select a writable Android document folder")
            }
            val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val parent = SafLyricCache(resolver, LyricCacheFolder(tree.toString(), root.toString(), ""), nowMs, expiry)
            val rootName = parent.describe(root).name
            val contents = parent.children(root)
            if (contents.any { it.name == MARKER }) {
                return SafLyricCache(resolver, LyricCacheFolder(tree.toString(), root.toString(), rootName),
                    nowMs, expiry).also { it.check() }
            }
            val matches = contents.filter { it.name == DIRECTORY }
            if (matches.size > 1 || matches.any { !it.directory }) throw IOException("Cache folder name is already in use")
            val destination = matches.singleOrNull() ?: parent.create(root, DIRECTORY, true)
            val cache = SafLyricCache(resolver, LyricCacheFolder(tree.toString(), destination.uri.toString(),
                "$rootName / $DIRECTORY"), nowMs, expiry)
            if (cache.children(destination.uri).isEmpty()) {
                val marker = cache.create(destination.uri, MARKER, false)
                val data = Json.encodeToString(Marker(FORMAT, 1)).toByteArray(Charsets.UTF_8)
                var verified = false
                try {
                    resolver.openOutputStream(marker.uri, "wt")?.use { it.write(data) }
                        ?: throw IOException("Cannot mark the lyric cache folder")
                    if (!cache.bytes(marker.uri, 1024).contentEquals(data)) throw IOException("Cache folder verification failed")
                    verified = true
                } finally {
                    if (!verified) {
                        try { cache.delete(marker) }
                        catch (error: IOException) { Log.w(TAG, "Could not remove an incomplete cache marker", error) }
                        catch (error: SecurityException) { Log.w(TAG, "Cache marker permission lost during cleanup", error) }
                    }
                }
            }
            cache.check()
            return cache
        }
    }
}
