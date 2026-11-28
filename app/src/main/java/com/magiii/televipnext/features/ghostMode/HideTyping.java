package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;

public class HideTyping {

    public static boolean isTypingRequest(Object object) {
        return ClassLoad.isTlInstance(object, ClassNames.TL_MESSAGES_SET_TYPING, "TL_messages_setTyping")
                || ClassLoad.isTlInstance(object, ClassNames.TL_MESSAGES_SET_ENCRYPTED_TYPING, "TL_messages_setEncryptedTyping");
    }
}
