package com.my.televip.utils;

import android.content.Context;

import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.virtuals.ActionBar.AlertDialog;

public class DialogUtils {

    public static void showQuranAlert(Context context){
        AlertDialog alertDialog = new AlertDialog(context);
        alertDialog.setTitle("TeleVip Next");
        alertDialog.setMessage(Translator.get(Keys.QuranNotification));
        alertDialog.setPositiveButton(Translator.get(Keys.Done), null);
        alertDialog.show();
    }
}
