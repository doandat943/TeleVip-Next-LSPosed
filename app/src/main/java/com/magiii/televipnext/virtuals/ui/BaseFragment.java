package com.magiii.televipnext.virtuals.ui;

import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.UserConfig;

import com.magiii.televipnext.xposed.ReflectHelper;

public class BaseFragment {

    Object baseFragment;

    public BaseFragment(Object obj){
        baseFragment = obj;
    }

    public UserConfig getUserConfig(){
        return new UserConfig(ReflectHelper.callMethod(baseFragment, Obfuscate.getMethodName("BaseFragment", "getUserConfig")));
    }

}
