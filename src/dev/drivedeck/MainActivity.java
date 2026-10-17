package dev.drivedeck;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * DriveDeck home screen, layout A (design/a-dashboard.png):
 *   dock | Waze, embedded | Now Playing + speed + date
 *
 * Test with other apps in the map slot:
 *   adb shell am start -n dev.drivedeck/.MainActivity --es left com.android.settings
 */
public class MainActivity extends Activity {
    static final String TAG = "DriveDeck";
    /** --ez activityview true: use the stock ActivityView instead of our TextureView embedder (for comparison). */
    static boolean useActivityView;
    /** --ef testwidth 0.5: squeeze the embedded app to half the slot (negative control for the pixel judge). */
    static float testWidth = 1f;

    private static final String PREFS = "drivedeck";
    private static final String KEY_MUSIC = "music_source";

    private static final Locale RO = new Locale("ro", "RO");

    private TextView speed;
    private TextView day;
    private TextView month;
    private AppSlot navSlot;

    /** The unit's locale is English; the date card is always Romanian ("vin. 17" / "octombrie"). */
    private final Runnable dateTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String d = new SimpleDateFormat("EEE d", RO).format(now);
            day.setText(d.substring(0, 1).toUpperCase(RO) + d.substring(1));
            month.setText(new SimpleDateFormat("LLLL", RO).format(now));
            day.postDelayed(this, 60_000);
        }
    };
    private NowPlayingCard nowPlaying;
    private final SpotifyLibrary spotify = new SpotifyLibrary();
    private LibraryPanel library;
    private Dock dock;
    private String musicSource;
    private SharedPreferences prefs;
    private LocationManager locations;

    private final LocationListener gps = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            if (l.hasSpeed()) speed.setText(String.valueOf(Math.round(l.getSpeed() * 3.6f)));
        }
        @Override public void onStatusChanged(String p, int s, Bundle b) {}
        @Override public void onProviderEnabled(String p) {}
        @Override public void onProviderDisabled(String p) { speed.setText("—"); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        useActivityView = getIntent().getBooleanExtra("activityview", false);
        EmbeddedDisplayView.scale = getIntent().getFloatExtra("vdscale", 1f);
        testWidth = getIntent().getFloatExtra("testwidth", 1f);
        String navPkg = extraOr("left", Apps.WAZE);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        musicSource = initialMusicSource();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        int gap = Ui.dp(this, 10);
        root.setPadding(0, gap, gap, gap);

        dock = new Dock(this, new Dock.Listener() {
            @Override public void onHome() { openVendorLauncher(); }
            @Override public void onApp(String pkg) { onDockApp(pkg); }
        });
        root.addView(dock, new LinearLayout.LayoutParams(Ui.dp(this, 92), ViewGroup.LayoutParams.MATCH_PARENT));

        navSlot = new AppSlot(this, navPkg, "Waze", false, new AppSlot.Fallback() {
            @Override public View create(Context c) { return new WazeCard(c); }
        });
        // Map card: Waze, with the Spotify library sliding over it when opened.
        FrameLayout mapArea = new FrameLayout(this);
        mapArea.addView(navSlot, LibraryPanel.fill());
        library = new LibraryPanel(this, spotify, new LibraryPanel.Listener() {
            @Override public void onClose() { library.setVisibility(View.GONE); }
        });
        library.setVisibility(View.GONE);
        mapArea.addView(library, LibraryPanel.fill());
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.75f);
        navLp.rightMargin = gap;
        root.addView(mapArea, navLp);

        root.addView(sideColumn(gap), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
        dock.setMusicSource(musicSource);
        Log.i(TAG, "dashboard up: nav=" + navPkg + " music=" + musicSource);
    }

    private View sideColumn(int gap) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        nowPlaying = new NowPlayingCard(this, musicSource);
        nowPlaying.libraryOpener = new Runnable() {
            @Override public void run() {
                library.setVisibility(View.VISIBLE);
                library.open();
            }
        };
        LinearLayout.LayoutParams npLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        npLp.bottomMargin = gap;
        col.addView(nowPlaying, npLp);

        LinearLayout small = new LinearLayout(this);
        LinearLayout speedCard = smallCard("Viteză");
        speed = Ui.text(this, 44, Ui.TEXT, true);
        speed.setText("—");
        speed.setPadding(0, Ui.dp(this, 4), 0, 0);
        speedCard.addView(speed);
        TextView unit = Ui.text(this, 15, Ui.MUTED, false);
        unit.setText("km/h");
        speedCard.addView(unit);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        sLp.rightMargin = gap;
        small.addView(speedCard, sLp);

        LinearLayout dateCard = smallCard("Azi");
        day = Ui.text(this, 32, Ui.TEXT, true);
        day.setPadding(0, Ui.dp(this, 8), 0, 0);
        dateCard.addView(day);
        month = Ui.text(this, 14, Ui.MUTED, false);
        month.setPadding(0, Ui.dp(this, 4), 0, 0);
        dateCard.addView(month);
        small.addView(dateCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        dateTick.run();

        col.addView(small, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 112)));
        return col;
    }

    private LinearLayout smallCard(String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this));
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 10));
        card.addView(Ui.label(this, label));
        return card;
    }

    /** Waze opens full screen; a music app becomes the only music source (the old one is stopped). */
    private void onDockApp(String pkg) {
        if (Apps.WAZE.equals(pkg)) {
            Apps.openFullScreen(this, pkg);
            return;
        }
        if (!Apps.installed(this, pkg)) return;
        if (!pkg.equals(musicSource)) {
            Apps.stop(this, musicSource);
            musicSource = pkg;
            prefs.edit().putString(KEY_MUSIC, pkg).apply();
            dock.setMusicSource(pkg);
            nowPlaying.setSource(pkg);
        }
        Apps.openFullScreen(this, pkg);
    }

    /** DriveDeck can be the Home app, so the grid icon opens the unit's own launcher (all apps, BMW menus). */
    private void openVendorLauncher() {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.android.launcher", "com.android.launcher2.Launcher"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "vendor launcher not found, opening Settings", e);
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private String initialMusicSource() {
        String saved = prefs.getString(KEY_MUSIC, null);
        if (saved != null && Apps.installed(this, saved)) return saved;
        for (String p : Apps.MUSIC) {
            if (Apps.installed(this, p)) return p;
        }
        return Apps.SPOTIFY;
    }

    private String extraOr(String key, String def) {
        String v = getIntent().getStringExtra(key);
        return v == null || v.isEmpty() ? def : v;
    }

    // Android 9 keeps a single resumed activity: touching an embedded app pauses this one.
    // So GPS follows start/stop (visible), not resume/pause, or the speed would freeze
    // while you use Waze. The slot is relaunched only when we come back from being hidden
    // (e.g. after an app was opened full screen), not on every touch.
    @Override
    protected void onStart() {
        super.onStart();
        startGps();
        navSlot.setWatching(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        navSlot.onHostResumed();
    }

    @Override
    protected void onStop() {
        navSlot.setWatching(false);
        if (locations != null) locations.removeUpdates(gps);
        super.onStop();
    }

    private void startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        locations = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, gps);
        } catch (RuntimeException e) {
            Log.w(TAG, "GPS unavailable", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override
    protected void onDestroy() {
        day.removeCallbacks(dateTick);
        spotify.disconnect();
        navSlot.release();
        super.onDestroy();
    }
}
