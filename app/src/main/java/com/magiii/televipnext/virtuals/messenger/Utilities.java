package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.magiii.televipnext.xposed.ReflectHelper;

public class Utilities {

    public static DispatchQueue getStageQueue() {
        try {
            Class<?> utilCls = ClassLoad.getClass(ClassNames.UTILITIES, Utils.classLoader, false);
            if (utilCls == null) return null;

            String mapped = Obfuscate.getFieldName("Utilities", "stageQueue");
            if (mapped != null) {
                try {
                    Object q = ReflectHelper.getStaticObjectField(utilCls, mapped);
                    if (q != null) return new DispatchQueue(q);
                } catch (Throwable ignored) {}
            }
            if (!"stageQueue".equals(mapped)) {
                try {
                    Object q = ReflectHelper.getStaticObjectField(utilCls, "stageQueue");
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
