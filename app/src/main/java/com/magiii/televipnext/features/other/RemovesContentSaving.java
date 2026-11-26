package com.magiii.televipnext.features.other;


import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.logging.Logger;

public class RemovesContentSaving {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                if (ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "isChatNoForwards"), ArgsResolver.merge("isChatNoForwards", new Class[]{ClassLoad.getClass(ClassNames.TLRPC_CHAT)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.removesContentSaving.isEnable())
                                param.setResult(false);
                        }
                    }));
                }

                if (ClassLoad.getClass(ClassNames.CHAT_ACTIVITY) != null && !ClientManager.is(ClientManager.Client.NagramXF)) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_ACTIVITY), Obfuscate.getMethodName("ChatActivity", "hasSelectedNoforwardsMessage"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.removesContentSaving.isEnable())
                                param.setResult(false);
                        }
                    });
                }

                if (ClassLoad.getClass(ClassNames.MESSAGE_OBJECT) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "canForwardMessage"), new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.removesContentSaving.isEnable())
                                param.setResult(true);
                        }
                    });
                }

                ForwardAsCopyHelper.init();
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
