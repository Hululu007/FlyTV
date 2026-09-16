package dev.flytv.engine;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 图片代理（等价 C# /api/pic）：并发闸门 + 磁盘缓存（原子写+完整性校验）+ 头解析重试。 */
public final class PicProxy {
    private static final Semaphore GATE = new Semaphore(12);
    private static final Map<String, Object> LOCKS = new HashMap<>();

    public static byte[] fetch(String rawUrl) throws Exception {
        File cacheDir = new File(AppPaths.Cache, "webimg");
        cacheDir.mkdirs();
        String key = md5(rawUrl);
        File file = new File(cacheDir, key + ".img");
        if (file.exists()) {
            try {
                byte[] cached = java.nio.file.Files.readAllBytes(file.toPath());
                if (cached.length > 0 && complete(cached)) return cached;
                file.delete();
            } catch (Exception ignored) { }
        }
        GATE.acquire();
        try {
            // 二次检查（并发去重）
            if (file.exists()) {
                try {
                    byte[] cached = java.nio.file.Files.readAllBytes(file.toPath());
                    if (cached.length > 0 && complete(cached)) return cached;
                } catch (Exception ignored) { }
            }
            return download(rawUrl, file);
        } finally {
            GATE.release();
        }
    }

    static byte[] download(String rawUrl, File file) throws Exception {
        Parsed p = parse(rawUrl);
        Map<String, String> headers = new HashMap<>(p.headers);
        if (!headers.containsKey("User-Agent")) headers.put("User-Agent", HttpUtil.DEFAULT_UA);
        if (!headers.containsKey("Accept")) headers.put("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (attempt >= 2 && !headers.containsKey("Referer")) {
                    try {
                        java.net.URI u = java.net.URI.create(p.url);
                        headers.put("Referer", u.getScheme() + "://" + u.getAuthority() + "/");
                    } catch (Exception ignored) { }
                }
                byte[] body = httpGetBytes(p.url, headers, 15000);
                if (body != null && body.length > 0 && isImage(body)) {
                    try {
                        File tmp = new File(file.getAbsolutePath() + ".tmp");
                        java.nio.file.Files.write(tmp.toPath(), body);
                        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) { }
                    return body;
                }
            } catch (Exception ignored) { }
            if (attempt < 2) Thread.sleep(500);
        }
        Logger.d("PicProxy", "pic fail: " + p.url);
        return new byte[0];
    }

    /** TVBox 图片后缀解析：@Headers={...} / @Referer= / @User-Agent=。 */
    static Parsed parse(String raw) {
        Parsed p = new Parsed();
        String url = raw == null ? "" : raw.trim();
        int at = url.indexOf('@');
        if (at < 0) { p.url = url; return p; }
        p.url = url.substring(0, at);
        String rest = url.substring(at);
        try {
            Matcher hm = Pattern.compile("@Headers=(\\{.*?\\})", Pattern.DOTALL).matcher(rest);
            if (hm.find()) {
                com.google.gson.JsonObject o = JsonUtil.parseObj(hm.group(1));
                if (o != null)
                    for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet())
                        try { p.headers.put(e.getKey(), e.getValue().getAsString()); } catch (Exception ignored) { }
            }
            for (Map.Entry<String, String> pat : new java.util.AbstractMap.SimpleEntry[]{
                    new java.util.AbstractMap.SimpleEntry<>("Referer", "@Referer=([^@]+)"),
                    new java.util.AbstractMap.SimpleEntry<>("User-Agent", "@User-Agent=([^@]+)")}) {
                Matcher m = Pattern.compile(pat.getValue()).matcher(rest);
                if (m.find()) p.headers.put(pat.getKey(), m.group(1).trim());
            }
        } catch (Exception ignored) { }
        return p;
    }

    static class Parsed {
        String url = "";
        Map<String, String> headers = new HashMap<>();
    }

    public static byte[] httpGetBytes(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        okhttp3.Request.Builder b = new okhttp3.Request.Builder().url(url).get();
        for (Map.Entry<String, String> e : headers.entrySet()) b.header(e.getKey(), e.getValue());
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        try (okhttp3.Response resp = client.newCall(b.build()).execute()) {
            okhttp3.ResponseBody body = resp.body();
            return body == null ? null : body.bytes();
        }
    }

    static boolean isImage(byte[] b) {
        if (b.length < 12) return false;
        if (b[0] == (byte) 0xFF && b[1] == (byte) 0xD8) return true;
        if (b[0] == (byte) 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return true;
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return true;
        if (b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return true;
        return false;
    }

    /** 完整性：JPEG EOI / PNG IEND，防半张图。 */
    static boolean complete(byte[] b) {
        if (b.length < 16) return false;
        if (b[0] == (byte) 0xFF && b[1] == (byte) 0xD8)
            return b[b.length - 2] == (byte) 0xFF && b[b.length - 1] == (byte) 0xD9;
        if (b[0] == (byte) 0x89 && b[1] == 'P') {
            int from = Math.max(0, b.length - 24);
            String tail = new String(b, from, b.length - from, java.nio.charset.StandardCharsets.ISO_8859_1);
            return tail.contains("IEND");
        }
        return true;
    }

    public static String mime(byte[] b, String url) {
        if (b.length >= 3 && b[0] == (byte) 0xFF && b[1] == (byte) 0xD8) return "image/jpeg";
        if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 'P') return "image/png";
        if (b.length >= 6 && b[0] == 'G') return "image/gif";
        if (b.length >= 12 && b[8] == 'W') return "image/webp";
        return "image/jpeg";
    }

    static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
