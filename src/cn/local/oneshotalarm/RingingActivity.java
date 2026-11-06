package cn.local.oneshotalarm;

import android.animation.ObjectAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.time.*;
import java.time.format.DateTimeFormatter;

public final class RingingActivity extends Activity {
    private int id;
    private String session;
    private LinearLayout body;
    private ObjectAnimator pulse;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes =
            (p, k) -> {
                if ("v2".equals(k)) runOnUiThread(this::render);
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else
            getWindow()
                    .addFlags(
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AlarmRepository.preferences(this).registerOnSharedPreferenceChangeListener(changes);
        read();
    }

    @Override
    public void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        read();
    }

    private void read() {
        id = getIntent().getIntExtra("id", 0);
        session = getIntent().getStringExtra("session");
        render();
    }

    private void render() {
        Alarm a = AlarmRepository.get(this, id);
        if (a == null
                || !a.execution.isActive()
                || !a.execution.session().equals(session)
                || (!a.execution.isPending()
                        && a.execution.retryAt() > System.currentTimeMillis()
                        && !PlaybackSessions.contains(id, session))) {
            finish();
            return;
        }
        if (body != null) {
            TextView details = body.findViewWithTag("details");
            if (details != null)
                details.setText(
                        "第 "
                                + (a.execution.round() + 1)
                                + " / "
                                + (a.plan.repeats + 1)
                                + " 次 · "
                                + AlarmText.playDescription(a));
            return;
        }
        LinearLayout page = Ui.column(this);
        page.setGravity(Gravity.CENTER);
        page.setPadding(Ui.dp(this, 28), Ui.dp(this, 36), Ui.dp(this, 28), Ui.dp(this, 36));
        GradientDrawable bg =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[] {0xFF29374A, Ui.DARK, 0xFF30282E});
        page.setBackground(bg);
        Ui.insets(page);
        TextView clock =
                Ui.text(
                        this,
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                        68,
                        Color.WHITE,
                        false);
        clock.setTypeface(Typeface.create("sans-serif-light", 0));
        page.addView(clock);
        Ui.gap(page, 12);
        Ui.addText(page, a.title(), 25, Color.WHITE, true);
        Ui.gap(page, 40);
        TextView orb = Ui.text(this, "♫", 70, 0xFFFFC4A2, false);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(Ui.shape(0x223C7D9E, 100, this));
        page.addView(orb, new LinearLayout.LayoutParams(Ui.dp(this, 172), Ui.dp(this, 172)));
        pulse = ObjectAnimator.ofFloat(orb, "alpha", .5f, 1f);
        pulse.setDuration(1500);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
        Ui.gap(page, 35);
        TextView detail =
                Ui.text(
                        this,
                        "第 "
                                + (a.execution.round() + 1)
                                + " / "
                                + (a.plan.repeats + 1)
                                + " 次 · "
                                + AlarmText.playDescription(a),
                        15,
                        0xFFCAD0D9,
                        false);
        detail.setTag("details");
        page.addView(detail);
        Ui.gap(page, 40);
        TextView stop = Ui.action(this, "×  结束本轮提醒", true);
        page.addView(stop, new LinearLayout.LayoutParams(-1, -2));
        stop.setOnClickListener(
                v -> {
                    AlarmController.close(this, a.id, a.execution.session(), "已结束本轮提醒");
                    finish();
                });
        Ui.gap(page, 16);
        TextView hide = Ui.text(this, "收起，继续播放", 15, 0xFFCAD0D9, false);
        hide.setPadding(Ui.dp(this, 25), Ui.dp(this, 15), Ui.dp(this, 25), Ui.dp(this, 15));
        page.addView(hide);
        hide.setOnClickListener(v -> finish());
        Ui.gap(page, 14);
        Ui.addText(page, "结束本轮不会影响下一个响铃日期", 12, 0xFF8F9BAA, false);
        body = page;
        setContentView(Ui.scroll(this, page));
    }

    @Override
    protected void onDestroy() {
        if (pulse != null) pulse.cancel();
        AlarmRepository.preferences(this).unregisterOnSharedPreferenceChangeListener(changes);
        super.onDestroy();
    }
}
