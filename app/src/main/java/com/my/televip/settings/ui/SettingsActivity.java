package com.my.televip.settings.ui;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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
import com.my.televip.Drawable.ArrowDrawable;
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
import java.util.List;
import java.util.Locale;

public class SettingsActivity {

    public static boolean isSettings;

    private final Context context;
    public RecyclerListView listView;

    private TextView heroStatusPill;
    private TextView heroCountText;

    public SettingsActivity(Context context) {
        this.context = context;
    }

    public View createView(SettingsController settingsController) {
        final boolean light = Theme.isLight();
        final int bgCanvas = light ? Color.rgb(242, 244, 248) : Color.rgb(15, 22, 30);
        final int cardBg = light ? Color.WHITE : Color.rgb(24, 34, 46);
        final int cardBorder = light ? 0x0F000000 : 0x16FFFFFF;
        final int topBarBg = light ? Color.WHITE : Color.rgb(20, 29, 40);
        final int textPrimary = light ? Color.rgb(20, 26, 34) : Color.rgb(238, 243, 248);
        final int textSecondary = light ? Color.rgb(108, 118, 130) : Color.rgb(138, 152, 168);
        final int accent = light ? Color.rgb(36, 132, 232) : Color.rgb(82, 168, 255);
        final int accentSoft = light ? 0x182484E8 : 0x2652A8FF;
        final int dividerColor = light ? 0x10000000 : 0x14FFFFFF;
        final int rippleColor = light ? 0x14000000 : 0x1EFFFFFF;
        final int warnBg = light ? 0x1AF59E0B : 0x26F59E0B;
        final int warnFg = light ? Color.rgb(180, 105, 0) : Color.rgb(251, 191, 36);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgCanvas);

        try {
            // Top Bar (with status bar inset)
            int statusH = 0;
            int sId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (sId > 0) statusH = context.getResources().getDimensionPixelSize(sId);

            LinearLayout topBar = new LinearLayout(context);
            topBar.setOrientation(LinearLayout.HORIZONTAL);
            topBar.setGravity(Gravity.CENTER_VERTICAL);
            topBar.setBackgroundColor(topBarBg);
            topBar.setPadding(dp(8), statusH + dp(6), dp(16), dp(10));

            ImageView backBtn = new ImageView(context);
            backBtn.setImageDrawable(new ArrowDrawable());
            backBtn.setScaleType(ImageView.ScaleType.CENTER);
            backBtn.setBackground(createCircleRipple(rippleColor));
            backBtn.setOnClickListener(v -> settingsController.hide());
            topBar.addView(backBtn, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout titleCol = new LinearLayout(context);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            titleCol.setPadding(dp(8), 0, 0, 0);

            TextView titleTv = new TextView(context);
            titleTv.setText("TeleVip Next");
            titleTv.setTextColor(textPrimary);
            titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            titleTv.setTypeface(Typeface.DEFAULT_BOLD);
            titleCol.addView(titleTv);

            TextView subTv = new TextView(context);
            subTv.setText(Translator.get(Keys.ByMustafa));
            subTv.setTextColor(textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            titleCol.addView(subTv);

            topBar.addView(titleCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(topBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            View topDiv = new View(context);
            topDiv.setBackgroundColor(dividerColor);
            root.addView(topDiv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f))));

            // Scrollable Content
            ScrollView scrollView = new ScrollView(context);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            scrollView.setClipToPadding(false);

            int navH = 0;
            int nId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (nId > 0) navH = context.getResources().getDimensionPixelSize(nId);
            scrollView.setPadding(0, dp(12), 0, navH + dp(28));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);

            // 1. Hero Status Card
            container.addView(buildHeroCard(light), marginLp(dp(16), dp(2), dp(16), dp(10)));

            // 2. Search Bar
            final List<SearchableRow> searchableRows = new ArrayList<>();
            final List<LinearLayout> sectionCards = new ArrayList<>();
            container.addView(buildSearchBar(cardBg, cardBorder, textPrimary, textSecondary, accent, searchableRows, sectionCards),
                    marginLp(dp(16), 0, dp(16), dp(10)));

            // 3. Settings Sections
            LinearLayout currentCard = null;
            List<View> currentCardRows = null;
            List<ConfigItem> items = ConfigManager.getItems();

            for (ConfigItem item : items) {
                if (item == null) continue;
                int type = item.getType();

                if (type == ConfigItem.DIVIDER) {
                    currentCard = null;
                    currentCardRows = null;
                    continue;
                }

                if (type == ConfigItem.INFO) {
                    currentCard = null;
                    currentCardRows = null;
                    TextView infoTv = new TextView(context);
                    infoTv.setText(Translator.get(item.getKey()));
                    infoTv.setTextColor(textSecondary);
                    infoTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                    infoTv.setLineSpacing(dp(2.5f), 1.0f);
                    container.addView(infoTv, marginLp(dp(22), dp(4), dp(22), dp(10)));
                    continue;
                }

                if (currentCard == null) {
                    currentCard = createCard(cardBg, cardBorder);
                    currentCardRows = new ArrayList<>();
                    sectionCards.add(currentCard);
                    container.addView(currentCard, marginLp(dp(16), dp(5), dp(16), dp(5)));
                }

                if (type == ConfigItem.HEADER) {
                    LinearLayout headerRow = new LinearLayout(context);
                    headerRow.setOrientation(LinearLayout.HORIZONTAL);
                    headerRow.setGravity(Gravity.CENTER_VERTICAL);
                    headerRow.setPadding(dp(18), dp(14), dp(18), dp(6));

                    View dot = new View(context);
                    GradientDrawable dotBg = new GradientDrawable();
                    dotBg.setShape(GradientDrawable.OVAL);
                    dotBg.setColor(accent);
                    dot.setBackground(dotBg);
                    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(7), dp(7));
                    dotLp.setMarginEnd(dp(8));
                    headerRow.addView(dot, dotLp);

                    TextView header = new TextView(context);
                    header.setText(Translator.get(item.getKey()).toUpperCase(Locale.ROOT));
                    header.setTextColor(accent);
                    header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    header.setLetterSpacing(0.06f);
                    header.setTypeface(Typeface.DEFAULT_BOLD);
                    headerRow.addView(header);

                    currentCard.addView(headerRow);
                } else if (type == ConfigItem.SWITCH) {
                    if (!currentCardRows.isEmpty()) addDivider(currentCard, dividerColor);
                    String badge = item.getValue() != null ? item.getValue()
                            : (item.isRestartRequired() ? Translator.get(Keys.RestartRequired) : null);
                    boolean isWarn = item.isRestartRequired() && item.getValue() == null;
                    String title = Translator.get(item.getKey());
                    View row = createSwitchRow(
                            title, badge, isWarn, item.isEnable(),
                            textPrimary, accent, rippleColor, light,
                            accentSoft, accent, warnBg, warnFg,
                            checked -> {
                                item.setEnable(checked);
                                item.run();
                                refreshHeroStats();
                            }
                    );
                    currentCard.addView(row);
                    currentCardRows.add(row);
                    searchableRows.add(new SearchableRow(title, row, currentCard));
                } else if (type == ConfigItem.EXPANDABLE_SWITCH) {
                    if (!currentCardRows.isEmpty()) addDivider(currentCard, dividerColor);
                    View group = addExpandableGroup(currentCard, item, textPrimary, textSecondary,
                            accent, dividerColor, rippleColor, light);
                    currentCardRows.add(group);
                    searchableRows.add(new SearchableRow(Translator.get(item.getKey()), group, currentCard));
                } else if (type == ConfigItem.TEXT) {
                    if (!currentCardRows.isEmpty()) addDivider(currentCard, dividerColor);
                    String title = Translator.get(item.getKey());
                    View row = createTextRow(item, textPrimary, textSecondary, accent, accentSoft, rippleColor);
                    currentCard.addView(row);
                    currentCardRows.add(row);
                    searchableRows.add(new SearchableRow(title, row, currentCard));
                }
            }

            scrollView.addView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

            refreshHeroStats();
        } catch (Throwable e) {
            Logger.e(e);
        }

        return root;
    }

    private View buildHeroCard(boolean light) {
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));

        GradientDrawable grad = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                light
                        ? new int[]{Color.rgb(36, 132, 232), Color.rgb(88, 86, 214)}
                        : new int[]{Color.rgb(28, 56, 94), Color.rgb(45, 42, 92)}
        );
        grad.setCornerRadius(dp(22));
        hero.setBackground(grad);

        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconTv = new TextView(context);
        iconTv.setText("👻");
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        iconTv.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(0x28FFFFFF);
        iconBg.setCornerRadius(dp(14));
        iconTv.setBackground(iconBg);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconLp.setMarginEnd(dp(14));
        topRow.addView(iconTv, iconLp);

        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView heroTitle = new TextView(context);
        heroTitle.setText("Ghost Mode & Tweaks");
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17.5f);
        heroTitle.setTypeface(Typeface.DEFAULT_BOLD);
        infoCol.addView(heroTitle);

        heroCountText = new TextView(context);
        heroCountText.setTextColor(0xCCFFFFFF);
        heroCountText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heroCountText.setPadding(0, dp(2), 0, 0);
        infoCol.addView(heroCountText);

        topRow.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(topRow);

        heroStatusPill = new TextView(context);
        heroStatusPill.setTextColor(Color.WHITE);
        heroStatusPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        heroStatusPill.setTypeface(Typeface.DEFAULT_BOLD);
        heroStatusPill.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(0x2AFFFFFF);
        pillBg.setCornerRadius(dp(20));
        heroStatusPill.setBackground(pillBg);
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillLp.topMargin = dp(14);
        hero.addView(heroStatusPill, pillLp);

        return hero;
    }

    private View buildSearchBar(int cardBg, int cardBorder, int textPrimary, int textSecondary, int accent,
                                List<SearchableRow> rows, List<LinearLayout> cards) {
        LinearLayout box = createCard(cardBg, cardBorder);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16), dp(4), dp(12), dp(4));

        TextView searchIcon = new TextView(context);
        searchIcon.setText("🔍");
        searchIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.setMarginEnd(dp(10));
        box.addView(searchIcon, iconLp);

        EditText input = new EditText(context);
        input.setHint("Search settings...");
        input.setHintTextColor(textSecondary);
        input.setTextColor(textPrimary);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        input.setBackground(null);
        input.setSingleLine(true);
        input.setPadding(0, dp(10), 0, dp(10));
        box.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearBtn = new TextView(context);
        clearBtn.setText("✕");
        clearBtn.setTextColor(accent);
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        clearBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> input.setText(""));
        box.addView(clearBtn);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                clearBtn.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                for (SearchableRow sr : rows) {
                    boolean match = q.isEmpty() || sr.titleLower.contains(q);
                    sr.rowView.setVisibility(match ? View.VISIBLE : View.GONE);
                }
                for (LinearLayout card : cards) {
                    boolean anyVisible = false;
                    for (int i = 0; i < card.getChildCount(); i++) {
                        View c = card.getChildAt(i);
                        if (c.getTag() != null && "item".equals(c.getTag()) && c.getVisibility() == View.VISIBLE) {
                            anyVisible = true;
                            break;
                        }
                    }
                    card.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
                }
            }
        });

        return box;
    }

    private void refreshHeroStats() {
        int active = 0;
        int total = 0;
        for (ConfigItem item : ConfigManager.getItems()) {
            if (item == null) continue;
            if (item.getType() == ConfigItem.SWITCH) {
                total++;
                if (item.isEnable()) active++;
            } else if (item.getType() == ConfigItem.EXPANDABLE_SWITCH && item.getChildren() != null) {
                for (ConfigItem c : item.getChildren()) {
                    total++;
                    if (c.isEnable()) active++;
                }
            }
        }
        if (heroCountText != null) {
            heroCountText.setText(active + " / " + total + " features enabled");
        }
        if (heroStatusPill != null) {
            boolean ghostOn = ConfigManager.isGhostMode();
            heroStatusPill.setText(ghostOn ? "● Ghost Mode Active" : "○ Ghost Mode Off");
        }
    }

    private LinearLayout createCard(int cardBg, int borderColor) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(dp(18));
        bg.setStroke(Math.max(1, dp(0.7f)), borderColor);
        card.setBackground(bg);
        card.setClipToOutline(true);
        return card;
    }

    private void addDivider(LinearLayout parent, int color) {
        View div = new View(context);
        div.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.6f)));
        lp.setMargins(dp(18), 0, dp(18), 0);
        parent.addView(div, lp);
    }

    private interface OnCheckedCallback {
        void onChanged(boolean checked);
    }

    private View createSwitchRow(String title, String badgeText, boolean isWarnBadge, boolean initialChecked,
                                 int textPrimary, int accent, int rippleColor, boolean light,
                                 int infoBg, int infoFg, int warnBg, int warnFg,
                                 OnCheckedCallback callback) {
        LinearLayout row = new LinearLayout(context);
        row.setTag("item");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(13), dp(16), dp(13));
        row.setMinimumHeight(dp(54));
        row.setBackground(createRipple(rippleColor));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextColor(textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        textCol.addView(titleTv);

        if (badgeText != null && !badgeText.isEmpty()) {
            TextView badgeTv = new TextView(context);
            badgeTv.setText(badgeText);
            badgeTv.setTextColor(isWarnBadge ? warnFg : infoFg);
            badgeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            badgeTv.setTypeface(Typeface.DEFAULT_BOLD);
            badgeTv.setPadding(dp(8), dp(2), dp(8), dp(2));
            GradientDrawable bBg = new GradientDrawable();
            bBg.setColor(isWarnBadge ? warnBg : infoBg);
            bBg.setCornerRadius(dp(8));
            badgeTv.setBackground(bBg);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bLp.topMargin = dp(4);
            textCol.addView(badgeTv, bLp);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(14));
        row.addView(textCol, textLp);

        PillSwitchView sw = new PillSwitchView(context, initialChecked, accent, light);
        row.addView(sw, new LinearLayout.LayoutParams(dp(50), dp(28)));

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            if (callback != null) callback.onChanged(next);
        });

        return row;
    }

    private View addExpandableGroup(LinearLayout card, ConfigItem parentItem,
                                    int textPrimary, int textSecondary, int accent,
                                    int dividerColor, int rippleColor, boolean light) {
        List<ConfigItem> children = parentItem.getChildren();
        boolean anyChecked = false;
        if (children != null && !children.isEmpty()) {
            for (ConfigItem c : children) {
                if (c.isEnable()) {
                    anyChecked = true;
                    break;
                }
            }
        } else {
            anyChecked = parentItem.isEnable();
        }
        parentItem.setEnable(anyChecked);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setTag("item");
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout childrenContainer = new LinearLayout(context);
        childrenContainer.setOrientation(LinearLayout.VERTICAL);
        childrenContainer.setVisibility(View.GONE);

        LinearLayout parentRow = new LinearLayout(context);
        parentRow.setOrientation(LinearLayout.HORIZONTAL);
        parentRow.setGravity(Gravity.CENTER_VERTICAL);
        parentRow.setPadding(dp(18), dp(13), dp(16), dp(13));
        parentRow.setMinimumHeight(dp(54));
        parentRow.setBackground(createRipple(rippleColor));

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(parentItem.getKey()) + "  ▾");
        titleTv.setTextColor(textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(14));
        parentRow.addView(titleTv, textLp);

        PillSwitchView parentSw = new PillSwitchView(context, anyChecked, accent, light);
        parentRow.addView(parentSw, new LinearLayout.LayoutParams(dp(50), dp(28)));

        wrapper.addView(parentRow);
        wrapper.addView(childrenContainer);

        final PillSwitchView[] childSwitches = children != null ? new PillSwitchView[children.size()] : new PillSwitchView[0];

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                final ConfigItem child = children.get(i);
                addDivider(childrenContainer, dividerColor);
                View childRow = createSwitchRow(
                        "   " + Translator.get(child.getKey()),
                        null, false, child.isEnable(),
                        textPrimary, accent, rippleColor, light,
                        0, 0, 0, 0,
                        checked -> {
                            child.setEnable(checked);
                            child.run();
                            boolean any = false;
                            for (ConfigItem c : children) {
                                if (c.isEnable()) {
                                    any = true;
                                    break;
                                }
                            }
                            parentItem.setEnable(any);
                            parentSw.setChecked(any, true);
                            refreshHeroStats();
                        }
                );
                if (childRow instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) childRow;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        if (vg.getChildAt(j) instanceof PillSwitchView) {
                            childSwitches[i] = (PillSwitchView) vg.getChildAt(j);
                            break;
                        }
                    }
                }
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
            }
            refreshHeroStats();
        });

        parentRow.setOnClickListener(v -> {
            boolean show = childrenContainer.getVisibility() != View.VISIBLE;
            childrenContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            titleTv.setText(Translator.get(parentItem.getKey()) + (show ? "  ▴" : "  ▾"));
        });

        card.addView(wrapper);
        return wrapper;
    }

    private View createTextRow(ConfigItem item, int textPrimary, int textSecondary, int accent, int accentSoft, int rippleColor) {
        LinearLayout row = new LinearLayout(context);
        row.setTag("item");
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setMinimumHeight(dp(50));
        row.setBackground(createRipple(rippleColor));

        TextView titleTv = new TextView(context);
        String prefix = Keys.DeveloperChannel.equals(item.getKey()) ? "📢  "
                : (Keys.RestartApp.equals(item.getKey()) ? "🔄  " : "📅  ");
        titleTv.setText(prefix + Translator.get(item.getKey()));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        boolean isAction = !Keys.Calendar.equals(item.getKey());
        titleTv.setTextColor(isAction ? accent : textPrimary);
        if (isAction) titleTv.setTypeface(Typeface.DEFAULT_BOLD);

        row.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (Keys.Calendar.equals(item.getKey())) {
            TextView valTv = new TextView(context);
            valTv.setTextColor(accent);
            valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            valTv.setTypeface(Typeface.DEFAULT_BOLD);
            valTv.setText(getCalendarLabel(item.getCustomCalendar()));
            valTv.setPadding(dp(10), dp(4), dp(10), dp(4));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(accentSoft);
            pill.setCornerRadius(dp(10));
            valTv.setBackground(pill);
            row.addView(valTv);

            row.setOnClickListener(v -> {
                String[] options = new String[]{
                        Translator.get(Keys.Gregorian),
                        Translator.get(Keys.Hijri),
                        Translator.get(Keys.Persian)
                };
                try {
                    int themeRes = Theme.isLight()
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
            });
        } else if (Keys.DeveloperChannel.equals(item.getKey())) {
            row.setOnClickListener(v -> {
                try {
                    Browser.openUrl(context, "https://t.me/televipnext");
                } catch (Throwable t) {
                    Logger.e(t);
                }
            });
        } else if (Keys.RestartApp.equals(item.getKey())) {
            row.setOnClickListener(v -> {
                Activity act = Utils.getCurrentActivity();
                if (act != null) {
                    Intent intent = act.getPackageManager().getLaunchIntentForPackage(act.getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        act.startActivity(intent);
                        row.postDelayed(() -> Runtime.getRuntime().exit(0), 150);
                    }
                }
            });
        }

        return row;
    }

    private String getCalendarLabel(int type) {
        if (type == 1) return Translator.get(Keys.Hijri);
        if (type == 2) return Translator.get(Keys.Persian);
        return Translator.get(Keys.Gregorian);
    }

    private RippleDrawable createRipple(int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    private RippleDrawable createCircleRipple(int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    private LinearLayout.LayoutParams marginLp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    private static final class SearchableRow {
        final String titleLower;
        final View rowView;

        SearchableRow(String title, View rowView, LinearLayout card) {
            this.titleLower = title != null ? title.toLowerCase(Locale.ROOT) : "";
            this.rowView = rowView;
        }
    }

    private static final class PillSwitchView extends View {
        private boolean checked;
        private float progress;
        private final int onColor;
        private final int offColor;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF trackRect = new RectF();
        private final ArgbEvaluator colorEval = new ArgbEvaluator();
        private ValueAnimator anim;

        PillSwitchView(Context ctx, boolean initial, int accent, boolean light) {
            super(ctx);
            this.checked = initial;
            this.progress = initial ? 1f : 0f;
            this.onColor = accent;
            this.offColor = light ? Color.rgb(205, 211, 220) : Color.rgb(66, 79, 94);
            this.thumbPaint.setColor(Color.WHITE);
        }

        boolean isChecked() {
            return checked;
        }

        void setChecked(boolean value, boolean animate) {
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
            anim.setInterpolator(new DecelerateInterpolator());
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
            int color = (int) colorEval.evaluate(progress, offColor, onColor);
            trackPaint.setColor(color);
            float r = h / 2f;
            canvas.drawRoundRect(trackRect, r, r, trackPaint);

            float pad = h * 0.12f;
            float thumbR = (h - pad * 2f) / 2f;
            float startX = pad + thumbR;
            float endX = w - pad - thumbR;
            float cx = startX + (endX - startX) * progress;
            canvas.drawCircle(cx, h / 2f, thumbR, thumbPaint);
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
