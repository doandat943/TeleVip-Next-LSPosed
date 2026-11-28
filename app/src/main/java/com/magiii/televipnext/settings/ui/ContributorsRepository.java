package com.magiii.televipnext.settings.ui;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.magiii.televipnext.BuildConfig;
import com.magiii.televipnext.Configs.ConfigPreferences;
import com.magiii.televipnext.logging.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches and caches GitHub repository contributors with ETag conditional requests
 * and TTL throttling to stay well within unauthenticated API rate limits.
 */
public final class ContributorsRepository {

    public static final class Contributor {
        public final String login;
        public final String url;
        public final int contributions;

        public Contributor(String login, String url, int contributions) {
            this.login = login;
            this.url = url;
            this.contributions = contributions;
        }
    }

    public static final class ShareSegment {
        /** Index into contributors list, or {@code -1} for the aggregated tail segment. */
        public final int contributorIndex;
        public final float fraction;

        public ShareSegment(int contributorIndex, float fraction) {
            this.contributorIndex = contributorIndex;
            this.fraction = fraction;
        }
    }

    public interface Listener {
        void onContributorsUpdated(List<Contributor> contributors, boolean loading);
    }

    public static final String CONTRIBUTORS_WEB_URL =
            "https://github.com/doandat943/TeleVip-Next-LSPosed/graphs/contributors";

    private static final String CONTRIBUTORS_API_URL =
            "https://api.github.com/repos/doandat943/TeleVip-Next-LSPosed/contributors?per_page=100";
    private static final String GITHUB_PREFIX = "https://github.com/";

    private static final String PREF_ETAG = "ContributorsETag";
    private static final String PREF_CACHED_JSON = "ContributorsCachedJson";
    private static final String PREF_LAST_FETCH_MS = "ContributorsLastFetchMs";

    private static final long CACHE_TTL_MS = 6L * 60L * 60L * 1000L;

    private static final CopyOnWriteArrayList<WeakReference<Listener>> LISTENERS =
            new CopyOnWriteArrayList<>();

    private static ExecutorService executor;
    private static OkHttpClient client;
    private static Handler mainHandler;

    private static volatile List<Contributor> memoryCache = null;
    private static final AtomicBoolean FETCH_RUNNING = new AtomicBoolean(false);
    private static volatile long lastFetchMs = 0L;

    private ContributorsRepository() {}

    /** Returns the cached list of contributors synchronously (from memory or persisted JSON). */
    public static List<Contributor> getCached() {
        List<Contributor> cached = memoryCache;
        if (cached != null) {
            return cached;
        }
        String raw = ConfigPreferences.getString(PREF_CACHED_JSON);
        List<Contributor> parsed = parseContributorsJson(raw);
        if (!parsed.isEmpty()) {
            memoryCache = parsed;
            return parsed;
        }
        return Collections.emptyList();
    }

    public static boolean isLoading() {
        return FETCH_RUNNING.get();
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        for (WeakReference<Listener> ref : LISTENERS) {
            Listener existing = ref.get();
            if (existing == null) {
                LISTENERS.remove(ref);
            } else if (existing == listener) {
                return;
            }
        }
        LISTENERS.add(new WeakReference<>(listener));
    }

    public static void removeListener(Listener listener) {
        if (listener == null) return;
        for (WeakReference<Listener> ref : LISTENERS) {
            Listener existing = ref.get();
            if (existing == null || existing == listener) {
                LISTENERS.remove(ref);
            }
        }
    }

    /** Triggers a background refresh if cached data is absent or older than the TTL. */
    public static void refreshIfNeeded() {
        List<Contributor> cached = getCached();
        long now = System.currentTimeMillis();
        long effectiveLastFetch = resolveLastFetchMs();
        if (!cached.isEmpty() && effectiveLastFetch > 0L) {
            long elapsed = now - effectiveLastFetch;
            if (elapsed >= 0L && elapsed < CACHE_TTL_MS) {
                return;
            }
        }
        if (!FETCH_RUNNING.compareAndSet(false, true)) {
            return;
        }
        notifyListeners(cached, true);

        getExecutor().execute(() -> {
            try {
                fetchFromGitHub();
            } catch (Throwable t) {
                Logger.e(t);
            } finally {
                FETCH_RUNNING.set(false);
                notifyListeners(getCached(), false);
            }
        });
    }

    /**
     * Parses a GitHub {@code /contributors} JSON array into a deduplicated, descending-sorted
     * list of non-bot user contributors. Safe to run in pure JVM unit tests without Android stubs.
     */
    public static List<Contributor> parseContributorsJson(String raw) {
        if (isBlank(raw)) {
            return Collections.emptyList();
        }
        try {
            JsonElement root = JsonParser.parseString(raw);
            if (!root.isJsonArray()) {
                return Collections.emptyList();
            }
            JsonArray arr = root.getAsJsonArray();
            Map<String, Contributor> dedup = new LinkedHashMap<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                String type = optString(obj, "type");
                if (!isBlank(type) && !"User".equalsIgnoreCase(type)) continue;

                String login = optString(obj, "login").trim();
                if (isBlank(login)) continue;
                if (login.toLowerCase(Locale.ROOT).endsWith("[bot]")) continue;

                int contributions = optInt(obj, "contributions", 0);
                if (contributions <= 0) continue;

                String htmlUrl = optString(obj, "html_url").trim();
                String resolvedUrl = isValidGitHubUrl(htmlUrl) ? htmlUrl : (GITHUB_PREFIX + login);

                String key = login.toLowerCase(Locale.ROOT);
                Contributor prev = dedup.get(key);
                if (prev == null || contributions > prev.contributions) {
                    dedup.put(key, new Contributor(login, resolvedUrl, contributions));
                }
            }

            if (dedup.isEmpty()) {
                return Collections.emptyList();
            }

            List<Contributor> out = new ArrayList<>(dedup.values());
            out.sort((a, b) -> {
                int cmp = Integer.compare(b.contributions, a.contributions);
                return cmp != 0 ? cmp : a.login.compareToIgnoreCase(b.login);
            });
            return Collections.unmodifiableList(out);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    /**
     * Computes proportional share segments for the contribution bar, keeping up to
     * {@code maxDistinct} significant contributors separate and collapsing the rest into a tail.
     */
    public static List<ShareSegment> computeShareSegments(
            List<Contributor> contributors,
            int maxDistinct,
            float minFraction) {
        if (contributors == null || contributors.isEmpty()) {
            return Collections.emptyList();
        }
        long total = 0L;
        for (Contributor c : contributors) {
            if (c != null && c.contributions > 0) {
                total += c.contributions;
            }
        }
        if (total <= 0L) {
            return Collections.emptyList();
        }
        int n = contributors.size();
        List<ShareSegment> segments = new ArrayList<>();
        long otherSum = 0L;
        for (int i = 0; i < n; i++) {
            Contributor c = contributors.get(i);
            int val = c != null ? Math.max(0, c.contributions) : 0;
            if (val == 0) continue;
            float frac = val / (float) total;
            if (i < maxDistinct && (i == 0 || frac >= minFraction || (i == n - 1 && otherSum == 0L))) {
                segments.add(new ShareSegment(i, frac));
            } else {
                otherSum += val;
            }
        }
        if (otherSum > 0L) {
            segments.add(new ShareSegment(-1, otherSum / (float) total));
        }
        return Collections.unmodifiableList(segments);
    }

    public static int computeSharePercent(int contributions, int totalContributions) {
        if (contributions <= 0 || totalContributions <= 0) {
            return 0;
        }
        int rounded = (int) Math.round(contributions * 100.0 / totalContributions);
        return Math.max(1, Math.min(100, rounded));
    }

    private static void fetchFromGitHub() {
        String savedEtag = ConfigPreferences.getString(PREF_ETAG);
        String savedJson = ConfigPreferences.getString(PREF_CACHED_JSON);
        boolean hasCache = !isBlank(savedEtag) && !isBlank(savedJson);

        Request.Builder rb = new Request.Builder()
                .url(CONTRIBUTORS_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME);
        if (hasCache) {
            rb.header("If-None-Match", savedEtag);
        }

        try (Response resp = getClient().newCall(rb.build()).execute()) {
            int code = resp.code();
            if (code == 304) {
                recordFetchTimestamp(System.currentTimeMillis());
                return;
            }
            if (code == 200 && resp.body() != null) {
                String body = resp.body().string();
                List<Contributor> parsed = parseContributorsJson(body);
                if (!parsed.isEmpty()) {
                    memoryCache = parsed;
                    String etag = resp.header("ETag");
                    if (!isBlank(etag)) {
                        ConfigPreferences.putString(PREF_ETAG, etag);
                    }
                    ConfigPreferences.putString(PREF_CACHED_JSON, body);
                    recordFetchTimestamp(System.currentTimeMillis());
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static long resolveLastFetchMs() {
        long mem = lastFetchMs;
        if (mem > 0L) return mem;
        String raw = ConfigPreferences.getString(PREF_LAST_FETCH_MS);
        if (isBlank(raw)) return 0L;
        try {
            long parsed = Long.parseLong(raw.trim());
            lastFetchMs = parsed;
            return parsed;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static void recordFetchTimestamp(long ts) {
        lastFetchMs = ts;
        ConfigPreferences.putString(PREF_LAST_FETCH_MS, String.valueOf(ts));
    }

    private static void notifyListeners(List<Contributor> contributors, boolean loading) {
        postToMain(() -> {
            for (WeakReference<Listener> ref : LISTENERS) {
                Listener l = ref.get();
                if (l == null) {
                    LISTENERS.remove(ref);
                } else {
                    try {
                        l.onContributorsUpdated(contributors, loading);
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            }
        });
    }

    private static void postToMain(Runnable r) {
        Handler h;
        synchronized (ContributorsRepository.class) {
            if (mainHandler == null) {
                mainHandler = new Handler(Looper.getMainLooper());
            }
            h = mainHandler;
        }
        h.post(r);
    }

    private static synchronized OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    private static synchronized ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        return executor;
    }

    private static String optString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return "";
        try {
            return el.getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int optInt(JsonObject obj, String key, int fallback) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return fallback;
        try {
            return el.getAsInt();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean isValidGitHubUrl(String url) {
        if (!url.startsWith(GITHUB_PREFIX)) return false;
        for (int i = 0; i < url.length(); i++) {
            if (Character.isWhitespace(url.charAt(i))) return false;
        }
        return url.length() > GITHUB_PREFIX.length();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
