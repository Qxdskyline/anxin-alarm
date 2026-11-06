package cn.local.oneshotalarm;

import android.app.NotificationManager;
import android.content.Context;

import org.json.*;

import java.time.*;
import java.util.UUID;

/** 闹钟操作入口：按顺序协调状态变化、持久化、系统调度和通知。 */
final class AlarmController {
    static synchronized void initialize(Context context) {
        boolean migrating = !AlarmRepository.preferences(context).contains("v2");
        AlarmRepository.all(context);
        AlarmScheduler.cancelLegacy(context);
        if (migrating) {
            restore(context, false);
        } else {
            refresh(context, System.currentTimeMillis(), true);
        }
    }

    /** Repair missed calendar occurrences without playing stale recordings or shifting live callbacks. */
    static synchronized void refresh(Context context, long now, boolean rearm) {
        for (Alarm alarm : AlarmRepository.all(context)) {
            boolean changed = false;
            boolean live = PlaybackSessions.contains(alarm.id, alarm.execution.session());
            if (!alarm.test && alarm.plan.enabled && !live
                    && (alarm.schedule.nextAt == 0 || alarm.schedule.nextAt < now - 60000)) {
                long missed = alarm.schedule.nextAt;
                AlarmScheduler.cancel(context, alarm.id, false);
                plan(context, alarm, now);
                changed = true;
                if (missed > 0) {
                    if (alarm.schedule.nextAt > 0) alarm.status = "上次未确认响铃，已恢复后续计划";
                    AlarmTrace.record(context, "恢复过期计划 #" + alarm.id + " 原=" + AlarmTrace.time(missed)
                            + " 新=" + AlarmTrace.time(alarm.schedule.nextAt));
                }
            }
            if (!live && alarm.execution.isActive() && !alarm.execution.isPending()
                    && alarm.execution.retryAt() > 0 && alarm.execution.retryAt() < now - 60000) {
                AlarmScheduler.cancel(context, alarm.id, true);
                alarm.execution.finish();
                changed = true;
            }
            if (changed) AlarmRepository.put(context, alarm);
            if ((rearm || changed) && AlarmScheduler.allowed(context)) {
                try {
                    if (alarm.plan.enabled && alarm.schedule.nextAt > now)
                        AlarmScheduler.register(context, alarm, false);
                    if (alarm.execution.isActive() && alarm.execution.retryAt() > now)
                        AlarmScheduler.register(context, alarm, true);
                } catch (RuntimeException error) {
                    alarm.status = "后续计划登记失败，请检查响铃记录";
                    AlarmRepository.put(context, alarm);
                    AlarmTrace.record(context, "恢复登记失败 #" + alarm.id + " " + error.getClass().getSimpleName());
                }
            }
        }
    }

    static synchronized void importHolidays(Context context, JSONObject file) throws JSONException {
        AlarmRepository.saveHolidays(context, file);
        restore(context, false);
    }

    private static void plan(Context context, Alarm alarm, long after) {
        AlarmPlan plan = alarm.plan;
        alarm.schedule.token = UUID.randomUUID().toString();
        alarm.schedule.nextAt =
                plan.enabled
                        ? plan.nextOccurrence(
                                after, ZoneId.systemDefault(), AlarmRepository.holidays(context))
                        : 0;
        if (plan.enabled && alarm.schedule.nextAt == 0) {
            if (plan.mode.equals("WORK") || plan.mode.equals("REST")) {
                alarm.status = "调休日历待更新，暂未安排下一次";
            } else {
                plan.enabled = false;
                alarm.status = "指定日期已结束";
            }
        }
    }

    static synchronized void save(Context context, Alarm alarm) {
        if (alarm.plan.enabled && !AlarmScheduler.allowed(context)) {
            throw new SecurityException("请先允许设置精确闹钟");
        }
        alarm.plan.validate();
        if (!AudioFiles.available(context, alarm.plan)) {
            throw new IllegalArgumentException("请先选择录音");
        }
        cancelSchedules(context, alarm.id);
        alarm.execution.finish();
        alarm.status = "";
        plan(context, alarm, System.currentTimeMillis());
        AlarmRepository.put(context, alarm);
        try {
            AlarmScheduler.register(context, alarm, false);
        } catch (RuntimeException execution) {
            alarm.plan.enabled = false;
            alarm.schedule.nextAt = 0;
            alarm.status = "系统未接受闹钟，请重新开启";
            AlarmRepository.put(context, alarm);
            throw execution;
        }
        cancelNotification(context, alarm.id);
        AlarmReliability.sync(context);
    }

    static synchronized void toggle(Context context, int id, boolean enabled) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null) return;
        AlarmPlan plan = alarm.plan;
        if (enabled
                && plan.mode.equals("ONCE")
                && plan.nextOccurrence(
                                System.currentTimeMillis(),
                                ZoneId.systemDefault(),
                                AlarmRepository.holidays(context))
                        == 0) {
            plan.date =
                    LocalDate.now()
                            .plusDays(
                                    LocalTime.of(plan.hour, plan.minute).isAfter(LocalTime.now())
                                            ? 0
                                            : 1)
                            .toString();
        }
        plan.enabled = enabled;
        save(context, alarm);
    }

    static synchronized void delete(Context context, int id) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null) return;
        cancelSchedules(context, id);
        AlarmRepository.delete(context, id);
        cancelNotification(context, id);
        for (AlarmPlan.Track track : alarm.plan.tracks()) AudioFiles.removeIfUnused(context, track.file);
        AlarmReliability.sync(context);
    }

    static synchronized Alarm consume(
            Context context, int id, boolean retry, String token, long now) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null) return null;
        AlarmExecution execution = alarm.execution;
        long at = retry ? execution.retryAt() : alarm.schedule.nextAt;
        String expectedToken = retry ? execution.retryToken() : alarm.schedule.token;
        if (at <= 0
                || at > now + 1000
                || !token.equals(expectedToken)
                || (retry ? !execution.isActive() : !alarm.plan.enabled)) return null;
        if (retry) {
            execution.enqueueRetry();
        } else {
            execution.begin();
            AlarmScheduler.cancel(context, id, true);
            if (alarm.test) {
                alarm.plan.enabled = false;
                alarm.schedule.nextAt = 0;
                alarm.schedule.token = "";
            } else {
                plan(context, alarm, now + 1000);
            }
        }
        alarm.status = "准备播放 · 第 " + (execution.round() + 1) + " 次";
        AlarmRepository.put(context, alarm);
        try {
            AlarmScheduler.register(context, alarm, false);
        } catch (RuntimeException ex) {
            alarm.status = "下次日期安排失败，请检查权限";
            AlarmRepository.put(context, alarm);
        }
        return alarm;
    }

    static synchronized Alarm started(Context context, int id, String session, long now) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null || !alarm.execution.matches(session) || !alarm.execution.isPending())
            return null;
        AlarmExecution execution = alarm.execution;
        execution.started(now, alarm.plan.intervalSeconds, alarm.plan.repeats);
        alarm.status =
                "正在播放 · 第 " + (execution.round() + 1) + " / " + (alarm.plan.repeats + 1) + " 次";
        AlarmRepository.put(context, alarm);
        try {
            AlarmScheduler.register(context, alarm, true);
        } catch (RuntimeException ex) {
            execution.clearRetry();
            alarm.status = "正在播放，下次重播未能安排";
            AlarmRepository.put(context, alarm);
        }
        return alarm;
    }

    static synchronized void completed(Context context, int id, String session, int round) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null || !alarm.execution.matches(session) || alarm.execution.round() != round)
            return;
        alarm.execution.completed(System.currentTimeMillis());
        alarm.status =
                alarm.execution.isActive()
                        ? "本次播放结束，等待第 " + (alarm.execution.round() + 2) + " 次"
                        : "本轮播放完成";
        AlarmRepository.put(context, alarm);
        if (alarm.test && !alarm.execution.isActive()) delete(context, id);
    }

    static synchronized void close(Context context, int id, String session, String reason) {
        Alarm alarm = AlarmRepository.get(context, id);
        if (alarm == null || !alarm.execution.session().equals(session)) return;
        AlarmScheduler.cancel(context, id, true);
        alarm.execution.finish();
        alarm.status = reason;
        AlarmRepository.put(context, alarm);
        cancelNotification(context, id);
        if (alarm.test) delete(context, id);
    }

    static synchronized void test(Context context, Alarm source) {
        Alarm alarm = source.copy();
        alarm.id = AlarmRepository.allocate(context);
        alarm.plan.name = "试响 · " + source.title();
        alarm.plan.mode = "ONCE";
        alarm.test = true;
        alarm.plan.enabled = true;
        alarm.execution.finish();
        alarm.schedule.nextAt = System.currentTimeMillis() + 30000;
        alarm.schedule.token = UUID.randomUUID().toString();
        alarm.plan.repeats = 0;
        AlarmRepository.put(context, alarm);
        AlarmScheduler.register(context, alarm, false);
        AlarmReliability.sync(context);
    }

    static synchronized void restore(Context context, boolean resetSessions) {
        long now = System.currentTimeMillis();
        for (Alarm alarm : AlarmRepository.all(context)) {
            cancelSchedules(context, alarm.id);
            AlarmExecution execution = alarm.execution;
            boolean orphaned =
                    !PlaybackSessions.contains(alarm.id, execution.session())
                            && (execution.isPending()
                                    || (execution.isActive() && execution.retryAt() <= now));
            if (resetSessions || orphaned) {
                execution.finish();
                alarm.status = "本轮已结束，过期声音不补播";
            }
            if (alarm.test && !execution.isActive() && alarm.schedule.nextAt <= now) {
                delete(context, alarm.id);
                continue;
            }
            if (!alarm.test) plan(context, alarm, now);
            AlarmRepository.put(context, alarm);
            if (AlarmScheduler.allowed(context)) {
                try {
                    if (alarm.plan.enabled) AlarmScheduler.register(context, alarm, false);
                    if (execution.isActive() && execution.retryAt() > now)
                        AlarmScheduler.register(context, alarm, true);
                } catch (RuntimeException ex) {
                    alarm.status = "恢复失败，请检查精确闹钟权限";
                    AlarmRepository.put(context, alarm);
                }
            }
        }
        AlarmReliability.sync(context);
    }

    private static void cancelSchedules(Context context, int id) {
        AlarmScheduler.cancel(context, id, false);
        AlarmScheduler.cancel(context, id, true);
    }

    private static void cancelNotification(Context context, int id) {
        context.getSystemService(NotificationManager.class).cancel(1000 + id);
    }
}
