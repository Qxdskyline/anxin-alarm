package cn.local.oneshotalarm;

import android.content.*;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int id = i.getIntExtra("id", 0);
        boolean retry = i.getBooleanExtra("retry", false);
        AlarmTrace.record(c, "收到" + (retry ? "重播" : "闹钟") + "触发 #" + id);
        Alarm a =
                AlarmController.consume(
                        c,
                        i.getIntExtra("id", 0),
                        i.getBooleanExtra("retry", false),
                        i.getStringExtra("token") == null ? "" : i.getStringExtra("token"),
                        System.currentTimeMillis());
        if (a == null) {
            Alarm saved = AlarmRepository.get(c, id);
            AlarmTrace.record(c, "忽略触发 #" + id + (saved == null ? " 记录不存在"
                    : " 已启用=" + saved.plan.enabled + " 计划=" + AlarmTrace.time(retry
                    ? saved.execution.retryAt() : saved.schedule.nextAt)
                    + " 标识匹配=" + java.util.Objects.equals(i.getStringExtra("token"), retry
                    ? saved.execution.retryToken() : saved.schedule.token)));
            return;
        }
        try {
            AlarmWakeBridge.acquire(c, a.id, a.execution.session());
            AlarmTrace.record(c, "请求后台播放 #" + a.id + " 试响=" + a.test);
            c.startForegroundService(
                    new Intent(c, PlaybackService.class)
                            .putExtra("id", a.id)
                            .putExtra("session", a.execution.session()));
        } catch (RuntimeException e) {
            AlarmWakeBridge.release(a.id, a.execution.session());
            AlarmTrace.record(c, "启动播放服务失败 #" + a.id + " " + e.getClass().getSimpleName());
            AlarmController.close(c, a.id, a.execution.session(), "系统阻止播放，请检查后台权限");
        }
    }
}
