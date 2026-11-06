package cn.local.oneshotalarm;

import android.content.Context;
import android.content.Intent;

/** User-authorized foreground protection, independent of playback resources. */
final class AlarmReliability {
    static boolean enabled(Context context) {
        return AlarmRepository.preferences(context).getBoolean("reliable-mode", true);
    }

    static boolean needed(Context context) {
        return AlarmProtectionPolicy.needed(enabled(context), AlarmRepository.all(context));
    }

    static void setEnabled(Context context, boolean value) {
        AlarmRepository.preferences(context).edit().putBoolean("reliable-mode", value).commit();
        AlarmTrace.record(context, "可靠模式=" + value);
        sync(context);
    }

    static void sync(Context context) {
        Intent service = new Intent(context, AlarmGuardService.class);
        if (!needed(context) || !AlarmScheduler.allowed(context)) {
            context.stopService(service);
        } else if (!AlarmGuardService.running) {
            try { context.startForegroundService(service); }
            catch (RuntimeException error) {
                AlarmTrace.record(context, "可靠模式启动失败 " + error.getClass().getSimpleName());
            }
        }
    }
}
