package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import de.robv.android.xposed.XposedHelpers;

public class NotificationCenter {


    private static int messagesDeleted = -1;
    private static int tlSchemeParseException = -1;


    public static int getMessagesDeleted() {
        if (messagesDeleted == -1) {
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER, com.my.televip.utils.Utils.classLoader, false);
                if (cls != null) {
                    messagesDeleted = XposedHelpers.getStaticIntField(cls, Obfuscate.getFieldName("NotificationCenter", "messagesDeleted"));
                }
            } catch (Throwable ignored) {
                messagesDeleted = -2;
            }
        }
        return messagesDeleted;
    }

    public static int getTlSchemeParseException() {
        if (tlSchemeParseException == -1) {
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER, com.my.televip.utils.Utils.classLoader, false);
                if (cls != null) {
                    tlSchemeParseException = XposedHelpers.getStaticIntField(cls, Obfuscate.getFieldName("NotificationCenter", "tlSchemeParseException"));
                }
            } catch (Throwable ignored) {
                tlSchemeParseException = -2;
            }
        }
        return tlSchemeParseException;
    }
}
