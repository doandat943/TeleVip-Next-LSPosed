package com.magiii.televipnext.virtuals.ui;

import android.view.View;

import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.ActionBar.ActionBarMenuItem;
import com.magiii.televipnext.virtuals.messenger.MessageObject;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ChatActivity {

    final Object chatActivity;

    public ChatActivity(Object obj){
        chatActivity = obj;
    }

    public MessageObject getSelectedObject(){
        return new MessageObject(ReflectHelper.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity","selectedObject")));
    }

    public ActionBarMenuItem getHeaderItem(){
        return new ActionBarMenuItem(ReflectHelper.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity", "headerItem")));
    }
    public View getPinnedMessageView(){
        return (View) ReflectHelper.getObjectField(chatActivity, Obfuscate.getFieldName("ChatActivity", "pinnedMessageView"));
    }

    public void scrollToMessageId(int id, int fromMessageId, boolean select, int loadIndex, boolean forceScroll, int forcePinnedMessageId){
        ReflectHelper.callMethod(chatActivity, Obfuscate.getMethodName("ChatActivity", "scrollToMessageId"), id, fromMessageId, select, loadIndex, forceScroll, forcePinnedMessageId);
    }

}
