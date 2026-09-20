package com.zoryvo.hub.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.zoryvo.hub.R;
import com.zoryvo.hub.model.AppItem;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(8);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ImageLoader() {}

    public static void load(ImageView target, AppItem app) {
        int fallback = fallback(app.id);
        target.setImageResource(fallback);
        if (app.iconUrl == null || app.iconUrl.isEmpty()) return;

        Bitmap cached = CACHE.get(app.iconUrl);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        target.setTag(app.iconUrl);
        POOL.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(app.iconUrl).openConnection();
                c.setConnectTimeout(10_000);
                c.setReadTimeout(20_000);
                c.setInstanceFollowRedirects(true);
                c.connect();
                if (c.getResponseCode() < 200 || c.getResponseCode() > 299) return;

                Bitmap bitmap = BitmapFactory.decodeStream(c.getInputStream());
                if (bitmap == null) return;
                CACHE.put(app.iconUrl, bitmap);

                MAIN.post(() -> {
                    Object tag = target.getTag();
                    if (app.iconUrl.equals(tag)) target.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private static int fallback(String id) {
        if ("selyro-tv".equals(id)) return R.drawable.selyro_tv_icon;
        if ("bubble-safari-tv".equals(id)) return R.drawable.bubble_safari_icon;
        if ("zoryvo-hub".equals(id)) return R.drawable.zoryvo_icon;
        return R.drawable.generic_app_icon;
    }
}
