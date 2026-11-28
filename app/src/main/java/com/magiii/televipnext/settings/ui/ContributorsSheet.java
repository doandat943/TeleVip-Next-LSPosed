package com.magiii.televipnext.settings.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.settings.controller.SettingsController;

import org.json.JSONObject;

import java.util.List;

/**
 * Liquid Glass sub-sheet displaying live GitHub contributors in a 2-column Bento grid
 * with a proportional contribution share bar, plus hardcoded Special Thanks entries.
 */
final class ContributorsSheet {

    private ContributorsSheet() {}

    static void show(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            List<JSONObject> specialThanks,
            Dialog parentDialog,
            SettingsController controller) {
        try {
            CreditsSheet.SheetScaffold scaffold = CreditsSheet.createSheetScaffold(
                    activity, p, mediumTf, Translator.get(Keys.CreditsContributors), 0.84f, 680);

            ContributorsRepository.Listener[] listenerHolder = new ContributorsRepository.Listener[1];
            scaffold.dialog.setOnDismissListener(d -> {
                if (listenerHolder[0] != null) {
                    ContributorsRepository.removeListener(listenerHolder[0]);
                    listenerHolder[0] = null;
                }
            });

            final LinearLayout bodyContainer = new LinearLayout(activity);
            bodyContainer.setOrientation(LinearLayout.VERTICAL);
            scaffold.content.addView(bodyContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final int[] activeFilter = {-1};
            renderBody(
                    activity, p, mediumTf, boldTf, bodyContainer,
                    ContributorsRepository.getCached(), specialThanks,
                    ContributorsRepository.isLoading(), activeFilter,
                    scaffold.dialog, parentDialog, controller);

            listenerHolder[0] = (contributors, loading) -> {
                if (scaffold.dialog.isShowing()) {
                    renderBody(
                            activity, p, mediumTf, boldTf, bodyContainer,
                            contributors, specialThanks, loading, activeFilter,
                            scaffold.dialog, parentDialog, controller);
                }
            };
            ContributorsRepository.addListener(listenerHolder[0]);
            ContributorsRepository.refreshIfNeeded();

            scaffold.present(activity, p.light);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void renderBody(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            LinearLayout bodyContainer,
            List<ContributorsRepository.Contributor> ghContributors,
            List<JSONObject> specialThanks,
            boolean loading,
            int[] activeFilter,
            Dialog subDialog,
            Dialog parentDialog,
            SettingsController controller) {
        bodyContainer.removeAllViews();

        if (activeFilter[0] == 1 && specialThanks.isEmpty()) {
            activeFilter[0] = -1;
        }

        int totalCount = ghContributors.size() + specialThanks.size();
        if (!ghContributors.isEmpty() && !specialThanks.isEmpty() && totalCount >= 6) {
            HorizontalScrollView filterScroll = new HorizontalScrollView(activity);
            filterScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout filterRow = new LinearLayout(activity);
            filterRow.setOrientation(LinearLayout.HORIZONTAL);

            String[] labels = new String[]{
                    String.valueOf(totalCount),
                    Translator.get(Keys.CreditsContributors),
                    Translator.get(Keys.CreditsSpecialThanks)
            };
            for (int i = 0; i < labels.length; i++) {
                final int target = i - 1;
                boolean selected = target == activeFilter[0];
                TextView chip = new TextView(activity);
                chip.setText(labels[i]);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                chip.setTypeface(mediumTf);
                chip.setSingleLine(true);
                chip.setPadding(CreditsSheet.dp(activity, 12), CreditsSheet.dp(activity, 6),
                        CreditsSheet.dp(activity, 12), CreditsSheet.dp(activity, 6));
                chip.setTextColor(selected ? p.accentBlue : p.textSecondary);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(CreditsSheet.dp(activity, 14));
                bg.setColor(selected ? p.accentSoft : p.divider);
                chip.setBackground(bg);
                chip.setOnClickListener(v -> {
                    activeFilter[0] = target;
                    renderBody(
                            activity, p, mediumTf, boldTf, bodyContainer,
                            ghContributors, specialThanks, loading, activeFilter,
                            subDialog, parentDialog, controller);
                });
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) chipLp.setMarginStart(CreditsSheet.dp(activity, 8));
                filterRow.addView(chip, chipLp);
            }
            filterScroll.addView(filterRow);
            LinearLayout.LayoutParams fsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fsLp.bottomMargin = CreditsSheet.dp(activity, 12);
            bodyContainer.addView(filterScroll, fsLp);
        }

        boolean addedCard = false;
        if (activeFilter[0] == -1 || activeFilter[0] == 0) {
            final boolean rtl = Translator.isRtl();
            LinearLayout ghCard = new LinearLayout(activity);
            ghCard.setOrientation(LinearLayout.VERTICAL);
            ghCard.setBackground(GlassDesign.createGlassCardDrawable(p, CreditsSheet.dp(activity, 22)));
            ghCard.setClipToOutline(true);

            LinearLayout headerRow = CreditsSheet.createSectionHeaderRow(
                    activity, p, mediumTf,
                    Translator.get(Keys.CreditsContributors),
                    CreditsSheet.parseColorSafe("#2A85FF", p.accentBlue),
                    GlassDesign.ICON_CODE);
            if (!ghContributors.isEmpty()) {
                TextView countPill = new TextView(activity);
                countPill.setText(String.valueOf(ghContributors.size()));
                countPill.setTextColor(p.accentBlue);
                countPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                countPill.setTypeface(mediumTf);
                countPill.setSingleLine(true);
                countPill.setPadding(CreditsSheet.dp(activity, 9), CreditsSheet.dp(activity, 3),
                        CreditsSheet.dp(activity, 9), CreditsSheet.dp(activity, 3));
                GradientDrawable pillBg = new GradientDrawable();
                pillBg.setColor(p.accentSoft);
                pillBg.setCornerRadius(CreditsSheet.dp(activity, 10));
                countPill.setBackground(pillBg);
                View spacer = new View(activity);
                if (rtl) {
                    headerRow.addView(countPill, 0, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    headerRow.addView(spacer, 1, new LinearLayout.LayoutParams(0, 1, 1f));
                } else {
                    headerRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
                    headerRow.addView(countPill, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                ghCard.addView(headerRow);

                ghCard.addView(buildContributionShareBar(activity, p, ghContributors, rtl));
                ghCard.addView(CreditsSheet.createDivider(activity, p));
                ghCard.addView(buildContributorsBentoGrid(
                        activity, p, mediumTf, boldTf, ghContributors, subDialog, parentDialog, controller));
            } else {
                ghCard.addView(headerRow);
                if (loading) {
                    ghCard.addView(CreditsSheet.createDivider(activity, p));
                    FrameLayout spinnerBox = new FrameLayout(activity);
                    spinnerBox.setPadding(0, CreditsSheet.dp(activity, 16), 0, CreditsSheet.dp(activity, 16));
                    ProgressBar progress = new ProgressBar(activity);
                    progress.setIndeterminateTintList(ColorStateList.valueOf(p.accentBlue));
                    FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                            CreditsSheet.dp(activity, 22), CreditsSheet.dp(activity, 22));
                    pbLp.gravity = Gravity.CENTER;
                    spinnerBox.addView(progress, pbLp);
                    ghCard.addView(spinnerBox, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    ghCard.addView(CreditsSheet.createDivider(activity, p));
                    JSONObject fallback = new JSONObject();
                    try {
                        fallback.put("name", Translator.get(Keys.CreditsContributors));
                        fallback.put("role", "github.com/doandat943/TeleVip-Next-LSPosed");
                        fallback.put("icon", "code");
                        fallback.put("avatarBg", "#2A85FF");
                        fallback.put("url", ContributorsRepository.CONTRIBUTORS_WEB_URL);
                    } catch (Throwable ignored) {}
                    ghCard.addView(CreditsSheet.buildItemRow(
                            activity, p, mediumTf, boldTf, fallback, subDialog, parentDialog, controller));
                }
            }

            bodyContainer.addView(ghCard, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addedCard = true;
        }

        if (!specialThanks.isEmpty() && (activeFilter[0] == -1 || activeFilter[0] == 1)) {
            LinearLayout specialCard = new LinearLayout(activity);
            specialCard.setOrientation(LinearLayout.VERTICAL);
            specialCard.setBackground(GlassDesign.createGlassCardDrawable(p, CreditsSheet.dp(activity, 22)));
            specialCard.setClipToOutline(true);
            specialCard.addView(CreditsSheet.createSectionHeaderRow(
                    activity, p, mediumTf,
                    Translator.get(Keys.CreditsSpecialThanks),
                    CreditsSheet.parseColorSafe("#FF9F0A", p.accentBlue),
                    GlassDesign.ICON_SPARKLES));
            for (JSONObject item : specialThanks) {
                specialCard.addView(CreditsSheet.createDivider(activity, p));
                specialCard.addView(CreditsSheet.buildItemRow(
                        activity, p, mediumTf, boldTf, item, subDialog, parentDialog, controller));
            }
            LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (addedCard) spLp.topMargin = CreditsSheet.dp(activity, 12);
            bodyContainer.addView(specialCard, spLp);
        }
    }

    private static View buildContributionShareBar(
            Activity activity,
            GlassDesign.Palette p,
            List<ContributorsRepository.Contributor> contributors,
            boolean rtl) {
        List<ContributorsRepository.ShareSegment> segments =
                ContributorsRepository.computeShareSegments(contributors, 6, 0.03f);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        GradientDrawable capsuleBg = new GradientDrawable();
        capsuleBg.setCornerRadius(CreditsSheet.dp(activity, 4));
        capsuleBg.setColor(p.divider);
        bar.setBackground(capsuleBg);
        bar.setClipToOutline(true);

        int tailColor = p.light ? Color.argb(90, 130, 140, 155) : Color.argb(95, 154, 167, 184);
        int gapPx = CreditsSheet.dp(activity, 2);
        int count = segments.size();
        for (int idx = 0; idx < count; idx++) {
            int segPos = rtl ? (count - 1 - idx) : idx;
            ContributorsRepository.ShareSegment seg = segments.get(segPos);
            int color = seg.contributorIndex >= 0
                    ? CreditsSheet.resolveAutoAvatarColor(contributors.get(seg.contributorIndex).login, p.accentBlue)
                    : tailColor;

            View segView = new View(activity);
            segView.setBackgroundColor(color);
            LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.025f, seg.fraction));
            if (idx > 0) {
                segLp.leftMargin = gapPx;
            }
            bar.addView(segView, segLp);
        }

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, CreditsSheet.dp(activity, 8));
        barLp.setMargins(CreditsSheet.dp(activity, 16), CreditsSheet.dp(activity, 2),
                CreditsSheet.dp(activity, 16), CreditsSheet.dp(activity, 12));
        bar.setLayoutParams(barLp);

        bar.post(() -> {
            if (bar.getWidth() > 0) {
                bar.setPivotX(rtl ? bar.getWidth() : 0f);
                bar.setScaleX(0.15f);
                bar.animate().scaleX(1f).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        });
        return bar;
    }

    private static View buildContributorsBentoGrid(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            List<ContributorsRepository.Contributor> contributors,
            Dialog dialog,
            Dialog parentDialog,
            SettingsController controller) {
        final boolean rtl = Translator.isRtl();
        int totalCommits = 0;
        for (ContributorsRepository.Contributor c : contributors) {
            totalCommits += Math.max(0, c.contributions);
        }

        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        int pad = CreditsSheet.dp(activity, 12);
        grid.setPadding(pad, pad, pad, pad);

        int gapPx = CreditsSheet.dp(activity, 10);
        for (int i = 0; i < contributors.size(); i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

            View firstTile = buildBentoContributorCard(
                    activity, p, mediumTf, boldTf, contributors.get(i), i, totalCommits, dialog, parentDialog, controller);
            View secondTile;
            if (i + 1 < contributors.size()) {
                secondTile = buildBentoContributorCard(
                        activity, p, mediumTf, boldTf, contributors.get(i + 1), i + 1, totalCommits, dialog, parentDialog, controller);
            } else {
                secondTile = new View(activity);
                secondTile.setVisibility(View.INVISIBLE);
            }

            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.leftMargin = gapPx;

            if (rtl) {
                row.addView(secondTile, leftLp);
                row.addView(firstTile, rightLp);
            } else {
                row.addView(firstTile, leftLp);
                row.addView(secondTile, rightLp);
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                rowLp.topMargin = gapPx;
            }
            grid.addView(row, rowLp);
        }
        return grid;
    }

    private static View buildBentoContributorCard(
            Activity activity,
            GlassDesign.Palette p,
            Typeface mediumTf,
            Typeface boldTf,
            ContributorsRepository.Contributor contributor,
            int index,
            int totalCommits,
            Dialog dialog,
            Dialog parentDialog,
            SettingsController controller) {
        final boolean rtl = Translator.isRtl();
        int avatarColor = CreditsSheet.resolveAutoAvatarColor(contributor.login, p.accentBlue);
        int rank = index + 1;
        int rankAccent = resolveRankAccent(rank, p.accentBlue);
        int rankSoft = Color.argb(p.light ? 34 : 46,
                Color.red(rankAccent), Color.green(rankAccent), Color.blue(rankAccent));

        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(CreditsSheet.dp(activity, 12), CreditsSheet.dp(activity, 12),
                CreditsSheet.dp(activity, 12), CreditsSheet.dp(activity, 12));
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius(CreditsSheet.dp(activity, 18));
        tileBg.setColor(p.light ? Color.argb(190, 255, 255, 255) : Color.argb(122, 42, 54, 70));
        tileBg.setStroke(Math.max(1, CreditsSheet.dp(activity, 1f)),
                p.light ? Color.argb(30, 15, 30, 60) : Color.argb(40, 255, 255, 255));
        tile.setBackground(tileBg);
        tile.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, CreditsSheet.dp(activity, 18)));
        tile.setClipToOutline(true);
        GlassDesign.attachPressScale(tile);

        // Top row: 40dp monogram orb + spacer + #rank pill
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        TextView orb = new TextView(activity);
        orb.setText(CreditsSheet.extractInitial(contributor.login, ""));
        orb.setTextColor(Color.WHITE);
        orb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        orb.setTypeface(boldTf);
        orb.setGravity(Gravity.CENTER);
        GradientDrawable orbBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{avatarColor, CreditsSheet.darken(avatarColor, 0.78f)}
        );
        orbBg.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBg);

        TextView rankPill = new TextView(activity);
        rankPill.setText("#" + rank);
        rankPill.setTextColor(rankAccent);
        rankPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        rankPill.setTypeface(boldTf);
        rankPill.setSingleLine(true);
        rankPill.setPadding(CreditsSheet.dp(activity, 8), CreditsSheet.dp(activity, 3),
                CreditsSheet.dp(activity, 8), CreditsSheet.dp(activity, 3));
        GradientDrawable rankBg = new GradientDrawable();
        rankBg.setCornerRadius(CreditsSheet.dp(activity, 9));
        rankBg.setColor(rankSoft);
        rankPill.setBackground(rankBg);

        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(
                CreditsSheet.dp(activity, 40), CreditsSheet.dp(activity, 40));
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1f);
        LinearLayout.LayoutParams rankLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rtl) {
            topRow.addView(rankPill, rankLp);
            topRow.addView(new View(activity), spacerLp);
            topRow.addView(orb, orbLp);
        } else {
            topRow.addView(orb, orbLp);
            topRow.addView(new View(activity), spacerLp);
            topRow.addView(rankPill, rankLp);
        }
        tile.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Middle: GitHub login
        TextView nameTv = new TextView(activity);
        nameTv.setText(contributor.login);
        nameTv.setTextColor(p.textPrimary);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        nameTv.setTypeface(boldTf);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        nameTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = CreditsSheet.dp(activity, 10);
        tile.addView(nameTv, nameLp);

        // Bottom: colored dot + "<contributions> · <sharePct>%"
        int sharePct = ContributorsRepository.computeSharePercent(contributor.contributions, totalCommits);
        LinearLayout bottomRow = new LinearLayout(activity);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        bottomRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        View dot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(avatarColor);
        dot.setBackground(dotBg);
        int dotSize = CreditsSheet.dp(activity, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);

        TextView statsTv = new TextView(activity);
        statsTv.setText(contributor.contributions + " \u00b7 " + sharePct + "%");
        statsTv.setTextColor(p.textSecondary);
        statsTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        statsTv.setTypeface(mediumTf);
        statsTv.setSingleLine(true);
        statsTv.setEllipsize(TextUtils.TruncateAt.END);
        statsTv.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
        statsTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (rtl) {
            dotLp.leftMargin = CreditsSheet.dp(activity, 6);
            bottomRow.addView(statsTv, statsLp);
            bottomRow.addView(dot, dotLp);
        } else {
            dotLp.rightMargin = CreditsSheet.dp(activity, 6);
            bottomRow.addView(dot, dotLp);
            bottomRow.addView(statsTv, statsLp);
        }
        LinearLayout.LayoutParams botLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        botLp.topMargin = CreditsSheet.dp(activity, 4);
        tile.addView(bottomRow, botLp);

        tile.setOnClickListener(v -> CreditsSheet.openUrl(activity, contributor.url, dialog, parentDialog, controller));
        return tile;
    }

    private static int resolveRankAccent(int rank, int fallback) {
        if (rank == 1) return Color.rgb(245, 158, 11);
        if (rank == 2) return Color.rgb(148, 163, 184);
        if (rank == 3) return Color.rgb(217, 119, 6);
        return fallback;
    }
}
