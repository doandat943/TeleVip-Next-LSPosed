package com.magiii.televipnext.features.stories;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

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

                    String hasStories = Obfuscate.getMethodName("StoriesController", "hasStories");
                    if (ReflectHelper.findMethodExactIfExists(scClass, hasStories) != null) {
                        HMethod.hookMethod(scClass, hasStories, falseHook);
                    }

                    String hasSelfStories = Obfuscate.getMethodName("StoriesController", "hasSelfStories");
                    if (!"hasSelfStories".equals(hasSelfStories) && ReflectHelper.findMethodExactIfExists(scClass, hasSelfStories) != null) {
                        HMethod.hookMethod(scClass, hasSelfStories, falseHook);
                    }

                    String hasStoriesForDialog = Obfuscate.getMethodName("StoriesController", "hasStoriesForDialog");
                    String longMethod = !"hasStoriesForDialog".equals(hasStoriesForDialog) ? hasStoriesForDialog : hasStories;
                    if (ReflectHelper.findMethodExactIfExists(scClass, longMethod, long.class) != null) {
                        HMethod.hookMethod(scClass, longMethod, long.class, falseHook);
                    }
                }
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
