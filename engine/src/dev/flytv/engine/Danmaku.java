package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 弹幕：优先使用 logvar 弹幕服务（安卓端同款，AI 弹幕），失败回退 360kan+dmku 老链路。
 *  输出统一为 {"danmuku":[[时间秒, 模式, "#颜色", 字号, 文本], ...]}（网页端渲染格式）。 */
public final class Danmaku {
    private static final ConcurrentHashMap<String, Object[]> CACHE = new ConcurrentHashMap<>();
    private static final String[] PLATS = { "qq", "qiyi", "youku", "imgo", "bilibili1", "m1905", "leshi" };
    private static final String LOGVAR_BASE = "https://logvardanmu.konfan.cn/87654321";

    public static String fetch(String nameRaw) {
        return fetch(nameRaw, "");
    }

    public static String fetch(String nameRaw, String epRaw) {
        String name = clean(nameRaw);
        String ep = clean(epRaw);
        if (name.isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("error", "missing name");
            return o.toString();
        }
        String cacheKey = name + "|" + ep;
        Object[] hit = CACHE.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 6 * 3600_000L) return (String) hit[1];
        // ① logvar（AI 弹幕）优先
        String out = tryLogvar(name, ep);
        if (out == null) out = tryDmku(name);
        if (out == null) {
            JsonObject o = new JsonObject();
            o.addProperty("error", "弹幕拉取失败");
            return o.toString();
        }
        if (CACHE.size() > 150) CACHE.clear();
        CACHE.put(cacheKey, new Object[]{System.currentTimeMillis(), out});
        Logger.d("Danmaku", "ok: " + name + (ep.isEmpty() ? "" : " / " + ep));
        return out;
    }

    // ---------- ① logvar 弹幕服务 ----------

    private static String tryLogvar(String name, String ep) {
        try {
            String search = HttpUtil.get(LOGVAR_BASE + "/api/v2/search/episodes?anime=" + enc(name)
                    + (ep.isEmpty() ? "" : "&episode=" + enc(ep)), null, 15000);
            JsonObject root = JsonUtil.parseObj(search);
            JsonArray eps = findEpisodes(root);
            if (eps == null || eps.size() == 0) return null;
            JsonObject match = pickEpisode(eps, ep);
            if (match == null) return null;
            String id = JsonUtil.str(match, "id", "");
            if (id.isEmpty()) id = JsonUtil.str(match, "episodeId", "");
            if (id.isEmpty()) return null;
            String body = HttpUtil.get(LOGVAR_BASE + "/api/v2/comment/" + id + "?format=json", null, 20000);
            return commentJsonToDanmuku(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonArray findEpisodes(JsonObject root) {
        if (root == null) return null;
        JsonArray a = root.getAsJsonArray("episodes");
        if (a != null && a.size() > 0) return a;
        JsonObject bangumi = root.getAsJsonObject("bangumi");
        if (bangumi != null) {
            a = bangumi.getAsJsonArray("episodes");
            if (a != null && a.size() > 0) return a;
        }
        for (String key : new String[]{"animes", "anime", "data"}) {
            JsonArray arr = root.getAsJsonArray(key);
            if (arr == null) continue;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                JsonObject item = e.getAsJsonObject();
                a = item.getAsJsonArray("episodes");
                if (a != null && a.size() > 0) return a;
                JsonObject bg = item.getAsJsonObject("bangumi");
                if (bg != null) {
                    a = bg.getAsJsonArray("episodes");
                    if (a != null && a.size() > 0) return a;
                }
            }
        }
        return null;
    }

    /** 按集名里的数字匹配剧集；匹配不到用第一条。 */
    private static JsonObject pickEpisode(JsonArray eps, String ep) {
        int want = epNumber(ep);
        JsonObject fallback = null;
        for (JsonElement e : eps) {
            if (!e.isJsonObject()) continue;
            JsonObject item = e.getAsJsonObject();
            if (fallback == null) fallback = item;
            if (want <= 0) continue;
            for (String key : new String[]{"title", "name", "episode", "sort", "number", "episodeNumber"}) {
                String v = JsonUtil.str(item, key, "");
                if (!v.isEmpty() && epNumber(v) == want) return item;
            }
        }
        return fallback;
    }

    /** 从"第13集 / S01E02 / 13 / 文件名"里提取集号。 */
    private static int epNumber(String s) {
        if (s == null || s.isEmpty()) return -1;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)S\\d+E(\\d+)").matcher(s);
            if (m.find()) return Integer.parseInt(m.group(1));
            m = java.util.regex.Pattern.compile("第\\s*(\\d+)\\s*[集话話]").matcher(s);
            if (m.find()) return Integer.parseInt(m.group(1));
            m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
            int last = -1;
            while (m.find()) last = Integer.parseInt(m.group(1));
            return last;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String commentJsonToDanmuku(String body) {
        try {
            JsonObject root = JsonUtil.parseObj(body);
            JsonArray comments = root == null ? null : root.getAsJsonArray("comments");
            if (comments == null && root != null) comments = root.getAsJsonArray("data");
            if (comments == null || comments.size() == 0) return null;
            JsonArray out = new JsonArray();
            for (JsonElement e : comments) {
                if (!e.isJsonObject()) continue;
                JsonObject c = e.getAsJsonObject();
                String p = JsonUtil.str(c, "p", "");
                String text = JsonUtil.str(c, "m", "");
                if (text.isEmpty()) text = JsonUtil.str(c, "text", "");
                if (text.isEmpty()) continue;
                double time = 0;
                int mode = 1;
                int size = 25;
                String color = "#ffffff";
                if (!p.isEmpty()) {
                    String[] ps = p.split(",");
                    try {
                        if (ps.length > 0) time = Double.parseDouble(ps[0].trim());
                        if (ps.length > 1) mode = safeInt(ps[1].trim(), 1);
                        if (ps.length > 2) {
                            String v2 = ps[2].trim();
                            if (isColorValue(v2)) {
                                // 格式为 时间,模式,颜色（logvar）：第 3 位是颜色，字号用默认
                                color = normColor(v2);
                                size = 25;
                            } else {
                                size = safeInt(v2, 25);
                                if (ps.length > 3) color = normColor(ps[3].trim());
                            }
                        }
                    } catch (Exception ignored) { }
                } else {
                    try {
                        if (c.has("time")) time = c.get("time").getAsDouble();
                        if (c.has("mode")) mode = c.get("mode").getAsInt();
                        if (c.has("color")) color = normColor(c.get("color").getAsString());
                        if (c.has("size")) size = c.get("size").getAsInt();
                    } catch (Exception ignored) { }
                }
                if (size <= 0) size = 25;
                JsonArray item = new JsonArray();
                item.add(Math.round(time * 100) / 100.0);
                item.add(mode);
                item.add(color);
                item.add(size);
                item.add(text);
                out.add(item);
            }
            if (out.size() == 0) return null;
            JsonObject result = new JsonObject();
            result.add("danmuku", out);
            result.addProperty("source", "logvar");
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    /** 是否像颜色值（十进制 / 0x / #）。 */
    private static boolean isColorValue(String v) {
        try {
            if (v == null || v.isEmpty()) return false;
            String s = v.trim();
            if (s.startsWith("#")) return true;
            if (s.startsWith("0x") || s.startsWith("0X")) return true;
            long c = Long.parseLong(s);
            return c >= 0 && c <= 0xffffffL;
        } catch (Exception e) {
            return false;
        }
    }

    /** 弹幕颜色统一成 #rrggbb（支持 十进制 / 0x / #）。 */
    private static String normColor(String v) {
        try {
            if (v == null || v.isEmpty()) return "#ffffff";
            String s = v.trim();
            if (s.startsWith("#")) return s.length() == 7 ? s : "#ffffff";
            long c;
            if (s.startsWith("0x") || s.startsWith("0X")) c = Long.parseLong(s.substring(2), 16);
            else c = Long.parseLong(s);
            if (c < 0 || c > 0xffffffL) return "#ffffff";
            return String.format("#%06x", c);
        } catch (Exception e) {
            return "#ffffff";
        }
    }

    // ---------- ② 老链路（360kan 匹配平台 + dmku 拉取），作为回退 ----------

    private static String tryDmku(String name) {
        try {
            String kw = enc(name);
            String sr = HttpUtil.get("https://api.so.360kan.com/index?force_v=1&kw=" + kw + "&from=&pageno=1&v_ap=1&tab=all", null, 12000);
            JsonObject sroot = JsonUtil.parseObj(sr);
            JsonObject sdata = sroot == null ? null : sroot.getAsJsonObject("data");
            if (sdata == null) return null;
            String platformUrl = null;
            outer:
            for (Map.Entry<String, JsonElement> group : sdata.entrySet()) {
                JsonObject g = group.getValue() != null && group.getValue().isJsonObject() ? group.getValue().getAsJsonObject() : null;
                JsonArray rows = g == null ? null : g.getAsJsonArray("rows");
                if (rows == null) continue;
                for (JsonElement re : rows) {
                    if (!re.isJsonObject()) continue;
                    JsonObject row = re.getAsJsonObject();
                    String title = JsonUtil.str(row, "title", "");
                    if (title.isEmpty()) continue;
                    if (!(title.equals(name) || title.contains(name) || name.contains(title))) continue;
                    JsonObject links = row.getAsJsonObject("playlinks");
                    if (links == null) continue;
                    for (String plat : PLATS) {
                        String v = JsonUtil.str(links, plat, "");
                        if (!v.isEmpty()) { platformUrl = v; break; }
                    }
                    if (platformUrl != null) break outer;
                }
            }
            if (platformUrl == null) return null;
            String dm = HttpUtil.get("https://dmku.hls.one/?ac=dm&url=" + enc(platformUrl), null, 25000);
            if (dm == null || dm.isEmpty()) return null;
            return dm;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 工具 ----------

    static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("[\\[\\]【】()（）]", " ").trim();
        t = t.replaceAll("\\s+", " ");
        return t;
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }
}
