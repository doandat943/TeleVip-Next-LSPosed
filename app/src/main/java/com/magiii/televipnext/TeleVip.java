package com.magiii.televipnext;

import static com.magiii.televipnext.obfuscate.ArgsResolver.resolverRegistry;

import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ObfuscationManager;
import com.magiii.televipnext.settings.SettingsManager;
import com.magiii.televipnext.settings.controller.SettingsController;

public class TeleVip {
    
    public static void startHook() {
        try {
            ObfuscationManager.initRuntimeResolver();
            resolverRegistry.loadParameter();
            Translator.init();
            AndroidUtilities.init();

            SettingsController settingsController = new SettingsController();

            ConfigManager.loadAndRead();
            SettingsManager.init(settingsController);

        } catch (Throwable e){
            Logger.e(e);
        }

    }

}
