package dev.drivedeck;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
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

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        int dpi = getResources().getDisplayMetrics().densityDpi;
        if (display == null) {
            DisplayManager dm = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
            display = dm.createVirtualDisplay("DriveDeck-" + name, w, h, dpi, surface, FLAGS);
            Log.i(MainActivity.TAG, name + ": virtual display " + displayId() + " " + w + "x" + h + " @" + dpi);
            createInputForwarder();
        } else {
            display.setSurface(surface);
            display.resize(w, h, dpi);
        }
        listener.onDisplayReady(this);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        if (display != null) display.resize(w, h, getResources().getDisplayMetrics().densityDpi);
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

    /** Starts the app on our display; if it already has a task elsewhere, moves that task here. */
    void launch(Intent launch, String pkg) {
        int id = displayId();
        if (id < 0) return;
        ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setLaunchDisplayId(id);
        getContext().startActivity(new Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle());
        Tasks.moveToDisplay(pkg, id);
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
