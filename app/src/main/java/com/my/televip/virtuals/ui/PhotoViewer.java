package com.my.televip.virtuals.ui;

import android.app.Activity;
import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.ImageReceiver;
import com.my.televip.virtuals.messenger.MessageObject;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedHelpers;

public class PhotoViewer {

    Object photoViewer;

    public PhotoViewer(Object photoViewer){
        this.photoViewer = photoViewer;
    }

    public static PhotoViewer getInstance(){
        return new PhotoViewer(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER), Obfuscate.getMethodName("PhotoViewer", "getInstance")));
    }

    public void setParentActivity(Activity activity){
        String methodName = Obfuscate.getMethodName("PhotoViewer", "setParentActivity");
        Class<?> pvClass = photoViewer.getClass();
        Method exact = XposedHelpers.findMethodExactIfExists(pvClass, methodName, Activity.class);
        if (exact != null) {
            XposedHelpers.callMethod(photoViewer, methodName, activity);
            return;
        }
        for (Method m : pvClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals(methodName) && pt.length >= 1 && pt[0] == Activity.class) {
                Object[] args = new Object[pt.length];
                args[0] = activity;
                try {
                    m.setAccessible(true);
                    m.invoke(photoViewer, args);
                    return;
                } catch (Throwable ignored) {}
            }
        }
        XposedHelpers.callMethod(photoViewer, methodName, activity);
    }

    public void openPhoto(MessageObject messageObject, long l, long l2, long l3, PhotoViewerProvider provider, boolean b){
        String methodName = Obfuscate.getMethodName("PhotoViewer", "openPhoto");
        Class<?> pvClass = photoViewer.getClass();
        Object rawMsg = messageObject.getMessageObject();
        Object rawProv = provider != null ? provider.getPhotoViewerProvider() : null;
        for (Method m : pvClass.getDeclaredMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals(methodName) && pt.length == 6 && pt[0].isInstance(rawMsg)) {
                try {
                    m.setAccessible(true);
                    if (pt[1] == long.class && pt[2] == long.class && pt[3] == long.class && pt[5] == boolean.class) {
                        m.invoke(photoViewer, rawMsg, l, l2, l3, rawProv, b);
                        return;
                    } else if (pt[2] == long.class && pt[3] == long.class && pt[4] == long.class
                            && (rawProv == null || pt[5].isInstance(rawProv))) {
                        m.invoke(photoViewer, rawMsg, null, l, l2, l3, rawProv);
                        return;
                    }
                } catch (Throwable ignored) {}
            }
        }
        XposedHelpers.callMethod(photoViewer, methodName, rawMsg, l, l2, l3, rawProv, b);
    }

    public View getGalleryButton(){
        return (View) XposedHelpers.getObjectField(photoViewer,  Obfuscate.getFieldName("PhotoViewer", "galleryButton"));
    }

    public static class PhotoViewerProvider {
        Object photoViewerProvider;

        public PhotoViewerProvider(Object photoViewer) {
            photoViewerProvider = photoViewer;
        }

        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, Object fileLocation, int index, boolean needPreview, boolean closing) {
            return new PlaceProviderObject(XposedHelpers.callMethod(photoViewerProvider,  Obfuscate.getMethodName("PhotoViewer$PhotoViewerProvider", "getPlaceForPhoto"), messageObject.getMessageObject(), fileLocation, index, needPreview, closing));
        }

        public Object getPhotoViewerProvider() {
            return photoViewerProvider;
        }

    }

    public static class PlaceProviderObject {
        Object placeProviderObject;

        public PlaceProviderObject(Object placeProvider) {
            placeProviderObject = placeProvider;
        }

        public ImageReceiver getImageReceiver() {
            return new ImageReceiver(XposedHelpers.getObjectField(placeProviderObject,  Obfuscate.getFieldName("PhotoViewer$PlaceProviderObject", "imageReceiver")));
        }

    }

}
