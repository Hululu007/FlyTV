package android.os;

/** android.os.Looper 桩：getMainLooper 可用；loop() 挂起线程（防 spinner 线程空转）。 */
public class Looper {

    private static final Looper MAIN = new Looper();
    private static final ThreadLocal<Looper> sThreadLocal = new ThreadLocal<>();

    public static void prepare() { if (sThreadLocal.get() == null) sThreadLocal.set(new Looper()); }

    public static void loop() {
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException ignored) { }
    }

    public static Looper myLooper() {
        Looper l = sThreadLocal.get();
        return l != null ? l : MAIN;
    }

    public static Looper getMainLooper() { return MAIN; }

    public boolean isCurrentThread() { return true; }

    public void quit() { }
}
