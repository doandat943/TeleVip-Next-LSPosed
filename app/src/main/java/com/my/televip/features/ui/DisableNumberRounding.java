package com.my.televip.features.ui;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

public class DisableNumberRounding {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                Class<?> lcCls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER);
                if (lcCls != null) {
                    String method = Obfuscate.getMethodName("LocaleController", "formatShortNumber");
                    final boolean firstIsArray = de.robv.android.xposed.XposedHelpers
                            .findMethodExactIfExists(lcCls, method, int.class, int[].class) == null;
                    Class<?>[] sig = firstIsArray
                            ? new Class[]{int[].class, int.class}
                            : new Class[]{int.class, int[].class};
                    HMethod.hookMethod(lcCls, method, ArgsResolver.merge("formatShortNumber", sig,
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (ConfigManager.disableNumberRounding.isEnable()) {
                                        int[] rounded = firstIsArray ? (int[]) param.args[0] : (int[]) param.args[1];
                                        int number = firstIsArray ? (int) param.args[1] : (int) param.args[0];
                                        if (rounded != null && rounded.length > 0) {
                                            rounded[0] = number;
                                        }
                                        param.setResult(String.valueOf(number));
                                    }
                                }
                            }));
                    isEnable = true;
                }
            }
        } catch (Throwable t) {
            isEnable = false;
            Logger.e(t);
        }
    }
}
