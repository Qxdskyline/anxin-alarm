package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.util.*;

public final class PlaybackService extends Service {
    private MediaPlayer player;
    private final List<AlarmPlan.Track> tracks = new ArrayList<>();
    private AudioAttributes attributes;
    private int trackIndex, failedTracks;
    private boolean roundStarted;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock preparingWake;
    private Alarm current;
    private final ArrayDeque<Alarm> queue = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences.OnSharedPreferenceChangeListener changes =
            (p, k) -> {
                if ("v2".equals(k)) handler.post(this::checkState);
            };
    private WindowManager window;
    private View overlay;
    private boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        AlarmRepository.preferences(this).registerOnSharedPreferenceChangeListener(changes);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        AlarmTrace.record(this, "进入播放服务 #" + (i == null ? 0 : i.getIntExtra("id", 0)));
        holdPreparation();
        // An exact alarm is permitted to start this service. Promote before doing playback work.
        if (current == null) promote(900, ReminderNotice.preparing(this));
        if (i != null) {
            AlarmWakeBridge.release(i.getIntExtra("id", 0), i.getStringExtra("session"));
            AlarmTrace.record(this, "播放服务已启动 #" + i.getIntExtra("id", 0));
        }
        boolean scheduled = i != null && AlarmDelivery.ACTION.equals(i.getAction());
        Alarm a = i == null ? null : scheduled
                ? AlarmDelivery.consume(this, i, System.currentTimeMillis())
                : AlarmRepository.get(this, i.getIntExtra("id", 0));
        if (a != null
                && a.execution.isActive()
                && a.execution.isPending()
                && (scheduled || a.execution.session().equals(i.getStringExtra("session")))) {
            boolean exists =
                    current != null
                            && current.id == a.id
                            && current.execution.session().equals(a.execution.session())
                            && current.execution.round() == a.execution.round();
            for (Alarm q : queue)
                if (q.id == a.id
                        && q.execution.session().equals(a.execution.session())
                        && q.execution.round() == a.execution.round()) exists = true;
            if (!exists) queue.add(a);
        }
        checkState();
        if (current == null) next();
        return START_NOT_STICKY;
    }

    private void promote(int id, Notification n) {
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(id, n);
    }

    private void checkState() {
        if (destroyed) return;
        if (current != null) {
            Alarm a = AlarmRepository.get(this, current.id);
            if (a == null
                    || !a.execution.isActive()
                    || !a.execution.session().equals(current.execution.session())
                    || a.execution.round() != current.execution.round()) {
                release();
                current = null;
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
        }
        queue.removeIf(
                q -> {
                    Alarm a = AlarmRepository.get(this, q.id);
                    return a == null
                            || !a.execution.isActive()
                            || !a.execution.isPending()
                            || !a.execution.session().equals(q.execution.session())
                            || a.execution.round() != q.execution.round();
                });
        if (current == null) next();
    }

    private void next() {
        if (destroyed || current != null) return;
        while (!queue.isEmpty()) {
            Alarm q = queue.remove();
            Alarm a = AlarmRepository.get(this, q.id);
            if (a == null
                    || !a.execution.isActive()
                    || !a.execution.isPending()
                    || !a.execution.session().equals(q.execution.session())
                    || a.execution.round() != q.execution.round()) continue;
            current = a;
            PlaybackSessions.add(a.id, a.execution.session());
            play();
            return;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).cancel(900);
        stopSelf();
    }

    private void play() {
        final Alarm a = current;
        try {
            promote(1000 + a.id, ReminderNotice.ringing(this, a));
            getSystemService(NotificationManager.class).cancel(900);
            attributes =
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
            audio = getSystemService(AudioManager.class);
            focus =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(attributes)
                            .setOnAudioFocusChangeListener(
                                    change -> {
                                        if (change < 0) fail("被其他音频中断，本轮已结束");
                                    },
                                    handler)
                            .build();
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                fail("未获得音频焦点，本轮已结束");
                return;
            }
            tracks.clear();
            tracks.addAll(a.plan.tracks());
            if (a.plan.shuffleAudio) Collections.shuffle(tracks);
            trackIndex = 0;
            failedTracks = 0;
            roundStarted = false;
            playTrack();
        } catch (RuntimeException e) {
            fail("无法播放录音，请重新选择文件");
        }
    }

    private void playTrack() {
        Alarm a = current;
        if (a == null) return;
        if (tracks.isEmpty() || failedTracks >= tracks.size()) {
            fail("没有可播放的录音，请检查文件");
            return;
        }
        if (trackIndex == tracks.size()) {
            if (!a.plan.loop) {
                complete();
                return;
            }
            trackIndex = 0;
        }

        AlarmPlan.Track track = tracks.get(trackIndex++);
        holdPreparation();
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        Runnable prepareTimeout = () -> trackFailed(mp, "录音打开超时");
        try {
            mp.setAudioAttributes(attributes);
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(new java.io.File(getFilesDir(), track.file).getAbsolutePath());
            mp.setLooping(a.plan.loop && tracks.size() == 1);
            mp.setOnCompletionListener(p -> {
                if (p != player) return;
                failedTracks = 0;
                holdPreparation();
                releasePlayer();
                playTrack();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                handler.removeCallbacks(prepareTimeout);
                trackFailed(p, "录音播放失败");
                return true;
            });
            mp.setOnPreparedListener(p -> {
                if (p != player) return;
                handler.removeCallbacks(prepareTimeout);
                checkState();
                if (p != player) return;
                try {
                    p.start();
                } catch (RuntimeException e) {
                    trackFailed(p, "录音无法开始播放");
                    return;
                }
                if (preparingWake != null && preparingWake.isHeld()) preparingWake.release();
                if (!roundStarted) startRound(a);
            });
            handler.postDelayed(prepareTimeout, 30000);
            mp.prepareAsync();
        } catch (Exception e) {
            handler.removeCallbacks(prepareTimeout);
            trackFailed(mp, "无法打开录音");
        }
    }

    private void startRound(Alarm a) {
        Alarm started = AlarmController.started(this, a.id, a.execution.session(),
                System.currentTimeMillis());
        if (started == null) {
            release();
            current = null;
            next();
            return;
        }
        current = started;
        roundStarted = true;
        AlarmTrace.record(this, "录音开始播放 #" + a.id + " 共 " + tracks.size() + " 段");
        showOverlay(started);
        if (started.plan.loop)
            handler.postDelayed(this::complete, started.plan.loopSeconds * 1000L);
        else
            handler.postDelayed(() -> fail("播放超时，本轮已结束"),
                    Math.max(60000L, started.plan.durationMs + 30000L * tracks.size()));
    }

    private void trackFailed(MediaPlayer mp, String reason) {
        if (mp != player) return;
        AlarmTrace.record(this, reason + " #" + current.id + " 第 " + trackIndex + " 段");
        failedTracks++;
        releasePlayer();
        handler.post(this::playTrack);
    }

    private void complete() {
        if (current != null) AlarmTrace.record(this, "录音播放结束 #" + current.id);
        if (current == null) return;
        Alarm done = current;
        release();
        current = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        AlarmController.completed(this, done.id, done.execution.session(), done.execution.round());
        ReminderNotice.waiting(this, AlarmRepository.get(this, done.id));
        next();
    }

    private void fail(String reason) {
        if (current != null) AlarmTrace.record(this, "播放失败 #" + current.id + " " + reason);
        if (current == null) return;
        Alarm done = current;
        release();
        current = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        AlarmController.close(this, done.id, done.execution.session(), reason);
        next();
    }

    private void showOverlay(Alarm a) {
        if (!Settings.canDrawOverlays(this)) return;
        hideOverlay();
        window = getSystemService(WindowManager.class);
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.shape(Ui.DARK, 28, this));
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14));
        card.setElevation(Ui.dp(this, 12));
        LinearLayout row = Ui.row(this), left = Ui.column(this);
        Ui.addText(left, "●  " + a.title(), 17, Color.WHITE, true);
        Ui.addText(
                left,
                AlarmText.playDescription(a)
                        + " · 第 "
                        + (a.execution.round() + 1)
                        + " / "
                        + (a.plan.repeats + 1)
                        + " 次",
                12,
                0xFFBFC6D0,
                false);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = Ui.text(this, "×", 30, Color.WHITE, false);
        close.setContentDescription("结束本轮及剩余重播");
        close.setGravity(Gravity.CENTER);
        row.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        close.setOnClickListener(
                v -> AlarmController.close(this, a.id, a.execution.session(), "已结束本轮提醒"));
        card.addView(row);
        card.setOnClickListener(
                v -> {
                    try {
                        startActivity(
                                new Intent(this, RingingActivity.class)
                                        .putExtra("id", a.id)
                                        .putExtra("session", a.execution.session())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException ignored) {
                    }
                });
        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.y = Ui.dp(this, 48);
        p.width = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32);
        try {
            window.addView(card, p);
            overlay = card;
            card.setAlpha(0);
            card.animate().alpha(1).setDuration(220).start();
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    private void hideOverlay() {
        if (overlay != null && window != null)
            try {
                window.removeViewImmediate(overlay);
            } catch (RuntimeException ignored) {
            }
        overlay = null;
    }

    private void holdPreparation() {
        if (preparingWake == null) {
            preparingWake = getSystemService(PowerManager.class)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AnxinAlarm:prepare");
            preparingWake.setReferenceCounted(false);
        }
        preparingWake.acquire(30000);
    }

    private void releasePlayer() {
        if (player != null) {
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            player.setOnPreparedListener(null);
            player.release();
            player = null;
        }
    }

    private void release() {
        if (current != null) PlaybackSessions.remove(current.id, current.execution.session());
        handler.removeCallbacksAndMessages(null);
        hideOverlay();
        releasePlayer();
        if (audio != null && focus != null) {
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
        if (preparingWake != null) {
            if (preparingWake.isHeld()) preparingWake.release();
            preparingWake = null;
        }
    }

    @Override
    public void onDestroy() {
        AlarmTrace.record(this, current == null ? "播放服务已退出" : "播放服务中断 #" + current.id);
        destroyed = true;
        AlarmRepository.preferences(this).unregisterOnSharedPreferenceChangeListener(changes);
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
