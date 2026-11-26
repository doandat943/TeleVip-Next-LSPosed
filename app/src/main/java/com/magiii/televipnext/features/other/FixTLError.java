package com.magiii.televipnext.features.other;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.messenger.NotificationCenter;

public class FixTLError {

    public static boolean isEnable = false;

    public static void init(){
        try {
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.LAUNCH_ACTIVITY), Obfuscate.getMethodName("LaunchActivity", "didReceivedNotification"), ArgsResolver.merge("didReceivedNotification", new Class[]{int.class, int.class, Object[].class}, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            int id = (int) param.args[0];
                            if (id == NotificationCenter.getTlSchemeParseException() && ConfigManager.fixTLError.isEnable())
                                param.setResult(null);
                        }
                    }));
                }
            }
        } catch (Throwable t){
            Logger.e(t);
    }
    }

}
