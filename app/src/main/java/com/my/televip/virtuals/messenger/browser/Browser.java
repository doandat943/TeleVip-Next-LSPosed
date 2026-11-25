package com.my.televip.virtuals.messenger.browser;

import android.content.Context;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import de.robv.android.xposed.XposedHelpers;

public class Browser {

    public static void openUrl(Context context, String url) {
        if (context == null || url == null) return;
        try {
            Class<?> cls = ClassLoad.getClass(ClassNames.BROWSER, Utils.classLoader, false);
            if (cls != null) {
                XposedHelpers.callStaticMethod(
                        cls,
                        Obfuscate.getMethodName("Browser", "openUrl"), context, url
                );
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
