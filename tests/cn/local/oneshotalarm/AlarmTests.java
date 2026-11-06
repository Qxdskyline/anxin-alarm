package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;

import org.json.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public class AlarmTests {
    static class P implements SharedPreferences, SharedPreferences.Editor {
        Map<String, Object> m = new HashMap<>();

        public long getLong(String k, long d) {
            return (Long) m.getOrDefault(k, d);
        }

        public boolean getBoolean(String k, boolean d) {
            return (Boolean) m.getOrDefault(k, d);
        }

        public String getString(String k, String d) {
            return (String) m.getOrDefault(k, d);
        }

        public Editor edit() {
            return this;
        }

        public Editor putLong(String k, long v) {
            m.put(k, v);
            return this;
        }

        public Editor putBoolean(String k, boolean v) {
            m.put(k, v);
            return this;
        }

        public Editor putString(String k, String v) {
            m.put(k, v);
            return this;
        }

        public boolean commit() {
            return true;
        }
    }

    static class C extends Context {
        P p = new P();
        AlarmManager am = new AlarmManager();
        NotificationManager nm = new NotificationManager();
        android.os.PowerManager power = new android.os.PowerManager();
        File dir;
        int starts;
        boolean failStart;

        C() throws Exception {
            dir = Files.createTempDirectory(Paths.get("work"), "v2-test-").toFile();
        }

        public SharedPreferences getSharedPreferences(String s, int m) {
            return p;
        }

        public <T> T getSystemService(Class<T> k) {
            return k.cast(k == AlarmManager.class ? am : k == android.os.PowerManager.class ? power : nm);
        }

        public File getFilesDir() {
            return dir;
        }

        public Object startForegroundService(Intent i) {
            if (failStart) throw new IllegalStateException();
            starts++;
            return null;
        }
    }

    static int count;

    static void check(boolean b, String s) {
        if (!b) throw new AssertionError(s);
        System.out.println("PASS " + s);
        count++;
    }

    static long at(String v) {
        return LocalDateTime.parse(v).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
    }

    static HolidayCalendar cal = new HolidayCalendar(null);

    static long next(String mode, int mask, String date, Set<String> dates, String now) {
        return Schedule.next(
                mode, 7, 30, mask, date, dates, at(now), ZoneId.of("Asia/Shanghai"), cal);
    }

    static Alarm fresh(C c) throws Exception {
        Alarm a = new Alarm(AlarmRepository.allocate(c));
        a.plan.audio = "recording-" + a.id;
        Files.write(AudioFiles.file(c, a).toPath(), new byte[] {1});
        a.plan.audioName = "test";
        a.plan.durationMs = 1000;
        a.plan.date = LocalDate.now().plusDays(1).toString();
        return a;
    }

    static Alarm due(C c, Alarm a) {
        AlarmController.save(c, a);
        Alarm s = AlarmRepository.get(c, a.id);
        return AlarmController.consume(c, a.id, false, s.schedule.token, s.schedule.nextAt);
    }

    static void rejects(Runnable f, String name) {
        boolean bad = false;
        try {
            f.run();
        } catch (Exception e) {
            bad = true;
        }
        check(bad, name);
    }

    public static void main(String[] args) throws Exception {
        check(
                next("WORK", 0, "", Collections.emptySet(), "2026-01-03T20:00")
                        == at("2026-01-04T07:30"),
                "weekend makeup workday rings");
        check(
                next("WORK", 0, "", Collections.emptySet(), "2026-02-14T08:00")
                        == at("2026-02-24T07:30"),
                "spring holiday skipped");
        check(
                next("WORK", 0, "", Collections.emptySet(), "2026-09-19T08:00")
                        == at("2026-09-20T07:30"),
                "national day weekend makeup rings");
        check(
                next("WORK", 0, "", Collections.emptySet(), "2026-10-01T00:00")
                        == at("2026-10-08T07:30"),
                "national holiday skipped");
        check(
                next("REST", 0, "", Collections.emptySet(), "2026-01-04T00:00")
                        == at("2026-01-10T07:30"),
                "rest mode skips makeup Sunday");
        check(
                next("DAILY", 0, "", Collections.emptySet(), "2026-10-01T00:00")
                        == at("2026-10-01T07:30"),
                "daily ignores holidays");
        check(
                next("WEEKLY", 31, "", Collections.emptySet(), "2026-10-02T08:00")
                        == at("2026-10-05T07:30"),
                "weekday custom is distinct from legal workdays");
        check(
                next("WEEKLY", 64, "", Collections.emptySet(), "2026-10-02T08:00")
                        == at("2026-10-04T07:30"),
                "Sunday mask mapping");
        check(
                next("WORK", 0, "", Collections.emptySet(), "2027-12-31T08:00") == 0,
                "unknown year never guessed");
        check(
                next("ONCE", 0, "2026-10-22", Collections.emptySet(), "2026-10-22T07:30") == 0,
                "one date consumed at exact boundary");
        check(
                next(
                                "DATES",
                                0,
                                "",
                                new TreeSet<>(
                                        Arrays.asList("2026-10-20", "2026-10-25", "2026-10-23")),
                                "2026-10-21T09:00")
                        == at("2026-10-23T07:30"),
                "multiple dates choose earliest future");
        check(
                next("DAILY", 0, "", Collections.emptySet(), "2026-10-21T07:30")
                        == at("2026-10-22T07:30"),
                "no duplicate at same wall time");
        check(
                Schedule.repeatAt(0, 300, 0, 2) - 120000 == 180000,
                "2 minute playback 5 minute start interval leaves 3 minute silence");
        check(
                Schedule.repeatAt(600000, 300, 2, 2) == 0,
                "extra repeat count ends after third total start");
        rejects(
                () -> Schedule.validate(7, 0, true, 300, 1000, 300, 1),
                "overlapping equal duration interval rejected");
        rejects(
                () -> Schedule.validate(7, 0, true, 600, 1000, 300, 1),
                "longer playback than interval rejected");
        Schedule.validate(7, 0, false, 9999, 1000, 300, 1);
        check(true, "once clip option independent of saved loop duration");
        check(
                !cal.working(LocalDate.parse("2026-01-01"))
                        && cal.working(LocalDate.parse("2026-01-04")),
                "builtin holiday and makeup data");
        JSONObject imp =
                new JSONObject(
                        "{\"year\":2027,\"days\":[{\"date\":\"2027-01-01\",\"isOffDay\":true},{\"date\":\"2027-01-02\",\"isOffDay\":false}]}");
        JSONObject parsed = HolidayCalendar.parseImport(imp),
                years = new JSONObject().put("2027", parsed.getJSONObject("map"));
        HolidayCalendar imported = new HolidayCalendar(years);
        check(
                imported.known(2027)
                        && !imported.working(LocalDate.parse("2027-01-01"))
                        && imported.working(LocalDate.parse("2027-01-02")),
                "future year import defines overrides");
        rejects(
                () ->
                        HolidayCalendar.parseImport(
                                new JSONObject(
                                        "{\"year\":2027,\"days\":[{\"date\":\"2028-01-01\",\"isOffDay\":true}]}")),
                "cross year import rejected");
        rejects(
                () ->
                        HolidayCalendar.parseImport(
                                new JSONObject(
                                        "{\"year\":2027,\"days\":[{\"date\":\"2027-01-01\",\"isOffDay\":\"true\"}]}")),
                "string boolean import rejected");
        C c = new C();
        Alarm a = fresh(c), b = fresh(c);
        a.plan.mode = "DAILY";
        b.plan.mode = "DAILY";
        AlarmController.save(c, a);
        AlarmController.save(c, b);
        check(
                c.am.jobs.size() == 2 && AlarmRepository.all(c).size() == 2,
                "multiple alarms have independent system registrations");
        a = AlarmRepository.get(c, a.id);
        String token = a.schedule.token;
        AlarmController.toggle(c, a.id, false);
        check(
                AlarmController.consume(c, a.id, false, token, a.schedule.nextAt) == null
                        && c.am.jobs.size() == 1,
                "disable affects only chosen alarm");
        a = AlarmRepository.get(c, b.id);
        check(
                AlarmController.consume(c, a.id, false, a.schedule.token, a.schedule.nextAt - 10000)
                        == null,
                "early callback ignored");
        String old = a.schedule.token;
        AlarmController.save(c, a);
        check(
                AlarmController.consume(c, a.id, false, old, Long.MAX_VALUE - 1000000) == null,
                "stale token ignored after editing");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        a.plan.repeats = 2;
        Alarm fired = due(c, a);
        long base = System.currentTimeMillis();
        check(
                fired.execution.isActive()
                        && fired.execution.isPending()
                        && fired.execution.round() == 0,
                "first occurrence enters pending state");
        Alarm loaded = AlarmRepository.get(c, a.id);
        check(
                AlarmController.consume(
                                c,
                                a.id,
                                false,
                                loaded.schedule.token,
                                fired.schedule.nextAt - 10000)
                        == null,
                "next regular occurrence cannot fire early");
        Alarm first = AlarmController.started(c, a.id, fired.execution.session(), base);
        check(
                first.execution.retryAt() == base + 300000 && c.am.jobs.size() == 2,
                "repeat starts at first start plus interval, regular retained");
        check(
                AlarmController.started(c, a.id, fired.execution.session(), base) == null,
                "duplicate service start cannot reschedule repeat");
        AlarmController.completed(c, a.id, fired.execution.session(), 0);
        check(
                AlarmRepository.get(c, a.id).execution.isActive(),
                "natural clip end keeps finite retry");
        Alarm second =
                AlarmController.consume(
                        c, a.id, true, first.execution.retryToken(), first.execution.retryAt());
        check(
                second.execution.round() == 1 && second.execution.isPending(),
                "second round consumes one retry");
        check(
                AlarmController.consume(
                                c,
                                a.id,
                                true,
                                first.execution.retryToken(),
                                first.execution.retryAt())
                        == null,
                "duplicate retry broadcast ignored");
        Alarm started2 =
                AlarmController.started(
                        c, a.id, second.execution.session(), first.execution.retryAt());
        AlarmController.completed(c, a.id, second.execution.session(), 0);
        check(
                !AlarmRepository.get(c, a.id).execution.isPending()
                        && AlarmRepository.get(c, a.id).execution.round() == 1,
                "stale completion cannot change newer round");
        AlarmController.completed(c, a.id, second.execution.session(), 1);
        Alarm third =
                AlarmController.consume(
                        c,
                        a.id,
                        true,
                        started2.execution.retryToken(),
                        started2.execution.retryAt());
        Alarm finalStart =
                AlarmController.started(
                        c, a.id, third.execution.session(), started2.execution.retryAt());
        check(
                finalStart.execution.retryAt() == 0 && finalStart.execution.round() == 2,
                "configured two replays yields exactly three starts");
        AlarmController.completed(c, a.id, third.execution.session(), 2);
        loaded = AlarmRepository.get(c, a.id);
        check(
                !loaded.execution.isActive() && loaded.plan.enabled && loaded.schedule.nextAt > 0,
                "series completion preserves next calendar date");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        a.plan.repeats = 2;
        fired = due(c, a);
        first = AlarmController.started(c, a.id, fired.execution.session(), base);
        AlarmController.close(c, a.id, fired.execution.session(), "closed");
        loaded = AlarmRepository.get(c, a.id);
        check(
                !loaded.execution.isActive()
                        && loaded.plan.enabled
                        && loaded.execution.retryAt() == 0
                        && c.am.jobs.size() == 1,
                "X cancels remaining series only");
        check(
                AlarmController.consume(
                                c,
                                a.id,
                                true,
                                first.execution.retryToken(),
                                first.execution.retryAt())
                        == null,
                "cancelled retry never resurrects");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        fired = due(c, a);
        first = AlarmController.started(c, a.id, fired.execution.session(), base);
        AlarmController.close(c, a.id, "old-session", "closed");
        check(
                AlarmRepository.get(c, a.id).execution.isActive(),
                "old popup cannot close new occurrence");
        c = new C();
        a = fresh(c);
        fired = due(c, a);
        AlarmController.started(c, a.id, fired.execution.session(), base);
        AlarmController.completed(c, a.id, fired.execution.session(), 0);
        check(
                !AlarmRepository.get(c, a.id).plan.enabled
                        && !AlarmRepository.get(c, a.id).execution.isActive(),
                "one date playback ends without remaining tasks");
        c = new C();
        a = fresh(c);
        AlarmController.save(c, a);
        AlarmController.test(c, a);
        Alarm t = null;
        for (Alarm candidate : AlarmRepository.all(c)) if (candidate.test) t = candidate;
        check(
                AlarmRepository.all(c).size() == 2 && t != null && t.plan.repeats == 0,
                "test does not replace saved schedule or repeat");
        fired = AlarmController.consume(c, t.id, false, t.schedule.token, t.schedule.nextAt);
        check(
                !fired.plan.enabled && fired.schedule.nextAt == 0,
                "test never registers source date");
        AlarmController.started(c, t.id, fired.execution.session(), base);
        AlarmController.completed(c, t.id, fired.execution.session(), 0);
        check(
                AlarmRepository.get(c, t.id) == null && AudioFiles.file(c, a).exists(),
                "test cleanup retains shared recording");
        c = new C();
        a = fresh(c);
        b = fresh(c);
        b.plan.audio = a.plan.audio;
        AlarmController.save(c, a);
        AlarmController.save(c, b);
        AlarmController.delete(c, a.id);
        check(
                AudioFiles.file(c, b).exists() && AlarmRepository.all(c).size() == 1,
                "deleting alarm preserves another alarm shared audio");
        AlarmController.delete(c, b.id);
        check(!AudioFiles.file(c, b).exists(), "last reference removes recording");
        c = new C();
        Files.write(new File(c.dir, "recording").toPath(), new byte[] {1});
        c.p.putLong("at", System.currentTimeMillis() + 3600000)
                .putBoolean("armed", true)
                .putString("name", "old-audio")
                .commit();
        a = AlarmRepository.all(c).get(0);
        check(
                a.plan.enabled
                        && a.plan.audio.equals("recording")
                        && a.plan.audioName.equals("old-audio"),
                "v1 settings and audio migrate");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        AlarmController.save(c, a);
        long original = AlarmRepository.get(c, a.id).schedule.nextAt;
        AlarmController.restore(c, false);
        check(
                c.am.jobs.size() == 1 && AlarmRepository.get(c, a.id).schedule.nextAt == original,
                "reboot re-registers future regular alarm");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        a.plan.repeats = 1;
        fired = due(c, a);
        AlarmController.started(c, a.id, fired.execution.session(), System.currentTimeMillis());
        AlarmController.restore(c, false);
        check(
                AlarmRepository.get(c, a.id).execution.isActive() && c.am.jobs.size() == 2,
                "future repeat survives reboot without replaying old audio");
        AlarmController.restore(c, true);
        check(
                !AlarmRepository.get(c, a.id).execution.isActive() && c.am.jobs.size() == 1,
                "clock or timezone change ends old repeat and replans");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        AlarmController.save(c, a);
        loaded = AlarmRepository.get(c, a.id);
        Intent delivery = AlarmScheduler.operation(c, a.id, false, loaded.schedule.token).intent;
        new AlarmReceiver().onReceive(c, delivery);
        check(c.starts == 0, "receiver checks due time rather than only token");
        c = new C();
        a = fresh(c);
        c.am.allowed = false;
        final C denied = c;
        final Alarm no = a;
        rejects(
                () -> AlarmController.save(denied, no),
                "exact permission checked before persistence");
        c = new C();
        a = fresh(c);
        c.am.fail = true;
        try {
            AlarmController.save(c, a);
        } catch (Exception expected) {
        }
        check(
                !AlarmRepository.get(c, a.id).plan.enabled
                        && AlarmRepository.get(c, a.id).schedule.nextAt == 0,
                "registration failure visible and disabled");
        JSONObject calendarFile =
                new JSONObject(
                        new String(
                                Files.readAllBytes(Paths.get(args[0])),
                                java.nio.charset.StandardCharsets.UTF_8));
        JSONArray entries = calendarFile.getJSONArray("days");
        boolean same = entries.length() == 39;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject day = entries.getJSONObject(i);
            same &=
                    cal.working(LocalDate.parse(day.getString("date")))
                            != day.getBoolean("isOffDay");
        }
        check(same, "delivered calendar file matches all 39 builtin official overrides");
        JSONObject official2027 = new JSONObject(new String(Files.readAllBytes(Paths.get(args[1])),
                java.nio.charset.StandardCharsets.UTF_8));
        JSONObject expected2027 = HolidayCalendar.parseImport(official2027).getJSONObject("map");
        Map<String, Boolean> actual2027 = cal.years.get(2027);
        boolean same2027 = actual2027.size() == 34 && expected2027.length() == 34;
        for (String date : expected2027.keySet())
            same2027 &= Boolean.valueOf(expected2027.getBoolean(date)).equals(actual2027.get(date));
        check(same2027, "all 34 official 2027 dates match the built-in calendar");
        check(next("WORK", 0, "", Collections.emptySet(), "2026-12-31T08:00")
                        == at("2027-01-04T07:30"),
                "workday alarms skip the 2027 New Year holiday across the year boundary");
        check(next("WORK", 0, "", Collections.emptySet(), "2027-01-29T08:00")
                        == at("2027-01-31T07:30"),
                "workday alarm rings on the 2027 Spring Festival makeup Sunday");
        c = new C();
        a = fresh(c);
        a.plan.loop = true;
        a.plan.loopSeconds = 120;
        AlarmController.save(c, a);
        loaded = AlarmRepository.get(c, a.id);
        loaded.plan.loop = false;
        AlarmController.save(c, loaded);
        check(
                !AlarmRepository.get(c, a.id).plan.loop
                        && AlarmRepository.get(c, a.id).plan.loopSeconds == 120,
                "can switch back to one-pass without losing saved duration");
        c = new C();
        a = fresh(c);
        a.plan.mode = "DAILY";
        AlarmController.save(c, a);
        JSONObject raw = new JSONObject(c.p.getString("v2", ""));
        JSONObject entry = raw.getJSONArray("alarms").getJSONObject(0);
        entry.put("nextAt", System.currentTimeMillis() - 100);
        c.p.putString("v2", raw.toString()).commit();
        loaded = AlarmRepository.get(c, a.id);
        Intent callback = AlarmScheduler.operation(c, a.id, false, loaded.schedule.token).intent;
        new AlarmReceiver().onReceive(c, callback);
        new AlarmReceiver().onReceive(c, callback);
        check(
                c.starts == 1,
                "actual receiver dispatches one service start for duplicate due callback");
        c = new C();
        a = fresh(c);
        AlarmController.save(c, a);
        raw = new JSONObject(c.p.getString("v2", ""));
        entry = raw.getJSONArray("alarms").getJSONObject(0);
        entry.put("nextAt", System.currentTimeMillis() - 100);
        c.p.putString("v2", raw.toString()).commit();
        loaded = AlarmRepository.get(c, a.id);
        c.failStart = true;
        new AlarmReceiver()
                .onReceive(
                        c, AlarmScheduler.operation(c, a.id, false, loaded.schedule.token).intent);
        check(
                !AlarmRepository.get(c, a.id).execution.isActive()
                        && AlarmRepository.get(c, a.id).execution.retryAt() == 0,
                "blocked service startup closes series instead of retrying forever");
        System.out.println("TOTAL " + count + " behavioral checks passed");
    }
}
