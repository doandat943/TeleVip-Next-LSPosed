package com.magiii.televipnext.features.media;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.virtuals.ui.SecretMediaViewer;

import com.magiii.televipnext.xposed.XposedCompat;
import com.magiii.televipnext.xposed.ReflectHelper;

public class PreventMedia {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> chatActivityClass = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
                if (chatActivityClass != null) {
                    String readMethod = Obfuscate.getMethodName("ChatActivity", "sendSecretMessageRead");
                    XposedCompat.hookAllMethods(chatActivityClass, readMethod, new BaseMethodHook() {
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
                        XposedCompat.hookAllMethods(chatActivityClass, deleteMethod, new BaseMethodHook() {
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
                    XposedCompat.hookAllMethods(secretViewerClass, closeMethod, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable()
                                    && param.args.length == 2
                                    && param.args[0] instanceof Boolean && param.args[1] instanceof Boolean) {
                                Object thisObject = param.thisObject;
                                ReflectHelper.setObjectField(thisObject, Obfuscate.getFieldName("SecretMediaViewer", "onClose"), null);
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
