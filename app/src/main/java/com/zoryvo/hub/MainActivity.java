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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private LinearLayout content;
    private TextView status;
    private Button refreshButton;
    private boolean compact;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        compact = getResources().getConfiguration().screenWidthDp < 600;
        buildShell();
        loadCatalog(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null && content.getChildCount() > 0) loadCatalog(true);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int side = compact ? dp(16) : dp(34);
        root.setPadding(side, compact ? dp(14) : dp(22), side, compact ? dp(14) : dp(22));
        root.setBackgroundColor(Color.rgb(7, 17, 31));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        header.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("ZORYVO");
        title.setTextSize(compact ? 26 : 30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("تطبيقاتك في مكان واحد");
        subtitle.setTextSize(compact ? 13 : 15);
        subtitle.setTextColor(Color.rgb(150, 177, 202));

        titleBox.addView(title);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                compact ? 0f : 1f));

        refreshButton = new Button(this);
        refreshButton.setText("تحديث القائمة");
        refreshButton.setFocusable(true);
        refreshButton.setOnClickListener(v -> loadCatalog(false));
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (compact) refreshLp.topMargin = dp(10);
        header.addView(refreshButton, refreshLp);

        status = new TextView(this);
        status.setText("جاري تحميل التطبيقات…");
        status.setTextSize(15);
        status.setTextColor(Color.rgb(170, 195, 218));
        status.setPadding(0, dp(12), 0, dp(14));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        root.addView(header);
        root.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void loadCatalog(boolean silent) {
        if (!silent) status.setText("جاري مزامنة الكتالوج…");
        refreshButton.setEnabled(false);

        new Thread(() -> {
            try {
                HttpURLConnection connection = openConnection(BuildConfig.CATALOG_URL, 20000);
                String text;
                try (java.io.BufferedReader reader =
                             new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()))) {
                    StringBuilder out = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) out.append(line);
                    text = out.toString();
                } finally {
                    connection.disconnect();
                }

                JSONArray apps = new JSONArray(text);
                runOnUiThread(() -> {
                    content.removeAllViews();
                    int visibleCount = 0;
                    Button firstAction = null;
                    for (int i = 0; i < apps.length(); i++) {
                        JSONObject o = apps.optJSONObject(i);
                        if (o == null || !o.optBoolean("enabled", true)) continue;
                        Button action = addAppCard(AppItem.from(o));
                        if (firstAction == null) firstAction = action;
                        visibleCount++;
                    }
                    status.setText(visibleCount + " تطبيقات • تمت المزامنة الآن");
                    refreshButton.setEnabled(true);
                    if (!compact && firstAction != null) firstAction.requestFocus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("تعذر تحميل القائمة • تحقق من الاتصال ثم أعد المحاولة");
                    refreshButton.setEnabled(true);
                    if (!silent) toast(e.getMessage() == null ? "خطأ شبكة" : e.getMessage());
                });
            }
        }).start();
    }

    private Button addAppCard(AppItem app) {
        Long installed = installedVersion(app.packageName);
        String state;
        if (installed == null) state = "غير مثبت • " + app.versionName;
        else if (installed < app.versionCode) state = "تحديث متاح • " + app.versionName;
        else state = "مثبت • " + app.versionName;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(compact ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(compact ? Gravity.START : Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.rgb(18, 34, 55));
        bg.setStroke(dp(1), Color.rgb(45, 72, 98));
        card.setBackground(bg);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView name = new TextView(this);
        name.setText(app.name);
        name.setTextSize(compact ? 20 : 22);
        name.setTextColor(Color.WHITE);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);

        TextView meta = new TextView(this);
        meta.setText(app.notes.isEmpty() ? state : state + "\n" + app.notes);
        meta.setTextSize(14);
        meta.setTextColor(Color.rgb(175, 198, 220));

        textBox.addView(name);
        textBox.addView(meta);
        card.addView(textBox, new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                compact ? 0f : 1f));

        Button button = new Button(this);
        button.setFocusable(true);
        if (installed == null) button.setText("تثبيت");
        else if (installed < app.versionCode) button.setText("تحديث");
        else button.setText("فتح");

        button.setOnClickListener(v -> {
            if (installed != null && installed >= app.versionCode) openApp(app.packageName);
            else downloadAndInstall(app);
        });

        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                compact ? LinearLayout.LayoutParams.MATCH_PARENT : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (compact) buttonLp.topMargin = dp(12);
        card.addView(button, buttonLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        content.addView(card, lp);
        return button;
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
            toast("اسمح لـ Zoryvo بتثبيت التطبيقات، ثم أعد الضغط على التثبيت");
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(40), dp(30), dp(40), dp(30));
        box.setBackgroundColor(Color.rgb(18, 34, 55));
        box.addView(new ProgressBar(this));

        TextView downloading = new TextView(this);
        downloading.setText("جاري تنزيل " + app.name + "…");
        downloading.setTextColor(Color.WHITE);
        downloading.setTextSize(16);
        downloading.setPadding(0, dp(12), 0, 0);
        box.addView(downloading);

        dialog.setContentView(box);
        dialog.setCancelable(false);
        dialog.show();

        new Thread(() -> {
            try {
                File dir = new File(getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد التنزيل");
                File file = new File(dir, app.id + "-" + app.versionCode + ".apk");

                HttpURLConnection connection = openConnection(app.apkUrl, 120000);
                try (java.io.InputStream input = connection.getInputStream();
                     java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    byte[] buffer = new byte[32768];
                    int count;
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                } finally {
                    connection.disconnect();
                }

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
                        if (Build.VERSION.SDK_INT < 26) {
                            try { startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                            catch (Exception ignored) { }
                        }
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
            throw new IllegalStateException("هوية APK لا تطابق التطبيق المطلوب");

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
        c.setConnectTimeout(15000);
        c.setReadTimeout(readTimeoutMs);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "Zoryvo-App-Hub/" + BuildConfig.VERSION_NAME);
        c.connect();
        if (c.getResponseCode() < 200 || c.getResponseCode() > 299)
            throw new IllegalStateException("HTTP " + c.getResponseCode());
        return c;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    static class AppItem {
        final String id, name, packageName, versionName, apkUrl, notes, sha256;
        final long versionCode;

        AppItem(String id, String name, String packageName, long versionCode,
                String versionName, String apkUrl, String notes, String sha256) {
            this.id = id;
            this.name = name;
            this.packageName = packageName;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.notes = notes;
            this.sha256 = sha256;
        }

        static AppItem from(JSONObject o) {
            long code = o.optLong("versionCode", 1);
            return new AppItem(
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("packageName"),
                    code,
                    o.optString("versionName", Long.toString(code)),
                    o.optString("apkUrl"),
                    o.optString("notes", ""),
                    o.optString("sha256", "")
            );
        }
    }
}
