package android.content;

/** Intent 桩。 */
public class Intent {

    public Intent() { }

    public Intent(String action) { }

    public Intent(android.content.Context packageContext, Class<?> cls) { }

    public Intent setAction(String action) { return this; }

    public Intent putExtra(String name, String value) { return this; }

    public Intent putExtra(String name, long value) { return this; }

    public Intent setData(android.net.Uri data) { return this; }

    public String getStringExtra(String name) { return null; }
}
