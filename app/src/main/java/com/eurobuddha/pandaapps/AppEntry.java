package com.eurobuddha.pandaapps;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** One catalog app, parsed from an apks.json entry. */
public class AppEntry {
    public String name;
    public String packageId;
    public String version;       // versionName in the catalog
    public long versionCode;     // drives Update detection
    public String description;
    public String category;
    public String source;        // "PandaApps" / "Official"
    public String icon;          // icon URL
    public String file;          // APK download URL
    public String sha256;        // lowercase hex SHA-256 of the APK (optional; verified if present)
    public String repo;          // public source repository (optional; shown as "View source code")

    public static AppEntry from(JSONObject o) {
        AppEntry a = new AppEntry();
        a.name        = o.optString("name", "App");
        a.packageId   = o.optString("packageId", "");
        a.version     = o.optString("version", "");
        a.versionCode = o.optLong("versionCode", 0);
        a.description = o.optString("description", "");
        a.category    = o.optString("category", "");
        a.source      = o.optString("source", "");
        a.icon        = o.optString("icon", "");
        a.file        = o.optString("file", "");
        a.sha256      = o.optString("sha256", "").trim().toLowerCase();
        a.repo        = o.optString("repo", "").trim();
        return a;
    }

    /**
     * Serialise for the hand-off to the detail screen (Intent extras carry a string, not an object).
     *
     * This writes the CURRENT field values on purpose: {@link Catalog} rewrites file/icon through
     * absUrl() when the catalog was served by an IPFS gateway, and the detail screen needs those
     * resolved URLs. Passing the raw catalog JSON instead would hand it relative paths and break
     * icons and downloads whenever GitHub is unreachable and the IPFS fallback is serving.
     */
    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("packageId", packageId);
            o.put("version", version);
            o.put("versionCode", versionCode);
            o.put("description", description);
            o.put("category", category);
            o.put("source", source);
            o.put("icon", icon);
            o.put("file", file);
            o.put("sha256", sha256);
            o.put("repo", repo);
        } catch (JSONException ignored) {}
        return o;
    }

    /** A non-.apk entry (an AI skill zip, a desktop build) is a plain download, never an install. */
    public boolean isApk() {
        return file != null && file.toLowerCase(Locale.US).endsWith(".apk");
    }

    /** A stable file name for the cached download. */
    public String cacheName() {
        String base = packageId.isEmpty() ? name.replaceAll("[^A-Za-z0-9]", "") : packageId;
        return base + "-" + (version.isEmpty() ? "latest" : version) + ".apk";
    }
}
