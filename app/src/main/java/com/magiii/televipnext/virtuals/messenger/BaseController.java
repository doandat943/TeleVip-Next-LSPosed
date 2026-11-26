package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class BaseController {

    Object baseController;

    public BaseController(Object obj){baseController = obj;}

    public UserConfig getUserConfig(){
        return new UserConfig(ReflectHelper.callMethod(baseController, Obfuscate.getMethodName("BaseController", "getUserConfig")));
    }

}
