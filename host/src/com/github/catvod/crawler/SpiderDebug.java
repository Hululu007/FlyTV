package com.github.catvod.crawler;

/** Spider 调试日志桩（与安卓端静态方法签名一致，输出到宿主 stdout）。 */
public class SpiderDebug {

    public static void logD(String msg) { System.out.println("[spider][D] " + msg); }
    public static void logD(Throwable t) { t.printStackTrace(); }
    public static void logI(String msg) { System.out.println("[spider][I] " + msg); }
    public static void logW(String msg) { System.out.println("[spider][W] " + msg); }
    public static void logE(String msg) { System.out.println("[spider][E] " + msg); }
    public static void logE(Throwable t) { t.printStackTrace(); }
    public static void log(String msg) { System.out.println("[spider] " + msg); }
    public static void log(Throwable t) { t.printStackTrace(); }
}
