package android.content.pm;

/** PackageInfo 桩。 */
public class PackageInfo {

    public String packageName = "com.github.catvod";
    public ApplicationInfo applicationInfo = new ApplicationInfo();
    public int versionCode = 1;
    public String versionName = "1.0";

    public static class PackageManager {

        public static final int GET_META_DATA = 128;
        public static final int GET_ACTIVITIES = 1;

        public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
            return new PackageInfo();
        }

        public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
            return new ApplicationInfo();
        }

        public static class NameNotFoundException extends Exception {
            public NameNotFoundException() { }
            public NameNotFoundException(String msg) { super(msg); }
        }
    }
}
