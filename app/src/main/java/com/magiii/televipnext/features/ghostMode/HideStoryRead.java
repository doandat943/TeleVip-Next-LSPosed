package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.utils.Utils;

public class HideStoryRead {

    public static boolean isReadStoriesRequest(Object object) {
        Class<?> objectClass = object.getClass();
        Class<?> readCls = ClassLoad.getClass(ClassNames.TL_STORIES_READ_STORIES, Utils.classLoader, false);
        Class<?> incCls = ClassLoad.getClass(ClassNames.TL_STORIES_INCREMENT_STORY_VIEWS, Utils.classLoader, false);
        if (objectClass.equals(readCls) || objectClass.equals(incCls)) {
            return true;
        }
        if (!ClientManager.isTgnetObfuscated()) {
            String className = objectClass.getName();
            return className.contains("TL_stories_readStories") ||
                    className.contains("TL_stories_incrementStoryViews");
        }
        return false;
    }
}
