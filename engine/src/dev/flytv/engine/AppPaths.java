package dev.flytv.engine;

import java.io.File;
import java.net.URI;

/** 数据目录与安装目录（与 C# 版路径布局完全一致，数据文件可直接共用）。 */
public final class AppPaths {
    public static String Root;
    public static String Cache;
    public static String Js;
    public static String Live;
    public static String Wall;
    public static String Restore;
    public static String Local;
    public static String Node;
    public static String JarCache;
    public static String InstallRoot;

    public static void init() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isEmpty()) localAppData = System.getProperty("user.home") + "\\AppData\\Local";
        Root = localAppData + "\\TVBox for Windows";
        Cache = Root + "\\cache";
        Js = Root + "\\js";
        Live = Root + "\\live";
        Wall = Root + "\\wall";
        Restore = Root + "\\restore";
        Local = Root + "\\local";
        Node = Root + "\\node";
        JarCache = Root + "\\jarcache";
        for (String d : new String[] { Root, Cache, Js, Live, Wall, Restore, Local, Node, JarCache })
            new File(d).mkdirs();
        InstallRoot = resolveInstallRoot();
    }

    /** 安装目录 = 引擎 jar 所在目录（其中含 web/、jar/ 等）。 */
    static String resolveInstallRoot() {
        try {
            File jar = new File(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jar.isFile() ? jar.getParentFile() : jar;
            if (dir != null) return dir.getAbsolutePath();
        } catch (Exception ignored) {
        }
        return System.getProperty("user.dir");
    }

    public static String webDir() { return InstallRoot + File.separator + "web"; }
    public static String jarDir() { return InstallRoot + File.separator + "jar"; }
}
