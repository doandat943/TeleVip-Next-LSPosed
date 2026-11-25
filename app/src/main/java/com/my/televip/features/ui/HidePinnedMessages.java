package com.my.televip.features.ui;

import android.view.View;

import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.ui.ChatActivity;

import de.robv.android.xposed.XposedHelpers;

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
                    if (XposedHelpers.findMethodExactIfExists(chatActivityClass, createName) != null) {
                        HMethod.hookMethod(chatActivityClass, createName, hideHook);
                    }

                    String updateName = Obfuscate.getMethodName("ChatActivity", "updatePinnedMessageView");
                    if (XposedHelpers.findMethodExactIfExists(chatActivityClass, updateName, boolean.class, int.class) != null) {
                        HMethod.hookMethod(chatActivityClass, updateName, boolean.class, int.class, hideHook);
                    } else if (XposedHelpers.findMethodExactIfExists(chatActivityClass, updateName, int.class, boolean.class) != null) {
                        HMethod.hookMethod(chatActivityClass, updateName, int.class, boolean.class, hideHook);
                    }
                }
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

}
