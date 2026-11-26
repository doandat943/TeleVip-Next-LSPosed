package com.magiii.televipnext.virtuals.ActionBar;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ActionBarMenuItem {

    Object actionBarMenuItem;

    public ActionBarMenuItem(Object obj){
        actionBarMenuItem = obj;
    }

    public void lazilyAddSubItem(int id, int resId, String name){
        ReflectHelper.callMethod(actionBarMenuItem, Obfuscate.getMethodName("ActionBarMenuItem", "lazilyAddSubItem"), id, resId, name);
    }
    public void addSubItem(int id, int resId, String name){
        ReflectHelper.callMethod(actionBarMenuItem, Obfuscate.getMethodName("ActionBarMenuItem", "addSubItem"), id, resId, name);
    }

    public Object getActionBarMenuItem(){
        return  actionBarMenuItem;
    }
}
