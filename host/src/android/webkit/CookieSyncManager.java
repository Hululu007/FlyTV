package android.webkit;

/** CookieSyncManager 桩。 */
public class CookieSyncManager {

    private static final CookieSyncManager INSTANCE = new CookieSyncManager();

    public static CookieSyncManager createInstance(android.content.Context context) { return INSTANCE; }

    public static CookieSyncManager getInstance() { return INSTANCE; }

    public void sync() { }

    public void startSync() { }

    public void stopSync() { }
}
