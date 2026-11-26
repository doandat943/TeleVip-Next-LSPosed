package com.magiii.televipnext.utils;

import android.content.Context;

import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.settings.ui.GlassDialog;

public class DialogUtils {

    public static void showQuranAlert(Context context){
        new GlassDialog.Builder(context)
                .setShowBrandEmblem(true)
                .setTitle("TeleVip Next")
                .setMessage(Translator.get(Keys.QuranNotification))
                .setPositiveButton(Translator.get(Keys.Done), null)
                .show();
    }
}
