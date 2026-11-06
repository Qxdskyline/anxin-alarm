package cn.local.oneshotalarm;

import org.json.*;

import java.time.*;
import java.util.*;

final class HolidayCalendar implements Schedule.WorkCalendar {
    final TreeMap<Integer, Map<String, Boolean>> years = new TreeMap<>();

    HolidayCalendar(JSONObject imported) {
        Map<String, Boolean> y = new HashMap<>();
        off(y, "2026-01-01", "2026-01-03");
        off(y, "2026-02-15", "2026-02-23");
        off(y, "2026-04-04", "2026-04-06");
        off(y, "2026-05-01", "2026-05-05");
        off(y, "2026-06-19", "2026-06-21");
        off(y, "2026-09-25", "2026-09-27");
        off(y, "2026-10-01", "2026-10-07");
        for (String d :
                Arrays.asList(
                        "2026-01-04",
                        "2026-02-14",
                        "2026-02-28",
                        "2026-05-09",
                        "2026-09-20",
                        "2026-10-10")) y.put(d, true);
        years.put(2026, y);
        if (imported != null) {
            Iterator<String> keys = imported.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject object = imported.optJSONObject(key);
                if (object == null) continue;
                Map<String, Boolean> map = new HashMap<>();
                Iterator<String> days = object.keys();
                while (days.hasNext()) {
                    String d = days.next();
                    map.put(d, object.optBoolean(d));
                }
                years.put(Integer.parseInt(key), map);
            }
        }
    }

    private static void off(Map<String, Boolean> map, String from, String to) {
        LocalDate end = LocalDate.parse(to);
        for (LocalDate d = LocalDate.parse(from); !d.isAfter(end); d = d.plusDays(1))
            map.put(d.toString(), false);
    }

    public boolean known(int year) {
        return years.containsKey(year);
    }

    public boolean working(LocalDate date) {
        Map<String, Boolean> map = years.get(date.getYear());
        if (map == null) throw new IllegalStateException("尚无该年度的调休日历");
        Boolean override = map.get(date.toString());
        return override != null ? override : date.getDayOfWeek().getValue() <= 5;
    }

    static JSONObject parseImport(JSONObject j) throws JSONException {
        int year = j.getInt("year");
        if (year < 2020 || year > 2100) throw new JSONException("年份无效");
        JSONArray days = j.getJSONArray("days");
        if (days.length() < 1 || days.length() > 366) throw new JSONException("日期列表为空或过长");
        JSONObject map = new JSONObject();
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            String value = day.getString("date");
            LocalDate d = LocalDate.parse(value);
            if (d.getYear() != year || !d.toString().equals(value) || map.has(value))
                throw new JSONException("日期无效或重复：" + value);
            if (!(day.get("isOffDay") instanceof Boolean))
                throw new JSONException("isOffDay 必须为布尔值");
            map.put(value, !day.getBoolean("isOffDay"));
        }
        return new JSONObject().put("year", year).put("map", map);
    }
}
