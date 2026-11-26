package com.magiii.televipnext.Clients;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.virtuals.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Turrit {

    public static class ParameterResolver
    {
        static Map<String,Class<?>[]> objectList = new HashMap<>();

        public static void register(String name,  Class<?>[] classes){
            objectList.put(name, classes);
        }

        public static Class<?>[] resolve(String name) {
            return objectList.get(name);
        }

        public static boolean has(String name)
        {
            boolean has = false;
            Class<?>[] classes = objectList.get(name);
            if (classes != null){
                has = true;
            }
            return has;
        }
    }

    public static void loadParameter() {
        ParameterResolver.register("fillMessageMenu", new Class[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), ArrayList.class, ArrayList.class, ArrayList.class, CharSequence.class});
    }

    public static void showGhostModeDialog(SettingsController settingsController){
        AlertDialog ghostMode = new AlertDialog(settingsController.getContext());
        ghostMode.setTitle(Translator.get(Keys.GhostMode));
        ghostMode.setMessage(Translator.get(Keys.OpenGhostMode));
        ghostMode.setPositiveButton(Translator.get(Keys.Open), AlertDialog.click(settingsController::openView));
        ghostMode.setNegativeButton(Translator.get(Keys.Cancel), null);
        ghostMode.show();
    }

}
