package cn.local.oneshotalarm;

import java.time.*;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public final class PlaylistTraceTests {
    public static void main(String[] args) throws Exception {
        Alarm legacy = AlarmJson.read(new JSONObject()
                .put("audio", "original").put("audioName", "old").put("durationMs", 3000));
        check(legacy.plan.tracks().get(0).file.equals("original"), "legacy single audio");
        legacy.plan.editTracks();
        legacy.plan.playlist.add(new AlarmPlan.Track("added", "new", 2000));
        Collections.swap(legacy.plan.playlist, 0, 1);
        legacy.plan.shuffleAudio = true;
        AlarmPlan restored = AlarmJson.read(AlarmJson.write(legacy.copy())).plan;
        check(restored.tracks().size() == 2 && restored.tracks().get(0).file.equals("added")
                && restored.shuffleAudio && restored.durationMs == 5000, "saved order, shuffle and total duration");
        restored.playlist.clear();
        restored.updateDuration();
        check(restored.tracks().isEmpty(), "removed legacy audio stays removed");

        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long now = LocalDateTime.of(2027, 1, 3, 12, 0).atZone(zone).toInstant().toEpochMilli();
        List<String> lines = AlarmTrace.recent("12-26 23:30:00 too old\n"
                + "    12-27 12:00:00 exact boundary\ncontinued\n"
                + (now - 1) + "\trecent", now, zone);
        check(lines.size() == 3 && lines.get(0).endsWith("exact boundary")
                && lines.get(1).endsWith("continued") && lines.get(2).endsWith("recent"),
                "seven-day boundary, old log migration and year rollover");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        System.out.println("PASS " + label);
    }
}
