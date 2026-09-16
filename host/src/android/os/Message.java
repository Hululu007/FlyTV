package android.os;

/** android.os.Message 桩：what/obj/arg1/arg2 基本字段。 */
public class Message {

    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    public Object replyTo;
    public Handler target;
    long when;

    public Message() { }

    public static Message obtain() { return new Message(); }

    public static Message obtain(Handler h) { Message m = new Message(); m.target = h; return m; }

    public static Message obtain(Handler h, int what) { Message m = obtain(h); m.what = what; return m; }

    public static Message obtain(Handler h, int what, Object obj) { Message m = obtain(h, what); m.obj = obj; return m; }

    public static Message obtain(Handler h, int what, int arg1, int arg2) { Message m = obtain(h, what); m.arg1 = arg1; m.arg2 = arg2; return m; }

    public static Message obtain(Handler h, int what, int arg1, int arg2, Object obj) { Message m = obtain(h, what, arg1, arg2); m.obj = obj; return m; }

    public void sendToTarget() { if (target != null) target.sendMessage(this); }

    public void recycle() { }
}
