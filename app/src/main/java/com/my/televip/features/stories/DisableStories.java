package com.my.televip.features.stories;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Clients.ClientManager;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import de.robv.android.xposed.XposedHelpers;

public class DisableStories {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                Class<?> mcClass = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
                if (mcClass != null) {
                    HMethod.hookMethod(mcClass, "MessagesController", new String[]{"storiesEnabled", "storyEntitiesAllowed"}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable()) param.setResult(false);
                        }
                    });

                    HMethod.hookMethod(mcClass, Obfuscate.getMethodName("MessagesController", "storyEntitiesAllowed2"), ArgsResolver.merge("storyEntitiesAllowed", new Class[]{ClassLoad.getClass(ClassNames.TLRPC_USER)}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable()) param.setResult(false);
                        }
                    }));
                }

                Class<?> scClass = ClassLoad.getClass(ClassNames.STORIES_CONTROLLER);
                if (scClass != null) {
                    BaseMethodHook falseHook = new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.disableStories.isEnable()) {
                                param.setResult(false);
                            }
                        }
                    };

                    if (ClientManager.is(ClientManager.Client.NagramX)) {
                        HMethod.hookMethod(scClass, "hasStories", long.class, falseHook);
                    } else {
                        String hasStories = Obfuscate.getMethodName("StoriesController", "hasStories");
                        if (XposedHelpers.findMethodExactIfExists(scClass, hasStories) != null) {
                            HMethod.hookMethod(scClass, hasStories, falseHook);
                        }

                        String hasSelfStories = Obfuscate.getMethodName("StoriesController", "hasSelfStories");
                        if (!"hasSelfStories".equals(hasSelfStories) && XposedHelpers.findMethodExactIfExists(scClass, hasSelfStories) != null) {
                            HMethod.hookMethod(scClass, hasSelfStories, falseHook);
                        }

                        String hasStoriesForDialog = Obfuscate.getMethodName("StoriesController", "hasStoriesForDialog");
                        String longMethod = !"hasStoriesForDialog".equals(hasStoriesForDialog) ? hasStoriesForDialog : hasStories;
                        if (XposedHelpers.findMethodExactIfExists(scClass, longMethod, long.class) != null) {
                            HMethod.hookMethod(scClass, longMethod, long.class, falseHook);
                        }
                    }
                }
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
