package android.view;

/** View 桩（WebView 等类的继承基座）。 */
public class View {

    protected android.content.Context context;

    public View(android.content.Context context) { this.context = context; }

    public android.content.Context getContext() { return context; }

    public void setVisibility(int visibility) { }

    public int getVisibility() { return 0; }

    public void postInvalidate() { }

    public boolean post(Runnable action) { try { action.run(); } catch (Throwable ignored) { } return true; }

    public interface OnClickListener { void onClick(View v); }

    public interface OnLongClickListener { boolean onLongClick(View v); }
}
