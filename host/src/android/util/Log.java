package android.util;

/** android.util.Log 桩：输出到宿主控制台。 */
public class Log {

    public static int v(String tag, String msg) { return println(2, tag, msg); }
    public static int d(String tag, String msg) { return println(3, tag, msg); }
    public static int i(String tag, String msg) { return println(4, tag, msg); }
    public static int w(String tag, String msg) { return println(5, tag, msg); }
    public static int e(String tag, String msg) { return println(6, tag, msg); }
    public static int w(String tag, String msg, Throwable t) { return println(5, tag, msg + " / " + t); }
    public static int e(String tag, String msg, Throwable t) { return println(6, tag, msg + " / " + t); }
    public static int e(String tag, Throwable t) { return println(6, tag, String.valueOf(t)); }

    static int println(int priority, String tag, String msg) {
        System.out.println("[spider][" + tag + "] " + msg);
        return msg == null ? 0 : msg.length();
    }
}
