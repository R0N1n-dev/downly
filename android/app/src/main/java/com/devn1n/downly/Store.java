package com.devn1n.downly;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

// The download list. The plugin (UI) and the service both edit it, so every change
// goes through mutate() under one lock. aria2 itself is stateless: this file is the truth.
final class Store {
    interface Edit { void apply(JSONArray all) throws Exception; }

    private static final Object LOCK = new Object();

    private Store() {}

    private static File file(Context c) { return new File(c.getFilesDir(), "downloads.json"); }

    static JSONArray read(Context c) {
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (!f.exists()) return new JSONArray();
                return new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new JSONArray();
            }
        }
    }

    static void mutate(Context c, Edit edit) throws Exception {
        synchronized (LOCK) {
            JSONArray all = read(c);
            edit.apply(all);
            File tmp = new File(c.getFilesDir(), "downloads.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(all.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file(c))) throw new Exception("Could not save download list");
        }
    }

    static JSONObject find(JSONArray all, String id) {
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    static void delete(JSONArray all, String id) {
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) { all.remove(i); return; }
        }
    }

    static boolean isFinal(JSONObject r) {
        String s = r.optString("state");
        return s.equals("completed") || s.equals("failed");
    }

    // aria2 needs a 16 hex digit gid; we use it as our own id too.
    static String newId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16); }

    static JSONObject create(String id, String name, String type, String uri, String folder) throws Exception {
        JSONObject r = new JSONObject();
        r.put("id", id);
        r.put("gid", id);
        r.put("name", name);
        r.put("type", type);          // http | magnet | torrent
        r.put("uri", uri);
        r.put("folder", folder);      // SAF tree uri chosen at add time, "" = public Downloads
        r.put("want", "run");         // run | pause | remove
        r.put("state", "queued");     // queued | downloading | paused | completed | failed
        r.put("total", 0);
        r.put("done", 0);
        r.put("speed", 0);
        r.put("eta", -1);
        r.put("connections", 0);
        r.put("error", "");
        r.put("openUri", "");
        r.put("created", System.currentTimeMillis());
        return r;
    }
}
