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
 * Resolves UI components: ActionBar, DialogsActivity, MainTabs, Theme, AlertDialog, Settings, Drawer, RecyclerView.
 */
final class UiMappingResolver {

    private UiMappingResolver() {}

    static void resolveChatPinnedMessageView(MappingContext ctx, DexScanner scanner, String chatActDesc) {
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

        ctx.addMethod("ChatActivity", "createPinnedMessageView", createPinnedName);
        ctx.addField("ChatActivity", "pinnedMessageView", pinnedFieldName);

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
            ctx.addMethod("ChatActivity", "updatePinnedMessageView", bestUpdate.name);
        }
    }


    static String findActionBarMenuOnItemClick(DexScanner scanner, String actionBarDesc) {
        String found = scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", (desc, fields, methods) -> {
            if (methods.size() > 3 || fields.size() > 3) return false;
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
        if (found == null && actionBarDesc != null) {
            for (DexScanner.MethodInfo am : scanner.getMethods(actionBarDesc)) {
                if (!am.isStatic && "V".equals(am.returnDesc) && am.paramDescs.length == 1) {
                    String cand = am.paramDescs[0];
                    if (!cand.startsWith("L") || cand.startsWith("Ljava/") || cand.startsWith("Landroid/")) continue;
                    List<DexScanner.MethodInfo> cm = scanner.getMethods(cand);
                    List<DexScanner.FieldInfo> cf = scanner.getFields(cand);
                    if (cm.size() <= 4 && cf.size() <= 3) {
                        boolean hasBoolNoArgs = false;
                        boolean hasVoidInt = false;
                        for (DexScanner.MethodInfo m : cm) {
                            if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0) hasBoolNoArgs = true;
                            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) hasVoidInt = true;
                        }
                        if (hasBoolNoArgs && hasVoidInt) {
                            return cand;
                        }
                    }
                }
            }
        }
        return found;
    }


    static String findChatMessageCell(DexScanner scanner, String msgObjDesc) {
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

    static void resolveSettingsClasses(MappingContext ctx, DexScanner scanner) {
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

        ctx.addClass("org.telegram.ui.SettingsActivity$SettingCell$Factory", factoryDesc);
        ctx.addMethod("SettingsActivity$SettingCell$Factory", "of", factoryOfName[0]);
        ctx.addClass(ClassNames.UITEM, uItemDesc);

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
            ctx.addMethod("SettingsActivity$SettingCell$Factory", "bindView", factoryBindName[0]);
        }

        // Resolve SettingCell (outer class of Factory)
        int dollarIdx = factoryDesc.lastIndexOf('$');
        if (dollarIdx > 0) {
            String settingCellDesc = factoryDesc.substring(0, dollarIdx) + ";";
            List<DexScanner.FieldInfo> cellFields = scanner.getFields(settingCellDesc);
            if (!cellFields.isEmpty()) {
                ctx.addClass(ClassNames.SETTINGS_ACTIVITY_SETTING_CELL, settingCellDesc);

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
                    ctx.addMethod("SettingsActivity$SettingCell", "set", bestSet.name);
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
                    ctx.addField("SettingsActivity$SettingCell", "iconView", iconField);
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
        if (firstInt != null) ctx.addField("UItem", "id", firstInt);
        if (cs1 != null) ctx.addField("UItem", "text", cs1);
        if (cs2 != null) ctx.addField("UItem", "subtext", cs2);

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
            ctx.addClass("org.telegram.ui.SettingsActivity", settingsDesc);
            if (bestUniversalAdapter[0] != null) {
                ctx.addClass(ClassNames.UNIVERSAL_ADAPTER, bestUniversalAdapter[0]);
            }
            if (bestFillName[0] != null) {
                ctx.addMethod("SettingsActivity", "fillItems", bestFillName[0]);
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
                ctx.addMethod("SettingsActivity", "onClick", bestClick.name);
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
                ctx.addClass("org.telegram.ui.SettingsActivity",
                        MappingContext.hasClass(scanner, directSettingsDesc) ? directSettingsDesc : lambdaClass[0]);
                ctx.addClass("org.telegram.ui.SettingsActivity$FillItemsLambda", lambdaClass[0]);
                ctx.addMethod("SettingsActivity", "fillItems", lambdaMethod[0]);

                if (MappingContext.hasClass(scanner, directSettingsDesc)) {
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
                        ctx.addClass("org.telegram.ui.SettingsActivity$OnClickLambda", bestClickCls);
                        ctx.addMethod("SettingsActivity", "onClick", bestClickMeth.name);
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
                ctx.addClass("org.telegram.ui.SettingsActivity", directSettingsDesc);
                if (universalAdapterDesc != null) {
                    ctx.addClass(ClassNames.UNIVERSAL_ADAPTER, universalAdapterDesc);
                }
                if (fill != null) ctx.addMethod("SettingsActivity", "fillItems", fill);
                if (click != null) ctx.addMethod("SettingsActivity", "onClick", click);
            }
        }
    }

    static void resolveDrawerClasses(MappingContext ctx, DexScanner scanner, String launchActDesc) {
        String dlaDesc = "Lorg/telegram/ui/Adapters/DrawerLayoutAdapter;";
        String dlcDesc = "Lorg/telegram/ui/ActionBar/DrawerLayoutContainer;";
        if (!MappingContext.hasClass(scanner, dlaDesc)) {
            return;
        }
        ctx.addClass(ClassNames.DRAWER_LAYOUT_ADAPTER, dlaDesc);

        String itemDesc = "Lorg/telegram/ui/Adapters/DrawerLayoutAdapter$Item;";
        if (!MappingContext.hasClass(scanner, itemDesc)) {
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
            ctx.addClass("org.telegram.ui.Adapters.DrawerLayoutAdapter$Item", itemDesc);
        }

        // LaunchActivity fields: drawerLayoutAdapter and drawerLayoutContainer
        if (launchActDesc != null) {
            String dlaField = MappingContext.hasFieldNamed(scanner, launchActDesc, "drawerLayoutAdapter")
                    ? "drawerLayoutAdapter"
                    : scanner.findSingleField(launchActDesc, false, dlaDesc);
            if (dlaField != null) {
                ctx.addField("LaunchActivity", "drawerLayoutAdapter", dlaField);
            }
            String dlcField = MappingContext.hasFieldNamed(scanner, launchActDesc, "drawerLayoutContainer")
                    ? "drawerLayoutContainer"
                    : scanner.findSingleField(launchActDesc, false, dlcDesc);
            if (dlcField != null) {
                ctx.addField("LaunchActivity", "drawerLayoutContainer", dlcField);
            }
        }

        // DrawerLayoutAdapter#resetItems: raw or largest instance () -> V
        String resetItems = MappingContext.hasMethodNamed(scanner, dlaDesc, "resetItems") ? "resetItems" : null;
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
            ctx.addMethod("DrawerLayoutAdapter", "resetItems", resetItems);
        }

        // Resolve DrawerLayoutAdapter#getId and DrawerLayoutContainer#closeDrawer from LaunchActivity (View, I, F, F) click handler
        String getIdMeth = MappingContext.hasMethodNamed(scanner, dlaDesc, "getId") ? "getId" : null;
        String closeDrawerMeth = MappingContext.hasMethodNamed(scanner, dlcDesc, "closeDrawer") ? "closeDrawer" : null;
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
                    if (closeDrawerMeth == null && MappingContext.hasClass(scanner, dlcDesc)) {
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
            ctx.addMethod("DrawerLayoutAdapter", "getId", getIdMeth);
        }
        if (closeDrawerMeth != null) {
            ctx.addMethod("DrawerLayoutContainer", "closeDrawer", closeDrawerMeth);
        }

        // DrawerLayoutAdapter#items: raw or ArrayList field read in getId(I)
        String itemsField = MappingContext.hasFieldNamed(scanner, dlaDesc, "items") ? "items" : null;
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
            ctx.addField("DrawerLayoutAdapter", "items", itemsField);
        }
    }

    static String findTheme(DexScanner scanner) {
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

    static String findIsCurrentThemeDark(
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

    static void resolveAlertDialog(MappingContext ctx, DexScanner scanner) {
        String builderDesc = DexScanner.javaToDesc(ClassNames.ALERT_DIALOG_BUILDER);
        List<DexScanner.MethodInfo> methods = scanner.getMethods(builderDesc);
        if (methods.isEmpty()) return;

        // Identify Dialog class from parameterless () -> ActionBar/ or Dialog subclass methods (create & show)
        Map<String, Integer> retCounts = new HashMap<>();
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")) {
                retCounts.put(m.returnDesc, retCounts.getOrDefault(m.returnDesc, 0) + 1);
            }
        }
        if (retCounts.isEmpty()) {
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && m.paramDescs.length == 0 && m.returnDesc.startsWith("L")
                        && !m.returnDesc.startsWith("Ljava/") && !m.returnDesc.startsWith("Landroid/")
                        && scanner.isSubclassOf(m.returnDesc, "Landroid/app/Dialog;")) {
                    retCounts.put(m.returnDesc, retCounts.getOrDefault(m.returnDesc, 0) + 1);
                }
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
                ctx.addField("AlertDialog$Builder", "alertDialog", alertField);
            }
        }

        // getDismissRunnable: () -> Ljava/lang/Runnable;
        String dismissRun = scanner.findSingleMethod(builderDesc, false, "Ljava/lang/Runnable;");
        if (dismissRun != null) {
            ctx.addMethod("AlertDialog$Builder", "getDismissRunnable", dismissRun);
        }

        // setView(View) -> V or Self
        for (DexScanner.MethodInfo m : methods) {
            if (!m.isStatic && m.paramDescs.length == 1 && "Landroid/view/View;".equals(m.paramDescs[0])
                    && ("V".equals(m.returnDesc) || builderDesc.equals(m.returnDesc))) {
                ctx.addMethod("AlertDialog$Builder", "setView", m.name);
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
                    && ("Ljava/lang/String;".equals(m.paramDescs[0]) || "Ljava/lang/CharSequence;".equals(m.paramDescs[0]))) {
                String cand = m.paramDescs[1];
                boolean listenerOk = cand.startsWith("Lorg/telegram/ui/ActionBar/");
                if (!listenerOk && cand.startsWith("L") && !cand.startsWith("Ljava/") && !cand.startsWith("Landroid/")) {
                    List<DexScanner.MethodInfo> lms = scanner.getMethods(cand);
                    if (lms.size() <= 2) {
                        for (DexScanner.MethodInfo lm : lms) {
                            if (!lm.isStatic && "V".equals(lm.returnDesc) && lm.paramDescs.length == 2 && "I".equals(lm.paramDescs[1])) {
                                listenerOk = true;
                                break;
                            }
                        }
                    }
                }
                if (listenerOk) {
                    listenerDesc = cand;
                    if (m.containsConstInt(-1)) posBtn = m.name;
                    else if (m.containsConstInt(-2)) negBtn = m.name;
                    else if (m.containsConstInt(-3)) neuBtn = m.name;
                    else unresolvedBtns.add(m);
                }
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
                for (String wf : MappingContext.collectWrittenDialogFields(scanner, builderDesc, dialogDesc, m)) {
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
        if (negBtn != null) ctx.addMethod("AlertDialog$Builder", "setNegativeButton", negBtn);
        if (neuBtn != null) ctx.addMethod("AlertDialog$Builder", "setNeutralButton", neuBtn);
        if (posBtn != null) ctx.addMethod("AlertDialog$Builder", "setPositiveButton", posBtn);

        if (listenerDesc != null) {
            ctx.addClass(ClassNames.ALERT_DIALOG_BUTTON_CLICK, listenerDesc);
            for (DexScanner.MethodInfo lm : scanner.getMethods(listenerDesc)) {
                if (!lm.isStatic && lm.paramDescs.length == 2 && "I".equals(lm.paramDescs[1])) {
                    ctx.addMethod("AlertDialog$OnButtonClickListener", "onClick", lm.name);
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
                    Set<String> written = MappingContext.collectWrittenDialogFields(scanner, builderDesc, dialogDesc, m);
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
        if (msgMethod != null) ctx.addMethod("AlertDialog$Builder", "setMessage", msgMethod);
        if (titleMethod != null) ctx.addMethod("AlertDialog$Builder", "setTitle", titleMethod);

        // create() and show(): () -> Dialog (create is smaller than show)
        if (dialogDesc != null) {
            List<DexScanner.MethodInfo> dlgMeths = new ArrayList<>();
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && dialogDesc.equals(m.returnDesc) && m.paramDescs.length == 0) {
                    dlgMeths.add(m);
                }
            }
            if (dlgMeths.size() >= 2) {
                dlgMeths.sort((a, b) -> Integer.compare(a.getInsnsSize(), b.getInsnsSize()));
                ctx.addMethod("AlertDialog$Builder", "create", dlgMeths.get(0).name);
                ctx.addMethod("AlertDialog$Builder", "show", dlgMeths.get(dlgMeths.size() - 1).name);
            } else if (dlgMeths.size() == 1) {
                ctx.addMethod("AlertDialog$Builder", "create", dlgMeths.get(0).name);
            }
        }
    }

    static void resolveDialogsActivityDownloads(MappingContext ctx, DexScanner scanner, String dialogsActDesc, String updateProxyName) {
        if (dialogsActDesc == null) return;
        if (updateProxyName != null) {
            boolean foundVis = false;
            for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
                if (!updateProxyName.equals(m.name) || m.isStatic || !"V".equals(m.returnDesc)) continue;
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(
                        dialogsActDesc, m.name, m.paramDescs, dialogsActDesc)) {
                    if (!"V".equals(mr.returnDesc) || mr.paramDescs.length != 0) continue;
                    boolean callsAnimVis = false;
                    for (DexScanner.MethodRef cmr : scanner.getCalledMethodsInMethod(
                            dialogsActDesc, mr.name, new String[0], null)) {
                        if ("V".equals(cmr.returnDesc) && cmr.paramDescs.length == 2
                                && "Landroid/view/View;".equals(cmr.paramDescs[0])
                                && "F".equals(cmr.paramDescs[1])) {
                            callsAnimVis = true;
                            break;
                        }
                    }
                    if (!callsAnimVis) continue;
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            dialogsActDesc, mr.name, new String[0], dialogsActDesc)) {
                        if ("Z".equals(rf.typeDesc)) {
                            ctx.addMethod("DialogsActivity", "checkUi_itemDownloadsVisibility", mr.name);
                            ctx.addField("DialogsActivity", "downloadsItemVisible", rf.name);
                            foundVis = true;
                            break;
                        }
                    }
                    if (foundVis) break;
                }
                if (foundVis) break;
            }
        }

        String dpiDesc = null;
        for (DexScanner.FieldInfo f : scanner.getFields(dialogsActDesc)) {
            if (f.isStatic || f.typeDesc.startsWith("Landroid/")) continue;
            if (!scanner.isSubclassOf(f.typeDesc, "Landroid/view/View;")) continue;
            int paints = 0, floats = 0, arrLists = 0, bools = 0;
            for (DexScanner.FieldInfo df : scanner.getFields(f.typeDesc)) {
                if (df.isStatic) continue;
                if ("Landroid/graphics/Paint;".equals(df.typeDesc)) paints++;
                else if ("F".equals(df.typeDesc)) floats++;
                else if ("Ljava/util/ArrayList;".equals(df.typeDesc)) arrLists++;
                else if ("Z".equals(df.typeDesc)) bools++;
            }
            if (paints >= 2 && floats >= 3 && arrLists >= 1 && bools >= 2) {
                dpiDesc = f.typeDesc;
                ctx.addClass(ClassNames.DOWNLOAD_PROGRESS_ICON, dpiDesc);
                ctx.addField("DialogsActivity", "downloadProgressIcon", f.name);
                break;
            }
        }
        if (dpiDesc == null) return;

        String wasDrawn = null;
        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                dpiDesc, "setAlpha", new String[]{"F"}, dpiDesc)) {
            if ("Z".equals(wf.typeDesc)) {
                wasDrawn = wf.name;
                break;
            }
        }

        String showCompletedField = null;
        String currentProgressField = null;
        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                dpiDesc, "onDraw", new String[]{"Landroid/graphics/Canvas;"}, dpiDesc)) {
            if ("Z".equals(wf.typeDesc) && !wf.name.equals(wasDrawn)) {
                showCompletedField = wf.name;
            } else if ("F".equals(wf.typeDesc)) {
                currentProgressField = wf.name;
            }
        }

        String updListeners = null;
        String progressField = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(dpiDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
            boolean callsClear = false;
            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(
                    dpiDesc, m.name, m.paramDescs, "Ljava/util/ArrayList;")) {
                if ("clear".equals(mr.name)) {
                    callsClear = true;
                    break;
                }
            }
            if (!callsClear) continue;
            List<String> writtenFloats = new ArrayList<>();
            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                    dpiDesc, m.name, m.paramDescs, dpiDesc)) {
                if ("F".equals(wf.typeDesc)) {
                    writtenFloats.add(wf.name);
                }
            }
            if (writtenFloats.size() >= 2) {
                updListeners = m.name;
                for (String fn : writtenFloats) {
                    if (!fn.equals(currentProgressField)) {
                        progressField = fn;
                        break;
                    }
                }
                break;
            }
        }

        String currentListeners = null;
        for (DexScanner.FieldInfo df : scanner.getFields(dpiDesc)) {
            if (!df.isStatic && "Ljava/util/ArrayList;".equals(df.typeDesc)) {
                currentListeners = df.name;
                break;
            }
        }

        if (updListeners != null) ctx.addMethod("DownloadProgressIcon", "updateDownloadingListeners", updListeners);
        if (currentListeners != null) ctx.addField("DownloadProgressIcon", "currentListeners", currentListeners);
        if (progressField != null) ctx.addField("DownloadProgressIcon", "progress", progressField);
        if (currentProgressField != null) ctx.addField("DownloadProgressIcon", "currentProgress", currentProgressField);
        if (showCompletedField != null) ctx.addField("DownloadProgressIcon", "showCompletedIcon", showCompletedField);
    }

    static void resolveDialogsActivityProxy(MappingContext ctx, DexScanner scanner, String baseFragmentDesc, String actionBarDesc, String chatActDesc) {
        String proxyListDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/ui/", baseFragmentDesc, "UseProxySettings");
        if (proxyListDesc == null) {
            proxyListDesc = MappingContext.findClassByStringsWithFallback(scanner, "Lorg/telegram/ui/", baseFragmentDesc, "proxy_ip");
        }
        if (proxyListDesc != null) {
            ctx.addClass(ClassNames.PROXY_LIST_ACTIVITY, proxyListDesc);
        }

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
        ctx.addClass(ClassNames.DIALOGS_ACTIVITY, dialogsActDesc);

        String dialogsCreateView = scanner.findSingleMethod(
                dialogsActDesc, false, "Landroid/view/View;", "Landroid/content/Context;");
        if (dialogsCreateView != null) {
            ctx.addMethod("DialogsActivity", "createView", dialogsCreateView);
        }

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
            ctx.addClass(ClassNames.PROXY_DRAWABLE, proxyDrawableDesc);
            ctx.addField("DialogsActivity", "proxyDrawable", proxyDrawableFieldName);
            if (setConnectedName != null) {
                ctx.addMethod("ProxyDrawable", "setConnected", setConnectedName);
            }
        }

        String menuItemDesc = null;
        if (actionBarDesc != null) {
            boolean flatActionBar = !actionBarDesc.startsWith("Lorg/telegram/ui/ActionBar/");
            String createViewTarget = dialogsCreateView != null ? dialogsCreateView : "createView";
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                    dialogsActDesc, createViewTarget, new String[]{"Landroid/content/Context;"}, actionBarDesc)) {
                if (ref.paramDescs.length != 0 || ref.returnDesc == null) {
                    continue;
                }
                boolean menuPkgOk = ref.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                        || (flatActionBar && ref.returnDesc.startsWith("L")
                        && !ref.returnDesc.startsWith("Landroid/") && !ref.returnDesc.startsWith("Ljava/"));
                if (!menuPkgOk || !scanner.isSubclassOf(ref.returnDesc, "Landroid/widget/LinearLayout;")) {
                    continue;
                }
                for (DexScanner.MethodInfo m : scanner.getMethods(ref.returnDesc)) {
                    if (!m.isStatic && m.paramDescs.length == 2
                            && "I".equals(m.paramDescs[0])
                            && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[1])
                            && m.returnDesc != null) {
                        boolean itemPkgOk = m.returnDesc.startsWith("Lorg/telegram/ui/ActionBar/")
                                || (flatActionBar && m.returnDesc.startsWith("L")
                                && !m.returnDesc.startsWith("Landroid/") && !m.returnDesc.startsWith("Ljava/")
                                && (scanner.isSubclassOf(m.returnDesc, "Landroid/widget/FrameLayout;")
                                || scanner.isSubclassOf(m.returnDesc, "Landroid/view/View;")));
                        if (itemPkgOk) {
                            ctx.addMethod("ActionBar", "createMenu", ref.name);
                            ctx.addClass(ClassNames.ACTION_BAR_MENU, ref.returnDesc);
                            ctx.addMethod("ActionBarMenu", "addItemDrawable", m.name);
                            menuItemDesc = m.returnDesc;
                            break;
                        }
                    }
                }
                if (menuItemDesc != null) break;
            }
        }
        if (menuItemDesc != null) {
            ctx.addClass("org.telegram.ui.ActionBar.ActionBarMenuItem", menuItemDesc);
            String lazilyAddSubItem = null;
            String addSubItem = null;
            for (DexScanner.MethodInfo mm : scanner.getMethods(menuItemDesc)) {
                if (mm.isStatic || mm.paramDescs.length != 3) continue;
                if (!"I".equals(mm.paramDescs[0]) || !"I".equals(mm.paramDescs[1])) continue;
                if (!"Ljava/lang/CharSequence;".equals(mm.paramDescs[2]) && !"Ljava/lang/String;".equals(mm.paramDescs[2])) continue;
                String retSup = scanner.getSuperClass(mm.returnDesc);
                if ("Ljava/lang/Object;".equals(retSup)) {
                    if ("lazilyAddSubItem".equals(mm.name) || lazilyAddSubItem == null) {
                        lazilyAddSubItem = mm.name;
                    }
                } else if (!"V".equals(mm.returnDesc)) {
                    if ("addSubItem".equals(mm.name) || addSubItem == null) {
                        addSubItem = mm.name;
                    }
                }
            }
            if (lazilyAddSubItem != null) ctx.addMethod("ActionBarMenuItem", "lazilyAddSubItem", lazilyAddSubItem);
            if (addSubItem != null) ctx.addMethod("ActionBarMenuItem", "addSubItem", addSubItem);
            if (chatActDesc != null) {
                String headerFld = null;
                if (MappingContext.hasFieldNamed(scanner, chatActDesc, "headerItem")) {
                    headerFld = "headerItem";
                } else {
                    String chatCreateView = ctx.methodMap.getOrDefault("ChatActivity#createView", "createView");
                    Map<String, Integer> readCounts = new LinkedHashMap<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            chatActDesc, chatCreateView, new String[]{"Landroid/content/Context;"}, chatActDesc)) {
                        if (menuItemDesc.equals(rf.typeDesc)) {
                            readCounts.put(rf.name, readCounts.getOrDefault(rf.name, 0) + 1);
                        }
                    }
                    int bestCount = 0;
                    for (Map.Entry<String, Integer> e : readCounts.entrySet()) {
                        if (e.getValue() > bestCount) {
                            bestCount = e.getValue();
                            headerFld = e.getKey();
                        }
                    }
                    if (headerFld == null) {
                        for (DexScanner.FieldInfo f : scanner.getFields(chatActDesc)) {
                            if (!f.isStatic && menuItemDesc.equals(f.typeDesc)) {
                                headerFld = f.name;
                                break;
                            }
                        }
                    }
                }
                if (headerFld != null) ctx.addField("ChatActivity", "headerItem", headerFld);
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
                    ctx.addMethod("DialogsActivity", "updateProxyButton", m.name);
                    resolvedUpdateProxy = true;
                    break;
                }
            }
        }
        if (!resolvedUpdateProxy && proxyUpdateFallback != null) {
            ctx.addMethod("DialogsActivity", "updateProxyButton", proxyUpdateFallback.name);
        }
        resolveDialogsActivityDownloads(ctx, scanner, dialogsActDesc, ctx.methodMap.get("DialogsActivity#updateProxyButton"));

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
                ctx.addMethod("DialogsActivity", "updateHeaderVisibility", m.name);
                if (menuItemDesc != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            dialogsActDesc, m.name, m.paramDescs, dialogsActDesc)) {
                        if (menuItemDesc.equals(rf.typeDesc)) {
                            overflowItemName = rf.name;
                            ctx.addField("DialogsActivity", "overflowItem", rf.name);
                            break;
                        }
                    }
                }
                break;
            }
        }

        String showMenuName = scanner.findMethodByString(dialogsActDesc, "proxy_enabled", false, "V");
        if (showMenuName == null) {
            int bestAddCalls = 1;
            for (DexScanner.MethodInfo m : scanner.getMethods(dialogsActDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0) continue;
                int addCalls = 0;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                        dialogsActDesc, m.name, m.paramDescs, null)) {
                    if (ref.ownerDesc.startsWith("L")
                            && !ref.ownerDesc.startsWith("Ljava/") && !ref.ownerDesc.startsWith("Landroid/")
                            && ("V".equals(ref.returnDesc) || ref.ownerDesc.equals(ref.returnDesc))
                            && isItemOptionsIconAddParams(ref.paramDescs)) {
                        addCalls++;
                    }
                }
                if (addCalls > bestAddCalls) {
                    bestAddCalls = addCalls;
                    showMenuName = m.name;
                }
            }
        }
        String itemOptionsDesc = null;
        if (showMenuName != null) {
            ctx.addMethod("DialogsActivity", "showOverflowMenu", showMenuName);
            String[] noParams = new String[0];
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
                    ctx.addClass(ClassNames.ITEM_OPTIONS, itemOptionsDesc);
                    ctx.addMethod("ItemOptions", "addViewSubItem", addViewName);
                    break;
                }
            }
            if (itemOptionsDesc == null) {
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                        dialogsActDesc, showMenuName, noParams, null)) {
                    if (ref.ownerDesc.startsWith("L")
                            && !ref.ownerDesc.startsWith("Ljava/") && !ref.ownerDesc.startsWith("Landroid/")
                            && ("V".equals(ref.returnDesc) || ref.ownerDesc.equals(ref.returnDesc))
                            && isItemOptionsIconAddParams(ref.paramDescs)) {
                        itemOptionsDesc = ref.ownerDesc;
                        ctx.addClass(ClassNames.ITEM_OPTIONS, itemOptionsDesc);
                        break;
                    }
                }
            }
            if (itemOptionsDesc != null) {
                String addIconName = resolveItemOptionsAddIconMethod(scanner, itemOptionsDesc);
                if (addIconName != null) {
                    ctx.addMethod("ItemOptions", "addIconItem", addIconName);
                }
            }
            if (subItemDesc != null) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        dialogsActDesc, showMenuName, noParams, dialogsActDesc)) {
                    if (subItemDesc.equals(rf.typeDesc)) {
                        subItemFieldName = rf.name;
                        ctx.addField("DialogsActivity", "proxySubItem", rf.name);
                        break;
                    }
                }
            }
            if (overflowItemName == null) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        dialogsActDesc, showMenuName, noParams, dialogsActDesc)) {
                    boolean typeMatch = (menuItemDesc != null && menuItemDesc.equals(rf.typeDesc))
                            || (menuItemDesc == null
                                && rf.typeDesc.startsWith("L")
                                && !rf.typeDesc.startsWith("Ljava/") && !rf.typeDesc.startsWith("Landroid/")
                                && !rf.typeDesc.startsWith("[")
                                && !rf.name.equals(subItemFieldName)
                                && (subItemDesc == null || !subItemDesc.equals(rf.typeDesc)));
                    if (typeMatch) {
                        overflowItemName = rf.name;
                        ctx.addField("DialogsActivity", "overflowItem", overflowItemName);
                        break;
                    }
                }
            }
            if (itemOptionsDesc != null) {
                List<DexScanner.MethodRef> optCalls = scanner.getCalledMethodsInMethod(
                        dialogsActDesc, showMenuName, noParams, itemOptionsDesc);
                if (addViewName != null) {
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
                                ctx.addMethod("ItemOptions", "addGap", addGapName);
                                break;
                            }
                        }
                        for (int i = addIdx + 1; i < optCalls.size(); i++) {
                            DexScanner.MethodRef c = optCalls.get(i);
                            if (c.paramDescs.length == 0
                                    && (addGapName == null || !addGapName.equals(c.name))
                                    && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                                ctx.addMethod("ItemOptions", "show", c.name);
                                break;
                            }
                        }
                    }
                } else {
                    String gapMethod = MappingContext.hasZeroArgMethodNamed(scanner, itemOptionsDesc, "addGap") ? "addGap" : null;
                    int firstAdd = -1;
                    int lastAdd = -1;
                    for (int i = 0; i < optCalls.size(); i++) {
                        DexScanner.MethodRef c = optCalls.get(i);
                        if (isItemOptionsIconAddParams(c.paramDescs)) {
                            if (firstAdd < 0) firstAdd = i;
                            lastAdd = i;
                        }
                    }
                    if (gapMethod == null && firstAdd >= 0 && lastAdd > firstAdd) {
                        for (int i = firstAdd + 1; i < lastAdd; i++) {
                            DexScanner.MethodRef c = optCalls.get(i);
                            if (c.paramDescs.length == 0
                                    && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))) {
                                gapMethod = c.name;
                                break;
                            }
                        }
                    }
                    if (gapMethod != null) {
                        ctx.addMethod("ItemOptions", "addGap", gapMethod);
                    }
                    String showMethod = null;
                    if (MappingContext.hasZeroArgMethodNamed(scanner, itemOptionsDesc, "show")) {
                        showMethod = "show";
                    } else if (lastAdd >= 0) {
                        for (int i = lastAdd + 1; i < optCalls.size(); i++) {
                            DexScanner.MethodRef c = optCalls.get(i);
                            if (c.paramDescs.length == 0
                                    && ("V".equals(c.returnDesc) || itemOptionsDesc.equals(c.returnDesc))
                                    && (gapMethod == null || !gapMethod.equals(c.name))) {
                                showMethod = c.name;
                                break;
                            }
                        }
                    }
                    if (showMethod != null) {
                        ctx.addMethod("ItemOptions", "show", showMethod);
                    }
                }
            }
        }

        if (updateHeaderVisName == null && overflowItemName != null) {
            if (overflowItemName != null) {
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
                            ctx.addMethod("DialogsActivity", "updateHeaderVisibility", m.name);
                            updateHeaderVisName = m.name;
                            break;
                        }
                    }
                    if (updateHeaderVisName != null) break;
                }
            }
        }
        if (itemOptionsDesc == null) {
            itemOptionsDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (methods.size() < 40 || methods.size() > 160) return false;
                boolean hasAddIcon = false, hasLargeShow = false, hasAddGap = false;
                for (DexScanner.MethodInfo m : methods) {
                    if (!m.isStatic && desc.equals(m.returnDesc)) {
                        if (isItemOptionsIconAddParams(m.paramDescs) && m.paramDescs.length == 3) hasAddIcon = true;
                        if (m.paramDescs.length == 0 && m.getInsnsSize() > 100) hasLargeShow = true;
                        if (m.paramDescs.length == 0 && m.getInsnsSize() > 5 && m.getInsnsSize() <= 45) hasAddGap = true;
                    }
                }
                return hasAddIcon && hasLargeShow && hasAddGap;
            });
            if (itemOptionsDesc != null) {
                ctx.addClass(ClassNames.ITEM_OPTIONS, itemOptionsDesc);
                String addIconName = resolveItemOptionsAddIconMethod(scanner, itemOptionsDesc);
                if (addIconName != null) ctx.addMethod("ItemOptions", "addIconItem", addIconName);
                String showMethod = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(itemOptionsDesc)) {
                    if (!m.isStatic && itemOptionsDesc.equals(m.returnDesc) && m.paramDescs.length == 0 && m.getInsnsSize() > 100) {
                        if (showMethod == null || m.getInsnsSize() > 150) {
                            showMethod = m.name;
                        }
                    }
                }
                if (showMethod != null) ctx.addMethod("ItemOptions", "show", showMethod);
            }
        }
    }

    static boolean isItemOptionsIconAddParams(String[] p) {
        if (p == null) return false;
        if (p.length == 3) {
            return "I".equals(p[0])
                    && "Ljava/lang/CharSequence;".equals(p[1])
                    && "Ljava/lang/Runnable;".equals(p[2]);
        }
        if (p.length == 4) {
            return "I".equals(p[0])
                    && "Ljava/lang/CharSequence;".equals(p[1])
                    && (("Ljava/lang/Runnable;".equals(p[2]) && "Z".equals(p[3]))
                        || ("Z".equals(p[2]) && "Ljava/lang/Runnable;".equals(p[3])));
        }
        return false;
    }

    static String resolveItemOptionsAddIconMethod(DexScanner scanner, String itemOptionsDesc) {
        String best4Arg = null;
        int best4Score = -1;
        for (DexScanner.MethodInfo m : scanner.getMethods(itemOptionsDesc)) {
            if (m.isStatic || !("V".equals(m.returnDesc) || itemOptionsDesc.equals(m.returnDesc))) {
                continue;
            }
            if (!isItemOptionsIconAddParams(m.paramDescs)) continue;
            if (m.paramDescs.length == 3) {
                return m.name;
            }
            if ("add".equals(m.name)) {
                return "add";
            }
            int staticReads = scanner.getReadStaticFieldsInMethod(
                    itemOptionsDesc, m.name, m.paramDescs, null).size();
            int score = staticReads * 1000 + m.getInsnsSize();
            if (score > best4Score) {
                best4Score = score;
                best4Arg = m.name;
            }
        }
        return best4Arg;
    }

    static void resolveMainTabs(MappingContext ctx, DexScanner scanner, String userConfigDesc) {
        String mainTabsDesc = MappingContext.findClassByStringsWithFallback(
                scanner, "Lorg/telegram/ui/", null, "askAboutContacts2", "my_profile");
        if (mainTabsDesc == null || "Lorg/telegram/ui/ContactsActivity;".equals(mainTabsDesc)) {
            mainTabsDesc = MappingContext.findClassByStringsWithFallback(
                    scanner, "Lorg/telegram/ui/", null, "askAboutContacts2", "SwitchAccountHint");
        }
        if ((mainTabsDesc == null || "Lorg/telegram/ui/ContactsActivity;".equals(mainTabsDesc))
                && !scanner.getMethods("Lorg/telegram/ui/MainTabsActivity;").isEmpty()) {
            mainTabsDesc = "Lorg/telegram/ui/MainTabsActivity;";
        }
        if (mainTabsDesc == null || "Lorg/telegram/ui/ContactsActivity;".equals(mainTabsDesc)) {
            return;
        }
        ctx.addClass(ClassNames.MAIN_TABS_ACTIVITY, mainTabsDesc);

        String tabsArrField = null;
        String wrapperField = null;
        String fadeViewField = null;
        String tabsViewField = null;
        String animLinearDesc = null;
        String setViewVisibleMethod = null;

        for (DexScanner.FieldInfo f : scanner.getFields(mainTabsDesc)) {
            if (f.isStatic) continue;
            if (tabsArrField == null && f.typeDesc.startsWith("[L") && !f.typeDesc.startsWith("[Ljava/")) {
                tabsArrField = f.name;
            }
            if (wrapperField == null && "Landroid/widget/FrameLayout;".equals(f.typeDesc)) {
                wrapperField = f.name;
            }
            if ("Landroid/view/View;".equals(f.typeDesc)) {
                if (fadeViewField == null) fadeViewField = f.name;
            }
            if (tabsViewField == null && f.typeDesc.startsWith("L")
                    && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/") && !f.typeDesc.startsWith("[")) {
                String sup = scanner.getSuperClass(f.typeDesc);
                if (sup != null && !"Ljava/lang/Object;".equals(sup)) {
                    String candidateVisible = null;
                    for (DexScanner.MethodInfo m : scanner.getMethods(sup)) {
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 3
                                && "Landroid/view/View;".equals(m.paramDescs[0])
                                && "Z".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2])) {
                            if ("setViewVisible".equals(m.name)) {
                                candidateVisible = m.name;
                                break;
                            }
                            if (candidateVisible == null) {
                                candidateVisible = m.name;
                            }
                        }
                    }
                    if (candidateVisible != null) {
                        tabsViewField = f.name;
                        animLinearDesc = sup;
                        setViewVisibleMethod = candidateVisible;
                    }
                }
            }
        }

        if (tabsArrField != null) ctx.addField("MainTabsActivity", "tabs", tabsArrField);
        if (wrapperField != null) ctx.addField("MainTabsActivity", "tabsViewWrapper", wrapperField);
        if (tabsViewField != null) ctx.addField("MainTabsActivity", "tabsView", tabsViewField);
        if (setViewVisibleMethod != null) ctx.addMethod("AnimatedLinearLayout", "setViewVisible", setViewVisibleMethod);

        String checkCallTabMethod = null;
        String selectTabMethod = null;
        String tabsPosMethod = null;
        String fadeViewMethod = null;

        for (DexScanner.MethodInfo m : scanner.getMethods(mainTabsDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc)) continue;
            if (checkCallTabMethod == null && m.paramDescs.length == 2
                    && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                    && !"prepareFragmentToSlide".equals(m.name) && setViewVisibleMethod != null) {
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(mainTabsDesc, m.name, m.paramDescs, null)) {
                    if (setViewVisibleMethod.equals(ref.name) && ref.paramDescs.length == 3
                            && (animLinearDesc.equals(ref.ownerDesc) || "Lorg/telegram/ui/Components/AnimatedLinearLayout;".equals(ref.ownerDesc))) {
                        checkCallTabMethod = m.name;
                        break;
                    }
                }
            } else if (selectTabMethod == null && m.paramDescs.length == 2
                    && "I".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) {
                selectTabMethod = m.name;
            } else if (tabsPosMethod == null && m.paramDescs.length == 4
                    && "I".equals(m.paramDescs[0]) && "F".equals(m.paramDescs[1]) && "F".equals(m.paramDescs[2])) {
                List<String> selfVoidCalls = new ArrayList<>();
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(mainTabsDesc, m.name, m.paramDescs, mainTabsDesc)) {
                    if ("V".equals(ref.returnDesc) && ref.paramDescs.length == 0) {
                        selfVoidCalls.add(ref.name);
                    }
                }
                if (selfVoidCalls.size() >= 2) {
                    tabsPosMethod = selfVoidCalls.get(0);
                    fadeViewMethod = selfVoidCalls.get(1);
                } else if (selfVoidCalls.size() == 1) {
                    tabsPosMethod = selfVoidCalls.get(0);
                }
            }
        }

        if (fadeViewMethod != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                    mainTabsDesc, fadeViewMethod, new String[0], mainTabsDesc)) {
                if ("Landroid/view/View;".equals(rf.typeDesc)
                        || (rf.typeDesc.startsWith("L") && !rf.typeDesc.startsWith("Ljava/") && !rf.typeDesc.startsWith("Landroid/")
                        && "Landroid/view/View;".equals(scanner.getSuperClass(rf.typeDesc)))) {
                    fadeViewField = rf.name;
                    break;
                }
            }
        }
        if (fadeViewField == null) {
            for (DexScanner.FieldInfo f : scanner.getFields(mainTabsDesc)) {
                if (!f.isStatic && f.typeDesc.startsWith("L") && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")
                        && "Landroid/view/View;".equals(scanner.getSuperClass(f.typeDesc))) {
                    fadeViewField = f.name;
                    break;
                }
            }
        }

        if (fadeViewField != null) ctx.addField("MainTabsActivity", "fadeView", fadeViewField);
        if (checkCallTabMethod != null) ctx.addMethod("MainTabsActivity", "checkUi_callTabVisible", checkCallTabMethod);
        if (selectTabMethod != null) ctx.addMethod("MainTabsActivity", "selectTab", selectTabMethod);
        if (tabsPosMethod != null) ctx.addMethod("MainTabsActivity", "checkUi_tabsPosition", tabsPosMethod);
        if (fadeViewMethod != null) ctx.addMethod("MainTabsActivity", "checkUi_fadeView", fadeViewMethod);

        String vpaDesc = scanner.getSuperClass(mainTabsDesc);
        if (vpaDesc != null && !"Ljava/lang/Object;".equals(vpaDesc)) {
            String vpFieldName = null;
            String vpLayoutDesc = null;
            String vpFixedDesc = null;
            for (DexScanner.FieldInfo f : scanner.getFields(vpaDesc)) {
                if (f.isStatic) continue;
                String sup = scanner.getSuperClass(f.typeDesc);
                if (sup != null && !sup.startsWith("Landroid/") && !sup.startsWith("Ljava/")) {
                    vpFieldName = f.name;
                    vpLayoutDesc = f.typeDesc;
                    vpFixedDesc = sup;
                    break;
                }
            }
            if (vpFieldName != null && vpLayoutDesc != null) {
                ctx.addField("ViewPagerActivity", "viewPager", vpFieldName);
                ctx.addClass(ClassNames.VIEW_PAGER_ACTIVITY_PAGER_LAYOUT, vpLayoutDesc);

                String backwardMeth = null;
                String forwardMeth = null;
                for (DexScanner.MethodInfo m : scanner.getMethods(vpLayoutDesc)) {
                    if (!m.isStatic && "Z".equals(m.returnDesc)
                            && m.paramDescs.length == 1 && "Landroid/view/MotionEvent;".equals(m.paramDescs[0])) {
                        if ("canScrollBackward".equals(m.name)) {
                            backwardMeth = m.name;
                        } else if ("canScrollForward".equals(m.name)) {
                            forwardMeth = m.name;
                        } else if (m.containsConstInt(1)) {
                            forwardMeth = m.name;
                        } else {
                            backwardMeth = m.name;
                        }
                    }
                }
                if (backwardMeth != null) ctx.addMethod("ViewPagerActivityPagerLayout", "canScrollBackward", backwardMeth);
                if (forwardMeth != null) ctx.addMethod("ViewPagerActivityPagerLayout", "canScrollForward", forwardMeth);

                String getPosMeth = null;
                String scrollToPosMeth = null;
                for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(mainTabsDesc, "onBackPressed", new String[]{"Z"}, null)) {
                    if (!ref.ownerDesc.equals(vpFixedDesc) && !ref.ownerDesc.equals(vpLayoutDesc)) continue;
                    if (getPosMeth == null && ref.paramDescs.length == 0 && "I".equals(ref.returnDesc)) {
                        getPosMeth = ref.name;
                    } else if (scrollToPosMeth == null && ref.paramDescs.length == 1 && "I".equals(ref.paramDescs[0])
                            && ("Z".equals(ref.returnDesc) || "V".equals(ref.returnDesc))) {
                        scrollToPosMeth = ref.name;
                    }
                }
                if (getPosMeth == null && vpFixedDesc != null && MappingContext.hasZeroArgMethodNamed(scanner, vpFixedDesc, "getCurrentPosition")) {
                    getPosMeth = "getCurrentPosition";
                }
                if (scrollToPosMeth == null && vpFixedDesc != null) {
                    for (DexScanner.MethodInfo m : scanner.getMethods(vpFixedDesc)) {
                        if (!m.isStatic && "scrollToPosition".equals(m.name)
                                && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                            scrollToPosMeth = "scrollToPosition";
                            break;
                        }
                    }
                }
                if (getPosMeth != null) ctx.addMethod("ViewPagerFixed", "getCurrentPosition", getPosMeth);
                if (scrollToPosMeth != null) ctx.addMethod("ViewPagerFixed", "scrollToPosition", scrollToPosMeth);
            }
        }

        if (userConfigDesc != null) {
            String showCallsTabField = null;
            if (MappingContext.hasFieldNamed(scanner, userConfigDesc, "showCallsTab")) {
                showCallsTabField = "showCallsTab";
            }
            if (showCallsTabField == null) {
                String mainCreateView = ctx.methodMap.getOrDefault("MainTabsActivity#createView",
                        ctx.methodMap.getOrDefault("BaseFragment#createView", "createView"));
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        mainTabsDesc, mainCreateView, new String[]{"Landroid/content/Context;"}, userConfigDesc)) {
                    if ("Z".equals(rf.typeDesc)) {
                        showCallsTabField = rf.name;
                        break;
                    }
                }
            }
            if (showCallsTabField == null) {
                String dialogsJava = ctx.classMap.get(ClassNames.DIALOGS_ACTIVITY);
                String showMenuName = ctx.methodMap.get("DialogsActivity#showOverflowMenu");
                if (dialogsJava != null && showMenuName != null) {
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            DexScanner.javaToDesc(dialogsJava), showMenuName, new String[0], userConfigDesc)) {
                        if ("Z".equals(rf.typeDesc)) {
                            showCallsTabField = rf.name;
                            break;
                        }
                    }
                }
            }
            if (showCallsTabField != null) {
                ctx.addField("UserConfig", "showCallsTab", showCallsTabField);
            }
        }
    }


    static void resolveRecyclerView(MappingContext ctx, DexScanner scanner) {
        String rvDesc = "Landroidx/recyclerview/widget/RecyclerView;";
        if (!MappingContext.hasClass(scanner, rvDesc)) {
            rvDesc = scanner.findClassByStrings(null, "Landroid/view/ViewGroup;",
                    "No adapter attached; skipping layout");
        }
        if (rvDesc == null) return;
        ctx.addClass("androidx.recyclerview.widget.RecyclerView", rvDesc);

        String scrollToPos = scanner.findMethodByString(rvDesc,
                "Cannot scroll to position a LayoutManager set. Call setLayoutManager with a non-null argument.",
                false, "V", "I");
        if (scrollToPos == null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(rvDesc)) {
                if (!m.isStatic && "scrollToPosition".equals(m.name)
                        && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    scrollToPos = m.name;
                    break;
                }
            }
        }
        ctx.addMethod("RecyclerView", "scrollToPosition", scrollToPos);

        String smoothScrollToPos = scanner.findMethodByString(rvDesc,
                "Cannot smooth scroll without a LayoutManager set. Call setLayoutManager with a non-null argument.",
                false, "V", "I");
        if (smoothScrollToPos == null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(rvDesc)) {
                if (!m.isStatic && "smoothScrollToPosition".equals(m.name)
                        && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    smoothScrollToPos = m.name;
                    break;
                }
            }
        }
        ctx.addMethod("RecyclerView", "smoothScrollToPosition", smoothScrollToPos);

        // mLayout & LayoutManager descriptor
        String lmDesc = null;
        String mLayoutField = null;
        DexScanner.MethodInfo setLmMethod = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(rvDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1) continue;
            String p0 = m.paramDescs[0];
            if (!p0.startsWith("L") || p0.startsWith("Ljava/") || p0.startsWith("Landroid/")) continue;
            if ("setLayoutManager".equals(m.name)
                    || m.usesString("LayoutManager ")
                    || m.usesString(" is already attached to a RecyclerView:")) {
                setLmMethod = m;
                lmDesc = p0;
                break;
            }
        }
        if (setLmMethod != null && lmDesc != null) {
            for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                    rvDesc, setLmMethod.name, setLmMethod.paramDescs, rvDesc)) {
                if (lmDesc.equals(wf.typeDesc)) {
                    mLayoutField = wf.name;
                    break;
                }
            }
            if (mLayoutField == null) {
                mLayoutField = scanner.findSingleField(rvDesc, false, lmDesc);
            }
        }
        if (mLayoutField == null) {
            for (DexScanner.FieldInfo f : scanner.getFields(rvDesc)) {
                if (!f.isStatic && "mLayout".equals(f.name)) {
                    mLayoutField = f.name;
                    lmDesc = f.typeDesc;
                    break;
                }
            }
        }
        ctx.addField("RecyclerView", "mLayout", mLayoutField);

        // LinearLayoutManager & scrollToPositionWithOffset
        String llmDesc = "Landroidx/recyclerview/widget/LinearLayoutManager;";
        if (!MappingContext.hasClass(scanner, llmDesc)) {
            llmDesc = lmDesc != null
                    ? scanner.findClassByStrings(null, lmDesc, "invalid orientation:")
                    : null;
        }
        if (llmDesc != null) {
            ctx.addClass("androidx.recyclerview.widget.LinearLayoutManager", llmDesc);
            String scrollWithOffset = null;
            DexScanner.MethodInfo smallestTwoInt = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(llmDesc)) {
                if (m.isStatic || m.name.startsWith("<") || m.name.startsWith("lambda$")) continue;
                if ("V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "I".equals(m.paramDescs[0]) && "I".equals(m.paramDescs[1])) {
                    if ("scrollToPositionWithOffset".equals(m.name)) {
                        scrollWithOffset = m.name;
                        break;
                    }
                    int sz = m.getInsnsSize();
                    if (sz > 0 && sz <= 25 && (smallestTwoInt == null || sz < smallestTwoInt.getInsnsSize())) {
                        smallestTwoInt = m;
                    }
                }
            }
            if (scrollWithOffset == null && smallestTwoInt != null) {
                scrollWithOffset = smallestTwoInt.name;
            }
            ctx.addMethod("LinearLayoutManager", "scrollToPositionWithOffset", scrollWithOffset);
        }

        // mAdapter & RecyclerView$Adapter#getItemCount
        String mAdapterField = null;
        String adapterDesc = null;
        String getItemCountMethod = null;
        for (DexScanner.FieldInfo f : scanner.getFields(rvDesc)) {
            if (!f.isStatic && "mAdapter".equals(f.name)) {
                mAdapterField = f.name;
                adapterDesc = f.typeDesc;
                break;
            }
        }
        if (adapterDesc != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(adapterDesc)) {
                if (!m.isStatic && "getItemCount".equals(m.name)
                        && "I".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    getItemCountMethod = m.name;
                    break;
                }
            }
        }
        if (mAdapterField == null || getItemCountMethod == null) {
            for (DexScanner.FieldInfo f : scanner.getFields(rvDesc)) {
                if (f.isStatic) continue;
                String ft = f.typeDesc;
                if (ft == null || !ft.startsWith("L") || ft.startsWith("Ljava/") || ft.startsWith("Landroid/")) continue;
                if (ft.equals(lmDesc)) continue;
                if (!"Ljava/lang/Object;".equals(scanner.getSuperClass(ft))) continue;
                String abstractIntName = null;
                int abstractIntCount = 0;
                boolean hasCreateVh = false;
                for (DexScanner.MethodInfo m : scanner.getMethods(ft)) {
                    if (m.isStatic || m.codeOff != 0) continue;
                    if ("I".equals(m.returnDesc) && m.paramDescs.length == 0) {
                        abstractIntName = m.name;
                        abstractIntCount++;
                    } else if (m.paramDescs.length == 2
                            && "Landroid/view/ViewGroup;".equals(m.paramDescs[0])
                            && "I".equals(m.paramDescs[1])
                            && m.returnDesc != null && m.returnDesc.startsWith("L")) {
                        hasCreateVh = true;
                    }
                }
                if (hasCreateVh && abstractIntCount == 1 && abstractIntName != null) {
                    mAdapterField = f.name;
                    getItemCountMethod = abstractIntName;
                    break;
                }
            }
        }
        ctx.addField("RecyclerView", "mAdapter", mAdapterField);
        ctx.addMethod("RecyclerView$Adapter", "getItemCount", getItemCountMethod);
    }

}
