package com.magiii.televipnext.language;

import com.magiii.televipnext.Configs.ConfigPreferences;
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

    public static final String LANG_AUTO = "auto";
    public static final String[] SUPPORTED_LANGS = {
            LANG_AUTO, "en",
            "ar", "az", "bg", "bn", "cs", "de", "es", "fa",
            "id", "vi", "zh"
    };

    private static final Map<String, JSONObject> langMap = new HashMap<>();

    private static LocaleController localeController;
    private static volatile String cachedOverrideLang = null;
    private static volatile boolean overrideLoaded = false;

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

    static String normalizeLangCode(String langCode) {
        if (langCode != null) {
            String lower = langCode.toLowerCase(Locale.ROOT);
            if ("in".equals(lower)) {
                lower = "id";
            } else if ("iw".equals(lower)) {
                lower = "he";
            } else if ("tl".equals(lower)) {
                lower = "fil";
            }
            for (String supported : SUPPORTED_LANGS) {
                if (supported.equals(lower)) {
                    return supported;
                }
            }
        }
        return LANG_AUTO;
    }

    public static String getSelectedLanguage() {
        if (!overrideLoaded && ConfigPreferences.isInitialized()) {
            cachedOverrideLang = normalizeLangCode(ConfigPreferences.getString(Keys.Language));
            overrideLoaded = true;
        }
        return cachedOverrideLang != null ? cachedOverrideLang : LANG_AUTO;
    }

    public static void setSelectedLanguage(String langCode) {
        String normalized = normalizeLangCode(langCode);
        cachedOverrideLang = normalized;
        overrideLoaded = true;
        ConfigPreferences.putString(Keys.Language, normalized);
    }

    public static String getLanguageDisplayName(String langCode) {
        String normalized = normalizeLangCode(langCode);
        switch (normalized) {
            case "en":
                return "English";
            case "ar":
                return "\u0627\u0644\u0639\u0631\u0628\u064a\u0629";
            case "az":
                return "Az\u0259rbaycan";
            case "bg":
                return "\u0411\u044a\u043b\u0433\u0430\u0440\u0441\u043a\u0438";
            case "bn":
                return "\u09ac\u09be\u0982\u09b2\u09be";
            case "cs":
                return "\u010ce\u0161tina";
            case "de":
                return "Deutsch";
            case "es":
                return "Espa\u00f1ol";
            case "fa":
                return "\u0641\u0627\u0631\u0633\u06cc";
            case "id":
                return "Bahasa Indonesia";
            case "vi":
                return "Ti\u1ebfng Vi\u1ec7t";
            case "zh":
                return "\u4e2d\u6587";
            default:
                return get(Keys.LanguageAuto);
        }
    }

    public static String getLanguagePickerLabel(String langCode) {
        String normalized = normalizeLangCode(langCode);
        if ("fa".equals(normalized)) {
            return isRtl()
                    ? "\u200F\u0641\u0627\u0631\u0633\u06cc \u2066(Persian)\u2069"
                    : "\u200E\u2067\u0641\u0627\u0631\u0633\u06cc\u2069 (Persian)";
        }
        return getLanguageDisplayName(normalized);
    }

    static boolean isRtlLang(String lang) {
        return "fa".equals(lang) || "ar".equals(lang) || "he".equals(lang) || "ur".equals(lang);
    }

    public static boolean isRtl() {
        return isRtlLang(getCurrentLangCode());
    }

    private static String getCurrentLangCode() {
        String selected = getSelectedLanguage();
        if (!LANG_AUTO.equals(selected)) {
            return selected;
        }
        try {
            Locale locale = localeController != null ? localeController.getCurrentLocale() : null;
            if (locale == null) {
                locale = Locale.getDefault();
            }
            if (locale != null) {
                String language = normalizeLangCode(locale.getLanguage());
                return LANG_AUTO.equals(language) ? "en" : language;
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
