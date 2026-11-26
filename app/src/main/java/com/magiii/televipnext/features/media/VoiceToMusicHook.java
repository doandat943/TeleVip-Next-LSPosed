package com.magiii.televipnext.features.media;


import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.MessageObject;

import com.magiii.televipnext.xposed.ReflectHelper;

public class VoiceToMusicHook {

    public static boolean isEnable = false;
    private static final ThreadLocal<Boolean> bypassVoice = new ThreadLocal<>();

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> msgObjClass = ClassLoad.getClass(ClassNames.MESSAGE_OBJECT);
                if (msgObjClass == null) return;

                String isVoiceName = Obfuscate.getMethodName("MessageObject", "isVoice");
                if (ReflectHelper.findMethodExactIfExists(msgObjClass, isVoiceName) != null) {
                    HMethod.hookMethod(msgObjClass, isVoiceName, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.enableVoiceMessageSaving == null
                                    || !ConfigManager.enableVoiceMessageSaving.isEnable()) {
                                return;
                            }
                            if (!Boolean.TRUE.equals(bypassVoice.get())) {
                                param.setResult(false);
                            }
                        }
                    });
                }

                String isMusicName = Obfuscate.getMethodName("MessageObject", "isMusic");
                if (ReflectHelper.findMethodExactIfExists(msgObjClass, isMusicName) != null) {
                    HMethod.hookMethod(msgObjClass, isMusicName, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.enableVoiceMessageSaving == null
                                    || !ConfigManager.enableVoiceMessageSaving.isEnable()) {
                                return;
                            }
                            bypassVoice.set(Boolean.TRUE);
                            try {
                                MessageObject messageObject = new MessageObject(param.thisObject);
                                if (messageObject.isVoice()) {
                                    param.setResult(true);
                                }
                            } finally {
                                bypassVoice.remove();
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
