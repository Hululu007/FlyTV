package android.provider;

/** Settings.Secure 桩。 */
public class Settings {

    public static class Secure {

        public static final String ANDROID_ID = "android_id";

        public static String getString(android.content.ContentResolver resolver, String name) {
            return "tvbox-desktop";
        }
    }
}
