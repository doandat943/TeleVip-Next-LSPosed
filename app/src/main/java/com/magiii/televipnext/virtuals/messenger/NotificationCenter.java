package com.magiii.televipnext.virtuals.messenger;

import android.os.Handler;
import android.os.Looper;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.features.messages.ShowDeletedMessages;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.util.ArrayList;

import com.magiii.televipnext.xposed.ReflectHelper;

public class NotificationCenter {


    private static int messagesDeleted = -1;
    private static int tlSchemeParseException = -1;


    public static int getMessagesDeleted() {
        if (messagesDeleted == -1) {
            try {
                Class<?> cls = ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER, com.magiii.televipnext.utils.Utils.classLoader, false);
                if (cls != null) {
                    messagesDeleted = ReflectHelper.getStaticIntField(cls, Obfuscate.getFieldName("NotificationCenter", "messagesDeleted"));
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
                Class<?> cls = ClassLoad.getClass(ClassNames.NOTIFICATION_CENTER, com.magiii.televipnext.utils.Utils.classLoader, false);
                if (cls != null) {
                    tlSchemeParseException = ReflectHelper.getStaticIntField(cls, Obfuscate.getFieldName("NotificationCenter", "tlSchemeParseException"));
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
                if (getInst != null && ReflectHelper.findMethodExactIfExists(cls, getInst, int.class) != null) {
                    instance = ReflectHelper.callStaticMethod(cls, getInst, account);
                } else if (ReflectHelper.findMethodExactIfExists(cls, "getInstance", int.class) != null) {
                    instance = ReflectHelper.callStaticMethod(cls, "getInstance", account);
                }
                if (instance == null) return;

                String postName = Obfuscate.getMethodName("NotificationCenter", "postNotificationName");
                if (postName == null || ReflectHelper.findMethodExactIfExists(cls, postName, int.class, Object[].class) == null) {
                    postName = "postNotificationName";
                }
                Object[] payload = new Object[]{msgIds, channelId, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, 0};
                ShowDeletedMessages.allowLocalDeleteIds(msgIds, 3000L);
                ReflectHelper.callMethod(instance, postName, notifId, (Object) payload);
            } catch (Throwable t) {
                Logger.e(t);
            }
        });
    }
}
