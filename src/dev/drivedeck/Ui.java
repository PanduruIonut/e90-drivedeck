package dev.drivedeck;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Colours and view helpers. Palette and sizes follow design/a-dashboard.html (CarPlay-like,
 * dark). On the head unit 1 dp is exactly 1 physical pixel of the 1280x480 panel, so the
 * mockup's pixel sizes are used as dp unchanged.
 */
final class Ui {
    static final int BG = Color.parseColor("#000000");
    static final int CARD = Color.parseColor("#1C1C1E");
    static final int CARD2 = Color.parseColor("#2C2C2E");
    static final int TEXT = Color.parseColor("#F5F5F7");
    static final int MUTED = Color.parseColor("#98989F");
    static final int ACCENT = Color.parseColor("#0A84FF");
    static final int GREEN = Color.parseColor("#30D158");
    static final int RED = Color.parseColor("#FF453A");

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    static GradientDrawable card(Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(c, 22));
        return d;
    }

    static TextView text(Context c, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(medium());
        return t;
    }

    static TextView label(Context c, String s) {
        TextView t = text(c, 13, MUTED, false);
        t.setText(s.toUpperCase());
        t.setLetterSpacing(0.06f);
        return t;
    }

    static TextView button(Context c, String label) {
        TextView b = text(c, 30, TEXT, false);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
