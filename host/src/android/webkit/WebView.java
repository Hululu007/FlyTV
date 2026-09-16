package android.webkit;

/** WebView 桩：桌面端无渲染能力，仅保证类可加载（嗅探类爬虫会功能缺失）。 */
public class WebView extends android.view.View {

    public WebView(android.content.Context context) { super(context); }

    public WebSettings getSettings() { return new WebSettings(); }

    public void loadUrl(String url) { }

    public void loadData(String data, String mime, String encoding) { }

    public void setWebChromeClient(Object client) { }

    public void setWebViewClient(Object client) { }

    public void evaluateJavascript(String script, Object callback) { }

    public void stopLoading() { }

    public void destroy() { }
}
