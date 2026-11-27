package com.magiii.televipnext.features.ghostMode;

import com.magiii.televipnext.Callback.IntCallback;
import com.magiii.televipnext.Class.ClassLoad;
import com.magiii.televipnext.Class.ClassNames;
import com.magiii.televipnext.Clients.ClientManager;
import com.magiii.televipnext.Configs.ConfigManager;
import com.magiii.televipnext.application.AndroidUtilities;
import com.magiii.televipnext.logging.Logger;
import com.magiii.televipnext.obfuscate.Obfuscate;
import com.magiii.televipnext.utils.Utils;
import com.magiii.televipnext.virtuals.SQLite.SQLiteCursor;
import com.magiii.televipnext.virtuals.messenger.DispatchQueue;
import com.magiii.televipnext.virtuals.messenger.MessagesController;
import com.magiii.televipnext.virtuals.messenger.MessagesStorage;
import com.magiii.televipnext.virtuals.messenger.UserConfig;
import com.magiii.televipnext.virtuals.messenger.Utilities;
import com.magiii.televipnext.virtuals.tgnet.ConnectionsManager;
import com.magiii.televipnext.virtuals.tgnet.RequestDelegate;
import com.magiii.televipnext.virtuals.tgnet.TLRPC;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import com.magiii.televipnext.xposed.ReflectHelper;

public class HideSeen {


    public static Object TLChannels_readHistory;
    public static Object TLMessages_readHistory;

    private static final Set<Object> allowedReadRequests = Collections.newSetFromMap(new IdentityHashMap<>());

    static void markAllowedReadRequest(Object req) {
        if (req == null) return;
        synchronized (allowedReadRequests) {
            if (allowedReadRequests.size() > 64) {
                allowedReadRequests.clear();
            }
            allowedReadRequests.add(req);
        }
    }

    static boolean consumeAllowedReadRequest(Object req) {
        if (req == null) return false;
        synchronized (allowedReadRequests) {
            return allowedReadRequests.remove(req);
        }
    }

    public static boolean isHideSeenEnabled() {
        return (ConfigManager.hideSeen != null && ConfigManager.hideSeen.isEnable())
                || (ConfigManager.hideSeenPrivateChat != null && ConfigManager.hideSeenPrivateChat.isEnable())
                || (ConfigManager.hideSeenChannel != null && ConfigManager.hideSeenChannel.isEnable());
    }

    public static void sendFakeReadResponse(Object onCompleteOrig) {
        if (onCompleteOrig == null) return;
        try {
            TLRPC.TL_messages_affectedMessages fakeRes = new TLRPC.TL_messages_affectedMessages();
            if (fakeRes.getTL_messages_affectedMessages() == null) return;
            fakeRes.setPts(-1);
            fakeRes.setPtsCount(0);
            RequestDelegate onComplete = new RequestDelegate(onCompleteOrig);
            Runnable runnable = () -> {
                try {
                    if (onComplete.requestDelegate != null) {
                        onComplete.run(fakeRes.getTL_messages_affectedMessages(), null);
                    }
                } catch (Throwable e) {
                    Logger.e(e);
                }
            };
            DispatchQueue queue = Utilities.getStageQueue();
            if (queue == null) {
                try {
                    queue = MessagesStorage.getMessagesStorage().getStorageQueue();
                } catch (Throwable ignored) {}
            }
            if (queue != null) {
                queue.postRunnable(runnable);
            } else {
                AndroidUtilities.runOnUIThread(runnable);
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static boolean isTLMessagesReadHistoryRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            return object.getClass().getName().contains("TL_messages_readHistory");
        } else {
            return object.getClass().getName().equals(Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_HISTORY));
        }
    }

    public static boolean isTLChannelsReadHistoryRequest(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
            return object.getClass().getName().contains("TL_channels_readHistory");
        } else {
            return object.getClass().getName().equals(Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_HISTORY));
        }
    }

    public static boolean isReadMessageRequest(Object object) {
        boolean privateHide = ConfigManager.hideSeenPrivateChat.isEnable();
        boolean channelHide = ConfigManager.hideSeenChannel.isEnable();

        boolean readHistory;
        boolean readDiscussion;
        boolean encryptedHistory;
        boolean readMessageContents;
        boolean channelReadMessageContents;
        boolean channelReadHistory;

        if (!ClientManager.isTgnetObfuscated()) {
            String className = object.getClass().getName();

            readHistory = className.contains("TL_messages_readHistory");
            readDiscussion = className.contains("TL_messages_readDiscussion");
            encryptedHistory = className.contains("TL_messages_readEncryptedHistory");
            readMessageContents = className.contains("TL_messages_readMessageContents");
            channelReadMessageContents = className.contains("TL_channels_readMessageContents");
            channelReadHistory = className.contains("TL_channels_readHistory");
        } else {
            Class<?> objectClass = object.getClass();

            readHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_HISTORY)));

            readDiscussion = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_DISCUSSION)));

            encryptedHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_ENCRYPTED_HISTORY)));

            readMessageContents = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS)));

            channelReadMessageContents = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_MESSAGE_CONTENTS)));

            channelReadHistory = objectClass.equals(ClassLoad.getClass(
                    Obfuscate.getClassName(ClassNames.TL_CHANNELS_READ_HISTORY)));
        }

        if (!(readHistory || readDiscussion ||
                (privateHide && encryptedHistory) ||
                (privateHide && readMessageContents) ||
                (channelHide && channelReadMessageContents) ||
                (channelHide && channelReadHistory))) {
            return false;
        }

        if (privateHide && channelHide) {
            return true;
        }

        if (readHistory || readDiscussion) {
            String objectName;
            if (!ClientManager.isTgnetObfuscated()){
                objectName = object.getClass().getSimpleName();
            } else {
                if (readHistory)
                    objectName = "TLRPC$TL_messages_readHistory";
                else
                    objectName = "TLRPC$TL_messages_readDiscussion";
            }
            TLRPC.InputPeer inputPeer = new TLRPC.InputPeer(
                    ReflectHelper.getObjectField(object, Obfuscate.getFieldName(objectName,"peer")));

            boolean isChannelOrGroup =
                    inputPeer.getChannel_id() > 0 ||
                            inputPeer.getChat_id() > 0;

            if (privateHide && !channelHide) {
                return !isChannelOrGroup;
            }

            if (channelHide && !privateHide) {
                return isChannelOrGroup;
            }

            return false;
        }

        return true;
    }

    public static void saveReadHistory(Object object) {
        if (HideSeen.TLChannels_readHistory == null && HideSeen.isTLChannelsReadHistoryRequest(object)) {
            HideSeen.TLChannels_readHistory = object;
        } else if (HideSeen.TLMessages_readHistory == null && HideSeen.isTLMessagesReadHistoryRequest(object)) {
            HideSeen.TLMessages_readHistory = object;
        }
    }

    public static void handleReadAfterSend(Object object) {
        try {
            if (isHideSeenEnabled() && ConfigManager.markReadAfterSend != null && ConfigManager.markReadAfterSend.isEnable()) {
                TLRPC.InputPeer peer = extractPeerFromSendObject(object);

                if (peer != null && peer.getInputPeer() != null) {
                    Long dialogId = getDialogId(peer);
                    if (dialogId != null && dialogId != 0L) {
                        MessagesStorage messagesStorage = MessagesStorage.getMessagesStorage();
                        messagesStorage.getStorageQueue().postRunnable(() ->
                                getDialogMaxMessageId(messagesStorage, dialogId, param -> {
                                    if (param > 0) {
                                        markReadOnServer(param, peer);
                                    }
                                }));
                    }
                }
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static void getDialogMaxMessageId(MessagesStorage messagesStorage, long dialog_id, IntCallback callback) {
        messagesStorage.getStorageQueue().postRunnable(() -> {
            int max = queryMaxMid(messagesStorage, "SELECT MAX(mid) FROM messages_v2 WHERE uid = " + dialog_id);
            if (max <= 0) {
                max = queryMaxMid(messagesStorage, "SELECT MAX(mid) FROM messages_topics WHERE uid = " + dialog_id);
            }
            final int resolvedMax = max;
            AndroidUtilities.runOnUIThread(() -> callback.run(resolvedMax));
        });
    }

    private static int queryMaxMid(MessagesStorage messagesStorage, String sql) {
        SQLiteCursor cursor = null;
        try {
            cursor = messagesStorage.getDatabase().queryFinalized(sql, new Object[]{});
            if (cursor != null && cursor.next()) {
                return cursor.intValue(0);
            }
        } catch (Throwable e) {
            Logger.e(e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.dispose();
                } catch (Throwable ignored) {}
            }
        }
        return 0;
    }

    public static void markReadOnServer(int messageId, TLRPC.InputPeer peer) {
        try {
            if (messageId <= 0 || peer == null || peer.getInputPeer() == null) return;
            Object req;
            boolean inputPeerChannel;

            if (ClientManager.isTgnetObfuscated()) {
                inputPeerChannel = peer.getInputPeer().getClass().getName().equals(Obfuscate.getClassName(ClassNames.TL_INPUT_PEER_CHANNEL));
            } else {
                inputPeerChannel = peer.getInputPeer().getClass().getName().contains("TL_inputPeerChannel");
            }

            if (inputPeerChannel) {
                TLRPC.TL_channels_readHistory request;
                if (!ClientManager.is(ClientManager.Client.Nagram) || TLChannels_readHistory == null) {
                    request = new TLRPC.TL_channels_readHistory();
                    Object inputChannel = buildInputChannel(peer);
                    if (inputChannel == null) {
                        inputChannel = MessagesController.getInputChannel(peer);
                    }
                    request.setChannel(inputChannel);
                } else {
                    request = new TLRPC.TL_channels_readHistory(TLChannels_readHistory);
                    request.setChannel(MessagesController.getInputChannel(getDialogId(peer)));
                }
                request.setMax_id(messageId);
                req = request.getTL_channels_readHistory();
            } else {
                TLRPC.TL_messages_readHistory request;
                if (!ClientManager.is(ClientManager.Client.Nagram) || TLMessages_readHistory == null) {
                    request = new TLRPC.TL_messages_readHistory();
                } else {
                    request = new TLRPC.TL_messages_readHistory(TLMessages_readHistory);
                }
                request.setPeer(peer);
                request.setMax_id(messageId);
                req = request.getTL_messages_readHistory();
            }

            if (req == null) return;
            markAllowedReadRequest(req);
            ConnectionsManager.getConnectionsManager().sendRequest(req, RequestDelegate.run((response, error) -> {
                if (error == null && response != null) {
                    Class<?> affectedCls = ClassLoad.getClass(ClassNames.TL_MESSAGES_AFFECTED);
                    if (affectedCls != null && affectedCls.isInstance(response)) {
                        TLRPC.TL_messages_affectedMessages res = new TLRPC.TL_messages_affectedMessages(response);
                        MessagesController.getMessagesController().processNewDifferenceParams(-1, res.getPts(), -1, res.getPtsCount());
                    }
                }
            }));
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private static Object buildInputChannel(TLRPC.InputPeer peer) {
        try {
            Class<?> icClass = ClassLoad.getClass("org.telegram.tgnet.TLRPC$TL_inputChannel", Utils.classLoader, false);
            if (icClass == null) return null;
            Object ic = ReflectHelper.newInstance(icClass);
            ReflectHelper.setLongField(ic, "channel_id", peer.getChannel_id());
            ReflectHelper.setLongField(ic, "access_hash", ReflectHelper.getLongField(peer.getInputPeer(), "access_hash"));
            return ic;
        } catch (Throwable t) {
            Logger.e(t);
            return null;
        }
    }

    private static TLRPC.InputPeer extractPeerFromSendObject(Object object) {
        if (!ClientManager.isTgnetObfuscated()) {
        String className = object.getClass().getName();
        if (className.contains("TL_messages_sendMessage") ||
                className.contains("TL_messages_sendMedia") ||
                className.contains("TL_messages_sendReaction") ||
                className.contains("TL_messages_sendPaidReaction") ||
                className.contains("TL_messages_sendMultiMedia"))
            return new TLRPC.InputPeer(getPeer(object));
        } else {
            Class<?> objectClass = object.getClass();
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MESSAGE))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MEDIA))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_REACTION))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_PAID_REACTION))) ||
                    objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MULTI_MEDIA))))
                return new TLRPC.InputPeer(getPeer(object));
        }
        return null;
    }

    private static Object getPeer(Object msg) {
        Class<?> objectClass = msg.getClass();
        String msgName = null;
        if (ClientManager.isTgnetObfuscated()) {
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MESSAGE))))
                msgName = "TLRPC$TL_messages_sendMessage";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MEDIA))))
                msgName = "TLRPC$TL_messages_sendMedia";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_REACTION))))
                msgName = "TLRPC$TL_messages_sendReaction";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_PAID_REACTION))))
                msgName = "TLRPC$TL_messages_sendPaidReaction";
            if (objectClass.equals(ClassLoad.getClass(Obfuscate.getClassName(ClassNames.TL_MESSAGES_SEND_MULTI_MEDIA))))
                msgName = "TLRPC$TL_messages_sendMultiMedia";
            return ReflectHelper.getObjectField(msg, Obfuscate.getFieldName(msgName, "peer"));
        } else {
            return ReflectHelper.getObjectField(msg, "peer");
        }
    }

    public static Long getDialogId(TLRPC.InputPeer peer) {
        if (peer == null || peer.getInputPeer() == null) return 0L;
        long dialogId;
        if (peer.getChat_id() != 0) {
            dialogId = -peer.getChat_id();
        } else if (peer.getChannel_id() != 0) {
            dialogId = -peer.getChannel_id();
        } else {
            dialogId = peer.getUser_id();
        }
        if (dialogId == 0L && peer.getInputPeer().getClass().getSimpleName().contains("Self")) {
            try {
                UserConfig uc = UserConfig.getInstance(UserConfig.getSelectedAccount());
                if (uc != null && uc.get_UserConfig() != null) {
                    Object ucObj = uc.get_UserConfig();
                    String mName = Obfuscate.getMethodName("UserConfig", "getClientUserId");
                    if (ReflectHelper.findMethodExactIfExists(ucObj.getClass(), mName) != null) {
                        return (long) ReflectHelper.callMethod(ucObj, mName);
                    }
                    return uc.getClientUserId();
                }
            } catch (Throwable ignored) {}
        }

        return dialogId;
    }

}
