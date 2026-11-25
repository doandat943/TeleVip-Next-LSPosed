package com.my.televip.virtuals.messenger;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.tgnet.TLRPC;

import de.robv.android.xposed.XposedHelpers;

public class MessageObject {

    private final Object messageObject;

    public MessageObject(Object obj){
        messageObject = obj;
    }

    public TLRPC.Message getMessageOwner(){
        return new TLRPC.Message(XposedHelpers.getObjectField(messageObject, Obfuscate.getFieldName("MessageObject","messageOwner")));
    }

    public long getDialogId() {
        String method = Obfuscate.getMethodName("MessageObject", "getDialogId");
        if (messageObject != null && XposedHelpers.findMethodExactIfExists(messageObject.getClass(), method) != null) {
            return (long) XposedHelpers.callMethod(messageObject, method);
        }
        TLRPC.Message owner = getMessageOwner();
        if (owner != null && owner.get_Message() != null) {
            try {
                return getDialogId(owner);
            } catch (Throwable ignored) {
                return XposedHelpers.getLongField(owner.get_Message(), Obfuscate.getFieldName("Message", "dialog_id"));
            }
        }
        return 0L;
    }

    public boolean isVoice() {
        return (boolean) XposedHelpers.callMethod(messageObject, Obfuscate.getMethodName("MessageObject", "isVoice"));
    }

    public static long getDialogId(TLRPC.Message message) {
        return (long) XposedHelpers.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "getDialogId"), message.get_Message());
    }

    public Object getMessageObject(){
        return messageObject;
    }

}
