package com.github.catvod.crawler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** CatVod Spider 基类（对齐 FongMi 版签名：含 safeDns/proxy/init(Context)/getExecutor），由宿主提供。 */
public abstract class Spider {

    private final ExecutorService executor;

    public Spider() {
        int pool = Math.min(8, Runtime.getRuntime().availableProcessors());
        executor = Executors.newFixedThreadPool(pool > 0 ? pool : 2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    public final ExecutorService getExecutor() { return executor; }

    public void init(android.content.Context context) { }

    public void init(android.content.Context context, String extend) { }

    public String homeContent(boolean filter) { return ""; }

    public String homeVideoContent() { return ""; }

    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) { return ""; }

    public String detailContent(List<String> ids) { return ""; }

    public String searchContent(String key, boolean quick) { return ""; }

    public String searchContent(String key, boolean quick, String pg) { return searchContent(key, quick); }

    public String playerContent(String flag, String id, List<String> vipFlags) { return ""; }

    public String liveContent(String url) { return ""; }

    public String action(String action) { return ""; }

    public Object[] proxy(Map<String, String> params) { return null; }

    public boolean manualVideoCheck() { return false; }

    public boolean isVideoFormat(String url) { return false; }

    public static okhttp3.Dns safeDns() { return okhttp3.Dns.SYSTEM; }

    public void destroy() {
        try { executor.shutdown(); } catch (Throwable ignored) { }
    }
}
