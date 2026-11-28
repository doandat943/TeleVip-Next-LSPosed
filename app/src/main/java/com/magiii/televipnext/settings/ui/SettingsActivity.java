package com.magiii.televipnext.settings.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.magiii.televipnext.Audio;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigItem;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.Configs.NativeFeatureTarget;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.features.messages.ShowDeletedMessages;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.settings.NativeFeatureNavigator;
import com.magiii.televipnext.settings.controller.SettingsController;
import com.magiii.televipnext.ui.RestartTrampolineActivity;
import com.magiii.televipnext.updater.UpdateManager;
import com.magiii.televipnext.utils.DialogUtils;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;
import com.magiii.televipnext.virtuals.messenger.browser.Browser;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity {

    public static boolean isSettings;

    private static final int TAB_PRIVACY = 0;
    private static final int TAB_CHATS = 1;
    private static final int TAB_APPEARANCE = 2;
    private static final int TAB_TOOLS = 3;

    private final Context context;

    private static final int TAB_COUNT = 4;
    private static final long NATIVE_DOUBLE_TAP_WINDOW_MS = 2000L;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private FrameLayout rootView;
    private SettingsController controller;
    private int currentTab = TAB_PRIVACY;

    private GlassDesign.SegmentedTabBar tabBar;
    private TabPagerView tabPager;
    private final ScrollView[] tabScrollViews = new ScrollView[TAB_COUNT];
    private final LinearLayout[] tabContainers = new LinearLayout[TAB_COUNT];
    private boolean isRtl = false;
    private int lastHeaderPadTop = 0;
    private int lastNavPadBottom = 0;
    private UpdateManager.Listener updateListener;

    private GlassDesign.Palette currentPalette;
    private String cachedClientName;
    private String armedNativeKey;
    private long armedNativeTimestampMs;
    private TextView armedChipTextView;
    private ImageView armedChipIconView;
    private GradientDrawable armedChipBg;
    private String armedDefaultLabel;
    private Runnable disarmNativeRunnable;
    private TextView floatingToastView;
    private Runnable hideToastRunnable;

    public SettingsActivity(Context context) {
        this.context = context;
    }

    public View createView(SettingsController settingsController) {
        this.controller = settingsController;
        rootView = new FrameLayout(context);
        rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                clearTransientFeedback();
                if (updateListener != null) {
                    UpdateManager.removeListener(updateListener);
                    updateListener = null;
                }
            }
        });
        rebuildUi();
        return rootView;
    }

    private void toggleTheme(View anchorView) {
        boolean wasLight = Theme.isLight();
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
        clearTransientFeedback();
        if (updateListener != null) {
            UpdateManager.removeListener(updateListener);
            updateListener = null;
        }
        isRtl = Translator.isRtl();
        rootView.setLayoutDirection(isRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        rootView.removeAllViews();
        tabBar = null;
        tabPager = null;
        floatingToastView = null;
        for (int i = 0; i < TAB_COUNT; i++) {
            tabScrollViews[i] = null;
            tabContainers[i] = null;
        }

        final GlassDesign.Palette p = GlassDesign.Palette.of(light);
        currentPalette = p;
        cachedClientName = ClientManager.getCurrentDisplayName(context);
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

            populateContent(p);

            // 2. Floating Glass Top Header + Segmented Tab Bar overlay
            LinearLayout floatingHeader = buildFloatingHeader(p, statusH);
            rootView.addView(floatingHeader, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

            // 3. Floating Liquid Glass Toast overlay (for native-feature double-tap hints)
            floatingToastView = buildFloatingToastView();
            FrameLayout.LayoutParams toastLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            toastLp.leftMargin = dp(20);
            toastLp.rightMargin = dp(20);
            toastLp.bottomMargin = navH + dp(22);
            rootView.addView(floatingToastView, toastLp);

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
                }
            });

            tabPager.jumpToTab(currentTab);
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private LinearLayout buildFloatingHeader(GlassDesign.Palette p, int statusH) {
        LinearLayout headerBox = new LinearLayout(context);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        headerBox.setBackground(GlassDesign.createTopScrimDrawable(p));
        headerBox.setPadding(0, statusH + dp(8), 0, dp(12));

        // Top Bar: [Back Circle]   [Title]   [Theme | Restart Pill]
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView backBtn = createCircleIconButton(p, GlassDesign.GlassIcons.backArrow(p.textPrimary));
        backBtn.setOnClickListener(v -> {
            if (controller != null) controller.hide();
        });
        topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        FrameLayout brandSlot = new FrameLayout(context);
        LinearLayout.LayoutParams brandSlotLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandSlotLp.leftMargin = dp(8);
        brandSlotLp.rightMargin = dp(8);

        LinearLayout brandPill = new LinearLayout(context);
        brandPill.setOrientation(LinearLayout.HORIZONTAL);
        brandPill.setGravity(Gravity.CENTER_VERTICAL);
        brandPill.setMinimumHeight(dp(40));
        brandPill.setPadding(dp(8), dp(6), dp(10), dp(6));
        brandPill.setBackground(GlassDesign.createGlassPillDrawable(p, dp(20)));
        brandPill.setForeground(GlassDesign.createPillRippleOverlay(p.ripple, dp(20)));
        GlassDesign.attachPressScale(brandPill);
        brandPill.setOnClickListener(v -> CreditsSheet.show(context, controller, this::rebuildUi));

        ImageView emblemIv = new ImageView(context);
        emblemIv.setImageDrawable(GlassDesign.GlassIcons.brandEmblemDrawable(dp(8)));
        LinearLayout.LayoutParams emblemLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        emblemLp.rightMargin = dp(8);
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

        View updateDot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(Color.rgb(48, 209, 88));
        updateDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotLp.leftMargin = dp(6);
        brandPill.addView(updateDot, dotLp);
        updateBrandDot(updateDot, UpdateManager.getState());
        updateListener = (state, release) -> updateBrandDot(updateDot, state);
        UpdateManager.addListener(updateListener);

        ImageView chevronIv = new ImageView(context);
        chevronIv.setImageDrawable(GlassDesign.GlassIcons.chevronRight(p.textMuted));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        chevLp.leftMargin = dp(4);
        brandPill.addView(chevronIv, chevLp);

        FrameLayout.LayoutParams pillCenterLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), Gravity.CENTER);
        brandSlot.addView(brandPill, pillCenterLp);
        topBar.addView(brandSlot, brandSlotLp);

        // Merged dual-icon glass pill (Theme toggle + Restart)
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

        ImageView restartBtn = new ImageView(context);
        restartBtn.setImageDrawable(GlassDesign.GlassIcons.restartIcon(p.textPrimary));
        restartBtn.setScaleType(ImageView.ScaleType.CENTER);
        restartBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        restartBtn.setContentDescription(Translator.get(Keys.RestartApp));
        restartBtn.setBackground(GlassDesign.createPillRippleOverlay(p.ripple, dp(21)));
        restartBtn.setOnClickListener(v -> restartHostApp(v));
        actionPill.addView(restartBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));

        topBar.addView(actionPill, new LinearLayout.LayoutParams(dp(84), dp(42)));

        LinearLayout.LayoutParams topBarLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        topBarLp.setMargins(dp(14), 0, dp(14), dp(12));
        headerBox.addView(topBar, topBarLp);

        // Segmented Tab Bar
        String[] tabTitles = new String[]{
                Translator.get(Keys.TabPrivacy),
                Translator.get(Keys.TabChats),
                Translator.get(Keys.TabAppearance),
                Translator.get(Keys.TabTools)
        };
        tabBar = new GlassDesign.SegmentedTabBar(context, tabTitles, p, currentTab);
        tabBar.setOnTabSelectedListener(index -> {
            if (tabPager != null) {
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
            LinearLayout card = buildSectionCard(sec.headerKey, sec.items, p);
            LinearLayout.LayoutParams cardLp = marginLp(dp(12), 0, dp(12), dp(12));
            if (pageCol != null) pageCol.addView(card, cardLp);

            for (ConfigItem note : sec.footnotes) {
                TextView noteTv = buildFootnoteView(note, p);
                LinearLayout.LayoutParams noteLp = marginLp(dp(20), 0, dp(20), dp(14));
                if (pageCol != null) pageCol.addView(noteTv, noteLp);
            }
        }

        // Render standalone action items (Developer Channel, Restart App) in Tools tab
        LinearLayout toolsCol = tabContainers[TAB_TOOLS];
        if (!standaloneActions.isEmpty()) {
            LinearLayout actionsCard = buildActionsCard(standaloneActions, p);
            LinearLayout.LayoutParams actionsLp = marginLp(dp(12), 0, dp(12), dp(14));
            if (toolsCol != null) toolsCol.addView(actionsCard, actionsLp);
        }
    }

    private int resolveTabForSection(String headerKey) {
        if (Keys.GhostModeSettings.equals(headerKey)) return TAB_PRIVACY;
        if (Keys.MessagesSettings.equals(headerKey) || Keys.MediaSettings.equals(headerKey)) return TAB_CHATS;
        if (Keys.UiSettings.equals(headerKey) || Keys.StoriesSettings.equals(headerKey)) return TAB_APPEARANCE;
        return TAB_TOOLS;
    }

    private LinearLayout buildSectionCard(String headerKey, List<ConfigItem> items, GlassDesign.Palette p) {
        LinearLayout card = createGlassCard(p);

        // Card header row with colored badge icon + blue title
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity((isRtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(16), dp(14), dp(16), dp(12));

        ImageView badge = new ImageView(context);
        badge.setImageDrawable(createSectionBadge(headerKey));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(28), dp(28));

        TextView headerTv = new TextView(context);
        headerTv.setText(Translator.get(headerKey));
        headerTv.setTextColor(p.accentBlue);
        headerTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        headerTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headerTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        headerTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

        if (isRtl) {
            badgeLp.leftMargin = dp(12);
            headerRow.addView(headerTv);
            headerRow.addView(badge, badgeLp);
        } else {
            badgeLp.rightMargin = dp(12);
            headerRow.addView(badge, badgeLp);
            headerRow.addView(headerTv);
        }

        card.addView(headerRow);

        for (ConfigItem item : items) {
            int type = item.getType();
            if (type == ConfigItem.SWITCH) {
                card.addView(buildSwitchItemContainer(item, p));
            } else if (type == ConfigItem.EXPANDABLE_SWITCH) {
                card.addView(buildExpandableItemContainer(item, p));
            } else if (type == ConfigItem.TEXT) {
                card.addView(buildTextItemContainer(item, p, false));
            }
        }

        return card;
    }

    private LinearLayout buildActionsCard(List<ConfigItem> actions, GlassDesign.Palette p) {
        LinearLayout card = createGlassCard(p);
        for (int i = 0; i < actions.size(); i++) {
            card.addView(buildTextItemContainer(actions.get(i), p, i == 0));
        }
        return card;
    }

    private View buildSwitchItemContainer(ConfigItem item, GlassDesign.Palette p) {
        if (item.isNativeBuiltIn()) {
            return buildDisabledNativeRow(item, item.getNativeTarget(), p, false, false, true);
        }
        LinearLayout wrapper = new LinearLayout(context);
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
        textCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(item.getKey()));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(item);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            subTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2.5f);
            textCol.addView(subTv, subLp);
        }

        GlassDesign.PillSwitchView sw = new GlassDesign.PillSwitchView(context, item.isEnable(), p);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(48), dp(28));
        if (isRtl) {
            swLp.rightMargin = dp(14);
            row.addView(sw, swLp);
            row.addView(textCol, textLp);
        } else {
            textLp.rightMargin = dp(14);
            row.addView(textCol, textLp);
            row.addView(sw, swLp);
        }

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

        // Case 1: Full-native expandable with a single unified destination -> render as one disabled native row
        NativeFeatureTarget unifiedTarget = parentItem.isNativeBuiltIn()
                ? parentItem.getNativeTarget()
                : parentItem.getUnifiedNativeChildrenTarget();
        if (unifiedTarget != null) {
            return buildDisabledNativeRow(parentItem, unifiedTarget, p, false, false, true);
        }

        // Case 2: All switch children are native, but jump to heterogeneous destinations -> muted expandable parent
        if (parentItem.isAllSwitchChildrenNative()) {
            return buildHeterogeneousNativeExpandableContainer(parentItem, p);
        }

        // Case 3: Active expandable (zero or partial native children)
        final int totalSwitchChildren = parentItem.getActiveSwitchChildCount();
        final int initialEnabled = parentItem.getEnabledActiveSwitchChildCount();
        parentItem.setEnable(initialEnabled > 0);

        LinearLayout wrapper = new LinearLayout(context);
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
        textCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(context);
        titleTv.setText(formatExpandableTitle(parentItem, initialEnabled, totalSwitchChildren));
        titleTv.setTextColor(p.textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(parentItem);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            subTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2.5f);
            textCol.addView(subTv, subLp);
        }

        GlassDesign.PillSwitchView parentSw = new GlassDesign.PillSwitchView(context, initialEnabled > 0, p);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams pSwLp = new LinearLayout.LayoutParams(dp(48), dp(28));
        if (isRtl) {
            pSwLp.rightMargin = dp(14);
            parentRow.addView(parentSw, pSwLp);
            parentRow.addView(textCol, textLp);
        } else {
            textLp.rightMargin = dp(14);
            parentRow.addView(textCol, textLp);
            parentRow.addView(parentSw, pSwLp);
        }

        wrapper.addView(parentRow);
        wrapper.addView(childrenContainer);

        final GlassDesign.PillSwitchView[] childSwitches = children != null
                ? new GlassDesign.PillSwitchView[children.size()]
                : new GlassDesign.PillSwitchView[0];

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                final ConfigItem child = children.get(i);
                if (child.isNativeBuiltIn()) {
                    childrenContainer.addView(buildDisabledNativeRow(
                            child, child.getNativeTarget(), p, false, true, child.getType() == ConfigItem.SWITCH));
                    continue;
                }

                childrenContainer.addView(createHairlineDivider(p, dp(32)));

                LinearLayout childRow = new LinearLayout(context);
                childRow.setOrientation(LinearLayout.HORIZONTAL);
                childRow.setGravity(Gravity.CENTER_VERTICAL);
                childRow.setPadding(isRtl ? dp(16) : dp(32), dp(13), isRtl ? dp(32) : dp(16), dp(13));
                childRow.setMinimumHeight(dp(50));
                childRow.setBackground(GlassDesign.createRowRipple(p.ripple));

                if (child.getType() == ConfigItem.TEXT) {
                    LinearLayout cTextCol = new LinearLayout(context);
                    cTextCol.setOrientation(LinearLayout.VERTICAL);
                    cTextCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

                    TextView childTitle = new TextView(context);
                    childTitle.setText(Translator.get(child.getKey()));
                    childTitle.setTextColor(Color.rgb(255, 69, 58));
                    childTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                    childTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    childTitle.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
                    childTitle.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                    cTextCol.addView(childTitle);

                    String childSub = buildSubtitleText(child);
                    if (childSub != null && !childSub.isEmpty()) {
                        TextView cSubTv = new TextView(context);
                        cSubTv.setText(childSub);
                        cSubTv.setTextColor(p.textSecondary);
                        cSubTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                        cSubTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
                        cSubTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
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
                childTitle.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
                childTitle.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

                GlassDesign.PillSwitchView childSw = new GlassDesign.PillSwitchView(context, child.isEnable(), p);
                childSwitches[i] = childSw;
                LinearLayout.LayoutParams cTextLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                LinearLayout.LayoutParams cSwLp = new LinearLayout.LayoutParams(dp(48), dp(28));
                if (isRtl) {
                    cSwLp.rightMargin = dp(14);
                    childRow.addView(childSw, cSwLp);
                    childRow.addView(childTitle, cTextLp);
                } else {
                    cTextLp.rightMargin = dp(14);
                    childRow.addView(childTitle, cTextLp);
                    childRow.addView(childSw, cSwLp);
                }

                childRow.setOnClickListener(v -> {
                    boolean next = !childSw.isChecked();
                    childSw.setChecked(next, true);
                    child.setEnable(next);

                    int count = 0;
                    for (ConfigItem c : children) {
                        if (c.getType() == ConfigItem.SWITCH && !c.isNativeBuiltIn() && c.isEnable()) count++;
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
                    if (c.getType() != ConfigItem.SWITCH || c.isNativeBuiltIn()) continue;
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

    private View buildHeterogeneousNativeExpandableContainer(ConfigItem parentItem, GlassDesign.Palette p) {
        List<ConfigItem> children = parentItem.getChildren();

        LinearLayout wrapper = new LinearLayout(context);
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
        textCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(parentItem.getKey()));
        titleTv.setTextColor(p.textDisabled);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(parentItem, true);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textDisabledSub);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            subTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2.5f);
            textCol.addView(subTv, subLp);
        }

        TextView nativeNoteTv = new TextView(context);
        nativeNoteTv.setText(Translator.get(Keys.NativeBuiltInHintNoJump, getClientDisplayName()));
        nativeNoteTv.setTextColor(p.nativeChipText);
        nativeNoteTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        nativeNoteTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nativeNoteTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        nativeNoteTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = dp(4f);
        textCol.addView(nativeNoteTv, noteLp);

        LinearLayout endGroup = new LinearLayout(context);
        endGroup.setOrientation(LinearLayout.HORIZONTAL);
        endGroup.setGravity(Gravity.CENTER_VERTICAL);

        TextView builtInPill = buildMutedBuiltInPill(p);
        ImageView chevronIv = new ImageView(context);
        chevronIv.setImageDrawable(GlassDesign.GlassIcons.chevronDown(p.textMuted));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        if (isRtl) {
            chevLp.rightMargin = dp(6);
            endGroup.addView(chevronIv, chevLp);
            endGroup.addView(builtInPill);
        } else {
            chevLp.leftMargin = dp(6);
            endGroup.addView(builtInPill);
            endGroup.addView(chevronIv, chevLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams endLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (isRtl) {
            endLp.rightMargin = dp(12);
            parentRow.addView(endGroup, endLp);
            parentRow.addView(textCol, textLp);
        } else {
            textLp.rightMargin = dp(12);
            parentRow.addView(textCol, textLp);
            parentRow.addView(endGroup, endLp);
        }

        wrapper.addView(parentRow);
        wrapper.addView(childrenContainer);

        if (children != null) {
            for (ConfigItem child : children) {
                if (child == null) continue;
                if (child.isNativeBuiltIn()) {
                    childrenContainer.addView(buildDisabledNativeRow(
                            child, child.getNativeTarget(), p, false, true, child.getType() == ConfigItem.SWITCH));
                } else if (child.getType() == ConfigItem.TEXT) {
                    childrenContainer.addView(createHairlineDivider(p, dp(32)));
                    LinearLayout childRow = new LinearLayout(context);
                    childRow.setOrientation(LinearLayout.HORIZONTAL);
                    childRow.setGravity(Gravity.CENTER_VERTICAL);
                    childRow.setPadding(isRtl ? dp(16) : dp(32), dp(13), isRtl ? dp(32) : dp(16), dp(13));
                    childRow.setMinimumHeight(dp(50));
                    childRow.setBackground(GlassDesign.createRowRipple(p.ripple));

                    LinearLayout cTextCol = new LinearLayout(context);
                    cTextCol.setOrientation(LinearLayout.VERTICAL);
                    cTextCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

                    TextView childTitle = new TextView(context);
                    childTitle.setText(Translator.get(child.getKey()));
                    childTitle.setTextColor(Color.rgb(255, 69, 58));
                    childTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
                    childTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                    childTitle.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
                    childTitle.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                    cTextCol.addView(childTitle);

                    String childSub = buildSubtitleText(child);
                    if (childSub != null && !childSub.isEmpty()) {
                        TextView cSubTv = new TextView(context);
                        cSubTv.setText(childSub);
                        cSubTv.setTextColor(p.textSecondary);
                        cSubTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                        cSubTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
                        cSubTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                        LinearLayout.LayoutParams cSubLp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        cSubLp.topMargin = dp(2f);
                        cTextCol.addView(cSubTv, cSubLp);
                    }

                    childRow.addView(cTextCol, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    childRow.setOnClickListener(v -> showClearDeletedConfirm());
                    childrenContainer.addView(childRow);
                }
            }
        }

        parentRow.setOnClickListener(v -> {
            boolean show = childrenContainer.getVisibility() != View.VISIBLE;
            childrenContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            chevronIv.animate().rotation(show ? 180f : 0f).setDuration(180).start();
        });

        return wrapper;
    }

    private void showClearDeletedConfirm() {
        new GlassDialog.Builder(context)
                .setTitle(Translator.get(Keys.ClearDeletedMessages))
                .setMessage(Translator.get(Keys.ClearDeletedConfirm))
                .setPositiveButton(Translator.get(Keys.ClearAction), () ->
                        ShowDeletedMessages.clearAllDeletedMessages(context))
                .setNegativeButton(Translator.get(Keys.Cancel), null)
                .show();
    }

    private String formatExpandableTitle(ConfigItem parentItem, int enabled, int total) {
        String base = Translator.get(parentItem.getKey());
        return total > 0 ? (base + " " + enabled + "/" + total) : base;
    }

    private View buildTextItemContainer(ConfigItem item, GlassDesign.Palette p, boolean hideTopDivider) {
        if (item.isNativeBuiltIn()) {
            return buildDisabledNativeRow(item, item.getNativeTarget(), p, hideTopDivider, false, false);
        }
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        if (!hideTopDivider) {
            wrapper.addView(createHairlineDivider(p));
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
        boolean isAlwaysShowProxy = Keys.AlwaysShowProxy.equals(key);

        ImageView iconBadge = null;
        LinearLayout.LayoutParams iconLp = null;
        if (isChannel || isRestart) {
            iconBadge = new ImageView(context);
            if (isChannel) {
                iconBadge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                        Color.rgb(255, 212, 38), dp(9), GlassDesign.ICON_TELEGRAM, Color.rgb(30, 32, 38)));
            } else {
                iconBadge.setImageDrawable(GlassDesign.GlassIcons.badgeDrawable(
                        Color.rgb(255, 69, 58), dp(9), GlassDesign.ICON_RESTART, Color.WHITE));
            }
            iconLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        }

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(key));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        titleTv.setTextColor(isChannel ? p.accentBlue : p.textPrimary);
        titleTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
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
            subTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2f);
            textCol.addView(subTv, subLp);
        }

        boolean hasRightPill = isCalendar || isDownloadSpeed || isAlwaysShowProxy;
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        if (hasRightPill) {
            TextView valTv = new TextView(context);
            valTv.setTextColor(p.accentBlue);
            valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            valTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(p.accentSoft);
            pill.setCornerRadius(dp(10));
            valTv.setBackground(pill);
            valTv.setPadding(dp(10), dp(4), dp(10), dp(4));

            if (isCalendar) {
                valTv.setText(getCalendarLabel(item.getIntValue()));
            } else if (isDownloadSpeed) {
                valTv.setText(getDownloadSpeedLabel(item.getIntValue()));
            } else {
                valTv.setText(getProxyDisplayLabel(item.getIntValue()));
            }

            if (isRtl) {
                LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                valLp.rightMargin = dp(12);
                row.addView(valTv, valLp);
                row.addView(textCol, textLp);
            } else {
                textLp.rightMargin = dp(12);
                row.addView(textCol, textLp);
                row.addView(valTv);
            }

            row.setOnClickListener(v -> {
                if (isCalendar) {
                    showOptionPicker(Translator.get(Keys.Calendar), new String[]{
                            Translator.get(Keys.Gregorian),
                            Translator.get(Keys.Hijri),
                            Translator.get(Keys.Persian)
                    }, item, valTv);
                } else if (isDownloadSpeed) {
                    showOptionPicker(Translator.get(Keys.DownloadSpeed), new String[]{
                            Translator.get(Keys.SpeedOff),
                            Translator.get(Keys.SpeedFast),
                            Translator.get(Keys.SpeedUltra)
                    }, item, valTv);
                } else {
                    showOptionPicker(Translator.get(Keys.AlwaysShowProxy), new String[]{
                            Translator.get(Keys.ProxyDisplayOff),
                            Translator.get(Keys.ProxyDisplayMenu),
                            Translator.get(Keys.ProxyDisplayTopBar)
                    }, item, valTv);
                }
            });
        } else {
            if (iconBadge != null && iconLp != null) {
                if (isRtl) {
                    iconLp.leftMargin = dp(14);
                    row.addView(textCol, textLp);
                    row.addView(iconBadge, iconLp);
                } else {
                    iconLp.rightMargin = dp(14);
                    row.addView(iconBadge, iconLp);
                    row.addView(textCol, textLp);
                }
            } else {
                row.addView(textCol, textLp);
            }
            if (isChannel) {
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
        tv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
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

    private void showOptionPicker(String title, String[] options, ConfigItem item, TextView valTv) {
        int current = item.getIntValue();
        if (current < 0 || current >= options.length) current = 0;
        GlassDialog.showSingleChoice(context, title, options, current, which -> {
            item.setIntValue(which);
            item.run();
            valTv.setText(options[which]);
        });
    }

    private void restartHostApp(View anchor) {
        RestartTrampolineActivity.restartHost(Utils.getCurrentActivity());
    }

    private void updateBrandDot(View updateDot, UpdateManager.State state) {
        if (updateDot == null) return;
        boolean ready = state == UpdateManager.State.READY_TO_INSTALL
                || state == UpdateManager.State.INSTALLED_NEEDS_RESTART;
        updateDot.setVisibility(ready ? View.VISIBLE : View.GONE);
    }

    private View buildDisabledNativeRow(
            ConfigItem item,
            NativeFeatureTarget target,
            GlassDesign.Palette p,
            boolean hideTopDivider,
            boolean isChild,
            boolean useDisabledSwitch) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        if (!hideTopDivider) {
            wrapper.addView(createHairlineDivider(p, isChild ? dp(32) : dp(16)));
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (isChild) {
            row.setPadding(isRtl ? dp(16) : dp(32), dp(12), isRtl ? dp(32) : dp(16), dp(12));
            row.setMinimumHeight(dp(50));
        } else {
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setMinimumHeight(dp(56));
        }
        row.setBackground(GlassDesign.createRowRipple(p.ripple));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(item.getKey()));
        titleTv.setTextColor(p.textDisabled);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isChild ? 15.5f : 16f);
        titleTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        titleTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        textCol.addView(titleTv);

        String subtitle = buildSubtitleText(item, true);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(p.textDisabledSub);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, isChild ? 12.5f : 13f);
            subTv.setLineSpacing(dp(2f), 1.0f);
            subTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
            subTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(2f);
            textCol.addView(subTv, subLp);
        }

        final boolean jumpable = target != null && target.isJumpable();
        final String clientName = getClientDisplayName();
        final String defaultChipLabel = jumpable
                ? Translator.get(Keys.NativeBuiltInHint, clientName)
                : Translator.get(Keys.NativeBuiltInHintNoJump, clientName);

        LinearLayout chipBox = new LinearLayout(context);
        chipBox.setOrientation(LinearLayout.HORIZONTAL);
        chipBox.setGravity(Gravity.CENTER_VERTICAL);
        chipBox.setPadding(dp(8), dp(3.5f), dp(8), dp(3.5f));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(dp(9));
        if (jumpable) {
            chipBg.setColor(p.nativeChipBg);
            chipBg.setStroke(Math.max(1, dp(0.75f)), p.nativeChipBorder);
        } else {
            chipBg.setColor(p.nativeMutedPillBg);
        }
        chipBox.setBackground(chipBg);

        TextView chipTv = new TextView(context);
        chipTv.setText(defaultChipLabel);
        chipTv.setTextColor(jumpable ? p.nativeChipText : p.textMuted);
        chipTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        chipTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chipTv.setGravity(isRtl ? Gravity.RIGHT : Gravity.LEFT);
        chipTv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);

        ImageView chipIcon = null;
        if (jumpable) {
            chipIcon = new ImageView(context);
            chipIcon.setImageDrawable(GlassDesign.GlassIcons.openInClientIcon(p.nativeChipText, isRtl));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(12), dp(12));
            if (isRtl) {
                iconLp.leftMargin = dp(5);
                chipBox.addView(chipTv);
                chipBox.addView(chipIcon, iconLp);
            } else {
                iconLp.rightMargin = dp(5);
                chipBox.addView(chipIcon, iconLp);
                chipBox.addView(chipTv);
            }
        } else {
            chipBox.addView(chipTv);
        }

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.topMargin = dp(6f);
        chipLp.gravity = isRtl ? Gravity.RIGHT : Gravity.LEFT;
        textCol.addView(chipBox, chipLp);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (useDisabledSwitch) {
            GlassDesign.PillSwitchView sw = new GlassDesign.PillSwitchView(context, false, p);
            sw.setDisabled(true);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(48), dp(28));
            if (isRtl) {
                swLp.rightMargin = dp(14);
                row.addView(sw, swLp);
                row.addView(textCol, textLp);
            } else {
                textLp.rightMargin = dp(14);
                row.addView(textCol, textLp);
                row.addView(sw, swLp);
            }
        } else {
            TextView builtInPill = buildMutedBuiltInPill(p);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (isRtl) {
                pillLp.rightMargin = dp(12);
                row.addView(builtInPill, pillLp);
                row.addView(textCol, textLp);
            } else {
                textLp.rightMargin = dp(12);
                row.addView(textCol, textLp);
                row.addView(builtInPill, pillLp);
            }
        }

        final ImageView finalChipIcon = chipIcon;
        row.setOnClickListener(v -> onNativeItemTapped(
                item.getKey(), target, chipBox, chipTv, finalChipIcon, chipBg, defaultChipLabel));

        wrapper.addView(row);
        return wrapper;
    }

    private TextView buildMutedBuiltInPill(GlassDesign.Palette p) {
        TextView pillTv = new TextView(context);
        pillTv.setText(Translator.get(Keys.NativeBuiltInBadge));
        pillTv.setTextColor(p.textMuted);
        pillTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        pillTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pillTv.setSingleLine(true);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(p.nativeMutedPillBg);
        pill.setCornerRadius(dp(10));
        pillTv.setBackground(pill);
        pillTv.setPadding(dp(10), dp(4), dp(10), dp(4));
        return pillTv;
    }

    private TextView buildFloatingToastView() {
        TextView tv = new TextView(context);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tv.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(236, 20, 27, 38));
        bg.setCornerRadius(dp(20));
        bg.setStroke(Math.max(1, dp(0.75f)), Color.argb(44, 255, 255, 255));
        tv.setBackground(bg);
        tv.setElevation(dp(6));
        tv.setAlpha(0f);
        tv.setVisibility(View.GONE);
        return tv;
    }

    private void onNativeItemTapped(
            String itemKey,
            NativeFeatureTarget target,
            View chipContainer,
            TextView chipTv,
            ImageView chipIcon,
            GradientDrawable chipBg,
            String defaultLabel) {
        String clientName = getClientDisplayName();
        if (target == null || !target.isJumpable()) {
            showFloatingToast(Translator.get(Keys.NativeBuiltInHintNoJump, clientName), 1800L);
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (itemKey != null && itemKey.equals(armedNativeKey)
                && (now - armedNativeTimestampMs) <= NATIVE_DOUBLE_TAP_WINDOW_MS) {
            disarmActiveNativeRow();
            hideFloatingToastImmediate();
            Activity act = Utils.getCurrentActivity();
            if (controller != null) {
                controller.hide();
            }
            NativeFeatureNavigator.navigate(act, target, clientName);
            return;
        }

        disarmActiveNativeRow();
        armedNativeKey = itemKey;
        armedNativeTimestampMs = now;
        armedChipTextView = chipTv;
        armedChipIconView = chipIcon;
        armedChipBg = chipBg;
        armedDefaultLabel = defaultLabel;

        String tapAgainMsg = Translator.get(Keys.NativeTapAgainHint, clientName);
        if (currentPalette != null) {
            if (chipTv != null) {
                chipTv.setText(tapAgainMsg);
                chipTv.setTextColor(currentPalette.accentBlue);
            }
            if (chipIcon != null) {
                chipIcon.setColorFilter(currentPalette.accentBlue);
            }
            if (chipBg != null) {
                chipBg.setColor(currentPalette.nativeChipArmedBg);
            }
        }
        if (chipContainer != null) {
            chipContainer.animate().cancel();
            chipContainer.animate().scaleX(1.03f).scaleY(1.03f).setDuration(90)
                    .withEndAction(() -> chipContainer.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                    .start();
        }
        showFloatingToast(tapAgainMsg, NATIVE_DOUBLE_TAP_WINDOW_MS - 100L);

        disarmNativeRunnable = this::disarmActiveNativeRow;
        uiHandler.postDelayed(disarmNativeRunnable, NATIVE_DOUBLE_TAP_WINDOW_MS);
    }

    private void disarmActiveNativeRow() {
        if (disarmNativeRunnable != null) {
            uiHandler.removeCallbacks(disarmNativeRunnable);
            disarmNativeRunnable = null;
        }
        if (currentPalette != null) {
            if (armedChipTextView != null) {
                if (armedDefaultLabel != null) {
                    armedChipTextView.setText(armedDefaultLabel);
                }
                armedChipTextView.setTextColor(currentPalette.nativeChipText);
            }
            if (armedChipIconView != null) {
                armedChipIconView.clearColorFilter();
            }
            if (armedChipBg != null) {
                armedChipBg.setColor(currentPalette.nativeChipBg);
            }
        }
        armedNativeKey = null;
        armedNativeTimestampMs = 0L;
        armedChipTextView = null;
        armedChipIconView = null;
        armedChipBg = null;
        armedDefaultLabel = null;
    }

    private void showFloatingToast(String text, long durationMs) {
        if (floatingToastView == null || TextUtils.isEmpty(text)) return;
        if (hideToastRunnable != null) {
            uiHandler.removeCallbacks(hideToastRunnable);
            hideToastRunnable = null;
        }
        floatingToastView.setText(text);
        floatingToastView.setVisibility(View.VISIBLE);
        floatingToastView.bringToFront();
        floatingToastView.animate().cancel();
        floatingToastView.animate().alpha(1f).setDuration(150).start();
        hideToastRunnable = () -> {
            if (floatingToastView != null) {
                floatingToastView.animate().alpha(0f).setDuration(220)
                        .withEndAction(() -> {
                            if (floatingToastView != null) {
                                floatingToastView.setVisibility(View.GONE);
                            }
                        }).start();
            }
        };
        uiHandler.postDelayed(hideToastRunnable, durationMs);
    }

    private void hideFloatingToastImmediate() {
        if (hideToastRunnable != null) {
            uiHandler.removeCallbacks(hideToastRunnable);
            hideToastRunnable = null;
        }
        if (floatingToastView != null) {
            floatingToastView.animate().cancel();
            floatingToastView.setAlpha(0f);
            floatingToastView.setVisibility(View.GONE);
        }
    }

    private void clearTransientFeedback() {
        disarmActiveNativeRow();
        hideFloatingToastImmediate();
    }

    private String getClientDisplayName() {
        if (cachedClientName == null || cachedClientName.isEmpty()) {
            cachedClientName = ClientManager.getCurrentDisplayName(context);
        }
        return cachedClientName;
    }

    private String buildSubtitleText(ConfigItem item) {
        return buildSubtitleText(item, false);
    }

    private String buildSubtitleText(ConfigItem item, boolean omitRestartNote) {
        String desc = Translator.getOrDefault(item.getKey() + "Desc", null);
        if (desc == null || desc.isEmpty()) {
            desc = item.getValue();
        }
        if (!omitRestartNote && item.isRestartRequired()) {
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
        lp.leftMargin = isRtl ? dp(16) : startMarginPx;
        lp.rightMargin = isRtl ? startMarginPx : dp(16);
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

    private String getProxyDisplayLabel(int mode) {
        if (mode == 1) return Translator.get(Keys.ProxyDisplayMenu);
        if (mode == 2) return Translator.get(Keys.ProxyDisplayTopBar);
        return Translator.get(Keys.ProxyDisplayOff);
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
            if (tabBar != null) {
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
                if (tabBar != null) {
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
                if (tabBar != null) {
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
            if (tabBar != null) {
                tabBar.setDragPosition(pos);
            }
        }

        private boolean isBackEdgeGesture(float dx) {
            return downX <= edgeBackWidth && dx > 0;
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
                    if (isBackEdgeGesture(dx)) {
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
                    if (isBackEdgeGesture(dx)) {
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
