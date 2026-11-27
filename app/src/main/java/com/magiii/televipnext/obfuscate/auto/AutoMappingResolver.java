package com.magiii.televipnext.obfuscate.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.struct.ClientObfuscationData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AutoMappingResolver {

    private static final String PREFS_NAME = "TeleVip_AutoMap";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_SCHEMA_VER = "schemaVersion";
    private static final String KEY_MAPPING_JSON = "mappingJson";
    private static final int SCHEMA_VERSION = 55;

    private final Map<String, String> classMap = new HashMap<>();
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();

    public static ClientObfuscationData resolveOrLoadCache(Context context, String sourceDir, ClassLoader classLoader) {
        if (context == null || sourceDir == null) return null;

        long versionCode = getVersionCode(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (versionCode > 0
                && prefs.getInt(KEY_SCHEMA_VER, 0) == SCHEMA_VERSION
                && prefs.getLong(KEY_VERSION_CODE, -1L) == versionCode) {
            String cached = prefs.getString(KEY_MAPPING_JSON, null);
            if (cached != null && !cached.isEmpty()) {
            ClientObfuscationData data = ClientObfuscationData.parse(cached, "{}");
            if (data != null) {
                Logger.w("AutoMappingResolver: loaded cached mapping for versionCode=" + versionCode);
                return data;
            }
        }
        }

        long t0 = System.currentTimeMillis();
        try {
            AutoMappingResolver resolver = new AutoMappingResolver();
            String json = resolver.buildMapping(sourceDir, classLoader);
            if (json != null) {
                prefs.edit()
                        .putLong(KEY_VERSION_CODE, versionCode)
                        .putInt(KEY_SCHEMA_VER, SCHEMA_VERSION)
                        .putString(KEY_MAPPING_JSON, json)
                        .apply();
                Logger.w("AutoMappingResolver: resolved " + resolver.classMap.size() + " classes, "
                        + resolver.methodMap.size() + " methods, " + resolver.fieldMap.size()
                        + " fields in " + (System.currentTimeMillis() - t0) + "ms (v=" + versionCode + ")");
                return ClientObfuscationData.parse(json, "{}");
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
        return null;
    }

    private static long getVersionCode(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pi.getLongVersionCode();
            }
            return pi.versionCode;
        } catch (Throwable t) {
            return -1L;
        }
    }

    private void addClass(String original, String resolvedDesc) {
        if (original != null && resolvedDesc != null) {
            classMap.put(original, DexScanner.descToJava(resolvedDesc));
        }
    }

    private void addMethod(String simpleClass, String originalMethod, String resolvedMethod) {
        if (simpleClass != null && originalMethod != null && resolvedMethod != null) {
            methodMap.put(simpleClass + "#" + originalMethod, resolvedMethod);
        }
    }

    private void addField(String simpleClass, String originalField, String resolvedField) {
        if (simpleClass != null && originalField != null && resolvedField != null) {
            fieldMap.put(simpleClass + "#" + originalField, resolvedField);
        }
    }

    private String buildMapping(String sourceDir, ClassLoader classLoader) throws Exception {
        DexScanner scanner = new DexScanner(sourceDir);

        // 1. Reflection-based superclasses (immune to R8), with DEX fallback
        String baseControllerDesc = getSuperDesc(classLoader, ClassNames.CONNECTIONS_MANAGER);
        if (baseControllerDesc == null) {
            baseControllerDesc = scanner.getSuperClass(DexScanner.javaToDesc(ClassNames.CONNECTIONS_MANAGER));
        }
        String applicationLoaderDesc = null;
        if (hasClass(scanner, "Lorg/telegram/messenger/ApplicationLoader;")
                && hasStaticFieldOfType(scanner, "Lorg/telegram/messenger/ApplicationLoader;", "Landroid/content/Context;")) {
            applicationLoaderDesc = "Lorg/telegram/messenger/ApplicationLoader;";
        }
        if (applicationLoaderDesc == null) {
            String cur = scanner.getSuperClass("Lorg/telegram/messenger/ApplicationLoaderImpl;");
            if (cur == null) {
                cur = getSuperDesc(classLoader, "org.telegram.messenger.ApplicationLoaderImpl");
            }
            for (int hop = 0; hop < 4 && cur != null && !"Landroid/app/Application;".equals(cur); hop++) {
                if (hasStaticFieldOfType(scanner, cur, "Landroid/content/Context;")) {
                    applicationLoaderDesc = cur;
                    break;
                }
                cur = scanner.getSuperClass(cur);
            }
            if (applicationLoaderDesc == null) {
                applicationLoaderDesc = getSuperDesc(classLoader, "org.telegram.messenger.ApplicationLoaderImpl");
                if (applicationLoaderDesc == null) {
                    applicationLoaderDesc = scanner.getSuperClass("Lorg/telegram/messenger/ApplicationLoaderImpl;");
                }
                if (applicationLoaderDesc == null && hasClass(scanner, "Lorg/telegram/messenger/ApplicationLoader;")) {
                    applicationLoaderDesc = "Lorg/telegram/messenger/ApplicationLoader;";
                }
            }
        }
        String baseFragmentDesc = getSuperDesc(classLoader, ClassNames.PROFILE_ACTIVITY);
        if (baseFragmentDesc == null) {
            baseFragmentDesc = scanner.getSuperClass(DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY));
        }

        if (applicationLoaderDesc != null) {
            addClass(ClassNames.APPLICATION_LOADER, applicationLoaderDesc);
            String appCtxField = hasFieldNamed(scanner, applicationLoaderDesc, "applicationContext")
                    ? "applicationContext"
                    : scanner.findSingleField(applicationLoaderDesc, true, "Landroid/content/Context;");
            if (appCtxField == null) {
                for (DexScanner.FieldInfo wf : scanner.getWrittenStaticFieldsInMethod(
                        applicationLoaderDesc, "onCreate", new String[0], applicationLoaderDesc)) {
                    if ("Landroid/content/Context;".equals(wf.typeDesc)) {
                        appCtxField = wf.name;
                        break;
                    }
                }
            }
            if (appCtxField == null) {
                for (DexScanner.FieldInfo f : scanner.getFields(applicationLoaderDesc)) {
                    if (f.isStatic && "Landroid/content/Context;".equals(f.typeDesc)) {
                        appCtxField = f.name;
                        break;
                    }
                }
            }
            addField("ApplicationLoader", "applicationContext", appCtxField);
        }

        String launchActDesc = DexScanner.javaToDesc(ClassNames.LAUNCH_ACTIVITY);
        String launchFrameField = scanner.findSingleField(launchActDesc, false, "Landroid/widget/FrameLayout;");
        if (launchFrameField == null) {
            for (DexScanner.FieldInfo f : scanner.getFields(launchActDesc)) {
                if (!f.isStatic && "frameLayout".equals(f.name) && "Landroid/widget/FrameLayout;".equals(f.typeDesc)) {
                    launchFrameField = f.name;
                    break;
                }
            }
        }
        addField("LaunchActivity", "frameLayout", launchFrameField);

        String actionBarDesc = null;
        if (baseFragmentDesc != null) {
            addClass("org.telegram.ui.ActionBar.BaseFragment", baseFragmentDesc);
            // Resolve ActionBar via BaseFragment method (Context) -> ActionBar (largest ActionBar/ non-SimpleActionBar return type)
            String bestActionBar = null;
            int bestAbMethods = 0;
            for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                if (!m.isStatic && m.paramDescs.length == 1 && "Landroid/content/Context;".equals(m.paramDescs[0])
                        && m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                    int mCnt = scanner.getMethods(m.returnDesc).size();
                    if (mCnt > bestAbMethods) {
                        bestAbMethods = mCnt;
                        bestActionBar = m.returnDesc;
                    }
                }
            }
            if (bestActionBar != null) {
                actionBarDesc = bestActionBar;
                addClass("org.telegram.ui.ActionBar.ActionBar", bestActionBar);
            }
        }

        // 2. PhotoViewer -> MessageObject, ChatActivity, PhotoViewerProvider
        String photoViewerDesc = DexScanner.javaToDesc(ClassNames.PHOTO_VIEWER);
        String msgObjDesc = null;
        String chatActDesc = null;
        String pvProviderDesc = null;

        String chatOpenPhoto = null;
        for (int pass = 0; pass < 2 && msgObjDesc == null; pass++) {
            for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 6
                        && "J".equals(m.paramDescs[2]) && "J".equals(m.paramDescs[3]) && "J".equals(m.paramDescs[4])
                        && (pass == 0
                                ? m.paramDescs[0].startsWith("Lorg/telegram/messenger/")
                                : (m.paramDescs[0].startsWith("L") && !m.paramDescs[0].startsWith("Ljava/") && !m.paramDescs[0].startsWith("Landroid/")))
                        && m.paramDescs[1].startsWith("Lorg/telegram/ui/")
                        && m.paramDescs[5].startsWith("L")
                        && !m.paramDescs[5].startsWith("Ljava/")
                        && !m.paramDescs[5].startsWith("Landroid/")) {
                    msgObjDesc = m.paramDescs[0];
                    chatActDesc = m.paramDescs[1];
                    pvProviderDesc = m.paramDescs[5];
                    chatOpenPhoto = m.name;
                    break;
                }
            }
        }
        // Prefer exact 6-arg overload used by virtuals/ui/PhotoViewer: (MessageObject, J, J, J, PhotoViewerProvider, Z)
        String callerOpenPhoto = null;
        if (msgObjDesc != null && pvProviderDesc != null) {
            callerOpenPhoto = scanner.findSingleMethod(photoViewerDesc, false, "Z",
                    msgObjDesc, "J", "J", "J", pvProviderDesc, "Z");
        }
        if (callerOpenPhoto != null) {
            addMethod("PhotoViewer", "openPhoto", callerOpenPhoto);
        } else if (chatOpenPhoto != null) {
            addMethod("PhotoViewer", "openPhoto", chatOpenPhoto);
        }
        // PhotoViewer#getInstance: largest static () -> PhotoViewer
        DexScanner.MethodInfo bestPvInst = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
            if (m.isStatic && photoViewerDesc.equals(m.returnDesc) && m.paramDescs.length == 0) {
                if (bestPvInst == null || m.getInsnsSize() > bestPvInst.getInsnsSize()) {
                    bestPvInst = m;
                }
            }
        }
        if (bestPvInst != null) {
            addMethod("PhotoViewer", "getInstance", bestPvInst.name);
        }

        // PhotoViewer#setParentActivity: prefer 1-arg (Activity) -> V, else largest (Activity, ...) -> V
        String setParentExact = scanner.findSingleMethod(photoViewerDesc, false, "V", "Landroid/app/Activity;");
        if (setParentExact != null) {
            addMethod("PhotoViewer", "setParentActivity", setParentExact);
        } else {
            DexScanner.MethodInfo bestSetParent = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 1 && m.paramDescs.length <= 3
                        && "Landroid/app/Activity;".equals(m.paramDescs[0])) {
                    if (bestSetParent == null || m.getInsnsSize() > bestSetParent.getInsnsSize()) {
                        bestSetParent = m;
                    }
                }
            }
            if (bestSetParent != null) {
                addMethod("PhotoViewer", "setParentActivity", bestSetParent.name);
            }
        }

        // PhotoViewer#setIsAboutToSwitchToIndex: larger (I, Z, Z, Z) -> V
        DexScanner.MethodInfo bestSwitchIdx = null;
        DexScanner.MethodInfo smallSwitchIdx = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                    && "I".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                    && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                if (bestSwitchIdx == null || m.getInsnsSize() > bestSwitchIdx.getInsnsSize()) {
                    bestSwitchIdx = m;
                }
                if (smallSwitchIdx == null || m.getInsnsSize() < smallSwitchIdx.getInsnsSize()) {
                    smallSwitchIdx = m;
                }
            }
        }
        if (bestSwitchIdx != null) {
            addMethod("PhotoViewer", "setIsAboutToSwitchToIndex", bestSwitchIdx.name);
            if (smallSwitchIdx != null && !smallSwitchIdx.name.equals(bestSwitchIdx.name)) {
                java.util.Set<String> smallSubItems = new java.util.HashSet<>();
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(photoViewerDesc, smallSwitchIdx.name, new String[]{"I", "Z", "Z", "Z"}, photoViewerDesc)) {
                    if (rf.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                        int mCnt = scanner.getMethods(rf.typeDesc).size();
                        if (mCnt >= 20 && mCnt <= 70) {
                            smallSubItems.add(rf.name);
                        }
                    }
                }
                String galleryField = null;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(photoViewerDesc, bestSwitchIdx.name, new String[]{"I", "Z", "Z", "Z"}, photoViewerDesc)) {
                    if (smallSubItems.contains(rf.name)) {
                        galleryField = rf.name;
                        break;
                    }
                }
                if (galleryField != null) {
                    addField("PhotoViewer", "galleryButton", galleryField);
                }
            }
        }

        // SecretMediaViewer#closePhoto & openMedia & onClose
        String secretViewerDesc = DexScanner.javaToDesc(ClassNames.SECRET_MEDIA_VIEWER);
        DexScanner.MethodInfo bestClosePhoto = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(secretViewerDesc)) {
            if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 2
                    && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) {
                if (bestClosePhoto == null || m.getInsnsSize() > bestClosePhoto.getInsnsSize()) {
                    bestClosePhoto = m;
                }
            }
        }
        if (bestClosePhoto == null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(secretViewerDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) {
                    if (bestClosePhoto == null || m.getInsnsSize() > bestClosePhoto.getInsnsSize()) {
                        bestClosePhoto = m;
                    }
                }
            }
        }
        if (bestClosePhoto != null) {
            addMethod("SecretMediaViewer", "closePhoto", bestClosePhoto.name);
        }

        if (msgObjDesc != null) {
            DexScanner.MethodInfo openMediaMethod = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(secretViewerDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                        && msgObjDesc.equals(m.paramDescs[0])) {
                    if (openMediaMethod == null || m.getInsnsSize() > openMediaMethod.getInsnsSize()) {
                        openMediaMethod = m;
                    }
                }
            }
            if (openMediaMethod != null) {
                addMethod("SecretMediaViewer", "openMedia", openMediaMethod.name);
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(secretViewerDesc, openMediaMethod.name, openMediaMethod.paramDescs, secretViewerDesc)) {
                    if ("Ljava/lang/Runnable;".equals(wf.typeDesc)) {
                        addField("SecretMediaViewer", "onClose", wf.name);
                        break;
                    }
                }
            }

            addClass(ClassNames.MESSAGE_OBJECT, msgObjDesc);
            addField("MessageObject", "messageOwner",
                    scanner.findSingleField(msgObjDesc, false, DexScanner.javaToDesc(ClassNames.MESSAGE)));
            String instDialogId = scanner.findSingleMethod(msgObjDesc, false, "J");
            if (instDialogId != null) {
                addMethod("MessageObject", "getDialogId", instDialogId);
            } else {
                String msgDesc = DexScanner.javaToDesc(ClassNames.MESSAGE);
                String staticDialogId = scanner.findSingleMethod(msgObjDesc, true, "J", msgDesc);
                if (staticDialogId == null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                        if (m.isStatic && "J".equals(m.returnDesc) && m.paramDescs.length == 1
                                && msgDesc.equals(m.paramDescs[0]) && m.readsFieldNamed("dialog_id")) {
                            staticDialogId = m.name;
                            break;
                        }
                    }
                }
                if (staticDialogId != null) {
                    addMethod("MessageObject", "getDialogId", staticDialogId);
                }
            }
            DexScanner.MethodInfo isSecretMethod = null;
            DexScanner.MethodInfo bestCanForward = null;
            boolean exactCanForward = false;
            String secretMsgDesc = "Lorg/telegram/tgnet/TLRPC$TL_message_secret;";
            for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    if (m.readsFieldNamed("voice")) {
                        addMethod("MessageObject", "isVoice", m.name);
                    } else if ("canForwardMessage".equals(m.name)) {
                        bestCanForward = m;
                        exactCanForward = true;
                    } else if (!exactCanForward && m.readsFieldNamed("noforwards")) {
                        if (bestCanForward == null || m.getInsnsSize() < bestCanForward.getInsnsSize()) {
                            bestCanForward = m;
                        }
                    } else if (m.checksInstanceOf(secretMsgDesc)) {
                        if (isSecretMethod == null || m.getInsnsSize() < isSecretMethod.getInsnsSize()) {
                            isSecretMethod = m;
                        }
                    }
                }
            }
            if (bestCanForward != null) {
                addMethod("MessageObject", "canForwardMessage", bestCanForward.name);
            }
            if (isSecretMethod == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0
                            && (m.readsFieldNamed("destroyTime") || m.readsFieldNamed("destroyTimeMillis"))) {
                        isSecretMethod = m;
                        break;
                    }
                }
            }
            if (isSecretMethod != null) {
                addMethod("MessageObject", "isSecret", isSecretMethod.name);
            }
            resolveMessageObjectAudio(scanner, msgObjDesc);
        }

        if (chatActDesc != null) {
            addClass(ClassNames.CHAT_ACTIVITY, chatActDesc);
            addMethod("ChatActivity", "createView",
                    scanner.findSingleMethod(chatActDesc, false, "Landroid/view/View;", "Landroid/content/Context;"));
            String fillMenuName = null;
            String[] fillMenuParams = null;
            if (msgObjDesc != null) {
                String[][] fillMenuVariants = new String[][]{
                        {msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;"},
                        {msgObjDesc, msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;"},
                        {msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/lang/CharSequence;"},
                        {msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Landroid/view/View;"},
                        {msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/HashMap;"},
                        {msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;"}
                };
                for (String[] variant : fillMenuVariants) {
                    fillMenuName = scanner.findSingleMethod(chatActDesc, false, "V", variant);
                    if (fillMenuName != null) {
                        fillMenuParams = variant;
                        break;
                    }
                }
                if (fillMenuName == null) {
                    DexScanner.MethodInfo bestFillMenu = null;
                    for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                        if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length < 4
                                || !msgObjDesc.equals(m.paramDescs[0]) || m.getInsnsSize() <= 1000) {
                            continue;
                        }
                        int alCount = 0;
                        for (String p : m.paramDescs) {
                            if ("Ljava/util/ArrayList;".equals(p)) alCount++;
                        }
                        if (alCount >= 3 && (bestFillMenu == null || m.getInsnsSize() > bestFillMenu.getInsnsSize())) {
                            bestFillMenu = m;
                        }
                    }
                    if (bestFillMenu != null) {
                        fillMenuName = bestFillMenu.name;
                        fillMenuParams = bestFillMenu.paramDescs;
                    }
                }
                addMethod("ChatActivity", "fillMessageMenu", fillMenuName);
                for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                    if (!m.isStatic && !"V".equals(m.returnDesc)) {
                        if (m.paramDescs.length == 2 && msgObjDesc.equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                                && m.readsFieldNamed("ttl") && m.readsFieldNamed("destroyTime")) {
                            addMethod("ChatActivity", "sendSecretMessageRead", m.name);
                        } else if (m.paramDescs.length == 1 && msgObjDesc.equals(m.paramDescs[0])
                                && m.readsFieldNamed("ttl") && m.containsConstInt(0x7FFFFFFF)) {
                            addMethod("ChatActivity", "sendSecretMediaDelete", m.name);
                        }
                    }
                }
            }
            DexScanner.MethodInfo bestOpt = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    if (bestOpt == null || m.getInsnsSize() > bestOpt.getInsnsSize()) {
                        bestOpt = m;
                    }
                } else if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.readsFieldNamed("noforwards")) {
                    addMethod("ChatActivity", "hasSelectedNoforwardsMessage", m.name);
                }
            }
            if (bestOpt != null && bestOpt.getInsnsSize() > 500) {
                addMethod("ChatActivity", "processSelectedOption", bestOpt.name);
            }
            if (msgObjDesc != null) {
                String probeMethod = fillMenuName != null ? fillMenuName : (bestOpt != null ? bestOpt.name : null);
                String[] probeParams = fillMenuName != null
                        ? fillMenuParams
                        : (bestOpt != null ? new String[]{"I"} : null);
                if (probeMethod != null && probeParams != null) {
                    Map<String, Integer> moReads = new HashMap<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(chatActDesc, probeMethod, probeParams, chatActDesc)) {
                        if (msgObjDesc.equals(rf.typeDesc)) {
                            Integer c = moReads.get(rf.name);
                            moReads.put(rf.name, c == null ? 1 : c + 1);
                        }
                    }
                    String bestSelectedObj = null;
                    int bestReads = 0;
                    for (Map.Entry<String, Integer> e : moReads.entrySet()) {
                        if (e.getValue() > bestReads) {
                            bestReads = e.getValue();
                            bestSelectedObj = e.getKey();
                        }
                    }
                    if (bestSelectedObj != null) {
                        addField("ChatActivity", "selectedObject", bestSelectedObj);
                    }
                }
            }

            resolveChatPinnedMessageView(scanner, chatActDesc);
        }

        if (pvProviderDesc != null) {
            addClass(ClassNames.PHOTO_VIEWER_PROVIDER, pvProviderDesc);
            if (msgObjDesc != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(pvProviderDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 5 && msgObjDesc.equals(m.paramDescs[0])
                            && "I".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3]) && "Z".equals(m.paramDescs[4])) {
                        addMethod("PhotoViewer$PhotoViewerProvider", "getPlaceForPhoto", m.name);
                        String placeDesc = m.returnDesc;
                        if (placeDesc != null && placeDesc.startsWith("L")) {
                            addClass("org.telegram.ui.PhotoViewer$PlaceProviderObject", placeDesc);
                            String imgRecvField = scanner.findSingleField(placeDesc, false, "Lorg/telegram/messenger/ImageReceiver;");
                            if (imgRecvField != null) {
                                addField("PhotoViewer$PlaceProviderObject", "imageReceiver", imgRecvField);
                            }
                        }
                        break;
                    }
                }
            }
        }

        // 3. BaseController subclasses
        String msgCtrlDesc = null;
        String userConfigDesc = null;
        boolean tgnetObfuscated = !hasClass(scanner, DexScanner.javaToDesc(ClassNames.MESSAGE));
        if (tgnetObfuscated) {
            resolveObfuscatedTgnetBase(scanner, msgObjDesc, chatActDesc);
        }
        String tlObjectDesc = resolvedDescOrRaw(ClassNames.TL_OBJECT);
        String tlEncChatDesc = resolvedDescOrRaw(ClassNames.TLRPC_ENCRYPTED_CHAT);
        String tlMessageDesc = resolvedDescOrRaw(ClassNames.MESSAGE);

        if (baseControllerDesc != null) {
            userConfigDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "userconfing");
            if (userConfigDesc == null) {
                userConfigDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "userconfig");
            }
            if (userConfigDesc == null && baseFragmentDesc != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                    if (!m.isStatic && "getUserConfig".equals(m.name) && m.paramDescs.length == 0
                            && scanner.isSubclassOf(m.returnDesc, baseControllerDesc)) {
                        userConfigDesc = m.returnDesc;
                        break;
                    }
                }
            }
            if (userConfigDesc != null) {
                addClass(ClassNames.USER_CONFIG, userConfigDesc);
                if (baseFragmentDesc != null) {
                    addMethod("BaseFragment", "getUserConfig",
                            scanner.findSingleMethod(baseFragmentDesc, false, userConfigDesc));
                }
                String getInstName = scanner.findSingleMethod(userConfigDesc, true, userConfigDesc, "I");
                addMethod("UserConfig", "getInstance", getInstName);
                String selectedAccField = null;
                if (getInstName != null) {
                    for (DexScanner.FieldInfo f : scanner.getReadStaticFieldsInMethod(userConfigDesc, getInstName, new String[]{"I"}, userConfigDesc)) {
                        if ("I".equals(f.typeDesc)) {
                            selectedAccField = f.name;
                            break;
                        }
                    }
                }
                if (selectedAccField == null) {
                    selectedAccField = scanner.findSingleField(userConfigDesc, true, "I");
                }
                if (selectedAccField == null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                        if (m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length == 0) {
                            String candidate = null;
                            boolean valid = true;
                            for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(userConfigDesc, m.name, new String[0], userConfigDesc)) {
                                if ("I".equals(rf.typeDesc)) {
                                    if (candidate == null) candidate = rf.name;
                                    else if (!candidate.equals(rf.name)) {
                                        valid = false;
                                        break;
                                    }
                                }
                            }
                            if (valid && candidate != null) {
                                selectedAccField = candidate;
                                break;
                            }
                        }
                    }
                }
                addField("UserConfig", "selectedAccount", selectedAccField);
                if (tgnetObfuscated && tlObjectDesc != null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                        if (!m.isStatic && m.paramDescs.length == 0
                                && scanner.isSubclassOf(m.returnDesc, tlObjectDesc)
                                && scanner.getFields(m.returnDesc).size() >= 40) {
                            addClass(ClassNames.TLRPC_USER, m.returnDesc);
                            addMethod("UserConfig", "getCurrentUser", m.name);
                            break;
                        }
                    }
                } else {
                    addMethod("UserConfig", "getCurrentUser",
                            scanner.findSingleMethod(userConfigDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_USER)));
                }
                addMethod("UserConfig", "getClientUserId",
                        scanner.findSingleMethod(userConfigDesc, false, "J"));
                for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.readsFieldNamed("premium")) {
                        addMethod("UserConfig", "isPremium", m.name);
                        break;
                    }
                }
            }

            String msgStorageDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "DELETE FROM media_counts_v2");
            if (msgStorageDesc != null) {
                addClass(ClassNames.MESSAGES_STORAGE, msgStorageDesc);
                addMethod("MessagesStorage", "getInstance",
                        hasMethodNamed(scanner, msgStorageDesc, "getInstance")
                                ? "getInstance"
                                : scanner.findSingleMethod(msgStorageDesc, true, msgStorageDesc, "I"));
                addMethod("MessagesStorage", "getDatabase",
                        hasMethodNamed(scanner, msgStorageDesc, "getDatabase")
                                ? "getDatabase"
                                : scanner.findSingleMethod(msgStorageDesc, false, "Lorg/telegram/SQLite/SQLiteDatabase;"));
                addMethod("MessagesStorage", "markMessagesAsDeleted",
                        hasMethodNamed(scanner, msgStorageDesc, "markMessagesAsDeleted")
                                ? "markMessagesAsDeleted"
                                : scanner.findSingleMethod(msgStorageDesc, false, "Ljava/util/ArrayList;",
                                "J", "Ljava/util/ArrayList;", "Z", "Z", "I", "I"));
                String tlMsgsDesc = DexScanner.javaToDesc(ClassNames.TL_MESSAGES_MESSAGES);
                DexScanner.MethodInfo bestPutMsgs = null;
                boolean rawPutMsgs = false;
                for (DexScanner.MethodInfo m : scanner.getMethods(msgStorageDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && !m.name.startsWith("lambda$")) {
                        for (String p : m.paramDescs) {
                            if (tlMsgsDesc.equals(p)) {
                                if ("putMessages".equals(m.name)) {
                                    bestPutMsgs = m;
                                    rawPutMsgs = true;
                                    break;
                                }
                                if (!rawPutMsgs && (bestPutMsgs == null || m.getInsnsSize() > bestPutMsgs.getInsnsSize())) {
                                    bestPutMsgs = m;
                                }
                                break;
                            }
                        }
                    }
                }
                if (bestPutMsgs != null) {
                    addMethod("MessagesStorage", "putMessages", bestPutMsgs.name);
                }
                for (DexScanner.MethodInfo m : scanner.getMethods(msgStorageDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 0
                            && (m.returnDesc.startsWith("Lorg/telegram/messenger/")
                            || (m.returnDesc.startsWith("L") && !m.returnDesc.startsWith("Ljava/") && !m.returnDesc.startsWith("Landroid/")
                            && scanner.isSubclassOf(m.returnDesc, "Ljava/lang/Thread;")))) {
                        boolean hasDelayedPost = false;
                        DexScanner.MethodInfo bestPostRun = null;
                        for (DexScanner.MethodInfo dm : scanner.getMethods(m.returnDesc)) {
                            if (!dm.isStatic && dm.paramDescs.length == 2
                                    && "Ljava/lang/Runnable;".equals(dm.paramDescs[0]) && "J".equals(dm.paramDescs[1])
                                    && ("Z".equals(dm.returnDesc) || "V".equals(dm.returnDesc))) {
                                hasDelayedPost = true;
                            } else if (!dm.isStatic && dm.paramDescs.length == 1
                                    && "Ljava/lang/Runnable;".equals(dm.paramDescs[0])
                                    && ("Z".equals(dm.returnDesc) || "V".equals(dm.returnDesc))) {
                                if ("postRunnable".equals(dm.name)) {
                                    bestPostRun = dm;
                                } else if (bestPostRun == null || (!"postRunnable".equals(bestPostRun.name) && dm.getInsnsSize() < bestPostRun.getInsnsSize())) {
                                    bestPostRun = dm;
                                }
                            }
                        }
                        if (hasDelayedPost && bestPostRun != null) {
                            addMethod("MessagesStorage", "getStorageQueue", m.name);
                            addClass("org.telegram.messenger.DispatchQueue", m.returnDesc);
                            addMethod("DispatchQueue", "postRunnable", bestPostRun.name);
                            String utilDesc = DexScanner.javaToDesc(ClassNames.UTILITIES);
                            if (hasFieldNamed(scanner, utilDesc, "stageQueue")) {
                                addField("Utilities", "stageQueue", "stageQueue");
                            } else {
                                String threadFallback = null;
                                for (DexScanner.FieldInfo wf : scanner.getWrittenStaticFieldsInMethod(utilDesc, "<clinit>", new String[0], utilDesc)) {
                                    if (m.returnDesc.equals(wf.typeDesc)
                                            || scanner.isSubclassOf(wf.typeDesc, m.returnDesc)
                                            || scanner.isSubclassOf(m.returnDesc, wf.typeDesc)) {
                                        addField("Utilities", "stageQueue", wf.name);
                                        threadFallback = null;
                                        break;
                                    }
                                    if (threadFallback == null && scanner.isSubclassOf(wf.typeDesc, "Ljava/lang/Thread;")) {
                                        threadFallback = wf.name;
                                    }
                                }
                                if (threadFallback != null) {
                                    addField("Utilities", "stageQueue", threadFallback);
                                }
                            }
                            break;
                        }
                    }
                }
                if (!classMap.containsKey("org.telegram.messenger.DispatchQueue")) {
                    // Fallback when MessagesStorage#getStorageQueue is R8-inlined (e.g. Momogram)
                    String dqDesc = scanner.findClassByPredicate(null, (d, fs, ms) -> {
                        if (!"Ljava/lang/Thread;".equals(scanner.getSuperClass(d))) return false;
                        boolean has1 = false, has2 = false;
                        for (DexScanner.MethodInfo dm : ms) {
                            if (!dm.isStatic && dm.paramDescs.length == 1 && "Ljava/lang/Runnable;".equals(dm.paramDescs[0])
                                    && ("Z".equals(dm.returnDesc) || "V".equals(dm.returnDesc))) {
                                has1 = true;
                            } else if (!dm.isStatic && dm.paramDescs.length == 2
                                    && "Ljava/lang/Runnable;".equals(dm.paramDescs[0]) && "J".equals(dm.paramDescs[1])
                                    && ("Z".equals(dm.returnDesc) || "V".equals(dm.returnDesc))) {
                                has2 = true;
                            }
                        }
                        return has1 && has2;
                    });
                    if (dqDesc != null) {
                        addClass("org.telegram.messenger.DispatchQueue", dqDesc);
                        DexScanner.MethodInfo bestPostRun = null;
                        for (DexScanner.MethodInfo dm : scanner.getMethods(dqDesc)) {
                            if (!dm.isStatic && dm.paramDescs.length == 1 && "Ljava/lang/Runnable;".equals(dm.paramDescs[0])
                                    && ("Z".equals(dm.returnDesc) || "V".equals(dm.returnDesc))) {
                                if ("postRunnable".equals(dm.name)) {
                                    bestPostRun = dm;
                                    break;
                                }
                                if (bestPostRun == null || dm.getInsnsSize() < bestPostRun.getInsnsSize()) {
                                    bestPostRun = dm;
                                }
                            }
                        }
                        if (bestPostRun != null) {
                            addMethod("DispatchQueue", "postRunnable", bestPostRun.name);
                        }
                        String utilDesc = DexScanner.javaToDesc(ClassNames.UTILITIES);
                        if (hasFieldNamed(scanner, utilDesc, "stageQueue")) {
                            addField("Utilities", "stageQueue", "stageQueue");
                        } else {
                            String threadFallback = null;
                            for (DexScanner.FieldInfo wf : scanner.getWrittenStaticFieldsInMethod(utilDesc, "<clinit>", new String[0], utilDesc)) {
                                if (dqDesc.equals(wf.typeDesc)
                                        || scanner.isSubclassOf(wf.typeDesc, dqDesc)
                                        || scanner.isSubclassOf(dqDesc, wf.typeDesc)) {
                                    addField("Utilities", "stageQueue", wf.name);
                                    threadFallback = null;
                                    break;
                                }
                                if (threadFallback == null && scanner.isSubclassOf(wf.typeDesc, "Ljava/lang/Thread;")) {
                                    threadFallback = wf.name;
                                }
                            }
                            if (threadFallback != null) {
                                addField("Utilities", "stageQueue", threadFallback);
                            }
                        }
                    }
                }
            }

            msgCtrlDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "telegramAntispamUserId");
            if (msgCtrlDesc != null) {
                addClass(ClassNames.MESSAGES_CONTROLLER, msgCtrlDesc);
                addMethod("MessagesController", "getInstance",
                        hasMethodNamed(scanner, msgCtrlDesc, "getInstance")
                                ? "getInstance"
                                : scanner.findSingleMethod(msgCtrlDesc, true, msgCtrlDesc, "I"));
                if (msgStorageDesc != null) {
                    String getMs = hasMethodNamed(scanner, baseControllerDesc, "getMessagesStorage")
                            || hasMethodNamed(scanner, msgCtrlDesc, "getMessagesStorage")
                            ? "getMessagesStorage"
                            : scanner.findSingleMethod(baseControllerDesc, false, msgStorageDesc);
                    if (getMs == null) {
                        getMs = scanner.findSingleMethod(msgCtrlDesc, false, msgStorageDesc);
                    }
                    if (getMs != null) {
                        addMethod("MessagesController", "getMessagesStorage", getMs);
                    }
                }
                String tlUserDesc = resolvedDescOrRaw(ClassNames.TLRPC_USER);
                if (tgnetObfuscated && tlObjectDesc != null) {
                    String bestChatMeth = null;
                    String bestChatDesc = null;
                    int bestChatFields = 0;
                    for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
                        if (!m.isStatic && m.paramDescs.length == 1
                                && "Ljava/lang/Long;".equals(m.paramDescs[0])
                                && !m.returnDesc.equals(tlUserDesc)
                                && scanner.isSubclassOf(m.returnDesc, tlObjectDesc)) {
                            int fCnt = scanner.getFields(m.returnDesc).size();
                            if (fCnt >= 40 && fCnt > bestChatFields) {
                                bestChatFields = fCnt;
                                bestChatMeth = m.name;
                                bestChatDesc = m.returnDesc;
                            }
                        }
                    }
                    if (bestChatDesc != null) {
                        addClass(ClassNames.TLRPC_CHAT, bestChatDesc);
                        addMethod("MessagesController", "getChat", bestChatMeth);
                    }
                }
                String tlChatDesc = resolvedDescOrRaw(ClassNames.TLRPC_CHAT);
                addMethod("MessagesController", "isChatNoForwards",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z", tlChatDesc));
                if (!methodMap.containsKey("MessagesController#getChat")) {
                    String getChatBoxed = scanner.findSingleMethod(msgCtrlDesc, false, tlChatDesc, "Ljava/lang/Long;");
                    if (getChatBoxed != null) {
                        addMethod("MessagesController", "getChat", getChatBoxed);
                    } else {
                        String getChatPrim = scanner.findSingleMethod(msgCtrlDesc, false, tlChatDesc, "J");
                        if (getChatPrim != null) {
                            addMethod("MessagesController", "getChat", getChatPrim);
                        }
                    }
                }
                String getUserBoxed = scanner.findSingleMethod(msgCtrlDesc, false, tlUserDesc, "Ljava/lang/Long;");
                if (getUserBoxed != null) {
                    addMethod("MessagesController", "getUser", getUserBoxed);
                } else {
                    String getUserPrim = scanner.findSingleMethod(msgCtrlDesc, false, tlUserDesc, "J");
                    if (getUserPrim != null) {
                        addMethod("MessagesController", "getUser", getUserPrim);
                    }
                }
                addMethod("MessagesController", "processUpdateArray",
                        hasMethodNamed(scanner, msgCtrlDesc, "processUpdateArray")
                                ? "processUpdateArray"
                                : scanner.findSingleMethod(msgCtrlDesc, false, "Z",
                                "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Z", "I"));
                String deleteMeth = hasMethodNamed(scanner, msgCtrlDesc, "deleteMessages")
                        ? "deleteMessages"
                        : scanner.findSingleMethod(msgCtrlDesc, false, "V",
                        "Ljava/util/ArrayList;", "Ljava/util/ArrayList;",
                        tlEncChatDesc,
                        "J", "Z", "I", "Z", "J", tlObjectDesc, "I", "Z", "I");
                if (deleteMeth == null) {
                    DexScanner.MethodInfo bestDel = null;
                    for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 12 && m.paramDescs.length <= 15
                                && "Ljava/util/ArrayList;".equals(m.paramDescs[0])
                                && "Ljava/util/ArrayList;".equals(m.paramDescs[1])
                                && "J".equals(m.paramDescs[3]) && "Z".equals(m.paramDescs[4])
                                && "I".equals(m.paramDescs[5]) && "Z".equals(m.paramDescs[6])
                                && "J".equals(m.paramDescs[7])
                                && (tlObjectDesc.equals(m.paramDescs[8]) || tgnetObfuscated)
                                && "I".equals(m.paramDescs[9]) && "Z".equals(m.paramDescs[10])
                                && "I".equals(m.paramDescs[11])) {
                            if (bestDel == null || m.getInsnsSize() > bestDel.getInsnsSize()) {
                                bestDel = m;
                            }
                        }
                    }
                    if (bestDel != null) {
                        deleteMeth = bestDel.name;
                        if (tgnetObfuscated && !classMap.containsKey(ClassNames.TLRPC_ENCRYPTED_CHAT)) {
                            addClass(ClassNames.TLRPC_ENCRYPTED_CHAT, bestDel.paramDescs[2]);
                        }
                    }
                }
                addMethod("MessagesController", "deleteMessages", deleteMeth);
                String procDiff = hasMethodNamed(scanner, msgCtrlDesc, "processNewDifferenceParams")
                        ? "processNewDifferenceParams"
                        : scanner.findMethodByString(msgCtrlDesc, "APPLY PTS", false, "V", "I", "I", "I");
                if (procDiff == null) {
                    procDiff = scanner.findMethodByString(msgCtrlDesc, "APPLY PTS", false, "V", "I", "I", "I", "I");
                }
                if (procDiff != null) {
                    addMethod("MessagesController", "processNewDifferenceParams", procDiff);
                }
                String inputChanChat = scanner.findSingleMethod(msgCtrlDesc, true, "Lorg/telegram/tgnet/TLRPC$InputChannel;", DexScanner.javaToDesc(ClassNames.TLRPC_CHAT));
                if (inputChanChat != null) {
                    addMethod("MessagesController", "getInputChannel", inputChanChat);
                } else {
                    addMethod("MessagesController", "getInputChannel",
                            scanner.findSingleMethod(msgCtrlDesc, true, "Lorg/telegram/tgnet/TLRPC$InputChannel;", "J"));
                }
                String dialogMsgsByIds = hasFieldNamed(scanner, msgCtrlDesc, "dialogMessagesByIds")
                        ? "dialogMessagesByIds"
                        : scanner.findSingleField(msgCtrlDesc, false, "Landroid/util/SparseArray;");
                if (dialogMsgsByIds == null && deleteMeth != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, deleteMeth, null, msgCtrlDesc)) {
                        if ("Landroid/util/SparseArray;".equals(rf.typeDesc)) {
                            dialogMsgsByIds = rf.name;
                            break;
                        }
                    }
                }
                addField("MessagesController", "dialogMessagesByIds", dialogMsgsByIds);

                // Identify LongSparseArray type and dialogMessage field (raw check -> 2-arg delete helper -> deleteMessages read)
                resolveDialogMessageField(scanner, msgCtrlDesc, deleteMeth);

                resolveMessagesControllerExtras(scanner, msgCtrlDesc);
            }

            String fileLoaderDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "create load operation fileName=");
            if (fileLoaderDesc != null) {
                addClass(ClassNames.FILE_LOADER, fileLoaderDesc);
                addMethod("FileLoader", "getInstance",
                        scanner.findSingleMethod(fileLoaderDesc, true, fileLoaderDesc, "I"));
                DexScanner.MethodInfo bestGetDir = null;
                String imgLocDesc = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(fileLoaderDesc)) {
                    if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && tlMessageDesc.equals(m.paramDescs[0])) {
                        addMethod("FileLoader", "getPathToMessage", m.name);
                    } else if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && tlObjectDesc.equals(m.paramDescs[0])) {
                        addMethod("FileLoader", "getPathToAttach", m.name);
                    } else if (m.isStatic && "Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && "I".equals(m.paramDescs[0])) {
                        if (bestGetDir == null || m.getInsnsSize() > bestGetDir.getInsnsSize()) {
                            bestGetDir = m;
                        }
                    } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                            && "Lorg/telegram/tgnet/TLRPC$Document;".equals(m.paramDescs[0])
                            && "Ljava/lang/Object;".equals(m.paramDescs[1])
                            && "I".equals(m.paramDescs[2]) && "I".equals(m.paramDescs[3])) {
                        addMethod("FileLoader", "loadFileDocument", m.name);
                    } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                            && m.paramDescs[0].startsWith("Lorg/telegram/messenger/")
                            && "Ljava/lang/Object;".equals(m.paramDescs[1])
                            && "Ljava/lang/String;".equals(m.paramDescs[2])
                            && "I".equals(m.paramDescs[3]) && "I".equals(m.paramDescs[4])) {
                        addMethod("FileLoader", "loadFileImage", m.name);
                        imgLocDesc = m.paramDescs[0];
                    }
                }
                if (bestGetDir != null) {
                    addMethod("FileLoader", "getDirectory", bestGetDir.name);
                }
                if (imgLocDesc != null) {
                    addClass(ClassNames.IMAGE_LOCATION, imgLocDesc);
                    String getForPhoto = scanner.findSingleMethod(imgLocDesc, true, imgLocDesc,
                            "Lorg/telegram/tgnet/TLRPC$PhotoSize;", "Lorg/telegram/tgnet/TLRPC$Photo;");
                    if (getForPhoto != null) {
                        addMethod("ImageLocation", "getForPhoto", getForPhoto);
                    }
                }
            }

            // SendMessagesHelper, SendMessageParams, SendingMediaInfo, AccountInstance
            resolveSendMessagesHelper(scanner, baseControllerDesc);

            String notifCtrlDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "show extra notifications chatId ");
            if (notifCtrlDesc != null) {
                addClass(ClassNames.NOTIFICATIONS_CONTROLLER, notifCtrlDesc);
                String lsaDesc = classMap.get(ClassNames.LONG_SPARES_ARRAY) != null
                        ? DexScanner.javaToDesc(classMap.get(ClassNames.LONG_SPARES_ARRAY))
                        : null;
                if (lsaDesc != null) {
                    addMethod("NotificationsController", "removeDeletedMessagesFromNotifications",
                            scanner.findSingleMethod(notifCtrlDesc, false, "V", lsaDesc, "Z"));
                }
            }
        }

        // 4. Other core messenger & UI classes
        String localeCtrlDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "LanguageName");
        if (localeCtrlDesc != null) {
            addClass(ClassNames.LOCALE_CONTROLLER, localeCtrlDesc);
            addMethod("LocaleController", "getInstance",
                    scanner.findSingleMethod(localeCtrlDesc, true, localeCtrlDesc));
            String isRtlField = scanner.findSingleField(localeCtrlDesc, true, "Z");
            if (isRtlField == null && chatActDesc != null) {
                Map<String, Integer> rtlReads = new HashMap<>();
                for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                    for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(chatActDesc, m.name, m.paramDescs, localeCtrlDesc)) {
                        if ("Z".equals(rf.typeDesc)) {
                            Integer c = rtlReads.get(rf.name);
                            rtlReads.put(rf.name, c == null ? 1 : c + 1);
                        }
                    }
                }
                int bestCount = 0;
                for (Map.Entry<String, Integer> e : rtlReads.entrySet()) {
                    if (e.getValue() > bestCount) {
                        bestCount = e.getValue();
                        isRtlField = e.getKey();
                    }
                }
            }
            addField("LocaleController", "isRTL", isRtlField);
            String getCurLocale = scanner.findSingleMethod(localeCtrlDesc, false, "Ljava/util/Locale;");
            if (getCurLocale != null) {
                addMethod("LocaleController", "getCurrentLocale", getCurLocale);
            }
            String curLocaleField = null;
            if (getCurLocale != null) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        localeCtrlDesc, getCurLocale, new String[0], localeCtrlDesc)) {
                    if ("Ljava/util/Locale;".equals(rf.typeDesc)) {
                        curLocaleField = rf.name;
                        break;
                    }
                }
            }
            if (curLocaleField == null) {
                curLocaleField = scanner.findSingleField(localeCtrlDesc, false, "Ljava/util/Locale;");
            }
            addField("LocaleController", "currentLocale", curLocaleField);
            String fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "I", "[I");
            if (fmtShort == null) {
                fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "[I", "I");
            }
            addMethod("LocaleController", "formatShortNumber", fmtShort);

            for (DexScanner.MethodInfo m : scanner.getMethods(localeCtrlDesc)) {
                if (m.isStatic && "Ljava/lang/String;".equals(m.returnDesc)
                        && m.paramDescs.length == 2 && "J".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                        && m.usesString(" ") && m.usesString("LOC_ERR")) {
                    addMethod("LocaleController", "formatYearMont", m.name);
                    break;
                }
            }

            String fastDateFmtDesc = "Lorg/telegram/messenger/time/FastDateFormat;";
            String fmtDay = scanner.findMethodByString(localeCtrlDesc, "formatterDay24H", false, fastDateFmtDesc);
            String fmtDaySec = scanner.findMethodByString(localeCtrlDesc, "formatterDayWithSeconds24H", false, fastDateFmtDesc);
            if (fmtDay == null || fmtDaySec == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(localeCtrlDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("L") && !m.returnDesc.startsWith("Ljava/")) {
                        if (fmtDay == null && m.usesString("formatterDay24H")) {
                            fmtDay = m.name;
                            fastDateFmtDesc = m.returnDesc;
                        } else if (fmtDaySec == null && m.usesString("formatterDayWithSeconds24H")) {
                            fmtDaySec = m.name;
                            fastDateFmtDesc = m.returnDesc;
                        }
                    }
                }
            }
            if (fmtDay != null) {
                addMethod("LocaleController", "getFormatterDay", fmtDay);
            }
            if (fmtDaySec != null) {
                addMethod("LocaleController", "getFormatterDayWithSeconds", fmtDaySec);
            }
            if ((fmtDay != null || fmtDaySec != null) && !scanner.getMethods(fastDateFmtDesc).isEmpty()) {
                addClass(ClassNames.FAST_DATE_FORMAT, fastDateFmtDesc);
                String fmtMeth = scanner.findSingleMethod(fastDateFmtDesc, false, "Ljava/lang/String;", "J");
                if (fmtMeth != null) {
                    addMethod("FastDateFormat", "format", fmtMeth);
                }
            }
        }

        resolveStoryItemHolder(scanner);
        resolveProfileActivity(scanner, msgCtrlDesc);
        resolveDialogsActivityProxy(scanner, baseFragmentDesc, actionBarDesc);
        resolveStoriesController(scanner);

        String sharedConfigDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "mainconfig", "saveIncomingPhotos");
        if (sharedConfigDesc != null) {
            addClass(ClassNames.SHARED_CONFIG, sharedConfigDesc);
            resolveSharedConfigUpdate(scanner, sharedConfigDesc);
        }

        String browserDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null,
                "com.android.browser.application_id", "android.support.customtabs.extra.SESSION");
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null,
                    "com.android.browser.application_id");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "com.android.browser.application_id", "android.support.customtabs.extra.SESSION");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "android.intent.action.VIEW", "https://t.me/");
        }
        if (browserDesc != null) {
            addClass(ClassNames.BROWSER, browserDesc);
            String openUrlMeth = null;
            Map<String, Integer> csVoidCounts = new HashMap<>();
            List<String> csVoidNames = new ArrayList<>();
            for (DexScanner.MethodInfo m : scanner.getMethods(browserDesc)) {
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 2
                        && "Landroid/content/Context;".equals(m.paramDescs[0])
                        && ("Ljava/lang/String;".equals(m.paramDescs[1]) || "Landroid/net/Uri;".equals(m.paramDescs[1]))) {
                    csVoidCounts.put(m.name, csVoidCounts.getOrDefault(m.name, 0) + 1);
                    if (m.paramDescs.length == 2 && "Ljava/lang/String;".equals(m.paramDescs[1])) {
                        if ("openUrl".equals(m.name)) {
                            openUrlMeth = m.name;
                            break;
                        }
                        csVoidNames.add(m.name);
                    }
                }
            }
            if (openUrlMeth == null && csVoidNames.size() == 1) {
                openUrlMeth = csVoidNames.get(0);
            } else if (openUrlMeth == null && !csVoidNames.isEmpty()) {
                for (String cand : csVoidNames) {
                    for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                            browserDesc, cand, new String[]{"Landroid/content/Context;", "Ljava/lang/String;"}, browserDesc)) {
                        if (ref.paramDescs.length == 3
                                && "Landroid/content/Context;".equals(ref.paramDescs[0])
                                && "Ljava/lang/String;".equals(ref.paramDescs[1])
                                && "Z".equals(ref.paramDescs[2])) {
                            openUrlMeth = cand;
                            break;
                        }
                    }
                    if (openUrlMeth != null) break;
                }
                if (openUrlMeth == null) {
                    int bestOverloads = 0;
                    for (String cand : csVoidNames) {
                        int cnt = csVoidCounts.getOrDefault(cand, 0);
                        if (cnt > bestOverloads) {
                            bestOverloads = cnt;
                            openUrlMeth = cand;
                        }
                    }
                }
            }
            if (openUrlMeth != null) {
                addMethod("Browser", "openUrl", openUrlMeth);
            }
        }

        String themeDesc = findTheme(scanner);
        if (themeDesc != null) {
            addClass(ClassNames.THEME, themeDesc);
            addField("Theme", "chat_timePaint",
                    scanner.findSingleField(themeDesc, true, "Landroid/text/TextPaint;"));

            String getThemeMethod = null;
            String themeInfoDesc = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(themeDesc)) {
                if (m.isStatic && m.paramDescs.length == 1
                        && "Ljava/lang/String;".equals(m.paramDescs[0])
                        && m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                        && m.getInsnsSize() > 0 && m.getInsnsSize() < 30) {
                    getThemeMethod = m.name;
                    themeInfoDesc = m.returnDesc;
                    break;
                }
            }
            String infoIsDark = null;
            if (getThemeMethod != null && themeInfoDesc != null) {
                addMethod("Theme", "getTheme", getThemeMethod);
                String applyAnim = scanner.findSingleMethod(themeDesc, true, "V", themeInfoDesc, "Z", "I", "Landroid/view/View;");
                if (applyAnim != null) {
                    addMethod("Theme", "applyThemeAnimated", applyAnim);
                }
                String applyDirect = scanner.findSingleMethod(themeDesc, true, "V", themeInfoDesc, "Z", "Z");
                if (applyDirect != null) {
                    addMethod("Theme", "applyTheme", applyDirect);
                }
                infoIsDark = scanner.findSingleMethod(themeInfoDesc, false, "Z");
                if (infoIsDark == null) {
                    infoIsDark = scanner.findMethodByString(themeInfoDesc, "Dark Blue", false, "Z");
                }
                if (infoIsDark != null) {
                    addMethod("ThemeInfo", "isDark", infoIsDark);
                }
            }

            String isDark = findIsCurrentThemeDark(scanner, themeDesc, themeInfoDesc, infoIsDark);
            if (isDark != null) {
                addMethod("Theme", "isCurrentThemeDark", isDark);
            }
        }

        // 5. Signature-based core classes (NotificationCenter, AndroidUtilities, ActionBarMenuOnItemClick)
        String notifCenterDesc = findNotificationCenter(scanner);
        if (notifCenterDesc != null) {
            addClass(ClassNames.NOTIFICATION_CENTER, notifCenterDesc);
            String notifGetInst = scanner.findSingleMethod(notifCenterDesc, true, notifCenterDesc, "I");
            if (notifGetInst != null) {
                addMethod("NotificationCenter", "getInstance", notifGetInst);
            }
            String bestPost = null;
            int bestPostSize = -1;
            for (DexScanner.MethodInfo m : scanner.getMethods(notifCenterDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "I".equals(m.paramDescs[0]) && "[Ljava/lang/Object;".equals(m.paramDescs[1])) {
                    int sz = m.getInsnsSize();
                    if (sz > bestPostSize) {
                        bestPostSize = sz;
                        bestPost = m.name;
                    }
                }
            }
            if (bestPost != null) {
                addMethod("NotificationCenter", "postNotificationName", bestPost);
            }
            if (hasFieldNamed(scanner, notifCenterDesc, "messagesDeleted")) {
                addField("NotificationCenter", "messagesDeleted", "messagesDeleted");
            } else {
                String mcDesc = classMap.get(ClassNames.MESSAGES_CONTROLLER) != null
                        ? DexScanner.javaToDesc(classMap.get(ClassNames.MESSAGES_CONTROLLER))
                        : null;
                String delMeth = methodMap.get("MessagesController#deleteMessages");
                if (mcDesc != null && delMeth != null) {
                    String lastNotifInt = null;
                    for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(mcDesc, delMeth, null, notifCenterDesc)) {
                        if ("I".equals(rf.typeDesc)) {
                            lastNotifInt = rf.name;
                        }
                    }
                    if (lastNotifInt != null) {
                        addField("NotificationCenter", "messagesDeleted", lastNotifInt);
                    }
                }
            }
            if (hasFieldNamed(scanner, notifCenterDesc, "tlSchemeParseException")) {
                addField("NotificationCenter", "tlSchemeParseException", "tlSchemeParseException");
            } else {
                String tlErrField = scanner.findReadStaticFieldBeforeString(
                        DexScanner.javaToDesc(ClassNames.LAUNCH_ACTIVITY),
                        "didReceivedNotification",
                        new String[]{"I", "I", "[Ljava/lang/Object;"},
                        notifCenterDesc, "I", "TL Error");
                if (tlErrField != null) {
                    addField("NotificationCenter", "tlSchemeParseException", tlErrField);
                }
            }
        }

        String androidUtilsDesc = findAndroidUtilities(scanner);
        if (androidUtilsDesc != null) {
            addClass(ClassNames.ANDROID_UTILITIES, androidUtilsDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(androidUtilsDesc)) {
                if (m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0
                        && !scanner.getReadStaticFieldsInMethod(androidUtilsDesc, m.name, new String[0], androidUtilsDesc).isEmpty()) {
                    addMethod("AndroidUtilities", "isTabletInternal", m.name);
                    break;
                }
            }
        }

        String abMenuClickDesc = findActionBarMenuOnItemClick(scanner);
        if (abMenuClickDesc != null) {
            addClass(ClassNames.ACTION_BAR_MENU_ON_ITEM_CLICK, abMenuClickDesc);
        }

        String fileLoadOpDesc = findFileLoadOperation(scanner);
        if (fileLoadOpDesc != null) {
            addClass(ClassNames.FILE_LOAD_OPERATION, fileLoadOpDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(fileLoadOpDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0 && m.getInsnsSize() > 15 && m.getInsnsSize() < 200) {
                    List<DexScanner.FieldInfo> writes = scanner.getWrittenFieldsInMethod(fileLoadOpDesc, m.name, new String[0], fileLoadOpDesc);
                    int intWrites = 0;
                    for (DexScanner.FieldInfo wf : writes) {
                        if ("I".equals(wf.typeDesc)) intWrites++;
                    }
                    if (intWrites >= 4) {
                        addMethod("FileLoadOperation", "updateParams", m.name);
                        java.util.List<String> uniqInts = new java.util.ArrayList<>();
                        for (DexScanner.FieldInfo wf : writes) {
                            if ("I".equals(wf.typeDesc) && !uniqInts.contains(wf.name)) {
                                uniqInts.add(wf.name);
                            }
                        }
                        if (uniqInts.size() >= 4) {
                            addField("FileLoadOperation", "downloadChunkSizeBig", uniqInts.get(0));
                            addField("FileLoadOperation", "maxDownloadRequests", uniqInts.get(1));
                            addField("FileLoadOperation", "maxDownloadRequestsBig", uniqInts.get(2));
                            addField("FileLoadOperation", "maxCdnParts", uniqInts.get(3));
                        }
                        break;
                    }
                }
            }
        }

        resolveFileUploadOperation(scanner);

        String chatMsgCellDesc = findChatMessageCell(scanner, msgObjDesc);
        if (chatMsgCellDesc != null) {
            addClass(ClassNames.CHAT_MESSAGE_CELL, chatMsgCellDesc);
            if (msgObjDesc != null) {
                DexScanner.MethodInfo measureTimeMeth = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(chatMsgCellDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                            && msgObjDesc.equals(m.paramDescs[0]) && m.readsFieldNamed("date")) {
                        if ("measureTime".equals(m.name)) {
                            measureTimeMeth = m;
                            break;
                        }
                        if (m.getInsnsSize() > 200
                                && (measureTimeMeth == null || m.getInsnsSize() < measureTimeMeth.getInsnsSize())) {
                            measureTimeMeth = m;
                        }
                    }
                }
                if (measureTimeMeth == null) {
                    // Fallback when TLRPC$Message#date is obfuscated (e.g. Nekogram)
                    for (DexScanner.MethodInfo m : scanner.getMethods(chatMsgCellDesc)) {
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                                && msgObjDesc.equals(m.paramDescs[0])
                                && m.getInsnsSize() > 200 && m.getInsnsSize() < 3500) {
                            boolean writesCs = false;
                            int intWrites = 0;
                            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(chatMsgCellDesc, m.name, m.paramDescs, chatMsgCellDesc)) {
                                if ("Ljava/lang/CharSequence;".equals(wf.typeDesc)
                                        || "Landroid/text/SpannableStringBuilder;".equals(wf.typeDesc)) {
                                    writesCs = true;
                                } else if ("I".equals(wf.typeDesc)) intWrites++;
                            }
                            if (writesCs && intWrites >= 2) {
                                if (measureTimeMeth == null || m.getInsnsSize() > measureTimeMeth.getInsnsSize()) {
                                    measureTimeMeth = m;
                                }
                            }
                        }
                    }
                }
                if (measureTimeMeth != null) {
                    addMethod("ChatMessageCell", "measureTime", measureTimeMeth.name);

                    if (themeDesc != null) {
                        Map<String, Integer> paintReads = new HashMap<>();
                        for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(chatMsgCellDesc, measureTimeMeth.name, new String[]{msgObjDesc}, themeDesc)) {
                            if ("Landroid/text/TextPaint;".equals(rf.typeDesc)) {
                                Integer c = paintReads.get(rf.name);
                                paintReads.put(rf.name, c == null ? 1 : c + 1);
                            }
                        }
                        String bestTimePaint = null;
                        int bestPaintCount = 0;
                        for (Map.Entry<String, Integer> pe : paintReads.entrySet()) {
                            if (pe.getValue() > bestPaintCount) {
                                bestPaintCount = pe.getValue();
                                bestTimePaint = pe.getKey();
                            }
                        }
                        if (bestTimePaint != null) {
                            addField("Theme", "chat_timePaint", bestTimePaint);
                        }
                    }

                    String timeStrField = null;
                    List<String> intWriteSeq = new ArrayList<>();
                    Map<String, Integer> intWriteCounts = new HashMap<>();
                    for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(chatMsgCellDesc, measureTimeMeth.name, new String[]{msgObjDesc}, chatMsgCellDesc)) {
                        if (("Ljava/lang/CharSequence;".equals(wf.typeDesc)
                                || "Landroid/text/SpannableStringBuilder;".equals(wf.typeDesc)) && timeStrField == null) {
                            timeStrField = wf.name;
                        } else if ("I".equals(wf.typeDesc)) {
                            Integer c = intWriteCounts.get(wf.name);
                            intWriteCounts.put(wf.name, c == null ? 1 : c + 1);
                            intWriteSeq.add(wf.name);
                        }
                    }
                    if (timeStrField == null) {
                        for (DexScanner.FieldInfo f : scanner.getFields(chatMsgCellDesc)) {
                            if (!f.isStatic && "currentTimeString".equals(f.name)) {
                                timeStrField = f.name;
                                break;
                            }
                        }
                    }
                    if (timeStrField != null) {
                        addField("ChatMessageCell", "currentTimeString", timeStrField);
                    }
                    String resolvedTimeWidth = null;
                    int bestTimeWidthWrites = 0;
                    for (String name : intWriteSeq) {
                        int cnt = intWriteCounts.get(name);
                        if (cnt > bestTimeWidthWrites) {
                            bestTimeWidthWrites = cnt;
                            resolvedTimeWidth = name;
                        }
                    }
                    String resolvedTimeTextWidth = null;
                    if (resolvedTimeWidth != null) {
                        int firstIdx = intWriteSeq.indexOf(resolvedTimeWidth);
                        String nextCand = (firstIdx + 1 < intWriteSeq.size() && !resolvedTimeWidth.equals(intWriteSeq.get(firstIdx + 1)))
                                ? intWriteSeq.get(firstIdx + 1) : null;
                        String prevCand = (firstIdx - 1 >= 0 && !resolvedTimeWidth.equals(intWriteSeq.get(firstIdx - 1)))
                                ? intWriteSeq.get(firstIdx - 1) : null;
                        if (nextCand != null && prevCand != null) {
                            int nextCnt = intWriteCounts.get(nextCand);
                            int prevCnt = intWriteCounts.get(prevCand);
                            resolvedTimeTextWidth = (prevCnt >= 2 && nextCnt < 2) ? prevCand : nextCand;
                        } else if (nextCand != null) {
                            resolvedTimeTextWidth = nextCand;
                        } else if (prevCand != null) {
                            resolvedTimeTextWidth = prevCand;
                        }
                    }
                    if (resolvedTimeTextWidth != null) {
                        addField("ChatMessageCell", "timeTextWidth", resolvedTimeTextWidth);
                    }
                    if (resolvedTimeWidth != null) {
                        addField("ChatMessageCell", "timeWidth", resolvedTimeWidth);
                    }
                }
            }
            if (msgObjDesc != null) {
                DexScanner.MethodInfo getMsgObjM = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(chatMsgCellDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 0 && msgObjDesc.equals(m.returnDesc)) {
                        if (getMsgObjM == null || m.getInsnsSize() < getMsgObjM.getInsnsSize()) {
                            getMsgObjM = m;
                        }
                    }
                }
                if (getMsgObjM != null) {
                    addMethod("ChatMessageCell", "getMessageObject", getMsgObjM.name);
                }
            }
            // Find ChatActivity$ChatMessageCellDelegate implementation on ChatActivity (largest didPressImage)
            boolean resolvedDelegateImpl = false;
            if (chatActDesc != null) {
                for (DexScanner.FieldInfo f : scanner.getFields(chatActDesc)) {
                    if (!f.isStatic && f.typeDesc.startsWith("Lorg/telegram/ui/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/Cells/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/Components/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                        for (DexScanner.MethodInfo m : scanner.getMethods(f.typeDesc)) {
                            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                                    && chatMsgCellDesc.equals(m.paramDescs[0]) && "F".equals(m.paramDescs[1])
                                    && "F".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])
                                    && m.getInsnsSize() > 500) {
                                addClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE, f.typeDesc);
                                addMethod("ChatActivity$ChatMessageCellDelegate", "didPressImage", m.name);
                                resolvedDelegateImpl = true;
                                break;
                            }
                        }
                        if (resolvedDelegateImpl) break;
                    }
                }
            }
            if (!resolvedDelegateImpl) {
                for (DexScanner.FieldInfo f : scanner.getFields(chatMsgCellDesc)) {
                    if (!f.isStatic && f.typeDesc.startsWith("Lorg/telegram/ui/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/Cells/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/Components/")
                            && !f.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                        List<DexScanner.MethodInfo> dm = scanner.getMethods(f.typeDesc);
                        if (dm.size() >= 10) {
                            addClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE, f.typeDesc);
                            break;
                        }
                    }
                }
            }
        }

        // 6. SettingsActivity, SettingCell$Factory, UItem, and legacy DrawerLayoutAdapter
        resolveSettingsClasses(scanner);
        resolveDrawerClasses(scanner, launchActDesc);

        // 7. AlertDialog$Builder & OnButtonClickListener
        resolveAlertDialog(scanner);

        // 8. SQLite classes
        resolveSQLite(scanner);

        // 9. Obfuscated tgnet TL constructors & fields (e.g. Nekogram)
        if (tgnetObfuscated) {
            resolveObfuscatedTgnetConstructors(scanner, tlObjectDesc);
        }

        // Serialize to JSON
        JSONObject root = new JSONObject();
        JSONArray clsArr = new JSONArray();
        for (Map.Entry<String, String> e : classMap.entrySet()) {
            JSONObject o = new JSONObject();
            o.put("o", e.getKey());
            o.put("r", e.getValue());
            clsArr.put(o);
        }
        JSONArray fldArr = new JSONArray();
        for (Map.Entry<String, String> e : fieldMap.entrySet()) {
            String[] parts = e.getKey().split("#", 2);
            JSONObject o = new JSONObject();
            o.put("c", parts[0]);
            o.put("o", parts[1]);
            o.put("r", e.getValue());
            fldArr.put(o);
        }
        JSONArray mthArr = new JSONArray();
        for (Map.Entry<String, String> e : methodMap.entrySet()) {
            String[] parts = e.getKey().split("#", 2);
            JSONObject o = new JSONObject();
            o.put("c", parts[0]);
            o.put("o", parts[1]);
            o.put("r", e.getValue());
            mthArr.put(o);
        }

        root.put("classes", clsArr);
        root.put("fields", fldArr);
        root.put("methods", mthArr);
        return root.toString();
    }

    private void resolveMessageObjectAudio(DexScanner scanner, String msgObjDesc) {
        String docDesc = "Lorg/telegram/tgnet/TLRPC$Document;";
        String msgDesc = DexScanner.javaToDesc(ClassNames.MESSAGE);
        String audioAttrDesc = "Lorg/telegram/tgnet/TLRPC$TL_documentAttributeAudio;";
        List<DexScanner.MethodInfo> methods = scanner.getMethods(msgObjDesc);

        DexScanner.MethodInfo musicDoc = null;
        DexScanner.MethodInfo voiceDoc = null;
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 1 || !docDesc.equals(m.paramDescs[0])) {
                continue;
            }
            if (!m.readsFieldNamed("voice")) continue;
            if (m.usesString("audio/flac") || m.usesString("audio/ogg")) {
                if (musicDoc == null || m.getInsnsSize() < musicDoc.getInsnsSize()) musicDoc = m;
            } else if (m.checksInstanceOf(audioAttrDesc)) {
                if (voiceDoc == null || m.getInsnsSize() < voiceDoc.getInsnsSize()) voiceDoc = m;
            }
        }

        String musicMsg = findSmallestBoolCaller(scanner, msgObjDesc, methods, true, msgDesc,
                musicDoc != null ? musicDoc.name : null);
        String voiceMsg = findSmallestBoolCaller(scanner, msgObjDesc, methods, true, msgDesc,
                voiceDoc != null ? voiceDoc.name : null);

        String isMusicName = findSmallestBoolCaller(scanner, msgObjDesc, methods, false, null, musicMsg);
        if (isMusicName != null) {
            addMethod("MessageObject", "isMusic", isMusicName);
        }
        String isVoiceName = findSmallestBoolCaller(scanner, msgObjDesc, methods, false, null, voiceMsg);
        if (isVoiceName != null) {
            addMethod("MessageObject", "isVoice", isVoiceName);
        }
    }

    private String findSmallestBoolCaller(DexScanner scanner, String classDesc, List<DexScanner.MethodInfo> methods,
                                          boolean isStatic, String singleParamDesc, String targetMethodName) {
        if (targetMethodName == null) return null;
        String[] sig = singleParamDesc != null ? new String[]{singleParamDesc} : new String[0];
        DexScanner.MethodInfo best = null;
        for (DexScanner.MethodInfo m : methods) {
            if (m.isStatic != isStatic || !"Z".equals(m.returnDesc) || !java.util.Arrays.equals(m.paramDescs, sig)) {
                continue;
            }
            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(classDesc, m.name, sig, classDesc)) {
                if (targetMethodName.equals(mr.name)) {
                    if (best == null || m.getInsnsSize() < best.getInsnsSize()) {
                        best = m;
                    }
                    break;
                }
            }
        }
        return best != null ? best.name : null;
    }

    private void resolveSharedConfigUpdate(DexScanner scanner, String sharedConfigDesc) {
        String tlAppUpdateDesc = DexScanner.javaToDesc(ClassNames.TL_HELP_APP_UPDATE);
        String setVer = scanner.findSingleMethod(sharedConfigDesc, true, "Z", tlAppUpdateDesc);
        if (setVer != null) {
            addMethod("SharedConfig", "setNewAppVersionAvailable", setVer);
        }

        String appUpdateField = scanner.findSingleField(sharedConfigDesc, true, tlAppUpdateDesc);
        if (appUpdateField != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(sharedConfigDesc)) {
                if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(sharedConfigDesc, m.name, new String[0], sharedConfigDesc)) {
                    if (appUpdateField.equals(rf.name)) {
                        addMethod("SharedConfig", "isAppUpdateAvailable", m.name);
                        return;
                    }
                }
            }
        }
    }

    private void resolveStoryItemHolder(DexScanner scanner) {
        String storyItemDesc = "Lorg/telegram/tgnet/tl/TL_stories$StoryItem;";
        final String[] allowMethodHolder = new String[1];
        String holderDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            int storyItemFields = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (!f.isStatic && storyItemDesc.equals(f.typeDesc)) {
                    storyItemFields++;
                }
            }
            if (storyItemFields < 2) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.readsFieldNamed("noforwards")) {
                    allowMethodHolder[0] = m.name;
                    return true;
                }
            }
            return false;
        });
        if (holderDesc != null && allowMethodHolder[0] != null) {
            addClass(ClassNames.STORY_ITEM_HOLDER, holderDesc);
            addMethod("PeerStoriesView$StoryItemHolder", "allowScreenshots", allowMethodHolder[0]);
        }
    }

    private void resolveChatPinnedMessageView(DexScanner scanner, String chatActDesc) {
        String createPinnedName = null;
        String pinnedFieldName = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
            List<DexScanner.FieldInfo> writes = scanner.getWrittenFieldsInMethod(chatActDesc, m.name, new String[0], chatActDesc);
            boolean hasNumberTv = false;
            String frameField = null;
            for (DexScanner.FieldInfo wf : writes) {
                if ("Lorg/telegram/ui/Components/NumberTextView;".equals(wf.typeDesc)) {
                    hasNumberTv = true;
                } else if (frameField == null && scanner.isSubclassOf(wf.typeDesc, "Landroid/widget/FrameLayout;")) {
                    frameField = wf.name;
                }
            }
            if (hasNumberTv && frameField != null) {
                createPinnedName = m.name;
                pinnedFieldName = frameField;
                break;
            }
        }
        if (createPinnedName == null) return;

        addMethod("ChatActivity", "createPinnedMessageView", createPinnedName);
        addField("ChatActivity", "pinnedMessageView", pinnedFieldName);

        DexScanner.MethodInfo bestUpdate = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 2) continue;
            boolean matchSig = ("I".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1]))
                    || ("Z".equals(m.paramDescs[0]) && "I".equals(m.paramDescs[1]));
            if (!matchSig) continue;
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(chatActDesc, m.name, m.paramDescs, chatActDesc)) {
                if (pinnedFieldName.equals(rf.name)) {
                    if (bestUpdate == null || m.getInsnsSize() > bestUpdate.getInsnsSize()) {
                        bestUpdate = m;
                    }
                    break;
                }
            }
        }
        if (bestUpdate != null) {
            addMethod("ChatActivity", "updatePinnedMessageView", bestUpdate.name);
        }
    }

    private void resolveMessagesControllerExtras(DexScanner scanner, String msgCtrlDesc) {
        String checkPromo = scanner.findMethodByString(msgCtrlDesc, "proxyDialogAddress", false, "V", "Z");
        if (checkPromo != null) {
            addMethod("MessagesController", "checkPromoInfoInternal", checkPromo);
            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(msgCtrlDesc, checkPromo, new String[]{"Z"}, msgCtrlDesc)) {
                if (mr.paramDescs.length == 0 && "Landroid/content/SharedPreferences;".equals(mr.returnDesc)) {
                    addMethod("MessagesController", "getGlobalMainSettings", mr.name);
                    break;
                }
            }
        }

        String promoDlgField = scanner.findSingleField(msgCtrlDesc, false, "Lorg/telegram/tgnet/TLRPC$Dialog;");
        if (promoDlgField != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(msgCtrlDesc, m.name, new String[0], msgCtrlDesc)) {
                    if (promoDlgField.equals(wf.name)) {
                        addMethod("MessagesController", "removePromoDialog", m.name);
                        break;
                    }
                }
            }
        }

        String userDesc = DexScanner.javaToDesc(ClassNames.TLRPC_USER);
        String storyEntitiesUser = null;
        String entitiesStrField = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
            if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 1
                    && userDesc.equals(m.paramDescs[0])
                    && m.usesString("disabled") && m.usesString("premium") && m.usesString("enabled")) {
                storyEntitiesUser = m.name;
                break;
            }
        }
        if (storyEntitiesUser != null) {
            addMethod("MessagesController", "storyEntitiesAllowed2", storyEntitiesUser);
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, storyEntitiesUser, new String[]{userDesc}, msgCtrlDesc)) {
                if ("Ljava/lang/String;".equals(rf.typeDesc)) {
                    entitiesStrField = rf.name;
                    break;
                }
            }
        }
        for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
            if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0
                    && m.usesString("disabled") && m.usesString("premium") && m.usesString("enabled")) {
                boolean isEntities = false;
                if (entitiesStrField != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, m.name, new String[0], msgCtrlDesc)) {
                        if (entitiesStrField.equals(rf.name)) {
                            isEntities = true;
                            break;
                        }
                    }
                }
                addMethod("MessagesController", isEntities ? "storyEntitiesAllowed" : "storiesEnabled", m.name);
            }
        }
    }

    private void resolveStoriesController(DexScanner scanner) {
        String storiesCtrlDesc = scanner.findClassByStrings(null, null, "StoriesController loadAllStoriesForDialog ");
        if (storiesCtrlDesc == null) {
            storiesCtrlDesc = scanner.findClassByStrings(null, null, "StoriesController cache=");
        }
        if (storiesCtrlDesc == null) return;

        addClass(ClassNames.STORIES_CONTROLLER, storiesCtrlDesc);
        List<DexScanner.MethodInfo> scMethods = scanner.getMethods(storiesCtrlDesc);
        List<String> hasZeroMethods = new ArrayList<>();
        List<String> peerVoidMethods = new ArrayList<>();
        List<String> getPeerMethods = new ArrayList<>();
        Set<String> getPeerSet = new HashSet<>();
        DexScanner.MethodInfo bestHasForDialog = null;
        String storiesListDesc = null;
        String smlDesc = null;

        for (DexScanner.MethodInfo m : scMethods) {
            if (m.isStatic) continue;
            if (m.returnDesc.contains("PeerStories") && m.paramDescs.length == 1 && "J".equals(m.paramDescs[0])) {
                getPeerMethods.add(m.name);
                getPeerSet.add(m.name);
            }
        }

        for (DexScanner.MethodInfo m : scMethods) {
            if (m.isStatic) continue;
            int sz = m.getInsnsSize();
            if ("Z".equals(m.returnDesc)) {
                if (m.paramDescs.length == 0) {
                    boolean readsList = false;
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(storiesCtrlDesc, m.name, new String[0], storiesCtrlDesc)) {
                        if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                            readsList = true;
                            break;
                        }
                    }
                    boolean readsStories = m.readsFieldNamed("stories");
                    if (readsList) {
                        addMethod("StoriesController", "hasStories", m.name);
                        hasZeroMethods.add(m.name);
                    } else if (readsStories) {
                        addMethod("StoriesController", "hasSelfStories", m.name);
                        hasZeroMethods.add(m.name);
                    } else if (sz >= 40) {
                        hasZeroMethods.add(m.name);
                    }
                } else if (m.paramDescs.length == 1 && "J".equals(m.paramDescs[0])) {
                    if (m.readsFieldNamed("stories")
                            && !m.checksInstanceOf("Lorg/telegram/tgnet/TLRPC$TL_messageMediaVideoStream;")) {
                        if (bestHasForDialog == null || sz < bestHasForDialog.getInsnsSize()) {
                            bestHasForDialog = m;
                        }
                    } else if (sz >= 60 && sz <= 90 && !m.readsFieldNamed("stories")) {
                        boolean callsGetPeer = false;
                        for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(storiesCtrlDesc, m.name, m.paramDescs, storiesCtrlDesc)) {
                            if (getPeerSet.contains(mr.name)) {
                                callsGetPeer = true;
                                break;
                            }
                        }
                        if (!callsGetPeer) {
                            addMethod("StoriesController", "canPostStories", m.name);
                        }
                    }
                } else if (m.paramDescs.length == 1 && "Lorg/telegram/tgnet/TLRPC$Chat;".equals(m.paramDescs[0])
                        && "canPostStories".equals(m.name)) {
                    addMethod("StoriesController", "canPostStoriesChat", m.name);
                }
            } else if ("V".equals(m.returnDesc)) {
                if (m.paramDescs.length == 4
                        && m.paramDescs[0].startsWith("L")
                        && "Z".equals(m.paramDescs[1])
                        && "Z".equals(m.paramDescs[2])
                        && "Z".equals(m.paramDescs[3])
                        && sz > 200) {
                    addMethod("StoriesController", "processAllStoriesResponse", m.name);
                    Set<String> scLists = new LinkedHashSet<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(storiesCtrlDesc, m.name, m.paramDescs, storiesCtrlDesc)) {
                        if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                            scLists.add(rf.name);
                        }
                    }
                    if (!scLists.isEmpty()) {
                        addField("StoriesController", "storyListFields", String.join(",", scLists));
                    }
                } else if (m.paramDescs.length == 1 && m.paramDescs[0].contains("TL_updateStory")) {
                    addMethod("StoriesController", "processUpdate", m.name);
                } else if (m.paramDescs.length == 1 && m.paramDescs[0].contains("PeerStories")
                        && sz >= 40 && sz <= 300) {
                    boolean readsList = false;
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(storiesCtrlDesc, m.name, m.paramDescs, storiesCtrlDesc)) {
                        if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                            readsList = true;
                            break;
                        }
                    }
                    if (readsList) {
                        peerVoidMethods.add(m.name);
                    }
                }
            } else if (m.paramDescs.length == 4 && sz > 100 && m.returnDesc.startsWith("L")) {
                int nJ = 0, nI = 0, nZ = 0;
                for (String p : m.paramDescs) {
                    if ("J".equals(p)) nJ++;
                    else if ("I".equals(p)) nI++;
                    else if ("Z".equals(p)) nZ++;
                }
                if (nJ == 1 && nI == 2 && nZ == 1) {
                    storiesListDesc = m.returnDesc;
                }
            }
        }

        if (bestHasForDialog != null) {
            addMethod("StoriesController", "hasStoriesForDialog", bestHasForDialog.name);
        }
        if (!hasZeroMethods.isEmpty()) {
            addMethod("StoriesController", "hasStoriesAll", String.join(",", new LinkedHashSet<>(hasZeroMethods)));
        }
        if (!peerVoidMethods.isEmpty()) {
            addMethod("StoriesController", "peerListAddMethods", String.join(",", new LinkedHashSet<>(peerVoidMethods)));
        }
        if (!getPeerMethods.isEmpty()) {
            addMethod("StoriesController", "getPeerStoriesMethods", String.join(",", new LinkedHashSet<>(getPeerMethods)));
        }

        if (storiesListDesc != null) {
            addClass(ClassNames.STORIES_LIST, storiesListDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(storiesListDesc)) {
                if (m.isStatic) continue;
                int sz = m.getInsnsSize();
                if ("Z".equals(m.returnDesc) && m.paramDescs.length == 3 && sz > 100) {
                    int nZ = 0, nI = 0, nL = 0;
                    for (String p : m.paramDescs) {
                        if ("Z".equals(p)) nZ++;
                        else if ("I".equals(p)) nI++;
                        else if ("Ljava/util/List;".equals(p)) nL++;
                    }
                    if (nZ == 1 && nI == 1 && nL == 1) {
                        addMethod("StoriesList", "load", m.name);
                    }
                } else if ("I".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    List<DexScanner.FieldInfo> rfs = scanner.getReadInstanceFieldsInMethod(storiesListDesc, m.name, new String[0], storiesListDesc);
                    int nBool = 0, nInt = 0, nArr = 0;
                    boolean readsSet = false;
                    for (DexScanner.FieldInfo rf : rfs) {
                        if ("Z".equals(rf.typeDesc)) nBool++;
                        else if ("I".equals(rf.typeDesc)) nInt++;
                        else if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) nArr++;
                        if (rf.typeDesc.contains("Set;")) readsSet = true;
                    }
                    if (nBool >= 2 && nInt >= 1 && nArr >= 1) {
                        addMethod("StoriesList", "getCount", m.name);
                    } else if (sz <= 12 && readsSet) {
                        addMethod("StoriesList", "getLoadedCount", m.name);
                    }
                }
            }

            final String finalSlDesc = storiesListDesc;
            smlDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (!"Landroid/widget/FrameLayout;".equals(scanner.getSuperClass(desc)) || methods.size() < 40) return false;
                for (DexScanner.FieldInfo f : fields) {
                    if (finalSlDesc.equals(f.typeDesc)) return true;
                }
                return false;
            });
            if (smlDesc != null) {
                addClass(ClassNames.SHARED_MEDIA_LAYOUT, smlDesc);
                for (DexScanner.MethodInfo m : scanner.getMethods(smlDesc)) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1
                            || !"Z".equals(m.paramDescs[0]) || m.getInsnsSize() <= 1000) continue;
                    List<String> zc = new ArrayList<>();
                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(smlDesc, m.name, m.paramDescs, smlDesc)) {
                        if ("Z".equals(mr.returnDesc) && mr.paramDescs.length == 0) {
                            zc.add(mr.name);
                        }
                    }
                    if (zc.size() >= 8 && zc.size() <= 15) {
                        String m0 = zc.get(0);
                        String m1 = zc.get(1);
                        if (!m0.equals(m1) && !zc.get(2).equals(m0)) {
                            int m0Count = 0;
                            for (String z : zc) if (m0.equals(z)) m0Count++;
                            boolean repeatedPair = false;
                            for (int i = 3; i + 1 < zc.size(); i++) {
                                if (m0.equals(zc.get(i)) && m1.equals(zc.get(i + 1))) {
                                    repeatedPair = true;
                                    break;
                                }
                            }
                            if (m0Count >= 4 && repeatedPair) {
                                addMethod("SharedMediaLayout", "includeStories", m1);
                                break;
                            }
                        }
                    }
                }

                for (DexScanner.MethodInfo m : scanner.getMethods(smlDesc)) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1
                            || !"Z".equals(m.paramDescs[0]) || m.getInsnsSize() <= 900) continue;
                    String apm = null, bc = null, bst = null;
                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(smlDesc, m.name, m.paramDescs, null)) {
                        if (smlDesc.equals(mr.ownerDesc) && mr.paramDescs.length == 0
                                && ("Ljava/lang/CharSequence;".equals(mr.returnDesc)
                                || "Landroid/text/SpannableStringBuilder;".equals(mr.returnDesc))) {
                            apm = mr.name;
                        } else if (!smlDesc.equals(mr.ownerDesc) && "V".equals(mr.returnDesc)
                                && mr.paramDescs.length == 2
                                && "Ljava/lang/CharSequence;".equals(mr.paramDescs[0])
                                && "Z".equals(mr.paramDescs[1])) {
                            bc = mr.ownerDesc;
                            bst = mr.name;
                        }
                    }
                    if (apm != null && bc != null) {
                        addMethod("SharedMediaLayout", "addPostText", apm);
                        addClass(ClassNames.BUTTON_WITH_COUNTER_VIEW, bc);
                        addMethod("ButtonWithCounterView", "setText", bst);
                        break;
                    }
                }
            }
        }

        final String finalStoriesCtrlDesc = storiesCtrlDesc;
        String dscDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            if (!"Landroid/widget/FrameLayout;".equals(scanner.getSuperClass(desc)) || methods.size() < 20) return false;
            boolean hasSc = false, hasUpd = false;
            for (DexScanner.FieldInfo f : fields) {
                if (finalStoriesCtrlDesc.equals(f.typeDesc)) {
                    hasSc = true;
                    break;
                }
            }
            if (!hasSc) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1]) && m.getInsnsSize() > 200) {
                    hasUpd = true;
                    break;
                }
            }
            return hasUpd;
        });

        if (dscDesc != null) {
            addClass(ClassNames.DIALOG_STORIES_CELL, dscDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(dscDesc)) {
                if (m.isStatic) continue;
                if ("V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1]) && m.getInsnsSize() > 200) {
                    addMethod("DialogStoriesCell", "updateItems", m.name);
                } else if ("setOverscroll".equals(m.name) && "V".equals(m.returnDesc)
                        && m.paramDescs.length == 1 && "F".equals(m.paramDescs[0])) {
                    addMethod("DialogStoriesCell", "setOverscroll", m.name);
                }
            }

            String daJava = classMap.get(ClassNames.DIALOGS_ACTIVITY);
            if (daJava == null) daJava = ClassNames.DIALOGS_ACTIVITY;
            String daDesc = DexScanner.javaToDesc(daJava);
            for (DexScanner.FieldInfo f : scanner.getFields(daDesc)) {
                if (!f.isStatic && scanner.isSubclassOf(f.typeDesc, dscDesc)) {
                    String daDscField = f.name;
                    addField("DialogsActivity", "dialogStoriesCell", daDscField);
                    String visMethodName = null;
                    String autoMethodName = null;
                    String[] autoParamDescs = null;

                    for (DexScanner.MethodInfo m : scanner.getMethods(daDesc)) {
                        if (!m.readsFieldNamed(daDscField)) continue;
                        int sz = m.getInsnsSize();
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                                && "Z".equals(m.paramDescs[0]) && sz > 250 && sz < 900) {
                            visMethodName = m.name;
                            addMethod("DialogsActivity", "updateStoriesVisibility", m.name);
                        } else {
                            int p0 = m.isStatic ? 1 : 0;
                            if (m.isStatic && (m.paramDescs.length < 1 || !daDesc.equals(m.paramDescs[0]))) continue;
                            if ("V".equals(m.returnDesc) && m.paramDescs.length == p0 + 2
                                    && m.paramDescs[p0].startsWith("L") && "F".equals(m.paramDescs[p0 + 1])
                                    && sz > 30 && sz < 150) {
                                addMethod("DialogsActivity", "setStoriesOvercroll", m.name);
                            } else if ("Z".equals(m.returnDesc) && m.paramDescs.length == p0 + 1
                                    && m.paramDescs[p0].startsWith("L")
                                    && sz > 50 && sz < 200) {
                                autoMethodName = m.name;
                                autoParamDescs = m.paramDescs;
                                addMethod("DialogsActivity", "checkAutoscrollToStories", m.name);
                            }
                        }
                    }

                    if (visMethodName != null) {
                        String getSc = null;
                        String maxScroll = null;
                        for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(daDesc, visMethodName, new String[]{"Z"}, daDesc)) {
                            if (getSc == null && storiesCtrlDesc.equals(mr.returnDesc) && mr.paramDescs.length == 0) {
                                getSc = mr.name;
                            } else if (maxScroll == null && "I".equals(mr.returnDesc) && mr.paramDescs.length == 0) {
                                maxScroll = mr.name;
                            }
                        }
                        if (getSc != null) {
                            addMethod("DialogsActivity", "getStoriesController", getSc);
                        }

                        String maxScrollNoSearch = null;
                        if (autoMethodName != null && autoParamDescs != null) {
                            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(daDesc, autoMethodName, autoParamDescs, daDesc)) {
                                if ("I".equals(mr.returnDesc) && mr.paramDescs.length == 0 && !mr.name.equals(maxScroll)) {
                                    maxScrollNoSearch = mr.name;
                                    break;
                                }
                            }
                        }
                        if (maxScrollNoSearch == null && maxScroll != null) {
                            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(daDesc, maxScroll, new String[0], daDesc)) {
                                if ("I".equals(mr.returnDesc) && mr.paramDescs.length == 0 && !mr.name.equals(maxScroll)) {
                                    maxScrollNoSearch = mr.name;
                                    break;
                                }
                            }
                        }

                        String hasStFld = null;
                        if (maxScrollNoSearch != null) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(daDesc, maxScrollNoSearch, new String[0], daDesc)) {
                                if ("Z".equals(rf.typeDesc)) {
                                    hasStFld = rf.name;
                                    break;
                                }
                            }
                            if (hasStFld != null) {
                                addMethod("DialogsActivity", "getMaxScrollYOffsetWithoutSearch", maxScrollNoSearch);
                                addField("DialogsActivity", "hasStories", hasStFld);
                            }
                        }
                        if (hasStFld == null && maxScroll != null) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(daDesc, maxScroll, new String[0], daDesc)) {
                                if ("Z".equals(rf.typeDesc)) {
                                    hasStFld = rf.name;
                                    break;
                                }
                            }
                            if (hasStFld != null) {
                                addField("DialogsActivity", "hasStories", hasStFld);
                            }
                        }
                        if (hasStFld == null) {
                            for (DexScanner.FieldInfo df : scanner.getFields(daDesc)) {
                                if (!df.isStatic && "Z".equals(df.typeDesc) && "hasStories".equals(df.name)) {
                                    addField("DialogsActivity", "hasStories", "hasStories");
                                    break;
                                }
                            }
                        }
                    }
                    break;
                }
            }

            // Resolve floatingButtonStories, updateFloatingButtonVisibility(Z)V, FragmentFloatingButton.setButtonVisible(Z,Z)V
            for (DexScanner.MethodInfo m : scanner.getMethods(daDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1
                        || !"Z".equals(m.paramDescs[0])) continue;
                int sz = m.getInsnsSize();
                if (sz <= 20 || sz >= 180) continue;

                Map<String, Integer> zzCounts = new HashMap<>();
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(daDesc, m.name, m.paramDescs, null)) {
                    if ("V".equals(mr.returnDesc) && mr.paramDescs.length == 2
                            && "Z".equals(mr.paramDescs[0]) && "Z".equals(mr.paramDescs[1])) {
                        String key = mr.ownerDesc + "#" + mr.name;
                        zzCounts.put(key, zzCounts.getOrDefault(key, 0) + 1);
                    }
                }

                boolean foundFab = false;
                for (Map.Entry<String, Integer> e : zzCounts.entrySet()) {
                    if (e.getValue() < 2) continue;
                    String[] parts = e.getKey().split("#", 2);
                    String fabTypeDesc = parts[0];
                    String setVisName = parts[1];
                    List<String> flds = new ArrayList<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(daDesc, m.name, m.paramDescs, daDesc)) {
                        if (fabTypeDesc.equals(rf.typeDesc) && !flds.contains(rf.name)) {
                            flds.add(rf.name);
                        }
                    }
                    if (flds.size() >= 2) {
                        String fabStoriesField = flds.contains("floatingButtonStories") ? "floatingButtonStories" : flds.get(1);
                        addField("DialogsActivity", "floatingButtonStories", fabStoriesField);
                        addMethod("DialogsActivity", "updateFloatingButtonVisibility", m.name);
                        addMethod("FragmentFloatingButton", "setButtonVisible", setVisName);
                        foundFab = true;
                        break;
                    }
                }
                if (foundFab) break;
            }
        }

        String psvDesc = DexScanner.javaToDesc(ClassNames.PROFILE_STORIES_VIEW);
        List<DexScanner.MethodInfo> psvMethods = scanner.getMethods(psvDesc);
        if (!psvMethods.isEmpty()) {
            addClass(ClassNames.PROFILE_STORIES_VIEW, psvDesc);
            for (DexScanner.MethodInfo m : psvMethods) {
                if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                if (m.paramDescs.length == 2 && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                        && m.getInsnsSize() > 300) {
                    addMethod("ProfileStoriesView", "updateStories", m.name);
                } else if (m.paramDescs.length == 1 && m.paramDescs[0].contains("PeerStories")) {
                    addMethod("ProfileStoriesView", "setStories", m.name);
                }
            }
        }

        String suDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                        && "J".equals(m.paramDescs[0])
                        && "Landroid/graphics/Canvas;".equals(m.paramDescs[1])
                        && m.paramDescs[2].startsWith("L")
                        && "Z".equals(m.paramDescs[3])
                        && m.paramDescs[4].startsWith("L")
                        && m.getInsnsSize() > 200) {
                    return true;
                }
            }
            return false;
        });
        if (suDesc != null) {
            addClass(ClassNames.STORIES_UTILITIES, suDesc);
            String paramsDesc = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(suDesc)) {
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                        && "J".equals(m.paramDescs[0])
                        && "Landroid/graphics/Canvas;".equals(m.paramDescs[1])
                        && m.paramDescs[2].startsWith("L")
                        && "Z".equals(m.paramDescs[3])
                        && m.paramDescs[4].startsWith("L")
                        && m.getInsnsSize() > 200) {
                    addMethod("StoriesUtilities", "drawAvatarWithStory", m.name);
                    paramsDesc = m.paramDescs[4];
                    addClass(ClassNames.AVATAR_STORY_PARAMS, paramsDesc);
                    List<String> zOrder = new ArrayList<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(suDesc, m.name, m.paramDescs, paramsDesc)) {
                        if ("Z".equals(rf.typeDesc) && !zOrder.contains(rf.name)) {
                            zOrder.add(rf.name);
                        }
                    }
                    String isDsc = zOrder.contains("isDialogStoriesCell")
                            ? "isDialogStoriesCell"
                            : (zOrder.size() >= 2 ? zOrder.get(1) : null);
                    addField("AvatarStoryParams", "isDialogStoriesCell", isDsc);
                    break;
                }
            }
            if (paramsDesc != null) {
                for (DexScanner.MethodInfo tm : scanner.getMethods(paramsDesc)) {
                    if (!tm.isStatic && "Z".equals(tm.returnDesc) && tm.paramDescs.length == 2) {
                        boolean hasME = "Landroid/view/MotionEvent;".equals(tm.paramDescs[0])
                                || "Landroid/view/MotionEvent;".equals(tm.paramDescs[1]);
                        boolean hasV = "Landroid/view/View;".equals(tm.paramDescs[0])
                                || "Landroid/view/View;".equals(tm.paramDescs[1]);
                        if (hasME && hasV) {
                            addMethod("AvatarStoryParams", "checkOnTouchEvent", tm.name);
                            break;
                        }
                    }
                }
            }
        }

        resolveProfileStoriesRows(scanner, smlDesc);
    }

    private void resolveProfileStoriesRows(DexScanner scanner, String smlDesc) {
        String profileDesc = DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY);

        String updRows = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0
                    && m.readsFieldNamed("common_chats_count")
                    && m.readsFieldNamed("stories_pinned_available")) {
                updRows = m.name;
                addMethod("ProfileActivity", "updateRowsIds", updRows);
                break;
            }
        }

        addField("ProfileActivity", "userInfo",
                scanner.findSingleField(profileDesc, false, "Lorg/telegram/tgnet/TLRPC$UserFull;"));
        addField("ProfileActivity", "chatInfo",
                scanner.findSingleField(profileDesc, false, "Lorg/telegram/tgnet/TLRPC$ChatFull;"));

        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if ("V".equals(m.returnDesc) && m.usesString("HideStoriesFromAlbum") && m.usesString("ArchiveStories")) {
                addMethod("ProfileActivity", "checkStoriesButtonText", m.name);
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(profileDesc, m.name, m.paramDescs, profileDesc)) {
                    if (rf.typeDesc.startsWith("[L")) {
                        addField("ProfileActivity", "bottomButton", rf.name);
                        break;
                    }
                }
                break;
            }
        }

        String smRow = null;
        for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(profileDesc, "onBackPressed", new String[]{"Z"}, profileDesc)) {
            if ("I".equals(rf.typeDesc)) {
                smRow = rf.name;
                break;
            }
        }
        if (smRow == null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(profileDesc, "isSwipeBackEnabled", new String[]{"Landroid/view/MotionEvent;"}, profileDesc)) {
                if ("I".equals(rf.typeDesc)) {
                    smRow = rf.name;
                    break;
                }
            }
        }
        addField("ProfileActivity", "sharedMediaRow", smRow);

        if (updRows != null && smRow != null) {
            List<DexScanner.FieldInfo> wf = scanner.getWrittenFieldsInMethod(profileDesc, updRows, new String[0], profileDesc);
            Map<String, Integer> counts = new HashMap<>();
            int lastSmIdx = -1;
            for (int i = 0; i < wf.size(); i++) {
                DexScanner.FieldInfo f = wf.get(i);
                if ("I".equals(f.typeDesc)) {
                    counts.put(f.name, counts.getOrDefault(f.name, 0) + 1);
                    if (smRow.equals(f.name)) lastSmIdx = i;
                }
            }
            String rowCountFld = null;
            int maxCnt = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > maxCnt) {
                    maxCnt = e.getValue();
                    rowCountFld = e.getKey();
                }
            }
            if (lastSmIdx >= 0) {
                for (int i = lastSmIdx + 1; i < wf.size(); i++) {
                    DexScanner.FieldInfo f = wf.get(i);
                    if ("I".equals(f.typeDesc) && !f.name.equals(rowCountFld) && !f.name.equals(smRow)) {
                        addField("ProfileActivity", "bottomPaddingRow", f.name);
                        break;
                    }
                }
            }
        }

        if (smlDesc != null) {
            final String fsml = smlDesc;
            String subSml = null;
            for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
                if (!f.isStatic && !f.typeDesc.equals(fsml) && scanner.isSubclassOf(f.typeDesc, fsml)) {
                    subSml = f.typeDesc;
                    break;
                }
            }
            if (subSml == null) {
                subSml = scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fields, methods) -> {
                    if (!fsml.equals(scanner.getSuperClass(desc))) return false;
                    for (DexScanner.FieldInfo f : fields) {
                        if (profileDesc.equals(f.typeDesc)) return true;
                    }
                    return false;
                });
            }
            if (subSml != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(subSml)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.getInsnsSize() <= 10) {
                        for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(subSml, m.name, new String[0], profileDesc)) {
                            if ("Z".equals(rf.typeDesc)) {
                                addField("ProfileActivity", "myProfile", rf.name);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void resolveProfileActivity(DexScanner scanner, String msgCtrlDesc) {
        String simpleTextViewDesc = scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", (desc, fields, methods) -> {
            if (!"Landroid/view/View;".equals(scanner.getSuperClass(desc))) return false;
            boolean hasGetText = false, hasSetText1 = false, hasSetText2 = false, hasSetMaxLines = false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic) continue;
                if ("Ljava/lang/CharSequence;".equals(m.returnDesc) && m.paramDescs.length == 0) hasGetText = true;
                else if ("Z".equals(m.returnDesc) && m.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) hasSetText1 = true;
                else if ("Z".equals(m.returnDesc) && m.paramDescs.length == 2 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) hasSetText2 = true;
                else if ("setMaxLines".equals(m.name) && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) hasSetMaxLines = true;
            }
            return hasGetText && hasSetText1 && hasSetText2 && hasSetMaxLines;
        });

        if (simpleTextViewDesc != null) {
            addClass("org.telegram.ui.ActionBar.SimpleTextView", simpleTextViewDesc);
            String getTextM = null;
            String getPaintM = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(simpleTextViewDesc)) {
                if (m.isStatic) continue;
                if (getTextM == null && "getText".equals(m.name) && "Ljava/lang/CharSequence;".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    getTextM = m.name;
                }
                if ("Landroid/text/TextPaint;".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    if ("getTextPaint".equals(m.name)) {
                        getPaintM = m.name;
                    } else if (getPaintM == null || "getPaint".equals(m.name)) {
                        getPaintM = m.name;
                    }
                }
            }
            if (getTextM == null) {
                getTextM = scanner.findSingleMethod(simpleTextViewDesc, false, "Ljava/lang/CharSequence;");
            }
            addMethod("SimpleTextView", "getText", getTextM);
            addMethod("SimpleTextView", "setText",
                    scanner.findSingleMethod(simpleTextViewDesc, false, "Z", "Ljava/lang/CharSequence;"));
            addMethod("SimpleTextView", "setTextForce",
                    scanner.findSingleMethod(simpleTextViewDesc, false, "Z", "Ljava/lang/CharSequence;", "Z"));
            addMethod("SimpleTextView", "setMaxLines", "setMaxLines");
            if (getPaintM != null) {
                addMethod("SimpleTextView", "getTextPaint", getPaintM);
            }
        }

        String profileDesc = DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY);
        List<DexScanner.MethodInfo> profileMethods = scanner.getMethods(profileDesc);
        DexScanner.MethodInfo bestUpdateData = null;
        DexScanner.MethodInfo fallbackOnlineCount = null;
        for (DexScanner.MethodInfo m : profileMethods) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1 || !"Z".equals(m.paramDescs[0])) continue;
            if (m.readsFieldNamed("participants_count")) {
                if (bestUpdateData == null || m.getInsnsSize() > bestUpdateData.getInsnsSize()) {
                    bestUpdateData = m;
                }
            } else if (m.readsFieldNamed("online_count")) {
                if (fallbackOnlineCount == null || m.getInsnsSize() > fallbackOnlineCount.getInsnsSize()) {
                    fallbackOnlineCount = m;
                }
            }
        }
        if (bestUpdateData == null) bestUpdateData = fallbackOnlineCount;
        if (bestUpdateData != null) {
            addMethod("ProfileActivity", "updateProfileData", bestUpdateData.name);
            if (msgCtrlDesc != null) {
                String getUserM = methodMap.get("MessagesController#getUser");
                if (getUserM != null) {
                    addField("ProfileActivity", "userId",
                            scanner.findReadFieldBeforeCall(profileDesc, bestUpdateData.name, new String[]{"Z"}, "J", msgCtrlDesc, getUserM));
                }
                String getChatM = methodMap.get("MessagesController#getChat");
                if (getChatM != null) {
                    addField("ProfileActivity", "chatId",
                            scanner.findReadFieldBeforeCall(profileDesc, bestUpdateData.name, new String[]{"Z"}, "J", msgCtrlDesc, getChatM));
                }
            }
        }

        if (simpleTextViewDesc != null) {
            List<DexScanner.FieldInfo> stvFields = new ArrayList<>();
            for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
                if (!f.isStatic && scanner.isSubclassOf(f.typeDesc, simpleTextViewDesc)) {
                    stvFields.add(f);
                }
            }
            String onlineField = null;
            String updateOnlineMethod = null;
            String needLayoutMethod = null;
            if (!stvFields.isEmpty()) {
                for (DexScanner.MethodInfo m : profileMethods) {
                    if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                    boolean okSig = m.paramDescs.length == 0
                            || (m.paramDescs.length == 1 && "Z".equals(m.paramDescs[0]));
                    if (!okSig) continue;
                    int sz = m.getInsnsSize();
                    if (sz <= 0 || sz >= 150) continue;

                    List<DexScanner.FieldInfo> reads = scanner.getReadInstanceFieldsInMethod(profileDesc, m.name, m.paramDescs, profileDesc);
                    DexScanner.FieldInfo matchedField = null;
                    for (DexScanner.FieldInfo rf : reads) {
                        for (DexScanner.FieldInfo sf : stvFields) {
                            if (sf.name.equals(rf.name)) {
                                matchedField = sf;
                                break;
                            }
                        }
                        if (matchedField != null) break;
                    }
                    if (matchedField == null) continue;

                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(profileDesc, m.name, m.paramDescs, null)) {
                        if ("Z".equals(mr.returnDesc) && mr.paramDescs.length == 2
                                && "Ljava/lang/CharSequence;".equals(mr.paramDescs[0]) && "Z".equals(mr.paramDescs[1])
                                && scanner.isSubclassOf(matchedField.typeDesc, mr.ownerDesc)) {
                            onlineField = matchedField.name;
                            updateOnlineMethod = m.name;
                            for (DexScanner.MethodRef mr2 : scanner.getCalledMethodsInMethod(profileDesc, m.name, m.paramDescs, profileDesc)) {
                                if ("V".equals(mr2.returnDesc) && mr2.paramDescs.length == 1 && "Z".equals(mr2.paramDescs[0])) {
                                    needLayoutMethod = mr2.name;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                    if (onlineField != null) break;
                }
                if (onlineField != null) {
                    addField("ProfileActivity", "onlineTextView", onlineField);
                    addMethod("ProfileActivity", "updateOnlineTextView", updateOnlineMethod);
                    String nameField = null;
                    for (DexScanner.FieldInfo sf : stvFields) {
                        if (!onlineField.equals(sf.name) && "nameTextView".equals(sf.name)) {
                            nameField = sf.name;
                            break;
                        }
                    }
                    if (nameField == null && bestUpdateData != null) {
                        for (DexScanner.FieldInfo sf : stvFields) {
                            if (!onlineField.equals(sf.name) && bestUpdateData.readsFieldNamed(sf.name)) {
                                nameField = sf.name;
                                break;
                            }
                        }
                    }
                    if (nameField == null) {
                        for (DexScanner.FieldInfo sf : stvFields) {
                            if (!onlineField.equals(sf.name)) {
                                nameField = sf.name;
                                break;
                            }
                        }
                    }
                    if (nameField != null) {
                        addField("ProfileActivity", "nameTextView", nameField);
                    }
                }
            }
            if (onlineField == null) {
                String arrDesc = "[" + simpleTextViewDesc;
                List<String> arrNames = new ArrayList<>();
                for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
                    if (!f.isStatic && arrDesc.equals(f.typeDesc)) {
                        arrNames.add(f.name);
                    }
                }
                if (arrNames.size() == 2) {
                    if (arrNames.contains("nameTextView") && arrNames.contains("onlineTextView")) {
                        addField("ProfileActivity", "nameTextView", "nameTextView");
                        addField("ProfileActivity", "onlineTextView", "onlineTextView");
                    } else {
                        addField("ProfileActivity", "nameTextView", arrNames.get(0));
                        addField("ProfileActivity", "onlineTextView", arrNames.get(1));
                    }
                }
            }
            if (needLayoutMethod == null && bestUpdateData != null) {
                int bestSize = 1000;
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(profileDesc, bestUpdateData.name, new String[]{"Z"}, profileDesc)) {
                    if ("V".equals(mr.returnDesc) && mr.paramDescs.length == 1 && "Z".equals(mr.paramDescs[0])) {
                        for (DexScanner.MethodInfo pm : profileMethods) {
                            if (!pm.isStatic && pm.name.equals(mr.name) && pm.paramDescs.length == 1 && "Z".equals(pm.paramDescs[0])) {
                                int sz = pm.getInsnsSize();
                                if (sz > bestSize) {
                                    bestSize = sz;
                                    needLayoutMethod = pm.name;
                                }
                                break;
                            }
                        }
                    }
                }
            }
            if (needLayoutMethod != null) {
                addMethod("ProfileActivity", "needLayout", needLayoutMethod);
            }
        }
    }

    private void resolveSendMessagesHelper(DexScanner scanner, String baseControllerDesc) {
        String smhDesc = scanner.findClassByPredicate("Lorg/telegram/messenger/", (desc, fields, methods) -> {
            if (methods.size() < 80) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length >= 6
                        && "Ljava/util/ArrayList;".equals(m.paramDescs[0]) && "J".equals(m.paramDescs[1])
                        && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                    return true;
                }
            }
            return false;
        });
        if (smhDesc == null) return;

        addClass(ClassNames.SEND_MESSAGES_HELPER, smhDesc);
        String getInst = scanner.findSingleMethod(smhDesc, true, smhDesc, "I");
        if (getInst != null) addMethod("SendMessagesHelper", "getInstance", getInst);

        DexScanner.MethodInfo fwdMethod = null;
        DexScanner.MethodInfo sendParamsMethod = null;
        DexScanner.MethodInfo prepPhoto = null;
        DexScanner.MethodInfo prepDoc = null;
        DexScanner.MethodInfo prepMedia = null;
        String accInstDesc = null;

        for (DexScanner.MethodInfo m : scanner.getMethods(smhDesc)) {
            if (!m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length >= 6
                    && "Ljava/util/ArrayList;".equals(m.paramDescs[0]) && "J".equals(m.paramDescs[1])
                    && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                if (fwdMethod == null || m.getInsnsSize() > fwdMethod.getInsnsSize()) {
                    fwdMethod = m;
                }
            } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                    && m.paramDescs[0].startsWith("Lorg/telegram/messenger/") && m.getInsnsSize() > 400) {
                if (sendParamsMethod == null || m.getInsnsSize() > sendParamsMethod.getInsnsSize()) {
                    sendParamsMethod = m;
                }
            } else if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 18
                    && "Ljava/lang/String;".equals(m.paramDescs[1])
                    && "Ljava/lang/String;".equals(m.paramDescs[2])
                    && "Landroid/net/Uri;".equals(m.paramDescs[3])
                    && "J".equals(m.paramDescs[4])) {
                if (prepPhoto == null || m.paramDescs.length > prepPhoto.paramDescs.length) {
                    prepPhoto = m;
                    accInstDesc = m.paramDescs[0];
                }
            } else if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 12
                    && "Ljava/lang/String;".equals(m.paramDescs[1])
                    && "Ljava/lang/String;".equals(m.paramDescs[2])
                    && "Landroid/net/Uri;".equals(m.paramDescs[3])
                    && "Ljava/lang/String;".equals(m.paramDescs[4])
                    && "Ljava/lang/String;".equals(m.paramDescs[5])
                    && "J".equals(m.paramDescs[6])) {
                prepDoc = m;
            } else if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 20
                    && "Ljava/util/ArrayList;".equals(m.paramDescs[1])
                    && "J".equals(m.paramDescs[2])) {
                prepMedia = m;
            }
        }

        if (fwdMethod != null) addMethod("SendMessagesHelper", "sendMessageForward", fwdMethod.name);
        if (prepPhoto != null) addMethod("SendMessagesHelper", "prepareSendingPhoto", prepPhoto.name);
        if (prepDoc != null) addMethod("SendMessagesHelper", "prepareSendingDocument", prepDoc.name);
        if (prepMedia != null) addMethod("SendMessagesHelper", "prepareSendingMedia", prepMedia.name);

        String tlEditDesc = "Lorg/telegram/tgnet/TLRPC$TL_messages_editMessage;";
        DexScanner.MethodInfo editMsgMethod = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(smhDesc)) {
            if (!m.isStatic && m.getInsnsSize() > 100) {
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(smhDesc, m.name, m.paramDescs, tlEditDesc)) {
                    if ("message".equals(wf.name)) {
                        if (editMsgMethod == null || m.getInsnsSize() > editMsgMethod.getInsnsSize()) {
                            editMsgMethod = m;
                        }
                        break;
                    }
                }
            }
        }
        if (editMsgMethod != null) addMethod("SendMessagesHelper", "editMessage", editMsgMethod.name);

        if (accInstDesc != null) {
            addClass(ClassNames.ACCOUNT_INSTANCE, accInstDesc);
            String accGetInst = scanner.findSingleMethod(accInstDesc, true, accInstDesc, "I");
            if (accGetInst != null) addMethod("AccountInstance", "getInstance", accGetInst);
        }

        if (sendParamsMethod != null) {
            addMethod("SendMessagesHelper", "sendMessageParams", sendParamsMethod.name);
            String paramsDesc = sendParamsMethod.paramDescs[0];
            addClass(ClassNames.SEND_MESSAGE_PARAMS, paramsDesc);
            DexScanner.MethodInfo masterFactory = null;
            for (DexScanner.MethodInfo pm : scanner.getMethods(paramsDesc)) {
                if (pm.isStatic && paramsDesc.equals(pm.returnDesc) && pm.paramDescs.length >= 8) {
                    if (pm.paramDescs.length >= 20 && (masterFactory == null || pm.paramDescs.length > masterFactory.paramDescs.length)) {
                        masterFactory = pm;
                    }
                    if ("Ljava/lang/String;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1]) && pm.paramDescs.length <= 16) {
                        addMethod("SendMessageParams", "ofText", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$MessageMedia;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1])) {
                        addMethod("SendMessageParams", "ofMedia", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$User;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1])) {
                        addMethod("SendMessageParams", "ofUser", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$TL_document;".equals(pm.paramDescs[0]) && pm.paramDescs.length >= 16) {
                        addMethod("SendMessageParams", "ofDocument", pm.name);
                    }
                }
            }
            if (masterFactory != null) {
                String msgObjDesc = classMap.get(ClassNames.MESSAGE_OBJECT) != null
                        ? DexScanner.javaToDesc(classMap.get(ClassNames.MESSAGE_OBJECT))
                        : null;
                List<String> writtenStrs = new ArrayList<>();
                List<String> writtenMsgObjs = new ArrayList<>();
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(paramsDesc, masterFactory.name, masterFactory.paramDescs, paramsDesc)) {
                    if ("Ljava/lang/String;".equals(wf.typeDesc) && !writtenStrs.contains(wf.name)) {
                        writtenStrs.add(wf.name);
                    } else if (msgObjDesc != null && msgObjDesc.equals(wf.typeDesc) && !writtenMsgObjs.contains(wf.name)) {
                        writtenMsgObjs.add(wf.name);
                    }
                }
                if (writtenStrs.size() >= 1) addField("SendMessageParams", "message", writtenStrs.get(0));
                if (writtenStrs.size() >= 2) addField("SendMessageParams", "caption", writtenStrs.get(1));
                if (writtenMsgObjs.size() >= 3) addField("SendMessageParams", "editingMessageObject", writtenMsgObjs.get(2));
            }
        }

        String smiDesc = null;
        if (prepPhoto != null) {
            for (Map.Entry<String, List<DexScanner.FieldInfo>> e :
                    scanner.getWrittenFieldsGroupedByOwner(smhDesc, prepPhoto.name, prepPhoto.paramDescs).entrySet()) {
                String owner = e.getKey();
                if (owner.equals(smhDesc) || !owner.startsWith("Lorg/telegram/messenger/")) continue;
                int strs = 0, lists = 0;
                for (DexScanner.FieldInfo f : e.getValue()) {
                    if ("Ljava/lang/String;".equals(f.typeDesc)) strs++;
                    else if ("Ljava/util/ArrayList;".equals(f.typeDesc)) lists++;
                }
                if (strs >= 2 && lists >= 1) {
                    smiDesc = owner;
                    break;
                }
            }
        }
        if (smiDesc == null) {
            smiDesc = scanner.findClassByPredicate("Lorg/telegram/messenger/", (d, fs, ms) -> {
                int strs = 0, bools = 0, lists = 0;
                for (DexScanner.FieldInfo f : fs) {
                    if (!f.isStatic) {
                        if ("Ljava/lang/String;".equals(f.typeDesc)) strs++;
                        else if ("Z".equals(f.typeDesc)) bools++;
                        else if ("Ljava/util/ArrayList;".equals(f.typeDesc)) lists++;
                    }
                }
                return strs >= 3 && bools >= 3 && lists >= 1 && fs.size() >= 10 && fs.size() <= 35 && ms.size() <= 10;
            });
        }
        if (smiDesc != null) {
            addClass(ClassNames.SENDING_MEDIA_INFO, smiDesc);
            if (prepPhoto != null) {
                List<String> writtenStrs = new ArrayList<>();
                String firstList = null;
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(smhDesc, prepPhoto.name, prepPhoto.paramDescs, smiDesc)) {
                    if ("Ljava/lang/String;".equals(wf.typeDesc)) {
                        writtenStrs.add(wf.name);
                    } else if ("Ljava/util/ArrayList;".equals(wf.typeDesc) && firstList == null) {
                        firstList = wf.name;
                    }
                }
                if (writtenStrs.size() >= 1) addField("SendingMediaInfo", "path", writtenStrs.get(0));
                if (writtenStrs.size() >= 3) addField("SendingMediaInfo", "caption", writtenStrs.get(2));
                if (firstList != null) addField("SendingMediaInfo", "entities", firstList);
            }
            String isVideoField = null;
            for (DexScanner.FieldInfo f : scanner.getFields(smiDesc)) {
                if (!f.isStatic && "Z".equals(f.typeDesc) && "isVideo".equals(f.name)) {
                    isVideoField = f.name;
                    break;
                }
            }
            if (isVideoField == null) {
                int bestZCount = 0;
                boolean bestIsStatic = false;
                for (DexScanner.MethodInfo m : scanner.getMethods(smhDesc)) {
                    List<String> writtenZs = new ArrayList<>();
                    for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(smhDesc, m.name, m.paramDescs, smiDesc)) {
                        if (!wf.isStatic && "Z".equals(wf.typeDesc) && !writtenZs.contains(wf.name)) {
                            writtenZs.add(wf.name);
                        }
                    }
                    if (writtenZs.size() >= 2 && (writtenZs.size() > bestZCount || (writtenZs.size() == bestZCount && m.isStatic && !bestIsStatic))) {
                        bestZCount = writtenZs.size();
                        bestIsStatic = m.isStatic;
                        isVideoField = writtenZs.get(0);
                    }
                }
            }
            if (isVideoField == null) {
                for (DexScanner.FieldInfo f : scanner.getFields(smiDesc)) {
                    if (!f.isStatic && "Z".equals(f.typeDesc)) {
                        isVideoField = f.name;
                        break;
                    }
                }
            }
            if (isVideoField != null) {
                addField("SendingMediaInfo", "isVideo", isVideoField);
            }
        }
    }

    private static String findNotificationCenter(DexScanner scanner) {
        DexScanner.ClassPredicate pred = (desc, fields, methods) -> {
            int staticInts = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (f.isStatic && "I".equals(f.typeDesc)) staticInts++;
            }
            if (staticInts < 100) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "I".equals(m.paramDescs[0]) && "[Ljava/lang/Object;".equals(m.paramDescs[1])) {
                    return true;
                }
            }
            return false;
        };
        String found = scanner.findClassByPredicate("Lorg/telegram/messenger/", pred);
        return found != null ? found : scanner.findClassByPredicate(null, pred);
    }

    private static String findAndroidUtilities(DexScanner scanner) {
        DexScanner.ClassPredicate strictPred = (desc, fields, methods) -> {
            if (methods.size() < 150) return false;
            boolean hasDp = false;
            boolean hasRun = false;
            int staticCnt = 0;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic) staticCnt++;
                if (m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length == 1 && "F".equals(m.paramDescs[0])) {
                    hasDp = true;
                }
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "Ljava/lang/Runnable;".equals(m.paramDescs[0])) {
                    hasRun = true;
                }
            }
            return hasDp && hasRun && staticCnt * 10 >= methods.size() * 9;
        };
        String best = scanner.findClassByPredicate("Lorg/telegram/messenger/", strictPred);
        if (best != null) return best;
        best = scanner.findClassByPredicate(null, strictPred);
        if (best != null) return best;
        DexScanner.ClassPredicate relaxedPred = (desc, fields, methods) -> {
            if (methods.size() < 150) return false;
            boolean hasDp = false;
            boolean hasRun = false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length == 1 && "F".equals(m.paramDescs[0])) {
                    hasDp = true;
                }
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "Ljava/lang/Runnable;".equals(m.paramDescs[0])) {
                    hasRun = true;
                }
                if (hasDp && hasRun) return true;
            }
            return false;
        };
        best = scanner.findClassByPredicate("Lorg/telegram/messenger/", relaxedPred);
        return best != null ? best : scanner.findClassByPredicate(null, relaxedPred);
    }

    private static String findActionBarMenuOnItemClick(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", (desc, fields, methods) -> {
            if (methods.size() > 3 || fields.size() > 1) return false;
            boolean hasBoolNoArgs = false;
            boolean hasVoidInt = false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    hasBoolNoArgs = true;
                }
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    hasVoidInt = true;
                }
            }
            return hasBoolNoArgs && hasVoidInt;
        });
    }

    private void resolveFileUploadOperation(DexScanner scanner) {
        String fuoDesc = findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "_uploaded", "_size");
        if (fuoDesc == null) return;

        addClass(ClassNames.FILE_UPLOAD_OPERATION, fuoDesc);

        String calcMethod = null;
        String startMethod = null;
        String[] initParams = null;
        String uploadChunkField = null;

        for (DexScanner.MethodInfo m : scanner.getMethods(fuoDesc)) {
            if (!m.isStatic && "<init>".equals(m.name) && m.paramDescs.length == 5) {
                initParams = m.paramDescs;
                continue;
            }
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                int insns = m.getInsnsSize();
                if (calcMethod == null && insns >= 40 && insns <= 80) {
                    List<String> intReads = new ArrayList<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(fuoDesc, m.name, m.paramDescs, fuoDesc)) {
                        if ("I".equals(rf.typeDesc)) {
                            intReads.add(rf.name);
                        }
                    }
                    if (intReads.size() == 3 && intReads.get(0).equals(intReads.get(1)) && intReads.get(0).equals(intReads.get(2))) {
                        calcMethod = m.name;
                        uploadChunkField = intReads.get(0);
                    }
                } else if (startMethod == null && insns > 400) {
                    startMethod = m.name;
                }
            }
        }

        if (calcMethod != null) {
            addMethod("FileUploadOperation", "calcTotalPartsCount", calcMethod);
        }

        String readBufferField = null;
        String maxReqField = null;
        String uploadConnField = null;

        if (startMethod != null) {
            List<String> uniqInts = new ArrayList<>();
            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(fuoDesc, startMethod, new String[0], fuoDesc)) {
                if ("[B".equals(wf.typeDesc) && readBufferField == null) {
                    readBufferField = wf.name;
                } else if ("I".equals(wf.typeDesc) && !uniqInts.contains(wf.name)) {
                    uniqInts.add(wf.name);
                }
            }
            if (uploadChunkField == null && !uniqInts.isEmpty()) {
                uploadChunkField = uniqInts.get(0);
            }
            for (String f : uniqInts) {
                if (f.equals(uploadChunkField)) continue;
                if (maxReqField == null) {
                    maxReqField = f;
                } else if (uploadConnField == null) {
                    uploadConnField = f;
                    break;
                }
            }
        }

        String reqPerConnField = null;
        if (initParams != null) {
            List<String> initInts = new ArrayList<>();
            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(fuoDesc, "<init>", initParams, fuoDesc)) {
                if ("[I".equals(wf.typeDesc) && reqPerConnField == null) {
                    reqPerConnField = wf.name;
                } else if ("I".equals(wf.typeDesc) && !initInts.contains(wf.name)) {
                    initInts.add(wf.name);
                }
            }
            if (uploadConnField == null && !initInts.isEmpty()) {
                uploadConnField = initInts.get(0);
            }
            if (uploadChunkField == null && initInts.size() >= 2) {
                uploadChunkField = initInts.get(1);
            }
        }

        if (readBufferField != null) addField("FileUploadOperation", "readBuffer", readBufferField);
        if (uploadChunkField != null) addField("FileUploadOperation", "uploadChunkSize", uploadChunkField);
        if (maxReqField != null) addField("FileUploadOperation", "maxRequestsCount", maxReqField);
        if (uploadConnField != null) addField("FileUploadOperation", "uploadConnectionsCount", uploadConnField);
        if (reqPerConnField != null) addField("FileUploadOperation", "uploadRequestsPerConnection", reqPerConnField);
    }

    private static String findFileLoadOperation(DexScanner scanner) {
        DexScanner.ClassPredicate pred = (desc, fields, methods) -> {
            if (methods.size() < 20) return false;
            int ints = 0, files = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (!f.isStatic) {
                    if ("I".equals(f.typeDesc)) ints++;
                    else if ("Ljava/io/File;".equals(f.typeDesc)) files++;
                }
            }
            return ints >= 15 && files >= 4;
        };
        String found = scanner.findClassByPredicate("Lorg/telegram/messenger/", pred);
        return found != null ? found : scanner.findClassByPredicate(null, pred);
    }

    private static String findChatMessageCell(DexScanner scanner, String msgObjDesc) {
        String found = scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) ->
                fields.size() > 300 && methods.size() > 200);
        if (found != null) return found;
        found = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            if (fields.size() <= 250 || methods.size() <= 150) return false;
            if (desc.contains("$") || desc.startsWith("Landroidx/") || desc.startsWith("Landroid/")) return false;
            if (msgObjDesc == null) return scanner.isSubclassOf(desc, "Landroid/view/ViewGroup;")
                    || scanner.isSubclassOf(desc, "Landroid/view/View;");
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                        && msgObjDesc.equals(m.paramDescs[0]) && m.readsFieldNamed("date")) {
                    return true;
                }
            }
            return false;
        });
        if (found != null) return found;
        return scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            if (fields.size() <= 250 || methods.size() <= 150) return false;
            if (desc.contains("$") || desc.startsWith("Landroidx/") || desc.startsWith("Landroid/")) return false;
            if (msgObjDesc == null) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                        && msgObjDesc.equals(m.paramDescs[0]) && m.getInsnsSize() > 1000) {
                    return true;
                }
            }
            return false;
        });
    }

    private void resolveSettingsClasses(DexScanner scanner) {
        final String[] uItemDescHolder = new String[1];
        final String[] factoryOfName = new String[1];
        final String[][] factoryOfParams = new String[1][];
        final String[] factoryBindName = new String[1];

        String factoryDesc = scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fields, methods) -> {
            if (desc.contains("/Components/")) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic && !desc.equals(m.returnDesc)
                        && m.returnDesc.startsWith("Lorg/telegram/ui/Components/")
                        && (m.paramDescs.length == 7 || m.paramDescs.length == 6)
                        && "I".equals(m.paramDescs[0]) && "I".equals(m.paramDescs[1])
                        && "I".equals(m.paramDescs[2]) && "I".equals(m.paramDescs[3])
                        && "Ljava/lang/CharSequence;".equals(m.paramDescs[4])
                        && "Ljava/lang/CharSequence;".equals(m.paramDescs[5])) {
                    uItemDescHolder[0] = m.returnDesc;
                    factoryOfName[0] = m.name;
                    factoryOfParams[0] = m.paramDescs;
                    return true;
                }
            }
            return false;
        });
        if (factoryDesc == null) {
            factoryDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (desc.contains("/Components/") || desc.startsWith("Ljava/") || desc.startsWith("Landroid/")) return false;
                for (DexScanner.MethodInfo m : methods) {
                    if (m.isStatic && !desc.equals(m.returnDesc)
                            && m.returnDesc.startsWith("L")
                            && !m.returnDesc.startsWith("Ljava/") && !m.returnDesc.startsWith("Landroid/")
                            && (m.paramDescs.length == 7 || m.paramDescs.length == 6)
                            && "I".equals(m.paramDescs[0]) && "I".equals(m.paramDescs[1])
                            && "I".equals(m.paramDescs[2]) && "I".equals(m.paramDescs[3])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[4])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[5])) {
                        uItemDescHolder[0] = m.returnDesc;
                        factoryOfName[0] = m.name;
                        factoryOfParams[0] = m.paramDescs;
                        return true;
                    }
                }
                return false;
            });
        }

        if (factoryDesc == null || uItemDescHolder[0] == null) return;
        final String resolvedFactoryDesc = factoryDesc;
        String uItemDesc = uItemDescHolder[0];

        addClass("org.telegram.ui.SettingsActivity$SettingCell$Factory", factoryDesc);
        addMethod("SettingsActivity$SettingCell$Factory", "of", factoryOfName[0]);
        addClass(ClassNames.UITEM, uItemDesc);

        // Find bindView on Factory: (View, UItem, Z, ...) -> V
        for (DexScanner.MethodInfo m : scanner.getMethods(factoryDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 3
                    && "Landroid/view/View;".equals(m.paramDescs[0])
                    && uItemDesc.equals(m.paramDescs[1])
                    && "Z".equals(m.paramDescs[2])) {
                factoryBindName[0] = m.name;
                break;
            }
        }
        if (factoryBindName[0] != null) {
            addMethod("SettingsActivity$SettingCell$Factory", "bindView", factoryBindName[0]);
        }

        // Resolve SettingCell (outer class of Factory)
        int dollarIdx = factoryDesc.lastIndexOf('$');
        if (dollarIdx > 0) {
            String settingCellDesc = factoryDesc.substring(0, dollarIdx) + ";";
            List<DexScanner.FieldInfo> cellFields = scanner.getFields(settingCellDesc);
            if (!cellFields.isEmpty()) {
                addClass(ClassNames.SETTINGS_ACTIVITY_SETTING_CELL, settingCellDesc);

                DexScanner.MethodInfo bestSet = null;
                int bestSetSize = -1;
                for (DexScanner.MethodInfo m : scanner.getMethods(settingCellDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 6
                            && "I".equals(m.paramDescs[0]) && "I".equals(m.paramDescs[1]) && "I".equals(m.paramDescs[2])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[3])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[4])) {
                        int sz = m.getInsnsSize();
                        if (sz > bestSetSize) {
                            bestSetSize = sz;
                            bestSet = m;
                        }
                    }
                }

                String iconField = null;
                if (bestSet != null) {
                    addMethod("SettingsActivity$SettingCell", "set", bestSet.name);
                    for (DexScanner.FieldInfo f : scanner.getReadInstanceFieldsInMethod(
                            settingCellDesc, bestSet.name, bestSet.paramDescs, settingCellDesc)) {
                        if (!f.isStatic && ("Landroid/widget/ImageView;".equals(f.typeDesc)
                                || scanner.isSubclassOf(f.typeDesc, "Landroid/widget/ImageView;"))) {
                            iconField = f.name;
                            break;
                        }
                    }
                }

                if (iconField == null) {
                    String uniqueImgField = null;
                    int imgCount = 0;
                    for (DexScanner.FieldInfo f : cellFields) {
                        if (!f.isStatic && "Landroid/widget/ImageView;".equals(f.typeDesc)) {
                            if ("iconView".equals(f.name)) {
                                iconField = f.name;
                            }
                            uniqueImgField = f.name;
                            imgCount++;
                        }
                    }
                    if (iconField == null && imgCount == 1) {
                        iconField = uniqueImgField;
                    }
                }
                if (iconField != null) {
                    addField("SettingsActivity$SettingCell", "iconView", iconField);
                }
            }
        }

        // Resolve UItem fields from Factory static->UItem overloads (largest overload first)
        List<DexScanner.MethodInfo> ofOverloads = new ArrayList<>();
        for (DexScanner.MethodInfo m : scanner.getMethods(factoryDesc)) {
            if (m.isStatic && uItemDesc.equals(m.returnDesc)) {
                ofOverloads.add(m);
            }
        }
        ofOverloads.sort((a, b) -> Integer.compare(b.paramDescs.length, a.paramDescs.length));
        String firstInt = null;
        String cs1 = null, cs2 = null;
        for (DexScanner.MethodInfo m : ofOverloads) {
            for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(factoryDesc, m.name, m.paramDescs, uItemDesc)) {
                if ("I".equals(f.typeDesc) && firstInt == null) {
                    firstInt = f.name;
                } else if ("Ljava/lang/CharSequence;".equals(f.typeDesc)) {
                    if (cs1 == null) cs1 = f.name;
                    else if (cs2 == null && !f.name.equals(cs1)) cs2 = f.name;
                }
            }
            if (firstInt != null && cs1 != null && cs2 != null) break;
        }
        if (firstInt != null) addField("UItem", "id", firstInt);
        if (cs1 != null) addField("UItem", "text", cs1);
        if (cs2 != null) addField("UItem", "subtext", cs2);

        // Find SettingsActivity by ranking top-level classes by total calls to Factory returning UItem in ArrayList methods
        final String[] bestSettingsDesc = new String[1];
        final String[] bestFillName = new String[1];
        final String[] bestUniversalAdapter = new String[1];
        final int[] bestCallScore = new int[]{0};

        scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fields, methods) -> {
            if (desc.equals(resolvedFactoryDesc) || desc.contains("$") || desc.contains("/Components/") || desc.contains("/Cells/")) {
                return false;
            }
            int totalCalls = 0;
            String localBestFill = null;
            int localBestFillCalls = 0;
            String localAdapter = null;
            for (DexScanner.MethodInfo m : methods) {
                if (m.codeOff == 0) continue;
                boolean hasArrayList = false;
                for (String p : m.paramDescs) {
                    if ("Ljava/util/ArrayList;".equals(p)) {
                        hasArrayList = true;
                        break;
                    }
                }
                if (!hasArrayList) continue;
                int callCount = 0;
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(desc, m.name, m.paramDescs, resolvedFactoryDesc)) {
                    if (uItemDesc.equals(mr.returnDesc)) {
                        callCount++;
                    }
                }
                if (callCount > 0) {
                    totalCalls += callCount;
                    if (callCount > localBestFillCalls) {
                        localBestFillCalls = callCount;
                        localBestFill = m.name;
                        localAdapter = null;
                        for (String p : m.paramDescs) {
                            if (p.startsWith("Lorg/telegram/ui/Components/") && !uItemDesc.equals(p)) {
                                localAdapter = p;
                                break;
                            }
                        }
                    }
                }
            }
            if (totalCalls > bestCallScore[0]) {
                bestCallScore[0] = totalCalls;
                bestSettingsDesc[0] = desc;
                bestFillName[0] = localBestFill;
                bestUniversalAdapter[0] = localAdapter;
            }
            return false;
        });

        if (bestSettingsDesc[0] != null && bestCallScore[0] >= 15) {
            String settingsDesc = bestSettingsDesc[0];
            addClass("org.telegram.ui.SettingsActivity", settingsDesc);
            if (bestUniversalAdapter[0] != null) {
                addClass(ClassNames.UNIVERSAL_ADAPTER, bestUniversalAdapter[0]);
            }
            if (bestFillName[0] != null) {
                addMethod("SettingsActivity", "fillItems", bestFillName[0]);
            }

            DexScanner.MethodInfo bestClick = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(settingsDesc)) {
                if (!"V".equals(m.returnDesc) || m.codeOff == 0) continue;
                if (m.paramDescs.length < 2 || m.paramDescs.length > 5) continue;
                boolean takesUItem = false;
                for (String p : m.paramDescs) {
                    if (uItemDesc.equals(p)) {
                        takesUItem = true;
                        break;
                    }
                }
                if (!takesUItem) continue;
                if ("onClick".equals(m.name)) {
                    bestClick = m;
                    break;
                }
                if (bestClick == null || m.getInsnsSize() > bestClick.getInsnsSize()) {
                    bestClick = m;
                }
            }
            if (bestClick != null) {
                addMethod("SettingsActivity", "onClick", bestClick.name);
            }
        } else {
            // Fallback for R8-inlined SettingsActivity lambdas (e.g. Nekogram root-repackaged lambdas)
            final String[] lambdaClass = new String[1];
            final String[] lambdaMethod = new String[1];
            final int[] lambdaScore = new int[]{0};
            final String finalFactoryDesc = factoryDesc;
            scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (desc.equals(finalFactoryDesc) || desc.startsWith("Ljava/") || desc.startsWith("Landroid/")) {
                    return false;
                }
                for (DexScanner.MethodInfo m : methods) {
                    if (m.codeOff == 0) continue;
                    int callCount = 0;
                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(desc, m.name, m.paramDescs, finalFactoryDesc)) {
                        if (uItemDesc.equals(mr.returnDesc)) {
                            callCount++;
                        }
                    }
                    if (callCount > lambdaScore[0]) {
                        lambdaScore[0] = callCount;
                        lambdaClass[0] = desc;
                        lambdaMethod[0] = m.name;
                    }
                }
                return false;
            });

            if (lambdaClass[0] != null && lambdaScore[0] >= 15) {
                String directSettingsDesc = "Lorg/telegram/ui/SettingsActivity;";
                addClass("org.telegram.ui.SettingsActivity",
                        hasClass(scanner, directSettingsDesc) ? directSettingsDesc : lambdaClass[0]);
                addClass("org.telegram.ui.SettingsActivity$FillItemsLambda", lambdaClass[0]);
                addMethod("SettingsActivity", "fillItems", lambdaMethod[0]);

                if (hasClass(scanner, directSettingsDesc)) {
                    Set<String> clickCandidates = new HashSet<>();
                    for (DexScanner.MethodInfo m : scanner.getMethods(directSettingsDesc)) {
                        if (m.codeOff == 0) continue;
                        for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(directSettingsDesc, m.name, m.paramDescs, null)) {
                            if ("<init>".equals(mr.name) && mr.paramDescs.length == 1
                                    && directSettingsDesc.equals(mr.paramDescs[0])
                                    && !mr.ownerDesc.equals(directSettingsDesc)) {
                                clickCandidates.add(mr.ownerDesc);
                            }
                        }
                    }
                    String bestClickCls = null;
                    DexScanner.MethodInfo bestClickMeth = null;
                    for (String cand : clickCandidates) {
                        for (DexScanner.MethodInfo cm : scanner.getMethods(cand)) {
                            if (!cm.isStatic && "V".equals(cm.returnDesc) && cm.paramDescs.length == 5
                                    && cm.getInsnsSize() > 100) {
                                if (bestClickMeth == null || cm.getInsnsSize() > bestClickMeth.getInsnsSize()) {
                                    bestClickMeth = cm;
                                    bestClickCls = cand;
                                }
                            }
                        }
                    }
                    if (bestClickCls != null && bestClickMeth != null) {
                        addClass("org.telegram.ui.SettingsActivity$OnClickLambda", bestClickCls);
                        addMethod("SettingsActivity", "onClick", bestClickMeth.name);
                    }
                }
                return;
            }

            String directSettingsDesc = "Lorg/telegram/ui/SettingsActivity;";
            String fill = null, click = null, universalAdapterDesc = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(directSettingsDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "Ljava/util/ArrayList;".equals(m.paramDescs[0])
                        && m.paramDescs[1].startsWith("Lorg/telegram/ui/Components/")) {
                    fill = m.name;
                    universalAdapterDesc = m.paramDescs[1];
                } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                        && uItemDesc.equals(m.paramDescs[0])
                        && "Landroid/view/View;".equals(m.paramDescs[1])
                        && "I".equals(m.paramDescs[2])
                        && "F".equals(m.paramDescs[3])
                        && "F".equals(m.paramDescs[4])) {
                    if (click == null || "onClick".equals(m.name)) {
                        click = m.name;
                    }
                }
            }
            if (fill != null || click != null) {
                addClass("org.telegram.ui.SettingsActivity", directSettingsDesc);
                if (universalAdapterDesc != null) {
                    addClass(ClassNames.UNIVERSAL_ADAPTER, universalAdapterDesc);
                }
                if (fill != null) addMethod("SettingsActivity", "fillItems", fill);
                if (click != null) addMethod("SettingsActivity", "onClick", click);
            }
        }
    }

    private void resolveDrawerClasses(DexScanner scanner, String launchActDesc) {
        String dlaDesc = "Lorg/telegram/ui/Adapters/DrawerLayoutAdapter;";
        String dlcDesc = "Lorg/telegram/ui/ActionBar/DrawerLayoutContainer;";
        if (!hasClass(scanner, dlaDesc)) {
            return;
        }
        addClass(ClassNames.DRAWER_LAYOUT_ADAPTER, dlaDesc);

        String itemDesc = "Lorg/telegram/ui/Adapters/DrawerLayoutAdapter$Item;";
        if (!hasClass(scanner, itemDesc)) {
            itemDesc = scanner.findClassByPredicate("Lorg/telegram/ui/Adapters/", (d, fs, ms) -> {
                for (DexScanner.MethodInfo m : ms) {
                    if ("<init>".equals(m.name) && m.paramDescs.length == 3
                            && "I".equals(m.paramDescs[0])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[1])
                            && "I".equals(m.paramDescs[2])) {
                        return true;
                    }
                }
                return false;
            });
        }
        if (itemDesc != null) {
            addClass("org.telegram.ui.Adapters.DrawerLayoutAdapter$Item", itemDesc);
        }

        // LaunchActivity fields: drawerLayoutAdapter and drawerLayoutContainer
        if (launchActDesc != null) {
            String dlaField = hasFieldNamed(scanner, launchActDesc, "drawerLayoutAdapter")
                    ? "drawerLayoutAdapter"
                    : scanner.findSingleField(launchActDesc, false, dlaDesc);
            if (dlaField != null) {
                addField("LaunchActivity", "drawerLayoutAdapter", dlaField);
            }
            String dlcField = hasFieldNamed(scanner, launchActDesc, "drawerLayoutContainer")
                    ? "drawerLayoutContainer"
                    : scanner.findSingleField(launchActDesc, false, dlcDesc);
            if (dlcField != null) {
                addField("LaunchActivity", "drawerLayoutContainer", dlcField);
            }
        }

        // DrawerLayoutAdapter#resetItems: raw or largest instance () -> V
        String resetItems = hasMethodNamed(scanner, dlaDesc, "resetItems") ? "resetItems" : null;
        if (resetItems == null) {
            DexScanner.MethodInfo bestReset = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(dlaDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0
                        && !m.name.startsWith("<")) {
                    if (bestReset == null || m.getInsnsSize() > bestReset.getInsnsSize()) {
                        bestReset = m;
                    }
                }
            }
            if (bestReset != null && bestReset.getInsnsSize() > 50) {
                resetItems = bestReset.name;
            }
        }
        if (resetItems != null) {
            addMethod("DrawerLayoutAdapter", "resetItems", resetItems);
        }

        // Resolve DrawerLayoutAdapter#getId and DrawerLayoutContainer#closeDrawer from LaunchActivity (View, I, F, F) click handler
        String getIdMeth = hasMethodNamed(scanner, dlaDesc, "getId") ? "getId" : null;
        String closeDrawerMeth = hasMethodNamed(scanner, dlcDesc, "closeDrawer") ? "closeDrawer" : null;
        if (launchActDesc != null && (getIdMeth == null || closeDrawerMeth == null)) {
            for (DexScanner.MethodInfo m : scanner.getMethods(launchActDesc)) {
                if (m.paramDescs.length == 4
                        && "Landroid/view/View;".equals(m.paramDescs[0])
                        && "I".equals(m.paramDescs[1])
                        && "F".equals(m.paramDescs[2])
                        && "F".equals(m.paramDescs[3])) {
                    if (getIdMeth == null) {
                        for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(launchActDesc, m.name, m.paramDescs, dlaDesc)) {
                            if ("I".equals(mr.returnDesc) && mr.paramDescs.length == 1 && "I".equals(mr.paramDescs[0])) {
                                getIdMeth = mr.name;
                                break;
                            }
                        }
                    }
                    if (closeDrawerMeth == null && hasClass(scanner, dlcDesc)) {
                        Map<String, Integer> closeCounts = new HashMap<>();
                        for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(launchActDesc, m.name, m.paramDescs, dlcDesc)) {
                            if ("V".equals(mr.returnDesc) && (mr.paramDescs.length == 0
                                    || (mr.paramDescs.length == 1 && "Z".equals(mr.paramDescs[0])))) {
                                closeCounts.put(mr.name, closeCounts.getOrDefault(mr.name, 0) + 1);
                            }
                        }
                        int bestCnt = 0;
                        for (Map.Entry<String, Integer> e : closeCounts.entrySet()) {
                            if (e.getValue() > bestCnt) {
                                bestCnt = e.getValue();
                                closeDrawerMeth = e.getKey();
                            }
                        }
                    }
                    break;
                }
            }
        }
        if (getIdMeth != null) {
            addMethod("DrawerLayoutAdapter", "getId", getIdMeth);
        }
        if (closeDrawerMeth != null) {
            addMethod("DrawerLayoutContainer", "closeDrawer", closeDrawerMeth);
        }

        // DrawerLayoutAdapter#items: raw or ArrayList field read in getId(I)
        String itemsField = hasFieldNamed(scanner, dlaDesc, "items") ? "items" : null;
        if (itemsField == null && getIdMeth != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(dlaDesc, getIdMeth, new String[]{"I"}, dlaDesc)) {
                if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                    itemsField = rf.name;
                    break;
                }
            }
        }
        if (itemsField == null) {
            itemsField = scanner.findSingleField(dlaDesc, false, "Ljava/util/ArrayList;");
        }
        if (itemsField != null) {
            addField("DrawerLayoutAdapter", "items", itemsField);
        }
    }

    private static String findTheme(DexScanner scanner) {
        DexScanner.ClassPredicate pred = (desc, fields, methods) -> {
            int staticInts = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (f.isStatic && "I".equals(f.typeDesc)) staticInts++;
            }
            return staticInts > 200;
        };
        String found = scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", pred);
        return found != null ? found : scanner.findClassByPredicate(null, pred);
    }

    private static String findIsCurrentThemeDark(
            DexScanner scanner, String themeDesc, String themeInfoDesc, String infoIsDark) {
        String bestPrimary = null;
        int bestPrimarySize = Integer.MAX_VALUE;
        String bestFieldMatch = null;
        int bestFieldSize = Integer.MAX_VALUE;
        String bestFallback = null;
        int bestFallbackSize = 0;

        String[] noArgs = new String[0];
        for (DexScanner.MethodInfo m : scanner.getMethods(themeDesc)) {
            if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 0 || m.codeOff == 0) {
                continue;
            }
            int insns = m.getInsnsSize();
            boolean hasXor = scanner.methodHasOpcode(themeDesc, m.name, noArgs, 0x97, 0xb7, 0xd7, 0xdf);

            if (themeInfoDesc != null && infoIsDark != null && !hasXor) {
                boolean callsInfoIsDark = false;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(themeDesc, m.name, noArgs, themeInfoDesc)) {
                    if (infoIsDark.equals(ref.name) && ref.paramDescs.length == 0 && "Z".equals(ref.returnDesc)) {
                        callsInfoIsDark = true;
                        break;
                    }
                }
                if (callsInfoIsDark && insns < bestPrimarySize) {
                    bestPrimarySize = insns;
                    bestPrimary = m.name;
                }
            }

            if (hasXor || !scanner.getCalledMethodsInMethod(themeDesc, m.name, noArgs, "Ljava/lang/System;").isEmpty()) {
                continue;
            }
            if (themeInfoDesc != null) {
                boolean readsThemeInfo = false;
                for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(themeDesc, m.name, noArgs, themeDesc)) {
                    if (themeInfoDesc.equals(rf.typeDesc)) {
                        readsThemeInfo = true;
                        break;
                    }
                }
                if (readsThemeInfo && insns < bestFieldSize) {
                    bestFieldSize = insns;
                    bestFieldMatch = m.name;
                }
            }
            if (insns > bestFallbackSize) {
                bestFallbackSize = insns;
                bestFallback = m.name;
            }
        }
        if (bestPrimary != null) return bestPrimary;
        if (bestFieldMatch != null) return bestFieldMatch;
        return bestFallback;
    }

    private void resolveAlertDialog(DexScanner scanner) {
        String builderDesc = DexScanner.javaToDesc(ClassNames.ALERT_DIALOG_BUILDER);
        List<DexScanner.MethodInfo> methods = scanner.getMethods(builderDesc);
        if (methods.isEmpty()) return;

        // Identify Dialog class from parameterless () -> ActionBar/ methods (create & show)
        Map<String, Integer> retCounts = new HashMap<>();
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                retCounts.put(m.returnDesc, retCounts.getOrDefault(m.returnDesc, 0) + 1);
            }
        }
        String dialogDesc = null;
        int bestCnt = 0;
        for (Map.Entry<String, Integer> e : retCounts.entrySet()) {
            if (e.getValue() > bestCnt) {
                bestCnt = e.getValue();
                dialogDesc = e.getKey();
            }
        }

        if (dialogDesc != null) {
            String alertField = scanner.findSingleField(builderDesc, false, dialogDesc);
            if (alertField != null) {
                addField("AlertDialog$Builder", "alertDialog", alertField);
            }
        }

        // getDismissRunnable: () -> Ljava/lang/Runnable;
        String dismissRun = scanner.findSingleMethod(builderDesc, false, "Ljava/lang/Runnable;");
        if (dismissRun != null) {
            addMethod("AlertDialog$Builder", "getDismissRunnable", dismissRun);
        }

        // setView(View) -> V or Self
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 1 && "Landroid/view/View;".equals(m.paramDescs[0])
                    && ("V".equals(m.returnDesc) || builderDesc.equals(m.returnDesc))) {
                addMethod("AlertDialog$Builder", "setView", m.name);
                break;
            }
        }

        if (dialogDesc == null) dialogDesc = "Lorg/telegram/ui/ActionBar/AlertDialog;";
        List<DexScanner.MethodInfo> dialogMethods = scanner.getMethods(dialogDesc);

        // OnButtonClickListener from (String/CharSequence, ?) -> V or Self
        String listenerDesc = null;
        String negBtn = null, neuBtn = null, posBtn = null;
        List<DexScanner.MethodInfo> unresolvedBtns = new ArrayList<>();
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 2
                    && m.paramDescs[1].startsWith("Lorg/telegram/ui/ActionBar/")
                    && ("Ljava/lang/String;".equals(m.paramDescs[0]) || "Ljava/lang/CharSequence;".equals(m.paramDescs[0]))) {
                listenerDesc = m.paramDescs[1];
                if (m.containsConstInt(-1)) posBtn = m.name;
                else if (m.containsConstInt(-2)) negBtn = m.name;
                else if (m.containsConstInt(-3)) neuBtn = m.name;
                else unresolvedBtns.add(m);
            }
        }
        if (!unresolvedBtns.isEmpty() && listenerDesc != null) {
            // In stripped builds (e.g. Cherrygram), button constants live in AlertDialog's click handlers
            Map<String, Integer> listenerFieldBtnCode = new HashMap<>();
            for (DexScanner.MethodInfo dm : dialogMethods) {
                if (dm.isStatic || dm.codeOff <= 0) continue;
                int code = dm.containsConstInt(-1) && !dm.containsConstInt(-2) ? -1
                        : (dm.containsConstInt(-2) && !dm.containsConstInt(-1) ? -2
                        : (dm.containsConstInt(-3) ? -3 : 0));
                if (code == 0) continue;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(dialogDesc, dm.name, dm.paramDescs, dialogDesc)) {
                    if (listenerDesc.equals(rf.typeDesc)) {
                        listenerFieldBtnCode.putIfAbsent(rf.name, code);
                    }
                }
            }
            List<DexScanner.MethodInfo> remaining = new ArrayList<>();
            for (DexScanner.MethodInfo m : unresolvedBtns) {
                boolean matched = false;
                for (String wf : collectWrittenDialogFields(scanner, builderDesc, dialogDesc, m)) {
                    Integer code = listenerFieldBtnCode.get(wf);
                    if (code == null) continue;
                    if (code == -1 && posBtn == null) { posBtn = m.name; matched = true; break; }
                    if (code == -2 && negBtn == null) { negBtn = m.name; matched = true; break; }
                    if (code == -3 && neuBtn == null) { neuBtn = m.name; matched = true; break; }
                }
                if (!matched) remaining.add(m);
            }
            for (DexScanner.MethodInfo m : remaining) {
                if ("Ljava/lang/CharSequence;".equals(m.paramDescs[0]) && posBtn == null) posBtn = m.name;
                else if (negBtn == null) negBtn = m.name;
                else if (neuBtn == null) neuBtn = m.name;
            }
        }
        if (negBtn != null) addMethod("AlertDialog$Builder", "setNegativeButton", negBtn);
        if (neuBtn != null) addMethod("AlertDialog$Builder", "setNeutralButton", neuBtn);
        if (posBtn != null) addMethod("AlertDialog$Builder", "setPositiveButton", posBtn);

        if (listenerDesc != null) {
            addClass(ClassNames.ALERT_DIALOG_BUTTON_CLICK, listenerDesc);
            for (DexScanner.MethodInfo lm : scanner.getMethods(listenerDesc)) {
                if (!lm.isStatic && lm.paramDescs.length == 2 && "I".equals(lm.paramDescs[1])) {
                    addMethod("AlertDialog$OnButtonClickListener", "onClick", lm.name);
                    break;
                }
            }
        }

        // setTitle / setMessage: match AlertDialog$Builder (CharSequence) against AlertDialog's title and message fields
        String titleField = null;
        String messageField = null;
        for (DexScanner.MethodInfo dm : dialogMethods) {
            if (!dm.isStatic && dm.paramDescs.length == 1
                    && "Ljava/lang/CharSequence;".equals(dm.paramDescs[0])
                    && "setTitle".equals(dm.name)) {
                for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(dialogDesc, dm.name, dm.paramDescs, dialogDesc)) {
                    if (!f.isStatic && "Ljava/lang/CharSequence;".equals(f.typeDesc)) {
                        titleField = f.name;
                        break;
                    }
                }
                break;
            }
        }
        for (DexScanner.MethodInfo dm : dialogMethods) {
            if (!dm.isStatic && "V".equals(dm.returnDesc) && dm.paramDescs.length == 1
                    && "Ljava/lang/CharSequence;".equals(dm.paramDescs[0])
                    && !"setTitle".equals(dm.name)) {
                for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(dialogDesc, dm.name, dm.paramDescs, dialogDesc)) {
                    if (!f.isStatic && "Ljava/lang/CharSequence;".equals(f.typeDesc) && !f.name.equals(titleField)) {
                        if ("setMessage".equals(dm.name) || messageField == null) {
                            messageField = f.name;
                        }
                        if ("setMessage".equals(dm.name)) break;
                    }
                }
            }
        }

        String titleMethod = null, msgMethod = null;
        String cs1 = null, cs2 = null;
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && ("V".equals(m.returnDesc) || builderDesc.equals(m.returnDesc))
                    && m.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) {
                if ("setTitle".equals(m.name)) titleMethod = m.name;
                else if ("setMessage".equals(m.name)) msgMethod = m.name;
                else {
                    Set<String> written = collectWrittenDialogFields(scanner, builderDesc, dialogDesc, m);
                    if (titleField != null && written.contains(titleField) && titleMethod == null) {
                        titleMethod = m.name;
                    } else if (messageField != null && written.contains(messageField) && msgMethod == null) {
                        msgMethod = m.name;
                    } else if (cs1 == null) {
                        cs1 = m.name;
                    } else if (cs2 == null) {
                        cs2 = m.name;
                    }
                }
            }
        }
        if (msgMethod == null) {
            msgMethod = (cs1 != null && !cs1.equals(titleMethod)) ? cs1 : cs2;
        }
        if (titleMethod == null) {
            titleMethod = (cs2 != null && !cs2.equals(msgMethod)) ? cs2 : (cs1 != null && !cs1.equals(msgMethod) ? cs1 : null);
        }
        if (msgMethod != null) addMethod("AlertDialog$Builder", "setMessage", msgMethod);
        if (titleMethod != null) addMethod("AlertDialog$Builder", "setTitle", titleMethod);

        // create() and show(): () -> Dialog (create is smaller than show)
        if (dialogDesc != null) {
            List<DexScanner.MethodInfo> dlgMeths = new java.util.ArrayList<>();
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && dialogDesc.equals(m.returnDesc) && m.paramDescs.length == 0) {
                    dlgMeths.add(m);
                }
            }
            if (dlgMeths.size() >= 2) {
                dlgMeths.sort((a, b) -> Integer.compare(a.getInsnsSize(), b.getInsnsSize()));
                addMethod("AlertDialog$Builder", "create", dlgMeths.get(0).name);
                addMethod("AlertDialog$Builder", "show", dlgMeths.get(dlgMeths.size() - 1).name);
            } else if (dlgMeths.size() == 1) {
                addMethod("AlertDialog$Builder", "create", dlgMeths.get(0).name);
            }
        }
    }

    private void resolveDialogsActivityProxy(DexScanner scanner, String baseFragmentDesc, String actionBarDesc) {
        String dialogsActDesc = scanner.findClassByStrings(
                "Lorg/telegram/ui/", baseFragmentDesc, "dialogsType", "proxy_enabled");
        if (dialogsActDesc == null) {
            dialogsActDesc = scanner.findClassByStrings(
                    "Lorg/telegram/ui/", null, "dialogsType", "proxy_enabled");
        }
        if (dialogsActDesc == null && !scanner.getMethods("Lorg/telegram/ui/DialogsActivity;").isEmpty()) {
            dialogsActDesc = "Lorg/telegram/ui/DialogsActivity;";
        }
        if (dialogsActDesc == null) return;
        addClass(ClassNames.DIALOGS_ACTIVITY, dialogsActDesc);

        if (actionBarDesc == null || scanner.getMethods(actionBarDesc).isEmpty()) {
            String rawActionBar = "Lorg/telegram/ui/ActionBar/ActionBar;";
            if (!scanner.getMethods(rawActionBar).isEmpty()) {
                actionBarDesc = rawActionBar;
            }
        }

        // Locate updateProxyButton fallback (takes boolean(s) and uses "proxy_enabled"), e.g. when setConnected is inlined
        DexScanner.MethodInfo proxyUpdateFallback = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc)) continue;
            if (m.paramDescs.length < 1 || m.paramDescs.length > 2) continue;
            boolean allBool = true;
            for (String p : m.paramDescs) {
                if (!"Z".equals(p)) {
                    allBool = false;
                    break;
                }
            }
            if (allBool && m.usesString("proxy_enabled")) {
                if (proxyUpdateFallback == null || m.paramDescs.length == 2) {
                    proxyUpdateFallback = m;
                }
            }
        }

        Set<String> updateReadFields = new HashSet<>();
        Set<String> updateWrittenOwners = new HashSet<>();
        if (proxyUpdateFallback != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                    dialogsActDesc, proxyUpdateFallback.name, proxyUpdateFallback.paramDescs, dialogsActDesc)) {
                updateReadFields.add(rf.name);
            }
            updateWrittenOwners.addAll(scanner.getWrittenFieldsGroupedByOwner(
                    dialogsActDesc, proxyUpdateFallback.name, proxyUpdateFallback.paramDescs).keySet());
        }

        String proxyDrawableDesc = null;
        String proxyDrawableFieldName = null;
        String setConnectedName = null;
        List<DexScanner.FieldInfo> daFields = scanner.getFields(dialogsActDesc);
        for (DexScanner.FieldInfo f : daFields) {
            if (f.isStatic || f.typeDesc.startsWith("Landroid/")) continue;
            if (!scanner.isSubclassOf(f.typeDesc, "Landroid/graphics/drawable/Drawable;")) continue;
            String setConn = scanner.findSingleMethod(f.typeDesc, false, "V", "Z", "Z", "Z");
            if (setConn != null) {
                boolean betterMatch = updateReadFields.contains(f.name) || updateWrittenOwners.contains(f.typeDesc);
                if (proxyDrawableDesc == null || betterMatch) {
                    proxyDrawableDesc = f.typeDesc;
                    proxyDrawableFieldName = f.name;
                    setConnectedName = setConn;
                    if (betterMatch) break;
                }
            }
        }

        // Pass 2: R8 may inline ProxyDrawable#setConnected(Z,Z,Z) (e.g. Nagram); identify ProxyDrawable by (Drawable)V + <=10 methods
        if (proxyDrawableDesc == null) {
            for (DexScanner.FieldInfo f : daFields) {
                if (f.isStatic || f.typeDesc.startsWith("Landroid/")) continue;
                if (!scanner.isSubclassOf(f.typeDesc, "Landroid/graphics/drawable/Drawable;")) continue;
                boolean hasDrawableSetter = false;
                int nonCtorInstanceMethods = 0;
                for (DexScanner.MethodInfo pm : scanner.getMethods(f.typeDesc)) {
                    if (pm.isStatic || pm.name.startsWith("<")) continue;
                    nonCtorInstanceMethods++;
                    if ("V".equals(pm.returnDesc) && pm.paramDescs.length == 1
                            && "Landroid/graphics/drawable/Drawable;".equals(pm.paramDescs[0])) {
                        hasDrawableSetter = true;
                    }
                }
                if (hasDrawableSetter && nonCtorInstanceMethods <= 10) {
                    boolean betterMatch = updateReadFields.contains(f.name) || updateWrittenOwners.contains(f.typeDesc);
                    if (proxyDrawableDesc == null || betterMatch) {
                        proxyDrawableDesc = f.typeDesc;
                        proxyDrawableFieldName = f.name;
                        if (betterMatch) break;
                    }
                }
            }
        }

        if (proxyDrawableDesc != null && proxyDrawableFieldName != null) {
            addClass(ClassNames.PROXY_DRAWABLE, proxyDrawableDesc);
            addField("DialogsActivity", "proxyDrawable", proxyDrawableFieldName);
            if (setConnectedName != null) {
                addMethod("ProxyDrawable", "setConnected", setConnectedName);
            }
        }

        String menuItemDesc = null;
        if (actionBarDesc != null) {
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                    dialogsActDesc, "createView", new String[]{"Landroid/content/Context;"}, actionBarDesc)) {
                if (ref.paramDescs.length != 0 || ref.returnDesc == null
                        || !ref.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                    continue;
                }
                if (!scanner.isSubclassOf(ref.returnDesc, "Landroid/widget/LinearLayout;")) {
                    continue;
                }
                for (DexScanner.MethodInfo m : scanner.getMethods(ref.returnDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 2
                            && "I".equals(m.paramDescs[0])
                            && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[1])
                            && m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                        addMethod("ActionBar", "createMenu", ref.name);
                        addClass(ClassNames.ACTION_BAR_MENU, ref.returnDesc);
                        addMethod("ActionBarMenu", "addItemDrawable", m.name);
                        menuItemDesc = m.returnDesc;
                        break;
                    }
                }
                if (menuItemDesc != null) break;
            }
        }

        boolean resolvedUpdateProxy = false;
        if (proxyDrawableDesc != null && setConnectedName != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                boolean callsSetConn = false;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                        dialogsActDesc, m.name, m.paramDescs, proxyDrawableDesc)) {
                    if (setConnectedName.equals(ref.name)) {
                        callsSetConn = true;
                        break;
                    }
                }
                if (callsSetConn) {
                    addMethod("DialogsActivity", "updateProxyButton", m.name);
                    resolvedUpdateProxy = true;
                    break;
                }
            }
        }
        if (!resolvedUpdateProxy && proxyUpdateFallback != null) {
            addMethod("DialogsActivity", "updateProxyButton", proxyUpdateFallback.name);
        }

        String overflowItemName = null;
        String updateHeaderVisName = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
            int selfVoidCalls = 0;
            boolean callsAlphaHelper = false;
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                    dialogsActDesc, m.name, m.paramDescs, null)) {
                if (dialogsActDesc.equals(ref.ownerDesc) && ref.paramDescs.length == 0 && "V".equals(ref.returnDesc)) {
                    selfVoidCalls++;
                } else if (ref.paramDescs.length == 2
                        && "Landroid/view/View;".equals(ref.paramDescs[0])
                        && "F".equals(ref.paramDescs[1])
                        && "V".equals(ref.returnDesc)) {
                    callsAlphaHelper = true;
                }
            }
            if (callsAlphaHelper && selfVoidCalls >= 3) {
                updateHeaderVisName = m.name;
                addMethod("DialogsActivity", "updateHeaderVisibility", m.name);
                if (menuItemDesc != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            dialogsActDesc, m.name, m.paramDescs, dialogsActDesc)) {
                        if (menuItemDesc.equals(rf.typeDesc)) {
                            overflowItemName = rf.name;
                            addField("DialogsActivity", "overflowItem", rf.name);
                            break;
                        }
                    }
                }
                break;
            }
        }

        String showMenuName = scanner.findMethodByString(dialogsActDesc, "proxy_enabled", false, "V");
        if (showMenuName != null) {
            addMethod("DialogsActivity", "showOverflowMenu", showMenuName);
            String[] noParams = new String[0];
            String itemOptionsDesc = null;
            String addViewName = null;
            String subItemDesc = null;
            String subItemFieldName = null;
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                    dialogsActDesc, showMenuName, noParams, null)) {
                if (ref.ownerDesc.startsWith("Lorg/telegram/ui/Components/")
                        && ("V".equals(ref.returnDesc) || ref.ownerDesc.equals(ref.returnDesc))
                        && ref.paramDescs.length == 1
                        && ref.paramDescs[0].startsWith("Lorg/telegram/ui/ActionBar/")) {
                    itemOptionsDesc = ref.ownerDesc;
                    addViewName = ref.name;
                    subItemDesc = ref.paramDescs[0];
                    addClass(ClassNames.ITEM_OPTIONS, itemOptionsDesc);
                    addMethod("ItemOptions", "addViewSubItem", addViewName);
                    break;
                }
            }
            if (subItemDesc != null) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        dialogsActDesc, showMenuName, noParams, dialogsActDesc)) {
                    if (subItemDesc.equals(rf.typeDesc)) {
                        subItemFieldName = rf.name;
                        addField("DialogsActivity", "proxySubItem", rf.name);
                        break;
                    }
                }
            }
            if (overflowItemName == null) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        dialogsActDesc, showMenuName, noParams, dialogsActDesc)) {
                    boolean typeMatch = (menuItemDesc != null && menuItemDesc.equals(rf.typeDesc))
                            || (menuItemDesc == null
                                && rf.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                                && !rf.name.equals(subItemFieldName)
                                && (subItemDesc == null || !subItemDesc.equals(rf.typeDesc)));
                    if (typeMatch) {
                        overflowItemName = rf.name;
                        addField("DialogsActivity", "overflowItem", overflowItemName);
                        break;
                    }
                }
            }
            if (itemOptionsDesc != null && addViewName != null) {
                List<DexScanner.MethodRef> optCalls = scanner.getCalledMethodsInMethod(
                        dialogsActDesc, showMenuName, noParams, itemOptionsDesc);
                int addIdx = -1;
                for (int i = 0; i < optCalls.size(); i++) {
                    DexScanner.MethodRef c = optCalls.get(i);
                    if (addViewName.equals(c.name) && c.paramDescs.length == 1) {
                        addIdx = i;
                        break;
                    }
                }
                if (addIdx >= 0) {
                    String addGapName = null;
                    for (int i = addIdx - 1; i >= 0; i--) {
                        DexScanner.MethodRef c = optCalls.get(i);
                        if (c.paramDescs.length == 0
                                && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                            addGapName = c.name;
                            addMethod("ItemOptions", "addGap", addGapName);
                            break;
                        }
                    }
                    for (int i = addIdx + 1; i < optCalls.size(); i++) {
                        DexScanner.MethodRef c = optCalls.get(i);
                        if (c.paramDescs.length == 0
                                && (addGapName == null || !addGapName.equals(c.name))
                                && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                            addMethod("ItemOptions", "show", c.name);
                            break;
                        }
                    }
                }
            }
        }

        if (updateHeaderVisName == null && overflowItemName != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                boolean readsOverflow = false;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        dialogsActDesc, m.name, m.paramDescs, dialogsActDesc)) {
                    if (overflowItemName.equals(rf.name)) {
                        readsOverflow = true;
                        break;
                    }
                }
                if (!readsOverflow) continue;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                        dialogsActDesc, m.name, m.paramDescs, null)) {
                    if (ref.paramDescs.length == 2
                            && "Landroid/view/View;".equals(ref.paramDescs[0])
                            && "F".equals(ref.paramDescs[1])
                            && "V".equals(ref.returnDesc)) {
                        addMethod("DialogsActivity", "updateHeaderVisibility", m.name);
                        updateHeaderVisName = m.name;
                        break;
                    }
                }
                if (updateHeaderVisName != null) break;
            }
        }
    }

    private void resolveSQLite(DexScanner scanner) {
        String dbDesc = "Lorg/telegram/SQLite/SQLiteDatabase;";
        String cursorDesc = "Lorg/telegram/SQLite/SQLiteCursor;";
        String stmtDesc = "Lorg/telegram/SQLite/SQLitePreparedStatement;";
        if (scanner.getMethods(dbDesc).isEmpty()) return;

        addMethod("SQLiteDatabase", "queryFinalized",
                scanner.findSingleMethod(dbDesc, false, cursorDesc, "Ljava/lang/String;", "[Ljava/lang/Object;"));
        addMethod("SQLiteDatabase", "executeFast",
                scanner.findSingleMethod(dbDesc, false, stmtDesc, "Ljava/lang/String;"));

        if (!scanner.getMethods(cursorDesc).isEmpty()) {
            addMethod("SQLiteCursor", "next", scanner.findSingleMethod(cursorDesc, false, "Z"));
            addMethod("SQLiteCursor", "byteBufferValue",
                    scanner.findSingleMethod(cursorDesc, false, "Lorg/telegram/tgnet/NativeByteBuffer;", "I"));

            String intValMeth = null;
            if (hasMethodNamed(scanner, cursorDesc, "intValue")) {
                intValMeth = "intValue";
            } else {
                for (DexScanner.MethodInfo m : scanner.getMethods(cursorDesc)) {
                    if (!m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                        for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(cursorDesc, m.name, m.paramDescs, cursorDesc)) {
                            if ("columnIntValue".equals(ref.name) && "I".equals(ref.returnDesc)
                                    && ref.paramDescs.length == 2 && "J".equals(ref.paramDescs[0]) && "I".equals(ref.paramDescs[1])) {
                                intValMeth = m.name;
                                break;
                            }
                        }
                        if (intValMeth != null) break;
                    }
                }
                if (intValMeth == null) {
                    intValMeth = scanner.findSingleMethod(cursorDesc, false, "I", "I");
                }
            }
            addMethod("SQLiteCursor", "intValue", intValMeth);
            addMethod("SQLiteCursor", "longValue", scanner.findSingleMethod(cursorDesc, false, "J", "I"));

            for (DexScanner.MethodInfo m : scanner.getMethods(cursorDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(cursorDesc, m.name, new String[0], stmtDesc)) {
                        if ("V".equals(ref.returnDesc) && ref.paramDescs.length == 0) {
                            addMethod("SQLiteCursor", "dispose", m.name);
                            addMethod("SQLitePreparedStatement", "dispose", ref.name);
                            break;
                        }
                    }
                }
            }
        }

        if (!scanner.getMethods(stmtDesc).isEmpty()) {
            addMethod("SQLitePreparedStatement", "bindByteBuffer",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "Lorg/telegram/tgnet/NativeByteBuffer;"));
            addMethod("SQLitePreparedStatement", "bindInteger",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "I"));
            addMethod("SQLitePreparedStatement", "bindLong",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "J"));

            String requeryMeth = null;
            if (hasZeroArgMethodNamed(scanner, stmtDesc, "requery")) {
                requeryMeth = "requery";
            } else {
                for (DexScanner.MethodInfo m : scanner.getMethods(stmtDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                        for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(stmtDesc, m.name, new String[0], stmtDesc)) {
                            if ("reset".equals(ref.name) && ref.paramDescs.length == 1 && "J".equals(ref.paramDescs[0])) {
                                requeryMeth = m.name;
                                break;
                            }
                        }
                        if (requeryMeth != null) break;
                    }
                }
            }
            addMethod("SQLitePreparedStatement", "requery", requeryMeth);
            String stepMeth = hasZeroArgMethodNamed(scanner, stmtDesc, "step")
                    ? "step"
                    : scanner.findSingleMethod(stmtDesc, false, "I");
            if (stepMeth == null) {
                // Fallback when R8 changes step() return type to void (e.g. Nekogram)
                for (DexScanner.MethodInfo m : scanner.getMethods(stmtDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                        for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(stmtDesc, m.name, new String[0], stmtDesc)) {
                            if ("step".equals(ref.name) && ref.paramDescs.length == 1 && "J".equals(ref.paramDescs[0])) {
                                stepMeth = m.name;
                                break;
                            }
                        }
                        if (stepMeth != null) break;
                    }
                }
            }
            addMethod("SQLitePreparedStatement", "step", stepMeth);
        }
    }

    private void resolveObfuscatedTgnetBase(DexScanner scanner, String msgObjDesc, String chatActDesc) {
        String cmDesc = DexScanner.javaToDesc(ClassNames.CONNECTIONS_MANAGER);
        for (DexScanner.MethodInfo m : scanner.getMethods(cmDesc)) {
            if (!m.isStatic && "sendRequestInternal".equals(m.name) && m.paramDescs.length >= 10
                    && "I".equals(m.paramDescs[5]) && "I".equals(m.paramDescs[6])
                    && "I".equals(m.paramDescs[7]) && "Z".equals(m.paramDescs[8]) && "I".equals(m.paramDescs[9])) {
                addClass(ClassNames.TL_OBJECT, m.paramDescs[0]);
                addClass(ClassNames.REQUEST_DELEGATE, m.paramDescs[1]);
                addClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP, m.paramDescs[2]);
                addClass(ClassNames.QUICK_ACK_DELEGATE, m.paramDescs[3]);
                addClass(ClassNames.WRITE_TO_SOCKET_DELEGATE, m.paramDescs[4]);
                for (DexScanner.MethodInfo rm : scanner.getMethods(m.paramDescs[1])) {
                    if (!rm.isStatic && "V".equals(rm.returnDesc) && rm.paramDescs.length == 2
                            && m.paramDescs[0].equals(rm.paramDescs[0])) {
                        addMethod("RequestDelegate", "run", rm.name);
                        break;
                    }
                }
                break;
            }
        }

        String tlObjectDesc = resolvedDescOrRaw(ClassNames.TL_OBJECT);
        if (msgObjDesc != null && tlObjectDesc != null) {
            String bestMsgOwnerField = null;
            String bestMsgDesc = null;
            String bestDeserName = null;
            int bestFieldCount = 0;
            for (DexScanner.FieldInfo f : scanner.getFields(msgObjDesc)) {
                if (f.isStatic || !scanner.isSubclassOf(f.typeDesc, tlObjectDesc)) continue;
                List<DexScanner.FieldInfo> tFields = scanner.getFields(f.typeDesc);
                if (tFields.size() < 50 || tFields.size() <= bestFieldCount) continue;
                for (DexScanner.MethodInfo tm : scanner.getMethods(f.typeDesc)) {
                    if (tm.isStatic && f.typeDesc.equals(tm.returnDesc) && tm.paramDescs.length == 3
                            && "I".equals(tm.paramDescs[1]) && "Z".equals(tm.paramDescs[2])) {
                        bestFieldCount = tFields.size();
                        bestMsgOwnerField = f.name;
                        bestMsgDesc = f.typeDesc;
                        bestDeserName = tm.name;
                        break;
                    }
                }
            }
            if (bestMsgDesc != null) {
                addClass(ClassNames.MESSAGE, bestMsgDesc);
                addField("MessageObject", "messageOwner", bestMsgOwnerField);
                addMethod("TLRPC$Message", "TLdeserialize", bestDeserName);

                // TLRPC$Message#readAttachPath: instance (?, J) -> V
                for (DexScanner.MethodInfo mm : scanner.getMethods(bestMsgDesc)) {
                    if (!mm.isStatic && "V".equals(mm.returnDesc) && mm.paramDescs.length == 2
                            && "J".equals(mm.paramDescs[1])) {
                        addMethod("TLRPC$Message", "readAttachPath", mm.name);
                        break;
                    }
                }

                // Resolve TLRPC$Message fields (flags, id, from_id, peer_id, date, message) from TL_message_secret readParams
                final String msgDescFinal = bestMsgDesc;
                final String tlObjFinal = tlObjectDesc;
                final String[] peerDescHolder = new String[1];
                for (DexScanner.FieldInfo f : scanner.getFields(msgDescFinal)) {
                    if (!f.isStatic && scanner.isSubclassOf(f.typeDesc, tlObjFinal)) {
                        List<DexScanner.FieldInfo> pfs = scanner.getFields(f.typeDesc);
                        int longs = 0;
                        for (DexScanner.FieldInfo pf : pfs) {
                            if (!pf.isStatic && "J".equals(pf.typeDesc)) longs++;
                        }
                        if (longs == 3 && pfs.size() <= 5) {
                            peerDescHolder[0] = f.typeDesc;
                            break;
                        }
                    }
                }
                if (peerDescHolder[0] != null) {
                    addClass("org.telegram.tgnet.TLRPC$Peer", peerDescHolder[0]);
                }

                scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                    if (!msgDescFinal.equals(scanner.getSuperClass(desc))) return false;
                    for (DexScanner.MethodInfo m : methods) {
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                                && "Z".equals(m.paramDescs[1]) && m.getInsnsSize() >= 100 && m.getInsnsSize() <= 250) {
                            List<DexScanner.FieldInfo> wfs = scanner.getWrittenFieldsInMethod(desc, m.name, m.paramDescs, msgDescFinal);
                            List<String> ints = new ArrayList<>();
                            List<String> peers = new ArrayList<>();
                            String firstStr = null;
                            for (DexScanner.FieldInfo wf : wfs) {
                                if ("I".equals(wf.typeDesc) && !ints.contains(wf.name)) {
                                    ints.add(wf.name);
                                } else if (peerDescHolder[0] != null && peerDescHolder[0].equals(wf.typeDesc) && !peers.contains(wf.name)) {
                                    peers.add(wf.name);
                                } else if ("Ljava/lang/String;".equals(wf.typeDesc) && firstStr == null) {
                                    firstStr = wf.name;
                                }
                            }
                            if (ints.size() >= 3 && peers.size() >= 2 && firstStr != null) {
                                addField("TLRPC$Message", "flags", ints.get(0));
                                addField("TLRPC$Message", "id", ints.get(1));
                                addField("TLRPC$Message", "date", ints.get(2));
                                addField("TLRPC$Message", "from_id", peers.get(0));
                                addField("TLRPC$Message", "peer_id", peers.get(1));
                                addField("TLRPC$Message", "message", firstStr);
                                return true;
                            }
                        }
                    }
                    return false;
                });

                // Resolve TLRPC$Message#ttl via method containing 0x7FFFFFFF (Integer.MAX_VALUE)
                String ttlField = null;
                if (chatActDesc != null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                        if (!m.isStatic && m.paramDescs.length == 1 && msgObjDesc.equals(m.paramDescs[0])
                                && m.containsConstInt(0x7FFFFFFF)) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(chatActDesc, m.name, m.paramDescs, bestMsgDesc)) {
                                if ("I".equals(rf.typeDesc)) {
                                    ttlField = rf.name;
                                    break;
                                }
                            }
                            if (ttlField != null) break;
                        }
                    }
                }
                if (ttlField == null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                        if (m.containsConstInt(0x7FFFFFFF)) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgObjDesc, m.name, m.paramDescs, bestMsgDesc)) {
                                if ("I".equals(rf.typeDesc)) {
                                    ttlField = rf.name;
                                    break;
                                }
                            }
                            if (ttlField != null) break;
                        }
                    }
                }
                if (ttlField != null) {
                    addField("TLRPC$Message", "ttl", ttlField);
                }
            }
        }
    }

    private void resolveObfuscatedTgnetConstructors(DexScanner scanner, String tlObjectDesc) {
        if (tlObjectDesc == null) return;
        Map<Integer, String> ctorTargets = new HashMap<>();
        ctorTargets.put(0xa20db0e5, ClassNames.TL_UPDATE_DELETE_MESSAGES);
        ctorTargets.put(0xc32d5b12, ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES);
        ctorTargets.put(0x6628562c, ClassNames.TL_ACCOUNT_UPDATE_STATUS);
        ctorTargets.put(0x0e306d3a, ClassNames.TL_MESSAGES_READ_HISTORY);
        ctorTargets.put(0xcc104937, ClassNames.TL_CHANNELS_READ_HISTORY);
        ctorTargets.put(0x84d19185, ClassNames.TL_MESSAGES_AFFECTED);
        ctorTargets.put(0x27bcbbfc, ClassNames.TL_INPUT_PEER_CHANNEL);
        ctorTargets.put(0x7f4b690a, ClassNames.TL_MESSAGES_READ_ENCRYPTED_HISTORY);
        ctorTargets.put(0xf731a9f4, ClassNames.TL_MESSAGES_READ_DISCUSSION);
        ctorTargets.put(0x36a73f77, ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS);
        ctorTargets.put(0xeab5dc38, ClassNames.TL_CHANNELS_READ_MESSAGE_CONTENTS);
        ctorTargets.put(0x58943ee2, ClassNames.TL_MESSAGES_SET_TYPING);
        ctorTargets.put(0x791451ed, ClassNames.TL_MESSAGES_SET_ENCRYPTED_TYPING);
        ctorTargets.put(0xa556dac8, ClassNames.TL_STORIES_READ_STORIES);
        ctorTargets.put(0xb2028afb, ClassNames.TL_STORIES_INCREMENT_STORY_VIEWS);

        // Peer & InputPeer concrete subtypes (for deterministic field resolution via serializeToStream)
        final String PEER_USER_KEY = "_TL_peerUser";
        final String PEER_CHAT_KEY = "_TL_peerChat";
        final String PEER_CHANNEL_KEY = "_TL_peerChannel";
        final String INPUT_PEER_USER_KEY = "_TL_inputPeerUser";
        final String INPUT_PEER_CHAT_KEY = "_TL_inputPeerChat";
        ctorTargets.put(0x59511722, PEER_USER_KEY);
        ctorTargets.put(0x36c6019a, PEER_CHAT_KEY);
        ctorTargets.put(0xa2a5371e, PEER_CHANNEL_KEY);
        ctorTargets.put(0xdde8a54c, INPUT_PEER_USER_KEY);
        ctorTargets.put(0x35a95cb9, INPUT_PEER_CHAT_KEY);

        Map<String, String> found = new HashMap<>();
        Map<String, List<String>> descToTargets = new HashMap<>();
        scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            if (!scanner.isSubclassOf(desc, tlObjectDesc)) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.codeOff == 0) continue;
                for (Map.Entry<Integer, String> e : ctorTargets.entrySet()) {
                    if (m.containsConstInt(e.getKey())) {
                        String target = e.getValue();
                        found.putIfAbsent(target, desc);
                        List<String> list = descToTargets.computeIfAbsent(desc, k -> new ArrayList<>());
                        if (!list.contains(target)) {
                            list.add(target);
                        }
                    }
                }
            }
            return false;
        });

        // Guard against R8 horizontal class merges: do NOT map collided TL classes that share one merged class
        for (Map.Entry<String, String> e : found.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("_")) continue;
            String desc = e.getValue();
            List<String> targets = descToTargets.get(desc);
            if (targets != null && targets.size() > 1) {
                continue;
            }
            addClass(key, desc);
        }

        // Resolve TLRPC$Peer (user_id, chat_id, channel_id) via serializeToStream on TL_peer* subclasses
        String peerDesc = classMap.containsKey("org.telegram.tgnet.TLRPC$Peer")
                ? DexScanner.javaToDesc(classMap.get("org.telegram.tgnet.TLRPC$Peer"))
                : (found.get(PEER_USER_KEY) != null ? scanner.getSuperClass(found.get(PEER_USER_KEY)) : null);
        if (peerDesc != null) {
            addClass("org.telegram.tgnet.TLRPC$Peer", peerDesc);
            addField("TLRPC$Peer", "user_id", findFirstReadLongField(scanner, found.get(PEER_USER_KEY), peerDesc));
            addField("TLRPC$Peer", "chat_id", findFirstReadLongField(scanner, found.get(PEER_CHAT_KEY), peerDesc));
            addField("TLRPC$Peer", "channel_id", findFirstReadLongField(scanner, found.get(PEER_CHANNEL_KEY), peerDesc));
        }

        // Resolve TLRPC$InputPeer (user_id, chat_id, channel_id) via serializeToStream on TL_inputPeer* subclasses
        String inputPeerChannelDesc = found.get(ClassNames.TL_INPUT_PEER_CHANNEL);
        String inputPeerDesc = inputPeerChannelDesc != null
                ? scanner.getSuperClass(inputPeerChannelDesc)
                : (found.get(INPUT_PEER_USER_KEY) != null ? scanner.getSuperClass(found.get(INPUT_PEER_USER_KEY)) : null);
        if (inputPeerDesc != null) {
            addClass("org.telegram.tgnet.TLRPC$InputPeer", inputPeerDesc);
            addField("TLRPC$InputPeer", "user_id", findFirstReadLongField(scanner, found.get(INPUT_PEER_USER_KEY), inputPeerDesc));
            addField("TLRPC$InputPeer", "chat_id", findFirstReadLongField(scanner, found.get(INPUT_PEER_CHAT_KEY), inputPeerDesc));
            addField("TLRPC$InputPeer", "channel_id", findFirstReadLongField(scanner, inputPeerChannelDesc, inputPeerDesc));
        }

        String delMsgDesc = classMap.containsKey(ClassNames.TL_UPDATE_DELETE_MESSAGES)
                ? found.get(ClassNames.TL_UPDATE_DELETE_MESSAGES) : null;
        if (delMsgDesc != null) {
            addField("TL_update$TL_updateDeleteMessages", "messages",
                    scanner.findSingleField(delMsgDesc, false, "Ljava/util/ArrayList;"));
        }
        String delChDesc = classMap.containsKey(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES)
                ? found.get(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES) : null;
        if (delChDesc != null) {
            addField("TL_update$TL_updateDeleteChannelMessages", "channel_id",
                    scanner.findSingleField(delChDesc, false, "J"));
            addField("TL_update$TL_updateDeleteChannelMessages", "messages",
                    scanner.findSingleField(delChDesc, false, "Ljava/util/ArrayList;"));
        }
        String statusDesc = classMap.containsKey(ClassNames.TL_ACCOUNT_UPDATE_STATUS)
                ? found.get(ClassNames.TL_ACCOUNT_UPDATE_STATUS) : null;
        if (statusDesc != null) {
            addField("TL_account$updateStatus", "offline",
                    scanner.findSingleField(statusDesc, false, "Z"));
        }
        String readHistDesc = classMap.containsKey(ClassNames.TL_MESSAGES_READ_HISTORY)
                ? found.get(ClassNames.TL_MESSAGES_READ_HISTORY) : null;
        if (readHistDesc != null) {
            addField("TLRPC$TL_messages_readHistory", "max_id",
                    scanner.findSingleField(readHistDesc, false, "I"));
            for (DexScanner.FieldInfo f : scanner.getFields(readHistDesc)) {
                if (!f.isStatic && f.typeDesc.startsWith("L")
                        && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")) {
                    addField("TLRPC$TL_messages_readHistory", "peer", f.name);
                    if (!classMap.containsKey("org.telegram.tgnet.TLRPC$InputPeer")) {
                        addClass("org.telegram.tgnet.TLRPC$InputPeer", f.typeDesc);
                    }
                    break;
                }
            }
        }
        String chReadHistDesc = classMap.containsKey(ClassNames.TL_CHANNELS_READ_HISTORY)
                ? found.get(ClassNames.TL_CHANNELS_READ_HISTORY) : null;
        if (chReadHistDesc != null) {
            addField("TLRPC$TL_channels_readHistory", "max_id",
                    scanner.findSingleField(chReadHistDesc, false, "I"));
            for (DexScanner.FieldInfo f : scanner.getFields(chReadHistDesc)) {
                if (!f.isStatic && f.typeDesc.startsWith("L")
                        && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")) {
                    addField("TLRPC$TL_channels_readHistory", "channel", f.name);
                    break;
                }
            }
        }
        String affectedDesc = classMap.containsKey(ClassNames.TL_MESSAGES_AFFECTED)
                ? found.get(ClassNames.TL_MESSAGES_AFFECTED) : null;
        if (affectedDesc != null) {
            List<String> intFields = new ArrayList<>();
            for (DexScanner.FieldInfo f : scanner.getFields(affectedDesc)) {
                if (!f.isStatic && "I".equals(f.typeDesc)) {
                    intFields.add(f.name);
                }
            }
            if (intFields.size() >= 2) {
                addField("TLRPC$TL_messages_affectedMessages", "pts", intFields.get(0));
                addField("TLRPC$TL_messages_affectedMessages", "pts_count", intFields.get(1));
            }
        }
    }

    private static String findFirstReadLongField(DexScanner scanner, String subDesc, String ownerDesc) {
        if (subDesc == null || ownerDesc == null) return null;
        for (DexScanner.MethodInfo m : scanner.getMethods(subDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(subDesc, m.name, m.paramDescs, ownerDesc)) {
                    if ("J".equals(rf.typeDesc)) {
                        return rf.name;
                    }
                }
            }
        }
        return null;
    }

    private String resolvedDescOrRaw(String className) {
        String mapped = classMap.get(className);
        return DexScanner.javaToDesc(mapped != null ? mapped : className);
    }

    private static String findClassByStringsWithFallback(DexScanner scanner, String pkgPrefix, String superDesc, String... requiredStrings) {
        String found = scanner.findClassByStrings(pkgPrefix, superDesc, requiredStrings);
        if (found == null && pkgPrefix != null) {
            found = scanner.findClassByStrings(null, superDesc, requiredStrings);
        }
        return found;
    }

    private void resolveDialogMessageField(DexScanner scanner, String msgCtrlDesc, String deleteMeth) {
        // 1. Mixed-mode raw field preservation
        for (DexScanner.FieldInfo f : scanner.getFields(msgCtrlDesc)) {
            if (!f.isStatic && "dialogMessage".equals(f.name)) {
                String getLong = findLongSparseArrayGet(scanner, f.typeDesc);
                if (getLong != null) {
                    addClass(ClassNames.LONG_SPARES_ARRAY, f.typeDesc);
                    addMethod("LongSparseArray", "get", getLong);
                    addField("MessagesController", "dialogMessage", f.name);
                    return;
                }
            }
        }

        // 2. Map all candidate LongSparseArray fields on MessagesController
        Map<String, String> lsaFieldTypes = new HashMap<>();
        Map<String, String> lsaTypeGetMethod = new HashMap<>();
        String firstLsaField = null;
        for (DexScanner.FieldInfo f : scanner.getFields(msgCtrlDesc)) {
            if (!f.isStatic && !f.typeDesc.startsWith("Lorg/telegram/")
                    && !f.typeDesc.startsWith("Landroid/") && !f.typeDesc.startsWith("Ljava/")) {
                String getLong = lsaTypeGetMethod.get(f.typeDesc);
                if (getLong == null && !lsaTypeGetMethod.containsKey(f.typeDesc)) {
                    getLong = findLongSparseArrayGet(scanner, f.typeDesc);
                    lsaTypeGetMethod.put(f.typeDesc, getLong);
                }
                if (getLong != null) {
                    lsaFieldTypes.put(f.name, f.typeDesc);
                    if (firstLsaField == null) firstLsaField = f.name;
                }
            }
        }
        if (lsaFieldTypes.isEmpty()) return;

        // 3. Find dialogMessage via 2-arg helper (J, ArrayList) or (ArrayList, J)
        Set<String> delCalledNames = new HashSet<>();
        if (deleteMeth != null) {
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(msgCtrlDesc, deleteMeth, null, msgCtrlDesc)) {
                if (ref.paramDescs.length == 2
                        && (("J".equals(ref.paramDescs[0]) && "Ljava/util/ArrayList;".equals(ref.paramDescs[1]))
                        || ("Ljava/util/ArrayList;".equals(ref.paramDescs[0]) && "J".equals(ref.paramDescs[1])))) {
                    delCalledNames.add(ref.name);
                }
            }
        }

        String helperMatchedField = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
            if (m.isStatic || m.paramDescs.length != 2) continue;
            boolean isTwoArg = ("J".equals(m.paramDescs[0]) && "Ljava/util/ArrayList;".equals(m.paramDescs[1]))
                    || ("Ljava/util/ArrayList;".equals(m.paramDescs[0]) && "J".equals(m.paramDescs[1]));
            if (!isTwoArg) continue;
            int insns = m.getInsnsSize();
            if (insns < 20 || insns > 220) continue;

            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, m.name, m.paramDescs, msgCtrlDesc)) {
                if (lsaFieldTypes.containsKey(rf.name)) {
                    if (delCalledNames.contains(m.name)) {
                        String lsaType = lsaFieldTypes.get(rf.name);
                        addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
                        addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
                        addField("MessagesController", "dialogMessage", rf.name);
                        return;
                    }
                    if (helperMatchedField == null && insns >= 40 && insns <= 160) {
                        helperMatchedField = rf.name;
                    }
                }
            }
        }
        if (helperMatchedField != null) {
            String lsaType = lsaFieldTypes.get(helperMatchedField);
            addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
            addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
            addField("MessagesController", "dialogMessage", helperMatchedField);
            return;
        }

        // 4. Fallback: field read directly in deleteMessages, or first LongSparseArray field
        if (deleteMeth != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, deleteMeth, null, msgCtrlDesc)) {
                if (lsaFieldTypes.containsKey(rf.name)) {
                    String lsaType = lsaFieldTypes.get(rf.name);
                    addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
                    addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
                    addField("MessagesController", "dialogMessage", rf.name);
                    return;
                }
            }
        }
        if (firstLsaField != null) {
            String lsaType = lsaFieldTypes.get(firstLsaField);
            addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
            addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
            addField("MessagesController", "dialogMessage", firstLsaField);
        }
    }

    private static String findLongSparseArrayGet(DexScanner scanner, String lsaDesc) {
        String fallback = null;
        for (DexScanner.MethodInfo lm : scanner.getMethods(lsaDesc)) {
            if (!lm.isStatic && "Ljava/lang/Object;".equals(lm.returnDesc)
                    && lm.paramDescs.length == 1 && "J".equals(lm.paramDescs[0])) {
                if ("get".equals(lm.name)) return "get";
                if (fallback == null) fallback = lm.name;
            }
        }
        return fallback;
    }

    private static boolean hasClass(DexScanner scanner, String desc) {
        return !scanner.getMethods(desc).isEmpty() || !scanner.getFields(desc).isEmpty();
    }

    private static boolean hasFieldNamed(DexScanner scanner, String classDesc, String fieldName) {
        for (DexScanner.FieldInfo f : scanner.getFields(classDesc)) {
            if (fieldName.equals(f.name)) return true;
        }
        return false;
    }

    private static boolean hasMethodNamed(DexScanner scanner, String classDesc, String methodName) {
        for (DexScanner.MethodInfo m : scanner.getMethods(classDesc)) {
            if (methodName.equals(m.name)) return true;
        }
        return false;
    }

    private static boolean hasZeroArgMethodNamed(DexScanner scanner, String classDesc, String methodName) {
        for (DexScanner.MethodInfo m : scanner.getMethods(classDesc)) {
            if (!m.isStatic && methodName.equals(m.name) && m.paramDescs.length == 0) return true;
        }
        return false;
    }

    private static boolean hasStaticFieldOfType(DexScanner scanner, String classDesc, String typeDesc) {
        for (DexScanner.FieldInfo f : scanner.getFields(classDesc)) {
            if (f.isStatic && typeDesc.equals(f.typeDesc)) return true;
        }
        return false;
    }

    private static Set<String> collectWrittenDialogFields(
            DexScanner scanner, String builderDesc, String dialogDesc, DexScanner.MethodInfo m) {
        Set<String> out = new HashSet<>();
        for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(builderDesc, m.name, m.paramDescs, dialogDesc)) {
            if (!f.isStatic) out.add(f.name);
        }
        for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(builderDesc, m.name, m.paramDescs, dialogDesc)) {
            for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(dialogDesc, ref.name, ref.paramDescs, dialogDesc)) {
                if (!f.isStatic) out.add(f.name);
            }
        }
        return out;
    }

    private static String getSuperDesc(ClassLoader classLoader, String className) {
        try {
            Class<?> cls = Class.forName(className, false, classLoader);
            Class<?> sup = cls.getSuperclass();
            if (sup != null) {
                return DexScanner.javaToDesc(sup.getName());
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
