package dev.drivedeck;

import android.content.ComponentName;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Moves an app's existing task stack onto one of our virtual displays (hidden Android 9 API,
 * needs MANAGE_ACTIVITY_STACKS + INTERNAL_SYSTEM_WINDOW). Without this, an app that was
 * already running full screen stays there instead of coming into its slot.
 */
final class Tasks {
    private Tasks() {}

    static void moveToDisplay(String pkg, int displayId) {
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object service = am.getMethod("getService").invoke(null);
            List<?> stacks = (List<?>) service.getClass().getMethod("getAllStackInfos").invoke(service);
            Method move = service.getClass().getMethod("moveStackToDisplay", int.class, int.class);
            for (Object info : stacks) {
                Class<?> k = info.getClass();
                ComponentName top = (ComponentName) field(k, "topActivity").get(info);
                int stackDisplay = field(k, "displayId").getInt(info);
                int stackId = field(k, "stackId").getInt(info);
                if (top != null && pkg.equals(top.getPackageName()) && stackDisplay != displayId) {
                    move.invoke(service, stackId, displayId);
                    Log.i(MainActivity.TAG, pkg + ": moved stack " + stackId + " from display "
                            + stackDisplay + " to " + displayId);
                }
            }
        } catch (Exception e) {
            Log.w(MainActivity.TAG, pkg + ": could not move its task to display " + displayId, e);
        }
    }

    /** True if any task stack lives on this display; on error, true (so callers don't relaunch blindly). */
    static boolean hasTaskOnDisplay(int displayId) {
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object service = am.getMethod("getService").invoke(null);
            List<?> stacks = (List<?>) service.getClass().getMethod("getAllStackInfos").invoke(service);
            for (Object info : stacks) {
                Class<?> k = info.getClass();
                int[] taskIds = (int[]) field(k, "taskIds").get(info);
                if (field(k, "displayId").getInt(info) == displayId && taskIds != null && taskIds.length > 0) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "could not list stacks", e);
            return true;
        }
    }

    private static Field field(Class<?> k, String name) throws NoSuchFieldException {
        Field f = k.getField(name);
        f.setAccessible(true);
        return f;
    }
}
