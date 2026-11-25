package com.my.televip.virtuals.messenger;

import android.os.Handler;
import android.os.Looper;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.features.messages.ShowDeletedMessages;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;

import java.util.ArrayList;

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

    /**
     * Posts Telegram's {@code messagesDeleted} notification on the main thread so open chats drop local messages.
     */
    public static void postMessagesDeleted(int account, ArrayList<Integer> msgIds, long channelId) {
        if (msgIds == null || msgIds.isEmpty()) return;
        int notifId = getMessagesDeleted();
        if (notifId < 0) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER, Utils.classLoader, false);
                if (cls == null) return;
                String getInst = Obfuscate.getMethodName("NotificationCenter", "getInstance");
                Object instance = null;
                if (getInst != null && XposedHelpers.findMethodExactIfExists(cls, getInst, int.class) != null) {
                    instance = XposedHelpers.callStaticMethod(cls, getInst, account);
                } else if (XposedHelpers.findMethodExactIfExists(cls, "getInstance", int.class) != null) {
                    instance = XposedHelpers.callStaticMethod(cls, "getInstance", account);
                }
                if (instance == null) return;

                String postName = Obfuscate.getMethodName("NotificationCenter", "postNotificationName");
                if (postName == null || XposedHelpers.findMethodExactIfExists(cls, postName, int.class, Object[].class) == null) {
                    postName = "postNotificationName";
                }
                Object[] payload = new Object[]{msgIds, channelId, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, 0};
                ShowDeletedMessages.allowLocalDeleteFor(1500L);
                XposedHelpers.callMethod(instance, postName, notifId, (Object) payload);
            } catch (Throwable t) {
                Logger.e(t);
            }
        });
    }
}
