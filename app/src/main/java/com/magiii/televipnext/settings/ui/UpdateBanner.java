package com.magiii.televipnext.settings.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.virtuals.ActionBar.Theme;

/**
 * Floating Liquid Glass notification banner anchored at the top of the foreground Activity.
 * Designed for 1-tap update installation and 1-tap post-update host restart.
 */
public final class UpdateBanner {

    private static final String BANNER_TAG = "televip_update_banner";

    private UpdateBanner() {}

    public static void show(
            Activity activity,
            boolean isRestartMode,
            CharSequence title,
            CharSequence subtitle,
            CharSequence actionLabel,
            Runnable onPrimaryClick,
            Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;

            View existing = root.findViewWithTag(BANNER_TAG);
            if (existing != null) {
                root.removeView(existing);
            }

            final boolean light = Theme.isLight();
            final boolean rtl = Translator.isRtl();
            final GlassDesign.Palette p = GlassDesign.Palette.of(light);
            final Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);

            final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
            final LinearLayout card = new LinearLayout(activity) {
                private float downY;
                private float downX;
                private boolean draggingUp;

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    int action = ev.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        downX = ev.getRawX();
                        downY = ev.getRawY();
                        draggingUp = false;
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float dy = ev.getRawY() - downY;
                        float dx = Math.abs(ev.getRawX() - downX);
                        if (dy < -touchSlop && Math.abs(dy) > dx * 1.2f) {
                            draggingUp = true;
                            return true;
                        }
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    int action = ev.getActionMasked();
                    if (draggingUp) {
                        if (action == MotionEvent.ACTION_MOVE) {
                            float dy = ev.getRawY() - downY;
                            setTranslationY(Math.min(0f, dy));
                            float fade = 1f - Math.min(1f, Math.abs(dy) / dp(activity, 90));
                            setAlpha(Math.max(0.2f, fade));
                            return true;
                        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                            float dy = ev.getRawY() - downY;
                            draggingUp = false;
                            if (dy < -dp(activity, 26)) {
                                animateDismiss(this, root, onDismiss);
                            } else {
                                animate().translationY(0f).alpha(1f).setDuration(140).start();
                            }
                            return true;
                        }
                    }
                    return super.onTouchEvent(ev);
                }
            };

            card.setTag(BANNER_TAG);
            card.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 10), dp(activity, 10));
            card.setBackground(GlassDesign.createGlassDialogDrawable(p, dp(activity, 20)));
            card.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(activity, 20)));
            card.setClipToOutline(true);
            card.setElevation(dp(activity, 12));
            card.setClickable(true);
            card.setFocusable(true);
            GlassDesign.attachPressScale(card);

            int badgeColor = isRestartMode ? Color.rgb(48, 209, 88) : p.accentBlue;
            int iconType = isRestartMode ? GlassDesign.ICON_RESTART : GlassDesign.ICON_UPDATE;
            ImageView iconIv = new ImageView(activity);
            iconIv.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(badgeColor, dp(activity, 11), iconType, Color.WHITE));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
            if (rtl) {
                iconLp.leftMargin = dp(activity, 10);
            } else {
                iconLp.rightMargin = dp(activity, 10);
            }
            card.addView(iconIv, iconLp);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

            TextView titleTv = new TextView(activity);
            titleTv.setText(title);
            titleTv.setTextColor(p.textPrimary);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            titleTv.setTypeface(mediumTf);
            titleTv.setSingleLine(true);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            titleTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            textCol.addView(titleTv);

            if (!TextUtils.isEmpty(subtitle)) {
                TextView subTv = new TextView(activity);
                subTv.setText(subtitle);
                subTv.setTextColor(p.textSecondary);
                subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                subTv.setMaxLines(2);
                subTv.setEllipsize(TextUtils.TruncateAt.END);
                subTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
                subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                subLp.topMargin = dp(activity, 1.5f);
                textCol.addView(subTv, subLp);
            }

            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (rtl) {
                textLp.leftMargin = dp(activity, 8);
            } else {
                textLp.rightMargin = dp(activity, 8);
            }
            card.addView(textCol, textLp);

            Runnable triggerAction = () -> {
                animateDismiss(card, root, null);
                if (onPrimaryClick != null) {
                    onPrimaryClick.run();
                }
            };

            TextView ctaBtn = new TextView(activity);
            ctaBtn.setText(actionLabel);
            ctaBtn.setTextColor(Color.WHITE);
            ctaBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            ctaBtn.setTypeface(mediumTf);
            ctaBtn.setSingleLine(true);
            ctaBtn.setGravity(Gravity.CENTER);
            ctaBtn.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
            GradientDrawable ctaBg = new GradientDrawable();
            ctaBg.setColor(badgeColor);
            ctaBg.setCornerRadius(dp(activity, 14));
            GradientDrawable ctaMask = new GradientDrawable();
            ctaMask.setColor(Color.WHITE);
            ctaMask.setCornerRadius(dp(activity, 14));
            ctaBtn.setBackground(ctaBg);
            ctaBtn.setForeground(new RippleDrawable(ColorStateList.valueOf(Color.argb(48, 255, 255, 255)), null, ctaMask));
            GlassDesign.attachPressScale(ctaBtn);
            ctaBtn.setOnClickListener(v -> triggerAction.run());

            LinearLayout.LayoutParams ctaLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 32));
            if (rtl) {
                ctaLp.leftMargin = dp(activity, 6);
            } else {
                ctaLp.rightMargin = dp(activity, 6);
            }
            card.addView(ctaBtn, ctaLp);

            ImageView closeBtn = GlassDesign.createCloseCircleButton(
                    activity, p, () -> animateDismiss(card, root, onDismiss));
            int closeSize = dp(activity, 28);
            int closePad = dp(activity, 8);
            closeBtn.setPadding(closePad, closePad, closePad, closePad);
            card.addView(closeBtn, new LinearLayout.LayoutParams(closeSize, closeSize));

            card.setOnClickListener(v -> triggerAction.run());

            int statusH = getStatusBarHeight(activity);
            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            cardLp.leftMargin = dp(activity, 12);
            cardLp.rightMargin = dp(activity, 12);
            cardLp.topMargin = statusH + dp(activity, 10);

            root.addView(card, cardLp);
            card.bringToFront();

            card.setTranslationY(-dp(activity, 90));
            card.setAlpha(0f);
            card.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .start();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    public static void bringToFrontIfShown(Activity activity) {
        if (activity == null) return;
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            View banner = root.findViewWithTag(BANNER_TAG);
            if (banner != null) {
                banner.bringToFront();
            }
        } catch (Throwable ignored) {}
    }

    private static void animateDismiss(View banner, ViewGroup root, Runnable onDismiss) {
        if (banner == null || banner.getParent() != root) return;
        banner.animate().withEndAction(null).cancel();
        banner.animate()
                .translationY(-dp(banner.getContext(), 90))
                .alpha(0f)
                .setDuration(170)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    if (banner.getParent() == root) {
                        root.removeView(banner);
                    }
                    if (onDismiss != null) {
                        onDismiss.run();
                    }
                })
                .start();
    }

    private static int getStatusBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : dp(activity, 24);
    }

    private static int dp(android.content.Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
