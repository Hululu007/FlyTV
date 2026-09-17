package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pinyin4j.PinyinHelper;

/** 拼音联想（等价 C# PinyinIndex）：读豆瓣榜单缓存（jarcache/pinyin_index.json 的 name 列表），
 * 用 pinyin4j 现场计算首字母/全拼，供搜索框输入字母联想（lldq → 流浪地球2）。 */
public final class PinyinIndex {
    private static volatile List<String[]> INDEX; // [name, initials, fullpy]

    public static void warmup() {
        Thread t = new Thread(PinyinIndex::build, "pinyin-warmup");
        t.setDaemon(true);
        t.start();
    }

    static synchronized void build() {
        if (INDEX != null) return;
        List<String[]> out = new ArrayList<>();
        try {
            File f = new File(AppPaths.JarCache, "pinyin_index.json");
            if (f.exists()) {
                String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                JsonObject root = JsonUtil.parseObj(text);
                JsonArray list = root == null ? null : root.getAsJsonArray("list");
                if (list != null) {
                    for (com.google.gson.JsonElement e : list) {
                        if (!e.isJsonObject()) continue;
                        String name = JsonUtil.str(e.getAsJsonObject(), "name", "").trim();
                        if (name.isEmpty()) continue;
                        String[] py = pinyin(name);
                        if (py != null) out.add(new String[]{ name, py[0], py[1] });
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("PinyinIndex", String.valueOf(e));
        }
        INDEX = out;
        Logger.d("PinyinIndex", "拼音索引就绪（" + out.size() + " 条）");
    }

    /** 返回 [首字母串, 全拼串]（小写）。 */
    static String[] pinyin(String name) {
        try {
            StringBuilder initials = new StringBuilder();
            StringBuilder full = new StringBuilder();
            for (char c : name.toCharArray()) {
                if (c >= 0x4E00 && c <= 0x9FFF) {
                    String[] arr = PinyinHelper.toHanyuPinyinStringArray(c);
                    if (arr != null && arr.length > 0) {
                        String py = arr[0].replaceAll("[0-9]", "");
                        initials.append(py.charAt(0));
                        full.append(py);
                    }
                } else if (Character.isLetterOrDigit(c)) {
                    initials.append(Character.toLowerCase(c));
                    full.append(Character.toLowerCase(c));
                }
            }
            return new String[]{ initials.toString(), full.toString() };
        } catch (Exception e) {
            return null;
        }
    }

    /** 字母输入联想：首字母前缀 / 全拼前缀匹配，返回 {list:[{title}]}（title 供前端填入搜索框）。 */
    public static JsonArray suggest(String q) {
        JsonArray out = new JsonArray();
        if (INDEX == null) {
            try { build(); } catch (Exception ignored) { }
        }
        List<String[]> idx = INDEX;
        if (idx == null) return out;
        String query = q == null ? "" : q.trim().toLowerCase();
        if (query.length() < 2) return out;
        for (String[] item : idx) {
            boolean hit = item[1].startsWith(query) || item[2].startsWith(query);
            if (!hit) continue;
            JsonObject o = new JsonObject();
            o.addProperty("title", item[0]);
            out.add(o);
            if (out.size() >= 8) break;
        }
        return out;
    }
}
