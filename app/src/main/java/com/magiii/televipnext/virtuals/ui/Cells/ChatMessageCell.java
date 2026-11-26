package com.magiii.televipnext.virtuals.ui.Cells;

import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.MessageObject;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ChatMessageCell {
    Object chatMessageCell;

    public ChatMessageCell(Object cell){ chatMessageCell = cell; }

    public MessageObject getMessageObject() {
        return new MessageObject(ReflectHelper.callMethod(chatMessageCell, Obfuscate.getMethodName("ChatMessageCell", "getMessageObject")));
    }


    public int getTimeTextWidth()
    {
        return ReflectHelper.getIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeTextWidth"));
    }

    public int getTimeWidth()
    {
        return ReflectHelper.getIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeWidth"));
    }

    public void setTimeTextWidth(int width)
    {
        ReflectHelper.setIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeTextWidth"), width);
    }

    public void setTimeWidth(int width)
    {
        ReflectHelper.setIntField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "timeWidth"), width);
    }

    public CharSequence getCurrentTimeString()
    {
        return (CharSequence) ReflectHelper.getObjectField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "currentTimeString"));
    }

    public void setCurrentTimeString(CharSequence currentTimeString)
    {
        ReflectHelper.setObjectField(this.chatMessageCell, Obfuscate.getFieldName("ChatMessageCell", "currentTimeString"), currentTimeString);
    }

    public Object getChatMessageCell(){
        return chatMessageCell;
    }

}
