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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.magiii.televipnext.BuildConfig;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigPreferences;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.updater.UpdateManager;
import com.magiii.televipnext.utils.JsonAssetReader;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;
import com.magiii.televipnext.virtuals.messenger.browser.Browser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Liquid Glass Credits & About bottom sheet with module/client versions,
 * update controls, language selector, and data-driven sections from {@code assets/credits.json}.
 */
public final class CreditsSheet {

    private static final int[] AUTO_AVATAR_COLORS = new int[]{
            0xFF2A85FF, 0xFF34C759, 0xFFAF52DE, 0xFFFF9F0A,
            0xFF5856D6, 0xFFFF375F, 0xFF0EA5E9, 0xFF14B8A6
    };

    private CreditsSheet() {}

    public static void show(Context ctx, SettingsController controller) {
        show(ctx, controller, null);
    }

    public static void show(Context ctx, SettingsController controller, Runnable onLanguageChanged) {
        Activity activity = ctx instanceof Activity ? (Activity) ctx : Utils.getCurrentActivity();
        if (activity == null) return;

        try {
            boolean light = Theme.isLight();
            GlassDesign.Palette p = GlassDesign.Palette.of(light);
            Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            Typeface boldTf = Typeface.DEFAULT_BOLD;
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable[] hideToastHolder = new Runnable[1];
            UpdateManager.Listener[] updateListenerHolder = new UpdateManager.Listener[1];

            Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setOnDismissListener(d -> {
                if (updateListenerHolder[0] != null) {
                    UpdateManager.removeListener(updateListenerHolder[0]);
                    updateListenerHolder[0] = null;
                }
                if (hideToastHolder[0] != null) {
                    mainHandler.removeCallbacks(hideToastHolder[0]);
                    hideToastHolder[0] = null;
                }
            });

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

            // 2. Top bar: [32dp spacer]  [Credits title]  [Close pill]
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
            scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
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

            // 3a. Compact Identity & Client Environment Card
            content.addView(buildHeroCard(activity, p, mediumTf, boldTf, appName, tagline, mainHandler, toastView, hideToastHolder));

            // 3b. Updates Card (first options section)
            View updatesCard = buildUpdatesCard(activity, p, mediumTf, updateListenerHolder);
            LinearLayout.LayoutParams updatesLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            updatesLp.topMargin = dp(activity, 12);
            content.addView(updatesCard, updatesLp);

            // 3c. Language Card (second options section)
            View languageCard = buildLanguageCard(activity, p, mediumTf, dialog, controller, onLanguageChanged);
            LinearLayout.LayoutParams langLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            langLp.topMargin = dp(activity, 12);
            content.addView(languageCard, langLp);

            // 3d. Data-driven Credits & Links sections + compact Contributors entry row
            List<JSONObject> contributorGroups = collectContributorGroups(json.optJSONArray("contributors"));
            JSONArray sections = json.optJSONArray("sections");
            boolean insertedContributors = false;
            if (sections != null) {
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject sec = sections.optJSONObject(i);
                    if (sec == null) continue;
                    View card = buildSectionCard(activity, p, mediumTf, boldTf, sec, dialog, null, controller);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(activity, 12);
                    content.addView(card, lp);

                    if (i == 0 && !contributorGroups.isEmpty()) {
                        View contribCard = buildContributorsEntryCard(
                                activity, p, mediumTf, boldTf, contributorGroups, dialog, controller);
                        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        cLp.topMargin = dp(activity, 12);
                        content.addView(contribCard, cLp);
                        insertedContributors = true;
                    }
                }
            }
            if (!insertedContributors && !contributorGroups.isEmpty()) {
                View contribCard = buildContributorsEntryCard(
                        activity, p, mediumTf, boldTf, contributorGroups, dialog, controller);
                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cLp.topMargin = dp(activity, 12);
                content.addView(contribCard, cLp);
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

        final boolean rtl = Translator.isRtl();
        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 14));
        hero.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        hero.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(activity, 22)));
        hero.setClipToOutline(true);

        String versionLabel = resolveModuleVersion(activity);
        String clientName = ClientManager.getCurrentDisplayName(activity);
        String clientVer = resolveClientVersion(activity);
        String clientFull = clientVer.isEmpty() ? clientName : (clientName + " · " + clientVer);

        // Compact horizontal header: [44dp emblem] [AppName + VersionPill / Tagline]
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView emblem = new ImageView(activity);
        emblem.setImageDrawable(GlassDesign.GlassIcons.brandEmblemDrawable(dp(activity, 14)));
        emblem.setElevation(dp(activity, 4));
        LinearLayout.LayoutParams emblemLp = new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44));

        LinearLayout infoCol = new LinearLayout(activity);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout titleLine = new LinearLayout(activity);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

        TextView nameTv = new TextView(activity);
        nameTv.setText(appName);
        nameTv.setTextColor(p.textPrimary);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        nameTv.setTypeface(boldTf);
        nameTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        nameTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        nameTv.setSingleLine(true);

        TextView verPill = new TextView(activity);
        verPill.setText(versionLabel);
        verPill.setTextColor(p.accentBlue);
        verPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        verPill.setTypeface(mediumTf);
        verPill.setSingleLine(true);
        verPill.setPadding(dp(activity, 8), dp(activity, 2.5f), dp(activity, 8), dp(activity, 2.5f));
        GradientDrawable verBg = new GradientDrawable();
        verBg.setCornerRadius(dp(activity, 8));
        verBg.setColor(p.accentSoft);
        verPill.setBackground(verBg);

        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rtl) {
            verLp.rightMargin = dp(activity, 8);
            titleLine.addView(verPill, verLp);
            titleLine.addView(nameTv);
        } else {
            verLp.leftMargin = dp(activity, 8);
            titleLine.addView(nameTv);
            titleLine.addView(verPill, verLp);
        }
        infoCol.addView(titleLine);

        TextView tagTv = new TextView(activity);
        tagTv.setText(tagline);
        tagTv.setTextColor(p.textSecondary);
        tagTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tagTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        tagTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tagLp.topMargin = dp(activity, 2);
        infoCol.addView(tagTv, tagLp);

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (rtl) {
            emblemLp.leftMargin = dp(activity, 12);
            topRow.addView(infoCol, colLp);
            topRow.addView(emblem, emblemLp);
        } else {
            emblemLp.rightMargin = dp(activity, 12);
            topRow.addView(emblem, emblemLp);
            topRow.addView(infoCol, colLp);
        }
        hero.addView(topRow);

        // Host client + version pill below header
        TextView clientPill = new TextView(activity);
        clientPill.setText(clientFull);
        clientPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        clientPill.setTypeface(mediumTf);
        clientPill.setTextColor(p.textSecondary);
        clientPill.setSingleLine(true);
        clientPill.setEllipsize(TextUtils.TruncateAt.END);
        clientPill.setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 12), dp(activity, 5));
        GradientDrawable clientBg = new GradientDrawable();
        clientBg.setCornerRadius(dp(activity, 12));
        clientBg.setColor(p.divider);
        clientPill.setBackground(clientBg);

        LinearLayout.LayoutParams clientLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clientLp.topMargin = dp(activity, 12);
        clientLp.gravity = rtl ? Gravity.RIGHT : Gravity.LEFT;
        hero.addView(clientPill, clientLp);

        hero.setOnClickListener(v -> {
            String pkg = !TextUtils.isEmpty(Utils.pkgName) ? Utils.pkgName : activity.getPackageName();
            String copyText = appName + " " + versionLabel + " • " + clientFull
                    + (!TextUtils.isEmpty(pkg) ? " (" + pkg + ")" : "");
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("TeleVip Info", copyText));
            }
            showToast(toastView, mainHandler, hideToastHolder, Translator.get(Keys.CreditsCopiedInfo));
        });

        return hero;
    }

    private static View buildUpdatesCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            UpdateManager.Listener[] updateListenerHolder) {

        final boolean rtl = Translator.isRtl();
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        card.setClipToOutline(true);

        // Compact header row
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 10));

        ImageView badge = new ImageView(activity);
        badge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                Color.rgb(48, 209, 88), dp(activity, 7), GlassDesign.ICON_UPDATE, Color.WHITE));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));

        TextView headerTv = new TextView(activity);
        headerTv.setText(Translator.get(Keys.UpdatesSettings));
        headerTv.setTextColor(p.accentBlue);
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        headerTv.setTypeface(mediumTf);
        headerTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        headerTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

        if (rtl) {
            badgeLp.leftMargin = dp(activity, 10);
            headerRow.addView(headerTv);
            headerRow.addView(badge, badgeLp);
        } else {
            badgeLp.rightMargin = dp(activity, 10);
            headerRow.addView(badge, badgeLp);
            headerRow.addView(headerTv);
        }
        card.addView(headerRow);

        // Row 1: Check for Updates
        card.addView(createDivider(activity, p));
        LinearLayout checkRow = new LinearLayout(activity);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER_VERTICAL);
        checkRow.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
        checkRow.setMinimumHeight(dp(activity, 52));
        checkRow.setBackground(GlassDesign.createRowRipple(p.ripple));

        LinearLayout checkTextCol = new LinearLayout(activity);
        checkTextCol.setOrientation(LinearLayout.VERTICAL);
        checkTextCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView checkTitle = new TextView(activity);
        checkTitle.setText(Translator.get(Keys.CheckForUpdates));
        checkTitle.setTextColor(p.textPrimary);
        checkTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        checkTitle.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        checkTitle.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        checkTextCol.addView(checkTitle);

        TextView checkSub = new TextView(activity);
        checkSub.setText(Translator.get(Keys.CheckForUpdatesDesc, UpdateManager.getCurrentVersionTag()));
        checkSub.setTextColor(p.textSecondary);
        checkSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        checkSub.setLineSpacing(dp(activity, 2), 1.0f);
        checkSub.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        checkSub.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams checkSubLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        checkSubLp.topMargin = dp(activity, 2);
        checkTextCol.addView(checkSub, checkSubLp);

        TextView valTv = new TextView(activity);
        valTv.setTextColor(p.accentBlue);
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        valTv.setTypeface(mediumTf);
        valTv.setSingleLine(true);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(p.accentSoft);
        pillBg.setCornerRadius(dp(activity, 10));
        valTv.setBackground(pillBg);
        valTv.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));

        bindUpdateStatusPill(valTv, pillBg, p, UpdateManager.getState(), UpdateManager.getLatestRelease());
        if (updateListenerHolder[0] != null) {
            UpdateManager.removeListener(updateListenerHolder[0]);
        }
        updateListenerHolder[0] = (state, release) -> bindUpdateStatusPill(valTv, pillBg, p, state, release);
        UpdateManager.addListener(updateListenerHolder[0]);

        LinearLayout.LayoutParams checkTextLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rtl) {
            valLp.rightMargin = dp(activity, 12);
            checkRow.addView(valTv, valLp);
            checkRow.addView(checkTextCol, checkTextLp);
        } else {
            checkTextLp.rightMargin = dp(activity, 12);
            checkRow.addView(checkTextCol, checkTextLp);
            checkRow.addView(valTv, valLp);
        }
        checkRow.setOnClickListener(v -> UpdateManager.onCheckRowClicked(activity));
        card.addView(checkRow);

        // Row 2: Auto-Check & Download Updates
        card.addView(createDivider(activity, p));
        LinearLayout autoRow = new LinearLayout(activity);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12));
        autoRow.setMinimumHeight(dp(activity, 52));
        autoRow.setBackground(GlassDesign.createRowRipple(p.ripple));

        LinearLayout autoTextCol = new LinearLayout(activity);
        autoTextCol.setOrientation(LinearLayout.VERTICAL);
        autoTextCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView autoTitle = new TextView(activity);
        autoTitle.setText(Translator.get(Keys.AutoUpdateCheck));
        autoTitle.setTextColor(p.textPrimary);
        autoTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        autoTitle.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        autoTitle.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        autoTextCol.addView(autoTitle);

        TextView autoSub = new TextView(activity);
        autoSub.setText(Translator.get(Keys.AutoUpdateCheck + "Desc"));
        autoSub.setTextColor(p.textSecondary);
        autoSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        autoSub.setLineSpacing(dp(activity, 2), 1.0f);
        autoSub.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        autoSub.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams autoSubLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        autoSubLp.topMargin = dp(activity, 2);
        autoTextCol.addView(autoSub, autoSubLp);

        boolean autoEnabled = !ConfigPreferences.contains(Keys.AutoUpdateCheck)
                || ConfigPreferences.getBoolean(Keys.AutoUpdateCheck);
        GlassDesign.PillSwitchView sw = new GlassDesign.PillSwitchView(activity, autoEnabled, p);
        LinearLayout.LayoutParams autoTextLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 28));
        if (rtl) {
            swLp.rightMargin = dp(activity, 14);
            autoRow.addView(sw, swLp);
            autoRow.addView(autoTextCol, autoTextLp);
        } else {
            autoTextLp.rightMargin = dp(activity, 14);
            autoRow.addView(autoTextCol, autoTextLp);
            autoRow.addView(sw, swLp);
        }

        autoRow.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            ConfigPreferences.putBoolean(Keys.AutoUpdateCheck, next);
        });
        card.addView(autoRow);

        return card;
    }

    private static View buildLanguageCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Dialog dialog,
            SettingsController controller,
            Runnable onLanguageChanged) {

        final boolean rtl = Translator.isRtl();
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 13));
        card.setMinimumHeight(dp(activity, 58));
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        card.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(activity, 22)));
        card.setClipToOutline(true);

        ImageView badge = new ImageView(activity);
        badge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                Color.rgb(88, 86, 214), dp(activity, 10), GlassDesign.ICON_LANGUAGE, Color.WHITE));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34));

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(activity);
        titleTv.setText(Translator.get(Keys.Language));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        titleTv.setTypeface(mediumTf);
        titleTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        TextView subTv = new TextView(activity);
        subTv.setText(Translator.get(Keys.Language + "Desc"));
        subTv.setTextColor(p.textSecondary);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subTv.setLineSpacing(dp(activity, 2), 1.0f);
        subTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(activity, 2);
        textCol.addView(subTv, subLp);

        TextView langPill = new TextView(activity);
        langPill.setText(Translator.getLanguageDisplayName(Translator.getSelectedLanguage()));
        langPill.setTextColor(p.accentBlue);
        langPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        langPill.setTypeface(mediumTf);
        langPill.setMaxLines(1);
        langPill.setEllipsize(TextUtils.TruncateAt.END);
        langPill.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        GradientDrawable langBg = new GradientDrawable();
        langBg.setColor(p.accentSoft);
        langBg.setCornerRadius(dp(activity, 10));
        langPill.setBackground(langBg);

        ImageView chev = new ImageView(activity);
        chev.setImageDrawable(GlassDesign.GlassIcons.chevronRight(p.textMuted, rtl));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 16));

        LinearLayout endGroup = new LinearLayout(activity);
        endGroup.setOrientation(LinearLayout.HORIZONTAL);
        endGroup.setGravity(Gravity.CENTER_VERTICAL);
        if (rtl) {
            chevLp.rightMargin = dp(activity, 6);
            endGroup.addView(chev, chevLp);
            endGroup.addView(langPill);
        } else {
            chevLp.leftMargin = dp(activity, 6);
            endGroup.addView(langPill);
            endGroup.addView(chev, chevLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rtl) {
            endLp.rightMargin = dp(activity, 10);
            badgeLp.leftMargin = dp(activity, 12);
            card.addView(endGroup, endLp);
            card.addView(textCol, textLp);
            card.addView(badge, badgeLp);
        } else {
            badgeLp.rightMargin = dp(activity, 12);
            textLp.rightMargin = dp(activity, 10);
            card.addView(badge, badgeLp);
            card.addView(textCol, textLp);
            card.addView(endGroup, endLp);
        }

        card.setOnClickListener(v -> showLanguagePicker(activity, dialog, controller, onLanguageChanged));
        return card;
    }

    private static void showLanguagePicker(
            Activity activity,
            Dialog creditsDialog,
            SettingsController controller,
            Runnable onLanguageChanged) {
        final String[] codes = Translator.SUPPORTED_LANGS;
        final String[] labels = new String[codes.length];
        String selected = Translator.getSelectedLanguage();
        int currentIdx = 0;
        for (int i = 0; i < codes.length; i++) {
            labels[i] = Translator.getLanguagePickerLabel(codes[i]);
            if (codes[i].equals(selected)) {
                currentIdx = i;
            }
        }
        GlassDialog.showSingleChoice(activity, Translator.get(Keys.Language), labels, currentIdx, which -> {
            String chosen = codes[which];
            if (chosen.equals(selected)) return;
            Translator.setSelectedLanguage(chosen);
            if (onLanguageChanged != null) {
                onLanguageChanged.run();
            }
            creditsDialog.dismiss();
            show(activity, controller, onLanguageChanged);
        });
    }

    private static void bindUpdateStatusPill(
            TextView pillTv,
            GradientDrawable pillBg,
            GlassDesign.Palette p,
            UpdateManager.State state,
            UpdateManager.ReleaseInfo info) {
        int readyGreen = Color.rgb(48, 209, 88);
        int readyBg = Color.argb(p.light ? 28 : 40, 48, 209, 88);
        if (state == UpdateManager.State.READY_TO_INSTALL && info != null) {
            pillTv.setText(Translator.get(Keys.UpdateInstallAction) + " " + info.tag);
            pillTv.setTextColor(readyGreen);
            pillBg.setColor(readyBg);
        } else if (state == UpdateManager.State.INSTALLED_NEEDS_RESTART) {
            pillTv.setText(Translator.get(Keys.UpdateRestartAction));
            pillTv.setTextColor(readyGreen);
            pillBg.setColor(readyBg);
        } else if (state == UpdateManager.State.CHECKING) {
            pillTv.setText(Translator.get(Keys.UpdateChecking));
            pillTv.setTextColor(p.accentBlue);
            pillBg.setColor(p.accentSoft);
        } else if (state == UpdateManager.State.DOWNLOADING) {
            pillTv.setText(Translator.get(Keys.UpdateDownloading));
            pillTv.setTextColor(p.accentBlue);
            pillBg.setColor(p.accentSoft);
        } else {
            pillTv.setText(UpdateManager.getCurrentVersionTag());
            pillTv.setTextColor(p.accentBlue);
            pillBg.setColor(p.accentSoft);
        }
    }

    private static List<JSONObject> collectContributorGroups(JSONArray arr) {
        List<JSONObject> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject group = arr.optJSONObject(i);
            if (group == null) continue;
            JSONArray items = group.optJSONArray("items");
            if (items != null && items.length() > 0) {
                out.add(group);
            }
        }
        return out;
    }

    private static View buildContributorsEntryCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            List<JSONObject> groups,
            Dialog parentDialog,
            SettingsController controller) {

        final boolean rtl = Translator.isRtl();
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 13));
        card.setMinimumHeight(dp(activity, 58));
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        card.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(activity, 22)));
        card.setClipToOutline(true);

        List<JSONObject> previewItems = new ArrayList<>();
        StringBuilder subtitleSb = new StringBuilder();
        int totalCount = 0;
        for (JSONObject group : groups) {
            String gTitle = resolveSectionTitle(group);
            if (!TextUtils.isEmpty(gTitle)) {
                if (subtitleSb.length() > 0) subtitleSb.append(" · ");
                subtitleSb.append(gTitle);
            }
            JSONArray items = group.optJSONArray("items");
            if (items != null) {
                totalCount += items.length();
                for (int i = 0; i < items.length() && previewItems.size() < 4; i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) previewItems.add(item);
                }
            }
        }

        // Overlapping monogram avatar stack (up to 4 avatars)
        int shown = Math.min(4, previewItems.size());
        int avSize = dp(activity, 26);
        int step = dp(activity, 18);
        int stackW = avSize + Math.max(0, shown - 1) * step;
        FrameLayout avatarStack = new FrameLayout(activity);
        int ringColor = (p.cardFillTop & 0x00FFFFFF) | 0xFF000000;
        int strokePx = Math.max(1, dp(activity, 1.5f));
        for (int i = 0; i < shown; i++) {
            JSONObject item = previewItems.get(i);
            String name = resolveLocalized(item, "nameKey", "name", "");
            String handle = item.optString("handle", "");
            int bg = resolveAvatarColor(item, name, p.accentBlue);

            TextView av = new TextView(activity);
            av.setText(extractInitial(name, handle));
            av.setTextColor(Color.WHITE);
            av.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            av.setTypeface(boldTf);
            av.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    new int[]{bg, darken(bg, 0.82f)}
            );
            circle.setShape(GradientDrawable.OVAL);
            circle.setStroke(strokePx, ringColor);
            av.setBackground(circle);

            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(avSize, avSize);
            int visualIdx = rtl ? (shown - 1 - i) : i;
            flp.leftMargin = visualIdx * step;
            avatarStack.addView(av, flp);
        }
        LinearLayout.LayoutParams stackLp = new LinearLayout.LayoutParams(stackW, avSize);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(activity);
        titleTv.setText(Translator.get(Keys.CreditsContributors));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        titleTv.setTypeface(mediumTf);
        titleTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        if (subtitleSb.length() > 0) {
            TextView subTv = new TextView(activity);
            subTv.setText(subtitleSb.toString());
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            subTv.setMaxLines(1);
            subTv.setEllipsize(TextUtils.TruncateAt.END);
            subTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(activity, 2);
            textCol.addView(subTv, subLp);
        }

        TextView countPill = new TextView(activity);
        countPill.setText(String.valueOf(totalCount));
        countPill.setTextColor(p.accentBlue);
        countPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        countPill.setTypeface(mediumTf);
        countPill.setSingleLine(true);
        countPill.setPadding(dp(activity, 10), dp(activity, 4), dp(activity, 10), dp(activity, 4));
        GradientDrawable countBg = new GradientDrawable();
        countBg.setColor(p.accentSoft);
        countBg.setCornerRadius(dp(activity, 10));
        countPill.setBackground(countBg);

        ImageView chev = new ImageView(activity);
        chev.setImageDrawable(GlassDesign.GlassIcons.chevronRight(p.textMuted, rtl));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(activity, 16), dp(activity, 16));

        LinearLayout endGroup = new LinearLayout(activity);
        endGroup.setOrientation(LinearLayout.HORIZONTAL);
        endGroup.setGravity(Gravity.CENTER_VERTICAL);
        if (rtl) {
            chevLp.rightMargin = dp(activity, 6);
            endGroup.addView(chev, chevLp);
            endGroup.addView(countPill);
        } else {
            chevLp.leftMargin = dp(activity, 6);
            endGroup.addView(countPill);
            endGroup.addView(chev, chevLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rtl) {
            endLp.rightMargin = dp(activity, 10);
            stackLp.leftMargin = dp(activity, 12);
            card.addView(endGroup, endLp);
            card.addView(textCol, textLp);
            card.addView(avatarStack, stackLp);
        } else {
            stackLp.rightMargin = dp(activity, 12);
            textLp.rightMargin = dp(activity, 10);
            card.addView(avatarStack, stackLp);
            card.addView(textCol, textLp);
            card.addView(endGroup, endLp);
        }

        card.setOnClickListener(v -> showContributorsSheet(
                activity, p, mediumTf, boldTf, groups, parentDialog, controller));
        return card;
    }

    private static void showContributorsSheet(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            List<JSONObject> groups,
            Dialog parentDialog,
            SettingsController controller) {
        try {
            Dialog subDialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
            subDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            FrameLayout scrim = new FrameLayout(activity);
            scrim.setLayoutDirection(Translator.isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            scrim.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            scrim.setOnClickListener(v -> subDialog.dismiss());

            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int maxSheetH = Math.min((int) (screenH * 0.84f), dp(activity, 680));
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

            View handle = new View(activity);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor(p.light ? Color.argb(42, 15, 23, 42) : Color.argb(56, 255, 255, 255));
            handleBg.setCornerRadius(dp(activity, 2));
            handle.setBackground(handleBg);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.topMargin = dp(activity, 10);
            handleLp.bottomMargin = dp(activity, 10);
            sheet.addView(handle, handleLp);

            LinearLayout topBar = new LinearLayout(activity);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            topBarLp.setMargins(dp(activity, 14), 0, dp(activity, 14), dp(activity, 10));
            sheet.addView(topBar, topBarLp);

            topBar.addView(new View(activity), new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

            TextView titleTv = new TextView(activity);
            titleTv.setText(Translator.get(Keys.CreditsContributors));
            titleTv.setTextColor(p.textPrimary);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            titleTv.setTypeface(mediumTf);
            titleTv.setGravity(Gravity.CENTER);
            titleTv.setSingleLine(true);
            topBar.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView closeBtn = GlassDesign.createCloseCircleButton(activity, p, subDialog::dismiss);
            topBar.addView(closeBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

            ScrollView scroll = new ScrollView(activity);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            sheet.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(activity, 14), dp(activity, 2), dp(activity, 14), getNavBarHeight(activity) + dp(activity, 22));
            scroll.addView(content, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int totalCount = 0;
            for (JSONObject group : groups) {
                JSONArray items = group.optJSONArray("items");
                if (items != null) totalCount += items.length();
            }

            final LinearLayout cardsContainer = new LinearLayout(activity);
            cardsContainer.setOrientation(LinearLayout.VERTICAL);
            final int[] activeFilter = {-1};

            final Runnable renderGroups = () -> {
                cardsContainer.removeAllViews();
                boolean first = true;
                for (int i = 0; i < groups.size(); i++) {
                    if (activeFilter[0] != -1 && activeFilter[0] != i) continue;
                    View card = buildSectionCard(
                            activity, p, mediumTf, boldTf, groups.get(i), subDialog, parentDialog, controller);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (!first) lp.topMargin = dp(activity, 12);
                    first = false;
                    cardsContainer.addView(card, lp);
                }
            };

            if (groups.size() >= 2 && totalCount >= 6) {
                HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
                filterScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout filterRow = new LinearLayout(activity);
                filterRow.setOrientation(LinearLayout.HORIZONTAL);

                final List<TextView> chips = new ArrayList<>();
                final int count = groups.size() + 1;
                for (int i = 0; i < count; i++) {
                    final int target = i - 1;
                    TextView chip = new TextView(activity);
                    chip.setText(target == -1 ? String.valueOf(totalCount) : resolveSectionTitle(groups.get(target)));
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                    chip.setTypeface(mediumTf);
                    chip.setSingleLine(true);
                    chip.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
                    LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) chipLp.setMarginStart(dp(activity, 8));
                    filterRow.addView(chip, chipLp);
                    chips.add(chip);
                }

                final Runnable updateChips = () -> {
                    for (int i = 0; i < chips.size(); i++) {
                        boolean selected = (i - 1) == activeFilter[0];
                        TextView chip = chips.get(i);
                        chip.setTextColor(selected ? p.accentBlue : p.textSecondary);
                        GradientDrawable bg = new GradientDrawable();
                        bg.setCornerRadius(dp(activity, 14));
                        bg.setColor(selected ? p.accentSoft : p.divider);
                        chip.setBackground(bg);
                    }
                };
                for (int i = 0; i < chips.size(); i++) {
                    final int target = i - 1;
                    chips.get(i).setOnClickListener(v -> {
                        activeFilter[0] = target;
                        updateChips.run();
                        renderGroups.run();
                    });
                }
                updateChips.run();
                filterScroll.addView(filterRow);
                LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                fsLp.bottomMargin = dp(activity, 12);
                content.addView(filterScroll, fsLp);
            }

            renderGroups.run();
            content.addView(cardsContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            subDialog.setContentView(scrim);
            GlassDesign.configureEdgeToEdgeSheetDialog(subDialog, p.light);

            sheet.setTranslationY(dp(activity, 44));
            sheet.setAlpha(0f);
            subDialog.show();
            GlassDesign.applySheetSystemBarAppearance(subDialog, p.light);
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

    private static String resolveSectionTitle(JSONObject sec) {
        String title = resolveLocalized(sec, "titleKey", "title", "");
        if (!TextUtils.isEmpty(title)) return title;
        String type = sec.optString("type", "");
        if ("contributors".equalsIgnoreCase(type)) {
            return Translator.getOrDefault(Keys.CreditsContributors, "Contributors");
        }
        if ("special".equalsIgnoreCase(type)) {
            return Translator.getOrDefault(Keys.CreditsSpecialThanks, "Special Thanks");
        }
        return "";
    }

    private static String resolveSectionIcon(JSONObject sec) {
        String icon = sec.optString("icon", "");
        if (!TextUtils.isEmpty(icon)) return icon;
        String type = sec.optString("type", "");
        if ("contributors".equalsIgnoreCase(type)) return "code";
        return "sparkles";
    }

    private static String resolveSectionBadgeColor(JSONObject sec) {
        String hex = sec.optString("badgeColor", "");
        if (!TextUtils.isEmpty(hex)) return hex;
        String type = sec.optString("type", "");
        if ("contributors".equalsIgnoreCase(type)) return "#2A85FF";
        if ("special".equalsIgnoreCase(type)) return "#FF9F0A";
        return "";
    }

    private static LinearLayout buildSectionCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            JSONObject sec,
            Dialog dialog,
            Dialog parentDialog,
            SettingsController controller) {

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        card.setClipToOutline(true);

        String title = resolveSectionTitle(sec);
        int badgeColor = parseColorSafe(resolveSectionBadgeColor(sec), p.accentBlue);
        int iconType = resolveIconType(resolveSectionIcon(sec));

        final boolean rtl = Translator.isRtl();
        // Compact header row
        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 10));

        ImageView badge = new ImageView(activity);
        badge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(badgeColor, dp(activity, 7), iconType, Color.WHITE));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));

        TextView headerTv = new TextView(activity);
        headerTv.setText(title);
        headerTv.setTextColor(p.accentBlue);
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        headerTv.setTypeface(mediumTf);
        headerTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        headerTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        if (rtl) {
            badgeLp.leftMargin = dp(activity, 10);
            headerRow.addView(headerTv);
            headerRow.addView(badge, badgeLp);
        } else {
            badgeLp.rightMargin = dp(activity, 10);
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
                card.addView(buildItemRow(activity, p, mediumTf, boldTf, item, dialog, parentDialog, controller));
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
            Dialog parentDialog,
            SettingsController controller) {

        String name = resolveLocalized(item, "nameKey", "name", "");
        String handle = item.optString("handle", "");
        String role = resolveItemRole(item);
        String url = item.optString("url", "");
        String iconName = item.optString("icon", "");
        int avatarBg = resolveAvatarColor(item, name, p.accentBlue);

        final boolean rtl = Translator.isRtl();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, rtl ? 14 : 16), dp(activity, 11), dp(activity, rtl ? 16 : 14), dp(activity, 11));
        row.setBackground(GlassDesign.createRowRipple(p.ripple));

        // Left avatar / icon badge (Right in RTL)
        View avatarView;
        if (!iconName.isEmpty()) {
            ImageView iv = new ImageView(activity);
            iv.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                    avatarBg, dp(activity, 10), resolveIconType(iconName), Color.WHITE));
            avatarView = iv;
        } else {
            TextView mono = new TextView(activity);
            mono.setText(extractInitial(name, handle));
            mono.setTextColor(Color.WHITE);
            mono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
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
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34));

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
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
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
            handleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
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
            roleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
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
            row.setOnClickListener(v -> openUrl(activity, url, dialog, parentDialog, controller));
        }

        if (rtl) {
            if (chev != null) {
                LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
                chevLp.rightMargin = dp(activity, 8);
                row.addView(chev, chevLp);
            }
            row.addView(textCol, colLp);
            avLp.leftMargin = dp(activity, 12);
            row.addView(avatarView, avLp);
        } else {
            avLp.rightMargin = dp(activity, 12);
            row.addView(avatarView, avLp);
            colLp.rightMargin = dp(activity, 8);
            row.addView(textCol, colLp);
            if (chev != null) {
                row.addView(chev, new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18)));
            }
        }

        return row;
    }

    private static void openUrl(Activity activity, String url, Dialog dialog, Dialog parentDialog, SettingsController controller) {
        try {
            if (dialog != null) dialog.dismiss();
            if (parentDialog != null) parentDialog.dismiss();
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
                    "{\"name\":\"TeleVip Next\",\"taglineKey\":\"CreditsTagline\",\"sections\":[{\"titleKey\":\"CreditsAuthors\",\"icon\":\"sparkles\",\"badgeColor\":\"#8B6BFF\",\"items\":[{\"name\":\"doandat943\",\"handle\":\"@doandat943\",\"roleKey\":\"CreditsMaintainerRole\",\"avatarBg\":\"#2A85FF\",\"url\":\"https://github.com/doandat943\"},{\"name\":\"Mustafa\",\"handle\":\"@mustafa1dev\",\"roleKey\":\"CreditsOriginalAuthorRole\",\"avatarBg\":\"#FF7A45\",\"url\":\"https://github.com/mustafa1dev\"}]},{\"titleKey\":\"CreditsLinks\",\"icon\":\"telegram\",\"badgeColor\":\"#28A8EA\",\"items\":[{\"nameKey\":\"DeveloperChannel\",\"handle\":\"@televipnext\",\"roleKey\":\"CreditsChannelRole\",\"icon\":\"telegram\",\"avatarBg\":\"#28A8EA\",\"url\":\"https://t.me/televipnext\"},{\"nameKey\":\"CreditsSourceCode\",\"handle\":\"GitHub\",\"roleKey\":\"CreditsSourceCodeRole\",\"icon\":\"code\",\"avatarBg\":\"#34C759\",\"url\":\"https://github.com/doandat943/TeleVip-Next-LSPosed\"}]}],\"contributors\":[{\"type\":\"contributors\",\"items\":[{\"name\":\"ronaldiputra09\",\"handle\":\"@ronaldiputra09\",\"lang\":\"id\",\"avatarBg\":\"#34C759\",\"url\":\"https://github.com/ronaldiputra09\"}]},{\"type\":\"special\",\"items\":[{\"name\":\"Soheyl | \u0633\u0647\u06cc\u0644\",\"handle\":\"@qlHlb\",\"roleKey\":\"CreditsMember\",\"avatarBg\":\"#AF52DE\",\"url\":\"https://t.me/qlHlb\"}]}]}");
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static String resolveItemRole(JSONObject item) {
        String baseRole = resolveLocalized(item, "roleKey", "role", "");
        StringBuilder langSb = new StringBuilder();
        String singleLang = item.optString("lang", "");
        if (!TextUtils.isEmpty(singleLang)) {
            langSb.append(Translator.getLanguageDisplayName(singleLang));
        }
        JSONArray langs = item.optJSONArray("langs");
        if (langs != null) {
            for (int i = 0; i < langs.length(); i++) {
                String code = langs.optString(i, "");
                if (TextUtils.isEmpty(code)) continue;
                if (langSb.length() > 0) langSb.append(" · ");
                langSb.append(Translator.getLanguageDisplayName(code));
            }
        }
        if (!TextUtils.isEmpty(baseRole) && langSb.length() > 0) {
            return baseRole + " · " + langSb;
        }
        return !TextUtils.isEmpty(baseRole) ? baseRole : langSb.toString();
    }

    private static int resolveAvatarColor(JSONObject item, String name, int fallback) {
        String hex = item.optString("avatarBg", "");
        if (!TextUtils.isEmpty(hex)) {
            return parseColorSafe(hex, fallback);
        }
        if (!TextUtils.isEmpty(name)) {
            return AUTO_AVATAR_COLORS[Math.abs(name.hashCode()) % AUTO_AVATAR_COLORS.length];
        }
        return fallback;
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
        return "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
    }

    private static String resolveClientVersion(Activity activity) {
        try {
            String pkg = !TextUtils.isEmpty(Utils.pkgName) ? Utils.pkgName : activity.getPackageName();
            if (TextUtils.isEmpty(pkg)) return "";
            PackageInfo pi = activity.getPackageManager().getPackageInfo(pkg, 0);
            if (pi == null || pi.versionName == null) return "";
            String vn = pi.versionName.trim();
            if (vn.startsWith("v") || vn.startsWith("V")) {
                vn = vn.substring(1).trim();
            }
            if (vn.isEmpty()) return "";
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? pi.getLongVersionCode() : pi.versionCode;
            return code > 0 ? ("v" + vn + " (" + code + ")") : ("v" + vn);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static int resolveIconType(String iconName) {
        if ("telegram".equalsIgnoreCase(iconName)) return GlassDesign.ICON_TELEGRAM;
        if ("code".equalsIgnoreCase(iconName)) return GlassDesign.ICON_CODE;
        if ("ghost".equalsIgnoreCase(iconName)) return GlassDesign.ICON_GHOST;
        if ("language".equalsIgnoreCase(iconName)) return GlassDesign.ICON_LANGUAGE;
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
