package cn.local.oneshotalarm;

import java.util.Locale;

/** 一条闹钟记录，配置、日历调度和本轮执行分别维护。 */
final class Alarm {
    int id;
    boolean test;
    String status = "";
    final AlarmPlan plan;
    final AlarmSchedule schedule;
    final AlarmExecution execution;

    Alarm(int id) {
        this(id, new AlarmPlan(), new AlarmSchedule(), new AlarmExecution());
    }

    Alarm(int id, AlarmPlan plan, AlarmSchedule schedule, AlarmExecution execution) {
        this.id = id;
        this.plan = plan;
        this.schedule = schedule;
        this.execution = execution;
    }

    Alarm copy() {
        Alarm copy = new Alarm(id, plan.copy(), schedule.copy(), execution.copy());
        copy.test = test;
        copy.status = status;
        return copy;
    }

    String title() {
        return plan.name.trim().isEmpty() ? "安心闹钟" : plan.name;
    }

    String time() {
        return String.format(Locale.CHINA, "%02d:%02d", plan.hour, plan.minute);
    }
}
