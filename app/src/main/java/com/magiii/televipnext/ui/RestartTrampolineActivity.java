package com.magiii.televipnext.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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

    public static void restartHost(Activity act) {
        if (act == null) return;
        String hostPkg = act.getPackageName();
        Intent hostLaunch = act.getPackageManager().getLaunchIntentForPackage(hostPkg);
        ComponentName hostComp = hostLaunch != null ? hostLaunch.getComponent() : null;

        try {
            Intent trampoline = new Intent()
                    .setClassName("com.magiii.televipnext", RestartTrampolineActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(EXTRA_TARGET_PACKAGE, hostPkg);
            if (hostComp != null) {
                trampoline.putExtra(EXTRA_TARGET_COMPONENT, hostComp);
            }
            act.startActivity(trampoline);
            act.overridePendingTransition(0, 0);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { act.finishAffinity(); } catch (Throwable ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
                Runtime.getRuntime().exit(0);
            }, 200);
            return;
        } catch (Throwable ignored) {}

        if (hostLaunch != null) {
            hostLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            try {
                AlarmManager am = (AlarmManager) act.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pi = PendingIntent.getActivity(
                        act, 0, hostLaunch, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                if (am != null) {
                    am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 400, pi);
                }
            } catch (Throwable ignored) {}
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { act.finishAffinity(); } catch (Throwable ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());
                Runtime.getRuntime().exit(0);
            }, 150);
        }
    }

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
