package com.devn1n.downly;

import android.app.Application;
import android.content.SharedPreferences;

public class DownlyApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        VideoEngine.warmUp(this); // unpack Python/ffmpeg now, not when the first video is added
        SharedPreferences prefs = getSharedPreferences("downly", MODE_PRIVATE);
        if (!prefs.getBoolean("folders_made", false)) { // first launch after install: create the category folders
            new Thread(() -> {
                Folders.ensureAll(this);
                prefs.edit().putBoolean("folders_made", true).apply();
            }, "folders-setup").start();
        }
    }
}
