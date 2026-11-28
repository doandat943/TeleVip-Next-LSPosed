package com.magiii.televipnext.features.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps Telegram's native Downloads icon always visible in the DialogsActivity top bar without requiring a restart.
 */
public class AlwaysShowDownloads {

    private static boolean loaded;

    private static final Map<Object, Boolean> ACTIVE_DIALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static Method checkDownloadsVisMethod;
    private static Field downloadsVisibleField;
    private static Method updateProxyMethod;
    private static Field downloadProgressIconField;
    private static Field dpiListenersField;
    private static Field dpiProgressField;
    private static Field dpiCurrentProgressField;
    private static Field dpiShowCompletedField;

    public static boolean isEnabled() {
        return ConfigManager.alwaysShowDownloads != null && ConfigManager.alwaysShowDownloads.isEnable();
    }

    /**
     * Installs DialogsActivity hooks once and applies the current toggle state to tracked instances.
     */
    public static void init() {
        if (!loaded) {
            loaded = true;
            installHooks();
        }
        refreshAll();
    }

    /**
     * Re-applies the downloads icon visibility across all tracked DialogsActivity instances on the UI thread.
     */
    public static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            List<Object> snapshot;
            synchronized (ACTIVE_DIALOGS) {
                snapshot = new ArrayList<>(ACTIVE_DIALOGS.keySet());
            }
            boolean enabled = isEnabled();
            for (Object dialogs : snapshot) {
                if (dialogs == null) continue;
                try {
                    if (enabled) {
                        if (checkDownloadsVisMethod != null) {
                            checkDownloadsVisMethod.invoke(dialogs);
                        }
                        if (downloadProgressIconField != null) {
                            ensureIdleIcon(downloadProgressIconField.get(dialogs), true);
                        }
                    } else if (updateProxyMethod != null) {
                        if (updateProxyMethod.getParameterTypes().length == 2) {
                            updateProxyMethod.invoke(dialogs, false, false);
                        } else {
                            updateProxyMethod.invoke(dialogs, false);
                        }
                    }
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }
        });
    }

    private static void installHooks() {
        try {
            Class<?> dialogsCls = ClassLoad.getClass(ClassNames.DIALOGS_ACTIVITY, Utils.classLoader, false);
            if (dialogsCls == null) return;

            String visMethodName = Obfuscate.getMethodName("DialogsActivity", "checkUi_itemDownloadsVisibility");
            String visFieldName = Obfuscate.getFieldName("DialogsActivity", "downloadsItemVisible");
            String updProxyName = Obfuscate.getMethodName("DialogsActivity", "updateProxyButton");
            String dpiFieldName = Obfuscate.getFieldName("DialogsActivity", "downloadProgressIcon");

            checkDownloadsVisMethod = ReflectHelper.findMethodExactIfExists(dialogsCls, visMethodName);
            downloadsVisibleField = ReflectHelper.findFieldIfExists(dialogsCls, visFieldName);
            if (downloadsVisibleField != null && downloadsVisibleField.getType() != boolean.class) {
                downloadsVisibleField = null;
            }
            if (checkDownloadsVisMethod == null || downloadsVisibleField == null) {
                Logger.w("AlwaysShowDownloads: missing DialogsActivity downloads symbols");
                return;
            }

            checkDownloadsVisMethod.setAccessible(true);
            downloadsVisibleField.setAccessible(true);

            downloadProgressIconField = ReflectHelper.findFieldIfExists(dialogsCls, dpiFieldName);
            if (downloadProgressIconField != null) {
                downloadProgressIconField.setAccessible(true);
            }

            updateProxyMethod = ReflectHelper.findMethodExactIfExists(dialogsCls, updProxyName, boolean.class, boolean.class);
            if (updateProxyMethod == null) {
                updateProxyMethod = ReflectHelper.findMethodExactIfExists(dialogsCls, updProxyName, boolean.class);
            }
            if (updateProxyMethod != null) {
                updateProxyMethod.setAccessible(true);
            }

            HMethod.hookMethod(checkDownloadsVisMethod, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (param.thisObject == null) return;
                    ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                    if (isEnabled()) {
                        try {
                            downloadsVisibleField.setBoolean(param.thisObject, true);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });

            String createViewName = Obfuscate.getMethodName("DialogsActivity", "createView");
            Method createViewMethod = ReflectHelper.findMethodExactIfExists(dialogsCls, createViewName, Context.class);
            if (createViewMethod != null) {
                createViewMethod.setAccessible(true);
                HMethod.hookMethod(createViewMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (param.thisObject == null) return;
                        ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                        if (isEnabled()) {
                            try {
                                checkDownloadsVisMethod.invoke(param.thisObject);
                                if (downloadProgressIconField != null) {
                                    ensureIdleIcon(downloadProgressIconField.get(param.thisObject), true);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
            }

            installDownloadIconHooks();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void installDownloadIconHooks() {
        try {
            Class<?> dpiCls = ClassLoad.getClass(ClassNames.DOWNLOAD_PROGRESS_ICON, Utils.classLoader, false);
            if (dpiCls == null) return;

            dpiListenersField = ReflectHelper.findFieldIfExists(dpiCls, Obfuscate.getFieldName("DownloadProgressIcon", "currentListeners"));
            dpiProgressField = ReflectHelper.findFieldIfExists(dpiCls, Obfuscate.getFieldName("DownloadProgressIcon", "progress"));
            dpiCurrentProgressField = ReflectHelper.findFieldIfExists(dpiCls, Obfuscate.getFieldName("DownloadProgressIcon", "currentProgress"));
            dpiShowCompletedField = ReflectHelper.findFieldIfExists(dpiCls, Obfuscate.getFieldName("DownloadProgressIcon", "showCompletedIcon"));

            if (dpiProgressField != null && dpiProgressField.getType() != float.class) dpiProgressField = null;
            if (dpiCurrentProgressField != null && dpiCurrentProgressField.getType() != float.class) dpiCurrentProgressField = null;
            if (dpiShowCompletedField != null && dpiShowCompletedField.getType() != boolean.class) dpiShowCompletedField = null;

            if (dpiListenersField == null || dpiProgressField == null || dpiCurrentProgressField == null || dpiShowCompletedField == null) {
                Logger.w("AlwaysShowDownloads: missing DownloadProgressIcon fields");
                return;
            }

            dpiListenersField.setAccessible(true);
            dpiProgressField.setAccessible(true);
            dpiCurrentProgressField.setAccessible(true);
            dpiShowCompletedField.setAccessible(true);

            String updListenersName = Obfuscate.getMethodName("DownloadProgressIcon", "updateDownloadingListeners");
            Method updListenersMethod = ReflectHelper.findMethodExactIfExists(dpiCls, updListenersName);
            if (updListenersMethod != null) {
                updListenersMethod.setAccessible(true);
                HMethod.hookMethod(updListenersMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        ensureIdleIcon(param.thisObject, true);
                    }
                });
            }

            Method onDrawMethod = ReflectHelper.findMethodExactIfExists(dpiCls, "onDraw", Canvas.class);
            if (onDrawMethod != null) {
                onDrawMethod.setAccessible(true);
                HMethod.hookMethod(onDrawMethod, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        ensureIdleIcon(param.thisObject, false);
                    }
                });
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void ensureIdleIcon(Object icon, boolean invalidate) {
        if (!isEnabled() || icon == null || dpiListenersField == null
                || dpiProgressField == null || dpiCurrentProgressField == null || dpiShowCompletedField == null) {
            return;
        }
        try {
            Object listeners = dpiListenersField.get(icon);
            if (listeners instanceof List && !((List<?>) listeners).isEmpty()) {
                return;
            }
            boolean showDone = dpiShowCompletedField.getBoolean(icon);
            float prog = dpiProgressField.getFloat(icon);
            float curProg = dpiCurrentProgressField.getFloat(icon);
            if (!showDone && prog == 0f && curProg == 0f) {
                dpiProgressField.setFloat(icon, 1f);
                dpiCurrentProgressField.setFloat(icon, 1f);
                // Keep showCompletedIcon=false so onDraw starts downloadCompleteDrawable from frame 0 to its resting arrow.
                if (invalidate && icon instanceof View) {
                    ((View) icon).invalidate();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
