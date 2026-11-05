package com.devn1n.downly;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Runs aria2c as a child process and, once a second, makes aria2 match the download list
// in Store (add what is missing, pause/resume/remove what the user asked for, copy
// finished files to their destination, notify). Stops itself when nothing needs the engine.
public class Aria2Service extends Service {
    private static final String CH_ENGINE = "downly_engine";
    private static final String CH_ALERT = "downly_alerts";
    private static final int FG_ID = 2201;
    private static final JSONArray KEYS = new JSONArray(Arrays.asList(
        "gid", "status", "totalLength", "completedLength", "downloadSpeed", "connections",
        "errorMessage", "followedBy", "files", "bittorrent"));

    private VideoJobs videos;
    private ScheduledExecutorService ses;
    private Process process;
    private PowerManager.WakeLock wake;
    private int port;
    private String secret;
    private int restarts;
    private int idleTicks;
    private int tickFailures;
    private String lastText = "";
    private int lastPct = -2;
    private long lastNotified, lastWidget;
    private int alertCounter;
    private volatile String lastOutput = "";

    @Override public void onCreate() {
        super.onCreate();
        videos = new VideoJobs(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ENGINE, "Download engine", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Download alerts", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(FG_ID, engineNotification("Download engine starting", -1), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        if (ses == null) {
            prefs().edit().remove("engine_error").apply();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Downly:engine");
            wake.setReferenceCounted(false);
            wake.acquire();
            ses = Executors.newScheduledThreadPool(1);
            ses.execute(this::boot);
        } else {
            restarts = 0; // a retry from the UI gives aria2 a fresh set of start attempts
            tickFailures = 0;
            ses.execute(this::tick); // the list changed, do not wait for the next second
        }
        return START_STICKY;
    }

    // Android 15 stops dataSync services after a few hours; end cleanly instead of crashing.
    public void onTimeout(int startId, int fgsType) { stopSelf(); }

    @Override public void onDestroy() {
        if (videos != null) videos.stopAll();
        DownlyWidget.refresh(this);
        if (ses != null) ses.shutdownNow();
        if (process != null) process.destroy();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private android.content.SharedPreferences prefs() { return getSharedPreferences("downly", MODE_PRIVATE); }

    // ---------------------------------------------------------------- engine

    // aria2 starts on demand (see tick), so video-only downloads never depend on it.
    private void boot() {
        ses.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    private void startEngine() throws Exception {
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libaria2c.so");
        if (!binary.exists()) {
            throw new Exception("aria2c is missing from the APK for " + Build.SUPPORTED_ABIS[0]);
        }
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        secret = UUID.randomUUID().toString();

        File root = new File(getFilesDir(), "aria2");
        root.mkdirs();
        List<String> cmd = new ArrayList<>(Arrays.asList(
            binary.getAbsolutePath(),
            "--enable-rpc=true", "--rpc-listen-all=false", "--rpc-listen-port=" + port,
            "--rpc-secret=" + secret,
            "--async-dns=true", // a static binary cannot use Android's system resolver, so aria2 does its own DNS
            "--async-dns-server=" + dnsServers(),
            "--continue=true", "--allow-overwrite=false", "--auto-file-renaming=true",
            "--file-allocation=none", "--max-concurrent-downloads=3",
            "--max-tries=5", "--retry-wait=3",
            "--seed-time=0", // stop after download instead of seeding forever, so torrents complete
            "--bt-save-metadata=true", "--bt-load-saved-metadata=true",
            "--dht-file-path=" + new File(root, "dht.dat").getAbsolutePath(),
            "--summary-interval=0", "--console-log-level=error"));
        File ca = caBundle(root);
        if (ca != null) cmd.add("--ca-certificate=" + ca.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("HOME", root.getAbsolutePath());
        pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
        lastOutput = "";
        final Process p = pb.start();
        process = p;
        new Thread(() -> { // drain output: a full pipe would freeze aria2, and the last line explains crashes
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) if (!line.trim().isEmpty()) lastOutput = line;
            } catch (Exception ignored) {}
        }).start();

        String why = "";
        for (int i = 0; i < 75; i++) {
            if (!p.isAlive()) throw new Exception("aria2c exited immediately (code " + p.exitValue() + "): " + lastOutput);
            try { rpc("aria2.getVersion"); return; } catch (Exception e) { why = e.toString(); Thread.sleep(200); }
        }
        throw new Exception("aria2c started but never answered RPC (" + why + "): " + lastOutput);
    }

    // The active network's own DNS servers first, public ones as a backup.
    private String dnsServers() {
        List<String> servers = new ArrayList<>();
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            if (lp != null) {
                for (InetAddress a : lp.getDnsServers()) {
                    String host = a.getHostAddress();
                    if (host != null && !host.contains("%")) servers.add(host);
                }
            }
        } catch (Exception ignored) {}
        servers.add("1.1.1.1");
        servers.add("8.8.8.8");
        return String.join(",", servers);
    }

    // aria2 does not read Android's trust store, so hand it the system certificates.
    private File caBundle(File root) {
        File bundle = new File(root, "ca.pem");
        try (OutputStream out = new FileOutputStream(bundle, false)) {
            int count = 0;
            for (String dir : new String[]{"/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts"}) {
                File[] certs = new File(dir).listFiles();
                if (certs == null) continue;
                for (File c : certs) {
                    if (c.isFile()) { out.write(Files.readAllBytes(c.toPath())); out.write('\n'); count++; }
                }
                if (count > 0) break;
            }
            return count > 0 ? bundle : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Object rpc(String method, Object... args) throws Exception {
        JSONArray params = new JSONArray();
        params.put("token:" + secret);
        for (Object a : args) params.put(a);
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("id", "1");
        body.put("method", method);
        body.put("params", params);

        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/jsonrpc").openConnection();
        conn.setConnectTimeout(1500);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder text = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) text.append(line);
        }
        JSONObject res = new JSONObject(text.toString());
        if (res.has("error")) throw new Exception(res.getJSONObject("error").optString("message", "aria2 error"));
        return res.get("result");
    }

    File stageDir() {
        File d = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (d == null) d = new File(getFilesDir(), "downloads");
        d.mkdirs();
        return d;
    }

    // ---------------------------------------------------------------- reconcile loop

    private void tick() {
        try {
            JSONArray records = Store.read(this);
            boolean needAria = false;
            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                if (!Store.isFinal(r) && !r.optString("type").equals("video")) needAria = true;
            }

            // aria2 only runs while a non-video download needs it. If it cannot run, say so and keep serving videos.
            Map<String, JSONObject> live = new HashMap<>();
            boolean ariaUp = false;
            if (needAria) {
                try {
                    if (process == null || !process.isAlive()) {
                        if (++restarts > 3) throw new Exception("aria2c keeps crashing: " + lastOutput);
                        startEngine();
                    }
                    collect(live, (JSONArray) rpc("aria2.tellActive", KEYS));
                    collect(live, (JSONArray) rpc("aria2.tellWaiting", 0, 200, KEYS));
                    collect(live, (JSONArray) rpc("aria2.tellStopped", 0, 200, KEYS));
                    ariaUp = true;
                    if (tickFailures >= 3) prefs().edit().remove("engine_error").apply();
                    tickFailures = 0;
                } catch (Exception e) {
                    if (++tickFailures == 3) prefs().edit().putString("engine_error", e.getMessage() == null ? e.toString() : e.getMessage()).apply();
                }
            } else if (process != null && process.isAlive()) {
                process.destroy();
            }

            final List<JSONObject> patches = new ArrayList<>();
            boolean needEngine = false;
            int active = 0;
            long speed = 0, done = 0, total = 0;

            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i);
                if (Store.isFinal(r)) continue;
                String id = r.getString("id");
                String gid = r.optString("gid", id);
                String want = r.optString("want", "run");
                needEngine = true;
                JSONObject patch = new JSONObject().put("id", id);
                JSONObject l = live.get(gid);

                try {
                    if (r.optString("type").equals("video")) { // yt-dlp, not aria2
                        videos.reconcile(r, patches);
                        continue;
                    }
                    if (!ariaUp) continue; // engine down: the banner already tells the user
                    if (want.equals("remove")) {
                        if (l != null) {
                            try { rpc(isStopped(l) ? "aria2.removeDownloadResult" : "aria2.forceRemove", gid); } catch (Exception ignored) {}
                            deletePartialFiles(l);
                        }
                        new File(getFilesDir(), "torrents/" + id + ".torrent").delete();
                        patch.put("_delete", true);
                        patches.add(patch);
                        continue;
                    }

                    if (l == null) {
                        addToAria(r, want.equals("pause"));
                        patch.put("state", want.equals("pause") ? "paused" : "queued");
                        patches.add(patch);
                        continue;
                    }

                    String status = l.optString("status");
                    JSONArray followed = l.optJSONArray("followedBy");
                    if (status.equals("complete") && followed != null && followed.length() > 0) {
                        // magnet: the metadata download finished, the real download continues under a new gid
                        try { rpc("aria2.removeDownloadResult", gid); } catch (Exception ignored) {}
                        patch.put("gid", followed.getString(0)).put("state", "downloading");
                        patches.add(patch);
                        continue;
                    }

                    long t = num(l, "totalLength"), d = num(l, "completedLength"), s = num(l, "downloadSpeed");
                    patch.put("total", t).put("done", d).put("speed", status.equals("active") ? s : 0)
                         .put("connections", num(l, "connections"))
                         .put("eta", status.equals("active") && s > 0 && t > d ? (t - d) / s : -1);
                    String name = displayName(l);
                    if (!name.isEmpty()) patch.put("name", name);

                    switch (status) {
                        case "active":
                            patch.put("state", "downloading");
                            active++; speed += s; done += d; total += t;
                            if (want.equals("pause")) rpc("aria2.forcePause", gid);
                            break;
                        case "waiting":
                            patch.put("state", "queued");
                            if (want.equals("pause")) rpc("aria2.forcePause", gid);
                            break;
                        case "paused":
                            patch.put("state", "paused");
                            if (want.equals("run")) rpc("aria2.unpause", gid);
                            break;
                        case "complete":
                            finish(r, l, patch);
                            break;
                        case "error":
                            String msg = l.optString("errorMessage", "Download failed");
                            try { rpc("aria2.removeDownloadResult", gid); } catch (Exception ignored) {}
                            fail(r, patch, msg);
                            break;
                        default: // removed
                            fail(r, patch, "Download was removed");
                    }
                    patches.add(patch);
                } catch (Exception e) {
                    fail(r, patch, e.getMessage() == null ? e.toString() : e.getMessage());
                    patches.add(patch);
                }
            }

            applyPatches(patches);
            active += videos.runningCount();

            if (needEngine) {
                idleTicks = 0;
                String text = active == 0 ? "Waiting" : active + " downloading · " + size(speed) + "/s";
                int pct = total > 0 ? (int) (done * 100 / total) : -1;
                long now = System.currentTimeMillis();
                if (!text.equals(lastText) && now - lastNotified > 1500 || pct != lastPct && now - lastNotified > 2000) { // the system UI redraws on every notify
                    getSystemService(NotificationManager.class).notify(FG_ID, engineNotification(text, active > 0 ? pct : -1));
                    lastText = text; lastPct = pct; lastNotified = now;
                }
                if (now - lastWidget > 2000) { DownlyWidget.refresh(this); lastWidget = now; }
            } else if (++idleTicks >= 3) {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        } catch (Exception e) {
            // bad list or store read: the next tick retries
        }
    }

    private void collect(Map<String, JSONObject> live, JSONArray arr) throws Exception {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            live.put(o.getString("gid"), o);
        }
    }

    private boolean isStopped(JSONObject l) {
        String s = l.optString("status");
        return s.equals("complete") || s.equals("error") || s.equals("removed");
    }

    private void applyPatches(final List<JSONObject> patches) throws Exception {
        if (patches.isEmpty()) return;
        Store.mutate(this, all -> {
            for (JSONObject p : patches) {
                String id = p.optString("id");
                JSONObject cur = Store.find(all, id);
                if (cur == null) continue;
                if (p.has("_delete")) {
                    if (cur.optString("want").equals("remove")) Store.delete(all, id);
                    continue;
                }
                java.util.Iterator<String> keys = p.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    if (!k.equals("id")) cur.put(k, p.get(k));
                }
            }
        });
    }

    private void addToAria(JSONObject r, boolean paused) throws Exception {
        String id = r.getString("id");
        try { rpc("aria2.removeDownloadResult", id); } catch (Exception ignored) {} // stale result from a failed run, if any
        JSONObject opts = new JSONObject();
        opts.put("gid", id);
        opts.put("dir", stageDir().getAbsolutePath());
        opts.put("split", "8");
        opts.put("max-connection-per-server", "8");
        opts.put("min-split-size", "1M");
        if (paused) opts.put("pause", "true");
        if (r.optString("type").equals("torrent")) {
            File t = new File(getFilesDir(), "torrents/" + id + ".torrent");
            String b64 = Base64.encodeToString(Files.readAllBytes(t.toPath()), Base64.NO_WRAP);
            rpc("aria2.addTorrent", b64, new JSONArray(), opts);
        } else {
            rpc("aria2.addUri", new JSONArray().put(r.getString("uri")), opts);
        }
    }

    private void fail(JSONObject r, JSONObject patch, String message) throws Exception {
        patch.put("state", "failed").put("error", message).put("speed", 0).put("eta", -1).put("connections", 0);
        alert("Download failed", r.optString("name") + " · " + message);
    }

    private void finish(JSONObject r, JSONObject l, JSONObject patch) throws Exception {
        String id = r.getString("id");
        String folder = r.optString("folder");
        File stage = stageDir();
        JSONArray files = l.optJSONArray("files");
        Map<String, Uri> dirs = new HashMap<>();
        String first = "";
        List<File> staged = new ArrayList<>();
        try {
            if (files != null) {
                for (int i = 0; i < files.length(); i++) {
                    JSONObject f = files.getJSONObject(i);
                    if (f.optString("selected").equals("false")) continue;
                    File src = new File(f.optString("path"));
                    if (!src.isFile()) continue;
                    String path = src.getAbsolutePath();
                    String base = stage.getAbsolutePath() + "/";
                    String rel = path.startsWith(base) ? path.substring(base.length()) : src.getName();
                    String uri = Folders.publish(this, src, rel, folder, Folders.categoryFolder(this, r.optString("type"), src.getName()), dirs);
                    if (first.isEmpty()) first = uri;
                    staged.add(src);
                }
            }
            if (first.isEmpty()) throw new Exception("aria2 reported no files");
        } catch (Exception e) {
            // the data is safe in the app's own folder; say so instead of pretending it worked
            fail(r, patch, "Downloaded, but could not save to the chosen folder: " + e.getMessage());
            return;
        }
        for (File f : staged) f.delete();
        deletePartialFiles(l);
        new File(getFilesDir(), "torrents/" + id + ".torrent").delete();
        try { rpc("aria2.removeDownloadResult", l.getString("gid")); } catch (Exception ignored) {}

        patch.put("state", "completed").put("speed", 0).put("eta", -1).put("connections", 0)
             .put("openUri", first).put("done", num(l, "totalLength"));
        alert("Download complete", displayNameOr(l, r.optString("name")));
    }

    // Removes aria2's .aria2 control file and, for multi-file torrents, the leftover folder.
    private void deletePartialFiles(JSONObject l) {
        JSONArray files = l.optJSONArray("files");
        if (files == null) return;
        String base = stageDir().getAbsolutePath() + "/";
        for (int i = 0; i < files.length(); i++) {
            String path = files.optJSONObject(i) == null ? "" : files.optJSONObject(i).optString("path");
            if (!path.startsWith(base)) continue;
            new File(path).delete();
            new File(path + ".aria2").delete();
            int slash = path.indexOf('/', base.length());
            if (slash > 0) deleteTree(new File(path.substring(0, slash)));
        }
    }

    private void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    // ---------------------------------------------------------------- helpers

    private static long num(JSONObject o, String key) {
        try { return Long.parseLong(o.optString(key, "0")); } catch (Exception e) { return 0; }
    }

    private String displayName(JSONObject l) { return displayNameOr(l, ""); }

    private String displayNameOr(JSONObject l, String fallback) {
        JSONObject bt = l.optJSONObject("bittorrent");
        JSONObject info = bt == null ? null : bt.optJSONObject("info");
        if (info != null && !info.optString("name").isEmpty()) return info.optString("name");
        JSONArray files = l.optJSONArray("files");
        if (files != null && files.length() > 0) {
            String path = files.optJSONObject(0).optString("path");
            String n = path.isEmpty() ? "" : new File(path).getName();
            if (!n.isEmpty() && !n.startsWith("[METADATA]")) return n;
        }
        return fallback;
    }

    private static String size(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f KB", v);
        v /= 1024;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        return String.format(Locale.US, "%.2f GB", v / 1024);
    }

    private PendingIntent openApp() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification engineNotification(String text, int percent) {
        Notification.Builder b = new Notification.Builder(this, CH_ENGINE)
            .setSmallIcon(R.drawable.ic_stat_downly)
            .setContentTitle("Downly")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp());
        if (percent >= 0) b.setProgress(100, percent, false);
        return b.build();
    }

    void alert(String title, String text) {
        if (!prefs().getBoolean("notifications", true)) return;
        Notification n = new Notification.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_downly)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build();
        getSystemService(NotificationManager.class).notify(3000 + (alertCounter++ % 500), n);
    }
}
