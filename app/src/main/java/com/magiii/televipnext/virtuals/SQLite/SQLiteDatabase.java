package com.magiii.televipnext.virtuals.SQLite;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SQLiteDatabase {

    Object sqLiteDatabase;

    public SQLiteDatabase(Object obj){ sqLiteDatabase = obj; }

    public SQLiteCursor queryFinalized(String s, Object[] objects){
        return new SQLiteCursor(ReflectHelper.callMethod(sqLiteDatabase, Obfuscate.getMethodName("SQLiteDatabase", "queryFinalized"), s, objects));
    }

    public SQLitePreparedStatement executeFast(String s){
        return new SQLitePreparedStatement(ReflectHelper.callMethod(sqLiteDatabase, Obfuscate.getMethodName("SQLiteDatabase", "executeFast"), s));
    }

}
