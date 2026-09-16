package android.app;

import android.os.Bundle;

/** android.app.Application 桩：仅继承链需要（Context 子类）。 */
public class Application extends android.content.Context {

    private static Application sInstance;

    public Application() { if (sInstance == null) sInstance = this; }

    public static Application getInstance() { return sInstance; }

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, Bundle savedInstanceState);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, Bundle outState);
        void onActivityDestroyed(Activity activity);
    }

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) { }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) { }
}
