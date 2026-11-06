package cn.local.oneshotalarm;

import org.json.*;

/** v2 磁盘格式适配器：继续读写原字段，升级无需丢弃原闹钟。 */
final class AlarmJson {
    static Alarm read(JSONObject json) {
        AlarmExecution execution =
                AlarmExecution.restore(
                        json.optString("phase"),
                        json.optBoolean("active"),
                        json.optBoolean("pending"),
                        json.optString("session"),
                        json.optInt("round"),
                        json.optLong("retryAt"),
                        json.optString("retryToken"));
        Alarm alarm = new Alarm(json.optInt("id"), new AlarmPlan(), new AlarmSchedule(), execution);

        alarm.plan.hour = json.optInt("hour", 7);
        alarm.plan.minute = json.optInt("minute");
        alarm.plan.weekdays = json.optInt("weekdays", 31);
        alarm.plan.loopSeconds = json.optInt("loopSeconds", 120);
        alarm.plan.intervalSeconds = json.optInt("intervalSeconds", 300);
        alarm.plan.repeats = json.optInt("repeats");
        alarm.plan.enabled = json.optBoolean("enabled", true);
        alarm.plan.loop = json.optBoolean("loop");
        alarm.test = json.optBoolean("test");
        alarm.plan.name = json.optString("name");
        alarm.plan.mode = json.optString("mode", "ONCE");
        alarm.plan.date = json.optString("date");
        alarm.plan.audio = json.optString("audio");
        alarm.plan.audioName = json.optString("audioName");
        alarm.status = json.optString("status");
        alarm.schedule.token = json.optString("token");
        alarm.plan.durationMs = json.optLong("durationMs");
        alarm.schedule.nextAt = json.optLong("nextAt");
        JSONArray dates = json.optJSONArray("dates");
        if (dates != null)
            for (int i = 0; i < dates.length(); i++) alarm.plan.dates.add(dates.optString(i));

        alarm.plan.shuffleAudio = json.optBoolean("shuffleAudio");
        JSONArray tracks = json.optJSONArray("playlist");
        if (tracks != null)
            for (int i = 0; i < tracks.length(); i++) {
                JSONObject track = tracks.optJSONObject(i);
                if (track != null && !track.optString("file").isEmpty())
                    alarm.plan.playlist.add(new AlarmPlan.Track(track.optString("file"),
                            track.optString("name"), track.optLong("duration")));
            }
        if (!alarm.plan.playlist.isEmpty()) alarm.plan.updateDuration();
        return alarm;
    }

    static JSONObject write(Alarm alarm) {

        JSONObject json = new JSONObject();
        try {
            JSONArray tracks = new JSONArray();
            for (AlarmPlan.Track track : alarm.plan.playlist)
                tracks.put(new JSONObject().put("file", track.file)
                        .put("name", track.name).put("duration", track.duration));
            json.put("playlist", tracks).put("shuffleAudio", alarm.plan.shuffleAudio);
            json.put("phase", alarm.execution.phase().name());
            json.put("id", alarm.id)
                    .put("hour", alarm.plan.hour)
                    .put("minute", alarm.plan.minute)
                    .put("weekdays", alarm.plan.weekdays)
                    .put("loopSeconds", alarm.plan.loopSeconds)
                    .put("intervalSeconds", alarm.plan.intervalSeconds)
                    .put("repeats", alarm.plan.repeats)
                    .put("round", alarm.execution.round());
            json.put("enabled", alarm.plan.enabled)
                    .put("loop", alarm.plan.loop)
                    .put("active", alarm.execution.isActive())
                    .put("pending", alarm.execution.isPending())
                    .put("test", alarm.test);
            json.put("name", alarm.plan.name)
                    .put("mode", alarm.plan.mode)
                    .put("date", alarm.plan.date)
                    .put("audio", alarm.plan.audio)
                    .put("audioName", alarm.plan.audioName)
                    .put("status", alarm.status)
                    .put("token", alarm.schedule.token)
                    .put("retryToken", alarm.execution.retryToken())
                    .put("session", alarm.execution.session());
            json.put("durationMs", alarm.plan.durationMs)
                    .put("nextAt", alarm.schedule.nextAt)
                    .put("retryAt", alarm.execution.retryAt())
                    .put("dates", new JSONArray(alarm.plan.dates));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return json;
    }
}
