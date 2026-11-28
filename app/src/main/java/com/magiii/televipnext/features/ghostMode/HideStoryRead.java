package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;

public class HideStoryRead {

    public static boolean isReadStoriesRequest(Object object) {
        return ClassLoad.isTlInstance(object, ClassNames.TL_STORIES_READ_STORIES, "TL_stories_readStories")
                || ClassLoad.isTlInstance(object, ClassNames.TL_STORIES_INCREMENT_STORY_VIEWS, "TL_stories_incrementStoryViews");
    }
}
