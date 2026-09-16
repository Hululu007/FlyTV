package android.app;

/** ProgressDialog 桩。 */
public class ProgressDialog extends AlertDialog {

    public ProgressDialog(android.content.Context context) { }

    public void setMessage(CharSequence msg) { }

    public static ProgressDialog show(android.content.Context context, CharSequence title, CharSequence message) {
        return new ProgressDialog(context);
    }
}
