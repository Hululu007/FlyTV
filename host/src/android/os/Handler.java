package android.os;

/**
 * android.os.Handler 桩：
 * - sendMessage/sendEmptyMessage → 立即执行（数据流语义）
 * - postDelayed/sendMessageDelayed → 延迟调度执行（定时器语义，保证超时逻辑能触发）
 * - post(Runnable) → 立即执行
 */
public class Handler {

    public interface Callback { boolean handleMessage(Message msg); }

    final Callback callback;
    static final java.util.concurrent.ScheduledExecutorService SCHEDULER =
            java.util.concurrent.Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "handler-delayed");
                t.setDaemon(true);
                return t;
            });

    public Handler() { this.callback = null; }

    public Handler(Callback callback) { this.callback = callback; }

    public Handler(Looper looper) { this.callback = null; }

    public Handler(Callback callback, Looper looper) { this.callback = callback; }

    public void handleMessage(Message msg) { }

    public void dispatchMessage(Message msg) {
        if (msg == null) return;
        if (msg.obj instanceof Runnable) { try { ((Runnable) msg.obj).run(); return; } catch (Throwable ignored) { } }
        if (callback != null) { try { callback.handleMessage(msg); return; } catch (Throwable ignored) { } }
        handleMessage(msg);
    }

    public boolean sendMessage(Message msg) { if (msg != null) msg.target = this; dispatchMessage(msg); return true; }

    public boolean sendEmptyMessage(int what) { return sendMessage(Message.obtain(this, what)); }

    public boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        Message msg = Message.obtain(this, what);
        if (delayMillis <= 0) return sendMessage(msg);
        SCHEDULER.schedule(() -> dispatchMessage(msg), delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean sendMessageDelayed(Message msg, long delayMillis) {
        if (msg != null) msg.target = this;
        if (delayMillis <= 0) return sendMessage(msg);
        SCHEDULER.schedule(() -> dispatchMessage(msg), delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean sendMessageAtTime(Message msg, long uptimeMillis) { return sendMessage(msg); }

    public Message obtainMessage() { return Message.obtain(this); }

    public Message obtainMessage(int what) { return Message.obtain(this, what); }

    public Message obtainMessage(int what, Object obj) { return Message.obtain(this, what, obj); }

    public Message obtainMessage(int what, int arg1, int arg2) { return Message.obtain(this, what, arg1, arg2); }

    public boolean post(Runnable r) { try { r.run(); } catch (Throwable ignored) { } return true; }

    public boolean postDelayed(Runnable r, long delayMillis) {
        if (r == null) return false;
        if (delayMillis <= 0) { try { r.run(); } catch (Throwable ignored) { } return true; }
        SCHEDULER.schedule(() -> { try { r.run(); } catch (Throwable ignored) { } }, delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        return true;
    }

    public void removeCallbacks(Runnable r) { }

    public void removeMessages(int what) { }

    public void removeCallbacksAndMessages(Object token) { }

    public boolean hasMessages(int what) { return false; }
}
