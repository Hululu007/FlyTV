package dev.flytv.engine;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** HTTP 客户端（OkHttp 封装，与 jar-host 同一依赖；默认不跟随无限重定向 + 可控超时）。 */
public final class HttpUtil {
    public static final String DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final OkHttpClient BASE = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build();

    private static OkHttpClient client(int timeoutMs) {
        if (timeoutMs <= 0) return BASE;
        return BASE.newBuilder()
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs + 3000L, TimeUnit.MILLISECONDS)
                .build();
    }

    public static String get(String url, Map<String, String> headers, int timeoutMs) throws Exception {
        Request.Builder b = new Request.Builder().url(url).get();
        applyHeaders(b, headers);
        try (Response resp = client(timeoutMs).newCall(b.build()).execute()) {
            ResponseBody body = resp.body();
            return body == null ? "" : body.string();
        }
    }

    public static String get(String url, int timeoutMs) throws Exception {
        return get(url, null, timeoutMs);
    }

    public static String postForm(String url, Map<String, String> form, Map<String, String> headers, int timeoutMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        RequestBody body = RequestBody.create(sb.toString(), MediaType.parse("application/x-www-form-urlencoded"));
        Request.Builder b = new Request.Builder().url(url).post(body);
        applyHeaders(b, headers);
        try (Response resp = client(timeoutMs).newCall(b.build()).execute()) {
            ResponseBody rb = resp.body();
            return rb == null ? "" : rb.string();
        }
    }

    public static String postJson(String url, String json, Map<String, String> headers, int timeoutMs) throws Exception {
        RequestBody body = RequestBody.create(json == null ? "{}" : json, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder b = new Request.Builder().url(url).post(body);
        applyHeaders(b, headers);
        try (Response resp = client(timeoutMs).newCall(b.build()).execute()) {
            ResponseBody rb = resp.body();
            return rb == null ? "" : rb.string();
        }
    }

    private static void applyHeaders(Request.Builder b, Map<String, String> headers) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", DEFAULT_UA);
        if (headers != null) h.putAll(headers);
        for (Map.Entry<String, String> e : h.entrySet()) {
            try { b.header(e.getKey(), e.getValue()); } catch (Exception ignored) { }
        }
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    public static boolean isHttp(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }
}
