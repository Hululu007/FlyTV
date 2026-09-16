package android.content.res;

import java.io.IOException;
import java.io.InputStream;

/** AssetManager 桩：TVBox jar 爬虫基本不使用 assets，仅保证类型存在。 */
public class AssetManager {

    public InputStream open(String fileName) throws IOException {
        throw new IOException("assets not supported on desktop host: " + fileName);
    }

    public String[] list(String path) throws IOException { return new String[0]; }
}
