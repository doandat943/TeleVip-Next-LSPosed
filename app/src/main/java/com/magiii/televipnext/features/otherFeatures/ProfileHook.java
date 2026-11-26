package com.magiii.televipnext.features.otherFeatures;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.IdDateEstimator;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.ActionBarMenuItem;
import com.magiii.televipnext.virtuals.ActionBar.AlertDialog;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;

import com.magiii.televipnext.xposed.ReflectHelper;

public class ProfileHook {

    private static boolean initialized = false;

    public static void init(String className) {
        if (initialized || className == null) return;

        Class<?> clazz = ClassLoad.getClass(className);
        if (clazz == null) FeatureStateManager.reset();

        initialized = true;

        HMethod.hookMethod(ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY), Obfuscate.getMethodName("ProfileActivity", "createActionBarMenu"), ArgsResolver.merge("createActionBarMenu", new Class[]{boolean.class}, new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {
                ProfileActivity profileActivity = new ProfileActivity(param.thisObject);
                if (getUserID(profileActivity) > 1) {

                    ActionBarMenuItem otherItem = profileActivity.getOtherItem();

                    if (otherItem.getActionBarMenuItem() != null) {

                        int drawableResource = 0x7f0806d3;

                        if (!ClientManager.is(ClientManager.Client.Nagram) && !ClientManager.is(ClientManager.Client.Momogram)) {
                            drawableResource = ReflectHelper.getStaticIntField(ClassLoad.getClass(ClassNames.DRAWABLE), "msg_filled_menu_users");
                        }

                        otherItem.addSubItem(8353847, drawableResource, Translator.get(Keys.ApproximateCreationDate));
                    }
                }
            }
        }));

        HMethod.hookMethod(clazz, "onItemClick", int.class, new BaseMethodHook() {
            @Override
            protected void afterMethod(MethodHookParam param) {

                int id = (int) param.args[0];

                if (id == 8353847) {
                    final Object thisClass = ReflectHelper.getObjectField(param.thisObject, Obfuscate.getFieldName("ProfileActivity", "this$0"));
                    ProfileActivity profile = new ProfileActivity(thisClass);

                    AlertDialog alertDialog = new AlertDialog(Utils.getCurrentActivity());
                    alertDialog.setTitle(Translator.get(Keys.TeleVip));
                    alertDialog.setMessage("\n" +
                            Translator.get(Keys.ApproximateCreationDate) + " : " +
                                    IdDateEstimator.getYearAndMethod(getUserID(profile)) + "\n\n" +
                                    Translator.get(Keys.Age) + " : " +
                                    IdDateEstimator.getAge(getUserID(profile)) + "\n\n" +
                                    Translator.get(Keys.ApproximateCreationDateNotice)
                    );
                    alertDialog.setPositiveButton(Translator.get(Keys.Done), null);
                    alertDialog.show();
                }
            }
        });
    }

    private static long getUserID(ProfileActivity profile) {
        if (profile.getUserId() > 1) {
            return profile.getUserId();
        }
        return 0;
    }

}