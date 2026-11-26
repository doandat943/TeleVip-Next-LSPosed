package com.magiii.televipnext.base;

import com.magiii.televipnext.logging.Logger;

import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;

/**
 * Framework-agnostic method hook callback shared between Modern (libxposed) and Legacy backends.
 */
public abstract class BaseMethodHook {

    public static final class MethodHookParam {
        public final Member method;
        public final Object thisObject;
        public final Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;
        private Map<String, Object> extra;

        public MethodHookParam(Member method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args != null ? args : new Object[0];
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.throwable = null;
            this.returnEarly = true;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
            this.returnEarly = true;
        }

        public void setResultInternal(Object result) {
            this.result = result;
            this.throwable = null;
        }

        public void setThrowableInternal(Throwable throwable) {
            this.throwable = throwable;
            this.result = null;
        }

        public boolean isReturnEarly() {
            return returnEarly;
        }

        public void clearReturnEarly() {
            this.returnEarly = false;
        }

        public synchronized Object getObjectExtra(String key) {
            return extra != null ? extra.get(key) : null;
        }

        public synchronized void setObjectExtra(String key, Object value) {
            if (extra == null) {
                extra = new HashMap<>();
            }
            extra.put(key, value);
        }
    }

    protected void beforeMethod(MethodHookParam param) throws Throwable {}

    protected void afterMethod(MethodHookParam param) throws Throwable {}

    public final void invokeBefore(MethodHookParam param) {
        try {
            beforeMethod(param);
        } catch (Throwable e) {
            onError(e);
        }
    }

    public final void invokeAfter(MethodHookParam param) {
        try {
            afterMethod(param);
        } catch (Throwable e) {
            onError(e);
        }
    }

    protected void onError(Throwable throwable) {
        Logger.e(throwable);
    }
}
