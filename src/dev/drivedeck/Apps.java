package dev.drivedeck;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.util.Log;

import java.lang.reflect.Method;

/** Small helpers for the apps DriveDeck drives (Waze, Spotify, YouTube, YT Music). */
final class Apps {
    static final String WAZE = "com.waze";
    static final String SPOTIFY = "com.spotify.music";
    static final String YOUTUBE = "com.google.android.youtube";
    static final String YT_MUSIC = "com.google.android.apps.youtube.music";
    static final String[] MUSIC = {SPOTIFY, YOUTUBE, YT_MUSIC};

    private Apps() {}

    static boolean installed(Context c, String pkg) {
        return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    static Drawable icon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static String label(String pkg) {
        if (SPOTIFY.equals(pkg)) return "Spotify";
        if (YOUTUBE.equals(pkg)) return "YouTube";
        if (YT_MUSIC.equals(pkg)) return "YT Music";
        if (WAZE.equals(pkg)) return "Waze";
        return pkg.substring(pkg.lastIndexOf('.') + 1);
    }

    static void openFullScreen(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Pauses the app's media session, then force-stops it, so it can't keep playing or running. */
    static void stop(Context c, String pkg) {
        try {
            MediaSessionManager msm = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            for (MediaController mc : msm.getActiveSessions(null)) {
                if (pkg.equals(mc.getPackageName())) mc.getTransportControls().pause();
            }
        } catch (RuntimeException e) {
            Log.w(MainActivity.TAG, "pause " + pkg + " failed", e);
        }
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            Method forceStop = ActivityManager.class.getMethod("forceStopPackage", String.class);
            forceStop.invoke(am, pkg);
            Log.i(MainActivity.TAG, "stopped " + pkg);
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "force-stop " + pkg + " failed", e);
        }
    }
}
