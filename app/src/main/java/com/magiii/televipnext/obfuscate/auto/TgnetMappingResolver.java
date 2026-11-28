package com.magiii.televipnext.obfuscate.auto;

import com.magiii.televipnext.Class.ClassNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves tgnet TL/ConnectionsManager/RPC and SQLite/MessagesStorage mappings.
 */
final class TgnetMappingResolver {

    private TgnetMappingResolver() {}

    static String extractConnectionsManagerTypes(MappingContext ctx, DexScanner scanner) {
        String cmDesc = DexScanner.javaToDesc(ClassNames.CONNECTIONS_MANAGER);
        DexScanner.MethodInfo bestSendReq = null;
        for (DexScanner.MethodInfo m : scanner.getMethods(cmDesc)) {
            if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length >= 10
                    && "I".equals(m.paramDescs[5]) && "I".equals(m.paramDescs[6])
                    && "I".equals(m.paramDescs[7]) && "Z".equals(m.paramDescs[8]) && "I".equals(m.paramDescs[9])) {
                if ("sendRequestInternal".equals(m.name)) {
                    bestSendReq = m;
                    break;
                }
                if (bestSendReq == null || m.paramDescs.length > bestSendReq.paramDescs.length) {
                    bestSendReq = m;
                }
            }
        }
        if (bestSendReq == null) return null;
        ctx.addMethod("ConnectionsManager", "sendRequestInternal", bestSendReq.name);
        ctx.addClass(ClassNames.TL_OBJECT, bestSendReq.paramDescs[0]);
        ctx.addClass(ClassNames.REQUEST_DELEGATE, bestSendReq.paramDescs[1]);
        ctx.addClass(ClassNames.REQUEST_DELEGATE_TIMESTAMP, bestSendReq.paramDescs[2]);
        ctx.addClass(ClassNames.QUICK_ACK_DELEGATE, bestSendReq.paramDescs[3]);
        ctx.addClass(ClassNames.WRITE_TO_SOCKET_DELEGATE, bestSendReq.paramDescs[4]);
        for (DexScanner.MethodInfo rm : scanner.getMethods(bestSendReq.paramDescs[1])) {
            if (!rm.isStatic && "V".equals(rm.returnDesc) && rm.paramDescs.length == 2
                    && bestSendReq.paramDescs[0].equals(rm.paramDescs[0])) {
                ctx.addMethod("RequestDelegate", "run", rm.name);
                break;
            }
        }
        return bestSendReq.paramDescs[0];
    }

    static String resolveUserPhoneField(DexScanner scanner, String userDesc) {
        if (userDesc == null) return null;
        String[] result = new String[1];
        scanner.findClassByPredicate(null, (desc, fields, methods) -> {
            if (!userDesc.equals(scanner.getSuperClass(desc)) && !userDesc.equals(desc)) return false;
            for (DexScanner.MethodInfo m : methods) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && m.getInsnsSize() > 250) {
                    List<String> strReads = new ArrayList<>();
                    for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(desc, m.name, m.paramDescs, userDesc)) {
                        if ("Ljava/lang/String;".equals(rf.typeDesc) && !strReads.contains(rf.name)) {
                            strReads.add(rf.name);
                        }
                    }
                    if (strReads.size() >= 4) {
                        result[0] = strReads.get(3);
                        return true;
                    }
                }
            }
            return false;
        });
        return result[0];
    }

    static void resolveObfuscatedTgnetBase(MappingContext ctx, DexScanner scanner, String msgObjDesc, String chatActDesc) {
        String tlObjectDesc = ctx.resolvedDescOrRaw(ClassNames.TL_OBJECT);
        String rawMsgDesc = DexScanner.javaToDesc(ClassNames.MESSAGE);
        boolean needMsgResolve = !MappingContext.hasClass(scanner, rawMsgDesc) || !MappingContext.hasFieldNamed(scanner, rawMsgDesc, "flags");
        if (needMsgResolve && msgObjDesc != null && tlObjectDesc != null) {
            String bestMsgOwnerField = null;
            String bestMsgDesc = null;
            String bestDeserName = null;
            if (MappingContext.hasClass(scanner, rawMsgDesc)) {
                bestMsgDesc = rawMsgDesc;
                bestDeserName = MappingContext.hasMethodNamed(scanner, rawMsgDesc, "TLdeserialize") ? "TLdeserialize" : null;
            } else {
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
            }
            if (bestMsgDesc != null) {
                ctx.addClass(ClassNames.MESSAGE, bestMsgDesc);
                if (bestMsgOwnerField != null) {
                    ctx.addField("MessageObject", "messageOwner", bestMsgOwnerField);
                }
                if (bestDeserName != null) {
                    ctx.addMethod("TLRPC$Message", "TLdeserialize", bestDeserName);
                }

                // TLRPC$Message#readAttachPath: instance (?, J) -> V
                for (DexScanner.MethodInfo mm : scanner.getMethods(bestMsgDesc)) {
                    if (!mm.isStatic && "V".equals(mm.returnDesc) && mm.paramDescs.length == 2
                            && "J".equals(mm.paramDescs[1])) {
                        ctx.addMethod("TLRPC$Message", "readAttachPath", mm.name);
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
                    ctx.addClass("org.telegram.tgnet.TLRPC$Peer", peerDescHolder[0]);
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
                                ctx.addField("TLRPC$Message", "flags", ints.get(0));
                                ctx.addField("TLRPC$Message", "id", ints.get(1));
                                ctx.addField("TLRPC$Message", "date", ints.get(2));
                                ctx.addField("TLRPC$Message", "from_id", peers.get(0));
                                ctx.addField("TLRPC$Message", "peer_id", peers.get(1));
                                ctx.addField("TLRPC$Message", "message", firstStr);
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
                    ctx.addField("TLRPC$Message", "ttl", ttlField);
                }
            }
        }
    }

    static String resolveObfuscatedTgnetConstructors(MappingContext ctx, DexScanner scanner, String tlObjectDesc) {
        if (tlObjectDesc == null) return null;
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
        ctorTargets.put(0x3d6ce850, ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES);
        ctorTargets.put(0x9bd2f439, ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES);
        ctorTargets.put(0xec210fbf, ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES);
        ctorTargets.put(0xb6c8c393, ClassNames.TL_CONTACTS_GET_SPONSORED_PEERS);
        ctorTargets.put(0xdfd14005, "org.telegram.tgnet.TLRPC$TL_messages_editMessage");

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
            ctx.addClass(key, desc);
        }

        // Resolve TLRPC$Peer (user_id, chat_id, channel_id) via serializeToStream on TL_peer* subclasses
        String peerDesc = ctx.classMap.containsKey("org.telegram.tgnet.TLRPC$Peer")
                ? DexScanner.javaToDesc(ctx.classMap.get("org.telegram.tgnet.TLRPC$Peer"))
                : (found.get(PEER_USER_KEY) != null ? scanner.getSuperClass(found.get(PEER_USER_KEY)) : null);
        if (peerDesc != null) {
            ctx.addClass("org.telegram.tgnet.TLRPC$Peer", peerDesc);
            ctx.addField("TLRPC$Peer", "user_id", MappingContext.findFirstReadLongField(scanner, found.get(PEER_USER_KEY), peerDesc));
            ctx.addField("TLRPC$Peer", "chat_id", MappingContext.findFirstReadLongField(scanner, found.get(PEER_CHAT_KEY), peerDesc));
            ctx.addField("TLRPC$Peer", "channel_id", MappingContext.findFirstReadLongField(scanner, found.get(PEER_CHANNEL_KEY), peerDesc));
        }

        // Resolve TLRPC$InputPeer (user_id, chat_id, channel_id) via serializeToStream on TL_inputPeer* subclasses
        String inputPeerChannelDesc = found.get(ClassNames.TL_INPUT_PEER_CHANNEL);
        String inputPeerDesc = inputPeerChannelDesc != null
                ? scanner.getSuperClass(inputPeerChannelDesc)
                : (found.get(INPUT_PEER_USER_KEY) != null ? scanner.getSuperClass(found.get(INPUT_PEER_USER_KEY)) : null);
        if (inputPeerDesc != null) {
            ctx.addClass("org.telegram.tgnet.TLRPC$InputPeer", inputPeerDesc);
            ctx.addField("TLRPC$InputPeer", "user_id", MappingContext.findFirstReadLongField(scanner, found.get(INPUT_PEER_USER_KEY), inputPeerDesc));
            ctx.addField("TLRPC$InputPeer", "chat_id", MappingContext.findFirstReadLongField(scanner, found.get(INPUT_PEER_CHAT_KEY), inputPeerDesc));
            ctx.addField("TLRPC$InputPeer", "channel_id", MappingContext.findFirstReadLongField(scanner, inputPeerChannelDesc, inputPeerDesc));
        }

        String delMsgDesc = ctx.classMap.containsKey(ClassNames.TL_UPDATE_DELETE_MESSAGES)
                ? found.get(ClassNames.TL_UPDATE_DELETE_MESSAGES) : null;
        if (delMsgDesc != null) {
            ctx.addField("TL_update$TL_updateDeleteMessages", "messages",
                    scanner.findSingleField(delMsgDesc, false, "Ljava/util/ArrayList;"));
        }
        String delChDesc = ctx.classMap.containsKey(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES)
                ? found.get(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES) : null;
        if (delChDesc != null) {
            ctx.addField("TL_update$TL_updateDeleteChannelMessages", "channel_id",
                    scanner.findSingleField(delChDesc, false, "J"));
            ctx.addField("TL_update$TL_updateDeleteChannelMessages", "messages",
                    scanner.findSingleField(delChDesc, false, "Ljava/util/ArrayList;"));
        }
        String statusDesc = ctx.classMap.containsKey(ClassNames.TL_ACCOUNT_UPDATE_STATUS)
                ? found.get(ClassNames.TL_ACCOUNT_UPDATE_STATUS) : null;
        if (statusDesc != null) {
            ctx.addField("TL_account$updateStatus", "offline",
                    scanner.findSingleField(statusDesc, false, "Z"));
        }
        String readHistDesc = ctx.classMap.containsKey(ClassNames.TL_MESSAGES_READ_HISTORY)
                ? found.get(ClassNames.TL_MESSAGES_READ_HISTORY) : null;
        if (readHistDesc != null) {
            ctx.addField("TLRPC$TL_messages_readHistory", "max_id",
                    scanner.findSingleField(readHistDesc, false, "I"));
            for (DexScanner.FieldInfo f : scanner.getFields(readHistDesc)) {
                if (!f.isStatic && f.typeDesc.startsWith("L")
                        && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")) {
                    ctx.addField("TLRPC$TL_messages_readHistory", "peer", f.name);
                    if (!ctx.classMap.containsKey("org.telegram.tgnet.TLRPC$InputPeer")) {
                        ctx.addClass("org.telegram.tgnet.TLRPC$InputPeer", f.typeDesc);
                    }
                    break;
                }
            }
        }
        String chReadHistDesc = ctx.classMap.containsKey(ClassNames.TL_CHANNELS_READ_HISTORY)
                ? found.get(ClassNames.TL_CHANNELS_READ_HISTORY) : null;
        if (chReadHistDesc != null) {
            ctx.addField("TLRPC$TL_channels_readHistory", "max_id",
                    scanner.findSingleField(chReadHistDesc, false, "I"));
            for (DexScanner.FieldInfo f : scanner.getFields(chReadHistDesc)) {
                if (!f.isStatic && f.typeDesc.startsWith("L")
                        && !f.typeDesc.startsWith("Ljava/") && !f.typeDesc.startsWith("Landroid/")) {
                    ctx.addField("TLRPC$TL_channels_readHistory", "channel", f.name);
                    break;
                }
            }
        }
        String affectedDesc = ctx.classMap.containsKey(ClassNames.TL_MESSAGES_AFFECTED)
                ? found.get(ClassNames.TL_MESSAGES_AFFECTED) : null;
        if (affectedDesc != null) {
            List<String> intFields = new ArrayList<>();
            for (DexScanner.FieldInfo f : scanner.getFields(affectedDesc)) {
                if (!f.isStatic && "I".equals(f.typeDesc)) {
                    intFields.add(f.name);
                }
            }
            if (intFields.size() >= 2) {
                ctx.addField("TLRPC$TL_messages_affectedMessages", "pts", intFields.get(0));
                ctx.addField("TLRPC$TL_messages_affectedMessages", "pts_count", intFields.get(1));
            }
        }

        String tlEditDesc = ctx.classMap.containsKey("org.telegram.tgnet.TLRPC$TL_messages_editMessage")
                ? DexScanner.javaToDesc(ctx.classMap.get("org.telegram.tgnet.TLRPC$TL_messages_editMessage"))
                : "Lorg/telegram/tgnet/TLRPC$TL_messages_editMessage;";
        if (MappingContext.hasClass(scanner, tlEditDesc)) {
            if (!MappingContext.hasFieldNamed(scanner, tlEditDesc, "message")) {
                String msgStr = scanner.findSingleField(tlEditDesc, false, "Ljava/lang/String;");
                if (msgStr != null) ctx.addField("TLRPC$TL_messages_editMessage", "message", msgStr);
            }
            if (!MappingContext.hasFieldNamed(scanner, tlEditDesc, "peer") || !MappingContext.hasFieldNamed(scanner, tlEditDesc, "id")) {
                String resolvedInputPeerDesc = ctx.resolvedDescOrRaw("org.telegram.tgnet.TLRPC$InputPeer");
                for (DexScanner.MethodInfo m : scanner.getMethods(tlEditDesc)) {
                    if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 1 && m.getInsnsSize() > 80) {
                        boolean seenPeer = false;
                        String peerFld = null;
                        String idFld = null;
                        for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(tlEditDesc, m.name, m.paramDescs, tlEditDesc)) {
                            if (!seenPeer && resolvedInputPeerDesc.equals(rf.typeDesc)) {
                                seenPeer = true;
                                peerFld = rf.name;
                            } else if (seenPeer && idFld == null && "I".equals(rf.typeDesc)) {
                                idFld = rf.name;
                                break;
                            }
                        }
                        if (peerFld != null) ctx.addField("TLRPC$TL_messages_editMessage", "peer", peerFld);
                        if (idFld != null) ctx.addField("TLRPC$TL_messages_editMessage", "id", idFld);
                        break;
                    }
                }
            }
        }
        return found.get(ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES);
    }


    static void resolveSQLite(MappingContext ctx, DexScanner scanner) {
        String dbDesc = "Lorg/telegram/SQLite/SQLiteDatabase;";
        String cursorDesc = "Lorg/telegram/SQLite/SQLiteCursor;";
        String stmtDesc = "Lorg/telegram/SQLite/SQLitePreparedStatement;";
        if (scanner.getMethods(dbDesc).isEmpty()) return;

        ctx.addMethod("SQLiteDatabase", "queryFinalized",
                scanner.findSingleMethod(dbDesc, false, cursorDesc, "Ljava/lang/String;", "[Ljava/lang/Object;"));
        ctx.addMethod("SQLiteDatabase", "executeFast",
                scanner.findSingleMethod(dbDesc, false, stmtDesc, "Ljava/lang/String;"));

        if (!scanner.getMethods(cursorDesc).isEmpty()) {
            ctx.addMethod("SQLiteCursor", "next", scanner.findSingleMethod(cursorDesc, false, "Z"));
            ctx.addMethod("SQLiteCursor", "byteBufferValue",
                    scanner.findSingleMethod(cursorDesc, false, "Lorg/telegram/tgnet/NativeByteBuffer;", "I"));

            String intValMeth = null;
            if (MappingContext.hasMethodNamed(scanner, cursorDesc, "intValue")) {
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
            ctx.addMethod("SQLiteCursor", "intValue", intValMeth);
            ctx.addMethod("SQLiteCursor", "longValue", scanner.findSingleMethod(cursorDesc, false, "J", "I"));

            for (DexScanner.MethodInfo m : scanner.getMethods(cursorDesc)) {
                if (!m.isStatic && "V".equals(m.returnDesc) && m.paramDescs.length == 0) {
                    for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(cursorDesc, m.name, new String[0], stmtDesc)) {
                        if ("V".equals(ref.returnDesc) && ref.paramDescs.length == 0) {
                            ctx.addMethod("SQLiteCursor", "dispose", m.name);
                            ctx.addMethod("SQLitePreparedStatement", "dispose", ref.name);
                            break;
                        }
                    }
                }
            }
        }

        if (!scanner.getMethods(stmtDesc).isEmpty()) {
            ctx.addMethod("SQLitePreparedStatement", "bindByteBuffer",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "Lorg/telegram/tgnet/NativeByteBuffer;"));
            ctx.addMethod("SQLitePreparedStatement", "bindInteger",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "I"));
            ctx.addMethod("SQLitePreparedStatement", "bindLong",
                    scanner.findSingleMethod(stmtDesc, false, "V", "I", "J"));

            String requeryMeth = null;
            if (MappingContext.hasZeroArgMethodNamed(scanner, stmtDesc, "requery")) {
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
            ctx.addMethod("SQLitePreparedStatement", "requery", requeryMeth);
            String stepMeth = MappingContext.hasZeroArgMethodNamed(scanner, stmtDesc, "step")
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
            ctx.addMethod("SQLitePreparedStatement", "step", stepMeth);
        }
    }


    static void resolveDialogMessageField(MappingContext ctx, DexScanner scanner, String msgCtrlDesc, String deleteMeth) {
        // 1. Mixed-mode raw field preservation
        for (DexScanner.FieldInfo f : scanner.getFields(msgCtrlDesc)) {
            if (!f.isStatic && "dialogMessage".equals(f.name)) {
                String getLong = findLongSparseArrayGet(scanner, f.typeDesc);
                if (getLong != null) {
                    ctx.addClass(ClassNames.LONG_SPARES_ARRAY, f.typeDesc);
                    ctx.addMethod("LongSparseArray", "get", getLong);
                    ctx.addField("MessagesController", "dialogMessage", f.name);
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
                        ctx.addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
                        ctx.addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
                        ctx.addField("MessagesController", "dialogMessage", rf.name);
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
            ctx.addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
            ctx.addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
            ctx.addField("MessagesController", "dialogMessage", helperMatchedField);
            return;
        }

        // 4. Fallback: field read directly in deleteMessages, or first LongSparseArray field
        if (deleteMeth != null) {
            for (DexScanner.FieldInfo rf : scanner.getReadInstanceFieldsInMethod(msgCtrlDesc, deleteMeth, null, msgCtrlDesc)) {
                if (lsaFieldTypes.containsKey(rf.name)) {
                    String lsaType = lsaFieldTypes.get(rf.name);
                    ctx.addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
                    ctx.addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
                    ctx.addField("MessagesController", "dialogMessage", rf.name);
                    return;
                }
            }
        }
        if (firstLsaField != null) {
            String lsaType = lsaFieldTypes.get(firstLsaField);
            ctx.addClass(ClassNames.LONG_SPARES_ARRAY, lsaType);
            ctx.addMethod("LongSparseArray", "get", lsaTypeGetMethod.get(lsaType));
            ctx.addField("MessagesController", "dialogMessage", firstLsaField);
        }
    }

    static String findLongSparseArrayGet(DexScanner scanner, String lsaDesc) {
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


    static void resolveMessagesControllerExtras(MappingContext ctx, DexScanner scanner, String msgCtrlDesc) {
        String checkPromo = scanner.findMethodByString(msgCtrlDesc, "proxyDialogAddress", false, "V", "Z");
        if (checkPromo != null) {
            ctx.addMethod("MessagesController", "checkPromoInfoInternal", checkPromo);
            for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(msgCtrlDesc, checkPromo, new String[]{"Z"}, msgCtrlDesc)) {
                if (mr.paramDescs.length == 0 && "Landroid/content/SharedPreferences;".equals(mr.returnDesc)) {
                    ctx.addMethod("MessagesController", "getGlobalMainSettings", mr.name);
                    break;
                }
            }
        }

        boolean foundRemovePromo = false;
        String promoDlgField = scanner.findSingleField(msgCtrlDesc, false, "Lorg/telegram/tgnet/TLRPC$Dialog;");
        if (promoDlgField != null) {
            for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0
                        || m.name.startsWith("lambda$")) continue;
                for (DexScanner.FieldInfo wf : scanner.getWrittenFieldsInMethod(msgCtrlDesc, m.name, new String[0], msgCtrlDesc)) {
                    if (promoDlgField.equals(wf.name)) {
                        ctx.addMethod("MessagesController", "removePromoDialog", m.name);
                        foundRemovePromo = true;
                        break;
                    }
                }
            }
        }
        if (!foundRemovePromo) {
            Map<String, Integer> typeCounts = new HashMap<>();
            for (DexScanner.FieldInfo f : scanner.getFields(msgCtrlDesc)) {
                if (!f.isStatic) typeCounts.merge(f.typeDesc, 1, Integer::sum);
            }
            for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
                if (m.isStatic || !"V".equals(m.returnDesc) || m.paramDescs.length != 0
                        || m.name.startsWith("lambda$")) continue;
                int sz = m.getInsnsSize();
                if (sz < 25 || sz > 120) continue;
                List<DexScanner.FieldInfo> written = scanner.getWrittenFieldsInMethod(
                        msgCtrlDesc, m.name, new String[0], msgCtrlDesc);
                if (written.size() != 1) continue;
                DexScanner.FieldInfo wf = written.get(0);
                if (!Integer.valueOf(1).equals(typeCounts.get(wf.typeDesc))
                        || wf.typeDesc.length() <= 1
                        || wf.typeDesc.startsWith("Ljava/")
                        || wf.typeDesc.startsWith("Landroid/")) {
                    continue;
                }
                boolean callsLongValOf = false;
                boolean callsNotifPost = false;
                boolean callsSelfWithFieldType = false;
                for (DexScanner.MethodRef mr : scanner.getCalledMethodsInMethod(
                        msgCtrlDesc, m.name, new String[0], null)) {
                    if ("Ljava/lang/Long;".equals(mr.ownerDesc) && "valueOf".equals(mr.name)) {
                        callsLongValOf = true;
                    } else if ("V".equals(mr.returnDesc) && mr.paramDescs.length == 2
                            && "I".equals(mr.paramDescs[0]) && "[Ljava/lang/Object;".equals(mr.paramDescs[1])) {
                        callsNotifPost = true;
                    } else if (msgCtrlDesc.equals(mr.ownerDesc) && "V".equals(mr.returnDesc)
                            && mr.paramDescs.length == 1 && wf.typeDesc.equals(mr.paramDescs[0])) {
                        callsSelfWithFieldType = true;
                    }
                }
                if (callsLongValOf && callsNotifPost && callsSelfWithFieldType) {
                    ctx.addMethod("MessagesController", "removePromoDialog", m.name);
                    break;
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
            ctx.addMethod("MessagesController", "storyEntitiesAllowed2", storyEntitiesUser);
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
                ctx.addMethod("MessagesController", isEntities ? "storyEntitiesAllowed" : "storiesEnabled", m.name);
            }
        }
    }

    static void resolveSponsoredMessagesMethod(MappingContext ctx, DexScanner scanner, String msgCtrlDesc, String reqDescHint) {
        if (msgCtrlDesc == null) return;
        String mappedReq = ctx.classMap.get(ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES);
        String reqDesc = reqDescHint != null
                ? reqDescHint
                : (mappedReq != null
                    ? DexScanner.javaToDesc(mappedReq)
                    : DexScanner.javaToDesc(ClassNames.TL_MESSAGES_GET_SPONSORED_MESSAGES));
        for (DexScanner.MethodInfo m : scanner.getMethods(msgCtrlDesc)) {
            if (m.isStatic || m.paramDescs.length != 1 || !"J".equals(m.paramDescs[0])
                    || "V".equals(m.returnDesc) || m.returnDesc.length() <= 1) {
                continue;
            }
            for (DexScanner.MethodRef ref : scanner.getCalledMethodsInMethod(msgCtrlDesc, m.name, m.paramDescs, reqDesc)) {
                if ("<init>".equals(ref.name)) {
                    ctx.addMethod("MessagesController", "getSponsoredMessages", m.name);
                    return;
                }
            }
        }
    }

}
