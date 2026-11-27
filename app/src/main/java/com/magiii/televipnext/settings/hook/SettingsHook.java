package com.magiii.televipnext.settings.hook;


import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Drawable.GhostDrawable;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.Adapters.DrawerLayoutAdapter;
import com.magiii.televipnext.virtuals.SettingsIconResolver;
import com.magiii.televipnext.virtuals.messenger.LocaleController;
import com.magiii.televipnext.virtuals.ui.Components.UItem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class SettingsHook {

    private Constructor<?> itemConstructor;

    public void newSettings(Class<?> SettingsActivityClass, Class<?> SettingsActivity$SettingCell$FactoryClass, SettingsController settingsController){
        try {

            GhostDrawable ghostDrawable = new GhostDrawable();

            Class<?> settingCellClass = ClassLoad.getClass(ClassNames.SETTINGS_ACTIVITY_SETTING_CELL, Utils.classLoader, false);
            if (settingCellClass != null) {
                XposedCompat.hookAllMethods(settingCellClass, Obfuscate.getMethodName("SettingsActivity$SettingCell", "set"), new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        if (param.args.length >= 3 && param.args[2] instanceof Integer && ((Integer) param.args[2]) == 8353847) {
                            ImageView iconView = findSettingCellIconView(param.thisObject);
                            if (iconView != null) {
                                iconView.setImageDrawable(ghostDrawable);
                            }
                        }
                    }
                });
            } else {
                String bindName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "bindView");
                if (bindName != null) {
                    XposedCompat.hookAllMethods(SettingsActivity$SettingCell$FactoryClass, bindName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (param.args.length >= 2 && param.args[0] instanceof View && param.args[1] != null) {
                                UItem uItem = new UItem(param.args[1]);
                                if (uItem.getID() == 8353847) {
                                    ImageView iconView = findSettingCellIconView(param.args[0]);
                                    if (iconView != null) {
                                        iconView.setImageDrawable(ghostDrawable);
                                    }
                                }
                            }
                        }
                    });
                }
            }

            final Class<?> UItemClass = ClassLoad.getClass(ClassNames.UITEM);

            final String ofName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "of");
            Class<?> fillTargetClass = ReflectHelper.findClassIfExists(
                    Obfuscate.getClassName("org.telegram.ui.SettingsActivity$FillItemsLambda"), Utils.classLoader);
            if (fillTargetClass == null) {
                fillTargetClass = SettingsActivityClass;
            }
            XposedCompat.hookAllMethods(fillTargetClass, Obfuscate.getMethodName("SettingsActivity", "fillItems"),
                    new BaseMethodHook() {
                        @Override
                        @SuppressWarnings("unchecked")
                        protected void afterMethod(final MethodHookParam param) {
                            ArrayList<Object> arrayList = null;
                            for (Object arg : param.args) {
                                if (arg instanceof ArrayList) {
                                    arrayList = (ArrayList<Object>) arg;
                                    break;
                                }
                            }
                            if (arrayList != null) {
                                int color1 = 0xFFF46F6F;
                                int color2 = 0xFFDF5555;

                                Object uItem = null;
                                try {
                                    String entryTitle = formatEntryTitle("TeleVip Next \uD83D\uDC7B", LocaleController.isRTL());
                                    Method m6 = ReflectHelper.findMethodExactIfExists(SettingsActivity$SettingCell$FactoryClass, ofName,
                                            int.class, int.class, int.class, int.class, CharSequence.class, CharSequence.class);
                                    if (m6 != null) {
                                        uItem = m6.invoke(null, 8353847, color1, color2, 8353847,
                                                entryTitle, null);
                                    } else {
                                        for (Method fm : SettingsActivity$SettingCell$FactoryClass.getDeclaredMethods()) {
                                            Class<?>[] pt = fm.getParameterTypes();
                                            if (Modifier.isStatic(fm.getModifiers()) && fm.getName().equals(ofName) && pt.length == 7
                                                    && pt[0] == int.class && pt[1] == int.class
                                                    && pt[2] == int.class && pt[3] == int.class
                                                    && pt[4] == CharSequence.class && pt[5] == CharSequence.class) {
                                                fm.setAccessible(true);
                                                Object arg6 = pt[6] == boolean.class ? Boolean.FALSE
                                                        : (pt[6] == int.class ? Integer.valueOf(0) : null);
                                                uItem = fm.invoke(null, 8353847, color1, color2, 8353847,
                                                        entryTitle, null, arg6);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    Logger.e(t);
                                }
                                if (uItem == null) return;

                                for (Object existing : arrayList) {
                                    if (existing != null && UItemClass != null && UItemClass.isInstance(existing)) {
                                        if (new UItem(existing).getID() == 8353847) return;
                                    }
                                }

                                boolean inserted = false;
                                for (int i = 0; i < arrayList.size(); i++) {
                                    UItem item = new UItem(arrayList.get(i));

                                    if (item.getText() != null && item.getSubtext() != null) {
                                        arrayList.add(i, uItem);
                                        Object shadowItem = createShadowItem(arrayList, UItemClass);
                                        if (shadowItem != null) {
                                            arrayList.add(i + 1, shadowItem);
                                        }
                                        inserted = true;
                                        break;
                                    }
                                }
                                if (!inserted) {
                                    for (int i = 0; i < arrayList.size(); i++) {
                                        UItem item = new UItem(arrayList.get(i));
                                        if (item.getText() != null) {
                                            arrayList.add(i, uItem);
                                            Object shadowItem = createShadowItem(arrayList, UItemClass);
                                            if (shadowItem != null) {
                                                arrayList.add(i + 1, shadowItem);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    });

            String onClickName = Obfuscate.getMethodName("SettingsActivity", "onClick");
            Class<?> clickTargetClass = ReflectHelper.findClassIfExists(
                    Obfuscate.getClassName("org.telegram.ui.SettingsActivity$OnClickLambda"), Utils.classLoader);
            if (clickTargetClass == null) {
                clickTargetClass = SettingsActivityClass;
            }
            XposedCompat.hookAllMethods(
                    clickTargetClass,
                    onClickName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(final MethodHookParam param) {
                            if (param.args == null || param.args.length < 2 || param.args.length > 5) return;
                            Object rawItem = null;
                            for (Object arg : param.args) {
                                if (arg != null && UItemClass != null && UItemClass.isInstance(arg)) {
                                    rawItem = arg;
                                    break;
                                }
                            }
                            if (rawItem != null) {
                                UItem uItem = new UItem(rawItem);
                                if (uItem.getID() == 8353847) {
                                    Logger.w("SettingsHook: opening TeleVip Settings via " + param.method.getName());
                                    settingsController.openView();
                                }
                            }
                        }
                    });
        } catch (Throwable t){
            Logger.e(t);
        }
    }

    private static String formatEntryTitle(String title, boolean telegramRtl) {
        if (!telegramRtl || title == null || title.isEmpty()) return title;
        return "\u200F\u2066" + title + "\u2069";
    }

    private static Object createShadowItem(ArrayList<Object> arrayList, Class<?> uItemClass) {
        if (uItemClass == null) return null;
        String idFieldName = Obfuscate.getFieldName("UItem", "id");
        try {
            Method asShadow = ReflectHelper.findMethodExactIfExists(uItemClass, "asShadow", CharSequence.class);
            if (asShadow != null && Modifier.isStatic(asShadow.getModifiers())) {
                asShadow.setAccessible(true);
                Object shadow = asShadow.invoke(null, (CharSequence) null);
                if (shadow != null) {
                    setShadowId(uItemClass, shadow, idFieldName);
                    return shadow;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Field viewTypeField = findViewTypeField(uItemClass, idFieldName);
            if (viewTypeField == null) return null;

            Integer shadowViewType = findShadowViewType(arrayList, uItemClass, viewTypeField);
            if (shadowViewType == null) return null;

            Object shadow;
            try {
                Constructor<?> ctor2 = uItemClass.getDeclaredConstructor(int.class, boolean.class);
                ctor2.setAccessible(true);
                shadow = ctor2.newInstance(shadowViewType, false);
            } catch (NoSuchMethodException e) {
                Constructor<?> ctor1 = uItemClass.getDeclaredConstructor(int.class);
                ctor1.setAccessible(true);
                shadow = ctor1.newInstance(shadowViewType);
            }
            if (shadow != null) {
                setShadowId(uItemClass, shadow, idFieldName);
            }
            return shadow;
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static Field findViewTypeField(Class<?> uItemClass, String idFieldName) {
        Field firstNonStaticInt = null;
        for (Class<?> c = uItemClass.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
                f.setAccessible(true);
                if (Modifier.isFinal(f.getModifiers())) {
                    return f;
                }
                if (firstNonStaticInt == null) {
                    firstNonStaticInt = f;
                }
            }
        }
        if (firstNonStaticInt != null) return firstNonStaticInt;

        for (Field f : uItemClass.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            if (idFieldName != null && idFieldName.equals(f.getName())) continue;
            f.setAccessible(true);
            if (Modifier.isFinal(f.getModifiers())) {
                return f;
            }
            if (firstNonStaticInt == null) {
                firstNonStaticInt = f;
            }
        }
        return firstNonStaticInt;
    }

    private static Integer findShadowViewType(ArrayList<Object> arrayList, Class<?> uItemClass, Field viewTypeField) {
        if (arrayList == null) return null;
        Map<Integer, Integer> counts = new HashMap<>();
        for (Object raw : arrayList) {
            if (raw == null || !uItemClass.isInstance(raw)) continue;
            UItem item = new UItem(raw);
            if (item.getText() == null && item.getSubtext() == null) {
                try {
                    int vt = viewTypeField.getInt(raw);
                    Integer prev = counts.get(vt);
                    counts.put(vt, prev == null ? 1 : prev + 1);
                } catch (Throwable ignored) {}
            }
        }
        Integer bestVt = null;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int vt = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && vt > 0 && (bestVt == null || bestVt <= 0))) {
                bestVt = vt;
                bestCount = count;
            }
        }
        return bestVt;
    }

    private static void setShadowId(Class<?> uItemClass, Object shadow, String idFieldName) {
        if (shadow == null || idFieldName == null) return;
        try {
            Field idField = ReflectHelper.findFieldIfExists(uItemClass, idFieldName);
            if (idField != null) {
                idField.setAccessible(true);
                idField.setInt(shadow, 8353848);
            }
        } catch (Throwable ignored) {}
    }

    private static ImageView findSettingCellIconView(Object cell) {
        if (cell == null) return null;
        try {
            Field iconField = ReflectHelper.findFieldIfExists(
                    cell.getClass(), Obfuscate.getFieldName("SettingsActivity$SettingCell", "iconView"));
            if (iconField != null) {
                iconField.setAccessible(true);
                Object v = iconField.get(cell);
                if (v instanceof ImageView) return (ImageView) v;
            }
        } catch (Throwable ignored) {}

        if (cell instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) cell;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof FrameLayout) {
                    FrameLayout fl = (FrameLayout) child;
                    for (int j = 0; j < fl.getChildCount(); j++) {
                        View inner = fl.getChildAt(j);
                        if (inner instanceof ImageView) {
                            return (ImageView) inner;
                        }
                    }
                }
            }
        }

        for (Field f : cell.getClass().getDeclaredFields()) {
            if (ImageView.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(cell);
                    if (v instanceof ImageView) return (ImageView) v;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    public void oldSettings(SettingsController settingsController){
        final Class<?> itemClass = ReflectHelper.findClassIfExists(Obfuscate.getClassName("org.telegram.ui.Adapters.DrawerLayoutAdapter$Item"), Utils.classLoader);

        if (itemClass != null) {
            HMethod.hookMethod(
                    ClassLoad.getClass(ClassNames.DRAWER_LAYOUT_ADAPTER),
                    Obfuscate.getMethodName("DrawerLayoutAdapter", "resetItems"),
                    new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) throws Throwable {

                            DrawerLayoutAdapter drawerLayoutAdapter = new DrawerLayoutAdapter(param.thisObject);

                            ArrayList<?> items = drawerLayoutAdapter.getItems();

                            if (itemConstructor == null) {
                                itemConstructor = itemClass.getDeclaredConstructor(ArgsResolver.resolveObject("item", new Class[]{int.class, CharSequence.class, int.class}));
                                itemConstructor.setAccessible(true);
                            }

                            Object newItem = itemConstructor.newInstance(
                                    8353847,
                                    formatEntryTitle("TeleVip Next \uD83D\uDC7B", LocaleController.isRTL()),
                                    SettingsIconResolver.getIconSettings());

                            if (items instanceof ArrayList<?>) {
                                ArrayList<Object> typedItems = (ArrayList<Object>) items;
                                typedItems.add(newItem);
                            }
                        }
                    }
            );

            BaseMethodHook onCreateHook = new BaseMethodHook() {
                @Override
                protected void afterMethod(final MethodHookParam param) {

                    Object Launch = param.thisObject;

                    Object drawerLayoutAdapter = ReflectHelper.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutAdapter"));
                    if (drawerLayoutAdapter != null) {
                        Object args = param.args[1];

                        int id = (int) ReflectHelper.callMethod(drawerLayoutAdapter, Obfuscate.getMethodName("DrawerLayoutAdapter", "getId"), args);
                        if (id == 8353847) {

                            Object drawerLayoutContainer = ReflectHelper.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutContainer"));
                            if (drawerLayoutContainer != null) {
                                String closeMethod = Obfuscate.getMethodName("DrawerLayoutContainer", "closeDrawer");
                                Class<?> dlcClass = drawerLayoutContainer.getClass();
                                if (!ClientManager.is(ClientManager.Client.ForkgramClassic)
                                        && ReflectHelper.findMethodExactIfExists(dlcClass, closeMethod) != null) {
                                    ReflectHelper.callMethod(drawerLayoutContainer, closeMethod);
                                } else if (ReflectHelper.findMethodExactIfExists(dlcClass, closeMethod, boolean.class) != null) {
                                    ReflectHelper.callMethod(drawerLayoutContainer, closeMethod, true);
                                } else if (ReflectHelper.findMethodExactIfExists(dlcClass, closeMethod) != null) {
                                    ReflectHelper.callMethod(drawerLayoutContainer, closeMethod);
                                }
                            }

                            settingsController.openView();
                        }

                    }
                }
            };

            if (ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY) != null) {

                Method onCreateMethod = null;
                for (Method method : ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY).getDeclaredMethods()) {
                    if (Arrays.equals(method.getParameterTypes(), ArgsResolver.resolveObject("onCreateMethod", new Class[]{android.view.View.class, int.class, float.class, float.class}))) {
                        onCreateMethod = method;
                        break;
                    }
                }

                if (onCreateMethod == null) {
                    Logger.w("Failed to hook onCreateMethod! Reason: No method found, " + Utils.issue);
                    return;
                }

                XposedCompat.hookMethod(onCreateMethod, onCreateHook);
            }
        }

    }
}

