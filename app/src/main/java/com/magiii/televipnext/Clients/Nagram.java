package com.magiii.televipnext.Clients;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class Nagram {

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
        ParameterResolver.register("markMessagesAsDeletedInternal",new Class[]{ArrayList.class, boolean.class, int.class, int.class, long.class});
        ParameterResolver.register("formatSmallDateChat",new Class[]{long.class});

        ParameterResolver.register("putMessages",new Class[]{int.class, int.class, int.class, long.class, long.class, ClassLoad.getClass(ClassNames.TL_MESSAGES_MESSAGES), boolean.class});
    }

}
