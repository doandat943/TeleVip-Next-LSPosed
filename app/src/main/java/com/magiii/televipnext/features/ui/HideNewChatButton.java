package com.magiii.televipnext.features.ui;

import android.content.Context;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides the floating "+" (new chat) action button on DialogsActivity without requiring a restart.
 */
public class HideNewChatButton {

    private static boolean loaded;

    private static final Map<Object, Boolean> ACTIVE_DIALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static String resolvedFabField;
    private static String setButtonVisibleMethod;
    private static Method updateFabVisMethodObj;

    public static boolean isEnabled() {
        return ConfigManager.hideNewChatButton != null && ConfigManager.hideNewChatButton.isEnable();
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
     * Updates the New Chat FAB visibility across all tracked DialogsActivity instances on the UI thread.
     */
    public static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            List<Object> snapshot;
            synchronized (ACTIVE_DIALOGS) {
                snapshot = new ArrayList<>(ACTIVE_DIALOGS.keySet());
            }
            boolean hide = isEnabled();
            for (Object dialogs : snapshot) {
                if (dialogs == null) continue;
                try {
                    if (hide) {
                        hideFab(dialogs);
                    } else {
                        restoreFab(dialogs);
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

            String mappedFab = Obfuscate.getFieldName("DialogsActivity", "floatingButton3");
            for (String candidate : new String[]{mappedFab, "floatingButton3", "floatingButtonContainer"}) {
                if (candidate != null && ReflectHelper.findFieldIfExists(dialogsCls, candidate) != null) {
                    resolvedFabField = candidate;
                    break;
                }
            }
            if (resolvedFabField == null) return;

            setButtonVisibleMethod = Obfuscate.getMethodName("FragmentFloatingButton", "setButtonVisible");
            String updateMethodName = Obfuscate.getMethodName("DialogsActivity", "updateFloatingButtonVisibility");
            updateFabVisMethodObj = ReflectHelper.findMethodExactIfExists(dialogsCls, updateMethodName, boolean.class);
            if (updateFabVisMethodObj != null) {
                updateFabVisMethodObj.setAccessible(true);
            }

            BaseMethodHook syncHook = new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (!dialogsCls.isInstance(param.thisObject)) return;
                    ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                    if (isEnabled()) {
                        hideFab(param.thisObject);
                    }
                }
            };

            String daCreateView = Obfuscate.getMethodName("DialogsActivity", "createView");
            if (daCreateView == null || "createView".equals(daCreateView)) {
                String bfCreate = Obfuscate.getMethodName("BaseFragment", "createView");
                if (bfCreate != null && !"createView".equals(bfCreate)) daCreateView = bfCreate;
            }
            Method createViewM = ReflectHelper.findMethodInHierarchy(dialogsCls, daCreateView, Context.class);
            if (createViewM == null && !"createView".equals(daCreateView)) {
                createViewM = ReflectHelper.findMethodInHierarchy(dialogsCls, "createView", Context.class);
            }
            if (createViewM != null) {
                HMethod.hookMethod(createViewM, syncHook);
            }

            String daOnResume = Obfuscate.getMethodName("DialogsActivity", "onResume");
            if (daOnResume == null || "onResume".equals(daOnResume)) {
                String bfResume = Obfuscate.getMethodName("BaseFragment", "onResume");
                if (bfResume != null && !"onResume".equals(bfResume)) daOnResume = bfResume;
            }
            Method onResumeM = ReflectHelper.findMethodInHierarchy(dialogsCls, daOnResume);
            if (onResumeM == null && !"onResume".equals(daOnResume)) {
                onResumeM = ReflectHelper.findMethodInHierarchy(dialogsCls, "onResume");
            }
            if (onResumeM != null) {
                HMethod.hookMethod(onResumeM, syncHook);
            }
            if (updateFabVisMethodObj != null) {
                HMethod.hookMethod(updateFabVisMethodObj, syncHook);
            }
            if ("floatingButtonContainer".equals(resolvedFabField)
                    && ReflectHelper.findMethodExactIfExists(dialogsCls, "hideFloatingButton", boolean.class, boolean.class) != null) {
                HMethod.hookMethod(dialogsCls, "hideFloatingButton", boolean.class, boolean.class, syncHook);
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void hideFab(Object dialogs) {
        Object fab = getFab(dialogs);
        if (fab == null) return;
        callSetButtonVisible(fab, false);
        if (fab instanceof View) {
            ((View) fab).setVisibility(View.GONE);
        }
    }

    private static void restoreFab(Object dialogs) {
        Object fab = getFab(dialogs);
        if (fab == null) return;
        if (fab instanceof View) {
            ((View) fab).setVisibility(View.VISIBLE);
        }
        if (updateFabVisMethodObj != null) {
            try {
                updateFabVisMethodObj.invoke(dialogs, false);
                return;
            } catch (Throwable ignored) {
            }
        }
        callSetButtonVisible(fab, true);
    }

    private static Object getFab(Object dialogs) {
        if (dialogs == null || resolvedFabField == null) return null;
        try {
            return ReflectHelper.getObjectField(dialogs, resolvedFabField);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void callSetButtonVisible(Object fab, boolean visible) {
        if (fab == null || setButtonVisibleMethod == null) return;
        if (ReflectHelper.findMethodExactIfExists(fab.getClass(), setButtonVisibleMethod, boolean.class, boolean.class) != null) {
            ReflectHelper.callMethod(fab, setButtonVisibleMethod, visible, false);
        }
    }
}
