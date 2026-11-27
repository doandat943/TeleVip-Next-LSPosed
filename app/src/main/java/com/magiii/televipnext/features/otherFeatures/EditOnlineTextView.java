package com.magiii.televipnext.features.otherFeatures;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.virtuals.ActionBar.SimpleTextView;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;
import com.magiii.televipnext.xposed.ReflectHelper;

public class EditOnlineTextView {
    public static boolean isEnable = false;

    private static final ThreadLocal<Boolean> IN_APPLY = new ThreadLocal<>();

    public static void init() {
        try {
            if (!isEnable) {
                isEnable = true;
                Class<?> profileClass = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
                if (profileClass == null) return;

                HMethod.hookMethod(profileClass,
                        Obfuscate.getMethodName("ProfileActivity", "updateProfileData"),
                        ArgsResolver.merge("updateProfileData", new Class[]{boolean.class},
                                new BaseMethodHook() {
                                    @Override
                                    protected void afterMethod(MethodHookParam param) {
                                        applyToProfile(param.thisObject);
                                    }
                                })
                );

                String updateOnline = Obfuscate.getMethodName("ProfileActivity", "updateOnlineTextView");
                if (ReflectHelper.findMethodExactIfExists(profileClass, updateOnline) != null) {
                    HMethod.hookMethod(profileClass, updateOnline, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            applyToProfile(param.thisObject);
                        }
                    });
                } else if (ReflectHelper.findMethodExactIfExists(profileClass, updateOnline, boolean.class) != null) {
                    HMethod.hookMethod(profileClass, updateOnline, boolean.class, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            applyToProfile(param.thisObject);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void applyToProfile(Object profileObj) {
        if (profileObj == null || Boolean.TRUE.equals(IN_APPLY.get())) return;
        IN_APPLY.set(Boolean.TRUE);
        try {
            final ProfileActivity profileActivity = new ProfileActivity(profileObj);
            Object[] onlineTextViewArray = profileActivity.getOnlineTextView();
            if (onlineTextViewArray == null || onlineTextViewArray.length == 0) return;

            boolean legacyArray = onlineTextViewArray.length > 2;
            Object hideTarget = legacyArray ? onlineTextViewArray[1]
                    : (onlineTextViewArray.length > 1 ? onlineTextViewArray[1] : onlineTextViewArray[0]);

            if (ConfigManager.hideOnline != null && ConfigManager.hideOnline.isEnable() && hideTarget != null) {
                SimpleTextView simpleTextView = new SimpleTextView(hideTarget);
                if (simpleTextView.getSimpleTextView() != null) {
                    long uid = profileActivity.getUserId();
                    if (uid != 0 && uid == profileActivity.getBaseFragment().getUserConfig().getClientUserId()) {
                        simpleTextView.setText(Translator.get(Keys.UserOffline));
                    }
                }
            }
        } finally {
            IN_APPLY.remove();
        }
    }
}
