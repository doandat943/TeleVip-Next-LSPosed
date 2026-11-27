package com.magiii.televipnext.virtuals.messenger;

import android.content.SharedPreferences;
import android.util.SparseArray;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.androidx.LongSparseArray;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;

import com.magiii.televipnext.xposed.ReflectHelper;

public class MessagesController {
    final Object messagesController;

    public MessagesController(Object instance)
    {
        this.messagesController = instance;
    }

    public void processNewDifferenceParams(int seq, int pts, int date, int pts_count) {
        if (messagesController == null) return;
        Class<?> cls = messagesController.getClass();
        String mName = Obfuscate.getMethodName("MessagesController", "processNewDifferenceParams");
        if (invokeDifferenceMethod(cls, mName, seq, pts, date, pts_count)) {
            return;
        }
        if (!"processNewDifferenceParams".equals(mName)) {
            invokeDifferenceMethod(cls, "processNewDifferenceParams", seq, pts, date, pts_count);
        }
    }

    private boolean invokeDifferenceMethod(Class<?> cls, String name, int seq, int pts, int date, int pts_count) {
        if (name == null) return false;
        if (ReflectHelper.findMethodExactIfExists(cls, name, int.class, int.class, int.class, int.class) != null) {
            ReflectHelper.callMethod(messagesController, name, seq, pts, date, pts_count);
            return true;
        }
        if (ReflectHelper.findMethodExactIfExists(cls, name, int.class, int.class, int.class) != null) {
            ReflectHelper.callMethod(messagesController, name, pts, date, pts_count);
            return true;
        }
        return false;
    }

    public void processNewDifferenceParams(int pts, int date, int pts_count) {
        //Nagram
        ReflectHelper.callMethod(messagesController, "processNewDifferenceParams", pts, date, pts_count);
    }

    public void removePromoDialog() {
        ReflectHelper.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "removePromoDialog"));
    }

    public static Object getInputChannel(TLRPC.InputPeer peer) {
        return ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInputChannel"), peer.getInputPeer());
    }

    public SparseArray<Object> getDialogMessagesByIds() {
        return (SparseArray<Object>) ReflectHelper.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessagesByIds"));
    }

    public LongSparseArray getDialogMessage() {
        if (messagesController == null) return null;
        Object val = ReflectHelper.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessage"));
        return val != null ? new LongSparseArray(val) : null;
    }

    public static SharedPreferences getGlobalMainSettings() {
        return (SharedPreferences) ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getGlobalMainSettings"));
    }

    public static Object getInputChannel(long id) {
        return ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInputChannel"), id);
    }

    /**
     * Looks up a cached TLRPC.Chat by positive chat/channel ID, supporting both Long and primitive long overloads.
     */
    public Object getChat(long chatId) {
        if (messagesController == null) return null;
        Class<?> cls = messagesController.getClass();
        String mapped = Obfuscate.getMethodName("MessagesController", "getChat");
        if (mapped != null && !"getChat".equals(mapped)) {
            if (ReflectHelper.findMethodExactIfExists(cls, mapped, Long.class) != null) {
                return ReflectHelper.callMethod(messagesController, mapped, Long.valueOf(chatId));
            }
            if (ReflectHelper.findMethodExactIfExists(cls, mapped, long.class) != null) {
                return ReflectHelper.callMethod(messagesController, mapped, chatId);
            }
        }
        if (ReflectHelper.findMethodExactIfExists(cls, "getChat", Long.class) != null) {
            return ReflectHelper.callMethod(messagesController, "getChat", Long.valueOf(chatId));
        }
        if (ReflectHelper.findMethodExactIfExists(cls, "getChat", long.class) != null) {
            return ReflectHelper.callMethod(messagesController, "getChat", chatId);
        }
        return null;
    }

    public MessagesStorage getMessagesStorage() {
        if (messagesController != null) {
            try {
                String mName = Obfuscate.getMethodName("MessagesController", "getMessagesStorage");
                if (mName != null && ReflectHelper.findMethodExactIfExists(messagesController.getClass(), mName) != null) {
                    Object ms = ReflectHelper.callMethod(messagesController, mName);
                    if (ms != null) return new MessagesStorage(ms);
                }
                if (!"getMessagesStorage".equals(mName)
                        && ReflectHelper.findMethodExactIfExists(messagesController.getClass(), "getMessagesStorage") != null) {
                    Object ms = ReflectHelper.callMethod(messagesController, "getMessagesStorage");
                    if (ms != null) return new MessagesStorage(ms);
                }
            } catch (Throwable ignored) {}
        }
        return MessagesStorage.getMessagesStorage();
    }

    public static MessagesController getInstance(int num){
        return new MessagesController(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInstance"), num));
    }

    public static MessagesController getMessagesController() {
        return getInstance(UserConfig.getSelectedAccount());
    }

    public Object getMessagesControllerObject() {
        return messagesController;
    }

}
