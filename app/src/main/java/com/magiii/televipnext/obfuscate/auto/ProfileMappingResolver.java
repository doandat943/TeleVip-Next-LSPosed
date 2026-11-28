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
 * Resolves ProfileActivity, SimpleTextView, Profile ID/DC row, and entity photo dc_id mappings.
 */
final class ProfileMappingResolver {

    private ProfileMappingResolver() {}

    static void resolveProfileActivity(MappingContext ctx, DexScanner scanner, String msgCtrlDesc) {
        DexScanner.ClassPredicate simpleTvPred = (desc, fields, methods) -> {
            if (!"Landroid/view/View;".equals(scanner.getSuperClass(desc))) return false;
            boolean hasTextPaintField = false;
            for (DexScanner.FieldInfo f : fields) {
                if (!f.isStatic && "Landroid/text/TextPaint;".equals(f.typeDesc)) {
                    hasTextPaintField = true;
                    break;
                }
            }
            if (!hasTextPaintField) return false;
            boolean hasGetText = false, hasSetText1 = false, hasSetText2 = false, hasVoidInt = false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic) continue;
                if ("Ljava/lang/CharSequence;".equals(m.returnDesc) && m.paramDescs.length == 0) hasGetText = true;
                else if ("Z".equals(m.returnDesc) && m.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0])) hasSetText1 = true;
                else if ("Z".equals(m.returnDesc) && m.paramDescs.length == 2 && "Ljava/lang/CharSequence;".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1])) hasSetText2 = true;
                else if ("V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) hasVoidInt = true;
            }
            return hasGetText && hasSetText1 && hasSetText2 && hasVoidInt;
        };
        String simpleTextViewDesc = scanner.findClassByPredicate("Lorg/telegram/ui/ActionBar/", simpleTvPred);
        if (simpleTextViewDesc == null) {
            simpleTextViewDesc = scanner.findClassByPredicate(null, simpleTvPred);
        }

        if (simpleTextViewDesc != null) {
            ctx.addClass("org.telegram.ui.ActionBar.SimpleTextView", simpleTextViewDesc);
            String getTextM = null;
            String getPaintM = null;
            String setMaxLinesM = null;
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
                if ("setMaxLines".equals(m.name) && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])) {
                    setMaxLinesM = m.name;
                }
            }
            if (getTextM == null) {
                getTextM = scanner.findSingleMethod(simpleTextViewDesc, false, "Ljava/lang/CharSequence;");
            }
            if (setMaxLinesM == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(simpleTextViewDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0])
                            && m.getInsnsSize() >= 10 && m.getInsnsSize() <= 45) {
                        for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(
                                simpleTextViewDesc, m.name, m.paramDescs, simpleTextViewDesc)) {
                            if ("I".equals(wf.typeDesc)) {
                                setMaxLinesM = m.name;
                                break;
                            }
                        }
                        if (setMaxLinesM != null) break;
                    }
                }
            }
            ctx.addMethod("SimpleTextView", "getText", getTextM);
            ctx.addMethod("SimpleTextView", "setText",
                    scanner.findSingleMethod(simpleTextViewDesc, false, "Z", "Ljava/lang/CharSequence;"));
            ctx.addMethod("SimpleTextView", "setTextForce",
                    scanner.findSingleMethod(simpleTextViewDesc, false, "Z", "Ljava/lang/CharSequence;", "Z"));
            if (setMaxLinesM != null) {
                ctx.addMethod("SimpleTextView", "setMaxLines", setMaxLinesM);
            }
            if (getPaintM != null) {
                ctx.addMethod("SimpleTextView", "getTextPaint", getPaintM);
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
        if (bestUpdateData == null && msgCtrlDesc != null) {
            String getUserM = ctx.methodMap.get("MessagesController#getUser");
            String getChatM = ctx.methodMap.get("MessagesController#getChat");
            if (getUserM != null && getChatM != null) {
                for (DexScanner.MethodInfo m : profileMethods) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 1
                            || !"Z".equals(m.paramDescs[0]) || m.getInsnsSize() < 200) {
                        continue;
                    }
                    String uFld = scanner.findReadFieldBeforeCall(profileDesc, m.name, m.paramDescs, "J", msgCtrlDesc, getUserM);
                    String cFld = scanner.findReadFieldBeforeCall(profileDesc, m.name, m.paramDescs, "J", msgCtrlDesc, getChatM);
                    if (uFld != null && cFld != null) {
                        if (bestUpdateData == null || m.getInsnsSize() > bestUpdateData.getInsnsSize()) {
                            bestUpdateData = m;
                        }
                    }
                }
            }
        }
        if (bestUpdateData != null) {
            ctx.addMethod("ProfileActivity", "updateProfileData", bestUpdateData.name);
            if (msgCtrlDesc != null) {
                String getUserM = ctx.methodMap.get("MessagesController#getUser");
                if (getUserM != null) {
                    ctx.addField("ProfileActivity", "userId",
                            scanner.findReadFieldBeforeCall(profileDesc, bestUpdateData.name, new String[]{"Z"}, "J", msgCtrlDesc, getUserM));
                }
                String getChatM = ctx.methodMap.get("MessagesController#getChat");
                if (getChatM != null) {
                    ctx.addField("ProfileActivity", "chatId",
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
                    ctx.addField("ProfileActivity", "onlineTextView", onlineField);
                    ctx.addMethod("ProfileActivity", "updateOnlineTextView", updateOnlineMethod);
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
                        ctx.addField("ProfileActivity", "nameTextView", nameField);
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
                for (DexScanner.MethodInfo m : profileMethods) {
                    if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                    boolean okSig = m.paramDescs.length == 0
                            || (m.paramDescs.length == 1 && "Z".equals(m.paramDescs[0]));
                    if (!okSig) continue;
                    int sz = m.getInsnsSize();
                    if (sz <= 0 || sz >= 150) continue;

                    String matchedArr = null;
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                            profileDesc, m.name, m.paramDescs, profileDesc)) {
                        if (arrDesc.equals(rf.typeDesc)) {
                            matchedArr = rf.name;
                            break;
                        }
                    }
                    if (matchedArr == null) continue;
                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(profileDesc, m.name, m.paramDescs, null)) {
                        if ("Z".equals(mr.returnDesc) && mr.paramDescs.length == 2
                                && "Ljava/lang/CharSequence;".equals(mr.paramDescs[0]) && "Z".equals(mr.paramDescs[1])
                                && scanner.isSubclassOf(simpleTextViewDesc, mr.ownerDesc)) {
                            onlineField = matchedArr;
                            updateOnlineMethod = m.name;
                            break;
                        }
                    }
                    if (updateOnlineMethod != null) {
                        ctx.addMethod("ProfileActivity", "updateOnlineTextView", updateOnlineMethod);
                        break;
                    }
                }
                if (arrNames.size() == 2) {
                    if (arrNames.contains("nameTextView") && arrNames.contains("onlineTextView")) {
                        ctx.addField("ProfileActivity", "nameTextView", "nameTextView");
                        ctx.addField("ProfileActivity", "onlineTextView", "onlineTextView");
                    } else if (onlineField != null) {
                        ctx.addField("ProfileActivity", "onlineTextView", onlineField);
                        ctx.addField("ProfileActivity", "nameTextView",
                                onlineField.equals(arrNames.get(0)) ? arrNames.get(1) : arrNames.get(0));
                    } else {
                        ctx.addField("ProfileActivity", "nameTextView", arrNames.get(0));
                        ctx.addField("ProfileActivity", "onlineTextView", arrNames.get(1));
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
                ctx.addMethod("ProfileActivity", "needLayout", needLayoutMethod);
            }
        }
    }

    /**
     * Resolves ProfileActivity row-management fields, ProfileActivity$ListAdapter,
     * TextDetailCell, and User/Chat photo dc_id fields for the Profile ID & DC card row.
     */
    static void resolveProfileIdDc(MappingContext ctx, DexScanner scanner) {
        String profileDesc = DexScanner.javaToDesc(ClassNames.PROFILE_ACTIVITY);

        // 1. Resolve ProfileActivity#updateRowsIds (reuse existing if found, else fallback to max unique int field writes)
        String updRows = ctx.methodMap.get("ProfileActivity#updateRowsIds");
        if (updRows == null) {
            int bestWrites = 59;
            for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0 || m.getInsnsSize() <= 300) {
                    continue;
                }
                Set<String> intFields = new HashSet<>();
                for (DexScanner.FieldInfo fi : scanner.getWrittenFieldsInMethod(profileDesc, m.name, new String[0], profileDesc)) {
                    if ("I".equals(fi.typeDesc)) {
                        intFields.add(fi.name);
                    }
                }
                if (intFields.size() > bestWrites) {
                    bestWrites = intFields.size();
                    updRows = m.name;
                }
            }
            if (updRows != null) {
                ctx.addMethod("ProfileActivity", "updateRowsIds", updRows);
            }
        }
        if (updRows == null) return;

        // 2. Resolve ProfileActivity#rowCount (most frequently incremented int field in updateRowsIds) & rowFields
        Map<String, Integer> writeCounts = new HashMap<>();
        List<DexScanner.FieldInfo> writtenInUpd = scanner.getWrittenFieldsInMethod(profileDesc, updRows, new String[0], profileDesc);
        for (DexScanner.FieldInfo fi : writtenInUpd) {
            if ("I".equals(fi.typeDesc)) {
                writeCounts.put(fi.name, writeCounts.getOrDefault(fi.name, 0) + 1);
            }
        }
        String rowCountField = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> e : writeCounts.entrySet()) {
            if (e.getValue() > maxCount) {
                maxCount = e.getValue();
                rowCountField = e.getKey();
            }
        }
        if (rowCountField == null || maxCount < 20) return;
        ctx.addField("ProfileActivity", "rowCount", rowCountField);

        Set<String> rowFieldSet = new LinkedHashSet<>(writeCounts.keySet());
        rowFieldSet.remove(rowCountField);
        if (!rowFieldSet.isEmpty()) {
            ctx.addField("ProfileActivity", "rowFields", String.join(",", rowFieldSet));
        }

        // 3. Resolve ProfileActivity$ListAdapter and its holding field on ProfileActivity
        String laDesc = null;
        String laFieldOnProfile = null;
        for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
            if (f.isStatic || f.typeDesc.startsWith("[") || f.typeDesc.length() <= 2) continue;
            if (hasProfileListAdapterShape(scanner, f.typeDesc, profileDesc, rowCountField)) {
                laDesc = f.typeDesc;
                laFieldOnProfile = f.name;
                break;
            }
        }
        if (laDesc == null) {
            final String fRc = rowCountField;
            laDesc = scanner.findClassByPredicate("L", (desc, fields, methods) -> {
                boolean hasPa = false;
                for (DexScanner.FieldInfo lf : fields) {
                    if (!lf.isStatic && profileDesc.equals(lf.typeDesc)) {
                        hasPa = true;
                        break;
                    }
                }
                return hasPa && hasProfileListAdapterShape(scanner, desc, profileDesc, fRc);
            });
            if (laDesc != null) {
                for (DexScanner.FieldInfo f : scanner.getFields(profileDesc)) {
                    if (!f.isStatic && laDesc.equals(f.typeDesc)) {
                        laFieldOnProfile = f.name;
                        break;
                    }
                }
            }
        }
        if (laDesc == null) return;

        ctx.addClass(ClassNames.PROFILE_LIST_ADAPTER, laDesc);
        if (laFieldOnProfile != null) {
            ctx.addField("ProfileActivity", "listAdapter", laFieldOnProfile);
        }

        // Resolve outer ProfileActivity reference field(s) on ListAdapter
        String outerField = scanner.findSingleField(laDesc, false, profileDesc);
        if (outerField == null) {
            for (DexScanner.FieldInfo lf : scanner.getFields(laDesc)) {
                if (!lf.isStatic && "Ljava/lang/Object;".equals(lf.typeDesc)) {
                    outerField = outerField == null ? lf.name : outerField + "," + lf.name;
                }
            }
        }
        if (outerField != null) {
            ctx.addField("ProfileActivity$ListAdapter", "this$0", outerField);
        }

        // 4. Resolve ListAdapter methods + TextDetailCell
        String mGetType = null;
        String mIsEnabled = null;
        DexScanner.MethodInfo bindCandidate = null;
        String tdcDesc = null;
        String tdcSetTV = null;

        for (DexScanner.MethodInfo m : scanner.getMethods(laDesc)) {
            if (m.isStatic) continue;
            if ("I".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0]) && m.getInsnsSize() > 50) {
                if (mGetType == null || "getItemViewType".equals(m.name)) {
                    mGetType = m.name;
                }
            } else if ("Z".equals(m.returnDesc) && m.paramDescs.length == 1
                    && m.paramDescs[0].startsWith("L") && m.getInsnsSize() > 20) {
                if (mIsEnabled == null || "isEnabled".equals(m.name)) {
                    mIsEnabled = m.name;
                }
            } else if ("V".equals(m.returnDesc) && m.paramDescs.length == 2
                    && m.paramDescs[0].startsWith("L") && "I".equals(m.paramDescs[1]) && m.getInsnsSize() > 300) {
                // Check if this method calls TextDetailCell#setTextAndValue
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(laDesc, m.name, m.paramDescs, null)) {
                    if ("V".equals(mr.returnDesc) && mr.paramDescs.length == 3
                            && "Ljava/lang/CharSequence;".equals(mr.paramDescs[0])
                            && "Ljava/lang/CharSequence;".equals(mr.paramDescs[1])
                            && "Z".equals(mr.paramDescs[2])
                            && isTextDetailCell(scanner, mr.ownerDesc)) {
                        tdcDesc = mr.ownerDesc;
                        tdcSetTV = mr.name;
                        bindCandidate = m;
                        break;
                    }
                }
                if (bindCandidate == null) {
                    bindCandidate = m;
                }
            }
        }

        if (mGetType != null) ctx.addMethod("ProfileActivity$ListAdapter", "getItemViewType", mGetType);
        if (mIsEnabled != null) ctx.addMethod("ProfileActivity$ListAdapter", "isEnabled", mIsEnabled);
        if (bindCandidate != null) ctx.addMethod("ProfileActivity$ListAdapter", "onBindViewHolder", bindCandidate.name);

        if (tdcDesc != null && tdcSetTV != null) {
            ctx.addClass(ClassNames.TEXT_DETAIL_CELL, tdcDesc);
            ctx.addMethod("TextDetailCell", "setTextAndValue", tdcSetTV);
            String imageViewField = scanner.findSingleField(tdcDesc, false, "Landroid/widget/ImageView;");
            if (imageViewField != null) {
                ctx.addField("TextDetailCell", "imageView", imageViewField);
            }
            String mSetImg1 = null;
            String mSetImg2 = null;
            String mSetImgClick = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(tdcDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc)) continue;
                if (m.paramDescs.length == 1 && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[0])) {
                    if (mSetImg1 == null || "setImage".equals(m.name)) mSetImg1 = m.name;
                } else if (m.paramDescs.length == 2 && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[0])
                        && ("Ljava/lang/CharSequence;".equals(m.paramDescs[1]) || "Ljava/lang/String;".equals(m.paramDescs[1]))) {
                    if (mSetImg2 == null || "setImage".equals(m.name)) mSetImg2 = m.name;
                } else if (m.paramDescs.length == 1 && "Landroid/view/View$OnClickListener;".equals(m.paramDescs[0])
                        && !"setOnClickListener".equals(m.name)) {
                    if (mSetImgClick == null || "setImageClickListener".equals(m.name)) mSetImgClick = m.name;
                }
            }
            if (mSetImg1 != null) ctx.addMethod("TextDetailCell", "setImage1", mSetImg1);
            if (mSetImg2 != null) ctx.addMethod("TextDetailCell", "setImage2", mSetImg2);
            if (mSetImgClick != null) ctx.addMethod("TextDetailCell", "setImageClickListener", mSetImgClick);
        }

        // 5. Resolve photo + dc_id on User / UserProfilePhoto and Chat / ChatPhoto
        resolveEntityPhotoDc(ctx, scanner, ctx.resolvedDescOrRaw(ClassNames.TLRPC_USER), "TLRPC$User",
                ClassNames.TLRPC_USER_PROFILE_PHOTO, "TLRPC$UserProfilePhoto");
        String chatDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_CHAT);
        resolveEntityPhotoDc(ctx, scanner, chatDesc, "TLRPC$Chat",
                ClassNames.TLRPC_CHAT_PHOTO, "TLRPC$ChatPhoto");
        resolveChatTitleAndDate(ctx, scanner, chatDesc);

        // 6. Resolve Nekogram's native ViewSwitcher ("ID: ..., DC: ...") if present
        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 3
                    && "Z".equals(m.paramDescs[0]) && "Z".equals(m.paramDescs[1]) && "Z".equals(m.paramDescs[2])
                    && m.usesString(", DC: ") && m.usesString("ID: ")) {
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(profileDesc, m.name, m.paramDescs, null)) {
                    if ("V".equals(mr.returnDesc) && mr.paramDescs.length == 2
                            && "Ljava/lang/String;".equals(mr.paramDescs[0])
                            && "Z".equals(mr.paramDescs[1])
                            && "Landroid/widget/ViewSwitcher;".equals(scanner.getSuperClass(mr.ownerDesc))) {
                        ctx.addClass(ClassNames.NEKO_PROFILE_ID_VIEW, mr.ownerDesc);
                        ctx.addMethod("NekoProfileIdView", "setText", mr.name);
                        break;
                    }
                }
                break;
            }
        }

        // 7. Resolve partial native ID/DC targets on other clients (iMe, Mercurygram, Momogram, Nagram, Telegraph)
        if (MappingContext.hasFieldNamed(scanner, profileDesc, "dialogIdRow")) {
            ctx.addField("ProfileActivity", "nativeIdRow", "dialogIdRow");
        } else if (MappingContext.hasFieldNamed(scanner, profileDesc, "idRow")) {
            ctx.addField("ProfileActivity", "nativeIdRow", "idRow");
        }

        resolveNagramOrTelegraphInlineId(ctx, scanner, profileDesc);
    }

    static void resolveNagramOrTelegraphInlineId(MappingContext ctx, DexScanner scanner, String profileDesc) {
        for (DexScanner.MethodInfo m : scanner.getMethods(profileDesc)) {
            if (m.isStatic || !"V".equals(m.returnDesc)) continue;
            if (m.paramDescs.length == 1 && "Z".equals(m.paramDescs[0])
                    && m.usesString(", DC: ") && m.usesString("ID: ")) {
                ctx.addMethod("ProfileActivity", "nagramIdUpdate", m.name);
                Set<String> viewFields = new LinkedHashSet<>();
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        profileDesc, m.name, m.paramDescs, profileDesc)) {
                    String elemDesc = rf.typeDesc.startsWith("[") ? rf.typeDesc.substring(1) : rf.typeDesc;
                    if (elemDesc.startsWith("L") && !elemDesc.startsWith("Landroid/")
                            && "Landroid/view/View;".equals(scanner.getSuperClass(elemDesc))
                            && hasCharSeqGetterAndSetter(scanner, elemDesc)) {
                        viewFields.add(rf.name);
                    }
                }
                if (!viewFields.isEmpty()) {
                    ctx.addField("ProfileActivity", "nagramIdViews", String.join(",", viewFields));
                }
            } else if (m.paramDescs.length == 0 && m.usesString("DC: ") && !m.usesString(", DC: ")) {
                String dcViewField = null;
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(
                        profileDesc, m.name, m.paramDescs, profileDesc)) {
                    if (!rf.typeDesc.startsWith("L") || rf.typeDesc.startsWith("Ljava/")) continue;
                    if ("Lorg/telegram/ui/ActionBar/SimpleTextView;".equals(rf.typeDesc)
                            || ("Landroid/view/View;".equals(scanner.getSuperClass(rf.typeDesc))
                            && hasCharSeqGetterAndSetter(scanner, rf.typeDesc))) {
                        dcViewField = rf.name;
                        break;
                    }
                }
                if (dcViewField != null) {
                    ctx.addMethod("ProfileActivity", "telegraphDcUpdate", m.name);
                    ctx.addField("ProfileActivity", "telegraphDcView", dcViewField);
                }
            }
        }
    }

    static boolean hasCharSeqGetterAndSetter(DexScanner scanner, String clsDesc) {
        boolean hasGetter = false;
        boolean hasSetter = false;
        for (DexScanner.MethodInfo vm : scanner.getMethods(clsDesc)) {
            if (vm.isStatic) continue;
            if (!hasGetter && "Ljava/lang/CharSequence;".equals(vm.returnDesc) && vm.paramDescs.length == 0) {
                hasGetter = true;
            }
            if (!hasSetter && ("V".equals(vm.returnDesc) || "Z".equals(vm.returnDesc))
                    && vm.paramDescs.length == 1 && "Ljava/lang/CharSequence;".equals(vm.paramDescs[0])) {
                hasSetter = true;
            }
            if (hasGetter && hasSetter) return true;
        }
        return false;
    }

    static void resolveEntityPhotoDc(MappingContext ctx, DexScanner scanner, String entityDesc, String entityKey,
                                      String photoClassName, String photoKey) {
        String stdPhotoDesc = DexScanner.javaToDesc(photoClassName);
        String photoField = null;
        String resolvedPhotoDesc = null;

        if (entityDesc != null) {
            for (DexScanner.FieldInfo f : scanner.getFields(entityDesc)) {
                if (f.isStatic) continue;
                if ("photo".equals(f.name) || stdPhotoDesc.equals(f.typeDesc)) {
                    photoField = f.name;
                    resolvedPhotoDesc = f.typeDesc;
                    break;
                }
            }
            // R8 fallback: field whose struct contains two reference FileLocation-like fields + BitmapDrawable (UserProfilePhoto)
            if (photoField == null) {
                for (DexScanner.FieldInfo f : scanner.getFields(entityDesc)) {
                    if (f.isStatic || !f.typeDesc.startsWith("L")) continue;
                    boolean hasBitmapDrawable = false;
                    int byteArr = 0, ints = 0;
                    for (DexScanner.FieldInfo sf : scanner.getFields(f.typeDesc)) {
                        if ("Landroid/graphics/drawable/BitmapDrawable;".equals(sf.typeDesc)) hasBitmapDrawable = true;
                        else if ("[B".equals(sf.typeDesc)) byteArr++;
                        else if ("I".equals(sf.typeDesc)) ints++;
                    }
                    if (hasBitmapDrawable && byteArr >= 1 && ints == 2) {
                        photoField = f.name;
                        resolvedPhotoDesc = f.typeDesc;
                        break;
                    }
                }
            }
        }
        if (resolvedPhotoDesc == null && MappingContext.hasClass(scanner, stdPhotoDesc)) {
            resolvedPhotoDesc = stdPhotoDesc;
        }
        if (photoField != null) {
            ctx.addField(entityKey, "photo", photoField);
        }
        if (resolvedPhotoDesc != null) {
            ctx.addClass(photoClassName, resolvedPhotoDesc);
            String dcField = resolveDcIdField(scanner, resolvedPhotoDesc);
            if (dcField != null) {
                ctx.addField(photoKey, "dc_id", dcField);
            }
        }
    }

    static String resolveDcIdField(DexScanner scanner, String photoDesc) {
        if (MappingContext.hasFieldNamed(scanner, photoDesc, "dc_id")) {
            return "dc_id";
        }
        // In UserProfilePhoto/ChatPhoto, subclass readParams writes `flags` then `dc_id` (both I).
        String fPhotoDesc = photoDesc;
        String found = null;
        for (DexScanner.FieldInfo f : scanner.getFields(photoDesc)) {
            if (!f.isStatic && "I".equals(f.typeDesc) && !"flags".equals(f.name)) {
                found = f.name;
            }
        }
        final String[] subDc = {null};
        scanner.findClassByPredicate("L", (desc, fields, methods) -> {
            if (!fPhotoDesc.equals(scanner.getSuperClass(desc))) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length < 2) continue;
                List<DexScanner.FieldInfo> writes = scanner.getWrittenFieldsInMethod(desc, m.name, m.paramDescs, fPhotoDesc);
                List<String> intWrites = new ArrayList<>();
                for (DexScanner.FieldInfo w : writes) {
                    if ("I".equals(w.typeDesc)) intWrites.add(w.name);
                }
                if (intWrites.size() >= 2 && !intWrites.get(0).equals(intWrites.get(intWrites.size() - 1))) {
                    subDc[0] = intWrites.get(intWrites.size() - 1);
                    return true;
                }
            }
            return false;
        });
        return subDc[0] != null ? subDc[0] : found;
    }

    static void resolveChatTitleAndDate(MappingContext ctx, DexScanner scanner, String chatDesc) {
        if (chatDesc == null) return;
        String titleField = MappingContext.hasFieldNamed(scanner, chatDesc, "title") ? "title" : null;
        String dateField = MappingContext.hasFieldNamed(scanner, chatDesc, "date") ? "date" : null;
        String creatorField = MappingContext.hasFieldNamed(scanner, chatDesc, "creator") ? "creator" : null;
        String leftField = MappingContext.hasFieldNamed(scanner, chatDesc, "left") ? "left" : null;

        if (titleField == null || dateField == null || creatorField == null || leftField == null) {
            final String photoField = ctx.fieldMap.get("TLRPC$Chat#photo");
            final String chatPhotoDesc = ctx.resolvedDescOrRaw(ClassNames.TLRPC_CHAT_PHOTO);
            final int[] bestSize = {0};
            final String[] bestTitle = {null};
            final String[] bestDate = {null};
            final String[] bestCreator = {null};
            final String[] bestLeft = {null};

            scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (!chatDesc.equals(scanner.getSuperClass(desc))) return false;
                for (DexScanner.MethodInfo m : methods) {
                    if (m.isStatic || !"V".equals(m.returnDesc)
                            || m.paramDescs.length != 2 || !"Z".equals(m.paramDescs[1])) {
                        continue;
                    }
                    List<DexScanner.FieldInfo> writes = scanner.getWrittenFieldsInMethod(
                            desc, m.name, m.paramDescs, chatDesc);
                    if (writes.size() < 20 || writes.size() <= bestSize[0]) continue;

                    String firstStr = null;
                    String dateAfterPhoto = null;
                    String candCreator = null;
                    String candLeft = null;
                    boolean seenPhoto = false;
                    for (DexScanner.FieldInfo wf : writes) {
                        if ("Z".equals(wf.typeDesc)) {
                            if (candCreator == null) {
                                candCreator = wf.name;
                            } else if (candLeft == null) {
                                candLeft = wf.name;
                            }
                        } else if (firstStr == null && "Ljava/lang/String;".equals(wf.typeDesc)) {
                            firstStr = wf.name;
                        }
                        if ((photoField != null && photoField.equals(wf.name))
                                || (chatPhotoDesc != null && chatPhotoDesc.equals(wf.typeDesc))) {
                            seenPhoto = true;
                        } else if (seenPhoto && dateAfterPhoto == null && "I".equals(wf.typeDesc)) {
                            dateAfterPhoto = wf.name;
                        }
                    }
                    if (firstStr != null && dateAfterPhoto != null) {
                        bestSize[0] = writes.size();
                        bestTitle[0] = firstStr;
                        bestDate[0] = dateAfterPhoto;
                        bestCreator[0] = candCreator;
                        bestLeft[0] = candLeft;
                    }
                }
                return false;
            });

            if (titleField == null) titleField = bestTitle[0];
            if (dateField == null) dateField = bestDate[0];
            if (creatorField == null) creatorField = bestCreator[0];
            if (leftField == null) leftField = bestLeft[0];
        }

        if (titleField != null) {
            ctx.addField("TLRPC$Chat", "title", titleField);
        }
        if (dateField != null) {
            ctx.addField("TLRPC$Chat", "date", dateField);
        }
        if (creatorField != null) {
            ctx.addField("TLRPC$Chat", "creator", creatorField);
        }
        if (leftField != null) {
            ctx.addField("TLRPC$Chat", "left", leftField);
        }
    }

    static boolean hasProfileListAdapterShape(DexScanner scanner, String clsDesc, String paDesc, String rowCountFld) {
        boolean readsRowCount = false;
        boolean hasGetType = false;
        boolean hasBind = false;
        for (DexScanner.MethodInfo m : scanner.getMethods(clsDesc)) {
            if (m.isStatic) continue;
            if (!readsRowCount && "I".equals(m.returnDesc) && m.paramDescs.length == 0 && m.getInsnsSize() <= 100) {
                for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(clsDesc, m.name, new String[0], paDesc)) {
                    if (rowCountFld.equals(rf.name)) {
                        readsRowCount = true;
                        break;
                    }
                }
                if (!readsRowCount) {
                    for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(clsDesc, m.name, new String[0], paDesc)) {
                        if ("I".equals(mr.returnDesc) && mr.paramDescs.length == 1 && paDesc.equals(mr.paramDescs[0])) {
                            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(paDesc, mr.name, mr.paramDescs, paDesc)) {
                                if (rowCountFld.equals(rf.name)) {
                                    readsRowCount = true;
                                    break;
                                }
                            }
                        }
                        if (readsRowCount) break;
                    }
                }
            } else if ("I".equals(m.returnDesc) && m.paramDescs.length == 1 && "I".equals(m.paramDescs[0]) && m.getInsnsSize() > 50) {
                hasGetType = true;
            } else if ("V".equals(m.returnDesc) && m.paramDescs.length == 2 && m.paramDescs[0].startsWith("L") && "I".equals(m.paramDescs[1]) && m.getInsnsSize() > 300) {
                hasBind = true;
            }
        }
        return readsRowCount && hasGetType && hasBind;
    }

    static boolean isTextDetailCell(DexScanner scanner, String desc) {
        if ("Lorg/telegram/ui/Cells/TextDetailCell;".equals(desc)) return true;
        boolean hasImgView = false;
        for (DexScanner.FieldInfo fi : scanner.getFields(desc)) {
            if ("Landroid/widget/ImageView;".equals(fi.typeDesc)) {
                hasImgView = true;
                break;
            }
        }
        if (!hasImgView) return false;
        for (DexScanner.MethodInfo m : scanner.getMethods(desc)) {
            if (m.isStatic || !"V".equals(m.returnDesc)) continue;
            if ((m.paramDescs.length == 1 && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[0]))
                    || (m.paramDescs.length == 2 && "Landroid/graphics/drawable/Drawable;".equals(m.paramDescs[0])
                        && ("Ljava/lang/CharSequence;".equals(m.paramDescs[1])
                            || "Ljava/lang/String;".equals(m.paramDescs[1])))) {
                return true;
            }
        }
        return false;
    }

}
