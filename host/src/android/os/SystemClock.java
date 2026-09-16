package android.os;

/** SystemClock 桩。 */
public class SystemClock {

    public static long currentThreadTimeMillis() { return System.currentTimeMillis(); }

    public static long elapsedRealtime() { return System.currentTimeMillis(); }

    public static long uptimeMillis() { return System.currentTimeMillis(); }

    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
