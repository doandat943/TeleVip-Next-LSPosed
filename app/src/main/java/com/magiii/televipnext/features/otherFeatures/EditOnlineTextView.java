package com.magiii.televipnext.features.otherFeatures;


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.RelativeSizeSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.SimpleTextView;
import com.magiii.televipnext.virtuals.ui.ProfileActivity;

import com.magiii.televipnext.xposed.ReflectHelper;

public class EditOnlineTextView {
    public static boolean isEnable = false;

    private static final int TAG_COPY_ID = 0x54564944;
    private static final int TAG_TOUCH_BOUND = 0x54564945;
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

            if (ConfigManager.showUserID != null && ConfigManager.showUserID.isEnable()) {
                Object idTarget = legacyArray
                        ? (onlineTextViewArray.length > 3 && onlineTextViewArray[3] != null ? onlineTextViewArray[3]
                            : (onlineTextViewArray[1] != null ? onlineTextViewArray[1] : onlineTextViewArray[0]))
                        : (onlineTextViewArray.length > 1 && onlineTextViewArray[1] != null
                            ? onlineTextViewArray[1] : onlineTextViewArray[0]);
                if (idTarget == null) return;

                SimpleTextView simpleTextView2 = new SimpleTextView(idTarget);
                View view = simpleTextView2.getSimpleTextView();
                if (view == null) return;

                long entityId = getID(profileActivity);
                if (entityId == 0) return;
                String id = String.valueOf(entityId);
                String idLine = "ID: " + id;

                CharSequence oldText = simpleTextView2.getText();
                if (oldText == null || !oldText.toString().contains(idLine)) {
                    SpannableStringBuilder sb = buildSingleLineText(oldText, idLine, simpleTextView2, view);
                    simpleTextView2.setText(sb, true);
                    profileActivity.needLayout(false);
                }

                bindTapToCopy(view, id);
            }
        } finally {
            IN_APPLY.remove();
        }
    }

    private static SpannableStringBuilder buildSingleLineText(
            CharSequence oldText, String idLine, SimpleTextView stv, View view) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int idStart;
        if (oldText == null || oldText.length() == 0) {
            idStart = 0;
            sb.append(idLine);
        } else {
            float density = view.getContext().getResources().getDisplayMetrics().density;
            int widthPx = view.getContext().getResources().getDisplayMetrics().widthPixels;
            int availableWidth = density > 0f && widthPx > 0
                    ? widthPx - (int) (48f * density)
                    : 900;
            String combined = oldText + " \u00B7 " + idLine;
            TextPaint paint = stv.getTextPaint();
            float measured = paint != null
                    ? paint.measureText(combined)
                    : combined.length() * 8f * (density > 0f ? density : 2.5f);
            if (measured <= availableWidth) {
                sb.append(oldText).append(" \u00B7 ");
                idStart = sb.length();
                sb.append(idLine);
            } else {
                idStart = 0;
                sb.append(idLine).append(" \u00B7 ").append(oldText);
            }
        }
        sb.setSpan(new RelativeSizeSpan(1.0f), idStart, idStart + idLine.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static void bindTapToCopy(View view, String id) {
        view.setTag(TAG_COPY_ID, id);
        if (view.getTag(TAG_TOUCH_BOUND) != null) return;
        view.setTag(TAG_TOUCH_BOUND, Boolean.TRUE);

        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        final float[] downPos = new float[2];
        final long[] downTime = new long[1];
        final boolean[] moved = new boolean[1];

        // Attach via OnTouchListener without calling setClickable(true) so ClickableSmallTextView does not draw its pill box.
        view.setOnTouchListener((v, event) -> {
            if (v.isClickable()) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downPos[0] = event.getX();
                downPos[1] = event.getY();
                downTime[0] = event.getEventTime();
                moved[0] = false;
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (Math.hypot(event.getX() - downPos[0], event.getY() - downPos[1]) > touchSlop) {
                    moved[0] = true;
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                moved[0] = true;
            } else if (action == MotionEvent.ACTION_UP) {
                if (!moved[0] && (event.getEventTime() - downTime[0]) < 500L) {
                    Object tag = v.getTag(TAG_COPY_ID);
                    if (tag instanceof String) {
                        String copyVal = (String) tag;
                        Context act = Utils.getCurrentActivity();
                        Context ctx = act != null ? act : v.getContext();
                        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("clipboard", copyVal));
                        }
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        Toast.makeText(ctx, Translator.get(Keys.Copied, copyVal), Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
            return true;
        });
    }

    private static long getID(ProfileActivity profile) {
        if (profile.getUserId() > 1) {
            return profile.getUserId();
        } else {
            return profile.getChatId();
        }
    }

}
