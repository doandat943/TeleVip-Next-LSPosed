package com.magiii.televipnext.language;

import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.utils.JsonAssetReader;
import com.magiii.televipnext.virtuals.messenger.LocaleController;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Translator {

    private static final Map<String, JSONObject> langMap = new HashMap<>();

    private static LocaleController localeController;

    public static void init() {
        try {
            loadAllLanguages();
            localeController = new LocaleController();
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private static void loadAllLanguages() {
        List<String> files = JsonAssetReader.listFiles("assets/lang/", ".json");

        for (String fileName : files) {
            String langCode = fileName.substring(0, fileName.lastIndexOf('.'));

            JSONObject json = JsonAssetReader.readObject("assets/lang/" + fileName);
            if (json != null) {
                langMap.put(langCode, json);
            }
        }
    }

    private static String getCurrentLangCode() {
        try {
            Locale locale = localeController != null ? localeController.getCurrentLocale() : null;
            if (locale == null) {
                locale = Locale.getDefault();
            }
            if (locale != null) {
                return locale.getLanguage().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {}
        return "en";
    }

    public static String getOrDefault(String key, String defaultValue) {
        if (key == null) return defaultValue;
        try {
            String lang = getCurrentLangCode();
            JSONObject langJson = langMap.get(lang);
            if (langJson != null && langJson.has(key)) {
                String val = langJson.optString(key, null);
                if (val != null) return val;
            }
            JSONObject enJson = langMap.get("en");
            if (enJson != null && enJson.has(key)) {
                String val = enJson.optString(key, null);
                if (val != null) return val;
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
        return defaultValue;
    }

    public static String get(String key) {
        return getOrDefault(key, key);
    }

    public static String get(String key, Object... args) {
        String text = getOrDefault(key, key);
        if (args != null && args.length > 0 && text != null) {
            try {
                return String.format(text, args);
            } catch (IllegalFormatException e) {
                Logger.e(e);
            }
        }
        return text;
    }

}
