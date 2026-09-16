package android.util;

/** android.util.Base64 桩：覆盖 TVBox jar 常用 flag 组合。 */
public class Base64 {

    public static final int DEFAULT = 0;
    public static final int NO_PADDING = 1;
    public static final int NO_WRAP = 2;
    public static final int CRLF = 4;
    public static final int URL_SAFE = 8;
    public static final int NO_CLOSE = 16;

    public static byte[] decode(String str, int flags) {
        return decode(str.getBytes(java.nio.charset.StandardCharsets.UTF_8), flags);
    }

    public static byte[] decode(byte[] input, int flags) {
        String s = new String(input, java.nio.charset.StandardCharsets.ISO_8859_1)
                .replaceAll("\\s", "");
        boolean urlSafe = (flags & URL_SAFE) != 0;
        java.util.Base64.Decoder decoder = urlSafe
                ? java.util.Base64.getUrlDecoder()
                : java.util.Base64.getDecoder();
        try {
            return decoder.decode(s);
        } catch (IllegalArgumentException e) {
            return decoder.decode(pad(s));
        }
    }

    static String pad(String s) {
        int rem = s.length() % 4;
        if (rem == 0) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = rem; i < 4; i++) sb.append('=');
        return sb.toString();
    }

    public static String encodeToString(byte[] input, int flags) {
        return new String(encode(input, flags), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    public static byte[] encode(byte[] input, int flags) {
        boolean urlSafe = (flags & URL_SAFE) != 0;
        java.util.Base64.Encoder encoder = urlSafe
                ? java.util.Base64.getUrlEncoder()
                : java.util.Base64.getEncoder();
        if ((flags & NO_WRAP) == 0) encoder = encoder.withoutPadding(); // JVM 不自动换行，直接输出
        byte[] out = (encoder.encode(input));
        String s = new String(out, java.nio.charset.StandardCharsets.ISO_8859_1);
        if ((flags & NO_PADDING) != 0) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '=') end--;
            s = s.substring(0, end);
        }
        return s.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
