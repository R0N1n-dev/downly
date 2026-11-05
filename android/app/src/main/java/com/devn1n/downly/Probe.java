package com.devn1n.downly;

import android.net.Uri;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Works out what a link is before anything is downloaded: a video page, a plain file (name, size, category),
// a web page that might hold a video, or a torrent. Used by the in-app Add sheet and the share sheet.
final class Probe {
    private static final Pattern VIDEO_HOST = Pattern.compile(
        "(^|\\.)(instagram\\.com|tiktok\\.com|youtube\\.com|youtu\\.be|facebook\\.com|fb\\.watch|twitter\\.com|x\\.com|vimeo\\.com|reddit\\.com|twitch\\.tv|dailymotion\\.com|pinterest\\.com|pin\\.it|snapchat\\.com|bilibili\\.com)$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DISPOSITION = Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private Probe() {}

    static boolean isVideoHost(String url) {
        try { return VIDEO_HOST.matcher(Uri.parse(url).getHost()).find(); } catch (Exception e) { return false; }
    }

    // kind: video | file | page | torrent | magnet | unknown. size is -1 when the server did not say.
    static JSONObject of(String url) throws Exception {
        JSONObject out = new JSONObject().put("kind", "unknown").put("name", "").put("size", -1).put("category", Categories.OTHER);
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("magnet:")) return out.put("kind", "magnet").put("category", Categories.TORRENTS);
        if (isVideoHost(url)) return out.put("kind", "video").put("category", Categories.VIDEO);
        if (!lower.startsWith("https://")) return out; // plain http is not allowed for the app's own requests

        HttpURLConnection c = open(url, "HEAD");
        int code = c.getResponseCode();
        if (code == 405 || code == 403 || code == 501) { // some servers refuse HEAD: ask for one byte instead
            c.disconnect();
            c = open(url, "GET");
            c.setRequestProperty("Range", "bytes=0-0");
            code = c.getResponseCode();
        }
        String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
        long size = c.getContentLengthLong();
        String range = c.getHeaderField("Content-Range"); // "bytes 0-0/12345"
        if (range != null && range.contains("/")) { try { size = Long.parseLong(range.substring(range.indexOf('/') + 1).trim()); } catch (Exception ignored) {} }
        String name = nameOf(c, url, type);
        c.disconnect();

        out.put("size", size).put("name", name).put("mime", type);
        if (type.startsWith("text/html") || type.contains("xhtml")) return out.put("kind", "page");
        if (type.contains("bittorrent") || name.toLowerCase(Locale.ROOT).endsWith(".torrent")) return out.put("kind", "torrent").put("category", Categories.TORRENTS);
        return out.put("kind", "file").put("category", Categories.ofFile(name));
    }

    private static HttpURLConnection open(String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Downly");
        return c;
    }

    private static String nameOf(HttpURLConnection c, String url, String type) {
        String disp = c.getHeaderField("Content-Disposition");
        if (disp != null) {
            Matcher m = DISPOSITION.matcher(disp);
            if (m.find()) { try { return URLDecoder.decode(m.group(1).trim(), "UTF-8"); } catch (Exception e) { return m.group(1).trim(); } }
        }
        String seg = Uri.parse(c.getURL().toString()).getLastPathSegment();
        if (seg == null || seg.isEmpty()) seg = Uri.parse(url).getLastPathSegment();
        if (seg == null || seg.isEmpty()) seg = "download";
        if (Categories.ext(seg).isEmpty() && !type.isEmpty()) { // no extension in the URL: borrow it from the content type
            String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type.split(";")[0].trim());
            if (ext != null) seg += "." + ext;
        }
        return seg;
    }
}
