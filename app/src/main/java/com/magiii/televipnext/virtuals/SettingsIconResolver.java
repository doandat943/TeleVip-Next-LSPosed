package com.magiii.televipnext.virtuals;

import android.content.Context;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ReflectHelper;

public class SettingsIconResolver {

    private static Integer cachedIcon = null;

    public static int getIconSettings() {
        if (cachedIcon != null) return cachedIcon;
        int resolved = resolveDrawableId(
                "msg_settings",
                "msg_settings_old",
                "msg_settings_ny",
                "msg_settings_14",
                "msg_settings_hw"
        );
        if (resolved != 0) {
            cachedIcon = resolved;
        }
        return resolved;
    }

    public static int resolveDrawableId(String... names) {
        return resolveDrawableId(ApplicationLoaderHook.getApplicationContext(), names);
    }

    public static int resolveDrawableId(Context ctx, String... names) {
        if (names == null || names.length == 0) return 0;
        Class<?> drawableCls = ClassLoad.getClass(ClassNames.DRAWABLE, Utils.classLoader, false);
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            if (drawableCls != null && ReflectHelper.findFieldIfExists(drawableCls, name) != null) {
                try {
                    int id = ReflectHelper.getStaticIntField(drawableCls, name);
                    if (id != 0) return id;
                } catch (Throwable ignored) {}
            }
            if (ctx != null && ctx.getResources() != null) {
                try {
                    int id = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
                    if (id != 0) return id;
                } catch (Throwable ignored) {}
            }
        }
        return 0;
    }
}
