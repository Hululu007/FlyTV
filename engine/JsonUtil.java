package dev.flytv.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** JSON 读写工具（gson）+ 原子文件持久化（tmp→主文件，旧文件留 .bak，与 C# DurableJsonFile 语义一致）。 */
public final class JsonUtil {
    public static final Gson gson = new GsonBuilder().serializeNulls().create();
    public static final Gson pretty = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public static JsonObject parseObj(String text) {
        try {
            JsonElement e = JsonParser.parseString(text);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static JsonArray parseArr(String text) {
        try {
            JsonElement e = JsonParser.parseString(text);
            return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String str(JsonObject o, String key, String def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && !e.isJsonNull() ? e.getAsString() : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    public static long lng(JsonObject o, String key, long def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && !e.isJsonNull() ? e.getAsLong() : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    public static int integer(JsonObject o, String key, int def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && !e.isJsonNull() ? e.getAsInt() : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        try {
            JsonElement e = o == null ? null : o.get(key);
            return e != null && !e.isJsonNull() ? e.getAsBoolean() : def;
        } catch (Exception ignored) {
            return def;
        }
    }

    /** 读文件（主文件损坏时回退 .bak）。 */
    public static String readFile(File file) {
        try {
            if (file.exists()) return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        File bak = new File(file.getAbsolutePath() + ".bak");
        try {
            if (bak.exists()) return new String(Files.readAllBytes(bak.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 原子写：先备份旧文件为 .bak，再写 tmp 后替换。 */
    public static void writeFile(File file, String content) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            File bak = new File(file.getAbsolutePath() + ".bak");
            if (file.exists()) {
                try { Files.copy(file.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) { }
            }
            File tmp = new File(file.getAbsolutePath() + ".tmp");
            Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Logger.e("JsonUtil", "写文件失败 " + file + ": " + e);
        }
    }
}
