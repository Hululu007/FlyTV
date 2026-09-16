package dev.flytv.engine;

import com.google.gson.JsonObject;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** 本机设备信息（/device，供局域网安卓端发现，协议与 C# 版一致）。 */
public final class DeviceJson {
    public static String get() {
        JsonObject o = new JsonObject();
        String uuid = Setting.deviceUuid();
        o.addProperty("uuid", uuid);
        o.addProperty("name", hostname());
        o.addProperty("ip", "http://" + lanIp() + ":" + WebServer.port());
        o.addProperty("type", 0);
        o.addProperty("serial", uuid.length() >= 8 ? uuid.substring(0, 8) : uuid);
        o.addProperty("eth", mac(false));
        o.addProperty("wlan", mac(true));
        o.addProperty("time", Stores.now() / 1000);
        return o.toString();
    }

    static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "PC"; }
    }

    /** 优选真实局域网地址：物理网卡 + 192.168/10/172 私网段打分（避免 VPN/虚拟网卡）。 */
    public static String lanIp() {
        try {
            String best = null;
            int bestScore = -1;
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                int typeScore = 5;
                String lower = ni.getName().toLowerCase();
                String display = ni.getDisplayName() == null ? "" : ni.getDisplayName().toLowerCase();
                if (lower.startsWith("wlan") || display.contains("wireless") || display.contains("wi-fi")) typeScore = 30;
                else if (lower.startsWith("eth") || display.contains("ethernet")) typeScore = 25;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!(a instanceof java.net.Inet4Address)) continue;
                    String s = a.getHostAddress();
                    if (s.startsWith("169.254.") || s.startsWith("127.")) continue;
                    int score = typeScore;
                    if (s.startsWith("192.168.")) score += 10;
                    else if (s.startsWith("10.")) score += 6;
                    else if (s.startsWith("172.")) score += 5;
                    else score += 3;
                    if (score > bestScore) { bestScore = score; best = s; }
                }
            }
            if (best != null) return best;
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }

    static String mac(boolean wlan) {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase();
                boolean isWlan = name.startsWith("wlan") || name.startsWith("wl");
                if (wlan != isWlan) continue;
                byte[] mac = ni.getHardwareAddress();
                if (mac == null || mac.length == 0) continue;
                StringBuilder sb = new StringBuilder();
                for (byte b : mac) {
                    if (sb.length() > 0) sb.append(':');
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            }
        } catch (Exception ignored) { }
        return "";
    }
}
