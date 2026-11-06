package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Build;

/** Android 系统调度适配器；不保存或改变业务状态。 */
final class AlarmScheduler {
    private static AlarmManager manager(Context context) {
        return context.getSystemService(AlarmManager.class);
    }

    static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || manager(context).canScheduleExactAlarms();
    }

    static void cancelLegacy(Context context) {
        manager(context)
                .cancel(
                        PendingIntent.getBroadcast(
                                context,
                                1,
                                new Intent(context, AlarmReceiver.class),
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    static PendingIntent operation(Context context, int id, boolean retry, String token) {
        Intent intent =
                new Intent(context, PlaybackService.class).setAction(AlarmDelivery.ACTION)
                        .setData(
                                Uri.parse(
                                        "recordingalarm://alarm/"
                                                + id
                                                + "/"
                                                + (retry ? "retry" : "regular")))
                        .putExtra("id", id)
                        .putExtra("retry", retry)
                        .putExtra("token", token);
        return PendingIntent.getForegroundService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void register(Context context, Alarm alarm, boolean retry) {
        long at = retry ? alarm.execution.retryAt() : alarm.schedule.nextAt;
        if (at <= 0) return;
        cancelBroadcast(context, alarm.id, retry);
        PendingIntent show =
                PendingIntent.getActivity(
                        context,
                        alarm.id,
                        new Intent(context, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
        manager(context)
                .setAlarmClock(
                        new AlarmManager.AlarmClockInfo(at, show),
                        operation(
                                context,
                                alarm.id,
                                retry,
                                retry ? alarm.execution.retryToken() : alarm.schedule.token));
            AlarmTrace.record(context, "系统已接受" + (retry ? "重播" : alarm.test ? "试响" : "正式闹钟")
                    + " #" + alarm.id + " " + AlarmTrace.time(at) + " " + alarm.plan.mode + " 入口=前台服务");
        } catch (RuntimeException error) {
            AlarmTrace.record(context, "注册失败 #" + alarm.id + " " + error.getClass().getSimpleName());
            throw error;
        }
    }

    static void cancel(Context context, int id, boolean retry) {
        manager(context).cancel(operation(context, id, retry, ""));
        cancelBroadcast(context, id, retry);
    }

    private static void cancelBroadcast(Context context, int id, boolean retry) {
        Intent old = new Intent(context, AlarmReceiver.class).setData(Uri.parse(
                "recordingalarm://alarm/" + id + "/" + (retry ? "retry" : "regular")));
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, old,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) manager(context).cancel(pending);
    }
}
