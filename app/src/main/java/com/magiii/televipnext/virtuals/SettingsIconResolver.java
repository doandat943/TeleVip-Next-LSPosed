package com.magiii.televipnext.virtuals;


import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SettingsIconResolver {

    private static Integer cachedIcon = null;

    public static int getIconSettings() {
        if (ClassLoad.getClass(ClassNames.DRAWABLE) == null) {
            return 0;
        }
        if (cachedIcon != null) return cachedIcon;

        String[] names = {
                "msg_settings",
                "msg_settings_old",
                "msg_settings_ny",
                "msg_settings_14",
                "msg_settings_hw"
        };

        for (String name : names) {
            try {
                int drawableResource = ReflectHelper.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), name);
                if (drawableResource != 0) {
                    cachedIcon = drawableResource;
                    return drawableResource;
                }
            } catch (Throwable ignored) {}
        }

        return 0;
    }

}
