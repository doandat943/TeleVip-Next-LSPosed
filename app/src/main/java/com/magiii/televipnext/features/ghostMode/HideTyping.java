package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.utils.Utils;

public class HideTyping {

    public static boolean isTypingRequest(Object object) {
        Class<?> objectClass = object.getClass();
        Class<?> typingCls = ClassLoad.getClass(ClassNames.TL_MESSAGES_SET_TYPING, Utils.classLoader, false);
        Class<?> encTypingCls = ClassLoad.getClass(ClassNames.TL_MESSAGES_SET_ENCRYPTED_TYPING, Utils.classLoader, false);
        if (objectClass.equals(typingCls) || objectClass.equals(encTypingCls)) {
            return true;
        }
        if (!ClientManager.isTgnetObfuscated()) {
            String className = objectClass.getName();
            return className.contains("TL_messages_setTyping") ||
                    className.contains("TL_messages_setEncryptedTyping");
        }
        return false;
    }
}
