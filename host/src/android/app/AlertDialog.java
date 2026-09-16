package android.app;

/** AlertDialog 桩（含 Builder）。 */
public class AlertDialog extends Dialog {

    public static class Builder {

        public Builder(android.content.Context context) { }

        public Builder setTitle(CharSequence title) { return this; }

        public Builder setMessage(CharSequence msg) { return this; }

        public Builder setPositiveButton(CharSequence text, Object listener) { return this; }

        public Builder setNegativeButton(CharSequence text, Object listener) { return this; }

        public Builder setView(android.view.View view) { return this; }

        public Builder setCancelable(boolean flag) { return this; }

        public AlertDialog create() { return new AlertDialog(); }

        public AlertDialog show() { return new AlertDialog(); }
    }
}
