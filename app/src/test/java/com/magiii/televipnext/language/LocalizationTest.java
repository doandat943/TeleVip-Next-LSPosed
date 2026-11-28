package com.magiii.televipnext.language;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalizationTest {

    private static final Pattern FORMAT_PLACEHOLDER =
            Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdif]");

    private static File resolveLangDir() {
        File direct = new File("src/main/assets/lang");
        if (direct.isDirectory()) return direct;
        File fromRoot = new File("app/src/main/assets/lang");
        if (fromRoot.isDirectory()) return fromRoot;
        throw new AssertionError("Could not locate assets/lang directory");
    }

    private static JsonObject readJson(File file) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<String> readKeysInFileOrder(File file) throws Exception {
        List<String> keys = new ArrayList<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                keys.add(reader.nextName());
                reader.skipValue();
            }
            reader.endObject();
        }
        return keys;
    }

    private static List<String> extractPlaceholders(String value) {
        List<String> list = new ArrayList<>();
        Matcher m = FORMAT_PLACEHOLDER.matcher(value);
        while (m.find()) {
            list.add(m.group());
        }
        Collections.sort(list);
        return list;
    }

    @Test
    public void allSupportedLanguagesMatchEnglishKeysAndPlaceholders() throws Exception {
        File langDir = resolveLangDir();
        JsonObject enJson = readJson(new File(langDir, "en.json"));
        Set<String> enKeys = enJson.keySet();
        assertFalse("en.json must not be empty", enKeys.isEmpty());

        Set<String> supportedCodes = new HashSet<>();
        for (String code : Translator.SUPPORTED_LANGS) {
            if (!Translator.LANG_AUTO.equals(code)) {
                supportedCodes.add(code);
                File f = new File(langDir, code + ".json");
                assertTrue("Missing language asset: " + f.getName(), f.isFile());
            }
        }

        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertTrue("Expected language JSON files", files != null && files.length > 0);
        Arrays.sort(files);

        for (File file : files) {
            String code = file.getName().substring(0, file.getName().length() - ".json".length());
            assertTrue("Asset file not listed in SUPPORTED_LANGS: " + file.getName(),
                    supportedCodes.contains(code));

            List<String> actualOrder = readKeysInFileOrder(file);
            List<String> sortedOrder = new ArrayList<>(actualOrder);
            Collections.sort(sortedOrder);
            assertEquals(file.getName() + " keys must be sorted alphabetically", sortedOrder, actualOrder);

            JsonObject obj = readJson(file);
            Set<String> keys = obj.keySet();

            Set<String> missing = new HashSet<>(enKeys);
            missing.removeAll(keys);
            assertTrue(file.getName() + " missing keys: " + missing, missing.isEmpty());

            Set<String> extra = new HashSet<>(keys);
            extra.removeAll(enKeys);
            assertTrue(file.getName() + " extra keys: " + extra, extra.isEmpty());

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = entry.getKey();
                assertTrue(file.getName() + " key '" + key + "' must be a string primitive",
                        entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString());
                String val = entry.getValue().getAsString();
                assertFalse(file.getName() + " key '" + key + "' must not be blank", val.trim().isEmpty());

                String enVal = enJson.get(key).getAsString();
                if (!enVal.startsWith(" ")) {
                    assertFalse(file.getName() + " key '" + key + "' must not start with a space",
                            val.startsWith(" "));
                }
                if ("PreventMedia".equals(key) || "JoinTeleVip".equals(key)) {
                    assertFalse(file.getName() + " key '" + key + "' must not end with a period",
                            val.endsWith("."));
                }

                assertEquals(file.getName() + " format placeholder mismatch for '" + key + "'",
                        extractPlaceholders(enVal),
                        extractPlaceholders(val));
            }
        }
    }

    @Test
    public void normalizeLangCodeDisplayNamesAndRtlSupportAllLanguages() {
        assertTrue(Translator.SUPPORTED_LANGS.length >= 7);
        Set<String> supportedSet = new HashSet<>();
        for (String code : Translator.SUPPORTED_LANGS) {
            if (Translator.LANG_AUTO.equals(code)) continue;
            supportedSet.add(code);
            String displayName = Translator.getLanguageDisplayName(code);
            assertFalse("Blank display name for " + code, displayName == null || displayName.trim().isEmpty());
            assertFalse("Fallback to LanguageAuto key for " + code, Keys.LanguageAuto.equals(displayName));
        }

        if (supportedSet.contains("id")) {
            assertEquals("id", Translator.normalizeLangCode("in"));
            assertEquals("id", Translator.normalizeLangCode("IN"));
            assertEquals("id", Translator.normalizeLangCode("id"));
            assertEquals("Bahasa Indonesia", Translator.getLanguageDisplayName("id"));
        }
        if (supportedSet.contains("he")) {
            assertEquals("he", Translator.normalizeLangCode("iw"));
            assertEquals("he", Translator.normalizeLangCode("IW"));
            assertEquals("he", Translator.normalizeLangCode("he"));
        }
        if (supportedSet.contains("fil")) {
            assertEquals("fil", Translator.normalizeLangCode("tl"));
            assertEquals("fil", Translator.normalizeLangCode("TL"));
            assertEquals("fil", Translator.normalizeLangCode("fil"));
        }
        if (supportedSet.contains("ru")) {
            assertEquals("ru", Translator.normalizeLangCode("ru"));
            assertEquals("ru", Translator.normalizeLangCode("RU"));
            assertEquals("\u0420\u0443\u0441\u0441\u043a\u0438\u0439", Translator.getLanguageDisplayName("ru"));
        }
        assertEquals(Translator.LANG_AUTO, Translator.normalizeLangCode("unknown"));
        assertEquals(Translator.LANG_AUTO, Translator.normalizeLangCode(null));

        assertTrue(Translator.isRtlLang("ar"));
        assertTrue(Translator.isRtlLang("fa"));
        assertTrue(Translator.isRtlLang("he"));
        assertTrue(Translator.isRtlLang("ur"));
        assertFalse(Translator.isRtlLang("en"));
        assertFalse(Translator.isRtlLang("id"));
        assertFalse(Translator.isRtlLang("ru"));
        assertFalse(Translator.isRtlLang("vi"));
    }
}
