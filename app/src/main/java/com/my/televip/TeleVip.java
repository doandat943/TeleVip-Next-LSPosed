package com.my.televip;

import static com.my.televip.obfuscate.ArgsResolver.resolverRegistry;

import com.my.televip.Configs.ConfigManager;
import com.my.televip.application.AndroidUtilities;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ObfuscationManager;
import com.my.televip.settings.SettingsManager;
import com.my.televip.settings.controller.SettingsController;

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
