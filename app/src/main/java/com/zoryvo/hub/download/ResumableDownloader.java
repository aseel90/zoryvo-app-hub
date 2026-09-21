package com.zoryvo.hub.download;

import com.zoryvo.hub.model.AppItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ResumableDownloader {
    public interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public interface CancelChecker {
        boolean isCancelled();
    }

    private ResumableDownloader() {}

    public static File partialFile(File cacheDir, AppItem app) {
        File dir = new File(cacheDir, "updates");
        return new File(dir, app.id + "-" + app.versionCode + ".apk.part");
    }

    public static File finalFile(File dir, AppItem app) {
        return new File(dir, app.id + "-" + app.versionCode + ".apk");
    }

    // One APK transfer at a time keeps partial files deterministic on rotations/retries.
    public static synchronized File download(
            File dir,
            AppItem app,
            String userAgent,
            Listener listener,
            CancelChecker cancelChecker) throws Exception {

        File partial = new File(dir, app.id + "-" + app.versionCode + ".apk.part");
        File complete = finalFile(dir, app);

        checkCancelled(cancelChecker);
        if (complete.isFile() && complete.length() > 1024) return complete;

        long existing = partial.isFile() ? partial.length() : 0L;
        HttpURLConnection connection = open(app.apkUrl, existing, userAgent);
        int code = connection.getResponseCode();

        if (code == 416 && existing > 0) {
            long serverTotal = totalFromRange(connection.getHeaderField("Content-Range"));
            connection.disconnect();
            if (serverTotal > 0 && existing == serverTotal) {
                checkCancelled(cancelChecker);
                promote(partial, complete);
                return complete;
            }
            //noinspection ResultOfMethodCallIgnored
            partial.delete();
            existing = 0;
            connection = open(app.apkUrl, 0, userAgent);
            code = connection.getResponseCode();
        }

        if (existing > 0 && code == HttpURLConnection.HTTP_PARTIAL) {
            long responseStart = startFromRange(connection.getHeaderField("Content-Range"));
            if (responseStart != existing) {
                connection.disconnect();
                // A mismatched range would corrupt the APK if appended.
                //noinspection ResultOfMethodCallIgnored
                partial.delete();
                existing = 0;
                connection = open(app.apkUrl, 0, userAgent);
                code = connection.getResponseCode();
            }
        }

        boolean append = existing > 0 && code == HttpURLConnection.HTTP_PARTIAL;
        if (existing > 0 && code == HttpURLConnection.HTTP_OK) {
            // Server does not support resuming; restart safely instead of appending.
            existing = 0;
            append = false;
        }

        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }

        long total = totalFromRange(connection.getHeaderField("Content-Range"));
        if (total <= 0) {
            long length = parseLongHeader(connection.getHeaderField("Content-Length"));
            total = length > 0 ? existing + length : -1;
        }

        long done = append ? existing : 0;
        if (listener != null) listener.onProgress(done, total);

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(partial, append)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                checkCancelled(cancelChecker);
                output.write(buffer, 0, read);
                done += read;
                if (listener != null) listener.onProgress(done, total);
            }
            output.flush();
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        checkCancelled(cancelChecker);
        if (total > 0 && partial.length() < total) {
            throw new IOException("التنزيل غير مكتمل");
        }

        promote(partial, complete);
        return complete;
    }

    private static void promote(File partial, File complete) throws IOException {
        if (complete.exists() && !complete.delete()) {
            throw new IOException("تعذر استبدال ملف التنزيل");
        }
        if (!partial.renameTo(complete)) {
            throw new IOException("تعذر إنهاء ملف التنزيل");
        }
    }

    private static void checkCancelled(CancelChecker checker) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || (checker != null && checker.isCancelled())) {
            throw new InterruptedIOException("تم إيقاف التنزيل");
        }
    }

    private static HttpURLConnection open(String source, long offset, String userAgent) throws Exception {
        URL url = new URL(source);

        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(120_000);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", userAgent);
            if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-");
            connection.connect();

            int code = connection.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect بلا وجهة");
                }
                url = new URL(url, location);
                continue;
            }

            return connection;
        }

        throw new IOException("عدد تحويلات التنزيل كبير");
    }

    private static long startFromRange(String contentRange) {
        if (contentRange == null) return -1;
        String value = contentRange.trim();
        if (!value.startsWith("bytes ")) return -1;
        int dash = value.indexOf('-', 6);
        int slash = value.lastIndexOf('/');
        if (dash < 0 || slash < 0 || dash >= slash) return -1;
        String start = value.substring(6, dash).trim();
        if (start.isEmpty() || "*".equals(start)) return -1;
        return parseLongHeader(start);
    }

    private static long totalFromRange(String contentRange) {
        if (contentRange == null) return -1;
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= contentRange.length()) return -1;
        String value = contentRange.substring(slash + 1).trim();
        if ("*".equals(value)) return -1;
        return parseLongHeader(value);
    }

    private static long parseLongHeader(String value) {
        if (value == null) return -1;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}