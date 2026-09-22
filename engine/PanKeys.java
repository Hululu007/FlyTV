package dev.flytv.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 网盘 Key/设置 推送与拉取：
 * 把蜘蛛配置里的网盘登录凭据（夸克/UC/百度/阿里/115/123/迅雷/光鸭/哔哩 等）和画质/线程等设置，
 * 通过加密云文件 &lt;token&gt;-pan 在“电脑端 / 手机端”之间互相推送、拉取。
 */
public final class PanKeys {

    /** 需要同步的字段：网盘凭据 + 画质/线程/常用设置 */
    static final String[] SYNC_KEYS = {
            "quark_cookie", "uc_cookie", "baidu_cookie", "ali_cookie", "ali_token",
            "115_cookie", "123_cookie", "xunlei_cookie", "guangya_token", "guangya_cookie",
            "bili_cookie", "cloud189_cookie", "tianyi_cookie",
            "quarkQuality", "ucQuality", "baiduQuality", "aliQuality", "123Quality",
            "quarkThread", "ucThread", "baiduThread", "aliThread", "123Thread", "xunleiThread", "guangyaThread",
            "panOrder", "panView", "proxyMode", "pansouUrl",
            "quark_nickname", "quark_member"
    };

    static File[] candidates() {
        File jc = new File(AppPaths.JarCache);
        return new File[]{
                new File(jc, "peizhi.json"),
                new File(jc, "files" + File.separator + "peizhi.json"),
                new File(jc, "files" + File.separator + "Pizazz" + File.separator + "config.json"),
                new File(jc, "files" + File.separator + "Pizazz" + File.separator + "peizhi.json"),
        };
    }

    /** 收集本地要同步的键值（多个文件按顺序覆盖，后面的优先）。 */
    public static JsonObject collect() {
        JsonObject out = new JsonObject();
        for (File f : candidates()) {
            try {
                if (!f.isFile()) continue;
                JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : SYNC_KEYS) {
                    if (o.has(k) && !o.get(k).isJsonNull()) {
                        String v = o.get(k).getAsString();
                        if (v == null || v.trim().isEmpty()) continue;
                        v = v.trim();
                        // cookie 类若是 JSON（用户信息，不是真 cookie）→ 跳过，交给 .txt 单文件
                        boolean isCred = k.contains("cookie") || k.contains("token");
                        if (isCred && v.startsWith("{")) continue;
                        out.addProperty(k, v);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        // 单文件形式的 cookie（quark_cookie.txt / quark_cookie 等）：真实登录凭据，覆盖 JSON 里的值
        try {
            File pz = new File(AppPaths.JarCache, "files" + File.separator + "Pizazz");
            addTextKey(out, new File(pz, "quark_cookie.txt"), "quark_cookie", true);
            addTextKey(out, new File(pz, "quark_cookie"), "quark_cookie", true);
            addTextKey(out, new File(pz, "guangya.txt"), "guangya_token", true);
            addTextKey(out, new File(pz, "xunlei.txt"), "xunlei_cookie", true);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void addTextKey(JsonObject out, File f, String key, boolean override) {
        try {
            if (!f.isFile()) return;
            String v = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            if (v.isEmpty() || v.startsWith("{")) return;
            if (override || !out.has(key)) out.addProperty(key, v);
        } catch (Exception ignored) {
        }
    }

    /** 把云端的键值写回本地配置（就地更新已有键；凭据同时写 Pizazz/config.json 与单文件）。 */
    public static int apply(JsonObject kv) {
        int n = 0;
        for (File f : candidates()) {
            try {
                if (!f.isFile()) continue;
                JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                boolean changed = false;
                for (String k : SYNC_KEYS) {
                    if (kv.has(k) && !kv.get(k).isJsonNull() && o.has(k)) {
                        String nv = kv.get(k).getAsString();
                        String ov = o.get(k).isJsonNull() ? "" : o.get(k).getAsString();
                        if (!nv.equals(ov)) {
                            o.addProperty(k, nv);
                            changed = true;
                            n++;
                        }
                    }
                }
                if (changed) Files.write(f.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        // 凭据类：确保写进 Pizazz/config.json（不存在则创建）与单文件 cookie
        try {
            File cfg = candidates()[2];
            JsonObject o;
            if (cfg.isFile()) {
                o = JsonParser.parseString(new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                o = new JsonObject();
                File p = cfg.getParentFile();
                if (p != null) p.mkdirs();
            }
            boolean changed = false;
            for (String k : SYNC_KEYS) {
                if (k.endsWith("Quality") || k.endsWith("Thread") || k.equals("panOrder") || k.equals("panView") || k.equals("proxyMode") || k.equals("pansouUrl")) continue;
                if (kv.has(k) && !kv.get(k).isJsonNull() && !kv.get(k).getAsString().trim().isEmpty()) {
                    o.addProperty(k, kv.get(k).getAsString());
                    changed = true;
                }
            }
            if (changed) Files.write(cfg.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            String qc = kv.has("quark_cookie") && !kv.get("quark_cookie").isJsonNull() ? kv.get("quark_cookie").getAsString() : "";
            if (!qc.isEmpty()) {
                File pz = cfg.getParentFile();
                if (pz != null) {
                    String text = quarkCookieJson(qc);
                    Files.write(new File(pz, "quark_cookie.txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
                    Files.write(new File(pz, "quark_cookie").toPath(), text.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    /**
     * 云端夸克凭据 → 蜘蛛要的 JSON 文件格式。
     * 蜘蛛判定"已登录"要求 nickname/member_type 非空；另外必须剥掉外设备的 __puus
     * （__puus 是各设备自己的会话，跨设备带入会被夸克拒绝，本机会自行重新收获）。
     */
    static String quarkCookieJson(String qc) {
        try {
            JsonObject o = (qc != null && qc.trim().startsWith("{"))
                    ? JsonParser.parseString(qc).getAsJsonObject() : new JsonObject();
            String ck = JsonUtil.str(o, "cookie", "");
            if (ck.isEmpty()) ck = qc == null ? "" : qc.trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("__pus=[^;]+").matcher(ck);
            if (m.find()) ck = m.group(0);
            o.addProperty("cookie", ck);
            if (JsonUtil.str(o, "nickname", "").isEmpty()) o.addProperty("nickname", "已登录");
            if (JsonUtil.str(o, "member_type", "").isEmpty()) o.addProperty("member_type", "SUPER_VIP");
            return o.toString();
        } catch (Exception e) {
            return qc == null ? "" : qc;
        }
    }

    // ---------- 云端推送/拉取（复用 Sync 的加密与 HTTP） ----------

    public static JsonObject push() throws Exception {
        String url = Setting.getString("sync_url", "");
        String pass = Setting.getString("sync_pass", "");
        JsonObject o = new JsonObject();
        if (url.isEmpty() || pass.isEmpty()) {
            o.addProperty("ok", false);
            o.addProperty("error", "未配置同步（请先填 flytv:// 链接）");
            return o;
        }
        JsonObject kv = collect();
        if (kv.size() == 0) {
            o.addProperty("ok", false);
            o.addProperty("error", "本机没有可推送的网盘Key（先在网盘配置里登录）");
            return o;
        }
        // 先合并云端已有的 Key：本地优先，但不清掉云端独有的（防止本机清了某个Key后把云端也覆盖没）
        try {
            byte[] old = Sync.get(Sync.panUrl(Sync.authUrl(url, pass)));
            if (old != null && old.length > 0) {
                JsonObject ob = JsonParser.parseString(new String(Sync.gunzip(Sync.decrypt(old, pass)), StandardCharsets.UTF_8)).getAsJsonObject();
                if (ob.has("keys")) {
                    JsonObject ok = ob.getAsJsonObject("keys");
                    for (String k : ok.keySet()) {
                        if (!kv.has(k) && !ok.get(k).isJsonNull() && !ok.get(k).getAsString().trim().isEmpty()) {
                            kv.addProperty(k, ok.get(k).getAsString().trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        JsonObject bundle = new JsonObject();
        bundle.addProperty("app", "FlyTV");
        bundle.addProperty("kind", "pan");
        bundle.addProperty("time", System.currentTimeMillis());
        bundle.add("keys", kv);
        byte[] data = Sync.encrypt(Sync.gzip(bundle.toString().getBytes(StandardCharsets.UTF_8)), pass);
        int code = Sync.put(Sync.panUrl(Sync.authUrl(url, pass)), data);
        o.addProperty("ok", code >= 200 && code < 300);
        o.addProperty("pushed", kv.size());
        if (!(code >= 200 && code < 300)) o.addProperty("error", "上传失败 HTTP " + code);
        return o;
    }

    public static JsonObject pull() throws Exception {
        String url = Setting.getString("sync_url", "");
        String pass = Setting.getString("sync_pass", "");
        JsonObject o = new JsonObject();
        if (url.isEmpty() || pass.isEmpty()) {
            o.addProperty("ok", false);
            o.addProperty("error", "未配置同步（请先填 flytv:// 链接）");
            return o;
        }
        byte[] blob = Sync.get(Sync.panUrl(Sync.authUrl(url, pass)));
        if (blob == null || blob.length == 0) {
            o.addProperty("ok", false);
            o.addProperty("error", "云端还没有网盘Key（先在另一端点『推送Key』）");
            return o;
        }
        JsonObject bundle = JsonParser.parseString(new String(Sync.gunzip(Sync.decrypt(blob, pass)), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject kv = bundle.has("keys") ? bundle.getAsJsonObject("keys") : new JsonObject();
        int n = apply(kv);
        // 关键：拉取后自动重载爬虫宿主，让网盘登录立即生效（无需重启 FlyTV）
        boolean reloaded = false;
        try {
            Logger.d("PanKeys", "拉取完成（应用 " + n + " 项），重载爬虫宿主…");
            JarHost.stop();
            Thread.sleep(400);
            reloaded = JarHost.start();
        } catch (Throwable ignored) {
        }
        o.addProperty("ok", true);
        o.addProperty("applied", n);
        o.addProperty("keys", kv.size());
        o.addProperty("reloaded", reloaded);
        return o;
    }

    public static String pushJson() {
        try { return push().toString(); } catch (Exception e) { return Sync.errJson(e); }
    }

    public static String pullJson() {
        try { return pull().toString(); } catch (Exception e) { return Sync.errJson(e); }
    }

    // ---------- 云端自动保鲜：本机 cookie 变了就自动推送（每 30 分钟检查；避免别的设备拉到过期 cookie） ----------

    private static volatile String lastPushedHash = "";
    private static volatile boolean autoStarted = false;

    public static void startAutoPush() {
        if (autoStarted) return;
        autoStarted = true;
        Thread t = new Thread(() -> {
            try { Thread.sleep(10000); } catch (InterruptedException ie) { return; }
            while (true) {
                try {
                    String url = Setting.getString("sync_url", "");
                    String pass = Setting.getString("sync_pass", "");
                    if (!url.isEmpty() && !pass.isEmpty()) {
                        JsonObject kv = collect();
                        String qc = kv.has("quark_cookie") ? kv.get("quark_cookie").getAsString() : "";
                        String hash = qc.isEmpty() ? "" : sha256Hex(qc);
                        if (!hash.isEmpty() && !hash.equals(lastPushedHash)) {
                            JsonObject r = push();
                            if (JsonUtil.bool(r, "ok", false)) {
                                lastPushedHash = hash;
                                Logger.d("PanKeys", "自动保鲜：网盘Key已推送云端（cookie " + qc.length() + " 字符）");
                            }
                        }
                    }
                } catch (Throwable e) {
                    Logger.d("PanKeys", "自动保鲜异常: " + e.getMessage());
                }
                try { Thread.sleep(5 * 60 * 1000L); } catch (InterruptedException ie) { return; }
            }
        }, "pan-keys-autopush");
        t.setDaemon(true);
        t.start();
        Logger.d("PanKeys", "网盘Key自动保鲜已启动（每 5 分钟检查，cookie 一变就推云端）");
    }

    private static String sha256Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
