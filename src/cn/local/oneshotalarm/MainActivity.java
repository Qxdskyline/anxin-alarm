package cn.local.oneshotalarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private LinearLayout list;
    private TextView summary;
    private boolean busy;
    private final Handler screenClock = new Handler(Looper.getMainLooper());
    private boolean screenActive;
    private final Runnable refreshClock = new Runnable() {
        @Override public void run() {
            if (!screenActive) return;
            AlarmController.refresh(MainActivity.this, System.currentTimeMillis(), false);
            render();
            screenClock.postDelayed(this, 30000);
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener changes =
            (p, k) -> {
                if ("v2".equals(k)) runOnUiThread(this::render);
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        recordLastProcessExit();
        AlarmController.initialize(this);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        Ui.insets(frame);
        LinearLayout page = Ui.column(this);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 110));
        LinearLayout header = Ui.row(this);
        TextView title = Ui.text(this, "闹钟", 34, Ui.INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        View settings = Ui.iconButton(this, "settings", Ui.ORANGE, Ui.SOFT, 50);
        settings.setContentDescription("安心响铃");
        header.addView(settings, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 50)));
        settings.setOnClickListener(v -> settings());
        page.addView(header);
        Ui.gap(page, 14);
        summary = Ui.text(this, "", 16, Ui.MUTED, false);
        summary.setLineSpacing(Ui.dp(this, 5), 1);
        page.addView(summary);
        Ui.gap(page, 24);
        list = Ui.column(this);
        page.addView(list);
        frame.addView(Ui.scroll(this, page), new FrameLayout.LayoutParams(-1, -1));
        View add = Ui.iconButton(this, "plus", Color.WHITE, Ui.ORANGE, 68);
        add.setContentDescription("添加闹钟");
        add.setElevation(Ui.dp(this, 6));
        FrameLayout.LayoutParams fab =
                new FrameLayout.LayoutParams(
                        Ui.dp(this, 68),
                        Ui.dp(this, 68),
                        Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fab.bottomMargin = Ui.dp(this, 22);
        frame.addView(add, fab);
        add.setOnClickListener(
                v -> {
                    if (!busy) startActivity(new Intent(this, EditAlarmActivity.class));
                });
        setContentView(frame);
        AlarmRepository.preferences(this).registerOnSharedPreferenceChangeListener(changes);
        render();
        setVolumeControlStream(AudioManager.STREAM_ALARM);
    }

    private void render() {
        if (list == null || isDestroyed()) return;
        list.removeAllViews();
        List<Alarm> alarms = AlarmRepository.all(this);
        alarms.sort(Comparator.comparingInt(a -> a.plan.hour * 60 + a.plan.minute));
        long next = 0;
        long now = System.currentTimeMillis();
        boolean due = false;
        int count = 0;
        for (Alarm a : alarms) {
            if (a.plan.enabled && a.schedule.nextAt > now && (next == 0 || a.schedule.nextAt < next))
                next = a.schedule.nextAt;
            if (a.plan.enabled && a.schedule.nextAt > 0 && a.schedule.nextAt <= now) due = true;
            if (a.execution.isActive()
                    && a.execution.retryAt() > now
                    && (next == 0 || a.execution.retryAt() < next)) next = a.execution.retryAt();
            if (a.test) continue;
            count++;
            LinearLayout box = Ui.card(list), row = Ui.row(this), left = Ui.column(this);
            TextView time =
                    Ui.text(this, a.time(), 49, a.plan.enabled ? Ui.INK : 0xFFB9B7B1, false);
            time.setTypeface(android.graphics.Typeface.create("sans-serif-light", 0));
            left.addView(time);
            Ui.addText(left, a.title(), 16, a.plan.enabled ? Ui.INK : Ui.MUTED, true);
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this);
            toggle.setChecked(a.plan.enabled);
            toggle.setContentDescription(a.title() + "开关");
            row.addView(toggle);
            box.addView(row);
            Ui.addText(box, AlarmText.description(a), 13, Ui.MUTED, false);
            String detail =
                    AlarmText.playDescription(a)
                            + (a.plan.repeats > 0
                                    ? "  ·  间隔 "
                                            + AlarmText.duration(a.plan.intervalSeconds)
                                            + "，重播 "
                                            + a.plan.repeats
                                            + " 次"
                                    : "");
            Ui.addText(box, detail, 12, Ui.MUTED, false);
            if (a.plan.enabled
                    && a.schedule.nextAt == 0
                    && (a.plan.mode.equals("WORK") || a.plan.mode.equals("REST")))
                Ui.addText(box, "调休日历待更新，暂未安排下一次", 12, Ui.ORANGE, false);
            else if (!a.status.isEmpty() && (a.execution.isActive() || a.schedule.nextAt == 0
                    || a.status.contains("未确认") || a.status.contains("失败")))
                Ui.addText(box, a.status, 12, Ui.ORANGE, false);
            if (a.execution.isActive()) {
                TextView end = Ui.action(this, "×  结束本轮提醒", false);
                box.addView(end);
                end.setOnClickListener(
                        v -> AlarmController.close(this, a.id, a.execution.session(), "已结束本轮提醒"));
            }
            View.OnClickListener edit =
                    v -> {
                        if (!busy)
                            startActivity(
                                    new Intent(this, EditAlarmActivity.class).putExtra("id", a.id));
                    };
            left.setOnClickListener(edit);
            box.setOnClickListener(edit);
            box.setOnLongClickListener(
                    v -> {
                        menu(a);
                        return true;
                    });
            left.setOnLongClickListener(
                    v -> {
                        menu(a);
                        return true;
                    });
            Ui.pressFeedback(box, box);
            Ui.pressFeedback(left, box);
            toggle.setOnCheckedChangeListener(
                    (b, on) -> {
                        if (busy) return;
                        busy = true;
                        b.setEnabled(false);
                        try {
                            AlarmController.toggle(this, a.id, on);
                        } catch (Exception e) {
                            Ui.error(this, e);
                        } finally {
                            busy = false;
                            render();
                        }
                    });
        }
        if (count == 0) {
            LinearLayout empty = Ui.card(list);
            Ui.gap(empty, 24);
            Ui.addText(empty, "☀", 62, Ui.ORANGE, false);
            Ui.addText(empty, "给明天一个好开场", 23, Ui.INK, true);
            Ui.addText(empty, "轻点 ＋，用你喜欢的声音\n唤醒一天，或提醒一件小事。", 16, Ui.MUTED, false);
            Ui.gap(empty, 28);
        }
        summary.setText(next == 0 && due ? "响铃时间已到，请查看响铃记录" : AlarmText.upcoming(next, now, count));
    }

    private void menu(Alarm a) {
        LinearLayout p = Ui.column(this), card = Ui.card(p);
        Dialog d = Ui.sheet(this, a.time(), a.title() + " · " + AlarmText.description(a), p);
        Ui.option(
                card,
                "编辑闹钟",
                "调整时间、日期与录音",
                v -> {
                    if (busy) return;
                    d.dismiss();
                    startActivity(new Intent(this, EditAlarmActivity.class).putExtra("id", a.id));
                });
        Ui.divider(card);
        Ui.option(
                card,
                "30 秒后试响",
                "保留原来的响铃计划",
                v -> {
                    if (busy) return;
                    d.dismiss();
                    try {
                        if (!AlarmScheduler.allowed(this)) {
                            permissions();
                            return;
                        }
                        AlarmController.test(this, a);
                        Ui.toast(this, "30 秒后试响，请锁屏测试");
                    } catch (Exception e) {
                        Ui.error(this, e);
                    }
                });
        Ui.divider(card);
        TextView remove =
                Ui.option(
                        card,
                        "删除闹钟",
                        "",
                        v -> {
                            if (busy) return;
                            d.dismiss();
                            Ui.confirmDelete(
                                    this,
                                    a.title(),
                                    () -> {
                                        if (busy) return;
                                        busy = true;
                                        try {
                                            AlarmController.delete(this, a.id);
                                        } finally {
                                            busy = false;
                                            render();
                                        }
                                    });
                        });
        remove.setTextColor(0xFFC55342);
        Ui.showSheet(this, d);
    }

    private void settings() {
        LinearLayout p = Ui.column(this), sound = Ui.card(p);
        AudioManager am = getSystemService(AudioManager.class);
        Ui.addText(sound, "声音", 18, Ui.INK, true);
        Ui.addText(sound, "闹钟音量", 13, Ui.MUTED, false);
        SeekBar volume = new SeekBar(this);
        volume.setMax(am.getStreamMaxVolume(AudioManager.STREAM_ALARM));
        volume.setProgress(am.getStreamVolume(AudioManager.STREAM_ALARM));
        sound.addView(volume);
        volume.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onStartTrackingTouch(SeekBar b) {}

                    public void onStopTrackingTouch(SeekBar b) {}

                    public void onProgressChanged(SeekBar b, int x, boolean user) {
                        if (user) am.setStreamVolume(AudioManager.STREAM_ALARM, x, 0);
                    }
                });
        LinearLayout reminders = Ui.card(p);
        Ui.addText(reminders, "提醒方式", 18, Ui.INK, true);
        NotificationManager nm = getSystemService(NotificationManager.class);
        Ui.option(
                reminders,
                "通知与准时提醒",
                nm.areNotificationsEnabled() && AlarmScheduler.allowed(this) ? "已就绪" : "轻点完成开启",
                v -> permissions());
        Ui.divider(reminders);
        Ui.option(
                reminders,
                "悬浮卡片",
                Settings.canDrawOverlays(this) ? "已开启 · 响铃时轻点 × 结束" : "未开启",
                v ->
                        open(
                                new Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()))));
        Ui.divider(reminders);
        Ui.option(
                reminders,
                "锁屏提醒",
                Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent() ? "已开启" : "未开启",
                v -> {
                    if (Build.VERSION.SDK_INT >= 34)
                        open(
                                new Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:" + getPackageName())));
                    else Ui.toast(this, "此版本由系统管理锁屏提醒");
                });
        LinearLayout other = Ui.card(p);
        LinearLayout reliabilityRow = Ui.row(this);
        LinearLayout reliabilityLabels = Ui.column(this);
        Ui.addText(reliabilityLabels, "可靠模式", 16, Ui.INK, true);
        Ui.addText(reliabilityLabels, "常驻通知保护响铃，可退出最近任务\n少量内存占用，不轮询、不持续占用 CPU", 12, Ui.MUTED, false);
        reliabilityRow.addView(reliabilityLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch reliability = new Switch(this);
        reliability.setChecked(AlarmReliability.enabled(this));
        reliability.setContentDescription("可靠模式开关");
        reliabilityRow.addView(reliability);
        other.addView(reliabilityRow);
        reliability.setOnCheckedChangeListener((button, on) -> {
            button.setEnabled(false);
            try { AlarmReliability.setEnabled(this, on); }
            finally { button.setEnabled(true); }
        });
        Ui.divider(other);
        PowerManager power = getSystemService(PowerManager.class);
        Ui.option(
                other,
                "锁屏待机保护",
                power.isIgnoringBatteryOptimizations(getPackageName())
                        ? "已允许不受电池优化限制" : "点此允许，减少待机时被限制",
                v -> {
                    if (power.isIgnoringBatteryOptimizations(getPackageName())) {
                        Ui.toast(this, "已允许不受电池优化限制");
                    } else {
                        open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    }
                });
        Ui.divider(other);
        Ui.option(other, "响铃记录", "查看计划、系统触发和播放情况", v -> showAlarmTrace());
        Ui.divider(other);
        Ui.option(
                other,
                "调休日历",
                "已缓存 " + AlarmRepository.holidays(this).years.keySet() + " 年 · 联网更新",
                v -> holidayInfo());
        Ui.divider(other);
        Ui.option(
                other,
                "后台运行",
                "允许自启动与后台活动，让提醒如约响起",
                v ->
                        open(
                                new Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName()))));
        Ui.addText(p, "安心闹钟 2.5.0", 12, Ui.MUTED, false);
        Ui.showSheet(this, Ui.sheet(this, "安心响铃", "声音、提醒和日历，按你的习惯来", p));
    }

    private void recordLastProcessExit() {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                List<ApplicationStartInfo> starts = getSystemService(ActivityManager.class)
                        .getHistoricalProcessStartReasons(1);
                if (!starts.isEmpty()) {
                    ApplicationStartInfo start = starts.get(0);
                    boolean forced = start.wasForceStopped();
                    AlarmTrace.record(this, "本次进程启动前曾处于强停状态=" + forced + " 启动原因=" + start.getReason());
                    AlarmRepository.preferences(this).edit().putString("last-force-stop", String.valueOf(forced)).commit();
                }
            } catch (RuntimeException error) {
                AlarmTrace.record(this, "无法读取系统强停标记 " + error.getClass().getSimpleName());
            }
        }
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            List<ApplicationExitInfo> exits = getSystemService(ActivityManager.class)
                    .getHistoricalProcessExitReasons(getPackageName(), 0, 2);
            for (ApplicationExitInfo exit : exits) {
                AlarmTrace.record(this, "上次进程退出 时间=" + AlarmTrace.time(exit.getTimestamp())
                        + " 原因码=" + exit.getReason() + " " + exit.getDescription());
            }
            AlarmTrace.record(this, "打开应用 后台受限="
                    + getSystemService(ActivityManager.class).isBackgroundRestricted());
            AlarmManager.AlarmClockInfo next = getSystemService(AlarmManager.class).getNextAlarmClock();
            AlarmTrace.record(this, next == null ? "打开前 系统没有下一次闹钟"
                    : "打开前 系统全局最早闹钟=" + AlarmTrace.time(next.getTriggerTime())
                    + " 归属=" + (next.getShowIntent() == null ? "未知" : next.getShowIntent().getCreatorPackage()));
        } catch (RuntimeException ignored) { }
    }
    private void showAlarmTrace() {
        StringBuilder report = new StringBuilder("安心闹钟 2.5.0\n");
        report.append("可靠模式=").append(AlarmReliability.enabled(this))
                .append(" 保护服务运行=").append(AlarmGuardService.running).append("\n");
        report.append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.SDK_INT).append("\n")
                .append("精确闹钟=").append(AlarmScheduler.allowed(this))
                .append(" 通知=").append(getSystemService(NotificationManager.class).areNotificationsEnabled())
                .append(" 电池优化豁免=").append(getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(getPackageName()))
                .append(" 闹钟音量=").append(getSystemService(AudioManager.class)
                        .getStreamVolume(AudioManager.STREAM_ALARM)).append("\n\n");
        for (Alarm alarm : AlarmRepository.all(this)) {
            if (alarm.test) continue;
            report.append("#").append(alarm.id).append(" ").append(alarm.plan.mode)
                    .append(" 启用=").append(alarm.plan.enabled)
                    .append(" 下次=").append(AlarmTrace.time(alarm.schedule.nextAt))
                    .append(" 阶段=").append(alarm.execution.phase()).append("\n");
        }
        report.append("\n").append(AlarmTrace.read(this));
        String text = report.toString();
        new AppDialog.Builder(this).setTitle("响铃记录")
                .setSubtitle("记录只保存在本机，自动保留最近 7 天")
                .setNeutralButton("清理旧记录", (d, w) -> {
                    AlarmTrace.read(this);
                    Ui.toast(this, "仅保留最近 7 天的记录");
                })
                .setMessage(text).setNegativeButton("关闭", null)
                .setPositiveButton("复制记录", (d, w) -> {
                    getSystemService(ClipboardManager.class).setPrimaryClip(
                            ClipData.newPlainText("安心闹钟响铃记录", text));
                    Ui.toast(this, "已复制响铃记录");
                }).show();
    }

    private void holidayInfo() {
        LinearLayout p = Ui.column(this), card = Ui.card(p);
        NumberPicker year = new NumberPicker(this);
        year.setMinValue(2020);
        year.setMaxValue(2100);
        year.setValue(LocalDate.now().getYear());
        year.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 140));
        card.addView(year, yp);
        TextView update = Ui.action(this, "联网更新所选年度", true);
        card.addView(update, new LinearLayout.LayoutParams(-1, -2));
        Dialog d =
                Ui.sheet(
                        this,
                        "调休日历",
                        "已缓存 " + AlarmRepository.holidays(this).years.keySet() + " 年",
                        p);
        update.setOnClickListener(
                v -> {
                    d.dismiss();
                    HolidaySync.update(this, year.getValue(), this::render);
                });
        Ui.addText(
                p,
                "只在你点击时联网，成功后保存在手机；响铃时无需联网。数据来自 holiday-cn 开源日历，以其引用的正式放假安排为依据。",
                12,
                Ui.MUTED,
                false);
        Ui.option(
                p,
                "从文件导入",
                "保留离线导入方式",
                v -> {
                    if (busy) return;
                    d.dismiss();
                    Intent i =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .setType("*/*")
                                    .addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(i, 42);
                    } catch (ActivityNotFoundException e) {
                        Ui.error(this, e);
                    }
                });
        Ui.showSheet(this, d);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 42 || result != RESULT_OK || data == null || data.getData() == null || busy)
            return;
        busy = true;
        Uri uri = data.getData();
        Context app = getApplicationContext();
        AppDialog progress = AppDialog.progress(this, "正在读取调休日历…");
        BackgroundWork.run(
                this,
                "calendar-import",
                () -> HolidayFiles.read(app, uri),
                file -> {
                    progress.dismiss();
                    busy = false;
                    confirmCalendarImport(file);
                },
                error -> {
                    progress.dismiss();
                    busy = false;
                    Ui.error(this, error);
                },
                ignored -> {});
    }

    private void confirmCalendarImport(JSONObject file) {
        new AppDialog.Builder(this)
                .setTitle("确认年度日历")
                .setSubtitle("更新前，再确认一下这份安排")
                .setMessage(
                        "导入 "
                                + file.optInt("year")
                                + " 年的 "
                                + file.optJSONArray("days").length()
                                + " 个调休日期，并据此重排工作日闹钟？请确认文件包含完整年度安排。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                        "导入",
                        (dialog, which) -> {
                            if (busy) return;
                            busy = true;
                            try {
                                AlarmController.importHolidays(this, file);
                                Ui.toast(this, "已导入并重新安排闹钟");
                            } catch (Exception e) {
                                Ui.error(this, e);
                            } finally {
                                busy = false;
                                render();
                            }
                        })
                .show();
    }

    private void permissions() {
        if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.allowed(this)) {
            open(
                    new Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 8);
            return;
        }
        open(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Ui.toast(this, "请在系统设置中搜索「安心闹钟」");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmController.refresh(this, System.currentTimeMillis(), false);
        AlarmReliability.sync(this);
        render();
        screenActive = true;
        screenClock.removeCallbacks(refreshClock);
        screenClock.postDelayed(refreshClock, 30000);
    }

    @Override protected void onPause() {
        screenActive = false;
        screenClock.removeCallbacks(refreshClock);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        screenClock.removeCallbacksAndMessages(null);
        AlarmRepository.preferences(this).unregisterOnSharedPreferenceChangeListener(changes);
        super.onDestroy();
    }
}
