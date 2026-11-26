package com.magiii.televipnext.features.ui;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.MessagesController;

public class HideProxySponsor {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER),
                            Obfuscate.getMethodName("MessagesController", "checkPromoInfoInternal"),ArgsResolver.merge("checkPromoInfoInternal", new Class[]{boolean.class}, new BaseMethodHook() {
                                @Override
                                protected void afterMethod(MethodHookParam param) {
                                    if (ConfigManager.hideProxySponsor.isEnable()) {
                                        MessagesController messagesController = new MessagesController(param.thisObject);
                                        AndroidUtilities.runOnUIThread(messagesController::removePromoDialog);
                                        removePromoDialog();
                                    }
                                }
                            }));
                }
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static void removePromoDialog() {
        if (MessagesController.getGlobalMainSettings() == null) return;
        MessagesController.getGlobalMainSettings().edit().remove("proxy_dialog").remove("proxyDialogAddress").remove("nextPromoInfoCheckTime").apply();
    }

}
