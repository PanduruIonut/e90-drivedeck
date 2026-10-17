package dev.drivedeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.TypedValue;

import java.util.List;

/**
 * "Now Playing" card for the current music source: cover, source, title, artist, progress
 * and transport controls, read from the app's media session (MEDIA_CONTENT_CONTROL).
 * Tapping the cover or title opens the music app full screen to pick something to play.
 */
final class NowPlayingCard extends LinearLayout {
    private String source;
    private final ImageView art;
    private final TextView src;
    private final TextView title;
    private final TextView artist;
    private final View progressFill;
    private final FrameLayout progress;
    private final TextView playPause;
    private MediaSessionManager sessions;
    private MediaController controller;
    /** For Spotify, tapping the cover/title opens our library screen instead of the Spotify app. */
    Runnable libraryOpener;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> list) { pick(list); }
            };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { render(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { render(); }
        @Override public void onSessionDestroyed() { setController(null); }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateProgress();
            postDelayed(this, 1000);
        }
    };

    NowPlayingCard(Context c, String source) {
        super(c);
        this.source = source;
        setOrientation(VERTICAL);
        setBackground(Ui.card(c));
        int p = Ui.dp(c, 16);
        setPadding(p, p, p, p);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setCornerRadius(Ui.dp(c, 14));
        artBg.setColor(Ui.CARD2);
        art.setBackground(artBg);
        int s = Ui.dp(c, 118);
        top.addView(art, new LayoutParams(s, s));

        LinearLayout meta = new LinearLayout(c);
        meta.setOrientation(VERTICAL);
        meta.setPadding(Ui.dp(c, 14), 0, 0, 0);
        src = Ui.text(c, 13, Ui.GREEN, true);
        meta.addView(src);
        title = Ui.text(c, 22, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setPadding(0, Ui.dp(c, 6), 0, 0);
        meta.addView(title);
        artist = Ui.text(c, 16, Ui.MUTED, false);
        artist.setSingleLine(true);
        artist.setPadding(0, Ui.dp(c, 3), 0, 0);
        meta.addView(artist);
        top.addView(meta, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        addView(top);

        progress = new FrameLayout(c);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(Ui.dp(c, 3));
        track.setColor(Ui.CARD2);
        progress.setBackground(track);
        progressFill = new View(c);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(Ui.dp(c, 3));
        fill.setColor(Ui.TEXT);
        progressFill.setBackground(fill);
        progress.addView(progressFill, new FrameLayout.LayoutParams(0, LayoutParams.MATCH_PARENT));
        LayoutParams barLp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 5));
        barLp.topMargin = Ui.dp(c, 14);
        addView(progress, barLp);

        LinearLayout controls = new LinearLayout(c);
        controls.setGravity(Gravity.CENTER);
        TextView prev = Ui.button(c, "⏮");
        playPause = Ui.button(c, "▶");
        playPause.setTextColor(Ui.BG);
        playPause.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Ui.TEXT);
        playPause.setBackground(circle);
        TextView next = Ui.button(c, "⏭");
        int big = Ui.dp(c, 64);
        controls.addView(prev, new LayoutParams(0, big, 1f));
        controls.addView(playPause, new LayoutParams(big, big));
        controls.addView(next, new LayoutParams(0, big, 1f));
        LayoutParams cLp = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        addView(controls, cLp);

        OnClickListener open = new OnClickListener() {
            @Override public void onClick(View v) {
                if (Apps.SPOTIFY.equals(source) && libraryOpener != null) libraryOpener.run();
                else Apps.openFullScreen(getContext(), source);
            }
        };
        art.setOnClickListener(open);
        meta.setOnClickListener(open);
        prev.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { if (controller != null) controller.getTransportControls().skipToPrevious(); }
        });
        next.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { if (controller != null) controller.getTransportControls().skipToNext(); }
        });
        playPause.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        render();
    }

    void setSource(String pkg) {
        source = pkg;
        if (sessions != null) {
            try {
                pick(sessions.getActiveSessions(null));
            } catch (SecurityException e) {
                Log.w(MainActivity.TAG, "media sessions not readable", e);
            }
        }
        render();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        sessions = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, null);
            pick(sessions.getActiveSessions(null));
        } catch (SecurityException e) {
            Log.w(MainActivity.TAG, "media sessions not readable", e);
        }
        post(tick);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        setController(null);
        super.onDetachedFromWindow();
    }

    /** Only the current source's session is shown, never another app's. */
    private void pick(List<MediaController> list) {
        MediaController chosen = null;
        for (MediaController mc : list) {
            if (source.equals(mc.getPackageName())) { chosen = mc; break; }
        }
        setController(chosen);
    }

    private void setController(MediaController mc) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = mc;
        if (controller != null) controller.registerCallback(callback);
        render();
    }

    private void togglePlay() {
        if (controller == null) {
            Apps.openFullScreen(getContext(), source);
            return;
        }
        PlaybackState st = controller.getPlaybackState();
        if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
    }

    private void render() {
        src.setText("● " + Apps.label(source));
        src.setTextColor(Apps.SPOTIFY.equals(source) ? Ui.GREEN : Ui.RED);
        MediaMetadata md = controller == null ? null : controller.getMetadata();
        if (md == null) {
            art.setImageBitmap(null);
            title.setText("Nothing playing");
            artist.setText("Tap to open " + Apps.label(source));
            playPause.setText("▶");
            updateProgress();
            return;
        }
        Bitmap cover = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null) cover = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (cover == null) cover = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art.setImageBitmap(cover);
        title.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE));
        artist.setText(md.getString(MediaMetadata.METADATA_KEY_ARTIST));
        PlaybackState st = controller.getPlaybackState();
        boolean playing = st != null && st.getState() == PlaybackState.STATE_PLAYING;
        playPause.setText(playing ? "❚❚" : "▶");
        updateProgress();
    }

    private void updateProgress() {
        float fraction = 0f;
        if (controller != null && controller.getMetadata() != null && controller.getPlaybackState() != null) {
            long duration = controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            PlaybackState st = controller.getPlaybackState();
            long pos = st.getPosition();
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
            }
            if (duration > 0) fraction = Math.max(0f, Math.min(1f, pos / (float) duration));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
        int w = Math.round(progress.getWidth() * fraction);
        if (lp.width != w) {
            lp.width = w;
            progressFill.setLayoutParams(lp);
        }
    }
}
