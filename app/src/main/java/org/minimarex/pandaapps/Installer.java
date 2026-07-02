package org.minimarex.pandaapps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;

/** Hands a downloaded APK to the system package installer, gating on the "install unknown apps" grant. */
public final class Installer {

    private Installer() {}

    /** Android 8+ requires a per-app "install unknown apps" grant before we can launch the installer. */
    public static boolean canInstall(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ctx.getPackageManager().canRequestPackageInstalls();
    }

    /** Send the user to the per-app "install unknown apps" toggle. */
    public static void requestPermission(Activity act) {
        try {
            act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + act.getPackageName())));
        } catch (Exception e) {
            try { act.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)); }
            catch (Exception ignored) {}
        }
    }

    /** Launch the system installer for the given APK (content:// URI via FileProvider). Returns
     *  false (rather than crashing) if no installer can handle the intent. */
    public static boolean install(Context ctx, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
