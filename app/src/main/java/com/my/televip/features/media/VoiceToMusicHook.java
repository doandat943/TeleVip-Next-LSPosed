package com.my.televip.features.media;


import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.messenger.MessageObject;

import de.robv.android.xposed.XposedHelpers;

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
                if (XposedHelpers.findMethodExactIfExists(msgObjClass, isVoiceName) != null) {
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
                if (XposedHelpers.findMethodExactIfExists(msgObjClass, isMusicName) != null) {
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
