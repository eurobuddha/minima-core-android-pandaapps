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
        public boolean installing;  // handed off to the system installer
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

    /** Forget a finished download's state (used once a screen has shown its error). */
    public static synchronized void clear(String pkg) { STATES.remove(pkg); }

    /**
     * True while any download is in flight or waiting on the system installer. Downloads now
     * outlive the screen that started them, so the cache prune on startup must not delete an APK
     * the installer is still reading through its content:// grant.
     */
    public static synchronized boolean anyBusy() {
        for (State s : STATES.values()) if (s.running || s.installing) return true;
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
