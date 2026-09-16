package android.app;

/** Activity 桩：仅保证类可加载与继承关系。 */
public class Activity extends android.content.Context {

    public android.view.Window getWindow() { return new android.view.Window(); }

    public void setContentView(android.view.View view) { }

    public void setContentView(int layoutResID) { }

    public Object getSystemService(String name) { return null; }

    public void runOnUiThread(Runnable action) { try { action.run(); } catch (Throwable ignored) { } }

    public void finish() { }

    public android.content.Intent getIntent() { return new android.content.Intent(); }
}
