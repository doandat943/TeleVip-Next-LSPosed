package com.my.televip.settings.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.my.televip.Audio;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.features.messages.ShowDeletedMessages;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.utils.DialogUtils;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.Theme;
import com.my.televip.virtuals.messenger.browser.Browser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsActivity {

    public static boolean isSettings;

    private static final int TAB_PRIVACY = 0;
    private static final int TAB_CHATS = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_TOOLS = 3;

    private final Context context;

    private static final int TAB_COUNT = 4;

    private FrameLayout rootView;
    private SettingsController controller;
    private int currentTab = TAB_PRIVACY;
    private boolean searchMode = false;
    private String searchQuery = "";

    private LinearLayout normalHeaderGroup;
    private LinearLayout searchHeaderGroup;
    private EditText searchInput;
    private GlassDesign.SegmentedTabBar tabBar;
    private TabPagerView tabPager;
    private final ScrollView[] tabScrollViews = new ScrollView[TAB_COUNT];
    private final LinearLayout[] tabContainers = new LinearLayout[TAB_COUNT];
    private ScrollView searchScrollView;
    private LinearLayout searchContainer;
    private boolean showingSearchList = false;
    private int lastHeaderPadTop = 0;
    private int lastNavPadBottom = 0;

    private final List<TabSection> allTabSections = new ArrayList<>();
    private final List<SearchableRow> searchableRows = new ArrayList<>();
    private final List<LinearLayout> sectionCards = new ArrayList<>();
    private final List<View> footnoteViews = new ArrayList<>();
    private LinearLayout actionsCardView;
    private LinearLayout footerCreditView;
    private LinearLayout emptySearchCard;

    public SettingsActivity(Context context) {
        this.context = context;
    }

    public View createView(SettingsController settingsController) {
        this.controller = settingsController;
        rootView = new FrameLayout(context);
        rebuildUi();
        return rootView;
    }

    /**
     * Closes search mode if currently active; returns true if consumed.
     */
    public boolean closeSearchIfOpen() {
        if (!searchMode) return false;
        exitSearchMode();
        return true;
    }

    private void toggleTheme(View anchorView) {
        boolean wasLight = Theme.isLight();
        hideKeyboard();
        boolean switched = Theme.toggleDayNight(anchorView);
        if (!switched) {
            return;
        }
        boolean nowLight = Theme.isLight();
        if (nowLight != wasLight) {
            rebuildUi(nowLight);
        } else {
            rebuildUi(!wasLight);
            if (rootView != null) {
                rootView.postDelayed(this::rebuildUi, 250);
            }
        }
    }

    private void rebuildUi() {
        rebuildUi(Theme.isLight());
    }

    private void rebuildUi(boolean light) {
        if (rootView == null) return;
        rootView.removeAllViews();
        allTabSections.clear();
        searchableRows.clear();
        sectionCards.clear();
        footnoteViews.clear();
        actionsCardView = null;
        footerCreditView = null;
        emptySearchCard = null;
        tabBar = null;
        tabPager = null;
        searchScrollView = null;
        searchContainer = null;
        showingSearchList = false;
        for (int i = 0; i < TAB_COUNT; i++) {
            tabScrollViews[i] = null;
            tabContainers[i] = null;
        }

        final GlassDesign.Palette p = GlassDesign.Palette.of(light);
        rootView.setBackground(GlassDesign.createAuroraBackground(p));

        try {
            int statusH = getDimenPx("status_bar_height");
            int navH = getDimenPx("navigation_bar_height");
            lastHeaderPadTop = statusH + dp(116);
            lastNavPadBottom = navH + dp(28);

            // 1. 4-page horizontal pager with independent vertical ScrollViews
            tabPager = new TabPagerView(context);
            for (int i = 0; i < TAB_COUNT; i++) {
                ScrollView sv = new ScrollView(context);
                sv.setVerticalScrollBarEnabled(false);
                sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
                sv.setClipToPadding(false);
                sv.setPadding(0, lastHeaderPadTop, 0, lastNavPadBottom);

                LinearLayout pageCol = new LinearLayout(context);
                pageCol.setOrientation(LinearLayout.VERTICAL);
                sv.addView(pageCol, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                tabScrollViews[i] = sv;
                tabContainers[i] = pageCol;
                tabPager.addView(sv, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            }
            rootView.addView(tabPager, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            // Dedicated search scroll container (shown only while searching)
            searchScrollView = new ScrollView(context);
            searchScrollView.setVerticalScrollBarEnabled(false);
            searchScrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            searchScrollView.setClipToPadding(false);
            searchScrollView.setPadding(0, lastHeaderPadTop, 0, lastNavPadBottom);
            searchScrollView.setVisibility(View.GONE);

            searchContainer = new LinearLayout(context);
            searchContainer.setOrientation(LinearLayout.VERTICAL);
            searchScrollView.addView(searchContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rootView.addView(searchScrollView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            populateContent(p);

            // 2. Floating Glass Top Header + Segmented Tab Bar overlay
            LinearLayout floatingHeader = buildFloatingHeader(p, statusH);
            rootView.addView(floatingHeader, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

            floatingHeader.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int h = b - t;
                int targetTop = h + dp(4);
                if (h > 0 && lastHeaderPadTop != targetTop) {
                    lastHeaderPadTop = targetTop;
                    for (int i = 0; i < TAB_COUNT; i++) {
                        if (tabScrollViews[i] != null) {
                            tabScrollViews[i].setPadding(0, targetTop, 0, lastNavPadBottom);
                        }
                    }
                    if (searchScrollView != null) {
                        searchScrollView.setPadding(0, targetTop, 0, lastNavPadBottom);
                    }
                }
            });

            tabPager.jumpToTab(currentTab);
            applyVisibilityFilter();
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private LinearLayout buildFloatingHeader(GlassDesign.Palette p, int statusH) {
        LinearLayout headerBox = new LinearLayout(context);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        headerBox.setBackground(GlassDesign.createTopScrimDrawable(p));
        headerBox.setPadding(0, statusH + dp(8), 0, dp(12));

        // Top Bar Frame (switches between normalHeaderGroup and searchHeaderGroup)
        FrameLayout topBarSlot = new FrameLayout(context);
        LinearLayout.LayoutParams topSlotLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        topSlotLp.setMargins(dp(14), 0, dp(14), dp(12));

        // Normal Top Bar: [Back Circle]   [Title]   [Theme | Search Pill]
        normalHeaderGroup = new LinearLayout(context);
        normalHeaderGroup.setOrientation(LinearLayout.HORIZONTAL);
        normalHeaderGroup.setGravity(Gravity.CENTER_VERTICAL);

        ImageView backBtn = createCircleIconButton(p, GlassDesign.GlassIcons.backArrow(p.textPrimary));
        backBtn.setOnClickListener(v -> {
            if (controller != null) controller.hide();
        });
        normalHeaderGroup.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        FrameLayout brandSlot = new FrameLayout(context);
        LinearLayout.LayoutParams brandSlotLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandSlotLp.setMarginStart(dp(8));
        brandSlotLp.setMarginEnd(dp(8));

        LinearLayout brandPill = new LinearLayout(context);
        brandPill.setOrientation(LinearLayout.HORIZONTAL);
        brandPill.setGravity(Gravity.CENTER_VERTICAL);
        brandPill.setMinimumHeight(dp(40));
        brandPill.setPadding(dp(8), dp(6), dp(10), dp(6));
        brandPill.setBackground(GlassDesign.createGlassPillDrawable(p, dp(20)));
        brandPill.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(20)));
        GlassDesign.attachPressScale(brandPill);
        brandPill.setOnClickListener(v -> CreditsSheet.show(context, controller));

        ImageView emblemIv = new ImageView(context);
        emblemIv.setImageDrawable(GlassDesign.GlassIcons.brandEmblemDrawable(dp(8)));
        LinearLayout.LayoutParams emblemLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        emblemLp.setMarginEnd(dp(8));
        brandPill.addView(emblemIv, emblemLp);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(Keys.SettingsTitle));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brandPill.addView(titleTv, titleTextLp);

        ImageView chevronIv = new ImageView(context);
        chevronIv.setImageDrawable(GlassDesign.GlassIcons.chevronRight(p.textMuted));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        chevLp.setMarginStart(dp(4));
        brandPill.addView(chevronIv, chevLp);

        FrameLayout.LayoutParams pillCenterLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), Gravity.CENTER);
        brandSlot.addView(brandPill, pillCenterLp);
        normalHeaderGroup.addView(brandSlot, brandSlotLp);

        // Merged dual-icon glass pill (Theme toggle + Search)
        LinearLayout actionPill = new LinearLayout(context);
        actionPill.setOrientation(LinearLayout.HORIZONTAL);
        actionPill.setGravity(Gravity.CENTER_VERTICAL);
        actionPill.setBackground(GlassDesign.createGlassPillDrawable(p, dp(21)));

        Drawable themeIcon = p.light
                ? GlassDesign.GlassIcons.sunIcon(p.textPrimary)
                : GlassDesign.GlassIcons.moonIcon(p.textPrimary);
        ImageView themeBtn = new ImageView(context);
        themeBtn.setImageDrawable(themeIcon);
        themeBtn.setScaleType(ImageView.ScaleType.CENTER);
        themeBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        themeBtn.setBackground(GlassDesign.createPillRippleOverlay(p.ripple, dp(21)));
        themeBtn.setOnClickListener(v -> toggleTheme(v));
        actionPill.addView(themeBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        ImageView searchBtn = new ImageView(context);
        searchBtn.setImageDrawable(GlassDesign.GlassIcons.searchIcon(p.textPrimary));
        searchBtn.setScaleType(ImageView.ScaleType.CENTER);
        searchBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchBtn.setBackground(GlassDesign.createPillRippleOverlay(p.ripple, dp(21)));
        searchBtn.setOnClickListener(v -> enterSearchMode());
        actionPill.addView(searchBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        normalHeaderGroup.addView(actionPill, new LinearLayout.LayoutParams(dp(84), dp(42)));
        topBarSlot.addView(normalHeaderGroup, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Search Mode Top Bar: [Back Circle]  [Search Glass Pill]  [Close Circle]
        searchHeaderGroup = new LinearLayout(context);
        searchHeaderGroup.setOrientation(LinearLayout.HORIZONTAL);
        searchHeaderGroup.setGravity(Gravity.CENTER_VERTICAL);
        searchHeaderGroup.setVisibility(searchMode ? View.VISIBLE : View.GONE);

        ImageView searchBackBtn = createCircleIconButton(p, GlassDesign.GlassIcons.backArrow(p.textPrimary));
        searchBackBtn.setOnClickListener(v -> exitSearchMode());
        searchHeaderGroup.addView(searchBackBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout searchPill = new LinearLayout(context);
        searchPill.setOrientation(LinearLayout.HORIZONTAL);
        searchPill.setGravity(Gravity.CENTER_VERTICAL);
        searchPill.setBackground(GlassDesign.createGlassPillDrawable(p, dp(21)));
        searchPill.setPadding(dp(16), 0, dp(16), 0);

        searchInput = new EditText(context);
        searchInput.setHint(Translator.get(Keys.SearchSettings));
        searchInput.setHintTextColor(p.textSecondary);
        searchInput.setTextColor(p.textPrimary);
        searchInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        searchInput.setBackground(null);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        if (!searchQuery.isEmpty()) {
            searchInput.setText(searchQuery);
            searchInput.setSelection(searchQuery.length());
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                applyVisibilityFilter();
            }
        });
        searchPill.addView(searchInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        pillLp.setMarginStart(dp(10));
        pillLp.setMarginEnd(dp(10));
        searchHeaderGroup.addView(searchPill, pillLp);

        ImageView closeBtn = createCircleIconButton(p, GlassDesign.GlassIcons.closeIcon(p.textPrimary));
        closeBtn.setOnClickListener(v -> exitSearchMode());
        searchHeaderGroup.addView(closeBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        topBarSlot.addView(searchHeaderGroup, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (searchMode) {
            normalHeaderGroup.setVisibility(View.GONE);
        }

        headerBox.addView(topBarSlot, topSlotLp);

        // Segmented Tab Bar
        String[] tabTitles = new String[]{
                Translator.get(Keys.TabPrivacy),
                Translator.get(Keys.TabChats),
                Translator.get(Keys.TabAppearance),
                Translator.get(Keys.TabTools)
        };
        tabBar = new GlassDesign.SegmentedTabBar(context, tabTitles, p, currentTab);
        tabBar.setOnTabSelectedListener(index -> {
            if (searchMode) {
                currentTab = index;
                exitSearchMode();
                if (tabPager != null) tabPager.jumpToTab(index);
            } else if (tabPager != null) {
                tabPager.springToTab(index, 0f);
            }
        });
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        tabLp.setMargins(dp(20), 0, dp(20), 0);
        headerBox.addView(tabBar, tabLp);

        return headerBox;
    }

    private ImageView createCircleIconButton(GlassDesign.Palette p, Drawable icon) {
        ImageView btn = new ImageView(context);
        btn.setImageDrawable(icon);
        btn.setScaleType(ImageView.ScaleType.CENTER);
        btn.setPadding(dp(10), dp(10), dp(10), dp(10));
        btn.setBackground(GlassDesign.createGlassPillDrawable(p, dp(21)));
        btn.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(21)));
        return btn;
    }

    private void enterSearchMode() {
        searchMode = true;
        if (normalHeaderGroup != null) normalHeaderGroup.setVisibility(View.GONE);
        if (searchHeaderGroup != null) searchHeaderGroup.setVisibility(View.VISIBLE);
        if (searchInput != null) {
            searchInput.requestFocus();
            searchInput.post(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            });
        }
        applyVisibilityFilter();
    }

    private void exitSearchMode() {
        searchMode = false;
        searchQuery = "";
        hideKeyboard();
        if (searchInput != null) searchInput.setText("");
        if (searchHeaderGroup != null) searchHeaderGroup.setVisibility(View.GONE);
        if (normalHeaderGroup != null) normalHeaderGroup.setVisibility(View.VISIBLE);
        applyVisibilityFilter();
    }

    private void hideKeyboard() {
        if (searchInput != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private void populateContent(GlassDesign.Palette p) {
        List<ConfigItem> items = ConfigManager.getItems();

        // Parse ConfigManager items into logical sections by HEADER
        List<SectionData> featureSections = new ArrayList<>();
        List<ConfigItem> standaloneActions = new ArrayList<>();
        SectionData current = null;

        for (ConfigItem item : items) {
            if (item == null) continue;
            int type = item.getType();
            if (type == ConfigItem.DIVIDER) {
                current = null;
                continue;
            }
            if (type == ConfigItem.HEADER) {
                current = new SectionData(item.getKey());
                featureSections.add(current);
            } else if (type == ConfigItem.INFO) {
                if (current != null) {
                    current.footnotes.add(item);
                }
            } else if (current != null) {
                current.items.add(item);
            } else if (type == ConfigItem.TEXT) {
                standaloneActions.add(item);
            }
        }

        // Render feature sections as frosted glass cards into their respective tab pages
        for (SectionData sec : featureSections) {
            int tab = resolveTabForSection(sec.headerKey);
            LinearLayout pageCol = tabContainers[tab];
            LinearLayout card = buildSectionCard(sec.headerKey, sec.items, p, tab);
            LinearLayout.LayoutParams cardLp = marginLp(dp(12), 0, dp(12), dp(12));
            if (pageCol != null) pageCol.addView(card, cardLp);
            sectionCards.add(card);
            allTabSections.add(new TabSection(tab, card, cardLp));

            for (ConfigItem note : sec.footnotes) {
                TextView noteTv = buildFootnoteView(note, p);
                LinearLayout.LayoutParams noteLp = marginLp(dp(20), 0, dp(20), dp(14));
                if (pageCol != null) pageCol.addView(noteTv, noteLp);
                footnoteViews.add(noteTv);
                allTabSections.add(new TabSection(tab, noteTv, noteLp));
            }
        }

        // Render standalone action items (Developer Channel, Restart App) in Tools tab
        LinearLayout toolsCol = tabContainers[TAB_TOOLS];
        if (!standaloneActions.isEmpty()) {
            actionsCardView = buildActionsCard(standaloneActions, p, TAB_TOOLS);
            LinearLayout.LayoutParams actionsLp = marginLp(dp(12), 0, dp(12), dp(14));
            if (toolsCol != null) toolsCol.addView(actionsCardView, actionsLp);
            sectionCards.add(actionsCardView);
            allTabSections.add(new TabSection(TAB_TOOLS, actionsCardView, actionsLp));
        }

        // Empty search state card (only inside searchContainer)
        emptySearchCard = createGlassCard(p);
        emptySearchCard.setPadding(dp(20), dp(24), dp(20), dp(24));
        emptySearchCard.setGravity(Gravity.CENTER);
        TextView emptyTv = new TextView(context);
        emptyTv.setText(Translator.get(Keys.NoMatchingSettings));
        emptyTv.setTextColor(p.textSecondary);
        emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        emptyTv.setGravity(Gravity.CENTER);
        emptySearchCard.addView(emptyTv);
        emptySearchCard.setVisibility(View.GONE);
        if (searchContainer != null) {
            searchContainer.addView(emptySearchCard, marginLp(dp(12), dp(4), dp(12), dp(12)));
        }

        // Footer credit (on Tools tab)
        footerCreditView = new LinearLayout(context);
        footerCreditView.setOrientation(LinearLayout.VERTICAL);
        footerCreditView.setGravity(Gravity.CENTER_HORIZONTAL);
        footerCreditView.setPadding(dp(16), dp(6), dp(16), dp(12));

        TextView creditTv = new TextView(context);
        creditTv.setText("TeleVip Next  •  " + Translator.get(Keys.ByMustafa));
        creditTv.setTextColor(p.textMuted);
        creditTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        creditTv.setGravity(Gravity.CENTER);
        footerCreditView.addView(creditTv);
        footerCreditView.setOnClickListener(v -> CreditsSheet.show(context, controller));
        if (toolsCol != null) {
            toolsCol.addView(footerCreditView);
        }
    }

    private int resolveTabForSection(String headerKey) {
        if (Keys.GhostModeSettings.equals(headerKey)) return TAB_PRIVACY;
        if (Keys.MessagesSettings.equals(headerKey) || Keys.MediaSettings.equals(headerKey)) return TAB_CHATS;
        if (Keys.UiSettings.equals(headerKey) || Keys.StoriesSettings.equals(headerKey)) return TAB_APPEARANCE;
        return TAB_TOOLS;
    }

    private LinearLayout buildSectionCard(String headerKey, List<ConfigItem> items,
                                          GlassDesign.Palette p, int tabIndex) {
        LinearLayout card = createGlassCard(p);

        // Card header row with colored badge icon + blue title
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(16), dp(14), dp(16), dp(12));

        ImageView badge = new ImageView(context);
        badge.setImageDrawable(createSectionBadge(headerKey));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(28), dp(28));
        badgeLp.setMarginEnd(dp(12));
        headerRow.addView(badge, badgeLp);

        TextView headerTv = new TextView(context);
        headerTv.setText(Translator.get(headerKey));
        headerTv.setTextColor(p.accentBlue);
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        headerTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerRow.addView(headerTv);

        card.addView(headerRow);

        for (ConfigItem item : items) {
            int type = item.getType();
            if (type == ConfigItem.SWITCH) {
                View row = buildSwitchItemContainer(item, p);
                card.addView(row);
                registerSearchable(item, row, card, tabIndex);
            } else if (type == ConfigItem.EXPANDABLE_SWITCH) {
                View group = buildExpandableItemContainer(item, p);
                card.addView(group);
                registerSearchable(item, group, card, tabIndex);
            } else if (type == ConfigItem.TEXT) {
                View row = buildTextItemContainer(item, p, false);
                card.addView(row);
                registerSearchable(item, row, card, tabIndex);
            }
        }

        return card;
    }

    private LinearLayout buildActionsCard(List<ConfigItem> actions, GlassDesign.Palette p, int tabIndex) {
        LinearLayout card = createGlassCard(p);
        for (int i = 0; i < actions.size(); i++) {
            ConfigItem item = actions.get(i);
            View row = buildTextItemContainer(item, p, i == 0);
            card.addView(row);
            registerSearchable(item, row, card, tabIndex);
        }
        return card;
    }

    private View buildSwitchItemContainer(ConfigItem item, GlassDesign.Palette p) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setTag("item");
        wrapper.setOrientation(LinearLayout.VERTICAL);

        wrapper.addView(createHairlineDivider(p));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(56));
        row.setBackground(GlassDesign.createRowRipple(p.ripple));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(item.getKey()));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(item);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2.5f);
            textCol.addView(subTv, subLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(14));
        row.addView(textCol, textLp);

        GlassDesign.PillSwitchView sw = new GlassDesign.PillSwitchView(context, item.isEnable(), p);
        row.addView(sw, new LinearLayout.LayoutParams(dp(48), dp(28)));

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            item.setEnable(next);
            item.run();
        });
        row.setOnLongClickListener(v -> {
            playAudio(context);
            return true;
        });

        wrapper.addView(row);
        return wrapper;
    }

    private View buildExpandableItemContainer(ConfigItem parentItem, GlassDesign.Palette p) {
        List<ConfigItem> children = parentItem.getChildren();
        int initialEnabled = 0;
        int switchCount = 0;
        if (children != null) {
            for (ConfigItem c : children) {
                if (c.getType() == ConfigItem.SWITCH) {
                    switchCount++;
                    if (c.isEnable()) initialEnabled++;
                }
            }
        }
        final int totalSwitchChildren = switchCount;
        parentItem.setEnable(initialEnabled > 0);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setTag("item");
        wrapper.setOrientation(LinearLayout.VERTICAL);

        wrapper.addView(createHairlineDivider(p));

        LinearLayout childrenContainer = new LinearLayout(context);
        childrenContainer.setOrientation(LinearLayout.VERTICAL);
        childrenContainer.setVisibility(View.GONE);

        LinearLayout parentRow = new LinearLayout(context);
        parentRow.setOrientation(LinearLayout.HORIZONTAL);
        parentRow.setGravity(Gravity.CENTER_VERTICAL);
        parentRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        parentRow.setMinimumHeight(dp(56));
        parentRow.setBackground(GlassDesign.createRowRipple(p.ripple));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(formatExpandableTitle(parentItem, initialEnabled, totalSwitchChildren));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(parentItem);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2.5f);
            textCol.addView(subTv, subLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(14));
        parentRow.addView(textCol, textLp);

        GlassDesign.PillSwitchView parentSw = new GlassDesign.PillSwitchView(context, initialEnabled > 0, p);
        parentRow.addView(parentSw, new LinearLayout.LayoutParams(dp(48), dp(28)));

        wrapper.addView(parentRow);
        wrapper.addView(childrenContainer);

        final GlassDesign.PillSwitchView[] childSwitches = children != null
                ? new GlassDesign.PillSwitchView[children.size()]
                : new GlassDesign.PillSwitchView[0];

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                final ConfigItem child = children.get(i);
                childrenContainer.addView(createHairlineDivider(p, dp(32)));

                LinearLayout childRow = new LinearLayout(context);
                childRow.setOrientation(LinearLayout.HORIZONTAL);
                childRow.setGravity(Gravity.CENTER_VERTICAL);
                childRow.setPaddingRelative(dp(32), dp(13), dp(16), dp(13));
                childRow.setMinimumHeight(dp(50));
                childRow.setBackground(GlassDesign.createRowRipple(p.ripple));

                if (child.getType() == ConfigItem.TEXT) {
                    LinearLayout cTextCol = new LinearLayout(context);
                    cTextCol.setOrientation(LinearLayout.VERTICAL);

                    TextView childTitle = new TextView(context);
                    childTitle.setText(Translator.get(child.getKey()));
                    childTitle.setTextColor(Color.rgb(255, 69, 58));
                    childTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                    childTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    cTextCol.addView(childTitle);

                    String childSub = buildSubtitleText(child);
                    if (childSub != null && !childSub.isEmpty()) {
                        TextView cSubTv = new TextView(context);
                        cSubTv.setText(childSub);
                        cSubTv.setTextColor(p.textSecondary);
                        cSubTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                        LinearLayout.LayoutParams cSubLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        cSubLp.topMargin = dp(2f);
                        cTextCol.addView(cSubTv, cSubLp);
                    }

                    childRow.addView(cTextCol, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    childRow.setOnClickListener(v -> showClearDeletedConfirm());
                    childrenContainer.addView(childRow);
                    continue;
                }

                TextView childTitle = new TextView(context);
                childTitle.setText(Translator.get(child.getKey()));
                childTitle.setTextColor(p.textPrimary);
                childTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                LinearLayout.LayoutParams cTextLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                cTextLp.setMarginEnd(dp(14));
                childRow.addView(childTitle, cTextLp);

                GlassDesign.PillSwitchView childSw = new GlassDesign.PillSwitchView(context, child.isEnable(), p);
                childSwitches[i] = childSw;
                childRow.addView(childSw, new LinearLayout.LayoutParams(dp(48), dp(28)));

                childRow.setOnClickListener(v -> {
                    boolean next = !childSw.isChecked();
                    childSw.setChecked(next, true);
                    child.setEnable(next);

                    int count = 0;
                    for (ConfigItem c : children) {
                        if (c.getType() == ConfigItem.SWITCH && c.isEnable()) count++;
                    }
                    parentItem.setEnable(count > 0);
                    parentSw.setChecked(count > 0, true);
                    titleTv.setText(formatExpandableTitle(parentItem, count, totalSwitchChildren));

                    child.run();
                });

                childrenContainer.addView(childRow);
            }
        }

        parentSw.setOnClickListener(v -> {
            boolean next = !parentSw.isChecked();
            parentSw.setChecked(next, true);
            parentItem.setEnable(next);
            if (children != null) {
                for (int i = 0; i < children.size(); i++) {
                    ConfigItem c = children.get(i);
                    if (c.getType() != ConfigItem.SWITCH) continue;
                    c.setEnable(next);
                    c.run();
                    if (childSwitches[i] != null) childSwitches[i].setChecked(next, true);
                }
                titleTv.setText(formatExpandableTitle(parentItem, next ? totalSwitchChildren : 0, totalSwitchChildren));
            }
        });

        parentRow.setOnClickListener(v -> {
            boolean show = childrenContainer.getVisibility() != View.VISIBLE;
            childrenContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        });

        return wrapper;
    }

    private void showClearDeletedConfirm() {
        try {
            int themeRes = Theme.isLight()
                    ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Dialog_Alert;
            new AlertDialog.Builder(context, themeRes)
                    .setTitle(Translator.get(Keys.ClearDeletedMessages))
                    .setMessage(Translator.get(Keys.ClearDeletedConfirm))
                    .setPositiveButton(Translator.get(Keys.ClearAction), (dialog, which) ->
                            ShowDeletedMessages.clearAllDeletedMessages(context))
                    .setNegativeButton(Translator.get(Keys.Cancel), null)
                    .show();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private String formatExpandableTitle(ConfigItem parentItem, int enabled, int total) {
        String base = Translator.get(parentItem.getKey());
        return total > 0 ? (base + " " + enabled + "/" + total) : base;
    }

    private View buildTextItemContainer(ConfigItem item, GlassDesign.Palette p, boolean hideTopDivider) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setTag("item");
        wrapper.setOrientation(LinearLayout.VERTICAL);

        if (!hideTopDivider) {
            View div = createHairlineDivider(p);
            div.setTag("row_divider");
            wrapper.addView(div);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setMinimumHeight(dp(56));
        row.setBackground(GlassDesign.createRowRipple(p.ripple));

        String key = item.getKey();
        boolean isChannel = Keys.DeveloperChannel.equals(key);
        boolean isRestart = Keys.RestartApp.equals(key);
        boolean isCalendar = Keys.Calendar.equals(key);
        boolean isDownloadSpeed = Keys.DownloadSpeed.equals(key);

        if (isChannel || isRestart) {
            ImageView iconBadge = new ImageView(context);
            if (isChannel) {
                iconBadge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                        Color.rgb(255, 212, 38), dp(9), GlassDesign.ICON_TELEGRAM, Color.rgb(30, 32, 38)));
            } else {
                iconBadge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                        Color.rgb(255, 69, 58), dp(9), GlassDesign.ICON_RESTART, Color.WHITE));
            }
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(32), dp(32));
            iconLp.setMarginEnd(dp(14));
            row.addView(iconBadge, iconLp);
        }

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(key));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setTextColor(isChannel ? p.accentBlue : p.textPrimary);
        if (isChannel || isRestart) {
            titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(item);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2f);
            textCol.addView(subTv, subLp);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (isCalendar || isDownloadSpeed) {
            TextView valTv = new TextView(context);
            valTv.setTextColor(p.accentBlue);
            valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            valTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            valTv.setText(isCalendar ? getCalendarLabel(item.getIntValue()) : getDownloadSpeedLabel(item.getIntValue()));
            valTv.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(p.accentSoft);
            pill.setCornerRadius(dp(10));
            valTv.setBackground(pill);
            row.addView(valTv);

            row.setOnClickListener(v -> {
                if (isCalendar) {
                    showOptionPicker(Translator.get(Keys.Calendar), new String[]{
                            Translator.get(Keys.Gregorian),
                            Translator.get(Keys.Hijri),
                            Translator.get(Keys.Persian)
                    }, item, valTv, true);
                } else {
                    showOptionPicker(Translator.get(Keys.DownloadSpeed), new String[]{
                            Translator.get(Keys.SpeedOff),
                            Translator.get(Keys.SpeedFast),
                            Translator.get(Keys.SpeedUltra)
                    }, item, valTv, false);
                }
            });
        } else if (isChannel) {
            row.setOnClickListener(v -> {
                try {
                    if (controller != null) controller.hide();
                    Browser.openUrl(context, "https://t.me/televipnext");
                } catch (Throwable t) {
                    Logger.e(t);
                }
            });
        } else if (isRestart) {
            row.setOnClickListener(v -> restartHostApp(row));
        }

        wrapper.addView(row);
        return wrapper;
    }

    private TextView buildFootnoteView(ConfigItem item, GlassDesign.Palette p) {
        TextView tv = new TextView(context);
        tv.setText(Translator.get(item.getKey()));
        tv.setTextColor(p.textSecondary);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setLineSpacing(dp(2f), 1.0f);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        final boolean[] expanded = {false};
        tv.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            if (expanded[0]) {
                tv.setMaxLines(Integer.MAX_VALUE);
                tv.setEllipsize(null);
            } else {
                tv.setMaxLines(2);
                tv.setEllipsize(TextUtils.TruncateAt.END);
            }
        });
        return tv;
    }

    private void showOptionPicker(String title, String[] options, ConfigItem item, TextView valTv, boolean isCalendar) {
        int current = item.getIntValue();
        if (current < 0 || current >= options.length) current = 0;
        try {
            int themeRes = Theme.isLight()
                    ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Dialog_Alert;
            new AlertDialog.Builder(context, themeRes)
                    .setTitle(title)
                    .setSingleChoiceItems(options, current, (dialog, which) -> {
                        item.setIntValue(which);
                        item.run();
                        valTv.setText(isCalendar ? getCalendarLabel(which) : getDownloadSpeedLabel(which));
                        dialog.dismiss();
                    })
                    .setNegativeButton(Translator.get(Keys.Cancel), null)
                    .show();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private void restartHostApp(View anchor) {
        Activity act = Utils.getCurrentActivity();
        if (act == null) return;
        Intent intent = act.getPackageManager().getLaunchIntentForPackage(act.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            act.startActivity(intent);
            anchor.postDelayed(() -> Runtime.getRuntime().exit(0), 150);
        }
    }

    private void registerSearchable(ConfigItem item, View rowView, LinearLayout card, int tabIndex) {
        String title = Translator.get(item.getKey());
        String sub = buildSubtitleText(item);
        String combined = (title + " " + (sub != null ? sub : "")).toLowerCase(Locale.ROOT);
        searchableRows.add(new SearchableRow(combined, rowView, card, tabIndex));
    }

    private void enterSearchListMode() {
        if (showingSearchList || searchContainer == null || searchScrollView == null) return;
        showingSearchList = true;
        if (tabPager != null) tabPager.setVisibility(View.GONE);
        searchContainer.removeAllViews();
        for (LinearLayout card : sectionCards) {
            if (card.getParent() instanceof ViewGroup) {
                ((ViewGroup) card.getParent()).removeView(card);
            }
            LinearLayout.LayoutParams lp = card == actionsCardView
                    ? marginLp(dp(12), 0, dp(12), dp(14))
                    : marginLp(dp(12), 0, dp(12), dp(12));
            searchContainer.addView(card, lp);
        }
        if (emptySearchCard != null) {
            if (emptySearchCard.getParent() instanceof ViewGroup) {
                ((ViewGroup) emptySearchCard.getParent()).removeView(emptySearchCard);
            }
            searchContainer.addView(emptySearchCard, marginLp(dp(12), dp(4), dp(12), dp(12)));
        }
        searchScrollView.scrollTo(0, 0);
        searchScrollView.setVisibility(View.VISIBLE);
    }

    private void exitSearchListMode() {
        if (!showingSearchList) return;
        showingSearchList = false;
        if (searchScrollView != null) searchScrollView.setVisibility(View.GONE);
        if (searchContainer != null) searchContainer.removeAllViews();
        for (int i = 0; i < TAB_COUNT; i++) {
            if (tabContainers[i] != null) tabContainers[i].removeAllViews();
        }
        for (TabSection ts : allTabSections) {
            if (ts.view.getParent() instanceof ViewGroup) {
                ((ViewGroup) ts.view.getParent()).removeView(ts.view);
            }
            LinearLayout target = tabContainers[ts.tabIndex];
            if (target != null) {
                target.addView(ts.view, ts.layoutParams);
            }
        }
        if (footerCreditView != null) {
            if (footerCreditView.getParent() instanceof ViewGroup) {
                ((ViewGroup) footerCreditView.getParent()).removeView(footerCreditView);
            }
            if (tabContainers[TAB_TOOLS] != null) {
                tabContainers[TAB_TOOLS].addView(footerCreditView);
            }
        }
        if (emptySearchCard != null && searchContainer != null) {
            searchContainer.addView(emptySearchCard, marginLp(dp(12), dp(4), dp(12), dp(12)));
        }
        if (tabPager != null) {
            tabPager.setVisibility(View.VISIBLE);
        }
    }

    private void applyVisibilityFilter() {
        if (searchMode && !searchQuery.isEmpty()) {
            enterSearchListMode();
            for (View note : footnoteViews) {
                note.setVisibility(View.GONE);
            }
            if (footerCreditView != null) footerCreditView.setVisibility(View.GONE);

            for (SearchableRow sr : searchableRows) {
                boolean match = sr.textLower.contains(searchQuery);
                sr.rowView.setVisibility(match ? View.VISIBLE : View.GONE);
            }

            int visibleCards = 0;
            for (LinearLayout card : sectionCards) {
                boolean anyVisible = false;
                for (int i = 0; i < card.getChildCount(); i++) {
                    View c = card.getChildAt(i);
                    if ("item".equals(c.getTag()) && c.getVisibility() == View.VISIBLE) {
                        anyVisible = true;
                        break;
                    }
                }
                card.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
                if (anyVisible) visibleCards++;
            }
            if (emptySearchCard != null) {
                emptySearchCard.setVisibility(visibleCards == 0 ? View.VISIBLE : View.GONE);
            }
            updateActionsCardDividers();
            return;
        }

        exitSearchListMode();
        if (emptySearchCard != null) emptySearchCard.setVisibility(View.GONE);
        for (SearchableRow sr : searchableRows) {
            sr.rowView.setVisibility(View.VISIBLE);
        }
        for (TabSection ts : allTabSections) {
            ts.view.setVisibility(View.VISIBLE);
        }
        updateActionsCardDividers();
        if (footerCreditView != null) {
            footerCreditView.setVisibility(View.VISIBLE);
        }
    }

    private void updateActionsCardDividers() {
        if (actionsCardView == null) return;
        boolean seenFirstVisible = false;
        for (int i = 0; i < actionsCardView.getChildCount(); i++) {
            View child = actionsCardView.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            View div = child.findViewWithTag("row_divider");
            if (div != null) {
                div.setVisibility(seenFirstVisible ? View.VISIBLE : View.GONE);
            }
            seenFirstVisible = true;
        }
    }

    private String buildSubtitleText(ConfigItem item) {
        String desc = Translator.getOrDefault(item.getKey() + "Desc", null);
        if (desc == null || desc.isEmpty()) {
            desc = item.getValue();
        }
        if (item.isRestartRequired()) {
            String restartNote = Translator.get(Keys.RestartRequired);
            if (desc != null && !desc.isEmpty()) {
                return desc + "\n" + restartNote;
            }
            return restartNote;
        }
        return desc;
    }

    private static void playAudio(Context context) {
        if (Audio.playing) {
            Audio.stop();
        } else {
            Audio.start();
            DialogUtils.showQuranAlert(context);
        }
    }

    private Drawable createSectionBadge(String headerKey) {
        int r = dp(8);
        if (Keys.GhostModeSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(139, 107, 255), r, GlassDesign.ICON_GHOST, Color.WHITE);
        } else if (Keys.MessagesSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(52, 199, 89), r, GlassDesign.ICON_MESSAGES, Color.WHITE);
        } else if (Keys.MediaSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(255, 77, 126), r, GlassDesign.ICON_MEDIA, Color.WHITE);
        } else if (Keys.ConnectionsSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(14, 165, 233), r, GlassDesign.ICON_CONNECTIONS, Color.WHITE);
        } else if (Keys.UiSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(42, 196, 155), r, GlassDesign.ICON_UI, Color.WHITE);
        } else if (Keys.StoriesSettings.equals(headerKey)) {
            return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(175, 82, 222), r, GlassDesign.ICON_STORIES, Color.WHITE);
        }
        return GlassDesign.GlassIcons.badgeDrawable(Color.rgb(255, 138, 52), r, GlassDesign.ICON_SPARKLES, Color.WHITE);
    }

    private LinearLayout createGlassCard(GlassDesign.Palette p) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(GlassDesign.createGlassCardDrawable(p, dp(22)));
        card.setClipToOutline(true);
        return card;
    }

    private View createHairlineDivider(GlassDesign.Palette p) {
        return createHairlineDivider(p, dp(16));
    }

    private View createHairlineDivider(GlassDesign.Palette p, int startMarginPx) {
        View div = new View(context);
        div.setBackgroundColor(p.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        lp.setMarginStart(startMarginPx);
        lp.setMarginEnd(dp(16));
        div.setLayoutParams(lp);
        return div;
    }

    private String getCalendarLabel(int type) {
        if (type == 1) return Translator.get(Keys.Hijri);
        if (type == 2) return Translator.get(Keys.Persian);
        return Translator.get(Keys.Gregorian);
    }

    private String getDownloadSpeedLabel(int mode) {
        if (mode == 1) return Translator.get(Keys.SpeedFast);
        if (mode == 2) return Translator.get(Keys.SpeedUltra);
        return Translator.get(Keys.SpeedOff);
    }

    private LinearLayout.LayoutParams marginLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int getDimenPx(String name) {
        int id = context.getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private static final class SectionData {
        final String headerKey;
        final List<ConfigItem> items = new ArrayList<>();
        final List<ConfigItem> footnotes = new ArrayList<>();

        SectionData(String headerKey) {
            this.headerKey = headerKey;
        }
    }

    private static final class TabSection {
        final int tabIndex;
        final View view;
        final LinearLayout.LayoutParams layoutParams;

        TabSection(int tabIndex, View view, LinearLayout.LayoutParams layoutParams) {
            this.tabIndex = tabIndex;
            this.view = view;
            this.layoutParams = layoutParams;
        }
    }

    private static final class SearchableRow {
        final String textLower;
        final View rowView;

        SearchableRow(String textLower, View rowView, LinearLayout card, int tabIndex) {
            this.textLower = textLower != null ? textLower : "";
            this.rowView = rowView;
        }
    }

    private final class TabPagerView extends FrameLayout implements Choreographer.FrameCallback {
        private static final float SPRING_OMEGA = 14.5f;

        private final float edgeBackWidth;
        private final int touchSlop;
        private float pagerPos = currentTab;
        private float downX;
        private float downY;
        private float dragAnchorX;
        private float dragStartPos;
        private int dragStartTab;
        private boolean isHorizontalDrag;
        private boolean lockedVertical;
        private VelocityTracker velocityTracker;

        private boolean springRunning;
        private long springStartNanos;
        private float springStartOffset;
        private float springStartVel;
        private int springTarget;

        TabPagerView(Context ctx) {
            super(ctx);
            this.edgeBackWidth = dp(32);
            this.touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        }

        void jumpToTab(int tab) {
            int clamped = Math.max(0, Math.min(TAB_COUNT - 1, tab));
            cancelSpring();
            currentTab = clamped;
            applyOffset(clamped);
            if (tabBar != null && !searchMode) {
                tabBar.setSelectedTab(clamped, false);
            }
        }

        void springToTab(int target, float initialVelPagesPerSec) {
            int clamped = Math.max(0, Math.min(TAB_COUNT - 1, target));
            cancelSpring();
            currentTab = clamped;
            springTarget = clamped;
            springStartOffset = pagerPos - clamped;
            springStartVel = Math.max(-6.5f, Math.min(6.5f, initialVelPagesPerSec));
            if (Math.abs(springStartOffset) < 0.001f && Math.abs(springStartVel) < 0.01f) {
                applyOffset(clamped);
                if (tabBar != null && !searchMode) {
                    tabBar.setSelectedTab(clamped, false);
                }
                return;
            }
            springStartNanos = System.nanoTime();
            springRunning = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void cancelSpring() {
            if (springRunning) {
                springRunning = false;
                Choreographer.getInstance().removeFrameCallback(this);
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!springRunning) return;
            float t = Math.max(0f, (frameTimeNanos - springStartNanos) * 1e-9f);
            float exp = (float) Math.exp(-SPRING_OMEGA * t);
            float a = springStartVel + SPRING_OMEGA * springStartOffset;
            float x = (springStartOffset + a * t) * exp;
            float v = (springStartVel - SPRING_OMEGA * a * t) * exp;

            if ((Math.abs(x) < 0.0008f && Math.abs(v) < 0.01f) || t >= 0.8f) {
                springRunning = false;
                applyOffset(springTarget);
                if (tabBar != null && !searchMode) {
                    tabBar.setSelectedTab(springTarget, false);
                }
                return;
            }

            applyOffset(springTarget + x);
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void applyOffset(float pos) {
            pagerPos = pos;
            int w = getWidth();
            if (w > 0) {
                int count = getChildCount();
                for (int i = 0; i < count; i++) {
                    View page = getChildAt(i);
                    float diff = i - pos;
                    page.setTranslationX(diff * w);
                    int vis = Math.abs(diff) < 1.05f ? View.VISIBLE : View.INVISIBLE;
                    if (page.getVisibility() != vis) {
                        page.setVisibility(vis);
                    }
                }
            }
            if (tabBar != null && !searchMode) {
                tabBar.setDragPosition(pos);
            }
        }

        private void recycleTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            applyOffset(pagerPos);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            cancelSpring();
            recycleTracker();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (searchMode) {
                isHorizontalDrag = false;
                lockedVertical = false;
                recycleTracker();
                return false;
            }
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = ev.getX();
                downY = ev.getY();
                isHorizontalDrag = false;
                lockedVertical = false;
                recycleTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                if (!lockedVertical && !isHorizontalDrag) {
                    float dx = ev.getX() - downX;
                    float dy = ev.getY() - downY;
                    if (downX <= edgeBackWidth && dx > 0) {
                        lockedVertical = true;
                        return false;
                    }
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) >= Math.abs(dx)) {
                        lockedVertical = true;
                    } else if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        cancelSpring();
                        isHorizontalDrag = true;
                        dragAnchorX = ev.getX();
                        dragStartPos = pagerPos;
                        dragStartTab = Math.max(0, Math.min(TAB_COUNT - 1, Math.round(pagerPos)));
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isHorizontalDrag = false;
                lockedVertical = false;
                recycleTracker();
            }
            return isHorizontalDrag;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (searchMode) {
                isHorizontalDrag = false;
                lockedVertical = false;
                recycleTracker();
                return false;
            }
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
            velocityTracker.addMovement(ev);

            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                if (!isHorizontalDrag && !lockedVertical) {
                    if (downX <= edgeBackWidth && dx > 0) {
                        lockedVertical = true;
                    } else if (Math.abs(dy) > touchSlop && Math.abs(dy) >= Math.abs(dx)) {
                        lockedVertical = true;
                    } else if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        cancelSpring();
                        isHorizontalDrag = true;
                        dragAnchorX = ev.getX();
                        dragStartPos = pagerPos;
                        dragStartTab = Math.max(0, Math.min(TAB_COUNT - 1, Math.round(pagerPos)));
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                if (isHorizontalDrag) {
                    float w = Math.max(1f, getWidth());
                    float rawPos = dragStartPos - (ev.getX() - dragAnchorX) / w;
                    float max = TAB_COUNT - 1;
                    if (rawPos < 0f) {
                        float over = -rawPos;
                        rawPos = -(over / (1f + over * 3.5f)) * 0.45f;
                    } else if (rawPos > max) {
                        float over = rawPos - max;
                        rawPos = max + (over / (1f + over * 3.5f)) * 0.45f;
                    }
                    applyOffset(rawPos);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (isHorizontalDrag) {
                    velocityTracker.computeCurrentVelocity(1000);
                    float vx = velocityTracker.getXVelocity();
                    recycleTracker();
                    isHorizontalDrag = false;
                    lockedVertical = false;

                    float w = Math.max(1f, getWidth());
                    float velPagesPerSec = -vx / w;
                    float projected = pagerPos + velPagesPerSec * 0.14f;
                    int minTab = Math.max(0, dragStartTab - 1);
                    int maxTab = Math.min(TAB_COUNT - 1, dragStartTab + 1);
                    int target = action == MotionEvent.ACTION_UP
                            ? Math.max(minTab, Math.min(maxTab, Math.round(projected)))
                            : dragStartTab;
                    springToTab(target, velPagesPerSec);
                    return true;
                }
                isHorizontalDrag = false;
                lockedVertical = false;
                recycleTracker();
            }
            return true;
        }
    }

    public static void init(SettingsController settingsController) {
        Audio.init();
        try {
            HMethod.hookMethod(android.app.Activity.class, "onBackPressed", new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (isSettings && param.thisObject == Utils.getCurrentActivity()) {
                        settingsController.hide();
                        param.setResult(null);
                    }
                }
            });

            HMethod.hookMethod(android.app.Activity.class, "dispatchKeyEvent", android.view.KeyEvent.class, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (isSettings && param.thisObject == Utils.getCurrentActivity()) {
                        android.view.KeyEvent ev = (android.view.KeyEvent) param.args[0];
                        if (ev != null && ev.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                            if (ev.getAction() == android.view.KeyEvent.ACTION_UP && !ev.isCanceled()) {
                                settingsController.hide();
                            }
                            param.setResult(true);
                        }
                    }
                }
            });

            HMethod.hookMethod(ClassLoad.getClass(ClassNames.ANDROID_UTILITIES), Obfuscate.getMethodName("AndroidUtilities", "isTabletInternal"), new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (isSettings) {
                        param.setResult(false);
                    }
                }
            });
        } catch (Throwable e) {
            Logger.e(e);
        }
    }
}
