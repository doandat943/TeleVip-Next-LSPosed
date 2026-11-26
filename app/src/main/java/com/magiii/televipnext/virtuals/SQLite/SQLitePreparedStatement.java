package com.magiii.televipnext.virtuals.SQLite;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.tgnet.NativeByteBuffer;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SQLitePreparedStatement {

    Object sQLitePreparedStatement;

    public SQLitePreparedStatement(Object sQLitePreparedStatement){
        this.sQLitePreparedStatement = sQLitePreparedStatement;
    }

    public void requery() {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","requery"));
    }

    public void step() {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","step"));
    }

    public void dispose() {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","dispose"));
    }

    public void bindByteBuffer(int index, NativeByteBuffer value) {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindByteBuffer"), index, value.nativeByteBuffer);
    }

    public void bindLong(int index, long value) {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindLong"), index, value);
    }

    public void bindInteger(int index, int value) {
        ReflectHelper.callMethod(sQLitePreparedStatement, Obfuscate.getMethodName("SQLitePreparedStatement","bindInteger"), index, value);
    }

}
