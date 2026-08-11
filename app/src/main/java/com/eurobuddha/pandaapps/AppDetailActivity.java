package com.eurobuddha.pandaapps;

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.List;

/**
 * One app's page: what it is, what changed, and everything you can do with it.
 *
 * The front page is a plain list now, so every install action lives here — Install / Update / Open
 * for APKs, Download-to-Downloads for foreign-signed updates, Get for non-APK entries. Chrome and
 * the JSON-over-Intent hand-off follow the fleet's existing sub-screen pattern
 * (apks/vestr SubActivity + ContractDetailActivity).
 */
public class AppDetailActivity extends AppCompatActivity implements Downloads.Listener {

    private static final String EXTRA_APP = "app";
    private static final int ERROR = 0xFFE0574A;
    private static final int AMBER = 0xFFE0A93A;
    private static final int NOTES_COLLAPSED = 2;

    /** Open the detail screen for a catalog entry. */
    public static void open(Context ctx, AppEntry app) {
        Intent i = new Intent(ctx, AppDetailActivity.class);
        i.putExtra(EXTRA_APP, app.toJson().toString());
        ctx.startActivity(i);
    }

    private AppEntry app;
    private Blurb blurb;
    private LinearLayout body;
    private boolean showAllNotes = false;

    // Live progress views. A download reports every changed percent, so rebuilding the page on each
    // tick meant up to 101 full view-tree rebuilds — and emptying the ScrollView throws the reader
    // back to the top each time. Held here so a tick repaints two views instead. Cleared at the top
    // of render() so they can never outlive the views they point at.
    private TextView progressPct;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        try {
            app = AppEntry.from(new JSONObject(getIntent().getStringExtra(EXTRA_APP)));
        } catch (Exception e) {
            finish();
            return;
        }
        blurb = Blurb.parse(app.description);
        body = findViewById(R.id.detailBody);

        final View root = findViewById(R.id.detailRoot);
        final View header = findViewById(R.id.detailHeader);
        final int headerTop = header.getPaddingTop();
        final int bodyBottom = body.getPaddingBottom();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars =
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            body.setPadding(body.getPaddingLeft(), body.getPaddingTop(),
                    body.getPaddingRight(), bodyBottom + bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.detailTitle)).setText(app.name);

        Downloads.addListener(this);
        render();
    }

    /** Re-render so the action flips to Open after returning from the system installer. */
    @Override protected void onResume() {
        super.onResume();
        if (app != null) render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        Downloads.removeListener(this);
    }

    @Override public void onDownloadsChanged() {
        if (app == null) return;
        Downloads.State d = Downloads.get(app.packageId);
        // A percent tick changes two labels, not the layout — only start, finish and failure alter
        // which sections exist, and those fall through to a full render.
        if (d != null && d.running && progressBar != null && progressPct != null) {
            progressPct.setText(d.percent < 0 ? "…" : d.percent + "%");
            progressBar.setIndeterminate(d.percent < 0);
            if (d.percent >= 0) progressBar.setProgress(d.percent);
            return;
        }
        render();
    }

    // ---------------------------------------------------------------- render

    private void render() {
        body.removeAllViews();
        progressPct = null;         // the views these pointed at are gone as of the line above
        progressBar = null;
        body.addView(hero());
        addActionArea();

        if (blurb.hasWarning()) body.addView(callout("NEW SIGNING KEY", blurb.warning));

        if (blurb.hasLead()) {
            body.addView(sectionHeading("ABOUT"));
            body.addView(paragraph(blurb.lead));
        }

        if (blurb.hasNotes()) addWhatsNew();

        addDetails();
    }

    /** Icon, name, version and category. */
    private LinearLayout hero() {
        LinearLayout hero = Ui.row(this);
        hero.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        int sz = Ui.dp(this, 72);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = Ui.dp(this, 14);
        icon.setLayoutParams(ilp);
        ImageLoader.load(this, app.icon, icon, android.R.drawable.sym_def_app_icon);
        hero.addView(icon);

        LinearLayout col = Ui.col(this);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(Ui.text(this, app.name, Theme.TEXT, 20, true));

        String line = "v" + app.version;
        if (!app.category.isEmpty()) line += "  ·  " + app.category;
        TextView sub = Ui.text(this, line, Theme.DIM, 12, false);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        col.addView(sub);

        if (!app.source.isEmpty()) {
            TextView src = Ui.text(this, app.source, Theme.DIM, 11, false);
            src.setPadding(0, Ui.dp(this, 2), 0, 0);
            col.addView(src);
        }
        hero.addView(col);
        return hero;
    }

    /** The primary button, or live progress while the APK is downloading. */
    private void addActionArea() {
        Downloads.State d = Downloads.get(app.packageId);
        if (d != null && d.running) {
            body.addView(progressPanel(d));
            return;
        }

        boolean isApk = app.isApk();
        long inst = isApk ? PackageUtil.installedVersionCode(this, app.packageId) : -1;
        boolean installed = inst >= 0;
        boolean update = installed && app.versionCode > inst;
        // "Official" (foreign-signed) apps can't be updated in place over a differently-signed copy,
        // so an update routes through Downloads for a manual install. A FIRST install is fine
        // through the in-app installer — signatures only block the in-place update.
        boolean official = !"PandaApps".equals(app.source);
        final String base = !isApk ? "Get"
                : (installed && !update) ? "Open"
                : (official && installed ? "Download" : (installed ? "Update" : "Install"));

        boolean accent = !base.equals("Open");
        Button action = Ui.button(this, base,
                accent ? Theme.ACCENT : Theme.PANEL2,
                accent ? Theme.ON_ACCENT : Theme.TEXT);
        action.setTextSize(14);
        action.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 18);
        action.setLayoutParams(blp);
        action.setOnClickListener(v -> onAction(base));
        body.addView(action);

        String state;
        int colour = Theme.DIM;
        if (!isApk) {
            state = "Opens in your browser";
        } else if (!installed) {
            state = "Not installed";
        } else if (update) {
            String v = PackageUtil.installedVersionName(this, app.packageId);
            state = "Installed v" + (v == null ? "?" : v) + "  ·  update available";
        } else {
            state = "Installed  ·  up to date";
            colour = Theme.GREEN;
        }
        TextView st = Ui.text(this, state, colour, 12, false);
        st.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        st.setPadding(0, Ui.dp(this, 8), 0, 0);
        body.addView(st);

        if (d != null && d.error != null) {
            TextView err = Ui.text(this, d.error, ERROR, 12, false);
            err.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            err.setPadding(0, Ui.dp(this, 6), 0, 0);
            body.addView(err);
        }

        addSourceButton();
    }

    /**
     * "View source code" — every app in the store is open source, so the install page links straight
     * to the repository for anyone who wants to read it, audit it or fork it. Hidden when the catalog
     * entry carries no repo, rather than guessing at a URL.
     */
    private void addSourceButton() {
        if (app.repo == null || app.repo.isEmpty()) return;

        Button src = Ui.button(this, "View source code  ↗", Theme.PANEL2, Theme.TEXT);
        src.setTextSize(13);
        src.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 12);
        src.setLayoutParams(lp);
        src.setOnClickListener(v -> openInBrowser(app.repo));
        body.addView(src);
    }

    private LinearLayout progressPanel(Downloads.State d) {
        LinearLayout p = Ui.col(this);
        p.setBackground(Ui.rounded(Theme.PANEL, Theme.BORDER, 10, this));
        int pad = Ui.dp(this, 14);
        p.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        p.setLayoutParams(lp);

        LinearLayout top = Ui.row(this);
        TextView label = Ui.text(this, "Downloading", Theme.TEXT, 13, true);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(label);
        progressPct = Ui.text(this, d.percent < 0 ? "…" : d.percent + "%", Theme.ACCENT, 13, true);
        top.addView(progressPct);
        p.addView(top);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar = bar;
        bar.setMax(100);
        if (d.percent < 0) {
            bar.setIndeterminate(true);
        } else {
            bar.setIndeterminate(false);
            bar.setProgress(d.percent);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(this, 10);
        bar.setLayoutParams(blp);
        p.addView(bar);
        return p;
    }

    /** Version history, collapsed to the newest few. */
    private void addWhatsNew() {
        body.addView(sectionHeading("WHAT'S NEW"));
        List<Blurb.Note> notes = blurb.notes;
        int shown = showAllNotes ? notes.size() : Math.min(NOTES_COLLAPSED, notes.size());

        for (int i = 0; i < shown; i++) {
            Blurb.Note n = notes.get(i);
            TextView ver = Ui.text(this, "v" + n.version, Theme.ACCENT, 12, true);
            ver.setPadding(0, Ui.dp(this, i == 0 ? 2 : 12), 0, Ui.dp(this, 3));
            body.addView(ver);
            body.addView(paragraph(n.text));
        }

        if (!showAllNotes && notes.size() > shown) {
            TextView more = Ui.text(this,
                    "Show all " + notes.size() + " versions  ⌄", Theme.ACCENT, 12, true);
            more.setPadding(0, Ui.dp(this, 12), 0, 0);
            more.setBackground(Ui.ripple(this));
            more.setOnClickListener(v -> { showAllNotes = true; render(); });
            body.addView(more);
        }
    }

    private void addDetails() {
        body.addView(sectionHeading("DETAILS"));
        LinearLayout table = Ui.col(this);
        table.setBackground(Ui.rounded(Theme.PANEL, Theme.BORDER, 10, this));
        int pad = Ui.dp(this, 12);
        table.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 4));

        if (!app.packageId.isEmpty()) table.addView(detailRow("Package", app.packageId, false));
        table.addView(detailRow("Version", app.version + " (" + app.versionCode + ")", false));

        if (app.isApk()) {
            long inst = PackageUtil.installedVersionCode(this, app.packageId);
            if (inst >= 0) {
                String v = PackageUtil.installedVersionName(this, app.packageId);
                table.addView(detailRow("Installed", (v == null ? "?" : v) + " (" + inst + ")", false));
            }
        }
        if (!app.category.isEmpty()) table.addView(detailRow("Category", app.category, false));
        // "Publisher", not "Source" — the source now means the code, linked above.
        if (!app.source.isEmpty()) table.addView(detailRow("Publisher", app.source, false));
        if (!app.sha256.isEmpty()) table.addView(detailRow("SHA-256", shortHash(app.sha256), true));

        body.addView(table);
    }

    // ---------------------------------------------------------------- building blocks

    private TextView sectionHeading(String s) {
        TextView t = Ui.text(this, s, Theme.DIM, 11, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 8));
        return t;
    }

    private TextView paragraph(String s) {
        TextView t = Ui.text(this, s, Theme.TEXT, 13, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        return t;
    }

    /** Amber notice box, matching the store-wide disclaimer banner on the list. */
    private LinearLayout callout(String title, String message) {
        LinearLayout b = Ui.col(this);
        b.setBackground(Ui.rounded(0x22E0A93A, AMBER, 10, this));
        int p = Ui.dp(this, 12);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 20);
        b.setLayoutParams(lp);

        TextView t = Ui.text(this, title, AMBER, 11, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, 0, 0, Ui.dp(this, 5));
        b.addView(t);
        b.addView(Ui.text(this, message, Theme.TEXT, 12, false));
        return b;
    }

    private LinearLayout detailRow(String label, String value, boolean copyable) {
        LinearLayout r = Ui.row(this);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int v = Ui.dp(this, 9);
        r.setPadding(0, v, 0, v);

        TextView l = Ui.text(this, label, Theme.DIM, 12, false);
        l.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 84),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(l);

        TextView val = Ui.text(this, value, Theme.TEXT, 12, false);
        val.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(val);

        if (copyable) {
            TextView copy = Ui.text(this, "COPY", Theme.ACCENT, 10, true);
            copy.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            r.addView(copy);
            r.setBackground(Ui.ripple(this));
            r.setOnClickListener(x -> copyToClipboard(label, app.sha256));
        }
        return r;
    }

    private static String shortHash(String h) {
        return h.length() <= 20 ? h : h.substring(0, 12) + "…" + h.substring(h.length() - 4);
    }

    // ---------------------------------------------------------------- actions

    private void onAction(String label) {
        if ("Open".equals(label)) {
            Intent i = PackageUtil.launchIntent(this, app.packageId);
            try {
                if (i != null) startActivity(i); else toast("Couldn't open " + app.name);
            } catch (Exception e) { toast("Couldn't open " + app.name); }
            return;
        }
        if ("Get".equals(label))      { openInBrowser(app.file); return; }
        if ("Download".equals(label)) { downloadToDownloads(); return; }

        // Install / Update — stream to cache, verify, then hand to the system installer.
        if (Downloads.isRunning(app.packageId)) return;
        if (app.file == null || app.file.isEmpty()) { toast("No download URL for " + app.name); return; }
        if (!Installer.canInstall(this)) {
            toast("Allow PandaApps to install apps, then tap again");
            Installer.requestPermission(this);
            return;
        }
        Downloads.start(this, app);
    }

    /** Open a non-app download (a skill zip, a desktop build) in the browser, which handles the
     *  filename and MIME type properly — never the Android package installer. */
    private void openInBrowser(String url) {
        if (url == null || url.isEmpty()) { toast("No download link"); return; }
        // The catalog is remote data, and the IPFS fallback can be served by a public gateway, so
        // only hand the browser a web link — never an arbitrary scheme aimed at another component.
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            toast("Unsupported link");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            toast("Couldn't open the link");
        }
    }

    /** Download to the public Downloads folder for a manual install. Used when updating an
     *  "Official" app we don't sign — the installer can't replace a differently-signed copy. */
    private void downloadToDownloads() {
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

    private void copyToClipboard(String label, String value) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText(label, value));
            toast(label + " copied");
        } catch (Exception e) {
            toast("Couldn't copy");
        }
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
