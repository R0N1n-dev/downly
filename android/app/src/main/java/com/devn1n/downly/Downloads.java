package com.devn1n.downly;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.util.Locale;

// One place that turns a link into a stored download and wakes the service. Used by the app and the share sheet.
final class Downloads {
    private Downloads() {}

    static boolean valid(String uri) {
        String l = uri.toLowerCase(Locale.ROOT);
        return l.startsWith("magnet:") || l.startsWith("http://") || l.startsWith("https://") || l.startsWith("ftp://");
    }

    // mode: video | audio (video pages only). quality: best | 1080 | 720 | 480. audioFormat: mp3 | m4a | opus | flac | wav
    static JSONObject add(Context c, String uri, boolean video, String mode, String quality, String audioFormat, String bitrate) throws Exception {
        uri = uri.trim();
        if (!valid(uri)) throw new Exception("Enter an http, https, ftp or magnet link");
        boolean magnet = uri.toLowerCase(Locale.ROOT).startsWith("magnet:");
        video = video && !magnet;
        String host = Uri.parse(uri).getHost();
        String name = magnet ? magnetName(uri) : video ? "Video from " + (host == null ? "the web" : host) : urlName(uri);
        JSONObject r = Store.create(Store.newId(), name, magnet ? "magnet" : video ? "video" : "http", uri,
            c.getSharedPreferences("downly", Context.MODE_PRIVATE).getString("folder", ""));
        if (video) {
            r.put("mode", "audio".equals(mode) ? "audio" : "video");
            r.put("quality", quality == null ? "best" : quality);
            r.put("audioFormat", audioFormat == null ? "mp3" : audioFormat);
            r.put("bitrate", bitrate == null ? "192K" : bitrate);
        }
        final JSONObject rec = r;
        Store.mutate(c, all -> all.put(rec));
        start(c);
        return r;
    }

    static void start(Context c) {
        c.getSharedPreferences("downly", Context.MODE_PRIVATE).edit().remove("engine_error").apply();
        c.startForegroundService(new Intent(c, Aria2Service.class));
        DownlyWidget.refresh(c);
    }

    static String urlName(String uri) {
        String p = Uri.parse(uri).getLastPathSegment();
        return p == null || p.isEmpty() ? uri : p;
    }

    // magnet: URIs are opaque, so Uri.getQueryParameter() throws on them. Parse dn= by hand.
    static String magnetName(String uri) {
        int q = uri.indexOf('?');
        if (q >= 0) {
            for (String part : uri.substring(q + 1).split("&")) {
                if (part.startsWith("dn=") && part.length() > 3) {
                    try { return java.net.URLDecoder.decode(part.substring(3), "UTF-8"); } catch (Exception ignored) {}
                }
            }
        }
        return "Magnet link";
    }
}
