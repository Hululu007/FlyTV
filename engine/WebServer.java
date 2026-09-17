package dev.flytv.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** HTTP 服务器（jdk.httpserver，零依赖）：认证 → API / 静态 / 协议端点。 */
public final class WebServer {
    private static HttpServer server;
    private static int port;

    public static int port() { return port; }

    public static void start(int preferredPort, boolean lan) throws Exception {
        String host = lan ? "0.0.0.0" : "127.0.0.1";
        Exception last = null;
        for (int p = preferredPort; p < preferredPort + 20; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(host, p), 0);
                port = p;
                break;
            } catch (Exception e) {
                last = e;
            }
        }
        if (server == null) throw last != null ? last : new Exception("无可用端口");
        server.setExecutor(Executors.newFixedThreadPool(24));
        server.createContext("/", WebServer::dispatch);
        server.start();
        Logger.d("WebServer", "HTTP 服务已启动: http://" + host + ":" + port + "/ （lan=" + lan + "）");
    }

    public static void stop() {
        try { if (server != null) server.stop(0); } catch (Exception ignored) { }
        server = null;
    }

    private static void dispatch(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            // 认证（口令启用时）
            if (!AuthGate.check(ex, path)) { deny(ex, path); return; }

            // 二进制：图片代理
            if (path.equals("/api/pic")) { apiPic(ex); return; }
            // 转发：网盘流速度统计
            if (path.equals("/api/streamstats")) { json(ex, streamStats()); return; }
            // 登录（成功后下发 token cookie）
            if (path.equals("/api/login")) {
                String result = Api.handle(path, ex);
                if (result != null && result.contains("\"ok\":true") && AuthGate.enabled()) {
                    ex.getResponseHeaders().set("Set-Cookie",
                            "tvbox_token=" + AuthGate.expectedToken() + "; Path=/; Max-Age=2592000; HttpOnly; SameSite=Lax");
                }
                json(ex, result == null ? "{\"ok\":true}" : result);
                return;
            }
            // API
            if (path.startsWith("/api/")) {
                String result = Api.handle(path, ex);
                if (result != null) { json(ex, result); return; }
                text(ex, 404, "{\"error\":\"unknown api: " + path + "\"}");
                return;
            }
            // /action /proxy /media /tvbus
            if (Actions.handle(ex, path)) return;
            // 设备发现
            if (path.equals("/device")) { json(ex, DeviceJson.get()); return; }
            // Web 前端
            if (path.equals("/web") || path.startsWith("/web/")) { WebStatic.serve(ex, path); return; }
            text(ex, 404, "not found");
        } catch (Exception e) {
            Logger.e("WebServer", ex.getRequestURI() + " -> " + e);
            try { text(ex, 500, "error"); } catch (Exception ignored) { }
        } finally {
            try { ex.close(); } catch (Exception ignored) { }
        }
    }

    static void deny(HttpExchange ex, String path) throws Exception {
        if (path.startsWith("/api/")) {
            byte[] b = "{\"error\":\"unauthorized\",\"login\":\"/api/login\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(401, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        } else {
            byte[] b = AuthGate.loginPage().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }
    }

    static void apiPic(HttpExchange ex) throws Exception {
        Map<String, String> p = params(ex);
        String url = p.getOrDefault("url", "");
        if (url.isEmpty() || !url.startsWith("http")) { text(ex, 400, "bad url"); return; }
        byte[] bytes = PicProxy.fetch(url);
        if (bytes.length == 0) { text(ex, 502, "fetch failed"); return; }
        ex.getResponseHeaders().set("Content-Type", PicProxy.mime(bytes, url));
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=604800");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    static String streamStats() {
        try {
            String text = HttpUtil.get("http://127.0.0.1:9790/jarstats", null, 3000);
            if (text != null && !text.isEmpty()) return text;
        } catch (Exception ignored) { }
        return "{\"bps\":0,\"total\":0}";
    }

    public static void json(HttpExchange ex, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static void text(HttpExchange ex, int code, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    public static Map<String, String> params(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        parseInto(ex.getRequestURI().getRawQuery(), map);
        try {
            String method = ex.getRequestMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                if (ct == null) ct = "";
                if (ct.contains("application/x-www-form-urlencoded")) {
                    parseInto(new String(readBody(ex), StandardCharsets.UTF_8), map);
                } else if (ct.contains("application/json")) {
                    String body = new String(readBody(ex), StandardCharsets.UTF_8);
                    com.google.gson.JsonObject o = JsonUtil.parseObj(body);
                    if (o != null)
                        for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet())
                            try { map.put(e.getKey(), e.getValue().getAsString()); } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
        return map;
    }

    private static void parseInto(String text, Map<String, String> map) {
        if (text == null || text.isEmpty()) return;
        for (String pair : text.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            try {
                String k = eq > 0 ? URLDecoder.decode(pair.substring(0, eq), "UTF-8") : pair;
                String v = eq > 0 ? URLDecoder.decode(pair.substring(eq + 1), "UTF-8") : "";
                if (!k.isEmpty()) map.put(k, v);
            } catch (Exception ignored) { }
        }
    }

    public static byte[] readBody(HttpExchange ex) throws Exception {
        try (InputStream in = ex.getRequestBody(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
