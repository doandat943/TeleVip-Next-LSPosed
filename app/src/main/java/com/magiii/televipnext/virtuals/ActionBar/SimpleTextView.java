package com.magiii.televipnext.virtuals.ActionBar;

import android.text.Layout;
import android.text.TextPaint;
import android.view.View;

import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;

import com.magiii.televipnext.xposed.ReflectHelper;

public class SimpleTextView {

    Object simpleTextView;

    public SimpleTextView(Object textview){
        simpleTextView = textview;
    }

    public CharSequence getText(){
        return (CharSequence) ReflectHelper.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "getText"));
    }

    public void setText(CharSequence text){
        ReflectHelper.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setText"), text);
    }

    public void setText(CharSequence text, boolean force){
        String method = Obfuscate.getMethodName("SimpleTextView", "setTextForce");
        if ("setTextForce".equals(method)) {
            method = Obfuscate.getMethodName("SimpleTextView", "setText");
        }
        ReflectHelper.callMethod(simpleTextView, method, text, force);
    }

    public void setAlignment(Layout.Alignment alignment){
        ReflectHelper.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setAlignment"), alignment);
    }

    public void setMaxLines(int value){
        ReflectHelper.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setMaxLines"), value);
    }

    public TextPaint getTextPaint(){
        try {
            String m = Obfuscate.getMethodName("SimpleTextView", "getTextPaint");
            Object res = ReflectHelper.callMethod(simpleTextView, m);
            if (res instanceof TextPaint) return (TextPaint) res;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public View getSimpleTextView(){
        return (View) simpleTextView;
    }

}
