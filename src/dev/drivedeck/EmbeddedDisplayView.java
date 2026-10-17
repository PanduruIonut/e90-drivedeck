package dev.drivedeck;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;

import java.lang.reflect.Method;

/**
 * Hosts another app on our own virtual display, drawn through a TextureView.
 *
 * Why not ActivityView: on the head unit the SurfaceView layer that ActivityView uses is
 * composed by the vendor's hardware composer at the wrong scale (the panel is 1280x480 while
 * the system runs at 1920x720). A TextureView is drawn by our own window on the GPU, so the
 * hardware composer only ever sees DriveDeck's single window. Owning the display also gives
 * us its id, which lets us pull an app's existing task into the slot.
 */
final class EmbeddedDisplayView extends TextureView implements TextureView.SurfaceTextureListener {
    interface Listener {
        void onDisplayReady(EmbeddedDisplayView view);
    }

    // DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, as ActivityView uses.
    private static final int FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;

    private final String name;
    private final Listener listener;
    private VirtualDisplay display;
    private Surface surface;
    private Object inputForwarder;
    private Method forwardEvent;

    EmbeddedDisplayView(Context c, String name, Listener listener) {
        super(c);
        this.name = name;
        this.listener = listener;
        setSurfaceTextureListener(this);
        setOpaque(false);
    }

    int displayId() {
        return display == null ? -1 : display.getDisplay().getDisplayId();
    }

    boolean isReady() {
        return display != null && surface != null;
    }

    /**
     * Virtual display size relative to the view (--ef vdscale, default 1). A car-side knob in case the
     * unit still renders the embedded app at the wrong size: e.g. 0.6667 creates the display at the
     * panel's physical pixel size. The TextureView scales the frames to fill the view either way, and
     * touches are scaled by the same factor.
     */
    static float scale = 1f;

    private int scaled(int px) {
        return Math.max(1, Math.round(px * scale));
    }

    /** --ei vddpi N: virtual display density independent of its size (0 = follow the scale). */
    static int dpiOverride = 0;

    private int dpi() {
        if (dpiOverride > 0) return dpiOverride;
        return scaled(getResources().getDisplayMetrics().densityDpi);
    }

    /**
     * The 825X_Pro head unit puts every virtual display's image into a frame the size of the main
     * display (1920x720 logical): the app occupies only the top-left vdW x vdH of it, and that whole
     * frame is then stretched into our view. Measured on the unit for five display sizes (width
     * fraction = vdW/1920, height = vdH/720, density irrelevant). The fix scales the texture back by
     * 1920/vdW and 720/vdH. Touch mapping is unchanged (content then lines up with the view again).
     * On by default on that model only; --ez vdfix true|false overrides.
     */
    static Boolean vdFixOverride = null;

    static boolean vdFixEnabled() {
        if (vdFixOverride != null) return vdFixOverride;
        return "825X_Pro".equals(android.os.Build.MODEL);
    }

    private void applyVdFix(int viewW, int viewH) {
        if (!vdFixEnabled()) {
            setTransform(null);
            return;
        }
        android.graphics.Point main = new android.graphics.Point();
        ((android.view.WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealSize(main);
        float fx = (float) main.x / scaled(viewW);
        float fy = (float) main.y / scaled(viewH);
        Matrix m = new Matrix();
        m.setScale(fx, fy);
        setTransform(m);
        Log.i(MainActivity.TAG, name + ": vdfix main " + main.x + "x" + main.y + " -> scale " + fx + " x " + fy);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        applyVdFix(w, h);
        st.setDefaultBufferSize(scaled(w), scaled(h));
        surface = new Surface(st);
        if (display == null) {
            DisplayManager dm = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
            display = dm.createVirtualDisplay("DriveDeck-" + name, scaled(w), scaled(h), dpi(), surface, FLAGS);
            Log.i(MainActivity.TAG, name + ": virtual display " + displayId() + " " + scaled(w) + "x" + scaled(h)
                    + " @" + dpi() + " (view " + w + "x" + h + ", scale " + scale + ")");
            createInputForwarder();
        } else {
            display.setSurface(surface);
            display.resize(scaled(w), scaled(h), dpi());
        }
        listener.onDisplayReady(this);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        applyVdFix(w, h);
        st.setDefaultBufferSize(scaled(w), scaled(h));
        if (display != null) display.resize(scaled(w), scaled(h), dpi());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        // Keep the display (and the app on it) alive while we're hidden; just detach the surface.
        if (display != null) display.setSurface(null);
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    /**
     * Brings the app onto our display. An app that already has a task is only moved here, never
     * started again: starting an existing task brings it to the front, and for a task the vendor
     * launcher started (NEW_TASK|TASK_ON_HOME) Android 9 then also brings the Home stack to the front
     * of the main display, over DriveDeck ("bringingFoundTaskToFront returnToHome", 17 oct car
     * session; repro-return-home.sh). Only an app with no task is started. Moving such a task does
     * the same ("reparent adjustFocusToNextFocusableStack returnToHome"), so afterwards DriveDeck is
     * put back on top of the main display if anything came over it. Returns true if the app had no
     * task and was started (a cold start).
     */
    boolean launch(Intent launch, String pkg) {
        int id = displayId();
        if (id < 0) return false;
        boolean started = false;
        if (Tasks.moveToDisplay(pkg, id)[0] == 0) {
            ActivityOptions opts = ActivityOptions.makeBasic();
            opts.setLaunchDisplayId(id);
            getContext().startActivity(new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle());
            started = true;
        }
        keepHostOnTop();
        return started;
    }

    /** If the app has left this display, moves it back and puts DriveDeck on top again; true if it had. */
    boolean pullBack(String pkg) {
        int id = displayId();
        if (id < 0 || Tasks.moveToDisplay(pkg, id)[1] == 0) return false;
        keepHostOnTop();
        return true;
    }

    /** Puts DriveDeck back on top of the main display if something came over it; true if it had to. */
    boolean keepHostOnTop() {
        return getContext() instanceof Activity
                && Tasks.keepOnTopOfMainDisplay(((Activity) getContext()).getTaskId(), getContext().getPackageName());
    }

    private void createInputForwarder() {
        try {
            Class<?> im = Class.forName("android.hardware.input.InputManager");
            Object manager = im.getMethod("getInstance").invoke(null);
            inputForwarder = im.getMethod("createInputForwarder", int.class).invoke(manager, displayId());
            forwardEvent = inputForwarder.getClass().getMethod("forwardEvent", InputEvent.class);
        } catch (Exception e) {
            Log.w(MainActivity.TAG, name + ": no input forwarder, touches won't reach the app", e);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (inputForwarder == null) return super.onTouchEvent(event);
        MotionEvent copy = MotionEvent.obtain(event);
        if (scale != 1f) {
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            copy.transform(m);
        }
        try {
            forwardEvent.invoke(inputForwarder, copy);
            return true;
        } catch (Exception e) {
            Log.w(MainActivity.TAG, name + ": forwarding touch failed", e);
            return false;
        } finally {
            copy.recycle();
        }
    }

    void release() {
        if (display != null) display.release();
        display = null;
        if (surface != null) surface.release();
        surface = null;
    }
}
