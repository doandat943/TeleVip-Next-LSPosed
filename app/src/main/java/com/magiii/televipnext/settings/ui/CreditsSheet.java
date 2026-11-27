package com.magiii.televipnext.settings.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.utils.JsonAssetReader;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;
import com.magiii.televipnext.virtuals.messenger.browser.Browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Liquid Glass Credits & About bottom sheet backed by {@code assets/credits.json}.
 */
public final class CreditsSheet {

    private CreditsSheet() {}

    public static void show(Context ctx, SettingsController controller) {
        Activity activity = ctx instanceof Activity ? (Activity) ctx : Utils.getCurrentActivity();
        if (activity == null) return;

        try {
            boolean light = Theme.isLight();
            GlassDesign.Palette p = GlassDesign.Palette.of(light);
            Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            Typeface boldTf = Typeface.DEFAULT_BOLD;
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable[] hideToastHolder = new Runnable[1];

            Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            FrameLayout scrim = new FrameLayout(activity);
            scrim.setLayoutDirection(Translator.isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            scrim.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrim.setOnClickListener(v -> dialog.dismiss());

            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int maxSheetH = Math.min((int) (screenH * 0.86f), dp(activity, 720));
            float topRadius = dp(activity, 28);

            LinearLayout sheet = new LinearLayout(activity) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int mode = MeasureSpec.getMode(heightMeasureSpec);
                    int size = MeasureSpec.getSize(heightMeasureSpec);
                    int capped = (mode == MeasureSpec.UNSPECIFIED) ? maxSheetH : Math.min(size, maxSheetH);
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(capped, MeasureSpec.AT_MOST));
                }
            };
            sheet.setOrientation(LinearLayout.VERTICAL);
            sheet.setClickable(true);
            sheet.setBackground(GlassDesign.createAuroraBackground(p));
            sheet.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + Math.round(topRadius), topRadius);
                }
            });
            sheet.setClipToOutline(true);
            scrim.addView(sheet, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

            // Floating toast overlay
            TextView toastView = new TextView(activity);
            toastView.setTextColor(Color.WHITE);
            toastView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            toastView.setTypeface(mediumTf);
            toastView.setPadding(dp(activity, 16), dp(activity, 9), dp(activity, 16), dp(activity, 9));
            GradientDrawable toastBg = new GradientDrawable();
            toastBg.setColor(Color.argb(235, 20, 27, 38));
            toastBg.setCornerRadius(dp(activity, 20));
            toastView.setBackground(toastBg);
            toastView.setElevation(dp(activity, 6));
            toastView.setAlpha(0f);
            toastView.setVisibility(View.GONE);
            FrameLayout.LayoutParams toastLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            toastLp.bottomMargin = getNavBarHeight(activity) + dp(activity, 20);
            scrim.addView(toastView, toastLp);

            // 1. Grab handle
            View handle = new View(activity);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor(light ? Color.argb(42, 15, 23, 42) : Color.argb(56, 255, 255, 255));
            handleBg.setCornerRadius(dp(activity, 2));
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.topMargin = dp(activity, 10);
            handleLp.bottomMargin = dp(activity, 10);
            sheet.addView(handle, handleLp);

            // 2. Top bar: [36dp spacer]  [Credits title]  [Close pill]
            LinearLayout topBar = new LinearLayout(activity);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topBarLp.setMargins(dp(activity, 14), 0, dp(activity, 14), dp(activity, 10));
            sheet.addView(topBar, topBarLp);

            topBar.addView(new View(activity), new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

            TextView sheetTitleTv = new TextView(activity);
            sheetTitleTv.setText(Translator.get(Keys.CreditsTitle));
            sheetTitleTv.setTextColor(p.textPrimary);
            sheetTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            sheetTitleTv.setTypeface(mediumTf);
            sheetTitleTv.setGravity(Gravity.CENTER);
            sheetTitleTv.setSingleLine(true);
            topBar.addView(sheetTitleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView closeBtn = GlassDesign.createCloseCircleButton(activity, p, dialog::dismiss);
            topBar.addView(closeBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

            // 3. Scrollable content
            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sheet.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(activity, 14), dp(activity, 2), dp(activity, 14), getNavBarHeight(activity) + dp(activity, 22));
            scroll.addView(content, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            JSONObject json = loadCreditsData();
            String appName = json.optString("name", "TeleVip Next");
            String tagline = resolveLocalized(json, "taglineKey", "tagline", Translator.get(Keys.CreditsTagline));

            // 3a. Hero Brand Card
            content.addView(buildHeroCard(activity, p, mediumTf, boldTf, appName, tagline, mainHandler, toastView, hideToastHolder));

            // 3b. Data-driven sections
            JSONArray sections = json.optJSONArray("sections");
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject sec = sections.optJSONObject(i);
                    if (sec == null) continue;
                    View card = buildSectionCard(activity, p, mediumTf, boldTf, sec, dialog, controller);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(activity, 12);
                    content.addView(card, lp);
                }
            }

            dialog.setContentView(scrim);
            GlassDesign.configureEdgeToEdgeSheetDialog(dialog, light);

            sheet.setTranslationY(dp(activity, 44));
            sheet.setAlpha(0f);
            dialog.show();
            GlassDesign.applySheetSystemBarAppearance(dialog, light);
            sheet.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(2.2f))
                    .start();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static View buildHeroCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            String appName,
            String tagline,
            Handler mainHandler,
            TextView toastView,
            Runnable[] hideToastHolder) {

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 18));
        hero.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 24)));

        // Glowing rounded emblem
        ImageView emblem = new ImageView(activity);
        emblem.setImageDrawable(GlassDesign.GlassIcons.brandEmblemDrawable(dp(activity, 18)));
        emblem.setElevation(dp(activity, 6));
        LinearLayout.LayoutParams emblemLp = new LinearLayout.LayoutParams(dp(activity, 56), dp(activity, 56));
        emblemLp.bottomMargin = dp(activity, 12);
        hero.addView(emblem, emblemLp);

        TextView nameTv = new TextView(activity);
        nameTv.setText(appName);
        nameTv.setTextColor(p.textPrimary);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f);
        nameTv.setTypeface(boldTf);
        nameTv.setGravity(Gravity.CENTER);
        hero.addView(nameTv);

        TextView tagTv = new TextView(activity);
        tagTv.setText(tagline);
        tagTv.setTextColor(p.textSecondary);
        tagTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tagTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(activity, 3);
        tagLp.bottomMargin = dp(activity, 14);
        hero.addView(tagTv, tagLp);

        // Version + Client pills
        String versionLabel = resolveModuleVersion(activity);
        ClientManager.Client client = ClientManager.getCurrent();
        String clientLabel = client != null ? client.name() : (Utils.pkgName != null ? Utils.pkgName : "Telegram");

        LinearLayout pillsRow = new LinearLayout(activity);
        pillsRow.setOrientation(LinearLayout.HORIZONTAL);
        pillsRow.setGravity(Gravity.CENTER);

        TextView verPill = createInfoPill(activity, p, mediumTf, versionLabel, true);
        pillsRow.addView(verPill);

        View spacer = new View(activity);
        pillsRow.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 8), 1));

        TextView clientPill = createInfoPill(activity, p, mediumTf, clientLabel, false);
        pillsRow.addView(clientPill);

        pillsRow.setOnClickListener(v -> {
            String copyText = appName + " " + versionLabel + " • " + (Utils.pkgName != null ? Utils.pkgName : clientLabel);
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("TeleVip Info", copyText));
            }
            showToast(toastView, mainHandler, hideToastHolder, Translator.get(Keys.CreditsCopiedInfo));
        });

        hero.addView(pillsRow);
        return hero;
    }

    private static TextView createInfoPill(Context ctx, GlassDesign.Palette p, Typeface mediumTf, String label, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tv.setTypeface(mediumTf);
        tv.setTextColor(accent ? p.accentBlue : p.textSecondary);
        tv.setSingleLine(true);
        tv.setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(ctx, 12));
        bg.setColor(accent ? p.accentSoft : p.divider);
        tv.setBackground(bg);
        return tv;
    }

    private static LinearLayout buildSectionCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            JSONObject sec,
            Dialog dialog,
            SettingsController controller) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        card.setClipToOutline(true);

        String title = resolveLocalized(sec, "titleKey", "title", "");
        int badgeColor = parseColorSafe(sec.optString("badgeColor"), p.accentBlue);
        int iconType = resolveIconType(sec.optString("icon"));

        final boolean rtl = Translator.isRtl();
        // Header row
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 12));

        ImageView badge = new ImageView(activity);
        badge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(badgeColor, dp(activity, 8), iconType, Color.WHITE));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));

        TextView headerTv = new TextView(activity);
        headerTv.setText(title);
        headerTv.setTextColor(p.accentBlue);
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        headerTv.setTypeface(mediumTf);
        headerTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        headerTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        if (rtl) {
            badgeLp.leftMargin = dp(activity, 12);
            headerRow.addView(headerTv);
            headerRow.addView(badge, badgeLp);
        } else {
            badgeLp.rightMargin = dp(activity, 12);
            headerRow.addView(badge, badgeLp);
            headerRow.addView(headerTv);
        }
        card.addView(headerRow);

        JSONArray items = sec.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                card.addView(createDivider(activity, p));
                card.addView(buildItemRow(activity, p, mediumTf, boldTf, item, dialog, controller));
            }
        }
        return card;
    }

    private static View buildItemRow(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            JSONObject item,
            Dialog dialog,
            SettingsController controller) {

        String name = resolveLocalized(item, "nameKey", "name", "");
        String handle = item.optString("handle", "");
        String role = resolveLocalized(item, "roleKey", "role", "");
        String url = item.optString("url", "");
        String iconName = item.optString("icon", "");
        int avatarBg = parseColorSafe(item.optString("avatarBg"), p.accentBlue);

        final boolean rtl = Translator.isRtl();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, rtl ? 14 : 16), dp(activity, 13), dp(activity, rtl ? 16 : 14), dp(activity, 13));
        row.setBackground(GlassDesign.createRowRipple(p.ripple));

        // Left avatar / icon badge (Right in RTL)
        View avatarView;
        if (!iconName.isEmpty()) {
            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                    avatarBg, dp(activity, 11), resolveIconType(iconName), Color.WHITE));
            avatarView = iv;
        } else {
            TextView mono = new TextView(activity);
            mono.setText(extractInitial(name, handle));
            mono.setTextColor(Color.WHITE);
            mono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            mono.setTypeface(boldTf);
            mono.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{avatarBg, darken(avatarBg, 0.82f)}
            );
            circle.setShape(GradientDrawable.OVAL);
            mono.setBackground(circle);
            avatarView = mono;
        }
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(activity, 38), dp(activity, 38));

        // Middle column: [Name + @handle] / role
        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout nameLine = new LinearLayout(activity);
        nameLine.setOrientation(LinearLayout.HORIZONTAL);
        nameLine.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(activity);
        nameTv.setText(name);
        nameTv.setTextColor(p.textPrimary);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        nameTv.setTypeface(mediumTf);
        nameTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        nameTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        nameTv.setSingleLine(true);

        TextView handleTv = null;
        LinearLayout.LayoutParams hLp = null;
        if (!handle.isEmpty()) {
            handleTv = new TextView(activity);
            handleTv.setText(handle);
            handleTv.setTextColor(p.accentBlue);
            handleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            handleTv.setTypeface(mediumTf);
            handleTv.setSingleLine(true);
            handleTv.setPadding(dp(activity, 7), dp(activity, 2), dp(activity, 7), dp(activity, 2));
            GradientDrawable hBg = new GradientDrawable();
            hBg.setCornerRadius(dp(activity, 8));
            hBg.setColor(p.accentSoft);
            handleTv.setBackground(hBg);
            hLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (rtl) {
            if (handleTv != null) {
                hLp.rightMargin = dp(activity, 8);
                nameLine.addView(handleTv, hLp);
            }
            nameLine.addView(nameTv);
        } else {
            nameLine.addView(nameTv);
            if (handleTv != null) {
                hLp.leftMargin = dp(activity, 8);
                nameLine.addView(handleTv, hLp);
            }
        }
        textCol.addView(nameLine);

        if (!role.isEmpty()) {
            TextView roleTv = new TextView(activity);
            roleTv.setText(role);
            roleTv.setTextColor(p.textSecondary);
            roleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            roleTv.setLineSpacing(dp(activity, 2), 1.0f);
            roleTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            roleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = dp(activity, 2);
            textCol.addView(roleTv, rLp);
        }

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ImageView chev = null;
        if (!url.isEmpty()) {
            chev = new ImageView(activity);
            chev.setImageDrawable(GlassDesign.GlassIcons.chevronRight(p.textMuted, rtl));
            row.setOnClickListener(v -> openUrl(activity, url, dialog, controller));
        }

        if (rtl) {
            if (chev != null) {
                LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
                chevLp.rightMargin = dp(activity, 8);
                row.addView(chev, chevLp);
            }
            row.addView(textCol, colLp);
            avLp.leftMargin = dp(activity, 13);
            row.addView(avatarView, avLp);
        } else {
            avLp.rightMargin = dp(activity, 13);
            row.addView(avatarView, avLp);
            colLp.rightMargin = dp(activity, 8);
            row.addView(textCol, colLp);
            if (chev != null) {
                row.addView(chev, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));
            }
        }

        return row;
    }

    private static void openUrl(Activity activity, String url, Dialog dialog, SettingsController controller) {
        try {
            dialog.dismiss();
            if (url.contains("t.me/")) {
                if (controller != null) controller.hide();
                Browser.openUrl(activity, url);
                return;
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Throwable t) {
            try {
                Browser.openUrl(activity, url);
            } catch (Throwable ignored) {
                Logger.e(t);
            }
        }
    }

    private static View createDivider(Context ctx, GlassDesign.Palette p) {
        View div = new View(ctx);
        div.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(0.7f * ctx.getResources().getDisplayMetrics().density)));
        lp.setMarginStart(dp(ctx, 16));
        lp.setMarginEnd(dp(ctx, 16));
        div.setLayoutParams(lp);
        return div;
    }

    private static JSONObject loadCreditsData() {
        JSONObject obj = JsonAssetReader.readObject("assets/credits.json");
        if (obj != null) return obj;
        try {
            return new JSONObject(
                    "{\"name\":\"TeleVip Next\",\"taglineKey\":\"CreditsTagline\",\"sections\":[{\"titleKey\":\"CreditsAuthors\",\"icon\":\"sparkles\",\"badgeColor\":\"#8B6BFF\",\"items\":[{\"name\":\"doandat943\",\"handle\":\"@doandat943\",\"roleKey\":\"CreditsMaintainerRole\",\"avatarBg\":\"#2A85FF\",\"url\":\"https://github.com/doandat943\"},{\"name\":\"Mustafa\",\"handle\":\"@mustafa1dev\",\"roleKey\":\"CreditsOriginalAuthorRole\",\"avatarBg\":\"#FF7A45\",\"url\":\"https://github.com/mustafa1dev\"}]},{\"titleKey\":\"CreditsLinks\",\"icon\":\"telegram\",\"badgeColor\":\"#28A8EA\",\"items\":[{\"nameKey\":\"DeveloperChannel\",\"handle\":\"@televipnext\",\"roleKey\":\"CreditsChannelRole\",\"icon\":\"telegram\",\"avatarBg\":\"#28A8EA\",\"url\":\"https://t.me/televipnext\"},{\"nameKey\":\"CreditsSourceCode\",\"handle\":\"GitHub\",\"roleKey\":\"CreditsSourceCodeRole\",\"icon\":\"code\",\"avatarBg\":\"#34C759\",\"url\":\"https://github.com/doandat943/TeleVip-Next-LSPosed\"}]}]}");
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static String resolveLocalized(JSONObject obj, String keyField, String literalField, String fallback) {
        String key = obj.optString(keyField, "");
        if (!key.isEmpty()) {
            String translated = Translator.get(key);
            if (!TextUtils.isEmpty(translated) && !translated.equals(key)) {
                return translated;
            }
        }
        String lit = obj.optString(literalField, "");
        return !lit.isEmpty() ? lit : fallback;
    }

    private static String resolveModuleVersion(Activity activity) {
        try {
            if (Utils.modulePath != null) {
                PackageInfo pi = activity.getPackageManager().getPackageArchiveInfo(Utils.modulePath, 0);
                if (pi != null && pi.versionName != null) {
                    long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
                    return "v" + pi.versionName + " (" + code + ")";
                }
            }
        } catch (Throwable ignored) {}
        return "v1.2.3 (6)";
    }

    private static int resolveIconType(String iconName) {
        if ("telegram".equalsIgnoreCase(iconName)) return GlassDesign.ICON_TELEGRAM;
        if ("code".equalsIgnoreCase(iconName)) return GlassDesign.ICON_CODE;
        if ("ghost".equalsIgnoreCase(iconName)) return GlassDesign.ICON_GHOST;
        return GlassDesign.ICON_SPARKLES;
    }

    private static int parseColorSafe(String hex, int fallback) {
        try {
            if (!TextUtils.isEmpty(hex)) return Color.parseColor(hex);
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static int darken(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * factor)),
                Math.max(0, Math.round(Color.green(color) * factor)),
                Math.max(0, Math.round(Color.blue(color) * factor))
        );
    }

    private static String extractInitial(String name, String handle) {
        String src = !TextUtils.isEmpty(name) ? name : handle;
        if (!TextUtils.isEmpty(src)) {
            for (int i = 0; i < src.length(); i++) {
                char ch = src.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    return String.valueOf(ch).toUpperCase(Locale.ROOT);
                }
            }
        }
        return "T";
    }

    private static void showToast(TextView tv, Handler handler, Runnable[] hideHolder, String text) {
        if (hideHolder[0] != null) handler.removeCallbacks(hideHolder[0]);
        tv.setText(text);
        tv.setVisibility(View.VISIBLE);
        tv.animate().cancel();
        tv.animate().alpha(1f).setDuration(150).start();
        hideHolder[0] = () -> tv.animate().alpha(0f).setDuration(220).withEndAction(() -> tv.setVisibility(View.GONE)).start();
        handler.postDelayed(hideHolder[0], 1800);
    }

    private static int getNavBarHeight(Context ctx) {
        int id = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : 0;
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
