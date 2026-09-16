package dev.flytv.engine;

import com.google.gson.JsonObject;

/** /api/play 编排：爬虫 player → 网盘 URL 走转存中继；直链原样返回。 */
public final class PlayService {
    public static JsonObject play(String siteKey, String flag, String epUrl) {
        try {
            VodConfig.Site site = findSite(siteKey);
            if (site == null) return err("站点不存在: " + siteKey);
            JsonObject pr = SiteService.player(site, flag, epUrl);
            if (pr.has("error")) return pr;
            String url = JsonUtil.str(pr, "url", "");
            if (url.isEmpty()) return err("未获取到播放地址");
            if (PanService.isPanUrl(url)) {
                String relay = PanService.resolveToRelay(url);
                JsonObject o = new JsonObject();
                o.addProperty("url", relay);
                o.add("headers", new JsonObject());
                o.addProperty("format", "");
                Logger.d("PlayService", "网盘中继就绪: " + siteKey);
                return o;
            }
            return pr;
        } catch (Exception e) {
            Logger.e("PlayService", "play(" + siteKey + ") " + e.getMessage());
            return err(e.getMessage());
        }
    }

    static JsonObject err(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg == null ? "播放失败" : msg);
        return o;
    }

    public static VodConfig.Site findSite(String key) {
        if (key == null || key.isEmpty()) {
            for (VodConfig.Site s : VodConfig.sites())
                if (!s.hidden) return s;
            return null;
        }
        for (VodConfig.Site s : VodConfig.sites())
            if (key.equals(s.key) || key.equals(s.name)) return s;
        return null;
    }
}
