package com.magiii.televipnext.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.magiii.televipnext.Clients.ClientManager;

/**
 * Cross-process restart trampoline hosted in the module APK.
 * Holds foreground focus briefly while the host Telegram client exits, then cold-starts it.
 */
public class RestartTrampolineActivity extends Activity {

    public static final String EXTRA_TARGET_PACKAGE = "target_package";
    public static final String EXTRA_TARGET_COMPONENT = "target_component";

    private static final long RELAUNCH_DELAY_MS = 500L;

    private boolean scheduled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        if (!isAllowedPackage(getIntent() != null ? getIntent().getStringExtra(EXTRA_TARGET_PACKAGE) : null)) {
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scheduled) return;
        scheduled = true;

        Intent incoming = getIntent();
        String targetPkg = incoming != null ? incoming.getStringExtra(EXTRA_TARGET_PACKAGE) : null;
        if (!isAllowedPackage(targetPkg)) {
            finish();
            return;
        }
        @SuppressWarnings("deprecation")
        ComponentName fallbackComp = incoming != null ? incoming.getParcelableExtra(EXTRA_TARGET_COMPONENT) : null;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPkg);
                if (launchIntent == null && fallbackComp != null && targetPkg.equals(fallbackComp.getPackageName())) {
                    launchIntent = new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(fallbackComp);
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(launchIntent);
                }
            } catch (Throwable ignored) {
            } finally {
                finish();
                overridePendingTransition(0, 0);
            }
        }, RELAUNCH_DELAY_MS);
    }

    private static boolean isAllowedPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        for (ClientManager.Client c : ClientManager.Client.values()) {
            if (c.hasPackage(pkg)) return true;
        }
        return false;
    }
}
