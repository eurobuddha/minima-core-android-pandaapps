package com.eurobuddha.pandaapps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;

/** Reads installed-app state to decide Install / Update / Open. */
public final class PackageUtil {

    private PackageUtil() {}

    /** Installed versionCode, or -1 if the package isn't installed. */
    public static long installedVersionCode(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return -1;
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String installedVersionName(Context ctx, String pkg) {
        try { return ctx.getPackageManager().getPackageInfo(pkg, 0).versionName; }
        catch (Exception e) { return null; }
    }

    public static Intent launchIntent(Context ctx, String pkg) {
        return ctx.getPackageManager().getLaunchIntentForPackage(pkg);
    }

    // ---------------------------------------------------------------- signing

    /**
     * SHA-256 of the Minima Family signing certificate — every app we publish carries it.
     * Check any of our APKs against it with:
     *     apksigner verify --print-certs <apk>   ->  "certificate SHA-256 digest"
     */
    private static final String FAMILY_CERT_SHA256 =
            "eca1383c9d27683a281fbe6355356267877dc2dd14d963d7cc289ca0700e517f";

    /** SHA-256 of the installed package's current signing certificate, or null if unknown. */
    public static String installedCertSha256(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(
                    pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.SigningInfo si = pi.signingInfo;
            if (si == null) return null;
            android.content.pm.Signature[] sigs = si.getApkContentsSigners();
            if (sigs == null || sigs.length == 0) return null;
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * True when this app is installed under a DIFFERENT signing key than the one we publish, so
     * Android will refuse the update in place and report only a bare "App not installed".
     *
     * Everything we sign moved from the old debug key to the Minima Family key on 2026-08-11, so
     * anyone still on a pre-move build hits this on every update, with nothing on screen explaining
     * why. Detecting it lets the app say "uninstall the old version first" instead of failing at the
     * system installer after a full download.
     *
     * Only meaningful for apps we publish: "Official" entries are signed by their own publisher and
     * their certificate is not ours to predict, so they always answer false.
     */
    public static boolean needsReinstall(Context ctx, AppEntry app) {
        if (app == null || !app.isApk()) return false;
        if (!"PandaApps".equals(app.source)) return false;          // not ours to judge
        if (installedVersionCode(ctx, app.packageId) < 0) return false;  // not installed: fresh install is fine
        String cert = installedCertSha256(ctx, app.packageId);
        return cert != null && !FAMILY_CERT_SHA256.equalsIgnoreCase(cert);
    }

    /**
     * SECURITY (review 7 Nov 2026): the downloaded APK's sha256 comes from the same catalog as its URL, so a catalog
     * compromise (GitHub, the eurobuddha.com box, IPNS) could ship a look-alike signed with another key. Android only
     * blocks that for an in-place UPDATE; a fresh install would go through, and the user then gives it ADMIN on their
     * node. Before installing: the archive must be the package the row names, and anything in our com.eurobuddha.*
     * namespace (or listed as ours) must carry the Minima Family certificate. Returns the refusal, or null when fine.
     */
    public static String archiveSignerProblem(Context ctx, java.io.File apk, AppEntry app, String packageId) {
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        android.content.pm.PackageInfo pi = null;
        try { pi = pm.getPackageArchiveInfo(apk.getPath(), android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES); } catch (Exception ignored) {}
        android.content.pm.Signature[] sigs = null;
        if (pi != null && pi.signingInfo != null) sigs = pi.signingInfo.getApkContentsSigners();
        if (sigs == null || sigs.length == 0) {   // some releases leave signingInfo empty for archives
            try {
                @SuppressWarnings("deprecation") android.content.pm.PackageInfo old =
                        pm.getPackageArchiveInfo(apk.getPath(), android.content.pm.PackageManager.GET_SIGNATURES);
                if (old != null) { if (pi == null) pi = old; sigs = old.signatures; }
            } catch (Exception ignored) {}
        }
        String archivePkg = pi == null ? null : pi.packageName;
        boolean ours = (packageId != null && packageId.startsWith("com.eurobuddha."))
                || (archivePkg != null && archivePkg.startsWith("com.eurobuddha."))
                || (app != null && "PandaApps".equals(app.source));
        if (archivePkg == null) return ours ? "the downloaded file is not a readable app" : null;
        if (packageId != null && !packageId.isEmpty() && !packageId.equals(archivePkg))
            return "the download is " + archivePkg + ", not " + packageId;
        if (!ours) return null;
        if (sigs == null || sigs.length == 0) return "the download carries no signature";
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(sigs[0].toByteArray());
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte x : h) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
            if (sigs.length == 1 && FAMILY_CERT_SHA256.equalsIgnoreCase(sb.toString())) return null;
            return "the download is not signed with the Minima Family key (" + sb + ")";
        } catch (Exception e) { return "the download's signature could not be checked"; }
    }

    /** The system uninstall prompt for a package. */
    public static Intent uninstallIntent(String pkg) {
        return new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + pkg));
    }
}
