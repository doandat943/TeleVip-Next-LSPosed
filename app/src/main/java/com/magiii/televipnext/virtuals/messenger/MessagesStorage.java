package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.SQLite.SQLiteDatabase;

import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class MessagesStorage {

    Object messagesStorage;

    public MessagesStorage(Object obj) {
        messagesStorage = obj;
    }

    public SQLiteDatabase getDatabase() {
        if (messagesStorage == null) return null;
        try {
            String mName = Obfuscate.getMethodName("MessagesStorage", "getDatabase");
            if (mName != null && ReflectHelper.findMethodExactIfExists(messagesStorage.getClass(), mName) != null) {
                Object db = ReflectHelper.callMethod(messagesStorage, mName);
                if (db != null) return new SQLiteDatabase(db);
            }
            if (!"getDatabase".equals(mName)
                    && ReflectHelper.findMethodExactIfExists(messagesStorage.getClass(), "getDatabase") != null) {
                Object db = ReflectHelper.callMethod(messagesStorage, "getDatabase");
                if (db != null) return new SQLiteDatabase(db);
            }
        } catch (Throwable ignored) {}

        // Fallback for R8-inlined getDatabase() (e.g. Momogram): scan instance fields for SQLiteDatabase
        try {
            Class<?> dbCls = ReflectHelper.findClassIfExists("org.telegram.SQLite.SQLiteDatabase", Utils.classLoader);
            if (dbCls != null) {
                for (Class<?> c = messagesStorage.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers()) && dbCls.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Object val = f.get(messagesStorage);
                            if (val != null) return new SQLiteDatabase(val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public DispatchQueue getStorageQueue() {
        if (messagesStorage == null) return Utilities.getStageQueue();
        try {
            String mName = Obfuscate.getMethodName("MessagesStorage", "getStorageQueue");
            if (mName != null && ReflectHelper.findMethodExactIfExists(messagesStorage.getClass(), mName) != null) {
                Object q = ReflectHelper.callMethod(messagesStorage, mName);
                if (q != null) return new DispatchQueue(q);
            }
            if (!"getStorageQueue".equals(mName)
                    && ReflectHelper.findMethodExactIfExists(messagesStorage.getClass(), "getStorageQueue") != null) {
                Object q = ReflectHelper.callMethod(messagesStorage, "getStorageQueue");
                if (q != null) return new DispatchQueue(q);
            }
        } catch (Throwable ignored) {}

        // Fallback for R8-inlined getStorageQueue() (e.g. Momogram): scan DispatchQueue field or stageQueue
        try {
            Class<?> dqCls = ClassLoad.getClass("org.telegram.messenger.DispatchQueue", Utils.classLoader, false);
            if (dqCls != null) {
                for (Class<?> c = messagesStorage.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers()) && dqCls.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            Object val = f.get(messagesStorage);
                            if (val != null) return new DispatchQueue(val);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Utilities.getStageQueue();
    }

    public int getLastPtsValue() {
        return (int) ReflectHelper.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getLastPtsValue"));
    }

    public static MessagesStorage getInstance(int num) {
        return new MessagesStorage(ReflectHelper.callStaticMethod(ClassLoad.getClass(ClassNames.MESSAGES_STORAGE), Obfuscate.getMethodName("MessagesStorage", "getInstance"), num));
    }

    public static MessagesStorage getMessagesStorage() {
        return getInstance(UserConfig.getSelectedAccount());
    }

}
