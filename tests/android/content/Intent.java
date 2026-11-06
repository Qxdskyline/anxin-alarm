package android.content;

import android.net.Uri;

import java.util.*;

public class Intent {
    public Class<?> target;
    public Uri data;
    private String action;
    Map<String, Object> extras = new HashMap<>();

    public Intent(Context c, Class<?> t) {
        target = t;
    }

    public Intent setData(Uri d) {
        data = d;
        return this;
    }

    public Intent setAction(String value) {action=value;return this;}
    public String getAction() {return action;}

    public Intent putExtra(String k, int v) {
        extras.put(k, v);
        return this;
    }

    public Intent putExtra(String k, boolean v) {
        extras.put(k, v);
        return this;
    }

    public Intent putExtra(String k, String v) {
        extras.put(k, v);
        return this;
    }

    public int getIntExtra(String k, int d) {
        return (Integer) extras.getOrDefault(k, d);
    }

    public boolean getBooleanExtra(String k, boolean d) {
        return (Boolean) extras.getOrDefault(k, d);
    }

    public String getStringExtra(String k) {
        return (String) extras.get(k);
    }
}
