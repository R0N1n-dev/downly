package com.devn1n.downly;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// The bottom sheet that appears over whatever app you are in: when a link is shared to Downly, or from the
// home screen widget. It looks at the link, shows what it is (file, video, torrent) with only the options that
// matter, and starts the download without opening the app.
public class QuickAddActivity extends Activity {
    static final String ACTION_PASTE = "com.devn1n.downly.PASTE";
    static final String ACTION_ADD = "com.devn1n.downly.ADD";
    private static final Pattern URL = Pattern.compile("(https?://|ftp://|magnet:)\\S+", Pattern.CASE_INSENSITIVE);

    private final Handler ui = new Handler(Looper.getMainLooper());
    private View sheet, scrim;
    private EditText input;
    private TextView detect, dest, go, open;
    private LinearLayout options;
    private Runnable pendingProbe;
    private int probeSeq;
    private boolean pasteDone, closing;

    // what the current link is: none | magnet | video | file | page | torrent | unknown
    private String kind = "none";
    private boolean treatAsVideo;
    private String mode = "video", quality = "best", audioFormat = "mp3", bitrate = "192K";
    private JSONObject info;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.quick_add);
        sheet = findViewById(R.id.qa_sheet);
        scrim = findViewById(R.id.qa_scrim);
        input = findViewById(R.id.qa_url);
        detect = findViewById(R.id.qa_detect);
        dest = findViewById(R.id.qa_dest);
        go = findViewById(R.id.qa_go);
        open = findViewById(R.id.qa_open);
        options = findViewById(R.id.qa_options);

        scrim.setOnClickListener(v -> dismiss());
        findViewById(R.id.qa_root).setOnApplyWindowInsetsListener((v, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()).bottom
                : insets.getSystemWindowInsetBottom();
            sheet.setPadding(dp(20), dp(10), dp(20), dp(16) + bottom);
            return insets;
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { onLinkChanged(); }
        });
        go.setOnClickListener(v -> start());
        open.setOnClickListener(v -> openApp());

        String action = getIntent().getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            String link = firstLink(getIntent().getStringExtra(Intent.EXTRA_TEXT));
            if (link != null) input.setText(link);
        } else if (ACTION_ADD.equals(action)) {
            input.requestFocus();
            getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        onLinkChanged();

        // slide up
        scrim.setAlpha(0f);
        sheet.setTranslationY(dp(600));
        sheet.post(() -> {
            scrim.animate().alpha(1f).setDuration(220).start();
            sheet.animate().translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    // the clipboard can only be read once the window has focus
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !pasteDone && ACTION_PASTE.equals(getIntent().getAction())) {
            pasteDone = true;
            String link = null;
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) link = firstLink(String.valueOf(d.getItemAt(0).coerceToText(this)));
            if (link != null) input.setText(link);
            else detect.setText("No link on the clipboard. Type or paste one above.");
        }
    }

    @Override public void onBackPressed() { dismiss(); }

    private void dismiss() {
        if (closing) return;
        closing = true;
        scrim.animate().alpha(0f).setDuration(160).start();
        sheet.animate().translationY(sheet.getHeight() + dp(40)).setDuration(180).withEndAction(() -> { finish(); overridePendingTransition(0, 0); }).start();
    }

    // ------------------------------------------------------------ detection

    private void onLinkChanged() {
        String url = input.getText().toString().trim();
        treatAsVideo = false;
        info = null;
        probeSeq++;
        if (pendingProbe != null) ui.removeCallbacks(pendingProbe);

        if (!Downloads.valid(url)) kind = "none";
        else if (url.toLowerCase(Locale.ROOT).startsWith("magnet:")) kind = "magnet";
        else if (Probe.isVideoHost(url)) kind = "video";
        else {
            kind = "unknown"; // until the probe answers
            final int seq = probeSeq;
            pendingProbe = () -> new Thread(() -> {
                try {
                    JSONObject p = Probe.of(url);
                    ui.post(() -> { if (seq == probeSeq) { info = p; kind = p.optString("kind", "unknown"); render(); } });
                } catch (Exception e) {
                    ui.post(() -> { if (seq == probeSeq) { kind = "unknown"; info = null; render(); } });
                }
            }).start();
            ui.postDelayed(pendingProbe, 450);
        }
        render();
    }

    private boolean isVideo() { return kind.equals("video") || treatAsVideo; }

    private String category() {
        if (isVideo()) return mode.equals("audio") ? Categories.MUSIC : Categories.VIDEO;
        if (kind.equals("magnet") || kind.equals("torrent")) return Categories.TORRENTS;
        return info == null ? Categories.OTHER : info.optString("category", Categories.OTHER);
    }

    private void render() {
        String url = input.getText().toString().trim();
        boolean ok = Downloads.valid(url);
        go.setEnabled(ok);
        options.removeAllViews();
        detect.setTextColor(getColor(R.color.qa_muted));
        if (!ok) {
            detect.setText(url.isEmpty() ? "Files, magnets and video pages all work." : "Use an http, https, ftp or magnet link.");
            dest.setText("");
            return;
        }
        switch (kind) {
            case "magnet": detect.setText("Magnet link"); break;
            case "torrent": detect.setText("Torrent file"); break;
            case "video": detect.setText(treatAsVideo ? "Using the video engine." : "Video link detected."); break;
            case "file": detect.setText(info.optString("name") + sizeText(info.optLong("size", -1))); break;
            case "page": detect.setText("This looks like a web page, not a file."); break;
            default: detect.setText("Checking the link…");
        }
        if (kind.equals("page") || (kind.equals("unknown") && !treatAsVideo) || (kind.equals("page") && treatAsVideo)) {
            options.addView(row(chip("This page holds a video", treatAsVideo, v -> { treatAsVideo = !treatAsVideo; render(); })));
        }
        if (isVideo()) {
            options.addView(row(chip("Video", mode.equals("video"), v -> { mode = "video"; render(); }), chip("Audio", mode.equals("audio"), v -> { mode = "audio"; render(); })));
            if (mode.equals("video")) {
                String[][] q = {{"best", "Best"}, {"1080", "1080p"}, {"720", "720p"}, {"480", "480p"}};
                TextView[] chips = new TextView[q.length];
                for (int i = 0; i < q.length; i++) { final String key = q[i][0]; chips[i] = chip(q[i][1], quality.equals(key), v -> { quality = key; render(); }); }
                options.addView(row(chips));
            } else {
                String[] f = {"mp3", "m4a", "opus", "flac", "wav"};
                TextView[] chips = new TextView[f.length];
                for (int i = 0; i < f.length; i++) { final String key = f[i]; chips[i] = chip(key.toUpperCase(Locale.ROOT), audioFormat.equals(key), v -> { audioFormat = key; render(); }); }
                options.addView(row(chips));
                if (!audioFormat.equals("flac") && !audioFormat.equals("wav")) {
                    String[] br = {"128K", "192K", "320K"};
                    TextView[] bc = new TextView[br.length];
                    for (int i = 0; i < br.length; i++) { final String key = br[i]; bc[i] = chip(key.replace("K", " kbps"), bitrate.equals(key), v -> { bitrate = key; render(); }); }
                    options.addView(row(bc));
                }
            }
        }
        dest.setText("Saves to " + Folders.label(this, Folders.sorting(this) ? category() : ""));
    }

    private static String sizeText(long b) {
        if (b <= 0) return "";
        double v = b / 1024.0; String u = "KB";
        if (v >= 1024) { v /= 1024; u = "MB"; }
        if (v >= 1024) { v /= 1024; u = "GB"; }
        return String.format(Locale.US, v >= 100 ? " · %.0f %s" : " · %.1f %s", v, u);
    }

    // ------------------------------------------------------------ actions

    private void start() {
        final String url = input.getText().toString().trim();
        final boolean video = isVideo();
        go.setEnabled(false);
        new Thread(() -> {
            try {
                Downloads.add(this, url, video, mode, quality, audioFormat, bitrate);
                ui.post(() -> { Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show(); dismiss(); });
            } catch (Exception e) {
                ui.post(() -> { detect.setText(e.getMessage()); detect.setTextColor(getColor(R.color.qa_error)); go.setEnabled(true); });
            }
        }).start();
    }

    private void openApp() {
        DownlyPlugin.remember(input.getText().toString().trim());
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------------ small view helpers

    private TextView chip(String label, boolean selected, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setMinHeight(dp(44));
        t.setBackgroundResource(R.drawable.qa_chip);
        t.setTextColor(getColorStateList(R.color.qa_chip_text));
        t.setSelected(selected);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private View row(TextView... chips) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView c : chips) row.addView(c);
        s.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(10), 0, 0);
        s.setLayoutParams(lp);
        return s;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static String firstLink(String text) {
        if (text == null) return null;
        Matcher m = URL.matcher(text);
        return m.find() ? m.group() : null;
    }
}
