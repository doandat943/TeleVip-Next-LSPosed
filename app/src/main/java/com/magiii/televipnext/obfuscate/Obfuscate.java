package com.magiii.televipnext.obfuscate;

import com.magiii.televipnext.obfuscate.struct.ClientObfuscationData;

public class Obfuscate {

    public static String getClassName(String className) {
        ClientObfuscationData data = ObfuscationManager.current();
        if (data == null) return className;
        return data.resolveClass(className);
    }

    public static String getMethodName(String className, String methodName) {
        ClientObfuscationData data = ObfuscationManager.current();
        if (data == null) return methodName.replace("storyEntitiesAllowed2", "storyEntitiesAllowed");
        return data.resolveMethod(className, methodName);
    }

    public static String getFieldName(String className, String fieldName) {
        ClientObfuscationData data = ObfuscationManager.current();
        if (data == null) return fieldName;
        return data.resolveField(className, fieldName);
    }
}