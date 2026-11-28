package com.magiii.televipnext.features.stories;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.base.BaseMethodHook;
import com.magiii.televipnext.hooks.HMethod;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.ArgsResolver;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.xposed.ReflectHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DisableStories {

    public static boolean isEnable = false;
    private static final ThreadLocal<Object> sBlockedAddPostText = new ThreadLocal<>();

    private static boolean isHideChatList() {
        return ConfigManager.hideStoriesChatList != null && ConfigManager.hideStoriesChatList.isEnable();
    }

    private static boolean isHideRings() {
        return ConfigManager.hideStoriesRings != null && ConfigManager.hideStoriesRings.isEnable();
    }

    private static boolean isHideProfile() {
        return ConfigManager.hideStoriesProfile != null && ConfigManager.hideStoriesProfile.isEnable();
    }

    private static boolean isDisablePosting() {
        return ConfigManager.disableStoriesPosting != null && ConfigManager.disableStoriesPosting.isEnable();
    }

    private static boolean isHideAllDisplay() {
        return isHideChatList() && isHideRings() && isHideProfile();
    }

    public static void init() {
        try {
            if (isEnable) return;
            isEnable = true;

            Set<Method> hooked = new HashSet<>();

            BaseMethodHook chatFalse = guardHook(DisableStories::isHideChatList, false, false);
            BaseMethodHook chatNull = guardHook(DisableStories::isHideChatList, null, false);
            BaseMethodHook chatZero = guardHook(DisableStories::isHideChatList, 0, false);
            BaseMethodHook chatHideView = guardHook(DisableStories::isHideChatList, null, true);

            BaseMethodHook ringsHideView = guardHook(DisableStories::isHideRings, null, true);

            BaseMethodHook profileFalse = guardHook(DisableStories::isHideProfile, false, false);
            BaseMethodHook profileZero = guardHook(DisableStories::isHideProfile, 0, false);

            BaseMethodHook postingFalse = guardHook(DisableStories::isDisablePosting, false, false);
            BaseMethodHook allFalse = guardHook(DisableStories::isHideAllDisplay, false, false);
            BaseMethodHook allNull = guardHook(DisableStories::isHideAllDisplay, null, false);

            Class<?> mcClass = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
            if (mcClass != null) {
                HMethod.hookMethod(mcClass, "MessagesController", new String[]{"storiesEnabled", "storyEntitiesAllowed"}, postingFalse);
                HMethod.hookMethod(mcClass, Obfuscate.getMethodName("MessagesController", "storyEntitiesAllowed2"),
                        ArgsResolver.merge("storyEntitiesAllowed", new Class[]{ClassLoad.getClass(ClassNames.TLRPC_USER)}, postingFalse));
            }

            hookStoriesController(hooked, chatFalse, chatNull, postingFalse, allFalse, allNull);
            hookStoriesList(hooked, profileFalse, profileZero);
            hookSharedMediaLayout(hooked, profileFalse);
            hookProfileActivity(hooked);
            hookDialogStoriesCell(hooked, chatHideView, chatNull);
            hookDialogsActivity(hooked, chatFalse, chatNull, chatZero);
            hookProfileStoriesView(hooked, ringsHideView);
            hookAvatarStoryRings(hooked);
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private interface Condition {
        boolean test();
    }

    private static BaseMethodHook guardHook(final Condition condition, final Object result, final boolean hideView) {
        return new BaseMethodHook() {
            @Override
            protected void beforeMethod(MethodHookParam param) {
                if (!condition.test()) return;
                if (hideView && param.thisObject instanceof View) {
                    ((View) param.thisObject).setVisibility(View.GONE);
                }
                param.setResult(result);
            }
        };
    }

    private static void hookStoriesController(
            Set<Method> hooked,
            BaseMethodHook chatFalse,
            BaseMethodHook chatNull,
            BaseMethodHook postingFalse,
            BaseMethodHook bothFalse,
            BaseMethodHook bothNull) {
        Class<?> scClass = ClassLoad.getClass(ClassNames.STORIES_CONTROLLER);
        if (scClass == null) return;

        String hasAll = Obfuscate.getMethodName("StoriesController", "hasStoriesAll");
        if (!"hasStoriesAll".equals(hasAll)) {
            for (String mName : hasAll.split(",")) {
                if (!mName.isEmpty()) hookZeroArgBoolean(scClass, mName, hooked, chatFalse);
            }
        } else {
            hookZeroArgBoolean(scClass, Obfuscate.getMethodName("StoriesController", "hasStories"), hooked, chatFalse);
            String hasSelf = Obfuscate.getMethodName("StoriesController", "hasSelfStories");
            if (!"hasSelfStories".equals(hasSelf)) hookZeroArgBoolean(scClass, hasSelf, hooked, chatFalse);
        }

        String hasForDialog = Obfuscate.getMethodName("StoriesController", "hasStoriesForDialog");
        String hasSingle = !"hasStoriesForDialog".equals(hasForDialog) ? hasForDialog : Obfuscate.getMethodName("StoriesController", "hasStories");
        hookExactIfExists(scClass, hasSingle, hooked, bothFalse, long.class);

        String canPost = Obfuscate.getMethodName("StoriesController", "canPostStories");
        hookExactIfExists(scClass, canPost, hooked, postingFalse, long.class);

        String canPostChat = Obfuscate.getMethodName("StoriesController", "canPostStoriesChat");
        if (!"canPostStoriesChat".equals(canPostChat) || hasDeclaredZeroOrMatch(scClass, "canPostStories")) {
            Class<?> chatCls = ClassLoad.getClass(ClassNames.TLRPC_CHAT);
            if (chatCls != null) {
                hookExactIfExists(scClass, "canPostStoriesChat".equals(canPostChat) ? "canPostStories" : canPostChat, hooked, postingFalse, chatCls);
            }
        }

        String procAll = Obfuscate.getMethodName("StoriesController", "processAllStoriesResponse");
        for (Method m : scClass.getDeclaredMethods()) {
            if (m.getName().equals(procAll) && m.getReturnType() == void.class && m.getParameterCount() == 4) {
                Class<?>[] p = m.getParameterTypes();
                if (!p[0].isPrimitive() && p[1] == boolean.class && p[2] == boolean.class && p[3] == boolean.class) {
                    hookMethodOnce(m, hooked, bothNull);
                }
            }
        }

        String procUpd = Obfuscate.getMethodName("StoriesController", "processUpdate");
        for (Method m : scClass.getDeclaredMethods()) {
            if (m.getName().equals(procUpd) && m.getReturnType() == void.class
                    && m.getParameterCount() == 1 && !m.getParameterTypes()[0].isPrimitive()) {
                hookMethodOnce(m, hooked, bothNull);
            }
        }

        String peerAdd = Obfuscate.getMethodName("StoriesController", "peerListAddMethods");
        if (!"peerListAddMethods".equals(peerAdd)) {
            for (String mName : peerAdd.split(",")) {
                if (mName.isEmpty()) continue;
                for (Method m : scClass.getDeclaredMethods()) {
                    if (m.getName().equals(mName) && m.getReturnType() == void.class
                            && m.getParameterCount() == 1 && !m.getParameterTypes()[0].isPrimitive()) {
                        hookMethodOnce(m, hooked, chatNull);
                    }
                }
            }
        }

        String getPeer = Obfuscate.getMethodName("StoriesController", "getPeerStoriesMethods");
        String[] getPeerNames = !"getPeerStoriesMethods".equals(getPeer) ? getPeer.split(",") : new String[]{"getStories", "getStoriesFromFullPeer"};
        for (String mName : getPeerNames) {
            if (mName.isEmpty()) continue;
            for (Method m : scClass.getDeclaredMethods()) {
                if (m.getName().equals(mName) && !m.getReturnType().isPrimitive() && m.getReturnType() != void.class
                        && m.getParameterCount() == 1 && m.getParameterTypes()[0] == long.class) {
                    hookMethodOnce(m, hooked, bothNull);
                }
            }
        }
    }

    private static void hookStoriesList(Set<Method> hooked, BaseMethodHook profileFalse, BaseMethodHook profileZero) {
        Class<?> slClass = ClassLoad.getClass(ClassNames.STORIES_LIST);
        if (slClass == null) return;

        String loadName = Obfuscate.getMethodName("StoriesList", "load");
        for (Method m : slClass.getDeclaredMethods()) {
            if (m.getName().equals(loadName) && m.getReturnType() == boolean.class && m.getParameterCount() == 3) {
                int nBool = 0, nInt = 0, nList = 0;
                for (Class<?> pt : m.getParameterTypes()) {
                    if (pt == boolean.class) nBool++;
                    else if (pt == int.class) nInt++;
                    else if (pt == List.class) nList++;
                }
                if (nBool == 1 && nInt == 1 && nList == 1) hookMethodOnce(m, hooked, profileFalse);
            }
        }

        String getCount = Obfuscate.getMethodName("StoriesList", "getCount");
        hookExactIfExists(slClass, getCount, hooked, profileZero);
        String getLoadedCount = Obfuscate.getMethodName("StoriesList", "getLoadedCount");
        hookExactIfExists(slClass, getLoadedCount, hooked, profileZero);
    }

    private static void hookSharedMediaLayout(Set<Method> hooked, BaseMethodHook profileFalse) {
        Class<?> smlClass = ClassLoad.getClass(ClassNames.SHARED_MEDIA_LAYOUT);
        if (smlClass != null) {
            String includeStories = Obfuscate.getMethodName("SharedMediaLayout", "includeStories");
            hookZeroArgBoolean(smlClass, includeStories, hooked, profileFalse);

            String addPostText = Obfuscate.getMethodName("SharedMediaLayout", "addPostText");
            for (Method m : smlClass.getDeclaredMethods()) {
                if (!m.getName().equals(addPostText) || Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                if (CharSequence.class.isAssignableFrom(rt)) {
                    hookMethodOnce(m, hooked, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (!isDisablePosting()) return;
                            Object res = param.getResult();
                            if (res instanceof Spanned
                                    && ((Spanned) res).getSpans(0, ((Spanned) res).length(), ImageSpan.class).length > 0) {
                                sBlockedAddPostText.set(res);
                            }
                        }
                    });
                }
            }
        }

        Class<?> btnClass = ClassLoad.getClass(ClassNames.BUTTON_WITH_COUNTER_VIEW);
        if (btnClass != null) {
            String setText = Obfuscate.getMethodName("ButtonWithCounterView", "setText");
            Method setTextM = ReflectHelper.findMethodExactIfExists(btnClass, setText, CharSequence.class, boolean.class);
            hookMethodOnce(setTextM, hooked, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (!isDisablePosting() || !(param.thisObject instanceof View) || param.args.length == 0) return;
                    Object expected = sBlockedAddPostText.get();
                    if (expected != null && param.args[0] == expected) {
                        sBlockedAddPostText.remove();
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private static void hookProfileActivity(Set<Method> hooked) {
        final Class<?> paClass = ClassLoad.getClass(ClassNames.PROFILE_ACTIVITY);
        if (paClass == null) return;

        String updRows = Obfuscate.getMethodName("ProfileActivity", "updateRowsIds");
        Method updRowsM = ReflectHelper.findMethodExactIfExists(paClass, updRows);

        final String myProfileField = Obfuscate.getFieldName("ProfileActivity", "myProfile");
        final String smRowField = Obfuscate.getFieldName("ProfileActivity", "sharedMediaRow");
        final String bpRowField = Obfuscate.getFieldName("ProfileActivity", "bottomPaddingRow");
        final String userInfoField = Obfuscate.getFieldName("ProfileActivity", "userInfo");
        final String chatInfoField = Obfuscate.getFieldName("ProfileActivity", "chatInfo");
        final String bottomButtonField = Obfuscate.getFieldName("ProfileActivity", "bottomButton");

        final boolean hasMyProfile = ReflectHelper.findFieldIfExists(paClass, myProfileField) != null;
        final boolean hasSmRow = ReflectHelper.findFieldIfExists(paClass, smRowField) != null;
        final boolean hasBpRow = ReflectHelper.findFieldIfExists(paClass, bpRowField) != null;
        final boolean hasUserInfo = ReflectHelper.findFieldIfExists(paClass, userInfoField) != null;
        final boolean hasChatInfo = ReflectHelper.findFieldIfExists(paClass, chatInfoField) != null;
        final boolean hasBottomButton = ReflectHelper.findFieldIfExists(paClass, bottomButtonField) != null;

        if (updRowsM != null) {
            hookMethodOnce(updRowsM, hooked, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (!isHideProfile() || param.thisObject == null) return;
                    if (hasUserInfo && clearStoriesPinned(ReflectHelper.getObjectField(param.thisObject, userInfoField))) {
                        param.setObjectExtra("tv_u_spa", Boolean.TRUE);
                    }
                    if (hasChatInfo && clearStoriesPinned(ReflectHelper.getObjectField(param.thisObject, chatInfoField))) {
                        param.setObjectExtra("tv_c_spa", Boolean.TRUE);
                    }
                }

                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (param.thisObject == null) return;
                    Object uInfo = hasUserInfo ? ReflectHelper.getObjectField(param.thisObject, userInfoField) : null;
                    Object cInfo = hasChatInfo ? ReflectHelper.getObjectField(param.thisObject, chatInfoField) : null;
                    restoreStoriesPinned(uInfo, param.getObjectExtra("tv_u_spa"));
                    restoreStoriesPinned(cInfo, param.getObjectExtra("tv_c_spa"));

                    if (!isHideProfile() || !hasMyProfile || !hasSmRow || !hasBpRow) return;
                    if (!ReflectHelper.getBooleanField(param.thisObject, myProfileField)) return;

                    boolean hasGifts = false;
                    if (uInfo != null) {
                        try {
                            hasGifts = ReflectHelper.getIntField(uInfo, "stargifts_count") > 0;
                        } catch (Throwable ignored) {
                        }
                    }
                    if (!hasGifts) {
                        int smRow = ReflectHelper.getIntField(param.thisObject, smRowField);
                        if (smRow >= 0) {
                            ReflectHelper.setIntField(param.thisObject, smRowField, -1);
                            if (ReflectHelper.getIntField(param.thisObject, bpRowField) < 0) {
                                ReflectHelper.setIntField(param.thisObject, bpRowField, smRow);
                            }
                        }
                    }
                }
            });
        }

        if (hasBottomButton) {
            String paCreate = Obfuscate.getMethodName("ProfileActivity", "createView");
            if (paCreate == null || "createView".equals(paCreate)) {
                String bfCreate = Obfuscate.getMethodName("BaseFragment", "createView");
                if (bfCreate != null && !"createView".equals(bfCreate)) paCreate = bfCreate;
            }
            Method createViewM = ReflectHelper.findMethodExactIfExists(paClass, paCreate, Context.class);
            if (createViewM == null && !"createView".equals(paCreate)) {
                createViewM = ReflectHelper.findMethodExactIfExists(paClass, "createView", Context.class);
            }
            hookMethodOnce(createViewM, hooked, new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (isDisablePosting()) {
                        setProfileAddPostButtonVisible(param.thisObject, bottomButtonField, false);
                    }
                }
            });

            String checkBtn = Obfuscate.getMethodName("ProfileActivity", "checkStoriesButtonText");
            for (Method m : paClass.getDeclaredMethods()) {
                if (!m.getName().equals(checkBtn) || m.getReturnType() != void.class) continue;
                final boolean isStatic = Modifier.isStatic(m.getModifiers());
                Class<?>[] p = m.getParameterTypes();
                boolean staticMatch = isStatic && p.length >= 2 && p[0] == paClass && p[1] == int.class
                        && (p.length == 2 || (p.length == 3 && p[2] == boolean.class));
                boolean instMatch = !isStatic && p.length >= 1 && p[0] == int.class
                        && (p.length == 1 || (p.length == 2 && p[1] == boolean.class));
                if (staticMatch || instMatch) {
                    hookMethodOnce(m, hooked, new BaseMethodHook() {
                        @Override
                        protected void afterMethod(MethodHookParam param) {
                            if (!isDisablePosting()) return;
                            Object target = isStatic ? param.args[0] : param.thisObject;
                            int count = (Integer) param.args[isStatic ? 1 : 0];
                            setProfileAddPostButtonVisible(target, bottomButtonField, count > 0);
                        }
                    });
                }
            }
        }
    }

    private static void setProfileAddPostButtonVisible(Object profileActivity, String bottomButtonField, boolean visible) {
        if (profileActivity == null) return;
        try {
            Object arr = ReflectHelper.getObjectField(profileActivity, bottomButtonField);
            if (arr instanceof Object[]) {
                Object[] buttons = (Object[]) arr;
                if (buttons.length > 0 && buttons[0] instanceof View) {
                    int vis = visible ? View.VISIBLE : View.GONE;
                    View btn = (View) buttons[0];
                    btn.setVisibility(vis);
                    if (btn.getParent() instanceof ViewGroup && ((ViewGroup) btn.getParent()).getChildCount() == 1) {
                        ((View) btn.getParent()).setVisibility(vis);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean clearStoriesPinned(Object info) {
        if (info == null) return false;
        try {
            if (ReflectHelper.getBooleanField(info, "stories_pinned_available")) {
                ReflectHelper.setBooleanField(info, "stories_pinned_available", false);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void restoreStoriesPinned(Object info, Object marker) {
        if (info == null || !Boolean.TRUE.equals(marker)) return;
        try {
            ReflectHelper.setBooleanField(info, "stories_pinned_available", true);
        } catch (Throwable ignored) {
        }
    }

    private static void hookDialogStoriesCell(Set<Method> hooked, BaseMethodHook chatHideView, BaseMethodHook chatNull) {
        Class<?> dscClass = ClassLoad.getClass(ClassNames.DIALOG_STORIES_CELL);
        if (dscClass == null) return;

        String updateItems = Obfuscate.getMethodName("DialogStoriesCell", "updateItems");
        hookExactIfExists(dscClass, updateItems, hooked, chatHideView, boolean.class, boolean.class);

        String setOverscroll = Obfuscate.getMethodName("DialogStoriesCell", "setOverscroll");
        hookExactIfExists(dscClass, setOverscroll, hooked, chatNull, float.class);
    }

    private static void hookDialogsActivity(Set<Method> hooked, BaseMethodHook chatFalse, BaseMethodHook chatNull, BaseMethodHook chatZero) {
        Class<?> daClass = ClassLoad.getClass(ClassNames.DIALOGS_ACTIVITY);
        if (daClass == null) return;

        final String dscField = Obfuscate.getFieldName("DialogsActivity", "dialogStoriesCell");
        final String hasStoriesField = Obfuscate.getFieldName("DialogsActivity", "hasStories");
        final String getScMethod = Obfuscate.getMethodName("DialogsActivity", "getStoriesController");
        final String scListFields = Obfuscate.getFieldName("StoriesController", "storyListFields");
        final boolean hasBooleanField = ReflectHelper.findFieldIfExists(daClass, hasStoriesField) != null;

        String updVis = Obfuscate.getMethodName("DialogsActivity", "updateStoriesVisibility");
        Method visM = ReflectHelper.findMethodExactIfExists(daClass, updVis, boolean.class);
        if (visM != null) {
            hookMethodOnce(visM, hooked, new BaseMethodHook() {
                @Override
                protected void beforeMethod(MethodHookParam param) {
                    if (!isHideChatList()) return;
                    try {
                        Object sc = ReflectHelper.callMethod(param.thisObject, getScMethod);
                        if (sc != null && !"storyListFields".equals(scListFields)) {
                            for (String f : scListFields.split(",")) {
                                if (!f.isEmpty()) {
                                    Object list = ReflectHelper.getObjectField(sc, f);
                                    if (list instanceof List) {
                                        ((List<?>) list).clear();
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    if (hasBooleanField) {
                        try {
                            ReflectHelper.setBooleanField(param.thisObject, hasStoriesField, false);
                        } catch (Throwable ignored) {
                        }
                    }
                }

                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (!isHideChatList()) return;
                    if (hasBooleanField) {
                        try {
                            ReflectHelper.setBooleanField(param.thisObject, hasStoriesField, false);
                        } catch (Throwable ignored) {
                        }
                    }
                    Object cell = ReflectHelper.getObjectField(param.thisObject, dscField);
                    if (cell instanceof View) {
                        ((View) cell).setVisibility(View.GONE);
                    }
                }
            });
        }

        String noSearch = Obfuscate.getMethodName("DialogsActivity", "getMaxScrollYOffsetWithoutSearch");
        if (!"getMaxScrollYOffsetWithoutSearch".equals(noSearch)
                || ReflectHelper.findMethodExactIfExists(daClass, noSearch) != null) {
            Method noSrchM = ReflectHelper.findMethodExactIfExists(daClass, noSearch);
            if (noSrchM != null && noSrchM.getReturnType() == int.class) {
                hookMethodOnce(noSrchM, hooked, chatZero);
            }
        }

        String setOverscroll = Obfuscate.getMethodName("DialogsActivity", "setStoriesOvercroll");
        for (Method m : daClass.getDeclaredMethods()) {
            if (!m.getName().equals(setOverscroll) || m.getReturnType() != void.class) continue;
            Class<?>[] p = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && p.length == 2
                    && !p[0].isPrimitive() && p[1] == float.class) {
                hookMethodOnce(m, hooked, chatNull);
            } else if (Modifier.isStatic(m.getModifiers()) && p.length == 3
                    && p[0] == daClass && !p[1].isPrimitive() && p[2] == float.class) {
                hookMethodOnce(m, hooked, chatNull);
            }
        }

        String checkAuto = Obfuscate.getMethodName("DialogsActivity", "checkAutoscrollToStories");
        for (Method m : daClass.getDeclaredMethods()) {
            if (!m.getName().equals(checkAuto) || m.getReturnType() != boolean.class) continue;
            Class<?>[] p = m.getParameterTypes();
            if (!Modifier.isStatic(m.getModifiers()) && p.length == 1 && !p[0].isPrimitive()) {
                hookMethodOnce(m, hooked, chatFalse);
            } else if (Modifier.isStatic(m.getModifiers()) && p.length == 2
                    && p[0] == daClass && !p[1].isPrimitive()) {
                hookMethodOnce(m, hooked, chatFalse);
            }
        }

        final String fabField = Obfuscate.getFieldName("DialogsActivity", "floatingButtonStories");
        final String fabSetVis = Obfuscate.getMethodName("FragmentFloatingButton", "setButtonVisible");
        final boolean hasFabField = !"floatingButtonStories".equals(fabField)
                || ReflectHelper.findFieldIfExists(daClass, fabField) != null;

        if (hasFabField) {
            String fabVisMethod = Obfuscate.getMethodName("DialogsActivity", "updateFloatingButtonVisibility");
            Method fabVisM = ReflectHelper.findMethodExactIfExists(daClass, fabVisMethod, boolean.class);
            BaseMethodHook hideFabAfter = new BaseMethodHook() {
                @Override
                protected void afterMethod(MethodHookParam param) {
                    if (isDisablePosting()) {
                        hideFloatingStoriesButton(param.thisObject, fabField, fabSetVis);
                    }
                }
            };
            hookMethodOnce(fabVisM, hooked, hideFabAfter);

            String daCreate = Obfuscate.getMethodName("DialogsActivity", "createView");
            if (daCreate == null || "createView".equals(daCreate)) {
                String bfCreate = Obfuscate.getMethodName("BaseFragment", "createView");
                if (bfCreate != null && !"createView".equals(bfCreate)) daCreate = bfCreate;
            }
            Method createViewM = ReflectHelper.findMethodExactIfExists(daClass, daCreate, Context.class);
            if (createViewM == null && !"createView".equals(daCreate)) {
                createViewM = ReflectHelper.findMethodExactIfExists(daClass, "createView", Context.class);
            }
            hookMethodOnce(createViewM, hooked, hideFabAfter);
        }
    }

    private static void hideFloatingStoriesButton(Object dialogsActivity, String fabField, String fabSetVis) {
        if (dialogsActivity == null) return;
        try {
            Object fab = ReflectHelper.getObjectField(dialogsActivity, fabField);
            if (fab == null) return;
            if (ReflectHelper.findMethodExactIfExists(fab.getClass(), fabSetVis, boolean.class, boolean.class) != null) {
                ReflectHelper.callMethod(fab, fabSetVis, false, false);
            }
            if (fab instanceof View) {
                ((View) fab).setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookProfileStoriesView(Set<Method> hooked, BaseMethodHook ringsHideView) {
        Class<?> psvClass = ClassLoad.getClass(ClassNames.PROFILE_STORIES_VIEW);
        if (psvClass == null) return;

        String updateStories = Obfuscate.getMethodName("ProfileStoriesView", "updateStories");
        hookExactIfExists(psvClass, updateStories, hooked, ringsHideView, boolean.class, boolean.class);

        String setStories = Obfuscate.getMethodName("ProfileStoriesView", "setStories");
        for (Method m : psvClass.getDeclaredMethods()) {
            if (m.getName().equals(setStories) && m.getReturnType() == void.class
                    && m.getParameterCount() == 1 && !m.getParameterTypes()[0].isPrimitive()) {
                hookMethodOnce(m, hooked, new BaseMethodHook() {
                    @Override
                    protected void beforeMethod(MethodHookParam param) {
                        if (isHideRings()) {
                            param.args[0] = null;
                            if (param.thisObject instanceof View) {
                                ((View) param.thisObject).setVisibility(View.GONE);
                            }
                        }
                    }
                });
            }
        }
    }

    private static void hookAvatarStoryRings(Set<Method> hooked) {
        Class<?> suClass = ClassLoad.getClass(ClassNames.STORIES_UTILITIES);
        Class<?> paramsClass = ClassLoad.getClass(ClassNames.AVATAR_STORY_PARAMS);
        final String isDscField = Obfuscate.getFieldName("AvatarStoryParams", "isDialogStoriesCell");
        final boolean hasIsDsc = paramsClass != null && ReflectHelper.findFieldIfExists(paramsClass, isDscField) != null;

        if (suClass != null) {
            String drawMethod = Obfuscate.getMethodName("StoriesUtilities", "drawAvatarWithStory");
            for (Method m : suClass.getDeclaredMethods()) {
                if (!m.getName().equals(drawMethod) || !Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != void.class || m.getParameterCount() < 5) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if (p[0] == long.class && p[1] == Canvas.class && !p[2].isPrimitive()
                        && p[3] == boolean.class && (paramsClass == null ? !p[4].isPrimitive() : p[4] == paramsClass)) {
                    hookMethodOnce(m, hooked, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (isHideRings() && !isDialogStoriesCell(param.args[4], hasIsDsc, isDscField)) {
                                param.args[0] = 0L;
                                param.args[3] = false;
                            }
                        }
                    });
                }
            }
        }

        if (paramsClass != null) {
            String touchMethod = Obfuscate.getMethodName("AvatarStoryParams", "checkOnTouchEvent");
            for (Method m : paramsClass.getDeclaredMethods()) {
                if (!m.getName().equals(touchMethod) || Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != boolean.class || m.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] p = m.getParameterTypes();
                if ((p[0] == MotionEvent.class && p[1] == View.class)
                        || (p[0] == View.class && p[1] == MotionEvent.class)) {
                    hookMethodOnce(m, hooked, new BaseMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (isHideRings() && !isDialogStoriesCell(param.thisObject, hasIsDsc, isDscField)) {
                                param.setResult(false);
                            }
                        }
                    });
                }
            }
        }
    }

    private static boolean isDialogStoriesCell(Object params, boolean hasIsDsc, String isDscField) {
        if (!hasIsDsc || params == null) return false;
        try {
            return ReflectHelper.getBooleanField(params, isDscField);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasDeclaredZeroOrMatch(Class<?> cls, String name) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    private static void hookZeroArgBoolean(Class<?> cls, String name, Set<Method> hooked, BaseMethodHook cb) {
        Method m = ReflectHelper.findMethodExactIfExists(cls, name);
        if (m != null && m.getReturnType() == boolean.class) {
            hookMethodOnce(m, hooked, cb);
        }
    }

    private static void hookExactIfExists(Class<?> cls, String name, Set<Method> hooked, BaseMethodHook cb, Class<?>... params) {
        Method m = ReflectHelper.findMethodExactIfExists(cls, name, (Object[]) params);
        hookMethodOnce(m, hooked, cb);
    }

    private static void hookMethodOnce(Method m, Set<Method> hooked, BaseMethodHook cb) {
        if (m != null && hooked.add(m)) {
            HMethod.hookMethod(m, cb);
        }
    }

}
