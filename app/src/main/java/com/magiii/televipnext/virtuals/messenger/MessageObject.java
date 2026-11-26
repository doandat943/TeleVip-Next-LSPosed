package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;

import com.magiii.televipnext.xposed.ReflectHelper;

public class MessageObject {

    private final Object messageObject;

    public MessageObject(Object obj){
        messageObject = obj;
    }

    public TLRPC.Message getMessageOwner(){
        return new TLRPC.Message(ReflectHelper.getObjectField(messageObject, Obfuscate.getFieldName("MessageObject","messageOwner")));
    }

    public long getDialogId() {
        String method = Obfuscate.getMethodName("MessageObject", "getDialogId");
        if (messageObject != null && ReflectHelper.findMethodExactIfExists(messageObject.getClass(), method) != null) {
            return (long) ReflectHelper.callMethod(messageObject, method);
        }
        TLRPC.Message owner = getMessageOwner();
        if (owner != null && owner.get_Message() != null) {
            try {
                return getDialogId(owner);
            } catch (Throwable ignored) {
                return ReflectHelper.getLongField(owner.get_Message(), Obfuscate.getFieldName("Message", "dialog_id"));
            }
        }
        return 0L;
    }

    public boolean isVoice() {
        return (boolean) ReflectHelper.callMethod(messageObject, Obfuscate.getMethodName("MessageObject", "isVoice"));
    }

    public static long getDialogId(TLRPC.Message message) {
        return (long) ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), Obfuscate.getMethodName("MessageObject", "getDialogId"), message.get_Message());
    }

    public Object getMessageObject(){
        return messageObject;
    }

}
