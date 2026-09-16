package android.text;

/** android.text.TextUtils 桩（jar 常用方法子集）。 */
public class TextUtils {

    public static boolean isEmpty(CharSequence str) { return str == null || str.length() == 0; }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        int len = a == null ? 0 : a.length();
        if (len != (b == null ? 0 : b.length())) return false;
        if (len == 0) return true;
        if (a instanceof String && b instanceof String) return a.equals(b);
        for (int i = 0; i < len; i++) if (a.charAt(i) != b.charAt(i)) return false;
        return true;
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object token : tokens) {
            if (!first) sb.append(delimiter);
            sb.append(token);
            first = false;
        }
        return sb.toString();
    }

    public static String[] split(String text, String expression) {
        if (text == null) return new String[0];
        return text.split(expression);
    }

    public static boolean isDigitsOnly(CharSequence str) {
        if (isEmpty(str)) return false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public enum TruncateAt { START, MIDDLE, END, MARQUEE }
}
