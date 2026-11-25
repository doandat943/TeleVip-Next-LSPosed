package com.my.televip.virtuals.messenger;

import android.content.SharedPreferences;
import android.util.SparseArray;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.androidx.LongSparseArray;
import com.my.televip.virtuals.tgnet.TLRPC;

import de.robv.android.xposed.XposedHelpers;

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
        if (XposedHelpers.findMethodExactIfExists(cls, name, int.class, int.class, int.class, int.class) != null) {
            XposedHelpers.callMethod(messagesController, name, seq, pts, date, pts_count);
            return true;
        }
        if (XposedHelpers.findMethodExactIfExists(cls, name, int.class, int.class, int.class) != null) {
            XposedHelpers.callMethod(messagesController, name, pts, date, pts_count);
            return true;
        }
        return false;
    }

    public void processNewDifferenceParams(int pts, int date, int pts_count) {
        //Nagram
        XposedHelpers.callMethod(messagesController, "processNewDifferenceParams", pts, date, pts_count);
    }

    public void removePromoDialog() {
        XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "removePromoDialog"));
    }

    public static Object getInputChannel(TLRPC.InputPeer peer) {
        return XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInputChannel"), peer.getInputPeer());
    }

    public SparseArray<Object> getDialogMessagesByIds() {
        return (SparseArray<Object>) XposedHelpers.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessagesByIds"));
    }

    public LongSparseArray getDialogMessage() {
        return  new LongSparseArray(XposedHelpers.getObjectField(messagesController, Obfuscate.getFieldName("MessagesController", "dialogMessage")));
    }

    public static SharedPreferences getGlobalMainSettings() {
        return (SharedPreferences) XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getGlobalMainSettings"));
    }

    public static Object getInputChannel(long id) {
        return XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInputChannel"), id);
    }

    /**
     * Looks up a cached TLRPC.Chat by positive chat/channel ID, supporting both Long and primitive long overloads.
     */
    public Object getChat(long chatId) {
        if (messagesController == null) return null;
        Class<?> cls = messagesController.getClass();
        String mapped = Obfuscate.getMethodName("MessagesController", "getChat");
        if (mapped != null && !"getChat".equals(mapped)) {
            if (XposedHelpers.findMethodExactIfExists(cls, mapped, Long.class) != null) {
                return XposedHelpers.callMethod(messagesController, mapped, Long.valueOf(chatId));
            }
            if (XposedHelpers.findMethodExactIfExists(cls, mapped, long.class) != null) {
                return XposedHelpers.callMethod(messagesController, mapped, chatId);
            }
        }
        if (XposedHelpers.findMethodExactIfExists(cls, "getChat", Long.class) != null) {
            return XposedHelpers.callMethod(messagesController, "getChat", Long.valueOf(chatId));
        }
        if (XposedHelpers.findMethodExactIfExists(cls, "getChat", long.class) != null) {
            return XposedHelpers.callMethod(messagesController, "getChat", chatId);
        }
        return null;
    }

    public MessagesStorage getMessagesStorage() {
        return new MessagesStorage(XposedHelpers.callMethod(messagesController, Obfuscate.getMethodName("MessagesController", "getMessagesStorage")));
    }

    public static MessagesController getInstance(int num){
        return new MessagesController(XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER), Obfuscate.getMethodName("MessagesController", "getInstance"), num));
    }

    public static MessagesController getMessagesController() {
        return getInstance(UserConfig.getSelectedAccount());
    }

    public Object getMessagesControllerObject() {
        return messagesController;
    }

}
