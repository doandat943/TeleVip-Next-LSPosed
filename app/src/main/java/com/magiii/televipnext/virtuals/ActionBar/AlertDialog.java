package com.magiii.televipnext.virtuals.ActionBar;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;

import java.lang.reflect.Proxy;

import com.magiii.televipnext.xposed.ReflectHelper;

public class AlertDialog {


    @FunctionalInterface
    public interface OnClick {
        void onClick();
    }

    public static Object click(OnClick lambda) {
        Class<?> listenerClass = ClassLoad.getClass(ClassNames.ALERT_DIALOG_BUTTON_CLICK);
        if (listenerClass != null) {
            return Proxy.newProxyInstance(
                    Utils.classLoader,
                    new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if (method.getName().equals(Obfuscate.getMethodName("AlertDialog$OnButtonClickListener", "onClick"))) {
                            lambda.onClick();
                        }
                        return null;
                    }
            );
        } else {
            return (DialogInterface.OnClickListener) (dialog, which) -> lambda.onClick();
        }
    }

    Object alertDialog;

    public AlertDialog(Context context) {
        alertDialog = ReflectHelper.newInstance(ClassLoad.getClass(ClassNames.ALERT_DIALOG_BUILDER), context);
    }

    public Dialog getAlertDialog() {
        return (Dialog) ReflectHelper.getObjectField(alertDialog, Obfuscate.getFieldName("AlertDialog$Builder", "alertDialog"));
    }

    public void setTitle(CharSequence title) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setTitle"), title);
    }

    public void setView(View view) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setView"), view);
    }

    public void setMessage(CharSequence message) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setMessage"), message);
    }

    public void setPositiveButton(CharSequence text, Object obj) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setPositiveButton"),
                text, obj
        );
    }

    public void setNegativeButton(CharSequence text, Object obj) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNegativeButton"),
                text, obj
        );
    }

    public void setNeutralButton(CharSequence text, Object obj) {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "setNeutralButton"),
                text, obj
        );
    }

    public void show() {
        ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "show"));
    }

    public Dialog create() {
        return (Dialog) ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "create"));
    }

    public Runnable getDismissRunnable() {
        return (Runnable) ReflectHelper.callMethod(alertDialog, Obfuscate.getMethodName("AlertDialog$Builder", "getDismissRunnable"));
    }

}
