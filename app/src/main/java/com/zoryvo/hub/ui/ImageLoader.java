package com.zoryvo.hub.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.zoryvo.hub.R;
import com.zoryvo.hub.model.AppItem;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final int MAX_ICON_BYTES = 2 * 1024 * 1024;
    private static final int TARGET_ICON_PX = 256;
    private static final int CACHE_KB = 4 * 1024;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() {}

    public static void load(ImageView target, AppItem app) {
        int fallback = fallback(app.id);
        target.setTag(null);
        target.setImageResource(fallback);
        if (app.iconUrl == null || app.iconUrl.isEmpty()) return;

        Bitmap cached = CACHE.get(app.iconUrl);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setTag(app.iconUrl);
        POOL.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(app.iconUrl).openConnection();
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Zoryvo-IconLoader");
                connection.connect();

                int code = connection.getResponseCode();
                if (code < 200 || code > 299) return;

                byte[] bytes;
                try (InputStream input = connection.getInputStream()) {
                    bytes = readBounded(input, MAX_ICON_BYTES);
                }
                if (bytes == null || bytes.length == 0) return;

                Bitmap bitmap = decodeScaled(bytes, TARGET_ICON_PX);
                if (bitmap == null) return;
                CACHE.put(app.iconUrl, bitmap);

                MAIN.post(() -> {
                    Object tag = target.getTag();
                    if (app.iconUrl.equals(tag)) {
                        target.setImageBitmap(bitmap);
                    }
                });
            } catch (Exception ignored) {
                // Keep the bundled fallback icon.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maxBytes) return null;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Bitmap decodeScaled(byte[] bytes, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while ((bounds.outWidth / sample) > maxDimension * 2
                || (bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static int fallback(String id) {
        if ("selyro-tv".equals(id)) return R.drawable.selyro_tv_icon;
        if ("bubble-safari-tv".equals(id)) return R.drawable.bubble_safari_icon;
        if ("zoryvo-hub".equals(id)) return R.drawable.zoryvo_icon;
        return R.drawable.generic_app_icon;
    }
}