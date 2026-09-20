package com.zoryvo.hub.network;

import com.zoryvo.hub.model.AppItem;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class CatalogClient {
    private CatalogClient() {}

    public static List<AppItem> fetch(String url, String versionName) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("User-Agent", "Zoryvo-App-Hub/" + versionName);
        connection.connect();

        if (connection.getResponseCode() < 200 || connection.getResponseCode() > 299)
            throw new IllegalStateException("HTTP " + connection.getResponseCode());

        String text;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            text = out.toString();
        } finally {
            connection.disconnect();
        }

        Object root = new JSONTokener(text).nextValue();
        JSONArray array;
        if (root instanceof JSONArray) {
            array = (JSONArray) root;
        } else {
            array = ((JSONObject) root).optJSONArray("apps");
            if (array == null) throw new IllegalStateException("صيغة الكتالوج غير صحيحة");
        }

        List<AppItem> apps = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) apps.add(AppItem.from(item));
        }
        return apps;
    }
}
