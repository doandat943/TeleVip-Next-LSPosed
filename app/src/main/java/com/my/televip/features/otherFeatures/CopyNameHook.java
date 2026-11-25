package com.my.televip.features.otherFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.ArgsResolver;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.SimpleTextView;
import com.my.televip.virtuals.ui.ProfileActivity;

import de.robv.android.xposed.XposedHelpers;

public class CopyNameHook {

    public static void init() {
        try {
            Class<?> profileClass = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
            if (profileClass != null) {
                String createViewName = Obfuscate.getMethodName("ProfileActivity", "createView");
                Class<?>[] params = ArgsResolver.resolveObject("createView", new Class[]{Context.class});
                if (params == null || XposedHelpers.findMethodExactIfExists(profileClass, createViewName, (Object[]) params) == null) {
                    return;
                }

                HMethod.hookMethod(profileClass, createViewName, ArgsResolver.merge("createView", new Class[]{Context.class}, new BaseMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        final ProfileActivity profileActivity = new ProfileActivity(param.thisObject);

                        Object[] nameTextViewArray = profileActivity.getNameTextView();

                        if (nameTextViewArray != null && nameTextViewArray.length > 0) {
                            Object target = nameTextViewArray.length > 1 ? nameTextViewArray[1] : nameTextViewArray[0];
                            if (target == null) return;

                            SimpleTextView simpleTextView = new SimpleTextView(target);

                            if (simpleTextView.getSimpleTextView() != null) {
                                simpleTextView.getSimpleTextView().setOnClickListener(v -> {
                                    if (simpleTextView.getText() != null) {
                                        String name = Translator.get(Keys.Copied, simpleTextView.getText());
                                        ((ClipboardManager) Utils.getCurrentActivity().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("clipboard", simpleTextView.getText()));
                                        Toast.makeText(Utils.getCurrentActivity(), name, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }
                }));
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }
}
