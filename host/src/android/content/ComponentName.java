package android.content;

/** ComponentName 桩。 */
public class ComponentName {

    public ComponentName(String pkg, String cls) { }

    public ComponentName(android.content.Context pkg, Class<?> cls) { }

    public String getPackageName() { return "com.github.catvod"; }

    public String getClassName() { return ""; }
}
