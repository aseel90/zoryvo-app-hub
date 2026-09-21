package com.zoryvo.hub.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AppItem {
    public final String id;
    public final String name;
    public final String packageName;
    public final long versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String iconUrl;
    public final String notes;
    public final String sha256;
    public final String kind;
    public final boolean enabled;
    public final List<String> formFactors;

    public AppItem(String id, String name, String packageName, long versionCode,
                   String versionName, String apkUrl, String iconUrl, String notes,
                   String sha256, String kind, boolean enabled, List<String> formFactors) {
        this.id = id;
        this.name = name;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.iconUrl = iconUrl;
        this.notes = notes;
        this.sha256 = sha256;
        this.kind = kind;
        this.enabled = enabled;
        this.formFactors = formFactors;
    }

    public boolean isHub(String ownPackage) {
        return "hub".equalsIgnoreCase(kind) || ownPackage.equals(packageName);
    }

    public boolean isValid() {
        return id != null && !id.trim().isEmpty()
                && name != null && !name.trim().isEmpty()
                && packageName != null && packageName.contains(".")
                && versionCode > 0
                && apkUrl != null && apkUrl.startsWith("https://");
    }

    public static AppItem from(JSONObject o) {
        long code = o.optLong("versionCode", 1);
        List<String> formFactors = new ArrayList<>();
        JSONArray values = o.optJSONArray("formFactors");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i, "").trim();
                if (!value.isEmpty()) formFactors.add(value);
            }
        }

        return new AppItem(
                o.optString("id", "").trim(),
                o.optString("name", "").trim(),
                o.optString("packageName", "").trim(),
                code,
                o.optString("versionName", Long.toString(code)).trim(),
                o.optString("apkUrl", "").trim(),
                o.optString("iconUrl", "").trim(),
                o.optString("notes", "").trim(),
                o.optString("sha256", "").trim(),
                o.optString("kind", "app").trim(),
                o.optBoolean("enabled", true),
                formFactors
        );
    }
}