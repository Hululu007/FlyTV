package android.net.wifi;

/** WifiManager 桩。 */
public class WifiManager {

    public WifiInfo getConnectionInfo() { return new WifiInfo(); }

    public boolean isWifiEnabled() { return false; }

    public Object createMulticastLock(String tag) { return new Object(); }

    @SuppressWarnings("unchecked")
    public java.util.List<ScanResult> getScanResults() { return new java.util.ArrayList<>(); }
}
