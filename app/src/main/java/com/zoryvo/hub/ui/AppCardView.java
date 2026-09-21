package com.zoryvo.hub.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zoryvo.hub.R;
import com.zoryvo.hub.model.AppItem;

public final class AppCardView extends LinearLayout {
    private final GradientDrawable normal;
    private final GradientDrawable focused;

    public AppCardView(
            Context context,
            AppItem app,
            String state,
            String action,
            boolean gridStyle,
            Runnable onAction,
            Runnable onManage) {

        super(context);
        setOrientation(gridStyle ? VERTICAL : HORIZONTAL);
        setGravity(gridStyle ? Gravity.START : Gravity.CENTER_VERTICAL);
        setPadding(dp(gridStyle ? 13 : 11), dp(11), dp(gridStyle ? 13 : 11), dp(11));
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setElevation(dp(3));

        normal = background(Color.rgb(15, 30, 50), Color.rgb(42, 68, 94), 1, 14);
        focused = background(Color.rgb(19, 45, 70), Color.rgb(99, 205, 255), 2, 14);
        setBackground(normal);

        if (gridStyle) {
            buildGrid(app, state, action, onAction, onManage);
        } else {
            buildList(app, state, action, onAction, onManage);
        }

        setOnClickListener(v -> onAction.run());
        setOnLongClickListener(v -> {
            onManage.run();
            return true;
        });

        setOnFocusChangeListener((v, hasFocus) -> {
            setBackground(hasFocus ? focused : normal);
            setElevation(dp(hasFocus ? 10 : 3));
            animate()
                    .scaleX(hasFocus ? 1.025f : 1f)
                    .scaleY(hasFocus ? 1.025f : 1f)
                    .setDuration(110)
                    .start();
        });
    }

    private void buildGrid(
            AppItem app,
            String state,
            String action,
            Runnable onAction,
            Runnable onManage) {

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = appIcon(app, 64);
        header.addView(icon, new LayoutParams(dp(64), dp(64)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(10));

        TextView name = label(app.name, 17, Color.WHITE, true);
        name.setMaxLines(1);
        TextView version = label(app.versionName, 11, Color.rgb(136, 166, 194), false);
        version.setPadding(0, dp(3), 0, 0);
        titleBlock.addView(name);
        titleBlock.addView(version);
        header.addView(titleBlock, titleLp);

        header.addView(manageButton(app, onManage), new LayoutParams(dp(34), dp(34)));
        addView(header);

        if (!app.formFactors.isEmpty()) addView(badges(app));

        TextView notes = label(app.notes, 12, Color.rgb(178, 198, 218), false);
        notes.setMaxLines(1);
        notes.setPadding(0, dp(9), 0, 0);
        addView(notes);

        TextView stateView = label(state, 11, stateColor(state), true);
        stateView.setPadding(0, dp(10), 0, dp(7));
        addView(stateView);

        Button button = actionButton(action, onAction);
        addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, dp(42)));
    }

    private void buildList(
            AppItem app,
            String state,
            String action,
            Runnable onAction,
            Runnable onManage) {

        ImageView icon = appIcon(app, 56);
        addView(icon, new LayoutParams(dp(56), dp(56)));

        LinearLayout middle = new LinearLayout(getContext());
        middle.setOrientation(VERTICAL);
        LayoutParams middleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        middleLp.setMarginStart(dp(11));

        TextView name = label(app.name, 16, Color.WHITE, true);
        name.setMaxLines(1);
        TextView meta = label(app.versionName + "  •  " + state, 11, stateColor(state), false);
        meta.setPadding(0, dp(4), 0, 0);
        middle.addView(name);
        middle.addView(meta);
        addView(middle, middleLp);

        Button actionButton = actionButton(action, onAction);
        LayoutParams actionLp = new LayoutParams(dp(88), dp(40));
        addView(actionButton, actionLp);

        LayoutParams moreLp = new LayoutParams(dp(34), dp(34));
        moreLp.setMarginStart(dp(5));
        addView(manageButton(app, onManage), moreLp);
    }

    private ImageView appIcon(AppItem app, int size) {
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(background(Color.rgb(8, 17, 28), Color.rgb(45, 78, 105), 1, 12));
        icon.setClipToOutline(true);
        ImageLoader.load(icon, app);
        return icon;
    }

    private LinearLayout badges(AppItem app) {
        LinearLayout badges = new LinearLayout(getContext());
        badges.setOrientation(HORIZONTAL);
        badges.setPadding(0, dp(8), 0, 0);

        for (String factor : app.formFactors) {
            TextView badge = label(factorLabel(factor), 10, Color.rgb(214, 241, 255), true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(7), dp(3), dp(7), dp(3));
            badge.setBackground(background(Color.rgb(20, 62, 92), Color.rgb(57, 128, 176), 1, 18));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(5));
            badges.addView(badge, lp);
        }
        return badges;
    }

    private Button actionButton(String action, Runnable onAction) {
        Button button = new Button(getContext());
        button.setText(action);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setFocusable(false);
        button.setBackground(background(
                "فتح".equals(action) ? Color.rgb(31, 69, 78) : Color.rgb(25, 102, 151),
                Color.TRANSPARENT,
                0,
                12));
        button.setOnClickListener(v -> onAction.run());
        return button;
    }

    private ImageButton manageButton(AppItem app, Runnable onManage) {
        ImageButton more = new ImageButton(getContext());
        more.setImageResource(R.drawable.ic_more);
        more.setColorFilter(Color.rgb(190, 214, 234));
        more.setPadding(dp(8), dp(8), dp(8), dp(8));
        more.setBackground(background(Color.TRANSPARENT, Color.TRANSPARENT, 0, 12));
        more.setContentDescription("إدارة " + app.name);
        more.setFocusable(false);
        more.setOnClickListener(v -> onManage.run());
        return more;
    }

    private int stateColor(String state) {
        if (state.startsWith("تحديث") || state.startsWith("تنزيل")) return Color.rgb(102, 211, 255);
        if ("مثبت".equals(state)) return Color.rgb(129, 221, 173);
        return Color.rgb(191, 205, 220);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private String factorLabel(String value) {
        if ("tv".equalsIgnoreCase(value)) return "TV";
        if ("phone".equalsIgnoreCase(value)) return "Phone";
        if ("tablet".equalsIgnoreCase(value)) return "Tablet";
        return value;
    }

    private GradientDrawable background(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
