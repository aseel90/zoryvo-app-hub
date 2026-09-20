package com.zoryvo.hub.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.zoryvo.hub.model.AppItem;

public final class AppCardView extends LinearLayout {
    private final GradientDrawable normal;
    private final GradientDrawable focused;

    public AppCardView(Context context, AppItem app, String state, String action,
                       boolean wide, Runnable onAction) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(18), dp(18), dp(18));
        setFocusable(true);
        setClickable(true);
        setElevation(dp(4));

        normal = background(Color.rgb(15, 30, 50), Color.rgb(42, 68, 94), 1);
        focused = background(Color.rgb(19, 45, 70), Color.rgb(99, 205, 255), 2);
        setBackground(normal);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(background(Color.rgb(8, 17, 28), Color.rgb(45, 78, 105), 1));
        icon.setClipToOutline(true);
        int iconSize = dp(wide ? 94 : 78);
        header.addView(icon, new LayoutParams(iconSize, iconSize));
        ImageLoader.load(icon, app);

        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(VERTICAL);
        LayoutParams titleBlockLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleBlockLp.setMarginStart(dp(15));

        TextView name = label(app.name, wide ? 21 : 19, Color.WHITE, true);
        TextView version = label(app.versionName, 12, Color.rgb(136, 166, 194), false);
        version.setPadding(0, dp(5), 0, 0);
        titleBlock.addView(name);
        titleBlock.addView(version);
        header.addView(titleBlock, titleBlockLp);

        addView(header);

        if (!app.formFactors.isEmpty()) {
            LinearLayout badges = new LinearLayout(context);
            badges.setOrientation(HORIZONTAL);
            badges.setPadding(0, dp(13), 0, 0);
            for (String factor : app.formFactors) {
                TextView badge = label(factorLabel(factor), 11, Color.rgb(214, 241, 255), true);
                badge.setGravity(Gravity.CENTER);
                badge.setPadding(dp(9), dp(5), dp(9), dp(5));
                badge.setBackground(background(Color.rgb(20, 62, 92), Color.rgb(57, 128, 176), 1));
                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(7));
                badges.addView(badge, lp);
            }
            addView(badges);
        }

        TextView notes = label(app.notes, 14, Color.rgb(178, 198, 218), false);
        notes.setMaxLines(2);
        notes.setPadding(0, dp(13), 0, 0);
        addView(notes);

        TextView stateView = label("●  " + state, 13,
                "تحديث متاح".equals(state) ? Color.rgb(102, 211, 255) :
                        "مثبت".equals(state) ? Color.rgb(129, 221, 173) :
                                Color.rgb(191, 205, 220), true);
        stateView.setPadding(0, dp(15), 0, dp(10));
        addView(stateView);

        Button button = new Button(context);
        button.setText(action);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setFocusable(false);
        button.setBackground(background(
                "فتح".equals(action) ? Color.rgb(33, 72, 82) : Color.rgb(25, 102, 151),
                Color.TRANSPARENT, 0));
        button.setOnClickListener(v -> onAction.run());
        addView(button, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));

        setOnClickListener(v -> onAction.run());
        setOnFocusChangeListener((v, hasFocus) -> {
            setBackground(hasFocus ? focused : normal);
            setElevation(dp(hasFocus ? 15 : 4));
            animate()
                    .scaleX(hasFocus ? 1.045f : 1f)
                    .scaleY(hasFocus ? 1.045f : 1f)
                    .setDuration(145)
                    .start();
        });
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

    private GradientDrawable background(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(18));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
