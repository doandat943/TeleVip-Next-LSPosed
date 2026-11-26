package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class DispatchQueue {

    Object dispatchQueue;

    public DispatchQueue(Object obj){
        dispatchQueue = obj;
    }

    public void postRunnable(Runnable runnable) {
        ReflectHelper.callMethod(dispatchQueue, Obfuscate.getMethodName("DispatchQueue", "postRunnable"), runnable);
    }

}
