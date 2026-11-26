package com.magiii.televipnext.utils;

import android.content.Context;

import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.virtuals.ActionBar.AlertDialog;

public class DialogUtils {

    public static void showQuranAlert(Context context){
        AlertDialog alertDialog = new AlertDialog(context);
        alertDialog.setTitle("TeleVip Next");
        alertDialog.setMessage(Translator.get(Keys.QuranNotification));
        alertDialog.setPositiveButton(Translator.get(Keys.Done), null);
        alertDialog.show();
    }
}
