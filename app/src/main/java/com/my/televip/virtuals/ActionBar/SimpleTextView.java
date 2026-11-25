package com.my.televip.virtuals.ActionBar;

import android.text.Layout;
import android.view.View;

import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;

import de.robv.android.xposed.XposedHelpers;

public class SimpleTextView {

    Object simpleTextView;

    public SimpleTextView(Object textview){
        simpleTextView = textview;
    }

    public CharSequence getText(){
        return (CharSequence) XposedHelpers.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "getText"));
    }

    public void setText(CharSequence text){
        XposedHelpers.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setText"), text);
    }

    public void setText(CharSequence text, boolean force){
        String method = Obfuscate.getMethodName("SimpleTextView", "setTextForce");
        if ("setTextForce".equals(method)) {
            method = Obfuscate.getMethodName("SimpleTextView", "setText");
        }
        XposedHelpers.callMethod(simpleTextView, method, text, force);
    }

    public void setAlignment(Layout.Alignment alignment){
        XposedHelpers.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setAlignment"), alignment);
    }

    public void setMaxLines(int value){
        XposedHelpers.callMethod(simpleTextView, Obfuscate.getMethodName("SimpleTextView", "setMaxLines"), value);
    }

    public View getSimpleTextView(){
        return (View) simpleTextView;
    }

}
