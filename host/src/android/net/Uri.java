package android.net;

/** Uri.Builder 桩：基于 StringBuilder 的子集。 */
public class Uri {

    private java.net.URI uri;

    private Uri(java.net.URI uri) { this.uri = uri; }

    public static Uri parse(String text) {
        try { return new Uri(java.net.URI.create(text.trim())); }
        catch (Exception e) { return new Uri(java.net.URI.create("about:blank")); }
    }

    public String getScheme() { return uri.getScheme(); }
    public String getHost() { return uri.getHost(); }
    public String getAuthority() { return uri.getAuthority(); }
    public String getPath() { return uri.getPath(); }
    public String getQuery() { return uri.getQuery(); }
    public int getPort() { return uri.getPort(); }
    public boolean isOpaque() { return uri.isOpaque(); }
    public boolean isAbsolute() { return uri.isAbsolute(); }

    public String getQueryParameter(String key) {
        String q = uri.getRawQuery();
        if (q == null || q.isEmpty()) return null;
        for (String pair : q.split("&")) {
            int eq = pair.indexOf('=');
            try {
                if (eq < 0) { if (java.net.URLDecoder.decode(pair, "UTF-8").equals(key)) return ""; }
                else if (java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8").equals(key))
                    return java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            } catch (Exception ignored) { }
        }
        return null;
    }

    public Builder buildUpon() { return new Builder(toString()); }

    @Override
    public String toString() { return uri.toString(); }

    public static class Builder {

        private final StringBuilder sb = new StringBuilder();
        private boolean hasQuery;

        Builder(String base) { sb.append(base == null ? "" : base); }

        public Builder scheme(String scheme) {
            String rest = sb.toString();
            int idx = rest.indexOf("://");
            sb.setLength(0);
            sb.append(scheme).append(rest.substring(idx >= 0 ? idx : 0));
            return this;
        }

        public Builder authority(String authority) {
            String rest = sb.toString();
            int start = rest.indexOf("://");
            int pathStart = rest.indexOf('/', start >= 0 ? start + 3 : 0);
            sb.setLength(0);
            if (start >= 0) sb.append(rest, 0, start + 3);
            sb.append(authority);
            if (pathStart >= 0) sb.append(rest.substring(pathStart));
            return this;
        }

        public Builder appendPath(String segment) {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') sb.append('/');
            sb.append(segment);
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            sb.append(hasQuery ? '&' : '?').append(encode(key)).append('=').append(encode(value));
            hasQuery = true;
            return this;
        }

        public Uri build() { return Uri.parse(sb.toString()); }

        static String encode(String s) {
            try { return java.net.URLEncoder.encode(s == null ? "" : s, "UTF-8").replace("+", "%20"); }
            catch (Exception e) { return s; }
        }
    }
}
