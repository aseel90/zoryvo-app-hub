package com.zoryvo.hub;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

import com.zoryvo.hub.download.ResumableDownloader;
import com.zoryvo.hub.model.AppItem;
import com.zoryvo.hub.network.CatalogClient;
import com.zoryvo.hub.ui.AppCardView;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "zoryvo_preferences";
    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String VIEW_AUTO = "auto";
    private static final String VIEW_GRID = "grid";
    private static final String VIEW_LIST = "list";

    private ViewGroup apps;
    private TextView status;
    private ImageButton refreshButton;
    private LinearLayout hubUpdateBanner;
    private TextView hubUpdateText;
    private Button hubUpdateButton;
    private SharedPreferences preferences;
    private AppItem currentHubItem;
    private boolean gridMode;
    private int gridColumns;
    private int sidePaddingDp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        resolveLayoutMode();
        buildShell();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (apps != null && apps.getChildCount() > 0) loadCatalog(true);
    }

    private void resolveLayoutMode() {
        int width = getResources().getConfiguration().screenWidthDp;
        String mode = preferences.getString(KEY_VIEW_MODE, VIEW_AUTO);
        gridMode = VIEW_GRID.equals(mode) || (VIEW_AUTO.equals(mode) && width >= 600);

        if (width >= 1500) gridColumns = 5;
        else if (width >= 1150) gridColumns = 4;
        else if (width >= 760) gridColumns = 3;
        else if (width >= 430) gridColumns = 2;
        else gridColumns = 2;

        sidePaddingDp = width >= 1200 ? 32 : width >= 700 ? 24 : 14;
    }

    private void buildShell() {
        int width = getResources().getConfiguration().screenWidthDp;
        boolean wideHeader = width >= 720;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(6, 14, 27));
        root.setPadding(dp(sidePaddingDp), dp(wideHeader ? 20 : 12), dp(sidePaddingDp), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.zoryvo_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(roundRect(Color.rgb(13, 27, 47), 14, Color.rgb(45, 79, 112), 1));
        logo.setClipToOutline(true);
        int logoSize = dp(wideHeader ? 48 : 42);
        top.addView(logo, new LinearLayout.LayoutParams(logoSize, logoSize));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.setMarginStart(dp(12));

        TextView title = text("ZORYVO", wideHeader ? 25 : 22, Color.WHITE, true);
        TextView subtitle = text("مركز التطبيقات والتحديثات", wideHeader ? 13 : 11, Color.rgb(145, 174, 204), false);
        brandText.addView(title);
        brandText.addView(subtitle);
        top.addView(brandText, brandLp);

        TextView version = text("v" + BuildConfig.VERSION_NAME, 11, Color.rgb(137, 170, 204), false);
        version.setBackground(roundRect(Color.rgb(14, 30, 50), 24, Color.rgb(44, 74, 103), 1));
        version.setPadding(dp(9), dp(6), dp(9), dp(6));
        top.addView(version);

        refreshButton = iconButton(R.drawable.ic_refresh, "مزامنة التطبيقات");
        refreshButton.setOnClickListener(v -> loadCatalog(false));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconLp.setMarginStart(dp(8));
        top.addView(refreshButton, iconLp);

        ImageButton settingsButton = iconButton(R.drawable.ic_settings, "إعدادات Zoryvo");
        settingsButton.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        settingsLp.setMarginStart(dp(6));
        top.addView(settingsButton, settingsLp);

        hubUpdateBanner = new LinearLayout(this);
        hubUpdateBanner.setOrientation(wideHeader ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hubUpdateBanner.setGravity(wideHeader ? Gravity.CENTER_VERTICAL : Gravity.START);
        hubUpdateBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        hubUpdateBanner.setBackground(roundRect(Color.rgb(18, 71, 109), 14, Color.rgb(83, 194, 255), 1));
        hubUpdateBanner.setVisibility(View.GONE);

        hubUpdateText = text("", wideHeader ? 14 : 13, Color.WHITE, true);
        hubUpdateBanner.addView(hubUpdateText, new LinearLayout.LayoutParams(
                wideHeader ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                wideHeader ? 1f : 0f));

        hubUpdateButton = button("تحديث الآن", true);
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(
                wideHeader ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42));
        if (wideHeader) updateLp.setMarginStart(dp(12)); else updateLp.topMargin = dp(8);
        hubUpdateBanner.addView(hubUpdateButton, updateLp);

        LinearLayout sectionHead = new LinearLayout(this);
        sectionHead.setOrientation(LinearLayout.HORIZONTAL);
        sectionHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView sectionTitle = text("التطبيقات", wideHeader ? 20 : 18, Color.WHITE, true);
        sectionHead.addView(sectionTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = text("جاري المزامنة…", 11, Color.rgb(132, 161, 190), false);
        sectionHead.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        if (gridMode) {
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(gridColumns);
            grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            grid.setUseDefaultMargins(false);
            apps = grid;
        } else {
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            apps = list;
        }

        scroll.addView(apps, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(top);

        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerLp.topMargin = dp(12);
        root.addView(hubUpdateBanner, bannerLp);

        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.topMargin = dp(16);
        sectionLp.bottomMargin = dp(8);
        root.addView(sectionHead, sectionLp);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void loadCatalog(boolean silent) {
        if (!silent) status.setText("جاري المزامنة…");
        refreshButton.setEnabled(false);

        new Thread(() -> {
            try {
                List<AppItem> catalog = CatalogClient.fetch(BuildConfig.CATALOG_URL, BuildConfig.VERSION_NAME);
                runOnUiThread(() -> renderCatalog(catalog));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    refreshButton.setEnabled(true);
                    status.setText("تعذر الاتصال");
                    if (!silent) toast("تعذر مزامنة الكتالوج");
                });
            }
        }).start();
    }

    private void renderCatalog(List<AppItem> catalog) {
        apps.removeAllViews();
        refreshButton.setEnabled(true);
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
                    () -> {
                        if (installed != null && installed >= item.versionCode) openApp(item.packageName);
                        else downloadAndInstall(item);
                    },
                    () -> showAppManagement(item, installed));

            addCard(card, count);
            cards.add(card);
            count++;
        }

        status.setText(count + " تطبيقات");
        renderHubUpdate();

        if (!cards.isEmpty() && getResources().getConfiguration().screenWidthDp >= 600) {
            cards.get(0).requestFocus();
        }
    }

    private void addCard(AppCardView card, int index) {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        int gapDp = widthDp >= 700 ? 12 : 9;

        if (gridMode) {
            int availablePx = getResources().getDisplayMetrics().widthPixels
                    - dp(sidePaddingDp * 2)
                    - dp(gapDp * (gridColumns - 1));
            int cardWidth = Math.max(dp(148), availablePx / gridColumns);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardWidth;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(index % gridColumns);
            lp.rowSpec = GridLayout.spec(index / gridColumns);
            if ((index % gridColumns) < gridColumns - 1) lp.setMarginEnd(dp(gapDp));
            lp.bottomMargin = dp(gapDp);
            apps.addView(card, lp);
        } else {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
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
        }
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
        update.setOnClickListener(v -> {
            if (currentHubItem != null && currentHubItem.versionCode > BuildConfig.VERSION_CODE) {
                dialog.dismiss();
                downloadAndInstall(currentHubItem);
            } else {
                dialog.dismiss();
                loadCatalog(false);
                toast("تم التحقق من التحديثات");
            }
        });
        box.addView(update, matchButtonLp());

        TextView viewTitle = text("نوع العرض", 13, Color.rgb(184, 205, 225), true);
        viewTitle.setPadding(0, dp(16), 0, dp(8));
        box.addView(viewTitle);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);

        String active = preferences.getString(KEY_VIEW_MODE, VIEW_AUTO);
        modes.addView(viewModeButton("تلقائي", VIEW_AUTO, active, dialog), weightedButtonLp());
        modes.addView(viewModeButton("شبكة", VIEW_GRID, active, dialog), weightedButtonLp());
        modes.addView(viewModeButton("قائمة", VIEW_LIST, active, dialog), weightedButtonLp());
        box.addView(modes);

        Button clearDownloads = button("مسح التنزيلات غير المكتملة", false);
        LinearLayout.LayoutParams clearLp = matchButtonLp();
        clearLp.topMargin = dp(14);
        clearDownloads.setOnClickListener(v -> {
            clearPartialDownloads();
            dialog.dismiss();
            loadCatalog(true);
            toast("تم مسح التنزيلات غير المكتملة");
        });
        box.addView(clearDownloads, clearLp);

        TextView aboutTitle = text("حول", 13, Color.rgb(184, 205, 225), true);
        aboutTitle.setPadding(0, dp(16), 0, dp(5));
        TextView about = text(
                "Zoryvo مدير خفيف للتطبيقات والتحديثات. لا يشغّل خدمة دائمة في الخلفية، وتُحفظ التنزيلات غير المكتملة محليًا للمتابعة لاحقًا.",
                12,
                Color.rgb(153, 180, 205),
                false);
        box.addView(aboutTitle);
        box.addView(about);

        showDialog(dialog, box, 520);
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
                lp.topMargin = dp(8);
                box.addView(update, lp);
            }

            Button info = button("معلومات التطبيق", false);
            info.setOnClickListener(v -> {
                Intent intent = new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.packageName));
                startActivity(intent);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams infoLp = matchButtonLp();
            infoLp.topMargin = dp(8);
            box.addView(info, infoLp);

            Button uninstall = button("إزالة التثبيت", false);
            uninstall.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
                startActivity(intent);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams uninstallLp = matchButtonLp();
            uninstallLp.topMargin = dp(8);
            box.addView(uninstall, uninstallLp);
        }

        showDialog(dialog, box, 440);
    }

    private void showDialog(Dialog dialog, View content, int widthDp) {
        dialog.setContentView(content);
        dialog.setCancelable(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            window.setLayout(
                    screenWidthDp >= 600 ? dp(widthDp) : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout dialogBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        box.setBackground(roundRect(Color.rgb(15, 31, 51), 18, Color.rgb(55, 92, 126), 1));
        return box;
    }

    private LinearLayout.LayoutParams matchButtonLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
    }

    private LinearLayout.LayoutParams weightedButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
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
        Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (intent != null) startActivity(intent);
        else toast("تعذر فتح التطبيق");
    }

    private void downloadAndInstall(AppItem app) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            toast("اسمح لـ Zoryvo بتثبيت التطبيقات ثم أعد المحاولة");
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout box = dialogBox();

        TextView title = text("تنزيل " + app.name, 18, Color.WHITE, true);
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

        box.addView(title);
        box.addView(detail);
        box.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(7)));

        showDialog(dialog, box, 520);
        dialog.setCancelable(false);

        new Thread(() -> {
            File downloaded = null;
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("تعذر إنشاء مجلد التنزيل");
                }

                downloaded = ResumableDownloader.download(
                        dir,
                        app,
                        "Zoryvo-App-Hub/" + BuildConfig.VERSION_NAME,
                        (done, total) -> {
                            int pct = total > 0 ? (int) Math.min(100, (done * 100L) / total) : 0;
                            saveProgress(app, pct);
                            runOnUiThread(() -> {
                                progress.setProgress(pct);
                                detail.setText("جاري التنزيل • " + pct + "%");
                            });
                        });

                runOnUiThread(() -> detail.setText("جاري التحقق من الملف…"));

                try {
                    verifyDownloadedApk(app, downloaded);
                } catch (Exception verificationError) {
                    downloaded.delete();
                    ResumableDownloader.partialFile(getCacheDir(), app).delete();
                    clearProgress(app);
                    throw verificationError;
                }

                clearProgress(app);
                File finalDownloaded = downloaded;
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Uri uri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".files",
                            finalDownloaded);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (Exception installError) {
                        toast("تعذر فتح مثبت Android");
                    }
                });
            } catch (Exception e) {
                int saved = savedProgress(app);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    loadCatalog(true);
                    if (saved > 0) {
                        toast("توقف التنزيل عند " + saved + "% وتم حفظه للمتابعة");
                    } else {
                        toast("تعذر بدء التنزيل");
                    }
                });
            }
        }).start();
    }

    private int savedProgress(AppItem app) {
        return preferences.getInt(progressKey(app), 0);
    }

    private void saveProgress(AppItem app, int progress) {
        preferences.edit().putInt(progressKey(app), progress).apply();
    }

    private void clearProgress(AppItem app) {
        preferences.edit().remove(progressKey(app)).apply();
    }

    private String progressKey(AppItem app) {
        return "download_pct_" + app.id + "_" + app.versionCode;
    }

    private void clearPartialDownloads() {
        File dir = new File(getCacheDir(), "updates");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".part")) file.delete();
            }
        }

        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith("download_pct_")) editor.remove(entry.getKey());
        }
        editor.apply();
    }

    private void verifyDownloadedApk(AppItem app, File file) throws Exception {
        if (file.length() < 1024) throw new IllegalStateException("ملف APK غير صالح");

        PackageInfo archive = getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null)
            throw new IllegalStateException("تعذر قراءة هوية APK");
        if (!app.packageName.equals(archive.packageName))
            throw new IllegalStateException("هوية APK لا تطابق التطبيق");

        long archiveVersion = Build.VERSION.SDK_INT >= 28
                ? archive.getLongVersionCode()
                : archive.versionCode;
        if (archiveVersion < app.versionCode)
            throw new IllegalStateException("إصدار APK أقدم من الكتالوج");

        if (!app.sha256.isEmpty() && !sha256(file).equalsIgnoreCase(app.sha256))
            throw new IllegalStateException("فشل التحقق من سلامة APK");
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : digest.digest()) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setTypeface(button.getTypeface(), primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setFocusable(true);
        button.setBackground(roundRect(
                primary ? Color.rgb(25, 102, 151) : Color.rgb(22, 48, 72),
                14,
                primary ? Color.rgb(75, 166, 220) : Color.rgb(52, 86, 116),
                1));
        applyFocusAnimation(button);
        return button;
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setColorFilter(Color.WHITE);
        button.setBackground(roundRect(Color.rgb(16, 37, 59), 14, Color.rgb(48, 84, 116), 1));
        button.setFocusable(true);
        applyFocusAnimation(button);
        return button;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }

    private void applyFocusAnimation(View view) {
        view.setOnFocusChangeListener((v, focused) -> v.animate()
                .scaleX(focused ? 1.05f : 1f)
                .scaleY(focused ? 1.05f : 1f)
                .setDuration(120)
                .start());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
