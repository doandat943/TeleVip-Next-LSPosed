package com.magiii.televipnext.Configs;

import android.app.Activity;
import android.content.SharedPreferences;

import com.magiii.televipnext.application.ApplicationLoaderHook;
import com.magiii.televipnext.logging.Logger;

public class ConfigPreferences {

    private static SharedPreferences sharedPreferences;


    public static void init(){
        sharedPreferences = ApplicationLoaderHook.getApplicationContext().getSharedPreferences("TeleVip", Activity.MODE_PRIVATE);
    }

    public static boolean isInitialized() {
        return sharedPreferences != null;
    }

    public static boolean contains(String key) {
        return sharedPreferences != null && key != null && sharedPreferences.contains(key);
    }

    public static boolean getBoolean(String key) {
        if (sharedPreferences == null) return false;
        try {
            return sharedPreferences.getBoolean(key, false);
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(key).apply();
            return false;
        }
    }

    public static void putBoolean(String key, boolean b) {
        if (sharedPreferences == null) return;
        try {
            sharedPreferences.edit().putBoolean(key, b).apply();
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(key).apply();
        }
    }
    public static int getInt(String key) {
        if (sharedPreferences == null) return 0;
        try {
            return sharedPreferences.getInt(key, 0);
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(key).apply();
            return 0;
        }
    }

    public static void putInt(String key, int var) {
        if (sharedPreferences == null) return;
        try {
            sharedPreferences.edit().putInt(key, var).apply();
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(key).apply();
        }
    }

    public static String getString(String key) {
        if (sharedPreferences == null) return null;
        try {
            return sharedPreferences.getString(key, null);
        } catch (ClassCastException e) {

            sharedPreferences.edit().remove(key).apply();
            return null;
        }
    }

    public static void putString(String key, String v) {
        if (sharedPreferences == null) return;
        try {
            sharedPreferences.edit().putString(key, v).apply();
        } catch (ClassCastException e) {
            sharedPreferences.edit().remove(key).apply();
        }
    }

    public static void remove(String key){
        if (sharedPreferences == null) return;
        try {
            if (key == null) return;
            sharedPreferences.edit().remove(key).apply();
        } catch (Throwable t){
            Logger.e(t);
        }
    }

}
