package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

public class HideTyping {

    public static boolean isTypingRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            String className = object.getClass().getName();
            return className.contains("TL_messages_setTyping") ||
                    className.contains("TL_messages_setEncryptedTyping");
        } else {
            Class<?> objectClass = object.getClass();
            return objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SET_TYPING))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SET_ENCRYPTED_TYPING)));
        }
    }
}
