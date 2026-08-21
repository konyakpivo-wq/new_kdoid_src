package com.newkdroid.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CatalogManager {
    public static final String CATALOG_URL = "https://raw.githubusercontent.com/konyakpivo-wq/new_kdord/main/catalog.json";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface Callback { void onSuccess(List<AppEntry> apps, int version); void onError(Exception error); }
    public interface ReleasesCallback { void onSuccess(List<ReleaseEntry> releases); void onError(Exception error); }

    public static final class AppEntry {
        public final String name, repository, category, description;
        public AppEntry(String name, String repository, String category, String description) {
            this.name = name; this.repository = repository; this.category = category; this.description = description;
        }
    }

    public static final class ReleaseEntry {
        public final String tag, name, htmlUrl, publishedAt;
        public final boolean prerelease;
        public ReleaseEntry(String tag, String name, String htmlUrl, String publishedAt, boolean prerelease) {
            this.tag = tag; this.name = name; this.htmlUrl = htmlUrl; this.publishedAt = publishedAt; this.prerelease = prerelease;
        }
        @Override public String toString() { return (name == null || name.isEmpty()) ? tag : name + " (" + tag + ")"; }
    }

    public void loadCatalog(Callback callback) {
        executor.execute(() -> {
            try {
                String text = get(CATALOG_URL);
                JSONObject root = new JSONObject(text);
                int version = root.optInt("catalogVersion", 1);
                JSONArray array = root.getJSONArray("apps");
                List<AppEntry> apps = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    apps.add(new AppEntry(o.optString("name"), o.optString("repository"), o.optString("category"), o.optString("description")));
                }
                callback.onSuccess(apps, version);
            } catch (Exception e) { callback.onError(e); }
        });
    }

    public void loadReleases(String repository, ReleasesCallback callback) {
        executor.execute(() -> {
            try {
                String text = get("https://api.github.com/repos/" + repository + "/releases?per_page=10");
                JSONArray array = new JSONArray(text);
                List<ReleaseEntry> releases = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    if (o.optBoolean("draft", false)) continue;
                    releases.add(new ReleaseEntry(o.optString("tag_name"), o.optString("name"), o.optString("html_url"), o.optString("published_at"), o.optBoolean("prerelease", false)));
                }
                callback.onSuccess(releases);
            } catch (Exception e) { callback.onError(e); }
        });
    }

    private String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "New-KDroid/0.1");
        try {
            int code = c.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            if (stream == null) throw new IllegalStateException("GitHub returned HTTP " + code);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); String line;
            while ((line = reader.readLine()) != null) out.append(line);
            if (code < 200 || code >= 300) throw new IllegalStateException("GitHub returned HTTP " + code + ": " + out);
            return out.toString();
        } finally { c.disconnect(); }
    }
}
