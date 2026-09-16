package android.app;

/** android.app.ActivityThread 桩：TVBox 爬虫常反射此类获取 Application 上下文。 */
public class ActivityThread {

    private static final Application APP = new Application();
    private static final ActivityThread THREAD = new ActivityThread();

    /** 安卓内部活动表：爬虫反射取当前 Activity 用；桌面环境恒为空表（getActivity 返回 null，对话框路径自动跳过）。 */
    public final java.util.Map<Object, Object> mActivities = new java.util.HashMap<>();

    public static ActivityThread currentActivityThread() { return THREAD; }

    public static Application currentApplication() { return APP; }

    public static String currentProcessName() { return "com.github.catvod"; }

    public Application getApplication() { return APP; }
}
