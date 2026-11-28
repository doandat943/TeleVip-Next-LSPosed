package com.magiii.televipnext.obfuscate.auto;


import java.util.HashMap;

/**
 * Resolves obfuscated client-native settings classes for Nekogram, ExteraGram, Momogram, OctoGram, and Nicegram.
 */
final class ClientNativeSettingsResolver {

    private ClientNativeSettingsResolver() {}

    static void resolveClientNativeSettings(MappingContext ctx, DexScanner scanner, String baseFragmentDesc) {
        if (baseFragmentDesc == null) return;

        // 1. Octogram (guarded by unobfuscated OctoConfig)
        if (MappingContext.hasClass(scanner, "Lit/octogram/android/unsorted/OctoConfig;")) {
            final String[] entryHolder = new String[1];
            String prefFragDesc = scanner.findClassByPredicate(null, (desc, fields, methods) -> {
                if (!baseFragmentDesc.equals(scanner.getSuperClass(desc))) return false;
                String candEntry = null;
                boolean has1Arg = false;
                for (DexScanner.MethodInfo m : methods) {
                    if (!"<init>".equals(m.name) || m.isStatic) continue;
                    if (m.paramDescs.length == 2 && "Ljava/lang/String;".equals(m.paramDescs[1])) {
                        String p0 = m.paramDescs[0];
                        if (p0.startsWith("L") && !p0.startsWith("Ljava/") && !p0.startsWith("Landroid/")) {
                            candEntry = p0;
                        }
                    }
                }
                if (candEntry == null) return false;
                for (DexScanner.MethodInfo m : methods) {
                    if ("<init>".equals(m.name) && !m.isStatic
                            && m.paramDescs.length == 1 && candEntry.equals(m.paramDescs[0])) {
                        has1Arg = true;
                        break;
                    }
                }
                if (!has1Arg) return false;
                boolean hasEntryField = false;
                boolean hasStringField = false;
                for (DexScanner.FieldInfo f : fields) {
                    if (!f.isStatic) {
                        if (candEntry.equals(f.typeDesc)) hasEntryField = true;
                        else if ("Ljava/lang/String;".equals(f.typeDesc)) hasStringField = true;
                    }
                }
                if (!hasEntryField || !hasStringField) return false;
                for (DexScanner.MethodInfo em : scanner.getMethods(candEntry)) {
                    if (!em.isStatic && em.paramDescs.length == 2
                            && desc.equals(em.paramDescs[0])
                            && "Landroid/content/Context;".equals(em.paramDescs[1])) {
                        entryHolder[0] = candEntry;
                        return true;
                    }
                }
                return false;
            });
            if (prefFragDesc != null && entryHolder[0] != null) {
                ctx.addClass("it.octogram.android.PreferencesFragment", prefFragDesc);
                ctx.addClass("it.octogram.android.PreferencesEntry", entryHolder[0]);
                ctx.addClass("it.octogram.android.GeneralPreferencesEntry",
                        findOctoEntry(scanner, prefFragDesc, "general", "alwaysShowDownloads", "hideStories"));
                ctx.addClass("it.octogram.android.ChatsPreferencesEntry",
                        findOctoEntry(scanner, prefFragDesc, "chats", "formatTimeWithSeconds", "numberRounding", "showDcId"));
                ctx.addClass("it.octogram.android.PrivacyPreferencesEntry",
                        findOctoEntry(scanner, prefFragDesc, "privacy", "hidePhoneNumber", "phoneNumberAlternative"));
                ctx.addClass("it.octogram.android.ExperimentsPreferencesEntry",
                        findOctoEntry(scanner, prefFragDesc, "experimental", "experimentsEnabled", "downloadBoost", "uploadBoost"));
            }
        }

        // 2. Nagram 13.1.1 / NagramX / NagramXF & Nekogram (tw.nekomimi.nekogram.settings.*)
        String nekoPkg = "Ltw/nekomimi/nekogram/settings/";
        String baseNekoXDesc = "Ltw/nekomimi/nekogram/settings/BaseNekoXSettingsActivity;";
        if (!MappingContext.hasClass(scanner, baseNekoXDesc)) {
            baseNekoXDesc = scanner.findClassByPredicate(nekoPkg, (desc, fields, methods) -> {
                if (!baseFragmentDesc.equals(scanner.getSuperClass(desc))) return false;
                boolean hasHashMap = false;
                for (DexScanner.FieldInfo f : fields) {
                    if (!f.isStatic && "Ljava/util/HashMap;".equals(f.typeDesc)) {
                        hasHashMap = true;
                        break;
                    }
                }
                if (!hasHashMap) return false;
                for (DexScanner.MethodInfo m : methods) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 2) continue;
                    boolean strRun = "Ljava/lang/String;".equals(m.paramDescs[0])
                            && "Ljava/lang/Runnable;".equals(m.paramDescs[1]);
                    boolean runStr = "Ljava/lang/Runnable;".equals(m.paramDescs[0])
                            && "Ljava/lang/String;".equals(m.paramDescs[1]);
                    if (!strRun && !runStr) {
                        continue;
                    }
                    boolean callsParseInt = false;
                    boolean callsMapGet = false;
                    for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                            desc, m.name, m.paramDescs, null)) {
                        if ("Ljava/lang/Integer;".equals(ref.ownerDesc) && "parseInt".equals(ref.name)) {
                            callsParseInt = true;
                        } else if (("Ljava/util/HashMap;".equals(ref.ownerDesc) || "Ljava/util/AbstractMap;".equals(ref.ownerDesc))
                                && "get".equals(ref.name)) {
                            callsMapGet = true;
                        }
                    }
                    if (callsParseInt && callsMapGet) {
                        return true;
                    }
                }
                return false;
            });
        }
        if (baseNekoXDesc != null) {
            ctx.addClass("tw.nekomimi.nekogram.settings.BaseNekoXSettingsActivity", baseNekoXDesc);
            String scrollToRow = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(baseNekoXDesc)) {
                if (!m.isStatic && "scrollToRow".equals(m.name) && "V".equals(m.returnDesc)
                        && m.paramDescs.length == 2
                        && (("Ljava/lang/String;".equals(m.paramDescs[0]) && "Ljava/lang/Runnable;".equals(m.paramDescs[1]))
                        || ("Ljava/lang/Runnable;".equals(m.paramDescs[0]) && "Ljava/lang/String;".equals(m.paramDescs[1])))) {
                    scrollToRow = m.name;
                    break;
                }
            }
            if (scrollToRow == null) {
                scrollToRow = scanner.findSingleMethod(baseNekoXDesc, false, "V",
                        "Ljava/lang/String;", "Ljava/lang/Runnable;");
            }
            if (scrollToRow == null) {
                scrollToRow = scanner.findSingleMethod(baseNekoXDesc, false, "V",
                        "Ljava/lang/Runnable;", "Ljava/lang/String;");
            }
            if (scrollToRow == null) {
                for (DexScanner.MethodInfo m : scanner.getMethods(baseNekoXDesc)) {
                    if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 2) continue;
                    boolean strRun = "Ljava/lang/String;".equals(m.paramDescs[0])
                            && "Ljava/lang/Runnable;".equals(m.paramDescs[1]);
                    boolean runStr = "Ljava/lang/Runnable;".equals(m.paramDescs[0])
                            && "Ljava/lang/String;".equals(m.paramDescs[1]);
                    if (!strRun && !runStr) continue;
                    boolean callsParseInt = false;
                    boolean callsMapGet = false;
                    for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(
                            baseNekoXDesc, m.name, m.paramDescs, null)) {
                        if ("Ljava/lang/Integer;".equals(ref.ownerDesc) && "parseInt".equals(ref.name)) {
                            callsParseInt = true;
                        } else if (("Ljava/util/HashMap;".equals(ref.ownerDesc) || "Ljava/util/AbstractMap;".equals(ref.ownerDesc))
                                && "get".equals(ref.name)) {
                            callsMapGet = true;
                        }
                    }
                    if (callsParseInt && callsMapGet) {
                        scrollToRow = m.name;
                        break;
                    }
                }
            }
            ctx.addMethod("BaseNekoXSettingsActivity", "scrollToRow", scrollToRow);

            String nekoGeneral = "Ltw/nekomimi/nekogram/settings/NekoGeneralSettingsActivity;";
            if (!MappingContext.hasClass(scanner, nekoGeneral)) {
                nekoGeneral = scanner.findClassByStrings(nekoPkg, baseNekoXDesc, "4.8K -> 4777", "DialogsSettings");
                if (nekoGeneral == null) {
                    nekoGeneral = scanner.findClassByStrings(nekoPkg, baseNekoXDesc, "4.8K -> 4777", "PrivacyTitle");
                }
            }
            ctx.addClass("tw.nekomimi.nekogram.settings.NekoGeneralSettingsActivity", nekoGeneral);

            String nekoChat = "Ltw/nekomimi/nekogram/settings/NekoChatSettingsActivity;";
            if (!MappingContext.hasClass(scanner, nekoChat)) {
                nekoChat = scanner.findClassByStrings(nekoPkg, baseNekoXDesc, "StickerSize", "DoubleTapAction", "MessageMenu");
            }
            ctx.addClass("tw.nekomimi.nekogram.settings.NekoChatSettingsActivity", nekoChat);

            String nekoExp = "Ltw/nekomimi/nekogram/settings/NekoExperimentalSettingsActivity;";
            if (!MappingContext.hasClass(scanner, nekoExp)) {
                nekoExp = scanner.findClassByStrings(nekoPkg, baseNekoXDesc, "Experiment", "GhostMode", "SensitiveDisableFiltering");
            }
            ctx.addClass("tw.nekomimi.nekogram.settings.NekoExperimentalSettingsActivity", nekoExp);

            String ghostAct = "Ltw/nekomimi/nekogram/settings/GhostModeActivity;";
            if (!MappingContext.hasClass(scanner, ghostAct)) {
                String cand = scanner.findClassByStrings(nekoPkg, null,
                        "sendReadMessagePackets", "sendOnlinePackets", "sendUploadProgress");
                if (cand != null && scanner.isSubclassOf(cand, baseFragmentDesc)) {
                    ghostAct = cand;
                } else {
                    ghostAct = null;
                }
            }
            ctx.addClass("tw.nekomimi.nekogram.settings.GhostModeActivity", ghostAct);
        }

        // 3. Nekogram BaseNekoSettingsActivity#scrollToRow
        String baseNekoDesc = "Ltw/nekomimi/nekogram/settings/BaseNekoSettingsActivity;";
        if (MappingContext.hasClass(scanner, baseNekoDesc)) {
            ctx.addClass("tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity", baseNekoDesc);
            String nekoScroll = null;
            for (DexScanner.MethodInfo m : scanner.getMethods(baseNekoDesc)) {
                if (!m.isStatic && "scrollToRow".equals(m.name) && "V".equals(m.returnDesc)
                        && m.paramDescs.length == 2 && "Ljava/lang/String;".equals(m.paramDescs[0])) {
                    nekoScroll = m.name;
                    break;
                }
            }
            if (nekoScroll == null) {
                String unique = null;
                boolean ambiguous = false;
                for (DexScanner.MethodInfo m : scanner.getMethods(baseNekoDesc)) {
                    if (m.isStatic || m.name.startsWith("<") || m.name.startsWith("lambda$")) continue;
                    if ("V".equals(m.returnDesc) && m.paramDescs.length == 2
                            && "Ljava/lang/String;".equals(m.paramDescs[0])) {
                        if (unique != null && !unique.equals(m.name)) {
                            ambiguous = true;
                            break;
                        }
                        unique = m.name;
                    }
                }
                if (!ambiguous) {
                    nekoScroll = unique;
                }
            }
            ctx.addMethod("BaseNekoSettingsActivity", "scrollToRow", nekoScroll);
        }

        // 4. Forkgram / Forkgram Classic
        String forkSettingsDesc = scanner.findClassByStrings("Lorg/telegram/ui/", baseFragmentDesc,
                "hideSensitivePhone", "formatWithSeconds", "updateForkCheckInterval");
        if (forkSettingsDesc == null) {
            forkSettingsDesc = scanner.findClassByStrings("Lorg/telegram/ui/", baseFragmentDesc,
                    "hideSensitivePhone", "formatWithSeconds", "forkgram_settings.json");
        }
        ctx.addClass("org.forkgram.messenger.ForkSettingsActivity", forkSettingsDesc);

        // 5. Mercurygram DebugSettingsActivity
        String mercuryMainDesc = "Lit/belloworld/mercurygram/ui/MercurygramSettingsActivity;";
        if (MappingContext.hasClass(scanner, mercuryMainDesc)) {
            String universalFragDesc = scanner.getSuperClass(mercuryMainDesc);
            String mercuryDebugDesc = null;
            if (universalFragDesc != null && !"Ljava/lang/Object;".equals(universalFragDesc)) {
                mercuryDebugDesc = scanner.findClassByStrings("Lorg/telegram/ui/", universalFragDesc,
                        "Remove Ads & Proxy Sponsor", "Disable Secure Flags", "Make Memory Dump");
            }
            if (mercuryDebugDesc == null) {
                mercuryDebugDesc = scanner.findClassByPredicate("Lorg/telegram/ui/", (desc, fs, ms) -> {
                    if (!scanner.isSubclassOf(desc, baseFragmentDesc)) return false;
                    boolean s1 = false, s2 = false, s3 = false;
                    for (DexScanner.MethodInfo m : ms) {
                        if (!s1 && m.usesString("Remove Ads & Proxy Sponsor")) s1 = true;
                        if (!s2 && m.usesString("Disable Secure Flags")) s2 = true;
                        if (!s3 && m.usesString("Make Memory Dump")) s3 = true;
                    }
                    return s1 && s2 && s3;
                });
            }
            ctx.addClass("it.belloworld.mercurygram.DebugSettingsActivity", mercuryDebugDesc);
        }

        // 6. Nicegram NicegramSettingsActivity
        String nicegramSettingsDesc = scanner.findClassByStrings(null, baseFragmentDesc,
                "HidePhoneNumber", "ShowRegistrationDate", "ShowDownloadVideosToGallery",
                "NG_GLOBAL_PREFS", "MaxAccountCount");
        if (nicegramSettingsDesc == null) {
            nicegramSettingsDesc = scanner.findClassByStrings(null, baseFragmentDesc,
                    "HidePhoneNumber", "ShowRegistrationDate", "ShowDownloadVideosToGallery",
                    "NG_GLOBAL_PREFS");
        }
        ctx.addClass("app.nicegram.NicegramSettingsActivity", nicegramSettingsDesc);
    }

    static String findOctoEntry(DexScanner scanner, String prefFragDesc, String screenSlug, String... octoConfigFields) {
        return scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            boolean hasNoArgCtor = false;
            boolean hasCreatePrefs = false;
            boolean hasSlug = false;
            int matchedFields = 0;
            boolean[] fieldMatched = new boolean[octoConfigFields.length];
            for (DexScanner.MethodInfo m : methods) {
                if ("<init>".equals(m.name) && !m.isStatic && m.paramDescs.length == 0) {
                    hasNoArgCtor = true;
                }
                if (!m.isStatic && m.paramDescs.length == 2
                        && prefFragDesc.equals(m.paramDescs[0])
                        && "Landroid/content/Context;".equals(m.paramDescs[1])) {
                    hasCreatePrefs = true;
                }
            }
            if (!hasNoArgCtor || !hasCreatePrefs) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!hasSlug && screenSlug != null && m.usesString(screenSlug)) {
                    hasSlug = true;
                }
                for (int i = 0; i < octoConfigFields.length; i++) {
                    if (!fieldMatched[i] && m.readsFieldNamed(octoConfigFields[i])) {
                        fieldMatched[i] = true;
                        matchedFields++;
                    }
                }
            }
            return (screenSlug == null || hasSlug) && matchedFields == octoConfigFields.length;
        });
    }

}
