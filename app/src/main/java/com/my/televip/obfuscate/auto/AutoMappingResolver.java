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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoMappingResolver {

    private static final String PREFS_NAME = "TeleVip_AutoMap";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_SCHEMA_VER = "schemaVersion";
    private static final String KEY_MAPPING_JSON = "mappingJson";
    private static final int SCHEMA_VERSION = 17;

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
        // PhotoViewer#setIsAboutToSwitchToIndex: smaller (I, Z, Z, Z) -> V
        DexScanner.MethodInfo bestSwitchIdx = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(photoViewerDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                    && "I".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                    && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                if (bestSwitchIdx == null || m.getInsnsSize() < bestSwitchIdx.getInsnsSize()) {
                    bestSwitchIdx = m;
                }
            }
        }
        if (bestSwitchIdx != null) {
            addMethod("PhotoViewer", "setIsAboutToSwitchToIndex", bestSwitchIdx.name);
        }

        // SecretMediaViewer#closePhoto: larger (Z, Z) -> V
        String secretViewerDesc = DexScanner.javaToDesc(ClassNames.SECRET_MEDIA_VIEWER);
        DexScanner.MethodInfo bestClosePhoto = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(secretViewerDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2
                    && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) {
                if (bestClosePhoto == null || m.getInsnsSize() > bestClosePhoto.getInsnsSize()) {
                    bestClosePhoto = m;
                }
            }
        }
        if (bestClosePhoto != null) {
            addMethod("SecretMediaViewer", "closePhoto", bestClosePhoto.name);
        }

        if (msgObjDesc != null) {
            addClass(ClassNames.MESSAGE_OBJECT, msgObjDesc);
            addField("MessageObject", "messageOwner",
                    scanner.findSingleField(msgObjDesc, false, DexScanner.javaToDesc(ClassNames.MESSAGE)));
            String instDialogId = scanner.findSingleMethod(msgObjDesc, false, "J");
            if (instDialogId != null) {
                addMethod("MessageObject", "getDialogId", instDialogId);
            } else {
                String staticDialogId = scanner.findSingleMethod(msgObjDesc, true, "J", DexScanner.javaToDesc(ClassNames.MESSAGE));
                if (staticDialogId != null) {
                    addMethod("MessageObject", "getDialogId", staticDialogId);
                }
            }
            for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    if (m.readsFieldNamed("voice")) addMethod("MessageObject", "isVoice", m.name);
                    else if (m.readsFieldNamed("noforwards")) addMethod("MessageObject", "canForwardMessage", m.name);
                    else if (m.readsFieldNamed("destroyTime") || m.readsFieldNamed("destroyTimeMillis")) {
                        addMethod("MessageObject", "isSecret", m.name);
                    }
                }
            }
        }

        if (chatActDesc != null) {
            addClass(ClassNames.CHAT_ACTIVITY, chatActDesc);
            addMethod("ChatActivity", "createView",
                    scanner.findSingleMethod(chatActDesc, false, "Landroid/view/View;", "Landroid/content/Context;"));
            if (msgObjDesc != null) {
                addMethod("ChatActivity", "fillMessageMenu",
                        scanner.findSingleMethod(chatActDesc, false, "V", msgObjDesc,
                                "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;"));
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
        }

        // ProfileActivity#updateProfileData: (Z) -> V reading online_count
        String profileDesc = DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY);
        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "Z".equals(m.paramDescs[0])
                    && m.readsFieldNamed("online_count")) {
                addMethod("ProfileActivity", "updateProfileData", m.name);
                break;
            }
        }

        if (pvProviderDesc != null) {
            addClass(ClassNames.PHOTO_VIEWER_PROVIDER, pvProviderDesc);
        }

        // 3. BaseController subclasses
        if (baseControllerDesc != null) {
            String userConfigDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "userconfing");
            if (userConfigDesc != null) {
                addClass(ClassNames.USER_CONFIG, userConfigDesc);
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
                for (DexScanner.MethodInfo m : scanner.getMethods(msgStorageDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("Lorg/telegram/messenger/")) {
                        String postRun = scanner.findSingleMethod(m.returnDesc, false, "Z", "Ljava/lang/Runnable;");
                        if (postRun == null) {
                            postRun = scanner.findSingleMethod(m.returnDesc, false, "V", "Ljava/lang/Runnable;");
                        }
                        if (postRun != null) {
                            addMethod("MessagesStorage", "getStorageQueue", m.name);
                            addClass("org.telegram.messenger.DispatchQueue", m.returnDesc);
                            addMethod("DispatchQueue", "postRunnable", postRun);
                            break;
                        }
                    }
                }
            }

            String msgCtrlDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "telegramAntispamUserId");
            if (msgCtrlDesc != null) {
                addClass(ClassNames.MESSAGES_CONTROLLER, msgCtrlDesc);
                addMethod("MessagesController", "getInstance",
                        scanner.findSingleMethod(msgCtrlDesc, true, msgCtrlDesc, "I"));
                addMethod("MessagesController", "isChatNoForwards",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z", DexScanner.javaToDesc(ClassNames.TLRPC_CHAT)));
                addMethod("MessagesController", "processUpdateArray",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z",
                                "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Z", "I"));
                String deleteMeth = scanner.findSingleMethod(msgCtrlDesc, false, "V",
                        "Ljava/util/ArrayList;", "Ljava/util/ArrayList;",
                        DexScanner.javaToDesc(ClassNames.TLRPC_ENCRYPTED_CHAT),
                        "J", "Z", "I", "Z", "J", DexScanner.javaToDesc(ClassNames.TL_OBJECT), "I", "Z", "I");
                addMethod("MessagesController", "deleteMessages", deleteMeth);
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
            }

            String fileLoaderDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", baseControllerDesc, "create load operation fileName=");
            if (fileLoaderDesc != null) {
                addClass(ClassNames.FILE_LOADER, fileLoaderDesc);
                addMethod("FileLoader", "getInstance",
                        scanner.findSingleMethod(fileLoaderDesc, true, fileLoaderDesc, "I"));
                for (DexScanner.MethodInfo m : scanner.getMethods(fileLoaderDesc)) {
                    if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && DexScanner.javaToDesc(ClassNames.MESSAGE).equals(m.paramDescs[0])) {
                        addMethod("FileLoader", "getPathToMessage", m.name);
                        break;
                    }
                }
            }

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
            addField("LocaleController", "currentLocale",
                    scanner.findSingleField(localeCtrlDesc, false, "Ljava/util/Locale;"));
            String fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "I", "[I");
            if (fmtShort == null) {
                fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "[I", "I");
            }
            addMethod("LocaleController", "formatShortNumber", fmtShort);
        }

        String sharedConfigDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null, "mainconfig", "saveIncomingPhotos");
        if (sharedConfigDesc != null) {
            addClass(ClassNames.SHARED_CONFIG, sharedConfigDesc);
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
        }

        String shadowCellDesc = findShadowSectionCell(scanner);
        if (shadowCellDesc != null) {
            addClass(ClassNames.SHADOW_SECTION_CELL, shadowCellDesc);
        }

        // 5. Signature-based core classes (NotificationCenter, AndroidUtilities, ActionBarMenuOnItemClick)
        String notifCenterDesc = findNotificationCenter(scanner);
        if (notifCenterDesc != null) {
            addClass(ClassNames.NOTIFICATION_CENTER, notifCenterDesc);
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
                for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(mcDesc, delMeth, null, notifCenterDesc)) {
                    if ("I".equals(rf.typeDesc)) {
                        addField("NotificationCenter", "messagesDeleted", rf.name);
                        break;
                    }
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

        String chatMsgCellDesc = findChatMessageCell(scanner);
        if (chatMsgCellDesc != null) {
            addClass(ClassNames.CHAT_MESSAGE_CELL, chatMsgCellDesc);
            if (msgObjDesc != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(chatMsgCellDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                            && msgObjDesc.equals(m.paramDescs[0]) && m.readsFieldNamed("date")) {
                        addMethod("ChatMessageCell", "measureTime", m.name);
                        String timeStrField = null;
                        java.util.List<String> intFieldsAfterStr = new java.util.ArrayList<>();
                        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(chatMsgCellDesc, m.name, new String[]{msgObjDesc}, chatMsgCellDesc)) {
                            if ("Ljava/lang/CharSequence;".equals(wf.typeDesc) && timeStrField == null) {
                                timeStrField = wf.name;
                            } else if (timeStrField != null && "I".equals(wf.typeDesc) && !intFieldsAfterStr.contains(wf.name)) {
                                intFieldsAfterStr.add(wf.name);
                            }
                        }
                        if (timeStrField != null) {
                            addField("ChatMessageCell", "currentTimeString", timeStrField);
                        }
                        if (intFieldsAfterStr.size() >= 2) {
                            addField("ChatMessageCell", "timeTextWidth", intFieldsAfterStr.get(0));
                            addField("ChatMessageCell", "timeWidth", intFieldsAfterStr.get(1));
                        }
                        break;
                    }
                }
            }
            // Find ChatActivity$ChatMessageCellDelegate on ChatMessageCell
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

        // 6. SettingsActivity, SettingCell$Factory, UItem
        resolveSettingsClasses(scanner);

        // 7. Settings UI Cells (TextCheckCell, TextSettingsCell, HeaderCell)
        resolveCells(scanner);

        // 8. AlertDialog$Builder & OnButtonClickListener
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

    private void resolveCells(DexScanner scanner) {
        // TextCheckCell: has (CharSequence, Z, Z) -> V and (CharSequence, String, Z, Z, Z) -> V, plus (Z) -> V (setChecked)
        final String[] tccSetAndCheck = new String[1];
        final String[] tccSetAndValAndCheck = new String[1];
        String tccDesc = scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) -> {
            String setCheck = null;
            String setValCheck = null;
            int textViews = 0;
            for (DexScanner.FieldInfo f : fields) {
                if (!f.isStatic && "Landroid/widget/TextView;".equals(f.typeDesc)) textViews++;
            }
            if (textViews < 1) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 3
                        && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])
                        && "Z".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2])) {
                    setCheck = m.name;
                }
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                        && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])
                        && "Ljava/lang/String;".equals(m.paramDescs[1])
                        && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3]) && "Z".equals(m.paramDescs[4])) {
                    setValCheck = m.name;
                }
            }
            if (setCheck != null && setValCheck != null) {
                tccSetAndCheck[0] = setCheck;
                tccSetAndValAndCheck[0] = setValCheck;
                return true;
            }
            return false;
        });
        if (tccDesc != null) {
            addClass(ClassNames.TEXT_CHECK_CELL, tccDesc);
            addMethod("TextCheckCell", "setTextAndCheck", tccSetAndCheck[0]);
            addMethod("TextCheckCell", "setTextAndValueAndCheck", tccSetAndValAndCheck[0]);
            String checkedM = scanner.findSingleMethod(tccDesc, false, "V", "Z");
            if (checkedM != null) addMethod("TextCheckCell", "setChecked", checkedM);
            String isCheckedM = scanner.findSingleMethod(tccDesc, false, "Z");
            if (isCheckedM != null) addMethod("TextCheckCell", "isChecked", isCheckedM);
            addField("TextCheckCell", "textView", scanner.findSingleField(tccDesc, false, "Landroid/widget/TextView;"));
        }

        // TextSettingsCell: has (CharSequence, Z) -> V and (CharSequence, CharSequence, Z, Z) -> V
        final String[] tscSetText = new String[1];
        final String[] tscSetTextAndVal = new String[1];
        String tscDesc = scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) -> {
            String st = null, stv = null;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc)) {
                    if (m.paramDescs.length == 2 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) {
                        st = m.name;
                    } else if (m.paramDescs.length == 4 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])
                            && "Ljava/lang/CharSequence;".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                        stv = m.name;
                    }
                }
            }
            if (st != null && stv != null) {
                tscSetText[0] = st;
                tscSetTextAndVal[0] = stv;
                return true;
            }
            return false;
        });
        if (tscDesc != null) {
            addClass(ClassNames.TEXT_SETTINGS_CELL, tscDesc);
            addMethod("TextSettingsCell", "setText", tscSetText[0]);
            addMethod("TextSettingsCell", "setTextAndValue", tscSetTextAndVal[0]);
            addField("TextSettingsCell", "textView", scanner.findSingleField(tscDesc, false, "Landroid/widget/TextView;"));
        }

        // HeaderCell: has (CharSequence) -> V, <init>(Context) -> V, and < 30 methods (no (CharSequence, Z)V)
        String hcDesc = scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) -> {
            if (methods.size() >= 30) return false;
            boolean hasSetText = false, hasCtxInit = false, hasTwoArg = false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) {
                    hasSetText = true;
                }
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 2 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) {
                    hasTwoArg = true;
                }
                if ("<init>".equals(m.name) && m.paramDescs.length == 1 && "Landroid/content/Context;".equals(m.paramDescs[0])) {
                    hasCtxInit = true;
                }
            }
            return hasSetText && hasCtxInit && !hasTwoArg;
        });
        if (hcDesc != null) {
            addClass(ClassNames.HEADER_CELL, hcDesc);
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

    private static String findShadowSectionCell(DexScanner scanner) {
        return scanner.findClassByPredicate("Lorg/telegram/ui/Cells/", (desc, fields, methods) -> {
            if (methods.size() > 10 || fields.size() > 6) return false;
            boolean hasCtxInit = false, hasCtxIIInit = false, hasOnMeasure = false, hasSetSize = false;
            for (DexScanner.MethodInfo m : methods) {
                if ("<init>".equals(m.name) && m.paramDescs.length == 1 && "Landroid/content/Context;".equals(m.paramDescs[0])) {
                    hasCtxInit = true;
                } else if ("<init>".equals(m.name) && m.paramDescs.length == 3 && "Landroid/content/Context;".equals(m.paramDescs[0])
                        && "I".equals(m.paramDescs[1]) && "I".equals(m.paramDescs[2])) {
                    hasCtxIIInit = true;
                } else if ("onMeasure".equals(m.name)) {
                    hasOnMeasure = true;
                } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    hasSetSize = true;
                }
            }
            return hasCtxInit && hasCtxIIInit && hasOnMeasure && hasSetSize;
        });
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
