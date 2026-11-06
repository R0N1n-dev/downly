package com.devn1n.downly;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

// Reads a public post page for the photo picker. The parsing itself lives in the web bundle
// (src/lib/photos.js), so a markup change ships with the UI instead of a new APK. This class only has to
// look like a desktop browser, reuse the imported cookies.txt session when there is one, and stay small.
final class Photos {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private Photos() {}

    // -> { html, url } where url is the final address after redirects (relative links resolve against it)
    static JSONObject page(String url, File cookiesFile) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(8000);
        c.setReadTimeout(9000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        c.setRequestProperty("Accept-Encoding", "identity"); // plain text for the bridge, no gzip
        String cookie = cookieHeader(cookiesFile, url);
        if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("The page answered " + code);
            String type = c.getContentType() == null ? "" : c.getContentType();
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
                byte[] buf = new byte[32 * 1024];
                int n, total = 0;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total >= MAX_BYTES) break;
                }
                String html = new String(out.toByteArray(), charset(type));
                return new JSONObject().put("html", html).put("url", c.getURL().toString());
            }
        } finally {
            c.disconnect();
        }
    }

    private static Charset charset(String contentType) {
        int at = contentType.toLowerCase(Locale.ROOT).indexOf("charset=");
        if (at >= 0) {
            String name = contentType.substring(at + 8).split("[;\\s]")[0].trim();
            try { return Charset.forName(name); } catch (Exception ignored) {}
        }
        return StandardCharsets.UTF_8;
    }

    // The cookies.txt imported in Settings is a Netscape file. yt-dlp already uses it for logged-in sites;
    // the photo reader can use the same session. Nothing here acquires cookies on its own.
    private static String cookieHeader(File file, String pageUrl) {
        if (file == null || !file.isFile()) return "";
        String host;
        try {
            host = new URL(pageUrl).getHost();
        } catch (Exception e) {
            return "";
        }
        if (host == null) return "";
        host = host.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        long now = System.currentTimeMillis() / 1000;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\t");
                if (parts.length < 7) continue;
                String domain = parts[0].toLowerCase(Locale.ROOT);
                if (domain.startsWith(".")) domain = domain.substring(1);
                if (domain.isEmpty()) continue;
                if (!host.equals(domain) && !host.endsWith("." + domain)) continue;
                String expiry = parts[4].trim();
                try {
                    long at = Long.parseLong(expiry);
                    if (at > 0 && at < now) continue; // expired
                } catch (Exception ignored) {}
                if (out.length() > 0) out.append("; ");
                out.append(parts[5]).append('=').append(parts[6]);
                if (out.length() > 8000) break;
            }
        } catch (Exception ignored) {}
        return out.toString();
    }
}
