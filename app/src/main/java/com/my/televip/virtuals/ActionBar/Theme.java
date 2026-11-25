package com.my.televip.virtuals.ActionBar;

import android.text.TextPaint;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;

import de.robv.android.xposed.XposedHelpers;

public class Theme {

    public static TextPaint getTextPaint() {
        try {
            Class<?> themeCls = ClassLoad.getClass(ClassNames.THEME, com.my.televip.utils.Utils.classLoader, false);
            if (themeCls != null) {
                Object val = XposedHelpers.getStaticObjectField(themeCls, Obfuscate.getFieldName("Theme", "chat_timePaint"));
                if (val instanceof TextPaint) return (TextPaint) val;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isLight(){
        try {
            Class<?> themeCls = ClassLoad.getClass(ClassNames.THEME, com.my.televip.utils.Utils.classLoader, false);
            if (themeCls != null) {
                String method = Obfuscate.getMethodName("Theme", "isCurrentThemeDark");
                if (de.robv.android.xposed.XposedHelpers.findMethodExactIfExists(themeCls, method) != null) {
                    return !((boolean) XposedHelpers.callStaticMethod(themeCls, method));
                }
            }
        } catch (Throwable ignored) {}
        try {
            android.app.Activity act = com.my.televip.utils.Utils.getCurrentActivity();
            if (act != null) {
                int night = act.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                return night != android.content.res.Configuration.UI_MODE_NIGHT_YES;
            }
        } catch (Throwable ignored) {}
        return true;
    }
}
