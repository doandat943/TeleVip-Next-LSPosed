package com.magiii.televipnext.settings;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Clients.Turrit;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.settings.hook.SettingsHook;
import com.magiii.televipnext.settings.ui.SettingsActivity;
import com.magiii.televipnext.utils.Utils;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SettingsManager {

    private static volatile SettingsController activeController;

    public static SettingsController getSettingsController() {
        return activeController;
    }

    public static void init(SettingsController settingsController) {
        activeController = settingsController;

        SettingsActivity.init(settingsController);

        Class<?> SettingsActivityClass = ReflectHelper.findClassIfExists(
                Obfuscate.getClassName("org.telegram.ui.SettingsActivity"),
                Utils.classLoader
        );

        Class<?> SettingsActivity$SettingCell$FactoryClass = ReflectHelper.findClassIfExists(
                Obfuscate.getClassName("org.telegram.ui.SettingsActivity$SettingCell$Factory"),
                Utils.classLoader
        );

        SettingsHook settings = new SettingsHook();
        if (SettingsActivityClass != null && SettingsActivity$SettingCell$FactoryClass != null) {
            settings.newSettings(SettingsActivityClass, SettingsActivity$SettingCell$FactoryClass, settingsController);
        } else {
            settings.oldSettings(settingsController);
        }
        if (ClientManager.is(ClientManager.Client.ForkgramClassic)) settings.oldSettings(settingsController);
        if (ClientManager.is(ClientManager.Client.Turrit)) Turrit.showGhostModeDialog(settingsController);
    }

}
