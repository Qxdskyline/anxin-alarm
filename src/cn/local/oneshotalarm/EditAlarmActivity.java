package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.time.*;
import java.util.*;

public final class EditAlarmActivity extends Activity {
    private Alarm alarm;
    private EditText name;
    private NumberPicker hours, minutes;
    private LinearLayout options;
    private TextView save;
    private boolean busy, picking, saved;
    private final ArrayList<String> imported = new ArrayList<>();
    private final ArrayList<String> originalAudio = new ArrayList<>();
    private int persistedId;
    private static final String[] MODES = {"ONCE", "DAILY", "WORK", "REST", "WEEKLY", "DATES"};
    private static final String[] MODE_NAMES = {
        "仅一次", "每天", "中国大陆工作日（含调休）", "中国大陆休息日（含调休）", "每周自定义", "指定多个日期"
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        int id = getIntent().getIntExtra("id", 0);
        alarm = id == 0 ? null : AlarmRepository.get(this, id);
        persistedId = alarm == null ? 0 : id;
        if (alarm == null) {
            alarm = new Alarm(AlarmRepository.allocate(this));
            alarm.plan.date = LocalDate.now().plusDays(1).toString();
        } else alarm = alarm.copy();
        for (AlarmPlan.Track track : alarm.plan.tracks()) originalAudio.add(track.file);
        if (state != null && state.containsKey("draft"))
            try {
                alarm = AlarmJson.read(new org.json.JSONObject(state.getString("draft")));
                ArrayList<String> files = state.getStringArrayList("imports");
                if (files != null) imported.addAll(files);
            } catch (Exception ignored) {
            }
        if (alarm.plan.durationMs == 0 && !alarm.plan.audio.isEmpty()) {
            readDuration();
        }
        LinearLayout page = Ui.column(this);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 10), Ui.dp(this, 24), Ui.dp(this, 36));
        LinearLayout header = Ui.row(this);
        TextView back = Ui.text(this, "取消", 16, Ui.MUTED, false);
        back.setPadding(0, Ui.dp(this, 15), Ui.dp(this, 10), Ui.dp(this, 15));
        header.addView(back);
        back.setOnClickListener(
                v -> {
                    if (!busy) finish();
                });
        TextView title = Ui.text(this, persistedId == 0 ? "新建闹钟" : "编辑闹钟", 19, Ui.INK, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        save = Ui.text(this, "保存", 16, Ui.ORANGE, true);
        save.setPadding(Ui.dp(this, 10), Ui.dp(this, 15), 0, Ui.dp(this, 15));
        header.addView(save);
        save.setOnClickListener(v -> save());
        page.addView(header);
        Ui.gap(page, 20);
        TextView hint = Ui.text(this, "设定一个值得期待的时刻", 14, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint);
        Ui.gap(page, 8);
        LinearLayout wheels = Ui.row(this);
        wheels.setGravity(Gravity.CENTER);
        hours = picker(0, 23, alarm.plan.hour);
        minutes = picker(0, 59, alarm.plan.minute);
        wheels.addView(hours, new LinearLayout.LayoutParams(Ui.dp(this, 115), Ui.dp(this, 190)));
        TextView colon = Ui.text(this, ":", 42, Ui.ORANGE, false);
        colon.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 9));
        wheels.addView(colon);
        wheels.addView(minutes, new LinearLayout.LayoutParams(Ui.dp(this, 115), Ui.dp(this, 190)));
        page.addView(wheels);
        hours.setOnValueChangedListener((p, o, n) -> alarm.plan.hour = n);
        minutes.setOnValueChangedListener((p, o, n) -> alarm.plan.minute = n);
        Ui.gap(page, 20);
        LinearLayout nameCard = Ui.card(page);
        name = new EditText(this);
        name.setText(alarm.plan.name);
        name.setHint("为闹钟起个名字，比如：早安");
        name.setTextSize(17);
        name.setSingleLine();
        name.setFilters(
                new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(40)});
        name.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.SOFT));
        nameCard.addView(name, new LinearLayout.LayoutParams(-1, -2));
        options = Ui.column(this);
        page.addView(options);
        renderOptions();
        if (persistedId != 0) {
            TextView test = Ui.action(this, "30 秒后试响", false);
            page.addView(test);
            test.setOnClickListener(
                    v -> {
                        if (busy || picking) return;
                        try {
                            validate();
                            if (!AlarmScheduler.allowed(this)) {
                                Ui.toast(this, "请在首页设置中允许精确闹钟");
                                return;
                            }
                            AlarmController.test(this, alarm);
                            Ui.toast(this, "30 秒后试响；不改变原闹钟日期，也不执行重播");
                        } catch (Exception e) {
                            Ui.error(this, e);
                        }
                    });
            Ui.gap(page, 12);
            TextView delete = Ui.text(this, "删除闹钟", 16, 0xFFC24F3B, true);
            delete.setGravity(Gravity.CENTER);
            delete.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
            page.addView(delete);
            delete.setOnClickListener(
                    v -> {
                        if (busy || picking) return;
                        Ui.confirmDelete(
                                this,
                                alarm.title(),
                                () -> {
                                    if (busy) return;
                                    busy = true;
                                    try {
                                        AlarmController.delete(this, alarm.id);
                                        finish();
                                    } catch (Exception e) {
                                        busy = false;
                                        Ui.error(this, e);
                                    }
                                });
                    });
        }
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG);
        Ui.insets(frame);
        frame.addView(Ui.scroll(this, page));
        setContentView(frame);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
    }

    private NumberPicker picker(int min, int max, int value) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(min);
        p.setMaxValue(max);
        p.setValue(value);
        p.setWrapSelectorWheel(true);
        p.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        p.setFormatter(n -> String.format(Locale.CHINA, "%02d", n));
        if (Build.VERSION.SDK_INT >= 29) {
            p.setTextSize(Ui.dp(this, 42));
            p.setTextColor(Ui.INK);
            p.setSelectionDividerHeight(0);
        }
        return p;
    }

    private boolean ready() {
        return !busy && !picking;
    }

    private void renderOptions() {
        if (options == null) return;
        options.removeAllViews();
        save.setEnabled(ready());
        save.setAlpha(ready() ? 1 : .35f);
        LinearLayout settings = Ui.card(options);
        Ui.option(
                settings,
                "重复日期",
                AlarmText.description(alarm),
                v -> {
                    if (ready()) chooseRepeat();
                });
        Ui.divider(settings);
        if (alarm.plan.mode.equals("ONCE")) {
            Ui.option(
                    settings,
                    "日期",
                    alarm.plan.date,
                    v -> {
                        if (ready())
                            datePicker(
                                    LocalDate.parse(alarm.plan.date),
                                    d -> {
                                        alarm.plan.date = d;
                                        renderOptions();
                                    });
                    });
            Ui.divider(settings);
        }
        if (alarm.plan.mode.equals("WEEKLY")) {
            Ui.option(
                    settings,
                    "星期",
                    AlarmText.description(alarm),
                    v -> {
                        if (ready()) weekdays();
                    });
            Ui.divider(settings);
        }
        if (alarm.plan.mode.equals("DATES")) {
            Ui.option(
                    settings,
                    "选择日期",
                    alarm.plan.dates.isEmpty() ? "尚未选择" : alarm.plan.dates.size() + " 天 · 点击查看和调整",
                    v -> {
                        if (ready()) dates();
                    });
            Ui.divider(settings);
        }
        Ui.option(
                settings,
                "录音",
                busy
                        ? "正在导入并检查音频…"
                        : audioSummary(),
                v -> {
                    if (ready()) chooseAudio();
                });
        Ui.divider(settings);
        Ui.option(
                settings,
                "单次播放",
                AlarmText.playDescription(alarm),
                v -> {
                    if (ready()) playMode();
                });
        Ui.divider(settings);
        Ui.option(
                settings,
                "贪睡重播",
                alarm.plan.repeats == 0
                        ? "关闭 · 每次闹钟只播放一轮"
                        : "每隔 "
                                + AlarmText.duration(alarm.plan.intervalSeconds)
                                + "开始，额外重播 "
                                + alarm.plan.repeats
                                + " 次",
                v -> {
                    if (ready()) replay();
                });
        if (alarm.plan.repeats > 0)
            Ui.addText(
                    settings,
                    "含首次共 " + (alarm.plan.repeats + 1) + " 次。间隔从开始播放计算；× 会结束本轮剩余重播。",
                    12,
                    Ui.MUTED,
                    false);
        if (alarm.plan.mode.equals("WORK") || alarm.plan.mode.equals("REST"))
            Ui.addText(
                    options,
                    "调休已缓存 " + AlarmRepository.holidays(this).years.keySet() + " 年，可在安心响铃中联网更新。",
                    12,
                    Ui.MUTED,
                    false);
        Ui.addText(options, "「录音播放一遍」不限制日期重复；每次触发都只完整播放一遍。", 12, Ui.MUTED, false);
        Ui.gap(options, 16);
    }

    private void chooseRepeat() {
        int checked = Arrays.asList(MODES).indexOf(alarm.plan.mode);
        new AppDialog.Builder(this)
                .setTitle("哪天响铃")
                .setSubtitle("按你的生活节奏安排提醒")
                .setItemDescriptions(
                        new String[] {
                            "选定日期，响过即结束",
                            "每天按时提醒",
                            "放假跳过，调休补班照常响",
                            "只在法定休息日提醒",
                            "自由组合每周的响铃日",
                            "在月历中跨月选择多个日期"
                        })
                .setSingleChoiceItems(
                        MODE_NAMES,
                        checked,
                        (d, n) -> {
                            alarm.plan.mode = MODES[n];
                            if (alarm.plan.mode.equals("ONCE") && alarm.plan.date.isEmpty())
                                alarm.plan.date = LocalDate.now().plusDays(1).toString();
                            d.dismiss();
                            renderOptions();
                            if (n == 4) weekdays();
                            if (n == 5) dates();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void weekdays() {
        boolean[] chosen = new boolean[7];
        for (int i = 0; i < 7; i++) chosen[i] = (alarm.plan.weekdays & (1 << i)) != 0;
        new AppDialog.Builder(this)
                .setTitle("每周自定义")
                .setSubtitle("可以选择多个星期，完成后统一保存")
                .setMultiChoiceItems(
                        new String[] {"周一", "周二", "周三", "周四", "周五", "周六", "周日"},
                        chosen,
                        (d, n, on) -> chosen[n] = on)
                .setNegativeButton("取消", null)
                .setPositiveButton(
                        "完成",
                        (d, w) -> {
                            int mask = 0;
                            for (int i = 0; i < 7; i++) if (chosen[i]) mask |= 1 << i;
                            alarm.plan.weekdays = mask;
                            renderOptions();
                        })
                .show();
    }

    private interface DateChosen {
        void choose(String date);
    }

    private void datePicker(LocalDate initial, DateChosen callback) {
        DatePicker picker = new DatePicker(this, null, 0, R.style.AlarmDatePicker);
        picker.setDescendantFocusability(DatePicker.FOCUS_BLOCK_DESCENDANTS);
        picker.init(initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth(), null);
        picker.setMinDate(System.currentTimeMillis() - 1000);
        LinearLayout panel = Ui.column(this);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        panel.setBackground(Ui.shape(android.graphics.Color.WHITE, 20, this));
        panel.addView(picker, new LinearLayout.LayoutParams(-2, -2));
        new AppDialog.Builder(this)
                .setTitle("选择响铃日期")
                .setSubtitle("为这一次提醒选一个日子")
                .setView(panel)
                .setNegativeButton("取消", null)
                .setPositiveButton(
                        "使用这个日期",
                        (dialog, which) ->
                                callback.choose(
                                        LocalDate.of(
                                                        picker.getYear(),
                                                        picker.getMonth() + 1,
                                                        picker.getDayOfMonth())
                                                .toString()))
                .show();
    }

    private void dates() {
        new DateSelection(
                        this,
                        alarm.plan.dates,
                        dates -> {
                            alarm.plan.dates.clear();
                            alarm.plan.dates.addAll(dates);
                            renderOptions();
                        })
                .show();
    }

    private void playMode() {
        new AppDialog.Builder(this)
                .setTitle("每次如何播放")
                .setSubtitle("每个响铃日期都会按这个方式播放")
                .setItemDescriptions(new String[] {"完整播放录音后停止", "在设定时长内重复播放录音"})
                .setSingleChoiceItems(
                        new String[] {"录音播放一遍", "循环播放指定时长"},
                        alarm.plan.loop ? 1 : 0,
                        (d, n) -> {
                            d.dismiss();
                            if (n == 0) {
                                alarm.plan.loop = false;
                                renderOptions();
                            } else durationDialog();
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    private void durationDialog() {
        String[] choices = {"1 分钟", "2 分钟", "3 分钟", "5 分钟", "10 分钟", "30 分钟", "自定义时长"};
        int[] seconds = {60, 120, 180, 300, 600, 1800};
        new AppDialog.Builder(this)
                .setTitle("循环播放多久")
                .setSubtitle("选择常用时长，也可以精确到秒")
                .setItems(
                        choices,
                        (d, n) -> {
                            if (n < 6) {
                                alarm.plan.loop = true;
                                alarm.plan.loopSeconds = seconds[n];
                                renderOptions();
                            } else customDuration();
                        })
                .setNegativeButton("返回", (d, w) -> playMode())
                .show();
    }

    private EditText number(LinearLayout parent, String label, String value) {
        return AppDialog.numberField(parent, label, value);
    }

    private void customDuration() {
        LinearLayout p = Ui.column(this);
        EditText mins = number(p, "分钟", String.valueOf(alarm.plan.loopSeconds / 60)),
                secs = number(p, "秒（0–59）", String.valueOf(alarm.plan.loopSeconds % 60));
        AppDialog dialog =
                new AppDialog.Builder(this)
                        .setTitle("自定义播放时长")
                        .setSubtitle("精确到秒，最长 180 分钟")
                        .setView(p)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(-1)
                                .setOnClickListener(
                                        v -> {
                                            try {
                                                int m = integer(mins, 0), ss = integer(secs, 0);
                                                if (m < 0 || m > 180 || ss < 0 || ss > 59)
                                                    throw new IllegalArgumentException();
                                                int s = m * 60 + ss;
                                                if (s < 1 || s > 10800)
                                                    throw new IllegalArgumentException();
                                                alarm.plan.loop = true;
                                                alarm.plan.loopSeconds = s;
                                                dialog.dismiss();
                                                renderOptions();
                                            } catch (Exception e) {
                                                Ui.toast(this, "时长无效，请输入 1 秒至 180 分钟");
                                            }
                                        }));
        dialog.show();
    }

    private int integer(EditText e, int fallback) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? fallback : Integer.parseInt(s);
    }

    private void replay() {
        LinearLayout p = Ui.column(this);
        Ui.addText(p, "两次开始之间的间隔，例如：响 2 分钟、间隔 5 分钟，会安静 3 分钟再响。", 14, Ui.MUTED, false);
        EditText mins = number(p, "开始间隔：分钟", String.valueOf(alarm.plan.intervalSeconds / 60));
        EditText secs = number(p, "开始间隔：秒（0–59）", String.valueOf(alarm.plan.intervalSeconds % 60));
        Ui.addText(p, "额外重播次数（0 代表关闭，最多 100 次）", 14, Ui.INK, false);
        EditText times = number(p, "重播次数", String.valueOf(alarm.plan.repeats));
        AppDialog dialog =
                new AppDialog.Builder(this)
                        .setTitle("贪睡重播")
                        .setSubtitle("设置开始间隔和额外播放次数")
                        .setView(p)
                        .setNegativeButton("取消", null)
                        .setNeutralButton(
                                "关闭重播",
                                (d, w) -> {
                                    alarm.plan.repeats = 0;
                                    renderOptions();
                                })
                        .setPositiveButton("完成", null)
                        .create();
        dialog.setOnShowListener(
                d ->
                        dialog.getButton(-1)
                                .setOnClickListener(
                                        v -> {
                                            try {
                                                int m = integer(mins, 0),
                                                        s = integer(secs, 0),
                                                        n = integer(times, 0);
                                                if (m < 0 || m > 1440 || s < 0 || s > 59)
                                                    throw new IllegalArgumentException(
                                                            "间隔分钟无效，秒数需为 0–59");
                                                Schedule.validate(
                                                        alarm.plan.hour,
                                                        alarm.plan.minute,
                                                        alarm.plan.loop,
                                                        alarm.plan.loopSeconds,
                                                        alarm.plan.durationMs,
                                                        m * 60 + s,
                                                        n);
                                                alarm.plan.intervalSeconds = m * 60 + s;
                                                alarm.plan.repeats = n;
                                                dialog.dismiss();
                                                renderOptions();
                                            } catch (Exception e) {
                                                Ui.toast(
                                                        this,
                                                        e.getMessage() == null
                                                                ? "请输入有效数字"
                                                                : e.getMessage());
                                            }
                                        }));
        dialog.show();
    }

    private String audioSummary() {
        List<AlarmPlan.Track> tracks = alarm.plan.tracks();
        if (tracks.isEmpty()) return "选择手机里的录音";
        if (tracks.size() == 1) return tracks.get(0).name;
        return tracks.size() + " 段录音 · " + (alarm.plan.shuffleAudio ? "随机顺序" : "自定义顺序");
    }

    private void chooseAudio() {
        List<AlarmPlan.Track> tracks = alarm.plan.tracks();
        ArrayList<String> labels = new ArrayList<>();
        labels.add("从手机添加录音（支持多选）");
        labels.add("添加已保存的录音");
        labels.add("播放顺序 · " + (alarm.plan.shuffleAudio ? "随机" : "自定义"));
        for (int i = 0; i < tracks.size(); i++)
            labels.add((i + 1) + ". " + tracks.get(i).name);
        new AppDialog.Builder(this).setTitle("响铃录音")
                .setSubtitle("点选已添加的录音可调整位置或移除")
                .setItems(labels.toArray(new String[0]), (d, n) -> {
                    if (n == 0) picker();
                    else if (n == 1) chooseSavedAudio();
                    else if (n == 2) chooseAudioOrder();
                    else editTrack(n - 3);
                }).setNegativeButton("完成", null).show();
    }

    private void chooseAudioOrder() {
        new AppDialog.Builder(this).setTitle("播放顺序")
                .setItems(new String[]{"按列表顺序", "每轮随机打乱"}, (d, n) -> {
                    alarm.plan.shuffleAudio = n == 1;
                    renderOptions();
                    chooseAudio();
                }).show();
    }

    private void editTrack(int index) {
        List<AlarmPlan.Track> tracks = alarm.plan.tracks();
        ArrayList<String> actions = new ArrayList<>();
        if (index > 0) actions.add("向上移动");
        if (index + 1 < tracks.size()) actions.add("向下移动");
        actions.add("移除录音");
        new AppDialog.Builder(this).setTitle(tracks.get(index).name)
                .setItems(actions.toArray(new String[0]), (d, n) -> {
                    alarm.plan.editTracks();
                    if (actions.get(n).equals("移除录音")) alarm.plan.playlist.remove(index);
                    else Collections.swap(alarm.plan.playlist, index,
                            index + (actions.get(n).equals("向上移动") ? -1 : 1));
                    alarm.plan.updateDuration();
                    renderOptions();
                    chooseAudio();
                }).setNegativeButton("返回", (d, n) -> chooseAudio()).show();
    }

    private void chooseSavedAudio() {
        LinkedHashMap<String, AlarmPlan.Track> available = new LinkedHashMap<>();
        for (Alarm a : AlarmRepository.all(this))
            for (AlarmPlan.Track track : a.plan.tracks())
                if (new File(getFilesDir(), track.file).isFile()) available.put(track.file, track);
        for (AlarmPlan.Track track : alarm.plan.tracks()) available.remove(track.file);
        if (available.isEmpty()) {
            Ui.toast(this, "没有其他已保存的录音");
            chooseAudio();
            return;
        }
        ArrayList<AlarmPlan.Track> tracks = new ArrayList<>(available.values());
        String[] labels = new String[tracks.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = tracks.get(i).name;
        new AppDialog.Builder(this).setTitle("添加已保存的录音")
                .setItems(labels, (d, n) -> {
                    addTracks(Collections.singletonList(tracks.get(n)));
                    chooseAudio();
                }).setNegativeButton("返回", (d, n) -> chooseAudio()).show();
    }

    private void addTracks(List<AlarmPlan.Track> tracks) {
        alarm.plan.editTracks();
        alarm.plan.playlist.addAll(tracks);
        alarm.plan.updateDuration();
        renderOptions();
    }

    private void readDuration() {
        alarm.plan.durationMs = AudioImporter.readDuration(AudioFiles.file(this, alarm));
    }

    private void picker() {
        if (!ready()) return;
        picking = true;
        renderOptions();
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .setType("audio/*")
                            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            .addCategory(Intent.CATEGORY_OPENABLE),
                    7);
        } catch (ActivityNotFoundException e) {
            picking = false;
            renderOptions();
            Ui.error(this, e);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 7) return;
        picking = false;
        if (result != RESULT_OK || data == null) {
            renderOptions();
            return;
        }
        if (busy) return;
        LinkedHashSet<Uri> sources = new LinkedHashSet<>();
        ClipData clips = data.getClipData();
        if (clips != null)
            for (int i = 0; i < clips.getItemCount(); i++) {
                Uri uri = clips.getItemAt(i).getUri();
                if (uri != null) sources.add(uri);
            }
        if (data.getData() != null) sources.add(data.getData());
        if (sources.isEmpty()) {
            renderOptions();
            return;
        }
        busy = true;
        renderOptions();
        Context app = getApplicationContext();
        AppDialog progress = AppDialog.progress(this, "正在保存并检查录音…");
        BackgroundWork.run(
                this,
                "audio-import",
                () -> {
                    List<AlarmPlan.Track> tracks = new ArrayList<>();
                    try {
                        for (Uri uri : sources) {
                            AudioImporter.Result item = AudioImporter.importAudio(app, uri);
                            tracks.add(new AlarmPlan.Track(item.fileName, item.displayName, item.durationMs));
                        }
                        return tracks;
                    } catch (Exception error) {
                        for (AlarmPlan.Track track : tracks) AudioFiles.removeIfUnused(app, track.file);
                        throw error;
                    }
                },
                importedAudio -> {
                    progress.dismiss();
                    busy = false;
                    for (AlarmPlan.Track track : importedAudio) imported.add(track.file);
                    addTracks(importedAudio);
                    chooseAudio();
                },
                error -> {
                    progress.dismiss();
                    busy = false;
                    Ui.error(this, error);
                    renderOptions();
                },
                importedAudio -> {
                    for (AlarmPlan.Track track : importedAudio) AudioFiles.removeIfUnused(app, track.file);
                });
    }

    private void validate() {
        alarm.plan.name = name.getText().toString().trim();
        alarm.plan.hour = hours.getValue();
        alarm.plan.minute = minutes.getValue();
        alarm.plan.validate();
        if (!AudioFiles.available(this, alarm.plan)) {
            throw new IllegalArgumentException("请选择一段录音");
        }
    }

    private void save() {
        if (!ready()) return;
        try {
            validate();
            if (!AlarmScheduler.allowed(this)) {
                if (Build.VERSION.SDK_INT >= 31)
                    startActivity(
                            new Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName())));
                return;
            }
            long next =
                    alarm.plan.nextOccurrence(
                            System.currentTimeMillis(),
                            ZoneId.systemDefault(),
                            AlarmRepository.holidays(this));
            if (next == 0 && !(alarm.plan.mode.equals("WORK") || alarm.plan.mode.equals("REST")))
                throw new IllegalArgumentException("选择的时间已过，请调整日期或时间");
            if (getSystemService(AudioManager.class).getStreamVolume(AudioManager.STREAM_ALARM)
                    == 0) {
                new AppDialog.Builder(this)
                        .setTitle("闹钟音量为零")
                        .setSubtitle("调高音量后即可保存")
                        .setMessage("请先调高系统闹钟音量，再保存。可使用音量键或首页设置。 ")
                        .setPositiveButton("知道了", null)
                        .show();
                return;
            }
            busy = true;
            renderOptions();
            alarm.plan.enabled = true;
            AlarmController.save(this, alarm);
            saved = true;
            for (String file : originalAudio) AudioFiles.removeIfUnused(this, file);
            for (String file : imported) AudioFiles.removeIfUnused(this, file);
            Ui.toast(this, next == 0 ? "已保存，请补充调休日历后安排响铃" : "已保存，声音会按时赴约");
            finish();
        } catch (Exception e) {
            busy = false;
            renderOptions();
            Ui.error(this, e);
        }
    }

    @Override
    public void onBackPressed() {
        if (!busy) super.onBackPressed();
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        if (alarm != null) {
            if (name != null) alarm.plan.name = name.getText().toString();
            b.putString("draft", AlarmJson.write(alarm).toString());
            b.putStringArrayList("imports", imported);
        }
        super.onSaveInstanceState(b);
    }

    @Override
    protected void onDestroy() {
        if (!saved && !isChangingConfigurations())
            for (String file : imported) AudioFiles.removeIfUnused(this, file);
        super.onDestroy();
    }
}
