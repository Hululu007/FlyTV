package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 弹幕（等价 C# Danmaku）：360kan 按片名匹配平台播放页 → 公共弹幕库 dmku 拉取（6 小时缓存）。 */
public final class Danmaku {
    private static final ConcurrentHashMap<String, Object[]> CACHE = new ConcurrentHashMap<>();
    private static final String[] PLATS = { "qq", "qiyi", "youku", "imgo", "bilibili1", "m1905", "leshi" };

    public static String fetch(String nameRaw) {
        String name = clean(nameRaw);
        if (name.isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("error", "missing name");
            return o.toString();
        }
        Object[] hit = CACHE.get(name);
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 6 * 3600_000L) return (String) hit[1];
        try {
            String kw = enc(name);
            String sr = HttpUtil.get("https://api.so.360kan.com/index?force_v=1&kw=" + kw + "&from=&pageno=1&v_ap=1&tab=all", null, 12000);
            JsonObject sroot = JsonUtil.parseObj(sr);
            JsonObject sdata = sroot == null ? null : sroot.getAsJsonObject("data");
            if (sdata == null) {
                JsonObject o = new JsonObject();
                o.addProperty("error", "弹幕搜索无结果");
                return o.toString();
            }
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
            if (platformUrl == null) {
                JsonObject o = new JsonObject();
                o.addProperty("error", "未匹配到该片的平台片源（可能名称不标准）");
                return o.toString();
            }
            String dm = HttpUtil.get("https://dmku.hls.one/?ac=dm&url=" + enc(platformUrl), null, 25000);
            if (dm == null || dm.isEmpty()) {
                JsonObject o = new JsonObject();
                o.addProperty("error", "弹幕拉取失败");
                return o.toString();
            }
            if (CACHE.size() > 150) CACHE.clear();
            CACHE.put(name, new Object[]{System.currentTimeMillis(), dm});
            Logger.d("Danmaku", "ok: " + name);
            return dm;
        } catch (Exception e) {
            Logger.e("Danmaku", String.valueOf(e));
            JsonObject o = new JsonObject();
            o.addProperty("error", String.valueOf(e.getMessage()));
            return o.toString();
        }
    }

    /** 清洗片名后缀（"流浪地球2（臻彩）"→"流浪地球2"）。 */
    static String clean(String name) {
        name = name == null ? "" : name.trim();
        for (char ch : new char[]{ '（', '(', '【', '[' }) {
            int i = name.indexOf(ch);
            if (i > 0) name = name.substring(0, i);
        }
        return name.trim();
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }
}
