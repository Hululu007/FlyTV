package android.net;

/** NetworkInfo 桩（Android 中为顶层类）。 */
public class NetworkInfo {

    public static final int TYPE_WIFI = 1;
    public static final int TYPE_MOBILE = 0;

    public boolean isConnected() { return true; }
    public boolean isAvailable() { return true; }
    public boolean isConnectedOrConnecting() { return true; }
    public int getType() { return TYPE_WIFI; }
    public android.net.NetworkInfo.DetailedState getDetailedState() { return DetailedState.CONNECTED; }

    public enum DetailedState { IDLE, SCANNING, CONNECTING, AUTHENTICATING, OBTAINING_IPADDR, CONNECTED, SUSPENDED, DISCONNECTED, FAILED, BLOCKED }
}
