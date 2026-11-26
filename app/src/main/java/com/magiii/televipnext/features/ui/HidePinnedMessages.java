package com.magiii.televipnext.features.ui;

import android.view.View;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.virtuals.ui.ChatActivity;

import com.magiii.televipnext.xposed.ReflectHelper;

public class HidePinnedMessages {
    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                Class<?> chatActivityClass = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
                if (chatActivityClass != null) {
                    BaseMethodHook hideHook = new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (ConfigManager.hidePinnedMessages.isEnable()) {
                                View button = new ChatActivity(param.thisObject).getPinnedMessageView();
                                if (button != null && button.getVisibility() != View.GONE) {
                                    button.setVisibility(View.GONE);
                                }
                            }
                        }
                    };

                    String createName = Obfuscate.getMethodName("ChatActivity", "createPinnedMessageView");
                    if (ReflectHelper.findMethodExactIfExists(chatActivityClass, createName) != null) {
                        HMethod.hookMethod(chatActivityClass, createName, hideHook);
                    }

                    String updateName = Obfuscate.getMethodName("ChatActivity", "updatePinnedMessageView");
                    if (ReflectHelper.findMethodExactIfExists(chatActivityClass, updateName, boolean.class, int.class) != null) {
                        HMethod.hookMethod(chatActivityClass, updateName, boolean.class, int.class, hideHook);
                    } else if (ReflectHelper.findMethodExactIfExists(chatActivityClass, updateName, int.class, boolean.class) != null) {
                        HMethod.hookMethod(chatActivityClass, updateName, int.class, boolean.class, hideHook);
                    }
                }
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

}
