package com.eurobuddha.pandaapps;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs a downloaded APK through a {@link PackageInstaller} session WE OWN, and reports the outcome.
 *
 * WHY NOT THE OLD WAY. This used to fire the legacy {@code ACTION_VIEW} install intent at
 * {@code com.google.android.packageinstaller} — deprecated since Android 7, and it yields NO callback of
 * any kind. {@code startActivity} returns the instant the intent launches, the resulting session belongs to
 * the system installer rather than to us, and so PandaApps could not tell success from failure from a
 * wedge. {@link Downloads} recorded {@code installing = "startActivity didn't throw"} and nothing could
 * ever clear it.
 *
 * That is not academic. On 2026-09-23 an S10+ sat on "Installing…" indefinitely for two different apps:
 * both sessions were committed and sealed at {@code mProgress=0.9} with {@code mFinalStatus=0}, waiting on
 * a Play Protect verification that had timed out and cancelled itself. No popup, no error, nothing the
 * store could report — and twelve earlier sessions on that device carried the same signature, so store
 * installs had been silently at risk for a long time. We could not even abandon it: the session was not
 * ours ({@code SecurityException: Caller has no access to session}).
 *
 * With a session we own we get {@link PackageInstaller#EXTRA_STATUS} back, we can surface the real failure
 * message, we can {@link #abandon} on a timeout, and the UI can never hang forever. Whether this also
 * avoids the underlying wedge is NOT guaranteed — but a visible, recoverable failure is strictly better
 * than a silent permanent one.
 */
public final class Installer {

    /** Nothing may sit in "Installing…" longer than this before it is abandoned and reported. The wedge
     *  that prompted this had sat for over eight minutes with no status at all. */
    public static final long TIMEOUT_MS = 90_000L;

    private Installer() {}

    /** Android 8+ requires a per-app "install unknown apps" grant before we can install at all. */
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

    /**
     * Stream the APK into a session we own and commit it. Returns the session id, or -1 if the session
     * could not be created or written — which the caller reports as a failure rather than as a pending
     * install, so a row never waits on something that never started.
     *
     * The outcome arrives at {@link InstallReceiver}, carrying {@code packageId}, so a result can always be
     * matched back to the row waiting for it.
     */
    public static int install(Context ctx, File apk, String packageId) {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        int sessionId = -1;
        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            if (packageId != null && !packageId.isEmpty()) params.setAppPackageName(packageId);
            try { params.setSize(apk.length()); } catch (Exception ignored) { }
            sessionId = pi.createSession(params);
            session = pi.openSession(sessionId);

            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("apk", 0, apk.length())) {
                byte[] buf = new byte[65536];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                session.fsync(out);
            }

            Intent callback = new Intent(ctx, InstallReceiver.class)
                    .setAction(InstallReceiver.ACTION_RESULT)
                    .putExtra(InstallReceiver.EXTRA_PACKAGE, packageId)
                    .putExtra(InstallReceiver.EXTRA_SESSION, sessionId);
            // FLAG_MUTABLE is required: the installer fills EXTRA_STATUS and friends into this intent.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pending = PendingIntent.getBroadcast(ctx, sessionId, callback, flags);

            session.commit(pending.getIntentSender());
            return sessionId;
        } catch (Exception e) {
            // Never leave a half-written session behind — it occupies space and confuses a later retry.
            abandon(ctx, sessionId);
            return -1;
        } finally {
            if (session != null) try { session.close(); } catch (Exception ignored) { }
        }
    }

    /** Abandon a session we own. Safe with -1 or an already-finished id. */
    public static void abandon(Context ctx, int sessionId) {
        if (sessionId < 0) return;
        try { ctx.getPackageManager().getPackageInstaller().abandonSession(sessionId); }
        catch (Exception ignored) { }
    }

    /** A human sentence for a {@link PackageInstaller} status, for the row's error line. */
    public static String describe(int status, String message) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                return "Install cancelled.";
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                return "Blocked by the device — Play Protect or another policy refused it.";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "Conflicts with the installed copy. Uninstall it first, then retry.";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                return "Not compatible with this device.";
            case PackageInstaller.STATUS_FAILURE_INVALID:
                return "The downloaded file is not a valid APK. Retry the download.";
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                return "Not enough storage to install.";
            default:
                return (message == null || message.isEmpty()) ? "Install failed." : message;
        }
    }
}
