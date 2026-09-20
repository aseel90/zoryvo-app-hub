package com.zoryvo.hub;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.zoryvo.hub.model.AppItem;
import com.zoryvo.hub.network.CatalogClient;
import com.zoryvo.hub.ui.AppCardView;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.List;

public class MainActivity extends Activity {
    private LinearLayout apps;
    private TextView status;
    private Button refreshButton;
    private LinearLayout hubUpdateBanner;
    private TextView hubUpdateText;
    private Button hubUpdateButton;
    private boolean wide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wide = getResources().getConfiguration().screenWidthDp >= 700;
        buildShell();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (apps != null && apps.getChildCount() > 0) loadCatalog(true);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(6, 14, 27));
        int side = dp(wide ? 42 : 18);
        root.setPadding(side, dp(wide ? 28 : 16), side, dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        top.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.START);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.zoryvo_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(roundRect(Color.rgb(13, 27, 47), 16, Color.rgb(45, 79, 112), 1));
        logo.setClipToOutline(true);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(wide ? 58 : 48), dp(wide ? 58 : 48)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brandTextLp.setMarginStart(dp(14));

        TextView title = text("ZORYVO", wide ? 30 : 25, Color.WHITE, true);
        TextView subtitle = text("مركز تطبيقاتك وتحديثاتها", wide ? 15 : 13, Color.rgb(145, 174, 204), false);
        brandText.addView(title);
        brandText.addView(subtitle);
        brand.addView(brandText, brandTextLp);

        top.addView(brand, new LinearLayout.LayoutParams(
                wide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                wide ? 1f : 0f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (!wide) actions.setPadding(0, dp(12), 0, 0);

        TextView version = text("v" + BuildConfig.VERSION_NAME, 12, Color.rgb(137, 170, 204), false);
        version.setBackground(roundRect(Color.rgb(14, 30, 50), 30, Color.rgb(44, 74, 103), 1));
        version.setPadding(dp(11), dp(7), dp(11), dp(7));
        actions.addView(version);

        refreshButton = new Button(this);
        refreshButton.setText("↻  مزامنة");
        refreshButton.setTextSize(13);
        refreshButton.setTextColor(Color.WHITE);
        refreshButton.setAllCaps(false);
        refreshButton.setFocusable(true);
        refreshButton.setBackground(roundRect(Color.rgb(20, 59, 91), 22, Color.rgb(74, 154, 209), 1));
        refreshButton.setOnClickListener(v -> loadCatalog(false));
        applyFocusAnimation(refreshButton);
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(46));
        refreshLp.setMarginStart(dp(10));
        actions.addView(refreshButton, refreshLp);
        top.addView(actions);

        hubUpdateBanner = new LinearLayout(this);
        hubUpdateBanner.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        hubUpdateBanner.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.START);
        hubUpdateBanner.setPadding(dp(18), dp(14), dp(18), dp(14));
        hubUpdateBanner.setBackground(roundRect(Color.rgb(18, 71, 109), 18, Color.rgb(83, 194, 255), 1));
        hubUpdateBanner.setVisibility(View.GONE);

        hubUpdateText = text("", wide ? 16 : 14, Color.WHITE, true);
        hubUpdateBanner.addView(hubUpdateText, new LinearLayout.LayoutParams(
                wide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                wide ? 1f : 0f));

        hubUpdateButton = new Button(this);
        hubUpdateButton.setText("تحديث Zoryvo");
        hubUpdateButton.setTextColor(Color.rgb(5, 21, 34));
        hubUpdateButton.setTextSize(13);
        hubUpdateButton.setTypeface(hubUpdateButton.getTypeface(), Typeface.BOLD);
        hubUpdateButton.setAllCaps(false);
        hubUpdateButton.setFocusable(true);
        hubUpdateButton.setBackground(roundRect(Color.rgb(102, 211, 255), 20, Color.TRANSPARENT, 0));
        applyFocusAnimation(hubUpdateButton);
        LinearLayout.LayoutParams updateButtonLp = new LinearLayout.LayoutParams(
                wide ? LinearLayout.LayoutParams.WRAP_CONTENT : LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46));
        if (wide) updateButtonLp.setMarginStart(dp(14)); else updateButtonLp.topMargin = dp(10);
        hubUpdateBanner.addView(hubUpdateButton, updateButtonLp);

        LinearLayout sectionHead = new LinearLayout(this);
        sectionHead.setOrientation(LinearLayout.HORIZONTAL);
        sectionHead.setGravity(Gravity.CENTER_VERTICAL);

        TextView sectionTitle = text("التطبيقات", wide ? 24 : 21, Color.WHITE, true);
        sectionHead.addView(sectionTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = text("جاري جلب الكتالوج…", 13, Color.rgb(132, 161, 190), false);
        sectionHead.addView(status);

        apps = new LinearLayout(this);
        apps.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        apps.setGravity(Gravity.START);

        root.addView(top);

        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerLp.topMargin = dp(18);
        root.addView(hubUpdateBanner, bannerLp);

        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionLp.topMargin = dp(wide ? 28 : 22);
        sectionLp.bottomMargin = dp(12);
        root.addView(sectionHead, sectionLp);

        if (wide) {
            HorizontalScrollView scroll = new HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setClipToPadding(false);
            scroll.addView(apps, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setFillViewport(true);
            scroll.addView(apps);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

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
                    status.setText("تعذر الاتصال • أعد المحاولة");
                    if (!silent) toast(e.getMessage() == null ? "خطأ شبكة" : e.getMessage());
                });
            }
        }).start();
    }

    private void renderCatalog(List<AppItem> catalog) {
        apps.removeAllViews();
        refreshButton.setEnabled(true);

        AppItem hub = null;
        int count = 0;
        AppCardView first = null;

        for (AppItem item : catalog) {
            if (!item.enabled) continue;
            if (item.isHub(getPackageName())) {
                hub = item;
                continue;
            }

            Long installed = installedVersion(item.packageName);
            String state;
            String action;
            if (installed == null) {
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
                    this, item, state, action, wide,
                    () -> {
                        if (installed != null && installed >= item.versionCode) openApp(item.packageName);
                        else downloadAndInstall(item);
                    });

            LinearLayout.LayoutParams lp;
            if (wide) {
                lp = new LinearLayout.LayoutParams(dp(330), LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(18));
            } else {
                lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(14);
            }
            apps.addView(card, lp);
            if (first == null) first = card;
            count++;
        }

        status.setText(count + " تطبيقات • تمت المزامنة الآن");
        renderHubUpdate(hub);

        if (wide && first != null) first.requestFocus();
    }

    private void renderHubUpdate(AppItem hub) {
        if (hub != null && hub.versionCode > BuildConfig.VERSION_CODE) {
            hubUpdateBanner.setVisibility(View.VISIBLE);
            hubUpdateText.setText("إصدار جديد من Zoryvo متاح  •  " + hub.versionName);
            hubUpdateButton.setOnClickListener(v -> downloadAndInstall(hub));
        } else {
            hubUpdateBanner.setVisibility(View.GONE);
        }
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
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(22), dp(24), dp(22));
        box.setBackground(roundRect(Color.rgb(15, 31, 51), 22, Color.rgb(55, 92, 126), 1));

        TextView title = text("تنزيل " + app.name, 18, Color.WHITE, true);
        TextView detail = text("جاري التحضير…", 13, Color.rgb(162, 189, 214), false);
        detail.setPadding(0, dp(8), 0, dp(12));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);

        box.addView(title);
        box.addView(detail);
        box.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));

        dialog.setContentView(box);
        dialog.setCancelable(false);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    wide ? dp(520) : ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد التنزيل");
                File file = new File(dir, app.id + "-" + app.versionCode + ".apk");

                HttpURLConnection connection = openConnection(app.apkUrl, 120_000);
                long total = connection.getContentLength();
                int[] lastPercent = {-1};

                try (java.io.InputStream input = connection.getInputStream();
                     java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int read;
                    long done = 0;
                    while ((read = input.read(buffer)) >= 0) {
                        output.write(buffer, 0, read);
                        done += read;
                        if (total > 0) {
                            int pct = (int) Math.min(100, done * 100 / total);
                            if (pct != lastPercent[0]) {
                                lastPercent[0] = pct;
                                runOnUiThread(() -> {
                                    progress.setProgress(pct);
                                    detail.setText("جاري التنزيل  •  " + pct + "%");
                                });
                            }
                        }
                    }
                } finally {
                    connection.disconnect();
                }

                runOnUiThread(() -> detail.setText("جاري التحقق من الملف…"));
                verifyDownloadedApk(app, file);

                runOnUiThread(() -> {
                    dialog.dismiss();
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
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
                runOnUiThread(() -> {
                    dialog.dismiss();
                    toast("فشل التنزيل: " + (e.getMessage() == null ? "خطأ" : e.getMessage()));
                });
            }
        }).start();
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

    private HttpURLConnection openConnection(String url, int readTimeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(15_000);
        c.setReadTimeout(readTimeoutMs);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "Zoryvo-App-Hub/" + BuildConfig.VERSION_NAME);
        c.connect();
        if (c.getResponseCode() < 200 || c.getResponseCode() > 299)
            throw new IllegalStateException("HTTP " + c.getResponseCode());
        return c;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
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
                .scaleX(focused ? 1.06f : 1f)
                .scaleY(focused ? 1.06f : 1f)
                .setDuration(150)
                .start());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
