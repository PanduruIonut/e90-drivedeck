package dev.drivedeck;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/**
 * Test hook for the car session, only reachable from adb/system (protected by DUMP in the manifest):
 *
 *   adb shell am broadcast -n dev.drivedeck/.DebugReceiver --ei hwoverlays 0   # disable HW overlays
 *   adb shell am broadcast -n dev.drivedeck/.DebugReceiver --ei hwoverlays 1   # enable again
 *   adb shell am broadcast -n dev.drivedeck/.DebugReceiver                     # report state
 *
 * The shell may not do this itself (SurfaceFlinger wants HARDWARE_TEST); the platform-signed app may.
 * It is the switch behind Developer options → "Disable HW overlays", and it resets on reboot.
 */
public class DebugReceiver extends BroadcastReceiver {
    private static final String COMPOSER = "android.ui.ISurfaceComposer";

    @Override
    public void onReceive(Context context, Intent intent) {
        String result;
        try {
            IBinder sf = surfaceFlinger();
            if (intent.hasExtra("hwoverlays")) {
                boolean enable = intent.getIntExtra("hwoverlays", 1) != 0;
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken(COMPOSER);
                data.writeInt(enable ? 0 : 1); // 1008: 1 = disable HWC overlays
                sf.transact(1008, data, null, 0);
                data.recycle();
            }
            // The authoritative state is `dumpsys SurfaceFlinger` ("h/w composer enabled/disabled");
            // the 1010 reply layout differs between releases, so it's only reported raw.
            result = "ok, 1010=" + interrogate(sf);
        } catch (Exception e) {
            result = "error: " + e;
            Log.w(MainActivity.TAG, "debug receiver failed", e);
        }
        Log.i(MainActivity.TAG, "debug: " + result);
        setResultData(result);
    }

    private static IBinder surfaceFlinger() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        return (IBinder) sm.getMethod("getService", String.class).invoke(null, "SurfaceFlinger");
    }

    /** Raw ints of SurfaceFlinger's 1010 "interrogate" reply, for the log. */
    private static String interrogate(IBinder sf) throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(COMPOSER);
            sf.transact(1010, data, reply, 0);
            StringBuilder sb = new StringBuilder("[");
            while (reply.dataAvail() >= 4) sb.append(reply.readInt()).append(reply.dataAvail() >= 4 ? "," : "");
            return sb.append("]").toString();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
