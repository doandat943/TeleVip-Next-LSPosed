package com.magiii.televipnext.obfuscate.auto;

import com.magiii.televipnext.Class.ClassNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates core BaseController, Messenger, FileLoader, and SendMessagesHelper mappings and delegates domain resolution.
 */
final class CoreMappingResolver {

    private CoreMappingResolver() {}

    static void resolveAll(MappingContext ctx, DexScanner scanner, ClassLoader classLoader) {
        // 1. Reflection-based superclasses (immune to R8), with DEX fallback
        String baseControllerDesc = MappingContext.getSuperDesc(classLoader, ClassNames.CONNECTIONS_MANAGER);
        if (baseControllerDesc == null) {
            baseControllerDesc = scanner.getSuperClass(DexScanner.javaToDesc(ClassNames.CONNECTIONS_MANAGER));
        }
        String applicationLoaderDesc = null;
        if (MappingContext.hasClass(scanner, "Lorg/telegram/messenger/ApplicationLoader;")
                && MappingContext.hasStaticFieldOfType(scanner, "Lorg/telegram/messenger/ApplicationLoader;", "Landroid/content/Context;")) {
            applicationLoaderDesc = "Lorg/telegram/messenger/ApplicationLoader;";
        }
        if (applicationLoaderDesc == null) {
            String cur = scanner.getSuperClass("Lorg/telegram/messenger/ApplicationLoaderImpl;");
            if (cur == null) {
                cur = MappingContext.getSuperDesc(classLoader, "org.telegram.messenger.ApplicationLoaderImpl");
            }
            for (int hop = 0; hop < 4 && cur != null && !"Landroid/app/Application;".equals(cur); hop++) {
                if (MappingContext.hasStaticFieldOfType(scanner, cur, "Landroid/content/Context;")) {
                    applicationLoaderDesc = cur;
                    break;
                }
                cur = scanner.getSuperClass(cur);
            }
            if (applicationLoaderDesc == null) {
                applicationLoaderDesc = MappingContext.getSuperDesc(classLoader, "org.telegram.messenger.ApplicationLoaderImpl");
                if (applicationLoaderDesc == null) {
                    applicationLoaderDesc = scanner.getSuperClass("Lorg/telegram/messenger/ApplicationLoaderImpl;");
                }
                if (applicationLoaderDesc == null && MappingContext.hasClass(scanner, "Lorg/telegram/messenger/ApplicationLoader;")) {
                    applicationLoaderDesc = "Lorg/telegram/messenger/ApplicationLoader;";
                }
            }
        }
        String baseFragmentDesc = MappingContext.getSuperDesc(classLoader, ClassNames.PROFILE_ACTIVITY);
        if (baseFragmentDesc == null) {
            baseFragmentDesc = scanner.getSuperClass(DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY));
        }
        if (baseFragmentDesc != null) {
            String cur = baseFragmentDesc;
            for (int hop = 0; hop < 6; hop++) {
                String sup = scanner.getSuperClass(cur);
                if (sup == null && classLoader != null) {
                    sup = MappingContext.getSuperDesc(classLoader, DexScanner.descToJava(cur));
                }
                if (sup == null || "Ljava/lang/Object;".equals(sup)) {
                    break;
                }
                cur = sup;
            }
            baseFragmentDesc = cur;
        }

        if (applicationLoaderDesc != null) {
            ctx.addClass(ClassNames.APPLICATION_LOADER, applicationLoaderDesc);
            String appCtxField = MappingContext.hasFieldNamed(scanner, applicationLoaderDesc, "applicationContext")
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
            ctx.addField("ApplicationLoader", "applicationContext", appCtxField);
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
        ctx.addField("LaunchActivity", "frameLayout", launchFrameField);

        String actionBarDesc = null;
        if (baseFragmentDesc != null) {
            ctx.addClass("org.telegram.ui.ActionBar.BaseFragment", baseFragmentDesc);

            // LaunchActivity#presentFragment3: (BaseFragment, Z, Z) -> Z (17/17 unique)
            String presentFrag3 = scanner.findSingleMethod(launchActDesc, false, "Z", baseFragmentDesc, "Z", "Z");
            if (presentFrag3 == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(launchActDesc)) {
                    if (!m.isStatic && "presentFragment".equals(m.name) && "Z".equals(m.returnDesc)
                            && m.paramDescs.length == 3 && baseFragmentDesc.equals(m.paramDescs[0])
                            && "Z".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2])) {
                        presentFrag3 = m.name;
                        break;
                    }
                }
            }
            ctx.addMethod("LaunchActivity", "presentFragment3", presentFrag3);

            // BaseFragment#getFragmentView: () -> View
            String getFragView = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                if (!m.isStatic && "getFragmentView".equals(m.name)
                        && "Landroid/view/View;".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    getFragView = m.name;
                    break;
                }
            }
            if (getFragView == null) {
                getFragView = scanner.findSingleMethod(baseFragmentDesc, false, "Landroid/view/View;");
            }
            ctx.addMethod("BaseFragment", "getFragmentView", getFragView);

            // BaseFragment#presentFragment: (BaseFragment) -> Z
            String bfPresentFrag = null;
            DexScanner.MethodInfo smallestBfPresent = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                if (m.isStatic || m.name.startsWith("<") || m.name.startsWith("lambda$")) continue;
                if ("Z".equals(m.returnDesc) && m.paramDescs.length == 1
                        && baseFragmentDesc.equals(m.paramDescs[0])) {
                    if ("presentFragment".equals(m.name)) {
                        bfPresentFrag = m.name;
                        break;
                    }
                    if (m.getInsnsSize() > 0 && (smallestBfPresent == null || m.getInsnsSize() < smallestBfPresent.getInsnsSize())) {
                        smallestBfPresent = m;
                    }
                }
            }
            if (bfPresentFrag == null && smallestBfPresent != null) {
                bfPresentFrag = smallestBfPresent.name;
            }
            ctx.addMethod("BaseFragment", "presentFragment", bfPresentFrag);

            // BaseFragment#createView: (Context) -> android.view.View
            String bfCreateView = MappingContext.hasMethodNamed(scanner, baseFragmentDesc, "createView")
                    ? "createView"
                    : scanner.findSingleMethod(baseFragmentDesc, false, "Landroid/view/View;", "Landroid/content/Context;");
            ctx.addMethod("BaseFragment", "createView", bfCreateView);

            // BaseFragment#isSwipeBackEnabled: (MotionEvent) -> Z
            String bfSwipeBack = scanner.findSingleMethod(baseFragmentDesc, false, "Z", "Landroid/view/MotionEvent;");
            if (bfSwipeBack != null) {
                ctx.addMethod("BaseFragment", "isSwipeBackEnabled", bfSwipeBack);
            }

            // BaseFragment#onResume / DialogsActivity#onResume
            if (MappingContext.hasZeroArgMethodNamed(scanner, baseFragmentDesc, "onResume")) {
                ctx.addMethod("BaseFragment", "onResume", "onResume");
                ctx.addMethod("DialogsActivity", "onResume", "onResume");
                ctx.addMethod("MainTabsActivity", "onResume", "onResume");
            } else {
                Set<String> dialogWrittenZ = new HashSet<>();
                for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                        boolean writesDlg = false;
                        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                                baseFragmentDesc, m.name, new String[0], baseFragmentDesc)) {
                            if ("Landroid/app/Dialog;".equals(wf.typeDesc)) {
                                writesDlg = true;
                                break;
                            }
                        }
                        if (writesDlg) {
                            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                                    baseFragmentDesc, m.name, new String[0], baseFragmentDesc)) {
                                if ("Z".equals(wf.typeDesc)) dialogWrittenZ.add(wf.name);
                            }
                        }
                    }
                }
                for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                        boolean writesDlg = false;
                        boolean writesSameZ = false;
                        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                                baseFragmentDesc, m.name, new String[0], baseFragmentDesc)) {
                            if ("Landroid/app/Dialog;".equals(wf.typeDesc)) writesDlg = true;
                            if ("Z".equals(wf.typeDesc) && dialogWrittenZ.contains(wf.name)) writesSameZ = true;
                        }
                        if (writesSameZ && !writesDlg) {
                            ctx.addMethod("BaseFragment", "onResume", m.name);
                            ctx.addMethod("DialogsActivity", "onResume", m.name);
                            ctx.addMethod("MainTabsActivity", "onResume", m.name);
                            break;
                        }
                    }
                }
            }

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
            if (bestActionBar == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(baseFragmentDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 1 && "Landroid/content/Context;".equals(m.paramDescs[0])
                            && m.returnDesc.startsWith("L")
                            && !m.returnDesc.startsWith("Landroid/") && !m.returnDesc.startsWith("Ljava/")
                            && scanner.isSubclassOf(m.returnDesc, "Landroid/widget/FrameLayout;")) {
                        int mCnt = scanner.getMethods(m.returnDesc).size();
                        if (mCnt >= 30 && mCnt > bestAbMethods) {
                            bestAbMethods = mCnt;
                            bestActionBar = m.returnDesc;
                        }
                    }
                }
            }
            if (bestActionBar != null) {
                actionBarDesc = bestActionBar;
                ctx.addClass("org.telegram.ui.ActionBar.ActionBar", bestActionBar);
                String abField = MappingContext.hasFieldNamed(scanner, baseFragmentDesc, "actionBar")
                        ? "actionBar"
                        : scanner.findSingleField(baseFragmentDesc, false, bestActionBar);
                if (abField != null) {
                    ctx.addField("BaseFragment", "actionBar", abField);
                }
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
            ctx.addMethod("PhotoViewer", "openPhoto", callerOpenPhoto);
        } else if (chatOpenPhoto != null) {
            ctx.addMethod("PhotoViewer", "openPhoto", chatOpenPhoto);
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
            ctx.addMethod("PhotoViewer", "getInstance", bestPvInst.name);
        }

        // PhotoViewer#setParentActivity: prefer 1-arg (Activity) -> V, else largest (Activity, ...) -> V
        String setParentExact = scanner.findSingleMethod(photoViewerDesc, false, "V", "Landroid/app/Activity;");
        if (setParentExact != null) {
            ctx.addMethod("PhotoViewer", "setParentActivity", setParentExact);
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
                ctx.addMethod("PhotoViewer", "setParentActivity", bestSetParent.name);
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
            ctx.addMethod("PhotoViewer", "setIsAboutToSwitchToIndex", bestSwitchIdx.name);
            if (smallSwitchIdx != null && !smallSwitchIdx.name.equals(bestSwitchIdx.name)) {
                Set<String> smallSubItems = new HashSet<>();
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(photoViewerDesc, smallSwitchIdx.name, new String[]{"I", "Z", "Z", "Z"}, photoViewerDesc)) {
                    if (rf.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                            || (rf.typeDesc.startsWith("L") && !rf.typeDesc.startsWith("Ljava/")
                            && !rf.typeDesc.startsWith("Landroid/") && !rf.typeDesc.startsWith("["))) {
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
                    ctx.addField("PhotoViewer", "galleryButton", galleryField);
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
            ctx.addMethod("SecretMediaViewer", "closePhoto", bestClosePhoto.name);
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
            String resolvedOnClose = null;
            if (openMediaMethod != null) {
                ctx.addMethod("SecretMediaViewer", "openMedia", openMediaMethod.name);
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(secretViewerDesc, openMediaMethod.name, openMediaMethod.paramDescs, secretViewerDesc)) {
                    if ("Ljava/lang/Runnable;".equals(wf.typeDesc)) {
                        resolvedOnClose = wf.name;
                        ctx.addField("SecretMediaViewer", "onClose", wf.name);
                        break;
                    }
                }
            }
            if (resolvedOnClose == null && bestClosePhoto != null) {
                // Fallback when openMedia was R8-inlined: closePhoto writes onOpenAnimation 3x & onClose once (and reads onClose >= 2x)
                Map<String, Integer> readCounts = new LinkedHashMap<>();
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        secretViewerDesc, bestClosePhoto.name, bestClosePhoto.paramDescs, secretViewerDesc)) {
                    if ("Ljava/lang/Runnable;".equals(rf.typeDesc)) {
                        readCounts.put(rf.name, readCounts.getOrDefault(rf.name, 0) + 1);
                    }
                }
                Map<String, Integer> writeCounts = new LinkedHashMap<>();
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                        secretViewerDesc, bestClosePhoto.name, bestClosePhoto.paramDescs, secretViewerDesc)) {
                    if ("Ljava/lang/Runnable;".equals(wf.typeDesc)) {
                        writeCounts.put(wf.name, writeCounts.getOrDefault(wf.name, 0) + 1);
                    }
                }
                for (Map.Entry<String, Integer> e : readCounts.entrySet()) {
                    if (e.getValue() >= 2 && Integer.valueOf(1).equals(writeCounts.get(e.getKey()))) {
                        resolvedOnClose = e.getKey();
                        break;
                    }
                }
                if (resolvedOnClose != null) {
                    ctx.addField("SecretMediaViewer", "onClose", resolvedOnClose);
                }
            }

            ctx.addClass(ClassNames.MESSAGE_OBJECT, msgObjDesc);
            ctx.addField("MessageObject", "messageOwner",
                    scanner.findSingleField(msgObjDesc, false, DexScanner.javaToDesc(ClassNames.MESSAGE)));
            String instDialogId = scanner.findSingleMethod(msgObjDesc, false, "J");
            if (instDialogId != null) {
                ctx.addMethod("MessageObject", "getDialogId", instDialogId);
            } else {
                String msgDesc = ctx.resolvedDescOrRaw(ClassNames.MESSAGE);
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
                    ctx.addMethod("MessageObject", "getDialogId", staticDialogId);
                }
            }
            DexScanner.MethodInfo isSecretMethod = null;
            DexScanner.MethodInfo bestCanForward = null;
            boolean exactCanForward = false;
            String secretMsgDesc = "Lorg/telegram/tgnet/TLRPC$TL_message_secret;";
            for (DexScanner.MethodInfo m : scanner.getMethods(msgObjDesc)) {
                if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    if (m.readsFieldNamed("voice")) {
                        ctx.addMethod("MessageObject", "isVoice", m.name);
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
                ctx.addMethod("MessageObject", "canForwardMessage", bestCanForward.name);
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
                ctx.addMethod("MessageObject", "isSecret", isSecretMethod.name);
            }
            resolveMessageObjectAudio(ctx, scanner, msgObjDesc);
        }

        if (chatActDesc != null) {
            ctx.addClass(ClassNames.CHAT_ACTIVITY, chatActDesc);
            String chatCreateView = MappingContext.hasMethodNamed(scanner, chatActDesc, "createView")
                    ? "createView"
                    : scanner.findSingleMethod(chatActDesc, false, "Landroid/view/View;", "Landroid/content/Context;");
            ctx.addMethod("ChatActivity", "createView", chatCreateView);

            String chatSwipeBack = scanner.findSingleMethod(chatActDesc, false, "Z", "Landroid/view/MotionEvent;");
            if (chatSwipeBack == null) chatSwipeBack = ctx.methodMap.get("BaseFragment#isSwipeBackEnabled");
            if (chatSwipeBack != null) ctx.addMethod("ChatActivity", "isSwipeBackEnabled", chatSwipeBack);

            String scrollToMsg = scanner.findSingleMethod(chatActDesc, false, "V", "I", "I", "Z", "I", "Z", "I");
            if (scrollToMsg != null) {
                ctx.addMethod("ChatActivity", "scrollToMessageId", scrollToMsg);
            }
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
                ctx.addMethod("ChatActivity", "fillMessageMenu", fillMenuName);
                boolean ttlObf = !MappingContext.hasFieldNamed(scanner, DexScanner.javaToDesc(ClassNames.MESSAGE), "ttl");
                for (DexScanner.MethodInfo m : scanner.getMethods(chatActDesc)) {
                    if (!m.isStatic && !"V".equals(m.returnDesc)) {
                        boolean nonStdRet = m.returnDesc.startsWith("L")
                                && !m.returnDesc.startsWith("Ljava/") && !m.returnDesc.startsWith("Landroid/");
                        if (m.paramDescs.length == 2 && msgObjDesc.equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                                && ((m.readsFieldNamed("ttl") && m.readsFieldNamed("destroyTime"))
                                    || (ttlObf && m.containsConstInt(0x7FFFFFFF) && nonStdRet))) {
                            ctx.addMethod("ChatActivity", "sendSecretMessageRead", m.name);
                        } else if (m.paramDescs.length == 1 && msgObjDesc.equals(m.paramDescs[0])
                                && m.containsConstInt(0x7FFFFFFF)
                                && (m.readsFieldNamed("ttl") || (ttlObf && nonStdRet))) {
                            ctx.addMethod("ChatActivity", "sendSecretMediaDelete", m.name);
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
                    ctx.addMethod("ChatActivity", "hasSelectedNoforwardsMessage", m.name);
                }
            }
            if (bestOpt != null && bestOpt.getInsnsSize() > 500) {
                ctx.addMethod("ChatActivity", "processSelectedOption", bestOpt.name);
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
                        ctx.addField("ChatActivity", "selectedObject", bestSelectedObj);
                    }
                }
            }

            UiMappingResolver.resolveChatPinnedMessageView(ctx, scanner, chatActDesc);
        }

        if (pvProviderDesc != null) {
            ctx.addClass(ClassNames.PHOTO_VIEWER_PROVIDER, pvProviderDesc);
            if (msgObjDesc != null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(pvProviderDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 5 && msgObjDesc.equals(m.paramDescs[0])
                            && "I".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3]) && "Z".equals(m.paramDescs[4])) {
                        ctx.addMethod("PhotoViewer$PhotoViewerProvider", "getPlaceForPhoto", m.name);
                        String placeDesc = m.returnDesc;
                        if (placeDesc != null && placeDesc.startsWith("L")) {
                            ctx.addClass("org.telegram.ui.PhotoViewer$PlaceProviderObject", placeDesc);
                            String imgRecvField = scanner.findSingleField(placeDesc, false, "Lorg/telegram/messenger/ImageReceiver;");
                            if (imgRecvField != null) {
                                ctx.addField("PhotoViewer$PlaceProviderObject", "imageReceiver", imgRecvField);
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
        boolean tgnetObfuscated = !MappingContext.hasClass(scanner, DexScanner.javaToDesc(ClassNames.MESSAGE))
                || !MappingContext.hasClass(scanner, DexScanner.javaToDesc(ClassNames.TL_OBJECT))
                || !MappingContext.hasClass(scanner, DexScanner.javaToDesc(ClassNames.TL_UPDATE_DELETE_MESSAGES));
        String tlObjectDescEarly = TgnetMappingResolver.extractConnectionsManagerTypes(ctx, scanner);
        if (tgnetObfuscated) {
            TgnetMappingResolver.resolveObfuscatedTgnetBase(ctx, scanner, msgObjDesc, chatActDesc);
        }
        String tlObjectDesc = ctx.resolvedDescOrRaw(ClassNames.TL_OBJECT);
        if (tlObjectDescEarly != null && DexScanner.javaToDesc(ClassNames.TL_OBJECT).equals(tlObjectDesc)) {
            tlObjectDesc = tlObjectDescEarly;
        }
        String tlEncChatDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_ENCRYPTED_CHAT);
        String tlMessageDesc = ctx.resolvedDescOrRaw(ClassNames.MESSAGE);

        if (baseControllerDesc != null) {
            userConfigDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "userconfing");
            if (userConfigDesc == null) {
                userConfigDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "userconfig");
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
                ctx.addClass(ClassNames.USER_CONFIG, userConfigDesc);
                if (baseFragmentDesc != null) {
                    ctx.addMethod("BaseFragment", "getUserConfig",
                            scanner.findSingleMethod(baseFragmentDesc, false, userConfigDesc));
                }
                String getInstName = scanner.findSingleMethod(userConfigDesc, true, userConfigDesc, "I");
                ctx.addMethod("UserConfig", "getInstance", getInstName);
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
                ctx.addField("UserConfig", "selectedAccount", selectedAccField);
                if (tgnetObfuscated && tlObjectDesc != null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                        if (!m.isStatic && m.paramDescs.length == 0
                                && scanner.isSubclassOf(m.returnDesc, tlObjectDesc)
                                && scanner.getFields(m.returnDesc).size() >= 40) {
                            ctx.addClass(ClassNames.TLRPC_USER, m.returnDesc);
                            ctx.addMethod("UserConfig", "getCurrentUser", m.name);
                            break;
                        }
                    }
                } else {
                    ctx.addMethod("UserConfig", "getCurrentUser",
                            scanner.findSingleMethod(userConfigDesc, false, DexScanner.javaToDesc(ClassNames.TLRPC_USER)));
                }
                ctx.addMethod("UserConfig", "getClientUserId",
                        scanner.findSingleMethod(userConfigDesc, false, "J"));
                for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0 && m.readsFieldNamed("premium")) {
                        ctx.addMethod("UserConfig", "isPremium", m.name);
                        break;
                    }
                }
                if (!ctx.methodMap.containsKey("UserConfig#isPremium")) {
                    String tlUserDescForPrem = ctx.resolvedDescOrRaw(ClassNames.TLRPC_USER);
                    for (DexScanner.MethodInfo m : scanner.getMethods(userConfigDesc)) {
                        if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) {
                            boolean readsUserField = false;
                            boolean readsLockObj = false;
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                                    userConfigDesc, m.name, new String[0], null)) {
                                if (tlUserDescForPrem.equals(rf.typeDesc)) readsUserField = true;
                                else if ("Ljava/lang/Object;".equals(rf.typeDesc)) readsLockObj = true;
                            }
                            if (readsUserField && !readsLockObj) {
                                ctx.addMethod("UserConfig", "isPremium", m.name);
                                break;
                            }
                        }
                    }
                }
                String resolvedUserDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_USER);
                if (!MappingContext.hasFieldNamed(scanner, resolvedUserDesc, "phone")) {
                    String phoneFld = TgnetMappingResolver.resolveUserPhoneField(scanner, resolvedUserDesc);
                    if (phoneFld != null) {
                        ctx.addField("TLRPC$User", "phone", phoneFld);
                    }
                }
            }

            String msgStorageDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "DELETE FROM media_counts_v2");
            if (msgStorageDesc == null) {
                String cand = scanner.findClassByStrings(null, null, "DELETE FROM media_counts_v2");
                if (cand != null && scanner.getMethods(cand).size() >= 250) {
                    msgStorageDesc = cand;
                }
            }
            if (msgStorageDesc == null) {
                msgStorageDesc = scanner.findClassByPredicate(null, (d, fs, ms) -> {
                    if (ms.size() < 300) return false;
                    boolean hasDb = false;
                    for (DexScanner.FieldInfo fi : fs) {
                        if (!fi.isStatic && "Lorg/telegram/SQLite/SQLiteDatabase;".equals(fi.typeDesc)) {
                            hasDb = true;
                            break;
                        }
                    }
                    if (!hasDb) return false;
                    for (DexScanner.MethodInfo m : ms) {
                        if (m.usesString("messages_v2")) return true;
                    }
                    return false;
                });
            }
            if (msgStorageDesc != null) {
                ctx.addClass(ClassNames.MESSAGES_STORAGE, msgStorageDesc);
                ctx.addMethod("MessagesStorage", "getInstance",
                        MappingContext.hasMethodNamed(scanner, msgStorageDesc, "getInstance")
                                ? "getInstance"
                                : scanner.findSingleMethod(msgStorageDesc, true, msgStorageDesc, "I"));
                ctx.addMethod("MessagesStorage", "getDatabase",
                        MappingContext.hasMethodNamed(scanner, msgStorageDesc, "getDatabase")
                                ? "getDatabase"
                                : scanner.findSingleMethod(msgStorageDesc, false, "Lorg/telegram/SQLite/SQLiteDatabase;"));
                ctx.addMethod("MessagesStorage", "markMessagesAsDeleted",
                        MappingContext.hasMethodNamed(scanner, msgStorageDesc, "markMessagesAsDeleted")
                                ? "markMessagesAsDeleted"
                                : scanner.findSingleMethod(msgStorageDesc, false, "Ljava/util/ArrayList;",
                                "J", "Ljava/util/ArrayList;", "Z", "Z", "I", "I"));
                String tlMsgsDesc = ctx.resolvedDescOrRaw(ClassNames.TL_MESSAGES_MESSAGES);
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
                if (bestPutMsgs == null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(msgStorageDesc)) {
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                                && m.paramDescs[0].startsWith("L") && !m.paramDescs[0].startsWith("Ljava/") && !m.paramDescs[0].startsWith("Landroid/")
                                && "Z".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2])
                                && "Z".equals(m.paramDescs[3]) && "I".equals(m.paramDescs[4])
                                && m.getInsnsSize() > 1500) {
                            if (bestPutMsgs == null || m.getInsnsSize() > bestPutMsgs.getInsnsSize()) {
                                bestPutMsgs = m;
                            }
                        }
                    }
                }
                if (bestPutMsgs != null) {
                    ctx.addMethod("MessagesStorage", "putMessages", bestPutMsgs.name);
                    String resolvedTlMsgsDesc = tlMsgsDesc;
                    if (!MappingContext.hasClass(scanner, tlMsgsDesc) && bestPutMsgs.paramDescs.length > 0 && bestPutMsgs.paramDescs[0].startsWith("L")) {
                        resolvedTlMsgsDesc = bestPutMsgs.paramDescs[0];
                        ctx.addClass(ClassNames.TL_MESSAGES_MESSAGES, resolvedTlMsgsDesc);
                    }
                    if (!MappingContext.hasFieldNamed(scanner, resolvedTlMsgsDesc, "messages")) {
                        final String baseMsgsDesc = resolvedTlMsgsDesc;
                        String[] foundMsgsFld = new String[1];
                        scanner.findClassByPredicate(null, (d, fs, ms) -> {
                            if (!baseMsgsDesc.equals(scanner.getSuperClass(d)) && !baseMsgsDesc.equals(d)) return false;
                            for (DexScanner.MethodInfo sm : ms) {
                                if (!sm.isStatic && "V".equals(sm.returnDesc) && sm.paramDescs.length == 1 && sm.getInsnsSize() > 20) {
                                    List<String> arrReads = new ArrayList<>();
                                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(d, sm.name, sm.paramDescs, baseMsgsDesc)) {
                                        if ("Ljava/util/ArrayList;".equals(rf.typeDesc) && !arrReads.contains(rf.name)) {
                                            arrReads.add(rf.name);
                                        }
                                    }
                                    if (arrReads.size() >= 2) {
                                        foundMsgsFld[0] = arrReads.get(0);
                                        return true;
                                    }
                                }
                            }
                            return false;
                        });
                        if (foundMsgsFld[0] == null) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                                    msgStorageDesc, bestPutMsgs.name, bestPutMsgs.paramDescs, baseMsgsDesc)) {
                                if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                                    foundMsgsFld[0] = rf.name;
                                    break;
                                }
                            }
                        }
                        if (foundMsgsFld[0] != null) {
                            ctx.addField("TLRPC$messages_Messages", "messages", foundMsgsFld[0]);
                        }
                    }
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
                            ctx.addMethod("MessagesStorage", "getStorageQueue", m.name);
                            ctx.addClass("org.telegram.messenger.DispatchQueue", m.returnDesc);
                            ctx.addMethod("DispatchQueue", "postRunnable", bestPostRun.name);
                            String utilDesc = DexScanner.javaToDesc(ClassNames.UTILITIES);
                            if (MappingContext.hasFieldNamed(scanner, utilDesc, "stageQueue")) {
                                ctx.addField("Utilities", "stageQueue", "stageQueue");
                            } else {
                                String threadFallback = null;
                                for (DexScanner.FieldInfo wf : scanner.getWrittenStaticFieldsInMethod(utilDesc, "<clinit>", new String[0], utilDesc)) {
                                    if (m.returnDesc.equals(wf.typeDesc)
                                            || scanner.isSubclassOf(wf.typeDesc, m.returnDesc)
                                            || scanner.isSubclassOf(m.returnDesc, wf.typeDesc)) {
                                        ctx.addField("Utilities", "stageQueue", wf.name);
                                        threadFallback = null;
                                        break;
                                    }
                                    if (threadFallback == null && scanner.isSubclassOf(wf.typeDesc, "Ljava/lang/Thread;")) {
                                        threadFallback = wf.name;
                                    }
                                }
                                if (threadFallback != null) {
                                    ctx.addField("Utilities", "stageQueue", threadFallback);
                                }
                            }
                            break;
                        }
                    }
                }
                if (!ctx.classMap.containsKey("org.telegram.messenger.DispatchQueue")) {
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
                        ctx.addClass("org.telegram.messenger.DispatchQueue", dqDesc);
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
                            ctx.addMethod("DispatchQueue", "postRunnable", bestPostRun.name);
                        }
                        String utilDesc = DexScanner.javaToDesc(ClassNames.UTILITIES);
                        if (MappingContext.hasFieldNamed(scanner, utilDesc, "stageQueue")) {
                            ctx.addField("Utilities", "stageQueue", "stageQueue");
                        } else {
                            String threadFallback = null;
                            for (DexScanner.FieldInfo wf : scanner.getWrittenStaticFieldsInMethod(utilDesc, "<clinit>", new String[0], utilDesc)) {
                                if (dqDesc.equals(wf.typeDesc)
                                        || scanner.isSubclassOf(wf.typeDesc, dqDesc)
                                        || scanner.isSubclassOf(dqDesc, wf.typeDesc)) {
                                    ctx.addField("Utilities", "stageQueue", wf.name);
                                    threadFallback = null;
                                    break;
                                }
                                if (threadFallback == null && scanner.isSubclassOf(wf.typeDesc, "Ljava/lang/Thread;")) {
                                    threadFallback = wf.name;
                                }
                            }
                            if (threadFallback != null) {
                                ctx.addField("Utilities", "stageQueue", threadFallback);
                            }
                        }
                    }
                }
            }

            msgCtrlDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "telegramAntispamUserId");
            if (msgCtrlDesc != null) {
                ctx.addClass(ClassNames.MESSAGES_CONTROLLER, msgCtrlDesc);
                ctx.addMethod("MessagesController", "getInstance",
                        MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "getInstance")
                                ? "getInstance"
                                : scanner.findSingleMethod(msgCtrlDesc, true, msgCtrlDesc, "I"));
                if (msgStorageDesc != null) {
                    String getMs = MappingContext.hasMethodNamed(scanner, baseControllerDesc, "getMessagesStorage")
                            || MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "getMessagesStorage")
                            ? "getMessagesStorage"
                            : scanner.findSingleMethod(baseControllerDesc, false, msgStorageDesc);
                    if (getMs == null) {
                        getMs = scanner.findSingleMethod(msgCtrlDesc, false, msgStorageDesc);
                    }
                    if (getMs != null) {
                        ctx.addMethod("MessagesController", "getMessagesStorage", getMs);
                    }
                }
                String tlUserDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_USER);
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
                        ctx.addClass(ClassNames.TLRPC_CHAT, bestChatDesc);
                        ctx.addMethod("MessagesController", "getChat", bestChatMeth);
                    }
                }
                String tlChatDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_CHAT);
                ctx.addMethod("MessagesController", "isChatNoForwards",
                        scanner.findSingleMethod(msgCtrlDesc, false, "Z", tlChatDesc));
                if (!ctx.methodMap.containsKey("MessagesController#getChat")) {
                    String getChatBoxed = scanner.findSingleMethod(msgCtrlDesc, false, tlChatDesc, "Ljava/lang/Long;");
                    if (getChatBoxed != null) {
                        ctx.addMethod("MessagesController", "getChat", getChatBoxed);
                    } else {
                        String getChatPrim = scanner.findSingleMethod(msgCtrlDesc, false, tlChatDesc, "J");
                        if (getChatPrim != null) {
                            ctx.addMethod("MessagesController", "getChat", getChatPrim);
                        }
                    }
                }
                String getUserBoxed = scanner.findSingleMethod(msgCtrlDesc, false, tlUserDesc, "Ljava/lang/Long;");
                if (getUserBoxed != null) {
                    ctx.addMethod("MessagesController", "getUser", getUserBoxed);
                } else {
                    String getUserPrim = scanner.findSingleMethod(msgCtrlDesc, false, tlUserDesc, "J");
                    if (getUserPrim != null) {
                        ctx.addMethod("MessagesController", "getUser", getUserPrim);
                    }
                }
                String userFullDesc = ctx.resolvedDescOrRaw("org.telegram.tgnet.TLRPC$UserFull");
                if (MappingContext.hasClass(scanner, userFullDesc)) {
                    String getUserFullM = MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "getUserFull")
                            ? "getUserFull"
                            : scanner.findSingleMethod(msgCtrlDesc, false, userFullDesc, "J");
                    if (getUserFullM != null) {
                        ctx.addMethod("MessagesController", "getUserFull", getUserFullM);
                    }
                }
                ctx.addMethod("MessagesController", "processUpdateArray",
                        MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "processUpdateArray")
                                ? "processUpdateArray"
                                : scanner.findSingleMethod(msgCtrlDesc, false, "Z",
                                "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Ljava/util/ArrayList;", "Z", "I"));
                String deleteMeth = MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "deleteMessages")
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
                        if (tgnetObfuscated && !ctx.classMap.containsKey(ClassNames.TLRPC_ENCRYPTED_CHAT)) {
                            ctx.addClass(ClassNames.TLRPC_ENCRYPTED_CHAT, bestDel.paramDescs[2]);
                        }
                    }
                }
                ctx.addMethod("MessagesController", "deleteMessages", deleteMeth);
                String procDiff = MappingContext.hasMethodNamed(scanner, msgCtrlDesc, "processNewDifferenceParams")
                        ? "processNewDifferenceParams"
                        : scanner.findMethodByString(msgCtrlDesc, "APPLY PTS", false, "V", "I", "I", "I");
                if (procDiff == null) {
                    procDiff = scanner.findMethodByString(msgCtrlDesc, "APPLY PTS", false, "V", "I", "I", "I", "I");
                }
                if (procDiff != null) {
                    ctx.addMethod("MessagesController", "processNewDifferenceParams", procDiff);
                }
                String inputChanChat = scanner.findSingleMethod(msgCtrlDesc, true, "Lorg/telegram/tgnet/TLRPC$InputChannel;", DexScanner.javaToDesc(ClassNames.TLRPC_CHAT));
                if (inputChanChat != null) {
                    ctx.addMethod("MessagesController", "getInputChannel", inputChanChat);
                } else {
                    ctx.addMethod("MessagesController", "getInputChannel",
                            scanner.findSingleMethod(msgCtrlDesc, true, "Lorg/telegram/tgnet/TLRPC$InputChannel;", "J"));
                }
                String dialogMsgsByIds = MappingContext.hasFieldNamed(scanner, msgCtrlDesc, "dialogMessagesByIds")
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
                ctx.addField("MessagesController", "dialogMessagesByIds", dialogMsgsByIds);

                // Identify LongSparseArray type and dialogMessage field (raw check -> 2-arg delete helper -> deleteMessages read)
                TgnetMappingResolver.resolveDialogMessageField(ctx, scanner, msgCtrlDesc, deleteMeth);

                TgnetMappingResolver.resolveMessagesControllerExtras(ctx, scanner, msgCtrlDesc);
            }

            String fileLoaderDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "create load operation fileName=");
            if (fileLoaderDesc != null) {
                ctx.addClass(ClassNames.FILE_LOADER, fileLoaderDesc);
                ctx.addMethod("FileLoader", "getInstance",
                        scanner.findSingleMethod(fileLoaderDesc, true, fileLoaderDesc, "I"));
                DexScanner.MethodInfo bestGetDir = null;
                String imgLocDesc = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(fileLoaderDesc)) {
                    if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && tlMessageDesc.equals(m.paramDescs[0])) {
                        ctx.addMethod("FileLoader", "getPathToMessage", m.name);
                    } else if ("Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && tlObjectDesc.equals(m.paramDescs[0])) {
                        ctx.addMethod("FileLoader", "getPathToAttach", m.name);
                    } else if (m.isStatic && "Ljava/io/File;".equals(m.returnDesc) && m.paramDescs.length == 1
                            && "I".equals(m.paramDescs[0])) {
                        if (bestGetDir == null || m.getInsnsSize() > bestGetDir.getInsnsSize()) {
                            bestGetDir = m;
                        }
                    } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                            && (ctx.resolvedDescOrRaw("org.telegram.tgnet.TLRPC$Document").equals(m.paramDescs[0])
                                || "Lorg/telegram/tgnet/TLRPC$Document;".equals(m.paramDescs[0]))
                            && "Ljava/lang/Object;".equals(m.paramDescs[1])
                            && "I".equals(m.paramDescs[2]) && "I".equals(m.paramDescs[3])) {
                        ctx.addMethod("FileLoader", "loadFileDocument", m.name);
                    } else if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 5
                            && m.paramDescs[0].startsWith("L")
                            && !m.paramDescs[0].startsWith("Ljava/")
                            && !m.paramDescs[0].startsWith("Landroid/")
                            && "Ljava/lang/Object;".equals(m.paramDescs[1])
                            && "Ljava/lang/String;".equals(m.paramDescs[2])
                            && "I".equals(m.paramDescs[3]) && "I".equals(m.paramDescs[4])) {
                        ctx.addMethod("FileLoader", "loadFileImage", m.name);
                        imgLocDesc = m.paramDescs[0];
                    }
                }
                if (bestGetDir != null) {
                    ctx.addMethod("FileLoader", "getDirectory", bestGetDir.name);
                }
                if (imgLocDesc != null) {
                    ctx.addClass(ClassNames.IMAGE_LOCATION, imgLocDesc);
                    String getLocalFile = scanner.findSingleMethod(fileLoaderDesc, false, "Ljava/io/File;", imgLocDesc);
                    if (getLocalFile != null) {
                        ctx.addMethod("FileLoader", "getLocalFile", getLocalFile);
                    }
                    String getForPhoto = scanner.findSingleMethod(imgLocDesc, true, imgLocDesc,
                            "Lorg/telegram/tgnet/TLRPC$PhotoSize;", "Lorg/telegram/tgnet/TLRPC$Photo;");
                    if (getForPhoto == null) {
                        for (DexScanner.MethodInfo im : scanner.getMethods(imgLocDesc)) {
                            if (im.isStatic && imgLocDesc.equals(im.returnDesc) && im.paramDescs.length == 2
                                    && im.paramDescs[0].startsWith("L") && im.paramDescs[1].startsWith("L")
                                    && scanner.getFields(im.paramDescs[0]).size() >= 3
                                    && scanner.getFields(im.paramDescs[1]).size() >= 6) {
                                getForPhoto = im.name;
                                break;
                            }
                        }
                    }
                    if (getForPhoto != null) {
                        ctx.addMethod("ImageLocation", "getForPhoto", getForPhoto);
                    }
                }
            }

            // SendMessagesHelper, SendMessageParams, SendingMediaInfo, AccountInstance
            resolveSendMessagesHelper(ctx, scanner, baseControllerDesc);

            String notifCtrlDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", baseControllerDesc, "show extra notifications chatId ");
            if (notifCtrlDesc != null) {
                ctx.addClass(ClassNames.NOTIFICATIONS_CONTROLLER, notifCtrlDesc);
                String lsaDesc = ctx.classMap.get(ClassNames.LONG_SPARES_ARRAY) != null
                        ? DexScanner.javaToDesc(ctx.classMap.get(ClassNames.LONG_SPARES_ARRAY))
                        : null;
                if (lsaDesc != null) {
                    ctx.addMethod("NotificationsController", "removeDeletedMessagesFromNotifications",
                            scanner.findSingleMethod(notifCtrlDesc, false, "V", lsaDesc, "Z"));
                }
            }
        }

        // 4. Other core messenger & UI classes
        String localeCtrlDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "LanguageName");
        if (localeCtrlDesc != null) {
            ctx.addClass(ClassNames.LOCALE_CONTROLLER, localeCtrlDesc);
            String localeGetInst = scanner.findSingleMethod(localeCtrlDesc, true, localeCtrlDesc);
            ctx.addMethod("LocaleController", "getInstance", localeGetInst);

            // LocaleController#getString: static (I) -> String (delegates to 2-arg getString(String, int) or (int, String))
            String localeGetString = null;
            DexScanner.MethodInfo bestLocaleGetString = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(localeCtrlDesc)) {
                if (!m.isStatic || !"Ljava/lang/String;".equals(m.returnDesc)
                        || m.paramDescs.length != 1 || !"I".equals(m.paramDescs[0])
                        || m.name.startsWith("lambda$")) {
                    continue;
                }
                if ("getString".equals(m.name)) {
                    localeGetString = m.name;
                    break;
                }
                if (m.getInsnsSize() <= 0) continue;
                boolean delegatesToTwoArgGetString = false;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                        localeCtrlDesc, m.name, m.paramDescs, localeCtrlDesc)) {
                    if ("Ljava/lang/String;".equals(ref.returnDesc) && ref.paramDescs.length == 2
                            && (("I".equals(ref.paramDescs[0]) && "Ljava/lang/String;".equals(ref.paramDescs[1]))
                            || ("Ljava/lang/String;".equals(ref.paramDescs[0]) && "I".equals(ref.paramDescs[1])))) {
                        delegatesToTwoArgGetString = true;
                        break;
                    }
                }
                if (delegatesToTwoArgGetString && (bestLocaleGetString == null
                        || m.getInsnsSize() < bestLocaleGetString.getInsnsSize())) {
                    bestLocaleGetString = m;
                }
            }
            if (localeGetString == null && bestLocaleGetString != null) {
                localeGetString = bestLocaleGetString.name;
            }
            ctx.addMethod("LocaleController", "getString", localeGetString);
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
            ctx.addField("LocaleController", "isRTL", isRtlField);
            String getCurLocale = scanner.findSingleMethod(localeCtrlDesc, false, "Ljava/util/Locale;");
            if (getCurLocale != null) {
                ctx.addMethod("LocaleController", "getCurrentLocale", getCurLocale);
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
            ctx.addField("LocaleController", "currentLocale", curLocaleField);
            String fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "I", "[I");
            if (fmtShort == null) {
                fmtShort = scanner.findSingleMethod(localeCtrlDesc, true, "Ljava/lang/String;", "[I", "I");
            }
            ctx.addMethod("LocaleController", "formatShortNumber", fmtShort);

            for (DexScanner.MethodInfo m : scanner.getMethods(localeCtrlDesc)) {
                if (m.isStatic && "Ljava/lang/String;".equals(m.returnDesc)
                        && m.paramDescs.length == 2 && "J".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                        && m.usesString(" ") && m.usesString("LOC_ERR")) {
                    ctx.addMethod("LocaleController", "formatYearMont", m.name);
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
                ctx.addMethod("LocaleController", "getFormatterDay", fmtDay);
            }
            if (fmtDaySec != null) {
                ctx.addMethod("LocaleController", "getFormatterDayWithSeconds", fmtDaySec);
            }
            if ((fmtDay != null || fmtDaySec != null) && !scanner.getMethods(fastDateFmtDesc).isEmpty()) {
                ctx.addClass(ClassNames.FAST_DATE_FORMAT, fastDateFmtDesc);
                String fmtMeth = scanner.findSingleMethod(fastDateFmtDesc, false, "Ljava/lang/String;", "J");
                if (fmtMeth != null) {
                    ctx.addMethod("FastDateFormat", "format", fmtMeth);
                }
            }
        }

        StoriesMappingResolver.resolveStoryItemHolder(ctx, scanner);
        ProfileMappingResolver.resolveProfileActivity(ctx, scanner, msgCtrlDesc);
        ProfileMappingResolver.resolveProfileIdDc(ctx, scanner);
        UiMappingResolver.resolveDialogsActivityProxy(ctx, scanner, baseFragmentDesc, actionBarDesc, chatActDesc);
        UiMappingResolver.resolveMainTabs(ctx, scanner, userConfigDesc);
        StoriesMappingResolver.resolveStoriesController(ctx, scanner);

        String sharedConfigDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "mainconfig", "saveIncomingPhotos");
        if (sharedConfigDesc != null) {
            ctx.addClass(ClassNames.SHARED_CONFIG, sharedConfigDesc);
            resolveSharedConfigUpdate(ctx, scanner, sharedConfigDesc);
        }

        String browserDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null,
                "com.android.browser.application_id", "android.intent.category.BROWSABLE");
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "com.android.browser.application_id", "android.intent.category.BROWSABLE");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null,
                    "com.android.browser.application_id", "android.support.customtabs.extra.SESSION");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "com.android.browser.application_id", "android.support.customtabs.extra.SESSION");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings("Lorg/telegram/messenger/", null,
                    "com.android.browser.application_id");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "com.android.browser.application_id");
        }
        if (browserDesc == null) {
            browserDesc = scanner.findClassByStrings(null, null,
                    "android.intent.action.VIEW", "https://t.me/");
        }
        if (browserDesc != null) {
            ctx.addClass(ClassNames.BROWSER, browserDesc);
            String openUrlMeth = null;
            Map<String, Integer> csVoidCounts = new HashMap<>();
            List<String> csVoidNames = new ArrayList<>();
            for (DexScanner.MethodInfo m : scanner.getMethods(browserDesc)) {
                if (m.isStatic && ("V".equals(m.returnDesc) || "Z".equals(m.returnDesc)) && m.paramDescs.length >= 2
                        && "Landroid/content/Context;".equals(m.paramDescs[0])
                        && ("Ljava/lang/String;".equals(m.paramDescs[1]) || "Landroid/net/Uri;".equals(m.paramDescs[1]))) {
                    csVoidCounts.put(m.name, csVoidCounts.getOrDefault(m.name, 0) + 1);
                    if ("V".equals(m.returnDesc) && m.paramDescs.length == 2 && "Ljava/lang/String;".equals(m.paramDescs[1])) {
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
                        if (ref.paramDescs.length >= 3
                                && "Landroid/content/Context;".equals(ref.paramDescs[0])
                                && ("Ljava/lang/String;".equals(ref.paramDescs[1]) || "Landroid/net/Uri;".equals(ref.paramDescs[1]))
                                && "Z".equals(ref.paramDescs[2]) && ref.paramDescs.length <= 4) {
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
                ctx.addMethod("Browser", "openUrl", openUrlMeth);
            }
        }

        String themeDesc = UiMappingResolver.findTheme(scanner);
        if (themeDesc != null) {
            ctx.addClass(ClassNames.THEME, themeDesc);
            ctx.addField("Theme", "chat_timePaint",
                    scanner.findSingleField(themeDesc, true, "Landroid/text/TextPaint;"));

            String getThemeMethod = null;
            String themeInfoDesc = null;
            for (int pass = 0; pass < 2 && getThemeMethod == null; pass++) {
                for (DexScanner.MethodInfo m : scanner.getMethods(themeDesc)) {
                    if (m.isStatic && m.paramDescs.length == 1
                            && "Ljava/lang/String;".equals(m.paramDescs[0])
                            && (pass == 0
                                    ? m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                                    : (m.returnDesc.startsWith("L")
                                        && !m.returnDesc.startsWith("Ljava/")
                                        && !m.returnDesc.startsWith("Landroid/")
                                        && "Ljava/lang/Object;".equals(scanner.getSuperClass(m.returnDesc))
                                        && scanner.getFields(m.returnDesc).size() >= 10
                                        && (scanner.findSingleMethod(themeDesc, true, "V", m.returnDesc, "Z", "Z") != null
                                            || scanner.findSingleMethod(themeDesc, true, "V", m.returnDesc, "Z", "I", "Landroid/view/View;") != null)))
                            && m.getInsnsSize() > 0 && m.getInsnsSize() < 30) {
                        getThemeMethod = m.name;
                        themeInfoDesc = m.returnDesc;
                        break;
                    }
                }
            }
            String infoIsDark = null;
            if (getThemeMethod != null && themeInfoDesc != null) {
                ctx.addMethod("Theme", "getTheme", getThemeMethod);
                String applyAnim = scanner.findSingleMethod(themeDesc, true, "V", themeInfoDesc, "Z", "I", "Landroid/view/View;");
                if (applyAnim != null) {
                    ctx.addMethod("Theme", "applyThemeAnimated", applyAnim);
                }
                String applyDirect = scanner.findSingleMethod(themeDesc, true, "V", themeInfoDesc, "Z", "Z");
                if (applyDirect != null) {
                    ctx.addMethod("Theme", "applyTheme", applyDirect);
                }
                if (MappingContext.hasZeroArgMethodNamed(scanner, themeInfoDesc, "isDark")) {
                    infoIsDark = "isDark";
                }
                if (infoIsDark == null) {
                    infoIsDark = scanner.findSingleMethod(themeInfoDesc, false, "Z");
                }
                if (infoIsDark == null) {
                    String[] noArgs = new String[0];
                    for (DexScanner.MethodInfo m : scanner.getMethods(themeDesc)) {
                        if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 0 || m.codeOff == 0) continue;
                        if (m.getInsnsSize() <= 25 && !scanner.methodHasOpcode(themeDesc, m.name, noArgs, 0x97, 0xb7, 0xd7, 0xdf)) {
                            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(themeDesc, m.name, noArgs, themeInfoDesc)) {
                                if ("Z".equals(ref.returnDesc) && ref.paramDescs.length == 0) {
                                    infoIsDark = ref.name;
                                    break;
                                }
                            }
                            if (infoIsDark != null) break;
                        }
                    }
                }
                if (infoIsDark == null) {
                    infoIsDark = scanner.findMethodByString(themeInfoDesc, "Dark Blue", false, "Z");
                }
                if (infoIsDark != null) {
                    ctx.addMethod("ThemeInfo", "isDark", infoIsDark);
                }
            }

            String isDark = UiMappingResolver.findIsCurrentThemeDark(scanner, themeDesc, themeInfoDesc, infoIsDark);
            if (isDark != null) {
                ctx.addMethod("Theme", "isCurrentThemeDark", isDark);
            }
        }

        // 5. Signature-based core classes (NotificationCenter, AndroidUtilities, ActionBarMenuOnItemClick)
        String notifCenterDesc = findNotificationCenter(scanner);
        if (notifCenterDesc != null) {
            ctx.addClass(ClassNames.NOTIFICATION_CENTER, notifCenterDesc);
            String notifGetInst = scanner.findSingleMethod(notifCenterDesc, true, notifCenterDesc, "I");
            if (notifGetInst != null) {
                ctx.addMethod("NotificationCenter", "getInstance", notifGetInst);
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
                ctx.addMethod("NotificationCenter", "postNotificationName", bestPost);
            }
            if (MappingContext.hasFieldNamed(scanner, notifCenterDesc, "messagesDeleted")) {
                ctx.addField("NotificationCenter", "messagesDeleted", "messagesDeleted");
            } else {
                String mcDesc = ctx.classMap.get(ClassNames.MESSAGES_CONTROLLER) != null
                        ? DexScanner.javaToDesc(ctx.classMap.get(ClassNames.MESSAGES_CONTROLLER))
                        : null;
                String delMeth = ctx.methodMap.get("MessagesController#deleteMessages");
                if (mcDesc != null && delMeth != null) {
                    String lastNotifInt = null;
                    for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(mcDesc, delMeth, null, notifCenterDesc)) {
                        if ("I".equals(rf.typeDesc)) {
                            lastNotifInt = rf.name;
                        }
                    }
                    if (lastNotifInt != null) {
                        ctx.addField("NotificationCenter", "messagesDeleted", lastNotifInt);
                    }
                }
            }
            if (MappingContext.hasFieldNamed(scanner, notifCenterDesc, "tlSchemeParseException")) {
                ctx.addField("NotificationCenter", "tlSchemeParseException", "tlSchemeParseException");
            } else {
                String tlErrField = scanner.findReadStaticFieldBeforeString(
                        DexScanner.javaToDesc(ClassNames.LAUNCH_ACTIVITY),
                        "didReceivedNotification",
                        new String[]{"I", "I", "[Ljava/lang/Object;"},
                        notifCenterDesc, "I", "TL Error");
                if (tlErrField != null) {
                    ctx.addField("NotificationCenter", "tlSchemeParseException", tlErrField);
                }
            }
        }

        String androidUtilsDesc = findAndroidUtilities(scanner);
        if (androidUtilsDesc != null) {
            ctx.addClass(ClassNames.ANDROID_UTILITIES, androidUtilsDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(androidUtilsDesc)) {
                if (m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0
                        && !scanner.getReadStaticFieldsInMethod(androidUtilsDesc, m.name, new String[0], androidUtilsDesc).isEmpty()) {
                    ctx.addMethod("AndroidUtilities", "isTabletInternal", m.name);
                    break;
                }
            }
        }

        String abMenuClickDesc = UiMappingResolver.findActionBarMenuOnItemClick(scanner, actionBarDesc);
        if (abMenuClickDesc != null) {
            ctx.addClass(ClassNames.ACTION_BAR_MENU_ON_ITEM_CLICK, abMenuClickDesc);
            String onItemClickName = MappingContext.hasMethodNamed(scanner, abMenuClickDesc, "onItemClick")
                    ? "onItemClick"
                    : scanner.findSingleMethod(abMenuClickDesc, false, "V", "I");
            if (onItemClickName != null) {
                ctx.addMethod("ActionBar$ActionBarMenuOnItemClick", "onItemClick", onItemClickName);
            }
            if (actionBarDesc != null) {
                String setCb = MappingContext.hasMethodNamed(scanner, actionBarDesc, "setActionBarMenuOnItemClick")
                        ? "setActionBarMenuOnItemClick"
                        : scanner.findSingleMethod(actionBarDesc, false, "V", abMenuClickDesc);
                if (setCb != null) {
                    ctx.addMethod("ActionBar", "setActionBarMenuOnItemClick", setCb);
                }
            }
        }

        String fileLoadOpDesc = findFileLoadOperation(scanner);
        if (fileLoadOpDesc != null) {
            ctx.addClass(ClassNames.FILE_LOAD_OPERATION, fileLoadOpDesc);
            for (DexScanner.MethodInfo m : scanner.getMethods(fileLoadOpDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0 && m.getInsnsSize() > 15 && m.getInsnsSize() < 200) {
                    List<DexScanner.FieldInfo> writes = scanner.getWrittenFieldsInMethod(fileLoadOpDesc, m.name, new String[0], fileLoadOpDesc);
                    int intWrites = 0;
                    for (DexScanner.FieldInfo wf : writes) {
                        if ("I".equals(wf.typeDesc)) intWrites++;
                    }
                    if (intWrites >= 4) {
                        ctx.addMethod("FileLoadOperation", "updateParams", m.name);
                        List<String> uniqInts = new ArrayList<>();
                        for (DexScanner.FieldInfo wf : writes) {
                            if ("I".equals(wf.typeDesc) && !uniqInts.contains(wf.name)) {
                                uniqInts.add(wf.name);
                            }
                        }
                        if (uniqInts.size() >= 4) {
                            ctx.addField("FileLoadOperation", "downloadChunkSizeBig", uniqInts.get(0));
                            ctx.addField("FileLoadOperation", "maxDownloadRequests", uniqInts.get(1));
                            ctx.addField("FileLoadOperation", "maxDownloadRequestsBig", uniqInts.get(2));
                            ctx.addField("FileLoadOperation", "maxCdnParts", uniqInts.get(3));
                        }
                        break;
                    }
                }
            }
        }

        resolveFileUploadOperation(ctx, scanner);

        String chatMsgCellDesc = UiMappingResolver.findChatMessageCell(scanner, msgObjDesc);
        if (chatMsgCellDesc != null) {
            ctx.addClass(ClassNames.CHAT_MESSAGE_CELL, chatMsgCellDesc);
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
                    ctx.addMethod("ChatMessageCell", "measureTime", measureTimeMeth.name);

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
                            ctx.addField("Theme", "chat_timePaint", bestTimePaint);
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
                        ctx.addField("ChatMessageCell", "currentTimeString", timeStrField);
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
                        ctx.addField("ChatMessageCell", "timeTextWidth", resolvedTimeTextWidth);
                    }
                    if (resolvedTimeWidth != null) {
                        ctx.addField("ChatMessageCell", "timeWidth", resolvedTimeWidth);
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
                    ctx.addMethod("ChatMessageCell", "getMessageObject", getMsgObjM.name);
                }
            }
            // Find ChatActivity$ChatMessageCellDelegate implementation on ChatActivity (largest didPressImage)
            boolean resolvedDelegateImpl = false;
            for (int pass = 0; pass < 2 && !resolvedDelegateImpl; pass++) {
                if (chatActDesc != null) {
                    for (DexScanner.FieldInfo f : scanner.getFields(chatActDesc)) {
                        if (f.isStatic || f.typeDesc.startsWith("[") || f.typeDesc.length() <= 2) continue;
                        boolean pkgOk = pass == 0
                                ? (f.typeDesc.startsWith("Lorg/telegram/ui/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/Cells/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/Components/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/"))
                                : (!f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/"));
                        if (!pkgOk) continue;
                        for (DexScanner.MethodInfo m : scanner.getMethods(f.typeDesc)) {
                            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                                    && chatMsgCellDesc.equals(m.paramDescs[0]) && "F".equals(m.paramDescs[1])
                                    && "F".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])
                                    && m.getInsnsSize() > 500) {
                                ctx.addClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE, f.typeDesc);
                                ctx.addMethod("ChatActivity$ChatMessageCellDelegate", "didPressImage", m.name);
                                resolvedDelegateImpl = true;
                                break;
                            }
                        }
                        if (resolvedDelegateImpl) break;
                    }
                }
            }
            if (!resolvedDelegateImpl) {
                for (int pass = 0; pass < 2 && !resolvedDelegateImpl; pass++) {
                    for (DexScanner.FieldInfo f : scanner.getFields(chatMsgCellDesc)) {
                        if (f.isStatic || f.typeDesc.startsWith("[") || f.typeDesc.length() <= 2) continue;
                        boolean pkgOk = pass == 0
                                ? (f.typeDesc.startsWith("Lorg/telegram/ui/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/Cells/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/Components/")
                                   && !f.typeDesc.startsWith("Lorg/telegram/ui/ActionBar/"))
                                : (!f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/"));
                        if (!pkgOk) continue;
                        for (DexScanner.MethodInfo m : scanner.getMethods(f.typeDesc)) {
                            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 4
                                    && chatMsgCellDesc.equals(m.paramDescs[0]) && "F".equals(m.paramDescs[1])
                                    && "F".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                                ctx.addClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE, f.typeDesc);
                                ctx.addMethod("ChatActivity$ChatMessageCellDelegate", "didPressImage", m.name);
                                resolvedDelegateImpl = true;
                                break;
                            }
                        }
                        if (resolvedDelegateImpl) break;
                    }
                }
            }
        }

        // 6. SettingsActivity, SettingCell$Factory, UItem, and legacy DrawerLayoutAdapter
        UiMappingResolver.resolveSettingsClasses(ctx, scanner);
        UiMappingResolver.resolveDrawerClasses(ctx, scanner, launchActDesc);

        // 7. AlertDialog$Builder & OnButtonClickListener
        UiMappingResolver.resolveAlertDialog(ctx, scanner);

        // 8. SQLite classes
        TgnetMappingResolver.resolveSQLite(ctx, scanner);

        // 9. Obfuscated tgnet TL constructors & fields (e.g. Nekogram)
        String sponsoredReqDescHint = null;
        if (tgnetObfuscated) {
            sponsoredReqDescHint = TgnetMappingResolver.resolveObfuscatedTgnetConstructors(ctx, scanner, tlObjectDesc);
        }
        TgnetMappingResolver.resolveSponsoredMessagesMethod(ctx, scanner, msgCtrlDesc, sponsoredReqDescHint);

        // 10. RecyclerView / LinearLayoutManager & R8-obfuscated client native settings
        UiMappingResolver.resolveRecyclerView(ctx, scanner);
        ClientNativeSettingsResolver.resolveClientNativeSettings(ctx, scanner, baseFragmentDesc);

    }

    static void resolveMessageObjectAudio(MappingContext ctx, DexScanner scanner, String msgObjDesc) {
        String docDesc = ctx.resolvedDescOrRaw("org.telegram.tgnet.TLRPC$Document");
        String msgDesc = ctx.resolvedDescOrRaw(ClassNames.MESSAGE);
        String audioAttrDesc = "Lorg/telegram/tgnet/TLRPC$TL_documentAttributeAudio;";
        List<DexScanner.MethodInfo> methods = scanner.getMethods(msgObjDesc);

        DexScanner.MethodInfo musicDoc = null;
        DexScanner.MethodInfo voiceDoc = null;
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 1) {
                continue;
            }
            if (!docDesc.equals(m.paramDescs[0])
                    && (m.paramDescs[0].length() <= 2 || msgDesc.equals(m.paramDescs[0]) || scanner.getFields(m.paramDescs[0]).size() < 8)) {
                continue;
            }
            if (m.usesString("audio/flac") || m.usesString("audio/ogg")) {
                if (musicDoc == null || m.getInsnsSize() < musicDoc.getInsnsSize()) {
                    musicDoc = m;
                }
            }
        }
        if (musicDoc != null) {
            docDesc = musicDoc.paramDescs[0];
        }

        // Prefer exact unobfuscated "voice" field match, else match static (docDesc)->Z reading the same Z field as musicDoc
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 1 || !docDesc.equals(m.paramDescs[0])) {
                continue;
            }
            if (m.readsFieldNamed("voice") && m.checksInstanceOf(audioAttrDesc)) {
                if (voiceDoc == null || m.getInsnsSize() < voiceDoc.getInsnsSize()) voiceDoc = m;
            }
        }
        if (voiceDoc == null && musicDoc != null) {
            Set<String> musicZKeys = new HashSet<>();
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                    msgObjDesc, musicDoc.name, musicDoc.paramDescs, null)) {
                if ("Z".equals(rf.typeDesc) && !docDesc.equals(rf.name)) {
                    // Track Z fields read on attribute types; we'll check matching field name+type on attribute class
                    musicZKeys.add(rf.name);
                }
            }
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 1
                        || !docDesc.equals(m.paramDescs[0]) || m.name.equals(musicDoc.name)) {
                    continue;
                }
                int sz = m.getInsnsSize();
                if (sz < 15 || sz > 60) continue;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        msgObjDesc, m.name, m.paramDescs, null)) {
                    if ("Z".equals(rf.typeDesc) && musicZKeys.contains(rf.name)) {
                        if (voiceDoc == null || sz < voiceDoc.getInsnsSize()) {
                            voiceDoc = m;
                        }
                        break;
                    }
                }
            }
        }

        String musicMsg = findSmallestBoolCaller(ctx, scanner, msgObjDesc, methods, true, msgDesc,
                musicDoc != null ? musicDoc.name : null);
        String voiceMsg = findSmallestBoolCaller(ctx, scanner, msgObjDesc, methods, true, msgDesc,
                voiceDoc != null ? voiceDoc.name : null);

        String isMusicName = findSmallestBoolCaller(ctx, scanner, msgObjDesc, methods, false, null, musicMsg);
        if (isMusicName != null) {
            ctx.addMethod("MessageObject", "isMusic", isMusicName);
        }
        String isVoiceName = findSmallestBoolCaller(ctx, scanner, msgObjDesc, methods, false, null, voiceMsg);
        if (isVoiceName != null) {
            ctx.addMethod("MessageObject", "isVoice", isVoiceName);
        }
    }

    static String findSmallestBoolCaller(MappingContext ctx, DexScanner scanner, String classDesc, List<DexScanner.MethodInfo> methods,
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

    static void resolveSharedConfigUpdate(MappingContext ctx, DexScanner scanner, String sharedConfigDesc) {
        String tlAppUpdateDesc = ctx.resolvedDescOrRaw(ClassNames.TL_HELP_APP_UPDATE);
        String setVer = scanner.findSingleMethod(sharedConfigDesc, true, "Z", tlAppUpdateDesc);
        if (setVer == null) {
            setVer = scanner.findSingleMethod(sharedConfigDesc, true, "Z", tlAppUpdateDesc, "Z");
        }
        if (setVer != null) {
            ctx.addMethod("SharedConfig", "setNewAppVersionAvailable", setVer);
        }

        String appUpdateField = scanner.findSingleField(sharedConfigDesc, true, tlAppUpdateDesc);
        if (appUpdateField != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(sharedConfigDesc)) {
                if (!m.isStatic || !"Z".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                for (DexScanner.FieldInfo rf : scanner.getReadStaticFieldsInMethod(sharedConfigDesc, m.name, new String[0], sharedConfigDesc)) {
                    if (appUpdateField.equals(rf.name)) {
                        ctx.addMethod("SharedConfig", "isAppUpdateAvailable", m.name);
                        return;
                    }
                }
            }
        }
    }


    static void resolveSendMessagesHelper(MappingContext ctx, DexScanner scanner, String baseControllerDesc) {
        DexScanner.ClassPredicate smhPred = (desc, fields, methods) -> {
            if (methods.size() < 80) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "I".equals(m.returnDesc) && m.paramDescs.length >= 6
                        && "Ljava/util/ArrayList;".equals(m.paramDescs[0]) && "J".equals(m.paramDescs[1])
                        && "Z".equals(m.paramDescs[2]) && "Z".equals(m.paramDescs[3])) {
                    return true;
                }
            }
            return false;
        };
        String smhDesc = scanner.findClassByPredicate("Lorg/telegram/messenger/", smhPred);
        if (smhDesc == null) {
            smhDesc = scanner.findClassByPredicate(null, smhPred);
        }
        if (smhDesc == null) return;

        ctx.addClass(ClassNames.SEND_MESSAGES_HELPER, smhDesc);
        String getInst = scanner.findSingleMethod(smhDesc, true, smhDesc, "I");
        if (getInst != null) ctx.addMethod("SendMessagesHelper", "getInstance", getInst);

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
                    && m.paramDescs[0].startsWith("L")
                    && !m.paramDescs[0].startsWith("Ljava/") && !m.paramDescs[0].startsWith("Landroid/")
                    && !m.paramDescs[0].startsWith("[") && m.getInsnsSize() > 400) {
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

        if (fwdMethod != null) ctx.addMethod("SendMessagesHelper", "sendMessageForward", fwdMethod.name);
        if (prepPhoto != null) ctx.addMethod("SendMessagesHelper", "prepareSendingPhoto", prepPhoto.name);
        if (prepDoc != null) ctx.addMethod("SendMessagesHelper", "prepareSendingDocument", prepDoc.name);
        if (prepMedia != null) ctx.addMethod("SendMessagesHelper", "prepareSendingMedia", prepMedia.name);

        String tlEditDesc = ctx.resolvedDescOrRaw("org.telegram.tgnet.TLRPC$TL_messages_editMessage");
        String tlEditMsgField = "message";
        if (!MappingContext.hasFieldNamed(scanner, tlEditDesc, "message")) {
            String singleStr = scanner.findSingleField(tlEditDesc, false, "Ljava/lang/String;");
            if (singleStr != null) {
                tlEditMsgField = singleStr;
            }
        }
        DexScanner.MethodInfo editMsgMethod = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(smhDesc)) {
            if (!m.isStatic && m.getInsnsSize() > 100) {
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(smhDesc, m.name, m.paramDescs, tlEditDesc)) {
                    if (tlEditMsgField.equals(wf.name) && "Ljava/lang/String;".equals(wf.typeDesc)) {
                        if (editMsgMethod == null || m.getInsnsSize() > editMsgMethod.getInsnsSize()) {
                            editMsgMethod = m;
                        }
                        break;
                    }
                }
            }
        }
        if (editMsgMethod != null) ctx.addMethod("SendMessagesHelper", "editMessage", editMsgMethod.name);

        if (accInstDesc != null) {
            ctx.addClass(ClassNames.ACCOUNT_INSTANCE, accInstDesc);
            String accGetInst = scanner.findSingleMethod(accInstDesc, true, accInstDesc, "I");
            if (accGetInst != null) ctx.addMethod("AccountInstance", "getInstance", accGetInst);
        }

        if (sendParamsMethod != null) {
            ctx.addMethod("SendMessagesHelper", "sendMessageParams", sendParamsMethod.name);
            String paramsDesc = sendParamsMethod.paramDescs[0];
            ctx.addClass(ClassNames.SEND_MESSAGE_PARAMS, paramsDesc);
            DexScanner.MethodInfo masterFactory = null;
            for (DexScanner.MethodInfo pm : scanner.getMethods(paramsDesc)) {
                if (pm.isStatic && paramsDesc.equals(pm.returnDesc) && pm.paramDescs.length >= 8) {
                    if (pm.paramDescs.length >= 20 && (masterFactory == null || pm.paramDescs.length > masterFactory.paramDescs.length)) {
                        masterFactory = pm;
                    }
                    if ("Ljava/lang/String;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1]) && pm.paramDescs.length <= 16) {
                        ctx.addMethod("SendMessageParams", "ofText", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$MessageMedia;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1])) {
                        ctx.addMethod("SendMessageParams", "ofMedia", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$User;".equals(pm.paramDescs[0]) && "J".equals(pm.paramDescs[1])) {
                        ctx.addMethod("SendMessageParams", "ofUser", pm.name);
                    } else if ("Lorg/telegram/tgnet/TLRPC$TL_document;".equals(pm.paramDescs[0]) && pm.paramDescs.length >= 16) {
                        ctx.addMethod("SendMessageParams", "ofDocument", pm.name);
                    }
                }
            }
            if (masterFactory != null) {
                String msgObjDesc = ctx.classMap.get(ClassNames.MESSAGE_OBJECT) != null
                        ? DexScanner.javaToDesc(ctx.classMap.get(ClassNames.MESSAGE_OBJECT))
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
                if (writtenStrs.size() >= 1) ctx.addField("SendMessageParams", "message", writtenStrs.get(0));
                if (writtenStrs.size() >= 2) ctx.addField("SendMessageParams", "caption", writtenStrs.get(1));
                if (writtenMsgObjs.size() >= 3) ctx.addField("SendMessageParams", "editingMessageObject", writtenMsgObjs.get(2));
            }
        }

        String smiDesc = null;
        if (prepPhoto != null) {
            for (Map.Entry<String, List<DexScanner.FieldInfo>> e :
                    scanner.getWrittenFieldsGroupedByOwner(smhDesc, prepPhoto.name, prepPhoto.paramDescs).entrySet()) {
                String owner = e.getKey();
                if (owner.equals(smhDesc) || owner.startsWith("Ljava/") || owner.startsWith("Landroid/")) continue;
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
            DexScanner.ClassPredicate smiPred = (d, fs, ms) -> {
                int strs = 0, bools = 0, lists = 0;
                for (DexScanner.FieldInfo f : fs) {
                    if (!f.isStatic) {
                        if ("Ljava/lang/String;".equals(f.typeDesc)) strs++;
                        else if ("Z".equals(f.typeDesc)) bools++;
                        else if ("Ljava/util/ArrayList;".equals(f.typeDesc)) lists++;
                    }
                }
                return strs >= 3 && bools >= 3 && lists >= 1 && fs.size() >= 10 && fs.size() <= 35 && ms.size() <= 10;
            };
            smiDesc = scanner.findClassByPredicate("Lorg/telegram/messenger/", smiPred);
            if (smiDesc == null) {
                smiDesc = scanner.findClassByPredicate(null, smiPred);
            }
        }
        if (smiDesc != null) {
            ctx.addClass(ClassNames.SENDING_MEDIA_INFO, smiDesc);
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
                if (writtenStrs.size() >= 1) ctx.addField("SendingMediaInfo", "path", writtenStrs.get(0));
                if (writtenStrs.size() >= 3) ctx.addField("SendingMediaInfo", "caption", writtenStrs.get(2));
                if (firstList != null) ctx.addField("SendingMediaInfo", "entities", firstList);
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
                ctx.addField("SendingMediaInfo", "isVideo", isVideoField);
            }
        }
    }

    static String findNotificationCenter(DexScanner scanner) {
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

    static String findAndroidUtilities(DexScanner scanner) {
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


    static void resolveFileUploadOperation(MappingContext ctx, DexScanner scanner) {
        String fuoDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/messenger/", null, "_uploaded", "_size");
        if (fuoDesc == null) return;

        ctx.addClass(ClassNames.FILE_UPLOAD_OPERATION, fuoDesc);

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
                    if (intReads.size() >= 2) {
                        boolean allSame = true;
                        for (int i = 1; i < intReads.size(); i++) {
                            if (!intReads.get(0).equals(intReads.get(i))) {
                                allSame = false;
                                break;
                            }
                        }
                        if (allSame) {
                            calcMethod = m.name;
                            uploadChunkField = intReads.get(0);
                        }
                    }
                } else if (startMethod == null && insns > 400) {
                    startMethod = m.name;
                }
            }
        }

        if (calcMethod != null) {
            ctx.addMethod("FileUploadOperation", "calcTotalPartsCount", calcMethod);
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

        if (readBufferField != null) ctx.addField("FileUploadOperation", "readBuffer", readBufferField);
        if (uploadChunkField != null) ctx.addField("FileUploadOperation", "uploadChunkSize", uploadChunkField);
        if (maxReqField != null) ctx.addField("FileUploadOperation", "maxRequestsCount", maxReqField);
        if (uploadConnField != null) ctx.addField("FileUploadOperation", "uploadConnectionsCount", uploadConnField);
        if (reqPerConnField != null) ctx.addField("FileUploadOperation", "uploadRequestsPerConnection", reqPerConnField);
    }

    static String findFileLoadOperation(DexScanner scanner) {
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

}
