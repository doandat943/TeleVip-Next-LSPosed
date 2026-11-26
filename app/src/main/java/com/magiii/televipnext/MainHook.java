package com.magiii.televipnext;

import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.LegacyXposedBackend;
import com.magiii.televipnext.xposed.XposedCompat;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Legacy Xposed entry point (API 93). Isolated from ModernMainHook.
 */
public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    @Override
    public void initZygote(StartupParam startupParam) {
        XposedCompat.setBackend(new LegacyXposedBackend());
        Utils.modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (XposedCompat.getBackend() == null) {
            XposedCompat.setBackend(new LegacyXposedBackend());
        }
        HookEntry.onPackageLoaded(lpparam.packageName, lpparam.classLoader, lpparam.appInfo);
    }
}
