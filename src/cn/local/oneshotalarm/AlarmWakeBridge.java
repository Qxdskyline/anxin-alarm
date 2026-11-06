package cn.local.oneshotalarm;

import android.content.Context;
import android.os.PowerManager;
import java.util.HashMap;
import java.util.Map;

/** Only bridges delivery to service startup; never held while waiting for an alarm. */
final class AlarmWakeBridge {
    private static final Map<String, PowerManager.WakeLock> pending = new HashMap<>();

    static synchronized void acquire(Context context, int id, String session) {
        pending.entrySet().removeIf(entry -> !entry.getValue().isHeld());
        String key = id + ":" + session;
        release(id, session);
        PowerManager.WakeLock lock = context.getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnxinAlarm:delivery");
        lock.setReferenceCounted(false);
        lock.acquire(30000);
        pending.put(key, lock);
    }

    static synchronized void release(int id, String session) {
        PowerManager.WakeLock lock = pending.remove(id + ":" + session);
        if (lock != null && lock.isHeld()) lock.release();
    }
}
