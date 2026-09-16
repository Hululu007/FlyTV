package android.net;

/** ConnectivityManager 桩。 */
public class ConnectivityManager {

    public static final int TYPE_WIFI = 1;
    public static final int TYPE_MOBILE = 0;

    public NetworkInfo getActiveNetworkInfo() { return new NetworkInfo(); }

    public NetworkInfo getNetworkInfo(int type) { return new NetworkInfo(); }

    public boolean getMobileDataEnabled() { return true; }
}
