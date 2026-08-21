package com.eurobuddha.pandaapps;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-scoped registry of in-flight APK downloads, keyed by packageId.
 *
 * Downloads used to be tracked in MainActivity fields, which was fine while the install button
 * lived on the only screen. Now that installing happens on the detail screen, backing out
 * mid-download would leave the ApkDownloader callback writing into a dead Activity, and the list
 * behind it could never show that a download was running. So the state — and the hand-off to the
 * system installer when the bytes land — belongs to the application, not to whichever screen
 * happened to start it.
 *
 * All callbacks from {@link ApkDownloader} already arrive on the main thread, so listeners are
 * notified directly; the state map is still synchronised because the download thread is what
 * ultimately drives it.
 */
public final class Downloads {

    /** Notified whenever any download's state changes. Screens re-read {@link #get} and redraw. */
    public interface Listener { void onDownloadsChanged(); }

    /** Live state for one package. */
    public static final class State {
        public int percent;         // -1 when the server sent no content length
        public boolean running;
        /** Handed off to the system installer. Guards the cache prune only — never drives row text,
         *  because once the installer has the APK, PackageUtil is what says whether it landed. */
        public boolean installing;
        /** Download-only flavor: the verified APK is being copied into public Downloads. Guards the
         *  cache prune like {@link #installing}, and drives a "Saving to Downloads…" state. */
        public boolean exporting;
        /** Download-only flavor: the copy landed in Downloads. Transient — cleared when the list
         *  resumes; the detail screen's persistent signal is {@link ApkExporter#alreadySaved}. */
        public boolean saved;
        public String error;        // last failure, or null
    }

    private static final Map<String, State> STATES = new HashMap<>();
    private static final List<Listener> LISTENERS = new ArrayList<>();

    private Downloads() {}

    public static synchronized void addListener(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static synchronized void removeListener(Listener l) { LISTENERS.remove(l); }

    /** Current state for a package, or null if it has never been downloaded this run. */
    public static synchronized State get(String pkg) { return STATES.get(pkg); }

    public static synchronized boolean isRunning(String pkg) {
        State s = STATES.get(pkg);
        return s != null && s.running;
    }

    /** Forget one package's state. */
    public static synchronized void clear(String pkg) { STATES.remove(pkg); }

    /**
     * Drop every download that is no longer in flight.
     *
     * Called when the list screen resumes, which means the user is back in our app: any download
     * that finished has either reached the system installer — from which point
     * {@link PackageUtil} is the only truth about what is installed — or failed and already shown
     * its error on the detail screen. Without this the terminal flags below are never reset, and a
     * row would keep reporting "Waiting for the installer…" long after the install succeeded.
     */
    public static synchronized void clearSettled() {
        java.util.Iterator<Map.Entry<String, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            State s = it.next().getValue();
            if (!s.running && !s.exporting) it.remove();
        }
    }

    /**
     * True while any download is in flight or waiting on the system installer. Downloads now
     * outlive the screen that started them, so the cache prune on startup must not delete an APK
     * the installer is still reading through its content:// grant.
     */
    public static synchronized boolean anyBusy() {
        for (State s : STATES.values()) if (s.running || s.installing || s.exporting) return true;
        return false;
    }

    /**
     * Start downloading an app, unless one is already in flight for it. On success the APK goes
     * straight to the system installer using the application context, so completion is not lost
     * if every screen has gone away.
     */
    public static void start(Context anyCtx, final AppEntry app) {
        final Context ctx = anyCtx.getApplicationContext();
        final String pkg = app.packageId;

        synchronized (Downloads.class) {
            State existing = STATES.get(pkg);
            if (existing != null && existing.running) return;   // never a second writer for one app
            State s = new State();
            s.running = true;
            s.percent = 0;
            STATES.put(pkg, s);
        }
        notifyChanged();

        ApkDownloader.download(ctx, app, new ApkDownloader.Cb() {
            @Override public void onProgress(int percent) {
                synchronized (Downloads.class) {
                    State s = STATES.get(pkg);
                    if (s != null) s.percent = percent;
                }
                notifyChanged();
            }

            @Override public void onComplete(File apk) {
                if (BuildConfig.DOWNLOAD_ONLY) {
                    // PandaGet has no installer permission: the hand-off is a verified copy into
                    // the user's Downloads, then they install it from the Files app themselves.
                    synchronized (Downloads.class) {
                        State s = STATES.get(pkg);
                        if (s != null) { s.running = false; s.exporting = true; }
                    }
                    notifyChanged();
                    ApkExporter.save(ctx, app, apk, new ApkExporter.Cb() {
                        @Override public void onSaved() {
                            synchronized (Downloads.class) {
                                State s = STATES.get(pkg);
                                if (s != null) { s.exporting = false; s.saved = true; }
                            }
                            notifyChanged();
                        }
                        @Override public void onError(String message) {
                            synchronized (Downloads.class) {
                                State s = STATES.get(pkg);
                                if (s != null) { s.exporting = false; s.error = message; }
                            }
                            notifyChanged();
                        }
                    });
                    return;
                }
                boolean opened = Installer.install(ctx, apk);
                synchronized (Downloads.class) {
                    State s = STATES.get(pkg);
                    if (s != null) {
                        s.running = false;
                        s.installing = opened;
                        if (!opened) s.error = "Couldn't open the installer";
                    }
                }
                notifyChanged();
            }

            @Override public void onError(String message) {
                synchronized (Downloads.class) {
                    State s = STATES.get(pkg);
                    if (s != null) { s.running = false; s.error = message; }
                }
                notifyChanged();
            }
        });
    }

    private static void notifyChanged() {
        List<Listener> copy;
        synchronized (Downloads.class) { copy = new ArrayList<>(LISTENERS); }
        for (Listener l : copy) l.onDownloadsChanged();
    }
}
