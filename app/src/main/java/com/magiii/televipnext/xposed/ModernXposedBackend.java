package com.magiii.televipnext.xposed;

import com.magiii.televipnext.base.BaseMethodHook;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Modern Xposed backend (libxposed API 101/102). Must never reference de.robv.android.xposed.
 */
public final class ModernXposedBackend implements XposedCompat.Backend {

    private final XposedInterface xposed;

    public ModernXposedBackend(XposedInterface xposed) {
        this.xposed = xposed;
    }

    @Override
    public void hookMethod(Method method, BaseMethodHook callback) {
        xposed.hook(method).intercept(chain -> {
            Object[] args = chain.getArgs().toArray();
            BaseMethodHook.MethodHookParam param = new BaseMethodHook.MethodHookParam(
                    chain.getExecutable(),
                    chain.getThisObject(),
                    args
            );

            callback.invokeBefore(param);
            if (param.isReturnEarly()) {
                if (param.hasThrowable()) {
                    throw param.getThrowable();
                }
                return param.getResult();
            }

            try {
                Object result = chain.proceed(param.args);
                param.setResultInternal(result);
            } catch (Throwable t) {
                param.setThrowableInternal(t);
            }

            param.clearReturnEarly();
            callback.invokeAfter(param);

            if (param.hasThrowable()) {
                throw param.getThrowable();
            }
            return param.getResult();
        });
    }

    @Override
    public void log(int priority, String tag, String msg) {
        xposed.log(priority, tag, msg);
    }

    @Override
    public void log(int priority, String tag, String msg, Throwable tr) {
        xposed.log(priority, tag, msg, tr);
    }
}
