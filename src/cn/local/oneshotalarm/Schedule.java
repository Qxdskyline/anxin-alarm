package cn.local.oneshotalarm;

import java.time.*;
import java.util.*;

/**
 * Calendar logic has no Android dependency; a repeated alarm still plays the clip once by default.
 */
final class Schedule {
    interface WorkCalendar {
        boolean known(int year);

        boolean working(LocalDate date);
    }

    static long next(
            String mode,
            int hour,
            int minute,
            int weekdays,
            String date,
            Set<String> dates,
            long after,
            ZoneId zone,
            WorkCalendar calendar) {
        LocalDate first = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        if ("ONCE".equals(mode)) return instant(date, hour, minute, after, zone);
        if ("DATES".equals(mode)) {
            long best = 0;
            for (String d : dates) {
                long at = instant(d, hour, minute, after, zone);
                if (at > 0 && (best == 0 || at < best)) best = at;
            }
            return best;
        }
        for (int i = 0; i < 3660; i++) {
            LocalDate d = first.plusDays(i);
            if (("WORK".equals(mode) || "REST".equals(mode)) && !calendar.known(d.getYear()))
                return 0;
            boolean match =
                    "DAILY".equals(mode)
                            || "WEEKLY".equals(mode)
                                    && (weekdays & (1 << (d.getDayOfWeek().getValue() - 1))) != 0
                            || "WORK".equals(mode) && calendar.working(d)
                            || "REST".equals(mode) && !calendar.working(d);
            if (match) {
                long at = d.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
                if (at > after) return at;
            }
        }
        return 0;
    }

    private static long instant(String date, int hour, int minute, long after, ZoneId zone) {
        if (date.isEmpty()) return 0;
        long at =
                LocalDate.parse(date).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli();
        return at > after ? at : 0;
    }

    static long repeatAt(long roundStart, int intervalSeconds, int round, int repeats) {
        return round < repeats ? roundStart + intervalSeconds * 1000L : 0;
    }

    static void validate(
            int hour,
            int minute,
            boolean loop,
            int seconds,
            long duration,
            int interval,
            int repeats) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            throw new IllegalArgumentException("时间无效");
        if (loop && (seconds < 1 || seconds > 10800))
            throw new IllegalArgumentException("循环时长需在 1 秒至 180 分钟之间");
        if (repeats < 0 || repeats > 100) throw new IllegalArgumentException("重播次数需为 0 至 100");
        if (repeats > 0 && (interval < 1 || interval > 86400))
            throw new IllegalArgumentException("重播间隔需在 1 秒至 24 小时之间");
        long length = loop ? seconds * 1000L : duration;
        if (repeats > 0 && interval * 1000L <= length)
            throw new IllegalArgumentException("开始间隔需大于单次播放时长，避免重叠");
    }
}
