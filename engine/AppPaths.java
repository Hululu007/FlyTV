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
        ensureDefaults();
    }

    /** 确保 jar 爬虫所需的默认配置文件存在（全新机器上缺失会导致网盘站点「剧集列表加载不出来」）。 */
    public static void ensureDefaults() {
        String peizhi = "{\"version\":\"2.0\",\"update\":\"关闭\",\"danmuColor\":\"默认\",\"danmuSearch\":\"搜索\","
                + "\"aliQuality\":\"阿里原画\",\"quarkQuality\":\"夸克原画\",\"ucQuality\":\"UC原画\",\"baiduQuality\":\"百度原画\","
                + "\"123Quality\":\"123原画\",\"panBlock\":\"\",\"proxyMode\":\"GHProxy\","
                + "\"panOrder\":\"百度,夸克,UC,迅雷,光鸭,天翼,123,阿里,移动\"}";
        File[] targets = {
                new File(JarCache, "files" + File.separator + "lzxw" + File.separator + "peizhi.json"),
                new File(JarCache, "files" + File.separator + "peizhi.json"),
                new File(JarCache, "data" + File.separator + "peizhi.json"),
                new File(JarCache, "peizhi.json"),
                new File(System.getenv("TEMP") == null ? "." : System.getenv("TEMP"), "TVBox" + File.separator + "peizhi.json")
        };
        for (File f : targets) {
            try {
                if (f.exists() && f.length() > 2) continue;
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                java.nio.file.Files.write(f.toPath(), peizhi.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Logger.d("AppPaths", "已生成默认配置: " + f);
            } catch (Exception ignored) { }
        }
        // 全新机器：生成默认点播配置列表（设置页可见"内置配置"，避免"点播配置为空"的困惑）
        try {
            File cfg = new File(Root, "configs.json");
            if (!cfg.exists() || cfg.length() < 3) {
                String defaultCfg = "[{\"id\":1,\"type\":0,\"time\":" + System.currentTimeMillis()
                        + ",\"url\":\"" + Setting.BuiltInConfigVod + "\",\"name\":\"内置配置\",\"home\":\"\",\"parse\":\"\",\"notice\":\"\",\"danmaku\":\"\"}]";
                java.nio.file.Files.write(cfg.toPath(), defaultCfg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Logger.d("AppPaths", "已生成默认点播配置: " + cfg);
            }
        } catch (Exception ignored) { }
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
