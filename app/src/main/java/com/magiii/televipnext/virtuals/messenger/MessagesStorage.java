package com.magiii.televipnext.virtuals.messenger;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.SQLite.SQLiteDatabase;

import com.magiii.televipnext.xposed.ReflectHelper;

public class MessagesStorage {

    Object messagesStorage;

    public MessagesStorage(Object obj) {
        messagesStorage = obj;
    }

    public SQLiteDatabase getDatabase() {

        return new SQLiteDatabase(ReflectHelper.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getDatabase")));
    }

    public DispatchQueue getStorageQueue() {

        return new DispatchQueue(ReflectHelper.callMethod(messagesStorage, Obfuscate.getMethodName("MessagesStorage", "getStorageQueue")));
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
