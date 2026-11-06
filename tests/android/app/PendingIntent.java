package android.app;

import android.content.*;

public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT = 1, FLAG_IMMUTABLE = 2, FLAG_NO_CREATE = 4;
    public boolean foregroundService;
    public Intent intent;
    public String key;

    public PendingIntent(Intent i, int n) {
        intent = i;
        key = n + ":" + i.target.getName() + ":" + i.data;
    }

    public static PendingIntent getBroadcast(Context c, int n, Intent i, int f) {
        return new PendingIntent(i, n);
    }

    public static PendingIntent getActivity(Context c, int n, Intent i, int f) {
        return new PendingIntent(i, n);
    }

    public static PendingIntent getForegroundService(Context c, int n, Intent i, int f) {
        PendingIntent pending=new PendingIntent(i,n);pending.foregroundService=true;return pending;
    }
}
