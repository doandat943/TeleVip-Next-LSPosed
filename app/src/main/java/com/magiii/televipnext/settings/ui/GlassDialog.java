package com.magiii.televipnext.settings.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;

/**
 * Self-contained Liquid Glass modal dialog and single-choice picker that renders without
 * relying on Telegram's ActionBar.AlertDialog while syncing its palette with {@link Theme}.
 */
public final class GlassDialog {

    public interface OnOptionSelected {
        void onSelected(int index);
    }

    private GlassDialog() {}

    public static EditText createGlassEditText(Context ctx, String hint, int inputType) {
        GlassDesign.Palette p = GlassDesign.Palette.of(Theme.isLight());
        boolean rtl = Translator.isRtl();

        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextColor(p.textPrimary);
        et.setHintTextColor(p.textSecondary);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        et.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        et.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        et.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        et.setBackground(GlassDesign.createGlassPillDrawable(p, dp(ctx, 14)));
        return et;
    }

    public static Dialog showSingleChoice(
            Context ctx,
            CharSequence title,
            CharSequence[] options,
            int selectedIndex,
            OnOptionSelected onSelect) {

        Activity activity = resolveActivity(ctx);
        if (activity == null || options == null) return null;

        try {
            boolean light = Theme.isLight();
            boolean rtl = Translator.isRtl();
            GlassDesign.Palette p = GlassDesign.Palette.of(light);
            Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);

            Dialog dialog = createBaseDialog(activity, false);
            int maxCardH = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.78f);

            FrameLayout scrim = createScrim(activity, true, dialog);
            LinearLayout card = createCardContainer(activity, p, maxCardH);

            // Header: [Title] [Close circle] (keep close button on top-right edge)
            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 16), dp(activity, 14));

            TextView titleTv = new TextView(activity);
            titleTv.setText(title);
            titleTv.setTextColor(p.textPrimary);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f);
            titleTv.setTypeface(mediumTf);
            titleTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            titleTv.setMaxLines(2);
            titleTv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleLp.rightMargin = dp(activity, 10);
            header.addView(titleTv, titleLp);

            ImageView closeBtn = GlassDesign.createCloseCircleButton(activity, p, dialog::dismiss);
            header.addView(closeBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
            card.addView(header);

            card.addView(createDivider(activity, p, 0));

            // Scrollable options list
            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

            LinearLayout list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < options.length; i++) {
                if (i > 0) {
                    list.addView(createDivider(activity, p, dp(activity, 16)));
                }
                final int idx = i;
                boolean isSelected = (i == selectedIndex);

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(activity, 50));
                row.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
                row.setBackground(GlassDesign.createRowRipple(p.ripple));

                TextView optTv = new TextView(activity);
                optTv.setText(options[i]);
                optTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                optTv.setTextColor(isSelected ? p.accentBlue : p.textPrimary);
                if (isSelected) {
                    optTv.setTypeface(mediumTf);
                }
                optTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
                optTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

                LinearLayout.LayoutParams optLp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

                if (isSelected) {
                    ImageView checkIv = new ImageView(activity);
                    checkIv.setImageDrawable(GlassDesign.GlassIcons.checkIcon(p.accentBlue));
                    LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(dp(activity, 20), dp(activity, 20));
                    if (rtl) {
                        checkLp.rightMargin = dp(activity, 12);
                        row.addView(checkIv, checkLp);
                        row.addView(optTv, optLp);
                    } else {
                        optLp.rightMargin = dp(activity, 12);
                        row.addView(optTv, optLp);
                        row.addView(checkIv, checkLp);
                    }
                } else {
                    row.addView(optTv, optLp);
                }

                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onSelect != null) {
                        onSelect.onSelected(idx);
                    }
                });
                list.addView(row);
            }

            scroll.addView(list, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            card.addView(createDivider(activity, p, 0));

            // Bottom Cancel pill
            LinearLayout footer = new LinearLayout(activity);
            footer.setOrientation(LinearLayout.VERTICAL);
            footer.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 16));

            TextView cancelBtn = createActionPill(
                    activity, p, mediumTf, Translator.get(Keys.Cancel), false, dp(activity, 42));
            cancelBtn.setOnClickListener(v -> dialog.dismiss());
            footer.addView(cancelBtn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 42)));
            card.addView(footer);

            attachAndShow(dialog, scrim, card, false, null);
            return dialog;
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    public static final class Builder {
        private final Context context;
        private boolean showBrandEmblem = false;
        private CharSequence title;
        private CharSequence message;
        private View customView;
        private CharSequence positiveText;
        private Runnable positiveAction;
        private CharSequence negativeText;
        private Runnable negativeAction;
        private CharSequence neutralText;
        private Runnable neutralAction;
        private boolean cancelable = true;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setShowBrandEmblem(boolean showBrandEmblem) {
            this.showBrandEmblem = showBrandEmblem;
            return this;
        }

        public Builder setTitle(CharSequence title) {
            this.title = title;
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public Builder setView(View customView) {
            this.customView = customView;
            return this;
        }

        public Builder setPositiveButton(CharSequence text, Runnable onClick) {
            this.positiveText = text;
            this.positiveAction = onClick;
            return this;
        }

        public Builder setNegativeButton(CharSequence text, Runnable onClick) {
            this.negativeText = text;
            this.negativeAction = onClick;
            return this;
        }

        public Builder setNeutralButton(CharSequence text, Runnable onClick) {
            this.neutralText = text;
            this.neutralAction = onClick;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Dialog show() {
            Activity activity = resolveActivity(context);
            if (activity == null) return null;

            try {
                boolean light = Theme.isLight();
                boolean rtl = Translator.isRtl();
                GlassDesign.Palette p = GlassDesign.Palette.of(light);
                Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);

                boolean hasInput = customView != null;
                Dialog dialog = createBaseDialog(activity, hasInput);
                dialog.setCancelable(cancelable);
                dialog.setCanceledOnTouchOutside(cancelable);

                int maxCardH = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
                FrameLayout scrim = createScrim(activity, cancelable, dialog);
                LinearLayout card = createCardContainer(activity, p, maxCardH);
                card.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));

                if (showBrandEmblem) {
                    ImageView emblem = new ImageView(activity);
                    emblem.setImageDrawable(GlassDesign.GlassIcons.brandEmblemDrawable(dp(activity, 13)));
                    emblem.setElevation(dp(activity, 4));
                    LinearLayout.LayoutParams emLp = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44));
                    emLp.gravity = Gravity.CENTER_HORIZONTAL;
                    emLp.bottomMargin = dp(activity, 12);
                    card.addView(emblem, emLp);
                }

                if (!TextUtils.isEmpty(title)) {
                    TextView titleTv = new TextView(activity);
                    titleTv.setText(title);
                    titleTv.setTextColor(p.textPrimary);
                    titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                    titleTv.setTypeface(mediumTf);
                    titleTv.setGravity(showBrandEmblem ? Gravity.CENTER_HORIZONTAL : (rtl ? Gravity.RIGHT : Gravity.LEFT));
                    titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                    LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tLp.bottomMargin = (!TextUtils.isEmpty(message) || customView != null) ? dp(activity, 10) : dp(activity, 16);
                    card.addView(titleTv, tLp);
                }

                if (!TextUtils.isEmpty(message)) {
                    ScrollView msgScroll = new ScrollView(activity);
                    msgScroll.setVerticalScrollBarEnabled(false);
                    msgScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

                    TextView msgTv = new TextView(activity);
                    msgTv.setText(message);
                    msgTv.setTextColor(p.textSecondary);
                    msgTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
                    msgTv.setLineSpacing(dp(activity, 3), 1.0f);
                    msgTv.setGravity(showBrandEmblem ? Gravity.CENTER_HORIZONTAL : (rtl ? Gravity.RIGHT : Gravity.LEFT));
                    msgTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                    msgScroll.addView(msgTv, new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                    LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                    mLp.bottomMargin = dp(activity, 16);
                    card.addView(msgScroll, mLp);
                }

                if (customView != null) {
                    if (customView.getParent() instanceof ViewGroup) {
                        ((ViewGroup) customView.getParent()).removeView(customView);
                    }
                    FrameLayout customHolder = new FrameLayout(activity);
                    customHolder.addView(customView, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cLp.bottomMargin = dp(activity, 16);
                    card.addView(customHolder, cLp);
                }

                boolean hasPos = !TextUtils.isEmpty(positiveText);
                boolean hasNeg = !TextUtils.isEmpty(negativeText);
                if (hasPos || hasNeg) {
                    LinearLayout btnRow = new LinearLayout(activity);
                    btnRow.setOrientation(LinearLayout.HORIZONTAL);
                    btnRow.setGravity(Gravity.CENTER_VERTICAL);

                    if (hasPos && hasNeg) {
                        TextView posBtn = createActionPill(activity, p, mediumTf, positiveText, true, dp(activity, 44));
                        posBtn.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (positiveAction != null) positiveAction.run();
                        });

                        TextView negBtn = createActionPill(activity, p, mediumTf, negativeText, false, dp(activity, 44));
                        negBtn.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (negativeAction != null) negativeAction.run();
                        });

                        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);
                        firstLp.rightMargin = dp(activity, 10);
                        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(0, dp(activity, 44), 1f);

                        if (rtl) {
                            btnRow.addView(posBtn, firstLp);
                            btnRow.addView(negBtn, secondLp);
                        } else {
                            btnRow.addView(negBtn, firstLp);
                            btnRow.addView(posBtn, secondLp);
                        }
                    } else if (hasPos) {
                        TextView posBtn = createActionPill(activity, p, mediumTf, positiveText, true, dp(activity, 44));
                        posBtn.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (positiveAction != null) positiveAction.run();
                        });
                        btnRow.addView(posBtn, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
                    } else {
                        TextView negBtn = createActionPill(activity, p, mediumTf, negativeText, false, dp(activity, 44));
                        negBtn.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (negativeAction != null) negativeAction.run();
                        });
                        btnRow.addView(negBtn, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)));
                    }

                    card.addView(btnRow, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }

                if (!TextUtils.isEmpty(neutralText)) {
                    TextView neutralBtn = new TextView(activity);
                    neutralBtn.setText(neutralText);
                    neutralBtn.setTextColor(p.textSecondary);
                    neutralBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                    neutralBtn.setTypeface(mediumTf);
                    neutralBtn.setGravity(Gravity.CENTER);
                    neutralBtn.setSingleLine(true);
                    neutralBtn.setEllipsize(TextUtils.TruncateAt.END);
                    neutralBtn.setBackground(GlassDesign.createPillRippleOverlay(p.ripple, dp(activity, 12)));
                    GlassDesign.attachPressScale(neutralBtn);
                    neutralBtn.setOnClickListener(v -> {
                        dialog.dismiss();
                        if (neutralAction != null) neutralAction.run();
                    });
                    LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 38));
                    nLp.topMargin = dp(activity, 8);
                    card.addView(neutralBtn, nLp);
                }

                attachAndShow(dialog, scrim, card, hasInput, customView instanceof EditText ? customView : null);
                return dialog;
            } catch (Throwable t) {
                Logger.e(t);
                return null;
            }
        }
    }

    private static Activity resolveActivity(Context ctx) {
        return ctx instanceof Activity ? (Activity) ctx : Utils.getCurrentActivity();
    }

    private static Dialog createBaseDialog(Activity activity, boolean forInput) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window win = dialog.getWindow();
        if (win != null && forInput) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        return dialog;
    }

    private static FrameLayout createScrim(Activity activity, boolean cancelable, Dialog dialog) {
        FrameLayout scrim = new FrameLayout(activity);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setBackgroundColor(Color.TRANSPARENT);
        if (cancelable) {
            scrim.setOnClickListener(v -> dialog.dismiss());
        }
        return scrim;
    }

    private static LinearLayout createCardContainer(Activity activity, GlassDesign.Palette p, int maxCardH) {
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int cardW = Math.min(screenW - dp(activity, 40), dp(activity, 360));

        LinearLayout card = new LinearLayout(activity) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int capped = (mode == MeasureSpec.UNSPECIFIED) ? maxCardH : Math.min(size, maxCardH);
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST));
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        card.setBackground(GlassDesign.createGlassDialogDrawable(p, dp(activity, 24)));
        card.setClipToOutline(true);
        card.setElevation(dp(activity, 12));
        return card;
    }

    private static void attachAndShow(
            Dialog dialog, FrameLayout scrim, LinearLayout card, boolean forInput, View focusTarget) {
        if (forInput) {
            scrim.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeBottom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        ? insets.getInsets(WindowInsets.Type.ime()).bottom
                        : insets.getSystemWindowInsetBottom();
                v.setPadding(0, 0, 0, imeBottom);
                return insets;
            });
        }
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        scrim.addView(card, cardLp);

        dialog.setContentView(scrim);
        boolean light = Theme.isLight();
        GlassDesign.configureEdgeToEdgeSheetDialog(dialog, light);
        Window win = dialog.getWindow();
        if (win != null) {
            win.setDimAmount(light ? 0.36f : 0.62f);
        }

        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.setAlpha(0f);
        dialog.show();
        GlassDesign.applySheetSystemBarAppearance(dialog, light);
        card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(2.0f))
                .start();

        if (focusTarget != null) {
            focusTarget.requestFocus();
            focusTarget.post(() -> {
                InputMethodManager imm = (InputMethodManager) dialog.getContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(focusTarget, InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }
    }

    private static TextView createActionPill(
            Context ctx, GlassDesign.Palette p, Typeface mediumTf, CharSequence label, boolean primary, int heightPx) {
        TextView btn = new TextView(ctx);
        btn.setText(label);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        btn.setTypeface(mediumTf);
        btn.setGravity(Gravity.CENTER);
        btn.setSingleLine(true);
        btn.setEllipsize(TextUtils.TruncateAt.END);
        btn.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        btn.setMinHeight(heightPx);

        if (primary) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(p.accentBlue);
            bg.setCornerRadius(dp(ctx, 14));
            btn.setBackground(bg);
            btn.setForeground(GlassDesign.createPillRippleOverlay(Color.argb(46, 255, 255, 255), dp(ctx, 14)));
            btn.setTextColor(Color.WHITE);
        } else {
            btn.setBackground(GlassDesign.createGlassPillDrawable(p, dp(ctx, 14)));
            btn.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(ctx, 14)));
            btn.setTextColor(p.textPrimary);
        }
        GlassDesign.attachPressScale(btn);
        return btn;
    }

    private static View createDivider(Context ctx, GlassDesign.Palette p, int horizontalMarginPx) {
        View div = new View(ctx);
        div.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(0.7f * ctx.getResources().getDisplayMetrics().density)));
        lp.leftMargin = horizontalMarginPx;
        lp.rightMargin = horizontalMarginPx;
        div.setLayoutParams(lp);
        return div;
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
