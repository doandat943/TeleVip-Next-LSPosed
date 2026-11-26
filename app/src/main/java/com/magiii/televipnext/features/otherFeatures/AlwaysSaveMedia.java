package com.magiii.televipnext.features.otherFeatures;

import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.ui.PhotoViewer;

public class AlwaysSaveMedia {
    public static void init() {
        try {
            if (ClassLoad.getClass(ClassNames.PHOTO_VIEWER) != null) {

                HMethod.hookMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER), Obfuscate.getMethodName("PhotoViewer", "setIsAboutToSwitchToIndex"), ArgsResolver.merge("setIsAboutToSwitchToIndex", new Class[]{int.class, boolean.class, boolean.class, boolean.class}, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        final PhotoViewer photoViewer = new PhotoViewer(param.thisObject);
                        if (photoViewer.getGalleryButton()!= null) photoViewer.getGalleryButton().setVisibility(View.VISIBLE);

                    }
                }));
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}