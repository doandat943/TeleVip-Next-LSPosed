package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import com.magiii.televipnext.xposed.ReflectHelper;

public class GhostMode {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (isEnable || !ConfigManager.isGhostMode()) return;
            Class<?> cmClass = ClassLoad.getClass(ClassNames.CONNECTIONS_MANAGER);
            if (cmClass == null) return;

            isEnable = true;
            HMethod.hookMethod(cmClass, Obfuscate.getMethodName("ConnectionsManager", "sendRequestInternal"), ArgsResolver.merge("sendRequestInternal", new Class[]{ClassLoad.getClass(ClassNames.TL_OBJECT), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE), ClassLoad.getClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP), ClassLoad.getClass(ClassNames.QUICK_ACK_DELEGATE), ClassLoad.getClass(ClassNames.WRITE_TO_SOCKET_DELEGATE), int.class, int.class, int.class, boolean.class, int.class}, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length == 0) return;
                        Object object = param.args[0];
                        if (object == null || HideSeen.consumeAllowedReadRequest(object)) {
                            return;
                        }
                        if (!ConfigManager.isGhostMode()) return;

                        if (ClientManager.is(ClientManager.Client.Nagram)) {
                            HideSeen.saveReadHistory(object);
                        }
                        if (ConfigManager.hideOnline.isEnable()) {
                            if (isOnlineRequest(object))
                                ReflectHelper.setBooleanField(object, Obfuscate.getFieldName("TL_account$updateStatus", "offline"), true);
                        }

                        if (HideSeen.isHideSeenEnabled() && HideSeen.isReadMessageRequest(object)) {
                            HideSeen.sendFakeReadResponse(param.args[1]);
                            param.setResult(null);
                            return;
                        }

                        if (ConfigManager.hideTyping.isEnable() && HideTyping.isTypingRequest(object)) {
                            param.setResult(null);
                            return;
                        }

                        if (ConfigManager.hideStoryView.isEnable() && HideStoryRead.isReadStoriesRequest(object)) {
                            param.setResult(null);
                            return;
                        }

                        HideSeen.handleReadAfterSend(object);
                    } catch (Throwable e) {
                        Logger.e(e);
                    }
                }
            }));
        } catch (Throwable t) {
            isEnable = false;
            Logger.e(t);
        }
    }

    public static boolean isOnlineRequest(Object object) {
        Class<?> cls = object.getClass();
        Class<?> mapped = ClassLoad.getClass(ClassNames.TL_ACCOUNT_UPDATE_STATUS, Utils.classLoader, false);
        if (mapped != null && cls.equals(mapped)) {
            return true;
        }
        return !ClientManager.isTgnetObfuscated() && cls.getName().contains("TL_account$updateStatus");
    }

}
