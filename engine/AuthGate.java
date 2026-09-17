package dev.flytv.engine;

import com.sun.net.httpserver.HttpExchange;

import java.security.MessageDigest;

/** 访问口令网关（等价 C# AuthGate）：/web 与 /api 需登录（cookie token）；
 * 局域网设备发现与同步协议（/action、/device）豁免。 */
public final class AuthGate {
    public static boolean enabled() { return !Setting.webPassword().isEmpty(); }

    public static boolean check(HttpExchange ex, String path) {
        if (!enabled()) return true;
        if (path.equals("/api/login")) return true;
        if (isPrivate(ex) && (path.startsWith("/action") || path.startsWith("/device"))) return true;
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        String token = cookieValue(cookie, "tvbox_token");
        return token != null && token.equals(expectedToken());
    }

    public static String expectedToken() {
        return sha256Hex(Setting.webPassword() + ":" + Setting.deviceUuid());
    }

    public static boolean isPrivate(HttpExchange ex) {
        try {
            java.net.InetSocketAddress addr = ex.getRemoteAddress();
            if (addr == null) return true;
            java.net.InetAddress ip = addr.getAddress();
            if (ip.isLoopbackAddress() || ip.isSiteLocalAddress() || ip.isLinkLocalAddress()) return true;
            byte[] b = ip.getAddress();
            if (b.length == 16) return (b[0] & 0xFE) == 0xFC; // fc00::/7
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static String cookieValue(String header, String name) {
        if (header == null || header.isEmpty()) return null;
        for (String part : header.split(";")) {
            String s = part.trim();
            int eq = s.indexOf('=');
            if (eq > 0 && s.substring(0, eq).equalsIgnoreCase(name)) return s.substring(eq + 1);
        }
        return null;
    }

    static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String loginPage() {
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>FlyTV · 访问验证</title>"
                + "<style>body{min-height:100vh;display:flex;align-items:center;justify-content:center;background:#161b24;"
                + "font-family:\"Segoe UI\",\"Microsoft YaHei\",sans-serif;color:#f0f2f6;margin:0}"
                + ".box{width:340px;max-width:92vw;background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12);"
                + "border-radius:18px;padding:34px 28px;box-shadow:0 20px 60px rgba(0,0,0,.5)}"
                + "h1{font-size:22px;margin:0 0 6px}p{color:rgba(255,255,255,.55);font-size:13px;margin:0 0 22px}"
                + "input{width:100%;box-sizing:border-box;background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.14);"
                + "border-radius:11px;padding:12px 14px;color:#fff;font-size:15px;outline:none}"
                + "button{width:100%;margin-top:14px;padding:12px;border:none;border-radius:11px;cursor:pointer;"
                + "background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;font-size:15px;font-weight:600}"
                + "#err{color:#f87171;font-size:13px;margin-top:12px;min-height:18px;text-align:center}</style></head>"
                + "<body><div class=\"box\"><h1>✈️ FlyTV</h1><p>本机引擎已启用访问口令，请输入后进入</p>"
                + "<input id=\"pw\" type=\"password\" placeholder=\"访问口令\" autofocus>"
                + "<button onclick=\"go()\">进入</button><div id=\"err\"></div></div>"
                + "<script>function go(){var p=document.getElementById('pw');var e=document.getElementById('err');"
                + "fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
                + "body:'password='+encodeURIComponent(p.value)}).then(function(r){return r.json()})"
                + ".then(function(j){if(j.ok){location.href='/web'}else{e.textContent=j.error||'口令错误'}})"
                + ".catch(function(){e.textContent='网络错误'})}"
                + "document.getElementById('pw').addEventListener('keydown',function(e){if(e.key==='Enter')go()});</script></body></html>";
    }
}
