package cn.local.oneshotalarm;
import java.util.List;

/** Foreground protection is needed only for an enabled schedule or an active playback series. */
final class AlarmProtectionPolicy {
    static boolean needed(boolean enabled, List<Alarm> alarms) {
        if (!enabled) return false;
        for (Alarm alarm : alarms) {
            if ((alarm.plan.enabled && alarm.schedule.nextAt > 0) || alarm.execution.isActive()) return true;
        }
        return false;
    }
}
