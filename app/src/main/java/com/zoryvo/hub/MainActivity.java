package com.zoryvo.hub;

import android.app.Activity;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.zoryvo.hub.download.ResumableDownloader;
import com.zoryvo.hub.model.AppItem;
import com.zoryvo.hub.network.CatalogClient;
import com.zoryvo.hub.ui.AppCardView;
import com.zoryvo.hub.ui.LayoutPolicy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "zoryvo_preferences";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String VIEW_AUTO = "auto";
    private static final String VIEW_GRID = "grid";
    private static final String VIEW_LIST = "list";

    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Object downloadLock = new Object();

    private ViewGroup apps;
    private ScrollView appsScroll;
    private TextView status;
    private ImageButton refreshButton;
    private ImageButton settingsButton;
    private LinearLayout hubUpdateBanner;
    private TextView hubUpdateText;
    private Button hubUpdateButton;
    private SharedPreferences preferences;
    private AppItem currentHubItem;
    private AppItem pendingInstallPermissionApp;
    private List<AppItem> lastCatalog = Collections.emptyList();
    private String lastFocusedAppId;
    private boolean gridMode;
    private boolean television;
    private int gridColumns;
    private int sidePaddingDp;
    private volatile boolean destroyed;
    private volatile boolean catalogLoading;
    private volatile boolean downloadCancelRequested;
    private volatile Thread activeDownloadThread;
    private int catalogGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        television = isTelevisionDevice();
        resolveLayoutMode();
        buildShell();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        cleanupCompletedDownloads();

        if (pendingInstallPermissionApp != null
                && Build.VERSION.SDK_INT >= 26
                && getPackageManager().canRequestPackageInstalls()) {
            AppItem pending = pendingInstallPermissionApp;
            pendingInstallPermissionApp = null;
            downloadAndInstall(pending);
            return;
        }

        // Returning from another app, package installer, or system settings should update
        // installed/open/update states locally without a network refresh or focus reset.
        if (!lastCatalog.isEmpty() && !catalogLoading) {
            renderCatalog(lastCatalog, true);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        catalogGeneration++;
        cancelActiveDownload(false);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void resolveLayoutMode() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        String mode = preferences.getString(KEY_VIEW_MODE, VIEW_AUTO);
        gridMode = LayoutPolicy.useGrid(mode, widthDp, television);
        sidePaddingDp = LayoutPolicy.sidePaddingDp(widthDp);
        gridColumns = gridMode ? LayoutPolicy.gridColumns(widthDp, sidePaddingDp) : 1;
    }

    private void buildShell() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        boolean wideHeader = widthDp >= 720;
        boolean compactHeader = widthDp < 520;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(6, 14, 27));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        int baseLeft = dp(sidePaddingDp);
        int baseTop = dp(wideHeader ? 18 : 10);
        int baseRight = dp(sidePaddingDp);
        int baseBottom = dp(10);
        root.setPadding(baseLeft, baseTop, baseRight, baseBottom);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + bars.bottom);
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.zoryvo_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(roundRect(Color.rgb(13, 27, 47), 14, Color.rgb(45, 79, 112), 1));
        logo.setClipToOutline(true);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int logoSize = dp(wideHeader ? 48 : 42);
        top.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.setMarginStart(dp(10));

        TextView title = text("ZORYVO", compactHeader ? 20 : (wideHeader ? 25 : 22), Color.WHITE, true);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        brandText.addView(title);

        if (!compactHeader) {
            TextView subtitle = text(
                    "مركز التطبيقات والتحديثات",
                    wideHeader ? 13 : 11,
                    Color.rgb(145, 174, 204),
                    false);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setTextDirection(View.TEXT_DIRECTION_RTL);
            brandText.addView(subtitle);
        }
        top.addView(brandText, brandLp);

        if (widthDp >= 600) {
            TextView version = text("v" + BuildConfig.VERSION_NAME, 11, Color.rgb(137, 170, 204), false);
            version.setBackground(roundRect(Color.rgb(14, 30, 50), 24, Color.rgb(44, 74, 103), 1));
            version.setPadding(dp(9), dp(6), dp(9), dp(6));
            LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            versionLp.setMarginEnd(dp(5));
            top.addView(version, versionLp);
        }

        refreshButton = iconButton(R.drawable.ic_refresh, "مزامنة التطبيقات");
        refreshButton.setId(View.generateViewId());
        refreshButton.setOnClickListener(v -> loadCatalog(false));
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        refreshLp.setMarginStart(dp(4));
        top.addView(refreshButton, refreshLp);

        settingsButton = iconButton(R.drawable.ic_settings, "إعدادات Zoryvo");
        settingsButton.setId(View.generateViewId());
        settingsButton.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsLp.setMarginStart(dp(4));
        top.addView(settingsButton, settingsLp);

        hubUpdateBanner = new LinearLayout(this);
        hubUpdateBanner.setOrientation(wideHeader ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hubUpdateBanner.setGravity(wideHeader ? Gravity.CENTER_VERTICAL : Gravity.START);
        hubUpdateBanner.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        hubUpdateBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        hubUpdateBanner.setBackground(roundRect(Color.rgb(18, 71, 109), 14, Color.rgb(83, 194, 255), 1));
        hubUpdateBanner.setVisibility(View.GONE);

        hubUpdateText = text("", wideHeader ? 14 : 13, Color.WHITE, true);
        hubUpdateText.setTextDirection(View.TEXT_DIRECTION_RTL);
        hubUpdateBanner.addView(hubUpdateText, new LinearLayout.LayoutParams(
                wideHeader ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                wideHeader ? 1f : 0f));

        hubUpdateButton = button("تحديث الآن", true);
        hubUpdateButton.setId(View.generateViewId());
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(
                wideHeader ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (wideHeader) updateLp.setMarginStart(dp(12));
        else updateLp.topMargin = dp(8);
        hubUpdateBanner.addView(hubUpdateButton, updateLp);

        LinearLayout sectionHead = new LinearLayout(this);
        sectionHead.setOrientation(LinearLayout.HORIZONTAL);
        sectionHead.setGravity(Gravity.CENTER_VERTICAL);
        sectionHead.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        TextView sectionTitle = text("التطبيقات", wideHeader ? 20 : 18, Color.WHITE, true);
        sectionTitle.setTextDirection(View.TEXT_DIRECTION_RTL);
        sectionHead.addView(sectionTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = text("جاري المزامنة…", 11, Color.rgb(132, 161, 190), false);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        sectionHead.addView(status);

        appsScroll = new ScrollView(this);
        appsScroll.setFillViewport(true);
        appsScroll.setVerticalScrollBarEnabled(false);
        appsScroll.setSmoothScrollingEnabled(true);
        appsScroll.setClipToPadding(false);
        appsScroll.setClipChildren(false);
        appsScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        if (gridMode) {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(gridColumns);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            grid.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            grid.setClipChildren(false);
            grid.setClipToPadding(false);
            apps = grid;
        } else {
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            list.setClipChildren(false);
            list.setClipToPadding(false);
            apps = list;
        }

        appsScroll.addView(apps, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(top);

        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerLp.topMargin = dp(10);
        root.addView(hubUpdateBanner, bannerLp);

        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.topMargin = dp(14);
        sectionLp.bottomMargin = dp(7);
        root.addView(sectionHead, sectionLp);

        root.addView(appsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        ViewCompat.requestApplyInsets(root);
    }

    private void loadCatalog(boolean silent) {
        if (catalogLoading || destroyed) return;
        catalogLoading = true;
        int generation = ++catalogGeneration;

        if (!silent) status.setText("جاري المزامنة…");
        refreshButton.setEnabled(false);

        ioExecutor.execute(() -> {
            List<AppItem> catalog = null;
            boolean bundledFallback = false;
            Exception failure = null;

            try {
                File cache = new File(getFilesDir(), "catalog/apps-cache.json");
                catalog = CatalogClient.fetch(BuildConfig.CATALOG_URL, BuildConfig.VERSION_NAME, cache);
            } catch (Exception e) {
                failure = e;
                try {
                    catalog = loadBundledCatalog();
                    bundledFallback = true;
                } catch (Exception ignored) {
                    // Report the original network/cache error below.
                }
            }

            if (destroyed || generation != catalogGeneration) return;
            List<AppItem> result = catalog;
            boolean fallback = bundledFallback;
            Exception finalFailure = failure;

            runOnUiThread(() -> {
                if (destroyed || generation != catalogGeneration) return;
                catalogLoading = false;
                refreshButton.setEnabled(true);

                if (result != null && !result.isEmpty()) {
                    lastCatalog = new ArrayList<>(result);
                    renderCatalog(lastCatalog, true);
                    if (fallback) status.setText("وضع دون اتصال • " + visibleAppCount(lastCatalog) + " تطبيقات");
                } else {
                    status.setText("تعذر الاتصال");
                    if (!silent) {
                        toast(finalFailure == null
                                ? "تعذر مزامنة الكتالوج"
                                : "تعذر مزامنة الكتالوج. تحقق من الاتصال.");
                    }
                }
            });
        });
    }

    private List<AppItem> loadBundledCatalog() throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("catalog-fallback.json"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return CatalogClient.parse(out.toString());
    }

    private int visibleAppCount(List<AppItem> catalog) {
        int count = 0;
        for (AppItem item : catalog) {
            if (item.enabled && !item.isHub(getPackageName())) count++;
        }
        return count;
    }

    private void renderCatalog(List<AppItem> catalog, boolean preserveFocus) {
        if (destroyed || apps == null) return;

        String focusId = preserveFocus ? focusedAppId() : null;
        if (focusId == null && preserveFocus) focusId = lastFocusedAppId;

        apps.removeAllViews();
        currentHubItem = null;

        List<AppCardView> cards = new ArrayList<>();
        int count = 0;

        for (AppItem item : catalog) {
            if (!item.enabled) continue;
            if (item.isHub(getPackageName())) {
                currentHubItem = item;
                continue;
            }

            Long installed = installedVersion(item.packageName);
            int savedProgress = savedProgress(item);
            boolean hasPartial = ResumableDownloader.partialFile(getCacheDir(), item).length() > 0;

            String state;
            String action;

            if (hasPartial && (installed == null || installed < item.versionCode)) {
                state = savedProgress > 0
                        ? "تنزيل محفوظ • " + savedProgress + "%"
                        : "تنزيل غير مكتمل";
                action = "متابعة";
            } else if (installed == null) {
                state = "غير مثبت";
                action = "تثبيت";
            } else if (installed < item.versionCode) {
                state = "تحديث متاح";
                action = "تحديث";
            } else if (installed > item.versionCode) {
                state = "إصدار أحدث مثبت";
                action = "فتح";
            } else {
                state = "مثبت";
                action = "فتح";
            }

            AppCardView card = new AppCardView(
                    this,
                    item,
                    state,
                    action,
                    gridMode,
                    getResources().getConfiguration().screenWidthDp >= 420,
                    () -> {
                        Long current = installedVersion(item.packageName);
                        if (current != null && current >= item.versionCode) openApp(item.packageName);
                        else downloadAndInstall(item);
                    },
                    () -> showAppManagement(item, installedVersion(item.packageName)),
                    () -> lastFocusedAppId = item.id);

            card.setId(View.generateViewId());
            addCard(card, count);
            cards.add(card);
            count++;
        }

        status.setText(count + " تطبيقات");
        renderHubUpdate();
        configureFocusGraph(cards);
        requestPreferredFocus(cards, focusId);
    }

    private String focusedAppId() {
        View focused = getCurrentFocus();
        if (focused instanceof AppCardView) {
            return ((AppCardView) focused).getAppId();
        }
        return null;
    }

    private void addCard(AppCardView card, int index) {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int gapDp = LayoutPolicy.cardGapDp(widthDp);

        if (gridMode) {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(index % gridColumns, 1, 1f);
            lp.rowSpec = GridLayout.spec(index / gridColumns);
            if ((index % gridColumns) < gridColumns - 1) lp.setMarginEnd(dp(gapDp));
            lp.bottomMargin = dp(gapDp);
            apps.addView(card, lp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(7);
            apps.addView(card, lp);
        }
    }

    private void renderHubUpdate() {
        if (currentHubItem != null && currentHubItem.versionCode > BuildConfig.VERSION_CODE) {
            hubUpdateBanner.setVisibility(View.VISIBLE);
            hubUpdateText.setText("يتوفر Zoryvo " + currentHubItem.versionName);
            hubUpdateButton.setOnClickListener(v -> downloadAndInstall(currentHubItem));
        } else {
            hubUpdateBanner.setVisibility(View.GONE);
            hubUpdateButton.setOnClickListener(null);
        }
    }

    private void configureFocusGraph(List<AppCardView> cards) {
        int count = cards.size();

        refreshButton.setNextFocusLeftId(refreshButton.getId());
        refreshButton.setNextFocusRightId(settingsButton.getId());
        settingsButton.setNextFocusLeftId(refreshButton.getId());
        settingsButton.setNextFocusRightId(settingsButton.getId());

        if (count == 0) {
            refreshButton.setNextFocusDownId(refreshButton.getId());
            settingsButton.setNextFocusDownId(settingsButton.getId());
            return;
        }

        boolean updateVisible = hubUpdateBanner.getVisibility() == View.VISIBLE;
        int firstId = cards.get(0).getId();
        int rightMostTop = Math.min(gridColumns - 1, count - 1);
        int rightTopId = cards.get(Math.max(0, rightMostTop)).getId();
        int refreshDownId = gridMode && count > 1
                ? cards.get(Math.max(0, rightMostTop - 1)).getId()
                : firstId;

        if (updateVisible) {
            refreshButton.setNextFocusDownId(hubUpdateButton.getId());
            settingsButton.setNextFocusDownId(hubUpdateButton.getId());
            hubUpdateButton.setNextFocusUpId(settingsButton.getId());
            hubUpdateButton.setNextFocusDownId(gridMode ? rightTopId : firstId);
            hubUpdateButton.setNextFocusLeftId(hubUpdateButton.getId());
            hubUpdateButton.setNextFocusRightId(hubUpdateButton.getId());
        } else {
            refreshButton.setNextFocusDownId(refreshDownId);
            settingsButton.setNextFocusDownId(gridMode ? rightTopId : firstId);
        }

        for (int i = 0; i < count; i++) {
            AppCardView card = cards.get(i);
            int self = card.getId();

            if (!gridMode) {
                int up = i > 0 ? cards.get(i - 1).getId()
                        : (updateVisible ? hubUpdateButton.getId() : settingsButton.getId());
                int down = i + 1 < count ? cards.get(i + 1).getId() : self;
                card.setNextFocusUpId(up);
                card.setNextFocusDownId(down);
                card.setNextFocusLeftId(self);
                card.setNextFocusRightId(self);
                continue;
            }

            int leftIndex = LayoutPolicy.leftIndex(i, count, gridColumns);
            int rightIndex = LayoutPolicy.rightIndex(i, count, gridColumns);
            int upIndex = LayoutPolicy.upIndex(i, count, gridColumns);
            int downIndex = LayoutPolicy.downIndex(i, count, gridColumns);

            card.setNextFocusLeftId(leftIndex >= 0 ? cards.get(leftIndex).getId() : self);
            card.setNextFocusRightId(rightIndex >= 0 ? cards.get(rightIndex).getId() : self);
            card.setNextFocusDownId(downIndex >= 0 ? cards.get(downIndex).getId() : self);

            if (upIndex >= 0) {
                card.setNextFocusUpId(cards.get(upIndex).getId());
            } else if (updateVisible) {
                card.setNextFocusUpId(hubUpdateButton.getId());
            } else {
                int column = i % gridColumns;
                card.setNextFocusUpId(
                        LayoutPolicy.toolbarTargetForColumn(column, gridColumns) == 1
                                ? settingsButton.getId()
                                : refreshButton.getId());
            }
        }
    }

    private void requestPreferredFocus(List<AppCardView> cards, String desiredAppId) {
        if (cards.isEmpty() || !isDpadEnvironment()) return;

        AppCardView target = null;
        if (desiredAppId != null) {
            for (AppCardView card : cards) {
                if (desiredAppId.equals(card.getAppId())) {
                    target = card;
                    break;
                }
            }
        }
        if (target == null) target = cards.get(0);

        AppCardView finalTarget = target;
        appsScroll.post(() -> {
            if (!destroyed && finalTarget.isAttachedToWindow()) {
                finalTarget.requestFocus();
            }
        });
    }

    private void showSettings() {
        Dialog dialog = new Dialog(this);
        LinearLayout box = dialogBox();

        TextView title = text("إعدادات Zoryvo", 20, Color.WHITE, true);
        TextView version = text("الإصدار " + BuildConfig.VERSION_NAME, 12, Color.rgb(146, 174, 201), false);
        version.setPadding(0, dp(4), 0, dp(14));
        box.addView(title);
        box.addView(version);

        Button update = button(
                currentHubItem != null && currentHubItem.versionCode > BuildConfig.VERSION_CODE
                        ? "تحديث Zoryvo إلى " + currentHubItem.versionName
                        : "التحقق من تحديث Zoryvo",
                false);
        update.setId(View.generateViewId());
        update.setOnClickListener(v -> {
            if (currentHubItem != null && currentHubItem.versionCode > BuildConfig.VERSION_CODE) {
                dialog.dismiss();
                downloadAndInstall(currentHubItem);
            } else {
                dialog.dismiss();
                loadCatalog(false);
            }
        });
        box.addView(update, matchButtonLp());

        TextView viewTitle = text("نوع العرض", 13, Color.rgb(184, 205, 225), true);
        viewTitle.setPadding(0, dp(16), 0, dp(8));
        box.addView(viewTitle);

        int widthDp = getResources().getConfiguration().screenWidthDp;
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(widthDp < 440 ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        modes.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        String active = preferences.getString(KEY_VIEW_MODE, VIEW_AUTO);
        Button auto = viewModeButton("تلقائي", VIEW_AUTO, active, dialog);
        Button grid = viewModeButton("شبكة", VIEW_GRID, active, dialog);
        Button list = viewModeButton("قائمة", VIEW_LIST, active, dialog);
        auto.setId(View.generateViewId());
        grid.setId(View.generateViewId());
        list.setId(View.generateViewId());

        if (widthDp < 440) {
            modes.addView(auto, matchButtonLp());
            LinearLayout.LayoutParams gridLp = matchButtonLp();
            gridLp.topMargin = dp(7);
            modes.addView(grid, gridLp);
            LinearLayout.LayoutParams listLp = matchButtonLp();
            listLp.topMargin = dp(7);
            modes.addView(list, listLp);
        } else {
            modes.addView(auto, weightedButtonLp());
            modes.addView(grid, weightedButtonLp());
            modes.addView(list, weightedButtonLp());
        }
        box.addView(modes);

        Button clearDownloads = button("مسح التنزيلات غير المكتملة", false);
        clearDownloads.setId(View.generateViewId());
        LinearLayout.LayoutParams clearLp = matchButtonLp();
        clearLp.topMargin = dp(14);
        clearDownloads.setOnClickListener(v -> {
            clearPartialDownloads();
            dialog.dismiss();
            if (!lastCatalog.isEmpty()) renderCatalog(lastCatalog, true);
            toast("تم مسح التنزيلات غير المكتملة");
        });
        box.addView(clearDownloads, clearLp);

        wireSettingsFocus(update, auto, grid, list, clearDownloads, widthDp < 440);

        TextView aboutTitle = text("حول", 13, Color.rgb(184, 205, 225), true);
        aboutTitle.setPadding(0, dp(16), 0, dp(5));
        TextView about = text(
                "Zoryvo مدير خفيف للتطبيقات والتحديثات. يدعم اللمس والريموت ولوحة المفاتيح، ويحفظ التنزيلات غير المكتملة للمتابعة لاحقًا.",
                12,
                Color.rgb(153, 180, 205),
                false);
        about.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(aboutTitle);
        box.addView(about);

        showDialog(dialog, box, 540);
    }

    private void wireSettingsFocus(
            Button update,
            Button auto,
            Button grid,
            Button list,
            Button clear,
            boolean verticalModes) {

        update.setNextFocusDownId(auto.getId());
        if (verticalModes) {
            auto.setNextFocusUpId(update.getId());
            auto.setNextFocusDownId(grid.getId());
            grid.setNextFocusUpId(auto.getId());
            grid.setNextFocusDownId(list.getId());
            list.setNextFocusUpId(grid.getId());
            list.setNextFocusDownId(clear.getId());
            clear.setNextFocusUpId(list.getId());
        } else {
            auto.setNextFocusUpId(update.getId());
            grid.setNextFocusUpId(update.getId());
            list.setNextFocusUpId(update.getId());
            auto.setNextFocusLeftId(auto.getId());
            auto.setNextFocusRightId(grid.getId());
            grid.setNextFocusLeftId(auto.getId());
            grid.setNextFocusRightId(list.getId());
            list.setNextFocusLeftId(grid.getId());
            list.setNextFocusRightId(list.getId());
            auto.setNextFocusDownId(clear.getId());
            grid.setNextFocusDownId(clear.getId());
            list.setNextFocusDownId(clear.getId());
            clear.setNextFocusUpId(grid.getId());
        }
        clear.setNextFocusDownId(clear.getId());
    }

    private Button viewModeButton(String label, String mode, String active, Dialog dialog) {
        Button button = button(label, mode.equals(active));
        button.setOnClickListener(v -> {
            preferences.edit().putString(KEY_VIEW_MODE, mode).apply();
            dialog.dismiss();
            recreate();
        });
        return button;
    }

    private void showAppManagement(AppItem app, Long installedVersion) {
        Dialog dialog = new Dialog(this);
        LinearLayout box = dialogBox();

        TextView title = text(app.name, 20, Color.WHITE, true);
        title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        TextView subtitle = text(
                installedVersion == null ? "غير مثبت" : "إدارة التطبيق",
                12,
                Color.rgb(146, 174, 201),
                false);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        box.addView(title);
        box.addView(subtitle);

        if (installedVersion == null) {
            Button install = button("تثبيت", true);
            install.setOnClickListener(v -> {
                dialog.dismiss();
                downloadAndInstall(app);
            });
            box.addView(install, matchButtonLp());
        } else {
            Button open = button("فتح التطبيق", true);
            open.setOnClickListener(v -> {
                dialog.dismiss();
                openApp(app.packageName);
            });
            box.addView(open, matchButtonLp());

            if (installedVersion < app.versionCode) {
                Button update = button("تحديث إلى " + app.versionName, false);
                update.setOnClickListener(v -> {
                    dialog.dismiss();
                    downloadAndInstall(app);
                });
                LinearLayout.LayoutParams lp = matchButtonLp();
                lp.topMargin = dp(7);
                box.addView(update, lp);
            }

            if (installedVersion <= app.versionCode) {
                Button reinstall = button("إعادة تثبيت النسخة الحالية", false);
                reinstall.setOnClickListener(v -> {
                    dialog.dismiss();
                    downloadAndInstall(app);
                });
                LinearLayout.LayoutParams reinstallLp = matchButtonLp();
                reinstallLp.topMargin = dp(7);
                box.addView(reinstall, reinstallLp);
            }

            Button info = button("معلومات التطبيق", false);
            info.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + app.packageName)));
                } catch (Exception e) {
                    toast("تعذر فتح معلومات التطبيق");
                }
                dialog.dismiss();
            });
            LinearLayout.LayoutParams infoLp = matchButtonLp();
            infoLp.topMargin = dp(7);
            box.addView(info, infoLp);

            Button uninstall = button("إزالة التثبيت", false);
            uninstall.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName)));
                } catch (Exception e) {
                    toast("تعذر فتح إزالة التثبيت");
                }
                dialog.dismiss();
            });
            LinearLayout.LayoutParams uninstallLp = matchButtonLp();
            uninstallLp.topMargin = dp(7);
            box.addView(uninstall, uninstallLp);
        }

        showDialog(dialog, box, 460);
    }

    private void showDialog(Dialog dialog, LinearLayout content, int preferredWidthDp) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(scroll);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            int widthDp = Math.max(280, Math.min(preferredWidthDp, screenWidthDp - 28));
            window.setLayout(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        if (isDpadEnvironment()) {
            content.post(() -> {
                ArrayList<View> focusables = content.getFocusables(View.FOCUS_FORWARD);
                if (!focusables.isEmpty()) focusables.get(0).requestFocus();
            });
        }
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(roundRect(Color.rgb(15, 31, 51), 18, Color.rgb(55, 92, 126), 1));
        return box;
    }

    private LinearLayout.LayoutParams matchButtonLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        return lp;
    }

    private Long installedVersion(String packageName) {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
            if (Build.VERSION.SDK_INT >= 28) return info.getLongVersionCode();
            return (long) info.versionCode;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openApp(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent == null && Build.VERSION.SDK_INT >= 21) {
            intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        }

        if (intent != null) {
            try {
                startActivity(intent);
            } catch (Exception e) {
                toast("تعذر فتح التطبيق");
            }
        } else {
            toast("لا توجد واجهة تشغيل لهذا التطبيق على هذا الجهاز");
        }
    }

    private void downloadAndInstall(AppItem app) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallPermissionApp = app;
            toast("اسمح لـ Zoryvo بتثبيت التطبيقات");
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                pendingInstallPermissionApp = null;
                toast("تعذر فتح إعداد السماح بالتثبيت");
            }
            return;
        }

        synchronized (downloadLock) {
            if (activeDownloadThread != null && activeDownloadThread.isAlive()) {
                toast("يوجد تنزيل جارٍ بالفعل");
                return;
            }
        }

        Dialog dialog = new Dialog(this);
        LinearLayout box = dialogBox();
        TextView title = text("تنزيل " + app.name, 18, Color.WHITE, true);
        title.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);

        int initial = savedProgress(app);
        TextView detail = text(
                initial > 0 ? "متابعة التنزيل من " + initial + "%" : "جاري التحضير…",
                12,
                Color.rgb(162, 189, 214),
                false);
        detail.setPadding(0, dp(8), 0, dp(10));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(initial);

        Button cancel = button("إيقاف وحفظ التنزيل", false);
        LinearLayout.LayoutParams cancelLp = matchButtonLp();
        cancelLp.topMargin = dp(12);

        box.addView(title);
        box.addView(detail);
        box.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(7)));
        box.addView(cancel, cancelLp);

        downloadCancelRequested = false;
        cancel.setOnClickListener(v -> {
            cancelActiveDownload(true);
            if (dialog.isShowing()) dialog.dismiss();
        });
        dialog.setOnCancelListener(d -> cancelActiveDownload(true));
        showDialog(dialog, box, 540);
        dialog.setCanceledOnTouchOutside(false);

        Thread worker = new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("تعذر إنشاء مجلد التنزيل");
                }

                final int[] lastUiPercent = {initial - 1};
                final long[] lastUiAt = {0L};

                File downloaded = ResumableDownloader.download(
                        dir,
                        app,
                        "Zoryvo-App-Hub/" + BuildConfig.VERSION_NAME,
                        (done, total) -> {
                            int pct = total > 0
                                    ? (int) Math.min(100, (done * 100L) / total)
                                    : 0;
                            long now = SystemClock.elapsedRealtime();
                            if (pct != lastUiPercent[0]
                                    && (pct == 100 || now - lastUiAt[0] >= 180 || lastUiPercent[0] < 0)) {
                                lastUiPercent[0] = pct;
                                lastUiAt[0] = now;
                                saveProgress(app, pct);
                                if (!destroyed) {
                                    runOnUiThread(() -> {
                                        if (destroyed || !dialog.isShowing()) return;
                                        progress.setProgress(pct);
                                        detail.setText(total > 0
                                                ? "جاري التنزيل • " + pct + "%"
                                                : "جاري التنزيل…");
                                    });
                                }
                            }
                        },
                        () -> downloadCancelRequested || Thread.currentThread().isInterrupted());

                if (destroyed) return;
                runOnUiThread(() -> {
                    if (dialog.isShowing()) detail.setText("جاري التحقق من الملف…");
                });

                try {
                    verifyDownloadedApk(app, downloaded);
                } catch (Exception verificationError) {
                    //noinspection ResultOfMethodCallIgnored
                    downloaded.delete();