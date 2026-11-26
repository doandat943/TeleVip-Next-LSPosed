package com.magiii.televipnext.features.media;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

public class EnableSavingStories {

    public static boolean isEnable = false;

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;

                if (ClassLoad.getClass(ClassNames.STORY_ITEM_HOLDER) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.STORY_ITEM_HOLDER), Obfuscate.getMethodName("PeerStoriesView$StoryItemHolder", "allowScreenshots"),
                            new BaseMethodHook() {
                                @Override
                                protected void beforeMethod(MethodHookParam param) {
                                    if (ConfigManager.enableSavingStories.isEnable())
                                        param.setResult(true);
                                }
                            });
                }
            }
        } catch (Throwable t){
            Logger.e(t);
        }
    }
}
