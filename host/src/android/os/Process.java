package android.os;

/** Process 桩。 */
public class Process {

    public static final int MY_UID = 10000;
    public static final int MY_PID = 1;

    public static int myPid() { return MY_PID; }

    public static int myUid() { return MY_UID; }

    public static void killProcess(int pid) { }
}
