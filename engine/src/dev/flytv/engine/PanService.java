package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 网盘播放编排（NativeQuark 的 Java 版）：转存 → play 接口取流 → jarstream 中继 URL。
 * 带转存缓存（30 分钟）与 savedFid 失效自动重新转存。 */
public final class PanService {
    private static final String QUARK_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/3.0.1 Chrome/100.0.4896.160 Electron/18.3.5.12-a038f7b798 Safari/537.36 Channel/pckk_other_ch";

    private static final ConcurrentHashMap<String, long[]> TRANSFER = new ConcurrentHashMap<>(); // key -> [tick, ] 用两个 map 简化
    private static final ConcurrentHashMap<String, String> FIDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object[]> SHARE_CACHE = new ConcurrentHashMap<>(); // shareId -> [tick, List<String[]>]
    private static final ConcurrentHashMap<String, String> DEST_FIDS = new ConcurrentHashMap<>();     // siteKey -> TVBox 目录 fid
    private static final long TTL = 30 * 60 * 1000;

    /** 判断是否网盘代理 URL（本引擎 /proxy?do=pan 或历史遗留 127.0.0.1:9978）。 */
    public static boolean isPanUrl(String url) {
        return url != null && (url.contains("/proxy?do=pan") || url.contains("/proxy?do=pan".replace("?", "%3F")));
    }

    /** 网盘 URL → jarstream 中继 URL（转存 + play + 返回可直接播放的中继地址）。 */
    public static String resolveToRelay(String url) throws Exception {
        Map<String, String> q = query(url);
        String siteKey = q.getOrDefault("siteKey", "");
        String shareId = q.getOrDefault("shareId", "");
        String fileId = q.getOrDefault("fileId", "");
        String fileToken = q.getOrDefault("fileToken", "");
        String savedFid = q.getOrDefault("savedFid", "");
        String epName = q.getOrDefault("epName", "");
        if (siteKey.isEmpty() || fileId.isEmpty()) throw new Exception("网盘参数缺失（siteKey/fileId）");
        String cookie = readCookie();
        if (cookie.isEmpty()) throw new Exception("未登录夸克账号");
        syncCookieForHost(cookie);

        String cacheKey = shareId + "+" + fileId;
        // 0. 转存缓存命中 → 探测复用
        String cached = cachedFid(cacheKey);
        if (cached != null) {
            String probe = playApi(siteKey, cached, cookie);
            String probeUrl = extractStreamUrl(probe);
            if (!probeUrl.isEmpty()) return relay(siteKey, probeUrl);
            removeFid(cacheKey);
        }
        // 1. savedFid（URL 参数，可能已失效）
        if (!savedFid.isEmpty()) {
            String u = extractStreamUrl(playApi(siteKey, savedFid, cookie));
            if (!u.isEmpty()) { remember(cacheKey, savedFid); return relay(siteKey, u); }
            Logger.d("QuarkPlay", "savedFid 已失效（文件可能被删），重新转存");
        }
        // 2. 宿主记录的最后 fid
        String hostFid = savedFidFromHost(siteKey);
        if (!hostFid.isEmpty()) {
            String u = extractStreamUrl(playApi(siteKey, hostFid, cookie));
            if (!u.isEmpty()) { remember(cacheKey, hostFid); return relay(siteKey, u); }
        }
        // 3. 转存（同会话取新 token + 分享内按集名匹配 + TVBox 目录；失败重试一次）
        String fid = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                fid = nativeTransfer(siteKey, shareId, fileId, epName, cookie);
                if (!fid.isEmpty()) break;
            } catch (Exception e) {
                if (attempt >= 2) throw e;
                Logger.d("QuarkPlay", "转存失败，1.2s 后重试: " + e.getMessage());
            }
            Thread.sleep(1200);
        }
        if (fid.isEmpty()) throw new Exception("转存失败：未获取到网盘文件");
        String u = extractStreamUrl(playApi(siteKey, fid, cookie));
        if (u.isEmpty()) throw new Exception("获取视频流失败");
        remember(cacheKey, fid);
        return relay(siteKey, u);
    }

    // ---------- 转存（修正版：同一会话取新 token；转存到专用 TVBox 目录，避开 jar 的 Quarktemp 清理） ----------
    /** 转存一个分享文件并返回网盘 fid。epName 为集名提示（如 "[01-60]64.mkv[107.90MB]"）。 */
    static String nativeTransfer(String siteKey, String shareId, String fileId, String epName, String cookie) throws Exception {
        // 1. 分享 token（stoken）——file token 必须与此同会话
        String stoken = match(relayRaw(siteKey, "post",
                "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/token?__t=" + System.currentTimeMillis(),
                "{\"pwd_id\":\"" + shareId + "\",\"passcode\":\"\"}", cookie), "\"stoken\":\"([^\"]+)\"");
        if (stoken.isEmpty()) throw new Exception("转存失败：分享已失效");
        // 2. 分享内定位文件（优先原 fileId，其次按集名/集数匹配，应对上传者换文件）
        String[] file = findShareFile(siteKey, shareId, stoken, fileId, epName, cookie);
        if (file == null) throw new Exception("转存失败：分享中未找到该集文件（可能已被替换）");
        // 3. 目标目录 TVBox（自建，jar 不会清理）
        String destFid = ensureFolder(siteKey, cookie);
        if (destFid.isEmpty()) throw new Exception("转存失败：无法创建目标目录");
        // 4. 目标目录已有同名文件 → 直接复用（旧副本仍可播放则零转存）
        String reuse = findInFolder(siteKey, destFid, file[2], cookie);
        if (!reuse.isEmpty()) {
            String pu = extractStreamUrl(playApi(siteKey, reuse, cookie));
            if (!pu.isEmpty()) return reuse;
            deleteFile(siteKey, reuse, cookie);
        }
        // 5. 转存（正确字段格式）→ 任务轮询取 save_as_top_fids
        String saveText = relayRaw(siteKey, "post",
                "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/save?pr=ucpro&fr=pc&uc_param_str=&__t=" + System.currentTimeMillis(),
                "{\"pdir_fid\":\"0\",\"pwd_id\":\"" + shareId + "\",\"scene\":\"link\",\"stoken\":\"" + stoken
                        + "\",\"to_pdir_fid\":\"" + destFid + "\",\"fid_list\":[\"" + file[0] + "\"],\"fid_token_list\":[\"" + file[1] + "\"]}",
                cookie);
        JsonObject so = JsonUtil.parseObj(saveText);
        String taskId = so == null ? "" : JsonUtil.str(so.getAsJsonObject("data") == null ? so : so.getAsJsonObject("data"), "task_id", "");
        if (taskId.isEmpty()) {
            String msg = so == null ? "" : JsonUtil.str(so, "message", "");
            throw new Exception("转存失败" + (msg.isEmpty() ? "" : "：" + msg));
        }
        for (int i = 0; i < 12; i++) {
            Thread.sleep(800);
            String taskText = relayRaw(siteKey, "get",
                    "https://drive-pc.quark.cn/1/clouddrive/task?pr=ucpro&fr=pc&uc_param_str=&task_id=" + enc(taskId)
                            + "&retry_index=" + i + "&__t=" + System.currentTimeMillis(), "", cookie);
            JsonObject to = JsonUtil.parseObj(taskText);
            JsonObject td = to == null ? null : to.getAsJsonObject("data");
            JsonObject sa = td == null ? null : td.getAsJsonObject("save_as");
            JsonArray arr = sa == null ? null : sa.getAsJsonArray("save_as_top_fids");
            if (arr != null && arr.size() > 0 && arr.get(0) != null && !arr.get(0).isJsonNull()) {
                return arr.get(0).getAsString();
            }
        }
        throw new Exception("转存超时（任务未完成）");
    }

    /** 递归列出分享文件（深度 ≤ 3）：优先原 fileId（取新 token），否则按集名匹配。返回 {fid, token, name}。 */
    static String[] findShareFile(String siteKey, String shareId, String stoken, String fileId, String epName, String cookie) {
        String cacheKey = shareId;
        Object[] c = SHARE_CACHE.get(cacheKey);
        java.util.List<String[]> files;
        if (c != null && System.currentTimeMillis() - (long) c[0] < 3 * 60 * 1000) {
            files = (java.util.List<String[]>) c[1];
        } else {
            files = new java.util.ArrayList<>();
            listShare(siteKey, shareId, stoken, "0", files, 0, cookie);
            SHARE_CACHE.put(cacheKey, new Object[]{System.currentTimeMillis(), files});
        }
        if (files.isEmpty()) return null;
        for (String[] f : files) if (f[0].equals(fileId)) return f;   // 原 fid 仍有效
        return pickFile(files, epName);                                // 按集名重新匹配
    }

    static void listShare(String siteKey, String shareId, String stoken, String pdirFid, java.util.List<String[]> out, int depth, String cookie) {
        if (depth > 3) return;
        String text;
        try {
            text = relayRaw(siteKey, "get",
                    "https://drive-pc.quark.cn/1/clouddrive/share/sharepage/detail?pr=ucpro&fr=pc&pwd_id=" + enc(shareId)
                            + "&stoken=" + enc(stoken) + "&pdir_fid=" + enc(pdirFid)
                            + "&force=0&_page=1&_size=100&_sort=file_type:asc,file_name:asc", "", cookie);
        } catch (Exception e) { return; }
        JsonObject o = JsonUtil.parseObj(text);
        JsonObject data = o == null ? null : o.getAsJsonObject("data");
        JsonArray list = data == null ? null : data.getAsJsonArray("list");
        if (list == null) return;
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject it = e.getAsJsonObject();
            String fid = JsonUtil.str(it, "fid", "");
            if (fid.isEmpty()) continue;
            JsonElement d = it.get("dir");
            boolean isDir = d != null && d.isJsonPrimitive() && d.getAsBoolean();
            if (isDir) {
                listShare(siteKey, shareId, stoken, fid, out, depth + 1, cookie);
            } else {
                out.add(new String[]{fid, JsonUtil.str(it, "share_fid_token", ""), JsonUtil.str(it, "file_name", "")});
            }
        }
    }

    /** 按集名提示匹配：精确文件名 → 同主名（扩展名不同）→ 主名前缀（64_HDR 等）。 */
    static String[] pickFile(java.util.List<String[]> files, String epName) {
        String hint = epName == null ? "" : epName.trim();
        hint = hint.replaceAll("\\[[^\\]]*(?:MB|GB|KB|TB)\\]\\s*$", ""); // 去掉 [107.90MB]
        int slash = Math.max(hint.lastIndexOf('/'), hint.lastIndexOf('\\'));
        if (slash >= 0) hint = hint.substring(slash + 1);
        final String exact = hint;
        String base = hint.replaceAll("\\.[A-Za-z0-9]{2,5}$", "").replaceAll("^\\[[^\\]]*\\]", "").trim();
        final String baseName = base;
        if (exact.isEmpty()) return null;
        for (String[] f : files) if (f[2].equals(exact)) return f;
        for (String[] f : files) {
            String fn = f[2].replaceAll("\\.[A-Za-z0-9]{2,5}$", "");
            if (!baseName.isEmpty() && fn.equalsIgnoreCase(baseName)) return f;
        }
        for (String[] f : files) {
            String fn = f[2].replaceAll("\\.[A-Za-z0-9]{2,5}$", "");
            if (!baseName.isEmpty() && fn.regionMatches(true, 0, baseName + "_", 0, baseName.length() + 1)) return f;
        }
        return null;
    }

    /** 目标目录 TVBox：查根目录，无则创建；缓存 fid。 */
    static String ensureFolder(String siteKey, String cookie) {
        String cached = DEST_FIDS.get(siteKey);
        if (cached != null) return cached;
        try {
            String text = relayRaw(siteKey, "get",
                    "https://drive-pc.quark.cn/1/clouddrive/file/sort?pr=ucpro&fr=pc&uc_param_str=&pdir_fid=0"
                            + "&_page=1&_size=200&_fetch_total=1&_fetch_sub_dirs=0&_sort=file_type:asc,file_name:asc", "", cookie);
            String fid = findName(text, "TVBox");
            if (fid.isEmpty()) {
                String mk = relayRaw(siteKey, "post", "https://drive-pc.quark.cn/1/clouddrive/file?pr=ucpro&fr=pc",
                        "{\"pdir_fid\":\"0\",\"file_name\":\"TVBox\",\"dir_path\":\"\",\"dir_init_lock\":false}", cookie);
                JsonObject o = JsonUtil.parseObj(mk);
                JsonObject data = o == null ? null : o.getAsJsonObject("data");
                fid = data == null ? "" : JsonUtil.str(data, "fid", "");
            }
            if (!fid.isEmpty()) DEST_FIDS.put(siteKey, fid);
            return fid;
        } catch (Exception e) {
            return "";
        }
    }

    /** 目标目录中查找同名文件，返回 fid。 */
    static String findInFolder(String siteKey, String pdirFid, String name, String cookie) {
        try {
            String text = relayRaw(siteKey, "get",
                    "https://drive-pc.quark.cn/1/clouddrive/file/sort?pr=ucpro&fr=pc&uc_param_str=&pdir_fid=" + enc(pdirFid)
                            + "&_page=1&_size=200&_fetch_total=1&_fetch_sub_dirs=0&_sort=file_type:asc,file_name:asc", "", cookie);
            return findName(text, name);
        } catch (Exception e) {
            return "";
        }
    }

    /** 在 file/sort 响应里按 file_name 找 fid。 */
    static String findName(String text, String name) {
        JsonObject o = JsonUtil.parseObj(text);
        JsonObject data = o == null ? null : o.getAsJsonObject("data");
        JsonArray list = data == null ? null : data.getAsJsonArray("list");
        if (list == null) return "";
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject it = e.getAsJsonObject();
            if (name.equals(JsonUtil.str(it, "file_name", ""))) return JsonUtil.str(it, "fid", "");
        }
        return "";
    }

    static void deleteFile(String siteKey, String fid, String cookie) {
        try {
            relayRaw(siteKey, "post", "https://drive-pc.quark.cn/1/clouddrive/file/delete?pr=ucpro&fr=pc",
                    "{\"action_type\":2,\"filelist\":[\"" + fid + "\"]}", cookie);
        } catch (Exception ignored) { }
    }

    static String playApi(String siteKey, String fid, String cookie) throws Exception {
        return relayRaw(siteKey, "post",
                "https://drive-pc.quark.cn/1/clouddrive/file/v2/play?pr=ucpro&fr=pc&uc_param_str=",
                "{\"fid\":\"" + fid + "\",\"resolutions\":\"4k,2k,super,high,normal,low\",\"supports\":\"fmp4,m3u8\"}", cookie);
    }

    static String relayRaw(String siteKey, String method, String url, String body, String cookie) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("siteKey", siteKey);
        payload.addProperty("url", url);
        payload.addProperty("method", method);
        payload.addProperty("body", body);
        JsonObject headers = new JsonObject();
        headers.addProperty("Referer", "https://pan.quark.cn");
        if (!cookie.isEmpty()) headers.addProperty("Cookie", cookie);
        headers.addProperty("User-Agent", QUARK_UA);
        payload.add("headers", headers);
        return JarHost.postJson("/jarpost", payload.toString(), 60000);
    }

    /** 取播放流地址：优先 video_list 首个 accessable（宿主已把最高档排前）。 */
    public static String extractStreamUrl(String text) {
        try {
            JsonObject o = JsonUtil.parseObj(text);
            JsonObject data = o == null ? null : o.getAsJsonObject("data");
            JsonArray list = data == null ? null : data.getAsJsonArray("video_list");
            if (list != null) {
                for (JsonElement e : list) {
                    if (!e.isJsonObject()) continue;
                    JsonObject item = e.getAsJsonObject();
                    if (!JsonUtil.bool(item, "accessable", false)) continue;
                    JsonObject vi = item.getAsJsonObject("video_info");
                    String u = vi == null ? "" : JsonUtil.str(vi, "url", "");
                    if (!u.isEmpty()) return unescape(u);
                }
            }
        } catch (Exception ignored) { }
        String m = match(text, "\"url\":\"(https?://[^\"]+\\.m3u8[^\"]*)\"");
        if (!m.isEmpty()) return unescape(m);
        m = match(text, "\"url\":\"(https?://video-play[^\"]+)\"");
        if (!m.isEmpty()) return unescape(m);
        return "";
    }

    static String relay(String siteKey, String streamUrl) {
        Logger.d("QuarkPlay", "流地址就绪: " + streamUrl);
        return JarHost.baseUrl() + "/jarstream?siteKey=" + enc(siteKey) + "&url=" + enc(streamUrl);
    }

    static String savedFidFromHost(String siteKey) {
        try {
            return JarHost.get("/savedfid?siteKey=" + enc(siteKey), 8000).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** 把引擎侧的有效 Cookie 同步给 jar 宿主（TEMP\\TVBox），保证 jarstream 中继带对 Cookie（CDN 依赖 __puus）。 */
    static void syncCookieForHost(String cookie) {
        if (cookie == null || cookie.isEmpty()) return;
        try {
            JsonObject j = new JsonObject();
            j.addProperty("cookie", cookie);
            String text = j.toString();
            File tmp = new File(System.getenv("TEMP") == null ? "." : System.getenv("TEMP"), "TVBox");
            if (!tmp.exists()) tmp.mkdirs();
            Files.write(new File(tmp, "quark_cookie.txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(tmp, "quark_cookie").toPath(), text.getBytes(StandardCharsets.UTF_8));
            Logger.d("QuarkPlay", "Cookie 已同步给宿主（" + cookie.length() + " 字符, __puus=" + cookie.contains("__puus") + "）");
        } catch (Exception ignored) { }
    }

    // ---------- 缓存 ----------
    static synchronized String cachedFid(String key) {
        String fid = FIDS.get(key);
        if (fid == null) return null;
        long[] tick = TRANSFER.get(key);
        if (tick == null || System.currentTimeMillis() - tick[0] > TTL) {
            FIDS.remove(key);
            TRANSFER.remove(key);
            return null;
        }
        return fid;
    }

    static synchronized void remember(String key, String fid) {
        if (fid == null || fid.isEmpty()) return;
        FIDS.put(key, fid);
        TRANSFER.put(key, new long[]{System.currentTimeMillis()});
    }

    static synchronized void removeFid(String key) {
        FIDS.remove(key);
        TRANSFER.remove(key);
    }

    // ---------- cookie ----------
    static String readCookie() {
        String[] dirs = { AppPaths.JarCache + "\\files\\Pizazz", System.getenv("TEMP") + "\\TVBox" };
        String[] names = { "quark_cookie.txt", "quark_cookie" };
        for (String dir : dirs) {
            for (String name : names) {
                File f = new File(dir, name);
                if (!f.exists()) continue;
                try {
                    String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                    if (text.isEmpty()) continue;
                    if (text.startsWith("{")) {
                        JsonObject o = JsonUtil.parseObj(text);
                        String c = o == null ? "" : JsonUtil.str(o, "cookie", "");
                        if (!c.isEmpty()) return c;
                    } else {
                        return text;
                    }
                } catch (Exception ignored) { }
            }
        }
        return "";
    }

    // ---------- 工具 ----------
    static Map<String, String> query(String url) {
        Map<String, String> m = new java.util.HashMap<>();
        try {
            int i = url.indexOf('?');
            if (i < 0) return m;
            for (String pair : url.substring(i + 1).split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                m.put(java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            }
        } catch (Exception ignored) { }
        return m;
    }

    static String match(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text == null ? "" : text);
            return m.find() ? m.group(1) : "";
        } catch (Exception e) {
            return "";
        }
    }

    static String unescape(String s) {
        return s.replace("\\u0026", "&").replace("\\u003d", "=").replace("\\/", "/");
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }
}
