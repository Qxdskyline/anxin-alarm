package cn.local.oneshotalarm;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

import java.time.*;
import java.util.*;

final class DateSelection {
    interface Result {
        void selected(TreeSet<String> dates);
    }

    private final Activity a;
    private final TreeSet<String> selected;
    private final Result result;
    private YearMonth month = YearMonth.now();
    private LinearLayout content;
    private Dialog dialog;

    DateSelection(Activity a, Set<String> dates, Result r) {
        this.a = a;
        selected = new TreeSet<>(dates);
        result = r;
        for (String d : selected)
            if (!LocalDate.parse(d).isBefore(LocalDate.now())) {
                month = YearMonth.from(LocalDate.parse(d));
                break;
            }
    }

    void show() {
        content = Ui.column(a);
        dialog = Ui.sheet(a, "选择响铃日期", "先批量选择，再点掉不需要响铃的几天", content);
        TextView cancel = dialog.getWindow().getDecorView().findViewWithTag("sheet-close");
        if (cancel != null) cancel.setText("取消");
        render();
        Ui.showSheet(a, dialog);
    }

    private void render() {
        content.removeAllViews();
        LinearLayout nav = Ui.row(a);
        View prev = Ui.iconButton(a, "prev", Ui.INK, Color.WHITE, 44),
                next = Ui.iconButton(a, "next", Ui.INK, Color.WHITE, 44);
        nav.addView(prev, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        TextView title =
                Ui.text(
                        a,
                        month.getYear() + " 年 " + month.getMonthValue() + " 月",
                        20,
                        Ui.INK,
                        true);
        title.setGravity(Gravity.CENTER);
        nav.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1));
        nav.addView(next, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        content.addView(nav);
        prev.setOnClickListener(
                v -> {
                    if (month.isAfter(YearMonth.of(2020, 1))) {
                        month = month.minusMonths(1);
                        render();
                    }
                });
        next.setOnClickListener(
                v -> {
                    if (month.isBefore(YearMonth.of(2100, 12))) {
                        month = month.plusMonths(1);
                        render();
                    }
                });
        title.setOnClickListener(v -> jump());
        LinearLayout actions = Ui.row(a);
        chip(actions, "全选", () -> apply(0));
        chip(actions, "仅工作日", () -> apply(1));
        chip(
                actions,
                "筛选",
                () ->
                        new AppDialog.Builder(a)
                                .setTitle("筛选本月已选日期")
                                .setSubtitle("只调整本月，其余月份的选择会保留")
                                .setItems(
                                        new String[] {"排除放假日（保留补班日）", "排除周六、周日", "清空本月"},
                                        (d, n) -> apply(n + 2))
                                .show());
        content.addView(actions);
        Ui.gap(content, 12);
        HolidayCalendar cal = AlarmRepository.holidays(a);
        boolean known = cal.known(month.getYear());
        LinearLayout week = Ui.row(a);
        for (String day : new String[] {"一", "二", "三", "四", "五", "六", "日"}) {
            TextView t = Ui.text(a, day, 12, Ui.MUTED, false);
            t.setGravity(Gravity.CENTER);
            week.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(a, 28), 1));
        }
        content.addView(week);
        int offset = month.atDay(1).getDayOfWeek().getValue() - 1;
        for (int row = 0; row < (offset + month.lengthOfMonth() + 6) / 7; row++) {
            LinearLayout cells = Ui.row(a);
            for (int col = 0; col < 7; col++) {
                int n = row * 7 + col - offset + 1;
                FrameLayout cell = new FrameLayout(a);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 49), 1);
                lp.setMargins(Ui.dp(a, 1), Ui.dp(a, 2), Ui.dp(a, 1), Ui.dp(a, 2));
                cells.addView(cell, lp);
                if (n < 1 || n > month.lengthOfMonth()) continue;
                LocalDate date = month.atDay(n);
                String key = date.toString();
                boolean on = selected.contains(key), past = date.isBefore(LocalDate.now());
                Boolean special = known ? cal.years.get(date.getYear()).get(key) : null;
                TextView t =
                        Ui.text(
                                a,
                                n + (special == null ? "" : special ? "\n班" : "\n休"),
                                15,
                                on ? Color.WHITE : past ? 0xFFC4C0B8 : Ui.INK,
                                on);
                t.setGravity(Gravity.CENTER);
                t.setIncludeFontPadding(false);
                cell.addView(t, new FrameLayout.LayoutParams(-1, -1));
                if (on) cell.setBackground(Ui.shape(Ui.ORANGE, 15, a));
                cell.setContentDescription(key + (on ? " 已选择" : " 未选择"));
                cell.setOnClickListener(
                        v -> {
                            if (selected.remove(key)) {
                                render();
                                return;
                            }
                            if (!past) {
                                selected.add(key);
                                render();
                            }
                        });
            }
            content.addView(cells);
        }
        Ui.gap(content, 8);
        Ui.addText(content, "已选 " + selected.size() + " 天 · 可跨月多选", 13, Ui.MUTED, false);
        if (!known) {
            TextView sync = Ui.text(a, "本年度调休尚未缓存，点此联网获取", 13, Ui.ORANGE, true);
            sync.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
            content.addView(sync);
            sync.setOnClickListener(v -> HolidaySync.update(a, month.getYear(), this::render));
        }
        TextView confirm = Ui.action(a, "使用这 " + selected.size() + " 个日期", true);
        content.addView(confirm, new LinearLayout.LayoutParams(-1, -2));
        confirm.setOnClickListener(
                v -> {
                    result.selected(new TreeSet<>(selected));
                    dialog.dismiss();
                });
    }

    private void chip(LinearLayout row, String label, Runnable action) {
        TextView t = Ui.action(a, label, false);
        t.setTextSize(13);
        t.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
        row.addView(t, p);
        t.setOnClickListener(v -> action.run());
    }

    private void apply(int operation) {
        HolidayCalendar cal = AlarmRepository.holidays(a);
        if ((operation == 1 || operation == 2) && !cal.known(month.getYear())) {
            Ui.toast(a, "请先联网获取本年度调休日历");
            return;
        }
        for (int n = 1; n <= month.lengthOfMonth(); n++) {
            LocalDate d = month.atDay(n);
            String key = d.toString();
            if (operation == 4) {
                selected.remove(key);
                continue;
            }
            if (d.isBefore(LocalDate.now())) continue;
            if (operation == 0) selected.add(key);
            else if (operation == 1) {
                if (cal.working(d)) selected.add(key);
                else selected.remove(key);
            } else if (operation == 2 && Boolean.FALSE.equals(cal.years.get(d.getYear()).get(key)))
                selected.remove(key);
            else if (operation == 3 && d.getDayOfWeek().getValue() >= 6) selected.remove(key);
        }
        render();
    }

    private void jump() {
        LinearLayout p = Ui.row(a);
        p.setGravity(Gravity.CENTER);
        p.setBackground(Ui.shape(Color.WHITE, 20, a));
        p.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        NumberPicker y = new NumberPicker(a), m = new NumberPicker(a);
        y.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        m.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        y.setMinValue(2020);
        y.setMaxValue(2100);
        y.setValue(month.getYear());
        m.setMinValue(1);
        m.setMaxValue(12);
        m.setValue(month.getMonthValue());
        p.addView(y, new LinearLayout.LayoutParams(Ui.dp(a, 120), Ui.dp(a, 160)));
        p.addView(m, new LinearLayout.LayoutParams(Ui.dp(a, 100), Ui.dp(a, 160)));
        new AppDialog.Builder(a)
                .setTitle("跳转月份")
                .setSubtitle("快速前往想要安排的月份")
                .setView(p)
                .setNegativeButton("取消", null)
                .setPositiveButton(
                        "前往",
                        (d, w) -> {
                            month = YearMonth.of(y.getValue(), m.getValue());
                            render();
                        })
                .show();
    }
}
