package com.magiii.televipnext.features.ui;

import android.content.Context;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class AlwaysShowProxy {

    public static final int MODE_OFF = 0;
    public static final int MODE_MENU = 1;
    public static final int MODE_TOP_BAR = 2;

    private static final int PROXY_MENU_ITEM_ID = -94301;
    private static final int SEARCH_BUTTON_TAG = -47;

    private static boolean loaded;

    private static final Map<Object, Boolean> ACTIVE_DIALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> CREATED_DIALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, ProxyEntry> ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> NATIVE_TO_TOPBAR_DRAWABLE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, boolean[]> LAST_DRAWABLE_STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final ThreadLocal<Object> HIDDEN_SUB_ITEM = new ThreadLocal<>();
    private static final ThreadLocal<Object> MENU_DIALOGS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PROXY_ADDED_IN_MENU = new ThreadLocal<>();

    private static Class<?> proxyDrawableCls;
    private static String proxyDrawableField;
    private static String setConnectedMethod;
    private static String updateProxyMethod;
    private static String updateHeaderVisMethod;
    private static String overflowItemField;
    private static String createMenuMethod;
    private static String addItemDrawableMethod;
    private static String showOverflowMenuMethod;
    private static String proxySubItemField;
    private static Class<?> itemOptionsCls;
    private static String itemOptionsAddGapMethod;
    private static String itemOptionsAddViewMethod;
    private static String itemOptionsShowMethod;

    private static final class ProxyEntry {
        final View proxyItem;
        final Object topBarDrawable;
        final boolean isNative;

        ProxyEntry(View proxyItem, Object topBarDrawable, boolean isNative) {
            this.proxyItem = proxyItem;
            this.topBarDrawable = topBarDrawable;
            this.isNative = isNative;
        }
    }

    public static int getMode() {
        return ConfigManager.alwaysShowProxy != null
                ? ConfigManager.alwaysShowProxy.getIntValue()
                : MODE_OFF;
    }

    public static boolean isEnabled() {
        return getMode() != MODE_OFF;
    }

    /**
     * Hooks DialogsActivity, ProxyDrawable, and ItemOptions to control where the proxy shortcut appears.
     */
    public static void init() {
        if (!loaded) {
            loaded = true;
            installHooks();
        }
        refreshAll();
    }

    private static void installHooks() {
        try {
            Class<?> dialogsCls = ClassLoad.getClass(ClassNames.DIALOGS_ACTIVITY, Utils.classLoader, false);
            if (dialogsCls == null) return;

            resolveSymbols(dialogsCls);
            if (proxyDrawableCls == null || proxyDrawableField == null) {
                Logger.w("AlwaysShowProxy: missing proxy drawable symbols");
                return;
            }

            if (setConnectedMethod != null) {
                HMethod.hookMethod(proxyDrawableCls, setConnectedMethod,
                        boolean.class, boolean.class, boolean.class,
                        new BaseMethodHook() {
                            @Override
                            protected void afterMethod(MethodHookParam param) {
                                boolean enabled = Boolean.TRUE.equals(param.args[0]);
                                boolean connected = Boolean.TRUE.equals(param.args[1]);
                                boolean animated = Boolean.TRUE.equals(param.args[2]);
                                LAST_DRAWABLE_STATE.put(param.thisObject, new boolean[]{enabled, connected});

                                Object topBar = NATIVE_TO_TOPBAR_DRAWABLE.get(param.thisObject);
                                if (topBar != null) {
                                    ReflectHelper.callMethod(topBar, setConnectedMethod, enabled, connected, animated);
                                }
                            }
                        });
            }

            HMethod.hookMethod(dialogsCls, "createView", Context.class, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    CREATED_DIALOGS.remove(param.thisObject);
                    ENTRIES.remove(param.thisObject);
                }

                @Override
                protected void afterMethod(MethodHookParam param) {
                    CREATED_DIALOGS.put(param.thisObject, Boolean.TRUE);
                    ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                    if (isEnabled()) {
                        ensureProxyButton(param.thisObject, (Context) param.args[0]);
                        syncVisibility(param.thisObject);
                    }
                }
            });

            if (hasMethodNamed(dialogsCls, updateProxyMethod)) {
                XposedCompat.hookAllMethods(dialogsCls, updateProxyMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                        if (isEnabled() && (CREATED_DIALOGS.containsKey(param.thisObject)
                                || ENTRIES.containsKey(param.thisObject))) {
                            ensureProxyButton(param.thisObject, null);
                        }
                        ProxyEntry entry = ENTRIES.get(param.thisObject);
                        if (entry != null) {
                            if (setConnectedMethod == null && entry.topBarDrawable != null) {
                                Object nativeDrawable = ReflectHelper.getObjectField(param.thisObject, proxyDrawableField);
                                if (nativeDrawable != null) {
                                    syncDrawableStateByFields(nativeDrawable, entry.topBarDrawable);
                                }
                            }
                            syncVisibility(param.thisObject);
                        }
                    }
                });
            }

            if (hasNoArgMethod(dialogsCls, updateHeaderVisMethod)) {
                HMethod.hookMethod(dialogsCls, updateHeaderVisMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                        if (getMode() == MODE_TOP_BAR && (CREATED_DIALOGS.containsKey(param.thisObject)
                                || ENTRIES.containsKey(param.thisObject))) {
                            ensureProxyButton(param.thisObject, null);
                        }
                        if (ENTRIES.containsKey(param.thisObject)) {
                            syncVisibility(param.thisObject);
                        }
                    }
                });
            }

            hookOverflowMenu(dialogsCls);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    /**
     * Re-applies the active proxy display mode to all tracked DialogsActivity instances on the UI thread.
     */
    public static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            List<Object> snapshot;
            synchronized (ACTIVE_DIALOGS) {
                snapshot = new ArrayList<>(ACTIVE_DIALOGS.keySet());
            }
            for (Object dialogs : snapshot) {
                if (dialogs == null) continue;
                try {
                    if (isEnabled()) {
                        ensureProxyButton(dialogs, null);
                    }
                    syncVisibility(dialogs);
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }
        });
    }

    private static void hookOverflowMenu(Class<?> dialogsCls) {
        if (!hasNoArgMethod(dialogsCls, showOverflowMenuMethod)
                || ReflectHelper.findFieldIfExists(dialogsCls, proxySubItemField) == null) {
            return;
        }

        HMethod.hookMethod(dialogsCls, showOverflowMenuMethod, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                ACTIVE_DIALOGS.put(param.thisObject, Boolean.TRUE);
                int mode = getMode();
                Object subItem = ReflectHelper.getObjectField(param.thisObject, proxySubItemField);
                if (mode == MODE_TOP_BAR) {
                    if (subItem != null) {
                        HIDDEN_SUB_ITEM.set(subItem);
                        ReflectHelper.setObjectField(param.thisObject, proxySubItemField, null);
                    }
                } else if (mode == MODE_MENU) {
                    if (subItem instanceof View) {
                        // Cleared before showOverflowMenu so ItemOptions#show knows the main menu configured it
                        ((View) subItem).setOnClickListener(null);
                        MENU_DIALOGS.set(param.thisObject);
                        PROXY_ADDED_IN_MENU.set(Boolean.FALSE);
                    }
                }
            }

            @Override
            protected void afterMethod(MethodHookParam param) {
                Object saved = HIDDEN_SUB_ITEM.get();
                if (saved != null) {
                    HIDDEN_SUB_ITEM.remove();
                    ReflectHelper.setObjectField(param.thisObject, proxySubItemField, saved);
                }
                MENU_DIALOGS.remove();
                PROXY_ADDED_IN_MENU.remove();
            }
        });

        if (itemOptionsCls != null
                && itemOptionsAddViewMethod != null
                && itemOptionsAddGapMethod != null
                && itemOptionsShowMethod != null) {
            if (hasMethodNamed(itemOptionsCls, itemOptionsAddViewMethod)) {
                XposedCompat.hookAllMethods(itemOptionsCls, itemOptionsAddViewMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (MENU_DIALOGS.get() != null) {
                            PROXY_ADDED_IN_MENU.set(Boolean.TRUE);
                        }
                    }
                });
            }
            if (hasNoArgMethod(itemOptionsCls, itemOptionsShowMethod)
                    && !itemOptionsShowMethod.equals(itemOptionsAddGapMethod)) {
                HMethod.hookMethod(itemOptionsCls, itemOptionsShowMethod, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        Object dialogs = MENU_DIALOGS.get();
                        if (dialogs == null || Boolean.TRUE.equals(PROXY_ADDED_IN_MENU.get())) {
                            return;
                        }
                        Object subItem = ReflectHelper.getObjectField(dialogs, proxySubItemField);
                        if (subItem instanceof View && ((View) subItem).hasOnClickListeners()) {
                            PROXY_ADDED_IN_MENU.set(Boolean.TRUE);
                            ReflectHelper.callMethod(param.thisObject, itemOptionsAddGapMethod);
                            ReflectHelper.callMethod(param.thisObject, itemOptionsAddViewMethod, subItem);
                        }
                    }
                });
            }
        }
    }

    private static void resolveSymbols(Class<?> dialogsCls) {
        proxyDrawableCls = ClassLoad.getClass(ClassNames.PROXY_DRAWABLE, Utils.classLoader, false);
        proxyDrawableField = Obfuscate.getFieldName("DialogsActivity", "proxyDrawable");
        setConnectedMethod = Obfuscate.getMethodName("ProxyDrawable", "setConnected");
        updateProxyMethod = Obfuscate.getMethodName("DialogsActivity", "updateProxyButton");
        updateHeaderVisMethod = Obfuscate.getMethodName("DialogsActivity", "updateHeaderVisibility");
        overflowItemField = Obfuscate.getFieldName("DialogsActivity", "overflowItem");
        createMenuMethod = Obfuscate.getMethodName("ActionBar", "createMenu");
        addItemDrawableMethod = Obfuscate.getMethodName("ActionBarMenu", "addItemDrawable");

        showOverflowMenuMethod = Obfuscate.getMethodName("DialogsActivity", "showOverflowMenu");
        proxySubItemField = Obfuscate.getFieldName("DialogsActivity", "proxySubItem");
        itemOptionsCls = ClassLoad.getClass(ClassNames.ITEM_OPTIONS, Utils.classLoader, false);
        itemOptionsAddGapMethod = Obfuscate.getMethodName("ItemOptions", "addGap");
        itemOptionsAddViewMethod = Obfuscate.getMethodName("ItemOptions", "addViewSubItem");
        itemOptionsShowMethod = Obfuscate.getMethodName("ItemOptions", "show");

        if (proxyDrawableCls == null || ReflectHelper.findFieldIfExists(dialogsCls, proxyDrawableField) == null) {
            Class<?> fallbackDrawableCls = null;
            String fallbackFieldName = null;
            for (Field f : dialogsCls.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if (!Drawable.class.isAssignableFrom(ft)) continue;
                if (ft.getName().startsWith("android.")) continue;
                Method m = findTripleBooleanVoidMethod(ft);
                if (m != null) {
                    proxyDrawableCls = ft;
                    proxyDrawableField = f.getName();
                    setConnectedMethod = m.getName();
                    break;
                }
                if (fallbackDrawableCls == null && isLikelyInlinedProxyDrawable(ft)) {
                    fallbackDrawableCls = ft;
                    fallbackFieldName = f.getName();
                }
            }
            if (proxyDrawableCls == null && fallbackDrawableCls != null) {
                proxyDrawableCls = fallbackDrawableCls;
                proxyDrawableField = fallbackFieldName;
                setConnectedMethod = null;
            }
        } else if (setConnectedMethod == null
                || ReflectHelper.findMethodExactIfExists(
                        proxyDrawableCls, setConnectedMethod, boolean.class, boolean.class, boolean.class) == null) {
            Method m = findTripleBooleanVoidMethod(proxyDrawableCls);
            setConnectedMethod = m != null ? m.getName() : null;
        }
    }

    private static boolean isLikelyInlinedProxyDrawable(Class<?> cls) {
        boolean hasDrawableSetter = false;
        int nonSyntheticMethods = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.isSynthetic()) continue;
            nonSyntheticMethods++;
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() == void.class && p.length == 1 && p[0] == Drawable.class) {
                hasDrawableSetter = true;
            }
        }
        return hasDrawableSetter && nonSyntheticMethods <= 10;
    }

    private static void syncDrawableStateByFields(Object src, Object dst) {
        if (src == null || dst == null || proxyDrawableCls == null) return;
        for (Field f : proxyDrawableCls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Class<?> ft = f.getType();
            if (!ft.isPrimitive()) continue;
            try {
                f.setAccessible(true);
                f.set(dst, f.get(src));
            } catch (Throwable ignored) {}
        }
        if (dst instanceof Drawable) {
            ((Drawable) dst).invalidateSelf();
        }
    }

    private static Method findTripleBooleanVoidMethod(Class<?> cls) {
        for (Method m : cls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0] == boolean.class && p[1] == boolean.class && p[2] == boolean.class) {
                return m;
            }
        }
        return null;
    }

    private static boolean hasMethodNamed(Class<?> cls, String name) {
        if (cls == null || name == null) return false;
        for (Method m : cls.getDeclaredMethods()) {
            if (name.equals(m.getName())) return true;
        }
        return false;
    }

    private static boolean hasNoArgMethod(Class<?> cls, String name) {
        if (cls == null || name == null) return false;
        return ReflectHelper.findMethodExactIfExists(cls, name) != null;
    }

    private static void ensureProxyButton(Object dialogsActivity, Context context) {
        if (dialogsActivity == null) return;

        Object nativeDrawable = ReflectHelper.getObjectField(dialogsActivity, proxyDrawableField);
        if (nativeDrawable == null) return;

        Object actionBar = ReflectHelper.getObjectField(dialogsActivity, "actionBar");
        if (actionBar == null) return;

        ViewGroup actionBarMenu = obtainActionBarMenu(actionBar);
        if (actionBarMenu == null) {
            return;
        }

        ProxyEntry existing = ENTRIES.get(dialogsActivity);
        if (existing != null) {
            if (existing.proxyItem != null && existing.proxyItem.getParent() == actionBarMenu) {
                return;
            }
            ENTRIES.remove(dialogsActivity);
        }

        // Classic Telegram builds already add a native ActionBarMenuItem holding proxyDrawable
        View nativeItem = findNativeProxyItem(actionBarMenu, nativeDrawable);
        if (nativeItem != null) {
            ENTRIES.put(dialogsActivity, new ProxyEntry(nativeItem, null, true));
            return;
        }

        if (getMode() != MODE_TOP_BAR) {
            return;
        }

        Object existingByTag = actionBarMenu.findViewWithTag(Integer.valueOf(PROXY_MENU_ITEM_ID));
        if (existingByTag instanceof View) {
            View taggedView = (View) existingByTag;
            ImageView iv = findFirstImageView(taggedView);
            Drawable recovered = iv != null ? iv.getDrawable() : null;
            Object recoveredDrawable = (recovered != null && proxyDrawableCls.isInstance(recovered)) ? recovered : null;
            if (recoveredDrawable != null) {
                NATIVE_TO_TOPBAR_DRAWABLE.put(nativeDrawable, recoveredDrawable);
            }
            ENTRIES.put(dialogsActivity, new ProxyEntry(taggedView, recoveredDrawable, false));
            return;
        }

        try {
            Context ctx = context != null ? context : actionBarMenu.getContext();
            if (ctx == null) return;

            Object topBarDrawable = ReflectHelper.newInstance(proxyDrawableCls, ctx);
            if (setConnectedMethod != null) {
                boolean[] state = LAST_DRAWABLE_STATE.get(nativeDrawable);
                boolean enabled = state != null && state[0];
                boolean connected = state != null && state[1];
                ReflectHelper.callMethod(topBarDrawable, setConnectedMethod, enabled, connected, false);
            } else {
                syncDrawableStateByFields(nativeDrawable, topBarDrawable);
            }

            Method addItemMethod = resolveAddItemDrawableMethod(actionBarMenu.getClass());
            if (addItemMethod == null) {
                return;
            }

            View proxyItem = (View) addItemMethod.invoke(actionBarMenu, PROXY_MENU_ITEM_ID, topBarDrawable);
            if (proxyItem == null) return;

            proxyItem.setContentDescription("Proxy");
            proxyItem.setTag(Integer.valueOf(PROXY_MENU_ITEM_ID));

            reorderBeforeSearchOrOverflow(dialogsActivity, actionBarMenu, proxyItem);
            proxyItem.setOnClickListener(v -> openProxyList(dialogsActivity));

            ProxyEntry entry = new ProxyEntry(proxyItem, topBarDrawable, false);
            NATIVE_TO_TOPBAR_DRAWABLE.put(nativeDrawable, topBarDrawable);
            ENTRIES.put(dialogsActivity, entry);
            syncIconColorFilter(dialogsActivity, actionBarMenu, proxyItem, topBarDrawable);
            syncVisibility(dialogsActivity);
            requestMenuAndParentLayout(actionBarMenu);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static ViewGroup obtainActionBarMenu(Object actionBar) {
        if (createMenuMethod != null
                && ReflectHelper.findMethodExactIfExists(actionBar.getClass(), createMenuMethod) != null) {
            Object res = ReflectHelper.callMethod(actionBar, createMenuMethod);
            if (res instanceof ViewGroup) return (ViewGroup) res;
        }
        if (ReflectHelper.findMethodExactIfExists(actionBar.getClass(), "createMenu") != null) {
            Object res = ReflectHelper.callMethod(actionBar, "createMenu");
            if (res instanceof ViewGroup) return (ViewGroup) res;
        }
        for (Class<?> c = actionBar.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (ViewGroup.class.isAssignableFrom(f.getType())
                        && resolveAddItemDrawableMethod(f.getType()) != null) {
                    f.setAccessible(true);
                    try {
                        Object val = f.get(actionBar);
                        if (val instanceof ViewGroup) return (ViewGroup) val;
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    private static Method resolveAddItemDrawableMethod(Class<?> menuCls) {
        if (addItemDrawableMethod != null) {
            Method m = ReflectHelper.findMethodExactIfExists(menuCls, addItemDrawableMethod, int.class, Drawable.class);
            if (m != null) return m;
        }
        Method fallback = ReflectHelper.findMethodExactIfExists(menuCls, "addItem", int.class, Drawable.class);
        if (fallback != null) return fallback;

        for (Method m : menuCls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers())) continue;
            if (!View.class.isAssignableFrom(m.getReturnType())) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == int.class && p[1] == Drawable.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static View findNativeProxyItem(ViewGroup menu, Object nativeDrawable) {
        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (containsProxyDrawable(child, nativeDrawable)) {
                return child;
            }
        }
        return null;
    }

    private static boolean containsProxyDrawable(View view, Object nativeDrawable) {
        if (view instanceof ImageView) {
            Drawable d = ((ImageView) view).getDrawable();
            if (d == nativeDrawable || (d != null && proxyDrawableCls.isInstance(d))) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (containsProxyDrawable(vg.getChildAt(i), nativeDrawable)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void reorderBeforeSearchOrOverflow(Object dialogsActivity, ViewGroup menu, View proxyItem) {
        View anchor = null;
        View overflow = getOverflowItem(dialogsActivity);
        if (overflow != null && overflow != proxyItem && overflow.getParent() == menu) {
            anchor = overflow;
        }
        if (anchor == null) {
            for (int i = 0; i < menu.getChildCount(); i++) {
                View child = menu.getChildAt(i);
                if (child != proxyItem && Integer.valueOf(SEARCH_BUTTON_TAG).equals(child.getTag())) {
                    anchor = child;
                    break;
                }
            }
        }

        if (anchor != null) {
            int anchorIdx = menu.indexOfChild(anchor);
            if (anchorIdx >= 0 && menu.indexOfChild(proxyItem) == anchorIdx - 1) {
                return;
            }
        }

        ViewGroup.LayoutParams lp = proxyItem.getLayoutParams();
        if (proxyItem.getParent() == menu) {
            menu.removeView(proxyItem);
        }
        int insertIdx = -1;
        if (anchor != null) {
            insertIdx = menu.indexOfChild(anchor);
        } else if (menu.getChildCount() > 0) {
            insertIdx = menu.getChildCount() - 1;
        }
        if (insertIdx < 0 || insertIdx > menu.getChildCount()) {
            insertIdx = menu.getChildCount();
        }
        menu.addView(proxyItem, insertIdx, lp);
    }

    private static void syncIconColorFilter(
            Object dialogsActivity, ViewGroup actionBarMenu, View proxyItem, Object topBarDrawable) {
        ImageView proxyImg = findFirstImageView(proxyItem);
        if (proxyImg == null) return;

        ColorFilter cf = null;
        View overflow = getOverflowItem(dialogsActivity);
        ImageView overflowImg = findFirstImageView(overflow);
        if (overflowImg != null) {
            cf = overflowImg.getColorFilter();
        }
        if (cf == null && actionBarMenu == null && proxyItem.getParent() instanceof ViewGroup) {
            actionBarMenu = (ViewGroup) proxyItem.getParent();
        }
        if (cf == null && actionBarMenu != null) {
            for (int i = 0; i < actionBarMenu.getChildCount(); i++) {
                View child = actionBarMenu.getChildAt(i);
                if (child == proxyItem) continue;
                ImageView iv = findFirstImageView(child);
                if (iv != null && iv.getColorFilter() != null) {
                    cf = iv.getColorFilter();
                    break;
                }
            }
        }

        if (cf != null) {
            if (proxyImg.getColorFilter() != cf) {
                proxyImg.setColorFilter(cf);
            }
            if (topBarDrawable instanceof Drawable) {
                Drawable d = (Drawable) topBarDrawable;
                if (d.getColorFilter() != cf) {
                    d.setColorFilter(cf);
                }
            }
        }
    }

    private static void requestMenuAndParentLayout(ViewGroup actionBarMenu) {
        if (actionBarMenu == null) return;
        actionBarMenu.requestLayout();
        actionBarMenu.invalidate();
        if (actionBarMenu.getParent() instanceof View) {
            View parent = (View) actionBarMenu.getParent();
            parent.requestLayout();
            parent.invalidate();
        }
    }

    private static ImageView findFirstImageView(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ImageView found = findFirstImageView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View getOverflowItem(Object dialogsActivity) {
        if (overflowItemField == null) return null;
        try {
            Object v = ReflectHelper.getObjectField(dialogsActivity, overflowItemField);
            return v instanceof View ? (View) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void openProxyList(Object dialogsActivity) {
        try {
            Class<?> proxyListCls = ClassLoad.getClass(ClassNames.PROXY_LIST_ACTIVITY, Utils.classLoader, false);
            if (proxyListCls == null) return;
            Object fragment = ReflectHelper.newInstance(proxyListCls);
            ReflectHelper.callMethod(dialogsActivity, "presentFragment", fragment);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void syncVisibility(Object dialogsActivity) {
        if (dialogsActivity == null) return;
        ProxyEntry entry = ENTRIES.get(dialogsActivity);
        if (entry == null || entry.proxyItem == null) return;

        int oldVis = entry.proxyItem.getVisibility();
        int mode = getMode();
        if (entry.isNative) {
            if (mode != MODE_OFF && !isSearchFieldVisible(dialogsActivity)) {
                entry.proxyItem.setVisibility(View.VISIBLE);
            }
            if (entry.proxyItem.getVisibility() != oldVis && entry.proxyItem.getParent() instanceof ViewGroup) {
                requestMenuAndParentLayout((ViewGroup) entry.proxyItem.getParent());
            }
            return;
        }

        ViewGroup menu = entry.proxyItem.getParent() instanceof ViewGroup
                ? (ViewGroup) entry.proxyItem.getParent()
                : null;

        if (mode != MODE_TOP_BAR) {
            entry.proxyItem.setVisibility(View.GONE);
            if (oldVis != View.GONE && menu != null) {
                requestMenuAndParentLayout(menu);
            }
            return;
        }

        syncIconColorFilter(dialogsActivity, menu, entry.proxyItem, entry.topBarDrawable);

        View overflow = getOverflowItem(dialogsActivity);
        if (overflow != null) {
            entry.proxyItem.setAlpha(overflow.getAlpha());
            entry.proxyItem.setScaleX(overflow.getScaleX());
            entry.proxyItem.setScaleY(overflow.getScaleY());
            entry.proxyItem.setVisibility(overflow.getVisibility());
        } else {
            entry.proxyItem.setAlpha(1f);
            entry.proxyItem.setScaleX(1f);
            entry.proxyItem.setScaleY(1f);
            entry.proxyItem.setVisibility(View.VISIBLE);
        }
        if (entry.proxyItem.getVisibility() != oldVis && menu != null) {
            requestMenuAndParentLayout(menu);
        }
    }

    private static boolean isSearchFieldVisible(Object dialogsActivity) {
        try {
            Object actionBar = ReflectHelper.getObjectField(dialogsActivity, "actionBar");
            if (actionBar != null && ReflectHelper.findFieldIfExists(actionBar.getClass(), "isSearchFieldVisible") != null) {
                return ReflectHelper.getBooleanField(actionBar, "isSearchFieldVisible");
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
