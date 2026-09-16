package android.content.pm;

/** PackageManager 桩（顶层类，与 Android 一致；NameNotFoundException 为其内部类）。 */
public class PackageManager {

    public static final int GET_META_DATA = 128;
    public static final int GET_ACTIVITIES = 1;

    public PackageInfo getPackageInfo(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return new PackageInfo();
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws PackageManager.NameNotFoundException {
        return new ApplicationInfo();
    }

    public CharSequence getApplicationLabel(ApplicationInfo info) { return "TVBox"; }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException() { }
        public NameNotFoundException(String msg) { super(msg); }
    }
}
