package android.app;

import java.util.*;

public class AlarmManager {
    public Map<String, PendingIntent> jobs = new HashMap<>();
    public Map<String, Long> times = new HashMap<>();
    public boolean allowed = true, fail = false;

    public boolean canScheduleExactAlarms() {
        return allowed;
    }

    public void setAlarmClock(AlarmClockInfo i, PendingIntent p) {
        if (fail) throw new SecurityException("blocked");
        jobs.put(p.key, p);
        times.put(p.key, i.at);
    }

    public void cancel(PendingIntent p) {
        jobs.remove(p.key);
        times.remove(p.key);
    }

    public static class AlarmClockInfo {
        long at;

        public AlarmClockInfo(long a, PendingIntent p) {
            at = a;
        }
    }
}
