package com.my.televip.virtuals.ui;

import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.virtuals.ActionBar.ActionBarMenuItem;

import de.robv.android.xposed.XposedHelpers;

public class ProfileActivity {

    Object profileActivity;

    public ProfileActivity(Object obj){
        profileActivity = obj;
    }

    public long getChatId(){
        return XposedHelpers.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "chatId"));
    }

    public long getUserId(){
        return XposedHelpers.getLongField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "userId"));
    }

    public Object[] getNameTextView(){
        Object val = XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "nameTextView"));
        if (val == null) return null;
        if (val instanceof Object[]) return (Object[]) val;
        return new Object[]{val};
    }

    public Object[] getOnlineTextView(){
        Object val = XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "onlineTextView"));
        if (val == null) return null;
        if (val instanceof Object[]) return (Object[]) val;
        return new Object[]{val};
    }

    public ActionBarMenuItem getOtherItem(){
        return new ActionBarMenuItem(XposedHelpers.getObjectField(profileActivity, Obfuscate.getFieldName("ProfileActivity", "otherItem")));
    }


    public BaseFragment getBaseFragment(){
        return new BaseFragment(profileActivity);
    }
}
