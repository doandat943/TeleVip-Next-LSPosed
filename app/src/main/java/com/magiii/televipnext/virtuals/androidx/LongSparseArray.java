package com.magiii.televipnext.virtuals.androidx;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import java.util.ArrayList;

import com.magiii.televipnext.xposed.ReflectHelper;

public class LongSparseArray {

    Object longSparseArray;

    public LongSparseArray(Object longSparseArray) {
        this.longSparseArray = longSparseArray;
    }

    public ArrayList<Object> get(long id){
        return (ArrayList<Object>) ReflectHelper.callMethod(longSparseArray, Obfuscate.getMethodName("LongSparseArray", "get"), id);
    }
}
