package com.devn1n.downly;

import android.app.Application;
import android.content.SharedPreferences;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// yt-dlp needs Python and ffmpeg unpacked on first use (several seconds). That now happens in the background
// at app start, so by the time a video is added the engine is usually ready. Downloads share a read lock;
// the daily yt-dlp update takes the write lock so it never swaps files under a running download.
final class VideoEngine {
    enum State { IDLE, PREPARING, READY, ERROR }

    private static volatile State state = State.IDLE;
    private static volatile String error = "";
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
    private static final long DAY = 24L * 60 * 60 * 1000;

    private VideoEngine() {}

    static State state() { return state; }
    static String error() { return error; }

    // non-blocking, safe to call repeatedly
    static synchronized void warmUp(Application app) {
        if (state == State.PREPARING || state == State.READY) return;
        state = State.PREPARING;
        Thread t = new Thread(() -> {
            try {
                YoutubeDL.getInstance().init(app);
                FFmpeg.getInstance().init(app);
                state = State.READY;
                error = "";
                updateIfStale(app);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                state = State.ERROR;
            }
        }, "video-engine-warmup");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // run `job` once the engine is ready (waits for a warm-up already in progress)
    static <T> T run(Application app, Callable<T> job) throws Exception {
        warmUp(app);
        while (state == State.PREPARING) Thread.sleep(150);
        if (state != State.READY) throw new Exception("Video engine is not available: " + error);
        LOCK.readLock().lock();
        try { return job.call(); } finally { LOCK.readLock().unlock(); }
    }

    private static void updateIfStale(Application app) {
        SharedPreferences prefs = app.getSharedPreferences("downly", Application.MODE_PRIVATE);
        if (System.currentTimeMillis() - prefs.getLong("ytdlp_updated", 0) < DAY) return;
        LOCK.writeLock().lock();
        try {
            YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel._STABLE);
            prefs.edit().putLong("ytdlp_updated", System.currentTimeMillis()).apply();
        } catch (Exception ignored) {
            // offline or rate limited: keep the bundled version, try again next launch
        } finally {
            LOCK.writeLock().unlock();
        }
    }
}
