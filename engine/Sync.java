package dev.flytv.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 云端数据同步（WebDAV + AES-256-GCM）。
 * 一键把「点播配置 + 网盘Cookie + 引擎设置 + 历史 + 收藏」加密打包上传，
 * 在其它设备下载还原（配置覆盖 + .bak 备份，历史/收藏按 key 合并）。
 * 地址形如 http://user:pass@host:port/dav/backup.dat（账号密码写在 URL 里）。
 */
public final class Sync {
    private static final byte[] MAGIC = "FLYTVSYNC1".getBytes(StandardCharsets.US_ASCII);
    private static final String[] COOKIE_FILES = {"quark_cookie.txt", "uc_cookie.txt", "baidu_cookie.txt", "ali_cookie.txt"};
    private static final int MAX_BLOB = 8 * 1024 * 1024;

    // ---------- 对外接口 ----------

    public static JsonObject status() {
        JsonObject o = new JsonObject();
        String url = Setting.getString("sync_url", "");
        o.addProperty("url", url);
        o.addProperty("hasPass", !Setting.getString("sync_pass", "").isEmpty());
        o.addProperty("auto", Setting.getBool("sync_auto", true));
        o.addProperty("interval", Setting.getInt("sync_interval", 1));
        o.addProperty("lastUp", Setting.getString("sync_last_up", ""));
        o.addProperty("lastDown", Setting.getString("sync_last_down", ""));
        return o;
    }

    // JSON 包装（异常转 error 字段，Api 直接调用）
    public static String statusJson() { return status().toString(); }

    public static String saveJson(String url, String pass) { return save(url, pass).toString(); }

    public static String saveJson3(String url, String pass, String auto) { return save(url, pass, auto).toString(); }

    public static String saveJson4(String url, String pass, String auto, String interval) { return save(url, pass, auto, interval).toString(); }

    public static String autoJson() {
        try { return autoSync().toString(); } catch (Exception e) { return errJson(e); }
    }

    public static String uploadJson() {
        try { return upload().toString(); } catch (Exception e) { return errJson(e); }
    }

    public static String downloadJson() {
        try { return download().toString(); } catch (Exception e) { return errJson(e); }
    }

    static String errJson(Exception e) {
        JsonObject o = new JsonObject();
        o.addProperty("error", e.getMessage() == null ? "同步失败" : e.getMessage());
        return o.toString();
    }

    public static JsonObject save(String url, String pass) { return save(url, pass, null, null); }

    public static JsonObject save(String url, String pass, String auto) { return save(url, pass, auto, null); }

    public static JsonObject save(String url, String pass, String auto, String interval) {
        JsonObject o = new JsonObject();
        url = url == null ? "" : url.trim();
        if (!url.isEmpty() && !url.startsWith("http")) {
            o.addProperty("error", "同步地址需要以 http/https 开头（WebDAV）");
            return o;
        }
        Setting.put("sync_url", url);
        if (pass != null && !pass.isEmpty()) Setting.put("sync_pass", pass);
        if (auto != null && !auto.isEmpty()) Setting.put("sync_auto", "1".equals(auto) || "true".equalsIgnoreCase(auto));
        if (interval != null && !interval.isEmpty()) {
            try {
                int iv = Integer.parseInt(interval.trim());
                if (iv >= 1 && iv <= 60) Setting.put("sync_interval", iv);
            } catch (Exception ignored) { }
        }
        o.addProperty("ok", true);
        return o;
    }

    /** 打包并上传（加密后 PUT）。 */
    public static JsonObject upload() throws Exception {
        String url = Setting.getString("sync_url", "");
        String pass = Setting.getString("sync_pass", "");
        if (url.isEmpty()) throw new Exception("请先填写同步地址");
        if (pass.isEmpty()) throw new Exception("请先设置同步口令（加密用）");
        JsonObject files = new JsonObject();
        // 1. 根目录数据文件
        for (String name : new String[]{"configs.json", "prefs.json", "history.json", "keep.json"}) {
            File f = new File(AppPaths.Root, name);
            if (!f.exists()) continue;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            if ("prefs.json".equals(name)) text = stripDeviceUuid(text);
            files.addProperty(name, text);
        }
        // 2. 网盘 Cookie（Pizazz 目录；TEMP 副本由宿主自动维护）
        File piz = new File(AppPaths.JarCache, "files" + File.separator + "Pizazz");
        for (String name : COOKIE_FILES) {
            File f = new File(piz, name);
            if (!f.exists()) continue;
            files.addProperty("cookie:" + name, new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        }
        JsonObject bundle = new JsonObject();
        bundle.addProperty("app", "FlyTV");
        bundle.addProperty("version", 1);
        bundle.addProperty("time", System.currentTimeMillis());
        bundle.add("files", files);
        byte[] blob = encrypt(gzip(bundle.toString().getBytes(StandardCharsets.UTF_8)), pass);
        int code = put(url, blob);
        if (code < 200 || code >= 300) throw new Exception("上传失败（HTTP " + code + "），请检查地址/账号密码");
        Setting.put("sync_last_up", String.valueOf(System.currentTimeMillis()));
        JsonObject o = status();
        o.addProperty("ok", true);
        o.addProperty("size", blob.length);
        o.addProperty("count", files.size());
        Logger.d("Sync", "上传完成: " + blob.length + " 字节, " + files.size() + " 项");
        return o;
    }

    /** 下载并还原（解密 → 配置覆盖 + 历史/收藏合并）。 */
    public static JsonObject download() throws Exception {
        String url = Setting.getString("sync_url", "");
        String pass = Setting.getString("sync_pass", "");
        if (url.isEmpty()) throw new Exception("请先填写同步地址");
        if (pass.isEmpty()) throw new Exception("请先设置同步口令（加密用）");
        byte[] blob = get(url);
        if (blob == null) throw new Exception("云端还没有备份（请先在主力设备上传）");
        String json = new String(gunzip(decrypt(blob, pass)), StandardCharsets.UTF_8);
        JsonObject bundle = JsonUtil.parseObj(json);
        JsonObject files = bundle == null ? null : bundle.getAsJsonObject("files");
        if (files == null) throw new Exception("云端文件格式不对（或口令错误）");
        int applied = 0;
        // 1. 配置类：覆盖 + .bak 备份
        for (String name : new String[]{"configs.json", "prefs.json"}) {
            if (!files.has(name)) continue;
            backup(name);
            if ("prefs.json".equals(name)) {
                applyPrefs(files.get(name).getAsString());
            } else {
                writeFile(new File(AppPaths.Root, name), files.get(name).getAsString());
            }
            applied++;
        }
        // 2. 历史：按 key 合并（createTime 新的胜出）
        if (files.has("history.json")) {
            JsonArray arr = JsonUtil.parseArr(files.get("history.json").getAsString());
            if (arr != null) for (JsonElement e : arr) {
                try {
                    if (!e.isJsonObject()) continue;
                    JsonObject t = e.getAsJsonObject();
                    String key = JsonUtil.str(t, "key", "");
                    if (key.isEmpty()) continue;
                    int cid = JsonUtil.integer(t, "cid", Api.currentCid());
                    JsonObject mine = Stores.findHistory(cid, key);
                    if (mine == null || JsonUtil.lng(t, "createTime", 0) > JsonUtil.lng(mine, "createTime", 0)) {
                        Stores.saveHistory(t);
                    }
                } catch (Exception ignored) { }
            }
            applied++;
        }
        // 3. 收藏：按 key 去重合并
        if (files.has("keep.json")) {
            JsonArray arr = JsonUtil.parseArr(files.get("keep.json").getAsString());
            if (arr != null) for (JsonElement e : arr) {
                try {
                    if (!e.isJsonObject()) continue;
                    JsonObject t = e.getAsJsonObject();
                    String key = JsonUtil.str(t, "key", "");
                    if (key.isEmpty()) continue;
                    int cid = JsonUtil.integer(t, "cid", Api.currentCid());
                    if (Stores.findKeep(cid, key) == null) Stores.saveKeep(t);
                } catch (Exception ignored) { }
            }
            applied++;
        }
        // 4. 网盘 Cookie：写回 Pizazz + TEMP 两份（宿主与引擎共用）
        for (String name : COOKIE_FILES) {
            String field = "cookie:" + name;
            if (!files.has(field)) continue;
            String text = files.get(field).getAsString();
            File piz = new File(AppPaths.JarCache, "files" + File.separator + "Pizazz");
            if (!piz.exists()) piz.mkdirs();
            writeFile(new File(piz, name), text);
            writeFile(new File(piz, name.replace(".txt", "")), text);
            String tmpDir = System.getenv("TEMP");
            if (tmpDir != null) {
                File tmp = new File(tmpDir, "TVBox");
                if (!tmp.exists()) tmp.mkdirs();
                writeFile(new File(tmp, name), text);
                writeFile(new File(tmp, name.replace(".txt", "")), text);
            }
            applied++;
        }
        Setting.put("sync_last_down", String.valueOf(System.currentTimeMillis()));
        // 5. 刷新内存缓存（配置/设置/站点）
        Stores.clearCache();
        Setting.load();
        try { VodConfig.loadStartup(); } catch (Exception ignored) { }
        JsonObject o = status();
        o.addProperty("ok", true);
        o.addProperty("applied", applied);
        o.addProperty("size", blob.length);
        Logger.d("Sync", "下载还原完成: " + applied + " 项");
        return o;
    }

    // ---------- 自动同步（历史/收藏，独立小文件 history.dat） ----------

    /** 由同步地址推导历史小文件地址（同目录 history.dat）。 */
    static String historyUrl(String url) {
        try {
            int q = url.indexOf('?');
            String base = q >= 0 ? url.substring(0, q) : url;
            int slash = base.lastIndexOf('/');
            if (slash < 0) return base + ".history.dat";
            return base.substring(0, slash + 1) + "history.dat" + (q >= 0 ? url.substring(q) : "");
        } catch (Exception e) {
            return url + ".history.dat";
        }
    }

    /** 合并历史数组（key 相同取 createTime 新者）。 */
    static void mergeHistoryText(String text) {
        JsonArray arr = JsonUtil.parseArr(text);
        if (arr == null) return;
        for (JsonElement e : arr) {
            try {
                if (!e.isJsonObject()) continue;
                JsonObject t = e.getAsJsonObject();
                String key = JsonUtil.str(t, "key", "");
                if (key.isEmpty()) continue;
                int cid = JsonUtil.integer(t, "cid", Api.currentCid());
                JsonObject mine = Stores.findHistory(cid, key);
                if (mine == null || JsonUtil.lng(t, "createTime", 0) > JsonUtil.lng(mine, "createTime", 0)) {
                    Stores.saveHistory(t);
                }
            } catch (Exception ignored) { }
        }
    }

    /** 合并收藏数组（key 去重）。 */
    static void mergeKeepText(String text) {
        JsonArray arr = JsonUtil.parseArr(text);
        if (arr == null) return;
        for (JsonElement e : arr) {
            try {
                if (!e.isJsonObject()) continue;
                JsonObject t = e.getAsJsonObject();
                String key = JsonUtil.str(t, "key", "");
                if (key.isEmpty()) continue;
                int cid = JsonUtil.integer(t, "cid", Api.currentCid());
                if (Stores.findKeep(cid, key) == null) Stores.saveKeep(t);
            } catch (Exception ignored) { }
        }
    }

    private static volatile long lastHistRev = -1, lastKeepRev = -1;

    /** 自动同步一次：拉取云端历史/收藏 → 合并 → 有变化则回传（不动配置/Cookie）。 */
    public static JsonObject autoSync() throws Exception {
        String url = Setting.getString("sync_url", "");
        String pass = Setting.getString("sync_pass", "");
        JsonObject o = new JsonObject();
        if (url.isEmpty() || pass.isEmpty()) { o.addProperty("skip", true); return o; }
        String hurl = historyUrl(url);
        long histBefore = Stores.historyRevision(), keepBefore = Stores.keepRevision();
        byte[] blob = get(hurl);
        if (blob != null) {
            String json = new String(gunzip(decrypt(blob, pass)), StandardCharsets.UTF_8);
            JsonObject bundle = JsonUtil.parseObj(json);
            JsonObject files = bundle == null ? null : bundle.getAsJsonObject("files");
            if (files != null) {
                if (files.has("history.json")) mergeHistoryText(files.get("history.json").getAsString());
                if (files.has("keep.json")) mergeKeepText(files.get("keep.json").getAsString());
            }
        }
        boolean pulled = Stores.historyRevision() != histBefore || Stores.keepRevision() != keepBefore;
        long hr = Stores.historyRevision(), kr = Stores.keepRevision();
        boolean pushed = false;
        if (pulled || hr != lastHistRev || kr != lastKeepRev) {
            JsonObject files = new JsonObject();
            File hf = new File(AppPaths.Root, "history.json");
            if (hf.exists()) files.addProperty("history.json", new String(Files.readAllBytes(hf.toPath()), StandardCharsets.UTF_8));
            File kf = new File(AppPaths.Root, "keep.json");
            if (kf.exists()) files.addProperty("keep.json", new String(Files.readAllBytes(kf.toPath()), StandardCharsets.UTF_8));
            JsonObject bundle = new JsonObject();
            bundle.addProperty("app", "FlyTV");
            bundle.addProperty("kind", "history");
            bundle.addProperty("time", System.currentTimeMillis());
            bundle.add("files", files);
            byte[] data = encrypt(gzip(bundle.toString().getBytes(StandardCharsets.UTF_8)), pass);
            int code = put(hurl, data);
            if (code >= 200 && code < 300) {
                pushed = true;
                lastHistRev = hr;
                lastKeepRev = kr;
            }
        }
        o.addProperty("ok", true);
        o.addProperty("pulled", pulled);
        o.addProperty("pushed", pushed);
        return o;
    }

    /** 自动同步线程：按设置间隔（默认 2 分钟）拉取合并 + 有变化回传；15 秒粒度检查，改设置即时生效。 */
    public static void startAutoSync() {
        Thread t = new Thread(() -> {
            try { Thread.sleep(60000); } catch (InterruptedException ignored) { return; }
            long last = 0;
            while (true) {
                try {
                    int iv = Setting.getInt("sync_interval", 1);
                    if (iv < 1) iv = 1;
                    if (iv > 60) iv = 60;
                    long now = System.currentTimeMillis();
                    if (Setting.getBool("sync_auto", true) && now - last >= iv * 60_000L) {
                        last = now;
                        JsonObject r = autoSync();
                        if (JsonUtil.bool(r, "pulled", false) || JsonUtil.bool(r, "pushed", false)) {
                            Logger.d("AutoSync", "自动同步 拉取=" + JsonUtil.bool(r, "pulled", false) + " 回传=" + JsonUtil.bool(r, "pushed", false));
                        }
                    }
                } catch (Exception e) { Logger.d("AutoSync", "异常: " + e.getMessage()); }
                try { Thread.sleep(15 * 1000); } catch (InterruptedException ignored) { return; }
            }
        }, "auto-sync");
        t.setDaemon(true);
        t.start();
        Logger.d("AutoSync", "自动同步已启动（默认每 1 分钟，可设置 1-60）");
    }

    // ---------- 内部实现 ----------

    static String stripDeviceUuid(String prefsJson) {
        try {
            JsonObject o = JsonUtil.parseObj(prefsJson);
            if (o == null) return prefsJson;
            o.remove("device_uuid");
            return o.toString();
        } catch (Exception e) {
            return prefsJson;
        }
    }

    /** prefs 合并：除 device_uuid 外逐项写入（保留本机设备标识）。 */
    static void applyPrefs(String remoteJson) {
        JsonObject o = JsonUtil.parseObj(remoteJson);
        if (o == null) return;
        for (java.util.Map.Entry<String, JsonElement> e : o.entrySet()) {
            String k = e.getKey();
            if ("device_uuid".equals(k)) continue;
            JsonElement v = e.getValue();
            if (v == null || v.isJsonNull()) continue;
            if (v.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive p = v.getAsJsonPrimitive();
                if (p.isBoolean()) Setting.put(k, p.getAsBoolean());
                else if (p.isNumber()) Setting.put(k, p.getAsNumber());
                else Setting.put(k, p.getAsString());
            }
        }
    }

    static void backup(String name) {
        try {
            File f = new File(AppPaths.Root, name);
            if (f.exists()) Files.copy(f.toPath(), new File(AppPaths.Root, name + ".bak").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Logger.e("Sync", "备份失败 " + name + ": " + e.getMessage());
        }
    }

    static void writeFile(File f, String text) throws Exception {
        Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 加密（AES-256-GCM + PBKDF2） ----------

    static byte[] encrypt(byte[] plain, String pass) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        rnd.nextBytes(salt);
        rnd.nextBytes(iv);
        SecretKeySpec key = deriveKey(pass, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] enc = c.doFinal(plain);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(MAGIC);
        bos.write(salt);
        bos.write(iv);
        bos.write(enc);
        return bos.toByteArray();
    }

    static byte[] decrypt(byte[] blob, String pass) throws Exception {
        if (blob.length < MAGIC.length + 28) throw new Exception("云端文件不完整");
        for (int i = 0; i < MAGIC.length; i++) {
            if (blob[i] != MAGIC[i]) throw new Exception("云端文件不是 FlyTV 同步包");
        }
        byte[] salt = java.util.Arrays.copyOfRange(blob, MAGIC.length, MAGIC.length + 16);
        byte[] iv = java.util.Arrays.copyOfRange(blob, MAGIC.length + 16, MAGIC.length + 28);
        byte[] enc = java.util.Arrays.copyOfRange(blob, MAGIC.length + 28, blob.length);
        SecretKeySpec key = deriveKey(pass, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        try {
            return c.doFinal(enc);
        } catch (Exception e) {
            throw new Exception("解密失败：同步口令不对？");
        }
    }

    static SecretKeySpec deriveKey(String pass, byte[] salt) throws Exception {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, 120000, 256);
        byte[] key = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(data);
        }
        return bos.toByteArray();
    }

    static byte[] gunzip(byte[] data) throws Exception {
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = gz.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    // ---------- WebDAV（Basic 认证从 URL 解析） ----------

    static HttpURLConnection open(String urlStr, String method) throws Exception {
        URL u = new URL(urlStr);
        String userInfo = u.getUserInfo();
        String clean = urlStr;
        if (userInfo != null && !userInfo.isEmpty()) {
            clean = urlStr.replaceFirst("//" + java.util.regex.Pattern.quote(userInfo) + "@", "//");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(clean).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(false);
        if (userInfo != null && !userInfo.isEmpty()) {
            String b64 = Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
            c.setRequestProperty("Authorization", "Basic " + b64);
        }
        return c;
    }

    static int put(String url, byte[] body) throws Exception {
        HttpURLConnection c = open(url, "PUT");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        c.getOutputStream().write(body);
        int code = c.getResponseCode();
        c.disconnect();
        return code;
    }

    static byte[] get(String url) throws Exception {
        HttpURLConnection c = open(url, "GET");
        int code = c.getResponseCode();
        if (code == 404) { c.disconnect(); return null; }
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new Exception("下载失败（HTTP " + code + "），请检查地址/账号密码");
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BLOB) throw new Exception("云端文件过大（超过 8MB）");
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            c.disconnect();
        }
    }
}
