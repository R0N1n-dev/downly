package com.devn1n.downly;

import android.net.Uri;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import kotlin.Unit;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Records of type "video" (Instagram, TikTok, YouTube, ...) are downloaded by yt-dlp instead of aria2.
// Aria2Service calls reconcile() once a second; the work itself runs on two background threads.
final class VideoJobs {
    private static final Pattern STATS = Pattern.compile(
        "([\\d.]+)%\\s+of\\s+~?\\s*([\\d.]+)\\s*([KMG]?i?B)\\s+at\\s+([\\d.]+)\\s*([KMG]?i?B)/s");
    private static final Pattern DEST = Pattern.compile("\\[download\\] Destination: (.+)");

    private final Aria2Service svc;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Set<String> running = ConcurrentHashMap.newKeySet();   // queued or downloading
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet(); // paused or removed by the user
    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();

    VideoJobs(Aria2Service svc) { this.svc = svc; }

    int runningCount() { return running.size(); }

    void stopAll() {
        for (String id : running) cancel(id);
        pool.shutdownNow();
    }

    // ------------------------------------------------------------ called from the service tick

    void reconcile(JSONObject r, List<JSONObject> patches) throws Exception {
        String id = r.getString("id");
        String want = r.optString("want", "run");
        JSONObject patch = new JSONObject().put("id", id);

        if (want.equals("remove")) {
            cancel(id);
            deleteTree(jobDir(id));
            patches.add(patch.put("_delete", true));
        } else if (want.equals("pause")) {
            cancel(id);
            if (!r.optString("state").equals("paused")) {
                patches.add(patch.put("state", "paused").put("speed", 0).put("eta", -1));
            }
        } else if (!running.contains(id)) {
            running.add(id);
            pool.execute(() -> work(r));
        }
    }

    private void cancel(String id) {
        if (running.contains(id)) {
            cancelled.add(id);
            YoutubeDL.getInstance().destroyProcessById(id);
        }
    }

    // ------------------------------------------------------------ the download itself

    private void work(JSONObject r) {
        String id = r.optString("id");
        try {
            // waits for the engine that was warmed up at app start (instant after the first launch)
            VideoEngine.run(svc.getApplication(), () -> {
                if (cancelled.contains(id)) return null;
                patch(id, p -> p.put("state", "downloading"));
                File dir = jobDir(id);
                dir.mkdirs();
                YoutubeDLRequest req = new YoutubeDLRequest(r.getString("uri"));
                req.addOption("--no-playlist");
                req.addOption("--no-mtime");
                for (String[] o : formatArgs(r.optString("mode", ""), r.optString("quality", "best"), r.optString("audioFormat", "mp3"), r.optString("bitrate", "192K"))) {
                    if (o.length == 1) req.addOption(o[0]); else req.addOption(o[0], o[1]);
                }
                File cookies = new File(svc.getFilesDir(), "cookies.txt"); // imported in Settings, Instagram needs it
                if (cookies.isFile()) req.addOption("--cookies", cookies.getAbsolutePath());
                req.addOption("-o", dir.getAbsolutePath() + "/%(title).70B [%(id)s].%(ext)s");
                YoutubeDL.getInstance().execute(req, id, (progress, eta, line) -> {
                    onProgress(id, eta, line);
                    return Unit.INSTANCE;
                });
                if (!cancelled.contains(id)) finish(r, dir);
                return null;
            });
        } catch (Exception e) {
            if (!cancelled.contains(id)) fail(id, r.optString("name"), explain(e));
        } finally {
            running.remove(id);
            cancelled.remove(id);
            lastWrite.remove(id);
        }
    }

    private static final List<String> AUDIO = Arrays.asList("mp3", "m4a", "opus", "flac", "wav");
    private static final List<String> HEIGHTS = Arrays.asList("1080", "720", "480");
    private static final List<String> BITRATES = Arrays.asList("128K", "192K", "320K");

    // mode video: h264 + aac mp4 (plays everywhere), optionally capped at 1080/720/480p.
    // mode audio: converted to mp3 / m4a / opus / flac / wav. (quality "audio" is how older records stored it.)
    static List<String[]> formatArgs(String mode, String quality, String audioFormat, String bitrate) {
        List<String[]> a = new ArrayList<>();
        if ("audio".equals(mode) || "audio".equals(quality)) {
            String fmt = AUDIO.contains(audioFormat) ? audioFormat : ("audio".equals(mode) ? "mp3" : "m4a");
            a.add(new String[]{"-f", "ba/b"});
            a.add(new String[]{"-x"});
            a.add(new String[]{"--audio-format", fmt});
            if (!fmt.equals("flac") && !fmt.equals("wav")) a.add(new String[]{"--audio-quality", BITRATES.contains(bitrate) ? bitrate : "192K"});
            a.add(new String[]{"--embed-metadata"});
            return a;
        }
        String h = HEIGHTS.contains(quality) ? quality : null;
        a.add(new String[]{"-f", h == null ? "bv*+ba/b" : "bv*[height<=" + h + "]+ba/b[height<=" + h + "]/b"});
        a.add(new String[]{"-S", "vcodec:h264,res,acodec:m4a"});
        a.add(new String[]{"--merge-output-format", "mp4"});
        return a;
    }

    private void onProgress(String id, long eta, String line) {
        if (cancelled.contains(id)) return;
        final String name = nameFrom(line);
        Matcher m = STATS.matcher(line);
        final boolean stats = m.find();
        final long total = stats ? Math.round(Double.parseDouble(m.group(2)) * unit(m.group(3))) : 0;
        final long speed = stats ? Math.round(Double.parseDouble(m.group(4)) * unit(m.group(5))) : 0;
        final double pct = stats ? Double.parseDouble(m.group(1)) : 0;

        long now = System.currentTimeMillis();
        Long last = lastWrite.get(id);
        if (name == null && (!stats || (last != null && now - last < 1000))) return; // at most one write a second
        lastWrite.put(id, now);
        try {
            patch(id, p -> {
                if (stats) p.put("total", total).put("done", Math.round(total * pct / 100)).put("speed", speed).put("eta", eta);
                if (name != null) p.put("name", name);
            });
        } catch (Exception ignored) {}
    }

    // "[download] Destination: /path/Title [id].f137.mp4" -> "Title [id].mp4"
    private static String nameFrom(String line) {
        Matcher m = DEST.matcher(line);
        if (!m.find()) return null;
        return new File(m.group(1).trim()).getName().replaceAll("\\.f[0-9a-z-]+(?=\\.[A-Za-z0-9]+$)", "");
    }

    private static double unit(String u) {
        switch (u.charAt(0)) {
            case 'K': return 1024;
            case 'M': return 1024 * 1024;
            case 'G': return 1024.0 * 1024 * 1024;
            default: return 1;
        }
    }

    // ------------------------------------------------------------ finishing

    private void finish(JSONObject r, File dir) throws Exception {
        String id = r.getString("id");
        File[] found = dir.listFiles();
        List<File> files = new ArrayList<>();
        if (found != null) {
            for (File f : found) {
                String n = f.getName();
                if (f.isFile() && !n.endsWith(".part") && !n.endsWith(".ytdl") && !n.endsWith(".temp")) files.add(f);
            }
        }
        if (files.isEmpty()) { fail(id, r.optString("name"), "yt-dlp finished but produced no file"); return; }

        final String folder = r.optString("folder");
        Map<String, Uri> dirs = new HashMap<>();
        String first = "";
        long bytes = 0;
        try {
            for (File f : files) {
                String uri = Folders.publish(svc, f, f.getName(), folder, Folders.categoryFolder(svc, "video", f.getName()), dirs);
                if (first.isEmpty()) first = uri;
                bytes += f.length();
            }
        } catch (Exception e) {
            // the video is safe in the app's own folder; say so instead of pretending it worked
            fail(id, r.optString("name"), "Downloaded, but could not save to the chosen folder: " + e.getMessage());
            return;
        }
        final String name = files.get(0).getName();
        final String openUri = first;
        final long size = bytes;
        deleteTree(dir);
        patch(id, p -> p.put("state", "completed").put("name", name).put("openUri", openUri)
            .put("total", size).put("done", size).put("speed", 0).put("eta", -1));
        svc.alert("Download complete", name);
    }

    private void fail(String id, String name, String message) {
        try {
            patch(id, p -> p.put("state", "failed").put("error", message).put("speed", 0).put("eta", -1));
        } catch (Exception ignored) {}
        svc.alert("Download failed", name + " · " + message);
    }

    // yt-dlp prints the whole log on failure; the useful part is the last "ERROR:" line.
    private static String explain(Exception e) {
        String text = e.getMessage() == null ? e.toString() : e.getMessage();
        String best = "";
        for (String line : text.split("\n")) {
            if (line.contains("ERROR:")) best = line.substring(line.indexOf("ERROR:") + 6).trim();
        }
        if (best.isEmpty()) best = text.trim();
        String low = best.toLowerCase();
        if (low.contains("login") || low.contains("cookies") || low.contains("sign in")) best += " Import cookies.txt in Settings.";
        return best.length() > 220 ? best.substring(0, 220) + "…" : best;
    }

    // ------------------------------------------------------------ helpers

    private interface Fill { void apply(JSONObject p) throws Exception; }

    private void patch(String id, Fill fill) throws Exception {
        Store.mutate(svc, all -> {
            JSONObject cur = Store.find(all, id);
            if (cur == null) return;
            JSONObject p = new JSONObject();
            fill.apply(p);
            java.util.Iterator<String> keys = p.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                cur.put(k, p.get(k));
            }
        });
    }

    private File jobDir(String id) { return new File(svc.stageDir(), "video-" + id); }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
