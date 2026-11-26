package com.magiii.televipnext.logging;

import static com.magiii.televipnext.utils.Utils.pkgName;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.obfuscate.struct.ResolverRegistry;
import com.magiii.televipnext.xposed.XposedCompat;

public class Logger {

    private static final String TAG = "TeleVip";

    public static void w(String text) {
        XposedCompat.log(Log.WARN, TAG, "[Warning] pkgName: " + pkgName + " " + text);
    }

    public static void l(String text) {
        XposedCompat.log(Log.INFO, TAG, "pkgName: " + pkgName + " " + text);
    }

    public static void e(String text) {
        XposedCompat.log(Log.ERROR, TAG, "[Error] Ai: " + text);
    }

    public static void e(Throwable throwable) {
        try {
            StringBuilder log = new StringBuilder();

            log.append("[Error] pkgName: ").append(pkgName).append(" ").append(throwable).append("\n");
            Class<?> resolverCls = ResolverRegistry.getResolverClass();
            log.append("appName = ").append(resolverCls != null ? resolverCls.getSimpleName() : pkgName).append("\n");

            try {
                PackageManager pm = ApplicationLoaderHook.getApplicationContext().getPackageManager();
                PackageInfo info = pm.getPackageInfo(ApplicationLoaderHook.getApplicationContext().getPackageName(), 0);
                String versionName = info.versionName;
                int versionCode = info.versionCode;

                log.append("versionName: ").append(versionName).append("\n");
                log.append("versionCode: ").append(versionCode).append("\n");
            } catch (Throwable e) {
                log.append("versionName/versionCode: error retrieving\n");
            }

            log.append("OS Version: ").append(Build.VERSION.RELEASE).append("\n");
            log.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
            log.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n");
            log.append("Model: ").append(Build.MODEL).append("\n");

            for (StackTraceElement element : throwable.getStackTrace()) {
                log.append("[TeleVip] at ").append(element.toString()).append("\n");
            }

            XposedCompat.log(Log.ERROR, TAG, log.toString(), throwable);
        } catch (Throwable g) {}
    }

}
