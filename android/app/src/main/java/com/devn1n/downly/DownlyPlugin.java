package com.devn1n.downly;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

// Thin bridge. Everything except the folder/file pickers just edits Store and wakes the service.
@CapacitorPlugin(
    name = "Downly",
    permissions = { @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS }) }
)
public class DownlyPlugin extends Plugin {
    private static volatile String launchUri = "";

    // A magnet link or .torrent opened from another app. The UI picks it up with getLaunch().
    static void remember(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data != null) launchUri = data.toString();
    }

    // "Open Downly" from the share sheet hands the link over the same way
    static void remember(String uri) { launchUri = uri; }

    private SharedPreferences prefs() { return getContext().getSharedPreferences("downly", Context.MODE_PRIVATE); }

    private void wakeService() { Downloads.start(getContext()); }

    private String folder() { return prefs().getString("folder", ""); }

    // ------------------------------------------------------------ reading state

    @PluginMethod
    public void list(PluginCall call) {
        JSObject out = new JSObject();
        out.put("items", Store.read(getContext()));
        out.put("engineError", prefs().getString("engine_error", ""));
        out.put("notifications", prefs().getBoolean("notifications", true));
        out.put("cookies", new File(getContext().getFilesDir(), "cookies.txt").isFile());
        out.put("videoEngine", VideoEngine.state().name().toLowerCase());
        out.put("sort", Folders.sorting(getContext()));
        out.put("dest", Folders.label(getContext(), ""));
        String f = folder();
        out.put("folder", f.isEmpty() ? "" : Uri.decode(Uri.parse(f).getLastPathSegment()));
        call.resolve(out);
    }

    @PluginMethod
    public void getLaunch(PluginCall call) {
        JSObject out = new JSObject();
        out.put("uri", launchUri);
        launchUri = "";
        call.resolve(out);
    }

    // ------------------------------------------------------------ adding

    @PluginMethod
    public void add(PluginCall call) {
        try {
            Downloads.add(getContext(), call.getString("uri", ""), Boolean.TRUE.equals(call.getBoolean("video", false)),
                call.getString("mode", "video"), call.getString("quality", "best"), call.getString("audioFormat", "mp3"), call.getString("bitrate", "192K"));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    // What is this link? video page, plain file (name, size, folder), web page, torrent. Never throws: unknown is a valid answer.
    @PluginMethod
    public void probe(PluginCall call) {
        JSObject out = new JSObject();
        try {
            JSONObject p = Probe.of(call.getString("uri", "").trim());
            for (java.util.Iterator<String> it = p.keys(); it.hasNext(); ) { String k = it.next(); out.put(k, p.get(k)); }
        } catch (Exception e) {
            out.put("kind", "unknown");
        }
        String cat = out.optString("category", "");
        out.put("dest", Folders.label(getContext(), Folders.sorting(getContext()) ? cat : ""));
        call.resolve(out);
    }

    @PluginMethod
    public void pickTorrent(PluginCall call) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-bittorrent", "application/octet-stream"});
        startActivityForResult(call, pick, "torrentPicked");
    }

    @ActivityCallback
    private void torrentPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve(); // cancelled
            return;
        }
        importTorrent(call, result.getData().getData());
    }

    @PluginMethod
    public void addTorrentUri(PluginCall call) {
        importTorrent(call, Uri.parse(call.getString("uri", "")));
    }

    private void importTorrent(PluginCall call, Uri uri) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = getContext().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Could not open the file");
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    bytes.write(buf, 0, n);
                    if (bytes.size() > 10 * 1024 * 1024) throw new Exception("File is too large to be a torrent");
                }
            }
            byte[] data = bytes.toByteArray();
            if (data.length == 0 || data[0] != 'd') throw new Exception("That is not a .torrent file");

            String id = Store.newId();
            File dir = new File(getContext().getFilesDir(), "torrents");
            dir.mkdirs();
            Files.write(new File(dir, id + ".torrent").toPath(), data);

            String name = "Torrent";
            try (Cursor c = getContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) name = c.getString(0);
            } catch (Exception ignored) {}
            insert(Store.create(id, name, "torrent", "", folder()));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private void insert(final JSONObject record) throws Exception {
        Store.mutate(getContext(), all -> all.put(record));
        wakeService();
    }

    // ------------------------------------------------------------ controlling

    private interface Change { void apply(JSONObject r) throws Exception; }

    private void change(PluginCall call, final Change change) {
        final String id = call.getString("id", "");
        try {
            Store.mutate(getContext(), all -> {
                JSONObject r = Store.find(all, id);
                if (r != null) change.apply(r);
            });
            wakeService();
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void pause(PluginCall call) {
        change(call, r -> { if (!Store.isFinal(r)) r.put("want", "pause"); });
    }

    @PluginMethod
    public void resume(PluginCall call) {
        change(call, r -> {
            if (r.optString("state").equals("failed")) { // retry
                r.put("state", "queued").put("error", "").put("done", 0).put("total", 0);
                r.put("gid", r.getString("id"));
            }
            if (!r.optString("state").equals("completed")) r.put("want", "run");
        });
    }

    @PluginMethod
    public void remove(PluginCall call) {
        final String id = call.getString("id", "");
        try {
            final boolean[] needService = {false};
            final boolean engineBroken = !prefs().getString("engine_error", "").isEmpty();
            Store.mutate(getContext(), all -> {
                JSONObject r = Store.find(all, id);
                if (r == null) return;
                if (Store.isFinal(r) || engineBroken) {
                    new File(getContext().getFilesDir(), "torrents/" + id + ".torrent").delete();
                    Store.delete(all, id);
                } else {
                    r.put("want", "remove"); // the service stops aria2's copy and cleans up partial files
                    needService[0] = true;
                }
            });
            if (needService[0]) wakeService();
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void clearHistory(PluginCall call) {
        try {
            Store.mutate(getContext(), all -> {
                for (int i = all.length() - 1; i >= 0; i--) if (Store.isFinal(all.getJSONObject(i))) all.remove(i);
            });
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void retryEngine(PluginCall call) {
        try {
            wakeService();
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not start the download engine: " + e.getMessage());
        }
    }

    @PluginMethod
    public void open(PluginCall call) {
        JSONObject r = Store.find(Store.read(getContext()), call.getString("id", ""));
        String uri = r == null ? "" : r.optString("openUri");
        if (uri.isEmpty()) { call.reject("File is not available"); return; }
        try {
            Intent view = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), Folders.mimeOf(r.optString("name")))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(view);
            call.resolve();
        } catch (ActivityNotFoundException e) {
            call.reject("No app can open this file");
        }
    }

    // ------------------------------------------------------------ settings

    @PluginMethod
    public void ensureNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT < 33 || getPermissionState("notifications") == PermissionState.GRANTED) {
            call.resolve();
        } else {
            requestPermissionForAlias("notifications", call, "notificationsAnswered");
        }
    }

    @PermissionCallback
    private void notificationsAnswered(PluginCall call) {
        call.resolve(); // downloads still work when denied, only the alerts are hidden
    }

    @PluginMethod
    public void setNotifications(PluginCall call) {
        prefs().edit().putBoolean("notifications", call.getBoolean("enabled", true)).apply();
        call.resolve();
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, pick, "folderPicked");
    }

    @ActivityCallback
    private void folderPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve();
            return;
        }
        try {
            Uri tree = result.getData().getData();
            getContext().getContentResolver().takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs().edit().putString("folder", tree.toString()).apply();
            new Thread(() -> Folders.ensureTree(getContext(), tree)).start(); // category folders inside the picked one
            call.resolve();
        } catch (Exception e) {
            call.reject("Could not use that folder: " + e.getMessage());
        }
    }

    // Netscape cookies.txt exported from a desktop browser. yt-dlp reads it for logged-in sites such as Instagram.
    @PluginMethod
    public void pickCookies(PluginCall call) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(call, pick, "cookiesPicked");
    }

    @ActivityCallback
    private void cookiesPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.resolve();
            return;
        }
        try (InputStream in = getContext().getContentResolver().openInputStream(result.getData().getData())) {
            if (in == null) throw new Exception("Could not open the file");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
            byte[] data = bytes.toByteArray();
            String text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            if (data.length > 5 * 1024 * 1024 || !text.contains("\t")) throw new Exception("That is not a cookies.txt file");
            Files.write(new File(getContext().getFilesDir(), "cookies.txt").toPath(), data);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void clearCookies(PluginCall call) {
        new File(getContext().getFilesDir(), "cookies.txt").delete();
        call.resolve();
    }

    @PluginMethod
    public void setSort(PluginCall call) {
        prefs().edit().putBoolean("sort", Boolean.TRUE.equals(call.getBoolean("enabled", true))).apply();
        call.resolve();
    }

    @PluginMethod
    public void ensureFolders(PluginCall call) {
        new Thread(() -> { Folders.ensureAll(getContext()); call.resolve(); }).start();
    }

    // colour behind the system bars, so the strip around the web view matches the current theme
    @PluginMethod
    public void setWindowColor(PluginCall call) {
        final String hex = call.getString("color", "#000000");
        getActivity().runOnUiThread(() -> {
            try { getActivity().getWindow().getDecorView().setBackgroundColor(android.graphics.Color.parseColor(hex)); } catch (Exception ignored) {}
        });
        call.resolve();
    }

    @PluginMethod
    public void clearFolder(PluginCall call) {
        prefs().edit().remove("folder").apply();
        call.resolve();
    }
}
