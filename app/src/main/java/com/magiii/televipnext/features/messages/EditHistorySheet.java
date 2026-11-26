package com.magiii.televipnext.features.messages;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
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

import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.settings.ui.GlassDesign;
import com.magiii.televipnext.virtuals.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edits History bottom sheet styled to match TeleVip Next Settings
 * using shared {@link GlassDesign} surfaces, segmented tabs, icon badges, and line+word diff.
 */
final class EditHistorySheet {

    static final class VersionEntry {
        final int versionNumber;
        final String text;
        final String formattedDate;
        final boolean isCurrent;

        VersionEntry(int versionNumber, String text, String formattedDate, boolean isCurrent) {
            this.versionNumber = versionNumber;
            this.text = text != null ? text : "";
            this.formattedDate = formattedDate != null ? formattedDate : "";
            this.isCurrent = isCurrent;
        }
    }

    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("\\S+|[ \\t]+");

    private static final int OP_SAME = 0;
    private static final int OP_DEL = 1;
    private static final int OP_ADD = 2;

    private static final class LineOp {
        final int op;
        final String text;

        LineOp(int op, String text) {
            this.op = op;
            this.text = text;
        }
    }

    private static final class DiffColors {
        final int copyBg;
        final int copyText;
        final int delBg;
        final int delText;
        final int addBg;
        final int addText;
        final int handle;

        DiffColors(boolean light) {
            if (light) {
                copyBg = Color.argb(24, 33, 133, 213);
                copyText = Color.parseColor("#1B74BD");
                delBg = Color.argb(20, 239, 68, 68);
                delText = Color.parseColor("#94A3B8");
                addBg = Color.argb(36, 33, 133, 213);
                addText = Color.parseColor("#1469B0");
                handle = Color.argb(42, 15, 23, 42);
            } else {
                copyBg = Color.argb(36, 75, 163, 227);
                copyText = Color.parseColor("#6BB8F0");
                delBg = Color.argb(31, 248, 113, 113);
                delText = Color.parseColor("#7E8B9B");
                addBg = Color.argb(56, 75, 163, 227);
                addText = Color.parseColor("#7CC4F8");
                handle = Color.argb(56, 255, 255, 255);
            }
        }
    }

    private EditHistorySheet() {}

    static void show(Activity activity, String title, int msgId, List<VersionEntry> chronologicalEntries) {
        if (activity == null || chronologicalEntries == null || chronologicalEntries.isEmpty()) return;

        final boolean light = Theme.isLight();
        final int[] tabState = new int[]{0}; // 0 = History, 1 = Changes
        final boolean[] showUnchangedDiff = new boolean[]{true};
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Runnable[] hideToastHolder = new Runnable[1];

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final FrameLayout scrim = new FrameLayout(activity);
        scrim.setLayoutDirection(Translator.isRtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setOnClickListener(v -> dialog.dismiss());

        final int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        final int maxSheetH = Math.min((int) (screenH * 0.84f), dp(activity, 700));
        final float topRadius = dp(activity, 28);

        final LinearLayout sheet = new LinearLayout(activity) {
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
        sheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + Math.round(topRadius), topRadius);
            }
        });
        sheet.setClipToOutline(true);
        scrim.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // Floating pill toast overlay
        final TextView toastView = new TextView(activity);
        toastView.setTextColor(Color.WHITE);
        toastView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        toastView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

        buildSheetContents(
                activity, dialog, sheet, title, msgId, chronologicalEntries,
                light, tabState, showUnchangedDiff, mainHandler, toastView,
                hideToastHolder);

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
    }

    private static void buildSheetContents(
            final Activity activity,
            final Dialog dialog,
            final LinearLayout sheet,
            final String title,
            final int msgId,
            final List<VersionEntry> chronologicalEntries,
            final boolean light,
            final int[] tabState,
            final boolean[] showUnchangedDiff,
            final Handler mainHandler,
            final TextView toastView,
            final Runnable[] hideToastHolder) {

        final GlassDesign.Palette p = GlassDesign.Palette.of(light);
        final DiffColors dc = new DiffColors(light);
        final Typeface mediumTf = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        final Typeface boldTf = Typeface.DEFAULT_BOLD;

        sheet.setBackground(GlassDesign.createAuroraBackground(p));

        // 1. Grab handle
        View handle = new View(activity);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(dc.handle);
        handleBg.setCornerRadius(dp(activity, 2));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(activity, 10);
        handleLp.bottomMargin = dp(activity, 10);
        sheet.addView(handle, handleLp);

        // 2. Frosted Top Bar: [36dp Spacer]  [Title + Subtitle]  [Close Glass Circle]
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarLp.setMargins(dp(activity, 14), 0, dp(activity, 14), dp(activity, 12));
        sheet.addView(topBar, topBarLp);

        topBar.addView(new View(activity), new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.leftMargin = dp(activity, 8);
        titleColLp.rightMargin = dp(activity, 8);
        topBar.addView(titleCol, titleColLp);

        TextView titleTv = new TextView(activity);
        titleTv.setText(!TextUtils.isEmpty(title) ? title : Translator.get(Keys.EditsHistory));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        titleTv.setTypeface(mediumTf);
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleCol.addView(titleTv);

        int total = chronologicalEntries.size();
        String subStr;
        if (msgId > 0) {
            String fmtKey = total == 1 ? Keys.MessageVersionSummaryOne : Keys.MessageVersionSummaryMany;
            subStr = String.format(Translator.get(fmtKey), msgId, total);
        } else {
            String fmtKey = total == 1 ? Keys.VersionsCountOne : Keys.VersionsCountMany;
            subStr = String.format(Translator.get(fmtKey), total);
        }
        TextView subTv = new TextView(activity);
        subTv.setText(subStr);
        subTv.setTextColor(p.textMuted);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        subTv.setTypeface(mediumTf);
        subTv.setGravity(Gravity.CENTER);
        subTv.setSingleLine(true);
        titleCol.addView(subTv);

        ImageView closeBtn = GlassDesign.createCloseCircleButton(activity, p, dialog::dismiss);
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));

        // 3. Segmented Pill Tabs ("History" | "Changes")
        GlassDesign.SegmentedTabBar tabBar = null;
        if (total >= 2) {
            tabBar = new GlassDesign.SegmentedTabBar(
                    activity,
                    new String[]{
                            Translator.get(Keys.HistoryTab),
                            Translator.get(Keys.ChangesTab)
                    },
                    p, tabState[0]);
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40));
            tabLp.setMargins(dp(activity, 14), 0, dp(activity, 14), dp(activity, 12));
            sheet.addView(tabBar, tabLp);
        }

        // 4. Scrollable Cards Stack
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        scrollView.setClipToPadding(false);
        int navH = getNavBarHeight(activity);
        scrollView.setPadding(dp(activity, 14), 0, dp(activity, 14), navH + dp(activity, 18));
        sheet.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final LinearLayout cardsStack = new LinearLayout(activity);
        cardsStack.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(cardsStack, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable renderCards = new Runnable() {
            @Override
            public void run() {
                renderCardsStack(
                        activity, cardsStack, p, dc, mediumTf, boldTf,
                        chronologicalEntries, tabState[0] == 1, showUnchangedDiff,
                        mainHandler, toastView, hideToastHolder, this);
            }
        };
        renderCards.run();

        if (tabBar != null) {
            tabBar.setOnTabSelectedListener(index -> {
                tabState[0] = index;
                renderCards.run();
            });
        }
    }

    private static void renderCardsStack(
            final Activity activity,
            final LinearLayout stack,
            final GlassDesign.Palette p,
            final DiffColors dc,
            final Typeface mediumTf,
            final Typeface boldTf,
            final List<VersionEntry> chronologicalEntries,
            final boolean isChangesTab,
            final boolean[] showUnchangedDiff,
            final Handler mainHandler,
            final TextView toastView,
            final Runnable[] hideToastHolder,
            final Runnable rerenderCards) {

        stack.removeAllViews();
        final int total = chronologicalEntries.size();

        // Card 0 (Changes tab only): "Show unchanged lines" switch card
        if (isChangesTab) {
            LinearLayout filterCard = new LinearLayout(activity);
            filterCard.setOrientation(LinearLayout.HORIZONTAL);
            filterCard.setGravity(Gravity.CENTER_VERTICAL);
            filterCard.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
            filterCard.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));
            LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fLp.bottomMargin = dp(activity, 12);
            stack.addView(filterCard, fLp);

            View badge = new View(activity);
            badge.setBackground(createBadgeDrawable(activity, Color.parseColor("#26A6C8"), gitCompareGlyph()));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));
            bLp.rightMargin = dp(activity, 10);
            filterCard.addView(badge, bLp);

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            filterCard.addView(textCol, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView ft = new TextView(activity);
            ft.setText(Translator.get(Keys.ShowUnchangedLines));
            ft.setTextColor(p.textPrimary);
            ft.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            ft.setTypeface(mediumTf);
            textCol.addView(ft);

            TextView fs = new TextView(activity);
            fs.setText(Translator.get(Keys.HideContextLines));
            fs.setTextColor(p.textMuted);
            fs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            textCol.addView(fs);

            final GlassDesign.PillSwitchView sw = new GlassDesign.PillSwitchView(activity, showUnchangedDiff[0], p);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 26));
            swLp.leftMargin = dp(activity, 10);
            filterCard.addView(sw, swLp);

            filterCard.setOnClickListener(v -> {
                showUnchangedDiff[0] = !showUnchangedDiff[0];
                sw.setChecked(showUnchangedDiff[0], true);
                mainHandler.postDelayed(rerenderCards, 120);
            });
        }

        // Card 1: Current Version Card
        final VersionEntry currentEntry = chronologicalEntries.get(total - 1);
        final VersionEntry prevToCurrent = total >= 2 ? chronologicalEntries.get(total - 2) : null;

        LinearLayout currentCard = new LinearLayout(activity);
        currentCard.setOrientation(LinearLayout.VERTICAL);
        currentCard.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ccLp.bottomMargin = dp(activity, 12);
        stack.addView(currentCard, ccLp);

        LinearLayout ccHeader = new LinearLayout(activity);
        ccHeader.setOrientation(LinearLayout.HORIZONTAL);
        ccHeader.setGravity(Gravity.CENTER_VERTICAL);
        ccHeader.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 10));
        currentCard.addView(ccHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View ccBadge = new View(activity);
        ccBadge.setBackground(createBadgeDrawable(activity, Color.parseColor("#26A6C8"), checkCircleGlyph()));
        LinearLayout.LayoutParams ccbLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));
        ccbLp.rightMargin = dp(activity, 10);
        ccHeader.addView(ccBadge, ccbLp);

        TextView ccTitle = new TextView(activity);
        ccTitle.setText(Translator.get(Keys.CurrentVersion));
        ccTitle.setTextColor(p.accentBlue);
        ccTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        ccTitle.setTypeface(boldTf);
        LinearLayout.LayoutParams cctLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cctLp.rightMargin = dp(activity, 8);
        ccHeader.addView(ccTitle, cctLp);

        TextView latestPill = new TextView(activity);
        latestPill.setText(Translator.get(Keys.LatestBadge));
        latestPill.setTextColor(dc.copyText);
        latestPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        latestPill.setTypeface(boldTf);
        latestPill.setPadding(dp(activity, 8), dp(activity, 2), dp(activity, 8), dp(activity, 2));
        latestPill.setBackground(createSolidPillBg(dc.copyBg, dp(activity, 12)));
        ccHeader.addView(latestPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ccHeader.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));

        final String copyLabel = Translator.get(Keys.CopyAction);
        View ccCopy = buildCopyPillButton(activity, dc, p.ripple, copyLabel, copyGlyph(dc.copyText), v -> {
            copyToClipboard(activity, currentEntry.text);
            showFloatingToast(mainHandler, toastView, hideToastHolder,
                    Translator.get(Keys.CopiedCurrent));
        });
        ccHeader.addView(ccCopy);

        addDivider(activity, currentCard, p.divider);

        LinearLayout ccBody = new LinearLayout(activity);
        ccBody.setOrientation(LinearLayout.VERTICAL);
        ccBody.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 14));
        currentCard.addView(ccBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!TextUtils.isEmpty(currentEntry.formattedDate)) {
            TextView dateTv = new TextView(activity);
            dateTv.setText(currentEntry.formattedDate);
            dateTv.setTextColor(p.textMuted);
            dateTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dLp.bottomMargin = dp(activity, 6);
            ccBody.addView(dateTv, dLp);
        }

        TextView currentTextTv = new TextView(activity);
        currentTextTv.setTextColor(p.textPrimary);
        currentTextTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        currentTextTv.setLineSpacing(dp(activity, 2), 1.32f);
        currentTextTv.setTextIsSelectable(true);
        if (isChangesTab && prevToCurrent != null) {
            CharSequence diff = buildLineWordDiff(prevToCurrent.text, currentEntry.text, dc, p, showUnchangedDiff[0]);
            if (TextUtils.isEmpty(diff)) {
                currentTextTv.setTextColor(p.textMuted);
                currentTextTv.setText(Translator.get(Keys.NoTextChangesPrev));
            } else {
                currentTextTv.setText(diff);
            }
        } else {
            currentTextTv.setText(currentEntry.text);
        }
        ccBody.addView(currentTextTv);

        // Card 2: Previous Edits Card (when >= 2 versions exist)
        if (total >= 2) {
            LinearLayout prevCard = new LinearLayout(activity);
            prevCard.setOrientation(LinearLayout.VERTICAL);
            prevCard.setBackground(GlassDesign.createGlassCardDrawable(p, dp(activity, 22)));
            LinearLayout.LayoutParams pcLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stack.addView(prevCard, pcLp);

            LinearLayout pcHeader = new LinearLayout(activity);
            pcHeader.setOrientation(LinearLayout.HORIZONTAL);
            pcHeader.setGravity(Gravity.CENTER_VERTICAL);
            pcHeader.setPadding(dp(activity, 16), dp(activity, 13), dp(activity, 16), dp(activity, 10));
            prevCard.addView(pcHeader, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View pcBadge = new View(activity);
            pcBadge.setBackground(createBadgeDrawable(activity, Color.parseColor("#7C4DFF"), historyGlyph()));
            LinearLayout.LayoutParams pcbLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));
            pcbLp.rightMargin = dp(activity, 10);
            pcHeader.addView(pcBadge, pcbLp);

            TextView pcTitle = new TextView(activity);
            pcTitle.setText(Translator.get(Keys.PreviousEdits));
            pcTitle.setTextColor(p.accentBlue);
            pcTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            pcTitle.setTypeface(boldTf);
            pcHeader.addView(pcTitle, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            View copyAllBtn = buildCopyPillButton(activity, dc, p.ripple,
                    Translator.get(Keys.CopyAllAction),
                    copyCheckGlyph(dc.copyText), v -> {
                StringBuilder all = new StringBuilder();
                for (int r = total - 1; r >= 0; r--) {
                    VersionEntry e = chronologicalEntries.get(r);
                    if (all.length() > 0) all.append("\n\n");
                    String lbl = formatVersionLabel(e, r);
                    all.append("[").append(lbl);
                    if (!TextUtils.isEmpty(e.formattedDate)) {
                        all.append(" · ").append(e.formattedDate);
                    }
                    all.append("]\n").append(e.text);
                }
                copyToClipboard(activity, all.toString());
                showFloatingToast(mainHandler, toastView, hideToastHolder,
                        String.format(Translator.get(Keys.CopiedAllVersions), total));
            });
            pcHeader.addView(copyAllBtn);

            for (int rev = total - 2; rev >= 0; rev--) {
                final int idx = rev;
                final VersionEntry entry = chronologicalEntries.get(idx);
                final VersionEntry prevEntry = idx > 0 ? chronologicalEntries.get(idx - 1) : null;
                final String rowLabel = formatVersionLabel(entry, idx);

                addDivider(activity, prevCard, p.divider);

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(activity, 16), dp(activity, 11), dp(activity, 16), dp(activity, 12));
                prevCard.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout metaRow = new LinearLayout(activity);
                metaRow.setOrientation(LinearLayout.HORIZONTAL);
                metaRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                mLp.bottomMargin = dp(activity, 6);
                row.addView(metaRow, mLp);

                LinearLayout labelCol = new LinearLayout(activity);
                labelCol.setOrientation(LinearLayout.HORIZONTAL);
                labelCol.setGravity(Gravity.CENTER_VERTICAL);
                metaRow.addView(labelCol, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView lblTv = new TextView(activity);
                lblTv.setText(rowLabel);
                lblTv.setTextColor(p.textPrimary);
                lblTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                lblTv.setTypeface(boldTf);
                labelCol.addView(lblTv);

                if (!TextUtils.isEmpty(entry.formattedDate)) {
                    TextView tsTv = new TextView(activity);
                    tsTv.setText("  ·  " + entry.formattedDate);
                    tsTv.setTextColor(p.textMuted);
                    tsTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
                    tsTv.setSingleLine(true);
                    tsTv.setEllipsize(TextUtils.TruncateAt.END);
                    labelCol.addView(tsTv);
                }

                View rowCopy = buildCopyPillButton(activity, dc, p.ripple, copyLabel, copyGlyph(dc.copyText), v -> {
                    copyToClipboard(activity, entry.text);
                    showFloatingToast(mainHandler, toastView, hideToastHolder,
                            String.format(Translator.get(Keys.CopiedItem), rowLabel));
                });
                metaRow.addView(rowCopy);

                TextView bodyTv = new TextView(activity);
                bodyTv.setTextColor(p.textPrimary);
                bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                bodyTv.setLineSpacing(dp(activity, 2), 1.32f);
                bodyTv.setTextIsSelectable(true);
                if (isChangesTab && prevEntry != null) {
                    CharSequence diff = buildLineWordDiff(prevEntry.text, entry.text, dc, p, showUnchangedDiff[0]);
                    if (TextUtils.isEmpty(diff)) {
                        bodyTv.setTextColor(p.textMuted);
                        bodyTv.setText(Translator.get(Keys.NoTextChanges));
                    } else {
                        bodyTv.setText(diff);
                    }
                } else {
                    bodyTv.setText(entry.text);
                }
                row.addView(bodyTv);
            }
        }

        // Muted Settings-Style Footer Note
        TextView noteTv = new TextView(activity);
        noteTv.setText(Translator.get(Keys.EditHistoryFooterNote));
        noteTv.setTextColor(p.textMuted);
        noteTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        noteTv.setLineSpacing(0f, 1.35f);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.setMargins(dp(activity, 8), dp(activity, 12), dp(activity, 8), 0);
        stack.addView(noteTv, noteLp);
    }

    private static String formatVersionLabel(VersionEntry entry, int chronologicalIndex) {
        if (entry.isCurrent) return Translator.get(Keys.CurrentVersion);
        if (chronologicalIndex == 0) return Translator.get(Keys.OriginalVersion);
        return String.format(Translator.get(Keys.EditVersionNum), entry.versionNumber);
    }

    private static View buildCopyPillButton(
            Context ctx, DiffColors dc, int rippleColor, String text, Drawable icon, View.OnClickListener onClick) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
        pill.setBackground(createSolidPillBg(dc.copyBg, dp(ctx, 14)));
        pill.setForeground(GlassDesign.createPillRippleOverlay(rippleColor, dp(ctx, 14)));
        pill.setOnClickListener(onClick);

        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 12), dp(ctx, 12));
            iLp.rightMargin = dp(ctx, 4);
            pill.addView(iv, iLp);
        }

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(dc.copyText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(tv);
        return pill;
    }

    private static void addDivider(Context ctx, LinearLayout parent, int color) {
        View d = new View(ctx);
        d.setBackgroundColor(color);
        int h = Math.max(1, Math.round(ctx.getResources().getDisplayMetrics().density * 0.7f));
        parent.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
    }

    private static void copyToClipboard(Context ctx, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Edit History", text));
            }
        } catch (Throwable ignored) {}
    }

    private static void showFloatingToast(
            Handler handler, TextView toastView, Runnable[] hideHolder, String message) {
        if (hideHolder[0] != null) {
            handler.removeCallbacks(hideHolder[0]);
        }
        toastView.setText(message);
        toastView.setVisibility(View.VISIBLE);
        toastView.animate().cancel();
        toastView.setTranslationY(dp(toastView.getContext(), 12));
        toastView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();

        Runnable hide = () -> toastView.animate()
                .alpha(0f)
                .translationY(dp(toastView.getContext(), 12))
                .setDuration(180)
                .withEndAction(() -> toastView.setVisibility(View.GONE))
                .start();
        hideHolder[0] = hide;
        handler.postDelayed(hide, 1800);
    }

    /**
     * Line-by-line LCS diff with inline word-level diff when a delete block pairs cleanly with an add block.
     */
    private static CharSequence buildLineWordDiff(
            String prevText, String currText, DiffColors dc, GlassDesign.Palette p, boolean showUnchanged) {
        if (prevText == null) prevText = "";
        if (currText == null) currText = "";

        List<String> linesA = splitLines(prevText);
        List<String> linesB = splitLines(currText);
        if (linesA.size() > 200 || linesB.size() > 200) {
            return currText;
        }

        int n = linesA.size();
        int m = linesB.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (linesA.get(i).equals(linesB.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        List<LineOp> ops = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (linesA.get(i).equals(linesB.get(j))) {
                ops.add(new LineOp(OP_SAME, linesA.get(i)));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new LineOp(OP_DEL, linesA.get(i)));
                i++;
            } else {
                ops.add(new LineOp(OP_ADD, linesB.get(j)));
                j++;
            }
        }
        while (i < n) ops.add(new LineOp(OP_DEL, linesA.get(i++)));
        while (j < m) ops.add(new LineOp(OP_ADD, linesB.get(j++)));

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int idx = 0;
        while (idx < ops.size()) {
            LineOp op = ops.get(idx);
            if (op.op == OP_SAME) {
                if (showUnchanged) {
                    appendLineBreakIfNeeded(ssb);
                    int s = ssb.length();
                    ssb.append(op.text);
                    ssb.setSpan(new ForegroundColorSpan(p.textPrimary), s, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                idx++;
                continue;
            }

            List<String> dels = new ArrayList<>();
            while (idx < ops.size() && ops.get(idx).op == OP_DEL) {
                dels.add(ops.get(idx).text);
                idx++;
            }
            List<String> adds = new ArrayList<>();
            while (idx < ops.size() && ops.get(idx).op == OP_ADD) {
                adds.add(ops.get(idx).text);
                idx++;
            }

            int pairCount = Math.min(dels.size(), adds.size());
            int k = 0;
            for (; k < pairCount; k++) {
                appendLineBreakIfNeeded(ssb);
                appendInlineWordDiff(ssb, dels.get(k), adds.get(k), dc, p);
            }
            for (int d = k; d < dels.size(); d++) {
                appendLineBreakIfNeeded(ssb);
                appendDiffSpan(ssb, dels.get(d), dc.delBg, dc.delText, true);
            }
            for (int a = k; a < adds.size(); a++) {
                appendLineBreakIfNeeded(ssb);
                appendDiffSpan(ssb, adds.get(a), dc.addBg, dc.addText, false);
            }
        }
        return ssb;
    }

    private static void appendInlineWordDiff(
            SpannableStringBuilder ssb, String lineA, String lineB, DiffColors dc, GlassDesign.Palette p) {
        List<String> a = tokenizeWords(lineA);
        List<String> b = tokenizeWords(lineB);
        if (a.size() > 120 || b.size() > 120) {
            appendDiffSpan(ssb, lineA, dc.delBg, dc.delText, true);
            ssb.append(" ");
            appendDiffSpan(ssb, lineB, dc.addBg, dc.addText, false);
            return;
        }

        int n = a.size();
        int m = b.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (a.get(i).equals(b.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<LineOp> segs = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                segs.add(new LineOp(OP_SAME, a.get(i)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                segs.add(new LineOp(OP_DEL, a.get(i)));
                i++;
            } else {
                segs.add(new LineOp(OP_ADD, b.get(j)));
                j++;
            }
        }
        while (i < n) segs.add(new LineOp(OP_DEL, a.get(i++)));
        while (j < m) segs.add(new LineOp(OP_ADD, b.get(j++)));

        List<LineOp> merged = new ArrayList<>();
        for (LineOp s : segs) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).op == s.op) {
                LineOp last = merged.remove(merged.size() - 1);
                merged.add(new LineOp(s.op, last.text + s.text));
            } else {
                merged.add(s);
            }
        }

        for (LineOp seg : merged) {
            String t = seg.text;
            if (isOnlySpaces(t)) {
                ssb.append(t);
                continue;
            }
            if (seg.op == OP_DEL) {
                appendDiffSpan(ssb, t.trim(), dc.delBg, dc.delText, true);
                if (t.endsWith(" ")) ssb.append(" ");
            } else if (seg.op == OP_ADD) {
                if (t.startsWith(" ")) ssb.append(" ");
                appendDiffSpan(ssb, t.trim(), dc.addBg, dc.addText, false);
                if (t.endsWith(" ") && t.trim().length() > 0) ssb.append(" ");
            } else {
                int start = ssb.length();
                ssb.append(t);
                ssb.setSpan(new ForegroundColorSpan(p.textPrimary), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static void appendLineBreakIfNeeded(SpannableStringBuilder ssb) {
        if (ssb.length() > 0) ssb.append("\n");
    }

    private static void appendDiffSpan(
            SpannableStringBuilder ssb, String text, int bg, int fg, boolean strike) {
        if (text == null || text.isEmpty()) return;
        int start = ssb.length();
        ssb.append(" ").append(text).append(" ");
        int end = ssb.length();
        ssb.setSpan(new BackgroundColorSpan(bg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(fg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (strike) {
            ssb.setSpan(new StrikethroughSpan(), start + 1, end - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static List<String> splitLines(String text) {
        return new ArrayList<>(Arrays.asList(text.split("\n", -1)));
    }

    private static List<String> tokenizeWords(String text) {
        List<String> out = new ArrayList<>();
        Matcher matcher = WORD_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            out.add(matcher.group());
        }
        return out;
    }

    private static boolean isOnlySpaces(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    private static Drawable createSolidPillBg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static Drawable createBadgeDrawable(Context ctx, int bgColor, VectorGlyph glyph) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 9));
        Drawable fg = new VectorDrawable24(glyph);
        return new LayerDrawable(new Drawable[]{bg, fg});
    }

    private interface VectorGlyph {
        void draw24(Canvas c, Paint stroke, Paint fill);
    }

    private static VectorGlyph checkCircleGlyph() {
        return (c, stroke, fill) -> {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2.0f);
            c.drawCircle(12f, 12f, 8.5f, stroke);
            Path p = new Path();
            p.moveTo(8.5f, 12.2f);
            p.lineTo(10.8f, 14.5f);
            p.lineTo(15.6f, 9.6f);
            c.drawPath(p, stroke);
        };
    }

    private static VectorGlyph historyGlyph() {
        return (c, stroke, fill) -> {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2.0f);
            RectF r = new RectF(4.2f, 4.2f, 19.8f, 19.8f);
            c.drawArc(r, -155f, 295f, false, stroke);
            Path arrow = new Path();
            arrow.moveTo(4.2f, 6.2f);
            arrow.lineTo(4.2f, 9.6f);
            arrow.lineTo(7.6f, 9.6f);
            c.drawPath(arrow, stroke);
            c.drawLine(12f, 7.8f, 12f, 12.2f, stroke);
            c.drawLine(12f, 12.2f, 15.2f, 14.0f, stroke);
        };
    }

    private static VectorGlyph gitCompareGlyph() {
        return (c, stroke, fill) -> {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2.0f);
            c.drawCircle(17.5f, 17.5f, 2.5f, stroke);
            c.drawCircle(6.5f, 6.5f, 2.5f, stroke);
            Path p1 = new Path();
            p1.moveTo(12.5f, 6.5f);
            p1.lineTo(15.5f, 6.5f);
            p1.quadTo(17.5f, 6.5f, 17.5f, 8.5f);
            p1.lineTo(17.5f, 15.0f);
            c.drawPath(p1, stroke);
            Path p2 = new Path();
            p2.moveTo(11.5f, 17.5f);
            p2.lineTo(8.5f, 17.5f);
            p2.quadTo(6.5f, 17.5f, 6.5f, 15.5f);
            p2.lineTo(6.5f, 9.0f);
            c.drawPath(p2, stroke);
        };
    }

    private static Drawable copyGlyph(final int color) {
        return new VectorDrawable24((c, stroke, fill) -> {
            stroke.setColor(color);
            stroke.setStrokeWidth(2.0f);
            c.drawRoundRect(new RectF(8.5f, 8.5f, 20f, 20f), 2.5f, 2.5f, stroke);
            Path back = new Path();
            back.moveTo(5.8f, 15.5f);
            back.lineTo(5.2f, 15.5f);
            back.quadTo(4f, 15.5f, 4f, 14.3f);
            back.lineTo(4f, 5.2f);
            back.quadTo(4f, 4f, 5.2f, 4f);
            back.lineTo(14.3f, 4f);
            back.quadTo(15.5f, 4f, 15.5f, 5.2f);
            back.lineTo(15.5f, 5.8f);
            c.drawPath(back, stroke);
        });
    }

    private static Drawable copyCheckGlyph(final int color) {
        return new VectorDrawable24((c, stroke, fill) -> {
            stroke.setColor(color);
            stroke.setStrokeWidth(2.0f);
            c.drawRoundRect(new RectF(8.5f, 8.5f, 20f, 20f), 2.5f, 2.5f, stroke);
            Path tick = new Path();
            tick.moveTo(11.5f, 14.4f);
            tick.lineTo(13.4f, 16.3f);
            tick.lineTo(17.1f, 12.2f);
            c.drawPath(tick, stroke);
            Path back = new Path();
            back.moveTo(5.8f, 15.5f);
            back.lineTo(5.2f, 15.5f);
            back.quadTo(4f, 15.5f, 4f, 14.3f);
            back.lineTo(4f, 5.2f);
            back.quadTo(4f, 4f, 5.2f, 4f);
            back.lineTo(14.3f, 4f);
            back.quadTo(15.5f, 4f, 15.5f, 5.2f);
            back.lineTo(15.5f, 5.8f);
            c.drawPath(back, stroke);
        });
    }

    private static final class VectorDrawable24 extends Drawable {
        private final VectorGlyph glyph;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

        VectorDrawable24(VectorGlyph glyph) {
            this.glyph = glyph;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height());
            if (size <= 0f) return;
            canvas.save();
            canvas.translate(b.left + (b.width() - size) * 0.5f, b.top + (b.height() - size) * 0.5f);
            float scale = size / 24f;
            canvas.scale(scale, scale);
            glyph.draw24(canvas, stroke, fill);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static int getNavBarHeight(Context ctx) {
        try {
            int resId = ctx.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resId > 0) {
                return ctx.getResources().getDimensionPixelSize(resId);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static int dp(Context ctx, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics()));
    }
}
