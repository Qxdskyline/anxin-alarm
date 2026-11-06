package cn.local.oneshotalarm;

import java.util.*;

/** 用户保存的配置；不包含某一天的播放进度。 */
final class AlarmPlan {
    int hour = 7;
    int minute;
    int weekdays = 31;
    int loopSeconds = 120;
    int intervalSeconds = 300;
    int repeats;
    boolean enabled = true;
    boolean loop;
    String name = "";
    String mode = "ONCE";
    String date = "";
    String audio = "";
    String audioName = "";
    long durationMs;
    boolean shuffleAudio;
    final List<Track> playlist = new ArrayList<>();

    static final class Track {
        final String file, name;
        final long duration;

        Track(String file, String name, long duration) {
            this.file = file;
            this.name = name;
            this.duration = duration;
        }
    }

    List<Track> tracks() {
        if (!playlist.isEmpty()) return playlist;
        return audio.isEmpty() ? Collections.emptyList()
                : Collections.singletonList(new Track(audio, audioName, durationMs));
    }

    void editTracks() {
        if (playlist.isEmpty()) playlist.addAll(tracks());
        audio = "";
        audioName = "";
    }

    void updateDuration() {
        durationMs = 0;
        for (Track track : playlist) {
            if (track.duration <= 0) {
                durationMs = 0;
                return;
            }
            durationMs += track.duration;
        }
    }

    final TreeSet<String> dates = new TreeSet<>();

    long nextOccurrence(long after, java.time.ZoneId zone, Schedule.WorkCalendar calendar) {
        return Schedule.next(mode, hour, minute, weekdays, date, dates, after, zone, calendar);
    }

    void validate() {
        Schedule.validate(hour, minute, loop, loopSeconds, durationMs, intervalSeconds, repeats);
        if (repeats > 0 && durationMs <= 0 && !loop) {
            throw new IllegalArgumentException("无法确认录音时长，请重新导入后设置重播");
        }
        if (mode.equals("WEEKLY") && weekdays == 0) {
            throw new IllegalArgumentException("请至少选择一个星期");
        }
        if (mode.equals("DATES") && dates.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个日期");
        }
    }

    AlarmPlan copy() {
        AlarmPlan copy = new AlarmPlan();
        copy.hour = hour;
        copy.minute = minute;
        copy.weekdays = weekdays;
        copy.loopSeconds = loopSeconds;
        copy.intervalSeconds = intervalSeconds;
        copy.repeats = repeats;
        copy.enabled = enabled;
        copy.loop = loop;
        copy.name = name;
        copy.mode = mode;
        copy.date = date;
        copy.audio = audio;
        copy.audioName = audioName;
        copy.durationMs = durationMs;
        copy.playlist.addAll(playlist);
        copy.shuffleAudio = shuffleAudio;
        copy.dates.addAll(dates);
        return copy;
    }
}
