package cn.local.oneshotalarm;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** 应用内对话框共用的外壳、选项、输入和操作区。业务回调仍由调用方处理。 */
final class AppDialog extends Dialog {
    private static final int BORDER = 0xFFEAE6DF;
    private static final int DANGER = 0xFFC55342;
    private final Builder spec;
    private final Map<Integer, TextView> buttons = new HashMap<>();
    private final ArrayList<LinearLayout> choices = new ArrayList<>();
    private final ArrayList<CompoundButton> indicators = new ArrayList<>();
    private int selectedIndex;

    private AppDialog(Builder spec) {
        super(spec.context, R.style.SheetTheme);
        this.spec = spec;
        selectedIndex = spec.selectedIndex;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(!spec.busy);
        setCanceledOnTouchOutside(!spec.busy);
        buildContent();
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.38f);
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.setWindowAnimations(R.style.DialogAnimation);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) return;
        int availableWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        if (Build.VERSION.SDK_INT >= 30 && spec.context instanceof Activity) {
            android.view.WindowMetrics metrics =
                    ((Activity) spec.context).getWindowManager().getCurrentWindowMetrics();
            Rect bounds = metrics.getBounds();
            android.graphics.Insets bars =
                    metrics.getWindowInsets()
                            .getInsetsIgnoringVisibility(
                                    WindowInsets.Type.systemBars()
                                            | WindowInsets.Type.displayCutout());
            availableWidth = bounds.width() - bars.left - bars.right;
        }
        window.setLayout(
                Math.min(availableWidth - Ui.dp(getContext(), 32), Ui.dp(getContext(), 520)),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.y = Ui.dp(getContext(), 16);
        window.setAttributes(attributes);
    }

    TextView getButton(int which) {
        return buttons.get(which);
    }

    private void buildContent() {
        Context context = getContext();
        LinearLayout header = Ui.column(context);
        header.setPadding(
                Ui.dp(context, 22), Ui.dp(context, 12), Ui.dp(context, 22), Ui.dp(context, 18));
        View handle = new View(context);
        handle.setBackground(Ui.shape(0xFFDAD4CA, 3, context));
        LinearLayout.LayoutParams handleParams =
                new LinearLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = Ui.dp(context, 12);
        header.addView(handle, handleParams);
        LinearLayout heading = Ui.row(context);
        TextView eyebrow =
                Ui.text(
                        context,
                        spec.destructive ? "操作确认" : "安心闹钟",
                        12,
                        spec.destructive ? DANGER : Ui.ORANGE,
                        true);
        heading.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1));
        if (!spec.busy) {
            View close = Ui.iconButton(context, "close", Ui.MUTED, Color.WHITE, 40);
            close.setContentDescription("关闭弹窗");
            heading.addView(
                    close, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
            close.setOnClickListener(view -> cancel());
        }
        header.addView(heading);
        TextView title = Ui.text(context, spec.title, 24, Ui.INK, true);
        title.setLineSpacing(Ui.dp(context, 3), 1);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (!spec.subtitle.isEmpty()) {
            TextView subtitle = Ui.text(context, spec.subtitle, 13, Ui.MUTED, false);
            subtitle.setPadding(0, Ui.dp(context, 8), 0, 0);
            subtitle.setLineSpacing(Ui.dp(context, 4), 1);
            header.addView(subtitle);
        }

        LinearLayout content = Ui.column(context);
        content.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
        if (!spec.message.isEmpty()) {
            TextView message = Ui.text(context, spec.message, 15, Ui.MUTED, false);
            message.setLineSpacing(Ui.dp(context, 6), 1);
            message.setPadding(
                    Ui.dp(context, 18), Ui.dp(context, 16), Ui.dp(context, 18), Ui.dp(context, 16));
            message.setBackground(Ui.shape(Color.WHITE, 20, context));
            content.addView(message, new LinearLayout.LayoutParams(-1, -2));
            Ui.gap(content, 8);
        }
        if (spec.items != null) buildChoices(content);
        if (spec.customView != null)
            content.addView(spec.customView, new LinearLayout.LayoutParams(-1, -2));

        ScrollView body = new ScrollView(context);
        body.setFillViewport(false);
        body.setClipToPadding(false);
        body.setVerticalScrollBarEnabled(false);
        body.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout footer = buildFooter();
        DialogLayout shell = new DialogLayout(context, header, body, footer);
        shell.setBackground(Ui.shape(Ui.BG, 30, context));
        shell.setClipToOutline(true);
        shell.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 28) shell.setAccessibilityPaneTitle(spec.title);
        setContentView(shell);
    }

    private void buildChoices(LinearLayout content) {
        Context context = getContext();
        for (int index = 0; index < spec.items.length; index++) {
            final int item = index;
            LinearLayout row = Ui.row(context);
            row.setPadding(
                    Ui.dp(context, 16), Ui.dp(context, 14), Ui.dp(context, 14), Ui.dp(context, 14));
            row.setMinimumHeight(Ui.dp(context, 60));
            LinearLayout labels = Ui.column(context);
            TextView label = Ui.text(context, spec.items[index], 16, Ui.INK, true);
            label.setLineSpacing(Ui.dp(context, 3), 1);
            labels.addView(label);
            if (spec.descriptions != null
                    && index < spec.descriptions.length
                    && !spec.descriptions[index].isEmpty()) {
                TextView description =
                        Ui.text(context, spec.descriptions[index], 12, Ui.MUTED, false);
                description.setPadding(0, Ui.dp(context, 5), Ui.dp(context, 8), 0);
                description.setLineSpacing(Ui.dp(context, 3), 1);
                labels.addView(description);
            }
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            CompoundButton indicator = null;
            if (spec.singleChoice || spec.checked != null) {
                indicator = spec.singleChoice ? new RadioButton(context) : new CheckBox(context);
                indicator.setButtonTintList(
                        new ColorStateList(
                                new int[][] {
                                    new int[] {android.R.attr.state_checked}, new int[] {}
                                },
                                new int[] {Ui.ORANGE, 0xFFC5BEB4}));
                indicator.setPadding(0, 0, 0, 0);
                indicator.setMinWidth(0);
                indicator.setMinHeight(0);
                indicator.setMinimumWidth(0);
                indicator.setMinimumHeight(0);
                indicator.setClickable(false);
                indicator.setFocusable(false);
                indicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                row.addView(
                        indicator,
                        new LinearLayout.LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));
                row.setAccessibilityDelegate(
                        new View.AccessibilityDelegate() {
                            @Override
                            public void onInitializeAccessibilityNodeInfo(
                                    View host, AccessibilityNodeInfo info) {
                                super.onInitializeAccessibilityNodeInfo(host, info);
                                info.setClassName(
                                        spec.singleChoice
                                                ? RadioButton.class.getName()
                                                : CheckBox.class.getName());
                                info.setCheckable(true);
                                info.setChecked(isChecked(item));
                            }
                        });
            } else {
                row.addView(
                        Ui.icon(context, "next", Ui.MUTED),
                        new LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
            }
            row.setFocusable(true);
            row.setContentDescription(spec.items[index]);
            choices.add(row);
            indicators.add(indicator);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Ui.dp(context, 8);
            content.addView(row, params);
            row.setOnClickListener(
                    view -> {
                        if (spec.checked != null) {
                            spec.checked[item] = !spec.checked[item];
                            refreshChoices();
                            spec.multiListener.onClick(this, item, spec.checked[item]);
                        } else if (spec.singleChoice) {
                            selectedIndex = item;
                            refreshChoices();
                            if (spec.itemListener != null) spec.itemListener.onClick(this, item);
                        } else {
                            dismiss();
                            if (spec.itemListener != null) spec.itemListener.onClick(this, item);
                        }
                    });
        }
        refreshChoices();
    }

    private boolean isChecked(int index) {
        return spec.checked != null
                ? spec.checked[index]
                : spec.singleChoice && selectedIndex == index;
    }

    private void refreshChoices() {
        for (int index = 0; index < choices.size(); index++) {
            boolean checked = isChecked(index);
            LinearLayout row = choices.get(index);
            row.setSelected(checked);
            row.setBackground(
                    surface(
                            getContext(),
                            checked ? Ui.SOFT : Color.WHITE,
                            checked ? 0xFFF4C8AE : BORDER,
                            18));
            ripple(row, 18);
            CompoundButton indicator = indicators.get(index);
            if (indicator != null) indicator.setChecked(checked);
        }
    }

    private LinearLayout buildFooter() {
        Context context = getContext();
        LinearLayout footer = Ui.column(context);
        footer.setPadding(
                Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), Ui.dp(context, 20));
        if (spec.neutral != null) {
            TextView neutral =
                    button(
                            spec.neutral,
                            DialogInterface.BUTTON_NEUTRAL,
                            spec.neutralListener,
                            false);
            neutral.setBackground(Ui.shape(Ui.SOFT, 18, context));
            neutral.setTextColor(Ui.ORANGE);
            footer.addView(neutral, new LinearLayout.LayoutParams(-1, -2));
            Ui.gap(footer, 10);
        }
        LinearLayout actions = Ui.row(context);
        if (spec.negative != null) {
            TextView cancel =
                    button(
                            spec.negative,
                            DialogInterface.BUTTON_NEGATIVE,
                            spec.negativeListener,
                            false);
            cancel.setTag("sheet-close");
            actions.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (spec.positive != null) {
            if (spec.negative != null) {
                Space space = new Space(context);
                actions.addView(space, new LinearLayout.LayoutParams(Ui.dp(context, 10), 1));
            }
            actions.addView(
                    button(
                            spec.positive,
                            DialogInterface.BUTTON_POSITIVE,
                            spec.positiveListener,
                            true),
                    new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (actions.getChildCount() > 0)
            footer.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return footer;
    }

    private TextView button(
            String title, int which, DialogInterface.OnClickListener listener, boolean primary) {
        Context context = getContext();
        TextView button = Ui.text(context, title, 16, primary ? Color.WHITE : Ui.INK, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(Ui.dp(context, 52));
        button.setPadding(
                Ui.dp(context, 12), Ui.dp(context, 14), Ui.dp(context, 12), Ui.dp(context, 14));
        button.setBackground(
                primary
                        ? Ui.shape(spec.destructive ? DANGER : Ui.ORANGE, 18, context)
                        : surface(context, Color.WHITE, BORDER, 18));
        ripple(button, 18);
        button.setOnClickListener(
                view -> {
                    dismiss();
                    if (listener != null) listener.onClick(this, which);
                });
        buttons.put(which, button);
        return button;
    }

    private static GradientDrawable surface(Context context, int color, int stroke, int radius) {
        GradientDrawable background = Ui.shape(color, radius, context);
        background.setStroke(Ui.dp(context, 1), stroke);
        return background;
    }

    private static void ripple(View view, int radius) {
        view.setForeground(
                new RippleDrawable(
                        ColorStateList.valueOf(0x18EC692D),
                        null,
                        Ui.shape(Color.WHITE, radius, view.getContext())));
    }

    static EditText numberField(LinearLayout parent, String label, String value) {
        Context context = parent.getContext();
        LinearLayout field = Ui.column(context);
        field.setPadding(
                Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 16), Ui.dp(context, 8));
        field.setBackground(surface(context, Color.WHITE, BORDER, 18));
        TextView caption = Ui.text(context, label, 12, Ui.MUTED, true);
        field.addView(caption);
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(value);
        input.setTextColor(Ui.INK);
        input.setTextSize(23);
        input.setSingleLine();
        input.setSelectAllOnFocus(true);
        input.setContentDescription(label);
        input.setBackground(null);
        input.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
        field.addView(input, new LinearLayout.LayoutParams(-1, -2));
        input.setOnFocusChangeListener(
                (view, focused) ->
                        field.setBackground(
                                surface(context, Color.WHITE, focused ? Ui.ORANGE : BORDER, 18)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, 10);
        parent.addView(field, params);
        return input;
    }

    static AppDialog progress(Context context, String title) {
        LinearLayout row = Ui.row(context);
        row.setPadding(
                Ui.dp(context, 18), Ui.dp(context, 20), Ui.dp(context, 18), Ui.dp(context, 20));
        row.setBackground(Ui.shape(Color.WHITE, 20, context));
        ProgressBar spinner = new ProgressBar(context);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.ORANGE));
        row.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));
        TextView description = Ui.text(context, "正在处理，请稍候", 14, Ui.MUTED, false);
        description.setPadding(Ui.dp(context, 14), 0, 0, 0);
        row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
        Builder builder = new Builder(context).setTitle(title).setView(row);
        builder.busy = true;
        return builder.show();
    }

    /** 每次内容变化或键盘缩小窗口时重新测量，只让内容区滚动。 */
    private static final class DialogLayout extends LinearLayout {
        private final View header;
        private final ScrollView body;
        private final View footer;

        DialogLayout(Context context, View header, ScrollView body, View footer) {
            super(context);
            setOrientation(VERTICAL);
            this.header = header;
            this.body = body;
            this.footer = footer;
            addView(header, new LayoutParams(-1, -2));
            addView(body, new LayoutParams(-1, -2));
            addView(footer, new LayoutParams(-1, -2));
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int limit = (int) (getResources().getDisplayMetrics().heightPixels * .86f);
            if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
                limit = Math.min(limit, MeasureSpec.getSize(heightSpec));
            }
            int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            header.measure(widthSpec, free);
            footer.measure(widthSpec, free);
            int available =
                    Math.max(0, limit - header.getMeasuredHeight() - footer.getMeasuredHeight());
            body.measure(widthSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
            body.getLayoutParams().height = body.getMeasuredHeight();
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }

    static final class Builder {
        private final Context context;
        private String title = "";
        private String subtitle = "";
        private String message = "";
        private String[] items;
        private String[] descriptions;
        private int selectedIndex = -1;
        private boolean singleChoice;
        private boolean[] checked;
        private DialogInterface.OnClickListener itemListener;
        private DialogInterface.OnMultiChoiceClickListener multiListener;
        private View customView;
        private String positive;
        private String negative;
        private String neutral;
        private DialogInterface.OnClickListener positiveListener;
        private DialogInterface.OnClickListener negativeListener;
        private DialogInterface.OnClickListener neutralListener;
        private boolean destructive;
        private boolean busy;

        Builder(Context context) {
            this.context = context;
        }

        Builder setTitle(String value) {
            title = value;
            return this;
        }

        Builder setSubtitle(String value) {
            subtitle = value;
            return this;
        }

        Builder setMessage(String value) {
            message = value;
            return this;
        }

        Builder setView(View value) {
            customView = value;
            return this;
        }

        Builder setDestructive() {
            destructive = true;
            return this;
        }

        Builder setItemDescriptions(String[] value) {
            descriptions = value;
            return this;
        }

        Builder setItems(String[] value, DialogInterface.OnClickListener listener) {
            items = value;
            itemListener = listener;
            return this;
        }

        Builder setSingleChoiceItems(
                String[] value, int selected, DialogInterface.OnClickListener listener) {
            items = value;
            selectedIndex = selected;
            singleChoice = true;
            itemListener = listener;
            return this;
        }

        Builder setMultiChoiceItems(
                String[] value,
                boolean[] selected,
                DialogInterface.OnMultiChoiceClickListener listener) {
            items = value;
            checked = selected.clone();
            multiListener = listener;
            return this;
        }

        Builder setPositiveButton(String value, DialogInterface.OnClickListener listener) {
            positive = value;
            positiveListener = listener;
            return this;
        }

        Builder setNegativeButton(String value, DialogInterface.OnClickListener listener) {
            negative = value;
            negativeListener = listener;
            return this;
        }

        Builder setNeutralButton(String value, DialogInterface.OnClickListener listener) {
            neutral = value;
            neutralListener = listener;
            return this;
        }

        AppDialog create() {
            if (items != null && positive == null && negative == null && neutral == null)
                negative = "取消";
            return new AppDialog(this);
        }

        AppDialog show() {
            AppDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }
}
