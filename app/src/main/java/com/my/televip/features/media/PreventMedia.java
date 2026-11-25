package com.my.televip.features.media;

import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.logging.Logger;
import com.my.televip.virtuals.ui.SecretMediaViewer;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class PreventMedia {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> chatActivityClass = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
                if (chatActivityClass != null) {
                    String readMethod = Obfuscate.getMethodName("ChatActivity", "sendSecretMessageRead");
                    XposedBridge.hookAllMethods(chatActivityClass, readMethod, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable()
                                    && param.args.length == 2 && param.args[1] instanceof Boolean) {
                                param.setResult(null);
                            }
                        }
                    });
                    String deleteMethod = Obfuscate.getMethodName("ChatActivity", "sendSecretMediaDelete");
                    if (deleteMethod != null && !"sendSecretMediaDelete".equals(deleteMethod)) {
                        XposedBridge.hookAllMethods(chatActivityClass, deleteMethod, new BaseMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable()
                                        && param.args.length == 1) {
                                    param.setResult(null);
                                }
                            }
                        });
                    }
                }

                Class<?> secretViewerClass = ClassLoad.getClass(ClassNames.SECRET_MEDIA_VIEWER);
                if (secretViewerClass != null) {
                    SecretMediaViewer.openMedia();
                    String closeMethod = Obfuscate.getMethodName("SecretMediaViewer", "closePhoto");
                    XposedBridge.hookAllMethods(secretViewerClass, closeMethod, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable()
                                    && param.args.length == 2
                                    && param.args[0] instanceof Boolean && param.args[1] instanceof Boolean) {
                                Object thisObject = param.thisObject;
                                XposedHelpers.setObjectField(thisObject, Obfuscate.getFieldName("SecretMediaViewer", "onClose"), null);
                            }
                        }
                    });
                }

                SecretMediaSave.init();
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
