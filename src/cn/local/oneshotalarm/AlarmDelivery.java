package cn.local.oneshotalarm;

import android.content.Context;
import android.content.Intent;

/** Shared one-time validation for a system-started service and legacy broadcast delivery. */
final class AlarmDelivery {
    static final String ACTION = "cn.local.oneshotalarm.FIRE_ALARM";

    static Alarm consume(Context context, Intent intent, long now) {
        int id = intent.getIntExtra("id", 0);
        boolean retry = intent.getBooleanExtra("retry", false);
        AlarmTrace.record(context, "系统直接唤醒播放服务 #" + id + " 重播=" + retry);
        String token = intent.getStringExtra("token");
        Alarm alarm = AlarmController.consume(context, id, retry, token == null ? "" : token, now);
        if (alarm == null) AlarmTrace.record(context, "忽略重复、失效或尚未到点的服务触发 #" + id);
        return alarm;
    }
}
