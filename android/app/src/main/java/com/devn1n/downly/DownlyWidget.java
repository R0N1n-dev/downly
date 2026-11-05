package com.devn1n.downly;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Home screen widget: live progress of the first two downloads, a "Paste link" button that opens the quick add
// sheet over the home screen, and an "Add" button for typing a link.
public class DownlyWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) { refresh(c); }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, DownlyWidget.class));
            if (ids.length == 0) return; // no widget on any home screen: do no work
            m.updateAppWidget(ids, build(c));
        } catch (Exception ignored) {}
    }

    private static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_downly);

        List<JSONObject> live = new ArrayList<>();
        long speed = 0;
        int running = 0;
        JSONArray all = Store.read(c);
        for (int i = 0; i < all.length(); i++) {
            JSONObject r = all.optJSONObject(i);
            if (r == null || Store.isFinal(r) || r.optString("want").equals("remove")) continue;
            live.add(r);
            if (r.optString("state").equals("downloading")) { running++; speed += r.optLong("speed"); }
        }
        // downloading first, then waiting, then paused
        live.sort((a, b) -> rank(a) - rank(b));

        v.setTextViewText(R.id.w_status, running == 0 ? (live.isEmpty() ? "Idle" : live.size() + " waiting") : running + " active · " + rate(speed));
        int[][] ids = {{R.id.w_row1, R.id.w_name1, R.id.w_bar1, R.id.w_pct1}, {R.id.w_row2, R.id.w_name2, R.id.w_bar2, R.id.w_pct2}};
        for (int i = 0; i < 2; i++) {
            if (i < live.size()) {
                JSONObject r = live.get(i);
                long total = r.optLong("total"), done = r.optLong("done");
                int pct = total > 0 ? (int) Math.min(100, done * 100 / total) : 0;
                boolean paused = r.optString("state").equals("paused");
                v.setViewVisibility(ids[i][0], android.view.View.VISIBLE);
                v.setInt(ids[i][0], "setBackgroundResource", card(r));
                v.setTextViewText(ids[i][1], r.optString("name"));
                v.setProgressBar(ids[i][2], 100, pct, total <= 0 && !paused);
                v.setTextViewText(ids[i][3], paused ? "Paused" : total > 0 ? pct + "%" : "…");
            } else {
                v.setViewVisibility(ids[i][0], android.view.View.GONE);
            }
        }
        v.setViewVisibility(R.id.w_empty, live.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        v.setViewVisibility(R.id.w_more, live.size() > 2 ? android.view.View.VISIBLE : android.view.View.GONE);
        if (live.size() > 2) v.setTextViewText(R.id.w_more, "+" + (live.size() - 2) + " more");

        v.setOnClickPendingIntent(R.id.w_paste, quick(c, QuickAddActivity.ACTION_PASTE, 1));
        v.setOnClickPendingIntent(R.id.w_add, quick(c, QuickAddActivity.ACTION_ADD, 2));
        v.setOnClickPendingIntent(R.id.w_header, PendingIntent.getActivity(c, 3,
            new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    private static PendingIntent quick(Context c, String action, int code) {
        Intent i = new Intent(c, QuickAddActivity.class).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static int rank(JSONObject r) {
        switch (r.optString("state")) { case "downloading": return 0; case "queued": return 1; default: return 2; }
    }

    // same pastel per file type as the app
    private static int card(JSONObject r) {
        String cat = r.optString("type").equals("video") ? (r.optString("mode").equals("audio") ? Categories.MUSIC : Categories.VIDEO)
            : Categories.of(r.optString("type"), r.optString("name"));
        switch (cat) {
            case Categories.VIDEO: return R.drawable.w_card_coral;
            case Categories.MUSIC: return R.drawable.w_card_pink;
            case Categories.DOCUMENTS: return R.drawable.w_card_mint;
            case Categories.COMPRESSED: return R.drawable.w_card_butter;
            case Categories.PROGRAMS: return R.drawable.w_card_sky;
            case Categories.IMAGES: return R.drawable.w_card_teal;
            case Categories.TORRENTS: return R.drawable.w_card_peach;
            default: return R.drawable.w_card_gray;
        }
    }

    private static String rate(long bytesPerSecond) {
        double v = bytesPerSecond / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.0f KB/s", v);
        return String.format(Locale.US, "%.1f MB/s", v / 1024);
    }
}
