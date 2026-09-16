package android.widget;

import android.content.Context;
import android.view.View;

/** TextView 桩。 */
public class TextView extends View {

    private CharSequence text = "";

    public TextView(Context context) { super(context); }

    public CharSequence getText() { return text; }

    public void setText(CharSequence t) { text = t; }

    public void setTextColor(int color) { }

    public void setOnClickListener(View.OnClickListener l) { }
}
