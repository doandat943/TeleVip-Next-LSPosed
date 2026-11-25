package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

import de.robv.android.xposed.XposedHelpers;

public class LocaleController {

    Object localeController;

    public LocaleController() {
        ensureInstance();
    }

    private void ensureInstance() {
        if (localeController != null) return;
        try {
            Class<?> cls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER, Utils.classLoader, false);
            if (cls != null) {
                String method = Obfuscate.getMethodName("LocaleController", "getInstance");
                if (XposedHelpers.findMethodExactIfExists(cls, method) != null) {
                    localeController = XposedHelpers.callStaticMethod(cls, method);
                }
            }
        } catch (Throwable ignored) {}
    }

    public Locale getCurrentLocale() {
        ensureInstance();
        if (localeController != null) {
            Class<?> cls = localeController.getClass();
            try {
                String mappedMethod = Obfuscate.getMethodName("LocaleController", "getCurrentLocale");
                if (XposedHelpers.findMethodExactIfExists(cls, mappedMethod) != null) {
                    Object val = XposedHelpers.callMethod(localeController, mappedMethod);
                    if (val instanceof Locale) return (Locale) val;
                }
                if (!"getCurrentLocale".equals(mappedMethod)
                        && XposedHelpers.findMethodExactIfExists(cls, "getCurrentLocale") != null) {
                    Object val = XposedHelpers.callMethod(localeController, "getCurrentLocale");
                    if (val instanceof Locale) return (Locale) val;
                }
                Method singleGetter = null;
                int count = 0;
                for (Method m : cls.getDeclaredMethods()) {
                    if (!Modifier.isStatic(m.getModifiers())
                            && m.getParameterTypes().length == 0
                            && m.getReturnType() == Locale.class) {
                        singleGetter = m;
                        count++;
                    }
                }
                if (count == 1 && singleGetter != null) {
                    singleGetter.setAccessible(true);
                    Object val = singleGetter.invoke(localeController);
                    if (val instanceof Locale) return (Locale) val;
                }
            } catch (Throwable ignored) {}

            try {
                String mappedField = Obfuscate.getFieldName("LocaleController", "currentLocale");
                Object val = XposedHelpers.getObjectField(localeController, mappedField);
                if (val instanceof Locale) return (Locale) val;
            } catch (Throwable ignored) {}

            try {
                for (Field f : cls.getDeclaredFields()) {
                    if (f.getType() == Locale.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        Object val = f.get(localeController);
                        if (val instanceof Locale) return (Locale) val;
                    }
                }
            } catch (Throwable ignored) {}
        }
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
