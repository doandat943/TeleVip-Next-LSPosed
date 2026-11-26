package com.magiii.televipnext.virtuals.tgnet;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class NativeByteBuffer {

    public final Object nativeByteBuffer;

    public NativeByteBuffer(Object obj){
        nativeByteBuffer = obj;
    }
    public NativeByteBuffer(boolean calculate){
        nativeByteBuffer = ReflectHelper.newInstance(ClassLoad.getClass(ClassNames.NATIVE_BYTE_BUFFER), calculate);
    }

    public void reuse(){
        ReflectHelper.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer", "reuse"));
    }

    public int readInt32(boolean exception) {
        return (int) ReflectHelper.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","readInt32"), exception);
    }

    public void position(int i) {
        ReflectHelper.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","position"), i);
    }

    public void writeInt32(int i) {
        ReflectHelper.callMethod(nativeByteBuffer, Obfuscate.getMethodName("NativeByteBuffer","writeInt32"), i);
    }


}
