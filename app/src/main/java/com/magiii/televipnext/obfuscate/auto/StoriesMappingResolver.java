package com.magiii.televipnext.obfuscate.auto;

import com.magiii.televipnext.Class.ClassNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves StoriesController, StoryItemHolder, DialogStoriesCell, and Profile stories mappings.
 */
final class StoriesMappingResolver {

    private StoriesMappingResolver() {}

    static void resolveStoryItemHolder(MappingContext ctx, DexScanner scanner) {
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
        if (holderDesc == null) {
            String tlObjDesc = ctx.resolvedDescOrRaw(ClassNames.TL_OBJECT);
            // Obfuscated StoryItem / StoryItem#noforwards fallback (e.g. OctoGram g$s0 / Nekogram jz7):
            // Class has 3..35 fields, 2..25 methods, holds >= 2 fields of the same TLObject subclass (>=15 fields),
            // and has a 40..59-insn ()Z method reading >= 2 Z fields & a J field (allowScreenshots).
            holderDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (methods.size() < 2 || methods.size() > 25 || fields.size() < 3 || fields.size() > 35) {
                    return false;
                }
                Map<String, Integer> tlCounts = new HashMap<>();
                for (DexScanner.FieldInfo f : fields) {
                    if (!f.isStatic && f.typeDesc.startsWith("L")
                            && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")
                            && scanner.isSubclassOf(f.typeDesc, tlObjDesc)
                            && scanner.getFields(f.typeDesc).size() >= 15) {
                        tlCounts.merge(f.typeDesc, 1, Integer::sum);
                    }
                }
                if (tlCounts.isEmpty()) return false;
                for (Integer cnt : tlCounts.values()) {
                    if (cnt < 2) continue;
                    for (DexScanner.MethodInfo m : methods) {
                        int sz = m.getInsnsSize();
                        if (!m.isStatic && "Z".equals(m.returnDesc) && m.paramDescs.length == 0
                                && sz >= 40 && sz <= 59) {
                            int zReads = 0, jReads = 0;
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                                    desc, m.name, new String[0], null)) {
                                if ("Z".equals(rf.typeDesc)) zReads++;
                                else if ("J".equals(rf.typeDesc)) jReads++;
                            }
                            if (zReads >= 2 && jReads >= 1) {
                                allowMethodHolder[0] = m.name;
                                return true;
                            }
                        }
                    }
                }
                return false;
            });
        }
        if (holderDesc != null && allowMethodHolder[0] != null) {
            ctx.addClass(ClassNames.STORY_ITEM_HOLDER, holderDesc);
            ctx.addMethod("PeerStoriesView$StoryItemHolder", "allowScreenshots", allowMethodHolder[0]);
        }
    }


    static void resolveStoriesController(MappingContext ctx, DexScanner scanner) {
        String storiesCtrlDesc = scanner.findClassByStrings(null, null, "StoriesController loadAllStoriesForDialog ");
        if (storiesCtrlDesc == null) {
            storiesCtrlDesc = scanner.findClassByStrings(null, null, "StoriesController cache=");
        }
        if (storiesCtrlDesc == null) return;

        ctx.addClass(ClassNames.STORIES_CONTROLLER, storiesCtrlDesc);
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
                        ctx.addMethod("StoriesController", "hasStories", m.name);
                        hasZeroMethods.add(m.name);
                    } else if (readsStories) {
                        ctx.addMethod("StoriesController", "hasSelfStories", m.name);
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
                            ctx.addMethod("StoriesController", "canPostStories", m.name);
                        }
                    }
                } else if (m.paramDescs.length == 1 && "Lorg/telegram/tgnet/TLRPC$Chat;".equals(m.paramDescs[0])
                        && "canPostStories".equals(m.name)) {
                    ctx.addMethod("StoriesController", "canPostStoriesChat", m.name);
                }
            } else if ("V".equals(m.returnDesc)) {
                if (m.paramDescs.length == 4
                        && m.paramDescs[0].startsWith("L")
                        && "Z".equals(m.paramDescs[1])
                        && "Z".equals(m.paramDescs[2])
                        && "Z".equals(m.paramDescs[3])
                        && sz > 200) {
                    ctx.addMethod("StoriesController", "processAllStoriesResponse", m.name);
                    Set<String> scLists = new LinkedHashSet<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(storiesCtrlDesc, m.name, m.paramDescs, storiesCtrlDesc)) {
                        if ("Ljava/util/ArrayList;".equals(rf.typeDesc)) {
                            scLists.add(rf.name);
                        }
                    }
                    if (!scLists.isEmpty()) {
                        ctx.addField("StoriesController", "storyListFields", String.join(",", scLists));
                    }
                } else if (m.paramDescs.length == 1 && m.paramDescs[0].contains("TL_updateStory")) {
                    ctx.addMethod("StoriesController", "processUpdate", m.name);
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
            ctx.addMethod("StoriesController", "hasStoriesForDialog", bestHasForDialog.name);
        }
        if (!hasZeroMethods.isEmpty()) {
            ctx.addMethod("StoriesController", "hasStoriesAll", String.join(",", new LinkedHashSet<>(hasZeroMethods)));
        }
        if (!peerVoidMethods.isEmpty()) {
            ctx.addMethod("StoriesController", "peerListAddMethods", String.join(",", new LinkedHashSet<>(peerVoidMethods)));
        }
        if (!getPeerMethods.isEmpty()) {
            ctx.addMethod("StoriesController", "getPeerStoriesMethods", String.join(",", new LinkedHashSet<>(getPeerMethods)));
        }

        if (storiesListDesc != null) {
            ctx.addClass(ClassNames.STORIES_LIST, storiesListDesc);
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
                        ctx.addMethod("StoriesList", "load", m.name);
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
                        ctx.addMethod("StoriesList", "getCount", m.name);
                    } else if (sz <= 12 && readsSet) {
                        ctx.addMethod("StoriesList", "getLoadedCount", m.name);
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
                ctx.addClass(ClassNames.SHARED_MEDIA_LAYOUT, smlDesc);
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
                                ctx.addMethod("SharedMediaLayout", "includeStories", m1);
                                break;
                            }
                        }
                    }
                }

                String fallbackApm = null;
                boolean resolvedPostText = false;
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
                        ctx.addMethod("SharedMediaLayout", "addPostText", apm);
                        ctx.addClass(ClassNames.BUTTON_WITH_COUNTER_VIEW, bc);
                        ctx.addMethod("ButtonWithCounterView", "setText", bst);
                        resolvedPostText = true;
                        break;
                    }
                    if (fallbackApm == null && apm != null) {
                        fallbackApm = apm;
                    }
                }
                if (!resolvedPostText && fallbackApm != null) {
                    ctx.addMethod("SharedMediaLayout", "addPostText", fallbackApm);
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
            ctx.addClass(ClassNames.DIALOG_STORIES_CELL, dscDesc);
            String dscSetOverscroll = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(dscDesc)) {
                if (m.isStatic) continue;
                if ("V".equals(m.returnDesc) && m.paramDescs.length == 2
                        && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1]) && m.getInsnsSize() > 200) {
                    ctx.addMethod("DialogStoriesCell", "updateItems", m.name);
                } else if ("V".equals(m.returnDesc) && m.paramDescs.length == 1 && "F".equals(m.paramDescs[0])) {
                    if ("setOverscroll".equals(m.name)) {
                        dscSetOverscroll = "setOverscroll";
                    } else if (dscSetOverscroll == null) {
                        dscSetOverscroll = m.name;
                    }
                }
            }
            if (dscSetOverscroll != null) {
                ctx.addMethod("DialogStoriesCell", "setOverscroll", dscSetOverscroll);
            }

            String daJava = ctx.classMap.get(ClassNames.DIALOGS_ACTIVITY);
            if (daJava == null) daJava = ClassNames.DIALOGS_ACTIVITY;
            String daDesc = DexScanner.javaToDesc(daJava);
            for (DexScanner.FieldInfo f : scanner.getFields(daDesc)) {
                if (!f.isStatic && scanner.isSubclassOf(f.typeDesc, dscDesc)) {
                    String daDscField = f.name;
                    ctx.addField("DialogsActivity", "dialogStoriesCell", daDscField);
                    String visMethodName = null;
                    String autoMethodName = null;
                    String[] autoParamDescs = null;

                    for (DexScanner.MethodInfo m : scanner.getMethods(daDesc)) {
                        if (!m.readsFieldNamed(daDscField)) continue;
                        int sz = m.getInsnsSize();
                        if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1
                                && "Z".equals(m.paramDescs[0]) && sz > 250 && sz < 900) {
                            visMethodName = m.name;
                            ctx.addMethod("DialogsActivity", "updateStoriesVisibility", m.name);
                        } else {
                            int p0 = m.isStatic ? 1 : 0;
                            if (m.isStatic && (m.paramDescs.length < 1 || !daDesc.equals(m.paramDescs[0]))) continue;
                            if ("V".equals(m.returnDesc) && m.paramDescs.length == p0 + 2
                                    && m.paramDescs[p0].startsWith("L") && "F".equals(m.paramDescs[p0 + 1])
                                    && sz > 30 && sz < 150) {
                                ctx.addMethod("DialogsActivity", "setStoriesOvercroll", m.name);
                            } else if ("Z".equals(m.returnDesc) && m.paramDescs.length == p0 + 1
                                    && m.paramDescs[p0].startsWith("L")
                                    && sz > 50 && sz < 200) {
                                autoMethodName = m.name;
                                autoParamDescs = m.paramDescs;
                                ctx.addMethod("DialogsActivity", "checkAutoscrollToStories", m.name);
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
                            ctx.addMethod("DialogsActivity", "getStoriesController", getSc);
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
                                ctx.addMethod("DialogsActivity", "getMaxScrollYOffsetWithoutSearch", maxScrollNoSearch);
                                ctx.addField("DialogsActivity", "hasStories", hasStFld);
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
                                ctx.addField("DialogsActivity", "hasStories", hasStFld);
                            }
                        }
                        if (hasStFld == null) {
                            for (DexScanner.FieldInfo df : scanner.getFields(daDesc)) {
                                if (!df.isStatic && "Z".equals(df.typeDesc) && "hasStories".equals(df.name)) {
                                    ctx.addField("DialogsActivity", "hasStories", "hasStories");
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
                        String fabField = flds.contains("floatingButton3")
                                ? "floatingButton3"
                                : (flds.get(0).equals(fabStoriesField) ? flds.get(1) : flds.get(0));
                        ctx.addField("DialogsActivity", "floatingButton3", fabField);
                        ctx.addField("DialogsActivity", "floatingButtonStories", fabStoriesField);
                        ctx.addMethod("DialogsActivity", "updateFloatingButtonVisibility", m.name);
                        ctx.addMethod("FragmentFloatingButton", "setButtonVisible", setVisName);
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
            ctx.addClass(ClassNames.PROFILE_STORIES_VIEW, psvDesc);
            for (DexScanner.MethodInfo m : psvMethods) {
                if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                if (m.paramDescs.length == 2 && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])
                        && m.getInsnsSize() > 300) {
                    ctx.addMethod("ProfileStoriesView", "updateStories", m.name);
                } else if (m.paramDescs.length == 1 && m.paramDescs[0].contains("PeerStories")) {
                    ctx.addMethod("ProfileStoriesView", "setStories", m.name);
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
        if (suDesc == null) {
            // Fork-extended drawAvatarWithStory (e.g. iMe 8-arg with boolean + ProfileMark + float)
            suDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                for (DexScanner.MethodInfo m : methods) {
                    if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length > 5
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
        }
        if (suDesc != null) {
            ctx.addClass(ClassNames.STORIES_UTILITIES, suDesc);
            String paramsDesc = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(suDesc)) {
                if (m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 5
                        && "J".equals(m.paramDescs[0])
                        && "Landroid/graphics/Canvas;".equals(m.paramDescs[1])
                        && m.paramDescs[2].startsWith("L")
                        && "Z".equals(m.paramDescs[3])
                        && m.paramDescs[4].startsWith("L")
                        && m.getInsnsSize() > 200) {
                    ctx.addMethod("StoriesUtilities", "drawAvatarWithStory", m.name);
                    paramsDesc = m.paramDescs[4];
                    ctx.addClass(ClassNames.AVATAR_STORY_PARAMS, paramsDesc);
                    List<String> zOrder = new ArrayList<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(suDesc, m.name, m.paramDescs, paramsDesc)) {
                        if ("Z".equals(rf.typeDesc) && !zOrder.contains(rf.name)) {
                            zOrder.add(rf.name);
                        }
                    }
                    String isDsc = zOrder.contains("isDialogStoriesCell")
                            ? "isDialogStoriesCell"
                            : (zOrder.size() >= 2 ? zOrder.get(1) : null);
                    ctx.addField("AvatarStoryParams", "isDialogStoriesCell", isDsc);
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
                            ctx.addMethod("AvatarStoryParams", "checkOnTouchEvent", tm.name);
                            break;
                        }
                    }
                }
            }
        }

        resolveProfileStoriesRows(ctx, scanner, smlDesc);
    }

    static void resolveProfileStoriesRows(MappingContext ctx, DexScanner scanner, String smlDesc) {
        String profileDesc = DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY);
        String profCreateView = MappingContext.hasMethodNamed(scanner, profileDesc, "createView")
                ? "createView"
                : scanner.findSingleMethod(profileDesc, false, "Landroid/view/View;", "Landroid/content/Context;");
        if (profCreateView != null) {
            ctx.addMethod("ProfileActivity", "createView", profCreateView);
        }
        String profSwipeBack = scanner.findSingleMethod(profileDesc, false, "Z", "Landroid/view/MotionEvent;");
        if (profSwipeBack == null) profSwipeBack = ctx.methodMap.get("BaseFragment#isSwipeBackEnabled");
        if (profSwipeBack != null) {
            ctx.addMethod("ProfileActivity", "isSwipeBackEnabled", profSwipeBack);
        }

        String updRows = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0
                    && m.readsFieldNamed("common_chats_count")
                    && m.readsFieldNamed("stories_pinned_available")) {
                updRows = m.name;
                ctx.addMethod("ProfileActivity", "updateRowsIds", updRows);
                break;
            }
        }

        String userInfoField = scanner.findSingleField(profileDesc, false, "Lorg/telegram/tgnet/TLRPC$UserFull;");
        String chatInfoField = scanner.findSingleField(profileDesc, false, "Lorg/telegram/tgnet/TLRPC$ChatFull;");
        ctx.addField("ProfileActivity", "userInfo", userInfoField);
        ctx.addField("ProfileActivity", "chatInfo", chatInfoField);

        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if ("V".equals(m.returnDesc) && m.usesString("HideStoriesFromAlbum") && m.usesString("ArchiveStories")) {
                ctx.addMethod("ProfileActivity", "checkStoriesButtonText", m.name);
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(profileDesc, m.name, m.paramDescs, profileDesc)) {
                    if (rf.typeDesc.startsWith("[L")) {
                        ctx.addField("ProfileActivity", "bottomButton", rf.name);
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
        if (smRow == null && profSwipeBack != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(profileDesc, profSwipeBack, new String[]{"Landroid/view/MotionEvent;"}, profileDesc)) {
                if ("I".equals(rf.typeDesc)) {
                    smRow = rf.name;
                    break;
                }
            }
        }
        ctx.addField("ProfileActivity", "sharedMediaRow", smRow);

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
                        ctx.addField("ProfileActivity", "bottomPaddingRow", f.name);
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
                                ctx.addField("ProfileActivity", "myProfile", rf.name);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

}
