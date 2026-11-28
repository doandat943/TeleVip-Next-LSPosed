package com.magiii.televipnext.features.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.settings.SettingsManager;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.messenger.UserConfig;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class HideBottomBarTabs {

    private static final int TABS_COUNT = 4;
    private static final int POSITION_CHATS = 0;
    private static final int POSITION_CONTACTS = 1;
    private static final int POSITION_CALLS_OR_SETTINGS = 2;
    private static final int POSITION_PROFILE = 3;

    private static final int INDEX_CHATS = 0;
    private static final int INDEX_CONTACTS = 1;
    private static final int INDEX_SETTINGS = 2;
    private static final int INDEX_CALLS = 3;
    private static final int INDEX_PROFILE = 4;

    private static final int PILL_MAX_WIDTH_4_TABS_DP = 344;
    private static final int PILL_MAX_WIDTH_3_TABS_DP = 312;
    private static final int PILL_MAX_WIDTH_2_TABS_DP = 224;

    private static boolean loaded;

    private static final Map<Object, Boolean> ACTIVE_MAIN_TABS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> VIEWPAGER_TO_MAIN_TABS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final ThreadLocal<Boolean> IN_APPLY = new ThreadLocal<>();
    private static final ThreadLocal<Object> TEMP_SHOW_CALLS_UC = new ThreadLocal<>();
    private static final ThreadLocal<Object> CURRENT_OVERFLOW_DIALOGS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SETTINGS_ADDED_IN_MENU = new ThreadLocal<>();

    private static String tabsField;
    private static String tabsViewField;
    private static String tabsViewWrapperField;
    private static String fadeViewField;
    private static String checkUiCallTabVisibleMethod;
    private static String selectTabMethod;
    private static String checkUiTabsPositionMethod;
    private static String checkUiFadeViewMethod;
    private static String setViewVisibleMethod;

    private static String viewPagerField;
    private static String canScrollBackwardMethod;
    private static String canScrollForwardMethod;
    private static String getCurrentPositionMethod;
    private static String scrollToPositionMethod;

    private static String getUserConfigMethod;
    private static String showCallsTabField;
    private static String showOverflowMenuMethod;

    private static Class<?> itemOptionsCls;
    private static String itemOptionsAddIconMethod;
    private static String itemOptionsAddGapMethod;
    private static String itemOptionsShowMethod;
    private static Method addIconItemMethodObj;

    private static String presentFragmentMethod;
    private static Class<?> settingsActivityCls;

    private static boolean symbolsResolved;

    private static Method setViewVisibleMethodObj;
    private static boolean setViewVisibleChecked;
    private static Method setMaxWidthMethodObj;
    private static boolean setMaxWidthChecked;
    private static Method getCurrentPositionMethodObj;
    private static boolean getCurrentPositionChecked;
    private static Method scrollToPositionMethodObj;
    private static boolean scrollToPositionChecked;
    private static Method selectTabMethodObj;
    private static boolean selectTabChecked;

    private static Set<Integer> settingsIconResIds;
    private static int settingsLabelResId = -1;

    public static boolean isHideContacts() {
        return ConfigManager.hideBottomTabContacts != null && ConfigManager.hideBottomTabContacts.isEnable();
    }

    public static boolean isHideSettingsOrCalls() {
        return ConfigManager.hideBottomTabSettingsCalls != null && ConfigManager.hideBottomTabSettingsCalls.isEnable();
    }

    public static boolean isHideProfile() {
        return ConfigManager.hideBottomTabProfile != null && ConfigManager.hideBottomTabProfile.isEnable();
    }

    public static boolean isAnyTabHidden() {
        return isHideContacts() || isHideSettingsOrCalls() || isHideProfile();
    }

    public static boolean isHideEntireBar() {
        return isHideContacts() && isHideSettingsOrCalls() && isHideProfile();
    }

    private static boolean isPositionHidden(int pos) {
        if (pos == POSITION_CONTACTS) return isHideContacts();
        if (pos == POSITION_CALLS_OR_SETTINGS) return isHideSettingsOrCalls();
        if (pos == POSITION_PROFILE) return isHideProfile();
        return false;
    }

    public static boolean isSupported() {
        return ClassLoad.getClass(ClassNames.MAIN_TABS_ACTIVITY, Utils.classLoader, false) != null;
    }

    /**
     * Hooks MainTabsActivity, ViewPagerActivityPagerLayout, and DialogsActivity's 3-dot ItemOptions
     * menu to hide selected bottom navigation tabs without locking out Settings or TeleVip Next.
     */
    public static void init() {
        if (!loaded) {
            loaded = true;
            installHooks();
        }
        refreshAll();
    }

    public static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            List<Object> snapshot;
            synchronized (ACTIVE_MAIN_TABS) {
                snapshot = new ArrayList<>(ACTIVE_MAIN_TABS.keySet());
            }
            for (Object mainTabs : snapshot) {
                if (mainTabs == null) continue;
                try {
                    applyToInstance(mainTabs, false);
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }
        });
    }

    private static void installHooks() {
        try {
            Class<?> mainTabsCls = ClassLoad.getClass(ClassNames.MAIN_TABS_ACTIVITY, Utils.classLoader, false);
            if (mainTabsCls == null) return;

            resolveSymbols();

            String mtCreateView = Obfuscate.getMethodName("MainTabsActivity", "createView");
            if (mtCreateView == null || "createView".equals(mtCreateView)) {
                String bfCreate = Obfuscate.getMethodName("BaseFragment", "createView");
                if (bfCreate != null && !"createView".equals(bfCreate)) mtCreateView = bfCreate;
            }
            Method createViewM = findMethodInHierarchy(mainTabsCls, mtCreateView, Context.class);
            if (createViewM == null && !"createView".equals(mtCreateView)) {
                createViewM = findMethodInHierarchy(mainTabsCls, "createView", Context.class);
            }
            if (createViewM != null) {
                HMethod.hookMethod(createViewM, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        registerInstance(param.thisObject);
                        applyToInstance(param.thisObject, false);
                    }
                });
            }

            String mtOnResume = Obfuscate.getMethodName("MainTabsActivity", "onResume");
            if (mtOnResume == null || "onResume".equals(mtOnResume)) {
                String bfResume = Obfuscate.getMethodName("BaseFragment", "onResume");
                if (bfResume != null && !"onResume".equals(bfResume)) mtOnResume = bfResume;
            }
            Method onResumeM = findMethodInHierarchy(mainTabsCls, mtOnResume);
            if (onResumeM == null && !"onResume".equals(mtOnResume)) {
                onResumeM = findMethodInHierarchy(mainTabsCls, "onResume");
            }
            if (onResumeM != null) {
                HMethod.hookMethod(onResumeM, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        registerInstance(param.thisObject);
                        applyToInstance(param.thisObject, false);
                    }
                });
            }

            if (ReflectHelper.findMethodExactIfExists(mainTabsCls, checkUiCallTabVisibleMethod, boolean.class, boolean.class) != null) {
                HMethod.hookMethod(mainTabsCls, checkUiCallTabVisibleMethod, boolean.class, boolean.class, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (Boolean.TRUE.equals(IN_APPLY.get())) return;
                        registerInstance(param.thisObject);
                        boolean animated = param.args.length > 1 && Boolean.TRUE.equals(param.args[1]);
                        applyToInstance(param.thisObject, animated);
                    }
                });
            }

            if (hasNoArgMethod(mainTabsCls, checkUiTabsPositionMethod)) {
                HMethod.hookMethod(mainTabsCls, checkUiTabsPositionMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (!isHideEntireBar()) return;
                        setViewGoneIfPresent(getFieldSafe(param.thisObject, tabsViewWrapperField));
                        setViewGoneIfPresent(getFieldSafe(param.thisObject, tabsViewField));
                    }
                });
            }

            if (hasNoArgMethod(mainTabsCls, checkUiFadeViewMethod)) {
                HMethod.hookMethod(mainTabsCls, checkUiFadeViewMethod, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (!isHideEntireBar()) return;
                        setViewGoneIfPresent(getFieldSafe(param.thisObject, fadeViewField));
                    }
                });
            }

            Class<?> vpLayoutCls = ClassLoad.getClass(ClassNames.VIEW_PAGER_ACTIVITY_PAGER_LAYOUT, Utils.classLoader, false);
            if (vpLayoutCls != null) {
                if (ReflectHelper.findMethodExactIfExists(vpLayoutCls, canScrollBackwardMethod, MotionEvent.class) != null) {
                    HMethod.hookMethod(vpLayoutCls, canScrollBackwardMethod, MotionEvent.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (!isAnyTabHidden() || !VIEWPAGER_TO_MAIN_TABS.containsKey(param.thisObject)) return;
                            int cur = getCurrentPosition(param.thisObject);
                            if (cur - 1 < 0 || isPositionHidden(cur - 1)) {
                                param.setResult(false);
                            }
                        }
                    });
                }

                if (ReflectHelper.findMethodExactIfExists(vpLayoutCls, canScrollForwardMethod, MotionEvent.class) != null) {
                    HMethod.hookMethod(vpLayoutCls, canScrollForwardMethod, MotionEvent.class, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (!isAnyTabHidden() || !VIEWPAGER_TO_MAIN_TABS.containsKey(param.thisObject)) return;
                            int cur = getCurrentPosition(param.thisObject);
                            if (cur + 1 >= TABS_COUNT || isPositionHidden(cur + 1)) {
                                param.setResult(false);
                            }
                        }
                    });
                }
            }

            Class<?> dialogsCls = ClassLoad.getClass(ClassNames.DIALOGS_ACTIVITY, Utils.classLoader, false);
            if (dialogsCls != null) {
                installOverflowSafetyNet(dialogsCls);
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void installOverflowSafetyNet(Class<?> dialogsCls) {
        if (showOverflowMenuMethod == null || !hasNoArgMethod(dialogsCls, showOverflowMenuMethod)) {
            return;
        }

        boolean canHookShow = itemOptionsCls != null
                && itemOptionsShowMethod != null
                && !itemOptionsShowMethod.equals(itemOptionsAddGapMethod)
                && hasNoArgMethod(itemOptionsCls, itemOptionsShowMethod);

        addIconItemMethodObj = canHookShow ? findAddIconItemMethod() : null;
        if (addIconItemMethodObj == null && showCallsTabField == null) {
            return;
        }

        HMethod.hookMethod(dialogsCls, showOverflowMenuMethod, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                if (!isHideSettingsOrCalls()) return;
                CURRENT_OVERFLOW_DIALOGS.set(param.thisObject);
                SETTINGS_ADDED_IN_MENU.set(Boolean.FALSE);
                if (showCallsTabField != null) {
                    Object uc = resolveUserConfig(param.thisObject);
                    if (uc != null) {
                        try {
                            if (!ReflectHelper.getBooleanField(uc, showCallsTabField)) {
                                ReflectHelper.setBooleanField(uc, showCallsTabField, true);
                                TEMP_SHOW_CALLS_UC.set(uc);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            @Override
            protected void afterMethod(MethodHookParam param) {
                CURRENT_OVERFLOW_DIALOGS.remove();
                SETTINGS_ADDED_IN_MENU.remove();
                Object uc = TEMP_SHOW_CALLS_UC.get();
                if (uc != null) {
                    TEMP_SHOW_CALLS_UC.remove();
                    try {
                        ReflectHelper.setBooleanField(uc, showCallsTabField, false);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });

        if (addIconItemMethodObj == null) {
            return;
        }

        BaseMethodHook unconditionalTracker = createSettingsTracker(-1, 0, 1);
        BaseMethodHook r8AddIfTracker = createSettingsTracker(3, 0, 1);
        BaseMethodHook plainAddIfTracker = createSettingsTracker(0, 1, 2);

        Set<Method> hooked = new HashSet<>();
        HMethod.hookMethod(addIconItemMethodObj, unconditionalTracker);
        hooked.add(addIconItemMethodObj);

        for (Method m : itemOptionsCls.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) || hooked.contains(m)) continue;
            Class<?>[] pt = m.getParameterTypes();
            if (pt.length != 4) continue;
            BaseMethodHook tracker = null;
            if (pt[0] == int.class && pt[1] == CharSequence.class
                    && pt[2] == Runnable.class && pt[3] == boolean.class) {
                tracker = r8AddIfTracker;
            } else if (pt[0] == boolean.class && pt[1] == int.class
                    && pt[2] == CharSequence.class && pt[3] == Runnable.class) {
                tracker = plainAddIfTracker;
            } else if (pt[0] == int.class && pt[1] == CharSequence.class
                    && pt[2] == boolean.class && pt[3] == Runnable.class) {
                tracker = unconditionalTracker;
            }
            if (tracker != null) {
                m.setAccessible(true);
                HMethod.hookMethod(m, tracker);
                hooked.add(m);
            }
        }

        HMethod.hookMethod(itemOptionsCls, itemOptionsShowMethod, new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                Object dialogs = CURRENT_OVERFLOW_DIALOGS.get();
                if (dialogs == null || Boolean.TRUE.equals(SETTINGS_ADDED_IN_MENU.get())) return;
                SETTINGS_ADDED_IN_MENU.set(Boolean.TRUE);
                try {
                    Context ctx = Utils.getCurrentActivity();
                    Set<Integer> iconIds = getSettingsIconResIds(ctx);
                    int iconRes = iconIds.isEmpty() ? 0 : iconIds.iterator().next();
                    invokeAddIconItem(param.thisObject, iconRes, resolveSettingsLabel(ctx), () -> openSettings(dialogs));
                } catch (Throwable t) {
                    Logger.e(t);
                }
            }
        });
    }

    private static BaseMethodHook createSettingsTracker(int condArgIdx, int iconArgIdx, int labelArgIdx) {
        return new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {
                if (CURRENT_OVERFLOW_DIALOGS.get() == null
                        || Boolean.TRUE.equals(SETTINGS_ADDED_IN_MENU.get())) {
                    return;
                }
                if (condArgIdx >= 0 && (param.args.length <= condArgIdx
                        || !Boolean.TRUE.equals(param.args[condArgIdx]))) {
                    return;
                }
                if (param.args.length > Math.max(iconArgIdx, labelArgIdx)
                        && isSettingsMenuEntry(param.args[iconArgIdx], param.args[labelArgIdx])) {
                    SETTINGS_ADDED_IN_MENU.set(Boolean.TRUE);
                }
            }
        };
    }

    private static Method findAddIconItemMethod() {
        if (itemOptionsCls == null || itemOptionsAddIconMethod == null) return null;
        Method m = ReflectHelper.findMethodExactIfExists(
                itemOptionsCls, itemOptionsAddIconMethod, int.class, CharSequence.class, Runnable.class);
        if (m != null) return m;
        m = ReflectHelper.findMethodExactIfExists(
                itemOptionsCls, itemOptionsAddIconMethod, int.class, CharSequence.class, Runnable.class, boolean.class);
        if (m != null) return m;
        return ReflectHelper.findMethodExactIfExists(
                itemOptionsCls, itemOptionsAddIconMethod, int.class, CharSequence.class, boolean.class, Runnable.class);
    }

    private static void invokeAddIconItem(Object io, int iconRes, String label, Runnable action) throws Throwable {
        Class<?>[] pt = addIconItemMethodObj.getParameterTypes();
        if (pt.length == 3) {
            addIconItemMethodObj.invoke(io, iconRes, label, action);
        } else if (pt[2] == Runnable.class) {
            addIconItemMethodObj.invoke(io, iconRes, label, action, false);
        } else {
            addIconItemMethodObj.invoke(io, iconRes, label, false, action);
        }
    }

    private static void resolveSymbols() {
        if (symbolsResolved) return;
        symbolsResolved = true;

        tabsField = Obfuscate.getFieldName("MainTabsActivity", "tabs");
        tabsViewField = Obfuscate.getFieldName("MainTabsActivity", "tabsView");
        tabsViewWrapperField = Obfuscate.getFieldName("MainTabsActivity", "tabsViewWrapper");
        fadeViewField = Obfuscate.getFieldName("MainTabsActivity", "fadeView");

        checkUiCallTabVisibleMethod = Obfuscate.getMethodName("MainTabsActivity", "checkUi_callTabVisible");
        selectTabMethod = Obfuscate.getMethodName("MainTabsActivity", "selectTab");
        checkUiTabsPositionMethod = Obfuscate.getMethodName("MainTabsActivity", "checkUi_tabsPosition");
        checkUiFadeViewMethod = Obfuscate.getMethodName("MainTabsActivity", "checkUi_fadeView");
        setViewVisibleMethod = Obfuscate.getMethodName("AnimatedLinearLayout", "setViewVisible");

        viewPagerField = Obfuscate.getFieldName("ViewPagerActivity", "viewPager");
        canScrollBackwardMethod = Obfuscate.getMethodName("ViewPagerActivityPagerLayout", "canScrollBackward");
        canScrollForwardMethod = Obfuscate.getMethodName("ViewPagerActivityPagerLayout", "canScrollForward");
        getCurrentPositionMethod = Obfuscate.getMethodName("ViewPagerFixed", "getCurrentPosition");
        scrollToPositionMethod = Obfuscate.getMethodName("ViewPagerFixed", "scrollToPosition");

        getUserConfigMethod = Obfuscate.getMethodName("BaseFragment", "getUserConfig");
        showCallsTabField = Obfuscate.getFieldName("UserConfig", "showCallsTab");
        showOverflowMenuMethod = Obfuscate.getMethodName("DialogsActivity", "showOverflowMenu");

        itemOptionsCls = ClassLoad.getClass(ClassNames.ITEM_OPTIONS, Utils.classLoader, false);
        itemOptionsAddIconMethod = Obfuscate.getMethodName("ItemOptions", "addIconItem");
        itemOptionsAddGapMethod = Obfuscate.getMethodName("ItemOptions", "addGap");
        itemOptionsShowMethod = Obfuscate.getMethodName("ItemOptions", "show");

        presentFragmentMethod = Obfuscate.getMethodName("BaseFragment", "presentFragment");
        settingsActivityCls = ReflectHelper.findClassIfExists(
                Obfuscate.getClassName("org.telegram.ui.SettingsActivity"), Utils.classLoader);
    }

    private static void openSettings(Object dialogsActivity) {
        if (dialogsActivity != null && settingsActivityCls != null) {
            try {
                Object fragment = ReflectHelper.newInstance(settingsActivityCls);
                if (fragment != null) {
                    Method pf = findPresentFragmentMethod(dialogsActivity.getClass(), fragment.getClass());
                    if (pf != null) {
                        pf.invoke(dialogsActivity, fragment);
                        return;
                    }
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        }
        SettingsController controller = SettingsManager.getSettingsController();
        if (controller != null) {
            AndroidUtilities.runOnUIThread(controller::openView);
        }
    }

    private static Method findPresentFragmentMethod(Class<?> dialogsCls, Class<?> fragmentCls) {
        for (String name : new String[]{presentFragmentMethod, "presentFragment"}) {
            if (name == null) continue;
            for (Class<?> c = dialogsCls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!Modifier.isStatic(m.getModifiers()) && name.equals(m.getName())
                            && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(fragmentCls)) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSettingsMenuEntry(Object iconArg, Object labelArg) {
        Context ctx = Utils.getCurrentActivity();
        if (iconArg instanceof Integer) {
            int iconRes = (Integer) iconArg;
            if (iconRes != 0 && getSettingsIconResIds(ctx).contains(iconRes)) return true;
        }
        if (labelArg instanceof CharSequence) {
            return resolveSettingsLabel(ctx).equalsIgnoreCase(labelArg.toString().trim());
        }
        return false;
    }

    private static Set<Integer> getSettingsIconResIds(Context ctx) {
        if (settingsIconResIds != null) return settingsIconResIds;
        Set<Integer> ids = new LinkedHashSet<>();
        String[] names = {"msg_settings", "msg_settings_old", "msg_settings_ny", "msg_settings_14", "msg_settings_hw"};
        Class<?> rDrawable = ClassLoad.getClass(ClassNames.DRAWABLE, Utils.classLoader, false);
        for (String name : names) {
            if (ctx != null && ctx.getResources() != null) {
                int val = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
                if (val != 0) ids.add(val);
            }
            if (rDrawable != null) {
                try {
                    int val = ReflectHelper.getStaticIntField(rDrawable, name);
                    if (val != 0) ids.add(val);
                } catch (Throwable ignored) {
                }
            }
        }
        if (!ids.isEmpty()) settingsIconResIds = ids;
        return ids;
    }

    private static String resolveSettingsLabel(Context ctx) {
        if (ctx != null && ctx.getResources() != null) {
            if (settingsLabelResId == -1) {
                int resId = ctx.getResources().getIdentifier("Settings", "string", ctx.getPackageName());
                if (resId == 0) {
                    Class<?> rString = ClassLoad.getClass("org.telegram.messenger.R$string", Utils.classLoader, false);
                    if (rString != null) {
                        try {
                            resId = ReflectHelper.getStaticIntField(rString, "Settings");
                        } catch (Throwable ignored) {
                        }
                    }
                }
                settingsLabelResId = resId;
            }
            if (settingsLabelResId > 0) {
                try {
                    String s = ctx.getString(settingsLabelResId);
                    if (s != null && !s.isEmpty()) return s;
                } catch (Throwable ignored) {
                }
            }
        }
        return "Settings";
    }

    private static void registerInstance(Object mainTabs) {
        if (mainTabs == null) return;
        ACTIVE_MAIN_TABS.put(mainTabs, Boolean.TRUE);
        Object vp = getFieldSafe(mainTabs, viewPagerField);
        if (vp != null) {
            VIEWPAGER_TO_MAIN_TABS.put(vp, mainTabs);
        }
    }

    private static void applyToInstance(Object mainTabs, boolean animated) {
        if (mainTabs == null || Boolean.TRUE.equals(IN_APPLY.get())) return;
        resolveSymbols();
        IN_APPLY.set(Boolean.TRUE);
        try {
            Object tabsViewObj = getFieldSafe(mainTabs, tabsViewField);
            Object tabsArrObj = getFieldSafe(mainTabs, tabsField);
            if (!(tabsViewObj instanceof View) || !(tabsArrObj instanceof Object[])) {
                return;
            }
            View tabsView = (View) tabsViewObj;
            Object[] tabs = (Object[]) tabsArrObj;
            if (tabs.length <= INDEX_PROFILE) {
                return;
            }

            if (!setViewVisibleChecked) {
                setViewVisibleChecked = true;
                setViewVisibleMethodObj = findMethodInHierarchy(
                        tabsView.getClass(), setViewVisibleMethod, View.class, boolean.class, boolean.class);
            }
            if (!setMaxWidthChecked) {
                setMaxWidthChecked = true;
                setMaxWidthMethodObj = findMethodInHierarchy(
                        tabsView.getClass(), "setMaxWidth", int.class);
            }

            boolean showCallsTab = false;
            Object uc = resolveUserConfig(mainTabs);
            if (uc != null && showCallsTabField != null) {
                try {
                    showCallsTab = ReflectHelper.getBooleanField(uc, showCallsTabField);
                } catch (Throwable ignored) {
                }
            }

            boolean visContacts = !isHideContacts();
            boolean visSettings = !showCallsTab && !isHideSettingsOrCalls();
            boolean visCalls = showCallsTab && !isHideSettingsOrCalls();
            boolean visProfile = !isHideProfile();

            if (setViewVisibleMethodObj != null) {
                setTabVisible(tabsView, tabs[INDEX_CHATS], true, animated);
                setTabVisible(tabsView, tabs[INDEX_CONTACTS], visContacts, animated);
                setTabVisible(tabsView, tabs[INDEX_SETTINGS], visSettings, animated);
                setTabVisible(tabsView, tabs[INDEX_CALLS], visCalls, animated);
                setTabVisible(tabsView, tabs[INDEX_PROFILE], visProfile, animated);
            } else {
                setViewVisibility(tabs[INDEX_CONTACTS], visContacts);
                setViewVisibility(tabs[INDEX_SETTINGS], visSettings);
                setViewVisibility(tabs[INDEX_CALLS], visCalls);
                setViewVisibility(tabs[INDEX_PROFILE], visProfile);
            }

            int visibleCount = 1 + (visContacts ? 1 : 0) + ((visSettings || visCalls) ? 1 : 0) + (visProfile ? 1 : 0);
            if (setMaxWidthMethodObj != null) {
                int maxWidthDp = visibleCount >= 4 ? PILL_MAX_WIDTH_4_TABS_DP
                        : (visibleCount == 3 ? PILL_MAX_WIDTH_3_TABS_DP : PILL_MAX_WIDTH_2_TABS_DP);
                setMaxWidthMethodObj.invoke(tabsView, AndroidUtilities.dp(maxWidthDp));
            }

            Object wrapperObj = getFieldSafe(mainTabs, tabsViewWrapperField);
            Object fadeObj = getFieldSafe(mainTabs, fadeViewField);
            boolean hideAll = isHideEntireBar();
            if (hideAll) {
                setViewGoneIfPresent(wrapperObj);
                setViewGoneIfPresent(tabsView);
                setViewGoneIfPresent(fadeObj);
            } else {
                if (wrapperObj instanceof View && ((View) wrapperObj).getVisibility() != View.VISIBLE) {
                    ((View) wrapperObj).setVisibility(View.VISIBLE);
                }
                if (!invokeNoArgIfExists(mainTabs, checkUiTabsPositionMethod)) {
                    tabsView.setVisibility(View.VISIBLE);
                }
                if (!invokeNoArgIfExists(mainTabs, checkUiFadeViewMethod) && fadeObj instanceof View) {
                    ((View) fadeObj).setVisibility(View.VISIBLE);
                }
            }

            Object vp = getFieldSafe(mainTabs, viewPagerField);
            if (vp != null) {
                int curPos = getCurrentPosition(vp);
                if (isPositionHidden(curPos)) {
                    if (!selectTabChecked) {
                        selectTabChecked = true;
                        selectTabMethodObj = findMethodInHierarchy(
                                mainTabs.getClass(), selectTabMethod, int.class, boolean.class);
                    }
                    if (selectTabMethodObj != null) {
                        selectTabMethodObj.invoke(mainTabs, POSITION_CHATS, animated);
                    }
                    scrollToPosition(vp, POSITION_CHATS);
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        } finally {
            IN_APPLY.remove();
        }
    }

    private static void setTabVisible(View tabsView, Object tabObj, boolean visible, boolean animated) {
        if (tabObj instanceof View && setViewVisibleMethodObj != null) {
            try {
                setViewVisibleMethodObj.invoke(tabsView, (View) tabObj, visible, animated);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void setViewVisibility(Object obj, boolean visible) {
        if (obj instanceof View) {
            ((View) obj).setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setViewGoneIfPresent(Object obj) {
        if (obj instanceof View && ((View) obj).getVisibility() != View.GONE) {
            ((View) obj).setVisibility(View.GONE);
        }
    }

    private static int getCurrentPosition(Object vp) {
        if (vp == null || getCurrentPositionMethod == null) return POSITION_CHATS;
        if (!getCurrentPositionChecked) {
            getCurrentPositionChecked = true;
            getCurrentPositionMethodObj = findMethodInHierarchy(vp.getClass(), getCurrentPositionMethod);
        }
        if (getCurrentPositionMethodObj == null) return POSITION_CHATS;
        try {
            Object res = getCurrentPositionMethodObj.invoke(vp);
            return res instanceof Integer ? (Integer) res : POSITION_CHATS;
        } catch (Throwable t) {
            return POSITION_CHATS;
        }
    }

    private static void scrollToPosition(Object vp, int pos) {
        if (vp == null || scrollToPositionMethod == null) return;
        if (!scrollToPositionChecked) {
            scrollToPositionChecked = true;
            scrollToPositionMethodObj = findMethodInHierarchy(vp.getClass(), scrollToPositionMethod, int.class);
        }
        if (scrollToPositionMethodObj == null) return;
        try {
            scrollToPositionMethodObj.invoke(vp, pos);
        } catch (Throwable ignored) {
        }
    }

    private static Object resolveUserConfig(Object fragmentObj) {
        if (fragmentObj != null && getUserConfigMethod != null) {
            try {
                Object uc = ReflectHelper.callMethod(fragmentObj, getUserConfigMethod);
                if (uc != null) return uc;
            } catch (Throwable ignored) {
            }
        }
        try {
            return UserConfig.getUserConfig().get_UserConfig();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getFieldSafe(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        if (ReflectHelper.findFieldIfExists(target.getClass(), fieldName) == null) return null;
        return ReflectHelper.getObjectField(target, fieldName);
    }

    private static boolean hasNoArgMethod(Class<?> cls, String name) {
        return findMethodInHierarchy(cls, name) != null;
    }

    private static boolean invokeNoArgIfExists(Object target, String name) {
        if (target == null || name == null) return false;
        Method m = findMethodInHierarchy(target.getClass(), name);
        if (m == null) return false;
        try {
            m.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethodInHierarchy(Class<?> cls, String name, Class<?>... params) {
        if (cls == null || name == null) return null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
