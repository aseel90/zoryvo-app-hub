package com.zoryvo.hub.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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

        // Keep the visual structure stable even when the device locale is RTL.
        // Text itself still uses FIRST_STRONG so Arabic and Latin names render naturally.
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setOrientation(gridStyle ? VERTICAL : HORIZONTAL);
        setGravity(gridStyle ? Gravity.START : Gravity.CENTER_VERTICAL);
        setPadding(dp(gridStyle ? 13 : 10), dp(10), dp(gridStyle ? 13 : 10), dp(10));
        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);
        setLongClickable(true);
        setElevation(dp(3));

        normal = background(Color.rgb(15, 30, 50), Color.rgb(42, 68, 94), 1, 14);
        focused = background(Color.rgb(20, 50, 78), Color.rgb(103, 218, 255), 3, 14);
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
            setElevation(dp(hasFocus ? 12 : 3));
            if (hasFocus) {
                bringToFront();
            }
            // Do not scale the card. On TV this keeps the focus ring exactly
            // on the selected card and avoids clipping against neighboring cards.
            animate().scaleX(1f).scaleY(1f).setDuration(70).start();
        });
    }

    @Override
    public View focusSearch(int direction) {
        ViewParent viewParent = getParent();
        if (!(viewParent instanceof ViewGroup)) {
            return super.focusSearch(direction);
        }

        ViewGroup parent = (ViewGroup) viewParent;

        // List mode: only vertical navigation is meaningful.
        if (parent instanceof LinearLayout
                && ((LinearLayout) parent).getOrientation() == LinearLayout.VERTICAL) {
            int index = parent.indexOfChild(this);

            if (direction == View.FOCUS_UP) {
                if (index > 0) return parent.getChildAt(index - 1);
                return super.focusSearch(direction);
            }

            if (direction == View.FOCUS_DOWN) {
                if (index >= 0 && index + 1 < parent.getChildCount()) {
                    return parent.getChildAt(index + 1);
                }
                return this;
            }

            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                return this;
            }
        }

        if (direction == View.FOCUS_LEFT
                || direction == View.FOCUS_RIGHT
                || direction == View.FOCUS_UP
                || direction == View.FOCUS_DOWN) {

            View target = nearestCard(parent, direction);
            if (target != null) return target;

            // At horizontal/bottom edges, keep focus on the current card instead
            // of allowing Android FocusFinder to jump to an unrelated control.
            if (direction == View.FOCUS_LEFT
                    || direction == View.FOCUS_RIGHT
                    || direction == View.FOCUS_DOWN) {
                return this;
            }
        }

        // Up from the first row can naturally reach the toolbar.
        return super.focusSearch(direction);
    }

    private View nearestCard(ViewGroup parent, int direction) {
        float centerX = getLeft() + getWidth() / 2f;
        float centerY = getTop() + getHeight() / 2f;

        View best = null;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View candidate = parent.getChildAt(i);
            if (candidate == this || !(candidate instanceof AppCardView) || !candidate.isFocusable()) {
                continue;
            }

            float candidateX = candidate.getLeft() + candidate.getWidth() / 2f;
            float candidateY = candidate.getTop() + candidate.getHeight() / 2f;
            float dx = candidateX - centerX;
            float dy = candidateY - centerY;

            float primary;
            float secondary;

            switch (direction) {
                case View.FOCUS_LEFT:
                    if (dx >= -1f) continue;
                    primary = -dx;
                    secondary = Math.abs(dy);
                    break;

                case View.FOCUS_RIGHT:
                    if (dx <= 1f) continue;
                    primary = dx;
                    secondary = Math.abs(dy);
                    break;

                case View.FOCUS_UP:
                    if (dy >= -1f) continue;
                    primary = -dy;
                    secondary = Math.abs(dx);
                    break;

                case View.FOCUS_DOWN:
                    if (dy <= 1f) continue;
                    primary = dy;
                    secondary = Math.abs(dx);
                    break;

                default:
                    continue;
            }

            // Strongly prefer the same visual row/column.
            float score = primary + (secondary * 4f);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
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
        header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        ImageView icon = appIcon(app);
        header.addView(icon, new LayoutParams(dp(64), dp(64)));

        LinearLayout titleBlock = new LinearLayout(getContext());
        titleBlock.setOrientation(VERTICAL);
        titleBlock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LayoutParams titleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(10));

        TextView name = label(app.name, 17, Color.WHITE, true);
        name.setMaxLines(1);
        alignText(name);

        TextView version = label(app.versionName, 11, Color.rgb(136, 166, 194), false);
        version.setPadding(0, dp(3), 0, 0);
        alignText(version);

        titleBlock.addView(name);
        titleBlock.addView(version);
        header.addView(titleBlock, titleLp);

        header.addView(manageButton(app, onManage), new LayoutParams(dp(34), dp(34)));
        addView(header);

        if (!app.formFactors.isEmpty()) addView(badges(app));

        TextView notes = label(app.notes, 12, Color.rgb(178, 198, 218), false);
        notes.setMaxLines(1);
        notes.setPadding(0, dp(9), 0, 0);
        alignText(notes);
        addView(notes);

        TextView stateView = label(state, 11, stateColor(state), true);
        stateView.setPadding(0, dp(10), 0, dp(7));
        alignText(stateView);
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

        setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = appIcon(app);
        addView(icon, new LayoutParams(dp(54), dp(54)));

        LinearLayout middle = new LinearLayout(getContext());
        middle.setOrientation(VERTICAL);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        middle.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LayoutParams middleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        middleLp.setMarginStart(dp(8));
        middleLp.setMarginEnd(dp(7));

        TextView name = label(app.name, 16, Color.WHITE, true);
        name.setMaxLines(1);
        alignText(name);

        TextView meta = label(app.versionName + "  •  " + state, 11, stateColor(state), false);
        meta.setPadding(0, dp(3), 0, 0);
        alignText(meta);

        middle.addView(name);
        middle.addView(meta);
        addView(middle, middleLp);

        Button actionButton = actionButton(action, onAction);
        LayoutParams actionLp = new LayoutParams(dp(80), dp(38));
        addView(actionButton, actionLp);

        LayoutParams moreLp = new LayoutParams(dp(32), dp(32));
        moreLp.setMarginStart(dp(4));
        addView(manageButton(app, onManage), moreLp);
    }

    private void alignText(TextView view) {
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        view.setGravity(Gravity.START);
    }

    private ImageView appIcon(AppItem app) {
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
        badges.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        badges.setPadding(0, dp(8), 0, 0);

        for (String factor : app.formFactors) {
            TextView badge = label(factorLabel(factor), 10, Color.rgb(214, 241, 255), true);
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(dp(7), dp(3), dp(7), dp(3));
            badge.setBackground(background(Color.rgb(20, 62, 92), Color.rgb(57, 128, 176), 1, 18));

            LayoutParams lp = new LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
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

    private GradientDrawable background(
            int fill,
            int stroke,
            int strokeDp,
            int radiusDp) {

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
