package android.app;

import android.content.Context;
import android.content.Intent;
import android.view.ViewGroup;

/**
 * Compile-time stub only (never packaged). The real class is the hidden
 * android.app.ActivityView from the Android 9 framework on the head unit.
 */
public class ActivityView extends ViewGroup {
    public ActivityView(Context context) { super(context); throw new RuntimeException("stub"); }

    public abstract static class StateCallback {
        public abstract void onActivityViewReady(ActivityView view);
        public abstract void onActivityViewDestroyed(ActivityView view);
    }

    public void setCallback(StateCallback callback) { throw new RuntimeException("stub"); }
    public void startActivity(Intent intent) { throw new RuntimeException("stub"); }
    public void release() { throw new RuntimeException("stub"); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) { }
}
