package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Holds no wake lock and runs no polling; only reacts to alarm data and task lifecycle events. */
public final class AlarmGuardService extends Service {
    static volatile boolean running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable changed = this::update;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
        if ("v2".equals(key) || "reliable-mode".equals(key)) {
            handler.removeCallbacks(changed);
            handler.post(changed);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        AlarmRepository.preferences(this).registerOnSharedPreferenceChangeListener(listener);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AlarmScheduler.allowed(this)) {
            AlarmTrace.record(this, "可靠模式未启动：缺少精确闹钟权限");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            promote();
        } catch (RuntimeException error) {
            AlarmTrace.record(this, "可靠模式前台保护被系统拒绝 " + error.getClass().getSimpleName());
            running = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        if (!AlarmReliability.needed(this)) { stopSelf(); return START_NOT_STICKY; }
        AlarmTrace.record(this, "可靠模式服务已启动");
        AlarmController.refresh(this, System.currentTimeMillis(), true);
        update();
        return START_STICKY;
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("alarm-guard", "可靠响铃", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.enableVibration(false); manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, 600, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long now=System.currentTimeMillis(), next=0;
        for (Alarm alarm : AlarmRepository.all(this)) {
            if (alarm.plan.enabled && alarm.schedule.nextAt > now && (next==0 || alarm.schedule.nextAt<next)) next=alarm.schedule.nextAt;
            if (alarm.execution.isActive() && alarm.execution.retryAt()>now && (next==0 || alarm.execution.retryAt()<next)) next=alarm.execution.retryAt();
        }
        return new Notification.Builder(this, "alarm-guard").setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("安心闹钟 · 可靠模式")
                .setContentText(next>0 ? "下次响铃 " + AlarmTrace.time(next) : "正在保护已启用的闹钟")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE).build();
    }

    private void promote() {
        Notification notice=notification();
        if (Build.VERSION.SDK_INT>=34) startForeground(600, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        else startForeground(600, notice);
    }

    private void update() {
        if (!AlarmReliability.needed(this)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return; }
        getSystemService(NotificationManager.class).notify(600, notification());
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        AlarmTrace.record(this, "最近任务已清理，可靠模式重新登记计划");
        AlarmController.refresh(this, System.currentTimeMillis(), true);
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        running=false;handler.removeCallbacksAndMessages(null);
        AlarmRepository.preferences(this).unregisterOnSharedPreferenceChangeListener(listener);
        AlarmTrace.record(this, "可靠模式服务已退出");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
