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

    /** Returns how many of the app's stacks are now on this display (moved or already there); 0 if none. */
    static int moveToDisplay(String pkg, int displayId) {
        int found = 0;
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
                if (top == null || !pkg.equals(top.getPackageName())) continue;
                found++;
                if (stackDisplay != displayId) {
                    move.invoke(service, stackId, displayId);
                    Log.i(MainActivity.TAG, pkg + ": moved stack " + stackId + " from display "
                            + stackDisplay + " to " + displayId);
                }
            }
        } catch (Exception e) {
            Log.w(MainActivity.TAG, pkg + ": could not move its task to display " + displayId, e);
        }
        return found;
    }

    /**
     * If the top of the main display is not our package, focuses the stack holding our task, which
     * puts it back on top (IActivityManager.setFocusedStack, MANAGE_ACTIVITY_STACKS). Stacks come
     * top-first for each display in getAllStackInfos.
     */
    static void keepOnTopOfMainDisplay(int ourTaskId, String ourPkg) {
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object service = am.getMethod("getService").invoke(null);
            List<?> stacks = (List<?>) service.getClass().getMethod("getAllStackInfos").invoke(service);
            String top = null;
            int ourStack = -1;
            for (Object info : stacks) {
                Class<?> k = info.getClass();
                if (field(k, "displayId").getInt(info) != 0) continue;
                ComponentName c = (ComponentName) field(k, "topActivity").get(info);
                if (top == null && c != null) top = c.getPackageName();
                for (int id : (int[]) field(k, "taskIds").get(info)) {
                    if (id == ourTaskId) ourStack = field(k, "stackId").getInt(info);
                }
            }
            if (ourStack < 0 || ourPkg.equals(top)) return;
            service.getClass().getMethod("setFocusedStack", int.class).invoke(service, ourStack);
            Log.i(MainActivity.TAG, top + " came over the dashboard; stack " + ourStack + " put back on top");
        } catch (Exception e) {
            Log.w(MainActivity.TAG, "could not put the dashboard back on top", e);
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
