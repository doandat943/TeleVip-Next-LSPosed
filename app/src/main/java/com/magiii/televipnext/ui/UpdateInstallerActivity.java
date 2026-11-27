package com.magiii.televipnext.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.magiii.televipnext.BuildConfig;
import com.magiii.televipnext.updater.UpdateVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Module-process translucent installer Activity.
 * Receives either a granted FileProvider APK URI pre-downloaded in the host process
 * or falls back to downloading from the verified GitHub Release URL, validates SHA-256,
 * package name, and signing certificate, and invokes Android's package installer.
 */
public class UpdateInstallerActivity extends Activity {

    public static final String EXTRA_VERSION = "version";
    public static final String EXTRA_SHA256 = "sha256";
    public static final String EXTRA_DOWNLOAD_URL = "download_url";
    public static final String EXTRA_MSG_SIGN_MISMATCH = "msg_sign_mismatch";
    public static final String EXTRA_MSG_ALLOW_UNKNOWN = "msg_allow_unknown";
    public static final String EXTRA_MSG_DOWNLOADING = "msg_downloading";
    public static final String EXTRA_MSG_FAILED = "msg_failed";

    private static final int REQ_UNKNOWN_SOURCES = 101;
    private static final int REQ_INSTALL = 102;
    private static final long MIN_APK_BYTES = 100L * 1024L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Call activeCall;
    private volatile boolean processing = false;

    private File pendingApkFile;
    private String expectedSha256;
    private String msgSignMismatch;
    private String msgAllowUnknown;
    private String msgDownloading;
    private String msgFailed;
    private Dialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!processing) {
            handleIncomingIntent(intent);
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        Call call = activeCall;
        if (call != null) {
            call.cancel();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_UNKNOWN_SOURCES) {
            if (getPackageManager().canRequestPackageInstalls()
                    && pendingApkFile != null
                    && pendingApkFile.exists()) {
                launchSystemInstaller(pendingApkFile);
            } else {
                finishSilently();
            }
        } else if (requestCode == REQ_INSTALL) {
            finishSilently();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            finishSilently();
            return;
        }

        String version = intent.getStringExtra(EXTRA_VERSION);
        expectedSha256 = trimToNull(intent.getStringExtra(EXTRA_SHA256));
        String downloadUrl = trimToNull(intent.getStringExtra(EXTRA_DOWNLOAD_URL));
        Uri sourceUri = intent.getData();

        msgSignMismatch = nonEmptyOr(intent.getStringExtra(EXTRA_MSG_SIGN_MISMATCH),
                "APK signature does not match this app");
        msgAllowUnknown = nonEmptyOr(intent.getStringExtra(EXTRA_MSG_ALLOW_UNKNOWN),
                "Please allow installing updates from TeleVip Next");
        msgDownloading = nonEmptyOr(intent.getStringExtra(EXTRA_MSG_DOWNLOADING),
                "Downloading update...");
        msgFailed = nonEmptyOr(intent.getStringExtra(EXTRA_MSG_FAILED),
                "Failed to prepare update package");

        if (sourceUri == null && !UpdateVersion.isAllowedDownloadUrl(downloadUrl)) {
            finishSilently();
            return;
        }
        if (downloadUrl != null && !UpdateVersion.isAllowedDownloadUrl(downloadUrl)) {
            finishSilently();
            return;
        }

        File updatesDir = new File(getCacheDir(), "updates");
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            showToastAndFinish(msgFailed);
            return;
        }
        File targetApk = new File(updatesDir,
                "TeleVip-Next-" + UpdateVersion.sanitizeVersionToken(version) + ".apk");

        processing = true;
        if (sourceUri == null && !targetApk.exists()) {
            showProgressDialog(msgDownloading);
        }

        executor.execute(() -> {
            try {
                boolean ready = false;
                if (verifyCandidateFile(targetApk)) {
                    ready = true;
                } else {
                    deleteQuietly(targetApk);
                    if (sourceUri != null && copyFromUri(sourceUri, targetApk, updatesDir)) {
                        ready = true;
                    } else if (UpdateVersion.isAllowedDownloadUrl(downloadUrl)
                            && downloadFromUrl(downloadUrl, targetApk, updatesDir)) {
                        ready = true;
                    }
                }

                if (!ready) {
                    if (!isDestroyed() && !isFinishing()) {
                        mainHandler.post(() -> {
                            dismissProgressDialog();
                            showToastAndFinish(msgFailed);
                        });
                    }
                    return;
                }

                pruneOtherApks(updatesDir, targetApk);
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    processing = false;
                    launchSystemInstaller(targetApk);
                });
            } catch (SignatureMismatchException e) {
                deleteQuietly(targetApk);
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    showToastAndFinish(msgSignMismatch);
                });
            } catch (Throwable t) {
                deleteQuietly(targetApk);
                mainHandler.post(() -> {
                    dismissProgressDialog();
                    showToastAndFinish(msgFailed);
                });
            }
        });
    }

    private boolean copyFromUri(Uri uri, File targetApk, File updatesDir) throws SignatureMismatchException {
        File part = new File(updatesDir, targetApk.getName() + ".part");
        deleteQuietly(part);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            try (FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            }
            if (!verifyCandidateFile(part)) {
                deleteQuietly(part);
                return false;
            }
            return moveIntoPlace(part, targetApk);
        } catch (SignatureMismatchException e) {
            deleteQuietly(part);
            throw e;
        } catch (Throwable ignored) {
            deleteQuietly(part);
            return false;
        }
    }

    private boolean downloadFromUrl(String url, File targetApk, File updatesDir) throws SignatureMismatchException {
        File part = new File(updatesDir, targetApk.getName() + ".part");
        deleteQuietly(part);

        mainHandler.post(() -> showProgressDialog(msgDownloading));

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "TeleVip-Next/" + BuildConfig.VERSION_NAME)
                .build();

        Call call = client.newCall(request);
        activeCall = call;
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) return false;
            ResponseBody body = response.body();
            if (body == null) return false;

            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(part)) {
                byte[] buf = new byte[16 * 1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                }
                out.flush();
            }

            if (!verifyCandidateFile(part)) {
                deleteQuietly(part);
                return false;
            }
            return moveIntoPlace(part, targetApk);
        } catch (SignatureMismatchException e) {
            deleteQuietly(part);
            throw e;
        } catch (Throwable ignored) {
            deleteQuietly(part);
            return false;
        } finally {
            activeCall = null;
        }
    }

    private boolean verifyCandidateFile(File apkFile) throws SignatureMismatchException {
        if (apkFile == null || !apkFile.isFile() || apkFile.length() < MIN_APK_BYTES) {
            return false;
        }
        if (expectedSha256 != null && expectedSha256.length() == 64) {
            String actualSha = computeFileSha256(apkFile);
            if (actualSha == null || !expectedSha256.equalsIgnoreCase(actualSha)) {
                return false;
            }
        }
        PackageManager pm = getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;
        PackageInfo archiveInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), flags);
        if (archiveInfo == null || !getPackageName().equals(archiveInfo.packageName)) {
            return false;
        }
        Set<String> installedCerts = extractCertHashes(getInstalledPackageInfo(pm, flags));
        Set<String> candidateCerts = extractCertHashes(archiveInfo);
        if (installedCerts.isEmpty() || candidateCerts.isEmpty() || !installedCerts.equals(candidateCerts)) {
            throw new SignatureMismatchException();
        }
        return true;
    }

    @SuppressWarnings("deprecation")
    private PackageInfo getInstalledPackageInfo(PackageManager pm, int flags) {
        try {
            return pm.getPackageInfo(getPackageName(), flags);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> extractCertHashes(PackageInfo info) {
        Set<String> hashes = new HashSet<>();
        if (info == null) return hashes;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SigningInfo si = info.signingInfo;
            if (si != null) {
                Signature[] signers = si.hasMultipleSigners()
                        ? si.getApkContentsSigners()
                        : si.getSigningCertificateHistory();
                addSignatureHashes(hashes, signers);
            }
        }
        if (hashes.isEmpty() && info.signatures != null) {
            addSignatureHashes(hashes, info.signatures);
        }
        return hashes;
    }

    private static void addSignatureHashes(Set<String> out, Signature[] signatures) {
        if (signatures == null) return;
        for (Signature sig : signatures) {
            if (sig == null) continue;
            String hash = sha256Hex(sig.toByteArray());
            if (hash != null) out.add(hash);
        }
    }

    private void launchSystemInstaller(File apkFile) {
        if (isFinishing() || isDestroyed()) return;
        pendingApkFile = apkFile;
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, msgAllowUnknown, Toast.LENGTH_LONG).show();
            try {
                Intent perm = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(perm, REQ_UNKNOWN_SOURCES);
            } catch (Throwable t) {
                showToastAndFinish(msgFailed);
            }
            return;
        }

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apkFile);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(install, REQ_INSTALL);
        } catch (Throwable t) {
            showToastAndFinish(msgFailed);
        }
    }

    private void showProgressDialog(String message) {
        if (isFinishing() || isDestroyed() || progressDialog != null) return;
        try {
            Dialog d = new Dialog(this);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setCancelable(false);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(20);
            box.setPadding(pad, dp(16), pad, dp(16));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(28, 36, 48));
            bg.setCornerRadius(dp(18));
            box.setBackground(bg);

            ProgressBar pb = new ProgressBar(this);
            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(dp(26), dp(26));
            pbLp.rightMargin = dp(14);
            box.addView(pb, pbLp);

            TextView tv = new TextView(this);
            tv.setText(message);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            box.addView(tv);

            d.setContentView(box);
            if (d.getWindow() != null) {
                d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            d.show();
            progressDialog = d;
        } catch (Throwable ignored) {}
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            try {
                if (progressDialog.isShowing()) progressDialog.dismiss();
            } catch (Throwable ignored) {}
            progressDialog = null;
        }
    }

    private void showToastAndFinish(String msg) {
        if (!isFinishing() && !isDestroyed()) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
        finishSilently();
    }

    private void finishSilently() {
        processing = false;
        if (!isFinishing()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private static boolean moveIntoPlace(File part, File target) {
        if (target.exists() && !target.delete()) {
            return false;
        }
        return part.renameTo(target);
    }

    private static void pruneOtherApks(File dir, File keep) {
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

    private static String computeFileSha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            return toHex(md.digest());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(bytes));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nonEmptyOr(String s, String fallback) {
        String t = trimToNull(s);
        return t != null ? t : fallback;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static final class SignatureMismatchException extends Exception {}
}
