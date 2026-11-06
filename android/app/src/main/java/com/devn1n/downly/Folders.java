package com.devn1n.downly;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;

// Where finished files go: <root>/<Category>/..., where root is Downloads/Downly or the folder the user picked.
final class Folders {
    static final String ROOT = "Downly";

    private Folders() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("downly", Context.MODE_PRIVATE); }
    static boolean sorting(Context c) { return prefs(c).getBoolean("sort", true); }
    static String tree(Context c) { return prefs(c).getString("folder", ""); }

    // "" when sorting is off
    static String categoryFolder(Context c, String recordType, String fileName) {
        return sorting(c) ? Categories.of(recordType, fileName) : "";
    }

    // human readable destination, e.g. "Downloads/Downly/Video"
    static String label(Context c, String category) {
        String t = tree(c);
        String base = t.isEmpty() ? "Downloads/" + ROOT : Uri.decode(String.valueOf(Uri.parse(t).getLastPathSegment())).replaceFirst("^primary:", "");
        return category.isEmpty() ? base : base + "/" + category;
    }

    static String mimeOf(String name) {
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Categories.ext(name));
        return m == null ? "application/octet-stream" : m;
    }

    // ---------------------------------------------------------------- creating the folders up front

    // Make the empty category folders exist so they are visible straight after install.
    // MediaStore cannot create an empty folder directly, so a hidden placeholder is added and removed again.
    static void ensureAll(Context c) {
        ContentResolver cr = c.getContentResolver();
        try { new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT).mkdirs(); } catch (Exception ignored) {}
        for (String cat : Categories.ALL) {
            try {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, ".downly-keep");
                v.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + ROOT + "/" + cat);
                Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) cr.delete(u, null, null);
            } catch (Exception ignored) {}
        }
        String t = tree(c);
        if (!t.isEmpty()) ensureTree(c, Uri.parse(t));
    }

    static void ensureTree(Context c, Uri tree) {
        try {
            ContentResolver cr = c.getContentResolver();
            Uri root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            for (String cat : Categories.ALL) dir(cr, tree, root, cat);
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- publishing a finished file

    // category: folder name under the root, "" for none. rel: path inside the download (torrent sub folders).
    static String publish(Context c, File src, String rel, String treeFolder, String category, Map<String, Uri> dirs) throws Exception {
        String name = src.getName();
        int slash = rel.lastIndexOf('/');
        String parent = slash > 0 ? rel.substring(0, slash) : "";
        String mime = mimeOf(name);
        ContentResolver cr = c.getContentResolver();

        if (treeFolder.isEmpty()) {
            // Finished photos belong in Pictures so the gallery (Photos app) indexes them; everything else
            // goes under Downloads. MediaStore does not index getExternalFilesDir on API 29+, so the insert
            // is the whole point — there is no side channel for images.
            if (Categories.isImage(name)) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, mime);
                v.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ROOT + "/Images");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("Could not create a file in Pictures/Downly/Images");
                copy(src, cr, uri);
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, done, null, null);
                return uri.toString();
            }
            String path = Environment.DIRECTORY_DOWNLOADS + "/" + ROOT + (category.isEmpty() ? "" : "/" + category) + (parent.isEmpty() ? "" : "/" + parent);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, path);
            v.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("Could not create a file in " + path);
            copy(src, cr, uri);
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri.toString();
        }

        Uri tree = Uri.parse(treeFolder);
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String walked = "";
        String sub = (category.isEmpty() ? "" : category) + (parent.isEmpty() ? "" : (category.isEmpty() ? "" : "/") + parent);
        if (!sub.isEmpty()) {
            for (String seg : sub.split("/")) {
                walked = walked.isEmpty() ? seg : walked + "/" + seg;
                Uri cached = dirs.get(walked);
                if (cached == null) { cached = dir(cr, tree, dir, seg); dirs.put(walked, cached); }
                dir = cached;
            }
        }
        Uri doc = DocumentsContract.createDocument(cr, dir, mime, name);
        if (doc == null) throw new Exception("Could not create " + name + " in the chosen folder");
        copy(src, cr, doc);
        return doc.toString();
    }

    // existing child folder named `name`, or a new one
    private static Uri dir(ContentResolver cr, Uri tree, Uri parent, String name) throws Exception {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent));
        try (Cursor q = cr.query(kids, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (q != null && q.moveToNext()) {
                if (name.equals(q.getString(1)) && DocumentsContract.Document.MIME_TYPE_DIR.equals(q.getString(2))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, q.getString(0));
                }
            }
        }
        Uri made = DocumentsContract.createDocument(cr, parent, DocumentsContract.Document.MIME_TYPE_DIR, name);
        if (made == null) throw new Exception("Could not create folder " + name);
        return made;
    }

    private static void copy(File src, ContentResolver cr, Uri dest) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream out = cr.openOutputStream(dest)) {
            if (out == null) throw new Exception("Could not open destination");
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }
}
