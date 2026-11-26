package com.magiii.televipnext.virtuals.messenger.browser;

import android.content.Context;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import com.magiii.televipnext.xposed.ReflectHelper;

public class Browser {

    public static void openUrl(Context context, String url) {
        if (context == null || url == null) return;
        try {
            Class<?> cls = ClassLoad.getClass(ClassNames.BROWSER, Utils.classLoader, false);
            if (cls != null) {
                ReflectHelper.callStaticMethod(
                        cls,
                        Obfuscate.getMethodName("Browser", "openUrl"), context, url
                );
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
