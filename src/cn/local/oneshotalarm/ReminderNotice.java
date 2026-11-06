package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;

import java.time.*;
import java.time.format.DateTimeFormatter;

final class ReminderNotice {
    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ring =
                new NotificationChannel("ring-v2", "闹钟响铃提醒", NotificationManager.IMPORTANCE_HIGH);
        ring.setSound(null, null);
        ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);
        NotificationChannel wait =
                new NotificationChannel("wait-v2", "等待贪睡重播", NotificationManager.IMPORTANCE_LOW);
        wait.setSound(null, null);
        nm.createNotificationChannel(wait);
    }

    static PendingIntent stop(Context c, Alarm a) {
        return PendingIntent.getBroadcast(
                c,
                0,
                new Intent(c, ControlReceiver.class)
                        .setData(
                                Uri.parse(
                                        "recordingalarm://close/"
                                                + a.id
                                                + "/"
                                                + a.execution.session()))
                        .putExtra("id", a.id)
                        .putExtra("session", a.execution.session()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification ringing(Context c, Alarm a) {
        channels(c);
        PendingIntent page =
                PendingIntent.getActivity(
                        c,
                        a.id,
                        new Intent(c, RingingActivity.class)
                                .setData(
                                        Uri.parse(
                                                "recordingalarm://ring/"
                                                        + a.id
                                                        + "/"
                                                        + a.execution.session()))
                                .putExtra("id", a.id)
                                .putExtra("session", a.execution.session()),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b =
                new Notification.Builder(c, "ring-v2")
                        .setSmallIcon(R.drawable.ic_alarm)
                        .setContentTitle(a.title())
                        .setContentText(
                                AlarmText.playDescription(a)
                                        + " · 第 "
                                        + (a.execution.round() + 1)
                                        + " / "
                                        + (a.plan.repeats + 1)
                                        + " 次")
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setContentIntent(page)
                        .setOngoing(true)
                        .setOnlyAlertOnce(false)
                        .addAction(
                                new Notification.Action.Builder(null, "× 结束本轮", stop(c, a))
                                        .build());
        if (Build.VERSION.SDK_INT < 34
                || c.getSystemService(NotificationManager.class).canUseFullScreenIntent())
            b.setFullScreenIntent(page, true);
        return b.build();
    }

    static Notification preparing(Context c) {
        channels(c);
        return new Notification.Builder(c, "wait-v2")
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("安心闹钟")
                .setContentText("准备播放录音")
                .setOngoing(true)
                .build();
    }

    static void waiting(Context c, Alarm a) {
        if (a == null
                || !a.execution.isActive()
                || a.execution.retryAt() <= System.currentTimeMillis()) return;
        channels(c);
        PendingIntent open =
                PendingIntent.getActivity(
                        c,
                        0,
                        new Intent(c, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String time =
                Instant.ofEpochMilli(a.execution.retryAt())
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Notification n =
                new Notification.Builder(c, "wait-v2")
                        .setSmallIcon(R.drawable.ic_alarm)
                        .setContentTitle(a.title() + " · 等待重播")
                        .setContentText(
                                time
                                        + " 开始第 "
                                        + (a.execution.round() + 2)
                                        + " / "
                                        + (a.plan.repeats + 1)
                                        + " 次")
                        .setContentIntent(open)
                        .setDeleteIntent(stop(c, a))
                        .setOnlyAlertOnce(true)
                        .addAction(
                                new Notification.Action.Builder(null, "× 结束本轮", stop(c, a)).build())
                        .build();
        c.getSystemService(NotificationManager.class).notify(1000 + a.id, n);
    }
}
