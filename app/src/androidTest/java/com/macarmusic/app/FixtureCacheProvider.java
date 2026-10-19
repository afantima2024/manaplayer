package com.macarmusic.app;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.UUID;

// Separate writable provider; no operation can reach the synthetic music provider or real media.
public class FixtureCacheProvider extends DocumentsProvider {
    private static final String[] ROOTS = {"root", "second", "readonly", "temporary", "foreign"};
    private static final String[] COLUMNS = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED};
    private final LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    private File directory;
    private AtomicFile index;
    private boolean unavailable;
    private boolean corruptReadback;
    private boolean denyDelete;
    private boolean reverseColumns;
    private int failAfterWrites = -1;
    private int failAfterDeletes = -1;
    private int writes;
    private int deletes;
    private String grantToken;
    private static class Node {
        final String id, parent, name;
        final boolean folder;
        Node(String id, String parent, String name, boolean folder) {
            this.id = id; this.parent = parent; this.name = name; this.folder = folder;
        }
    }

    @Override public synchronized boolean onCreate() {
        directory = new File(getContext().getFilesDir(), "cache-documents-fixture");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Fixture directory failed");
        index = new AtomicFile(new File(directory, "index.json"));
        for (String root : ROOTS) nodes.put(root, new Node(root, "", "Synthetic " + root, true));
        if (index.getBaseFile().exists() || new File(index.getBaseFile().getPath() + ".bak").exists()) {
            try {
                JSONArray array = new JSONArray(new String(index.readFully(), StandardCharsets.UTF_8));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject row = array.getJSONObject(i);
                    String id = row.getString("id");
                    if (!id.matches("node-[0-9a-f-]{36}")) throw new IOException("Invalid fixture ID");
                    nodes.put(id, new Node(id, row.getString("parent"), row.getString("name"), row.getBoolean("folder")));
                }
            } catch (IOException | org.json.JSONException error) { throw new IllegalStateException("Cannot restore cache fixture", error); }
        }
        return true;
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Node node : nodes.values()) {
                if (node.parent.isEmpty()) continue;
                array.put(new JSONObject().put("id", node.id).put("parent", node.parent)
                    .put("name", node.name).put("folder", node.folder));
            }
            FileOutputStream output = index.startWrite();
            try {
                output.write(array.toString().getBytes(StandardCharsets.UTF_8));
                index.finishWrite(output);
            } catch (IOException error) { index.failWrite(output); throw error; }
        } catch (IOException | org.json.JSONException error) { throw new IllegalStateException("Cannot persist cache fixture", error); }
    }

    private Node node(String id) throws FileNotFoundException {
        if (unavailable || !nodes.containsKey(id)) throw new FileNotFoundException("Synthetic cache unavailable");
        return nodes.get(id);
    }

    private File file(Node node) { return new File(directory, node.id); }

    private void add(MatrixCursor cursor, Node node) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : cursor.getColumnNames()) {
            Object value = null;
            if (column.equals(DocumentsContract.Document.COLUMN_DOCUMENT_ID)) value = node.id;
            if (column.equals(DocumentsContract.Document.COLUMN_DISPLAY_NAME)) value = node.name;
            if (column.equals(DocumentsContract.Document.COLUMN_MIME_TYPE)) value =
                node.folder ? DocumentsContract.Document.MIME_TYPE_DIR : "application/json";
            if (column.equals(DocumentsContract.Document.COLUMN_FLAGS)) value = node.id.equals("readonly") ? 0 :
                node.folder ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE :
                    DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
            if (column.equals(DocumentsContract.Document.COLUMN_SIZE)) value = node.folder ? 0 : file(node).length();
            if (column.equals(DocumentsContract.Document.COLUMN_LAST_MODIFIED)) value = file(node).lastModified();
            row.add(column, value);
        }
    }

    @Override public synchronized Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection != null ? projection : new String[] {
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS});
        for (String root : ROOTS) {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : cursor.getColumnNames()) {
                Object value = null;
                if (column.equals(DocumentsContract.Root.COLUMN_ROOT_ID) ||
                    column.equals(DocumentsContract.Root.COLUMN_DOCUMENT_ID)) value = root;
                if (column.equals(DocumentsContract.Root.COLUMN_TITLE)) value = "Synthetic " + root;
                if (column.equals(DocumentsContract.Root.COLUMN_FLAGS)) value =
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD;
                row.add(column, value);
            }
        }
        return cursor;
    }

    @Override public synchronized Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(columns(projection));
        add(cursor, node(id));
        return cursor;
    }

    @Override public synchronized Cursor queryChildDocuments(String parent, String[] projection, String sort)
            throws FileNotFoundException {
        node(parent);
        MatrixCursor cursor = new MatrixCursor(columns(projection));
        for (Node child : nodes.values()) if (child.parent.equals(parent)) add(cursor, child);
        return cursor;
    }

    private String[] columns(String[] projection) {
        String[] columns = (projection != null ? projection : COLUMNS).clone();
        if (reverseColumns) {
            for (int left = 0, right = columns.length - 1; left < right; left++, right--) {
                String saved = columns[left]; columns[left] = columns[right]; columns[right] = saved;
            }
        }
        return columns;
    }

    @Override public synchronized boolean isChildDocument(String parent, String id) {
        for (int depth = 0; depth < 16 && nodes.containsKey(id); depth++) {
            id = nodes.get(id).parent;
            if (id.equals(parent)) return true;
        }
        return false;
    }

    @Override public synchronized String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        if (!node(parent).folder || parent.equals("readonly") || isChildDocument("readonly", parent)) {
            throw new FileNotFoundException("Synthetic read-only folder");
        }
        for (Node other : nodes.values()) {
            if (other.parent.equals(parent) && other.name.equals(name)) throw new FileNotFoundException("Duplicate fixture name");
        }
        String id = "node-" + UUID.randomUUID();
        Node added = new Node(id, parent, name, DocumentsContract.Document.MIME_TYPE_DIR.equals(mime));
        if (!added.folder) {
            try { if (!file(added).createNewFile()) throw new IOException("Fixture file exists"); }
            catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        nodes.put(id, added);
        save();
        return id;
    }

    @Override public synchronized ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        Node node = node(id);
        if (node.folder) throw new FileNotFoundException("Not a cache file");
        if (!mode.equals("r")) {
            if (failAfterWrites >= 0 && writes >= failAfterWrites) throw new FileNotFoundException("Synthetic write failure");
            if (isChildDocument("readonly", id)) throw new FileNotFoundException("Synthetic read-only cache");
            writes++;
        }
        File selected = file(node);
        if (mode.equals("r") && corruptReadback && node.name.matches("[0-9a-f]{64}-[0-9]+\\.json")) {
            selected = new File(directory, "corrupt-readback.json");
            try (FileOutputStream output = new FileOutputStream(selected)) { output.write("{bad".getBytes(StandardCharsets.UTF_8)); }
            catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        }
        return ParcelFileDescriptor.open(selected, ParcelFileDescriptor.parseMode(mode));
    }

    @Override public synchronized void deleteDocument(String id) throws FileNotFoundException {
        Node node = node(id);
        if (denyDelete || node.parent.isEmpty() || (failAfterDeletes >= 0 && deletes >= failAfterDeletes)) {
            throw new FileNotFoundException("Synthetic deletion denied");
        }
        for (Node child : nodes.values()) if (child.parent.equals(id)) throw new FileNotFoundException("Directory not empty");
        if (file(node).exists() && !file(node).delete()) throw new FileNotFoundException("Fixture deletion failed");
        nodes.remove(id);
        deletes++;
        save();
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        if (method.equals("cacheGrant")) {
            grantToken = arg;
            return Bundle.EMPTY;
        }
        if (method.equals("cacheFixture")) {
            if (extras != null) {
                unavailable = extras.getBoolean("unavailable");
                corruptReadback = extras.getBoolean("corruptReadback");
                denyDelete = extras.getBoolean("denyDelete");
                reverseColumns = extras.getBoolean("reverseColumns");
                failAfterWrites = extras.getInt("failAfterWrites", -1);
                failAfterDeletes = extras.getInt("failAfterDeletes", -1);
                if (extras.getBoolean("resetCounters")) { writes = 0; deletes = 0; }
                if (extras.getBoolean("reset")) {
                    for (Node node : new ArrayList<>(nodes.values())) {
                        if (node.parent.isEmpty()) continue;
                        if (file(node).exists() && !file(node).delete()) throw new IllegalStateException("Fixture reset failed");
                        nodes.remove(node.id);
                    }
                    save();
                }
            }
            Bundle result = new Bundle();
            result.putInt("writes", writes);
            result.putInt("deletes", deletes);
            int records = 0;
            for (Node node : nodes.values()) if (!node.folder) records++;
            result.putInt("records", records);
            result.putString("grantToken", grantToken);
            return result;
        }
        return super.call(method, arg, extras);
    }
}
