package org.minimarex.pandaapps;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Streams an APK to the cache with progress, verifying integrity before completing. Downloads to a
 * ".part" temp file and only renames it to the final name once the full length AND (when supplied)
 * the catalog SHA-256 check out — so a truncated or tampered download is never handed to the
 * installer. Follows redirects (GitHub release → CDN) manually.
 */
public final class ApkDownloader {

    public interface Cb {
        void onProgress(int percent);   // -1 if content length unknown
        void onComplete(File apk);
        void onError(String message);
    }

    private ApkDownloader() {}

    public static void download(final Context anyCtx, final AppEntry app, final Cb cb) {
        final Context ctx = anyCtx.getApplicationContext();   // don't hold the Activity for the download
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            HttpURLConnection con = null;
            File part = null;
            try {
                File dir = new File(ctx.getCacheDir(), "apks");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, app.cacheName());
                part = new File(dir, app.cacheName() + ".part");
                if (part.exists()) part.delete();

                String url = app.file;
                int redirects = 0;
                while (true) {
                    con = (HttpURLConnection) new URL(url).openConnection();
                    con.setConnectTimeout(12000);
                    con.setReadTimeout(20000);
                    con.setInstanceFollowRedirects(false);   // follow manually (https↔cross-host)
                    int code = con.getResponseCode();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        String loc = con.getHeaderField("Location");
                        con.disconnect();
                        if (loc == null || ++redirects > 6) throw new Exception("Too many redirects");
                        url = loc;
                        continue;
                    }
                    if (code != 200) throw new Exception("HTTP " + code);
                    break;
                }

                long total = con.getContentLengthLong();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                InputStream in = con.getInputStream();
                FileOutputStream fos = new FileOutputStream(part);
                byte[] buf = new byte[8192];
                int n; long got = 0; int lastPct = -2;
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    md.update(buf, 0, n);
                    got += n;
                    int pct = total > 0 ? (int) (got * 100 / total) : -1;
                    if (pct != lastPct) { lastPct = pct; final int p = pct; main.post(() -> cb.onProgress(p)); }
                }
                fos.flush(); fos.close(); in.close(); con.disconnect(); con = null;

                // --- integrity gates (a partial never becomes the final APK) ---
                if (got == 0) throw new Exception("Empty download");
                if (total > 0 && got != total)
                    throw new Exception("Incomplete (" + got + "/" + total + " bytes)");
                if (app.sha256 != null && !app.sha256.isEmpty()) {
                    String actual = hex(md.digest());
                    if (!actual.equalsIgnoreCase(app.sha256))
                        throw new Exception("Checksum mismatch — download may be corrupt or tampered");
                }

                if (out.exists()) out.delete();
                if (!part.renameTo(out)) {
                    // Fallback if rename across the same dir somehow fails.
                    throw new Exception("Could not finalise download");
                }
                final File done = out;
                main.post(() -> cb.onComplete(done));
            } catch (Exception e) {
                if (con != null) try { con.disconnect(); } catch (Exception ignored) {}
                if (part != null) try { part.delete(); } catch (Exception ignored) {}
                final String m = e.getMessage();
                main.post(() -> cb.onError("Download failed" + (m == null ? "" : ": " + m)));
            }
        }).start();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
