package com.magiii.televipnext.virtuals.ui;

import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.ActionBar.ActionBarMenuItem;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ProfileActivity {

    Object profileActivity;

    public ProfileActivity(Object obj){
        profileActivity = obj;
    }

    public long getChatId(){
        return ReflectHelper.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "chatId"));
    }

    public long getUserId(){
        return ReflectHelper.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "userId"));
    }

    public Object[] getNameTextView(){
        Object val = ReflectHelper.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "nameTextView"));
        if (val == null) return null;
        if (val instanceof Object[]) return (Object[]) val;
        return new Object[]{val};
    }

    public Object[] getOnlineTextView(){
        Object val = ReflectHelper.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "onlineTextView"));
        if (val == null) return null;
        if (val instanceof Object[]) return (Object[]) val;
        return new Object[]{val};
    }

    public ActionBarMenuItem getOtherItem(){
        return new ActionBarMenuItem(ReflectHelper.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "otherItem")));
    }


    public BaseFragment getBaseFragment(){
        return new BaseFragment(profileActivity);
    }
}
