package com.eurobuddha.pandaapps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Where a {@link PackageInstaller} session tells us what actually happened.
 *
 * This is the half the old {@code ACTION_VIEW} hand-off never had. Every terminal state reaches
 * {@link Downloads#installFinished} so the row stops saying "Installing…" — success, failure, and the
 * user declining the confirmation all end the wait. Silence is the one outcome that is not allowed.
 */
public class InstallReceiver extends BroadcastReceiver {

    public static final String ACTION_RESULT = "com.eurobuddha.pandaapps.INSTALL_RESULT";
    public static final String EXTRA_PACKAGE = "pandaapps.pkg";
    public static final String EXTRA_SESSION = "pandaapps.session";

    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra(EXTRA_PACKAGE);
        int sessionId = intent.getIntExtra(EXTRA_SESSION, -1);
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The system wants the user to confirm. Launch its dialog; the real result comes back here
            // afterwards. If we cannot launch it, that is itself a terminal failure — say so rather than
            // leaving the row waiting for a dialog that never appeared.
            Intent confirm = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                    : intent.<Intent>getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { ctx.startActivity(confirm); return; }
                catch (Exception e) { /* fall through to the failure below */ }
            }
            Installer.abandon(ctx, sessionId);
            Downloads.installFinished(pkg, false, "Could not open the install confirmation.");
            return;
        }

        if (status == PackageInstaller.STATUS_SUCCESS) {
            Downloads.installFinished(pkg, true, null);
            return;
        }

        // Any other status is terminal. The session is already finished by the framework at this point,
        // but abandoning a finished id is harmless and guards the paths where it is not.
        Installer.abandon(ctx, sessionId);
        Downloads.installFinished(pkg, false, Installer.describe(status, message));
    }
}
