package android.webkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** CookieManager 桩：进程内存级 Cookie 存储。 */
public class CookieManager {

    private static final CookieManager INSTANCE = new CookieManager();
    private static final Map<String, String> COOKIES = new ConcurrentHashMap<>();

    public static CookieManager getInstance() { return INSTANCE; }

    public void setCookie(String url, String value) {
        if (url == null || value == null) return;
        try {
            String host = new java.net.URI(url).getHost();
            if (host != null) COOKIES.put(host, value);
        } catch (Exception ignored) { }
    }

    public String getCookie(String url) {
        if (url == null) return null;
        try {
            String host = new java.net.URI(url).getHost();
            return host == null ? null : COOKIES.get(host);
        } catch (Exception e) { return null; }
    }

    public boolean setAcceptCookie(boolean accept) { return accept; }

    public boolean getAcceptCookie() { return true; }

    public void removeAllCookies(Object callback) { COOKIES.clear(); }

    public void removeSessionCookies(Object callback) { }

    public void flush() { }
}
