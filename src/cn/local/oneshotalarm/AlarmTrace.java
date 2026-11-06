package cn.local.oneshotalarm;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Local event trail, retained for seven days. */
final class AlarmTrace {
    private static final int MAX_EVENTS = 80;
    private static final long RETENTION_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    static String time(long millis) {
        return millis <= 0 ? "无" : Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DISPLAY);
    }

    static synchronized void record(Context context, String event) {
        try {
            SharedPreferences p = context.getSharedPreferences("alarm-trace", 0);
            long now = System.currentTimeMillis();
            List<String> lines = recent(p.getString("events", ""), now, ZoneId.systemDefault());
            for (String line : event.split("\\R")) {
                lines.add(now + "\t" + line);
            }
            int first = Math.max(0, lines.size() - MAX_EVENTS);
            p.edit().putString("events", String.join("\n", lines.subList(first, lines.size()))).commit();
        } catch (RuntimeException ignored) { }
    }

    static synchronized String read(Context context) {
        SharedPreferences p = context.getSharedPreferences("alarm-trace", 0);
        String stored = p.getString("events", "");
        List<String> lines = recent(stored, System.currentTimeMillis(), ZoneId.systemDefault());
        String updated = String.join("\n", lines);
        if (!stored.equals(updated)) p.edit().putString("events", updated).commit();
        if (lines.isEmpty()) return "尚无响铃记录";
        List<String> display = new ArrayList<>();
        for (String line : lines) {
            int separator = line.indexOf('\t');
            display.add(time(Long.parseLong(line.substring(0, separator))) + " "
                    + line.substring(separator + 1));
        }
        return String.join("\n", display);
    }

    static List<String> recent(String stored, long now, ZoneId zone) {
        List<String> result = new ArrayList<>();
        int year = Instant.ofEpochMilli(now).atZone(zone).getYear();
        long previous = Long.MIN_VALUE;
        for (String raw : stored.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int separator = line.indexOf('\t');
            try {
                if (separator > 0) {
                    previous = Long.parseLong(line.substring(0, separator));
                    line = line.substring(separator + 1);
                } else if (line.matches("^\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*")) {
                    String date = line.substring(0, 5);
                    String clock = line.substring(6, 14);
                    LocalDateTime timestamp = LocalDateTime.parse(year + "-" + date + "T" + clock);
                    previous = timestamp.atZone(zone).toInstant().toEpochMilli();
                    if (previous > now) previous = timestamp.minusYears(1).atZone(zone).toInstant().toEpochMilli();
                    line = line.substring(15);
                }
                if (previous >= now - RETENTION_MS && previous <= now) result.add(previous + "\t" + line);
            } catch (DateTimeException | NumberFormatException ignored) {
                previous = Long.MIN_VALUE;
            }
        }
        return result;
    }
}
