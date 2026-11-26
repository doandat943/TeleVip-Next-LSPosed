package com.magiii.televipnext.features.otherFeatures;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.Toast;

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
import com.magiii.televipnext.ui.ThemeColors;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.SimpleTextView;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;

import com.magiii.televipnext.xposed.ReflectHelper;

public class EditOnlineTextView {
    public static boolean isEnable = false;

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
                if (!"updateOnlineTextView".equals(updateOnline)
                        && ReflectHelper.findMethodExactIfExists(profileClass, updateOnline) != null) {
                    HMethod.hookMethod(profileClass, updateOnline, new BaseMethodHook() {
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
        if (profileObj == null) return;
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

        if (ConfigManager.showUserID != null && ConfigManager.showUserID.isEnable()) {
            Object idTarget = legacyArray ? onlineTextViewArray[3]
                    : (onlineTextViewArray.length > 1 ? onlineTextViewArray[1] : onlineTextViewArray[0]);
            if (idTarget == null) return;

            SimpleTextView simpleTextView2 = new SimpleTextView(idTarget);
            if (simpleTextView2.getSimpleTextView() == null) return;

            long entityId = getID(profileActivity);
            if (entityId == 0) return;
            String id = String.valueOf(entityId);
            String idLine = "ID: " + id;

            CharSequence oldText = simpleTextView2.getText();
            if (oldText == null || !oldText.toString().contains(idLine)) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                if (legacyArray) {
                    sb.append("\u200E\u200F");
                    if (oldText != null) sb.append(oldText);
                    sb.append("\n");
                } else if (oldText != null && oldText.length() > 0) {
                    sb.append(oldText).append("\n");
                }

                int start = sb.length();
                sb.append(idLine);

                sb.setSpan(new RelativeSizeSpan(1.0f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (legacyArray) {
                    sb.setSpan(new ForegroundColorSpan(ThemeColors.getTextColor()), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }

                simpleTextView2.setMaxLines(2);
                simpleTextView2.setText(sb, true);
            }

            simpleTextView2.getSimpleTextView().setClickable(true);
            simpleTextView2.getSimpleTextView().setOnClickListener(v -> {
                String name = Translator.get(Keys.Copied, id);
                ((ClipboardManager) Utils.getCurrentActivity().getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("clipboard", id));
                Toast.makeText(Utils.getCurrentActivity(), name, Toast.LENGTH_LONG).show();
            });
        }
    }

    private static long getID(ProfileActivity profile) {
        if (profile.getUserId() > 1) {
            return profile.getUserId();
        } else {
            return profile.getChatId();
        }
    }

}
