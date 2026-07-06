package com.eurobuddha.pandaapps;

import org.json.JSONObject;

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
        return a;
    }

    /** A stable file name for the cached download. */
    public String cacheName() {
        String base = packageId.isEmpty() ? name.replaceAll("[^A-Za-z0-9]", "") : packageId;
        return base + "-" + (version.isEmpty() ? "latest" : version) + ".apk";
    }
}
