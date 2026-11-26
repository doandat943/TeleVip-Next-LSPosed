package com.magiii.televipnext.virtuals.ui;

import android.widget.FrameLayout;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class LaunchActivity {

    Object launchActivity;
    public FrameLayout frameLayout;

    public LaunchActivity(Object obj){
       launchActivity = obj;
       frameLayout = (FrameLayout) ReflectHelper.getObjectField(obj, Obfuscate.getFieldName("LaunchActivity","frameLayout"));
    }

}
