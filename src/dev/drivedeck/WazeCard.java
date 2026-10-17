package dev.drivedeck;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** Fallback for the Waze slot: the next manoeuvre from Waze's own notification. */
final class WazeCard extends LinearLayout implements DeckNotificationListener.Observer {
    private final ImageView arrow;
    private final android.widget.TextView title;
    private final android.widget.TextView detail;

    WazeCard(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int p = Ui.dp(c, 24);
        setPadding(p, p, p, p);

        arrow = new ImageView(c);
        int s = Ui.dp(c, 140);
        addView(arrow, new LayoutParams(s, s));

        title = Ui.text(c, 34, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        addView(title);

        detail = Ui.text(c, 20, Ui.MUTED, false);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, Ui.dp(c, 10), 0, 0);
        addView(detail);

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Context ctx = getContext();
                Intent i = DeckNotificationListener.connected
                        ? ctx.getPackageManager().getLaunchIntentForPackage(DeckNotificationListener.WAZE)
                        : new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                if (i != null) ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        render();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        DeckNotificationListener.addObserver(this);
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        DeckNotificationListener.removeObserver(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onWazeChanged() {
        render();
    }

    private void render() {
        if (!DeckNotificationListener.connected) {
            arrow.setImageDrawable(null);
            title.setText("Waze");
            detail.setText("Atinge ca să permiți accesul la notificări");
            return;
        }
        CharSequence t = DeckNotificationListener.wazeTitle;
        if (t == null) {
            arrow.setImageDrawable(null);
            title.setText("Waze");
            detail.setText("Fără navigare activă. Atinge ca să deschizi Waze.");
            return;
        }
        arrow.setImageDrawable(DeckNotificationListener.wazeIcon);
        title.setText(t);
        detail.setText(DeckNotificationListener.wazeText);
    }
}
