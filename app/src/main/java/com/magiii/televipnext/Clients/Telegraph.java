package com.magiii.televipnext.Clients;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Telegraph {

    public static class ParameterResolver {
        static Map<String, Class<?>[]> objectList = new HashMap<>();

        public static void register(String name, Class<?>[] classes) {
            objectList.put(name, classes);
        }

        public static Class<?>[] resolve(String name) {
            return objectList.get(name);
        }

        public static boolean has(String name) {
            return objectList.get(name) != null;
        }
    }

    public static void loadParameter() {
        ParameterResolver.register("fillMessageMenu", new Class[]{
                ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),
                ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),
                ArrayList.class,
                ArrayList.class,
                ArrayList.class
        });
    }

    public static void removeAd() {
        try {
            Class<?> connectionsManager = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
            if (connectionsManager != null) {
                if (ReflectHelper.findMethodExactIfExists(connectionsManager, "native_expireFile", long.class) != null) {
                    HMethod.hookMethod(connectionsManager, "native_expireFile", long.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            param.setResult(false);
                        }
                    });
                }
                if (ReflectHelper.findMethodExactIfExists(connectionsManager, "native_daysFile", long.class) != null) {
                    HMethod.hookMethod(connectionsManager, "native_daysFile", long.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            param.setResult(999);
                        }
                    });
                }
                if (ReflectHelper.findMethodExactIfExists(connectionsManager, "native_checkLicense", long.class) != null) {
                    HMethod.hookMethod(connectionsManager, "native_checkLicense", long.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
                }
                if (ReflectHelper.findMethodExactIfExists(connectionsManager, "native_removeInstance", int.class, boolean.class) != null) {
                    HMethod.hookMethod(connectionsManager, "native_removeInstance", int.class, boolean.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            param.setResult(null);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
