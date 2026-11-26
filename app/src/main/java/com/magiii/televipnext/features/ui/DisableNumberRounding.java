package com.magiii.televipnext.features.ui;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

public class DisableNumberRounding {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                Class<?> lcCls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER);
                if (lcCls != null) {
                    String method = Obfuscate.getMethodName("LocaleController", "formatShortNumber");
                    final boolean firstIsArray = com.magiii.televipnext.xposed.ReflectHelper
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
