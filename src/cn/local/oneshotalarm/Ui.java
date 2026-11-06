package cn.local.oneshotalarm;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG = 0xFFF7F6F2,
            INK = 0xFF252627,
            MUTED = 0xFF8A8986,
            ORANGE = 0xFFEC692D,
            SOFT = 0xFFFFE9DD,
            DARK = 0xFF181F2B;

    static int dp(Context c, float n) {
        return (int) (n * c.getResources().getDisplayMetrics().density + .5f);
    }

    static GradientDrawable shape(int color, float radius, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        return g;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static TextView text(Context c, String s, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setFontFeatureSettings("tnum");
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    static void addText(LinearLayout l, String s, int size, int color, boolean bold) {
        TextView t = text(l.getContext(), s, size, color, bold);
        t.setPadding(0, dp(l.getContext(), 4), 0, dp(l.getContext(), 4));
        l.addView(t);
    }

    static void gap(LinearLayout l, int n) {
        l.addView(
                new Space(l.getContext()), new LinearLayout.LayoutParams(1, dp(l.getContext(), n)));
    }

    static LinearLayout card(LinearLayout parent) {
        Context c = parent.getContext();
        LinearLayout box = column(c);
        box.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 18));
        box.setBackground(shape(Color.WHITE, 26, c));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(c, 14);
        parent.addView(box, p);
        return box;
    }

    static TextView action(Context c, String text, boolean primary) {
        TextView t = Ui.text(c, text, 16, primary ? Color.WHITE : ORANGE, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 50));
        t.setPadding(dp(c, 18), dp(c, 12), dp(c, 18), dp(c, 12));
        t.setBackground(shape(primary ? ORANGE : SOFT, 25, c));
        t.setClickable(true);
        return t;
    }

    static TextView option(
            LinearLayout parent, String title, String detail, View.OnClickListener click) {
        Context c = parent.getContext();
        LinearLayout row = row(c), labels = column(c);
        row.setPadding(0, dp(c, 14), 0, dp(c, 14));
        TextView t = text(c, title.replace("›", "").trim(), 16, INK, true);
        labels.addView(t);
        if (!detail.isEmpty()) {
            TextView sub = text(c, detail, 13, MUTED, false);
            sub.setPadding(0, dp(c, 6), dp(c, 8), 0);
            sub.setLineSpacing(dp(c, 3), 1);
            labels.addView(sub);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(icon(c, "next", MUTED), new LinearLayout.LayoutParams(dp(c, 22), dp(c, 22)));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        row.setOnClickListener(click);
        return t;
    }

    static View icon(Context c, String kind, int color) {
        return new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float x = getWidth() / 2f,
                        y = getHeight() / 2f,
                        u = Math.min(getWidth(), getHeight()) / 24f;
                p.setColor(color);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.1f * u);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                if (kind.equals("plus")) {
                    canvas.drawLine(x - 7.5f * u, y, x + 7.5f * u, y, p);
                    canvas.drawLine(x, y - 7.5f * u, x, y + 7.5f * u, p);
                } else if (kind.equals("close")) {
                    canvas.drawLine(x - 4 * u, y - 4 * u, x + 4 * u, y + 4 * u, p);
                    canvas.drawLine(x - 4 * u, y + 4 * u, x + 4 * u, y - 4 * u, p);
                } else if (kind.equals("settings")) {
                    canvas.drawCircle(x, y, 6 * u, p);
                    canvas.drawCircle(x, y, 2 * u, p);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        canvas.drawLine(
                                x + (float) Math.cos(a) * 7 * u,
                                y + (float) Math.sin(a) * 7 * u,
                                x + (float) Math.cos(a) * 9 * u,
                                y + (float) Math.sin(a) * 9 * u,
                                p);
                    }
                } else {
                    float d = kind.equals("prev") ? -1 : 1;
                    Path path = new Path();
                    path.moveTo(x - d * 2 * u, y - 5 * u);
                    path.lineTo(x + d * 3 * u, y);
                    path.lineTo(x - d * 2 * u, y + 5 * u);
                    canvas.drawPath(path, p);
                }
            }
        };
    }

    static FrameLayout iconButton(Context c, String kind, int color, int bg, int size) {
        FrameLayout frame = new FrameLayout(c);
        frame.setBackground(shape(bg, size / 2f, c));
        frame.setMinimumWidth(dp(c, size));
        frame.setMinimumHeight(dp(c, size));
        frame.setClickable(true);
        frame.addView(
                icon(c, kind, color),
                new FrameLayout.LayoutParams(dp(c, 28), dp(c, 28), Gravity.CENTER));
        return frame;
    }

    static Dialog sheet(Activity activity, String title, String subtitle, LinearLayout content) {
        return new AppDialog.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setView(content)
                .setNegativeButton("完成", null)
                .create();
    }

    static void showSheet(Activity activity, Dialog dialog) {
        dialog.show();
    }

    static void pressFeedback(View view, View surface) {
        view.setClickable(true);
        surface.setForeground(
                new RippleDrawable(
                        android.content.res.ColorStateList.valueOf(0x22EC692D),
                        null,
                        shape(Color.WHITE, 26, view.getContext())));
        view.setOnTouchListener(
                (v, event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            surface.animate().cancel();
                            surface.animate().scaleX(.985f).scaleY(.985f).setDuration(90).start();
                            surface.getForeground().setHotspot(event.getX(), event.getY());
                            surface.getForeground()
                                    .setState(
                                            new int[] {
                                                android.R.attr.state_enabled,
                                                android.R.attr.state_pressed
                                            });
                            break;
                        case MotionEvent.ACTION_MOVE:
                            if (event.getX() < 0
                                    || event.getX() > v.getWidth()
                                    || event.getY() < 0
                                    || event.getY() > v.getHeight()) {
                                surface.animate().scaleX(1).scaleY(1).setDuration(130).start();
                                surface.getForeground().setState(new int[0]);
                            }
                            break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:
                            surface.animate().scaleX(1).scaleY(1).setDuration(130).start();
                            surface.getForeground().setState(new int[0]);
                            break;
                    }
                    return false;
                });
    }

    static void confirmDelete(Activity activity, String name, Runnable delete) {
        new AppDialog.Builder(activity)
                .setTitle("删除这个闹钟？")
                .setSubtitle(name)
                .setMessage("这个闹钟和本轮剩余重播会一起移除。")
                .setDestructive()
                .setNegativeButton("保留闹钟", null)
                .setPositiveButton("删除闹钟", (dialog, which) -> delete.run())
                .show();
    }

    static void divider(LinearLayout l) {
        View v = new View(l.getContext());
        v.setBackgroundColor(0xFFF1F0ED);
        l.addView(v, new LinearLayout.LayoutParams(-1, dp(l.getContext(), 1)));
    }

    static ScrollView scroll(Context c, LinearLayout content) {
        ScrollView s = new ScrollView(c);
        s.setClipToPadding(false);
        s.setFillViewport(true);
        s.addView(content);
        return s;
    }

    static void insets(View view) {
        final int left = view.getPaddingLeft(),
                top = view.getPaddingTop(),
                right = view.getPaddingRight(),
                bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener(
                (v, i) -> {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Insets b =
                                i.getInsets(
                                        WindowInsets.Type.systemBars()
                                                | WindowInsets.Type.displayCutout());
                        v.setPadding(
                                left + b.left, top + b.top, right + b.right, bottom + b.bottom);
                    } else
                        v.setPadding(
                                left + i.getSystemWindowInsetLeft(),
                                top + i.getSystemWindowInsetTop(),
                                right + i.getSystemWindowInsetRight(),
                                bottom + i.getSystemWindowInsetBottom());
                    return i;
                });
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    static void error(Context c, Exception e) {
        new AppDialog.Builder(c)
                .setTitle("暂时无法完成")
                .setSubtitle("调整后可以再试一次")
                .setMessage(e.getMessage() == null ? "请重试" : e.getMessage())
                .setPositiveButton("知道了", null)
                .show();
    }
}
