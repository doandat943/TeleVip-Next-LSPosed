package com.magiii.televipnext.features.other;

import android.app.Activity;
import android.content.SharedPreferences;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class HideUpdateApp {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                android.content.Context ctx = ApplicationLoaderHook.getApplicationContext();
                if (ctx != null) {
                    SharedPreferences preferences = ctx.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    preferences.edit().remove("appUpdate").apply();
                    preferences.edit().remove("appUpdateCheckTime").apply();
                    preferences.edit().remove("appUpdateBuild").apply();
                }

                Class<?> sharedConfigClass = ClassLoad.getClass(ClassNames.SHARED_CONFIG);
                if (sharedConfigClass != null) {
                    BaseMethodHook hideUpdateHook = new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.hideUpdateApp != null && ConfigManager.hideUpdateApp.isEnable()) {
                                param.setResult(false);
                            }
                        }
                    };

                    String setNewVer = Obfuscate.getMethodName("SharedConfig", "setNewAppVersionAvailable");
                    Class<?>[] setParams = ArgsResolver.resolveObject("setNewAppVersionAvailable",
                            new Class[]{ClassLoad.getClass(ClassNames.TL_HELP_APP_UPDATE)});
                    if (setParams != null && setParams[0] != null
                            && ReflectHelper.findMethodExactIfExists(sharedConfigClass, setNewVer, (Object[]) setParams) != null) {
                        HMethod.hookMethod(sharedConfigClass, setNewVer,
                                ArgsResolver.merge("setNewAppVersionAvailable",
                                        new Class[]{ClassLoad.getClass(ClassNames.TL_HELP_APP_UPDATE)}, hideUpdateHook));
                    }

                    String isAvail = Obfuscate.getMethodName("SharedConfig", "isAppUpdateAvailable");
                    if (ReflectHelper.findMethodExactIfExists(sharedConfigClass, isAvail) != null) {
                        HMethod.hookMethod(sharedConfigClass, isAvail, hideUpdateHook);
                    }
                }
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }
}
