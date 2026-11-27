package com.magiii.televipnext.Clients;

import java.util.HashMap;
import java.util.Map;

public class Nagram {

    public static class ParameterResolver {
        static Map<String, Class<?>[]> objectList = new HashMap<>();

        public static void register(String name, Class<?>[] classes) {
            objectList.put(name, classes);
        }

        public static Class<?>[] resolve(String name) {
            return objectList.get(name);
        }

        public static boolean has(String name) {
            return objectList.get(name) != null;
        }
    }

    public static void loadParameter() {
        // Nagram v13.1.1+ uses standard Telegram hook signatures; obsolete overrides removed.
    }

}
