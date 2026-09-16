package android.security;

/** NetworkSecurityPolicy 桩。 */
public class NetworkSecurityPolicy {

    private static final NetworkSecurityPolicy INSTANCE = new NetworkSecurityPolicy();

    public static NetworkSecurityPolicy getInstance() { return INSTANCE; }

    public boolean isCleartextTrafficPermitted() { return true; }

    public boolean isCleartextTrafficPermitted(String hostname) { return true; }
}
