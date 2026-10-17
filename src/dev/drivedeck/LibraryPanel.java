package dev.drivedeck;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

import com.spotify.protocol.types.ListItem;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Spotify library over the map card (CarPlay-style list of big tiles). Top row: Spotify's car
 * categories; below: tiles of the selected category. A playable tile starts playback and closes
 * the panel; a folder tile opens its children (back arrow returns).
 */
final class LibraryPanel extends LinearLayout {
    interface Listener {
        void onClose();
    }

    private static final int COLUMNS = 4;

    private final SpotifyLibrary spotify;
    private final Listener listener;
    private final LinearLayout categories;
    private final GridLayout grid;
    private final TextView title;
    private final TextView back;
    private final TextView status;
    private final Deque<ListItem> path = new ArrayDeque<>();
    private ListItem[] roots = new ListItem[0];
    private int selectedRoot;

    LibraryPanel(Context c, SpotifyLibrary spotify, Listener listener) {
        super(c);
        this.spotify = spotify;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackground(Ui.card(c));
        int p = Ui.dp(c, 14);
        setPadding(p, p, p, p);
        setClickable(true); // don't let taps fall through to Waze underneath

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        back = Ui.button(c, "‹");
        back.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        back.setVisibility(GONE);
        back.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { goBack(); }
        });
        header.addView(back, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        title = Ui.text(c, 22, Ui.TEXT, true);
        title.setText("Spotify");
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView close = Ui.button(c, "✕");
        close.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        close.setTextColor(Ui.MUTED);
        close.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { LibraryPanel.this.listener.onClose(); }
        });
        header.addView(close, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        addView(header);

        HorizontalScrollView catScroll = new HorizontalScrollView(c);
        catScroll.setHorizontalScrollBarEnabled(false);
        categories = new LinearLayout(c);
        catScroll.addView(categories);
        LayoutParams catLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        catLp.topMargin = Ui.dp(c, 8);
        catLp.bottomMargin = Ui.dp(c, 12);
        addView(catScroll, catLp);

        status = Ui.text(c, 16, Ui.MUTED, false);
        status.setGravity(Gravity.CENTER);
        addView(status, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView gridScroll = new ScrollView(c);
        grid = new GridLayout(c);
        grid.setColumnCount(COLUMNS);
        gridScroll.addView(grid);
        addView(gridScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Connects (first time: Spotify may ask to authorise) and loads the car categories. */
    void open() {
        path.clear();
        back.setVisibility(GONE);
        title.setText("Spotify");
        showStatus("Se conectează la Spotify…");
        spotify.connect(getContext(), new SpotifyLibrary.Status() {
            @Override public void onConnected() {
                post(new Runnable() { @Override public void run() { loadRoots(); } });
            }
            @Override public void onFailure(final String why) {
                post(new Runnable() { @Override public void run() { showStatus("Nu merge conexiunea: " + why); } });
            }
        });
    }

    private void loadRoots() {
        showStatus("Se încarcă…");
        spotify.recommended(new SpotifyLibrary.Callback<ListItem[]>() {
            @Override public void onResult(final ListItem[] items) {
                post(new Runnable() { @Override public void run() { setRoots(items); } });
            }
        });
    }

    private void setRoots(ListItem[] items) {
        roots = items;
        categories.removeAllViews();
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView chip = Ui.text(getContext(), 15, Ui.TEXT, true);
            chip.setText(items[i].title);
            int ph = Ui.dp(getContext(), 14), pv = Ui.dp(getContext(), 8);
            chip.setPadding(ph, pv, ph, pv);
            chip.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { selectRoot(index); }
            });
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(getContext(), 8);
            categories.addView(chip, lp);
        }
        if (items.length == 0) showStatus("Spotify n-a trimis nimic pentru mașină");
        else selectRoot(0);
    }

    private void selectRoot(int index) {
        selectedRoot = index;
        for (int i = 0; i < categories.getChildCount(); i++) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(getContext(), 14));
            bg.setColor(i == index ? Ui.TEXT : Ui.CARD2);
            categories.getChildAt(i).setBackground(bg);
            ((TextView) categories.getChildAt(i)).setTextColor(i == index ? Ui.BG : Ui.TEXT);
        }
        path.clear();
        back.setVisibility(GONE);
        title.setText("Spotify");
        openItem(roots[index]);
    }

    private void openItem(ListItem item) {
        showStatus("Se încarcă…");
        spotify.children(item, new SpotifyLibrary.Callback<ListItem[]>() {
            @Override public void onResult(final ListItem[] items) {
                post(new Runnable() { @Override public void run() { showTiles(items); } });
            }
        });
    }

    private void goBack() {
        if (path.isEmpty()) return;
        path.pop();
        if (path.isEmpty()) {
            selectRoot(selectedRoot);
        } else {
            title.setText(path.peek().title);
            openItem(path.peek());
        }
    }

    private void showTiles(ListItem[] items) {
        grid.removeAllViews();
        status.setVisibility(items.length == 0 ? VISIBLE : GONE);
        if (items.length == 0) status.setText("Gol");
        int gap = Ui.dp(getContext(), 10);
        int tileW = (getWidth() - getPaddingLeft() - getPaddingRight() - gap * (COLUMNS - 1)) / COLUMNS;
        if (tileW <= 0) tileW = Ui.dp(getContext(), 150);
        for (final ListItem item : items) {
            LinearLayout tile = new LinearLayout(getContext());
            tile.setOrientation(VERTICAL);
            final ImageView cover = new ImageView(getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Ui.dp(getContext(), 12));
            bg.setColor(Ui.CARD2);
            cover.setBackground(bg);
            tile.addView(cover, new LayoutParams(tileW, tileW));
            TextView name = Ui.text(getContext(), 15, Ui.TEXT, true);
            name.setText(item.title);
            name.setMaxLines(2);
            name.setPadding(0, Ui.dp(getContext(), 6), 0, 0);
            tile.addView(name, new LayoutParams(tileW, LayoutParams.WRAP_CONTENT));
            tile.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { onTile(item); }
            });
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = tileW;
            glp.setMargins(0, 0, gap, gap);
            grid.addView(tile, glp);
            spotify.cover(item, new SpotifyLibrary.Callback<Bitmap>() {
                @Override public void onResult(final Bitmap b) {
                    cover.post(new Runnable() { @Override public void run() { cover.setImageBitmap(b); } });
                }
            });
        }
    }

    private void onTile(ListItem item) {
        if (item.playable) {
            spotify.play(item);
            listener.onClose();
        } else if (item.hasChildren) {
            path.push(item);
            back.setVisibility(VISIBLE);
            title.setText(item.title);
            openItem(item);
        }
    }

    private void showStatus(String s) {
        grid.removeAllViews();
        status.setText(s);
        status.setVisibility(VISIBLE);
    }

    /** Used by MainActivity to place the panel over the map card. */
    static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
