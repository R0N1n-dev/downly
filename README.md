# Downly

Vue 3 + Capacitor 8 download manager with a bundled aria2 engine.

Needs Node 22+, JDK 21, Android Studio / Android SDK 36.

    npm install
    npm run apk      # builds android/app/build/outputs/apk/debug/app-debug.apk
    npm run sync     # or sync only, then run from Android Studio

Android 10+ only.

`npm run dev` runs the UI in a desktop browser against an empty stand-in for the native plugin (no sample data, nothing downloads), so you can iterate on the design without a phone.

## How it works

- `android/app/src/main/jniLibs/<abi>/libaria2c.so` are static Android builds of aria2 1.37.0
  (from Zackptg5/Cross-Compiled-Binaries-Android). Replace them with your own builds if you prefer.
- `Aria2Service` (foreground service) runs aria2c on a random localhost port with a random secret.
  Once a second it makes aria2 match the list in `Store` (`downloads.json`): adds, pauses, resumes,
  removes, copies finished files to the chosen folder (or public Downloads) and posts notifications.
  The list is the source of truth, so unfinished downloads are re-added after a restart and resume from
  aria2's partial files. The service stops itself when nothing needs the engine.
- Video pages (Instagram, TikTok, YouTube, ...) are downloaded by yt-dlp via `VideoJobs` (youtubedl-android, bundled Python + ffmpeg), independent of aria2. Pick quality in the Add sheet. Instagram needs a cookies.txt imported in Settings.
- Errors from the engine show in the app with a Retry button.

## Themes

`src/theme.js` holds Sorbet (default) and the nine palettes from `app-theme.txt`, each in light and dark. `npm run check:theme` fails if any text, button or tab pill drops below 4.5:1 contrast.

## Sidebar

Hamburger in every header. It switches screens and filters the lists by status (Downloading, Queued, Paused, Completed, Failed) and by file type.

## v11 notes

- **arm64 only** (`ndk.abiFilters`), so aria2, Python and ffmpeg ship for phones only.
- **Category folders.** Finished files go to `Downloads/Downly/<Video|Music|Documents|Compressed|Programs|Images|Torrents|Other>`
  (or the same names inside the folder you pick in Settings). The folders are created on first launch.
- **Video engine warm-up.** `DownlyApp` unpacks Python, yt-dlp and ffmpeg in the background at app start (`VideoEngine`).
- **Share sheet and widget.** Sharing a link to Downly opens `QuickAddActivity`, a bottom sheet over the app you shared from.
  The home screen widget (`DownlyWidget`) has a Paste link button that opens the same sheet.
- **Edge-to-edge.** `MainActivity` pads the content by the system bar, cut-out and keyboard insets.
- **Icon.** `public/icon.svg` is the source. Adaptive launcher icon, monochrome icon, splash and notification glyph are generated from it.
- **Fonts.** Plus Jakarta Sans, Outfit and Geist are bundled. Satoshi and Cabinet Grotesk are on Fontshare, not npm: see `public/fonts/README.txt`.
