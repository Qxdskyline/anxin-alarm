package cn.local.oneshotalarm;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 跨模块回归：旧数据、执行生命周期和存储/调度的职责分离。 */
public final class RefactorTests {
    private static int count;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        System.out.println("PASS " + label);
        count++;
    }

    private static JSONObject legacyRecord() throws Exception {
        return new JSONObject(
                "{\"id\":9,\"hour\":6,\"minute\":25,\"weekdays\":65,"
                    + "\"loopSeconds\":135,\"intervalSeconds\":400,\"repeats\":2,\"round\":1,"
                    + "\"enabled\":true,\"loop\":true,\"active\":true,\"pending\":false,\"test\":false,"
                    + "\"name\":\"早安\",\"mode\":\"DATES\",\"date\":\"2026-10-22\","
                    + "\"audio\":\"audio-local\",\"audioName\":\"家人录音.mp3\",\"status\":\"等待重播\","
                    + "\"token\":\"regular-token\",\"retryToken\":\"repeat-token\",\"session\":\"session-1\","
                    + "\"durationMs\":123456,\"nextAt\":1800000000000,\"retryAt\":1799999990000,"
                    + "\"dates\":[\"2026-10-22\",\"2026-11-01\"]}");
    }

    private static void oldDataRoundTrip() throws Exception {
        JSONObject old = legacyRecord();
        Alarm alarm = AlarmJson.read(old);
        check(
                alarm.plan.name.equals("早安") && alarm.plan.audioName.equals("家人录音.mp3"),
                "legacy names and audio retained");
        check(
                alarm.plan.dates.size() == 2
                        && alarm.plan.loopSeconds == 135
                        && alarm.plan.repeats == 2,
                "legacy date and playback settings retained");
        check(
                alarm.schedule.nextAt == 1800000000000L
                        && alarm.schedule.token.equals("regular-token"),
                "legacy regular schedule retained");
        check(
                alarm.execution.isActive()
                        && !alarm.execution.isPending()
                        && alarm.execution.round() == 1
                        && alarm.execution.retryAt() == 1799999990000L,
                "legacy active repeat retained");
        JSONObject saved = AlarmJson.write(alarm);
        Iterator<String> keys = old.keys();
        boolean same = true;
        while (keys.hasNext()) {
            String key = keys.next();
            same &= old.get(key).toString().equals(saved.get(key).toString());
        }
        check(same, "every old persisted field round trips without renaming or loss");
        old.put("pending", true);
        check(
                AlarmJson.read(old).execution.phase() == AlarmExecution.Phase.QUEUED,
                "legacy pending maps to queued");
        old.put("active", false).put("pending", false);
        check(
                AlarmJson.read(old).execution.phase() == AlarmExecution.Phase.IDLE,
                "legacy inactive maps to idle");
    }

    private static void executionLifecycle() throws Exception {
        Alarm a = new Alarm(1);
        a.plan.enabled = true;
        a.plan.repeats = 2;
        a.execution.begin();
        String firstSession = a.execution.session();
        check(
                a.execution.phase() == AlarmExecution.Phase.QUEUED && a.execution.isPending(),
                "occurrence enters explicit queued phase");
        a.execution.started(1000, 300, 2);
        check(
                a.execution.phase() == AlarmExecution.Phase.PLAYING && !a.execution.isPending(),
                "playback start enters playing phase");
        a.execution.completed(2000);
        check(
                a.execution.phase() == AlarmExecution.Phase.WAITING
                        && a.execution.retryAt() == 301000,
                "completion enters waiting until next replay");
        check(
                AlarmJson.read(AlarmJson.write(a)).execution.phase()
                        == AlarmExecution.Phase.WAITING,
                "waiting phase survives persistence");
        a.execution.enqueueRetry();
        check(
                a.execution.round() == 1
                        && a.execution.session().equals(firstSession)
                        && a.execution.isPending()
                        && a.execution.retryAt() == 0,
                "retry advances within same occurrence");
        a.execution.finish();
        check(
                a.plan.enabled && !a.execution.isActive() && a.execution.retryToken().isEmpty(),
                "ending occurrence keeps enabled plan and clears replay");
        a.execution.begin();
        check(
                a.execution.round() == 0 && !a.execution.session().equals(firstSession),
                "new occurrence replaces session and resets round");
        a.execution.started(1000, 300, 0);
        a.execution.completed(2000);
        check(a.execution.phase() == AlarmExecution.Phase.IDLE, "one-pass completion becomes idle");

        a.plan.dates.add("2026-11-01");
        Alarm copy = a.copy();
        copy.plan.dates.clear();
        copy.plan.name = "draft";
        copy.schedule.nextAt = 999;
        copy.execution.begin();
        check(
                a.plan.dates.size() == 1
                        && a.plan.name.isEmpty()
                        && a.schedule.nextAt == 0
                        && !a.execution.isActive(),
                "editing copy cannot mutate saved plan schedule or execution");
    }

    private static void moduleBoundaries() throws Exception {
        AlarmTests.C c = new AlarmTests.C();
        c.am.fail = true;
        check(
                AlarmRepository.all(c).isEmpty() && c.am.jobs.isEmpty(),
                "repository initializes without scheduler permission or registration");
        Alarm a = AlarmTests.fresh(c);
        a.plan.mode = "DAILY";
        a.schedule.nextAt = 1234;
        a.execution.begin();
        AlarmRepository.put(c, a);
        check(
                c.am.jobs.isEmpty() && AlarmRepository.get(c, a.id).execution.isPending(),
                "repository persists without scheduling or consuming execution");
        check(AudioFiles.file(c, a).isFile(), "repository read does not delete audio");

        c = new AlarmTests.C();
        a = AlarmTests.fresh(c);
        a.plan.mode = "DAILY";
        a.plan.repeats = 1;
        Alarm fired = AlarmTests.due(c, a);
        PlaybackSessions.add(a.id, fired.execution.session());
        AlarmController.restore(c, false);
        check(
                AlarmRepository.get(c, a.id).execution.isPending(),
                "restore preserves actual preparing playback without service singleton");
        PlaybackSessions.remove(a.id, fired.execution.session());
        AlarmController.restore(c, false);
        check(
                !AlarmRepository.get(c, a.id).execution.isActive(),
                "restore ends orphaned pending playback after process loss");

        c = new AlarmTests.C();
        a = AlarmTests.fresh(c);
        a.plan.mode = "DAILY";
        a.plan.repeats = 1;
        fired = AlarmTests.due(c, a);
        c.am.fail = true;
        Alarm started =
                AlarmController.started(
                        c, a.id, fired.execution.session(), System.currentTimeMillis());
        Alarm persisted = AlarmRepository.get(c, a.id);
        check(
                started.execution.retryAt() == 0
                        && persisted.execution.retryAt() == 0
                        && persisted.execution.retryToken().isEmpty(),
                "failed retry registration clears persisted retry as well as returned state");
        c.am.fail = false;
        AlarmController.completed(c, a.id, fired.execution.session(), 0);
        check(
                !AlarmRepository.get(c, a.id).execution.isActive(),
                "failed replay cannot leave a phantom waiting phase");

        c = new AlarmTests.C();
        a = AlarmTests.fresh(c);
        a.plan.mode = "DAILY";
        fired = AlarmTests.due(c, a);
        a = AlarmRepository.get(c, a.id);
        a.plan.minute = (a.plan.minute + 1) % 60;
        AlarmController.save(c, a);
        check(
                !AlarmRepository.get(c, a.id).execution.isActive()
                        && AlarmRepository.get(c, a.id).schedule.nextAt > 0,
                "saving active alarm ends old execution and keeps new date plan");
        check(
                AlarmController.started(
                                c, a.id, fired.execution.session(), System.currentTimeMillis())
                        == null,
                "old playback callback cannot start after edit");
    }

    private static void boundedImports() throws Exception {
        byte[] bytes = "中文调休".getBytes(StandardCharsets.UTF_8);
        check(
                BoundedStreams.text(new ByteArrayInputStream(bytes), bytes.length, "too large")
                        .equals("中文调休"),
                "bounded text preserves UTF-8 at exact limit");
        boolean rejected = false;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            BoundedStreams.copy(new ByteArrayInputStream(new byte[65537]), output, 65536, "limit");
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected && output.size() <= 65536, "oversized stream never writes beyond limit");
        rejected = false;
        try {
            BoundedStreams.text(null, 100, "limit");
        } catch (IOException expected) {
            rejected = true;
        }
        check(rejected, "null import stream is rejected");
    }

    public static void main(String[] args) throws Exception {
        oldDataRoundTrip();
        executionLifecycle();
        moduleBoundaries();
        boundedImports();
        System.out.println("TOTAL " + count + " refactor checks passed");
    }
}
