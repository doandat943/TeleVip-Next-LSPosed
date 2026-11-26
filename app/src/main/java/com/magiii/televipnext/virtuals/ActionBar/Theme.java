package com.magiii.televipnext.virtuals.ActionBar;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextPaint;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import com.magiii.televipnext.xposed.ReflectHelper;

public class Theme {

    public static TextPaint getTextPaint() {
        try {
            Class<?> themeCls = ClassLoad.getClass(ClassNames.THEME, Utils.classLoader, false);
            if (themeCls != null) {
                Object val = ReflectHelper.getStaticObjectField(themeCls, Obfuscate.getFieldName("Theme", "chat_timePaint"));
                if (val instanceof TextPaint) return (TextPaint) val;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isLight(){
        try {
            Class<?> themeCls = ClassLoad.getClass(ClassNames.THEME, Utils.classLoader, false);
            if (themeCls != null) {
                String method = Obfuscate.getMethodName("Theme", "isCurrentThemeDark");
                if (ReflectHelper.findMethodExactIfExists(themeCls, method) != null) {
                    return !((boolean) ReflectHelper.callStaticMethod(themeCls, method));
                }
            }
        } catch (Throwable ignored) {}
        return true;
    }

    /**
     * Switches Telegram's active theme between its configured Day and Night themes.
     */
    public static boolean toggleDayNight(View anchorView) {
        try {
            Class<?> themeCls = ClassLoad.getClass(ClassNames.THEME, Utils.classLoader, false);
            if (themeCls == null) return false;

            Method getThemeMethod = findGetThemeMethod(themeCls);
            if (getThemeMethod == null) return false;

            Context ctx = ApplicationLoaderHook.getApplicationContext();
            if (ctx == null) ctx = Utils.getCurrentActivity();
            SharedPreferences prefs = ctx != null ? ctx.getSharedPreferences("themeconfig", Context.MODE_PRIVATE) : null;
            String dayName = prefs != null ? prefs.getString("lastDayTheme", prefs.getString("lastDayCustomTheme", "Blue")) : "Blue";
            String nightName = prefs != null ? prefs.getString("lastDarkTheme", prefs.getString("lastDarkCustomTheme", "Dark Blue")) : "Dark Blue";

            Object dayTheme = invokeGetTheme(getThemeMethod, dayName);
            if (dayTheme == null || isThemeInfoDark(dayTheme)) {
                dayTheme = invokeGetTheme(getThemeMethod, "Blue");
                if (dayTheme == null || isThemeInfoDark(dayTheme)) {
                    dayTheme = invokeGetTheme(getThemeMethod, "Day");
                }
            }

            Object nightTheme = invokeGetTheme(getThemeMethod, nightName);
            if (nightTheme == null || !isThemeInfoDark(nightTheme)) {
                nightTheme = invokeGetTheme(getThemeMethod, "Dark Blue");
                if (nightTheme == null || !isThemeInfoDark(nightTheme)) {
                    nightTheme = invokeGetTheme(getThemeMethod, "Night");
                }
            }

            Object targetTheme = isLight() ? nightTheme : dayTheme;
            if (targetTheme == null) return false;

            Class<?> themeInfoCls = targetTheme.getClass();
            Method applyAnim = findStaticMethod(
                    themeCls,
                    Obfuscate.getMethodName("Theme", "applyThemeAnimated"),
                    null,
                    themeInfoCls, boolean.class, int.class, View.class);
            if (applyAnim != null) {
                applyAnim.invoke(null, targetTheme, false, -1, anchorView);
                return true;
            }

            String applyDirectName = Obfuscate.getMethodName("Theme", "applyTheme");
            Method applyDirect3 = findStaticMethod(
                    themeCls, applyDirectName, "applyTheme",
                    themeInfoCls, boolean.class, boolean.class);
            if (applyDirect3 != null) {
                applyDirect3.invoke(null, targetTheme, false, false);
                return true;
            }

            Method applyDirect2 = findStaticMethod(
                    themeCls, applyDirectName, "applyTheme",
                    themeInfoCls, boolean.class);
            if (applyDirect2 != null) {
                applyDirect2.invoke(null, targetTheme, false);
                return true;
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return false;
    }

    private static Method findGetThemeMethod(Class<?> themeCls) {
        Method m = ReflectHelper.findMethodExactIfExists(
                themeCls, Obfuscate.getMethodName("Theme", "getTheme"), String.class);
        if (m == null) {
            m = ReflectHelper.findMethodExactIfExists(themeCls, "getTheme", String.class);
        }
        if (m != null && Modifier.isStatic(m.getModifiers())) {
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    private static Method findStaticMethod(
            Class<?> cls, String mappedName, String fallbackName, Class<?>... paramTypes) {
        Method m = null;
        if (mappedName != null) {
            m = ReflectHelper.findMethodExactIfExists(cls, mappedName, (Object[]) paramTypes);
        }
        if (m == null && fallbackName != null && !fallbackName.equals(mappedName)) {
            m = ReflectHelper.findMethodExactIfExists(cls, fallbackName, (Object[]) paramTypes);
        }
        if (m != null && Modifier.isStatic(m.getModifiers())) {
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    private static Object invokeGetTheme(Method getThemeMethod, String name) {
        if (name == null) return null;
        try {
            return getThemeMethod.invoke(null, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isThemeInfoDark(Object themeInfo) {
        if (themeInfo == null) return false;
        try {
            Class<?> cls = themeInfo.getClass();
            Method m = ReflectHelper.findMethodExactIfExists(cls, Obfuscate.getMethodName("ThemeInfo", "isDark"));
            if (m == null) {
                m = ReflectHelper.findMethodExactIfExists(cls, "isDark");
            }
            if (m != null) {
                m.setAccessible(true);
                return (boolean) m.invoke(themeInfo);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
