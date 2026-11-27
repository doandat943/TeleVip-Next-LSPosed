package com.magiii.televipnext.updater;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.magiii.televipnext.BuildConfig;
import com.magiii.televipnext.Configs.ConfigPreferences;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.settings.ui.GlassDialog;
import com.magiii.televipnext.settings.ui.UpdateBanner;
import com.magiii.televipnext.ui.RestartTrampolineActivity;
import com.magiii.televipnext.ui.UpdateInstallerActivity;
import com.magiii.televipnext.utils.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Handles rate-limit-safe GitHub Release checking, silent background APK pre-downloading,
 * SHA-256 & package verification, and 1-tap Liquid Glass notification banners.
 */
public final class UpdateManager {

    public enum State {
        IDLE,
        CHECKING,
        DOWNLOADING,
        READY_TO_INSTALL,
        UP_TO_DATE,
        ERROR,
        INSTALLED_NEEDS_RESTART
    }

    public static final class ReleaseInfo {
        public final String tag;
        public final String version;
        public final String changelog;
        public final String apkUrl;
        public final String apkName;
        public final long apkSize;
        public final String apkSha256;
        public final String htmlUrl;

        ReleaseInfo(
                String tag,
                String version,
                String changelog,
                String apkUrl,
                String apkName,
                long apkSize,
                String apkSha256,
                String htmlUrl) {
            this.tag = tag;
            this.version = version;
            this.changelog = changelog;
            this.apkUrl = apkUrl;
            this.apkName = apkName;
            this.apkSize = apkSize;
            this.apkSha256 = apkSha256;
            this.htmlUrl = htmlUrl;
        }
    }

    public interface Listener {
        void onUpdateStateChanged(State state, ReleaseInfo release);
    }

    private static final String MODULE_PACKAGE = "com.magiii.televipnext";
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/doandat943/TeleVip-Next-LSPosed/releases/latest";
    private static final String RELEASES_LATEST_URL =
            "https://github.com/doandat943/TeleVip-Next-LSPosed/releases/latest";

    private static final String PREF_ETAG = "UpdateETag";
    private static final String PREF_CACHED_JSON = "UpdateCachedReleaseJson";

    private static final long RESUME_DEBOUNCE_MS = 20_000L;
    private static final long TELEVIP_OPEN_DEBOUNCE_MS = 15_000L;
    private static final long MIN_APK_BYTES = 100L * 1024L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<WeakReference<Listener>> LISTENERS = new CopyOnWriteArrayList<>();

    private static final OkHttpClient API_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final OkHttpClient HEAD_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(false)
            .build();

    private static final OkHttpClient DOWNLOAD_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    private static volatile State currentState = State.IDLE;
    private static volatile ReleaseInfo latestRelease = null;
    private static volatile File readyApkFile = null;
    private static volatile String readyApkAuthority = null;
    private static volatile String cachedProviderAuthority = null;
    private static volatile File cachedUpdateDir = null;
    private static volatile boolean providerProbed = false;
    private static volatile String dismissedBannerTag = null;
    private static volatile String launchedInstallTag = null;
    private static volatile String installedNewVersionLabel = null;
    private static volatile long lastAutoCheckMs = 0L;
    private static volatile boolean checkRunning = false;

    private UpdateManager() {}

    public static State getState() {
        return currentState;
    }

    public static ReleaseInfo getLatestRelease() {
        return latestRelease;
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        for (WeakReference<Listener> ref : LISTENERS) {
            if (ref.get() == listener) return;
        }
        LISTENERS.add(new WeakReference<>(listener));
    }

    public static void removeListener(Listener listener) {
        if (listener == null) return;
        for (WeakReference<Listener> ref : LISTENERS) {
            Listener l = ref.get();
            if (l == null || l == listener) {
                LISTENERS.remove(ref);
            }
        }
    }

    public static void onTelegramResumed(Activity activity) {
        if (activity == null) return;
        if (checkPendingRestart(activity)) {
            showRestartBanner(activity);
            return;
        }
        if (currentState == State.READY_TO_INSTALL && latestRelease != null) {
            if (!latestRelease.tag.equals(dismissedBannerTag)) {
                showReadyBanner(activity, latestRelease);
            }
        }
        if (!isAutoCheckEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoCheckMs >= RESUME_DEBOUNCE_MS) {
            lastAutoCheckMs = now;
            checkAndPreDownload(activity, false);
        }
    }

    public static void onTeleVipOpened(Activity activity) {
        if (activity == null) return;
        if (checkPendingRestart(activity)) {
            showRestartBanner(activity);
            UpdateBanner.bringToFrontIfShown(activity);
            return;
        }
        if (currentState == State.READY_TO_INSTALL && latestRelease != null) {
            dismissedBannerTag = null;
            showReadyBanner(activity, latestRelease);
            UpdateBanner.bringToFrontIfShown(activity);
        }
        if (!isAutoCheckEnabled()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAutoCheckMs >= TELEVIP_OPEN_DEBOUNCE_MS) {
            lastAutoCheckMs = now;
            checkAndPreDownload(activity, false);
        }
    }

    public static void onCheckRowClicked(Context context) {
        Activity act = resolveActivity(context);
        if (act == null) return;

        if (checkPendingRestart(act) || currentState == State.INSTALLED_NEEDS_RESTART) {
            RestartTrampolineActivity.restartHost(act);
            return;
        }
        if (currentState == State.READY_TO_INSTALL && latestRelease != null) {
            showReleaseDialog(act, latestRelease);
            return;
        }
        checkManually(act);
    }

    public static void checkManually(Activity activity) {
        if (activity == null) return;
        if (checkPendingRestart(activity) || currentState == State.INSTALLED_NEEDS_RESTART) {
            showRestartBanner(activity);
            RestartTrampolineActivity.restartHost(activity);
            return;
        }
        if (checkRunning) {
            String msg = currentState == State.DOWNLOADING
                    ? Translator.get(Keys.UpdateDownloading)
                    : Translator.get(Keys.UpdateChecking);
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
            return;
        }
        lastAutoCheckMs = SystemClock.elapsedRealtime();
        checkAndPreDownload(activity, true);
    }

    public static void launchInstall(Activity activity) {
        if (activity == null || latestRelease == null) return;
        try {
            ReleaseInfo info = latestRelease;
            Intent intent = new Intent()
                    .setClassName(MODULE_PACKAGE, UpdateInstallerActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(UpdateInstallerActivity.EXTRA_VERSION, info.tag)
                    .putExtra(UpdateInstallerActivity.EXTRA_DOWNLOAD_URL, info.apkUrl)
                    .putExtra(UpdateInstallerActivity.EXTRA_MSG_SIGN_MISMATCH,
                            Translator.get(Keys.UpdateSignatureMismatch))
                    .putExtra(UpdateInstallerActivity.EXTRA_MSG_ALLOW_UNKNOWN,
                            Translator.get(Keys.UpdateAllowUnknownSources))
                    .putExtra(UpdateInstallerActivity.EXTRA_MSG_DOWNLOADING,
                            Translator.get(Keys.UpdateDownloading))
                    .putExtra(UpdateInstallerActivity.EXTRA_MSG_FAILED,
                            Translator.get(Keys.UpdateCheckFailed));

            if (!TextUtils.isEmpty(info.apkSha256)) {
                intent.putExtra(UpdateInstallerActivity.EXTRA_SHA256, info.apkSha256);
            }

            if (readyApkAuthority != null && readyApkFile != null && readyApkFile.isFile()) {
                try {
                    Uri contentUri = FileProvider.getUriForFile(activity, readyApkAuthority, readyApkFile);
                    intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                    intent.setClipData(ClipData.newRawUri("televip_update", contentUri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }

            launchedInstallTag = info.tag;
            activity.startActivity(intent);
        } catch (Throwable t) {
            Logger.e(t);
            Toast.makeText(activity, Translator.get(Keys.UpdateCheckFailed), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isAutoCheckEnabled() {
        return !ConfigPreferences.contains(Keys.AutoUpdateCheck)
                || ConfigPreferences.getBoolean(Keys.AutoUpdateCheck);
    }

    private static void checkAndPreDownload(Activity activity, boolean manual) {
        if (checkRunning) return;
        Context appCtx = resolveAppContext(activity);
        if (appCtx == null) return;

        checkRunning = true;
        final WeakReference<Activity> actRef = new WeakReference<>(activity);
        if (currentState != State.READY_TO_INSTALL) {
            updateState(State.CHECKING, latestRelease);
        }

        EXECUTOR.execute(() -> {
            try {
                ReleaseInfo release = fetchLatestRelease();
                if (release == null) {
                    if (currentState != State.READY_TO_INSTALL) {
                        updateState(State.ERROR, latestRelease);
                    }
                    if (manual) {
                        MAIN.post(() -> {
                            Activity act = resolveForegroundActivity(actRef.get());
                            if (act != null) {
                                Toast.makeText(act, Translator.get(Keys.UpdateCheckFailed), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }

                latestRelease = release;

                if (!UpdateVersion.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    cleanUpdateDir(resolveUpdateDir(appCtx));
                    readyApkFile = null;
                    readyApkAuthority = null;
                    updateState(State.UP_TO_DATE, release);
                    if (manual) {
                        MAIN.post(() -> {
                            Activity act = resolveForegroundActivity(actRef.get());
                            if (act != null) {
                                Toast.makeText(act, Translator.get(Keys.UpdateUpToDate), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }

                ensureProviderResolved(appCtx);
                File updateDir = resolveUpdateDir(appCtx);
                if (!updateDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    updateDir.mkdirs();
                }

                File targetApk = new File(updateDir,
                        "TeleVip-Next-" + UpdateVersion.sanitizeVersionToken(release.tag) + ".apk");

                if (!verifyDownloadedApk(targetApk, release.apkSize, release.apkSha256, appCtx)) {
                    if (cachedProviderAuthority != null && UpdateVersion.isAllowedDownloadUrl(release.apkUrl)) {
                        updateState(State.DOWNLOADING, release);
                        boolean ok = downloadApk(release, targetApk, updateDir, appCtx);
                        if (!ok) {
                            readyApkFile = null;
                            readyApkAuthority = null;
                            // Still allow manual/1-tap handoff to UpdateInstallerActivity fallback downloader
                            updateState(State.READY_TO_INSTALL, release);
                        } else {
                            pruneOtherFiles(updateDir, targetApk);
                            readyApkFile = targetApk;
                            readyApkAuthority = cachedProviderAuthority;
                            updateState(State.READY_TO_INSTALL, release);
                        }
                    } else {
                        // Host does not expose a compatible FileProvider; installer will download directly on tap
                        readyApkFile = null;
                        readyApkAuthority = null;
                        updateState(State.READY_TO_INSTALL, release);
                    }
                } else {
                    pruneOtherFiles(updateDir, targetApk);
                    readyApkFile = targetApk;
                    readyApkAuthority = cachedProviderAuthority;
                    updateState(State.READY_TO_INSTALL, release);
                }

                MAIN.post(() -> {
                    Activity fg = resolveForegroundActivity(actRef.get());
                    if (fg == null) return;
                    if (checkPendingRestart(fg)) {
                        showRestartBanner(fg);
                        return;
                    }
                    if (manual) {
                        dismissedBannerTag = null;
                        showReadyBanner(fg, release);
                        showReleaseDialog(fg, release);
                    } else if (!release.tag.equals(dismissedBannerTag)) {
                        showReadyBanner(fg, release);
                    }
                });
            } catch (Throwable t) {
                Logger.e(t);
                if (currentState != State.READY_TO_INSTALL) {
                    updateState(State.ERROR, latestRelease);
                }
                if (manual) {
                    MAIN.post(() -> {
                        Activity fg = resolveForegroundActivity(actRef.get());
                        if (fg != null) {
                            Toast.makeText(fg, Translator.get(Keys.UpdateCheckFailed), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } finally {
                checkRunning = false;
            }
        });
    }

    private static ReleaseInfo fetchLatestRelease() {
        String savedEtag = ConfigPreferences.getString(PREF_ETAG);
        Request.Builder rb = new Request.Builder()
                .url(RELEASES_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME);
        if (!TextUtils.isEmpty(savedEtag)) {
            rb.header("If-None-Match", savedEtag);
        }

        try (Response resp = API_CLIENT.newCall(rb.build()).execute()) {
            int code = resp.code();
            if (code == 304) {
                String cachedJson = ConfigPreferences.getString(PREF_CACHED_JSON);
                ReleaseInfo cached = parseReleaseJson(cachedJson);
                if (cached != null) return cached;
                // Cache missing despite ETag; re-fetch without If-None-Match once
                Request retry = new Request.Builder()
                        .url(RELEASES_API_URL)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME)
                        .build();
                try (Response r2 = API_CLIENT.newCall(retry).execute()) {
                    if (r2.isSuccessful() && r2.body() != null) {
                        String raw = r2.body().string();
                        ReleaseInfo parsed = parseReleaseJson(raw);
                        if (parsed != null) {
                            saveReleaseCache(r2.header("ETag"), raw);
                            return parsed;
                        }
                    }
                }
                return null;
            }
            if (code == 200 && resp.body() != null) {
                String raw = resp.body().string();
                ReleaseInfo parsed = parseReleaseJson(raw);
                if (parsed != null) {
                    saveReleaseCache(resp.header("ETag"), raw);
                    return parsed;
                }
            }
            if (code == 403 || code == 429) {
                return fetchLatestViaHeadRedirect();
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return fetchLatestViaHeadRedirect();
    }

    private static void saveReleaseCache(String etag, String rawJson) {
        if (!TextUtils.isEmpty(etag)) {
            ConfigPreferences.putString(PREF_ETAG, etag);
        }
        if (!TextUtils.isEmpty(rawJson)) {
            ConfigPreferences.putString(PREF_CACHED_JSON, rawJson);
        }
    }

    private static ReleaseInfo parseReleaseJson(String raw) {
        if (TextUtils.isEmpty(raw)) return null;
        try {
            JSONObject obj = new JSONObject(raw);
            if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
                return null;
            }
            String tag = obj.optString("tag_name", "").trim();
            if (tag.isEmpty()) return null;
            String version = UpdateVersion.normalize(tag);
            String changelog = obj.optString("body", "").trim();
            String htmlUrl = obj.optString("html_url", "");

            String apkUrl = null;
            String apkName = null;
            long apkSize = 0L;
            String apkSha256 = null;

            JSONArray assets = obj.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                    String url = asset.optString("browser_download_url", "");
                    if (!UpdateVersion.isAllowedDownloadUrl(url)) continue;

                    boolean preferred = name.contains("TeleVip-Next");
                    if (apkUrl == null || preferred) {
                        apkUrl = url;
                        apkName = name;
                        apkSize = asset.optLong("size", 0L);
                        apkSha256 = extractSha256Digest(asset.optString("digest", ""));
                        if (preferred) break;
                    }
                }
            }

            if (apkUrl == null) return null;
            return new ReleaseInfo(tag, version, changelog, apkUrl, apkName, apkSize, apkSha256, htmlUrl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractSha256Digest(String digest) {
        if (TextUtils.isEmpty(digest)) return null;
        String d = digest.trim();
        if (d.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
            d = d.substring("sha256:".length()).trim();
        }
        return d.length() == 64 ? d : null;
    }

    private static ReleaseInfo fetchLatestViaHeadRedirect() {
        Request req = new Request.Builder()
                .url(RELEASES_LATEST_URL)
                .head()
                .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME)
                .build();
        try (Response resp = HEAD_CLIENT.newCall(req).execute()) {
            String location = resp.header("Location");
            if (TextUtils.isEmpty(location)) return null;
            int idx = location.lastIndexOf("/releases/tag/");
            if (idx < 0) return null;
            String tag = location.substring(idx + "/releases/tag/".length()).trim();
            int queryIdx = tag.indexOf('?');
            if (queryIdx >= 0) tag = tag.substring(0, queryIdx);
            if (tag.isEmpty()) return null;

            String version = UpdateVersion.normalize(tag);
            String apkName = "TeleVip-Next-" + tag + ".apk";
            String apkUrl = UpdateVersion.ALLOWED_DOWNLOAD_PREFIX + tag + "/" + apkName;
            return new ReleaseInfo(tag, version, "", apkUrl, apkName, 0L, null, location);
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static boolean downloadApk(ReleaseInfo release, File targetApk, File updateDir, Context appCtx) {
        File part = new File(updateDir, targetApk.getName() + ".part");
        deleteQuietly(part);
        Request req = new Request.Builder()
                .url(release.apkUrl)
                .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME)
                .build();
        try (Response resp = DOWNLOAD_CLIENT.newCall(req).execute()) {
            if (!resp.isSuccessful()) return false;
            ResponseBody body = resp.body();
            if (body == null) return false;
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            }
            if (!verifyDownloadedApk(part, release.apkSize, release.apkSha256, appCtx)) {
                deleteQuietly(part);
                return false;
            }
            if (targetApk.exists() && !targetApk.delete()) {
                deleteQuietly(part);
                return false;
            }
            return part.renameTo(targetApk);
        } catch (Throwable t) {
            Logger.e(t);
            deleteQuietly(part);
            return false;
        }
    }

    private static boolean verifyDownloadedApk(File file, long expectedSize, String expectedSha256, Context ctx) {
        if (file == null || !file.isFile() || file.length() < MIN_APK_BYTES) {
            return false;
        }
        if (expectedSize > 0 && file.length() != expectedSize) {
            return false;
        }
        if (!TextUtils.isEmpty(expectedSha256) && expectedSha256.length() == 64) {
            String actual = computeSha256(file);
            if (actual == null || !expectedSha256.equalsIgnoreCase(actual)) {
                return false;
            }
        }
        try {
            PackageInfo archiveInfo = ctx.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (archiveInfo == null || !MODULE_PACKAGE.equals(archiveInfo.packageName)) {
                return false;
            }
            if (!TextUtils.isEmpty(archiveInfo.versionName)
                    && !UpdateVersion.isNewer(archiveInfo.versionName, BuildConfig.VERSION_NAME)) {
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static synchronized void ensureProviderResolved(Context appCtx) {
        if (providerProbed) return;
        providerProbed = true;
        try {
            PackageInfo pi = appCtx.getPackageManager().getPackageInfo(
                    appCtx.getPackageName(), PackageManager.GET_PROVIDERS);
            if (pi == null || pi.providers == null) return;

            List<File> candidateRoots = new ArrayList<>();
            if (appCtx.getCacheDir() != null) candidateRoots.add(appCtx.getCacheDir());
            if (appCtx.getFilesDir() != null) candidateRoots.add(appCtx.getFilesDir());
            if (appCtx.getExternalCacheDir() != null) candidateRoots.add(appCtx.getExternalCacheDir());
            if (appCtx.getExternalFilesDir(null) != null) candidateRoots.add(appCtx.getExternalFilesDir(null));

            for (ProviderInfo provider : pi.providers) {
                if (provider == null || !provider.grantUriPermissions || TextUtils.isEmpty(provider.authority)) {
                    continue;
                }
                for (File root : candidateRoots) {
                    File updateDir = new File(root, "televip_updates");
                    File probe = new File(updateDir, ".probe.apk");
                    try {
                        Uri uri = FileProvider.getUriForFile(appCtx, provider.authority, probe);
                        if (uri != null) {
                            cachedProviderAuthority = provider.authority;
                            cachedUpdateDir = updateDir;
                            return;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static File resolveUpdateDir(Context appCtx) {
        if (cachedUpdateDir != null) return cachedUpdateDir;
        File cache = appCtx.getCacheDir();
        return new File(cache != null ? cache : appCtx.getFilesDir(), "televip_updates");
    }

    private static boolean checkPendingRestart(Activity activity) {
        if (currentState == State.INSTALLED_NEEDS_RESTART) return true;
        try {
            PackageManager pm = activity.getPackageManager();
            String diskVersionName = null;
            boolean upgraded = false;

            try {
                PackageInfo installed = pm.getPackageInfo(MODULE_PACKAGE, 0);
                if (installed != null) {
                    long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            ? installed.getLongVersionCode()
                            : installed.versionCode;
                    if (code > BuildConfig.VERSION_CODE) {
                        upgraded = true;
                        diskVersionName = installed.versionName;
                    }
                }
            } catch (Throwable ignored) {}

            if (!upgraded && Utils.modulePath != null) {
                File oldModuleFile = new File(Utils.modulePath);
                if (!oldModuleFile.exists()) {
                    if (launchedInstallTag != null) {
                        upgraded = true;
                        diskVersionName = launchedInstallTag;
                    }
                } else {
                    PackageInfo diskInfo = pm.getPackageArchiveInfo(Utils.modulePath, 0);
                    if (diskInfo != null) {
                        long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? diskInfo.getLongVersionCode()
                                : diskInfo.versionCode;
                        if (code > BuildConfig.VERSION_CODE) {
                            upgraded = true;
                            diskVersionName = diskInfo.versionName;
                        }
                    }
                }
            }

            if (upgraded) {
                String label = !TextUtils.isEmpty(diskVersionName)
                        ? formatTag(diskVersionName)
                        : (latestRelease != null ? latestRelease.tag : "");
                installedNewVersionLabel = label;
                cleanUpdateDir(resolveUpdateDir(activity.getApplicationContext()));
                readyApkFile = null;
                readyApkAuthority = null;
                updateState(State.INSTALLED_NEEDS_RESTART, latestRelease);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void showReadyBanner(Activity activity, ReleaseInfo info) {
        if (activity == null || info == null) return;
        String title = Translator.get(Keys.UpdateReadyTitle, info.tag);
        String subtitle = Translator.get(Keys.UpdateReadySubtitle);
        String action = Translator.get(Keys.UpdateInstallAction);
        UpdateBanner.show(
                activity,
                false,
                title,
                subtitle,
                action,
                () -> launchInstall(activity),
                () -> dismissedBannerTag = info.tag
        );
    }

    private static void showRestartBanner(Activity activity) {
        if (activity == null) return;
        String ver = !TextUtils.isEmpty(installedNewVersionLabel)
                ? installedNewVersionLabel
                : (latestRelease != null ? latestRelease.tag : "");
        String title = Translator.get(Keys.UpdateInstalledTitle, ver).trim();
        String subtitle = Translator.get(Keys.UpdateInstalledSubtitle);
        String action = Translator.get(Keys.UpdateRestartAction);
        UpdateBanner.show(
                activity,
                true,
                title,
                subtitle,
                action,
                () -> RestartTrampolineActivity.restartHost(activity),
                null
        );
    }

    private static void showReleaseDialog(Activity activity, ReleaseInfo info) {
        String body = !TextUtils.isEmpty(info.changelog)
                ? info.changelog
                : Translator.get(Keys.UpdateReadySubtitle);
        new GlassDialog.Builder(activity)
                .setShowBrandEmblem(true)
                .setTitle("TeleVip Next " + info.tag)
                .setMessage(body)
                .setPositiveButton(Translator.get(Keys.UpdateInstallAction), () -> launchInstall(activity))
                .setNegativeButton(Translator.get(Keys.Cancel), null)
                .show();
    }

    private static void updateState(State newState, ReleaseInfo info) {
        currentState = newState;
        MAIN.post(() -> {
            for (WeakReference<Listener> ref : LISTENERS) {
                Listener l = ref.get();
                if (l == null) {
                    LISTENERS.remove(ref);
                } else {
                    try {
                        l.onUpdateStateChanged(newState, info);
                    } catch (Throwable t) {
                        Logger.e(t);
                    }
                }
            }
        });
    }

    private static Activity resolveActivity(Context ctx) {
        return ctx instanceof Activity ? (Activity) ctx : Utils.getCurrentActivity();
    }

    private static Activity resolveForegroundActivity(Activity fallback) {
        Activity cur = Utils.getCurrentActivity();
        Activity chosen = (cur != null && !cur.isFinishing() && !cur.isDestroyed()) ? cur : fallback;
        if (chosen == null || chosen.isFinishing() || chosen.isDestroyed()) return null;
        return chosen;
    }

    private static Context resolveAppContext(Activity activity) {
        Context ctx = ApplicationLoaderHook.getApplicationContext();
        if (ctx != null) return ctx;
        return activity != null ? activity.getApplicationContext() : null;
    }

    private static void cleanUpdateDir(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            deleteQuietly(f);
        }
    }

    private static void pruneOtherFiles(File dir, File keep) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.equals(keep)) {
                deleteQuietly(f);
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static String computeSha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatTag(String raw) {
        String s = raw.trim();
        return (s.startsWith("v") || s.startsWith("V")) ? s : ("v" + s);
    }

    public static String getCurrentVersionTag() {
        return "v" + BuildConfig.VERSION_NAME;
    }
}
