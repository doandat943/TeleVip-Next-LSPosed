package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

public class HideStoryRead {

    public static boolean isReadStoriesRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            String className = object.getClass().getName();
            return className.contains("TL_stories_readStories") ||
                    className.contains("TL_stories_incrementStoryViews");
        } else {
            Class<?> objectClass = object.getClass();
            return objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_STORIES_READ_STORIES))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_STORIES_INCREMENT_STORY_VIEWS)));
        }
    }
}
