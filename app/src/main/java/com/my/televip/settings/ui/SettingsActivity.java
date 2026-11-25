package com.my.televip.settings.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.my.televip.Audio;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.Drawable.ArrowDrawable;
import com.my.televip.base.BaseMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.Obfuscate;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.ui.ThemeColors;
import com.my.televip.ui.toolBar.MainToolBar;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.Theme;
import com.my.televip.virtuals.messenger.browser.Browser;
import com.my.televip.virtuals.ui.Components.RecyclerListView;

import java.util.List;

public class SettingsActivity {

    public static boolean isSettings;

    private final Context context;
    public RecyclerListView listView;

    public SettingsActivity(Context context) {
        this.context = context;
    }

    public View createView(SettingsController settingsController) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        final boolean light = Theme.isLight();
        final int bgGray = ThemeColors.getBackgroundGrayColor();
        final int cardBg = ThemeColors.getBackgroundWhiteOrBlueColor();
        final int textPrimary = ThemeColors.getTextColor();
        final int textSecondary = ThemeColors.getTextGrayColor();
        final int accentBlue = light ? Color.rgb(42, 134, 219) : Color.rgb(102, 178, 255);
        final int dividerColor = light ? 0x14000000 : 0x18FFFFFF;
        final int rippleColor = light ? 0x16000000 : 0x20FFFFFF;

        root.setBackgroundColor(bgGray);

        try {
            // MainToolBar already includes statusBar height & top padding
            MainToolBar toolbar = new MainToolBar(context);
            toolbar.setColorTitle(ThemeColors.getTextToolBarColor());
            toolbar.setRippleColor(ThemeColors.getToolBarRippleColor());
            toolbar.setTextTitle(Translator.get(Keys.GhostMode));
            toolbar.setImageDrawable(new ArrowDrawable());
            toolbar.getImage().setOnClickListener(v -> settingsController.hide());
            root.addView(toolbar);

            View topDivider = new View(context);
            topDivider.setBackgroundColor(dividerColor);
            root.addView(topDivider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f))));

            // Scrollable cards container
            ScrollView scrollView = new ScrollView(context);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            scrollView.setClipToPadding(false);

            int navH = 0;
            int nId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (nId > 0) navH = context.getResources().getDimensionPixelSize(nId);
            scrollView.setPadding(0, dp(10), 0, navH + dp(24));

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);

            LinearLayout currentCard = null;
            List<ConfigItem> items = ConfigManager.getItems();

            for (ConfigItem item : items) {
                if (item == null) continue;
                int type = item.getType();

                if (type == ConfigItem.DIVIDER) {
                    currentCard = null;
                    continue;
                }

                if (type == ConfigItem.INFO) {
                    currentCard = null;
                    TextView infoTv = new TextView(context);
                    infoTv.setText(Translator.get(item.getKey()));
                    infoTv.setTextColor(textSecondary);
                    infoTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    infoTv.setLineSpacing(dp(2), 1.0f);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(dp(24), dp(8), dp(24), dp(10));
                    container.addView(infoTv, lp);
                    continue;
                }

                if (currentCard == null) {
                    currentCard = createCard(cardBg);
                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    cardLp.setMargins(dp(14), dp(6), dp(14), dp(6));
                    container.addView(currentCard, cardLp);
                } else {
                    addDivider(currentCard, dividerColor);
                }

                if (type == ConfigItem.HEADER) {
                    TextView header = new TextView(context);
                    header.setText(Translator.get(item.getKey()));
                    header.setTextColor(accentBlue);
                    header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    header.setTypeface(Typeface.DEFAULT_BOLD);
                    header.setPadding(dp(18), dp(14), dp(18), dp(6));
                    currentCard.addView(header, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                } else if (type == ConfigItem.SWITCH) {
                    String sub = item.getValue() != null ? item.getValue()
                            : (item.isRestartRequired() ? Translator.get(Keys.RestartRequired) : null);
                    View row = createSwitchRow(
                            Translator.get(item.getKey()),
                            sub,
                            item.isEnable(),
                            textPrimary, textSecondary, accentBlue, rippleColor, light,
                            checked -> {
                                item.setEnable(checked);
                                ConfigPreferences.putBoolean(item.getKey(), checked);
                                item.run();
                            }
                    );
                    currentCard.addView(row);
                } else if (type == ConfigItem.EXPANDABLE_SWITCH) {
                    addExpandableGroup(currentCard, item, textPrimary, textSecondary, accentBlue, dividerColor, rippleColor, light);
                } else if (type == ConfigItem.TEXT) {
                    View row = createTextRow(item, textPrimary, textSecondary, accentBlue, rippleColor);
                    currentCard.addView(row);
                }
            }

            scrollView.addView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        } catch (Throwable e) {
            Logger.e(e);
        }

        return root;
    }

    private LinearLayout createCard(int cardBg) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(dp(16));
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

    private View createSwitchRow(String title, String subtitle, boolean initialChecked,
                                 int textPrimary, int textSecondary, int accent, int rippleColor, boolean light,
                                 OnCheckedCallback callback) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(14), dp(12));
        row.setMinimumHeight(dp(50));
        row.setBackground(createRipple(rippleColor));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText(title);
        titleTv.setTextColor(textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        textCol.addView(titleTv);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subTv = new TextView(context);
            subTv.setText(subtitle);
            subTv.setTextColor(textSecondary);
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            subTv.setPadding(0, dp(2), 0, 0);
            textCol.addView(subTv);
        }

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(12));
        row.addView(textCol, textLp);

        Switch sw = new Switch(context);
        sw.setChecked(initialChecked);
        sw.setClickable(false);
        sw.setFocusable(false);
        styleSwitch(sw, accent, light);
        row.addView(sw);

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            if (callback != null) callback.onChanged(next);
        });

        return row;
    }

    private void addExpandableGroup(LinearLayout card, ConfigItem parentItem,
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

        LinearLayout childrenContainer = new LinearLayout(context);
        childrenContainer.setOrientation(LinearLayout.VERTICAL);
        childrenContainer.setVisibility(View.GONE);

        LinearLayout parentRow = new LinearLayout(context);
        parentRow.setOrientation(LinearLayout.HORIZONTAL);
        parentRow.setGravity(Gravity.CENTER_VERTICAL);
        parentRow.setPadding(dp(18), dp(12), dp(14), dp(12));
        parentRow.setMinimumHeight(dp(50));
        parentRow.setBackground(createRipple(rippleColor));

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(parentItem.getKey()) + "  ▾");
        titleTv.setTextColor(textPrimary);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(12));
        parentRow.addView(titleTv, textLp);

        Switch parentSw = new Switch(context);
        parentSw.setChecked(anyChecked);
        styleSwitch(parentSw, accent, light);
        parentRow.addView(parentSw);

        card.addView(parentRow);
        card.addView(childrenContainer);

        final Switch[] childSwitches = children != null ? new Switch[children.size()] : new Switch[0];
        final boolean[] updating = new boolean[]{false};

        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                final ConfigItem child = children.get(i);
                addDivider(childrenContainer, dividerColor);
                View childRow = createSwitchRow(
                        "   " + Translator.get(child.getKey()),
                        null,
                        child.isEnable(),
                        textPrimary, textSecondary, accent, rippleColor, light,
                        checked -> {
                            child.setEnable(checked);
                            ConfigPreferences.putBoolean(child.getKey(), checked);
                            child.run();
                            boolean any = false;
                            for (ConfigItem c : children) {
                                if (c.isEnable()) {
                                    any = true;
                                    break;
                                }
                            }
                            parentItem.setEnable(any);
                            updating[0] = true;
                            parentSw.setChecked(any);
                            updating[0] = false;
                        }
                );
                if (childRow instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) childRow;
                    for (int j = 0; j < vg.getChildCount(); j++) {
                        if (vg.getChildAt(j) instanceof Switch) {
                            childSwitches[i] = (Switch) vg.getChildAt(j);
                            break;
                        }
                    }
                }
                childrenContainer.addView(childRow);
            }
        }

        parentSw.setOnCheckedChangeListener((btn, next) -> {
            if (updating[0]) return;
            parentItem.setEnable(next);
            if (children != null) {
                for (int i = 0; i < children.size(); i++) {
                    ConfigItem c = children.get(i);
                    c.setEnable(next);
                    ConfigPreferences.putBoolean(c.getKey(), next);
                    c.run();
                    if (childSwitches[i] != null) childSwitches[i].setChecked(next);
                }
            }
        });

        parentRow.setOnClickListener(v -> {
            boolean show = childrenContainer.getVisibility() != View.VISIBLE;
            childrenContainer.setVisibility(show ? View.VISIBLE : View.GONE);
            titleTv.setText(Translator.get(parentItem.getKey()) + (show ? "  ▴" : "  ▾"));
        });
    }

    private View createTextRow(ConfigItem item, int textPrimary, int textSecondary, int accent, int rippleColor) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));
        row.setMinimumHeight(dp(48));
        row.setBackground(createRipple(rippleColor));

        TextView titleTv = new TextView(context);
        titleTv.setText(Translator.get(item.getKey()));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        boolean isAction = !Keys.Calendar.equals(item.getKey());
        titleTv.setTextColor(isAction ? accent : textPrimary);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(titleTv, lp);

        if (Keys.Calendar.equals(item.getKey())) {
            TextView valTv = new TextView(context);
            valTv.setTextColor(accent);
            valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            valTv.setText(getCalendarLabel(item.getCustomCalendar()));
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

    private void styleSwitch(Switch sw, int accent, boolean light) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] thumbColors = new int[]{
                Color.WHITE,
                light ? Color.rgb(245, 245, 245) : Color.rgb(190, 200, 210)
        };
        int[] trackColors = new int[]{
                accent,
                light ? Color.rgb(200, 204, 210) : Color.rgb(75, 88, 102)
        };
        sw.setThumbTintList(new ColorStateList(states, thumbColors));
        sw.setTrackTintList(new ColorStateList(states, trackColors));
    }

    private RippleDrawable createRipple(int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null,
                new GradientDrawable() {{ setColor(Color.WHITE); }});
    }

    private int dp(float v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
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
