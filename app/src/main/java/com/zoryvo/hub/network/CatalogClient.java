package com.zoryvo.hub.network;

import com.zoryvo.hub.model.AppItem;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class CatalogClient {
    private static final int MAX_CATALOG_CHARS = 512 * 1024;

    private CatalogClient() {}

    public static List<AppItem> fetch(String url, String versionName, File cacheFile) throws Exception {
        Exception networkFailure = null;

        try {
            String text = download(url, versionName);
            List<AppItem> apps = parse(text);
            writeCache(cacheFile, text);
            return apps;
        } catch (Exception e) {
            networkFailure = e;
        }

        if (cacheFile != null && cacheFile.isFile() && cacheFile.length() > 0) {
            try {
                return parse(readFile(cacheFile));
            } catch (Exception ignored) {
                // Fall through and report the original network error.
            }
        }

        throw networkFailure != null ? networkFailure : new IllegalStateException("تعذر تحميل الكتالوج");
    }

    public static List<AppItem> parse(String text) throws Exception {
        Object root = new JSONTokener(text).nextValue();
        JSONArray array;
        if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            array = ((JSONObject) root).optJSONArray("apps");
            if (array == null) throw new IllegalStateException("صيغة الكتالوج غير صحيحة");
        } else {
            throw new IllegalStateException("صيغة الكتالوج غير صحيحة");
        }

        List<AppItem> apps = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            AppItem app = AppItem.from(item);
            if (app.isValid()) apps.add(app);
        }

        if (apps.isEmpty()) throw new IllegalStateException("الكتالوج فارغ أو غير صالح");
        return apps;
    }

    private static String download(String source, String versionName) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Zoryvo-App-Hub/" + versionName);
        connection.connect();

        try {
            int response = connection.getResponseCode();
            if (response < 200 || response > 299) {
                throw new IllegalStateException("HTTP " + response);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() + line.length() > MAX_CATALOG_CHARS) {
                        throw new IllegalStateException("الكتالوج أكبر من الحد المسموح");
                    }
                    out.append(line).append('\n');
                }
                return out.toString();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void writeCache(File cacheFile, String text) {
        if (cacheFile == null) return;
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;

        File temp = new File(cacheFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
            if (cacheFile.exists() && !cacheFile.delete()) return;
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(cacheFile);
        } catch (Exception ignored) {
            // Cache failure must never make the online catalog fail.
        } finally {
            if (temp.exists() && !temp.equals(cacheFile)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
            }
        }
    }

    private static String readFile(File file) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() + line.length() > MAX_CATALOG_CHARS) {
                    throw new IllegalStateException("الكتالوج المخزن أكبر من الحد المسموح");
                }
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}