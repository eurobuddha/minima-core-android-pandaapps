package org.minimarex.pandaapps;

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
}
