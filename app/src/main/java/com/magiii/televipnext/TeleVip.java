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
        runSafe("ObfuscationManager.initRuntimeResolver", ObfuscationManager::initRuntimeResolver);
        runSafe("ResolverRegistry.loadParameter", () -> resolverRegistry.loadParameter());
        runSafe("Translator.init", Translator::init);
        runSafe("AndroidUtilities.init", AndroidUtilities::init);

        SettingsController settingsController = new SettingsController();

        runSafe("ConfigManager.loadAndRead", ConfigManager::loadAndRead);
        runSafe("SettingsManager.init", () -> SettingsManager.init(settingsController));
    }

    private static void runSafe(String name, Runnable task) {
        try {
            task.run();
        } catch (Throwable e) {
            Logger.w("TeleVip startup step failed: " + name);
            Logger.e(e);
        }
    }

}
