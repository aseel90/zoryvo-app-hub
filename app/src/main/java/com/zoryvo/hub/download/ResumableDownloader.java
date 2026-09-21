package com.zoryvo.hub.download;

import com.zoryvo.hub.model.AppItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ResumableDownloader {
    public interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    private ResumableDownloader() {}

    public static File partialFile(File cacheDir, AppItem app) {
        File dir = new File(cacheDir, "updates");
        return new File(dir, app.id + "-" + app.versionCode + ".apk.part");
    }

    public static File finalFile(File dir, AppItem app) {
        return new File(dir, app.id + "-" + app.versionCode + ".apk");
    }

    public static File download(
            File dir,
            AppItem app,
            String userAgent,
            Listener listener) throws Exception {

        File partial = new File(dir, app.id + "-" + app.versionCode + ".apk.part");
        File complete = finalFile(dir, app);

        if (complete.isFile() && complete.length() > 1024) return complete;

        long existing = partial.isFile() ? partial.length() : 0L;
        HttpURLConnection connection = open(app.apkUrl, existing, userAgent);
        int code = connection.getResponseCode();

        if (code == 416 && existing > 0) {
            long serverTotal = totalFromRange(connection.getHeaderField("Content-Range"));
            connection.disconnect();
            if (serverTotal > 0 && existing == serverTotal) {
                if (!partial.renameTo(complete)) {
                    throw new IOException("تعذر إكمال ملف التنزيل");
                }
                return complete;
            }
            partial.delete();
            existing = 0;
            connection = open(app.apkUrl, 0, userAgent);
            code = connection.getResponseCode();
        }

        boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
        if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
            existing = 0;
            append = false;
        }

        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }

        long total = totalFromRange(connection.getHeaderField("Content-Range"));
        if (total <= 0) {
            int length = connection.getContentLength();
            total = length > 0 ? existing + length : -1;
        }

        long done = append ? existing : 0;
        if (listener != null) listener.onProgress(done, total);

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(partial, append)) {
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                done += read;
                if (listener != null) listener.onProgress(done, total);
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        if (total > 0 && partial.length() < total) {
            throw new IOException("التنزيل غير مكتمل");
        }

        if (complete.exists() && !complete.delete()) {
            throw new IOException("تعذر استبدال ملف التنزيل");
        }
        if (!partial.renameTo(complete)) {
            throw new IOException("تعذر إنهاء ملف التنزيل");
        }

        return complete;
    }

    private static HttpURLConnection open(String source, long offset, String userAgent) throws Exception {
        URL url = new URL(source);

        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(120_000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("User-Agent", userAgent);
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            connection.connect();

            int code = connection.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) throw new IOException("Redirect بلا وجهة");
                url = new URL(url, location);
                continue;
            }

            return connection;
        }

        throw new IOException("عدد تحويلات التنزيل كبير");
    }

    private static long totalFromRange(String contentRange) {
        if (contentRange == null) return -1;
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= contentRange.length()) return -1;

        String value = contentRange.substring(slash + 1).trim();
        if ("*".equals(value)) return -1;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
