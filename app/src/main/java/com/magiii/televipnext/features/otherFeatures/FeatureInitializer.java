package com.magiii.televipnext.features.otherFeatures;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FeatureInitializer {

    public static void init() {

        try {
            if (!FeatureStateManager.isChatEnabled()) {

                Class<?> actionBarClass = ReflectHelper.findClassIfExists(
                        Obfuscate.getClassName("org.telegram.ui.ActionBar.ActionBar"),
                        Utils.classLoader
                );

                HMethod.hookMethod(
                        actionBarClass,
                        Obfuscate.getMethodName("ActionBar", "setActionBarMenuOnItemClick"), ClassLoad.getClass(ClassNames.ACTION_BAR_MENU_ON_ITEM_CLICK),
                        new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {

                                Object clazz = param.args[0];

                                if (clazz == null) return;

                                String name = clazz.getClass().getName();

                                if ((name.contains("ChatActivity") || isInnerOfChatActivity(clazz.getClass()))
                                        && !FeatureStateManager.isChatEnabled()) {
                                    FeatureStateManager.saveChat(name);
                                    ChatHook.init(name);
                                }
                            }
                        });

            } else {
                ChatHook.init(FeatureStateManager.getChatClass());
            }

        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static boolean isInnerOfChatActivity(Class<?> listenerCls) {
        if (listenerCls == null) return false;
        Class<?> chatCls = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY, Utils.classLoader, false);
        if (chatCls == null) return false;
        for (Field f : listenerCls.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == chatCls) {
                return true;
            }
        }
        return false;
    }
}