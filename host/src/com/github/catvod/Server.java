package com.github.catvod;

/** 代理基址（对应安卓端 com.github.catvod.Server.getApi()），宿主在 init 前注入。 */
public class Server {

    private static volatile String api = "http://127.0.0.1:9978/proxy?";

    public static void setApi(String value) { if (value != null && !value.isEmpty()) api = value; }

    public static String getApi() { return api; }

    public static String getHost() {
        try {
            return new java.net.URI(api).getHost();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static int getPort() {
        try { return new java.net.URI(api).getPort(); } catch (Exception e) { return 9978; }
    }
}
