package com.magiii.televipnext.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure Java reflection helper replacing XposedHelpers without touching de.robv.android.xposed.
 */
public final class ReflectHelper {

    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final ConcurrentHashMap<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<Constructor<?>>> CTOR_CACHE = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER = new HashMap<>();
    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();

    static {
        PRIMITIVE_TO_WRAPPER.put(boolean.class, Boolean.class);
        PRIMITIVE_TO_WRAPPER.put(byte.class, Byte.class);
        PRIMITIVE_TO_WRAPPER.put(short.class, Short.class);
        PRIMITIVE_TO_WRAPPER.put(char.class, Character.class);
        PRIMITIVE_TO_WRAPPER.put(int.class, Integer.class);
        PRIMITIVE_TO_WRAPPER.put(long.class, Long.class);
        PRIMITIVE_TO_WRAPPER.put(float.class, Float.class);
        PRIMITIVE_TO_WRAPPER.put(double.class, Double.class);
        for (Map.Entry<Class<?>, Class<?>> e : PRIMITIVE_TO_WRAPPER.entrySet()) {
            WRAPPER_TO_PRIMITIVE.put(e.getValue(), e.getKey());
        }
    }

    private ReflectHelper() {}

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        if (className == null) return null;
        try {
            ClassLoader cl = classLoader != null ? classLoader : ReflectHelper.class.getClassLoader();
            return Class.forName(className, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Field findFieldIfExists(Class<?> clazz, String fieldName) {
        if (clazz == null || fieldName == null) return null;
        String key = buildClassKey(clazz) + '#' + fieldName;
        Optional<Field> cached = FIELD_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        Class<?> cur = clazz;
        Field found = null;
        while (cur != null) {
            try {
                found = cur.getDeclaredField(fieldName);
                found.setAccessible(true);
                break;
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        FIELD_CACHE.putIfAbsent(key, Optional.ofNullable(found));
        return found;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Field f = findFieldIfExists(clazz, fieldName);
        if (f == null) {
            throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
        }
        return f;
    }

    public static Method findMethodInHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return findMethodInHierarchy(clazz, methodName, false, parameterTypes);
    }

    public static Method findMethodInHierarchy(
            Class<?> clazz, String methodName, boolean onlyStatic, Class<?>... parameterTypes) {
        if (clazz == null || methodName == null) return null;
        for (Class<?> cur = clazz; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            try {
                Method m = cur.getDeclaredMethod(methodName, parameterTypes);
                if (onlyStatic && !Modifier.isStatic(m.getModifiers())) continue;
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Object... parameterTypes) {
        if (clazz == null || methodName == null) return null;
        Class<?>[] paramClasses = resolveParameterClasses(clazz.getClassLoader(), parameterTypes);
        if (paramClasses == null) return null;

        String key = buildSignatureKey(clazz, methodName, paramClasses);
        Optional<Method> cached = METHOD_CACHE.get(key);
        if (cached != null) return cached.orElse(null);

        Method found = null;
        try {
            found = clazz.getDeclaredMethod(methodName, paramClasses);
            found.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
        }
        METHOD_CACHE.putIfAbsent(key, Optional.ofNullable(found));
        return found;
    }

    private static Class<?>[] resolveParameterClasses(ClassLoader loader, Object[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            return new Class<?>[0];
        }
        Class<?>[] out = new Class<?>[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
            Object p = parameterTypes[i];
            if (p instanceof Class<?>) {
                out[i] = (Class<?>) p;
            } else if (p instanceof String) {
                Class<?> resolved = findClassIfExists((String) p, loader);
                if (resolved == null) return null;
                out[i] = resolved;
            } else {
                return null;
            }
        }
        return out;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            findField(obj.getClass(), fieldName).set(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static int getIntField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getInt(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setIntField(Object obj, String fieldName, int value) {
        try {
            findField(obj.getClass(), fieldName).setInt(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static long getLongField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getLong(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setLongField(Object obj, String fieldName, long value) {
        try {
            findField(obj.getClass(), fieldName).setLong(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static boolean getBooleanField(Object obj, String fieldName) {
        try {
            return findField(obj.getClass(), fieldName).getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static void setBooleanField(Object obj, String fieldName, boolean value) {
        try {
            findField(obj.getClass(), fieldName).setBoolean(obj, value);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Object getStaticObjectField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static int getStaticIntField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).getInt(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static boolean getStaticBooleanField(Class<?> clazz, String fieldName) {
        try {
            return findField(clazz, fieldName).getBoolean(null);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        }
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        Object[] safeArgs = args != null ? args : EMPTY_ARGS;
        Class<?>[] argTypes = getParameterTypes(safeArgs);
        Method m = findMethodBestMatch(obj.getClass(), methodName, argTypes, false);
        try {
            return m.invoke(obj, safeArgs);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw wrapInvocationException(e);
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        Object[] safeArgs = args != null ? args : EMPTY_ARGS;
        Class<?>[] argTypes = getParameterTypes(safeArgs);
        Method m = findMethodBestMatch(clazz, methodName, argTypes, true);
        try {
            return m.invoke(null, safeArgs);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw wrapInvocationException(e);
        }
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        Object[] safeArgs = args != null ? args : EMPTY_ARGS;
        Class<?>[] argTypes = getParameterTypes(safeArgs);
        Constructor<?> ctor = findConstructorBestMatch(clazz, argTypes);
        try {
            return ctor.newInstance(safeArgs);
        } catch (IllegalAccessException e) {
            throw new IllegalAccessError(e.getMessage());
        } catch (InstantiationException e) {
            throw new InstantiationError(e.getMessage());
        } catch (InvocationTargetException e) {
            throw wrapInvocationException(e);
        }
    }

    private static Class<?>[] getParameterTypes(Object[] args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] != null ? args[i].getClass() : null;
        }
        return types;
    }

    private static Method findMethodBestMatch(Class<?> clazz, String methodName, Class<?>[] argTypes, boolean onlyStatic) {
        String key = buildSignatureKey(clazz, methodName + (onlyStatic ? "$S" : "$I"), argTypes);
        Optional<Method> cached = METHOD_CACHE.get(key);
        if (cached != null) {
            Method m = cached.orElse(null);
            if (m != null) return m;
            throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
        }

        Method bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (Class<?> cur = clazz; cur != null; cur = cur.getSuperclass()) {
            for (Method m : cur.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                if (onlyStatic && !Modifier.isStatic(m.getModifiers())) continue;
                int score = scoreMatch(m.getParameterTypes(), argTypes);
                if (score >= 0 && score < bestScore) {
                    bestScore = score;
                    bestMatch = m;
                    if (score == 0) break;
                }
            }
            if (bestScore == 0) break;
        }

        if (bestMatch != null) {
            bestMatch.setAccessible(true);
            METHOD_CACHE.putIfAbsent(key, Optional.of(bestMatch));
            return bestMatch;
        }
        METHOD_CACHE.putIfAbsent(key, Optional.empty());
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName);
    }

    private static Constructor<?> findConstructorBestMatch(Class<?> clazz, Class<?>[] argTypes) {
        String key = buildSignatureKey(clazz, "<init>", argTypes);
        Optional<Constructor<?>> cached = CTOR_CACHE.get(key);
        if (cached != null) {
            Constructor<?> c = cached.orElse(null);
            if (c != null) return c;
            throw new NoSuchMethodError(clazz.getName() + "#<init>");
        }

        Constructor<?> bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            int score = scoreMatch(c.getParameterTypes(), argTypes);
            if (score >= 0 && score < bestScore) {
                bestScore = score;
                bestMatch = c;
                if (score == 0) break;
            }
        }

        if (bestMatch != null) {
            bestMatch.setAccessible(true);
            CTOR_CACHE.putIfAbsent(key, Optional.of(bestMatch));
            return bestMatch;
        }
        CTOR_CACHE.putIfAbsent(key, Optional.empty());
        throw new NoSuchMethodError(clazz.getName() + "#<init>");
    }

    private static int scoreMatch(Class<?>[] paramTypes, Class<?>[] argTypes) {
        if (paramTypes.length != argTypes.length) return -1;
        int totalScore = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            int s = singleParamDistance(paramTypes[i], argTypes[i]);
            if (s < 0) return -1;
            totalScore += s;
        }
        return totalScore;
    }

    private static int singleParamDistance(Class<?> paramType, Class<?> argType) {
        if (argType == null) {
            return paramType.isPrimitive() ? -1 : 1;
        }
        if (paramType.equals(argType)) {
            return 0;
        }
        if (paramType.isPrimitive()) {
            Class<?> argPrim = argType.isPrimitive() ? argType : WRAPPER_TO_PRIMITIVE.get(argType);
            if (argPrim == null) return -1;
            if (paramType.equals(argPrim)) return 1;
            return isPrimitiveWidenable(argPrim, paramType) ? 3 : -1;
        }
        if (argType.isPrimitive()) {
            Class<?> argWrapper = PRIMITIVE_TO_WRAPPER.get(argType);
            if (paramType.equals(argWrapper)) return 1;
            argType = argWrapper;
        }
        if (!paramType.isAssignableFrom(argType)) return -1;
        if (paramType.isInterface()) return 5;
        int distance = 0;
        Class<?> cur = argType;
        while (cur != null && !cur.equals(paramType)) {
            distance += 2;
            cur = cur.getSuperclass();
        }
        return cur != null ? distance : 10;
    }

    private static boolean isPrimitiveWidenable(Class<?> from, Class<?> to) {
        if (from == byte.class) {
            return to == short.class || to == int.class || to == long.class || to == float.class || to == double.class;
        }
        if (from == short.class || from == char.class) {
            return to == int.class || to == long.class || to == float.class || to == double.class;
        }
        if (from == int.class) {
            return to == long.class || to == float.class || to == double.class;
        }
        if (from == long.class) {
            return to == float.class || to == double.class;
        }
        if (from == float.class) {
            return to == double.class;
        }
        return false;
    }

    private static RuntimeException wrapInvocationException(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        return new RuntimeException(cause != null ? cause : e);
    }

    private static String buildClassKey(Class<?> clazz) {
        return clazz.getName() + '@' + System.identityHashCode(clazz.getClassLoader());
    }

    private static String buildSignatureKey(Class<?> clazz, String name, Class<?>[] types) {
        StringBuilder sb = new StringBuilder(buildClassKey(clazz));
        sb.append('#').append(name).append('(');
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(types[i] != null ? types[i].getName() : "null");
        }
        sb.append(')');
        return sb.toString();
    }
}
