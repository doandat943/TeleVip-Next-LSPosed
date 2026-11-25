package com.my.televip.settings.hook;


import android.view.View;
import android.widget.ImageView;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Drawable.GhostDrawable;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.Adapters.DrawerLayoutAdapter;
import com.my.televip.virtuals.SettingsIconResolver;
import com.my.televip.virtuals.ui.Components.UItem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class SettingsHook {

    private Constructor<?> itemConstructor;

    public void newSettings(Class<?> SettingsActivityClass, Class<?> SettingsActivity$SettingCell$FactoryClass, SettingsController settingsController){
        try {

            GhostDrawable ghostDrawable = new GhostDrawable();

            Class<?> settingCellClass = ClassLoad.getClass(ClassNames.SETTINGS_ACTIVITY_SETTING_CELL, Utils.classLoader, false);
            if (settingCellClass != null) {
                HMethod.hookMethod(settingCellClass, Obfuscate.getMethodName("SettingsActivity$SettingCell", "set"), ArgsResolver.merge("set", new Class[]{int.class, int.class, int.class, CharSequence.class, CharSequence.class, CharSequence.class}, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        int id = (int) param.args[2];
                        if (id == 8353847) {
                            ImageView iconView = (ImageView) XposedHelpers.getObjectField(param.thisObject, Obfuscate.getFieldName("SettingsActivity$SettingCell", "iconView"));
                            if (iconView != null) {
                                iconView.setImageDrawable(ghostDrawable);
                            }
                        }
                    }
                }));
            } else {
                String bindName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "bindView");
                if (bindName != null) {
                    XposedBridge.hookAllMethods(SettingsActivity$SettingCell$FactoryClass, bindName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (param.args.length >= 2 && param.args[0] instanceof View && param.args[1] != null) {
                                UItem uItem = new UItem(param.args[1]);
                                if (uItem.getID() == 8353847) {
                                    View cell = (View) param.args[0];
                                    for (java.lang.reflect.Field f : cell.getClass().getDeclaredFields()) {
                                        if (ImageView.class.isAssignableFrom(f.getType())) {
                                            f.setAccessible(true);
                                            try {
                                                ImageView iv = (ImageView) f.get(cell);
                                                if (iv != null) {
                                                    iv.setImageDrawable(ghostDrawable);
                                                    break;
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                    }
                                }
                            }
                        }
                    });
                }
            }

            final Class<?> UItemClass = ClassLoad.getClass(ClassNames.UITEM);

            final String ofName = Obfuscate.getMethodName("SettingsActivity$SettingCell$Factory", "of");
            XposedBridge.hookAllMethods(SettingsActivityClass, Obfuscate.getMethodName("SettingsActivity", "fillItems"),
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
                                    Method m6 = XposedHelpers.findMethodExactIfExists(SettingsActivity$SettingCell$FactoryClass, ofName,
                                            int.class, int.class, int.class, int.class, CharSequence.class, CharSequence.class);
                                    if (m6 != null) {
                                        uItem = m6.invoke(null, 8353847, color1, color2, 8353847,
                                                "TeleVip Next \uD83D\uDC7B", Translator.get(Keys.ByMustafa));
                                    } else {
                                        for (Method fm : SettingsActivity$SettingCell$FactoryClass.getDeclaredMethods()) {
                                            Class<?>[] pt = fm.getParameterTypes();
                                            if (fm.getName().equals(ofName) && pt.length == 7
                                                    && pt[0] == int.class && pt[1] == int.class
                                                    && pt[2] == int.class && pt[3] == int.class
                                                    && pt[4] == CharSequence.class && pt[5] == CharSequence.class) {
                                                fm.setAccessible(true);
                                                Object arg6 = pt[6] == boolean.class ? Boolean.FALSE
                                                        : (pt[6] == int.class ? Integer.valueOf(0) : null);
                                                uItem = fm.invoke(null, 8353847, color1, color2, 8353847,
                                                        "TeleVip Next \uD83D\uDC7B", Translator.get(Keys.ByMustafa), arg6);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    Logger.e(t);
                                }
                                if (uItem == null) return;

                                for (int i = 0; i < arrayList.size(); i++) {
                                    UItem item = new UItem(arrayList.get(i));

                                    if (item.getText() != null && item.getSubtext() != null) {
                                        arrayList.add(i, uItem);
                                        break;
                                    }
                                }
                            }
                        }
                    });

            String onClickName = Obfuscate.getMethodName("SettingsActivity", "onClick");
            XposedBridge.hookAllMethods(
                    SettingsActivityClass,
                    onClickName, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(final MethodHookParam param) {
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

    public void oldSettings(SettingsController settingsController){
        final Class<?> itemClass = XposedHelpers.findClassIfExists(Obfuscate.getClassName("org.telegram.ui.Adapters.DrawerLayoutAdapter$Item"), Utils.classLoader);

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

                            Object newItem = itemConstructor.newInstance(8353847, "TeleVip Next \uD83D\uDC7B", SettingsIconResolver.getIconSettings());

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

                    Object drawerLayoutAdapter = XposedHelpers.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutAdapter"));
                    if (drawerLayoutAdapter != null) {
                        Object args = param.args[1];

                        int id = (int) XposedHelpers.callMethod(drawerLayoutAdapter, Obfuscate.getMethodName("DrawerLayoutAdapter", "getId"), args);
                        if (id == 8353847) {

                            Object drawerLayoutContainer = XposedHelpers.getObjectField(Launch, Obfuscate.getFieldName("LaunchActivity", "drawerLayoutContainer"));
                            if (drawerLayoutContainer != null) {
                                if (!ClientManager.is(ClientManager.Client.ForkgramClassic)) {
                                    XposedHelpers.callMethod(drawerLayoutContainer, Obfuscate.getMethodName("DrawerLayoutContainer", "closeDrawer"));
                                } else {
                                    XposedHelpers.callMethod(drawerLayoutContainer, Obfuscate.getMethodName("DrawerLayoutContainer", "closeDrawer"), true);
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

                XposedBridge.hookMethod(onCreateMethod, onCreateHook);
            }
        }

    }
}

