package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.Obfuscate;

import java.util.Locale;

import de.robv.android.xposed.XposedHelpers;

public class LocaleController {

    Object localeController;

    public LocaleController(){
        try {
            Class<?> cls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER, com.my.televip.utils.Utils.classLoader, false);
            if (cls != null) {
                String method = Obfuscate.getMethodName("LocaleController", "getInstance");
                if (XposedHelpers.findMethodExactIfExists(cls, method) != null) {
                    localeController = XposedHelpers.callStaticMethod(cls, method);
                }
            }
        } catch (Throwable ignored) {}
    }

    public Locale getCurrentLocale() {
        try {
            Object val = XposedHelpers.getObjectField(localeController, Obfuscate.getFieldName("LocaleController", "currentLocale"));
            if (val instanceof Locale) return (Locale) val;
        } catch (Throwable ignored) {}
        try {
            if (localeController != null) {
                for (java.lang.reflect.Field f : localeController.getClass().getDeclaredFields()) {
                    if (f.getType() == Locale.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        Object val = f.get(localeController);
                        if (val instanceof Locale) return (Locale) val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Locale.getDefault();
    }

    public static boolean isRTL() {
        try {
            return XposedHelpers.getStaticBooleanField(ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER), Obfuscate.getFieldName("LocaleController", "isRTL"));
        } catch (Throwable ignored) {
            return false;
        }
    }

}
