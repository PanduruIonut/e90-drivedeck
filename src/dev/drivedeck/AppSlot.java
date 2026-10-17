package dev.drivedeck;

import android.app.ActivityView;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.util.TypedValue;

/**
 * One area of the dashboard that hosts a real app (Waze, Spotify, YouTube…) through the
 * hidden ActivityView. The app shown can be switched at runtime. If embedding is not
 * possible on this unit, the slot shows a fallback card instead and says why.
 */
final class AppSlot extends FrameLayout {
    interface Fallback {
        View create(Context c);
    }

    private final Fallback fallback;
    private String pkg;
    private String label;
    private Intent launch;
    private ActivityView activityView;
    private EmbeddedDisplayView textureView;
    private View fallbackView;
    private final TextView notice;
    private boolean ready;
    private boolean fellBack;
    /** Runs each time the app has been put in the slot. */
    Runnable onStarted;

    AppSlot(Context c, String pkg, String label, boolean expandButton, Fallback fallback) {
        super(c);
        this.fallback = fallback;
        setBackground(Ui.card(c));
        setClipToOutline(true);

        notice = Ui.text(c, 20, Ui.MUTED, false);
        notice.setGravity(Gravity.CENTER);
        notice.setVisibility(GONE);

        setTarget(pkg, label);
        embed();
        addView(notice, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (expandButton) addExpandButton();
    }

    private void setTarget(String pkg, String label) {
        this.pkg = pkg;
        this.label = label;
        this.launch = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
    }

    String currentPackage() {
        return pkg;
    }

    /** Shows another app in this slot (e.g. a music tab switch). */
    void switchTo(String newPkg, String newLabel) {
        setTarget(newPkg, newLabel);
        Log.i(MainActivity.TAG, "slot switched to " + newPkg);
        if (fellBack) {
            return;
        }
        startInside();
    }

    private void embed() {
        if (!MainActivity.useActivityView) {
            textureView = new EmbeddedDisplayView(getContext(), label, new EmbeddedDisplayView.Listener() {
                @Override
                public void onDisplayReady(EmbeddedDisplayView view) {
                    ready = true;
                    startInside();
                }
            });
            addView(textureView, 0, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Log.i(MainActivity.TAG, pkg + ": TextureView embedder created");
            return;
        }
        try {
            final ActivityView av = new ActivityView(getContext());
            av.setCallback(new ActivityView.StateCallback() {
                @Override
                public void onActivityViewReady(ActivityView view) {
                    ready = true;
                    startInside();
                }

                @Override
                public void onActivityViewDestroyed(ActivityView view) {
                    ready = false;
                    Log.i(MainActivity.TAG, "embedded view destroyed (" + pkg + ")");
                }
            });
            addView(av, 0, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            activityView = av;
            Log.i(MainActivity.TAG, pkg + ": ActivityView created");
        } catch (Throwable t) {
            Log.w(MainActivity.TAG, "ActivityView unavailable", t);
            showFallback("încorporare indisponibilă: " + t.getClass().getSimpleName());
        }
    }

    private void startInside() {
        if (fellBack) return;
        if (launch == null) {
            notice.setText(label + " nu e instalat");
            notice.setVisibility(VISIBLE);
            return;
        }
        notice.setVisibility(GONE);
        if (!ready || (activityView == null && textureView == null)) return;
        try {
            if (textureView != null) {
                if (textureView.launch(launch, pkg)) startEscapeGuard();
            } else {
                activityView.startActivity(new Intent(launch));
            }
            Log.i(MainActivity.TAG, pkg + ": started inside the dashboard");
            if (onStarted != null) onStarted.run();
        } catch (final Throwable t) {
            Log.w(MainActivity.TAG, pkg + ": start inside failed", t);
            post(new Runnable() {
                @Override
                public void run() {
                    showFallback("pornire eșuată: " + t.getClass().getSimpleName());
                }
            });
        }
    }

    /**
     * A cold-started app can open its next screen by itself, from a context with no display, and that
     * lands on the main display over the dashboard (Waze: FreeMapAppActivity starts MainActivity ~6 s
     * later; 3/3 on the emulator). For a while after starting an app, bring it back whenever it leaves,
     * even while the dashboard is covered, since being covered is exactly what the escape causes.
     */
    private static final long ESCAPE_GUARD_MS = 20_000;
    private long guardUntil;
    private final Runnable escapeGuard = new Runnable() {
        @Override
        public void run() {
            if (textureView == null || SystemClock.uptimeMillis() > guardUntil) return;
            if (textureView.pullBack(pkg)) Log.i(MainActivity.TAG, pkg + ": left the slot on its own; brought back");
            postDelayed(this, 1000);
        }
    };

    private void startEscapeGuard() {
        guardUntil = SystemClock.uptimeMillis() + ESCAPE_GUARD_MS;
        removeCallbacks(escapeGuard);
        postDelayed(escapeGuard, 1000);
    }

    /** The user is taking the app (or the screen) somewhere else: let it go. */
    void stopEscapeGuard() {
        guardUntil = 0;
        removeCallbacks(escapeGuard);
    }

    /** Called when the dashboard comes back to the front, e.g. after the app was opened full screen. */
    void onHostResumed() {
        startInside();
    }

    private static final long WATCH_MS = 5000;
    private boolean watching;

    /** If the embedded app dies (crash, killed for memory), its display goes empty: relaunch it. */
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (!watching) return;
            if (textureView != null && ready && launch != null && !Tasks.hasTaskOnDisplay(textureView.displayId())) {
                Log.i(MainActivity.TAG, pkg + ": slot empty, relaunching");
                startInside();
            }
            postDelayed(this, WATCH_MS);
        }
    };

    /** Watch only while DriveDeck is visible, so an app opened full screen isn't pulled back behind your back. */
    void setWatching(boolean on) {
        watching = on;
        removeCallbacks(watchdog);
        if (on) postDelayed(watchdog, WATCH_MS);
    }

    /** Opens the current app full screen. */
    void expand() {
        if (launch == null) return;
        stopEscapeGuard();
        getContext().startActivity(new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void showFallback(String reason) {
        if (fellBack) return;
        fellBack = true;
        Log.i(MainActivity.TAG, pkg + ": fallback card (" + reason + ")");
        release();
        notice.setVisibility(GONE);
        fallbackView = fallback.create(getContext());
        addView(fallbackView, 0, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView why = Ui.text(getContext(), 11, Ui.MUTED, false);
        why.setText(reason);
        int p = Ui.dp(getContext(), 10);
        why.setPadding(p, p, p, p);
        addView(why, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
    }

    private void addExpandButton() {
        TextView expand = Ui.button(getContext(), "⤢");
        expand.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        expand.setTextColor(Ui.MUTED);
        expand.setContentDescription("Deschide pe tot ecranul");
        expand.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                expand();
            }
        });
        addView(expand, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END));
    }

    private String lastRect;

    /**
     * Logs the slot's on-screen rectangle (logical px) for the pixel judge, and in test mode
     * (--ef testwidth 0.5) squeezes the embedded view to a fraction of the slot to imitate the
     * half-width bug, so the judge can be shown to fail on it.
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int[] at = new int[2];
        getLocationOnScreen(at);
        String rect = at[0] + " " + at[1] + " " + (at[0] + getWidth()) + " " + (at[1] + getHeight());
        if (!rect.equals(lastRect)) {
            lastRect = rect;
            Log.i(MainActivity.TAG, "slot-rect " + label + " " + rect);
        }
        View embedded = textureView != null ? textureView : activityView;
        if (embedded != null && MainActivity.testWidth < 1f) {
            final View v = embedded;
            final int want = Math.round(getWidth() * MainActivity.testWidth);
            if (v.getLayoutParams().width != want) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        LayoutParams lp = (LayoutParams) v.getLayoutParams();
                        lp.width = want;
                        lp.gravity = Gravity.TOP | Gravity.START;
                        v.setLayoutParams(lp);
                    }
                });
            }
        }
    }

    void release() {
        stopEscapeGuard();
        if (textureView != null) {
            textureView.release();
            removeView(textureView);
            textureView = null;
        }
        if (activityView != null) {
            try {
                activityView.release();
            } catch (Throwable t) {
                Log.w(MainActivity.TAG, "release failed", t);
            }
            removeView(activityView);
            activityView = null;
        }
        ready = false;
    }
}
