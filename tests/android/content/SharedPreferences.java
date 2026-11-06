package android.content;

public interface SharedPreferences {
    default boolean contains(String k) {
        return getString(k, null) != null;
    }

    long getLong(String k, long d);

    boolean getBoolean(String k, boolean d);

    String getString(String k, String d);

    Editor edit();

    interface Editor {
        Editor putLong(String k, long v);

        Editor putBoolean(String k, boolean v);

        Editor putString(String k, String v);

        boolean commit();
    }
}
