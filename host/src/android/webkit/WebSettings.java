package android.webkit;

/** WebSettings 桩。 */
public class WebSettings {

    public enum LayoutAlgorithm { NORMAL, SINGLE_COLUMN, NARROW_COLUMNS, TEXT_AUTOSIZING }

    public enum PluginState { ON, OFF, ON_DEMAND }

    public enum RenderPriority { NORMAL, HIGH, LOW }

    public void setJavaScriptEnabled(boolean flag) { }

    public void setDomStorageEnabled(boolean flag) { }

    public void setUseWideViewPort(boolean flag) { }

    public void setLoadWithOverviewMode(boolean flag) { }

    public void setUserAgentString(String ua) { }

    public String getUserAgentString() { return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"; }

    public void setLayoutAlgorithm(LayoutAlgorithm algorithm) { }

    public void setPluginState(PluginState state) { }

    public void setRenderPriority(RenderPriority priority) { }

    public void setSupportZoom(boolean flag) { }

    public void setBuiltInZoomControls(boolean flag) { }

    public void setCacheMode(int mode) { }
}
