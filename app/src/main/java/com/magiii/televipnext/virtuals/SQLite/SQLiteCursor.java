package com.magiii.televipnext.virtuals.SQLite;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.tgnet.NativeByteBuffer;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SQLiteCursor {

    Object sqLiteCursor;

    public SQLiteCursor(Object obj){
        sqLiteCursor = obj;
    }

    public boolean next(){
        return (boolean) ReflectHelper.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "next"));
    }

    public int intValue(int columnIndex) {
        return (int) ReflectHelper.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "intValue"), columnIndex);
    }

    public long longValue(int columnIndex) {
        return (long) ReflectHelper.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "longValue"), columnIndex);
    }

    public void dispose() {
        ReflectHelper.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "dispose"));
    }

    public NativeByteBuffer byteBufferValue(int columnIndex){
        return new NativeByteBuffer(ReflectHelper.callMethod(sqLiteCursor, Obfuscate.getMethodName("SQLiteCursor", "byteBufferValue"), columnIndex));
    }

}
