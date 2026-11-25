package com.my.televip.virtuals.messenger.browser;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import de.robv.android.xposed.XposedHelpers;

public class Browser {

    public static void openUrl(Context context, String url){
        try {
            Class<?> cls = ClassLoad.getClass(ClassNames.BROWSER, Utils.classLoader, false);
            if (cls != null) {
                XposedHelpers.callStaticMethod(
                        cls,
                        Obfuscate.getMethodName("Browser", "openUrl"), context, url
                );
                return;
            }
        } catch (Throwable ignored) {}
        if (context != null && url != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

}
