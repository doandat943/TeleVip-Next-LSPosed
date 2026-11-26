package com.magiii.televipnext.virtuals.ui;

import android.app.Activity;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.ImageReceiver;
import com.magiii.televipnext.virtuals.messenger.MessageObject;

import java.lang.reflect.Method;

import com.magiii.televipnext.xposed.ReflectHelper;

public class PhotoViewer {

    Object photoViewer;

    public PhotoViewer(Object photoViewer){
        this.photoViewer = photoViewer;
    }

    public static PhotoViewer getInstance(){
        return new PhotoViewer(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.PHOTO_VIEWER), Obfuscate.getMethodName("PhotoViewer", "getInstance")));
    }

    public void setParentActivity(Activity activity){
        String methodName = Obfuscate.getMethodName("PhotoViewer", "setParentActivity");
        Class<?> pvClass = photoViewer.getClass();
        Method exact = ReflectHelper.findMethodExactIfExists(pvClass, methodName, Activity.class);
        if (exact != null) {
            ReflectHelper.callMethod(photoViewer, methodName, activity);
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
        ReflectHelper.callMethod(photoViewer, methodName, activity);
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
        ReflectHelper.callMethod(photoViewer, methodName, rawMsg, l, l2, l3, rawProv, b);
    }

    public View getGalleryButton(){
        return (View) ReflectHelper.getObjectField(photoViewer,  Obfuscate.getFieldName("PhotoViewer", "galleryButton"));
    }

    public static class PhotoViewerProvider {
        Object photoViewerProvider;

        public PhotoViewerProvider(Object photoViewer) {
            photoViewerProvider = photoViewer;
        }

        public PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, Object fileLocation, int index, boolean needPreview, boolean closing) {
            return new PlaceProviderObject(ReflectHelper.callMethod(photoViewerProvider,  Obfuscate.getMethodName("PhotoViewer$PhotoViewerProvider", "getPlaceForPhoto"), messageObject.getMessageObject(), fileLocation, index, needPreview, closing));
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
            return new ImageReceiver(ReflectHelper.getObjectField(placeProviderObject,  Obfuscate.getFieldName("PhotoViewer$PlaceProviderObject", "imageReceiver")));
        }

    }

}
