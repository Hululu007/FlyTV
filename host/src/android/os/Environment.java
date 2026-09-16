package android.os;

/** Environment/Process/SystemClock 桩。 */
public class Environment {

    public static final String MEDIA_MOUNTED = "mounted";

    public static java.io.File getExternalStorageDirectory() { return new java.io.File(System.getProperty("java.io.tmpdir")); }

    public static String getExternalStorageState() { return MEDIA_MOUNTED; }

    public static java.io.File getDownloadCacheDirectory() { return new java.io.File(System.getProperty("java.io.tmpdir")); }
}
