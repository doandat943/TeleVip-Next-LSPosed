package com.magiii.televipnext.features.otherFeatures;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.SimpleTextView;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;

import com.magiii.televipnext.xposed.ReflectHelper;

public class CopyNameHook {

    public static void init() {
        try {
            Class<?> profileClass = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
            if (profileClass != null) {
                String createViewName = Obfuscate.getMethodName("ProfileActivity", "createView");
                Class<?>[] params = ArgsResolver.resolveObject("createView", new Class[]{Context.class});
                if (params == null || ReflectHelper.findMethodExactIfExists(profileClass, createViewName, (Object[]) params) == null) {
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
