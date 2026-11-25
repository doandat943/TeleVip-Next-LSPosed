package com.my.televip.obfuscate.auto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;

import com.my.televip.Class.ClassNames;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.struct.ClientObfuscationData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoMappingResolver {

    private static final String PREFS_NAME = "TeleVip_AutoMap";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_SCHEMA_VER = "schemaVersion";
    private static final String KEY_MAPPING_JSON = "mappingJson";
    private static final int SCHEMA_VERSION = 38;

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

        // 1. Reflection-based superclasses (immune to R8)
        String baseControllerDesc = getSuperDesc(classLoader, ClassNames.CONNECTIONS_MANAGER);
        String applicationLoaderDesc = getSuperDesc(classLoader, "org.telegram.messenger.ApplicationLoaderImpl");
        String baseFragmentDesc = getSuperDesc(classLoader, ClassNames.PROFILE_ACTIVITY);

        if (applicationLoaderDesc != null) {
            addClass(ClassNames.APPLICATION_LOADER, applicationLoaderDesc);
            addField("ApplicationLoader", "applicationContext",
                    scanner.findSingleField(applicationLoaderDesc, true, "Landroid/content/Context;"));
        }

        String launchActDesc = DexScanner.javaToDesc(ClassNames.LAUNCH_ACTIVITY);
        addField("LaunchActivity", "frameLayout",
                scanner.findSingleField(launchActDesc, false, "Landroid/widget/FrameLayout;"));

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
        for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
            if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 6
                    && "J".equals(m.paramDescs[2]) && "J".equals(m.paramDescs[3]) && "J".equals(m.paramDescs[4])) {
                msgObjDesc = m.paramDescs[0];
                chatActDesc = m.paramDescs[1];
                pvProviderDesc = m.paramDescs[5];
                chatOpenPhoto = m.name;
                break;
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
            String secretMsgDesc = "Lorg/telegram/tgnet/TLRPC$TL_message_secret;";
            for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    if (m.readsFieldNamed("voice")) addMethod("MessageObject", "isVoice", m.name);
                    else if (m.readsFieldNamed("noforwards")) addMethod("MessageObject", "canForwardMessage", m.name);
                    else if (m.checksInstanceOf(secretMsgDesc)) {
                        if (isSecretMethod == null || m.getInsnsSize() < isSecretMethod.getInsnsSize()) {
                            isSecretMethod = m;
                        }
                    }
                }
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
            if (msgObjDesc != null) {
                fillMenuName = scanner.findSingleMethod(chatActDesc, false, "V", msgObjDesc,
                        "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;");
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
                        ? new String[]{msgObjDesc, "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;"}
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
        if (baseControllerDesc != null) {
            String userConfigDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "userconfing");
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
                addField("UserConfig", "selectedAccount", selectedAccField);
                addMethod("UserConfig", "getCurrentUser",
                        scanner.findSingleMethod(userConfigDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_USER)));
                addMethod("UserConfig", "getClientUserId",
                        scanner.findSingleMethod(userConfigDesc, false, "J"));
                for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.readsFieldNamed("premium")) {
                        addMethod("UserConfig", "isPremium", m.name);
                        break;
                    }
                }
            }

            String msgStorageDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "DELETE FROM media_counts_v2");
            if (msgStorageDesc != null) {
                addClass(ClassNames.MESSAGES_STORAGE, msgStorageDesc);
                addMethod("MessagesStorage", "getInstance",
                        scanner.findSingleMethod(msgStorageDesc, true, msgStorageDesc, "I"));
                addMethod("MessagesStorage", "getDatabase",
                        scanner.findSingleMethod(msgStorageDesc, false, "Lorg/telegram/SQLite/SQLiteDatabase;"));
                addMethod("MessagesStorage", "markMessagesAsDeleted",
                        scanner.findSingleMethod(msgStorageDesc, false, "Ljava/util/ArrayList;",
                                "J", "Ljava/util/ArrayList;", "Z", "Z", "I", "I"));
                String tlMsgsDesc = DexScanner.javaToDesc(ClassNames.TL_MESSAGES_MESSAGES);
                DexScanner.MethodInfo bestPutMsgs = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(msgStorageDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc)) {
                        for (String p : m.paramDescs) {
                            if (tlMsgsDesc.equals(p)) {
                                if (bestPutMsgs == null || m.getInsnsSize() > bestPutMsgs.getInsnsSize()) {
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
                    if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("Lorg/telegram/messenger/")) {
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
                                if (bestPostRun == null || dm.getInsnsSize() < bestPostRun.getInsnsSize()) {
                                    bestPostRun = dm;
                                }
                            }
                        }
                        if (hasDelayedPost && bestPostRun != null) {
                            addMethod("MessagesStorage", "getStorageQueue", m.name);
                            addClass("org.telegram.messenger.DispatchQueue", m.returnDesc);
                            addMethod("DispatchQueue", "postRunnable", bestPostRun.name);
                            String utilDesc = DexScanner.javaToDesc(ClassNames.UTILITIES);
                            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(utilDesc, "<clinit>", new String[0], utilDesc)) {
                                if (m.returnDesc.equals(wf.typeDesc)) {
                                    addField("Utilities", "stageQueue", wf.name);
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }

            msgCtrlDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "telegramAntispamUserId");
            if (msgCtrlDesc != null) {
                addClass(ClassNames.MESSAGES_CONTROLLER, msgCtrlDesc);
                addMethod("MessagesController", "getInstance",
                        scanner.findSingleMethod(msgCtrlDesc, true, msgCtrlDesc, "I"));
                addMethod("MessagesController", "isChatNoForwards",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z", DexScanner.javaToDesc(ClassNames.TLRPC_CHAT)));
                String getChatBoxed = scanner.findSingleMethod(msgCtrlDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_CHAT), "Ljava/lang/Long;");
                if (getChatBoxed != null) {
                    addMethod("MessagesController", "getChat", getChatBoxed);
                } else {
                    String getChatPrim = scanner.findSingleMethod(msgCtrlDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_CHAT), "J");
                    if (getChatPrim != null) {
                        addMethod("MessagesController", "getChat", getChatPrim);
                    }
                }
                String getUserBoxed = scanner.findSingleMethod(msgCtrlDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_USER), "Ljava/lang/Long;");
                if (getUserBoxed != null) {
                    addMethod("MessagesController", "getUser", getUserBoxed);
                } else {
                    String getUserPrim = scanner.findSingleMethod(msgCtrlDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_USER), "J");
                    if (getUserPrim != null) {
                        addMethod("MessagesController", "getUser", getUserPrim);
                    }
                }
                addMethod("MessagesController", "processUpdateArray",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z",
                                "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Z", "I"));
                String deleteMeth = scanner.findSingleMethod(msgCtrlDesc, false, "V",
                        "Ljava/util/ArrayList;", "Ljava/util/ArrayList;",
                        DexScanner.javaToDesc(ClassNames.TLRPC_ENCRYPTED_CHAT),
                        "J", "Z", "I", "Z", "J", DexScanner.javaToDesc(ClassNames.TL_OBJECT), "I", "Z", "I");
                addMethod("MessagesController", "deleteMessages", deleteMeth);
                String procDiff = scanner.findMethodByString(msgCtrlDesc, "APPLY PTS", false, "V", "I", "I", "I");
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
                addField("MessagesController", "dialogMessagesByIds",
                        scanner.findSingleField(msgCtrlDesc, false, "Landroid/util/SparseArray;"));

                // Identify LongSparseArray type and dialogMessage field (prefer field read in deleteMessages)
                java.util.Set<String> delReadFields = new java.util.HashSet<>();
                if (deleteMeth != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, deleteMeth, null, msgCtrlDesc)) {
                        delReadFields.add(rf.name);
                    }
                }
                String fallbackLsaField = null;
                String fallbackLsaType = null;
                String fallbackLsaGet = null;
                for (DexScanner.FieldInfo f : scanner.getFields(msgCtrlDesc)) {
                    if (!f.isStatic && !f.typeDesc.startsWith("Lorg/telegram/") && !f.typeDesc.startsWith("Landroid/") && !f.typeDesc.startsWith("Ljava/")) {
                        List<DexScanner.MethodInfo> lsaMeths = scanner.getMethods(f.typeDesc);
                        String getLongName = null;
                        for (DexScanner.MethodInfo lm : lsaMeths) {
                            if (!lm.isStatic && "Ljava/lang/Object;".equals(lm.returnDesc) && lm.paramDescs.length == 1 && "J".equals(lm.paramDescs[0])) {
                                getLongName = lm.name;
                                break;
                            }
                        }
                        if (getLongName != null) {
                            if (fallbackLsaField == null) {
                                fallbackLsaField = f.name;
                                fallbackLsaType = f.typeDesc;
                                fallbackLsaGet = getLongName;
                            }
                            if (delReadFields.contains(f.name)) {
                                addClass(ClassNames.LONG_SPARES_ARRAY, f.typeDesc);
                                addMethod("LongSparseArray", "get", getLongName);
                                addField("MessagesController", "dialogMessage", f.name);
                                fallbackLsaField = null;
                                break;
                            }
                        }
                    }
                }
                if (fallbackLsaField != null) {
                    addClass(ClassNames.LONG_SPARES_ARRAY, fallbackLsaType);
                    addMethod("LongSparseArray", "get", fallbackLsaGet);
                    addField("MessagesController", "dialogMessage", fallbackLsaField);
                }

                resolveMessagesControllerExtras(scanner, msgCtrlDesc);
            }

            String fileLoaderDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "create load operation fileName=");
            if (fileLoaderDesc != null) {
                addClass(ClassNames.FILE_LOADER, fileLoaderDesc);
                addMethod("FileLoader", "getInstance",
                        scanner.findSingleMethod(fileLoaderDesc, true, fileLoaderDesc, "I"));
                DexScanner.MethodInfo bestGetDir = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(fileLoaderDesc)) {
                    if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && DexScanner.javaToDesc(ClassNames.MESSAGE).equals(m.paramDescs[0])) {
                        addMethod("FileLoader", "getPathToMessage", m.name);
                    } else if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && DexScanner.javaToDesc(ClassNames.TL_OBJECT).equals(m.paramDescs[0])) {
                        addMethod("FileLoader", "getPathToAttach", m.name);
                    } else if (m.isStatic && "Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && "I".equals(m.paramDescs[0])) {
                        if (bestGetDir == null || m.getInsnsSize() > bestGetDir.getInsnsSize()) {
                            bestGetDir = m;
                        }
                    }
                }
                if (bestGetDir != null) {
                    addMethod("FileLoader", "getDirectory", bestGetDir.name);
                }
            }

            // SendMessagesHelper, SendMessageParams, SendingMediaInfo, AccountInstance
            resolveSendMessagesHelper(scanner, baseControllerDesc);

            String notifCtrlDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "show extra notifications chatId ");
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
        String localeCtrlDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null, "LanguageName");
        if (localeCtrlDesc != null) {
            addClass(ClassNames.LOCALE_CONTROLLER, localeCtrlDesc);
            addMethod("LocaleController", "getInstance",
                    scanner.findSingleMethod(localeCtrlDesc, true, localeCtrlDesc));
            addField("LocaleController", "isRTL",
                    scanner.findSingleField(localeCtrlDesc, true, "Z"));
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
            if (fmtDay != null) {
                addMethod("LocaleController", "getFormatterDay", fmtDay);
            }
            String fmtDaySec = scanner.findMethodByString(localeCtrlDesc, "formatterDayWithSeconds24H", false, fastDateFmtDesc);
            if (fmtDaySec != null) {
                addMethod("LocaleController", "getFormatterDayWithSeconds", fmtDaySec);
            }
        }

        resolveStoriesController(scanner);
        resolveStoryItemHolder(scanner);
        resolveProfileActivity(scanner, msgCtrlDesc);
        resolveDialogsActivityProxy(scanner, baseFragmentDesc, actionBarDesc);

        String sharedConfigDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null, "mainconfig", "saveIncomingPhotos");
        if (sharedConfigDesc != null) {
            addClass(ClassNames.SHARED_CONFIG, sharedConfigDesc);
            resolveSharedConfigUpdate(scanner, sharedConfigDesc);
        }

        String browserDesc = scanner.findClassByStrings(null, null,
                "com.android.browser.application_id", "android.support.customtabs.extra.SESSION");
        if (browserDesc != null) {
            addClass(ClassNames.BROWSER, browserDesc);
            addMethod("Browser", "openUrl",
                    scanner.findSingleMethod(browserDesc, true, "V", "Landroid/content/Context;", "Ljava/lang/String;"));
        }

        String themeDesc = findTheme(scanner);
        if (themeDesc != null) {
            addClass(ClassNames.THEME, themeDesc);
            String isDark = findIsCurrentThemeDark(scanner, themeDesc);
            if (isDark != null) {
                addMethod("Theme", "isCurrentThemeDark", isDark);
            }
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
                String infoIsDark = scanner.findSingleMethod(themeInfoDesc, false, "Z");
                if (infoIsDark != null) {
                    addMethod("ThemeInfo", "isDark", infoIsDark);
                }
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
            String tlErrField = scanner.findReadStaticFieldBeforeString(
                    DexScanner.javaToDesc(ClassNames.LAUNCH_ACTIVITY),
                    "didReceivedNotification",
                    new String[]{"I", "I", "[Ljava/lang/Object;"},
                    notifCenterDesc, "I", "TL Error");
            if (tlErrField != null) {
                addField("NotificationCenter", "tlSchemeParseException", tlErrField);
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

        String chatMsgCellDesc = findChatMessageCell(scanner);
        if (chatMsgCellDesc != null) {
            addClass(ClassNames.CHAT_MESSAGE_CELL, chatMsgCellDesc);
            if (msgObjDesc != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(chatMsgCellDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                            && msgObjDesc.equals(m.paramDescs[0]) && m.readsFieldNamed("date")) {
                        addMethod("ChatMessageCell", "measureTime", m.name);

                        if (themeDesc != null) {
                            Map<String, Integer> paintReads = new HashMap<>();
                            for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(chatMsgCellDesc, m.name, new String[]{msgObjDesc}, themeDesc)) {
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
                        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(chatMsgCellDesc, m.name, new String[]{msgObjDesc}, chatMsgCellDesc)) {
                            if ("Ljava/lang/CharSequence;".equals(wf.typeDesc) && timeStrField == null) {
                                timeStrField = wf.name;
                            } else if ("I".equals(wf.typeDesc)) {
                                Integer c = intWriteCounts.get(wf.name);
                                intWriteCounts.put(wf.name, c == null ? 1 : c + 1);
                                if (timeStrField != null) {
                                    intWriteSeq.add(wf.name);
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
                        break;
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

        // 6. SettingsActivity, SettingCell$Factory, UItem
        resolveSettingsClasses(scanner);

        // 7. AlertDialog$Builder & OnButtonClickListener
        resolveAlertDialog(scanner);

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
        DexScanner.MethodInfo bestHasForDialog = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(storiesCtrlDesc)) {
            if (m.isStatic || !"Z".equals(m.returnDesc)) continue;
            if (m.paramDescs.length == 0) {
                boolean readsList = false;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(storiesCtrlDesc, m.name, new String[0], storiesCtrlDesc)) {
                    if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                        readsList = true;
                        break;
                    }
                }
                if (readsList) {
                    addMethod("StoriesController", "hasStories", m.name);
                } else if (m.readsFieldNamed("stories")) {
                    addMethod("StoriesController", "hasSelfStories", m.name);
                }
            } else if (m.paramDescs.length == 1 && "J".equals(m.paramDescs[0])) {
                if (m.readsFieldNamed("stories")
                        && !m.checksInstanceOf("Lorg/telegram/tgnet/TLRPC$TL_messageMediaVideoStream;")) {
                    if (bestHasForDialog == null || m.getInsnsSize() < bestHasForDialog.getInsnsSize()) {
                        bestHasForDialog = m;
                    }
                }
            }
        }
        if (bestHasForDialog != null) {
            addMethod("StoriesController", "hasStoriesForDialog", bestHasForDialog.name);
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
            for (DexScanner.MethodInfo m : scanner.getMethods(simpleTextViewDesc)) {
                if (!m.isStatic && "getText".equals(m.name) && "Ljava/lang/CharSequence;".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    getTextM = m.name;
                    break;
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
            if (!stvFields.isEmpty()) {
                for (DexScanner.MethodInfo m : profileMethods) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                    int sz = m.getInsnsSize();
                    if (sz <= 0 || sz >= 120) continue;

                    List<DexScanner.FieldInfo> reads = scanner.getReadInstanceFieldsInMethod(profileDesc, m.name, new String[0], profileDesc);
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

                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(profileDesc, m.name, new String[0], null)) {
                        if ("Z".equals(mr.returnDesc) && mr.paramDescs.length == 2
                                && "Ljava/lang/CharSequence;".equals(mr.paramDescs[0]) && "Z".equals(mr.paramDescs[1])
                                && scanner.isSubclassOf(matchedField.typeDesc, mr.ownerDesc)) {
                            onlineField = matchedField.name;
                            updateOnlineMethod = m.name;
                            break;
                        }
                    }
                    if (onlineField != null) break;
                }
                if (onlineField != null) {
                    addField("ProfileActivity", "onlineTextView", onlineField);
                    addMethod("ProfileActivity", "updateOnlineTextView", updateOnlineMethod);
                    for (DexScanner.FieldInfo sf : stvFields) {
                        if (!onlineField.equals(sf.name)) {
                            addField("ProfileActivity", "nameTextView", sf.name);
                            break;
                        }
                    }
                }
            } else {
                String arrDesc = "[" + simpleTextViewDesc;
                List<String> arrNames = new ArrayList<>();
                for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
                    if (!f.isStatic && arrDesc.equals(f.typeDesc)) {
                        arrNames.add(f.name);
                    }
                }
                if (arrNames.size() == 2) {
                    addField("ProfileActivity", "nameTextView", arrNames.get(0));
                    addField("ProfileActivity", "onlineTextView", arrNames.get(1));
                }
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

        String smiDesc = scanner.findClassByPredicate("Lorg/telegram/messenger/", (d, fs, ms) -> {
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
            for (DexScanner.FieldInfo f : scanner.getFields(smiDesc)) {
                if (!f.isStatic && "Z".equals(f.typeDesc)) {
                    addField("SendingMediaInfo", "isVideo", f.name);
                    break;
                }
            }
        }
    }

    private static String findNotificationCenter(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/messenger/", (desc, fields, methods) -> {
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
        });
    }

    private static String findAndroidUtilities(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/messenger/", (desc, fields, methods) -> {
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
        });
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
        String fuoDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null, "_uploaded", "_size");
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
        return scanner.findClassByPredicate("Lorg/telegram/messenger/", (desc, fields, methods) -> {
            if (methods.size() < 20) return false;
            int ints = 0, files = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (!f.isStatic) {
                    if ("I".equals(f.typeDesc)) ints++;
                    else if ("Ljava/io/File;".equals(f.typeDesc)) files++;
                }
            }
            return ints >= 15 && files >= 4;
        });
    }

    private static String findChatMessageCell(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) ->
                fields.size() > 300 && methods.size() > 200);
    }

    private void resolveSettingsClasses(DexScanner scanner) {
        final String[] uItemDescHolder = new String[1];
        final String[] factoryOfName = new String[1];
        final String[][] factoryOfParams = new String[1][];
        final String[] factoryBindName = new String[1];

        String factoryDesc = scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fields, methods) -> {
            if (desc.contains("/Components/")) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic && m.returnDesc.startsWith("Lorg/telegram/ui/Components/")
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

        if (factoryDesc == null || uItemDescHolder[0] == null) return;
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

        // Resolve UItem fields from exact Factory.of() overload bytecode writes (iput / iput-object)
        String firstInt = null;
        String cs1 = null, cs2 = null;
        for (DexScanner.FieldInfo f : scanner.getWrittenFieldsInMethod(factoryDesc, factoryOfName[0], factoryOfParams[0], uItemDesc)) {
            if ("I".equals(f.typeDesc) && firstInt == null) {
                firstInt = f.name;
            } else if ("Ljava/lang/CharSequence;".equals(f.typeDesc)) {
                if (cs1 == null) cs1 = f.name;
                else if (cs2 == null && !f.name.equals(cs1)) cs2 = f.name;
            }
        }
        if (firstInt != null) addField("UItem", "id", firstInt);
        if (cs1 != null) addField("UItem", "text", cs1);
        if (cs2 != null) addField("UItem", "subtext", cs2);

        // Find SettingsActivity: has static (Self, ArrayList) -> V and static (Self, UItem) -> V
        final String[] fillItemsName = new String[1];
        final String[] onClickName = new String[1];
        String settingsDesc = scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fields, methods) -> {
            if (desc.contains("$") || desc.contains("/Components/") || desc.contains("/Cells/")) return false;
            String fill = null, click = null;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2 && desc.equals(m.paramDescs[0])) {
                    if ("Ljava/util/ArrayList;".equals(m.paramDescs[1])) fill = m.name;
                    else if (uItemDesc.equals(m.paramDescs[1])) click = m.name;
                }
            }
            if (fill != null && click != null) {
                fillItemsName[0] = fill;
                onClickName[0] = click;
                return true;
            }
            return false;
        });

        if (settingsDesc != null) {
            addClass("org.telegram.ui.SettingsActivity", settingsDesc);
            addMethod("SettingsActivity", "fillItems", fillItemsName[0]);
            addMethod("SettingsActivity", "onClick", onClickName[0]);
        }
    }

    private static String findTheme(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", (desc, fields, methods) -> {
            int staticInts = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (f.isStatic && "I".equals(f.typeDesc)) staticInts++;
            }
            return staticInts > 200;
        });
    }

    private static String findIsCurrentThemeDark(DexScanner scanner, String themeDesc) {
        String best = null;
        int bestSize = 0;
        for (DexScanner.MethodInfo m : scanner.getMethods(themeDesc)) {
            if (m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.codeOff != 0) {
                int insns = m.getInsnsSize();
                if (insns > bestSize) {
                    bestSize = insns;
                    best = m.name;
                }
            }
        }
        return best;
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

        // OnButtonClickListener from (String/CharSequence, ?) -> V or Self
        String listenerDesc = null;
        String negBtn = null, neuBtn = null, posBtn = null;
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 2
                    && m.paramDescs[1].startsWith("Lorg/telegram/ui/ActionBar/")
                    && ("Ljava/lang/String;".equals(m.paramDescs[0]) || "Ljava/lang/CharSequence;".equals(m.paramDescs[0]))) {
                listenerDesc = m.paramDescs[1];
                if (m.containsConstInt(-1)) posBtn = m.name;
                else if (m.containsConstInt(-2)) negBtn = m.name;
                else if (m.containsConstInt(-3)) neuBtn = m.name;
                else if ("Ljava/lang/CharSequence;".equals(m.paramDescs[0]) && posBtn == null) posBtn = m.name;
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

        // setMessage / setTitle: (CharSequence) -> V or Self
        String cs1 = null, cs2 = null;
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && ("V".equals(m.returnDesc) || builderDesc.equals(m.returnDesc))
                    && m.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) {
                if (cs1 == null) cs1 = m.name;
                else if (cs2 == null) cs2 = m.name;
            }
        }
        if (cs1 != null) addMethod("AlertDialog$Builder", "setMessage", cs1);
        if (cs2 != null) addMethod("AlertDialog$Builder", "setTitle", cs2);

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
        if (dialogsActDesc == null) return;
        addClass(ClassNames.DIALOGS_ACTIVITY, dialogsActDesc);

        String proxyDrawableDesc = null;
        String setConnectedName = null;
        for (DexScanner.FieldInfo f : scanner.getFields(dialogsActDesc)) {
            if (f.isStatic || !f.typeDesc.startsWith("Lorg/telegram/ui/Components/")) continue;
            if (!scanner.isSubclassOf(f.typeDesc, "Landroid/graphics/drawable/Drawable;")) continue;
            String setConn = scanner.findSingleMethod(f.typeDesc, false, "V", "Z", "Z", "Z");
            if (setConn != null) {
                proxyDrawableDesc = f.typeDesc;
                setConnectedName = setConn;
                addClass(ClassNames.PROXY_DRAWABLE, proxyDrawableDesc);
                addMethod("ProxyDrawable", "setConnected", setConnectedName);
                addField("DialogsActivity", "proxyDrawable", f.name);
                break;
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
                    break;
                }
            }
        }

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
                addMethod("DialogsActivity", "updateHeaderVisibility", m.name);
                if (menuItemDesc != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            dialogsActDesc, m.name, m.paramDescs, dialogsActDesc)) {
                        if (menuItemDesc.equals(rf.typeDesc)) {
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
                        addField("DialogsActivity", "proxySubItem", rf.name);
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
                    for (int i = addIdx - 1; i >= 0; i--) {
                        DexScanner.MethodRef c = optCalls.get(i);
                        if (c.paramDescs.length == 0
                                && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                            addMethod("ItemOptions", "addGap", c.name);
                            break;
                        }
                    }
                    for (int i = addIdx + 1; i < optCalls.size(); i++) {
                        DexScanner.MethodRef c = optCalls.get(i);
                        if (c.paramDescs.length == 0
                                && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                            addMethod("ItemOptions", "show", c.name);
                            break;
                        }
                    }
                }
            }
        }
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
