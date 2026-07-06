package com.eurobuddha.pandaapps;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Fetches and parses the apks.json catalog over HTTPS on a worker thread. */
public final class Catalog {

    // The GitHub API contents endpoint reflects the latest commit within seconds (ETag-based), so a freshly
    // published release shows up immediately. The raw.githubusercontent CDN caches ~5 min and IGNORES query
    // cache-busters, so it's only a fallback (used if the API is unreachable / rate-limited).
    private static final String API_URL =
            "https://api.github.com/repos/eurobuddha/minima-core-apks/contents/apks.json";
    private static final String RAW_URL =
            "https://raw.githubusercontent.com/eurobuddha/minima-core-apks/main/apks.json";

    public interface Cb {
        void onCatalog(List<AppEntry> apps);
        void onError(String message);
    }

    private Catalog() {}

    public static void fetch(Cb cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String json;
            try {
                json = get(API_URL, true);          // primary: near-real-time
            } catch (Exception apiErr) {
                try { json = get(RAW_URL, false); }  // fallback: raw CDN (may lag ~5 min)
                catch (Exception rawErr) { main.post(() -> cb.onError(friendly(rawErr))); return; }
            }
            try {
                JSONObject root = new JSONObject(json);
                JSONArray arr = root.optJSONArray("apps");
                List<AppEntry> apps = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) apps.add(AppEntry.from(o));
                    }
                }
                main.post(() -> cb.onCatalog(apps));
            } catch (Exception e) {
                main.post(() -> cb.onError(friendly(e)));
            }
        }).start();
    }

    private static String get(String url, boolean api) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(10000);
        con.setInstanceFollowRedirects(true);
        con.setUseCaches(false);
        con.setRequestProperty("Cache-Control", "no-cache");
        con.setRequestProperty("Pragma", "no-cache");
        con.setRequestProperty("User-Agent", "PandaApps");                       // GitHub API rejects requests with no UA
        con.setRequestProperty("Accept", api ? "application/vnd.github.raw" : "application/json");
        int code = con.getResponseCode();
        if (code != 200) { con.disconnect(); throw new Exception((api ? "API" : "raw") + " HTTP " + code); }
        InputStream in = con.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        in.close();
        con.disconnect();
        return bos.toString("UTF-8");
    }

    private static String friendly(Exception e) {
        String m = e.getMessage();
        return "Couldn't load the app catalog" + (m == null ? "" : " (" + m + ")")
                + ". Check your connection and try Refresh.";
    }
}
