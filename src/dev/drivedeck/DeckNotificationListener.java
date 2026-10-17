package dev.drivedeck;

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the latest Waze navigation notification (next manoeuvre text and arrow icon)
 * for the Waze fallback card. Needs notification access, which the owner grants once.
 */
public class DeckNotificationListener extends NotificationListenerService {
    static final String WAZE = "com.waze";

    interface Observer {
        void onWazeChanged();
    }

    static volatile boolean connected;
    static volatile CharSequence wazeTitle;
    static volatile CharSequence wazeText;
    static volatile Drawable wazeIcon;

    private static final List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void addObserver(Observer o) {
        OBSERVERS.add(o);
    }

    static void removeObserver(Observer o) {
        OBSERVERS.remove(o);
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        StatusBarNotification latest = null;
        for (StatusBarNotification sbn : getActiveNotifications()) {
            if (WAZE.equals(sbn.getPackageName())
                    && (latest == null || sbn.getPostTime() > latest.getPostTime())) {
                latest = sbn;
            }
        }
        if (latest != null) consume(latest); else notifyObservers();
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        notifyObservers();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (WAZE.equals(sbn.getPackageName())) consume(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!WAZE.equals(sbn.getPackageName())) return;
        wazeTitle = null;
        wazeText = null;
        wazeIcon = null;
        notifyObservers();
    }

    private void consume(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        wazeTitle = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence big = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        wazeText = big != null ? big : e.getCharSequence(Notification.EXTRA_TEXT);
        wazeIcon = loadLargeIcon(n);
        Log.i(MainActivity.TAG, "waze: " + wazeTitle + " / " + wazeText);
        notifyObservers();
    }

    private Drawable loadLargeIcon(Notification n) {
        Icon icon = n.getLargeIcon();
        if (icon == null) return null;
        try {
            Context wazeContext = createPackageContext(WAZE, 0);
            return icon.loadDrawable(wazeContext);
        } catch (Exception ex) {
            Log.w(MainActivity.TAG, "waze icon not loadable", ex);
            return null;
        }
    }

    private static void notifyObservers() {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Observer o : OBSERVERS) o.onWazeChanged();
            }
        });
    }
}
