package com.magiii.televipnext;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.updater.UpdateManager;
import com.magiii.televipnext.utils.Utils;

/**
 * Shared target-package initialization invoked by either ModernMainHook or legacy MainHook.
 * Must not reference de.robv.android.xposed or io.github.libxposed APIs.
 */
public final class HookEntry {

    private static volatile boolean isStart = false;
    private static volatile boolean launchHooked = false;

    private HookEntry() {}

    public static void onPackageLoaded(String packageName, ClassLoader classLoader, ApplicationInfo appInfo) {
        if (launchHooked) return;
        if (!ClientManager.containsPackage(packageName, classLoader)) return;

        launchHooked = true;
        Utils.classLoader = classLoader;
        Utils.pkgName = packageName;
        Utils.sourceDir = appInfo != null ? appInfo.sourceDir : null;

        HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY), "onCreate", Bundle.class, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                Utils.setCurrentActivity((Activity) param.thisObject);
                if (!isStart) {
                    TeleVip.startHook();
                    isStart = true;
                }
            }
        });

        HMethod.hookMethod(Activity.class, "onResume", new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof Activity)) return;
                Activity act = (Activity) param.thisObject;
                Class<?> launchCls = ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY);
                if (launchCls != null && launchCls.isInstance(act)) {
                    Utils.setCurrentActivity(act);
                    if (isStart) {
                        UpdateManager.onTelegramResumed(act);
                    }
                }
            }
        });
    }
}
