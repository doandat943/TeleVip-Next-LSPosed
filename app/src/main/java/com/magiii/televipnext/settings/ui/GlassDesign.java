package com.magiii.televipnext.settings.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Self-contained Liquid Glass styling primitives, programmatic icons, segmented tab bar,
 * and iOS-inspired pill switch drawn directly on Canvas so it works cleanly inside any
 * hooked Telegram client Context without relying on module R.drawable resources.
 */
public final class GlassDesign {

    public static final int ICON_GHOST = 1;
    public static final int ICON_MESSAGES = 2;
    public static final int ICON_MEDIA = 3;
    public static final int ICON_CONNECTIONS = 4;
    public static final int ICON_UI = 5;
    public static final int ICON_STORIES = 6;
    public static final int ICON_SPARKLES = 7;
    public static final int ICON_TELEGRAM = 8;
    public static final int ICON_RESTART = 9;
    public static final int ICON_CODE = 10;
    public static final int ICON_UPDATE = 11;

    private GlassDesign() {}

    public static final class Palette {
        public final boolean light;
        public final int baseBg;
        public final int glowTopLeft;
        public final int glowMidRight;
        public final int glowBottomLeft;
        public final int scrimTop;
        public final int scrimMid;

        public final int cardFillTop;
        public final int cardFillBottom;
        public final int cardBorderTop;
        public final int cardBorderBottom;

        public final int pillFillTop;
        public final int pillFillBottom;
        public final int pillBorderTop;
        public final int pillBorderBottom;
        public final int tabThumbFill;
        public final int tabThumbBorder;

        public final int textPrimary;
        public final int textSecondary;
        public final int textMuted;
        public final int accentBlue;
        public final int accentSoft;
        public final int divider;
        public final int ripple;

        public final int switchOnStart;
        public final int switchOnEnd;
        public final int switchOff;

        private Palette(boolean light) {
            this.light = light;
            if (light) {
                baseBg = Color.rgb(238, 243, 250);
                glowTopLeft = Color.argb(175, 198, 229, 255);
                glowMidRight = Color.argb(165, 221, 214, 252);
                glowBottomLeft = Color.argb(150, 206, 240, 246);
                scrimTop = Color.argb(235, 238, 243, 250);
                scrimMid = Color.argb(170, 238, 243, 250);

                cardFillTop = Color.argb(232, 255, 255, 255);
                cardFillBottom = Color.argb(214, 248, 251, 255);
                cardBorderTop = Color.argb(255, 255, 255, 255);
                cardBorderBottom = Color.argb(30, 15, 30, 60);

                pillFillTop = Color.argb(210, 255, 255, 255);
                pillFillBottom = Color.argb(175, 238, 243, 250);
                pillBorderTop = Color.argb(255, 255, 255, 255);
                pillBorderBottom = Color.argb(32, 15, 30, 60);
                tabThumbFill = Color.argb(245, 255, 255, 255);
                tabThumbBorder = Color.argb(26, 0, 20, 50);

                textPrimary = Color.rgb(18, 24, 34);
                textSecondary = Color.rgb(100, 112, 128);
                textMuted = Color.rgb(130, 140, 155);
                accentBlue = Color.rgb(28, 128, 232);
                accentSoft = Color.argb(26, 28, 128, 232);
                divider = Color.argb(18, 15, 25, 45);
                ripple = Color.argb(20, 0, 0, 0);

                switchOnStart = Color.rgb(42, 148, 245);
                switchOnEnd = Color.rgb(74, 174, 255);
                switchOff = Color.argb(56, 120, 130, 145);
            } else {
                baseBg = Color.rgb(14, 21, 31);
                glowTopLeft = Color.argb(165, 21, 56, 74);
                glowMidRight = Color.argb(150, 38, 32, 78);
                glowBottomLeft = Color.argb(145, 16, 52, 60);
                scrimTop = Color.argb(236, 15, 23, 34);
                scrimMid = Color.argb(175, 15, 23, 34);

                cardFillTop = Color.argb(192, 27, 36, 48);
                cardFillBottom = Color.argb(172, 21, 29, 39);
                cardBorderTop = Color.argb(56, 255, 255, 255);
                cardBorderBottom = Color.argb(18, 255, 255, 255);

                pillFillTop = Color.argb(172, 46, 56, 70);
                pillFillBottom = Color.argb(142, 34, 43, 55);
                pillBorderTop = Color.argb(72, 255, 255, 255);
                pillBorderBottom = Color.argb(22, 255, 255, 255);
                tabThumbFill = Color.argb(155, 84, 96, 112);
                tabThumbBorder = Color.argb(74, 255, 255, 255);

                textPrimary = Color.rgb(242, 246, 252);
                textSecondary = Color.rgb(154, 167, 184);
                textMuted = Color.rgb(122, 135, 152);
                accentBlue = Color.rgb(88, 178, 255);
                accentSoft = Color.argb(36, 88, 178, 255);
                divider = Color.argb(22, 255, 255, 255);
                ripple = Color.argb(28, 255, 255, 255);

                switchOnStart = Color.rgb(54, 156, 246);
                switchOnEnd = Color.rgb(92, 184, 255);
                switchOff = Color.argb(66, 160, 175, 195);
            }
        }

        public static Palette of(boolean light) {
            return new Palette(light);
        }
    }

    public static Drawable createAuroraBackground(Palette p) {
        return new AuroraDrawable(p);
    }

    public static Drawable createTopScrimDrawable(Palette p) {
        GradientDrawable scrim = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{p.scrimTop, p.scrimMid, Color.TRANSPARENT}
        );
        return scrim;
    }

    public static Drawable createGlassCardDrawable(Palette p, float radiusPx) {
        return new SpecularGlassDrawable(
                p.cardFillTop, p.cardFillBottom,
                p.cardBorderTop, p.cardBorderBottom,
                radiusPx, Math.max(1.5f, radiusPx * 0.048f)
        );
    }

    public static Drawable createGlassDialogDrawable(Palette p, float radiusPx) {
        int fillTop = p.light ? Color.argb(252, 252, 253, 255) : Color.argb(252, 26, 35, 48);
        int fillBottom = p.light ? Color.argb(250, 241, 246, 252) : Color.argb(250, 20, 28, 39);
        return new SpecularGlassDrawable(
                fillTop, fillBottom,
                p.cardBorderTop, p.cardBorderBottom,
                radiusPx, Math.max(1.5f, radiusPx * 0.048f)
        );
    }

    public static Drawable createGlassPillDrawable(Palette p, float radiusPx) {
        return new SpecularGlassDrawable(
                p.pillFillTop, p.pillFillBottom,
                p.pillBorderTop, p.pillBorderBottom,
                radiusPx, Math.max(1.5f, radiusPx * 0.055f)
        );
    }

    public static Drawable createPillRippleOverlay(int rippleColor, float radiusPx) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    public static RippleDrawable createRowRipple(int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    /**
     * Attaches a subtle tactile press-scale spring animation to a pill or circle button.
     */
    public static void attachPressScale(View view) {
        view.setOnTouchListener((v, ev) -> {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            }
            return false;
        });
    }

    /**
     * Creates the standardized 32dp floating-glass circular close button with subtle icon padding.
     */
    public static ImageView createCloseCircleButton(Context context, Palette p, Runnable onClick) {
        ImageView closeBtn = new ImageView(context);
        closeBtn.setImageDrawable(GlassIcons.closeIcon(p.textSecondary));
        closeBtn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int closePad = dp(context, 9);
        closeBtn.setPadding(closePad, closePad, closePad, closePad);
        closeBtn.setBackground(createGlassPillDrawable(p, dp(context, 16)));
        closeBtn.setForeground(createPillRippleOverlay(p.ripple, dp(context, 16)));
        attachPressScale(closeBtn);
        if (onClick != null) {
            closeBtn.setOnClickListener(v -> onClick.run());
        }
        return closeBtn;
    }

    /**
     * Configures a bottom-sheet Dialog window for edge-to-edge rendering with transparent system bars.
     */
    public static void configureEdgeToEdgeSheetDialog(Dialog dialog, boolean light) {
        Window win = dialog.getWindow();
        if (win == null) {
            return;
        }
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        win.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        win.setDimAmount(light ? 0.26f : 0.48f);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            win.setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (light && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            win.getDecorView().setSystemUiVisibility(flags);
        }
    }

    /**
     * Updates navigation bar icon contrast on API 30+ once the sheet Dialog window is shown.
     */
    public static void applySheetSystemBarAppearance(Dialog dialog, boolean light) {
        Window win = dialog.getWindow();
        if (win != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController ic = win.getInsetsController();
            if (ic != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ic.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        }
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * Ambient mesh/aurora backdrop with soft radial glows behind the glass cards.
     */
    private static final class AuroraDrawable extends Drawable {
        private final Palette p;
        private final Paint basePaint = new Paint();
        private final Paint glow1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow3 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int cachedW = -1;
        private int cachedH = -1;

        AuroraDrawable(Palette p) {
            this.p = p;
            basePaint.setColor(p.baseBg);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            int w = bounds.width();
            int h = bounds.height();
            if (w <= 0 || h <= 0 || (w == cachedW && h == cachedH)) return;
            cachedW = w;
            cachedH = h;

            float r1 = Math.max(w, h) * 0.65f;
            glow1.setShader(new RadialGradient(
                    w * 0.14f, h * 0.12f, r1,
                    new int[]{p.glowTopLeft, Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));

            float r2 = Math.max(w, h) * 0.62f;
            glow2.setShader(new RadialGradient(
                    w * 0.92f, h * 0.42f, r2,
                    new int[]{p.glowMidRight, Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));

            float r3 = Math.max(w, h) * 0.68f;
            glow3.setShader(new RadialGradient(
                    w * 0.18f, h * 0.88f, r3,
                    new int[]{p.glowBottomLeft, Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            canvas.drawRect(b, basePaint);
            if (cachedW > 0 && cachedH > 0) {
                canvas.save();
                canvas.translate(b.left, b.top);
                canvas.drawRect(0, 0, cachedW, cachedH, glow1);
                canvas.drawRect(0, 0, cachedW, cachedH, glow2);
                canvas.drawRect(0, 0, cachedW, cachedH, glow3);
                canvas.restore();
            }
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /**
     * Rounded glass surface with vertical translucent gradient fill and top-lit specular border.
     */
    private static final class SpecularGlassDrawable extends Drawable {
        private final int fillTop;
        private final int fillBottom;
        private final int borderTop;
        private final int borderBottom;
        private final float radius;
        private final float strokeWidth;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private int cachedH = -1;

        SpecularGlassDrawable(int fillTop, int fillBottom, int borderTop, int borderBottom,
                              float radius, float strokeWidth) {
            this.fillTop = fillTop;
            this.fillBottom = fillBottom;
            this.borderTop = borderTop;
            this.borderBottom = borderBottom;
            this.radius = radius;
            this.strokeWidth = strokeWidth;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            int h = bounds.height();
            if (h <= 0 || h == cachedH) return;
            cachedH = h;
            fillPaint.setShader(new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    fillTop, fillBottom, Shader.TileMode.CLAMP));
            strokePaint.setShader(new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    borderTop, borderBottom, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float half = strokeWidth * 0.5f;
            rect.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
            float r = Math.min(radius, Math.min(rect.width(), rect.height()) * 0.5f);
            canvas.drawRoundRect(rect, r, r, fillPaint);
            canvas.drawRoundRect(rect, r, r, strokePaint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * Segmented floating glass tab bar with smoothly animated sliding active thumb.
     */
    public static final class SegmentedTabBar extends FrameLayout {

        public interface OnTabSelectedListener {
            void onTabSelected(int index);
        }

        private final View thumbView;
        private final TextView[] labels;
        private final Palette palette;
        private final ArgbEvaluator labelColorEval = new ArgbEvaluator();
        private int selectedIndex = 0;
        private float currentPos = 0f;
        private ValueAnimator thumbAnim;
        private OnTabSelectedListener listener;

        public SegmentedTabBar(Context ctx, String[] titles, Palette palette, int initialIndex) {
            super(ctx);
            this.palette = palette;
            this.selectedIndex = Math.max(0, Math.min(initialIndex, titles.length - 1));
            this.currentPos = this.selectedIndex;

            float density = ctx.getResources().getDisplayMetrics().density;
            int pad = Math.round(4f * density);
            setPadding(pad, pad, pad, pad);
            setBackground(createGlassPillDrawable(palette, 22f * density));

            thumbView = new View(ctx);
            GradientDrawable thumbBg = new GradientDrawable();
            thumbBg.setColor(palette.tabThumbFill);
            thumbBg.setCornerRadius(18f * density);
            thumbBg.setStroke(Math.max(1, Math.round(1f * density)), palette.tabThumbBorder);
            thumbView.setBackground(thumbBg);
            addView(thumbView, new LayoutParams(0, LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            labels = new TextView[titles.length];
            Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            for (int i = 0; i < titles.length; i++) {
                final int idx = i;
                TextView tv = new TextView(ctx);
                tv.setText(titles[i]);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                tv.setTypeface(medium);
                tv.setTextColor(i == selectedIndex ? palette.textPrimary : palette.textSecondary);
                tv.setOnClickListener(v -> {
                    setSelectedTab(idx, true);
                    if (listener != null) listener.onTabSelected(idx);
                });
                labels[i] = tv;
                row.addView(tv, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            }
        }

        public void setOnTabSelectedListener(OnTabSelectedListener listener) {
            this.listener = listener;
        }

        public void setDragPosition(float pos) {
            if (labels.length == 0) return;
            if (thumbAnim != null) thumbAnim.cancel();
            currentPos = Math.max(0f, Math.min(pos, labels.length - 1));
            selectedIndex = Math.round(currentPos);
            for (int i = 0; i < labels.length; i++) {
                float weight = Math.max(0f, 1f - Math.abs(i - currentPos));
                int color = (int) labelColorEval.evaluate(weight, palette.textSecondary, palette.textPrimary);
                labels[i].setTextColor(color);
            }
            positionThumb();
        }

        public void setSelectedTab(int index, boolean animate) {
            if (index < 0 || index >= labels.length) return;
            this.selectedIndex = index;
            for (int i = 0; i < labels.length; i++) {
                labels[i].setTextColor(i == index ? palette.textPrimary : palette.textSecondary);
            }
            if (thumbAnim != null) thumbAnim.cancel();
            if (!animate || getWidth() <= 0) {
                currentPos = index;
                positionThumb();
                return;
            }
            thumbAnim = ValueAnimator.ofFloat(currentPos, index);
            thumbAnim.setDuration(220);
            thumbAnim.setInterpolator(new DecelerateInterpolator(1.6f));
            thumbAnim.addUpdateListener(a -> {
                currentPos = (float) a.getAnimatedValue();
                positionThumb();
            });
            thumbAnim.start();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            positionThumb();
        }

        private void positionThumb() {
            if (labels.length == 0) return;
            int innerW = getWidth() - getPaddingLeft() - getPaddingRight();
            int innerH = getHeight() - getPaddingTop() - getPaddingBottom();
            if (innerW <= 0 || innerH <= 0) return;

            float tabW = innerW / (float) labels.length;
            LayoutParams lp = (LayoutParams) thumbView.getLayoutParams();
            int targetW = Math.round(tabW);
            if (lp.width != targetW || lp.height != innerH) {
                lp.width = targetW;
                lp.height = innerH;
                thumbView.setLayoutParams(lp);
            }
            thumbView.setTranslationX(currentPos * tabW);
        }
    }

    /**
     * iOS-style pill switch with gradient ON track, translucent OFF track, and soft-shadowed thumb.
     */
    public static final class PillSwitchView extends View {
        private boolean checked;
        private float progress;
        private final Palette palette;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private final ArgbEvaluator colorEval = new ArgbEvaluator();
        private ValueAnimator anim;

        public PillSwitchView(Context ctx, boolean initial, Palette palette) {
            super(ctx);
            this.checked = initial;
            this.progress = initial ? 1f : 0f;
            this.palette = palette;
            this.thumbPaint.setColor(Color.WHITE);
            this.shadowPaint.setColor(Color.argb(42, 0, 0, 0));
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean value, boolean animate) {
            if (this.checked == value) return;
            this.checked = value;
            float target = value ? 1f : 0f;
            if (anim != null) anim.cancel();
            if (!animate) {
                progress = target;
                invalidate();
                return;
            }
            anim = ValueAnimator.ofFloat(progress, target);
            anim.setDuration(180);
            anim.setInterpolator(new DecelerateInterpolator(1.5f));
            anim.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            trackRect.set(0, 0, w, h);
            int midOn = (int) colorEval.evaluate(0.5f, palette.switchOnStart, palette.switchOnEnd);
            int color = (int) colorEval.evaluate(progress, palette.switchOff, midOn);
            trackPaint.setColor(color);
            float r = h / 2f;
            canvas.drawRoundRect(trackRect, r, r, trackPaint);

            float pad = h * 0.11f;
            float thumbR = (h - pad * 2f) / 2f;
            float startX = pad + thumbR;
            float endX = w - pad - thumbR;
            float cx = startX + (endX - startX) * progress;
            float cy = h / 2f;

            canvas.drawCircle(cx, cy + h * 0.03f, thumbR, shadowPaint);
            canvas.drawCircle(cx, cy, thumbR, thumbPaint);
        }
    }

    /**
     * Programmatic vector icons drawn on a 24x24 grid.
     */
    public static final class GlassIcons {

        public static Drawable backArrow(int color) {
            return new VectorDrawable24(color, true, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.1f);
                    c.drawLine(19f, 12f, 5f, 12f, stroke);
                    Path p = new Path();
                    p.moveTo(11.5f, 5.5f);
                    p.lineTo(5f, 12f);
                    p.lineTo(11.5f, 18.5f);
                    c.drawPath(p, stroke);
                }
            };
        }

        public static Drawable moonIcon(int color) {
            return new VectorDrawable24(color, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    Path p = new Path();
                    p.moveTo(20.2f, 14.8f);
                    p.cubicTo(19.0f, 15.2f, 17.7f, 15.4f, 16.4f, 15.4f);
                    p.cubicTo(11.5f, 15.4f, 7.6f, 11.5f, 7.6f, 6.6f);
                    p.cubicTo(7.6f, 5.3f, 7.9f, 4.1f, 8.3f, 3.0f);
                    p.cubicTo(5.0f, 4.2f, 2.7f, 7.4f, 2.7f, 11.2f);
                    p.cubicTo(2.7f, 16.0f, 6.6f, 19.9f, 11.4f, 19.9f);
                    p.cubicTo(15.3f, 19.9f, 18.6f, 17.7f, 20.2f, 14.8f);
                    p.close();
                    c.drawPath(p, fill);
                }
            };
        }

        public static Drawable sunIcon(int color) {
            return new VectorDrawable24(color, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.0f);
                    c.drawCircle(12f, 12f, 4.2f, fill);
                    float[][] rays = {
                            {12f, 2.6f, 12f, 5.0f},
                            {12f, 19.0f, 12f, 21.4f},
                            {2.6f, 12f, 5.0f, 12f},
                            {19.0f, 12f, 21.4f, 12f},
                            {5.35f, 5.35f, 7.05f, 7.05f},
                            {16.95f, 16.95f, 18.65f, 18.65f},
                            {18.65f, 5.35f, 16.95f, 7.05f},
                            {7.05f, 16.95f, 5.35f, 18.65f}
                    };
                    for (float[] r : rays) {
                        c.drawLine(r[0], r[1], r[2], r[3], stroke);
                    }
                }
            };
        }

        static Drawable restartIcon(int color) {
            return new VectorDrawable24(color, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.1f);
                    RectF arc = new RectF(4.8f, 4.8f, 19.2f, 19.2f);
                    c.drawArc(arc, -45f, 290f, false, stroke);
                    Path arrow = new Path();
                    arrow.moveTo(15.2f, 3.6f);
                    arrow.lineTo(19.8f, 4.6f);
                    arrow.lineTo(17.8f, 9.0f);
                    arrow.close();
                    c.drawPath(arrow, fill);
                }
            };
        }

        public static Drawable closeIcon(int color) {
            return new VectorDrawable24(color, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.2f);
                    c.drawLine(6.2f, 6.2f, 17.8f, 17.8f, stroke);
                    c.drawLine(17.8f, 6.2f, 6.2f, 17.8f, stroke);
                }
            };
        }

        public static Drawable checkIcon(int color) {
            return new VectorDrawable24(color, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.3f);
                    Path p = new Path();
                    p.moveTo(5.2f, 12.6f);
                    p.lineTo(9.8f, 17.2f);
                    p.lineTo(18.8f, 7.6f);
                    c.drawPath(p, stroke);
                }
            };
        }

        public static Drawable chevronRight(int color) {
            return chevronRight(color, false);
        }

        public static Drawable chevronRight(int color, boolean rtl) {
            return new VectorDrawable24(color, true, rtl) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.2f);
                    Path p = new Path();
                    p.moveTo(9.2f, 6.0f);
                    p.lineTo(15.2f, 12.0f);
                    p.lineTo(9.2f, 18.0f);
                    c.drawPath(p, stroke);
                }
            };
        }

        public static Drawable brandEmblemDrawable(int cornerRadiusPx) {
            GradientDrawable bg = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{Color.rgb(124, 92, 255), Color.rgb(45, 156, 255)}
            );
            bg.setCornerRadius(cornerRadiusPx);

            Drawable fg = new VectorDrawable24(Color.WHITE, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    // Stylized TeleVip Next ghost silhouette with eyes
                    Path ghost = new Path();
                    ghost.moveTo(5.6f, 19.6f);
                    ghost.lineTo(5.6f, 10.6f);
                    ghost.cubicTo(5.6f, 5.9f, 8.6f, 3.6f, 12.0f, 3.6f);
                    ghost.cubicTo(15.4f, 3.6f, 18.4f, 5.9f, 18.4f, 10.6f);
                    ghost.lineTo(18.4f, 19.6f);
                    ghost.cubicTo(17.1f, 18.2f, 15.8f, 18.2f, 14.8f, 19.6f);
                    ghost.cubicTo(13.8f, 18.2f, 12.8f, 18.2f, 12.0f, 19.6f);
                    ghost.cubicTo(11.2f, 18.2f, 10.2f, 18.2f, 9.2f, 19.6f);
                    ghost.cubicTo(8.2f, 18.2f, 6.9f, 18.2f, 5.6f, 19.6f);
                    ghost.close();
                    c.drawPath(ghost, fill);

                    Paint eyeCut = new Paint(Paint.ANTI_ALIAS_FLAG);
                    eyeCut.setColor(Color.rgb(88, 122, 255));
                    eyeCut.setStyle(Paint.Style.FILL);
                    c.drawCircle(9.8f, 10.8f, 1.35f, eyeCut);
                    c.drawCircle(14.2f, 10.8f, 1.35f, eyeCut);
                }
            };

            LayerDrawable layer = new LayerDrawable(new Drawable[]{bg, fg});
            int inset = Math.max(2, Math.round(cornerRadiusPx * 0.42f));
            layer.setLayerInset(1, inset, inset, inset, inset);
            return layer;
        }

        public static Drawable badgeDrawable(int bgColor, int cornerRadiusPx, int iconType, int fgColor) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(bgColor);
            bg.setCornerRadius(cornerRadiusPx);

            Drawable fg = new VectorDrawable24(fgColor, false) {
                @Override
                protected void draw24(Canvas c, Paint fill, Paint stroke) {
                    stroke.setStrokeWidth(2.0f);
                    switch (iconType) {
                        case ICON_GHOST: {
                            // Eye-slash icon matching Ghost Mode purple badge
                            Path eye = new Path();
                            eye.moveTo(2.5f, 12f);
                            eye.quadTo(12f, 4.5f, 21.5f, 12f);
                            eye.quadTo(12f, 19.5f, 2.5f, 12f);
                            c.drawPath(eye, stroke);
                            c.drawCircle(12f, 12f, 2.6f, fill);
                            c.drawLine(4.5f, 4.5f, 19.5f, 19.5f, stroke);
                            break;
                        }
                        case ICON_MESSAGES: {
                            // Speech bubble with two message lines
                            Path bubble = new Path();
                            bubble.moveTo(4f, 6.5f);
                            bubble.quadTo(4f, 4.5f, 6f, 4.5f);
                            bubble.lineTo(18f, 4.5f);
                            bubble.quadTo(20f, 4.5f, 20f, 6.5f);
                            bubble.lineTo(20f, 15.5f);
                            bubble.quadTo(20f, 17.5f, 18f, 17.5f);
                            bubble.lineTo(9.2f, 17.5f);
                            bubble.lineTo(5.2f, 20.8f);
                            bubble.lineTo(5.8f, 17.5f);
                            bubble.quadTo(4f, 17.5f, 4f, 15.5f);
                            bubble.close();
                            c.drawPath(bubble, fill);
                            Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
                            cut.setColor(bgColor);
                            cut.setStyle(Paint.Style.STROKE);
                            cut.setStrokeCap(Paint.Cap.ROUND);
                            cut.setStrokeWidth(1.8f);
                            c.drawLine(8f, 9.5f, 16f, 9.5f, cut);
                            c.drawLine(8f, 13f, 13.5f, 13f, cut);
                            break;
                        }
                        case ICON_MEDIA: {
                            // Photo frame with mountain peak
                            RectF frame = new RectF(4f, 5f, 20f, 19f);
                            c.drawRoundRect(frame, 2.5f, 2.5f, stroke);
                            c.drawCircle(8.5f, 9.3f, 1.4f, fill);
                            Path mtn = new Path();
                            mtn.moveTo(6f, 17f);
                            mtn.lineTo(10.5f, 12f);
                            mtn.lineTo(13.5f, 15f);
                            mtn.lineTo(16f, 12.5f);
                            mtn.lineTo(18.5f, 17f);
                            mtn.close();
                            c.drawPath(mtn, fill);
                            break;
                        }
                        case ICON_CONNECTIONS: {
                            // Speed lightning bolt
                            Path bolt = new Path();
                            bolt.moveTo(13.5f, 3.2f);
                            bolt.lineTo(6.2f, 13.0f);
                            bolt.lineTo(11.6f, 13.0f);
                            bolt.lineTo(10.2f, 20.8f);
                            bolt.lineTo(17.8f, 10.6f);
                            bolt.lineTo(12.3f, 10.6f);
                            bolt.close();
                            c.drawPath(bolt, fill);
                            break;
                        }
                        case ICON_UI: {
                            // Palette / brush circle
                            Path pal = new Path();
                            pal.addCircle(12f, 12f, 7.6f, Path.Direction.CW);
                            c.drawPath(pal, stroke);
                            c.drawCircle(9.2f, 9.5f, 1.3f, fill);
                            c.drawCircle(14.2f, 8.8f, 1.3f, fill);
                            c.drawCircle(15.6f, 13.2f, 1.3f, fill);
                            c.drawCircle(10.4f, 15.0f, 1.6f, fill);
                            break;
                        }
                        case ICON_STORIES: {
                            // Concentric story ring with play triangle
                            c.drawCircle(12f, 12f, 7.6f, stroke);
                            Path play = new Path();
                            play.moveTo(10.2f, 8.5f);
                            play.lineTo(16.2f, 12f);
                            play.lineTo(10.2f, 15.5f);
                            play.close();
                            c.drawPath(play, fill);
                            break;
                        }
                        case ICON_SPARKLES: {
                            // 4-point star + small star matching Other Features orange badge
                            Path s1 = new Path();
                            s1.moveTo(10.5f, 4.0f);
                            s1.quadTo(10.8f, 10.2f, 17.0f, 10.5f);
                            s1.quadTo(10.8f, 10.8f, 10.5f, 17.0f);
                            s1.quadTo(10.2f, 10.8f, 4.0f, 10.5f);
                            s1.quadTo(10.2f, 10.2f, 10.5f, 4.0f);
                            s1.close();
                            c.drawPath(s1, fill);

                            Path s2 = new Path();
                            s2.moveTo(17.5f, 13.5f);
                            s2.quadTo(17.7f, 16.3f, 20.5f, 16.5f);
                            s2.quadTo(17.7f, 16.7f, 17.5f, 19.5f);
                            s2.quadTo(17.3f, 16.7f, 14.5f, 16.5f);
                            s2.quadTo(17.3f, 16.3f, 17.5f, 13.5f);
                            s2.close();
                            c.drawPath(s2, fill);
                            break;
                        }
                        case ICON_TELEGRAM: {
                            // Paper plane icon matching yellow channel badge
                            Path plane = new Path();
                            plane.moveTo(20.0f, 4.6f);
                            plane.lineTo(3.5f, 11.4f);
                            plane.lineTo(9.4f, 13.7f);
                            plane.lineTo(17.2f, 7.6f);
                            plane.lineTo(11.3f, 14.9f);
                            plane.lineTo(16.8f, 19.4f);
                            plane.close();
                            c.drawPath(plane, fill);
                            break;
                        }
                        case ICON_CODE: {
                            // Angle brackets </ > representing open source repository
                            stroke.setStrokeWidth(2.0f);
                            Path left = new Path();
                            left.moveTo(8.5f, 7.5f);
                            left.lineTo(4.2f, 12.0f);
                            left.lineTo(8.5f, 16.5f);
                            c.drawPath(left, stroke);

                            Path right = new Path();
                            right.moveTo(15.5f, 7.5f);
                            right.lineTo(19.8f, 12.0f);
                            right.lineTo(15.5f, 16.5f);
                            c.drawPath(right, stroke);

                            c.drawLine(13.4f, 6.0f, 10.6f, 18.0f, stroke);
                            break;
                        }
                        case ICON_UPDATE: {
                            // Downward arrow into tray representing update download/install
                            stroke.setStrokeWidth(2.1f);
                            c.drawLine(12.0f, 4.2f, 12.0f, 14.5f, stroke);
                            Path chevron = new Path();
                            chevron.moveTo(7.8f, 10.8f);
                            chevron.lineTo(12.0f, 15.0f);
                            chevron.lineTo(16.2f, 10.8f);
                            c.drawPath(chevron, stroke);

                            Path tray = new Path();
                            tray.moveTo(5.2f, 15.5f);
                            tray.lineTo(5.2f, 18.2f);
                            tray.quadTo(5.2f, 19.5f, 6.5f, 19.5f);
                            tray.lineTo(17.5f, 19.5f);
                            tray.quadTo(18.8f, 19.5f, 18.8f, 18.2f);
                            tray.lineTo(18.8f, 15.5f);
                            c.drawPath(tray, stroke);
                            break;
                        }
                        case ICON_RESTART:
                        default: {
                            // Circular refresh arrow matching red Restart App badge
                            RectF arc = new RectF(5.5f, 5.5f, 18.5f, 18.5f);
                            c.drawArc(arc, -45f, 290f, false, stroke);
                            Path arrow = new Path();
                            arrow.moveTo(15.2f, 4.2f);
                            arrow.lineTo(19.6f, 5.0f);
                            arrow.lineTo(17.6f, 9.2f);
                            arrow.close();
                            c.drawPath(arrow, fill);
                            break;
                        }
                    }
                }
            };

            LayerDrawable layer = new LayerDrawable(new Drawable[]{bg, fg});
            int inset = Math.max(2, Math.round(cornerRadiusPx * 0.55f));
            layer.setLayerInset(1, inset, inset, inset, inset);
            return layer;
        }
    }

    private abstract static class VectorDrawable24 extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean autoMirror;
        private final boolean forceMirror;

        VectorDrawable24(int color, boolean autoMirror) {
            this(color, autoMirror, false);
        }

        VectorDrawable24(int color, boolean autoMirror, boolean forceMirror) {
            this.autoMirror = autoMirror;
            this.forceMirror = forceMirror;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(color);
        }

        protected abstract void draw24(Canvas c, Paint fill, Paint stroke);

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float sx = b.width() / 24f;
            float sy = b.height() / 24f;
            canvas.save();
            canvas.translate(b.left, b.top);
            if (forceMirror || (autoMirror && getLayoutDirection() == View.LAYOUT_DIRECTION_RTL)) {
                canvas.translate(b.width(), 0);
                canvas.scale(-sx, sy);
            } else {
                canvas.scale(sx, sy);
            }
            draw24(canvas, fill, stroke);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); stroke.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
