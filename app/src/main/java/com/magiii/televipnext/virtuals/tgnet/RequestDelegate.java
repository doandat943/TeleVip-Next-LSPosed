package com.magiii.televipnext.virtuals.tgnet;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import java.lang.reflect.Proxy;

import com.magiii.televipnext.xposed.ReflectHelper;

public class RequestDelegate {

    public Object requestDelegate;

    public RequestDelegate(Object obj){
        requestDelegate = obj;
    }

    public void run(Object response, Object error){
        ReflectHelper.callMethod(requestDelegate, Obfuscate.getMethodName("RequestDelegate", "run"), response, error);
    }


    @FunctionalInterface
    public interface requestDelegate {
        void run(Object response, Object error);
    }

    public static Object run(requestDelegate lambda) {
        Class<?> requestDelegateClass = ClassLoad.getClass(ClassNames.REQUEST_DELEGATE);
        if (requestDelegateClass != null) {
            return Proxy.newProxyInstance(
                    Utils.classLoader,
                    new Class[]{requestDelegateClass},
                    (proxy, method, args) -> {
                        if (method.getParameterCount() == 2 && method.getParameterTypes()[0] == ClassLoad.getClass(ClassNames.TL_OBJECT)) {
                            lambda.run(args[0], args[1]);
                        }
                        return null;
                    }
            );
        }
        return null;
    }
}
