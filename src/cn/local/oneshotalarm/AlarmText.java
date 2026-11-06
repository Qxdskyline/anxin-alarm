package cn.local.oneshotalarm;

import java.time.*;
import java.time.format.DateTimeFormatter;

/** 页面和通知共用的显示文案，不访问存储。 */
final class AlarmText {
    static String upcoming(long at, long now, int count) {
        if (at <= 0) return count == 0 ? "每一个值得期待的时刻。" : "暂无已安排的响铃";
        if (at <= now) return "上次计划时间已过，正在恢复后续响铃";
        ZonedDateTime date = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        String day = date.toLocalDate().equals(today) ? "今天"
                : date.toLocalDate().equals(today.plusDays(1)) ? "明天"
                : date.format(DateTimeFormatter.ofPattern("M 月 d 日"));
        long minutes = (at - now + 59999) / 60000;
        String delay = minutes >= 60 ? minutes / 60 + " 小时 " + minutes % 60 + " 分钟" : minutes + " 分钟";
        return "下一次 · " + day + " " + date.format(DateTimeFormatter.ofPattern("HH:mm"))
                + "\n约 " + delay + "后，熟悉的声音会响起";
    }

    static String description(Alarm alarm) {
        switch (alarm.plan.mode) {
            case "DAILY":
                return "每天";
            case "WORK":
                return "法定工作日 · 含调休";
            case "REST":
                return "休息日 · 含调休";
            case "DATES":
                return "指定 " + alarm.plan.dates.size() + " 个日期";
            case "WEEKLY":
                StringBuilder s = new StringBuilder();
                String[] days = {"一", "二", "三", "四", "五", "六", "日"};
                for (int i = 0; i < 7; i++)
                    if ((alarm.plan.weekdays & (1 << i)) != 0)
                        s.append(s.length() == 0 ? "每周" : "、").append(days[i]);
                return s.toString();
            default:
                return alarm.plan.date;
        }
    }

    static String playDescription(Alarm alarm) {
        return alarm.plan.loop ? "循环 " + duration(alarm.plan.loopSeconds) : "录音播放一遍";
    }

    static String duration(int seconds) {
        return seconds % 60 == 0
                ? seconds / 60 + " 分钟"
                : seconds < 60 ? seconds + " 秒" : seconds / 60 + " 分 " + seconds % 60 + " 秒";
    }
}
