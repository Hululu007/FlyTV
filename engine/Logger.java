package dev.flytv.engine;

import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** 线程安全日志（控制台 + engine.log）。 */
public final class Logger {
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss.SSS");

    public static void d(String tag, String msg) { write("D", tag, msg); }
    public static void e(String tag, String msg) { write("E", tag, msg); }

    private static void write(String level, String tag, String msg) {
        String line = FMT.format(new Date()) + " " + level + "/" + tag + ": " + msg;
        synchronized (LOCK) {
            System.out.println(line);
            if (AppPaths.Root == null) return;
            try (FileWriter fw = new FileWriter(AppPaths.Root + "\\engine.log", true)) {
                fw.write(line + "\n");
            } catch (Exception ignored) {
            }
        }
    }
}
