package dev.flytv.engine;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网盘扫码登录（目前实现：夸克）。
 * 流程：getTokenForQrcodeLogin 取 token → 前端展示二维码（夸克 App 扫）
 *      → 轮询 getServiceTicketByQrcodeToken 取 service_ticket
 *      → /account/info?st=&lw=scan 换 cookie → 写入三处存储（config.json / jarcache / TEMP）。
 * 仅用 java.base（HttpURLConnection），兼容精简 JRE。
 */
public final class PanLogin {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final ConcurrentHashMap<String, String> SESSIONS = new ConcurrentHashMap<>(); // session -> qr token

    /** 极简 HTTP：手动跟随重定向 + 收集 Set-Cookie。 */
    static class Resp {
        int code;
        String body = "";
    }

    static Resp get(String url, LinkedHashMap<String, String> jar) throws Exception {
        Resp out = new Resp();
        for (int hop = 0; hop <= 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "*/*");
                if (!jar.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> e : jar.entrySet()) {
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(e.getKey()).append("=").append(e.getValue());
                    }
                    c.setRequestProperty("Cookie", sb.toString());
                }
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                Map<String, List<String>> hs = c.getHeaderFields();
                for (Map.Entry<String, List<String>> e : hs.entrySet()) {
                    if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
                    for (String v : e.getValue()) {
                        int eq = v.indexOf('=');
                        if (eq <= 0) continue;
                        String name = v.substring(0, eq).trim();
                        String val = v.substring(eq + 1);
                        int sc = val.indexOf(';');
                        if (sc >= 0) val = val.substring(0, sc);
                        if (!name.isEmpty()) jar.put(name, val.trim());
                    }
                }
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) {
                        out.code = code;
                        return out;
                    }
                    url = new URL(new URL(url), loc).toString();
                    continue;
                }
                InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                out.body = is == null ? "" : readAll(is);
                out.code = code;
                return out;
            } finally {
                c.disconnect();
            }
        }
        out.code = 0;
        return out;
    }

    static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 生成二维码会话：返回 {session, url}（url 由前端渲染成二维码图片）。 */
    public static JsonObject qrStart() throws Exception {
        Resp r = get("https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin?client_id=532&v=1.2&request_id=" + UUID.randomUUID(),
                new LinkedHashMap<>());
        JsonObject o = JsonUtil.parseObj(r.body);
        JsonObject data = o == null ? null : o.getAsJsonObject("data");
        JsonObject members = data == null ? null : data.getAsJsonObject("members");
        String token = members == null ? "" : JsonUtil.str(members, "token", "");
        if (token.isEmpty()) throw new Exception("获取二维码失败（接口返回异常，稍后再试）");
        String session = UUID.randomUUID().toString().replace("-", "");
        SESSIONS.put(session, token);
        JsonObject out = new JsonObject();
        out.addProperty("session", session);
        out.addProperty("url", qrUrl(token));
        return out;
    }

    static String qrUrl(String token) {
        return "https://su.quark.cn/4_eMHBJ?token=" + enc(token)
                + "&client_id=532&ssb=weblogin&uc_param_str="
                + "&uc_biz_str=" + enc("S:custom|OPT:SAREA@0|OPT:IMMERSIVE@1|OPT:BACK_BTN_STYLE@0");
    }

    /** 轮询扫码状态：{status: wait|ok|expired, message, nickname?, member?}。 */
    public static JsonObject qrPoll(String session) throws Exception {
        JsonObject out = new JsonObject();
        String token = session == null ? null : SESSIONS.get(session);
        if (token == null) {
            out.addProperty("status", "expired");
            out.addProperty("message", "二维码会话已失效，请刷新");
            return out;
        }
        Resp r = get("https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken?client_id=532&v=1.2&token="
                + enc(token) + "&request_id=" + UUID.randomUUID(), new LinkedHashMap<>());
        JsonObject o = JsonUtil.parseObj(r.body);
        int status = o == null ? 0 : JsonUtil.integer(o, "status", 0);
        JsonObject data = o == null ? null : o.getAsJsonObject("data");
        JsonObject members = data == null ? null : data.getAsJsonObject("members");
        String ticket = members == null ? "" : JsonUtil.str(members, "service_ticket", "");
        if (status == 2000000 && !ticket.isEmpty()) {
            SESSIONS.remove(session);
            String[] info = exchangeTicket(ticket); // {cookie, nickname, member}
            if (info[0].isEmpty()) throw new Exception("登录成功但换取 Cookie 失败，请重试");
            saveQuarkCookie(info[0], info[1], info[2]);
            out.addProperty("status", "ok");
            out.addProperty("nickname", info[1]);
            out.addProperty("member", info[2]);
            out.addProperty("message", "登录成功");
            return out;
        }
        if (status == 50004002 || status == 50004003 || status == 50004004) {
            SESSIONS.remove(session);
            out.addProperty("status", "expired");
            out.addProperty("message", status == 50004002 ? "二维码已过期，请刷新"
                    : status == 50004004 ? "已取消登录" : "登录失败，请重试");
            return out;
        }
        String msg = o == null ? "" : JsonUtil.str(o, "message", "");
        out.addProperty("status", "wait");
        boolean generic = msg.isEmpty() || "ok".equals(msg) || "Query result is empty".equalsIgnoreCase(msg);
        out.addProperty("message", generic ? "等待扫码…" : msg);
        return out;
    }

    /** service_ticket 换 cookie：GET /account/info?st=&lw=scan（跟随跳转，收集 quark.cn 域 Cookie）。 */
    static String[] exchangeTicket(String ticket) {
        try {
            LinkedHashMap<String, String> jar = new LinkedHashMap<>();
            Resp r = get("https://pan.quark.cn/account/info?st=" + enc(ticket) + "&lw=scan", jar);
            String cookie = joinCookies(jar);
            String nickname = "";
            String member = "";
            JsonObject o = JsonUtil.parseObj(r.body);
            if (o != null) {
                nickname = JsonUtil.str(o, "nickname", "");
                JsonObject d = o.getAsJsonObject("data");
                if (nickname.isEmpty() && d != null) nickname = JsonUtil.str(d, "nickname", "");
            }
            try {
                Resp m = get("https://drive-pc.quark.cn/1/clouddrive/member?pr=ucpro&fr=pc&uc_param_str=&fetch_subscribe=true&_ch=home&fetch_identity=true", jar);
                JsonObject mo = JsonUtil.parseObj(m.body);
                JsonObject md = mo == null ? null : mo.getAsJsonObject("data");
                if (md != null) {
                    if (nickname.isEmpty()) nickname = JsonUtil.str(md, "nickname", "");
                    member = JsonUtil.str(md, "member_type", "");
                }
            } catch (Exception ignored) { }
            return new String[]{cookie, nickname, member};
        } catch (Exception e) {
            return new String[]{"", "", ""};
        }
    }

    static String joinCookies(LinkedHashMap<String, String> jar) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : jar.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    /** 保存夸克 cookie 到三处：jarcache 文件、config.json 字段、TEMP\\TVBox（jar 宿主用）。 */
    public static void saveQuarkCookie(String cookie, String nickname, String member) throws Exception {
        File dir = new File(AppPaths.JarCache, "files" + File.separator + "Pizazz");
        if (!dir.exists()) dir.mkdirs();
        JsonObject j = new JsonObject();
        j.addProperty("cookie", cookie);
        j.addProperty("nickname", nickname == null ? "" : nickname);
        j.addProperty("member_type", member == null ? "" : member);
        j.addProperty("time", System.currentTimeMillis());
        String text = j.toString();
        writeUtf8(new File(dir, "quark_cookie.txt"), text);
        writeUtf8(new File(dir, "quark_cookie"), text);
        File cfg = new File(dir, "config.json");
        if (cfg.exists()) {
            try {
                JsonObject c = JsonUtil.parseObj(new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8));
                if (c != null) {
                    c.addProperty("quark_cookie", cookie);
                    writeUtf8(cfg, c.toString());
                }
            } catch (Exception ignored) { }
        }
        try {
            File tmp = new File(System.getenv("TEMP") == null ? "." : System.getenv("TEMP"), "TVBox");
            if (!tmp.exists()) tmp.mkdirs();
            writeUtf8(new File(tmp, "quark_cookie.txt"), text);
            writeUtf8(new File(tmp, "quark_cookie"), text);
        } catch (Exception ignored) { }
    }

    /** 手动粘贴 cookie 保存（任意网盘：quark / uc / baidu）。 */
    public static JsonObject saveManual(String drive, String cookie) throws Exception {
        String base = "quark".equals(drive) ? "quark_cookie" : "uc".equals(drive) ? "uc_cookie" : "baidu_cookie";
        cookie = cookie == null ? "" : cookie.trim().replace("\r", "").replace("\n", " ");
        JsonObject out = new JsonObject();
        if (cookie.isEmpty()) {
            out.addProperty("error", "Cookie 不能为空");
            return out;
        }
        File dir = new File(AppPaths.JarCache, "files" + File.separator + "Pizazz");
        if (!dir.exists()) dir.mkdirs();
        JsonObject j = new JsonObject();
        j.addProperty("cookie", cookie);
        j.addProperty("time", System.currentTimeMillis());
        String text = j.toString();
        writeUtf8(new File(dir, base + ".txt"), text);
        writeUtf8(new File(dir, base), text);
        File cfg = new File(dir, "config.json");
        if (cfg.exists()) {
            try {
                JsonObject c = JsonUtil.parseObj(new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8));
                if (c != null) {
                    c.addProperty(base, cookie);
                    writeUtf8(cfg, c.toString());
                }
            } catch (Exception ignored) { }
        }
        try {
            File tmp = new File(System.getenv("TEMP") == null ? "." : System.getenv("TEMP"), "TVBox");
            if (!tmp.exists()) tmp.mkdirs();
            writeUtf8(new File(tmp, base + ".txt"), text);
            writeUtf8(new File(tmp, base), text);
        } catch (Exception ignored) { }
        out.addProperty("ok", true);
        out.addProperty("message", "已保存");
        return out;
    }

    static void writeUtf8(File f, String s) throws Exception {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }
}
