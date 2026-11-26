package com.magiii.televipnext.Class;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ClassLoad {

    private static final Map<ClassLoader, Map<String, Class<?>>> cache =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static Class<?> getClass(String name) {
        return getClass(name, Utils.classLoader, true);
    }

    public static Class<?> getClass(String name, ClassLoader classLoader) {
        return getClass(name, classLoader, true);
    }

    public static Class<?> getClass(String name, ClassLoader classLoader, boolean log) {
        if (name == null) return null;
        String resolved = Obfuscate.getClassName(name);
        if (resolved == null) return null;

        Map<String, Class<?>> loaderCache = cache.get(classLoader);
        if (loaderCache == null) {
            synchronized (cache) {
                loaderCache = cache.get(classLoader);
                if (loaderCache == null) {
                    loaderCache = new ConcurrentHashMap<>();
                    cache.put(classLoader, loaderCache);
                }
            }
        }

        if (loaderCache.containsKey(resolved)) {
            return loaderCache.get(resolved);
        }

        try {
            Class<?> cls = ReflectHelper.findClassIfExists(
                    resolved,
                    classLoader
            );

            if (cls != null) {
                loaderCache.put(resolved, cls);
            } else {
                if (log) {
                    if ((ClientManager.is(ClientManager.Client.Nagram) || ClientManager.is(ClientManager.Client.Momogram) || ClientManager.is(ClientManager.Client.Nekogram)) && name.equals(ClassNames.DRAWABLE))
                        return null;

                    if (name.equals(resolved)) {
                        Logger.w("Not found " + name + " " + Utils.issue);
                    } else {
                        Logger.w("Not found " + name + ", " + resolved + " " + Utils.issue);
                    }
                }
            }

            return cls;

        } catch (Throwable e) {
            Logger.e(e);
            return null;
        }
    }

}
