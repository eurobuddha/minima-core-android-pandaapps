package com.eurobuddha.pandaapps;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PandaApps — a native app store for Minima companion APKs. Fetches a curated apks.json catalog,
 * lists the apps (grouped by source), and downloads + installs them via the system package installer.
 */
public class MainActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView status;
    private final List<AppEntry> apps = new ArrayList<>();
    private boolean loaded = false;
    // Downloads in progress, keyed by packageId — survives re-renders so we never start a second
    // writer for the same app, and the current card's button always reflects live progress.
    private final Set<String> downloading = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();       // packageId -> last percent
    private final Map<String, Button> actionButtons = new HashMap<>();   // packageId -> current button

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pruneCache();   // clear leftover downloads from previous runs (none are in flight at start)

        View root = findViewById(R.id.main);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        container = findViewById(R.id.container);
        status = findViewById(R.id.status);
        findViewById(R.id.btnRefresh).setOnClickListener(v -> fetch());

        fetch();
    }

    @Override protected void onResume() {
        super.onResume();
        // Re-render so Install/Update flips to Open after returning from the system installer.
        if (loaded) render();
    }

    private void fetch() {
        showStatus("Loading apps…");
        Catalog.fetch(new Catalog.Cb() {
            @Override public void onCatalog(List<AppEntry> list) {
                apps.clear();
                apps.addAll(list);
                loaded = true;
                hideStatus();
                render();
            }
            @Override public void onError(String message) {
                showStatus(message);
                container.removeAllViews();
            }
        });
    }

    private void render() {
        container.removeAllViews();
        renderGroup("YOUR APPS", "PandaApps");
        renderGroup("OFFICIAL MINIMA", "Official");
        // Anything with an unexpected source goes under a catch-all.
        List<AppEntry> other = new ArrayList<>();
        for (AppEntry a : apps) if (!"PandaApps".equals(a.source) && !"Official".equals(a.source)) other.add(a);
        if (!other.isEmpty()) {
            container.addView(heading("MORE"));
            for (AppEntry a : other) container.addView(card(a));
        }
    }

    private void renderGroup(String title, String source) {
        boolean any = false;
        for (AppEntry a : apps) {
            if (!source.equals(a.source)) continue;
            if (!any) { container.addView(heading(title)); any = true; }
            container.addView(card(a));
        }
    }

    private TextView heading(String s) {
        TextView t = Ui.text(this, s, Theme.DIM, 11, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        return t;
    }

    private LinearLayout card(AppEntry app) {
        LinearLayout card = Ui.card(this);

        // icon
        ImageView icon = new ImageView(this);
        int sz = Ui.dp(this, 56);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = Ui.dp(this, 12);
        icon.setLayoutParams(ilp);
        ImageLoader.load(this, app.icon, icon, android.R.drawable.sym_def_app_icon);
        card.addView(icon);

        // middle column: name + version + source, description
        LinearLayout mid = Ui.col(this);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout titleRow = Ui.row(this);
        titleRow.addView(Ui.text(this, app.name, Theme.TEXT, 15, true));
        TextView ver = Ui.text(this, "  v" + app.version, Theme.DIM, 12, false);
        titleRow.addView(ver);
        mid.addView(titleRow);

        long inst = PackageUtil.installedVersionCode(this, app.packageId);
        String state = inst < 0 ? "Not installed"
                : (app.versionCode > inst ? "Installed v" + PackageUtil.installedVersionName(this, app.packageId) + " · update available"
                                          : "Installed · up to date");
        TextView meta = Ui.text(this, (app.category.isEmpty() ? "" : app.category + "  ·  ") + state,
                inst >= 0 && app.versionCode <= inst ? Theme.GREEN : Theme.DIM, 11, false);
        meta.setPadding(0, Ui.dp(this, 2), 0, 0);
        mid.addView(meta);

        if (!app.description.isEmpty()) {
            TextView desc = Ui.text(this, app.description, Theme.DIM, 12, false);
            desc.setPadding(0, Ui.dp(this, 6), 0, 0);
            mid.addView(desc);
        }
        card.addView(mid);

        // action button
        boolean installed = inst >= 0;
        boolean update = installed && app.versionCode > inst;
        // "Official" (foreign-signed) apps can't be reliably installed/updated in-place over an existing
        // copy, so we DOWNLOAD them to the user's Downloads to install manually instead of Install/Update.
        boolean official = !"PandaApps".equals(app.source);
        boolean busy = downloading.contains(app.packageId);
        String base = (installed && !update) ? "Open"
                : (official ? "Download" : (installed ? "Update" : "Install"));
        boolean accent = !base.equals("Open");
        String label = busy ? progressLabel(app.packageId) : base;
        int bg = busy ? Theme.PANEL2 : (accent ? Theme.ACCENT : Theme.PANEL2);
        int fg = busy ? Theme.DIM : (accent ? Theme.ON_ACCENT : Theme.TEXT);
        Button action = Ui.button(this, label, bg, fg);
        action.setEnabled(!busy);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Ui.dp(this, 8);
        action.setLayoutParams(blp);
        actionButtons.put(app.packageId, action);   // current button for this app (for live progress)
        action.setOnClickListener(v -> onAction(app, base));
        card.addView(action);
        return card;
    }

    private String progressLabel(String pkg) {
        Integer p = progress.get(pkg);
        return p == null || p < 0 ? "…" : p + "%";
    }

    private void onAction(AppEntry app, String label) {
        if ("Open".equals(label)) {
            Intent i = PackageUtil.launchIntent(this, app.packageId);
            try {
                if (i != null) startActivity(i); else toast("Couldn't open " + app.name);
            } catch (Exception e) { toast("Couldn't open " + app.name); }
            return;
        }
        if ("Download".equals(label)) { downloadToDownloads(app); return; }
        if (downloading.contains(app.packageId)) return;   // already downloading this app
        if (app.file == null || app.file.isEmpty()) { toast("No download URL for " + app.name); return; }
        if (!Installer.canInstall(this)) {
            toast("Allow PandaApps to install apps, then tap again");
            Installer.requestPermission(this);
            return;
        }
        final String pkg = app.packageId;
        downloading.add(pkg);
        progress.put(pkg, 0);
        setButton(pkg, "0%", false);
        ApkDownloader.download(this, app, new ApkDownloader.Cb() {
            @Override public void onProgress(int percent) {
                progress.put(pkg, percent);
                setButton(pkg, percent < 0 ? "…" : percent + "%", false);
            }
            @Override public void onComplete(File apk) {
                downloading.remove(pkg);
                progress.remove(pkg);
                setButton(pkg, "Opening…", false);
                if (!Installer.install(MainActivity.this, apk)) {
                    toast("Couldn't open the installer");
                    render();   // reset the button to its normal state
                }
                // else: system installer takes over; onResume re-renders to Open/Update
            }
            @Override public void onError(String message) {
                downloading.remove(pkg);
                progress.remove(pkg);
                render();       // back to Install/Update
                toast(message);
            }
        });
    }

    /** Download an APK to the public Downloads folder via the system DownloadManager (with a notification
     *  to install when done). Used for "Official" apps we don't sign — the user installs them manually,
     *  since the system installer can't update a same-package app signed with a different key. */
    private void downloadToDownloads(AppEntry app) {
        if (app.file == null || app.file.isEmpty()) { toast("No download URL for " + app.name); return; }
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(app.file));
            req.setTitle(app.name + " " + app.version);
            req.setDescription("Tap when finished to install");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, app.cacheName());
            req.setMimeType("application/vnd.android.package-archive");
            dm.enqueue(req);
            toast("Downloading " + app.name + " to your Downloads…");
        } catch (Exception e) {
            toast("Couldn't start the download");
        }
    }

    /** Update the button currently representing this app (survives re-renders). */
    private void setButton(String pkg, String text, boolean enabled) {
        Button b = actionButtons.get(pkg);
        if (b != null) { b.setText(text); b.setEnabled(enabled); }
    }

    /** Delete leftover finished downloads so they don't accumulate. Leaves any ".part" alone in case
     *  a download survived a config-change recreate (it streams to app context, not this Activity). */
    private void pruneCache() {
        try {
            File dir = new File(getCacheDir(), "apks");
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) if (f.getName().endsWith(".apk")) f.delete();
        } catch (Exception ignored) {}
    }

    private void showStatus(String msg) {
        status.setText(msg);
        status.setVisibility(View.VISIBLE);
    }

    private void hideStatus() { status.setVisibility(View.GONE); }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
