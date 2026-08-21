package com.eurobuddha.pandaapps;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Copies a verified APK from the cache into the user's public Downloads via MediaStore. This is
 * the download-only (PandaGet) hand-off: with no REQUEST_INSTALL_PACKAGES the store cannot open
 * the system installer, so completion means "the file is in Downloads — tap it in your Files app".
 *
 * The bytes are already SHA-256-checked by {@link ApkDownloader} before this runs; this is a plain
 * byte copy, never a second download — which is exactly why PandaGet routes through the cache
 * pipeline instead of DownloadManager, whose fetch would be unverified.
 *
 * Copying a multi-MB file through a ContentResolver stream is too slow for the main thread (the
 * ApkDownloader callback arrives there), so the copy runs on its own thread and calls back on main,
 * mirroring ApkDownloader. Needs API 29 (MediaStore.Downloads) — the pandaget flavor's minSdk; the
 * full flavor (minSdk 28) compiles this but never calls it.
 */
public final class ApkExporter {

    public interface Cb {
        void onSaved();
        void onError(String message);
    }

    private ApkExporter() {}

    public static void save(Context anyCtx, final AppEntry app, final File apk, final Cb cb) {
        final Context ctx = anyCtx.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                main.post(() -> cb.onError("Saving to Downloads needs Android 10 or newer"));
                return;
            }
            try {
                doSave(ctx, app, apk);
                main.post(cb::onSaved);
            } catch (Exception e) {
                final String m = e.getMessage();
                main.post(() -> cb.onError("Couldn't save to Downloads" + (m == null ? "" : ": " + m)));
            }
        }).start();
    }

    /** True if a finished copy of this exact version is already sitting in Downloads (ours only —
     *  MediaStore scopes both the query and the "Saved" claim to rows this app created). */
    public static boolean alreadySaved(Context ctx, AppEntry app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try (Cursor c = ctx.getContentResolver().query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Downloads._ID},
                MediaStore.Downloads.DISPLAY_NAME + "=?",
                new String[]{app.cacheName()}, null)) {
            // Pending (half-written) rows are filtered out of queries by default, so a hit here
            // means a completed export.
            return c != null && c.getCount() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static void doSave(Context ctx, AppEntry app, File apk) throws Exception {
        ContentResolver cr = ctx.getContentResolver();

        // Replace our own earlier copy so re-downloads don't pile up "name (1).apk" duplicates.
        // Rows created before an uninstall/reinstall no longer belong to us and can't be deleted;
        // MediaStore then uniquifies the new name — cosmetic, accepted.
        try {
            cr.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    MediaStore.Downloads.DISPLAY_NAME + "=?", new String[]{app.cacheName()});
        } catch (Exception ignored) {}

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, app.cacheName());
        cv.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
        cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        cv.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri row = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (row == null) throw new Exception("Couldn't create the Downloads entry");
        try {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = cr.openOutputStream(row)) {
                if (out == null) throw new Exception("Couldn't write the Downloads entry");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            cr.update(row, done, null, null);
        } catch (Exception e) {
            // Never leave a half-written pending row behind.
            try { cr.delete(row, null, null); } catch (Exception ignored) {}
            throw e;
        }
    }
}
