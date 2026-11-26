package com.magiii.televipnext.application;

import android.content.Context;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ApplicationLoaderHook {

    private static Context applicationContext;

    public static Context getApplicationContext() {
        if (applicationContext == null) {
            applicationContext = (Context) ReflectHelper.getStaticObjectField(
                    ClassLoad.getClass(ClassNames.APPLICATION_LOADER),
                    Obfuscate.getFieldName("ApplicationLoader", "applicationContext")
            );
        }
        return applicationContext;
    }
}