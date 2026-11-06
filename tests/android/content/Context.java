package android.content;

import java.io.*;

public abstract class Context {
    public abstract SharedPreferences getSharedPreferences(String s, int m);

    public abstract <T> T getSystemService(Class<T> k);

    public abstract File getFilesDir();

    public abstract Object startForegroundService(Intent i);
}
