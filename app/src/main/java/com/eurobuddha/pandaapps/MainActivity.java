package com.eurobuddha.pandaapps;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PandaApps — a native app store for Minima companion APKs. Fetches a curated apks.json catalog and
 * lists the apps grouped by what they do; tapping a row opens {@link AppDetailActivity}, which owns
 * the description, the version history and every install action.
 *
 * This screen deliberately carries no app descriptions. The catalog's `description` field is a
 * running changelog (median 653 chars, up to 6741), so rendering it per row is what made the old
 * single-screen list unreadable.
 */
public class MainActivity extends AppCompatActivity implements Downloads.Listener {

    private LinearLayout container;
    private TextView status;
    private TextView subtitle;
    private final List<AppEntry> apps = new ArrayList<>();
    private boolean loaded = false;
    private String disclaimer = "";   // store-wide "in development / use at own risk" banner (from the catalog)

    // Per-row state binders, so a download tick repaints the rows instead of rebuilding the whole
    // list on every percent. A list, not a map keyed by packageId: an app with an update is drawn
    // twice — once under UPDATES and once in its group — and keying by package let the second
    // registration silently replace the first, freezing the pinned UPDATES row mid-download.
    private final List<Runnable> rowBinders = new ArrayList<>();

    // Installed versionCode per package, snapshotted once per render. Each lookup is a binder IPC
    // into system_server, and this screen asks about every app in the catalog twice per pass.
    private final Map<String, Long> installedCodes = new HashMap<>();

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
        subtitle = findViewById(R.id.subtitle);
        findViewById(R.id.btnRefresh).setOnClickListener(v -> fetch());

        Downloads.addListener(this);
        fetch();
    }

    @Override protected void onResume() {
        super.onResume();
        // Being back here means the installer (or the detail screen) is done with anything it had,
        // so retire settled downloads before redrawing — otherwise their terminal flags would keep
        // overriding the real installed state below.
        Downloads.clearSettled();
        // Re-render so install state is current after returning from the installer or a detail page.
        if (loaded) render();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        Downloads.removeListener(this);
    }

    /** A download ticked — repaint the affected rows in place, no relayout of the whole list. */
    @Override public void onDownloadsChanged() {
        for (Runnable binder : new ArrayList<>(rowBinders)) binder.run();
    }

    private void fetch() {
        showStatus("Loading apps…");
        Catalog.fetch(new Catalog.Cb() {
            @Override public void onCatalog(List<AppEntry> list, String disc) {
                apps.clear();
                apps.addAll(list);
                disclaimer = disc == null ? "" : disc;
                loaded = true;
                hideStatus();
                render();
            }
            @Override public void onError(String message) {
                showStatus(message);
                container.removeAllViews();
                rowBinders.clear();
            }
        });
    }

    // ---------------------------------------------------------------- list

    private void render() {
        container.removeAllViews();
        rowBinders.clear();

        // One PackageManager lookup per app for the whole pass, rather than one in hasUpdate() and
        // another in every bindRow().
        installedCodes.clear();
        for (AppEntry a : apps) {
            if (a.isApk() && !installedCodes.containsKey(a.packageId)) {
                installedCodes.put(a.packageId, PackageUtil.installedVersionCode(this, a.packageId));
            }
        }

        // The store's own entry gets a quiet footer row rather than a group of its own.
        AppEntry self = null;
        List<AppEntry> rest = new ArrayList<>();
        for (AppEntry a : apps) {
            if (getPackageName().equals(a.packageId)) self = a; else rest.add(a);
        }

        // Anything out of date goes to the top — including the store itself.
        List<AppEntry> updates = new ArrayList<>();
        for (AppEntry a : apps) if (hasUpdate(a)) updates.add(a);

        subtitle.setText(apps.size() + " apps"
                + (updates.isEmpty() ? "" : "  ·  " + updates.size()
                        + (updates.size() == 1 ? " update" : " updates")));

        if (!disclaimer.isEmpty()) container.addView(disclaimerBanner());

        if (!updates.isEmpty()) {
            container.addView(heading("UPDATES  ·  " + updates.size()));
            for (AppEntry a : updates) container.addView(row(a, true));
        }

        // Then the function groups, in the order Groups declares.
        Map<String, List<AppEntry>> byGroup = new HashMap<>();
        for (AppEntry a : rest) {
            String g = Groups.groupFor(a);
            List<AppEntry> l = byGroup.get(g);
            if (l == null) { l = new ArrayList<>(); byGroup.put(g, l); }
            l.add(a);
        }
        for (String group : Groups.order()) {
            List<AppEntry> l = byGroup.get(group);
            if (l == null || l.isEmpty()) continue;
            container.addView(heading(group));
            for (AppEntry a : l) container.addView(row(a, false));
        }

        if (self != null) container.addView(footerRow(self));
    }

    /** Installed versionCode from this render's snapshot, or -1 if absent/not an APK. */
    private long installedCode(AppEntry a) {
        Long c = installedCodes.get(a.packageId);
        return c == null ? -1 : c;
    }

    private boolean hasUpdate(AppEntry a) {
        if (!a.isApk()) return false;
        long inst = installedCode(a);
        return inst >= 0 && a.versionCode > inst;
    }

    private TextView heading(String s) {
        TextView t = Ui.text(this, s, Theme.DIM, 11, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 18), 0, Ui.dp(this, 8));
        return t;
    }

    /** A store-wide "in development / use at your own risk" banner shown at the top of the list. */
    private LinearLayout disclaimerBanner() {
        LinearLayout b = Ui.col(this);
        int amber = 0xFFE0A93A;
        b.setBackground(Ui.rounded(0x22E0A93A, amber, 10, this));   // faint amber fill + amber stroke
        int p = Ui.dp(this, 12);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 4);
        b.setLayoutParams(lp);
        b.addView(Ui.text(this, disclaimer, Theme.TEXT, 12, false));
        return b;
    }

    /**
     * One list row: icon, name, a single state line, an optional status chip and a chevron. The
     * whole row is the tap target and opens the detail screen — no actions live here.
     */
    private LinearLayout row(final AppEntry app, final boolean inUpdates) {
        LinearLayout card = Ui.card(this);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 10);
        card.setPadding(p, p, p, p);
        card.setForeground(Ui.ripple(this));
        card.setOnClickListener(v -> AppDetailActivity.open(this, app));

        ImageView icon = new ImageView(this);
        int sz = Ui.dp(this, 44);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(sz, sz);
        ilp.rightMargin = Ui.dp(this, 12);
        icon.setLayoutParams(ilp);
        ImageLoader.load(this, app.icon, icon, android.R.drawable.sym_def_app_icon);
        card.addView(icon);

        LinearLayout mid = Ui.col(this);
        mid.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = Ui.text(this, app.name, Theme.TEXT, 15, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mid.addView(name);
        final TextView sub = Ui.text(this, "", Theme.DIM, 11, false);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        mid.addView(sub);
        card.addView(mid);

        final TextView chip = Ui.badge(this, "", Theme.ON_ACCENT, Theme.ACCENT);
        card.addView(chip);
        card.addView(Ui.chevron(this));

        Runnable bind = () -> bindRow(app, inUpdates, sub, chip);
        bind.run();
        rowBinders.add(bind);
        return card;
    }

    /** Paint a row's changeable parts. Called when the row is built and on every download tick. */
    private void bindRow(AppEntry app, boolean inUpdates, TextView sub, TextView chip) {
        Downloads.State d = Downloads.get(app.packageId);

        if (d != null && d.running) {
            sub.setText(d.percent < 0 ? "Downloading…" : "Downloading " + d.percent + "%");
            sub.setTextColor(Theme.DIM);
            showChip(chip, d.percent < 0 ? "…" : d.percent + "%");
            return;
        }
        // Deliberately no branch for d.installing: once the APK has reached the system installer,
        // the package manager is the only honest answer about whether it landed, and that flag is
        // not cleared until this screen resumes.
        if (d != null && d.error != null) {
            sub.setText(d.error);
            sub.setTextColor(0xFFE0574A);
            hideChip(chip);
            return;
        }

        if (!app.isApk()) {
            sub.setText(app.source.isEmpty() ? "Download" : app.source);
            sub.setTextColor(Theme.DIM);
            hideChip(chip);
            return;
        }

        long inst = installedCode(app);
        boolean installed = inst >= 0;
        boolean update = installed && app.versionCode > inst;

        if (update) {
            String v = PackageUtil.installedVersionName(this, app.packageId);
            sub.setText(inUpdates
                    ? (v == null ? "?" : v) + "  →  " + app.version
                    : "Installed v" + (v == null ? "?" : v) + "  ·  update available");
            sub.setTextColor(Theme.DIM);
            showChip(chip, "UPDATE");
        } else if (installed) {
            sub.setText("Installed  ·  up to date");
            sub.setTextColor(Theme.GREEN);
            hideChip(chip);
        } else {
            sub.setText("Not installed");
            sub.setTextColor(Theme.DIM);
            hideChip(chip);
        }
    }

    private void showChip(TextView chip, String text) {
        chip.setText(text);
        chip.setVisibility(View.VISIBLE);
    }

    private void hideChip(TextView chip) { chip.setVisibility(View.GONE); }

    /** The store's own entry, kept out of the groups and parked at the bottom. */
    private LinearLayout footerRow(final AppEntry self) {
        LinearLayout card = Ui.card(this);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 18);
        card.setLayoutParams(lp);
        card.setForeground(Ui.ripple(this));
        card.setOnClickListener(v -> AppDetailActivity.open(this, self));

        LinearLayout mid = Ui.col(this);
        mid.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        mid.addView(Ui.text(this, "About this store", Theme.TEXT, 13, true));
        TextView v = Ui.text(this, self.name + " v" + self.version, Theme.DIM, 11, false);
        v.setPadding(0, Ui.dp(this, 2), 0, 0);
        mid.addView(v);
        card.addView(mid);
        card.addView(Ui.chevron(this));
        return card;
    }

    // ---------------------------------------------------------------- housekeeping

    /** Delete leftover finished downloads so they don't accumulate. Leaves any ".part" alone in case
     *  a download survived a config-change recreate (it streams to app context, not this Activity). */
    private void pruneCache() {
        try {
            if (Downloads.anyBusy()) return;   // a recreate must not delete what the installer is reading
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
}
