package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ImageReceiver {

    public Object imageReceiver;

    public ImageReceiver(Object imageReceiver){
        this.imageReceiver = imageReceiver;
    }

    public ImageLocation getImageLocation() {
        return new ImageLocation(ReflectHelper.callMethod(imageReceiver, Obfuscate.getMethodName("ImageReceiver", "getImageLocation")));
    }

}
