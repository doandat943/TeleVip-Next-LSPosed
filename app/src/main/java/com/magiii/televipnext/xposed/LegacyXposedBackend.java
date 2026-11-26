package com.magiii.televipnext.xposed;

import com.magiii.televipnext.base.BaseMethodHook;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Legacy Xposed backend (API 93). Isolated from Modern entry to preserve API 102 compliance.
 */
public final class LegacyXposedBackend implements XposedCompat.Backend {

    private static final String EXTRA_PARAM_KEY = "__televip_compat_param";

    @Override
    public void hookMethod(Method method, BaseMethodHook callback) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam legacyParam) {
                BaseMethodHook.MethodHookParam compatParam = new BaseMethodHook.MethodHookParam(
                        legacyParam.method,
                        legacyParam.thisObject,
                        legacyParam.args
                );
                legacyParam.setObjectExtra(EXTRA_PARAM_KEY, compatParam);

                callback.invokeBefore(compatParam);
                if (compatParam.isReturnEarly()) {
                    if (compatParam.hasThrowable()) {
                        legacyParam.setThrowable(compatParam.getThrowable());
                    } else {
                        legacyParam.setResult(compatParam.getResult());
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam legacyParam) {
                Object saved = legacyParam.getObjectExtra(EXTRA_PARAM_KEY);
                BaseMethodHook.MethodHookParam compatParam = saved instanceof BaseMethodHook.MethodHookParam
                        ? (BaseMethodHook.MethodHookParam) saved
                        : new BaseMethodHook.MethodHookParam(legacyParam.method, legacyParam.thisObject, legacyParam.args);

                if (legacyParam.hasThrowable()) {
                    compatParam.setThrowableInternal(legacyParam.getThrowable());
                } else {
                    compatParam.setResultInternal(legacyParam.getResult());
                }

                compatParam.clearReturnEarly();
                callback.invokeAfter(compatParam);

                if (compatParam.isReturnEarly()) {
                    if (compatParam.hasThrowable()) {
                        legacyParam.setThrowable(compatParam.getThrowable());
                    } else {
                        legacyParam.setResult(compatParam.getResult());
                    }
                }
            }
        });
    }

    @Override
    public void log(int priority, String tag, String msg) {
        XposedBridge.log("[" + tag + "] " + msg);
    }

    @Override
    public void log(int priority, String tag, String msg, Throwable tr) {
        XposedBridge.log("[" + tag + "] " + msg);
        if (tr != null) {
            XposedBridge.log(tr);
        }
    }
}
