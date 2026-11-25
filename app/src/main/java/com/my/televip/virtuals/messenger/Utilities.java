package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.XposedHelpers;

public class Utilities {

    public static DispatchQueue getStageQueue() {
        try {
            Class<?> utilCls = ClassLoad.getClass(ClassNames.UTILITIES, Utils.classLoader, false);
            if (utilCls == null) return null;

            String mapped = Obfuscate.getFieldName("Utilities", "stageQueue");
            if (mapped != null) {
                try {
                    Object q = XposedHelpers.getStaticObjectField(utilCls, mapped);
                    if (q != null) return new DispatchQueue(q);
                } catch (Throwable ignored) {}
            }
            if (!"stageQueue".equals(mapped)) {
                try {
                    Object q = XposedHelpers.getStaticObjectField(utilCls, "stageQueue");
                    if (q != null) return new DispatchQueue(q);
                } catch (Throwable ignored) {}
            }

            Class<?> dqCls = ClassLoad.getClass("org.telegram.messenger.DispatchQueue", Utils.classLoader, false);
            if (dqCls != null) {
                for (Field f : utilCls.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) && f.getType() == dqCls) {
                        f.setAccessible(true);
                        Object q = f.get(null);
                        if (q != null) return new DispatchQueue(q);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
