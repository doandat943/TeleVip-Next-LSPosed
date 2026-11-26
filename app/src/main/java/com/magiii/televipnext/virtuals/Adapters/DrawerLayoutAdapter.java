package com.magiii.televipnext.virtuals.Adapters;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.magiii.televipnext.xposed.ReflectHelper;

public class DrawerLayoutAdapter {

    private final Object drawerLayout;

    public DrawerLayoutAdapter(Object obj){
        drawerLayout = obj;
    }

    public ArrayList<?> getItems(){
        return (ArrayList<?>) ReflectHelper.getObjectField(drawerLayout, Obfuscate.getFieldName("DrawerLayoutAdapter", "items"));
    }

}
