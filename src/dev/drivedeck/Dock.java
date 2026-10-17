package dev.drivedeck;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.util.TypedValue;

/** CarPlay-style left dock: time, connection, home and the four app icons. */
final class Dock extends LinearLayout {
    interface Listener {
        void onHome();
        void onApp(String pkg);
    }

    private final TextView status;
    private final ConnectivityManager connectivity;
    private final FrameLayout[] musicIcons = new FrameLayout[Apps.MUSIC.length];
    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { refreshStatus(); }
        @Override public void onLost(Network n) { refreshStatus(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) { refreshStatus(); }
    };

    Dock(Context c, final Listener listener) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 10));
        connectivity = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);

        TextClock time = new TextClock(c);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
        time.setTextColor(Ui.TEXT);
        time.setTypeface(Ui.medium());
        time.setGravity(Gravity.CENTER);
        addView(time);

        status = Ui.text(c, 11, Ui.MUTED, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(c, 3), 0, 0);
        addView(status);

        addView(icon(c, null, true, new OnClickListener() {
            @Override public void onClick(View v) { listener.onHome(); }
        }), iconParams(c, 16));
        addView(icon(c, Apps.WAZE, false, new OnClickListener() {
            @Override public void onClick(View v) { listener.onApp(Apps.WAZE); }
        }), iconParams(c, 12));
        for (int i = 0; i < Apps.MUSIC.length; i++) {
            final String pkg = Apps.MUSIC[i];
            musicIcons[i] = icon(c, pkg, false, new OnClickListener() {
                @Override public void onClick(View v) { listener.onApp(pkg); }
            });
            addView(musicIcons[i], iconParams(c, 12));
        }
        refreshStatus();
    }

    /** Dims the music apps that aren't the current source. */
    void setMusicSource(String pkg) {
        for (int i = 0; i < Apps.MUSIC.length; i++) {
            boolean on = Apps.MUSIC[i].equals(pkg);
            musicIcons[i].setAlpha(Apps.installed(getContext(), Apps.MUSIC[i]) ? (on ? 1f : 0.45f) : 0.2f);
        }
    }

    private FrameLayout icon(Context c, String pkg, boolean home, OnClickListener click) {
        FrameLayout box = new FrameLayout(c);
        box.setClipToOutline(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(c, 15));
        bg.setColor(Ui.CARD2);
        box.setBackground(bg);

        if (home) {
            TextView glyph = Ui.text(c, 22, Ui.TEXT, false);
            glyph.setText("▦");
            glyph.setGravity(Gravity.CENTER);
            box.addView(glyph, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            ImageView img = new ImageView(c);
            Drawable d = Apps.icon(c, pkg);
            if (d != null) {
                img.setImageDrawable(d);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                box.setAlpha(0.2f);
            }
            box.addView(img, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        box.setContentDescription(home ? "All apps" : Apps.label(pkg));
        box.setOnClickListener(click);
        return box;
    }

    private static LayoutParams iconParams(Context c, int topDp) {
        int s = Ui.dp(c, 58);
        LayoutParams lp = new LayoutParams(s, s);
        lp.topMargin = Ui.dp(c, topDp);
        return lp;
    }

    private void refreshStatus() {
        post(new Runnable() {
            @Override
            public void run() {
                Network n = connectivity.getActiveNetwork();
                NetworkCapabilities nc = n == null ? null : connectivity.getNetworkCapabilities(n);
                String s = "offline";
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s = "Wi-Fi";
                else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) s = "4G";
                status.setText(s);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        connectivity.registerDefaultNetworkCallback(netCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        connectivity.unregisterNetworkCallback(netCallback);
        super.onDetachedFromWindow();
    }
}
