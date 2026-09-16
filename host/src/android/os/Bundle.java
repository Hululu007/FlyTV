package android.os;

import java.util.ArrayList;
import java.util.List;

/** android.os.Bundle 桩：内存键值。 */
public class Bundle {

    final List<String> keys = new ArrayList<>();
    final List<Object> values = new ArrayList<>();

    public void putString(String key, String value) { keys.add(key); values.add(value); }
    public void putInt(String key, int value) { keys.add(key); values.add(value); }
    public void putLong(String key, long value) { keys.add(key); values.add(value); }
    public void putBoolean(String key, boolean value) { keys.add(key); values.add(value); }

    public String getString(String key) { return getString(key, null); }
    public String getString(String key, String def) { Integer i = indexOf(key); return i == null ? def : (String) values.get(i); }
    public int getInt(String key) { Integer i = indexOf(key); return i == null ? 0 : (Integer) values.get(i); }
    public long getLong(String key) { Integer i = indexOf(key); return i == null ? 0L : (Long) values.get(i); }
    public boolean getBoolean(String key) { Integer i = indexOf(key); return i != null && (Boolean) values.get(i); }
    public boolean containsKey(String key) { return indexOf(key) != null; }

    Integer indexOf(String key) {
        for (int i = 0; i < keys.size(); i++) if (keys.get(i).equals(key)) return i;
        return null;
    }
}
