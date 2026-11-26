package com.magiii.televipnext;

import android.content.pm.ApplicationInfo;

import androidx.annotation.NonNull;

import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ModernXposedBackend;
import com.magiii.televipnext.xposed.XposedCompat;

import io.github.libxposed.api.XposedModule;

/**
 * Modern Xposed entry point (libxposed API 101/102).
 * Must never touch de.robv.android.xposed classes.
 */
public class ModernMainHook extends XposedModule {

    public ModernMainHook() {}

    private void ensureInitialized() {
        if (!(XposedCompat.getBackend() instanceof ModernXposedBackend)) {
            XposedCompat.setBackend(new ModernXposedBackend(this));
        }
        if (Utils.modulePath == null) {
            ApplicationInfo modInfo = getModuleApplicationInfo();
            Utils.modulePath = modInfo != null ? modInfo.sourceDir : null;
        }
    }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        ensureInitialized();
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        ensureInitialized();
        HookEntry.onPackageLoaded(
                param.getPackageName(),
                param.getClassLoader(),
                param.getApplicationInfo()
        );
    }
}
