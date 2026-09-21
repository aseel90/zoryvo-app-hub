package com.zoryvo.hub.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zoryvo.hub.R;
import com.zoryvo.hub.model.AppItem;

public final class AppCardView extends LinearLayout {
    private final String appId;
    private final GradientDrawable normal;
    private final GradientDrawable focused;

    public AppCardView(
            Context context,
            AppItem app,
            String state,
            String action,
            boolean gridStyle,
            boolean showInlineAction,
            Runnable onAction,
            Runnable onManage,
            Runnable onFocused) {

        super(context);
        appId = app.id;

        // Geometry is intentionally LTR so the same physical D-pad direction always
        // corresponds to the same visual direction. Individual text still detects RTL.
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setOrientation(gridStyle ? VERTICAL : HORIZONTAL);
        setGravity(gridStyle ? Gravity.START : Gravity.CENTER_VERTICAL);
        setPadding(dp(gridStyle ? 13 : 10), dp(10), dp(gridStyle ? 13 : 10), dp(10));
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setLongClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(app.name + "، " + state + "، " + action);
        setMinimumHeight(dp(gridStyle ? 176 : 72));
        setElevation(dp(2));

        normal = background(Color.rgb(15, 30, 50), Color.rgb(43, 70, 96), 1, 14);
        focused = background(Color.rgb(20, 50, 78), Color.rgb(103, 218, 255), 3, 14);
        setBackground(normal);

        if (gridStyle) {
            buildGrid(app, state, action, onAction, onManage);
        } else {
            buildList(app, state, action, showInlineAction, onAction, onManage);
        }

        setOnClickListener(v -> onAction.run());
        setOnLongClickListener(v -> {
            onManage.run();
            return true;
        });
        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_UP
                    && (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO)) {
                onManage.run();
                return true;
            }
            return false;
        });

        setOnFocusChangeListener((v, hasFocus) -> {
            setBackground(hasFocus ? focused : normal);
            setElevation(dp(hasFocus ? 7 : 2));
            if (hasFocus && onFocused != null) onFocused.run();
        });
    }

    public String getAppId() {
        return appId;
    }

    private void buildGrid(
            AppItem app,
            String state,
            String action,
            Runnable onAction,
            Runnable onManage) {

        LinearLayout topRow = horizontalRow();

        ImageView icon = appIcon(app);
        topRow.addView(icon, new LayoutParams(dp(58), dp(58)));

        View spacer = new View(getContext());
        topRow.addView(spacer, new LayoutParams(0, 1, 1f));

        LayoutParams moreLp = new LayoutParams(dp(44), dp(44));
        topRow.addView(manageButton(app, onManage), moreLp);
        addView(topRow);

        TextView name = label(app.name, 16, Color.WHITE, true);
        singleLine(name);
        alignText(name);
        name.setPadding(0, dp(8), 0, 0);
        addView(name);

        TextView version = label(app.versionName, 11, Color.rgb(136, 166, 194), false);
        singleLine(version);
        version.setPadding(0, dp(2), 0, 0);
        alignText(version);
        addView(version);

        if (!app.formFactors.isEmpty()) addView(badges(app));

        TextView stateView = label(state, 11, stateColor(state), true);
        singleLine(stateView);
        stateView.setPadding(0, dp(7), 0, dp(6));
        alignText(stateView);
        addView(stateView);

        Button button = actionButton(action, onAction);
        LayoutParams buttonLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        addView(button, buttonLp);
    }

    private void buildList(
            AppItem app,
            String state,
            String action,
            boolean showInlineAction,
            Runnable onAction,
            Runnable onManage) {

        setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = appIcon(app);
        addView(icon, new LayoutParams(dp(52), dp(52)));

        LinearLayout identity = new LinearLayout(getContext());
        identity.setOrientation(VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        identity.setMinimumWidth(dp(70));

        LayoutParams identityLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        identityLp.setMarginStart(dp(8));
        identityLp.setMarginEnd(dp(6));

        TextView name = label(app.name, 16, Color.WHITE, true);
        singleLine(name);
        alignText(name);

        TextView meta = label(app.versionName + "  •  " + state, 11, stateColor(state), false);
        singleLine(meta);
        meta.setPadding(0, dp(3), 0, 0);
        alignText(meta);

        identity.addView(name);
        identity.addView(meta);
        addView(identity, identityLp);

        if (showInlineAction) {
            Button actionButton = actionButton(action, onAction);
            actionButton.setMinWidth(dp(76));
            LayoutParams actionLp = new LayoutParams(dp(82), LayoutParams.WRAP_CONTENT);
            addView(actionButton, actionLp);
        }

        LayoutParams moreLp = new LayoutParams(dp(44), dp(44));
        moreLp.setMarginStart(dp(4));
        addView(manageButton(app, onManage), moreLp);
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return row;
    }

    private void singleLine(TextView view) {
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    private void alignText(TextView view) {
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }

    private ImageView appIcon(AppItem app) {
        ImageView icon = new ImageView(getContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(background(Color.rgb(8, 17, 28), Color.rgb(45, 78, 105), 1, 12));
        icon.setClipToOutline(true);
        icon.setContentDescription(null);
        icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        ImageLoader.load(icon, app);
        return icon;
    }

    private LinearLayout badges(AppItem app) {
        LinearLayout badges = horizontalRow();
        badges.setPadding(0, dp(7), 0, 0);

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
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setPadding(dp(10), 0, dp(10), 0);
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
        more.setPadding(dp(10), dp(10), dp(10), dp(10));
        more.setBackground(background(Color.rgb(16, 34, 54), Color.TRANSPARENT, 0, 12));
        more.setContentDescription("إدارة " + app.name);
        more.setFocusable(false);
        more.setOnClickListener(v -> onManage.run());
        return more;
    }

    private int stateColor(String state) {
        if (state.startsWith("تحديث") || state.startsWith("تنزيل")) {
            return Color.rgb(102, 211, 255);
        }
        if ("مثبت".equals(state)) {
            return Color.rgb(129, 221, 173);
        }
        return Color.rgb(191, 205, 220);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text == null ? "" : text);
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
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}