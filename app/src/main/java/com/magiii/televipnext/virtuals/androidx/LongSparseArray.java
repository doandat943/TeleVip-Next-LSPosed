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

    @SuppressWarnings("unchecked")
    public ArrayList<Object> get(long id){
        if (longSparseArray == null) return null;
        Object val = ReflectHelper.callMethod(longSparseArray, Obfuscate.getMethodName("LongSparseArray", "get"), id);
        return val instanceof ArrayList ? (ArrayList<Object>) val : null;
    }
}
