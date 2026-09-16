package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 点播配置服务（VodConfigService 迁移）：拉取配置 → 解析 sites/parses/flags/doh/rules。 */
public final class VodConfig {
    public static final class Site {
        public String key = "", name = "", api = "", ext = "", jar = "", logo = "";
        public int type = 3;
        public boolean searchable = true, hidden = false, quickSearch = true;
        public String toString() { return name.isEmpty() ? key : name; }
    }

    private static final Object LOCK = new Object();
    private static volatile JsonObject config;              // 当前配置原始 JSON
    private static volatile String configUrl = "";
    private static volatile int configId = 0;
    private static final List<Site> sites = new ArrayList<>();
    private static final List<JsonObject> parses = new ArrayList<>();
    private static final List<String> flags = new ArrayList<>();

    public static JsonObject rawConfig() { return config; }
    public static String url() { return configUrl; }
    public static int id() { return configId; }
    public static List<Site> sites() { return sites; }
    public static List<String> flags() { return flags; }

    /** 站点列表（隐藏站点排除），与 C# siteCount 语义一致。 */
    public static int visibleSiteCount() {
        int n = 0;
        for (Site s : sites) if (!s.hidden) n++;
        return n;
    }

    /** 加载配置（URL 或本地文件路径；支持 depot 嵌套，与 C# LoadCore 语义一致）。 */
    public static void load(String url) throws Exception {
        String text = fetch(url, 0);
        JsonObject root = JsonUtil.parseObj(text);
        if (root == null) throw new Exception("配置解析失败");
        String depot = JsonUtil.str(root, "depot", "");
        if (!depot.isEmpty()) {
            // depot 嵌套：配置包一层，取内部 URL 再拉
            String inner = depot.replace("$name", JsonUtil.str(root, "name", ""));
            text = fetch(inner, 0);
            root = JsonUtil.parseObj(text);
            if (root == null) throw new Exception("depot 配置解析失败");
        }
        synchronized (LOCK) {
            config = root;
            configUrl = url;
            configId = resolveConfigId(url);
            sites.clear();
            parses.clear();
            flags.clear();
            JsonArray arr = root.getAsJsonArray("sites");
            if (arr != null) {
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    Site s = new Site();
                    s.key = JsonUtil.str(o, "key", "");
                    s.name = JsonUtil.str(o, "name", s.key);
                    s.api = JsonUtil.str(o, "api", "");
                    s.type = JsonUtil.integer(o, "type", 3);
                    s.logo = JsonUtil.str(o, "logo", "");
                    s.searchable = JsonUtil.integer(o, "searchable", 1) != 0;
                    s.hidden = JsonUtil.bool(o, "hide", false);
                    s.quickSearch = JsonUtil.integer(o, "quickSearch", 1) != 0;
                    JsonElement ext = o.get("ext");
                    s.ext = ext == null || ext.isJsonNull() ? "" : (ext.isJsonPrimitive() ? ext.getAsString() : JsonUtil.gson.toJson(ext));
                    if (s.key.isEmpty()) continue;
                    sites.add(s);
                }
            }
            JsonArray pa = root.getAsJsonArray("parses");
            if (pa != null) for (JsonElement e : pa) try { if (e.isJsonObject()) parses.add(e.getAsJsonObject()); } catch (Exception ignored) { }
            JsonArray fa = root.getAsJsonArray("flags");
            if (fa != null) for (JsonElement e : fa) try { flags.add(e.getAsString()); } catch (Exception ignored) { }
            Logger.d("VodConfig", "配置已加载: " + configUrl + " → " + visibleSiteCount() + " 站点, " + parses.size() + " 解析器");
        }
    }

    /** 启动加载：优先 Setting.configVod，回退 configs.json 最新记录。 */
    public static void loadStartup() {
        try {
            String url = Setting.configVod();
            if (url == null || url.isEmpty()) {
                List<JsonObject> list = Stores.getConfigs(0);
                if (!list.isEmpty()) url = JsonUtil.str(list.get(0), "url", "");
            }
            if (url == null || url.isEmpty()) { Logger.d("VodConfig", "无可用配置"); return; }
            load(url);
        } catch (Exception e) {
            Logger.e("VodConfig", "启动配置加载失败: " + e);
        }
    }

    private static String fetch(String url, int depth) throws Exception {
        if (depth > 3) throw new Exception("配置嵌套过深");
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return HttpUtil.get(url, null, 30000);
        }
        if (url.startsWith("file://")) url = url.substring(7);
        File f = new File(url);
        if (!f.isAbsolute()) f = new File(AppPaths.Local, url);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static int resolveConfigId(String url) {
        for (JsonObject c : Stores.configs())
            if (url.equals(JsonUtil.str(c, "url", ""))) return JsonUtil.integer(c, "id", 0);
        return 0;
    }
}
