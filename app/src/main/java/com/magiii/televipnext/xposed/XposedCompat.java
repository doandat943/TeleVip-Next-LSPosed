package com.magiii.televipnext.xposed;

import android.util.Log;

import com.magiii.televipnext.base.BaseMethodHook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Framework-agnostic hooking & logging dispatcher backed by either Modern (libxposed) or Legacy backend.
 */
public final class XposedCompat {

    public interface Backend {
        void hookMethod(Method method, BaseMethodHook callback);

        void log(int priority, String tag, String msg);

        void log(int priority, String tag, String msg, Throwable tr);
    }

    private static volatile Backend backend;

    private XposedCompat() {}

    public static void setBackend(Backend b) {
        if (b != null) {
            backend = b;
        }
    }

    public static Backend getBackend() {
        return backend;
    }

    public static void hookMethod(Method method, BaseMethodHook callback) {
        Backend b = backend;
        if (b == null || method == null || callback == null) return;
        b.hookMethod(method, callback);
    }

    public static void hookAllMethods(Class<?> hookClass, String methodName, BaseMethodHook callback) {
        Backend b = backend;
        if (b == null || hookClass == null || methodName == null || callback == null) return;
        for (Method method : hookClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !Modifier.isAbstract(method.getModifiers())) {
                b.hookMethod(method, callback);
            }
        }
    }

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        if (clazz == null || methodName == null || parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("Invalid arguments for findAndHookMethod");
        }
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof BaseMethodHook)) {
            throw new IllegalArgumentException("Last argument must be a BaseMethodHook");
        }
        BaseMethodHook callback = (BaseMethodHook) last;
        Object[] paramTypes = Arrays.copyOf(parameterTypesAndCallback, parameterTypesAndCallback.length - 1);
        Method method = ReflectHelper.findMethodExactIfExists(clazz, methodName, paramTypes);
        if (method == null) {
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
        }
        hookMethod(method, callback);
    }

    public static void log(int priority, String tag, String msg) {
        Backend b = backend;
        if (b != null) {
            b.log(priority, tag, msg);
        } else {
            Log.println(priority, tag, msg);
        }
    }

    public static void log(int priority, String tag, String msg, Throwable tr) {
        Backend b = backend;
        if (b != null) {
            b.log(priority, tag, msg, tr);
        } else {
            Log.println(priority, tag, msg + '\n' + Log.getStackTraceString(tr));
        }
    }
}
