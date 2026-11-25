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
import android.view.Gravity;
import android.view.View;
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
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.Theme;
import com.my.televip.virtuals.messenger.browser.Browser;
import com.my.televip.virtuals.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity {

    public static boolean isSettings;

    private static final String PREF_THEME_MODE = "TeleVipUiThemeMode";
    private static final int THEME_FOLLOW = 0;
    private static final int THEME_LIGHT = 1;
    private static final int THEME_DARK = 2;

    private static final int TAB_PRIVACY = 0;
    private static final int TAB_CHATS = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_TOOLS = 3;

    private static final Map<String, String> SUBTITLE_DEFAULTS = new HashMap<>();

    static {
        SUBTITLE_DEFAULTS.put(Keys.HideSeen, "Read messages without sending read receipts");
        SUBTITLE_DEFAULTS.put(Keys.HideSeenPrivateChat, "Hide read receipts in private chats");
        SUBTITLE_DEFAULTS.put(Keys.HideSeenChannel, "Hide read receipts in channels and groups");
        SUBTITLE_DEFAULTS.put(Keys.MarkReadAfterSend, "With 'Seen' hidden, a chat is still marked as read once you reply or react in it");
        SUBTITLE_DEFAULTS.put(Keys.HideTyping, "Others won't see that you are typing");
        SUBTITLE_DEFAULTS.put(Keys.HideStoryView, "Watch stories without appearing in their viewers list");
        SUBTITLE_DEFAULTS.put(Keys.HidePhone, "Hides your own phone number across the app, handy for screenshots");
        SUBTITLE_DEFAULTS.put(Keys.HideOnline, "Appear online much less often. Tap the note below for details");
        SUBTITLE_DEFAULTS.put(Keys.DisableStories, "Hides the stories bar from the chat list");
        SUBTITLE_DEFAULTS.put(Keys.ShowDeletedMessages, "Keeps messages others delete and marks them as deleted");
        SUBTITLE_DEFAULTS.put(Keys.ShowMessageID, "Shows the ID of each message in the chat");
        SUBTITLE_DEFAULTS.put(Keys.SaveEditsHistory, "Keeps earlier versions of edited messages so you can look back at them");
        SUBTITLE_DEFAULTS.put(Keys.DownloadSpeed, "Increases parallel connection throughput for faster media downloads");
        SUBTITLE_DEFAULTS.put(Keys.SecretMediaSave, "Allows saving self-destructing photos and videos");
        SUBTITLE_DEFAULTS.put(Keys.PreventMedia, "Keeps disappearing secret media visible after viewing");
        SUBTITLE_DEFAULTS.put(Keys.EnableSavingStories, "Enables saving stories directly to your device");
        SUBTITLE_DEFAULTS.put(Keys.EnableVoiceMessageSaving, "Save voice messages directly as audio files");
        SUBTITLE_DEFAULTS.put(Keys.HidePinnedMessages, "Hides the pinned message bar at the top of chats");
        SUBTITLE_DEFAULTS.put(Keys.DisableChannelSwipeBack, "Disables swiping left/right to jump between channels");
        SUBTITLE_DEFAULTS.put(Keys.DisableProfileSwipeBack, "Prevents accidental swipe-back gestures on profile screens");
        SUBTITLE_DEFAULTS.put(Keys.HideProxySponsor, "Removes the sponsored channel entry when connected via proxy");
        SUBTITLE_DEFAULTS.put(Keys.ShowUserID, "Shows numeric Telegram user IDs on profile pages");
        SUBTITLE_DEFAULTS.put(Keys.Calendar, "Display Hijri or Persian dates in chat headers and timestamps");
        SUBTITLE_DEFAULTS.put(Keys.RemovesContentSaving, "Forward, copy and save from chats and channels that restrict it");
        SUBTITLE_DEFAULTS.put(Keys.SayWhereCopyCameFrom, "Add a clickable \"Forwarded from\" header linking to the sender and original message when copying from restricted chats");
        SUBTITLE_DEFAULTS.put(Keys.TelegramPremium, "Unlocks local Telegram Premium features on this device only");
        SUBTITLE_DEFAULTS.put(Keys.DisableNumberRounding, "Shows exact counts, for example 5300 instead of 5.3K");
        SUBTITLE_DEFAULTS.put(Keys.HideUpdateApp, "Suppresses in-app Telegram client update prompts");
        SUBTITLE_DEFAULTS.put(Keys.FixTLError, "Hides occasional 'TL error' pop-ups shown by the client");
        SUBTITLE_DEFAULTS.put(Keys.DeveloperChannel, "t.me/televipnext");
        SUBTITLE_DEFAULTS.put(Keys.RestartApp, "Applies the settings marked 'Restart required'");
    }

    private final Context context;
    public RecyclerListView listView;

    private FrameLayout rootView;
    private SettingsController controller;
    private int currentTab = TAB_PRIVACY;
    private boolean searchMode = false;
    private String searchQuery = "";

    private LinearLayout normalHeaderGroup;
    private LinearLayout searchHeaderGroup;
    private EditText searchInput;
    private ScrollView mainScrollView;

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

    private boolean isEffectiveLight() {
        int mode = ConfigPreferences.getInt(PREF_THEME_MODE);
        if (mode == THEME_LIGHT) return true;
        if (mode == THEME_DARK) return false;
        return Theme.isLight();
    }

    private void toggleTheme() {
        boolean nextLight = !isEffectiveLight();
        ConfigPreferences.putInt(PREF_THEME_MODE, nextLight ? THEME_LIGHT : THEME_DARK);
        hideKeyboard();
        rebuildUi();
    }

    private void rebuildUi() {
        if (rootView == null) return;
        rootView.removeAllViews();
        allTabSections.clear();
        searchableRows.clear();
        sectionCards.clear();
        footnoteViews.clear();
        actionsCardView = null;
        footerCreditView = null;
        emptySearchCard = null;

        final boolean light = isEffectiveLight();
        final GlassDesign.Palette p = GlassDesign.Palette.of(light);
        rootView.setBackground(GlassDesign.createAuroraBackground(p));

        try {
            // 1. Scrollable full-bleed content layer
            mainScrollView = new ScrollView(context);
            mainScrollView.setVerticalScrollBarEnabled(false);
            mainScrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            mainScrollView.setClipToPadding(false);

            int statusH = getDimenPx("status_bar_height");
            int navH = getDimenPx("navigation_bar_height");
            int fallbackTopPad = statusH + dp(116);
            mainScrollView.setPadding(0, fallbackTopPad, 0, navH + dp(28));

            LinearLayout contentContainer = new LinearLayout(context);
            contentContainer.setOrientation(LinearLayout.VERTICAL);

            populateContent(contentContainer, p);

            mainScrollView.addView(contentContainer, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            rootView.addView(mainScrollView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            // 2. Floating Glass Top Header + Segmented Tab Bar overlay
            LinearLayout floatingHeader = buildFloatingHeader(p, statusH);
            rootView.addView(floatingHeader, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

            floatingHeader.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int h = b - t;
                if (h > 0 && mainScrollView.getPaddingTop() != h + dp(4)) {
                    mainScrollView.setPadding(0, h + dp(4), 0, navH + dp(28));
                }
            });

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

        TextView titleTv = new TextView(context);
        titleTv.setText("TeleVip Next Settings \uD83D\uDC7B");
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setGravity(Gravity.CENTER);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(8));
        titleLp.setMarginEnd(dp(8));
        normalHeaderGroup.addView(titleTv, titleLp);

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
        themeBtn.setOnClickListener(v -> toggleTheme());
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
        searchInput.setHint("Search settings");
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
        String[] tabTitles = new String[]{"Privacy", "Chats", "Appearance", "Tools"};
        GlassDesign.SegmentedTabBar tabBar = new GlassDesign.SegmentedTabBar(context, tabTitles, p, currentTab);
        tabBar.setOnTabSelectedListener(index -> {
            currentTab = index;
            if (searchMode) {
                exitSearchMode();
            } else {
                applyVisibilityFilter();
                if (mainScrollView != null) mainScrollView.scrollTo(0, 0);
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

    private void populateContent(LinearLayout container, GlassDesign.Palette p) {
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

        // Render feature sections as frosted glass cards assigned to tabs
        for (SectionData sec : featureSections) {
            int tab = resolveTabForSection(sec.headerKey);
            LinearLayout card = buildSectionCard(sec.headerKey, sec.items, p, tab);
            container.addView(card, marginLp(dp(12), 0, dp(12), dp(12)));
            sectionCards.add(card);
            allTabSections.add(new TabSection(tab, card, false));

            for (ConfigItem note : sec.footnotes) {
                TextView noteTv = buildFootnoteView(note, p);
                container.addView(noteTv, marginLp(dp(20), 0, dp(20), dp(14)));
                footnoteViews.add(noteTv);
                allTabSections.add(new TabSection(tab, noteTv, true));
            }
        }

        // Render standalone action items (Developer Channel, Restart App) in Tools tab
        if (!standaloneActions.isEmpty()) {
            actionsCardView = buildActionsCard(standaloneActions, p, TAB_TOOLS);
            container.addView(actionsCardView, marginLp(dp(12), 0, dp(12), dp(14)));
            sectionCards.add(actionsCardView);
            allTabSections.add(new TabSection(TAB_TOOLS, actionsCardView, false));
        }

        // Empty search state card
        emptySearchCard = createGlassCard(p);
        emptySearchCard.setPadding(dp(20), dp(24), dp(20), dp(24));
        emptySearchCard.setGravity(Gravity.CENTER);
        TextView emptyTv = new TextView(context);
        emptyTv.setText("No matching settings found");
        emptyTv.setTextColor(p.textSecondary);
        emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        emptyTv.setGravity(Gravity.CENTER);
        emptySearchCard.addView(emptyTv);
        emptySearchCard.setVisibility(View.GONE);
        container.addView(emptySearchCard, marginLp(dp(12), dp(4), dp(12), dp(12)));

        // Footer credit (visible on Tools tab)
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
        container.addView(footerCreditView);
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
            SettingsAdapter.playAudio(context);
            return true;
        });

        wrapper.addView(row);
        return wrapper;
    }

    private View buildExpandableItemContainer(ConfigItem parentItem, GlassDesign.Palette p) {
        List<ConfigItem> children = parentItem.getChildren();
        int initialEnabled = 0;
        int totalChildren = children != null ? children.size() : 0;
        if (children != null) {
            for (ConfigItem c : children) {
                if (c.isEnable()) initialEnabled++;
            }
        }
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
        titleTv.setText(formatExpandableTitle(parentItem, initialEnabled, totalChildren));
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
                    child.run();

                    int count = 0;
                    for (ConfigItem c : children) {
                        if (c.isEnable()) count++;
                    }
                    parentItem.setEnable(count > 0);
                    parentSw.setChecked(count > 0, true);
                    titleTv.setText(formatExpandableTitle(parentItem, count, children.size()));
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
                    c.setEnable(next);
                    c.run();
                    if (childSwitches[i] != null) childSwitches[i].setChecked(next, true);
                }
                titleTv.setText(formatExpandableTitle(parentItem, next ? children.size() : 0, children.size()));
            }
        });

        parentRow.setOnClickListener(v -> {
            boolean show = childrenContainer.getVisibility() != View.VISIBLE;
            childrenContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        });

        return wrapper;
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

        if (isCalendar) {
            TextView valTv = new TextView(context);
            valTv.setTextColor(p.accentBlue);
            valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            valTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            valTv.setText(getCalendarLabel(item.getCustomCalendar()));
            valTv.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(p.accentSoft);
            pill.setCornerRadius(dp(10));
            valTv.setBackground(pill);
            row.addView(valTv);

            row.setOnClickListener(v -> showCalendarPicker(item, valTv));
        } else if (isChannel) {
            row.setOnClickListener(v -> {
                try {
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

    private void showCalendarPicker(ConfigItem item, TextView valTv) {
        String[] options = new String[]{
                Translator.get(Keys.Gregorian),
                Translator.get(Keys.Hijri),
                Translator.get(Keys.Persian)
        };
        try {
            int themeRes = isEffectiveLight()
                    ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                    : android.R.style.Theme_DeviceDefault_Dialog_Alert;
            new AlertDialog.Builder(context, themeRes)
                    .setTitle(Translator.get(Keys.Calendar))
                    .setSingleChoiceItems(options, item.getCustomCalendar(), (dialog, which) -> {
                        item.setCustomCalendar(which);
                        item.run();
                        valTv.setText(getCalendarLabel(which));
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

    private void applyVisibilityFilter() {
        if (searchMode && !searchQuery.isEmpty()) {
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

        if (emptySearchCard != null) emptySearchCard.setVisibility(View.GONE);
        for (SearchableRow sr : searchableRows) {
            sr.rowView.setVisibility(View.VISIBLE);
        }
        for (TabSection ts : allTabSections) {
            ts.view.setVisibility(ts.tabIndex == currentTab ? View.VISIBLE : View.GONE);
        }
        updateActionsCardDividers();
        if (footerCreditView != null) {
            footerCreditView.setVisibility(currentTab == TAB_TOOLS ? View.VISIBLE : View.GONE);
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
        String key = item.getKey();
        String desc = item.getValue();
        if (desc == null || desc.isEmpty()) {
            desc = SUBTITLE_DEFAULTS.get(key);
        } else if (Keys.DisableNumberRounding.equals(key)) {
            desc = "Shows exact counts, for example " + desc;
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

        TabSection(int tabIndex, View view, boolean footnote) {
            this.tabIndex = tabIndex;
            this.view = view;
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
