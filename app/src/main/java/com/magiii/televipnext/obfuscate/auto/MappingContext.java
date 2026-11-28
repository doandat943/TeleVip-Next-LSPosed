package com.magiii.televipnext.obfuscate.auto;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared mapping state and DEX helper utilities used by AutoMappingResolver collaborators.
 */
final class MappingContext {

    final Map<String, String> classMap;
    final Map<String, String> fieldMap;
    final Map<String, String> methodMap;

    MappingContext(Map<String, String> classMap, Map<String, String> fieldMap, Map<String, String> methodMap) {
        this.classMap = classMap;
        this.fieldMap = fieldMap;
        this.methodMap = methodMap;
    }

    void addClass(String original, String resolvedDesc) {
        if (original != null && resolvedDesc != null) {
            classMap.put(original, DexScanner.descToJava(resolvedDesc));
        }
    }

    void addMethod(String simpleClass, String originalMethod, String resolvedMethod) {
        if (simpleClass != null && originalMethod != null && resolvedMethod != null) {
            methodMap.put(simpleClass + "#" + originalMethod, resolvedMethod);
        }
    }

    void addField(String simpleClass, String originalField, String resolvedField) {
        if (simpleClass != null && originalField != null && resolvedField != null) {
            fieldMap.put(simpleClass + "#" + originalField, resolvedField);
        }
    }


    String resolvedDescOrRaw(String className) {
        String mapped = classMap.get(className);
        return DexScanner.javaToDesc(mapped != null ? mapped : className);
    }


    static String findClassByStringsWithFallback(DexScanner scanner, String pkgPrefix, String superDesc, String... requiredStrings) {
        String found = scanner.findClassByStrings(pkgPrefix, superDesc, requiredStrings);
        if (found == null && pkgPrefix != null) {
            found = scanner.findClassByStrings(null, superDesc, requiredStrings);
        }
        return found;
    }


    static String findFirstReadLongField(DexScanner scanner, String subDesc, String ownerDesc) {
        if (subDesc == null || ownerDesc == null) return null;
        for (DexScanner.MethodInfo m : scanner.getMethods(subDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(subDesc, m.name, m.paramDescs, ownerDesc)) {
                    if ("J".equals(rf.typeDesc)) {
                        return rf.name;
                    }
                }
            }
        }
        return null;
    }


    static boolean hasClass(DexScanner scanner, String desc) {
        return !scanner.getMethods(desc).isEmpty() || !scanner.getFields(desc).isEmpty();
    }

    static boolean hasFieldNamed(DexScanner scanner, String classDesc, String fieldName) {
        for (DexScanner.FieldInfo f : scanner.getFields(classDesc)) {
            if (fieldName.equals(f.name)) return true;
        }
        return false;
    }

    static boolean hasMethodNamed(DexScanner scanner, String classDesc, String methodName) {
        for (DexScanner.MethodInfo m : scanner.getMethods(classDesc)) {
            if (methodName.equals(m.name)) return true;
        }
        return false;
    }

    static boolean hasZeroArgMethodNamed(DexScanner scanner, String classDesc, String methodName) {
        for (DexScanner.MethodInfo m : scanner.getMethods(classDesc)) {
            if (!m.isStatic && methodName.equals(m.name) && m.paramDescs.length == 0) return true;
        }
        return false;
    }

    static boolean hasStaticFieldOfType(DexScanner scanner, String classDesc, String typeDesc) {
        for (DexScanner.FieldInfo f : scanner.getFields(classDesc)) {
            if (f.isStatic && typeDesc.equals(f.typeDesc)) return true;
        }
        return false;
    }

    static Set<String> collectWrittenDialogFields(
            DexScanner scanner, String builderDesc, String dialogDesc, DexScanner.MethodInfo m) {
        Set<String> out = new HashSet<>();
        for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(builderDesc, m.name, m.paramDescs, dialogDesc)) {
            if (!f.isStatic) out.add(f.name);
        }
        for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(builderDesc, m.name, m.paramDescs, dialogDesc)) {
            for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(dialogDesc, ref.name, ref.paramDescs, dialogDesc)) {
                if (!f.isStatic) out.add(f.name);
            }
        }
        return out;
    }

    static String getSuperDesc(ClassLoader classLoader, String className) {
        try {
            Class<?> cls = Class.forName(className, false, classLoader);
            Class<?> sup = cls.getSuperclass();
            if (sup != null) {
                return DexScanner.javaToDesc(sup.getName());
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
