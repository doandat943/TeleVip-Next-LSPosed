package com.magiii.televipnext.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.NativeFeatureTarget;
import com.magiii.televipnext.Configs.NativeFeatureTarget.LaunchMode;
import com.magiii.televipnext.Configs.NativeFeatureTarget.NativeScrollMode;
import com.magiii.televipnext.language.Keys;
import com.magiii.televipnext.language.Translator;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.ActionBar.Theme;
import com.magiii.televipnext.virtuals.ui.Components.UItem;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Navigates from TeleVip Next settings directly into a client's native settings screen,
 * then scrolls to and highlights the exact target row using client-native helpers
 * with a universal 3-tier fallback.
 */
public final class NativeFeatureNavigator {

    private static final String BASE_FRAGMENT_CLASS = "org.telegram.ui.ActionBar.BaseFragment";
    private static final String SIMPLE_TEXT_VIEW_CLASS = "org.telegram.ui.ActionBar.SimpleTextView";
    private static final String RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView";
    private static final String LINEAR_LAYOUT_MANAGER_CLASS = "androidx.recyclerview.widget.LinearLayoutManager";

    private static final long[] POLL_DELAYS_MS = {40L, 90L, 150L, 230L, 340L, 480L, 680L, 950L};
    private static final int MAX_SCAN_STEPS = 35;
    private static final long HIGHLIGHT_SETTLE_MS = 340L;
    private static final long HIGHLIGHT_DURATION_MS = 1280L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> GET_TEXT_CACHE =
            new ConcurrentHashMap<>();

    private NativeFeatureNavigator() {}

    public static void navigate(Activity activity, NativeFeatureTarget target, String clientName) {
        if (target == null || !target.isJumpable()) return;
        Activity act = activity != null ? activity : Utils.getCurrentActivity();
        if (act == null) return;

        Runnable task = () -> navigateInternal(act, target, clientName, 0);
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            MAIN_HANDLER.post(task);
        }
    }

    private static void navigateInternal(
            Activity activity, NativeFeatureTarget target, String clientName, int depth) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (target == null || !target.isJumpable()) {
            showFailureToast(activity, target, clientName);
            return;
        }

        try {
            if (target.launchMode == LaunchMode.EXTERA_REGISTRY) {
                if (tryExteraRegistryLaunch(target)) {
                    return;
                }
                handleFailure(activity, target, clientName, depth);
                return;
            }

            if (target.launchMode == LaunchMode.ANDROID_ACTIVITY) {
                if (tryActivityLaunch(activity, target)) {
                    return;
                }
                handleFailure(activity, target, clientName, depth);
                return;
            }

            boolean[] preScrollApplied = new boolean[1];
            Object fragment = instantiateFragment(target, preScrollApplied);
            if (fragment == null) {
                handleFailure(activity, target, clientName, depth);
                return;
            }

            if (target.nativeScrollMode == NativeScrollMode.MOMO_SET_SCROLL_TO
                    && target.nativeScrollKey != null) {
                preScrollApplied[0] = tryMomoSetScrollTo(fragment, target.nativeScrollKey);
            }

            long navStartMs = SystemClock.uptimeMillis();
            if (!presentFragment(activity, fragment)) {
                handleFailure(activity, target, clientName, depth);
                return;
            }

            if (!preScrollApplied[0]) {
                schedulePostNavigation(activity, fragment, target, navStartMs);
            }
        } catch (Throwable t) {
            Logger.e(t);
            handleFailure(activity, target, clientName, depth);
        }
    }

    private static void handleFailure(
            Activity activity, NativeFeatureTarget target, String clientName, int depth) {
        if (target != null && target.fallbackTarget != null && depth < 1) {
            navigateInternal(activity, target.fallbackTarget, clientName, depth + 1);
            return;
        }
        showFailureToast(activity, target, clientName);
    }

    private static void showFailureToast(
            Activity activity, NativeFeatureTarget target, String clientName) {
        try {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            String safeClient = (clientName != null && !clientName.isEmpty()) ? clientName : "Telegram";
            String hint = (target != null && target.sectionHint != null && !target.sectionHint.isEmpty())
                    ? target.sectionHint : safeClient;
            String message = Translator.get(Keys.NativeOpenFailed, safeClient, hint);
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // Launch Mode Implementations
    // =========================================================================

    private static boolean tryExteraRegistryLaunch(NativeFeatureTarget target) {
        try {
            if (target.nativeScrollKey == null || target.nativeScrollKey.isEmpty()) return false;
            Class<?> registryCls = resolveClass(target.targetClassName, target.fallbackClassNames);
            if (registryCls == null) return false;
            Object instance = null;
            Method getInstance = ReflectHelper.findMethodInHierarchy(registryCls, "getInstance", true);
            if (getInstance != null) {
                instance = getInstance.invoke(null);
            }
            if (instance == null) {
                Field f = ReflectHelper.findFieldIfExists(registryCls, "INSTANCE");
                if (f != null && Modifier.isStatic(f.getModifiers())) {
                    instance = f.get(null);
                }
            }
            if (instance == null) return false;
            Method handleLink = ReflectHelper.findMethodInHierarchy(
                    instance.getClass(), "handleLink", false, String.class, String.class);
            if (handleLink == null) return false;
            handleLink.invoke(instance, target.nativeScrollKey, null);
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static boolean tryActivityLaunch(Activity activity, NativeFeatureTarget target) {
        try {
            Class<?> actCls = resolveClass(target.targetClassName, target.fallbackClassNames);
            Intent intent = actCls != null
                    ? new Intent(activity, actCls)
                    : new Intent().setClassName(activity.getPackageName(), target.targetClassName);
            if (target.activityExtras != null) {
                intent.putExtras(target.activityExtras);
            }
            activity.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static Object instantiateFragment(NativeFeatureTarget target, boolean[] preScrollApplied) {
        try {
            Object fragment = null;
            if (target.launchMode == LaunchMode.FRAGMENT) {
                Class<?> fragCls = resolveClass(target.targetClassName, target.fallbackClassNames);
                if (fragCls != null) {
                    fragment = invokeBestConstructor(fragCls, target.ctorArgs);
                }
            } else if (target.launchMode == LaunchMode.WRAPPER_FRAGMENT) {
                Class<?> entryCls = resolveClass(target.targetClassName, target.fallbackClassNames);
                Class<?> wrapperCls = resolveClass(target.wrapperClassName, null);
                if (entryCls != null && wrapperCls != null) {
                    Object entryObj = invokeBestConstructor(entryCls, target.ctorArgs);
                    if (entryObj != null) {
                        fragment = instantiateWrapper(wrapperCls, entryObj, target, preScrollApplied);
                    }
                }
            } else if (target.launchMode == LaunchMode.STATIC_FACTORY) {
                Class<?> ownerCls = resolveClass(target.targetClassName, target.fallbackClassNames);
                if (ownerCls != null && target.factoryMethodName != null) {
                    fragment = ReflectHelper.callStaticMethod(ownerCls, target.factoryMethodName, target.ctorArgs);
                }
            }

            if (fragment == null) return null;
            Class<?> baseFragCls = resolveBaseFragmentClass();
            if (baseFragCls != null && !baseFragCls.isInstance(fragment)) {
                return null;
            }
            return fragment;
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static Object instantiateWrapper(
            Class<?> wrapperCls, Object entryObj, NativeFeatureTarget target, boolean[] preScrollApplied) {
        try {
            boolean wantKey = target.nativeScrollMode == NativeScrollMode.OCTO_WRAPPER_KEY
                    && target.nativeScrollKey != null;

            if (wantKey) {
                for (Constructor<?> c : wrapperCls.getDeclaredConstructors()) {
                    Class<?>[] params = c.getParameterTypes();
                    if (params.length == 2
                            && params[0].isInstance(entryObj)
                            && params[1] == String.class) {
                        c.setAccessible(true);
                        Object wrapper = c.newInstance(entryObj, target.nativeScrollKey);
                        if (preScrollApplied != null) preScrollApplied[0] = true;
                        return wrapper;
                    }
                }
            }

            for (Constructor<?> c : wrapperCls.getDeclaredConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 1 && params[0].isInstance(entryObj)) {
                    c.setAccessible(true);
                    return c.newInstance(entryObj);
                }
            }

            for (Constructor<?> c : wrapperCls.getDeclaredConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2
                        && params[0].isInstance(entryObj)
                        && !params[1].isPrimitive()) {
                    c.setAccessible(true);
                    boolean passKey = params[1] == String.class && wantKey;
                    Object arg2 = passKey ? target.nativeScrollKey : null;
                    Object wrapper = c.newInstance(entryObj, arg2);
                    if (passKey && preScrollApplied != null) preScrollApplied[0] = true;
                    return wrapper;
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return null;
    }

    private static boolean tryMomoSetScrollTo(Object fragment, String key) {
        try {
            Method m = ReflectHelper.findMethodInHierarchy(
                    fragment.getClass(), "setScrollTo", false, String.class, int.class, boolean.class);
            if (m != null) {
                m.invoke(fragment, key, 0, true);
                return true;
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return false;
    }

    // =========================================================================
    // Presenting on LaunchActivity
    // =========================================================================

    private static boolean presentFragment(Activity activity, Object fragment) {
        Class<?> baseFragCls = resolveBaseFragmentClass();
        Class<?> actCls = activity.getClass();

        // Primary: mapped LaunchActivity#presentFragment3(BaseFragment, boolean, boolean) -> boolean
        try {
            String mapped = Obfuscate.getMethodName("LaunchActivity", "presentFragment3");
            if (mapped != null && baseFragCls != null) {
                Method m = ReflectHelper.findMethodInHierarchy(
                        actCls, mapped, false, baseFragCls, boolean.class, boolean.class);
                if (m != null) {
                    Object res = m.invoke(activity, fragment, false, false);
                    return !(res instanceof Boolean) || (Boolean) res;
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }

        // Fallback 1: unique non-static (BaseFragment, boolean, boolean) -> boolean on LaunchActivity
        if (baseFragCls != null) {
            try {
                Method unique = null;
                int hits = 0;
                for (Class<?> cur = actCls; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                    for (Method m : cur.getDeclaredMethods()) {
                        if (Modifier.isStatic(m.getModifiers())) continue;
                        if (m.getReturnType() != boolean.class) continue;
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 3
                                && p[0] == baseFragCls
                                && p[1] == boolean.class
                                && p[2] == boolean.class) {
                            unique = m;
                            hits++;
                        }
                    }
                    if (hits > 0) break;
                }
                if (hits == 1 && unique != null) {
                    unique.setAccessible(true);
                    Object res = unique.invoke(activity, fragment, false, false);
                    return !(res instanceof Boolean) || (Boolean) res;
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        }

        // Fallback 2: method named "presentFragment" taking (BaseFragment) or (BaseFragment, boolean, boolean)
        if (baseFragCls != null) {
            try {
                Method m3 = ReflectHelper.findMethodInHierarchy(
                        actCls, "presentFragment", false, baseFragCls, boolean.class, boolean.class);
                if (m3 != null) {
                    Object res = m3.invoke(activity, fragment, false, false);
                    return !(res instanceof Boolean) || (Boolean) res;
                }
                Method m1 = ReflectHelper.findMethodInHierarchy(actCls, "presentFragment", false, baseFragCls);
                if (m1 != null) {
                    Object res = m1.invoke(activity, fragment);
                    return !(res instanceof Boolean) || (Boolean) res;
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        }

        // Fallback 3: actionBarLayout / INavigationLayout field -> presentFragment(fragment)
        if (baseFragCls != null) {
            try {
                for (Class<?> cur = actCls; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                    for (Field f : cur.getDeclaredFields()) {
                        if (Modifier.isStatic(f.getModifiers())) continue;
                        String typeName = f.getType().getName();
                        if (!typeName.contains("NavigationLayout") && !typeName.contains("ActionBarLayout")) {
                            continue;
                        }
                        f.setAccessible(true);
                        Object nav = f.get(activity);
                        if (nav == null) continue;
                        Method pm = ReflectHelper.findMethodInHierarchy(nav.getClass(), "presentFragment", false, baseFragCls);
                        if (pm != null) {
                            Object res = pm.invoke(nav, fragment);
                            return !(res instanceof Boolean) || (Boolean) res;
                        }
                    }
                }
            } catch (Throwable t) {
                Logger.e(t);
            }
        }

        return false;
    }

    // =========================================================================
    // Post-Navigation Native Scroll & 3-Tier Universal Locator
    // =========================================================================

    private static void schedulePostNavigation(
            Activity activity, Object fragment, NativeFeatureTarget target, long navStartMs) {
        boolean hasNativePostScroll = target.nativeScrollKey != null && !target.nativeScrollKey.isEmpty()
                && (target.nativeScrollMode == NativeScrollMode.CHERRYGRAM_SLUG
                || target.nativeScrollMode == NativeScrollMode.NEKO_SCROLL_TO_ROW
                || target.nativeScrollMode == NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW);
        boolean hasUniversalHints = target.rowStringResKeys.length > 0
                || target.rowFallbackTexts.length > 0
                || target.rowFieldNames.length > 0
                || target.uItemIds.length > 0;
        if (!hasNativePostScroll && !hasUniversalHints) return;

        pollForLayout(activity, fragment, target, navStartMs, 0);
    }

    private static void pollForLayout(
            Activity activity, Object fragment, NativeFeatureTarget target, long navStartMs, int attempt) {
        if (attempt >= POLL_DELAYS_MS.length) return;
        long delay = attempt == 0
                ? POLL_DELAYS_MS[0]
                : (POLL_DELAYS_MS[attempt] - POLL_DELAYS_MS[attempt - 1]);

        MAIN_HANDLER.postDelayed(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                View fragRoot = getFragmentView(fragment);
                if (fragRoot == null || !fragRoot.isAttachedToWindow() || fragRoot.getHeight() <= 0) {
                    pollForLayout(activity, fragment, target, navStartMs, attempt + 1);
                    return;
                }

                ViewGroup scrollable = findPrimaryScrollable(fragRoot);
                boolean listReady = scrollable != null
                        && scrollable.isAttachedToWindow()
                        && scrollable.getHeight() > 0
                        && scrollable.getChildCount() > 0;

                if (!listReady) {
                    if (attempt + 1 < POLL_DELAYS_MS.length) {
                        pollForLayout(activity, fragment, target, navStartMs, attempt + 1);
                        return;
                    }
                    // Final attempt: still try native post-scroll if the fragment root is attached
                    tryNativePostScroll(activity, fragment, scrollable, target, navStartMs);
                    return;
                }

                if (tryNativePostScroll(activity, fragment, scrollable, target, navStartMs)) {
                    return;
                }

                runUniversalRowLocator(activity, fragment, scrollable, target, navStartMs);
            } catch (Throwable t) {
                Logger.e(t);
            }
        }, Math.max(16L, delay));
    }

    private static boolean tryNativePostScroll(
            Activity activity,
            Object fragment,
            ViewGroup scrollable,
            NativeFeatureTarget target,
            long navStartMs) {
        if (target.nativeScrollKey == null || target.nativeScrollKey.isEmpty()) return false;

        Runnable fallbackRunner = () -> MAIN_HANDLER.post(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                View root = getFragmentView(fragment);
                ViewGroup sc = root != null ? findPrimaryScrollable(root) : scrollable;
                if (sc != null) {
                    runUniversalRowLocator(activity, fragment, sc, target, navStartMs);
                }
            } catch (Throwable ignored) {}
        });

        try {
            if (target.nativeScrollMode == NativeScrollMode.CHERRYGRAM_SLUG) {
                if (invokeScrollToRowWithRunnable(fragment, "scrollToRow", target.nativeScrollKey, fallbackRunner)) {
                    return true;
                }
            } else if (target.nativeScrollMode == NativeScrollMode.NEKO_SCROLL_TO_ROW) {
                String mapped = Obfuscate.getMethodName("BaseNekoXSettingsActivity", "scrollToRow");
                if (invokeScrollToRowWithRunnable(fragment, mapped, target.nativeScrollKey, fallbackRunner)) {
                    return true;
                }
                if (!"scrollToRow".equals(mapped)
                        && invokeScrollToRowWithRunnable(fragment, "scrollToRow", target.nativeScrollKey, fallbackRunner)) {
                    return true;
                }
            } else if (target.nativeScrollMode == NativeScrollMode.NEKOGRAM_SCROLL_TO_ROW) {
                String mapped = Obfuscate.getMethodName("BaseNekoSettingsActivity", "scrollToRow");
                Method m = findStringPlusOneArgMethod(fragment.getClass(), mapped);
                if (m == null && !"scrollToRow".equals(mapped)) {
                    m = findStringPlusOneArgMethod(fragment.getClass(), "scrollToRow");
                }
                if (m != null) {
                    m.invoke(fragment, target.nativeScrollKey, null);
                    return true;
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return false;
    }

    private static boolean invokeScrollToRowWithRunnable(
            Object fragment, String methodName, String key, Runnable onNotFound) throws Throwable {
        if (fragment == null || methodName == null || methodName.isEmpty()) return false;
        Class<?> cls = fragment.getClass();
        Method m = ReflectHelper.findMethodInHierarchy(cls, methodName, false, String.class, Runnable.class);
        if (m != null) {
            m.invoke(fragment, key, onNotFound);
            return true;
        }
        // R8 may reorder parameters to (Runnable, String) (e.g. Nagram 13.2.2 BaseNekoXSettingsActivity#g0)
        Method swapped = ReflectHelper.findMethodInHierarchy(cls, methodName, false, Runnable.class, String.class);
        if (swapped != null) {
            swapped.invoke(fragment, onNotFound, key);
            return true;
        }
        return false;
    }

    private static Method findStringPlusOneArgMethod(Class<?> cls, String name) {
        if (cls == null || name == null) return null;
        for (Class<?> cur = cls; cur != null && cur != Object.class; cur = cur.getSuperclass()) {
            for (Method m : cur.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (!name.equals(m.getName())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == String.class && !p[1].isPrimitive()) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static void runUniversalRowLocator(
            Activity activity,
            Object fragment,
            ViewGroup scrollable,
            NativeFeatureTarget target,
            long navStartMs) {
        List<String> expectedLabels = resolveExpectedLabels(activity, target);
        Class<?> rvCls = resolveRecyclerViewClass();

        if (rvCls != null && rvCls.isInstance(scrollable)) {
            if (tryTierADirectPosition(activity, fragment, scrollable, target, expectedLabels, navStartMs)) {
                return;
            }
            if (!expectedLabels.isEmpty()) {
                runTierBSynchronousScan(activity, scrollable, expectedLabels, navStartMs);
            }
            return;
        }

        if (!expectedLabels.isEmpty()) {
            runTierCGenericScrollable(activity, scrollable, expectedLabels, navStartMs);
        }
    }

    // =========================================================================
    // Tier A: Direct Position Fast Path (rowFieldNames + UItem IDs/text)
    // =========================================================================

    private static boolean tryTierADirectPosition(
            Activity activity,
            Object fragment,
            ViewGroup rv,
            NativeFeatureTarget target,
            List<String> expectedLabels,
            long navStartMs) {
        try {
            int itemCount = getRecyclerViewItemCount(rv);
            int targetPos = -1;
            Set<Integer> uItemIdCandidates = new HashSet<>();
            for (int id : target.uItemIds) {
                uItemIdCandidates.add(id);
            }

            // 1. Check rowFieldNames on fragment (instance int = row index, static int = UItem ID)
            if (target.rowFieldNames.length > 0) {
                for (String fieldName : target.rowFieldNames) {
                    Field f = ReflectHelper.findFieldIfExists(fragment.getClass(), fieldName);
                    if (f == null || f.getType() != int.class) continue;
                    if (Modifier.isStatic(f.getModifiers())) {
                        int id = f.getInt(null);
                        if (id != 0) uItemIdCandidates.add(id);
                    } else {
                        int pos = f.getInt(fragment);
                        if (pos >= 0 && (itemCount <= 0 || pos < itemCount)) {
                            targetPos = pos;
                            break;
                        }
                    }
                }
            }

            // 2. Check UniversalAdapter UItem list
            if (targetPos < 0 && (!uItemIdCandidates.isEmpty() || !expectedLabels.isEmpty())) {
                targetPos = findPositionInUItemAdapter(rv, uItemIdCandidates, expectedLabels);
            }

            if (targetPos < 0) return false;

            int desiredTop = (int) (rv.getHeight() * 0.32f);
            boolean scrolled = tryScrollToPositionWithOffset(rv, targetPos, desiredTop);
            if (!scrolled) {
                scrolled = tryScrollToPosition(rv, targetPos);
            }
            if (!scrolled) return false;

            rv.post(() -> {
                try {
                    if (activity.isFinishing() || activity.isDestroyed() || !rv.isAttachedToWindow()) return;
                    View matched = !expectedLabels.isEmpty()
                            ? findMatchingChild(rv, expectedLabels) : null;
                    if (matched == null) {
                        matched = findChildClosestToY(rv, desiredTop);
                    }
                    if (matched != null) {
                        int delta = matched.getTop() - desiredTop;
                        if (Math.abs(delta) > dp(rv.getContext(), 8)) {
                            rv.scrollBy(0, delta);
                        }
                        scheduleHighlightPulse(activity, rv, matched, expectedLabels, navStartMs);
                    }
                } catch (Throwable ignored) {}
            });
            return true;
        } catch (Throwable t) {
            Logger.e(t);
            return false;
        }
    }

    private static int findPositionInUItemAdapter(
            ViewGroup rv, Set<Integer> uItemIds, List<String> expectedLabels) {
        try {
            Class<?> uItemCls = ClassLoad.getClass(ClassNames.UITEM, Utils.classLoader, false);
            if (uItemCls == null) return -1;
            Object adapter = getRecyclerViewAdapter(rv);
            if (adapter == null) return -1;

            int bestTextPos = -1;
            int bestTextScore = 0;

            for (Class<?> cur = adapter.getClass(); cur != null && cur != Object.class; cur = cur.getSuperclass()) {
                for (Field f : cur.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (!List.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(adapter);
                    if (!(val instanceof List<?>)) continue;
                    List<?> list = (List<?>) val;
                    if (list.isEmpty()) continue;

                    Object sample = null;
                    for (int i = 0; i < Math.min(list.size(), 5); i++) {
                        if (list.get(i) != null) {
                            sample = list.get(i);
                            break;
                        }
                    }
                    if (sample == null || !uItemCls.isInstance(sample)) continue;

                    for (int i = 0; i < list.size(); i++) {
                        Object elem = list.get(i);
                        if (elem == null || !uItemCls.isInstance(elem)) continue;
                        UItem uItem = new UItem(elem);
                        if (!uItemIds.isEmpty()) {
                            try {
                                if (uItemIds.contains(uItem.getID())) {
                                    return i;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (!expectedLabels.isEmpty()) {
                            try {
                                int score = scoreTextAgainstLabels(uItem.getText(), expectedLabels);
                                if (score > bestTextScore) {
                                    bestTextScore = score;
                                    bestTextPos = i;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            return bestTextPos;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // =========================================================================
    // Tier B: Universal Synchronous View#scrollBy Scan (17/17 R8-immune)
    // =========================================================================

    private static void runTierBSynchronousScan(
            Activity activity, ViewGroup rv, List<String> expectedLabels, long navStartMs) {
        try {
            View matched = findMatchingChild(rv, expectedLabels);
            int totalScrolled = 0;

            if (matched == null) {
                int step = Math.max(dp(rv.getContext(), 180), (int) (rv.getHeight() * 0.78f));
                for (int i = 0; i < MAX_SCAN_STEPS; i++) {
                    if (!rv.canScrollVertically(1)) break;
                    rv.scrollBy(0, step);
                    totalScrolled += step;
                    matched = findMatchingChild(rv, expectedLabels);
                    if (matched != null) break;
                }
            }

            if (matched != null) {
                int desiredTop = (int) (rv.getHeight() * 0.32f);
                int delta = matched.getTop() - desiredTop;
                if (Math.abs(delta) > dp(rv.getContext(), 8)) {
                    rv.scrollBy(0, delta);
                }
                scheduleHighlightPulse(activity, rv, matched, expectedLabels, navStartMs);
            } else if (totalScrolled > 0) {
                if (!tryScrollToPosition(rv, 0)) {
                    rv.scrollBy(0, -totalScrolled);
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    // =========================================================================
    // Tier C: ScrollView / NestedScrollView / ListView Fallback
    // =========================================================================

    private static void runTierCGenericScrollable(
            Activity activity, ViewGroup container, List<String> expectedLabels, long navStartMs) {
        try {
            if (container instanceof ListView) {
                View matched = findMatchingChild(container, expectedLabels);
                if (matched != null) {
                    scheduleHighlightPulse(activity, container, matched, expectedLabels, navStartMs);
                }
                return;
            }

            View contentRoot = container.getChildCount() > 0 ? container.getChildAt(0) : null;
            if (!(contentRoot instanceof ViewGroup)) return;

            View matched = findBestMatchingDescendant((ViewGroup) contentRoot, expectedLabels);
            if (matched == null) return;

            int y = computeOffsetY(matched, contentRoot);
            int targetY = Math.max(0, y - container.getHeight() / 3);
            if (container instanceof ScrollView) {
                ((ScrollView) container).smoothScrollTo(0, targetY);
            } else {
                Method smoothScroll = ReflectHelper.findMethodInHierarchy(
                        container.getClass(), "smoothScrollTo", false, int.class, int.class);
                if (smoothScroll != null) {
                    smoothScroll.invoke(container, 0, targetY);
                } else {
                    container.scrollTo(0, targetY);
                }
            }
            scheduleHighlightPulse(activity, container, matched, expectedLabels, navStartMs);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static int computeOffsetY(View child, View ancestor) {
        int y = 0;
        View cur = child;
        while (cur != null && cur != ancestor) {
            y += cur.getTop();
            if (cur.getParent() instanceof View) {
                cur = (View) cur.getParent();
            } else {
                break;
            }
        }
        return y;
    }

    // =========================================================================
    // Step 4: Non-Invasive Highlight Pulse via ViewOverlay
    // =========================================================================

    private static void scheduleHighlightPulse(
            Activity activity,
            ViewGroup container,
            View initialTarget,
            List<String> expectedLabels,
            long navStartMs) {
        long elapsed = SystemClock.uptimeMillis() - navStartMs;
        long delay = Math.max(120L, HIGHLIGHT_SETTLE_MS - elapsed);

        MAIN_HANDLER.postDelayed(() -> {
            try {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                View targetView = initialTarget;
                if (container != null && container.isAttachedToWindow() && !expectedLabels.isEmpty()) {
                    View refreshed = findMatchingChild(container, expectedLabels);
                    if (refreshed != null) {
                        targetView = refreshed;
                    }
                }
                if (targetView == null || !targetView.isAttachedToWindow()
                        || targetView.getWidth() <= 0 || targetView.getHeight() <= 0) {
                    return;
                }
                playHighlightPulse(targetView);
            } catch (Throwable ignored) {}
        }, delay);
    }

    private static void playHighlightPulse(View targetView) {
        final int baseColor = Theme.isLight()
                ? Color.rgb(42, 148, 245)
                : Color.rgb(88, 178, 255);
        final int r = Color.red(baseColor);
        final int g = Color.green(baseColor);
        final int b = Color.blue(baseColor);

        final GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(Color.argb(0, r, g, b));
        highlight.setCornerRadius(dp(targetView.getContext(), 6));
        highlight.setBounds(0, 0, targetView.getWidth(), targetView.getHeight());
        targetView.getOverlay().add(highlight);

        Keyframe kf0 = Keyframe.ofInt(0f, 0);
        Keyframe kf1 = Keyframe.ofInt(220f / HIGHLIGHT_DURATION_MS, 72);
        Keyframe kf2 = Keyframe.ofInt(500f / HIGHLIGHT_DURATION_MS, 22);
        Keyframe kf3 = Keyframe.ofInt(740f / HIGHLIGHT_DURATION_MS, 64);
        Keyframe kf4 = Keyframe.ofInt(1f, 0);
        PropertyValuesHolder alphaHolder =
                PropertyValuesHolder.ofKeyframe("alpha", kf0, kf1, kf2, kf3, kf4);

        final ValueAnimator animator = ValueAnimator.ofPropertyValuesHolder(alphaHolder);
        animator.setDuration(HIGHLIGHT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());

        final View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                animator.cancel();
                try {
                    v.getOverlay().remove(highlight);
                } catch (Throwable ignored) {}
            }
        };
        targetView.addOnAttachStateChangeListener(detachListener);

        animator.addUpdateListener(anim -> {
            int alpha = (Integer) anim.getAnimatedValue();
            highlight.setColor(Color.argb(alpha, r, g, b));
            if (highlight.getBounds().width() != targetView.getWidth()
                    || highlight.getBounds().height() != targetView.getHeight()) {
                highlight.setBounds(0, 0, targetView.getWidth(), targetView.getHeight());
            }
            targetView.invalidate();
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                cleanup();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cleanup();
            }

            private void cleanup() {
                try {
                    targetView.removeOnAttachStateChangeListener(detachListener);
                    targetView.getOverlay().remove(highlight);
                } catch (Throwable ignored) {}
            }
        });

        animator.start();
    }

    // =========================================================================
    // Localized Label Resolution & View Text Matching
    // =========================================================================

    private static List<String> resolveExpectedLabels(Activity activity, NativeFeatureTarget target) {
        Set<String> labels = new LinkedHashSet<>();
        String pkg = activity.getPackageName();

        for (String resKey : target.rowStringResKeys) {
            if (resKey == null || resKey.isEmpty()) continue;
            try {
                int resId = activity.getResources().getIdentifier(resKey, "string", pkg);
                if (resId != 0) {
                    String inApp = getTelegramLocalizedString(resId);
                    addNormalizedLabel(labels, inApp);
                    try {
                        addNormalizedLabel(labels, activity.getString(resId));
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        for (String fallback : target.rowFallbackTexts) {
            addNormalizedLabel(labels, fallback);
        }

        return new ArrayList<>(labels);
    }

    private static String getTelegramLocalizedString(int resId) {
        try {
            Class<?> localeCls = ClassLoad.getClass(ClassNames.LOCALE_CONTROLLER, Utils.classLoader, false);
            if (localeCls == null) return null;
            String mapped = Obfuscate.getMethodName("LocaleController", "getString");
            Method m = mapped != null
                    ? ReflectHelper.findMethodInHierarchy(localeCls, mapped, true, int.class) : null;
            if (m == null && !"getString".equals(mapped)) {
                m = ReflectHelper.findMethodInHierarchy(localeCls, "getString", true, int.class);
            }
            if (m != null && m.getReturnType() == String.class) {
                Object res = m.invoke(null, resId);
                return res instanceof String ? (String) res : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void addNormalizedLabel(Set<String> out, String raw) {
        String norm = normalizeText(raw);
        if (!norm.isEmpty()) {
            out.add(norm);
        }
    }

    private static String normalizeText(CharSequence raw) {
        if (raw == null) return "";
        String s = raw.toString().trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("\\s+", " ");
        while (s.endsWith(":") || s.endsWith("\u2026") || s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static View findMatchingChild(ViewGroup parent, List<String> expectedLabels) {
        if (parent == null || expectedLabels.isEmpty()) return null;
        Class<?> simpleTextCls = ClassLoad.getClass(SIMPLE_TEXT_VIEW_CLASS, Utils.classLoader, false);
        View bestView = null;
        int bestScore = 0;

        int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            int score = scoreRowView(child, expectedLabels, simpleTextCls);
            if (score > bestScore) {
                bestScore = score;
                bestView = child;
            }
        }
        return bestView;
    }

    private static View findBestMatchingDescendant(ViewGroup root, List<String> expectedLabels) {
        Class<?> simpleTextCls = ClassLoad.getClass(SIMPLE_TEXT_VIEW_CLASS, Utils.classLoader, false);
        View bestView = null;
        int bestScore = 0;

        ArrayDeque<View> queue = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) {
            queue.add(root.getChildAt(i));
        }

        while (!queue.isEmpty()) {
            View v = queue.poll();
            if (v == null || v.getVisibility() != View.VISIBLE) continue;
            int score = scoreRowView(v, expectedLabels, simpleTextCls);
            if (score > bestScore) {
                bestScore = score;
                bestView = v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    queue.add(vg.getChildAt(i));
                }
            }
        }
        return bestView;
    }

    private static int scoreRowView(View row, List<String> expectedLabels, Class<?> simpleTextCls) {
        List<String> texts = new ArrayList<>();
        extractTexts(row, simpleTextCls, texts);
        if (texts.isEmpty()) return 0;

        int best = 0;
        for (int i = 0; i < texts.size(); i++) {
            int s = scoreTextAgainstLabels(texts.get(i), expectedLabels);
            if (s > 0 && i > 0) {
                // Slightly prefer the primary/first text in the row over subtitle text
                s = Math.max(1, s - 5);
            }
            if (s > best) best = s;
        }
        if (best <= 0) return 0;

        String clsName = row.getClass().getSimpleName();
        if (clsName.contains("HeaderCell")
                || clsName.contains("TextInfoPrivacyCell")
                || clsName.contains("ShadowSectionCell")) {
            best = Math.max(1, best - 50);
        }
        return best;
    }

    private static int scoreTextAgainstLabels(CharSequence rawText, List<String> expectedLabels) {
        String norm = normalizeText(rawText);
        if (norm.isEmpty()) return 0;
        int best = 0;
        for (int i = 0; i < expectedLabels.size(); i++) {
            String target = expectedLabels.get(i);
            int score = 0;
            if (norm.equals(target)) {
                score = 100;
            } else if (norm.startsWith(target) || target.startsWith(norm)) {
                score = 70;
            } else if (norm.contains(target)) {
                score = 40;
            }
            if (score > 0 && i == 0) {
                score += 5; // Prefer primary label over secondary fallback
            }
            if (score > best) best = score;
        }
        return best;
    }

    private static void extractTexts(View view, Class<?> simpleTextCls, List<String> out) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;

        if (view instanceof TextView) {
            CharSequence t = ((TextView) view).getText();
            if (t != null && t.length() > 0) out.add(t.toString());
        } else if (simpleTextCls != null && simpleTextCls.isInstance(view)) {
            try {
                String m = Obfuscate.getMethodName("SimpleTextView", "getText");
                Object res = ReflectHelper.callMethod(view, m);
                if (res instanceof CharSequence && ((CharSequence) res).length() > 0) {
                    out.add(res.toString());
                }
            } catch (Throwable ignored) {}
        } else if (!(view instanceof ViewGroup) && !(view instanceof ImageView)) {
            Method getTextMethod = resolveCachedGetTextMethod(view.getClass());
            if (getTextMethod != null) {
                try {
                    Object res = getTextMethod.invoke(view);
                    if (res instanceof CharSequence && ((CharSequence) res).length() > 0) {
                        out.add(res.toString());
                    }
                } catch (Throwable ignored) {}
            }
        }

        CharSequence cd = view.getContentDescription();
        if (cd != null && cd.length() > 0) {
            out.add(cd.toString());
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                extractTexts(vg.getChildAt(i), simpleTextCls, out);
            }
        }
    }

    private static Method resolveCachedGetTextMethod(Class<?> cls) {
        Optional<Method> cached = GET_TEXT_CACHE.get(cls);
        if (cached != null) return cached.orElse(null);
        Method found = null;
        try {
            Method m = cls.getMethod("getText");
            if (!Modifier.isStatic(m.getModifiers())
                    && m.getParameterTypes().length == 0
                    && CharSequence.class.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                found = m;
            }
        } catch (Throwable ignored) {}
        GET_TEXT_CACHE.putIfAbsent(cls, Optional.ofNullable(found));
        return found;
    }

    private static View findChildClosestToY(ViewGroup parent, int targetY) {
        View best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            int dist = Math.abs(child.getTop() - targetY);
            if (dist < bestDist) {
                bestDist = dist;
                best = child;
            }
        }
        return best;
    }

    // =========================================================================
    // View Hierarchy & RecyclerView Reflection Helpers
    // =========================================================================

    private static View getFragmentView(Object fragment) {
        if (fragment == null) return null;
        try {
            String mapped = Obfuscate.getMethodName("BaseFragment", "getFragmentView");
            Method m = mapped != null ? ReflectHelper.findMethodInHierarchy(fragment.getClass(), mapped, false) : null;
            if (m == null && !"getFragmentView".equals(mapped)) {
                m = ReflectHelper.findMethodInHierarchy(fragment.getClass(), "getFragmentView", false);
            }
            if (m != null && View.class.isAssignableFrom(m.getReturnType())) {
                Object res = m.invoke(fragment);
                if (res instanceof View) return (View) res;
            }
        } catch (Throwable ignored) {}

        try {
            Field f = ReflectHelper.findFieldIfExists(fragment.getClass(), "fragmentView");
            if (f != null && View.class.isAssignableFrom(f.getType())) {
                Object res = f.get(fragment);
                if (res instanceof View) return (View) res;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static ViewGroup findPrimaryScrollable(View root) {
        if (!(root instanceof ViewGroup)) return null;
        Class<?> rvCls = resolveRecyclerViewClass();
        ViewGroup fallbackScrollable = null;

        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            View v = queue.poll();
            if (v == null || v.getVisibility() != View.VISIBLE) continue;

            if (rvCls != null && rvCls.isInstance(v) && v instanceof ViewGroup) {
                ViewGroup rv = (ViewGroup) v;
                if (rv.getHeight() > 0 && rv.getChildCount() > 0) {
                    return rv;
                }
                if (fallbackScrollable == null) {
                    fallbackScrollable = rv;
                }
            } else if (fallbackScrollable == null && v instanceof ViewGroup) {
                if (v instanceof ListView || v instanceof ScrollView
                        || v.getClass().getName().contains("NestedScrollView")) {
                    fallbackScrollable = (ViewGroup) v;
                }
            }

            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    queue.add(vg.getChildAt(i));
                }
            }
        }
        return fallbackScrollable;
    }

    private static Object getRecyclerViewAdapter(ViewGroup rv) {
        try {
            String mapped = Obfuscate.getFieldName("RecyclerView", "mAdapter");
            Field f = mapped != null ? ReflectHelper.findFieldIfExists(rv.getClass(), mapped) : null;
            if (f == null && !"mAdapter".equals(mapped)) {
                f = ReflectHelper.findFieldIfExists(rv.getClass(), "mAdapter");
            }
            if (f != null) {
                return f.get(rv);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int getRecyclerViewItemCount(ViewGroup rv) {
        try {
            Object adapter = getRecyclerViewAdapter(rv);
            if (adapter == null) return -1;
            String mapped = Obfuscate.getMethodName("RecyclerView$Adapter", "getItemCount");
            Method m = mapped != null ? ReflectHelper.findMethodInHierarchy(adapter.getClass(), mapped, false) : null;
            if (m == null && !"getItemCount".equals(mapped)) {
                m = ReflectHelper.findMethodInHierarchy(adapter.getClass(), "getItemCount", false);
            }
            if (m != null && m.getReturnType() == int.class) {
                return (Integer) m.invoke(adapter);
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static boolean tryScrollToPositionWithOffset(ViewGroup rv, int pos, int offset) {
        try {
            String layoutFieldName = Obfuscate.getFieldName("RecyclerView", "mLayout");
            Field layoutField = layoutFieldName != null
                    ? ReflectHelper.findFieldIfExists(rv.getClass(), layoutFieldName) : null;
            if (layoutField == null && !"mLayout".equals(layoutFieldName)) {
                layoutField = ReflectHelper.findFieldIfExists(rv.getClass(), "mLayout");
            }
            if (layoutField == null) return false;
            Object lm = layoutField.get(rv);
            if (lm == null) return false;

            Class<?> llmCls = ClassLoad.getClass(LINEAR_LAYOUT_MANAGER_CLASS, Utils.classLoader, false);
            if (llmCls != null && !llmCls.isInstance(lm)) return false;

            String mapped = Obfuscate.getMethodName("LinearLayoutManager", "scrollToPositionWithOffset");
            Method m = mapped != null
                    ? ReflectHelper.findMethodInHierarchy(lm.getClass(), mapped, false, int.class, int.class) : null;
            if (m == null && !"scrollToPositionWithOffset".equals(mapped)) {
                m = ReflectHelper.findMethodInHierarchy(
                        lm.getClass(), "scrollToPositionWithOffset", false, int.class, int.class);
            }
            if (m != null) {
                m.invoke(lm, pos, offset);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean tryScrollToPosition(ViewGroup rv, int pos) {
        try {
            String mapped = Obfuscate.getMethodName("RecyclerView", "scrollToPosition");
            Method m = mapped != null
                    ? ReflectHelper.findMethodInHierarchy(rv.getClass(), mapped, false, int.class) : null;
            if (m == null && !"scrollToPosition".equals(mapped)) {
                m = ReflectHelper.findMethodInHierarchy(rv.getClass(), "scrollToPosition", false, int.class);
            }
            if (m != null) {
                m.invoke(rv, pos);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // =========================================================================
    // Reflection Utilities
    // =========================================================================

    private static Class<?> resolveBaseFragmentClass() {
        return ClassLoad.getClass(BASE_FRAGMENT_CLASS, Utils.classLoader, false);
    }

    private static Class<?> resolveRecyclerViewClass() {
        return ClassLoad.getClass(RECYCLER_VIEW_CLASS, Utils.classLoader, false);
    }

    private static Class<?> resolveClass(String primary, String[] fallbacks) {
        Class<?> cls = resolveSingleClass(primary);
        if (cls != null) return cls;
        if (fallbacks != null) {
            for (String fb : fallbacks) {
                cls = resolveSingleClass(fb);
                if (cls != null) return cls;
            }
        }
        return null;
    }

    private static Class<?> resolveSingleClass(String name) {
        if (name == null || name.isEmpty()) return null;
        Class<?> cls = ClassLoad.getClass(name, Utils.classLoader, false);
        if (cls != null) return cls;
        return ReflectHelper.findClassIfExists(name, Utils.classLoader);
    }

    private static Object invokeBestConstructor(Class<?> cls, Object[] args) {
        try {
            if (args == null || args.length == 0) {
                Constructor<?> zeroArg = cls.getDeclaredConstructor();
                zeroArg.setAccessible(true);
                return zeroArg.newInstance();
            }
        } catch (Throwable ignored) {}

        try {
            return ReflectHelper.newInstance(cls, args != null ? args : new Object[0]);
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static int dp(Context context, float value) {
        float density = context != null ? context.getResources().getDisplayMetrics().density : 2f;
        return Math.round(value * density);
    }
}
