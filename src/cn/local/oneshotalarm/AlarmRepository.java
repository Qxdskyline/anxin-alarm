package cn.local.oneshotalarm;

import android.content.*;

import org.json.*;

import java.io.File;
import java.time.*;
import java.util.*;

/** 仅负责私有数据的读写和旧格式迁移，不注册闹钟、不操作播放器。 */
final class AlarmRepository {
    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("alarm", 0);
    }

    private static JSONObject root(Context context) {
        try {
            String raw = preferences(context).getString("v2", null);
            if (raw != null) return new JSONObject(raw);
            JSONObject root =
                    new JSONObject()
                            .put("alarms", new JSONArray())
                            .put("sequence", 1)
                            .put("holidays", new JSONObject());
            SharedPreferences old = preferences(context);
            File file = new File(context.getFilesDir(), "recording");
            if (file.isFile()) {
                Alarm alarm = new Alarm(1);
                alarm.plan.name = "我的安心闹钟";
                alarm.plan.audio = "recording";
                alarm.plan.audioName = old.getString("name", "已导入录音");
                long at = old.getLong("at", 0);
                ZonedDateTime d =
                        Instant.ofEpochMilli(at > 0 ? at : System.currentTimeMillis())
                                .atZone(ZoneId.systemDefault());
                alarm.plan.hour = d.getHour();
                alarm.plan.minute = d.getMinute();
                alarm.plan.date = d.toLocalDate().toString();
                alarm.schedule.nextAt = at;
                alarm.plan.enabled =
                        old.getBoolean("armed", false) && at > System.currentTimeMillis();
                alarm.schedule.token = UUID.randomUUID().toString();
                alarm.status = old.getString("status", "");
                root.getJSONArray("alarms").put(AlarmJson.write(alarm));
                root.put("sequence", 2);
            }
            write(context, root);
            return root;
        } catch (JSONException e) {
            throw new IllegalStateException("闹钟数据无法读取，请保留应用数据并反馈", e);
        }
    }

    private static void write(Context context, JSONObject root) {
        if (!preferences(context).edit().putString("v2", root.toString()).commit())
            throw new IllegalStateException("保存失败，存储空间可能不足");
    }

    static synchronized List<Alarm> all(Context context) {
        JSONArray array = root(context).optJSONArray("alarms");
        List<Alarm> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) list.add(AlarmJson.read(array.optJSONObject(i)));
        return list;
    }

    static synchronized Alarm get(Context context, int id) {
        for (Alarm alarm : all(context)) if (alarm.id == id) return alarm;
        return null;
    }

    static synchronized int allocate(Context context) {
        JSONObject document = root(context);
        int id = document.optInt("sequence", 1);
        try {
            document.put("sequence", id + 1);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        write(context, document);
        return id;
    }

    static synchronized void put(Context context, Alarm alarm) {
        JSONObject document = root(context);
        JSONArray old = document.optJSONArray("alarms"), out = new JSONArray();
        boolean found = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry.optInt("id") == alarm.id) {
                out.put(AlarmJson.write(alarm));
                found = true;
            } else out.put(entry);
        }
        if (!found) out.put(AlarmJson.write(alarm));
        try {
            document.put("alarms", out);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        write(context, document);
    }

    static synchronized HolidayCalendar holidays(Context context) {
        return new HolidayCalendar(root(context).optJSONObject("holidays"));
    }

    static synchronized void delete(Context context, int id) {
        JSONObject document = root(context);
        JSONArray old = document.optJSONArray("alarms");
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject entry = old.optJSONObject(i);
            if (entry.optInt("id") != id) out.put(entry);
        }
        try {
            document.put("alarms", out);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        write(context, document);
    }

    static synchronized void saveHolidays(Context context, JSONObject file) throws JSONException {
        JSONObject parsed = HolidayCalendar.parseImport(file);
        JSONObject document = root(context);
        document.getJSONObject("holidays")
                .put(String.valueOf(parsed.getInt("year")), parsed.getJSONObject("map"));
        write(context, document);
    }
}
