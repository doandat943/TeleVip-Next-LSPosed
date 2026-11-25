package com.my.televip.settings.controller;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.my.televip.Configs.ConfigPreferences;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.settings.ui.SettingsActivity;
import com.my.televip.logging.Logger;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.AlertDialog;
import com.my.televip.virtuals.messenger.browser.Browser;
import com.my.televip.virtuals.ui.LaunchActivity;

public class SettingsController {

    public FrameLayout settingsView;

    public SettingsActivity settingsActivity;
    private Object backCallback;
    private java.lang.ref.WeakReference<Activity> backCallbackActivity;
    private int openGeneration = 0;

    public SettingsController() {}

    public void openView(){
        try {
            Activity act = Utils.getCurrentActivity();
            if (act == null) return;
            if (settingsView != null && settingsView.getContext() != act) {
                if (settingsView.getParent() instanceof android.view.ViewGroup) {
                    ((android.view.ViewGroup) settingsView.getParent()).removeView(settingsView);
                }
                settingsView = null;
                SettingsActivity.isSettings = false;
            }
            if (SettingsActivity.isSettings && settingsView != null && settingsView.getParent() != null) {
                return;
            }
            if (settingsView == null) {
                final float density = act.getResources().getDisplayMetrics().density;
                final float edgeSlop = 32 * density;
                final float touchSlop = 12 * density;
                settingsView = new FrameLayout(act) {
                    private float downX, downY;
                    private boolean trackingSwipe;
                    private boolean isDragging;

                    @Override
                    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
                        if (!SettingsActivity.isSettings) {
                            trackingSwipe = false;
                            isDragging = false;
                            return false;
                        }
                        int action = ev.getActionMasked();
                        if (action == android.view.MotionEvent.ACTION_DOWN) {
                            downX = ev.getX();
                            downY = ev.getY();
                            trackingSwipe = downX <= edgeSlop;
                            isDragging = false;
                        } else if (action == android.view.MotionEvent.ACTION_MOVE && trackingSwipe) {
                            float dx = ev.getX() - downX;
                            float dy = Math.abs(ev.getY() - downY);
                            if (dx > touchSlop && dx > dy * 1.5f) {
                                isDragging = true;
                                return true;
                            }
                        } else if (action == android.view.MotionEvent.ACTION_CANCEL || action == android.view.MotionEvent.ACTION_UP) {
                            trackingSwipe = false;
                            isDragging = false;
                        }
                        return super.onInterceptTouchEvent(ev);
                    }

                    @Override
                    public boolean onTouchEvent(android.view.MotionEvent ev) {
                        if (!SettingsActivity.isSettings) {
                            trackingSwipe = false;
                            isDragging = false;
                            return super.onTouchEvent(ev);
                        }
                        if (trackingSwipe) {
                            int action = ev.getActionMasked();
                            if (action == android.view.MotionEvent.ACTION_MOVE) {
                                float dx = ev.getX() - downX;
                                float dy = Math.abs(ev.getY() - downY);
                                if (!isDragging && dx > touchSlop && dx > dy * 1.5f) {
                                    isDragging = true;
                                }
                                if (isDragging) {
                                    setTranslationX(Math.max(0f, dx));
                                }
                                return true;
                            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                                boolean wasDragging = isDragging;
                                trackingSwipe = false;
                                isDragging = false;
                                float dx = ev.getX() - downX;
                                if (wasDragging && dx > getWidth() * 0.25f) {
                                    hide();
                                } else if (wasDragging) {
                                    animate().translationX(0f).setDuration(150).start();
                                }
                                return true;
                            }
                        }
                        return super.onTouchEvent(ev);
                    }
                };
                settingsView.setClickable(true);
                settingsView.setFocusable(true);
                settingsView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {}

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        SettingsActivity.isSettings = false;
                    }
                });
            }

            settingsView.removeAllViews();
            settingsActivity = new SettingsActivity(act);
            showJoinTeleVip();

            settingsView.addView(settingsActivity.createView(this), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            show(settingsView);
            Logger.w("SettingsController.openView() displayed TeleVip Settings");
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private void showJoinTeleVip() {
        try {
            if (!ConfigPreferences.getBoolean("JTV")) {
                AlertDialog alertDialog = new AlertDialog(getContext());

                alertDialog.setTitle(Translator.get(Keys.GhostMode));
                alertDialog.setMessage(Translator.get(Keys.JoinTeleVip));

                alertDialog.setPositiveButton(Translator.get(Keys.Join), AlertDialog.click(() -> {
                    Context ctx = getContext();
                    if (ctx != null) {
                        try {
                            Browser.openUrl(ctx, "https://t.me/televipnext");
                        } catch (Throwable t) {
                            Logger.e(t);
                        }
                    }
                    hide();
                }));

                alertDialog.setNegativeButton(Translator.get(Keys.Cancel), null);
                alertDialog.setNeutralButton(Translator.get(Keys.DontShowAgain), AlertDialog.click(() -> ConfigPreferences.putBoolean("JTV", true)));
                alertDialog.show();
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public void show(View target) {
        Activity act = Utils.getCurrentActivity();
        if (act == null) return;
        android.view.ViewGroup root = act.findViewById(android.R.id.content);
        if (root == null) return;

        openGeneration++;
        target.animate().withEndAction(null).cancel();

        if (target.getParent() != null) {
            ((android.view.ViewGroup) target.getParent()).removeView(target);
        }
        root.addView(target, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));
        target.setVisibility(View.VISIBLE);
        target.bringToFront();
        SettingsActivity.isSettings = true;

        int width = root.getWidth();
        if (width <= 0) width = act.getResources().getDisplayMetrics().widthPixels;
        target.setTranslationX(width);
        target.animate()
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                .start();

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (backCallback != null) {
                    Activity prevAct = backCallbackActivity != null ? backCallbackActivity.get() : null;
                    if (prevAct != null) {
                        prevAct.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                                (OnBackInvokedCallback) backCallback);
                    }
                    backCallback = null;
                }
                OnBackInvokedCallback cb = this::hide;
                backCallback = cb;
                backCallbackActivity = new java.lang.ref.WeakReference<>(act);
                act.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb);
            } catch (Throwable ignored) {}
        }
    }

    public void hide() {
        if (settingsActivity != null && settingsActivity.closeSearchIfOpen()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                Activity prevAct = backCallbackActivity != null ? backCallbackActivity.get() : Utils.getCurrentActivity();
                if (prevAct != null) {
                    prevAct.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                            (OnBackInvokedCallback) backCallback);
                }
            } catch (Throwable ignored) {}
            backCallback = null;
            backCallbackActivity = null;
        }
        SettingsActivity.isSettings = false;
        final int hideGen = ++openGeneration;
        if (settingsView != null && settingsView.getParent() instanceof android.view.ViewGroup) {
            final View viewToRemove = settingsView;
            final android.view.ViewGroup parent = (android.view.ViewGroup) settingsView.getParent();
            int width = parent.getWidth();
            if (width <= 0) width = viewToRemove.getResources().getDisplayMetrics().widthPixels;
            viewToRemove.animate()
                    .withEndAction(null)
                    .translationX(width)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        if (openGeneration == hideGen && viewToRemove.getParent() == parent) {
                            parent.removeView(viewToRemove);
                            viewToRemove.setTranslationX(0f);
                        }
                    })
                    .start();
        }
    }

    public Context getContext() {
        return Utils.getCurrentActivity();
    }

}
