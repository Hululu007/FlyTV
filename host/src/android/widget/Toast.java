package android.widget;

import android.content.Context;

/** Toast 桩。 */
public class Toast {

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static Toast makeText(Context context, CharSequence text, int duration) {
        System.out.println("[toast] " + text);
        return new Toast();
    }

    public void show() { }

    public void setText(CharSequence text) { }
}
