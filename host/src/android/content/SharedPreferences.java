package android.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** SharedPreferences 桩：进程内存级，仅保证 API 可调用。 */
public interface SharedPreferences {

    SharedPreferences DUMMY = new Impl();

    Map<String, ?> getAll();
    String getString(String key, String defValue);
    Set<String> getStringSet(String key, Set<String> defValues);
    int getInt(String key, int defValue);
    long getLong(String key, long defValue);
    float getFloat(String key, float defValue);
    boolean getBoolean(String key, boolean defValue);
    boolean contains(String key);
    Editor edit();

    interface Editor {
        Editor putString(String key, String value);
        Editor putStringSet(String key, Set<String> values);
        Editor putInt(String key, int value);
        Editor putLong(String key, long value);
        Editor putFloat(String key, float value);
        Editor putBoolean(String key, boolean value);
        Editor remove(String key);
        Editor clear();
        boolean commit();
        void apply();
    }

    class Impl implements SharedPreferences, Editor {
        private final Map<String, Object> data = new HashMap<>();
        public Map<String, ?> getAll() { return data; }
        public String getString(String key, String defValue) { Object v = data.get(key); return v instanceof String ? (String) v : defValue; }
        public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        public int getInt(String key, int defValue) { Object v = data.get(key); return v instanceof Integer ? (Integer) v : defValue; }
        public long getLong(String key, long defValue) { Object v = data.get(key); return v instanceof Long ? (Long) v : defValue; }
        public float getFloat(String key, float defValue) { Object v = data.get(key); return v instanceof Float ? (Float) v : defValue; }
        public boolean getBoolean(String key, boolean defValue) { Object v = data.get(key); return v instanceof Boolean ? (Boolean) v : defValue; }
        public boolean contains(String key) { return data.containsKey(key); }
        public Editor edit() { return this; }
        public Editor putString(String key, String value) { data.put(key, value); return this; }
        public Editor putStringSet(String key, Set<String> values) { data.put(key, values); return this; }
        public Editor putInt(String key, int value) { data.put(key, value); return this; }
        public Editor putLong(String key, long value) { data.put(key, value); return this; }
        public Editor putFloat(String key, float value) { data.put(key, value); return this; }
        public Editor putBoolean(String key, boolean value) { data.put(key, value); return this; }
        public Editor remove(String key) { data.remove(key); return this; }
        public Editor clear() { data.clear(); return this; }
        public boolean commit() { return true; }
        public void apply() { }
    }
}
