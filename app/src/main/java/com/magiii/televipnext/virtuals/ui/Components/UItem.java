package com.magiii.televipnext.virtuals.ui.Components;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import com.magiii.televipnext.xposed.ReflectHelper;

public class UItem {

    Object uItem;

    public UItem(Object uItem){
        this.uItem = uItem;
    }

    public int getID(){
        return ReflectHelper.getIntField(uItem, Obfuscate.getFieldName("UItem", "id"));
    }

    public String getText(){
        return Utils.getFieldAsString(ReflectHelper.getObjectField(uItem, Obfuscate.getFieldName("UItem", "text")));
    }

    public String getSubtext(){
        return Utils.getFieldAsString(ReflectHelper.getObjectField(uItem, Obfuscate.getFieldName("UItem", "subtext")));
    }

    public Object getUItem(){
        return uItem;
    }
}
