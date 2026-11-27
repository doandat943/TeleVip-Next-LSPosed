package com.magiii.televipnext.Clients;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Nicegram {

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
        ParameterResolver.register("fillMessageMenu", new Class[]{
                ClassLoad.getClass(ClassNames.MESSAGE_OBJECT),
                ArrayList.class,
                ArrayList.class,
                ArrayList.class,
                ArrayList.class,
                ArrayList.class,
                ArrayList.class
        });
    }
}
