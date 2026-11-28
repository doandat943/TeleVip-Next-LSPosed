package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;

import com.magiii.televipnext.xposed.ReflectHelper;

public class UserConfig {

    Object userConfig;

    public UserConfig(Object obl){
        userConfig = obl;
    }

    public static int getSelectedAccount() {
        String selectedAccountField = Obfuscate.getFieldName("UserConfig", "selectedAccount");
        return ReflectHelper.getStaticIntField(ClassLoad.getClass(ClassNames.USER_CONFIG), selectedAccountField);
    }

    public long getClientUserId(){
        if (userConfig != null) {
            try {
                String mapped = Obfuscate.getMethodName("UserConfig", "getClientUserId");
                if (mapped != null && ReflectHelper.findMethodExactIfExists(userConfig.getClass(), mapped) != null) {
                    Object res = ReflectHelper.callMethod(userConfig, mapped);
                    if (res instanceof Long) return (Long) res;
                }
            } catch (Throwable ignored) {}
        }
        return ReflectHelper.getLongField(userConfig, Obfuscate.getFieldName("UserConfig" , "clientUserId"));
    }

    public TLRPC.User getCurrentUser(){
        return new TLRPC.User(ReflectHelper.callMethod(userConfig, Obfuscate.getMethodName("UserConfig" , "getCurrentUser")));
    }

    public static UserConfig getInstance(int num) {
        return new UserConfig(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.USER_CONFIG), Obfuscate.getMethodName("UserConfig", "getInstance"), num));
    }

    public static UserConfig getUserConfig() {
        return getInstance(getSelectedAccount());
    }

    public Object get_UserConfig() {
        return userConfig;
    }

}
