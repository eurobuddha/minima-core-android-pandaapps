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

    // IPFS fallbacks — keep the store working if GitHub is unreachable (outage / blocked).
    // The IPFS snapshot's apks.json carries RELATIVE file/icon paths, so entries loaded from an
    // IPFS source are resolved against that gateway's base. Own gateway first (fast — DNSLink is
    // instant on the publisher), then a public gateway (survives the Pi / home line being down).
    private static final String IPFS_OWN_BASE    = "https://ipfs.eurobuddha.com/";
    private static final String IPFS_PUBLIC_BASE = "https://ipfs.io/ipns/ipfs.eurobuddha.com/";

    /** A catalog source: where to GET it, whether it's the GitHub API, and the base to resolve
     *  relative file/icon paths against ("" = entries are already absolute, e.g. GitHub). */
    private static final class Src {
        final String url; final boolean api; final String base;
        Src(String url, boolean api, String base) { this.url = url; this.api = api; this.base = base; }
    }

    private static final Src[] SOURCES = new Src[] {
        new Src(API_URL, true,  ""),                                            // GitHub API — near-real-time
        new Src(RAW_URL, false, ""),                                            // GitHub raw CDN — ~5 min lag
        new Src(IPFS_OWN_BASE    + "apks/apks.json", false, IPFS_OWN_BASE),     // own IPFS gateway
        new Src(IPFS_PUBLIC_BASE + "apks/apks.json", false, IPFS_PUBLIC_BASE)   // public IPFS gateway
    };

    public interface Cb {
        void onCatalog(List<AppEntry> apps, String disclaimer);
        void onError(String message);
    }

    private Catalog() {}

    public static void fetch(Cb cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String json = null, base = "";
            Exception last = null;
            for (Src s : SOURCES) {                       // first source that answers wins
                try { json = get(s.url, s.api); base = s.base; last = null; break; }
                catch (Exception e) { last = e; }         // try the next fallback
            }
            if (json == null) { final Exception fe = last; main.post(() -> cb.onError(friendly(fe))); return; }
            final String fbase = base;
            try {
                JSONObject root = new JSONObject(json);
                final String disclaimer = root.optString("disclaimer", "");   // store-wide dev/use-at-own-risk notice
                JSONArray arr = root.optJSONArray("apps");
                List<AppEntry> apps = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) continue;
                        AppEntry a = AppEntry.from(o);
                        a.file = absUrl(fbase, a.file);   // no-op for absolute GitHub URLs; resolves IPFS-relative paths
                        a.icon = absUrl(fbase, a.icon);
                        apps.add(a);
                    }
                }
                final List<AppEntry> fapps = apps;
                main.post(() -> cb.onCatalog(fapps, disclaimer));
            } catch (Exception e) {
                main.post(() -> cb.onError(friendly(e)));
            }
        }).start();
    }

    /** Resolve a possibly-relative catalog URL against the source gateway base. Absolute URLs
     *  (http/https/data:/ipfs:) are returned unchanged; blanks pass through. */
    private static String absUrl(String base, String u) {
        if (u == null || u.isEmpty()) return u;
        if (u.startsWith("http://") || u.startsWith("https://")
                || u.startsWith("data:") || u.startsWith("ipfs://")) return u;
        if (base == null || base.isEmpty()) return u;
        return base + (u.startsWith("/") ? u.substring(1) : u);
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
