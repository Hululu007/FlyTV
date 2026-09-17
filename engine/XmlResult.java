package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 苹果CMS XML（type0 站点）解析 → 统一 JSON 风格（vod_play_from/url 用 $$$/# 重建，便于与 JSON 站点共用后续处理）。 */
public final class XmlResult {
    public JsonArray types = new JsonArray();
    public JsonArray list = new JsonArray();
    public JsonObject rawVod;
    public String page = "1", pagecount = "1", limit = "20", total = "0";

    public static XmlResult parse(String xml) {
        XmlResult r = new XmlResult();
        try {
            Document doc = Jsoup.parse(xml == null ? "" : xml, "", org.jsoup.parser.Parser.xmlParser());
            for (Element ty : doc.select("class ty")) {
                JsonObject t = new JsonObject();
                t.addProperty("id", ty.attr("id"));
                t.addProperty("name", ty.text());
                r.types.add(t);
            }
            r.page = firstText(doc, "page", "1");
            r.pagecount = firstText(doc, "pagecount", "1");
            r.limit = firstText(doc, "limit", "20");
            r.total = firstText(doc, "total", "0");
            for (Element v : doc.select("video")) {
                JsonObject vod = vodJson(v);
                r.list.add(vod);
                if (r.rawVod == null) r.rawVod = vod;
            }
        } catch (Exception e) {
            Logger.e("XmlResult", String.valueOf(e));
        }
        return r;
    }

    static String firstText(Document doc, String tag, String def) {
        Element e = doc.selectFirst(tag);
        String t = e == null ? "" : e.text().trim();
        return t.isEmpty() ? def : t;
    }

    /** <video> 元素 → JSON 风格 vod（含 vod_play_from/vod_play_url 重建）。 */
    static JsonObject vodJson(Element v) {
        JsonObject o = new JsonObject();
        o.addProperty("vod_id", text(v, "id"));
        o.addProperty("vod_name", text(v, "name"));
        o.addProperty("vod_pic", text(v, "pic"));
        o.addProperty("vod_remarks", text(v, "note"));
        o.addProperty("vod_year", text(v, "year"));
        o.addProperty("vod_area", text(v, "area"));
        o.addProperty("type_name", text(v, "type"));
        o.addProperty("vod_content", text(v, "des"));
        StringBuilder from = new StringBuilder(), urls = new StringBuilder();
        for (Element dd : v.select("dl dd")) {
            if (from.length() > 0) { from.append("$$$"); urls.append("$$$"); }
            String flag = dd.attr("flag");
            from.append(flag.isEmpty() ? "线路" : flag);
            urls.append(dd.text().trim());
        }
        o.addProperty("vod_play_from", from.toString());
        o.addProperty("vod_play_url", urls.toString());
        return o;
    }

    static String text(Element parent, String tag) {
        Element e = parent.selectFirst(tag);
        return e == null ? "" : e.text().trim();
    }
}
